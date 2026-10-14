package com.enterprise.inventory.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsByMerchantIdAndSkuIgnoreCase(Long merchantId, String sku);

    Optional<Product> findByIdAndMerchantId(Long id, Long merchantId);

    List<Product> findByMerchantIdAndActiveTrueOrderByName(Long merchantId);
}
