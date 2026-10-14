package com.enterprise.payment.payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    Page<Payment> findByMerchantId(Long merchantId, Pageable pageable);

    Page<Payment> findByMerchantIdAndStatus(Long merchantId, PaymentStatus status, Pageable pageable);

    /** Count, amount, fees and net per status and method for one seller (GROUP BY two columns). */
    @Query("""
            select p.status, p.method, count(p), sum(p.amount), sum(p.platformFee), sum(p.merchantNet)
            from Payment p
            where p.merchantId = :merchantId and p.createdAt >= :from and p.createdAt < :to
            group by p.status, p.method
            """)
    List<Object[]> merchantTotals(Long merchantId, Instant from, Instant to);

    @Query("""
            select p.status, p.method, count(p), sum(p.amount), sum(p.platformFee), sum(p.merchantNet)
            from Payment p
            group by p.status, p.method
            """)
    List<Object[]> platformTotals();

    /** Platform fees per calendar month (year()/month() are translated to the database's EXTRACT). */
    @Query("""
            select year(p.createdAt), month(p.createdAt), sum(p.platformFee)
            from Payment p
            where p.merchantId = :merchantId and p.status in :statuses and p.createdAt >= :from and p.createdAt < :to
            group by year(p.createdAt), month(p.createdAt)
            """)
    List<Object[]> monthlyFees(Long merchantId, Collection<PaymentStatus> statuses, Instant from, Instant to);
}
