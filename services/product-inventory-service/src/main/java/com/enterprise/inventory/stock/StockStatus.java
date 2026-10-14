package com.enterprise.inventory.stock;

/** Health of a product's stock compared with its reorder point. */
public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    public static StockStatus of(int available, int reorderPoint) {
        if (available <= 0) {
            return OUT_OF_STOCK;
        }
        return available <= reorderPoint ? LOW_STOCK : IN_STOCK;
    }
}
