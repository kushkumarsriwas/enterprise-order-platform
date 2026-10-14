package com.enterprise.order.stats;

import com.enterprise.order.order.OrderRepository;
import com.enterprise.order.order.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Marketplace-wide figures for the platform administrator (SQL aggregates). */
@RestController
@RequestMapping("/api/orders/stats")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Platform statistics (admin)")
public class StatsController {

    private static final List<OrderStatus> SOLD = List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    public StatsController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Orders by status, gross merchandise value, top merchants and the last 14 days")
    public Stats stats() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        BigDecimal gmv = BigDecimal.ZERO;
        long totalOrders = 0;
        for (Object[] row : orderRepository.countAndRevenueByStatus()) {
            OrderStatus status = (OrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            counts.put(status, count);
            totalOrders += count;
            if (SOLD.contains(status)) {
                gmv = gmv.add(toBigDecimal(row[2]));
            }
        }
        List<TopMerchant> merchants = new ArrayList<>();
        for (Object[] row : orderRepository.topMerchants(SOLD, Limit.of(10))) {
            merchants.add(new TopMerchant(((Number) row[0]).longValue(), (String) row[1], ((Number) row[2]).longValue(),
                    toBigDecimal(row[3])));
        }
        Instant since = LocalDate.now(ZoneOffset.UTC).minusDays(13).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<DailySales> daily = new ArrayList<>();
        for (Object[] row : orderRepository.dailySales(since, SOLD)) {
            daily.add(new DailySales(row[0].toString(), ((Number) row[1]).longValue(), toBigDecimal(row[2])));
        }
        return new Stats(totalOrders, gmv, "AED", counts, merchants, daily);
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    public record Stats(long totalOrders, BigDecimal grossMerchandiseValue, String currency,
                        Map<OrderStatus, Long> ordersByStatus, List<TopMerchant> topMerchants,
                        List<DailySales> last14Days) {
    }

    public record TopMerchant(Long merchantId, String merchantName, long orders, BigDecimal sales) {
    }

    public record DailySales(String date, long orders, BigDecimal sales) {
    }
}
