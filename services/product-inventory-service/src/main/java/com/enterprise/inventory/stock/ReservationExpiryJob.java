package com.enterprise.inventory.stock;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases stock held by orders that were never paid (reservation TTL expired). */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final InventoryService inventoryService;

    public ReservationExpiryJob(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Scheduled(fixedDelayString = "${app.reservation.expiry-check-interval:60s}", initialDelayString = "30s")
    public void releaseExpiredReservations() {
        try {
            int released = inventoryService.expireOverdue(Instant.now(), 100);
            if (released > 0) {
                log.info("Released {} expired stock reservation(s)", released);
            }
        } catch (Exception ex) {
            log.warn("Reservation expiry job failed: {}", ex.getMessage());
        }
    }
}
