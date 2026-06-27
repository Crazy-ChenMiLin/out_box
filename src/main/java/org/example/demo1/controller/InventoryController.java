package org.example.demo1.controller;

import org.example.demo1.model.bo.InventoryBo;
import org.example.demo1.model.request.CreateInventoryRequest;
import org.example.demo1.model.response.InventoryResponse;
import org.example.demo1.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Controller 层：负责接收 HTTP 请求和返回 HTTP 响应。
 *
 * 注意：Controller 不直接操作数据库，真正的业务逻辑交给 Service 层处理。
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * 扣减指定 SKU 的库存。
     *
     * 返回 404：SKU 不存在。
     * 返回 400：库存不足，不能继续扣减。
     */
    @PostMapping("/{sku}/decrement")
    public ResponseEntity<?> decrement(@PathVariable String sku) {
        // 这条日志表示：HTTP 请求已经进到系统入口了。
        logger.info("[请求] 收到扣减库存请求，sku={}", sku);

        try {
            InventoryBo inventory = inventoryService.decrementSku(sku);
            // 对外返回 VO，避免把数据库 Entity 直接暴露给接口调用方。
            InventoryResponse response = InventoryResponse.fromBo(inventory);
            logger.info("[请求] 扣减库存成功，sku={}，剩余库存={}", response.getSku(), response.getQuantity());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("[请求] 扣减失败，sku 不存在，sku={}", sku);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            logger.warn("[请求] 扣减失败，sku={}，原因={}", sku, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 创建库存。
     *
     * 推荐方式：POST JSON body，例如 {"quantity": 10}。
     * 兼容方式：POST /api/inventory/{sku}?qty=10，方便用浏览器或 curl 快速测试。
     */
    @PostMapping("/{sku}")
    public ResponseEntity<InventoryResponse> create(
            @PathVariable String sku,
            @RequestParam(required = false) Integer qty,
            @RequestBody(required = false) CreateInventoryRequest request) {
        CreateInventoryRequest createRequest = request == null ? new CreateInventoryRequest(qty) : request;
        Integer quantity = createRequest.quantityOrDefault(10);

        // 这条日志可以帮助你确认：接口收到的 sku 和数量是不是你 curl 里传的值。
        logger.info("[请求] 收到创建库存请求，sku={}，数量={}", sku, quantity);

        InventoryBo saved = inventoryService.createInventory(sku, quantity);
        InventoryResponse response = InventoryResponse.fromBo(saved);
        // 创建成功返回 201 Created，并把新资源地址放到 Location 响应头里。
        logger.info("[请求] 创建库存成功，id={}，sku={}，数量={}",
                response.getId(), response.getSku(), response.getQuantity());
        return ResponseEntity.created(URI.create("/api/inventory/" + response.getSku())).body(response);
    }
}
