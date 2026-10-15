package com.enterprise.order.seller;

import com.enterprise.order.common.ApiException;
import com.enterprise.order.order.OrderFilter;
import com.enterprise.order.order.OrderQueryService;
import com.enterprise.order.order.OrderDtos.SellerOrderResponse;
import com.enterprise.order.reporting.Report;
import com.enterprise.order.reporting.ReportFiles;
import com.enterprise.order.security.AuthenticatedUser;
import com.enterprise.order.seller.AnalyticsService.Analytics;
import com.enterprise.order.seller.AnalyticsService.SalesSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
@Tag(name = "Seller - sales analytics and reports")
public class SellerAnalyticsController {

    private final AnalyticsService analyticsService;
    private final OrderQueryService queries;

    public SellerAnalyticsController(AnalyticsService analyticsService, OrderQueryService queries) {
        this.analyticsService = analyticsService;
        this.queries = queries;
    }

    @GetMapping("/api/seller/analytics")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Revenue, cost of goods, gross profit, growth vs previous period, trend (day/week/month), "
            + "best sellers, categories, regions, customers, dispatch time and cancellation rate")
    public Analytics analytics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(defaultValue = "auto") String granularity,
                               @AuthenticationPrincipal AuthenticatedUser seller) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return analyticsService.analytics(seller.id(), start, end, granularity);
    }

    @GetMapping("/api/seller/reports/sales/{format}")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Sales report (KPIs, orders, best sellers, categories, regions, trend) as xlsx or pdf")
    public ResponseEntity<byte[]> salesReport(@PathVariable String format,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @AuthenticationPrincipal AuthenticatedUser seller) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        Analytics a = analyticsService.analytics(seller.id(), start, end, "auto");
        OrderFilter filter = new OrderFilter(seller.id(), null, AnalyticsService.SOLD, null, null, start, end, null,
                null, null, null);
        return ReportFiles.download(salesReport("Sales report - " + seller.displayName(), a,
                queries.export(filter, "newest")), format, "sales-report-" + start + "-to-" + end);
    }

    /** Used by the finance (payment) service to build the profit &amp; loss statement. Not routed by the gateway. */
    @GetMapping("/internal/sellers/{sellerId}/sales-summary")
    @Operation(summary = "Internal: revenue and cost of goods per month")
    public SalesSummary salesSummary(@PathVariable Long sellerId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        if (!caller.isService() && !caller.isAdmin() && !caller.id().equals(sellerId)) {
            throw ApiException.forbidden("Sales figures are only available to the seller");
        }
        return analyticsService.salesSummary(sellerId, from, to);
    }

    /** Shared with the administrator's platform report. */
    public static Report salesReport(String title, Analytics a, List<SellerOrderResponse> orders) {
        List<Report.Metric> metrics = new ArrayList<>(List.of(
                new Report.Metric("Revenue (incl. VAT)", a.revenue()),
                new Report.Metric("Cost of goods sold", a.costOfGoodsSold()),
                new Report.Metric("Gross profit", a.grossProfit()),
                new Report.Metric("Gross margin %", a.grossMarginPercent()),
                new Report.Metric("Orders", a.orders()),
                new Report.Metric("Average order value", a.averageOrderValue()),
                new Report.Metric("Units sold", a.unitsSold()),
                new Report.Metric("VAT included in sales", a.vatCollected()),
                new Report.Metric("Cancellation rate %", a.cancellationRatePercent())));
        return new Report(title,
                "Period " + a.from() + " to " + a.to() + " (AED; sales = confirmed, shipped and delivered orders)",
                metrics,
                List.of(new Report.Table("Orders",
                                List.of("Date (UTC)", "Order", "Store", "Customer", "City", "Status", "Payment", "Units",
                                        "Total", "Cost", "Profit"),
                                orders.stream().map(o -> List.<Object>of(ReportFiles.dateTime(o.createdAt()),
                                        o.orderNumber(), o.sellerName(), o.customerUsername(),
                                        ReportFiles.orDash(o.shippingCity()), o.status(),
                                        o.paymentMethod() == null ? o.paymentState() : o.paymentMethod(), o.units(),
                                        o.totalAmount(), o.totalCost(), o.grossProfit())).toList()),
                        new Report.Table("Best sellers",
                                List.of("Product", "Units", "Revenue", "Gross profit", "Margin %"),
                                a.topProducts().stream().map(p -> List.<Object>of(p.productName(), p.unitsSold(),
                                        p.revenue(), p.grossProfit(), p.marginPercent())).toList()),
                        new Report.Table("Categories",
                                List.of("Category", "Units", "Revenue", "Gross profit", "Share %"),
                                a.categories().stream().map(c -> List.<Object>of(c.category(), c.unitsSold(),
                                        c.revenue(), c.grossProfit(), c.sharePercent())).toList()),
                        new Report.Table("Regions",
                                List.of("Emirate / city", "Orders", "Revenue"),
                                a.regions().stream().map(r -> List.<Object>of(r.region(), r.orders(), r.revenue()))
                                        .toList()),
                        new Report.Table("Sales trend (" + a.granularity() + ")",
                                List.of("Period", "Orders", "Units", "Revenue", "Cost", "Profit"),
                                a.trend().stream().map(d -> List.<Object>of(d.date(), d.orders(), d.units(),
                                        d.revenue(), d.cost(), d.profit())).toList())));
    }
}
