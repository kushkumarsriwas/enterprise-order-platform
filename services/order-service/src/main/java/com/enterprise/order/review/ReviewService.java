package com.enterprise.order.review;

import com.enterprise.order.client.NotificationPublisher;
import com.enterprise.order.common.ApiException;
import com.enterprise.order.common.Like;
import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.Order;
import com.enterprise.order.order.OrderItem;
import com.enterprise.order.order.OrderRepository;
import com.enterprise.order.order.OrderStatus;
import com.enterprise.order.review.ReviewDtos.AwaitingReview;
import com.enterprise.order.review.ReviewDtos.CreateReview;
import com.enterprise.order.review.ReviewDtos.ProductReviews;
import com.enterprise.order.review.ReviewDtos.RatingChanged;
import com.enterprise.order.review.ReviewDtos.ReviewResponse;
import com.enterprise.order.review.ReviewDtos.StoreReviews;
import com.enterprise.order.security.AuthenticatedUser;
import com.enterprise.order.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verified-purchase reviews, seller replies and moderation; ratings are pushed to the catalogue after commit. */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final NotificationPublisher notifications;
    private final ApplicationEventPublisher events;

    public ReviewService(ReviewRepository reviewRepository, OrderRepository orderRepository,
                         NotificationPublisher notifications, ApplicationEventPublisher events) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.notifications = notifications;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public ProductReviews forProduct(Long productId, Integer rating, String sort, int page, int size) {
        Object[] summary = reviewRepository.ratingSummary(productId).get(0);
        Map<Integer, Long> distribution = distribution(reviewRepository.ratingDistribution(productId));
        Specification<Review> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("productId"), productId));
            if (rating != null) {
                where.add(cb.equal(root.get("rating"), rating));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), sort(sort));
        return new ProductReviews(productId, average(summary[0]), ((Number) summary[1]).longValue(), distribution,
                PageResponse.from(reviewRepository.findAll(spec, pageable), ReviewResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> mine(AuthenticatedUser customer, int page, int size) {
        return PageResponse.from(reviewRepository.findByCustomerId(customer.id(), PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"))), ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public List<AwaitingReview> awaitingReview(AuthenticatedUser customer) {
        Map<Long, AwaitingReview> byProduct = new LinkedHashMap<>();
        for (Object[] row : reviewRepository.awaitingReview(customer.id())) {
            Long productId = ((Number) row[0]).longValue();
            byProduct.putIfAbsent(productId, new AwaitingReview(productId, (String) row[1],
                    ((Number) row[2]).longValue(), (String) row[3], (String) row[4], (Instant) row[5]));
        }
        return new ArrayList<>(byProduct.values());
    }

    @Transactional
    public ReviewResponse create(AuthenticatedUser customer, CreateReview request) {
        if (!orderRepository.hasDeliveredItem(request.orderId(), customer.id(), request.productId(),
                OrderStatus.DELIVERED)) {
            throw ApiException.forbidden("You can review a product after the order containing it has been delivered");
        }
        if (reviewRepository.existsByCustomerIdAndProductId(customer.id(), request.productId())) {
            throw ApiException.conflict("You have already reviewed this product");
        }
        Order order = orderRepository.findWithItemsById(request.orderId()).orElseThrow();
        String productName = order.getItems().stream().filter(i -> i.getProductId().equals(request.productId()))
                .map(OrderItem::getProductName).findFirst().orElse("Product");
        Review review = reviewRepository.save(new Review(request.productId(), productName, order.getSellerId(),
                order.getSellerName(), order.getId(), customer.id(),
                publicName(order.getRecipientName(), customer.username()), request.rating(),
                blankToNull(request.comment())));
        events.publishEvent(new RatingChanged(request.productId()));
        notifications.toUser(CurrentUser.token(), order.getSellerId(), "NEW_REVIEW",
                request.rating() + "-star review for " + productName,
                customer.username() + ": " + (review.getComment() == null ? "(no comment)" : review.getComment()));
        log.info("{} rated product {} with {} star(s)", customer.username(), request.productId(), request.rating());
        return ReviewResponse.from(review);
    }

    /**
     * The name printed under a public review: first name and the initial of the last name of the person the order
     * was delivered to ("Aisha Al Mansoori" -> "Aisha M."), so reviews read like people without exposing full names.
     */
    static String publicName(String fullName, String fallback) {
        if (fullName == null || fullName.isBlank()) {
            return fallback;
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }

    // ------------------------------------------------------------------ seller

    @Transactional(readOnly = true)
    public StoreReviews forStore(Long sellerId, Integer rating, Boolean replied, Long productId, String query,
                                 String sort, int page, int size) {
        Object[] summary = reviewRepository.storeSummary(sellerId).get(0);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort(sort));
        return new StoreReviews(average(summary[0]), ((Number) summary[1]).longValue(),
                ((Number) summary[2]).longValue(), distribution(reviewRepository.storeDistribution(sellerId)),
                PageResponse.from(reviewRepository.findAll(filter(sellerId, rating, replied, productId, query),
                        pageable), ReviewResponse::from));
    }

    @Transactional
    public ReviewResponse reply(AuthenticatedUser seller, Long reviewId, String text) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getSellerId().equals(seller.id()))
                .orElseThrow(() -> ApiException.notFound("Review " + reviewId + " not found"));
        review.reply(text.trim());
        notifications.toUser(CurrentUser.token(), review.getCustomerId(), "REVIEW_REPLY",
                seller.displayName() + " replied to your review", text.trim());
        return ReviewResponse.from(review);
    }

    // ------------------------------------------------------------------ administrator (moderation)

    @Transactional(readOnly = true)
    public StoreReviews all(Long sellerId, Integer rating, Boolean replied, Long productId, String query, String sort,
                            int page, int size) {
        return forStore(sellerId, rating, replied, productId, query, sort, page, size);
    }

    @Transactional
    public void remove(Long reviewId, String reason) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review " + reviewId + " not found"));
        reviewRepository.delete(review);
        events.publishEvent(new RatingChanged(review.getProductId()));
        notifications.toUser(CurrentUser.token(), review.getCustomerId(), "REVIEW_REMOVED",
                "Your review of " + review.getProductName() + " was removed",
                reason == null || reason.isBlank() ? "It did not follow the Nexora review guidelines." : reason.trim());
        log.info("Review {} of product {} removed by the administrator", reviewId, review.getProductId());
    }

    /** Average and count after a change, read in a new transaction by {@link RatingSync}. */
    @Transactional(readOnly = true)
    public Object[] summaryOf(Long productId) {
        return reviewRepository.ratingSummary(productId).get(0);
    }

    // ------------------------------------------------------------------ helpers

    private static Specification<Review> filter(Long sellerId, Integer rating, Boolean replied, Long productId,
                                                String query) {
        return (root, criteria, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (sellerId != null) {
                where.add(cb.equal(root.get("sellerId"), sellerId));
            }
            if (rating != null) {
                where.add(cb.equal(root.get("rating"), rating));
            }
            if (replied != null) {
                where.add(replied ? cb.isNotNull(root.get("sellerReply")) : cb.isNull(root.get("sellerReply")));
            }
            if (productId != null) {
                where.add(cb.equal(root.get("productId"), productId));
            }
            if (query != null && !query.isBlank()) {
                String pattern = Like.contains(query);
                where.add(cb.or(cb.like(cb.lower(root.get("productName")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(root.get("customerName")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("comment"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("sellerName"), "")), pattern, Like.ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static Sort sort(String sort) {
        Sort newest = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        if (sort == null) {
            return newest;
        }
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "highest" -> Sort.by(Sort.Direction.DESC, "rating").and(newest);
            case "lowest" -> Sort.by(Sort.Direction.ASC, "rating").and(newest);
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            default -> newest;
        };
    }

    private static Map<Integer, Long> distribution(List<Object[]> rows) {
        Map<Integer, Long> distribution = new TreeMap<>();
        for (int stars = 1; stars <= 5; stars++) {
            distribution.put(stars, 0L);
        }
        rows.forEach(row -> distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue()));
        return distribution;
    }

    static BigDecimal average(Object value) {
        return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(1, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
