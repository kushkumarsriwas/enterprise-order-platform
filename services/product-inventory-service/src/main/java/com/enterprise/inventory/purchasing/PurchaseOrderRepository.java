package com.enterprise.inventory.purchasing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    @EntityGraph(attributePaths = {"supplier", "warehouse", "lines", "lines.product"})
    Optional<PurchaseOrder> findWithLinesByIdAndSellerId(Long id, Long sellerId);

    /** Units still expected per product (open purchase orders): product id, outstanding quantity. */
    @Query("""
            select l.product.id, sum(l.quantityOrdered - l.quantityReceived)
            from PurchaseOrderLine l join l.purchaseOrder po
            where po.sellerId = :sellerId and po.status in :statuses and l.product.id in :productIds
            group by l.product.id""")
    List<Object[]> incomingByProduct(Long sellerId, Collection<PurchaseOrderStatus> statuses,
                                     Collection<Long> productIds);

    @Query("""
            select l.product.id, sum(l.quantityOrdered - l.quantityReceived)
            from PurchaseOrderLine l join l.purchaseOrder po
            where po.status in :statuses and l.product.id in :productIds
            group by l.product.id""")
    List<Object[]> incomingByProductIds(Collection<PurchaseOrderStatus> statuses, Collection<Long> productIds);

    @Query("""
            select coalesce(sum(l.quantityOrdered - l.quantityReceived), 0)
            from PurchaseOrderLine l join l.purchaseOrder po
            where po.sellerId = :sellerId and po.status in :statuses""")
    long incomingUnits(Long sellerId, Collection<PurchaseOrderStatus> statuses);

    @Query("""
            select l from PurchaseOrderLine l join fetch l.purchaseOrder po join fetch po.supplier
            where po.sellerId = :sellerId and po.status in :statuses and l.product.id = :productId
            order by po.expectedDate""")
    List<PurchaseOrderLine> openLinesForProduct(Long sellerId, Collection<PurchaseOrderStatus> statuses, Long productId);

    long countBySellerIdAndStatusIn(Long sellerId, Collection<PurchaseOrderStatus> statuses);

    /** Per supplier: supplier id, purchase orders, open orders, ordered amount. */
    @Query("""
            select po.supplier.id, count(po),
                   sum(case when po.status in :open then 1 else 0 end),
                   coalesce(sum(case when po.status <> com.enterprise.inventory.purchasing.PurchaseOrderStatus.CANCELLED
                                     and po.status <> com.enterprise.inventory.purchasing.PurchaseOrderStatus.DRAFT
                                then po.totalAmount else 0 end), 0)
            from PurchaseOrder po where po.sellerId = :sellerId group by po.supplier.id""")
    List<Object[]> statsBySupplier(Long sellerId, Collection<PurchaseOrderStatus> open);

    /** Fully received purchase orders with an expected date: supplier id, total, on time (received <= expected). */
    @Query("""
            select po.supplier.id, count(po),
                   sum(case when cast(po.receivedAt as LocalDate) <= po.expectedDate then 1 else 0 end)
            from PurchaseOrder po
            where po.sellerId = :sellerId and po.status = com.enterprise.inventory.purchasing.PurchaseOrderStatus.RECEIVED
              and po.expectedDate is not null
            group by po.supplier.id""")
    List<Object[]> onTimeBySupplier(Long sellerId);
}
