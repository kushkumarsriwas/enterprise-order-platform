package com.enterprise.payment.strategy;

/** Payment details typed by the customer. Card data lives in memory only and is never stored. */
public record PaymentDetails(String cardNumber, String cardHolder, String expiry, String cvv, String upiId) {

    @Override
    public String toString() {
        return "PaymentDetails[hidden]";
    }
}
