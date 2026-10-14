package com.enterprise.inventory.warehouse;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    List<Warehouse> findBySellerIdOrderByDefaultWarehouseDescCodeAsc(Long sellerId);

    Optional<Warehouse> findByIdAndSellerId(Long id, Long sellerId);

    Optional<Warehouse> findFirstBySellerIdAndDefaultWarehouseTrue(Long sellerId);

    boolean existsBySellerIdAndCodeIgnoreCase(Long sellerId, String code);

    long countBySellerId(Long sellerId);
}
