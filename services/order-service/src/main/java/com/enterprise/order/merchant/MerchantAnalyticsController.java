package com.enterprise.order.merchant;

import com.enterprise.order.common.ApiException;
import com.enterprise.order.merchant.AnalyticsService.Analytics;
import com.enterprise.order.merchant.AnalyticsService.SalesSummary;
import com.enterprise.order.order.OrderDtos.MerchantOrderResponse;
import com.enterprise.order.order.OrderMapper;
import com.enterprise.order.order.OrderRepository;
import com.enterprise.order.reporting.ExcelReportWriter;
import com.enterprise.order.reporting.PdfReportWriter;
import com.enterprise.order.reporting.Report;
import com.enterprise.order.security.AuthenticatedUser;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Merchant - analytics and reports")
public class MerchantAnalyticsController {

    private final AnalyticsService analyticsService;
    private final OrderRepository orderRepository;

    public MerchantAnalyticsController(AnalyticsService analyticsService, OrderRepository orderRepository) {
        this.analyticsService = analyticsService;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/api/merchant/analytics")
    @PreAuthorize("hasRole('MERCHANT')")
    @Operation(summary = "Revenue, cost of goods, gross profit, trends, best sellers and best customers")
    public Analytics analytics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @AuthenticationPrincipal AuthenticatedUser merchant) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        validateRange(start, end);
        return analyticsService.analytics(merchant.id(), start, end);
    }

    @GetMapping("/api/merchant/reports/sales/{format}")
    @PreAuthorize("hasRole('MERCHANT')")
    @Transactional(readOnly = true)
    @Operation(summary = "Sales report (orders, best sellers, daily sales) as xlsx or pdf")
    public ResponseEntity<byte[]> salesReport(@PathVariable String format,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @AuthenticationPrincipal AuthenticatedUser merchant) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        validateRange(start, end);
        Analytics a = analyticsService.analytics(merchant.id(), start, end);
        List<MerchantOrderResponse> orders = orderRepository.findByMerchantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                merchant.id(), AnalyticsService.start(start), AnalyticsService.end(end)).stream()
                .map(OrderMapper::toMerchantResponse).toList();
        Report report = new Report(
                "Sales report - " + merchant.displayName(),
                "Period " + start + " to " + end + " (amounts in AED; sales = confirmed, shipped and delivered orders)",
                List.of(new Report.Metric("Revenue", a.revenue()),
                        new Report.Metric("Cost of goods sold", a.costOfGoodsSold()),
                        new Report.Metric("Gross profit", a.grossProfit()),
                        new Report.Metric("Gross margin %", a.grossMarginPercent()),
                        new Report.Metric("Orders sold", a.orders()),
                        new Report.Metric("Average order value", a.averageOrderValue())),
                List.of(new Report.Table("Orders",
                                List.of("Date", "Order", "Customer", "Status", "Payment", "Items", "Total", "Cost", "Profit"),
                                orders.stream().map(o -> List.<Object>of(o.createdAt().toString().substring(0, 10),
                                        o.orderNumber(), o.customerUsername(), o.status(),
                                        o.paymentMethod() == null ? o.paymentState() : o.paymentMethod(),
                                        o.items().stream().mapToInt(i -> i.quantity()).sum(), o.totalAmount(),
                                        o.totalCost(), o.grossProfit())).toList()),
                        new Report.Table("Best sellers",
                                List.of("Product", "Units", "Revenue", "Gross profit", "Margin %"),
                                a.topProducts().stream().map(p -> List.<Object>of(p.productName(), p.unitsSold(),
                                        p.revenue(), p.grossProfit(), p.marginPercent())).toList()),
                        new Report.Table("Daily sales",
                                List.of("Date", "Orders", "Revenue", "Cost", "Profit"),
                                a.daily().stream().map(d -> List.<Object>of(d.date(), d.orders(), d.revenue(), d.cost(),
                                        d.profit())).toList())));
        boolean excel = "xlsx".equalsIgnoreCase(format);
        if (!excel && !"pdf".equalsIgnoreCase(format)) {
            throw ApiException.badRequest("Format must be xlsx or pdf");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(excel ? ExcelReportWriter.CONTENT_TYPE : PdfReportWriter.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("sales-report-" + start + "-to-" + end + (excel ? ".xlsx" : ".pdf")).build().toString())
                .body(excel ? ExcelReportWriter.write(report) : PdfReportWriter.write(report));
    }

    /** Used by the payment/finance service to build the profit &amp; loss statement. Not routed by the gateway. */
    @GetMapping("/internal/merchants/{merchantId}/sales-summary")
    @Operation(summary = "Internal: revenue and cost of goods per month")
    public SalesSummary salesSummary(@PathVariable Long merchantId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        if (!caller.isAdmin() && !caller.id().equals(merchantId)) {
            throw ApiException.forbidden("Sales figures are only available to the merchant");
        }
        validateRange(from, to);
        return analyticsService.salesSummary(merchantId, from, to);
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be before 'to'");
        }
        if (from.plusYears(2).isBefore(to)) {
            throw ApiException.badRequest("The period can be at most 2 years");
        }
    }
}
