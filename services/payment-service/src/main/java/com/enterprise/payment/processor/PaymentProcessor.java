package com.enterprise.payment.processor;

import com.enterprise.payment.payment.PaymentMethod;

/**
 * Strategy pattern: one implementation per payment method. Adding a new method (e.g. Apple Pay)
 * means adding one class - the payment service itself does not change (Open/Closed principle).
 */
public interface PaymentProcessor {

    PaymentMethod method();

    ProcessorResult process(PaymentCommand command);
}
