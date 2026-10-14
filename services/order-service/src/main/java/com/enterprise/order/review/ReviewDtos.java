package com.enterprise.order.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record CreateReview(@NotNull Long orderId, @NotNull Long productId,
                               @NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {
    }

    public record ReplyRequest(@NotBlank @Size(max = 1000) String reply) {
    }

    public record ReviewResponse(Long id, Long productId, String productName, Long sellerId, String sellerName,
                                 Long orderId, Long customerId, String customerName, int rating, String comment,
                                 boolean verifiedPurchase, String sellerReply, Instant sellerRepliedAt,
                                 Instant createdAt) {

        static ReviewResponse from(Review review) {
            return new ReviewResponse(review.getId(), review.getProductId(), review.getProductName(),
                    review.getSellerId(), review.getSellerName(), review.getOrderId(), review.getCustomerId(),
                    review.getCustomerName(), review.getRating(), review.getComment(), true, review.getSellerReply(),
                    review.getSellerRepliedAt(), review.getCreatedAt());
        }
    }

    /** Rating summary with the number of reviews per star (1..5). */
    public record RatingSummary(BigDecimal averageRating, long reviewCount, Map<Integer, Long> distribution) {
    }

    public record ProductReviews(Long productId, BigDecimal averageRating, long reviewCount,
                                 Map<Integer, Long> distribution,
                                 com.enterprise.order.common.PageResponse<ReviewResponse> reviews) {
    }

    public record StoreReviews(BigDecimal averageRating, long reviewCount, long awaitingReply,
                               Map<Integer, Long> distribution,
                               com.enterprise.order.common.PageResponse<ReviewResponse> reviews) {
    }

    /** A delivered product the customer has not rated yet. */
    public record AwaitingReview(Long productId, String productName, Long orderId, String orderNumber,
                                 String sellerName, Instant deliveredAt) {
    }

    record RatingChanged(Long productId) {
    }

    public record ReviewList(List<ReviewResponse> reviews) {
    }
}
