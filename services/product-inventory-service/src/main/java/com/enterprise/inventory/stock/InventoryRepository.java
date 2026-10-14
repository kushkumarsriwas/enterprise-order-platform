package com.enterprise.inventory.stock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Stock changes are single conditional UPDATE statements. The database checks and changes the row
 * atomically, so two customers can never buy the last unit at the same time (no overselling).
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

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
                   i.version = i.version + 1,
                   i.updatedAt = :now
             where i.productId = :productId and i.reservedQuantity >= :quantity""")
    int commitReserved(Long productId, int quantity, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.reservedQuantity = i.reservedQuantity - :quantity,
                   i.availableQuantity = i.availableQuantity + :quantity,
                   i.version = i.version + 1,
                   i.updatedAt = :now
             where i.productId = :productId and i.reservedQuantity >= :quantity""")
    int releaseReserved(Long productId, int quantity, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.availableQuantity = i.availableQuantity + :quantity,
                   i.version = i.version + 1,
                   i.updatedAt = :now
             where i.productId = :productId""")
    int addStock(Long productId, int quantity, Instant now);

    /** Scalar query on purpose: it does not load (and cache) the entity in the persistence context. */
    @Query("select i.availableQuantity + i.reservedQuantity from Inventory i where i.productId = :productId")
    Optional<Integer> unitsOnHand(Long productId);

    @Query("""
            select i from Inventory i join fetch i.product p
            where p.merchantId = :merchantId and p.active = true and i.availableQuantity <= i.reorderLevel
            order by i.availableQuantity""")
    List<Inventory> findLowStock(Long merchantId);

    @Query("select i from Inventory i join fetch i.product p where p.merchantId = :merchantId and p.active = true order by p.name")
    List<Inventory> findByMerchant(Long merchantId);
}
