package com.enterprise.inventory.stock;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Two locking strategies, each where it fits best:
 * <ul>
 *   <li>Reservations (hot path, many customers): one conditional UPDATE - the database checks and changes the
 *   row atomically, so two customers can never buy the last unit (no read-before-write, no lock held).</li>
 *   <li>Physical stock changes (receipts, transfers, counts, shipping): SELECT ... FOR UPDATE on the product's
 *   inventory row, then the warehouse rows are changed under that lock.</li>
 * </ul>
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i join fetch i.product where i.productId = :productId")
    Optional<Inventory> findForUpdate(Long productId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.availableQuantity = i.availableQuantity - :quantity,
                   i.reservedQuantity = i.reservedQuantity + :quantity,
                   i.version = i.version + 1,
                   i.updatedAt = :now
             where i.productId = :productId and i.availableQuantity >= :quantity""")
    int reserve(Long productId, int quantity, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.reservedQuantity = i.reservedQuantity - :quantity,
                   i.availableQuantity = i.availableQuantity + :quantity,
                   i.version = i.version + 1,
                   i.updatedAt = :now
             where i.productId = :productId and i.reservedQuantity >= :quantity""")
    int releaseReserved(Long productId, int quantity, Instant now);

    @Query("select i.availableQuantity from Inventory i where i.productId = :productId")
    Optional<Integer> availableOf(Long productId);

    List<Inventory> findByProductIdIn(Collection<Long> productIds);

    @Query("select i from Inventory i join fetch i.product p left join fetch p.category where p.sellerId = :sellerId and p.active = true")
    List<Inventory> findActiveBySeller(Long sellerId);

    /**
     * Platform inventory per seller (administrator): seller id, store, products, listed, units on hand,
     * value at cost, value at selling price, low-stock products, out-of-stock products.
     */
    @Query("""
            select p.sellerId, p.sellerName, count(p), sum(case when p.listed = true then 1 else 0 end),
                   sum(i.availableQuantity + i.reservedQuantity),
                   sum((i.availableQuantity + i.reservedQuantity) * p.costPrice),
                   sum((i.availableQuantity + i.reservedQuantity) * p.price),
                   sum(case when i.availableQuantity > 0 and i.availableQuantity <= p.reorderPoint then 1 else 0 end),
                   sum(case when i.availableQuantity <= 0 then 1 else 0 end)
            from Inventory i join i.product p
            where p.active = true
            group by p.sellerId, p.sellerName
            order by sum((i.availableQuantity + i.reservedQuantity) * p.costPrice) desc""")
    List<Object[]> platformTotalsBySeller();
}
