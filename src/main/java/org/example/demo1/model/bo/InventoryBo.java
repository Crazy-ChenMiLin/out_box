package org.example.demo1.model.bo;

import org.example.demo1.model.entity.InventoryEntity;

import java.time.LocalDateTime;

public class InventoryBo {

    private Long id;
    private String sku;
    private Integer quantity;
    private LocalDateTime updatedAt;

    public InventoryBo() {
    }

    public InventoryBo(Long id, String sku, Integer quantity, LocalDateTime updatedAt) {
        this.id = id;
        this.sku = sku;
        this.quantity = quantity;
        this.updatedAt = updatedAt;
    }

    public static InventoryBo fromEntity(InventoryEntity entity) {
        return new InventoryBo(entity.getId(), entity.getSku(), entity.getQuantity(), entity.getUpdatedAt());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
