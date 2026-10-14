package com.enterprise.inventory.purchasing;

import java.util.Set;

/**
 * Purchase order lifecycle (state machine):
 * DRAFT -> ORDERED -> PARTIALLY_RECEIVED -> RECEIVED, DRAFT/ORDERED -> CANCELLED,
 * PARTIALLY_RECEIVED -> CLOSED (the rest will not be delivered).
 */
public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED,
    CLOSED;

    /** Orders whose remaining quantities are still expected ("incoming stock"). */
    public static final Set<PurchaseOrderStatus> OPEN = Set.of(ORDERED, PARTIALLY_RECEIVED);

    public boolean canTransitionTo(PurchaseOrderStatus next) {
        return switch (this) {
            case DRAFT -> next == ORDERED || next == CANCELLED;
            case ORDERED -> next == PARTIALLY_RECEIVED || next == RECEIVED || next == CANCELLED;
            case PARTIALLY_RECEIVED -> next == PARTIALLY_RECEIVED || next == RECEIVED || next == CLOSED;
            case RECEIVED, CANCELLED, CLOSED -> false;
        };
    }
}
