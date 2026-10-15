package com.enterprise.inventory.purchasing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long>, JpaSpecificationExecutor<GoodsReceipt> {

    @EntityGraph(attributePaths = {"warehouse", "purchaseOrder", "lines"})
    Optional<GoodsReceipt> findWithLinesByIdAndSellerId(Long id, Long sellerId);

    @EntityGraph(attributePaths = {"warehouse", "purchaseOrder", "lines"})
    List<GoodsReceipt> findByPurchaseOrderIdOrderByReceivedAt(Long purchaseOrderId);

    /** Purchase spend per supplier name in a period: supplier, receipts, units, amount. */
    @Query("""
            select coalesce(r.supplierName, 'No supplier'), count(r), sum(r.totalUnits), sum(r.totalAmount)
            from GoodsReceipt r
            where r.sellerId = :sellerId and r.receivedAt >= :from and r.receivedAt < :to
            group by coalesce(r.supplierName, 'No supplier')
            order by sum(r.totalAmount) desc""")
    List<Object[]> spendBySupplier(Long sellerId, Instant from, Instant to);

    /** Purchase spend per month: year, month, goods receipts, units, amount. */
    @Query("""
            select year(r.receivedAt), month(r.receivedAt), count(r), coalesce(sum(r.totalUnits), 0), sum(r.totalAmount)
            from GoodsReceipt r
            where r.sellerId = :sellerId and r.receivedAt >= :from and r.receivedAt < :to
            group by year(r.receivedAt), month(r.receivedAt)
            order by year(r.receivedAt), month(r.receivedAt)""")
    List<Object[]> spendByMonth(Long sellerId, Instant from, Instant to);

    @Query("select coalesce(sum(r.totalAmount), 0) from GoodsReceipt r where r.supplier.id = :supplierId")
    java.math.BigDecimal totalSpendWith(Long supplierId);
}
