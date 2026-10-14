package com.enterprise.inventory.catalog;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySellerIdAndSkuIgnoreCase(Long sellerId, String sku);

    boolean existsBySellerIdAndBarcodeAndIdNot(Long sellerId, String barcode, Long id);

    boolean existsBySellerIdAndBarcode(Long sellerId, String barcode);

    @EntityGraph(attributePaths = {"category", "preferredSupplier"})
    Optional<Product> findByIdAndSellerId(Long id, Long sellerId);

    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findWithCategoryById(Long id);

    Optional<Product> findBySellerIdAndSkuIgnoreCase(Long sellerId, String sku);

    @EntityGraph(attributePaths = {"category", "preferredSupplier"})
    List<Product> findBySellerIdAndActiveTrue(Long sellerId);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByIdIn(Collection<Long> ids);

    @Query("select p.id from Product p where p.sellerId = :sellerId and p.id in :ids and p.active = true")
    List<Long> findOwnedIds(Long sellerId, Collection<Long> ids);

    /** Counters are bulk-incremented (no read-modify-write, no optimistic-lock conflict with seller edits). */
    @Modifying
    @Query("update Product p set p.unitsSold = p.unitsSold + :units where p.id = :productId")
    int addUnitsSold(Long productId, long units);

    @Modifying
    @Query("update Product p set p.ratingAverage = :average, p.ratingCount = :count where p.id = :productId")
    int updateRating(Long productId, BigDecimal average, int count);

    /** Vacation mode: hide or show every listing of a store with one UPDATE. */
    @Modifying
    @Query("update Product p set p.sellerActive = :active where p.sellerId = :sellerId")
    int setSellerActive(Long sellerId, boolean active);

    /** Store suspended or reactivated by the platform administrator. */
    @Modifying
    @Query("update Product p set p.storeSuspended = :suspended where p.sellerId = :sellerId")
    int setStoreSuspended(Long sellerId, boolean suspended);

    @Query("select p.id from Product p where p.sellerId = :sellerId")
    List<Long> findIdsBySeller(Long sellerId);

    @Query("select distinct p.brand from Product p where p.sellerId = :sellerId and p.active = true and p.brand is not null order by p.brand")
    List<String> brandsOfSeller(Long sellerId);
}
