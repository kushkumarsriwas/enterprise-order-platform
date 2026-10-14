package com.enterprise.order.merchant;

import com.enterprise.order.order.OrderRepository;
import com.enterprise.order.order.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales analytics of one merchant, computed with SQL aggregates. A sale counts once the order is confirmed
 * (paid or cash on delivery); profit uses the unit cost recorded at the time of sale.
 */
@Service
public class AnalyticsService {

    static final List<OrderStatus> SOLD = List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    public AnalyticsService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Analytics analytics(Long merchantId, LocalDate from, LocalDate to) {
        Instant start = start(from);
        Instant end = end(to);
        List<DailyPoint> daily = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        long orders = 0;
        long units = 0;
        for (Object[] row : orderRepository.merchantDaily(merchantId, SOLD, start, end)) {
            BigDecimal dayRevenue = money(row[2]);
            BigDecimal dayCost = money(row[3]);
            daily.add(new DailyPoint(row[0].toString(), count(row[1]), dayRevenue, dayCost, dayRevenue.subtract(dayCost)));
            orders += count(row[1]);
            revenue = revenue.add(dayRevenue);
            cogs = cogs.add(dayCost);
            units += count(row[4]);
        }
        List<ProductPerformance> topProducts = new ArrayList<>();
        for (Object[] row : orderRepository.merchantTopProducts(merchantId, SOLD, start, end, Limit.of(10))) {
            BigDecimal productRevenue = money(row[3]);
            BigDecimal productCost = money(row[4]);
            topProducts.add(new ProductPerformance(count(row[0]), (String) row[1], count(row[2]), productRevenue,
                    productRevenue.subtract(productCost), percent(productRevenue.subtract(productCost), productRevenue)));
        }
        Map<OrderStatus, StatusCount> byStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            byStatus.put(status, new StatusCount(0, BigDecimal.ZERO));
        }
        for (Object[] row : orderRepository.merchantStatusBreakdown(merchantId, start, end)) {
            byStatus.put((OrderStatus) row[0], new StatusCount(count(row[1]), money(row[2])));
        }
        Map<String, StatusCount> paymentMethods = new LinkedHashMap<>();
        for (Object[] row : orderRepository.merchantPaymentMethods(merchantId, SOLD, start, end)) {
            paymentMethods.put((String) row[0], new StatusCount(count(row[1]), money(row[2])));
        }
        List<CustomerValue> topCustomers = new ArrayList<>();
        for (Object[] row : orderRepository.merchantTopCustomers(merchantId, SOLD, start, end, Limit.of(5))) {
            topCustomers.add(new CustomerValue(count(row[0]), (String) row[1], count(row[2]), money(row[3])));
        }
        BigDecimal grossProfit = revenue.subtract(cogs);
        return new Analytics(from.toString(), to.toString(), "AED", orders, units, revenue, cogs, grossProfit,
                percent(grossProfit, revenue),
                orders == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP),
                orderRepository.countByMerchantAndStatus(merchantId, OrderStatus.CONFIRMED),
                orderRepository.countByMerchantAndStatus(merchantId, OrderStatus.PENDING),
                byStatus, paymentMethods, daily, topProducts, topCustomers);
    }

    /** Revenue and cost of goods per month, used by the finance service to build the profit & loss statement. */
    @Transactional(readOnly = true)
    public SalesSummary salesSummary(Long merchantId, LocalDate from, LocalDate to) {
        Map<String, MonthAmounts> months = new LinkedHashMap<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            months.put(month.toString(), new MonthAmounts(month.toString(), BigDecimal.ZERO, BigDecimal.ZERO, 0));
        }
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        long orders = 0;
        for (Object[] row : orderRepository.merchantDaily(merchantId, SOLD, start(from), end(to))) {
            String month = row[0].toString().substring(0, 7);
            MonthAmounts current = months.getOrDefault(month, new MonthAmounts(month, BigDecimal.ZERO, BigDecimal.ZERO, 0));
            months.put(month, new MonthAmounts(month, current.revenue().add(money(row[2])),
                    current.costOfGoodsSold().add(money(row[3])), current.orders() + count(row[1])));
            revenue = revenue.add(money(row[2]));
            cogs = cogs.add(money(row[3]));
            orders += count(row[1]);
        }
        return new SalesSummary(revenue, cogs, revenue.subtract(cogs), orders, new ArrayList<>(months.values()));
    }

    static Instant start(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    static Instant end(LocalDate date) {
        return date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }

    private static long count(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO
                : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    public record Analytics(String from, String to, String currency, long orders, long unitsSold, BigDecimal revenue,
                            BigDecimal costOfGoodsSold, BigDecimal grossProfit, BigDecimal grossMarginPercent,
                            BigDecimal averageOrderValue, long ordersToShip, long ordersAwaitingPayment,
                            Map<OrderStatus, StatusCount> ordersByStatus, Map<String, StatusCount> paymentMethods,
                            List<DailyPoint> daily, List<ProductPerformance> topProducts,
                            List<CustomerValue> topCustomers) {
    }

    public record StatusCount(long orders, BigDecimal amount) {
    }

    public record DailyPoint(String date, long orders, BigDecimal revenue, BigDecimal cost, BigDecimal profit) {
    }

    public record ProductPerformance(long productId, String productName, long unitsSold, BigDecimal revenue,
                                     BigDecimal grossProfit, BigDecimal marginPercent) {
    }

    public record CustomerValue(long customerId, String customerUsername, long orders, BigDecimal spent) {
    }

    public record SalesSummary(BigDecimal revenue, BigDecimal costOfGoodsSold, BigDecimal grossProfit, long orders,
                               List<MonthAmounts> months) {
    }

    public record MonthAmounts(String month, BigDecimal revenue, BigDecimal costOfGoodsSold, long orders) {
    }
}
