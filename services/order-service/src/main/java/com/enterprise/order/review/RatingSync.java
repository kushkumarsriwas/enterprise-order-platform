package com.enterprise.order.review;

import com.enterprise.order.client.InventoryClient;
import com.enterprise.order.review.ReviewDtos.RatingChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the product rating shown in search results up to date: after a review is saved or removed (and only if
 * that transaction committed) the new average is pushed to the product service in the background.
 */
@Component
public class RatingSync {

    private static final Logger log = LoggerFactory.getLogger(RatingSync.class);

    private final ReviewService reviewService;
    private final InventoryClient inventoryClient;

    public RatingSync(ReviewService reviewService, InventoryClient inventoryClient) {
        this.reviewService = reviewService;
        this.inventoryClient = inventoryClient;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRatingChanged(RatingChanged event) {
        try {
            Object[] summary = reviewService.summaryOf(event.productId());
            inventoryClient.updateRating(event.productId(), ReviewService.average(summary[0]),
                    ((Number) summary[1]).longValue());
        } catch (RuntimeException ex) {
            log.warn("Rating of product {} could not be synchronised: {}", event.productId(), ex.getMessage());
        }
    }
}
