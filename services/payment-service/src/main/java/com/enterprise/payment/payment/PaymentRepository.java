package com.enterprise.payment.payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Payments and the SQL aggregates of seller earnings and platform revenue ({@code sellerId == null}: all). */
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    Page<Payment> findByCustomerId(Long customerId, Pageable pageable);

    /** Payments the order service has not acknowledged yet (retried by the background job). */
    @Query("""
            select p from Payment p
            where p.orderUpdatePending = true and p.orderUpdateAttempts < :maxAttempts and p.createdAt < :before
            order by p.createdAt""")
    List<Payment> findAwaitingOrderUpdate(Instant before, int maxAttempts, Limit limit);

    /** Count, amount, fees and net per status and method (GROUP BY two columns). */
    @Query("""
            select p.status, p.method, count(p), sum(p.amount), sum(p.platformFee), sum(p.sellerNet)
            from Payment p
            where (:sellerId is null or p.sellerId = :sellerId) and p.createdAt >= :from and p.createdAt < :to
            group by p.status, p.method""")
    List<Object[]> totals(Long sellerId, Instant from, Instant to);

    /** Platform fees per calendar month (year()/month() are translated to the database's EXTRACT). */
    @Query("""
            select year(p.createdAt), month(p.createdAt), sum(p.platformFee)
            from Payment p
            where p.sellerId = :sellerId and p.status in :statuses and p.createdAt >= :from and p.createdAt < :to
            group by year(p.createdAt), month(p.createdAt)""")
    List<Object[]> monthlyFees(Long sellerId, Collection<PaymentStatus> statuses, Instant from, Instant to);

    /** Payment volume and platform fees per day (year/month/day = EXTRACT, portable to Oracle and PostgreSQL). */
    @Query("""
            select year(p.createdAt), month(p.createdAt), day(p.createdAt), count(p), sum(p.amount), sum(p.platformFee)
            from Payment p
            where (:sellerId is null or p.sellerId = :sellerId) and p.status in :statuses
              and p.createdAt >= :from and p.createdAt < :to
            group by year(p.createdAt), month(p.createdAt), day(p.createdAt)""")
    List<Object[]> daily(Long sellerId, Collection<PaymentStatus> statuses, Instant from, Instant to);

    /** Sellers by payment volume: what the platform earns from each store. */
    @Query("""
            select p.sellerId, max(p.sellerName), count(p), sum(p.amount), sum(p.platformFee), sum(p.sellerNet)
            from Payment p
            where p.status in :statuses and p.createdAt >= :from and p.createdAt < :to
            group by p.sellerId
            order by sum(p.amount) desc""")
    List<Object[]> bySeller(Collection<PaymentStatus> statuses, Instant from, Instant to, Limit limit);
}
