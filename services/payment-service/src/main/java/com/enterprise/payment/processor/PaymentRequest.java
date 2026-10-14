package com.enterprise.payment.processor;

import com.enterprise.payment.payment.PaymentMethod;
import java.math.BigDecimal;

/**
 * What is sent to the payment provider. {@code instrument} is the raw card number or UPI ID: it is used in
 * memory only and never stored or logged (PCI-DSS) - only {@code maskedInstrument} is persisted.
 */
public record PaymentRequest(String orderNumber, BigDecimal amount, String currency, PaymentMethod method,
                             String instrument, String maskedInstrument) {

    @Override
    public String toString() {
        return "PaymentRequest[" + orderNumber + ", " + currency + " " + amount + ", " + method + ", "
                + maskedInstrument + "]";
    }
}
