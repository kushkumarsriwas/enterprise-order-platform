package com.enterprise.inventory.stock;

/** Every reason stock can change. Each change is one row in the stock ledger ({@link StockMovement}). */
public enum MovementType {
    /** First stock entered when a product is created. */
    OPENING_STOCK(true),
    /** Goods received from a supplier (with or without a purchase order). */
    RECEIVED(true),
    /** Units of a paid order left the warehouse. */
    SOLD(true),
    /** Units of a cancelled paid order came back to the warehouse. */
    RETURNED(true),
    /** Manual correction with a reason (damaged, lost, found...). */
    ADJUSTMENT(true),
    /** Difference found by a stock count. */
    COUNT_ADJUSTMENT(true),
    TRANSFER_OUT(true),
    TRANSFER_IN(true),
    /** Units held for an order awaiting payment (still on the shelf). */
    RESERVED(false),
    /** Reservation cancelled before payment. */
    RELEASED(false),
    /** Reservation released because the payment window passed. */
    EXPIRED(false);

    private final boolean physical;

    MovementType(boolean physical) {
        this.physical = physical;
    }

    /** True when units physically moved in or out of a warehouse (false for reservations). */
    public boolean isPhysical() {
        return physical;
    }
}
