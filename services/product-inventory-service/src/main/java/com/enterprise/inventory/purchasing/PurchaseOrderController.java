package com.enterprise.inventory.purchasing;

import static com.enterprise.inventory.reporting.ReportFiles.orDash;

import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.purchasing.PurchaseOrderService.Filter;
import com.enterprise.inventory.purchasing.PurchasingDtos.GoodsReceiptResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReceivePurchaseOrderRequest;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.reporting.ReportFiles;
import com.enterprise.inventory.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/seller/purchase-orders")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - purchase orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Purchase orders: status, supplier, warehouse, text (PO number, supplier, SKU), dates, overdue, sort")
    public PageResponse<PurchaseOrderResponse> list(@RequestParam(required = false) PurchaseOrderStatus status,
                                                    @RequestParam(required = false) Long supplierId,
                                                    @RequestParam(required = false) Long warehouseId,
                                                    @RequestParam(required = false) String search,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                    @RequestParam(required = false) Boolean overdue,
                                                    @RequestParam(defaultValue = "newest") String sort,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.list(seller.id(), new Filter(status, supplierId, warehouseId, search, from, to, overdue, sort),
                page, size);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.get(seller.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Draft a purchase order (unit cost defaults to the product's current cost)")
    public PurchaseOrderResponse create(@Valid @RequestBody PurchaseOrderRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.create(seller.id(), seller.username(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a draft purchase order")
    public PurchaseOrderResponse update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.update(seller.id(), id, request);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Send the purchase order to the supplier (DRAFT -> ORDERED)")
    public PurchaseOrderResponse submit(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.submit(seller.id(), id, seller.username());
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Receive a delivery (all or part of the quantities): stock in + goods receipt note")
    public GoodsReceiptResponse receive(@PathVariable Long id, @Valid @RequestBody ReceivePurchaseOrderRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.receive(seller.id(), id, seller.username(), request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a draft or ordered purchase order (nothing received yet)")
    public PurchaseOrderResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.cancel(seller.id(), id, seller.username());
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close a partially received purchase order (the rest will not be delivered)")
    public PurchaseOrderResponse close(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.close(seller.id(), id, seller.username());
    }

    @GetMapping("/{id}/document")
    @Operation(summary = "Printable purchase order (PDF) to send to the supplier")
    public ResponseEntity<byte[]> document(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        PurchaseOrderResponse po = service.get(seller.id(), id);
        Report report = new Report("Purchase order " + po.number(),
                seller.displayName() + " -> " + po.supplierName() + " | deliver to " + po.warehouseCode() + " - "
                        + po.warehouseName() + " | status " + po.status(),
                List.of(new Report.Metric("Order date", po.orderDate() == null ? "Draft" : po.orderDate().toString()),
                        new Report.Metric("Expected delivery", po.expectedDate() == null ? "-" : po.expectedDate().toString()),
                        new Report.Metric("Units", po.totalUnits()),
                        new Report.Metric("Total (AED)", po.totalAmount())),
                List.of(new Report.Table("Order lines", List.of("#", "SKU", "Product", "Quantity", "Unit cost", "Line total",
                                "Received"),
                        po.lines().stream().map(l -> List.<Object>of(po.lines().indexOf(l) + 1, l.sku(), l.productName(),
                                l.quantityOrdered(), l.unitCost(), l.lineTotal(), l.quantityReceived())).toList()),
                        new Report.Table("Notes", List.of("Notes"), List.of(List.of(orDash(po.notes()))))));
        return ReportFiles.download(report, "pdf", po.number());
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "The filtered purchase order list as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) PurchaseOrderStatus status,
                                         @RequestParam(required = false) Long supplierId,
                                         @RequestParam(required = false) Long warehouseId,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Boolean overdue,
                                         @RequestParam(defaultValue = "newest") String sort,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        List<PurchaseOrderResponse> orders = service.export(seller.id(),
                new Filter(status, supplierId, warehouseId, search, from, to, overdue, sort));
        BigDecimal total = orders.stream().map(PurchaseOrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Report report = new Report("Purchase orders - " + seller.displayName(), orders.size() + " order(s), amounts in AED",
                List.of(new Report.Metric("Purchase orders", orders.size()), new Report.Metric("Total ordered", total),
                        new Report.Metric("Units outstanding",
                                orders.stream().mapToInt(PurchaseOrderResponse::outstandingUnits).sum())),
                List.of(new Report.Table("Purchase orders", List.of("Number", "Supplier", "Warehouse", "Status", "Ordered",
                        "Expected", "Units", "Outstanding", "Total"),
                        orders.stream().map(o -> List.<Object>of(o.number(), o.supplierName(), o.warehouseCode(),
                                o.status() + (o.overdue() ? " (overdue)" : ""),
                                o.orderDate() == null ? "-" : o.orderDate().toString(),
                                o.expectedDate() == null ? "-" : o.expectedDate().toString(), o.totalUnits(),
                                o.outstandingUnits(), o.totalAmount())).toList())));
        return ReportFiles.download(report, format, "purchase-orders-" + LocalDate.now());
    }
}
