package com.enterprise.order.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderService orderService;

    public OrderExpiryJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.orders.expiry-check-interval:60s}", initialDelayString = "30s")
    public void cancelUnpaidOrders() {
        try {
            int expired = orderService.expireUnpaidOrders(100);
            if (expired > 0) {
                log.info("Cancelled {} unpaid order(s) after the payment window", expired);
            }
        } catch (Exception ex) {
            log.warn("Order expiry job failed: {}", ex.getMessage());
        }
    }
}
