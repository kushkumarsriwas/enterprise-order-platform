package com.enterprise.inventory.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {

    @Query("select s from WarehouseStock s where s.product.id = :productId and s.warehouse.id = :warehouseId")
    Optional<WarehouseStock> find(Long productId, Long warehouseId);

    @Query("select s from WarehouseStock s join fetch s.warehouse w where s.product.id = :productId order by w.code")
    List<WarehouseStock> findByProduct(Long productId);

    @Query("select s from WarehouseStock s join fetch s.warehouse w where s.product.id in :productIds")
    List<WarehouseStock> findByProducts(Collection<Long> productIds);

    @Query("""
            select s from WarehouseStock s join fetch s.product p left join fetch p.category
            where s.warehouse.id = :warehouseId and p.active = true order by p.name""")
    List<WarehouseStock> findByWarehouse(Long warehouseId);

    /** Units and value at cost per warehouse (warehouse id, product lines with stock, units, value). */
    @Query("""
            select s.warehouse.id, count(s), coalesce(sum(s.onHand), 0), coalesce(sum(s.onHand * p.costPrice), 0)
            from WarehouseStock s join s.product p
            where p.sellerId = :sellerId and s.onHand > 0
            group by s.warehouse.id""")
    List<Object[]> totalsByWarehouse(Long sellerId);

    @Query("select coalesce(sum(s.onHand), 0) from WarehouseStock s where s.warehouse.id = :warehouseId")
    long unitsIn(Long warehouseId);
}
