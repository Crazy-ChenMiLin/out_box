package org.example.demo1.controller;

import org.example.demo1.model.bo.InventoryBo;
import org.example.demo1.model.request.CreateInventoryRequest;
import org.example.demo1.model.response.InventoryResponse;
import org.example.demo1.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/{sku}/decrement")
    public ResponseEntity<?> decrement(@PathVariable String sku) {
        try {
            InventoryBo inventory = inventoryService.decrementSku(sku);
            return ResponseEntity.ok(InventoryResponse.fromBo(inventory));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{sku}")
    public ResponseEntity<InventoryResponse> create(
            @PathVariable String sku,
            @RequestParam(required = false) Integer qty,
            @RequestBody(required = false) CreateInventoryRequest request) {
        CreateInventoryRequest createRequest = request == null ? new CreateInventoryRequest(qty) : request;
        InventoryBo saved = inventoryService.createInventory(sku, createRequest.quantityOrDefault(10));
        InventoryResponse response = InventoryResponse.fromBo(saved);
        return ResponseEntity.created(URI.create("/api/inventory/" + response.getSku())).body(response);
    }
}
