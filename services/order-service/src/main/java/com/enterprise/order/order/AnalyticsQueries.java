package com.enterprise.order.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Small read models used by the administrator's customer profile. */
@Service
@Transactional(readOnly = true)
public class AnalyticsQueries {

    private static final Set<OrderStatus> SOLD = Set.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED,
            OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    public AnalyticsQueries(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public CustomerPurchases customerPurchases(Long customerId) {
        List<Object[]> rows = orderRepository.customerSummary(customerId, SOLD);
        Object[] row = rows.isEmpty() ? new Object[]{0L, BigDecimal.ZERO, null, null} : rows.get(0);
        long orders = row[0] == null ? 0 : ((Number) row[0]).longValue();
        BigDecimal spent = row[1] == null ? BigDecimal.ZERO
                : new BigDecimal(row[1].toString()).setScale(2, RoundingMode.HALF_UP);
        long cancelled = orderRepository.count((root, query, cb) -> cb.and(cb.equal(root.get("userId"), customerId),
                cb.equal(root.get("status"), OrderStatus.CANCELLED)));
        return new CustomerPurchases(customerId, orders, cancelled, spent, (Instant) row[2], (Instant) row[3]);
    }

    public record CustomerPurchases(Long customerId, long orders, long cancelledOrders, BigDecimal spent,
                                    Instant firstOrderAt, Instant lastOrderAt) {
    }
}
