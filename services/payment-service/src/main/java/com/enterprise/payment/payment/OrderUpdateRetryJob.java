package com.enterprise.payment.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reliability net of the payment saga: payments whose result could not be delivered to the order service
 * (it was down or slow) are reported again until it acknowledges them. Safe because the order service treats a
 * repeated report of the same payment as a no-op (idempotent consumer).
 */
@Component
public class OrderUpdateRetryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderUpdateRetryJob.class);

    private final PaymentService paymentService;

    public OrderUpdateRetryJob(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${app.payments.order-update-retry-interval:60s}", initialDelayString = "45s")
    public void retry() {
        try {
            int delivered = paymentService.retryOrderUpdates(50);
            if (delivered > 0) {
                log.info("Delivered {} pending payment result(s) to the order service", delivered);
            }
        } catch (Exception ex) {
            log.warn("Payment result retry failed: {}", ex.getMessage());
        }
    }
}
