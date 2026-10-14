package com.enterprise.inventory.purchasing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    Optional<Supplier> findByIdAndSellerId(Long id, Long sellerId);

    boolean existsBySellerIdAndNameIgnoreCase(Long sellerId, String name);

    boolean existsBySellerIdAndNameIgnoreCaseAndIdNot(Long sellerId, String name, Long id);

    List<Supplier> findBySellerIdOrderByName(Long sellerId);
}
