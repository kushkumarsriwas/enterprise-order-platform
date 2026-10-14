package com.enterprise.inventory.merchant;

import com.enterprise.inventory.catalog.CatalogDtos.MerchantProductResponse;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.reporting.ExcelReportWriter;
import com.enterprise.inventory.reporting.PdfReportWriter;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.stock.InventoryService;
import com.enterprise.inventory.stock.StockDtos.InventorySummary;
import com.enterprise.inventory.stock.StockDtos.PurchaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Downloadable inventory report: stock valuation, low stock and goods received. */
@RestController
@RequestMapping("/api/merchant/reports/inventory")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant - reports")
public class InventoryReportController {

    private final MerchantProductService productService;
    private final InventoryService inventoryService;

    public InventoryReportController(MerchantProductService productService, InventoryService inventoryService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{format}")
    @Operation(summary = "Inventory valuation report as xlsx or pdf")
    public ResponseEntity<byte[]> report(@PathVariable String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @AuthenticationPrincipal AuthenticatedUser merchant) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        List<MerchantProductResponse> products = productService.list(merchant, null, null);
        InventorySummary summary = inventoryService.summary(merchant);
        List<PurchaseResponse> purchases = inventoryService.purchases(merchant,
                start.atStartOfDay().toInstant(ZoneOffset.UTC), end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));

        Report report = new Report(
                "Inventory report - " + merchant.displayName(),
                "Stock on hand today; goods received " + start + " to " + end + " (amounts in AED)",
                List.of(new Report.Metric("Products", summary.products()),
                        new Report.Metric("Listed on marketplace", summary.listedProducts()),
                        new Report.Metric("Units available", summary.unitsOnHand()),
                        new Report.Metric("Stock value at cost", summary.stockValueAtCost()),
                        new Report.Metric("Stock value at selling price", summary.stockValueAtPrice()),
                        new Report.Metric("Low-stock products", summary.lowStockProducts())),
                List.of(new Report.Table("Stock valuation",
                                List.of("SKU", "Product", "Category", "Listed", "Available", "Reserved", "Reorder at",
                                        "Unit cost", "Price", "Margin %", "Value at cost"),
                                products.stream().map(p -> List.<Object>of(p.sku(), p.name(),
                                        p.categoryName() == null ? "-" : p.categoryName(), p.listed() ? "Yes" : "No",
                                        p.availableQuantity(), p.reservedQuantity(), p.reorderLevel(), p.costPrice(),
                                        p.price(), p.marginPercent(), p.stockValueAtCost())).toList()),
                        new Report.Table("Low stock",
                                List.of("SKU", "Product", "Available", "Reorder at"),
                                products.stream().filter(MerchantProductResponse::lowStock)
                                        .map(p -> List.<Object>of(p.sku(), p.name(), p.availableQuantity(), p.reorderLevel()))
                                        .toList()),
                        new Report.Table("Goods received",
                                List.of("Date", "SKU", "Product", "Supplier", "Reference", "Qty", "Unit cost", "Total"),
                                purchases.stream().map(p -> List.<Object>of(p.purchasedAt().toString().substring(0, 10),
                                        p.sku(), p.productName(), p.supplier() == null ? "-" : p.supplier(),
                                        p.reference() == null ? "-" : p.reference(), p.quantity(), p.unitCost(),
                                        p.totalCost())).toList())));
        return ReportResponses.file(report, format, "inventory-report-" + end);
    }

    /** Shared helper: renders a report in the requested format with a download header. */
    public static final class ReportResponses {

        private ReportResponses() {
        }

        public static ResponseEntity<byte[]> file(Report report, String format, String baseName) {
            boolean excel = "xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format);
            boolean pdf = "pdf".equalsIgnoreCase(format);
            if (!excel && !pdf) {
                throw ApiException.badRequest("Format must be xlsx or pdf");
            }
            byte[] body = excel ? ExcelReportWriter.write(report) : PdfReportWriter.write(report);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(excel ? ExcelReportWriter.CONTENT_TYPE : PdfReportWriter.CONTENT_TYPE))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(baseName + (excel ? ".xlsx" : ".pdf")).build().toString())
                    .body(body);
        }
    }
}
