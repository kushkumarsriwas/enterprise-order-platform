package com.enterprise.inventory.purchasing;

import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReplenishmentOrderRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReplenishmentSuggestion;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.reporting.ReportFiles;
import com.enterprise.inventory.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/replenishment")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - replenishment planning")
public class ReplenishmentController {

    private final ReplenishmentService service;

    public ReplenishmentController(ReplenishmentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "What to reorder: reorder point, lead-time demand, incoming stock, suggested quantity")
    public List<ReplenishmentSuggestion> suggestions(@RequestParam(required = false) Long supplierId,
                                                     @RequestParam(defaultValue = "true") boolean onlyNeeded,
                                                     @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.suggestions(seller.id(), supplierId, onlyNeeded);
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create one draft purchase order per supplier from the selected suggestions")
    public List<PurchaseOrderResponse> createPurchaseOrders(@Valid @RequestBody ReplenishmentOrderRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.createPurchaseOrders(seller.id(), seller.username(), request);
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Reorder report as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format, @RequestParam(required = false) Long supplierId,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        List<ReplenishmentSuggestion> rows = service.suggestions(seller.id(), supplierId, true);
        BigDecimal cost = rows.stream().map(ReplenishmentSuggestion::estimatedCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        Report report = new Report("Reorder report - " + seller.displayName(),
                "Products to replenish on " + LocalDate.now() + " (demand = average of the last 30 days)",
                List.of(new Report.Metric("Products to reorder", rows.size()),
                        new Report.Metric("Estimated purchase cost (AED)", cost)),
                List.of(new Report.Table("Suggestions", List.of("SKU", "Product", "Supplier", "Available", "Incoming",
                        "Reorder point", "Daily sales", "Days of cover", "Lead time", "Suggested qty", "Est. cost", "Reason"),
                        rows.stream().map(s -> List.<Object>of(s.sku(), s.productName(),
                                s.supplierName() == null ? "-" : s.supplierName(), s.availableQuantity(),
                                s.incomingQuantity(), s.reorderPoint(), s.averageDailySales(),
                                s.daysOfCover() == null ? "-" : s.daysOfCover(), s.leadTimeDays(), s.suggestedQuantity(),
                                s.estimatedCost(), s.reason())).toList())));
        return ReportFiles.download(report, format, "reorder-report-" + LocalDate.now());
    }
}
