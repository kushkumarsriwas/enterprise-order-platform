package com.enterprise.inventory.purchasing;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderLineRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReplenishmentItem;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReplenishmentOrderRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReplenishmentSuggestion;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.InventoryRepository;
import com.enterprise.inventory.stock.MovementType;
import com.enterprise.inventory.stock.StockMovementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replenishment planning: which products to reorder, how many, from whom.
 * A product needs stock when (available + incoming) is at or below its reorder point, or when the expected
 * demand during the supplier's lead time (+7 safety days) is more than what is available or on the way.
 * Suggested quantity = enough for the lead time + 30 days of average demand, at least the reorder quantity.
 */
@Service
public class ReplenishmentService {

    static final int DEMAND_WINDOW_DAYS = 30;
    static final int SAFETY_DAYS = 7;
    static final int COVER_DAYS = 30;
    private static final Set<MovementType> SALES = Set.of(MovementType.SOLD, MovementType.RETURNED);

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final PurchaseOrderRepository purchaseOrders;
    private final StockMovementRepository movements;
    private final PurchaseOrderService purchaseOrderService;

    public ReplenishmentService(ProductRepository productRepository, InventoryRepository inventoryRepository,
                                PurchaseOrderRepository purchaseOrders, StockMovementRepository movements,
                                PurchaseOrderService purchaseOrderService) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.purchaseOrders = purchaseOrders;
        this.movements = movements;
        this.purchaseOrderService = purchaseOrderService;
    }

    @Transactional(readOnly = true)
    public List<ReplenishmentSuggestion> suggestions(Long sellerId, Long supplierId, boolean onlyNeeded) {
        List<Product> products = productRepository.findBySellerIdAndActiveTrue(sellerId).stream()
                .filter(p -> supplierId == null || (p.getPreferredSupplier() != null
                        && p.getPreferredSupplier().getId().equals(supplierId)))
                .toList();
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, Inventory> stock = inventoryRepository.findByProductIdIn(ids).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        Map<Long, Integer> incoming = new HashMap<>();
        purchaseOrders.incomingByProduct(sellerId, PurchaseOrderStatus.OPEN, ids)
                .forEach(row -> incoming.put((Long) row[0], ((Number) row[1]).intValue()));
        Map<Long, Long> sold = new HashMap<>();
        movements.unitsSoldSince(sellerId, SALES, Instant.now().minus(DEMAND_WINDOW_DAYS, ChronoUnit.DAYS))
                .forEach(row -> sold.put((Long) row[0], ((Number) row[1]).longValue()));

        List<ReplenishmentSuggestion> result = new ArrayList<>();
        for (Product p : products) {
            Inventory inventory = stock.get(p.getId());
            int available = inventory == null ? 0 : inventory.getAvailableQuantity();
            int onTheWay = incoming.getOrDefault(p.getId(), 0);
            BigDecimal daily = BigDecimal.valueOf(Math.max(sold.getOrDefault(p.getId(), 0L), 0))
                    .divide(BigDecimal.valueOf(DEMAND_WINDOW_DAYS), 2, RoundingMode.HALF_UP);
            int leadTime = p.getPreferredSupplier() == null ? 7 : p.getPreferredSupplier().getLeadTimeDays();
            int position = available + onTheWay;
            int demandUntilRestock = daily.multiply(BigDecimal.valueOf(leadTime + SAFETY_DAYS))
                    .setScale(0, RoundingMode.CEILING).intValue();
            boolean belowReorderPoint = position <= p.getReorderPoint();
            boolean runsOut = daily.signum() > 0 && position < demandUntilRestock;
            if (onlyNeeded && !belowReorderPoint && !runsOut) {
                continue;
            }
            int coverTarget = daily.multiply(BigDecimal.valueOf(leadTime + COVER_DAYS)).setScale(0, RoundingMode.CEILING)
                    .intValue();
            int target = Math.max(p.getReorderPoint() + Math.max(p.getReorderQuantity(), 1), coverTarget);
            int suggested = Math.max(Math.max(p.getReorderQuantity(), 1), target - position);
            Integer daysOfCover = daily.signum() > 0
                    ? BigDecimal.valueOf(available).divide(daily, 0, RoundingMode.FLOOR).intValue() : null;
            String reason = belowReorderPoint
                    ? (available == 0 ? "Out of stock" : "At or below the reorder point (" + p.getReorderPoint() + ")")
                    : runsOut ? "Runs out in about " + daysOfCover + " day(s); supplier lead time is " + leadTime + " day(s)"
                    : "Healthy";
            result.add(new ReplenishmentSuggestion(p.getId(), p.getSku(), p.getName(),
                    p.getCategory() == null ? null : p.getCategory().getName(),
                    p.getPreferredSupplier() == null ? null : p.getPreferredSupplier().getId(),
                    p.getPreferredSupplier() == null ? null : p.getPreferredSupplier().getName(), available, onTheWay,
                    p.getReorderPoint(), p.getReorderQuantity(), daily, daysOfCover, leadTime, suggested,
                    p.getCostPrice(), p.getCostPrice().multiply(BigDecimal.valueOf(suggested)).setScale(2, RoundingMode.HALF_UP),
                    reason));
        }
        result.sort(Comparator.comparing((ReplenishmentSuggestion s) -> s.availableQuantity() > 0)
                .thenComparing(s -> s.daysOfCover() == null ? Integer.MAX_VALUE : s.daysOfCover())
                .thenComparing(ReplenishmentSuggestion::sku));
        return result;
    }

    /** One draft purchase order per supplier from the selected suggestions (the seller reviews and sends them). */
    @Transactional
    public List<PurchaseOrderResponse> createPurchaseOrders(Long sellerId, String actor, ReplenishmentOrderRequest request) {
        Map<Long, List<PurchaseOrderLineRequest>> bySupplier = new LinkedHashMap<>();
        for (ReplenishmentItem item : request.items()) {
            Product product = productRepository.findByIdAndSellerId(item.productId(), sellerId)
                    .filter(Product::isActive)
                    .orElseThrow(() -> ApiException.notFound("Product " + item.productId() + " not found in your inventory"));
            Long supplierId = item.supplierId() != null ? item.supplierId()
                    : product.getPreferredSupplier() == null ? null : product.getPreferredSupplier().getId();
            if (supplierId == null) {
                throw ApiException.badRequest("Choose a supplier for " + product.getSku()
                        + " (or set a preferred supplier on the product)");
            }
            bySupplier.computeIfAbsent(supplierId, id -> new ArrayList<>())
                    .add(new PurchaseOrderLineRequest(product.getId(), item.quantity(), null));
        }
        List<PurchaseOrderResponse> created = new ArrayList<>();
        bySupplier.forEach((supplierId, lines) -> created.add(purchaseOrderService.create(sellerId, actor,
                new PurchaseOrderRequest(supplierId, request.warehouseId(), null,
                        request.notes() == null ? "Created from replenishment suggestions" : request.notes(), lines))));
        return created;
    }
}
