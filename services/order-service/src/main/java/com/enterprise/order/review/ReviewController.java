package com.enterprise.order.review;

import com.enterprise.order.common.PageResponse;
import com.enterprise.order.review.ReviewDtos.AwaitingReview;
import com.enterprise.order.review.ReviewDtos.CreateReview;
import com.enterprise.order.review.ReviewDtos.ProductReviews;
import com.enterprise.order.review.ReviewDtos.ReplyRequest;
import com.enterprise.order.review.ReviewDtos.ReviewResponse;
import com.enterprise.order.review.ReviewDtos.StoreReviews;
import com.enterprise.order.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/reviews")
    @Operation(summary = "Reviews of a product with the rating summary (public; filter by stars, sort)")
    public ProductReviews forProduct(@RequestParam Long productId, @RequestParam(required = false) Integer rating,
                                     @RequestParam(defaultValue = "newest") String sort,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return reviewService.forProduct(productId, rating, sort, page, size);
    }

    @GetMapping("/api/reviews/mine")
    @Operation(summary = "Reviews I have written")
    public PageResponse<ReviewResponse> mine(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return reviewService.mine(user, page, size);
    }

    @GetMapping("/api/reviews/pending")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Delivered products I have not rated yet")
    public List<AwaitingReview> pending(@AuthenticationPrincipal AuthenticatedUser user) {
        return reviewService.awaitingReview(user);
    }

    @PostMapping("/api/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Review a product from one of my delivered orders (verified purchase)")
    public ReviewResponse create(@Valid @RequestBody CreateReview request,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return reviewService.create(user, request);
    }

    @GetMapping("/api/seller/reviews")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Reviews of my products with rating summary (filter: stars, replied, product, text)")
    public StoreReviews forSeller(@RequestParam(required = false) Integer rating,
                                  @RequestParam(required = false) Boolean replied,
                                  @RequestParam(required = false) Long productId,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "newest") String sort,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @AuthenticationPrincipal AuthenticatedUser seller) {
        return reviewService.forStore(seller.id(), rating, replied, productId, q, sort, page, size);
    }

    @PutMapping("/api/seller/reviews/{id}/reply")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Publicly answer a review of one of my products")
    public ReviewResponse reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest request,
                                @AuthenticationPrincipal AuthenticatedUser seller) {
        return reviewService.reply(seller, id, request.reply());
    }

    @GetMapping("/api/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All reviews on the marketplace (filter: store, stars, replied, product, text)")
    public StoreReviews all(@RequestParam(required = false) Long sellerId,
                            @RequestParam(required = false) Integer rating,
                            @RequestParam(required = false) Boolean replied,
                            @RequestParam(required = false) Long productId,
                            @RequestParam(required = false) String q,
                            @RequestParam(defaultValue = "newest") String sort,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return reviewService.all(sellerId, rating, replied, productId, q, sort, page, size);
    }

    @DeleteMapping("/api/admin/reviews/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove a review that breaks the guidelines (the product rating is recalculated)")
    public ResponseEntity<Void> remove(@PathVariable Long id, @RequestParam(required = false) String reason) {
        reviewService.remove(id, reason);
        return ResponseEntity.noContent().build();
    }
}
