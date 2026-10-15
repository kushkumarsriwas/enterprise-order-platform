package com.enterprise.payment.processor;

/**
 * The payment provider (acquirer / bank) behind the platform. Programming to this interface means the
 * mock used today can be replaced by a real gateway without touching the payment service (Dependency Inversion):
 * <pre>
 * PaymentProcessor
 *   |- MockPaymentProcessor        simulated bank: SUCCESS / FAILED (default)
 *   \- FutureRealPaymentProcessor  where a real gateway is integrated (app.payments.provider=real)
 * </pre>
 */
public interface PaymentProcessor {

    /** Configuration name of the provider: "mock" or "real". */
    String name();

    PaymentResult process(PaymentRequest request);
}
