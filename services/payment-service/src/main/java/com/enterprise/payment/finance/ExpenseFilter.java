package com.enterprise.payment.finance;

import com.enterprise.payment.common.Like;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Expense filters: period, categories, amount range and free text (description, vendor, reference). */
public record ExpenseFilter(LocalDate from, LocalDate to, List<ExpenseCategory> categories, BigDecimal minAmount,
                            BigDecimal maxAmount, String query) {

    public Specification<Expense> toSpecification(Long sellerId) {
        return (root, criteria, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("sellerId"), sellerId));
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            }
            if (to != null) {
                where.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            }
            if (categories != null && !categories.isEmpty()) {
                where.add(root.get("category").in(categories));
            }
            if (minAmount != null) {
                where.add(cb.ge(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                where.add(cb.le(root.get("amount"), maxAmount));
            }
            if (query != null && !query.isBlank()) {
                // explicit escape character: user input like "50%" is matched literally on every database
                String pattern = Like.contains(query);
                where.add(cb.or(cb.like(cb.lower(root.get("description")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("vendor"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("reference"), "")), pattern, Like.ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
