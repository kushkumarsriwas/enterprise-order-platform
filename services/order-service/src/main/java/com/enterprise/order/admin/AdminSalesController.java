package com.enterprise.order.admin;

import com.enterprise.order.order.OrderFilter;
import com.enterprise.order.order.OrderQueryService;
import com.enterprise.order.reporting.ReportFiles;
import com.enterprise.order.seller.AnalyticsService;
import com.enterprise.order.seller.AnalyticsService.Analytics;
import com.enterprise.order.seller.AnalyticsService.SellerValue;
import com.enterprise.order.seller.CustomerInsightsService;
import com.enterprise.order.seller.CustomerInsightsService.CustomerPage;
import com.enterprise.order.seller.SellerAnalyticsController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Marketplace-wide sales intelligence for the platform administrator. */
@RestController
@RequestMapping("/api/admin/sales")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - sales analytics")
public class AdminSalesController {

    private final AnalyticsService analyticsService;
    private final CustomerInsightsService customerInsights;
    private final OrderQueryService queries;

    public AdminSalesController(AnalyticsService analyticsService, CustomerInsightsService customerInsights,
                                OrderQueryService queries) {
        this.analyticsService = analyticsService;
        this.customerInsights = customerInsights;
        this.queries = queries;
    }

    @GetMapping("/overview")
    @Operation(summary = "Gross merchandise value, orders, profit, growth, trend, categories, regions, top stores")
    public PlatformOverview overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "auto") String granularity) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return new PlatformOverview(analyticsService.analytics(null, start, end, granularity),
                analyticsService.topSellers(start, end, 10));
    }

    @GetMapping("/sellers/{sellerId}")
    @Operation(summary = "One store's sales analytics, exactly as the seller sees them")
    public Analytics seller(@PathVariable Long sellerId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue = "auto") String granularity) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return analyticsService.analytics(sellerId, start, end, granularity);
    }

    @GetMapping("/sellers/{sellerId}/customers")
    @Operation(summary = "Customers of one store")
    public CustomerPage sellerCustomers(@PathVariable Long sellerId, @RequestParam(defaultValue = "") String q,
                                        @RequestParam(required = false) String segment,
                                        @RequestParam(defaultValue = "spent") String sort,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return customerInsights.customers(sellerId, q, segment, sort, page, size);
    }

    @GetMapping("/report/{format}")
    @Operation(summary = "Marketplace sales report (xlsx or pdf)")
    public ResponseEntity<byte[]> report(@PathVariable String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        Analytics analytics = analyticsService.analytics(null, start, end, "auto");
        OrderFilter filter = new OrderFilter(null, null, AnalyticsService.SOLD, null, null, start, end, null, null,
                null, null);
        return ReportFiles.download(SellerAnalyticsController.salesReport("Nexora marketplace sales", analytics,
                queries.export(filter, "newest")), format, "nexora-sales-" + start + "-to-" + end);
    }

    public record PlatformOverview(Analytics analytics, List<SellerValue> topSellers) {
    }
}
