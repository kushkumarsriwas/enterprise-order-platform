package com.enterprise.payment.processor;

import com.enterprise.payment.common.ApiException;
import org.springframework.stereotype.Component;

/**
 * Placeholder for a real payment gateway (for example Network International, Checkout.com or Stripe in the UAE).
 * A real implementation would call the gateway's API over HTTPS with a tokenised card and map its response to a
 * {@link PaymentResult}. Selected with {@code app.payments.provider=real}; until then it refuses to charge.
 */
@Component
public class FutureRealPaymentProcessor implements PaymentProcessor {

    @Override
    public String name() {
        return "real";
    }

    @Override
    public PaymentResult process(PaymentRequest request) {
        throw ApiException.serviceUnavailable("Live payments are not enabled yet: no payment gateway is configured");
    }
}
