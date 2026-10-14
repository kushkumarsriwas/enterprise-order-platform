package com.enterprise.order.review;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    Page<Review> findByCustomerId(Long customerId, Pageable pageable);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    @Query("select coalesce(avg(r.rating), 0), count(r) from Review r where r.productId = :productId")
    List<Object[]> ratingSummary(Long productId);

    @Query("select r.rating, count(r) from Review r where r.productId = :productId group by r.rating")
    List<Object[]> ratingDistribution(Long productId);

    @Query("""
            select coalesce(avg(r.rating), 0), count(r),
                   coalesce(sum(case when r.sellerReply is null then 1 else 0 end), 0)
            from Review r where (:sellerId is null or r.sellerId = :sellerId)""")
    List<Object[]> storeSummary(Long sellerId);

    @Query("""
            select r.rating, count(r) from Review r where (:sellerId is null or r.sellerId = :sellerId)
            group by r.rating""")
    List<Object[]> storeDistribution(Long sellerId);

    /** Delivered products of a customer that have no review yet ("Rate your purchases"). */
    @Query("""
            select i.productId, i.productName, o.id, o.orderNumber, o.sellerName, o.deliveredAt
            from OrderItem i join i.order o
            where o.userId = :customerId and o.status = com.enterprise.order.order.OrderStatus.DELIVERED
              and not exists (select r.id from Review r where r.customerId = :customerId and r.productId = i.productId)
            order by o.deliveredAt desc""")
    List<Object[]> awaitingReview(Long customerId);
}
