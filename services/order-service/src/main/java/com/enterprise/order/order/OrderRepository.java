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
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Orders and the SQL aggregates behind seller and platform analytics. In the analytics queries a null
 * {@code sellerId} means "all sellers" (the administrator's platform view).
 */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    /** Unpaid orders past the payment window (a payment attempt that never reported back counts as finished). */
    @Query("""
            select o from Order o
            where o.status = :status and o.createdAt < :cutoff
              and (o.paymentInProgress = false or o.paymentStartedAt is null or o.paymentStartedAt < :staleBefore)
            order by o.createdAt""")
    List<Order> findExpirable(OrderStatus status, Instant cutoff, Instant staleBefore, Limit limit);

    @Query("""
            select cast(o.createdAt as LocalDate), count(distinct o.id), sum(i.lineTotal), sum(i.unitCost * i.quantity),
                   sum(i.quantity)
            from OrderItem i join i.order o
            where (:sellerId is null or o.sellerId = :sellerId) and o.status in :statuses
              and o.createdAt >= :from and o.createdAt < :to
            group by cast(o.createdAt as LocalDate)
            order by cast(o.createdAt as LocalDate)""")
    List<Object[]> daily(Long sellerId, Collection<OrderStatus> statuses, Instant from, Instant to);

    @Query("""
            select i.productId, i.productName, sum(i.quantity), sum(i.lineTotal), sum(i.unitCost * i.quantity)
            from OrderItem i join i.order o
            where (:sellerId is null or o.sellerId = :sellerId) and o.status in :statuses
              and o.createdAt >= :from and o.createdAt < :to
            group by i.productId, i.productName
            order by sum(i.lineTotal) desc""")
    List<Object[]> topProducts(Long sellerId, Collection<OrderStatus> statuses, Instant from, Instant to, Limit limit);

    @Query("""
            select coalesce(i.categoryName, 'Uncategorised'), sum(i.quantity), sum(i.lineTotal),
                   sum(i.unitCost * i.quantity)
            from OrderItem i join i.order o
            where (:sellerId is null or o.sellerId = :sellerId) and o.status in :statuses
              and o.createdAt >= :from and o.createdAt < :to
            group by coalesce(i.categoryName, 'Uncategorised')
            order by sum(i.lineTotal) desc""")
    List<Object[]> byCategory(Long sellerId, Collection<OrderStatus> statuses, Instant from, Instant to);

    @Query("""
            select coalesce(o.shippingRegion, o.shippingCity, 'Unknown'), count(o), sum(o.totalAmount)
            from Order o
            where (:sellerId is null or o.sellerId = :sellerId) and o.status in :statuses
              and o.createdAt >= :from and o.createdAt < :to
            group by coalesce(o.shippingRegion, o.shippingCity, 'Unknown')
            order by sum(o.totalAmount) desc""")
    List<Object[]> byRegion(Long sellerId, Collection<OrderStatus> statuses, Instant from, Instant to);

    @Query("""
            select o.status, count(o), coalesce(sum(o.totalAmount), 0)
            from Order o
            where (:sellerId is null or o.sellerId = :sellerId) and o.createdAt >= :from and o.createdAt < :to
            group by o.status""")
    List<Object[]> statusBreakdown(Long sellerId, Instant from, Instant to);

    @Query("""
            select coalesce(o.paymentMethod, 'UNPAID'), count(o), coalesce(sum(o.totalAmount), 0)
            from Order o
            where (:sellerId is null or o.sellerId = :sellerId) and o.status in :statuses
              and o.createdAt >= :from and o.createdAt < :to
            group by coalesce(o.paymentMethod, 'UNPAID')""")
    List<Object[]> paymentMethods(Long sellerId, Collection<OrderStatus> statuses, Instant from, Instant to);

    @Query("""
            select o.userId, o.customerUsername, count(o), coalesce(sum(o.totalAmount), 0)
            from Order o
            where (:sellerId is null or o.sellerId = :sellerId) and o.status in :statuses
              and o.createdAt >= :from and o.createdAt < :to
            group by o.userId, o.customerUsername
            order by coalesce(sum(o.totalAmount), 0) desc""")
    List<Object[]> topCustomers(Long sellerId, Collection<OrderStatus> statuses, Instant from, Instant to, Limit limit);

    /** Orders per customer in the period (repeat-customer rate = customers with more than one order). */
    @Query("""
            select o.userId, count(o) from Order o
            where (:sellerId is null or o.sellerId = :sellerId) and o.status in :statuses
              and o.createdAt >= :from and o.createdAt < :to
            group by o.userId""")
    List<Object[]> ordersPerCustomer(Long sellerId, Collection<OrderStatus> statuses, Instant from, Instant to);

    @Query("""
            select o.sellerId, o.sellerName, count(o), coalesce(sum(o.totalAmount), 0)
            from Order o
            where o.status in :statuses and o.createdAt >= :from and o.createdAt < :to
            group by o.sellerId, o.sellerName
            order by coalesce(sum(o.totalAmount), 0) desc""")
    List<Object[]> topSellers(Collection<OrderStatus> statuses, Instant from, Instant to, Limit limit);

    /** Shipped orders of the period with their history (average dispatch time). */
    @EntityGraph(attributePaths = "history")
    @Query("""
            select o from Order o
            where (:sellerId is null or o.sellerId = :sellerId) and o.shippedAt is not null
              and o.createdAt >= :from and o.createdAt < :to""")
    List<Order> shippedInPeriod(Long sellerId, Instant from, Instant to);

    @Query("select count(o) from Order o where o.sellerId = :sellerId and o.status = :status")
    long countBySellerAndStatus(Long sellerId, OrderStatus status);

    @Query("select count(o) from Order o where o.status = :status")
    long countByStatus(OrderStatus status);

    /** The seller's customers (CRM): customer id, username, e-mail, orders, spent, first and last order. */
    @Query(value = """
            select o.userId, o.customerUsername, o.customerEmail, count(o), sum(o.totalAmount), min(o.createdAt),
                   max(o.createdAt)
            from Order o
            where o.sellerId = :sellerId and o.status in :statuses
              and (lower(o.customerUsername) like :pattern escape '\\' or lower(coalesce(o.customerEmail, '')) like :pattern escape '\\')
            group by o.userId, o.customerUsername, o.customerEmail""")
    List<Object[]> sellerCustomers(Long sellerId, Collection<OrderStatus> statuses, String pattern);

    /** Has this customer received this product in this order? (verified-purchase reviews) */
    @Query("""
            select count(i) > 0 from OrderItem i join i.order o
            where o.id = :orderId and o.userId = :customerId and i.productId = :productId and o.status = :status""")
    boolean hasDeliveredItem(Long orderId, Long customerId, Long productId, OrderStatus status);

    /** Customer summary for the administrator: orders, spent (sold statuses), first and last order. */
    @Query("""
            select count(o), coalesce(sum(case when o.status in :sold then o.totalAmount else 0 end), 0),
                   min(o.createdAt), max(o.createdAt)
            from Order o where o.userId = :customerId""")
    List<Object[]> customerSummary(Long customerId, Collection<OrderStatus> sold);
}
