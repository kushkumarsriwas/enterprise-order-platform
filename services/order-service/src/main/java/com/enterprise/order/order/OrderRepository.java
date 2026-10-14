package com.enterprise.order.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    List<Order> findByStatusAndPaymentInProgressFalseAndCreatedAtBefore(OrderStatus status, Instant cutoff, Limit limit);

    /** Orders and revenue per status (GROUP BY). */
    @Query("select o.status, count(o), coalesce(sum(o.totalAmount), 0) from Order o group by o.status")
    List<Object[]> countAndRevenueByStatus();

    /** Best sellers among non-cancelled orders (JOIN + GROUP BY + ORDER BY aggregate). */
    @Query("""
            select i.productId, i.productName, sum(i.quantity), sum(i.lineTotal)
            from OrderItem i join i.order o
            where o.status in :statuses
            group by i.productId, i.productName
            order by sum(i.quantity) desc""")
    List<Object[]> topProducts(List<OrderStatus> statuses, Limit limit);

    /** Daily order count and revenue since a date. */
    @Query("""
            select cast(o.createdAt as LocalDate), count(o), coalesce(sum(o.totalAmount), 0)
            from Order o
            where o.createdAt >= :since and o.status <> com.enterprise.order.order.OrderStatus.CANCELLED
            group by cast(o.createdAt as LocalDate)
            order by cast(o.createdAt as LocalDate)""")
    List<Object[]> dailySales(Instant since);
}
