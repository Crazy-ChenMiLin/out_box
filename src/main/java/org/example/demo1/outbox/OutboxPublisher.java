package org.example.demo1.outbox;

import org.example.demo1.dao.OutboxEventDao;
import org.example.demo1.model.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 备用 outbox 发布器：定时扫描 PENDING 事件并发到 Kafka。
 *
 * 小白理解版：
 * 1. 正常推荐走 Canal：MySQL binlog -> Canal -> Spring Canal 客户端 -> Kafka。
 * 2. 这个类只是备用方案：不经过 Canal，Spring 自己查 outbox 表并发 Kafka。
 * 3. application.properties 里 outbox.publisher.enabled=false，所以默认不会启动。
 */
@Component
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventDao outboxEventDao;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public OutboxPublisher(OutboxEventDao outboxEventDao,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${outbox.publisher.topic:inventory-events}") String topic) {
        this.outboxEventDao = outboxEventDao;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms}")
    @Transactional
    public void publishPending() {
        logger.info("[备用发布器] 开始扫描 PENDING 事件，topic={}", topic);

        // 查询待发送事件。DAO 上加了悲观锁，避免多个发布器重复处理同一批事件。
        List<OutboxEventEntity> pending = outboxEventDao.findPendingEvents();
        if (pending.isEmpty()) {
            logger.info("[备用发布器] 没有待发送的 outbox 事件");
            return;
        }

        logger.info("[备用发布器] 找到待发送事件，数量={}", pending.size());

        for (OutboxEventEntity ev : pending) {
            try {
                // outbox payload 本身已经是 JSON 字符串，所以这里用 StringSerializer 发送。
                logger.info("[备用发布器] 准备发送 outbox 事件，eventId={}，业务ID={}，topic={}",
                        ev.getId(), ev.getAggregateId(), topic);
                CompletableFuture<SendResult<String, String>> future =
                        kafkaTemplate.send(topic, ev.getAggregateId(), ev.getPayload());

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        // 发送失败时保持 PENDING，下一轮定时任务还会继续重试。
                        logger.error("[备用发布器] 发送 outbox 事件失败，eventId={}", ev.getId(), ex);
                        return;
                    }

                    // Kafka 确认发送成功后，才把事件标记为 SENT。
                    try {
                        ev.setStatus("SENT");
                        ev.setSentAt(LocalDateTime.now());
                        outboxEventDao.save(ev);
                        logger.info("[备用发布器] outbox 状态已改为 SENT，eventId={}", ev.getId());
                    } catch (Exception e) {
                        logger.error("[备用发布器] 修改 outbox 状态失败，eventId={}", ev.getId(), e);
                    }
                });

            } catch (Exception ex) {
                logger.error("[备用发布器] 发布 outbox 事件出错，eventId={}", ev.getId(), ex);
            }
        }
    }
}


