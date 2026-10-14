package com.enterprise.payment.processor;

import java.math.BigDecimal;

/** What a processor needs to charge a customer (raw card data is used in memory only, never stored). */
public record PaymentCommand(String orderNumber, BigDecimal amount, String currency, String cardNumber,
                             String cardHolder, String expiry, String cvv, String upiId) {
}
