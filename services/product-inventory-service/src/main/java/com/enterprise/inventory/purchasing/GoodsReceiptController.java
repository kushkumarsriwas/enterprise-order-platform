package com.enterprise.inventory.purchasing;

import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.GoodsReceiptResponse;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.reporting.ReportFiles;
import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.stock.StockDtos.ReceiveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/seller/goods-receipts")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - goods receipts")
public class GoodsReceiptController {

    private final GoodsReceiptService service;

    public GoodsReceiptController(GoodsReceiptService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Goods receipt notes: text (GRN, supplier, delivery note), supplier, warehouse, dates")
    public PageResponse<GoodsReceiptResponse> list(@RequestParam(required = false) String search,
                                                   @RequestParam(required = false) Long supplierId,
                                                   @RequestParam(required = false) Long warehouseId,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.list(seller.id(), search, supplierId, warehouseId, from, to, page, size);
    }

    @GetMapping("/{id}")
    public GoodsReceiptResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.get(seller.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Receive goods without a purchase order (updates weighted average cost)")
    public GoodsReceiptResponse receive(@Valid @RequestBody ReceiveRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.receiveManual(seller.id(), seller.username(), request);
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Purchases report: the filtered goods receipts as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) Long supplierId,
                                         @RequestParam(required = false) Long warehouseId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        List<GoodsReceiptResponse> receipts = service.export(seller.id(), search, supplierId, warehouseId, from, to);
        BigDecimal total = receipts.stream().map(GoodsReceiptResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<List<Object>> lines = new ArrayList<>();
        receipts.forEach(r -> r.lines().forEach(l -> lines.add(List.of(ReportFiles.date(r.receivedAt()), r.number(),
                r.purchaseOrderNumber() == null ? "-" : r.purchaseOrderNumber(),
                r.supplierName() == null ? "-" : r.supplierName(), r.warehouseCode(), l.sku(), l.productName(),
                l.quantity(), l.unitCost(), l.lineTotal()))));
        Report report = new Report("Purchases (goods received) - " + seller.displayName(),
                (from == null ? "All dates" : from + " to " + (to == null ? LocalDate.now() : to)) + ", amounts in AED",
                List.of(new Report.Metric("Receipts", receipts.size()),
                        new Report.Metric("Units received", receipts.stream().mapToInt(GoodsReceiptResponse::totalUnits).sum()),
                        new Report.Metric("Purchase value", total)),
                List.of(new Report.Table("Received lines", List.of("Date", "GRN", "PO", "Supplier", "Warehouse", "SKU",
                        "Product", "Qty", "Unit cost", "Total"), lines)));
        return ReportFiles.download(report, format, "purchases-" + LocalDate.now());
    }
}
