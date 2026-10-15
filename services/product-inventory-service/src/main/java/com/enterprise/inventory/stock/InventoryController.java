package com.enterprise.inventory.stock;

import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.stock.StockDtos.InventoryResponse;
import com.enterprise.inventory.stock.StockDtos.SetStockRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stock of one product: GET /api/inventory/{productId} and PUT /api/inventory/{productId} (seller's own products). */
@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Stock position of a product: available, reserved, on hand per warehouse, value")
    public InventoryResponse get(@PathVariable Long productId, @AuthenticationPrincipal AuthenticatedUser seller) {
        return inventoryService.inventoryOf(seller.id(), productId);
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Stock take: set the counted quantity of a warehouse (difference is written to the ledger)")
    public InventoryResponse update(@PathVariable Long productId, @Valid @RequestBody SetStockRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser seller) {
        return inventoryService.setStock(seller.id(), seller.username(), productId, request);
    }
}
