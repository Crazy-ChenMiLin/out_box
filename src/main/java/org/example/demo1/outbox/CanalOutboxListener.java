package org.example.demo1.outbox;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import org.example.demo1.dao.OutboxEventDao;
import org.example.demo1.model.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Canal TCP 客户端：把 outbox 表的 INSERT binlog 转发到 Kafka。
 *
 * 小白理解版：
 * 1. Spring 启动后，这个类会连上本地 canal-server 的 11111 端口。
 * 2. Canal 负责盯 MySQL binlog，这个类负责从 Canal 拉取变化数据。
 * 3. 只处理 demo1.outbox 的 INSERT，因为新插入的 outbox 行才代表“有一个业务事件要发”。
 * 4. Kafka 发送成功后，再把 outbox.status 改成 SENT，方便你在数据库里看处理结果。
 */
@Component
@ConditionalOnProperty(prefix = "canal.client", name = "enabled", havingValue = "true")
public class CanalOutboxListener implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(CanalOutboxListener.class);

    private final OutboxEventDao outboxEventDao;
    private final TransactionTemplate transactionTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String host;
    private final int port;
    private final String destination;
    private final String username;
    private final String password;
    private final String subscribeFilter;
    private final int batchSize;
    private final long emptySleepMs;
    private final String topic;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "canal-outbox-listener");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private volatile CanalConnector connector;

    public CanalOutboxListener(OutboxEventDao outboxEventDao,
                               TransactionTemplate transactionTemplate,
                               KafkaTemplate<String, String> kafkaTemplate,
                               @Value("${canal.client.host:localhost}") String host,
                               @Value("${canal.client.port:11111}") int port,
                               @Value("${canal.client.destination:example}") String destination,
                               @Value("${canal.client.username:}") String username,
                               @Value("${canal.client.password:}") String password,
                               @Value("${canal.client.subscribe-filter:demo1\\\\.outbox}") String subscribeFilter,
                               @Value("${canal.client.batch-size:100}") int batchSize,
                               @Value("${canal.client.empty-sleep-ms:1000}") long emptySleepMs,
                               @Value("${canal.client.topic:inventory-events}") String topic) {
        this.outboxEventDao = outboxEventDao;
        this.transactionTemplate = transactionTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.host = host;
        this.port = port;
        this.destination = destination;
        this.username = username;
        this.password = password;
        this.subscribeFilter = subscribeFilter;
        this.batchSize = batchSize;
        this.emptySleepMs = emptySleepMs;
        this.topic = topic;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        logger.info("[Canal] 开始连接 canal-server，host={}，port={}，destination={}，监听表={}，Kafka topic={}",
                host, port, destination, subscribeFilter, topic);
        executor.submit(this::listen);
    }

    @Override
    public void stop() {
        logger.info("[Canal] 监听器停止");
        running = false;
        if (connector != null) {
            connector.disconnect();
        }
        executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listen() {
        while (running) {
            try {
                // 这里不是直接连 MySQL，而是连 canal-server。
                // canal-server 再根据它自己的 instance.properties 去连 MySQL。
                connector = CanalConnectors.newSingleConnector(
                        new InetSocketAddress(host, port),
                        destination,
                        username,
                        password);
                connector.connect();
                connector.subscribe(subscribeFilter);

                // rollback() 在这里的意思是：连接建立后，先让 Canal 客户端状态回到上一次确认的位置。
                // 这样可以避免应用重启后从一个不确定的位置继续消费。
                connector.rollback();
                logger.info("[Canal] 已连接 canal-server，host={}，port={}，destination={}，监听表={}",
                        host, port, destination, subscribeFilter);

                while (running) {
                    // getWithoutAck 表示“先拿一批，但我处理完之前不确认”。
                    // 如果处理 Kafka 失败，下面会 rollback(batchId)，这批数据下次还能重新拿到。
                    Message message = connector.getWithoutAck(batchSize);
                    long batchId = message.getId();
                    List<CanalEntry.Entry> entries = message.getEntries();
                    if (batchId == -1 || entries.isEmpty()) {
                        logger.debug("[Canal] 暂时没有新的 binlog，sleepMs={}", emptySleepMs);
                        sleep(emptySleepMs);
                        continue;
                    }

                    try {
                        logger.debug("[Canal] 收到一批 binlog，batchId={}，entryCount={}", batchId, entries.size());
                        publishEntries(entries);

                        // ack 表示：这批 binlog 已经成功处理，Canal 可以推进消费位点。
                        connector.ack(batchId);
                        logger.debug("[Canal] 这批 binlog 已确认，batchId={}", batchId);
                    } catch (Exception ex) {
                        // rollback 表示：这批数据处理失败，不推进位点，后面还能重试。
                        connector.rollback(batchId);
                        logger.error("[Canal] 这批 binlog 处理失败，已回滚，batchId={}", batchId, ex);
                        sleep(emptySleepMs);
                    }
                }
            } catch (Exception ex) {
                if (running) {
                    logger.error("[Canal] 连接失败，5 秒后重试", ex);
                    sleep(TimeUnit.SECONDS.toMillis(5));
                }
            } finally {
                if (connector != null) {
                    connector.disconnect();
                    connector = null;
                    logger.info("[Canal] 已断开连接");
                }
            }
        }
    }

    private void publishEntries(List<CanalEntry.Entry> entries) throws Exception {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                logger.debug("[Canal] 跳过非行数据，entryType={}", entry.getEntryType());
                continue;
            }

            CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            if (rowChange.getEventType() != CanalEntry.EventType.INSERT) {
                logger.debug("[Canal] 跳过非 INSERT 事件，eventType={}", rowChange.getEventType());
                continue;
            }

            String schema = entry.getHeader().getSchemaName();
            String table = entry.getHeader().getTableName();
            if (!"demo1".equalsIgnoreCase(schema) || !"outbox".equalsIgnoreCase(table)) {
                logger.debug("[Canal] 跳过不需要监听的表，schema={}，table={}", schema, table);
                continue;
            }

            logger.info("[Canal] 监听到 outbox 新事件，schema={}，table={}，行数={}",
                    schema, table, rowChange.getRowDatasCount());

            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                Map<String, String> row = toMap(rowData.getAfterColumnsList());
                String eventId = row.get("id");
                String aggregateId = row.get("aggregate_id");
                String payload = row.get("payload");
                if (eventId == null || aggregateId == null || payload == null) {
                    logger.warn("[Canal] 跳过不完整的 outbox 行，row={}", row);
                    continue;
                }

                // Kafka 的 key 用 aggregateId，方便同一个业务对象的事件尽量落到同一个分区。
                logger.info("[Kafka] 准备发送 outbox 事件，eventId={}，业务ID={}，topic={}",
                        eventId, aggregateId, topic);
                kafkaTemplate.send(topic, aggregateId, payload).get();
                markSent(Long.parseLong(eventId));
                logger.info("[Kafka] 发送成功，eventId={}，业务ID={}，topic={}",
                        eventId, aggregateId, topic);
            }
        }
    }

    private void markSent(Long eventId) {
        transactionTemplate.executeWithoutResult(status -> outboxEventDao.findById(eventId).ifPresent(event -> {
            if (!"SENT".equals(event.getStatus())) {
                event.setStatus("SENT");
                event.setSentAt(LocalDateTime.now());
                outboxEventDao.save(event);
                logger.info("[Outbox] 状态已改为 SENT，eventId={}", eventId);
            } else {
                logger.info("[Outbox] 状态已经是 SENT，eventId={}", eventId);
            }
        }));
    }

    private Map<String, String> toMap(List<CanalEntry.Column> columns) {
        Map<String, String> values = new HashMap<>();
        for (CanalEntry.Column column : columns) {
            values.put(column.getName(), column.getValue());
        }
        return values;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
