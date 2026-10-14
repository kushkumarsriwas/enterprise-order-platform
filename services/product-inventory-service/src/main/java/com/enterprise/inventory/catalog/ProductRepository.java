package com.enterprise.inventory.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsByMerchantIdAndSkuIgnoreCase(Long merchantId, String sku);

    Optional<Product> findByIdAndMerchantId(Long id, Long merchantId);

    List<Product> findByMerchantIdAndActiveTrueOrderByName(Long merchantId);

    @Query("select p.id from Product p where p.merchantId = :merchantId")
    List<Long> findIdsByMerchantId(Long merchantId);

    /** One UPDATE for all products of a store (suspension by an administrator). */
    @Modifying
    @Query("update Product p set p.merchantActive = :active where p.merchantId = :merchantId")
    int setMerchantActive(Long merchantId, boolean active);
}
