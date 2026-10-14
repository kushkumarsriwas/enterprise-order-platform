package com.enterprise.inventory.stock;

/** Why a seller corrected stock by hand (reported as shrinkage when units are lost). */
public enum AdjustmentReason {
    DAMAGED("Damaged"),
    EXPIRED("Expired"),
    LOST("Lost or stolen"),
    FOUND("Found"),
    CORRECTION("Data correction"),
    INTERNAL_USE("Internal use / samples"),
    COUNT("Stock count"),
    OTHER("Other");

    private final String label;

    AdjustmentReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
