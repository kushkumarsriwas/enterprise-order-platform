package com.enterprise.payment.payment;

import com.enterprise.payment.common.Like;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Transaction filters for the seller payments screen and the administrator's transaction explorer
 * (JPA Criteria API: any combination of filters becomes one SQL statement).
 *
 * @param query transaction reference, order number, customer, store, masked card / UPI ID or provider reference
 */
public record PaymentFilter(Long sellerId, Long customerId, List<PaymentStatus> statuses,
                            List<PaymentMethod> methods, LocalDate from, LocalDate to, BigDecimal minAmount,
                            BigDecimal maxAmount, String query) {

    public Specification<Payment> toSpecification() {
        return (root, criteria, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (sellerId != null) {
                where.add(cb.equal(root.get("sellerId"), sellerId));
            }
            if (customerId != null) {
                where.add(cb.equal(root.get("customerId"), customerId));
            }
            if (statuses != null && !statuses.isEmpty()) {
                where.add(root.get("status").in(statuses));
            }
            if (methods != null && !methods.isEmpty()) {
                where.add(root.get("method").in(methods));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), startOf(from)));
            }
            if (to != null) {
                where.add(cb.lessThan(root.<Instant>get("createdAt"), startOf(to.plusDays(1))));
            }
            if (minAmount != null) {
                where.add(cb.ge(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                where.add(cb.le(root.get("amount"), maxAmount));
            }
            if (query != null && !query.isBlank()) {
                String pattern = Like.contains(query);
                where.add(cb.or(
                        cb.like(cb.lower(root.get("transactionReference")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(root.get("orderNumber")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("customerUsername"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("sellerName"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("maskedInstrument"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("providerReference"), "")), pattern,
                                Like.ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** newest (default), oldest, amount_desc, amount_asc. */
    public static Sort sort(String sort) {
        Sort newest = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        if (sort == null) {
            return newest;
        }
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
            case "amount_desc" -> Sort.by(Sort.Direction.DESC, "amount").and(newest);
            case "amount_asc" -> Sort.by(Sort.Direction.ASC, "amount").and(newest);
            default -> newest;
        };
    }

    static Instant startOf(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
