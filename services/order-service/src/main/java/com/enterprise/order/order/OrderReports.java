package com.enterprise.order.order;

import com.enterprise.order.order.OrderDtos.SellerOrderResponse;
import com.enterprise.order.reporting.Report;
import com.enterprise.order.reporting.ReportFiles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Excel/PDF exports of filtered order lists (seller order desk and administrator order explorer). */
public final class OrderReports {

    private OrderReports() {
    }

    public static Report orders(String title, OrderFilter filter, List<SellerOrderResponse> orders,
                                boolean includeStore) {
        BigDecimal value = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        long units = 0;
        long cancelled = 0;
        List<List<Object>> rows = new ArrayList<>();
        for (SellerOrderResponse o : orders) {
            boolean isCancelled = "CANCELLED".equals(o.status());
            if (isCancelled) {
                cancelled++;
            } else {
                value = value.add(o.totalAmount());
                profit = profit.add(o.grossProfit());
                units += o.units();
            }
            List<Object> row = new ArrayList<>(List.of(ReportFiles.dateTime(o.createdAt()), o.orderNumber()));
            if (includeStore) {
                row.add(o.sellerName());
            }
            row.addAll(List.of(o.customerUsername(), ReportFiles.orDash(o.recipientName()),
                    ReportFiles.orDash(o.shippingCity()), o.status(),
                    o.paymentMethod() == null ? o.paymentState() : o.paymentMethod() + " / " + o.paymentState(),
                    o.units(), o.totalAmount(), o.vatAmount(), o.totalCost(), o.grossProfit(),
                    ReportFiles.orDash(o.carrier()), ReportFiles.orDash(o.trackingNumber())));
            rows.add(row);
        }
        List<String> headers = new ArrayList<>(List.of("Date (UTC)", "Order"));
        if (includeStore) {
            headers.add("Store");
        }
        headers.addAll(List.of("Customer", "Recipient", "City", "Status", "Payment", "Units", "Total", "VAT",
                "Cost", "Profit", "Carrier", "Tracking"));
        return new Report(title, describe(filter) + (orders.size() >= OrderQueryService.EXPORT_LIMIT
                ? " (first " + OrderQueryService.EXPORT_LIMIT + " orders)" : ""),
                List.of(new Report.Metric("Orders", orders.size()),
                        new Report.Metric("Order value (excl. cancelled)", value),
                        new Report.Metric("Gross profit", profit),
                        new Report.Metric("Units", units),
                        new Report.Metric("Cancelled orders", cancelled)),
                List.of(new Report.Table("Orders", headers, rows)));
    }

    static String describe(OrderFilter filter) {
        List<String> parts = new ArrayList<>();
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            parts.add("Status " + filter.statuses().stream().map(Enum::name).collect(Collectors.joining("/")));
        }
        if (filter.from() != null || filter.to() != null) {
            parts.add("Period " + (filter.from() == null ? "start" : filter.from()) + " to "
                    + (filter.to() == null ? "today" : filter.to()));
        }
        if (filter.paymentMethod() != null && !filter.paymentMethod().isBlank()) {
            parts.add("Payment " + filter.paymentMethod());
        }
        if (filter.paymentState() != null) {
            parts.add("Payment state " + filter.paymentState());
        }
        if (filter.minTotal() != null || filter.maxTotal() != null) {
            parts.add("Total " + (filter.minTotal() == null ? "0" : filter.minTotal()) + " - "
                    + (filter.maxTotal() == null ? "any" : filter.maxTotal()));
        }
        if (filter.region() != null && !filter.region().isBlank()) {
            parts.add("Region " + filter.region());
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            parts.add("Search '" + filter.query().trim() + "'");
        }
        return parts.isEmpty() ? "All orders (amounts in AED, prices include 5% VAT)"
                : String.join(", ", parts) + " (amounts in AED)";
    }
}
