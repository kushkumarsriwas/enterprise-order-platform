package com.enterprise.inventory.merchant;

import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.stock.InventoryService;
import com.enterprise.inventory.stock.StockDtos.AdjustStockRequest;
import com.enterprise.inventory.stock.StockDtos.InventoryResponse;
import com.enterprise.inventory.stock.StockDtos.InventorySummary;
import com.enterprise.inventory.stock.StockDtos.MovementResponse;
import com.enterprise.inventory.stock.StockDtos.PurchaseResponse;
import com.enterprise.inventory.stock.StockDtos.RestockRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant - inventory")
public class MerchantInventoryController {

    private final InventoryService inventoryService;

    public MerchantInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/inventory")
    @Operation(summary = "Stock levels and stock value of all my products")
    public List<InventoryResponse> stock(@AuthenticationPrincipal AuthenticatedUser merchant) {
        return inventoryService.stockList(merchant);
    }

    @GetMapping("/inventory/summary")
    @Operation(summary = "Inventory KPIs: units, value at cost and at price, low stock")
    public InventorySummary summary(@AuthenticationPrincipal AuthenticatedUser merchant) {
        return inventoryService.summary(merchant);
    }

    @GetMapping("/inventory/low-stock")
    public List<InventoryResponse> lowStock(@AuthenticationPrincipal AuthenticatedUser merchant) {
        return inventoryService.lowStock(merchant);
    }

    @GetMapping("/inventory/movements")
    @Operation(summary = "Audit trail of stock movements (all products, or one with productId)")
    public List<MovementResponse> movements(@RequestParam(required = false) Long productId,
                                            @AuthenticationPrincipal AuthenticatedUser merchant) {
        return inventoryService.movements(productId, merchant);
    }

    @GetMapping("/inventory/{productId}")
    public InventoryResponse get(@PathVariable Long productId, @AuthenticationPrincipal AuthenticatedUser merchant) {
        return inventoryService.get(productId, merchant);
    }

    @PutMapping("/inventory/{productId}")
    @Operation(summary = "Stock count: set the available quantity (audited)")
    public InventoryResponse adjust(@PathVariable Long productId, @Valid @RequestBody AdjustStockRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser merchant) {
        return inventoryService.adjust(productId, request, merchant);
    }

    @PostMapping("/inventory/{productId}/restock")
    @Operation(summary = "Receive goods from a supplier at a unit cost (weighted average cost)")
    public InventoryResponse restock(@PathVariable Long productId, @Valid @RequestBody RestockRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser merchant) {
        return inventoryService.restock(productId, request, merchant);
    }

    @GetMapping("/purchases")
    @Operation(summary = "Goods received (stock purchases) in a date range")
    public List<PurchaseResponse> purchases(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AuthenticatedUser merchant) {
        LocalDate start = from == null ? LocalDate.now(ZoneOffset.UTC).minusDays(90) : from;
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        return inventoryService.purchases(merchant, start.atStartOfDay().toInstant(ZoneOffset.UTC),
                end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
