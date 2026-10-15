package com.enterprise.inventory.analytics;

import com.enterprise.inventory.analytics.AnalyticsDtos.InventoryAnalytics;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.reporting.ReportFiles;
import com.enterprise.inventory.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - inventory analytics and reports")
public class InventoryAnalyticsController {

    private final InventoryAnalyticsService service;

    public InventoryAnalyticsController(InventoryAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/api/seller/inventory/analytics")
    @Operation(summary = "Valuation, stock health, ABC, dead stock, turnover, sell-through, shrinkage, flow, purchases")
    public InventoryAnalytics analytics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "90") int deadStockDays,
                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        validate(start, end, deadStockDays);
        return service.analyse(seller.id(), start, end, deadStockDays);
    }

    /** report = valuation | abc | dead-stock | shrinkage | flow */
    @GetMapping("/api/seller/reports/inventory/{report}/{format}")
    @Operation(summary = "Inventory reports as xlsx or pdf: valuation, abc, dead-stock, shrinkage, flow")
    public ResponseEntity<byte[]> report(@PathVariable String report, @PathVariable String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "90") int deadStockDays,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        validate(start, end, deadStockDays);
        InventoryAnalytics a = service.analyse(seller.id(), start, end, deadStockDays);
        String store = seller.displayName();
        String period = start + " to " + end + ", amounts in AED";
        Report document = switch (report) {
            case "valuation" -> new Report("Inventory valuation - " + store, "Stock on hand on " + end + "; " + period,
                    List.of(new Report.Metric("Value at cost", a.stockValueAtCost()),
                            new Report.Metric("Value at selling price", a.stockValueAtPrice()),
                            new Report.Metric("Units on hand", a.unitsOnHand()),
                            new Report.Metric("Turnover (period)", a.turnoverRatio())),
                    List.of(table("By category", a.valueByCategory()), table("By warehouse", a.valueByWarehouse())));
            case "abc" -> new Report("ABC analysis - " + store, "Class A = first 80% of revenue, B = next 15%, C = rest; "
                    + period,
                    a.abcSummary().stream().map(c -> new Report.Metric("Class " + c.abcClass(), c.products() + " products, "
                            + c.revenueSharePercent() + "% of revenue")).toList(),
                    List.of(new Report.Table("Products", List.of("Class", "SKU", "Product", "Units sold", "Revenue",
                            "Gross profit", "On hand", "Stock value"),
                            a.abcProducts().stream().map(p -> List.<Object>of(p.abcClass(), p.sku(), p.name(), p.unitsSold(),
                                    p.revenue(), p.grossProfit(), p.onHand(), p.stockValueAtCost())).toList())));
            case "dead-stock" -> new Report("Dead stock - " + store, "On hand but not sold for " + deadStockDays + " days",
                    List.of(new Report.Metric("Products", a.deadStock().size()),
                            new Report.Metric("Value tied up", a.deadStockValue())),
                    List.of(new Report.Table("Dead stock", List.of("SKU", "Product", "On hand", "Value at cost",
                            "Last sold", "Days since sale"),
                            a.deadStock().stream().map(d -> List.<Object>of(d.sku(), d.name(), d.onHand(),
                                    d.stockValueAtCost(), d.lastSoldAt() == null ? "Never" : ReportFiles.date(d.lastSoldAt()),
                                    d.daysSinceLastSale() == null ? "-" : d.daysSinceLastSale())).toList())));
            case "shrinkage" -> new Report("Stock adjustments by reason - " + store, period,
                    List.of(new Report.Metric("Reasons", a.shrinkage().size())),
                    List.of(new Report.Table("Adjustments", List.of("Reason", "Units (+ found / - lost)", "Value at cost"),
                            a.shrinkage().stream().map(s -> List.<Object>of(s.reason(), s.units(), s.valueAtCost())).toList())));
            case "flow" -> new Report("Stock in and out per day - " + store, period,
                    List.of(new Report.Metric("Units in", a.flow().stream().mapToLong(f -> f.unitsIn()).sum()),
                            new Report.Metric("Units out", a.flow().stream().mapToLong(f -> f.unitsOut()).sum())),
                    List.of(new Report.Table("Daily flow", List.of("Date", "Units in", "Units out"),
                            a.flow().stream().map(f -> List.<Object>of(f.date(), f.unitsIn(), f.unitsOut())).toList())));
            default -> throw ApiException.badRequest("Unknown report '" + report
                    + "' (valuation, abc, dead-stock, shrinkage or flow)");
        };
        return ReportFiles.download(document, format, report + "-report-" + end);
    }

    private static Report.Table table(String title, List<AnalyticsDtos.ValueSlice> slices) {
        return new Report.Table(title, List.of(title.startsWith("By category") ? "Category" : "Warehouse", "Products",
                "Units", "Value at cost", "Share %"),
                slices.stream().map(s -> List.<Object>of(s.label(), s.products(), s.units(), s.valueAtCost(),
                        s.sharePercent())).toList());
    }

    private static void validate(LocalDate from, LocalDate to, int deadStockDays) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be before 'to'");
        }
        if (from.plusYears(2).isBefore(to)) {
            throw ApiException.badRequest("The period can be at most 2 years");
        }
        if (deadStockDays < 7 || deadStockDays > 730) {
            throw ApiException.badRequest("deadStockDays must be between 7 and 730");
        }
    }
}
