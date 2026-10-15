package com.enterprise.inventory.stock;

import com.enterprise.inventory.catalog.SellerProductQuery;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.reporting.ReportFiles;
import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.stock.InventoryService.MovementFilter;
import com.enterprise.inventory.stock.StockDtos.AdjustmentRequest;
import com.enterprise.inventory.stock.StockDtos.BinRequest;
import com.enterprise.inventory.stock.StockDtos.IntegrityIssue;
import com.enterprise.inventory.stock.StockDtos.InventoryResponse;
import com.enterprise.inventory.stock.StockDtos.InventorySummary;
import com.enterprise.inventory.stock.StockDtos.MovementResponse;
import com.enterprise.inventory.stock.StockDtos.StockLevelRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/inventory")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - stock levels and ledger")
public class SellerInventoryController {

    private final InventoryService inventoryService;

    public SellerInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @Operation(summary = "Stock levels: text, category, listing, stock status, supplier, brand, warehouse, price range, sort")
    public PageResponse<StockLevelRow> levels(@RequestParam(required = false) String search,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) Boolean listed,
                                              @RequestParam(required = false) StockStatus stockStatus,
                                              @RequestParam(required = false) Long supplierId,
                                              @RequestParam(required = false) String brand,
                                              @RequestParam(required = false) Long warehouseId,
                                              @RequestParam(required = false) BigDecimal minPrice,
                                              @RequestParam(required = false) BigDecimal maxPrice,
                                              @RequestParam(defaultValue = "name") String sort,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "25") int size,
                                              @AuthenticationPrincipal AuthenticatedUser seller) {
        return inventoryService.stockLevels(seller.id(), new SellerProductQuery.Filter(search, categoryId, listed,
                stockStatus, supplierId, brand, warehouseId, minPrice, maxPrice, sort), page, size);
    }

    @GetMapping("/summary")
    @Operation(summary = "Inventory KPIs: units, reserved, incoming, low / out of stock, value at cost and price")
    public InventorySummary summary(@AuthenticationPrincipal AuthenticatedUser seller) {
        return inventoryService.summary(seller.id());
    }

    @GetMapping("/{productId}")
    public InventoryResponse get(@PathVariable Long productId, @AuthenticationPrincipal AuthenticatedUser seller) {
        return inventoryService.inventoryOf(seller.id(), productId);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adjust stock in a warehouse with a reason (damaged, expired, lost, found, correction...)")
    public MovementResponse adjust(@Valid @RequestBody AdjustmentRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser seller) {
        return inventoryService.adjust(seller.id(), seller.username(), request);
    }

    @PutMapping("/{productId}/bin")
    @Operation(summary = "Set the shelf / bin location of a product in a warehouse")
    public InventoryResponse bin(@PathVariable Long productId, @Valid @RequestBody BinRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser seller) {
        return inventoryService.setBin(seller.id(), productId, request);
    }

    @GetMapping("/movements")
    @Operation(summary = "Stock ledger: product, warehouse, movement types, reason, text (SKU, name, reference), dates")
    public PageResponse<MovementResponse> movements(@RequestParam(required = false) Long productId,
                                                    @RequestParam(required = false) Long warehouseId,
                                                    @RequestParam(required = false) Set<MovementType> type,
                                                    @RequestParam(required = false) AdjustmentReason reason,
                                                    @RequestParam(required = false) String search,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                    @RequestParam(defaultValue = "false") boolean physicalOnly,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "30") int size,
                                                    @AuthenticationPrincipal AuthenticatedUser seller) {
        return inventoryService.movements(seller.id(), new MovementFilter(productId, warehouseId, type, reason, search,
                from, to, physicalOnly), page, size);
    }

    @GetMapping("/integrity")
    @Operation(summary = "Reconciliation check: products whose warehouse quantities don't add up (should be empty)")
    public List<IntegrityIssue> integrity(@AuthenticationPrincipal AuthenticatedUser seller) {
        return inventoryService.integrityCheck(seller.id());
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Stock levels (same filters) as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Boolean listed,
                                         @RequestParam(required = false) StockStatus stockStatus,
                                         @RequestParam(required = false) Long supplierId,
                                         @RequestParam(required = false) String brand,
                                         @RequestParam(required = false) Long warehouseId,
                                         @RequestParam(required = false) BigDecimal minPrice,
                                         @RequestParam(required = false) BigDecimal maxPrice,
                                         @RequestParam(defaultValue = "name") String sort,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        List<StockLevelRow> rows = inventoryService.stockLevelsExport(seller.id(), new SellerProductQuery.Filter(search,
                categoryId, listed, stockStatus, supplierId, brand, warehouseId, minPrice, maxPrice, sort));
        BigDecimal value = rows.stream().map(StockLevelRow::stockValueAtCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        Report report = new Report("Stock levels - " + seller.displayName(),
                (warehouseId == null ? "All warehouses" : "One warehouse") + " on " + LocalDate.now() + ", amounts in AED",
                List.of(new Report.Metric("Products", rows.size()),
                        new Report.Metric("Units on hand", rows.stream().mapToLong(StockLevelRow::onHandQuantity).sum()),
                        new Report.Metric("Value at cost", value),
                        new Report.Metric("Low / out of stock", rows.stream()
                                .filter(r -> !"IN_STOCK".equals(r.stockStatus())).count())),
                List.of(new Report.Table("Stock", List.of("SKU", "Product", "Category", "Listed", "Available", "Reserved",
                        "On hand", warehouseId == null ? "Incoming" : "In warehouse", "Reorder at", "Status", "Unit cost",
                        "Value"),
                        rows.stream().map(r -> List.<Object>of(r.sku(), r.productName(),
                                r.categoryName() == null ? "-" : r.categoryName(), r.listed() ? "Yes" : "No",
                                r.availableQuantity(), r.reservedQuantity(), r.onHandQuantity(),
                                warehouseId == null ? r.incomingQuantity() : r.warehouseOnHand() == null ? 0 : r.warehouseOnHand(),
                                r.reorderPoint(), r.stockStatus().replace('_', ' '), r.unitCost(), r.stockValueAtCost()))
                                .toList())));
        return ReportFiles.download(report, format, "stock-levels-" + LocalDate.now());
    }

    @GetMapping("/movements/export/{format}")
    @Operation(summary = "Stock ledger (same filters) as xlsx or pdf")
    public ResponseEntity<byte[]> exportMovements(@PathVariable String format,
                                                  @RequestParam(required = false) Long productId,
                                                  @RequestParam(required = false) Long warehouseId,
                                                  @RequestParam(required = false) Set<MovementType> type,
                                                  @RequestParam(required = false) AdjustmentReason reason,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "false") boolean physicalOnly,
                                                  @AuthenticationPrincipal AuthenticatedUser seller) {
        List<MovementResponse> rows = inventoryService.movementsExport(seller.id(), new MovementFilter(productId,
                warehouseId, type, reason, search, from, to, physicalOnly));
        Report report = new Report("Stock ledger - " + seller.displayName(),
                (from == null ? "All dates" : from + " to " + (to == null ? LocalDate.now() : to)),
                List.of(new Report.Metric("Movements", rows.size()),
                        new Report.Metric("Units in", rows.stream().filter(m -> m.warehouseId() != null && m.quantity() > 0)
                                .mapToLong(MovementResponse::quantity).sum()),
                        new Report.Metric("Units out", -rows.stream().filter(m -> m.warehouseId() != null && m.quantity() < 0)
                                .mapToLong(MovementResponse::quantity).sum())),
                List.of(new Report.Table("Movements", List.of("Time (UTC)", "SKU", "Product", "Warehouse", "Type", "Qty",
                        "Balance", "Reason", "Reference", "By"),
                        rows.stream().map(m -> List.<Object>of(ReportFiles.dateTime(m.createdAt()), m.sku(), m.productName(),
                                m.warehouseCode() == null ? "-" : m.warehouseCode(), m.type().replace('_', ' '),
                                m.quantity(), m.balanceAfter() == null ? "-" : m.balanceAfter(),
                                m.reason() == null ? "-" : m.reason(), m.reference() == null ? "-" : m.reference(),
                                m.performedBy() == null ? "-" : m.performedBy())).toList())));
        return ReportFiles.download(report, format, "stock-ledger-" + LocalDate.now());
    }
}
