package com.enterprise.order.order;

import java.time.Instant;
import java.util.Collection;
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

    Page<Order> findByMerchantId(Long merchantId, Pageable pageable);

    Page<Order> findByMerchantIdAndStatus(Long merchantId, OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    List<Order> findByStatusAndPaymentInProgressFalseAndCreatedAtBefore(OrderStatus status, Instant cutoff, Limit limit);

    @EntityGraph(attributePaths = "items")
    List<Order> findByMerchantIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long merchantId, Instant from, Instant to);

    // ---------------------------------------------------------------- platform statistics (admin)

    @Query("select o.status, count(o), coalesce(sum(o.totalAmount), 0) from Order o group by o.status")
    List<Object[]> countAndRevenueByStatus();

    @Query("""
            select o.merchantId, o.merchantName, count(o), coalesce(sum(o.totalAmount), 0)
            from Order o where o.status in :statuses
            group by o.merchantId, o.merchantName
            order by coalesce(sum(o.totalAmount), 0) desc""")
    List<Object[]> topMerchants(Collection<OrderStatus> statuses, Limit limit);

    @Query("""
            select cast(o.createdAt as LocalDate), count(o), coalesce(sum(o.totalAmount), 0)
            from Order o
            where o.createdAt >= :since and o.status in :statuses
            group by cast(o.createdAt as LocalDate)
            order by cast(o.createdAt as LocalDate)""")
    List<Object[]> dailySales(Instant since, Collection<OrderStatus> statuses);

    // ---------------------------------------------------------------- merchant analytics (one merchant)

    /** Revenue, cost of goods sold and units per day (JOIN + GROUP BY + SUM over computed columns). */
    @Query("""
            select cast(o.createdAt as LocalDate), count(distinct o.id), sum(i.lineTotal), sum(i.unitCost * i.quantity),
                   sum(i.quantity)
            from OrderItem i join i.order o
            where o.merchantId = :merchantId and o.status in :statuses and o.createdAt >= :from and o.createdAt < :to
            group by cast(o.createdAt as LocalDate)
            order by cast(o.createdAt as LocalDate)""")
    List<Object[]> merchantDaily(Long merchantId, Collection<OrderStatus> statuses, Instant from, Instant to);

    @Query("""
            select i.productId, i.productName, sum(i.quantity), sum(i.lineTotal), sum(i.unitCost * i.quantity)
            from OrderItem i join i.order o
            where o.merchantId = :merchantId and o.status in :statuses and o.createdAt >= :from and o.createdAt < :to
            group by i.productId, i.productName
            order by sum(i.lineTotal) desc""")
    List<Object[]> merchantTopProducts(Long merchantId, Collection<OrderStatus> statuses, Instant from, Instant to,
                                       Limit limit);

    @Query("""
            select o.status, count(o), coalesce(sum(o.totalAmount), 0)
            from Order o
            where o.merchantId = :merchantId and o.createdAt >= :from and o.createdAt < :to
            group by o.status""")
    List<Object[]> merchantStatusBreakdown(Long merchantId, Instant from, Instant to);

    @Query("""
            select coalesce(o.paymentMethod, 'UNPAID'), count(o), coalesce(sum(o.totalAmount), 0)
            from Order o
            where o.merchantId = :merchantId and o.status in :statuses and o.createdAt >= :from and o.createdAt < :to
            group by coalesce(o.paymentMethod, 'UNPAID')""")
    List<Object[]> merchantPaymentMethods(Long merchantId, Collection<OrderStatus> statuses, Instant from, Instant to);

    @Query("""
            select o.userId, o.customerUsername, count(o), coalesce(sum(o.totalAmount), 0)
            from Order o
            where o.merchantId = :merchantId and o.status in :statuses and o.createdAt >= :from and o.createdAt < :to
            group by o.userId, o.customerUsername
            order by coalesce(sum(o.totalAmount), 0) desc""")
    List<Object[]> merchantTopCustomers(Long merchantId, Collection<OrderStatus> statuses, Instant from, Instant to,
                                        Limit limit);

    @Query("""
            select count(o) from Order o where o.merchantId = :merchantId and o.status = :status""")
    long countByMerchantAndStatus(Long merchantId, OrderStatus status);

    /** Has this customer received an order of this merchant containing the product? (verified-purchase reviews) */
    @Query("""
            select count(i) > 0 from OrderItem i join i.order o
            where o.id = :orderId and o.userId = :customerId and i.productId = :productId and o.status = :status""")
    boolean hasDeliveredItem(Long orderId, Long customerId, Long productId, OrderStatus status);
}
