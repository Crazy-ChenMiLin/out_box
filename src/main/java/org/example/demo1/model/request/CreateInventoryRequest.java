package org.example.demo1.model.request;

/**
 * Request DTO：接口调用方传进来的创建库存参数。
 *
 * DTO 只描述“请求需要什么字段”，不要直接拿 Entity 当请求参数。
 */
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
