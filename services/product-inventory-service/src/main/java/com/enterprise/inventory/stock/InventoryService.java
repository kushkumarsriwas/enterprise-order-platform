package com.enterprise.inventory.stock;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.ProductMapper;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.notify.LowStockEvent;
import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.security.CurrentUser;
import com.enterprise.inventory.stock.StockDtos.AdjustStockRequest;
import com.enterprise.inventory.stock.StockDtos.InventoryResponse;
import com.enterprise.inventory.stock.StockDtos.InventorySummary;
import com.enterprise.inventory.stock.StockDtos.MovementResponse;
import com.enterprise.inventory.stock.StockDtos.ProductLookup;
import com.enterprise.inventory.stock.StockDtos.PurchaseResponse;
import com.enterprise.inventory.stock.StockDtos.ReserveItem;
import com.enterprise.inventory.stock.StockDtos.ReserveRequest;
import com.enterprise.inventory.stock.StockDtos.ReservationResponse;
import com.enterprise.inventory.stock.StockDtos.ReservedLine;
import com.enterprise.inventory.stock.StockDtos.RestockRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock workflow. Order saga: reserve (order placed) -> commit (paid) or release (cancelled / payment timeout).
 * Merchant operations: goods received at a unit cost (weighted average cost), stock counts, audit trail.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMovementRepository movementRepository;
    private final StockPurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final ProductCache productCache;
    private final ApplicationEventPublisher events;
    private final Duration reservationTtl;

    public InventoryService(InventoryRepository inventoryRepository, StockReservationRepository reservationRepository,
                            StockMovementRepository movementRepository, StockPurchaseRepository purchaseRepository,
                            ProductRepository productRepository, ProductCache productCache,
                            ApplicationEventPublisher events,
                            @Value("${app.reservation.ttl:40m}") Duration reservationTtl) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.movementRepository = movementRepository;
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.productCache = productCache;
        this.events = events;
        this.reservationTtl = reservationTtl;
    }

    // ------------------------------------------------------------------ order saga (internal API)

    /** Merchant, price and availability of products, used by the order service to split a cart per merchant. */
    @Transactional(readOnly = true)
    public List<ProductLookup> lookup(List<Long> productIds) {
        Map<Long, Inventory> stock = inventoryRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        return productRepository.findAllById(productIds).stream()
                .map(p -> new ProductLookup(p.getId(), p.getMerchantId(), p.getMerchantName(), p.getName(),
                        p.getPrice(), p.isPurchasable(),
                        stock.containsKey(p.getId()) ? stock.get(p.getId()).getAvailableQuantity() : 0))
                .toList();
    }

    /**
     * All-or-nothing reservation for ONE merchant's order. Products are processed in id order (consistent
     * lock ordering prevents deadlocks); if any line is short of stock the whole transaction rolls back.
     * Idempotent per orderRef.
     */
    @Transactional
    public ReservationResponse reserve(ReserveRequest request) {
        List<StockReservation> existing = reservationRepository.findByOrderRefOrderByProductId(request.orderRef());
        if (!existing.isEmpty()) {
            log.info("Reservation for {} already exists - returning it (idempotent retry)", request.orderRef());
            return toResponse(request.orderRef(), existing);
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        for (ReserveItem item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(reservationTtl);
        Long merchantId = null;
        List<StockReservation> created = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = productRepository.findById(productId)
                    .filter(Product::isPurchasable)
                    .orElseThrow(() -> ApiException.notFound("Product " + productId + " is not available for sale"));
            if (merchantId != null && !merchantId.equals(product.getMerchantId())) {
                throw ApiException.badRequest("All products of one order must belong to the same merchant");
            }
            merchantId = product.getMerchantId();
            if (inventoryRepository.reserve(productId, quantity, now) == 0) {
                int available = inventoryRepository.findById(productId)
                        .map(Inventory::getAvailableQuantity).orElse(0);
                throw ApiException.conflict("Insufficient stock for '" + product.getName() + "': requested "
                        + quantity + ", available " + available);
            }
            created.add(reservationRepository.save(
                    new StockReservation(request.orderRef(), productId, quantity, expiresAt)));
            movementRepository.save(new StockMovement(product.getMerchantId(), productId, MovementType.RESERVED,
                    -quantity, request.orderRef(), "Reserved for order", actor()));
            productCache.evictProduct(productId);
        }
        log.info("Reserved stock for order {} ({} product(s))", request.orderRef(), created.size());
        publishLowStockAlerts(quantities.keySet());
        return toResponse(request.orderRef(), created);
    }

    /** Order paid: reserved units become sold. Safe to call twice. */
    @Transactional
    public ReservationResponse commit(String orderRef) {
        List<StockReservation> reservations = findReservations(orderRef);
        Instant now = Instant.now();
        for (StockReservation reservation : reservations) {
            switch (reservation.getStatus()) {
                case COMMITTED -> { }
                case RESERVED -> {
                    if (inventoryRepository.commitReserved(reservation.getProductId(), reservation.getQuantity(), now) == 0) {
                        throw ApiException.conflict("Reserved quantity mismatch for product " + reservation.getProductId());
                    }
                    reservation.changeStatus(ReservationStatus.COMMITTED);
                    movementRepository.save(new StockMovement(merchantOf(reservation.getProductId()),
                            reservation.getProductId(), MovementType.SOLD, 0, orderRef, "Order paid - units sold", actor()));
                    productCache.evictProduct(reservation.getProductId());
                }
                case RELEASED, EXPIRED -> throw ApiException.conflict(
                        "Reservation for order " + orderRef + " is no longer active (" + reservation.getStatus() + ")");
            }
        }
        log.info("Committed stock for order {}", orderRef);
        return toResponse(orderRef, reservations);
    }

    /** Order cancelled: put the units back on the shelf (also after commit, as a return). Safe to call twice. */
    @Transactional
    public ReservationResponse release(String orderRef) {
        List<StockReservation> reservations = findReservations(orderRef);
        Instant now = Instant.now();
        for (StockReservation reservation : reservations) {
            Long productId = reservation.getProductId();
            int quantity = reservation.getQuantity();
            switch (reservation.getStatus()) {
                case RESERVED -> {
                    inventoryRepository.releaseReserved(productId, quantity, now);
                    movementRepository.save(new StockMovement(merchantOf(productId), productId, MovementType.RELEASED,
                            quantity, orderRef, "Order cancelled before payment", actor()));
                }
                case COMMITTED -> {
                    inventoryRepository.addStock(productId, quantity, now);
                    movementRepository.save(new StockMovement(merchantOf(productId), productId, MovementType.RETURNED,
                            quantity, orderRef, "Paid order cancelled - stock returned", actor()));
                }
                case RELEASED, EXPIRED -> {
                    continue;
                }
            }
            reservation.changeStatus(ReservationStatus.RELEASED);
            productCache.evictProduct(productId);
        }
        log.info("Released stock for order {}", orderRef);
        return toResponse(orderRef, reservations);
    }

    /** Called by the scheduler for reservations whose order was never paid. */
    @Transactional
    public int expireOverdue(Instant now, int batchSize) {
        List<StockReservation> overdue = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.RESERVED, now, Limit.of(batchSize));
        for (StockReservation reservation : overdue) {
            inventoryRepository.releaseReserved(reservation.getProductId(), reservation.getQuantity(), now);
            reservation.changeStatus(ReservationStatus.EXPIRED);
            movementRepository.save(new StockMovement(merchantOf(reservation.getProductId()), reservation.getProductId(),
                    MovementType.EXPIRED, reservation.getQuantity(), reservation.getOrderRef(),
                    "Payment not received in time", "system"));
            productCache.evictProduct(reservation.getProductId());
        }
        return overdue.size();
    }

    // ------------------------------------------------------------------ merchant operations

    @Transactional(readOnly = true)
    public List<InventoryResponse> stockList(AuthenticatedUser merchant) {
        return inventoryRepository.findByMerchant(merchant.id()).stream().map(this::toInventoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(Long productId, AuthenticatedUser merchant) {
        owned(productId, merchant);
        return toInventoryResponse(findInventory(productId));
    }

    /** Stock count: sets the available quantity and records the difference in the audit trail. */
    @Transactional
    public InventoryResponse adjust(Long productId, AdjustStockRequest request, AuthenticatedUser merchant) {
        owned(productId, merchant);
        Inventory inventory = findInventory(productId);
        int delta = request.availableQuantity() - inventory.getAvailableQuantity();
        int reorderLevel = request.reorderLevel() == null ? inventory.getReorderLevel() : request.reorderLevel();
        inventory.setLevels(request.availableQuantity(), reorderLevel);
        inventoryRepository.flush();
        movementRepository.save(new StockMovement(merchant.id(), productId, MovementType.ADJUSTMENT, delta, null,
                request.note() == null ? "Stock count" : request.note(), merchant.username()));
        productCache.evictProduct(productId);
        log.info("Stock of product {} adjusted by {} ({}), reorder level {}", productId, delta, merchant.username(),
                reorderLevel);
        publishLowStockAlerts(List.of(productId));
        return toInventoryResponse(inventory);
    }

    /** Goods received: adds units, records the purchase and updates the weighted average cost. */
    @Transactional
    public InventoryResponse restock(Long productId, RestockRequest request, AuthenticatedUser merchant) {
        Product product = owned(productId, merchant);
        int onHand = inventoryRepository.unitsOnHand(productId)
                .orElseThrow(() -> ApiException.notFound("No inventory record for product " + productId));
        product.receiveStockAtCost(onHand, request.quantity(), request.unitCost());
        productRepository.flush();
        inventoryRepository.addStock(productId, request.quantity(), Instant.now());
        purchaseRepository.save(new StockPurchase(merchant.id(), productId, product.getSku(), product.getName(),
                request.quantity(), request.unitCost(), blankToNull(request.supplier()), blankToNull(request.reference())));
        movementRepository.save(new StockMovement(merchant.id(), productId, MovementType.RESTOCK, request.quantity(),
                blankToNull(request.reference()), "Received from " + (request.supplier() == null || request.supplier().isBlank()
                ? "supplier" : request.supplier().trim()) + " at " + request.unitCost() + " per unit", merchant.username()));
        productCache.evictProduct(productId);
        log.info("{} units of product {} received by {} at {}", request.quantity(), productId, merchant.username(),
                request.unitCost());
        return toInventoryResponse(inventoryRepository.findById(productId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> lowStock(AuthenticatedUser merchant) {
        return inventoryRepository.findLowStock(merchant.id()).stream().map(this::toInventoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movements(Long productId, AuthenticatedUser merchant) {
        if (productId != null) {
            owned(productId, merchant);
            return movementRepository.findByProductIdOrderByCreatedAtDesc(productId, Limit.of(100)).stream()
                    .map(MovementResponse::from).toList();
        }
        return movementRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.id(), Limit.of(200)).stream()
                .map(MovementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> purchases(AuthenticatedUser merchant, Instant from, Instant to) {
        return purchaseRepository.findByMerchantIdAndPurchasedAtBetweenOrderByPurchasedAtDesc(merchant.id(), from, to)
                .stream().map(PurchaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InventorySummary summary(AuthenticatedUser merchant) {
        List<Inventory> rows = inventoryRepository.findByMerchant(merchant.id());
        BigDecimal atCost = BigDecimal.ZERO;
        BigDecimal atPrice = BigDecimal.ZERO;
        long units = 0;
        long reserved = 0;
        int listed = 0;
        int low = 0;
        int out = 0;
        for (Inventory inventory : rows) {
            Product product = inventory.getProduct();
            int onHand = inventory.getAvailableQuantity() + inventory.getReservedQuantity();
            units += inventory.getAvailableQuantity();
            reserved += inventory.getReservedQuantity();
            atCost = atCost.add(product.getCostPrice().multiply(BigDecimal.valueOf(onHand)));
            atPrice = atPrice.add(product.getPrice().multiply(BigDecimal.valueOf(onHand)));
            listed += product.isListed() ? 1 : 0;
            low += inventory.isLowStock() ? 1 : 0;
            out += inventory.getAvailableQuantity() == 0 ? 1 : 0;
        }
        return new InventorySummary(rows.size(), listed, units, reserved, low, out,
                atCost.setScale(2, RoundingMode.HALF_UP), atPrice.setScale(2, RoundingMode.HALF_UP),
                atPrice.subtract(atCost).setScale(2, RoundingMode.HALF_UP), ProductMapper.CURRENCY);
    }

    // ------------------------------------------------------------------ helpers

    private void publishLowStockAlerts(Iterable<Long> productIds) {
        for (Long productId : productIds) {
            inventoryRepository.findById(productId).filter(Inventory::isLowStock).ifPresent(inventory -> {
                Product product = productRepository.findById(productId).orElseThrow();
                events.publishEvent(new LowStockEvent(product.getMerchantId(), productId, product.getSku(),
                        product.getName(), inventory.getAvailableQuantity(), inventory.getReorderLevel(),
                        CurrentUser.token()));
            });
        }
    }

    private Product owned(Long productId, AuthenticatedUser merchant) {
        return productRepository.findByIdAndMerchantId(productId, merchant.id())
                .orElseThrow(() -> ApiException.notFound("Product " + productId + " not found in your inventory"));
    }

    private Long merchantOf(Long productId) {
        return productRepository.findById(productId).map(Product::getMerchantId).orElse(0L);
    }

    private List<StockReservation> findReservations(String orderRef) {
        List<StockReservation> reservations = reservationRepository.findByOrderRefOrderByProductId(orderRef);
        if (reservations.isEmpty()) {
            throw ApiException.notFound("No stock reservation found for order " + orderRef);
        }
        return reservations;
    }

    private Inventory findInventory(Long productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("No inventory record for product " + productId));
    }

    private ReservationResponse toResponse(String orderRef, List<StockReservation> reservations) {
        List<ReservedLine> lines = reservations.stream().map(reservation -> {
            Product product = productRepository.findById(reservation.getProductId()).orElseThrow();
            return new ReservedLine(product.getId(), product.getMerchantId(), product.getMerchantName(),
                    product.getSku(), product.getName(), product.getPrice(), product.getCostPrice(),
                    reservation.getQuantity(), reservation.getStatus().name());
        }).toList();
        Instant expiresAt = reservations.stream().map(StockReservation::getExpiresAt).min(Instant::compareTo).orElse(null);
        return new ReservationResponse(orderRef, ProductMapper.CURRENCY, expiresAt, lines);
    }

    private InventoryResponse toInventoryResponse(Inventory inventory) {
        Product product = productRepository.findById(inventory.getProductId()).orElseThrow();
        int onHand = inventory.getAvailableQuantity() + inventory.getReservedQuantity();
        return new InventoryResponse(product.getId(), product.getSku(), product.getName(), product.isListed(),
                inventory.getAvailableQuantity(), inventory.getReservedQuantity(), inventory.getReorderLevel(),
                inventory.isLowStock(), product.getCostPrice(), product.getPrice(),
                product.getCostPrice().multiply(BigDecimal.valueOf(onHand)).setScale(2, RoundingMode.HALF_UP),
                inventory.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor() {
        try {
            return CurrentUser.require().username();
        } catch (ApiException ex) {
            return "system";
        }
    }
}
