package com.enterprise.inventory.analytics;

import com.enterprise.inventory.analytics.AnalyticsDtos.AbcClass;
import com.enterprise.inventory.analytics.AnalyticsDtos.DeadStockItem;
import com.enterprise.inventory.analytics.AnalyticsDtos.FlowPoint;
import com.enterprise.inventory.analytics.AnalyticsDtos.InventoryAnalytics;
import com.enterprise.inventory.analytics.AnalyticsDtos.ProductInsight;
import com.enterprise.inventory.analytics.AnalyticsDtos.ShrinkageRow;
import com.enterprise.inventory.analytics.AnalyticsDtos.SpendRow;
import com.enterprise.inventory.analytics.AnalyticsDtos.StockHealth;
import com.enterprise.inventory.analytics.AnalyticsDtos.ValueSlice;
import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductMapper;
import com.enterprise.inventory.purchasing.GoodsReceiptRepository;
import com.enterprise.inventory.stock.AdjustmentReason;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.InventoryRepository;
import com.enterprise.inventory.stock.MovementType;
import com.enterprise.inventory.stock.StockMovementRepository;
import com.enterprise.inventory.stock.StockStatus;
import com.enterprise.inventory.stock.WarehouseStockRepository;
import com.enterprise.inventory.warehouse.Warehouse;
import com.enterprise.inventory.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventory analytics computed from the stock position and the stock ledger:
 * valuation, stock health, ABC classification (80% / 15% / 5% of revenue), dead stock, turnover,
 * sell-through, shrinkage by reason, daily inbound/outbound flow and purchase spend.
 */
@Service
public class InventoryAnalyticsService {

    private static final Set<MovementType> SALES = Set.of(MovementType.SOLD, MovementType.RETURNED);
    private static final Set<MovementType> CORRECTIONS = Set.of(MovementType.ADJUSTMENT, MovementType.COUNT_ADJUSTMENT);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movements;
    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final GoodsReceiptRepository receipts;

    public InventoryAnalyticsService(InventoryRepository inventoryRepository, StockMovementRepository movements,
                                     WarehouseStockRepository stockRepository, WarehouseRepository warehouseRepository,
                                     GoodsReceiptRepository receipts) {
        this.inventoryRepository = inventoryRepository;
        this.movements = movements;
        this.stockRepository = stockRepository;
        this.warehouseRepository = warehouseRepository;
        this.receipts = receipts;
    }

