package com.enterprise.payment.strategy;

import com.enterprise.payment.payment.PaymentMethod;
import com.enterprise.payment.processor.PaymentProcessor;
import com.enterprise.payment.processor.PaymentResult;
import java.math.BigDecimal;

/**
 * Strategy pattern: how each payment method is validated and executed.
 * <pre>
 * PaymentStrategy
 *   |- CardPayment     card number (Luhn), expiry, CVV -> processor
 *   |- UpiPayment      UPI ID -> processor
 *   \- CashOnDelivery  nothing charged now; collected by the courier
 * </pre>
 * A new method (e.g. Apple Pay) is one new class; the payment service does not change (Open/Closed principle).
 */
public interface PaymentStrategy {

    PaymentMethod method();

    /** Rejects invalid details (400) before the order is locked or anything is charged. */
    void validate(PaymentDetails details);

    PaymentResult pay(String orderNumber, BigDecimal amount, String currency, PaymentDetails details,
                      PaymentProcessor processor);
}
