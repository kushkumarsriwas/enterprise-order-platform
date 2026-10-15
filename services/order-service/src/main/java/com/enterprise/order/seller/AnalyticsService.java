package com.enterprise.order.seller;

import com.enterprise.order.common.ApiException;
import com.enterprise.order.order.Order;
import com.enterprise.order.order.OrderRepository;
import com.enterprise.order.order.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales analytics computed with SQL aggregates (GROUP BY / SUM over order lines). A sale counts once the order
 * is confirmed (paid or cash on delivery accepted); profit uses the unit cost recorded at the time of sale.
 * {@code sellerId == null} gives the whole marketplace (administrator).
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    public static final List<OrderStatus> SOLD = List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED,
            OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    public AnalyticsService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Analytics analytics(Long sellerId, LocalDate from, LocalDate to, String granularity) {
        validateRange(from, to);
        Granularity unit = Granularity.parse(granularity, from, to);
        Instant start = start(from);
        Instant end = end(to);

        Totals current = new Totals();
        Map<LocalDate, TrendPoint> buckets = emptyBuckets(from, to, unit);
        for (Object[] row : orderRepository.daily(sellerId, SOLD, start, end)) {
            LocalDate day = toDate(row[0]);
            long orders = count(row[1]);
            BigDecimal revenue = money(row[2]);
            BigDecimal cost = money(row[3]);
            long units = count(row[4]);
            current.add(orders, units, revenue, cost);
            LocalDate bucket = unit.bucketOf(day);
            TrendPoint point = buckets.get(bucket);
            if (point != null) {
                buckets.put(bucket, point.plus(orders, units, revenue, cost));
            }
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        Totals previous = new Totals();
        for (Object[] row : orderRepository.daily(sellerId, SOLD, start(previousFrom), end(previousTo))) {
            previous.add(count(row[1]), count(row[4]), money(row[2]), money(row[3]));
        }

        List<ProductPerformance> topProducts = new ArrayList<>();
        for (Object[] row : orderRepository.topProducts(sellerId, SOLD, start, end, Limit.of(10))) {
            BigDecimal revenue = money(row[3]);
            BigDecimal profit = revenue.subtract(money(row[4]));
            topProducts.add(new ProductPerformance(count(row[0]), (String) row[1], count(row[2]), revenue, profit,
                    percent(profit, revenue)));
        }
        List<CategoryPerformance> categories = new ArrayList<>();
        for (Object[] row : orderRepository.byCategory(sellerId, SOLD, start, end)) {
            BigDecimal revenue = money(row[2]);
            BigDecimal profit = revenue.subtract(money(row[3]));
            categories.add(new CategoryPerformance((String) row[0], count(row[1]), revenue, profit,
                    percent(revenue, current.revenue)));
        }
        List<RegionPerformance> regions = new ArrayList<>();
        for (Object[] row : orderRepository.byRegion(sellerId, SOLD, start, end)) {
            regions.add(new RegionPerformance((String) row[0], count(row[1]), money(row[2])));
        }
        Map<OrderStatus, StatusCount> byStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            byStatus.put(status, new StatusCount(0, BigDecimal.ZERO));
        }
        long allOrders = 0;
        for (Object[] row : orderRepository.statusBreakdown(sellerId, start, end)) {
            byStatus.put((OrderStatus) row[0], new StatusCount(count(row[1]), money(row[2])));
            allOrders += count(row[1]);
        }
        Map<String, StatusCount> paymentMethods = new LinkedHashMap<>();
        for (Object[] row : orderRepository.paymentMethods(sellerId, SOLD, start, end)) {
            paymentMethods.put((String) row[0], new StatusCount(count(row[1]), money(row[2])));
        }
        List<CustomerValue> topCustomers = new ArrayList<>();
        for (Object[] row : orderRepository.topCustomers(sellerId, SOLD, start, end, Limit.of(5))) {
            topCustomers.add(new CustomerValue(count(row[0]), (String) row[1], count(row[2]), money(row[3])));
        }
        long customers = 0;
        long repeatCustomers = 0;
        for (Object[] row : orderRepository.ordersPerCustomer(sellerId, SOLD, start, end)) {
            customers++;
            if (count(row[1]) > 1) {
                repeatCustomers++;
            }
        }
        long dispatched = 0;
        long dispatchMinutes = 0;
        for (Order order : orderRepository.shippedInPeriod(sellerId, start, end)) {
            Instant confirmed = order.getConfirmedAt();
            if (confirmed != null && order.getShippedAt() != null) {
                dispatched++;
                dispatchMinutes += Math.max(0, Duration.between(confirmed, order.getShippedAt()).toMinutes());
            }
        }
        Kpis now = current.kpis();
        Kpis before = previous.kpis();
        return new Analytics(from.toString(), to.toString(), unit.name().toLowerCase(Locale.ROOT), "AED",
                now.orders(), now.unitsSold(), now.revenue(), now.costOfGoodsSold(), now.grossProfit(),
                now.grossMarginPercent(), now.averageOrderValue(), vatIncluded(now.revenue()),
                before, new Growth(growth(now.revenue(), before.revenue()),
                        growth(BigDecimal.valueOf(now.orders()), BigDecimal.valueOf(before.orders())),
                        growth(now.grossProfit(), before.grossProfit()),
                        growth(now.averageOrderValue(), before.averageOrderValue())),
                sellerId == null ? orderRepository.countByStatus(OrderStatus.CONFIRMED)
                        : orderRepository.countBySellerAndStatus(sellerId, OrderStatus.CONFIRMED),
                sellerId == null ? orderRepository.countByStatus(OrderStatus.PENDING)
                        : orderRepository.countBySellerAndStatus(sellerId, OrderStatus.PENDING),
                byStatus, paymentMethods, new ArrayList<>(buckets.values()), topProducts, categories, regions,
                topCustomers, customers, percent(BigDecimal.valueOf(repeatCustomers), BigDecimal.valueOf(customers)),
                percent(BigDecimal.valueOf(byStatus.get(OrderStatus.CANCELLED).orders()), BigDecimal.valueOf(allOrders)),
                dispatched == 0 ? null : BigDecimal.valueOf(dispatchMinutes)
                        .divide(BigDecimal.valueOf(dispatched * 60), 1, RoundingMode.HALF_UP));
    }

    /** Best sellers of the marketplace (administrator). */
    public List<SellerValue> topSellers(LocalDate from, LocalDate to, int limit) {
        validateRange(from, to);
        List<SellerValue> sellers = new ArrayList<>();
        for (Object[] row : orderRepository.topSellers(SOLD, start(from), end(to), Limit.of(limit))) {
            sellers.add(new SellerValue(count(row[0]), (String) row[1], count(row[2]), money(row[3])));
        }
        return sellers;
    }

    /** Revenue and cost of goods per month, used by the finance service to build the profit &amp; loss statement. */
    public SalesSummary salesSummary(Long sellerId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<String, MonthAmounts> months = new LinkedHashMap<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            months.put(month.toString(), new MonthAmounts(month.toString(), BigDecimal.ZERO, BigDecimal.ZERO, 0));
        }
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        long orders = 0;
        for (Object[] row : orderRepository.daily(sellerId, SOLD, start(from), end(to))) {
            String month = YearMonth.from(toDate(row[0])).toString();
            MonthAmounts current = months.getOrDefault(month, new MonthAmounts(month, BigDecimal.ZERO, BigDecimal.ZERO, 0));
            months.put(month, new MonthAmounts(month, current.revenue().add(money(row[2])),
                    current.costOfGoodsSold().add(money(row[3])), current.orders() + count(row[1])));
            revenue = revenue.add(money(row[2]));
            cogs = cogs.add(money(row[3]));
            orders += count(row[1]);
        }
        return new SalesSummary(revenue, cogs, revenue.subtract(cogs), orders, new ArrayList<>(months.values()));
    }

    // ------------------------------------------------------------------ helpers

    public static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be before 'to'");
        }
        if (from.plusYears(2).isBefore(to)) {
            throw ApiException.badRequest("The period can be at most 2 years");
        }
    }

    public static Instant start(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public static Instant end(LocalDate date) {
        return date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Map<LocalDate, TrendPoint> emptyBuckets(LocalDate from, LocalDate to, Granularity unit) {
        Map<LocalDate, TrendPoint> buckets = new LinkedHashMap<>();
        for (LocalDate bucket = unit.bucketOf(from); !bucket.isAfter(to); bucket = unit.next(bucket)) {
            buckets.put(bucket, new TrendPoint(bucket.toString(), unit.label(bucket), 0, 0, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return buckets;
    }

    private static LocalDate toDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }

    static long count(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO
                : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    /** Change against the previous period in percent; null when there was nothing to compare with. */
    static BigDecimal growth(BigDecimal now, BigDecimal before) {
        if (before == null || before.signum() == 0) {
            return null;
        }
        return now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before.abs(), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal vatIncluded(BigDecimal gross) {
        return gross.multiply(BigDecimal.valueOf(5)).divide(BigDecimal.valueOf(105), 2, RoundingMode.HALF_UP);
    }

    private static final class Totals {
        long orders;
        long units;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;

        void add(long orders, long units, BigDecimal revenue, BigDecimal cost) {
            this.orders += orders;
            this.units += units;
            this.revenue = this.revenue.add(revenue);
            this.cost = this.cost.add(cost);
        }

        Kpis kpis() {
            BigDecimal profit = revenue.subtract(cost);
            return new Kpis(orders, units, revenue, cost, profit, percent(profit, revenue),
                    orders == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP));
        }
    }

    /** Chart resolution; "auto" picks days up to 2 months, weeks up to 6 months, then months. */
    enum Granularity {
        DAY, WEEK, MONTH;

        static Granularity parse(String value, LocalDate from, LocalDate to) {
            if (value != null && !value.isBlank() && !"auto".equalsIgnoreCase(value)) {
                try {
                    return valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw ApiException.badRequest("granularity must be day, week, month or auto");
                }
            }
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            return days <= 62 ? DAY : days <= 186 ? WEEK : MONTH;
        }

        LocalDate bucketOf(LocalDate day) {
            return switch (this) {
                case DAY -> day;
                case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTH -> day.withDayOfMonth(1);
            };
        }

        LocalDate next(LocalDate bucket) {
            return switch (this) {
                case DAY -> bucket.plusDays(1);
                case WEEK -> bucket.plusWeeks(1);
                case MONTH -> bucket.plusMonths(1);
            };
        }

        String label(LocalDate bucket) {
            return switch (this) {
                case DAY -> bucket.getDayOfMonth() + " " + bucket.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.US);
                case WEEK -> "Wk of " + bucket.getDayOfMonth() + " "
                        + bucket.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.US);
                case MONTH -> bucket.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.US) + " "
                        + bucket.getYear();
            };
        }
    }

    // ------------------------------------------------------------------ response types

    public record Analytics(String from, String to, String granularity, String currency, long orders, long unitsSold,
                            BigDecimal revenue, BigDecimal costOfGoodsSold, BigDecimal grossProfit,
                            BigDecimal grossMarginPercent, BigDecimal averageOrderValue, BigDecimal vatCollected,
                            Kpis previousPeriod, Growth growth, long ordersToShip, long ordersAwaitingPayment,
                            Map<OrderStatus, StatusCount> ordersByStatus, Map<String, StatusCount> paymentMethods,
                            List<TrendPoint> trend, List<ProductPerformance> topProducts,
                            List<CategoryPerformance> categories, List<RegionPerformance> regions,
                            List<CustomerValue> topCustomers, long customers, BigDecimal repeatCustomerRatePercent,
                            BigDecimal cancellationRatePercent, BigDecimal averageDispatchHours) {
    }

    public record Kpis(long orders, long unitsSold, BigDecimal revenue, BigDecimal costOfGoodsSold,
                       BigDecimal grossProfit, BigDecimal grossMarginPercent, BigDecimal averageOrderValue) {
    }

    /** Percent change against the previous period of the same length (null = no previous sales). */
    public record Growth(BigDecimal revenuePercent, BigDecimal ordersPercent, BigDecimal grossProfitPercent,
                         BigDecimal averageOrderValuePercent) {
    }

    public record StatusCount(long orders, BigDecimal amount) {
    }

    public record TrendPoint(String date, String label, long orders, long units, BigDecimal revenue, BigDecimal cost,
                             BigDecimal profit) {

        TrendPoint plus(long moreOrders, long moreUnits, BigDecimal moreRevenue, BigDecimal moreCost) {
            BigDecimal newRevenue = revenue.add(moreRevenue);
            BigDecimal newCost = cost.add(moreCost);
            return new TrendPoint(date, label, orders + moreOrders, units + moreUnits, newRevenue, newCost,
                    newRevenue.subtract(newCost));
        }
    }

    public record ProductPerformance(long productId, String productName, long unitsSold, BigDecimal revenue,
                                     BigDecimal grossProfit, BigDecimal marginPercent) {
    }

    public record CategoryPerformance(String category, long unitsSold, BigDecimal revenue, BigDecimal grossProfit,
                                      BigDecimal sharePercent) {
    }

    public record RegionPerformance(String region, long orders, BigDecimal revenue) {
    }

    public record CustomerValue(long customerId, String customerUsername, long orders, BigDecimal spent) {
    }

    public record SellerValue(long sellerId, String sellerName, long orders, BigDecimal sales) {
    }

    public record SalesSummary(BigDecimal revenue, BigDecimal costOfGoodsSold, BigDecimal grossProfit, long orders,
                               List<MonthAmounts> months) {
    }

    public record MonthAmounts(String month, BigDecimal revenue, BigDecimal costOfGoodsSold, long orders) {
    }
}
