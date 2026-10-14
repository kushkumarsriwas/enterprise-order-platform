package com.enterprise.order.review;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductId(Long productId, Pageable pageable);

    Page<Review> findByMerchantId(Long merchantId, Pageable pageable);

    List<Review> findByCustomerId(Long customerId);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    @Query("select coalesce(avg(r.rating), 0), count(r) from Review r where r.productId = :productId")
    List<Object[]> ratingSummary(Long productId);

    @Query("select r.rating, count(r) from Review r where r.productId = :productId group by r.rating")
    List<Object[]> ratingDistribution(Long productId);
}
