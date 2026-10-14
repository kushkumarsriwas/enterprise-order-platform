package com.enterprise.inventory.notify;

/** Published inside the stock transaction; handled after commit (see {@link LowStockNotifier}). */
public record LowStockEvent(Long merchantId, Long productId, String sku, String productName, int available,
                            int reorderLevel, String bearerToken) {
}
