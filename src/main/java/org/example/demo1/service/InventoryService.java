package org.example.demo1.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.demo1.dao.InventoryDao;
import org.example.demo1.dao.OutboxEventDao;
import org.example.demo1.model.bo.InventoryBo;
import org.example.demo1.model.entity.InventoryEntity;
import org.example.demo1.model.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service 层：放业务规则。
 *
 * 小白理解版：
 * 1. Controller 只负责接 HTTP 请求。
 * 2. 真正的“创建库存、扣库存、写 outbox 事件”都放在 Service。
 * 3. @Transactional 表示方法里的数据库操作在同一个事务里执行。
 */
@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryDao inventoryDao;
    private final OutboxEventDao outboxEventDao;
    private final ObjectMapper objectMapper;

    public InventoryService(InventoryDao inventoryDao, OutboxEventDao outboxEventDao, ObjectMapper objectMapper) {
        this.inventoryDao = inventoryDao;
        this.outboxEventDao = outboxEventDao;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建库存。
     *
     * 这里还不会写 outbox，因为“创建库存”不是本 demo 要发送的库存扣减事件。
     */
    @Transactional
    public InventoryBo createInventory(String sku, Integer quantity) {
        logger.info("[业务] 开始创建库存，sku={}，数量={}", sku, quantity);

        if (quantity == null || quantity < 0) {
            logger.warn("[业务] 创建库存失败，数量不合法，sku={}，数量={}", sku, quantity);
            throw new IllegalArgumentException("Quantity must be greater than or equal to 0");
        }

        // 这一步只是把 Java 对象交给 JPA，事务提交时会落到 inventory 表。
        InventoryEntity inventory = new InventoryEntity(sku, quantity);
        InventoryEntity saved = inventoryDao.save(inventory);

        logger.info("[业务] 库存已保存，id={}，sku={}，数量={}",
                saved.getId(), saved.getSku(), saved.getQuantity());
        return InventoryBo.fromEntity(saved);
    }

    /**
     * 扣减库存，并同时写入一条 outbox 事件。
     *
     * 小白理解版：
     * 1. 先查库存。
     * 2. 库存够，就把 quantity 减 1。
     * 3. 同一个事务里再插入 outbox 表。
     * 4. Canal 看到 outbox 表的 INSERT binlog 后，再把消息送到 Kafka。
     *
     * 关键点：扣库存和写 outbox 必须一起成功或一起失败，不能只做一半。
     */
    @Transactional
    public InventoryBo decrementSku(String sku) {
        logger.info("[业务] 开始扣减库存，sku={}", sku);

        InventoryEntity inventory = inventoryDao.findBySku(sku)
                .orElseThrow(() -> {
                    logger.warn("[业务] 库存不存在，sku={}", sku);
                    return new IllegalArgumentException("SKU not found: " + sku);
                });

        logger.info("[业务] 已读取库存，id={}，sku={}，当前库存={}",
                inventory.getId(), inventory.getSku(), inventory.getQuantity());

        if (inventory.getQuantity() <= 0) {
            logger.warn("[业务] 库存不足，id={}，sku={}", inventory.getId(), sku);
            throw new IllegalStateException("Out of stock for sku=" + sku);
        }

        inventory.setQuantity(inventory.getQuantity() - 1);
        inventory.setUpdatedAt(LocalDateTime.now());
        InventoryEntity saved = inventoryDao.save(inventory);

        logger.info("[业务] 库存已扣减，id={}，sku={}，剩余库存={}",
                saved.getId(), saved.getSku(), saved.getQuantity());

        // payload 是准备给 Kafka 消费者看的业务消息。这里不要放 Entity，放最小必要信息更清晰。
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", sku);
        payload.put("delta", -1);
        payload.put("remaining", saved.getQuantity());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.error("[业务] 生成 outbox 消息失败，sku={}", sku, e);
            throw new RuntimeException(e);
        }

        // 这里不直接发 Kafka，只写 outbox 表。
        // Canal 后面会监听 MySQL binlog，所以即使 Kafka 当下不可用，数据库里的事件也不会丢。
        OutboxEventEntity event = new OutboxEventEntity(
                "Inventory",
                String.valueOf(saved.getId()),
                "INVENTORY_DECREMENTED",
                payloadJson);
        OutboxEventEntity savedEvent = outboxEventDao.save(event);

        logger.info("[业务] outbox 事件已写入，eventId={}，业务对象={}，业务ID={}，类型={}，状态={}",
                savedEvent.getId(),
                savedEvent.getAggregateType(),
                savedEvent.getAggregateId(),
                savedEvent.getType(),
                savedEvent.getStatus());

        return InventoryBo.fromEntity(saved);
    }
}
