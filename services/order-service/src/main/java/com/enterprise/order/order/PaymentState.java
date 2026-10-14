package com.enterprise.order.order;

/** Payment situation of an order as seen by the order service. */
public enum PaymentState {
    UNPAID,
    PAID,
    PAY_ON_DELIVERY,
    REFUNDED,
    VOIDED
}
