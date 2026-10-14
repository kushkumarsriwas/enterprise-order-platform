package com.enterprise.order.review;

import com.enterprise.order.client.NotificationPublisher;
import com.enterprise.order.common.ApiException;
import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.Order;
import com.enterprise.order.order.OrderItem;
import com.enterprise.order.order.OrderRepository;
import com.enterprise.order.order.OrderStatus;
import com.enterprise.order.security.AuthenticatedUser;
import com.enterprise.order.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final NotificationPublisher notifications;

    public ReviewController(ReviewRepository reviewRepository, OrderRepository orderRepository,
                            NotificationPublisher notifications) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.notifications = notifications;
    }

    @GetMapping("/api/reviews")
    @Transactional(readOnly = true)
    @Operation(summary = "Reviews of a product with the average rating (public)")
    public ProductReviews forProduct(@RequestParam Long productId, @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        Object[] summary = reviewRepository.ratingSummary(productId).get(0);
        Map<Integer, Long> distribution = new TreeMap<>();
        for (int stars = 1; stars <= 5; stars++) {
            distribution.put(stars, 0L);
        }
        reviewRepository.ratingDistribution(productId)
                .forEach(row -> distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue()));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return new ProductReviews(productId,
                BigDecimal.valueOf(((Number) summary[0]).doubleValue()).setScale(1, RoundingMode.HALF_UP),
                ((Number) summary[1]).longValue(), distribution,
                PageResponse.from(reviewRepository.findByProductId(productId, pageable), ReviewResponse::from));
    }

    @GetMapping("/api/reviews/mine")
    @Transactional(readOnly = true)
    @Operation(summary = "Reviews I have written")
    public List<ReviewResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return reviewRepository.findByCustomerId(user.id()).stream().map(ReviewResponse::from).toList();
    }

    @PostMapping("/api/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    @Operation(summary = "Review a product from one of my delivered orders (verified purchase)")
    public ReviewResponse create(@Valid @RequestBody CreateReview request, @AuthenticationPrincipal AuthenticatedUser user) {
        if (!orderRepository.hasDeliveredItem(request.orderId(), user.id(), request.productId(), OrderStatus.DELIVERED)) {
            throw ApiException.forbidden("You can review a product after the order containing it has been delivered");
        }
        if (reviewRepository.existsByCustomerIdAndProductId(user.id(), request.productId())) {
            throw ApiException.conflict("You have already reviewed this product");
        }
        Order order = orderRepository.findWithItemsById(request.orderId()).orElseThrow();
        String productName = order.getItems().stream().filter(i -> i.getProductId().equals(request.productId()))
                .map(OrderItem::getProductName).findFirst().orElse("Product");
        Review review = reviewRepository.save(new Review(request.productId(), productName, order.getMerchantId(),
                order.getId(), user.id(), user.username(), request.rating(),
                request.comment() == null || request.comment().isBlank() ? null : request.comment().trim()));
        notifications.toUser(CurrentUser.token(), order.getMerchantId(), "NEW_REVIEW",
                request.rating() + "-star review for " + productName,
                user.username() + ": " + (review.getComment() == null ? "(no comment)" : review.getComment()));
        return ReviewResponse.from(review);
    }

    @GetMapping("/api/merchant/reviews")
    @PreAuthorize("hasRole('MERCHANT')")
    @Transactional(readOnly = true)
    @Operation(summary = "Reviews of my products")
    public PageResponse<ReviewResponse> forMerchant(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @AuthenticationPrincipal AuthenticatedUser merchant) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(reviewRepository.findByMerchantId(merchant.id(), pageable), ReviewResponse::from);
    }

    public record CreateReview(@NotNull Long orderId, @NotNull Long productId, @NotNull @Min(1) @Max(5) Integer rating,
                               @Size(max = 1000) String comment) {
    }

    public record ReviewResponse(Long id, Long productId, String productName, Long orderId, String customerName,
                                 int rating, String comment, Instant createdAt) {

        static ReviewResponse from(Review review) {
            return new ReviewResponse(review.getId(), review.getProductId(), review.getProductName(),
                    review.getOrderId(), review.getCustomerName(), review.getRating(), review.getComment(),
                    review.getCreatedAt());
        }
    }

    public record ProductReviews(Long productId, BigDecimal averageRating, long reviewCount,
                                 Map<Integer, Long> distribution, PageResponse<ReviewResponse> reviews) {
    }
}
