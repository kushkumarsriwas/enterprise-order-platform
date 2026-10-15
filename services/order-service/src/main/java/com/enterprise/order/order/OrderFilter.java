package com.enterprise.order.order;

import com.enterprise.order.common.Like;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
 * Filters of the seller and administrator order screens, turned into a JPA Criteria query
 * ({@link Specification}) so any combination of filters becomes one SQL statement.
 *
 * @param query free text: order number, customer, recipient, e-mail, tracking number, store, product name or SKU
 */
public record OrderFilter(Long sellerId, Long customerId, List<OrderStatus> statuses, PaymentState paymentState,
                          String paymentMethod, LocalDate from, LocalDate to, BigDecimal minTotal,
                          BigDecimal maxTotal, String query, String region) {

    public static OrderFilter forSeller(Long sellerId) {
        return new OrderFilter(sellerId, null, List.of(), null, null, null, null, null, null, null, null);
    }

    public OrderFilter withSeller(Long seller) {
        return new OrderFilter(seller, customerId, statuses, paymentState, paymentMethod, from, to, minTotal, maxTotal,
                query, region);
    }

    public Specification<Order> toSpecification() {
        return (root, criteria, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (sellerId != null) {
                where.add(cb.equal(root.get("sellerId"), sellerId));
            }
            if (customerId != null) {
                where.add(cb.equal(root.get("userId"), customerId));
            }
            if (statuses != null && !statuses.isEmpty()) {
                where.add(root.get("status").in(statuses));
            }
            if (paymentState != null) {
                where.add(cb.equal(root.get("paymentState"), paymentState));
            }
            if (paymentMethod != null && !paymentMethod.isBlank()) {
                where.add(cb.equal(root.get("paymentMethod"), paymentMethod.trim().toUpperCase(Locale.ROOT)));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), startOf(from)));
            }
            if (to != null) {
                where.add(cb.lessThan(root.<Instant>get("createdAt"), startOf(to.plusDays(1))));
            }
            if (minTotal != null) {
                where.add(cb.ge(root.get("totalAmount"), minTotal));
            }
            if (maxTotal != null) {
                where.add(cb.le(root.get("totalAmount"), maxTotal));
            }
            if (region != null && !region.isBlank()) {
                String value = region.trim().toLowerCase(Locale.ROOT);
                where.add(cb.or(cb.equal(cb.lower(root.get("shippingRegion")), value),
                        cb.equal(cb.lower(root.get("shippingCity")), value)));
            }
            if (query != null && !query.isBlank()) {
                String pattern = Like.contains(query);
                Subquery<Long> matchingItem = criteria.subquery(Long.class);
                Root<OrderItem> item = matchingItem.from(OrderItem.class);
                matchingItem.select(item.get("id")).where(cb.equal(item.get("order"), root),
                        cb.or(cb.like(cb.lower(item.get("productName")), pattern, Like.ESCAPE),
                                cb.like(cb.lower(item.get("sku")), pattern, Like.ESCAPE)));
                where.add(cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(root.get("customerUsername")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("customerEmail"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("recipientName"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("trackingNumber"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(root.get("sellerName")), pattern, Like.ESCAPE),
                        cb.exists(matchingItem)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** newest (default), oldest, total_desc, total_asc. */
    public static Sort sort(String sort) {
        Sort newest = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        if (sort == null) {
            return newest;
        }
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
            case "total_desc" -> Sort.by(Sort.Direction.DESC, "totalAmount").and(newest);
            case "total_asc" -> Sort.by(Sort.Direction.ASC, "totalAmount").and(newest);
            default -> newest;
        };
    }

    static Instant startOf(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
