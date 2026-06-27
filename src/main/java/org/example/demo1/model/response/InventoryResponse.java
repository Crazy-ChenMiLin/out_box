package org.example.demo1.model.response;

import org.example.demo1.model.bo.InventoryBo;

import java.time.LocalDateTime;

public class InventoryResponse {

    private Long id;
    private String sku;
    private Integer quantity;
    private LocalDateTime updatedAt;

    public InventoryResponse() {
    }

    public InventoryResponse(Long id, String sku, Integer quantity, LocalDateTime updatedAt) {
        this.id = id;
        this.sku = sku;
        this.quantity = quantity;
        this.updatedAt = updatedAt;
    }

    public static InventoryResponse fromBo(InventoryBo bo) {
        return new InventoryResponse(bo.getId(), bo.getSku(), bo.getQuantity(), bo.getUpdatedAt());
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
