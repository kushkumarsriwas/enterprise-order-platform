package com.enterprise.inventory.counting;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StockCountRepository extends JpaRepository<StockCount, Long>, JpaSpecificationExecutor<StockCount> {

    @EntityGraph(attributePaths = {"warehouse", "category", "lines", "lines.product"})
    Optional<StockCount> findWithLinesByIdAndSellerId(Long id, Long sellerId);

    boolean existsBySellerIdAndWarehouseIdAndStatus(Long sellerId, Long warehouseId, StockCountStatus status);
}
