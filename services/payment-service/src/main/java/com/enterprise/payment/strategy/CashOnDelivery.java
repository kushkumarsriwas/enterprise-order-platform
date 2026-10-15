package com.enterprise.payment.strategy;

import com.enterprise.payment.payment.PaymentMethod;
import com.enterprise.payment.processor.PaymentProcessor;
import com.enterprise.payment.processor.PaymentResult;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Nothing is charged now: the payment stays PENDING until the courier collects the cash, which the seller
 * confirms when marking the order delivered. Limited to AED 5,000 per order.
 */
@Component
public class CashOnDelivery implements PaymentStrategy {

    public static final BigDecimal LIMIT = new BigDecimal("5000.00");

    @Override
    public PaymentMethod method() {
        return PaymentMethod.COD;
    }

    @Override
    public void validate(PaymentDetails details) {
        // no details needed
    }

    @Override
    public PaymentResult pay(String orderNumber, BigDecimal amount, String currency, PaymentDetails details,
                             PaymentProcessor processor) {
        String reference = "COD-" + orderNumber;
        if (amount.compareTo(LIMIT) > 0) {
            return PaymentResult.failed(reference, "Cash on delivery",
                    "Cash on delivery is only available for orders up to " + currency + " " + LIMIT + ".");
        }
        return PaymentResult.pending(reference, "Cash on delivery");
    }
}
