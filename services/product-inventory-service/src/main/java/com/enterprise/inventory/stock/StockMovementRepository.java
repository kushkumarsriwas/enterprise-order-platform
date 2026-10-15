package com.enterprise.inventory.stock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("""
            select m from StockMovement m left join fetch m.warehouse
            where m.product.id = :productId order by m.createdAt desc, m.id desc""")
    List<StockMovement> recentForProduct(Long productId, Limit limit);

    /** Where the units of an order were shipped from (to put them back to the same shelves on a return). */
    @Query("""
            select m from StockMovement m join fetch m.warehouse
            where m.product.id = :productId and m.reference = :reference and m.type = :type order by m.id""")
    List<StockMovement> findByProductReferenceAndType(Long productId, String reference, MovementType type);

    /** Units sold (net of returns) and revenue per product in a period: product id, units, revenue, cost. */
    @Query("""
            select m.product.id, -sum(m.quantity), -sum(m.quantity * m.unitPrice), -sum(m.quantity * m.unitCost)
            from StockMovement m
            where m.sellerId = :sellerId and m.type in :types and m.createdAt >= :from and m.createdAt < :to
            group by m.product.id""")
    List<Object[]> salesByProduct(Long sellerId, Collection<MovementType> types, Instant from, Instant to);

    /** Last sale per product: product id, time of the most recent SOLD movement. */
    @Query("""
            select m.product.id, max(m.createdAt) from StockMovement m
            where m.sellerId = :sellerId and m.type = com.enterprise.inventory.stock.MovementType.SOLD
            group by m.product.id""")
    List<Object[]> lastSaleByProduct(Long sellerId);

    /** Units sold per product since a time (demand for replenishment planning). */
    @Query("""
            select m.product.id, -sum(m.quantity) from StockMovement m
            where m.sellerId = :sellerId and m.type in :types and m.createdAt >= :since
            group by m.product.id""")
    List<Object[]> unitsSoldSince(Long sellerId, Collection<MovementType> types, Instant since);

    /** Inbound and outbound units per day: day, type, units. */
    @Query("""
            select cast(m.createdAt as LocalDate), m.type, sum(m.quantity)
            from StockMovement m
            where m.sellerId = :sellerId and m.createdAt >= :from and m.createdAt < :to and m.warehouse is not null
            group by cast(m.createdAt as LocalDate), m.type
            order by cast(m.createdAt as LocalDate)""")
    List<Object[]> dailyFlow(Long sellerId, Instant from, Instant to);

    /** Stock written off / found by reason: reason, units, value at cost. */
    @Query("""
            select m.reason, sum(m.quantity), sum(m.quantity * m.unitCost)
            from StockMovement m
            where m.sellerId = :sellerId and m.type in :types and m.createdAt >= :from and m.createdAt < :to
            group by m.reason""")
    List<Object[]> adjustmentsByReason(Long sellerId, Collection<MovementType> types, Instant from, Instant to);
}
