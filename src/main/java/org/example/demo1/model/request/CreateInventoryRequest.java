package org.example.demo1.model.request;

public class CreateInventoryRequest {

    private Integer quantity;

    public CreateInventoryRequest() {
    }

    public CreateInventoryRequest(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer quantityOrDefault(int defaultQuantity) {
        return quantity == null ? defaultQuantity : quantity;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}

