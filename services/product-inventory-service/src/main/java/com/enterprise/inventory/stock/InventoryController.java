package com.enterprise.inventory.stock;

import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.stock.StockDtos.AdjustStockRequest;
import com.enterprise.inventory.stock.StockDtos.InventoryResponse;
import com.enterprise.inventory.stock.StockDtos.MovementResponse;
import com.enterprise.inventory.stock.StockDtos.RestockRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Inventory (admin)")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Products at or below their reorder level")
    public List<InventoryResponse> lowStock() {
        return inventoryService.lowStock();
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Stock level of a product")
    public InventoryResponse get(@PathVariable Long productId) {
        return inventoryService.get(productId);
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Set the available quantity after a stock count (audited)")
    public InventoryResponse adjust(@PathVariable Long productId, @Valid @RequestBody AdjustStockRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return inventoryService.adjust(productId, request, user.username());
    }

    @PostMapping("/{productId}/restock")
    @Operation(summary = "Add received goods to stock (audited)")
    public InventoryResponse restock(@PathVariable Long productId, @Valid @RequestBody RestockRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        return inventoryService.restock(productId, request, user.username());
    }

    @GetMapping("/{productId}/movements")
    @Operation(summary = "Audit trail of the last 50 stock movements")
    public List<MovementResponse> movements(@PathVariable Long productId) {
        return inventoryService.movements(productId);
    }
}
