package com.enterprise.payment.payment;

public enum PaymentStatus {
    /** Money captured. */
    SUCCESS,
    /** Declined by the (mock) provider. */
    FAILED,
    /** Cash on delivery: authorised, collected when the order is delivered. */
    PENDING,
    /** Captured money returned to the customer. */
    REFUNDED,
    /** Pending cash-on-delivery payment cancelled before collection. */
    CANCELLED
}