    @Transactional(readOnly = true)
    public InventoryAnalytics analyse(Long sellerId, LocalDate from, LocalDate to, int deadStockDays) {
        Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long days = Math.max(1, ChronoUnit.DAYS.between(from, to) + 1);
        List<Inventory> stock = inventoryRepository.findActiveBySeller(sellerId);

        // sales from the ledger (net of returns)
        Map<Long, long[]> units = new HashMap<>();
        Map<Long, BigDecimal[]> money = new HashMap<>();
        for (Object[] row : movements.salesByProduct(sellerId, SALES, start, end)) {
            Long productId = (Long) row[0];
            units.put(productId, new long[]{((Number) row[1]).longValue()});
            money.put(productId, new BigDecimal[]{decimal(row[2]), decimal(row[3])});
        }
        Map<Long, Instant> lastSale = new HashMap<>();
        movements.lastSaleByProduct(sellerId).forEach(row -> lastSale.put((Long) row[0], (Instant) row[1]));

        BigDecimal valueAtCost = BigDecimal.ZERO;
        BigDecimal valueAtPrice = BigDecimal.ZERO;
        long onHandTotal = 0;
        long soldTotal = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        int inStock = 0;
        int low = 0;
        int out = 0;
        int excess = 0;
        int listed = 0;
        Map<String, BigDecimal[]> byCategory = new TreeMap<>();
        Map<String, long[]> categoryCounts = new HashMap<>();
        List<ProductInsight> insights = new ArrayList<>();
        List<DeadStockItem> dead = new ArrayList<>();
        Instant deadCutoff = Instant.now().minus(deadStockDays, ChronoUnit.DAYS);
        for (Inventory inventory : stock) {
            Product p = inventory.getProduct();
            int onHand = inventory.onHand();
            BigDecimal cost = p.getCostPrice().multiply(BigDecimal.valueOf(onHand));
            BigDecimal retail = p.getPrice().multiply(BigDecimal.valueOf(onHand));
            valueAtCost = valueAtCost.add(cost);
            valueAtPrice = valueAtPrice.add(retail);
            onHandTotal += onHand;
            long sold = units.containsKey(p.getId()) ? units.get(p.getId())[0] : 0;
            BigDecimal productRevenue = money.containsKey(p.getId()) ? money.get(p.getId())[0] : BigDecimal.ZERO;
            BigDecimal productCost = money.containsKey(p.getId()) ? money.get(p.getId())[1] : BigDecimal.ZERO;
            soldTotal += sold;
            revenue = revenue.add(productRevenue);
            cogs = cogs.add(productCost);
            listed += p.isListed() ? 1 : 0;
            StockStatus status = inventory.status(p.getReorderPoint());
            Integer daysOfCover = sold > 0 ? (int) Math.floor(inventory.getAvailableQuantity() / ((double) sold / days)) : null;
            switch (status) {
                case IN_STOCK -> inStock++;
                case LOW_STOCK -> low++;
                case OUT_OF_STOCK -> out++;
            }
            if (daysOfCover != null && daysOfCover > 120) {
                excess++;
            }
            String category = p.getCategory() == null ? "Uncategorised" : p.getCategory().getName();
            BigDecimal[] slice = byCategory.computeIfAbsent(category, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            slice[0] = slice[0].add(cost);
            slice[1] = slice[1].add(retail);
            long[] counts = categoryCounts.computeIfAbsent(category, k -> new long[2]);
            counts[0]++;
            counts[1] += onHand;
            insights.add(new ProductInsight(p.getId(), p.getSku(), p.getName(), null, sold, money(productRevenue),
                    money(productRevenue.subtract(productCost)), onHand, money(cost), daysOfCover));
            Instant last = lastSale.get(p.getId());
            if (onHand > 0 && (last == null || last.isBefore(deadCutoff)) && p.getCreatedAt().isBefore(deadCutoff)) {
                dead.add(new DeadStockItem(p.getId(), p.getSku(), p.getName(), onHand, money(cost), last,
                        last == null ? null : (int) ChronoUnit.DAYS.between(last, Instant.now())));
            }
        }

        // ABC classification by revenue share
        List<ProductInsight> ranked = new ArrayList<>(insights);
        ranked.sort(Comparator.comparing(ProductInsight::revenue).reversed().thenComparing(ProductInsight::sku));
        Map<Long, String> classes = new HashMap<>();
        BigDecimal running = BigDecimal.ZERO;
        Map<String, Object[]> abc = new LinkedHashMap<>();
        for (String c : List.of("A", "B", "C")) {
            abc.put(c, new Object[]{0L, BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (ProductInsight insight : ranked) {
            String cls;
            if (revenue.signum() == 0 || insight.revenue().signum() == 0) {
                cls = "C";
            } else {
                BigDecimal before = running.multiply(HUNDRED).divide(revenue, 4, RoundingMode.HALF_UP);
                cls = before.compareTo(BigDecimal.valueOf(80)) < 0 ? "A"
                        : before.compareTo(BigDecimal.valueOf(95)) < 0 ? "B" : "C";
                running = running.add(insight.revenue());
            }
            classes.put(insight.productId(), cls);
            Object[] totals = abc.get(cls);
            totals[0] = (Long) totals[0] + 1;
            totals[1] = ((BigDecimal) totals[1]).add(insight.revenue());
            totals[2] = ((BigDecimal) totals[2]).add(insight.stockValueAtCost());
        }
        List<AbcClass> abcSummary = new ArrayList<>();
        BigDecimal totalRevenue = revenue;
        abc.forEach((cls, totals) -> abcSummary.add(new AbcClass(cls, (Long) totals[0], money((BigDecimal) totals[1]),
                percent((BigDecimal) totals[1], totalRevenue), money((BigDecimal) totals[2]))));
        List<ProductInsight> classified = ranked.stream().map(i -> new ProductInsight(i.productId(), i.sku(), i.name(),
                classes.get(i.productId()), i.unitsSold(), i.revenue(), i.grossProfit(), i.onHand(), i.stockValueAtCost(),
                i.daysOfCover())).toList();
        List<ProductInsight> topMovers = classified.stream().filter(i -> i.unitsSold() > 0)
                .sorted(Comparator.comparing(ProductInsight::unitsSold).reversed()).limit(10).toList();

        // valuation slices
        BigDecimal totalCost = valueAtCost;
        List<ValueSlice> categories = new ArrayList<>();
        byCategory.forEach((name, values) -> categories.add(new ValueSlice(name, categoryCounts.get(name)[0],
                categoryCounts.get(name)[1], money(values[0]), money(values[1]), percent(values[0], totalCost))));
        categories.sort(Comparator.comparing(ValueSlice::valueAtCost).reversed());
        Map<Long, Warehouse> warehouses = new HashMap<>();
        warehouseRepository.findBySellerIdOrderByDefaultWarehouseDescCodeAsc(sellerId).forEach(w -> warehouses.put(w.getId(), w));
        List<ValueSlice> byWarehouse = new ArrayList<>();
        for (Object[] row : stockRepository.totalsByWarehouse(sellerId)) {
            Warehouse w = warehouses.get((Long) row[0]);
            BigDecimal value = decimal(row[3]);
            byWarehouse.add(new ValueSlice(w == null ? "#" + row[0] : w.label(), ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(), money(value), null, percent(value, totalCost)));
        }

        // shrinkage and flow
        List<ShrinkageRow> shrinkage = new ArrayList<>();
        for (Object[] row : movements.adjustmentsByReason(sellerId, CORRECTIONS, start, end)) {
            AdjustmentReason reason = (AdjustmentReason) row[0];
            shrinkage.add(new ShrinkageRow(reason == null ? "Other" : reason.label(), ((Number) row[1]).longValue(),
                    money(decimal(row[2]))));
        }
        Map<String, long[]> flow = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to) && flow.size() < 400; d = d.plusDays(1)) {
            flow.put(d.toString(), new long[2]);
        }
        for (Object[] row : movements.dailyFlow(sellerId, start, end)) {
            MovementType type = (MovementType) row[1];
            if (type == MovementType.TRANSFER_IN || type == MovementType.TRANSFER_OUT) {
                continue;
            }
            long quantity = ((Number) row[2]).longValue();
            long[] point = flow.computeIfAbsent(row[0].toString(), k -> new long[2]);
            if (quantity > 0) {
                point[0] += quantity;
            } else {
                point[1] += -quantity;
            }
        }
        List<FlowPoint> flowPoints = new ArrayList<>();
        flow.forEach((day, values) -> flowPoints.add(new FlowPoint(day, values[0], values[1])));

        // purchases
        List<SpendRow> bySupplier = receipts.spendBySupplier(sellerId, start, end).stream()
                .map(r -> new SpendRow((String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue(),
                        money(decimal(r[3])))).toList();
        List<SpendRow> byMonth = receipts.spendByMonth(sellerId, start, end).stream()
                .map(r -> new SpendRow(Month.of(((Number) r[1]).intValue()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        + " " + r[0], 0, 0, money(decimal(r[2])))).toList();
        BigDecimal spend = bySupplier.stream().map(SpendRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal turnover = valueAtCost.signum() == 0 ? BigDecimal.ZERO
                : cogs.divide(valueAtCost, 2, RoundingMode.HALF_UP);
        Integer daysOfInventory = turnover.signum() == 0 ? null
                : BigDecimal.valueOf(days).divide(turnover, 0, RoundingMode.HALF_UP).intValue();
        BigDecimal sellThrough = soldTotal + onHandTotal == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(soldTotal).multiply(HUNDRED).divide(BigDecimal.valueOf(soldTotal + onHandTotal), 1,
                RoundingMode.HALF_UP);
        BigDecimal deadValue = dead.stream().map(DeadStockItem::stockValueAtCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        dead.sort(Comparator.comparing(DeadStockItem::stockValueAtCost).reversed());

        return new InventoryAnalytics(from.toString(), to.toString(), ProductMapper.CURRENCY, money(valueAtCost),
                money(valueAtPrice), onHandTotal, soldTotal, money(revenue), money(cogs), money(revenue.subtract(cogs)),
                turnover, daysOfInventory, sellThrough, money(deadValue), money(spend),
                new StockHealth(inStock, low, out, excess, stock.size() - listed, listed), categories, byWarehouse,
                abcSummary, topMovers, classified, dead, shrinkage, flowPoints, bySupplier, byMonth);
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO : part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
