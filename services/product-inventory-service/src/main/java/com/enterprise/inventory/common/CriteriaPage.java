package com.enterprise.inventory.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Dynamic search with the JPA Criteria API: one {@link Spec} builds the WHERE clause (and ORDER BY) for both
 * the page query and the count query, so filters can join other entities and sort by computed expressions -
 * things Spring Data's derived queries can't express.
 */
public final class CriteriaPage {

    public static final int MAX_PAGE_SIZE = 100;

    private CriteriaPage() {
    }

    /** {@code count} is true while building the COUNT query: skip fetch joins and ordering there. */
    @FunctionalInterface
    public interface Spec<T> {
        Parts build(CriteriaBuilder cb, CriteriaQuery<?> query, Root<T> root, boolean count);
    }

    public record Parts(List<Predicate> where, List<Order> orderBy) {

        public static Parts of(List<Predicate> where, List<Order> orderBy) {
            return new Parts(where, orderBy);
        }
    }

    public static <T> Page<T> find(EntityManager em, Class<T> type, Spec<T> spec, int page, int size) {
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<T> select = cb.createQuery(type);
        Root<T> root = select.from(type);
        Parts parts = spec.build(cb, select, root, false);
        select.select(root).where(parts.where().toArray(Predicate[]::new)).orderBy(parts.orderBy());
        List<T> content = em.createQuery(select)
                .setFirstResult(pageNumber * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        long total;
        if (pageNumber == 0 && content.size() < pageSize) {
            total = content.size(); // the first page is not full: no COUNT query needed
        } else {
            CriteriaQuery<Long> count = cb.createQuery(Long.class);
            Root<T> countRoot = count.from(type);
            Parts countParts = spec.build(cb, count, countRoot, true);
            count.select(cb.count(countRoot)).where(countParts.where().toArray(Predicate[]::new));
            total = em.createQuery(count).getSingleResult();
        }
        return new PageImpl<>(content, PageRequest.of(pageNumber, pageSize), total);
    }

    /** Same filters without paging (exports), capped at {@code limit} rows. */
    public static <T> List<T> list(EntityManager em, Class<T> type, Spec<T> spec, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> select = cb.createQuery(type);
        Root<T> root = select.from(type);
        Parts parts = spec.build(cb, select, root, false);
        select.select(root).where(parts.where().toArray(Predicate[]::new)).orderBy(parts.orderBy());
        return em.createQuery(select).setMaxResults(limit).getResultList();
    }
}
