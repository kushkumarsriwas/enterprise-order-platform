package com.enterprise.inventory.stock;

public enum ReservationStatus {
    /** Stock is held for an order that is waiting for payment. */
    RESERVED,
    /** Order was paid/confirmed: the stock is sold. */
    COMMITTED,
    /** Order was cancelled: stock went back to available. */
    RELEASED,
    /** Payment never arrived: the scheduler released the stock. */
    EXPIRED
}
