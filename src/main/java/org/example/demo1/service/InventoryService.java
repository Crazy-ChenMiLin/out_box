package org.example.demo1.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.demo1.dao.InventoryDao;
import org.example.demo1.dao.OutboxEventDao;
import org.example.demo1.model.bo.InventoryBo;
import org.example.demo1.model.entity.InventoryEntity;
import org.example.demo1.model.entity.OutboxEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class InventoryService {

    private final InventoryDao inventoryDao;
    private final OutboxEventDao outboxEventDao;
    private final ObjectMapper objectMapper;

    public InventoryService(InventoryDao inventoryDao, OutboxEventDao outboxEventDao, ObjectMapper objectMapper) {
        this.inventoryDao = inventoryDao;
        this.outboxEventDao = outboxEventDao;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventoryBo createInventory(String sku, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Quantity must be greater than or equal to 0");
        }

        InventoryEntity inventory = new InventoryEntity(sku, quantity);
        InventoryEntity saved = inventoryDao.save(inventory);
        return InventoryBo.fromEntity(saved);
    }

    @Transactional
    public InventoryBo decrementSku(String sku) {
        InventoryEntity inventory = inventoryDao.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));

        if (inventory.getQuantity() <= 0) {
            throw new IllegalStateException("Out of stock for sku=" + sku);
        }

        inventory.setQuantity(inventory.getQuantity() - 1);
        inventory.setUpdatedAt(LocalDateTime.now());
        InventoryEntity saved = inventoryDao.save(inventory);

        // prepare outbox event
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", sku);
        payload.put("delta", -1);
        payload.put("remaining", inventory.getQuantity());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        OutboxEventEntity event = new OutboxEventEntity("Inventory", String.valueOf(saved.getId()), "INVENTORY_DECREMENTED", payloadJson);
        outboxEventDao.save(event);
        return InventoryBo.fromEntity(saved);
    }
}

