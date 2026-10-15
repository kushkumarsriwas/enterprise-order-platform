package com.enterprise.order.seller;

import com.enterprise.order.common.Like;
import com.enterprise.order.order.OrderRepository;
import com.enterprise.order.reporting.Report;
import com.enterprise.order.reporting.ReportFiles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The seller's customer list (CRM): who bought, how often, how much and how recently, with simple segments
 * (new, repeat, VIP, at risk) computed from the order history.
 */
@Service
@Transactional(readOnly = true)
public class CustomerInsightsService {

    static final int AT_RISK_DAYS = 90;
    static final BigDecimal VIP_SPEND = new BigDecimal("5000");

    private final OrderRepository orderRepository;

    public CustomerInsightsService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public CustomerPage customers(Long sellerId, String search, String segment, String sort, int page, int size) {
        List<CustomerRow> all = all(sellerId, search);
        long vip = all.stream().filter(c -> c.segment() == Segment.VIP).count();
        long repeat = all.stream().filter(c -> c.orders() > 1).count();
        long atRisk = all.stream().filter(c -> c.segment() == Segment.AT_RISK).count();
        List<CustomerRow> filtered = all.stream()
                .filter(c -> segment == null || segment.isBlank() || c.segment().name().equalsIgnoreCase(segment))
                .sorted(comparator(sort))
                .toList();
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min(Math.max(page, 0) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        int totalPages = (filtered.size() + safeSize - 1) / safeSize;
        return new CustomerPage(filtered.subList(from, to), Math.max(page, 0), safeSize, filtered.size(), totalPages,
                to >= filtered.size(), all.size(), repeat, vip, atRisk);
    }

    public Report export(Long sellerId, String storeName, String search, String segment, String sort) {
        List<CustomerRow> rows = all(sellerId, search).stream()
                .filter(c -> segment == null || segment.isBlank() || c.segment().name().equalsIgnoreCase(segment))
                .sorted(comparator(sort)).toList();
        BigDecimal spent = rows.stream().map(CustomerRow::spent).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Report("Customers - " + storeName,
                "Customers with at least one confirmed order" + (search == null || search.isBlank() ? ""
                        : ", search '" + search.trim() + "'") + (segment == null || segment.isBlank() ? ""
                        : ", segment " + segment.toUpperCase(Locale.ROOT)),
                List.of(new Report.Metric("Customers", rows.size()), new Report.Metric("Total spent", spent),
                        new Report.Metric("Repeat customers", rows.stream().filter(c -> c.orders() > 1).count())),
                List.of(new Report.Table("Customers",
                        List.of("Customer", "E-mail", "Segment", "Orders", "Spent", "Average order", "First order",
                                "Last order", "Days since last order"),
                        rows.stream().map(c -> List.<Object>of(c.username(), ReportFiles.orDash(c.email()),
                                c.segment().name(), c.orders(), c.spent(), c.averageOrderValue(),
                                ReportFiles.date(c.firstOrderAt()), ReportFiles.date(c.lastOrderAt()),
                                c.daysSinceLastOrder())).toList())));
    }

    private List<CustomerRow> all(Long sellerId, String search) {
        Instant now = Instant.now();
        List<CustomerRow> rows = new ArrayList<>();
        for (Object[] row : orderRepository.sellerCustomers(sellerId, AnalyticsService.SOLD, Like.contains(search))) {
            long orders = AnalyticsService.count(row[3]);
            BigDecimal spent = AnalyticsService.money(row[4]);
            Instant first = (Instant) row[5];
            Instant last = (Instant) row[6];
            long days = Duration.between(last, now).toDays();
            Segment segment = days > AT_RISK_DAYS ? Segment.AT_RISK
                    : orders >= 5 || spent.compareTo(VIP_SPEND) >= 0 ? Segment.VIP
                    : orders > 1 ? Segment.REPEAT : Segment.NEW;
            rows.add(new CustomerRow(AnalyticsService.count(row[0]), (String) row[1], (String) row[2], orders, spent,
                    spent.divide(BigDecimal.valueOf(Math.max(orders, 1)), 2, RoundingMode.HALF_UP), first, last, days,
                    segment));
        }
        return rows;
    }

    private static Comparator<CustomerRow> comparator(String sort) {
        Comparator<CustomerRow> bySpent = Comparator.comparing(CustomerRow::spent).reversed();
        if (sort == null) {
            return bySpent;
        }
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "orders" -> Comparator.comparingLong(CustomerRow::orders).reversed().thenComparing(bySpent);
            case "recent" -> Comparator.comparing(CustomerRow::lastOrderAt).reversed();
            case "name" -> Comparator.comparing(c -> c.username().toLowerCase(Locale.ROOT));
            default -> bySpent;
        };
    }

    public enum Segment {
        NEW, REPEAT, VIP, AT_RISK
    }

    public record CustomerRow(long customerId, String username, String email, long orders, BigDecimal spent,
                              BigDecimal averageOrderValue, Instant firstOrderAt, Instant lastOrderAt,
                              long daysSinceLastOrder, Segment segment) {
    }

    public record CustomerPage(List<CustomerRow> content, int page, int size, long totalElements, int totalPages,
                               boolean last, long customers, long repeatCustomers, long vipCustomers,
                               long atRiskCustomers) {
    }
}
