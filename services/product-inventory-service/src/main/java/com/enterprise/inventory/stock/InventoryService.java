package com.enterprise.inventory.stock;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.ProductMapper;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.notify.LowStockEvent;
import com.enterprise.inventory.security.CurrentUser;
import com.enterprise.inventory.stock.StockDtos.AdjustStockRequest;
import com.enterprise.inventory.stock.StockDtos.InventoryResponse;
import com.enterprise.inventory.stock.StockDtos.MovementResponse;
import com.enterprise.inventory.stock.StockDtos.ReserveItem;
import com.enterprise.inventory.stock.StockDtos.ReserveRequest;
import com.enterprise.inventory.stock.StockDtos.ReservationResponse;
import com.enterprise.inventory.stock.StockDtos.ReservedLine;
import com.enterprise.inventory.stock.StockDtos.RestockRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock reservation workflow used by the order saga:
 * reserve (order placed) -> commit (order paid) or release (order cancelled / payment timeout).
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final ProductCache productCache;
    private final ApplicationEventPublisher events;
    private final Duration reservationTtl;

    public InventoryService(InventoryRepository inventoryRepository, StockReservationRepository reservationRepository,
                            StockMovementRepository movementRepository, ProductRepository productRepository,
                            ProductCache productCache, ApplicationEventPublisher events,
                            @Value("${app.reservation.ttl:40m}") Duration reservationTtl) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.productCache = productCache;
        this.events = events;
        this.reservationTtl = reservationTtl;
    }

    /**
     * All-or-nothing reservation. Products are processed in id order (consistent lock ordering prevents
     * deadlocks); if any line is short of stock the whole transaction rolls back. Idempotent per orderRef.
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
        List<StockReservation> created = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = productRepository.findById(productId)
                    .filter(Product::isActive)
                    .orElseThrow(() -> ApiException.notFound("Product " + productId + " is not available"));
            if (inventoryRepository.reserve(productId, quantity, now) == 0) {
                int available = inventoryRepository.findById(productId)
                        .map(Inventory::getAvailableQuantity).orElse(0);
                throw ApiException.conflict("Insufficient stock for '" + product.getName() + "': requested "
                        + quantity + ", available " + available);
            }
            created.add(reservationRepository.save(
                    new StockReservation(request.orderRef(), productId, quantity, expiresAt)));
            movementRepository.save(new StockMovement(productId, MovementType.RESERVED, -quantity,
                    request.orderRef(), "Reserved for order", actor()));
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
                    movementRepository.save(new StockMovement(reservation.getProductId(), MovementType.SOLD, 0,
                            orderRef, "Order confirmed", actor()));
                    productCache.evictProduct(reservation.getProductId());
                }
                case RELEASED, EXPIRED -> throw ApiException.conflict(
                        "Reservation for order " + orderRef + " is no longer active (" + reservation.getStatus() + ")");
            }
        }
        log.info("Committed stock for order {}", orderRef);
        return toResponse(orderRef, reservations);
    }

    /** Order cancelled: put the units back on the shelf (also works after commit, as a return). Safe to call twice. */
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
                    movementRepository.save(new StockMovement(productId, MovementType.RELEASED, quantity, orderRef,
                            "Order cancelled", actor()));
                }
                case COMMITTED -> {
                    inventoryRepository.addStock(productId, quantity, now);
                    movementRepository.save(new StockMovement(productId, MovementType.RETURNED, quantity, orderRef,
                            "Paid order cancelled - stock returned", actor()));
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
            movementRepository.save(new StockMovement(reservation.getProductId(), MovementType.EXPIRED,
                    reservation.getQuantity(), reservation.getOrderRef(), "Payment not received in time", "system"));
            productCache.evictProduct(reservation.getProductId());
        }
        return overdue.size();
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(Long productId) {
        return toInventoryResponse(findInventory(productId));
    }

    @Transactional
    public InventoryResponse adjust(Long productId, AdjustStockRequest request, String actor) {
        Inventory inventory = findInventory(productId);
        int delta = request.availableQuantity() - inventory.getAvailableQuantity();
        int reorderLevel = request.reorderLevel() == null ? inventory.getReorderLevel() : request.reorderLevel();
        inventory.setLevels(request.availableQuantity(), reorderLevel);
        inventoryRepository.flush();
        movementRepository.save(new StockMovement(productId, MovementType.ADJUSTMENT, delta, null,
                request.note() == null ? "Manual stock count" : request.note(), actor));
        productCache.evictProduct(productId);
        log.info("Stock of product {} adjusted by {} ({}), reorder level {}", productId, delta, actor, reorderLevel);
        publishLowStockAlerts(List.of(productId));
        return toInventoryResponse(inventory);
    }

    @Transactional
    public InventoryResponse restock(Long productId, RestockRequest request, String actor) {
        if (!inventoryRepository.existsById(productId)) {
            throw ApiException.notFound("No inventory record for product " + productId);
        }
        inventoryRepository.addStock(productId, request.quantity(), Instant.now());
        movementRepository.save(new StockMovement(productId, MovementType.RESTOCK, request.quantity(), null,
                request.note() == null ? "Restock" : request.note(), actor));
        productCache.evictProduct(productId);
        return toInventoryResponse(findInventory(productId));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> lowStock() {
        return inventoryRepository.findLowStock().stream().map(this::toInventoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movements(Long productId) {
        findInventory(productId);
        return movementRepository.findByProductIdOrderByCreatedAtDesc(productId, Limit.of(50)).stream()
                .map(MovementResponse::from).toList();
    }

    private void publishLowStockAlerts(Iterable<Long> productIds) {
        for (Long productId : productIds) {
            inventoryRepository.findById(productId).filter(Inventory::isLowStock).ifPresent(inventory -> {
                Product product = productRepository.findById(productId).orElseThrow();
                events.publishEvent(new LowStockEvent(productId, product.getSku(), product.getName(),
                        inventory.getAvailableQuantity(), inventory.getReorderLevel(), CurrentUser.token()));
            });
        }
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
            return new ReservedLine(product.getId(), product.getSku(), product.getName(), product.getPrice(),
                    reservation.getQuantity(), reservation.getStatus().name());
        }).toList();
        Instant expiresAt = reservations.stream().map(StockReservation::getExpiresAt).min(Instant::compareTo).orElse(null);
        return new ReservationResponse(orderRef, ProductMapper.CURRENCY, expiresAt, lines);
    }

    private InventoryResponse toInventoryResponse(Inventory inventory) {
        Product product = productRepository.findById(inventory.getProductId()).orElseThrow();
        return new InventoryResponse(product.getId(), product.getSku(), product.getName(),
                inventory.getAvailableQuantity(), inventory.getReservedQuantity(), inventory.getReorderLevel(),
                inventory.isLowStock(), inventory.getUpdatedAt());
    }

    private static String actor() {
        try {
            return CurrentUser.require().username();
        } catch (ApiException ex) {
            return "system";
        }
    }
}
