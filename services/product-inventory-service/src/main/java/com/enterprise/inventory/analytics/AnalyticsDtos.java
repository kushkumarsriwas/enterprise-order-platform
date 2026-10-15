package com.enterprise.inventory.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record ValueSlice(String label, long products, long units, BigDecimal valueAtCost, BigDecimal valueAtPrice,
                             BigDecimal sharePercent) {
    }

    public record StockHealth(int inStock, int lowStock, int outOfStock, int excessStock, int inventoryOnly,
                              int listed) {
    }

    public record AbcClass(String abcClass, long products, BigDecimal revenue, BigDecimal revenueSharePercent,
                           BigDecimal stockValueAtCost) {
    }

    public record ProductInsight(Long productId, String sku, String name, String abcClass, long unitsSold,
                                 BigDecimal revenue, BigDecimal grossProfit, int onHand, BigDecimal stockValueAtCost,
                                 Integer daysOfCover) {
    }

    public record DeadStockItem(Long productId, String sku, String name, int onHand, BigDecimal stockValueAtCost,
                                Instant lastSoldAt, Integer daysSinceLastSale) {
    }

    public record ShrinkageRow(String reason, long units, BigDecimal valueAtCost) {
    }

    public record FlowPoint(String date, long unitsIn, long unitsOut) {
    }

    public record SpendRow(String label, long receipts, long units, BigDecimal amount) {
    }

    public record InventoryAnalytics(String from, String to, String currency,
                                     BigDecimal stockValueAtCost, BigDecimal stockValueAtPrice, long unitsOnHand,
                                     long unitsSold, BigDecimal revenue, BigDecimal costOfGoodsSold,
                                     BigDecimal grossProfit, BigDecimal turnoverRatio, Integer daysOfInventory,
                                     BigDecimal sellThroughPercent, BigDecimal deadStockValue,
                                     BigDecimal purchaseSpend, StockHealth health,
                                     List<ValueSlice> valueByCategory, List<ValueSlice> valueByWarehouse,
                                     List<AbcClass> abcSummary, List<ProductInsight> topMovers,
                                     List<ProductInsight> abcProducts, List<DeadStockItem> deadStock,
                                     List<ShrinkageRow> shrinkage, List<FlowPoint> flow,
                                     List<SpendRow> spendBySupplier, List<SpendRow> spendByMonth) {
    }
}
