package com.enterprise.payment.strategy;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentMethod;
import com.enterprise.payment.processor.PaymentProcessor;
import com.enterprise.payment.processor.PaymentRequest;
import com.enterprise.payment.processor.PaymentResult;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Instant bank payment with a UPI-style payer address (name@bank). */
@Component
public class UpiPayment implements PaymentStrategy {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.UPI;
    }

    @Override
    public void validate(PaymentDetails details) {
        if (!upiId(details).matches("^[a-z0-9._-]{2,64}@[a-z]{2,32}$")) {
            throw ApiException.badRequest("The UPI ID must look like name@bank");
        }
    }

    @Override
    public PaymentResult pay(String orderNumber, BigDecimal amount, String currency, PaymentDetails details,
                             PaymentProcessor processor) {
        String upiId = upiId(details);
        return processor.process(new PaymentRequest(orderNumber, amount, currency, PaymentMethod.UPI, upiId, upiId));
    }

    private static String upiId(PaymentDetails details) {
        return details.upiId() == null ? "" : details.upiId().trim().toLowerCase(Locale.ROOT);
    }
}
