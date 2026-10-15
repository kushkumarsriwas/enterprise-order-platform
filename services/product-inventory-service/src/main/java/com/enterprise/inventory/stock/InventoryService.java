package com.enterprise.inventory.stock;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.ProductMapper;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.catalog.SellerProductQuery;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.CriteriaPage;
import com.enterprise.inventory.common.Like;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.notify.LowStockEvent;
import com.enterprise.inventory.purchasing.PurchaseOrderRepository;
import com.enterprise.inventory.purchasing.PurchaseOrderStatus;
import com.enterprise.inventory.security.CurrentUser;
import com.enterprise.inventory.stock.StockDtos.AdjustmentRequest;
import com.enterprise.inventory.stock.StockDtos.BinRequest;
import com.enterprise.inventory.stock.StockDtos.IntegrityIssue;
import com.enterprise.inventory.stock.StockDtos.InventoryResponse;
import com.enterprise.inventory.stock.StockDtos.InventorySummary;
import com.enterprise.inventory.stock.StockDtos.MovementResponse;
import com.enterprise.inventory.stock.StockDtos.ProductLookup;
import com.enterprise.inventory.stock.StockDtos.ReservationResponse;
import com.enterprise.inventory.stock.StockDtos.ReserveItem;
import com.enterprise.inventory.stock.StockDtos.ReserveRequest;
import com.enterprise.inventory.stock.StockDtos.ReservedLine;
import com.enterprise.inventory.stock.StockDtos.SetStockRequest;
import com.enterprise.inventory.stock.StockDtos.StockLevelRow;
import com.enterprise.inventory.stock.StockDtos.WarehouseLevel;
import com.enterprise.inventory.warehouse.Warehouse;
import com.enterprise.inventory.warehouse.WarehouseRepository;
import com.enterprise.inventory.warehouse.WarehouseService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock workflow.
 * <pre>
 * Order saga (internal API): reserve (order placed) -> commit (paid: units leave the warehouses) | release
 * Seller operations: adjustments with a reason, stock takes per warehouse, bins, stock levels, the ledger
 * </pre>
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMovementRepository movementRepository;
    private final WarehouseStockRepository stockRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrders;
    private final WarehouseService warehouseService;
    private final WarehouseRepository warehouseRepository;
    private final SellerProductQuery productQuery;
    private final StockLedger ledger;
    private final ProductCache productCache;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;
    private final Duration reservationTtl;

    public InventoryService(InventoryRepository inventoryRepository, StockReservationRepository reservationRepository,
                            StockMovementRepository movementRepository, WarehouseStockRepository stockRepository,
                            ProductRepository productRepository, PurchaseOrderRepository purchaseOrders,
                            WarehouseService warehouseService, WarehouseRepository warehouseRepository,
                            SellerProductQuery productQuery, StockLedger ledger, ProductCache productCache,
                            ApplicationEventPublisher events, EntityManager entityManager,
                            @Value("${app.reservation.ttl:40m}") Duration reservationTtl) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.movementRepository = movementRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.purchaseOrders = purchaseOrders;
        this.warehouseService = warehouseService;
        this.warehouseRepository = warehouseRepository;
        this.productQuery = productQuery;
        this.ledger = ledger;
        this.productCache = productCache;
        this.events = events;
        this.entityManager = entityManager;
        this.reservationTtl = reservationTtl;
    }

    // ================================================================== order saga (internal API)

    /** Seller, price and availability of products: the order service splits a cart into one order per seller. */
    @Transactional(readOnly = true)
    public List<ProductLookup> lookup(List<Long> productIds) {
        Map<Long, Inventory> stock = inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        return productRepository.findAllById(productIds).stream()
                .map(p -> new ProductLookup(p.getId(), p.getSellerId(), p.getSellerName(), p.getName(), p.getPrice(),
                        p.isPurchasable(), stock.containsKey(p.getId()) ? stock.get(p.getId()).getAvailableQuantity() : 0))
                .toList();
    }

    /**
     * All-or-nothing reservation for ONE seller's order. Products are processed in id order (consistent lock
     * ordering prevents deadlocks); if any line is short of stock the whole transaction rolls back.
     * Idempotent per orderRef (a retried request returns the existing reservation).
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
        Long sellerId = null;
        List<StockReservation> created = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = productRepository.findWithCategoryById(productId)
                    .filter(Product::isPurchasable)
                    .orElseThrow(() -> ApiException.notFound("Product " + productId + " is not available for sale"));
            if (sellerId != null && !sellerId.equals(product.getSellerId())) {
                throw ApiException.badRequest("All products of one order must belong to the same seller");
            }
            sellerId = product.getSellerId();
            log.info("Reserving stock for {}: {} x {}", request.orderRef(), quantity, product.getSku());
            if (inventoryRepository.reserve(productId, quantity, now) == 0) {
                int available = inventoryRepository.availableOf(productId).orElse(0);
                throw ApiException.conflict("Insufficient stock for '" + product.getName() + "': requested "
                        + quantity + ", available " + available);
            }
            created.add(reservationRepository.save(new StockReservation(request.orderRef(), productId, quantity,
                    product.getPrice(), product.getCostPrice(), expiresAt)));
            ledger.recordReservation(product, MovementType.RESERVED, -quantity, request.orderRef(),
                    "Reserved for order awaiting payment", actor());
            productCache.evictProduct(productId);
            alertIfCrossedReorderPoint(product, quantity);
        }
        log.info("Reserved stock for order {} ({} product(s))", loggable(request.orderRef()), created.size());
        return toResponse(request.orderRef(), created);
    }

    /** Order paid: reserved units leave the warehouses (chosen by the allocation strategy). Safe to call twice. */
    @Transactional
    public ReservationResponse commit(String orderRef) {
        List<StockReservation> reservations = findReservations(orderRef);
        for (StockReservation reservation : reservations) {
            switch (reservation.getStatus()) {
                case COMMITTED -> {
                }
                case RESERVED -> {
                    Inventory inventory = ledger.lock(reservation.getProductId());
                    ledger.ship(inventory, reservation.getQuantity(), reservation.getUnitPrice(),
                            reservation.getUnitCost(), orderRef);
                    reservation.changeStatus(ReservationStatus.COMMITTED);
                    productCache.evictProduct(reservation.getProductId());
                }
                default -> throw ApiException.conflict(
                        "Reservation for order " + orderRef + " is no longer active (" + reservation.getStatus() + ")");
            }
        }
        log.info("Committed stock for order {}", loggable(orderRef));
        return toResponse(orderRef, reservations);
    }

    /** Order cancelled: unpaid reservations are released, shipped units go back to their warehouses. Idempotent. */
    @Transactional
    public ReservationResponse release(String orderRef) {
        List<StockReservation> reservations = findReservations(orderRef);
        Instant now = Instant.now();
        for (StockReservation reservation : reservations) {
            Long productId = reservation.getProductId();
            switch (reservation.getStatus()) {
                case RESERVED -> {
                    inventoryRepository.releaseReserved(productId, reservation.getQuantity(), now);
                    ledger.recordReservation(productRepository.findById(productId).orElseThrow(), MovementType.RELEASED,
                            reservation.getQuantity(), orderRef, "Order cancelled before payment", actor());
                }
                case COMMITTED -> ledger.returnShipped(ledger.lock(productId), orderRef, actor());
                default -> {
                    // released or expired earlier: nothing to give back
                    continue;
                }
            }
            reservation.changeStatus(ReservationStatus.RELEASED);
            productCache.evictProduct(productId);
        }
        log.info("Released stock for order {}", loggable(orderRef));
        return toResponse(orderRef, reservations);
    }

    /** Scheduler: reservations whose order was never paid go back to available. */
    @Transactional
    public int expireOverdue(Instant now, int batchSize) {
        List<StockReservation> overdue = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.RESERVED, now, Limit.of(batchSize));
        for (StockReservation reservation : overdue) {
            inventoryRepository.releaseReserved(reservation.getProductId(), reservation.getQuantity(), now);
            reservation.changeStatus(ReservationStatus.EXPIRED);
            ledger.recordReservation(productRepository.findById(reservation.getProductId()).orElseThrow(),
                    MovementType.EXPIRED, reservation.getQuantity(), reservation.getOrderRef(),
                    "Payment not received in time", "system");
            productCache.evictProduct(reservation.getProductId());
        }
        return overdue.size();
    }

    // ================================================================== seller operations

    /** Manual correction with a reason (damaged, lost, found, internal use...). */
    @Transactional
    public MovementResponse adjust(Long sellerId, String actor, AdjustmentRequest request) {
        Inventory inventory = ownedLocked(sellerId, request.productId());
        Warehouse warehouse = warehouseService.resolve(sellerId, request.warehouseId());
        StockMovement movement = ledger.adjust(inventory, warehouse, MovementType.ADJUSTMENT, request.quantityChange(),
                request.reason(), null, trim(request.note()), actor);
        productCache.evictProduct(request.productId());
        log.info("{} adjusted {} in {} by {} ({})", actor, inventory.getProduct().getSku(), warehouse.getCode(),
                request.quantityChange(), request.reason());
        if (request.quantityChange() < 0) {
            alertIfCrossedReorderPoint(inventory.getProduct(), -request.quantityChange());
        }
        return MovementResponse.from(movement);
    }

    /** Stock take of one product in one warehouse: sets the counted quantity, the difference is audited. */
    @Transactional
    public InventoryResponse setStock(Long sellerId, String actor, Long productId, SetStockRequest request) {
        Inventory inventory = ownedLocked(sellerId, productId);
        Warehouse warehouse = warehouseService.resolve(sellerId, request.warehouseId());
        int current = stockRepository.find(productId, warehouse.getId()).map(WarehouseStock::getOnHand).orElse(0);
        int delta = request.onHand() - current;
        if (delta != 0) {
            ledger.adjust(inventory, warehouse, MovementType.ADJUSTMENT, delta,
                    request.reason() == null ? AdjustmentReason.COUNT : request.reason(), null,
                    request.note() == null ? "Stock take: counted " + request.onHand() : request.note().trim(), actor);
        }
        if (request.binLocation() != null) {
            ledger.locate(inventory.getProduct(), warehouse).setBinLocation(trim(request.binLocation()));
        }
        productCache.evictProduct(productId);
        return toInventoryResponse(inventory, stockRepository.findByProduct(productId));
    }

    @Transactional
    public InventoryResponse setBin(Long sellerId, Long productId, BinRequest request) {
        Inventory inventory = ownedLocked(sellerId, productId);
        Warehouse warehouse = warehouseService.resolve(sellerId, request.warehouseId());
        ledger.locate(inventory.getProduct(), warehouse).setBinLocation(trim(request.binLocation()));
        return toInventoryResponse(inventory, stockRepository.findByProduct(productId));
    }

    @Transactional(readOnly = true)
    public InventoryResponse inventoryOf(Long sellerId, Long productId) {
        Inventory inventory = inventoryRepository.findById(productId)
                .filter(i -> i.getProduct().isOwnedBy(sellerId))
                .orElseThrow(() -> ApiException.notFound("Product " + productId + " not found in your inventory"));
        return toInventoryResponse(inventory, stockRepository.findByProduct(productId));
    }

    /** Stock levels screen: same filters as the product list, optionally scoped to one warehouse. */
    @Transactional(readOnly = true)
    public PageResponse<StockLevelRow> stockLevels(Long sellerId, SellerProductQuery.Filter filter, int page, int size) {
        Page<Product> products = productQuery.find(sellerId, filter, page, size);
        return PageResponse.from(products, rowMapper(sellerId, products.getContent(), filter.warehouseId()));
    }

    @Transactional(readOnly = true)
    public List<StockLevelRow> stockLevelsExport(Long sellerId, SellerProductQuery.Filter filter) {
        List<Product> products = productQuery.all(sellerId, filter, 5000);
        return products.stream().map(rowMapper(sellerId, products, filter.warehouseId())).toList();
    }

    @Transactional(readOnly = true)
    public InventorySummary summary(Long sellerId) {
        List<Inventory> rows = inventoryRepository.findActiveBySeller(sellerId);
        BigDecimal atCost = BigDecimal.ZERO;
        BigDecimal atPrice = BigDecimal.ZERO;
        long available = 0;
        long reserved = 0;
        int listed = 0;
        int low = 0;
        int out = 0;
        for (Inventory inventory : rows) {
            Product product = inventory.getProduct();
            int onHand = inventory.onHand();
            available += inventory.getAvailableQuantity();
            reserved += inventory.getReservedQuantity();
            atCost = atCost.add(product.getCostPrice().multiply(BigDecimal.valueOf(onHand)));
            atPrice = atPrice.add(product.getPrice().multiply(BigDecimal.valueOf(onHand)));
            listed += product.isListed() ? 1 : 0;
            StockStatus status = inventory.status(product.getReorderPoint());
            low += status == StockStatus.LOW_STOCK ? 1 : 0;
            out += status == StockStatus.OUT_OF_STOCK ? 1 : 0;
        }
        return new InventorySummary(rows.size(), listed, rows.size() - listed, available, reserved, available + reserved,
                purchaseOrders.incomingUnits(sellerId, PurchaseOrderStatus.OPEN), low, out,
                (int) warehouseRepository.countBySellerId(sellerId),
                (int) purchaseOrders.countBySellerIdAndStatusIn(sellerId, PurchaseOrderStatus.OPEN),
                atCost.setScale(2, RoundingMode.HALF_UP), atPrice.setScale(2, RoundingMode.HALF_UP),
                atPrice.subtract(atCost).setScale(2, RoundingMode.HALF_UP), ProductMapper.CURRENCY);
    }

    public record MovementFilter(Long productId, Long warehouseId, Set<MovementType> types, AdjustmentReason reason,
                                 String search, LocalDate from, LocalDate to, boolean physicalOnly) {
    }

    /** The stock ledger with filters (product, warehouse, movement types, reason, text, dates). */
    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> movements(Long sellerId, MovementFilter filter, int page, int size) {
        return PageResponse.from(CriteriaPage.find(entityManager, StockMovement.class, movementSpec(sellerId, filter),
                page, size), MovementResponse::from);
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movementsExport(Long sellerId, MovementFilter filter) {
        return CriteriaPage.list(entityManager, StockMovement.class, movementSpec(sellerId, filter), 10000).stream()
                .map(MovementResponse::from).toList();
    }

    /** Reconciliation: available + reserved must equal the sum of the warehouse rows for every product. */
    @Transactional(readOnly = true)
    public List<IntegrityIssue> integrityCheck(Long sellerId) {
        Map<Long, Integer> byProduct = new HashMap<>();
        List<Inventory> rows = inventoryRepository.findActiveBySeller(sellerId);
        stockRepository.findByProducts(rows.stream().map(Inventory::getProductId).toList())
                .forEach(s -> byProduct.merge(s.getProduct().getId(), s.getOnHand(), Integer::sum));
        List<IntegrityIssue> issues = new ArrayList<>();
        for (Inventory inventory : rows) {
            int warehouses = byProduct.getOrDefault(inventory.getProductId(), 0);
            if (warehouses != inventory.onHand()) {
                issues.add(new IntegrityIssue(inventory.getProductId(), inventory.getProduct().getSku(),
                        inventory.onHand(), warehouses));
            }
        }
        return issues;
    }

    // ================================================================== helpers

    private CriteriaPage.Spec<StockMovement> movementSpec(Long sellerId, MovementFilter f) {
        return (cb, query, root, count) -> {
            if (!count) {
                root.fetch("product", JoinType.INNER);
                root.fetch("warehouse", JoinType.LEFT);
            }
            List<Predicate> where = new ArrayList<>();
            if (sellerId != null) {
                where.add(cb.equal(root.get("sellerId"), sellerId)); // null = whole platform (administrator)
            }
            if (f.productId() != null) {
                where.add(cb.equal(root.get("product").get("id"), f.productId()));
            }
            if (f.warehouseId() != null) {
                where.add(cb.equal(root.get("warehouse").get("id"), f.warehouseId()));
            }
            if (f.types() != null && !f.types().isEmpty()) {
                where.add(root.get("type").in(f.types()));
            }
            if (f.physicalOnly()) {
                where.add(cb.isNotNull(root.get("warehouse")));
            }
            if (f.reason() != null) {
                where.add(cb.equal(root.get("reason"), f.reason()));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String pattern = Like.contains(f.search());
                var product = root.join("product", JoinType.INNER);
                where.add(cb.or(cb.like(cb.lower(product.get("sku")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(product.get("name")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("reference"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("note"), "")), pattern, Like.ESCAPE)));
            }
            if (f.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (f.to() != null) {
                where.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return CriteriaPage.Parts.of(where, count ? List.of()
                    : List.of(cb.desc(root.get("createdAt")), cb.desc(root.get("id"))));
        };
    }

    private Function<Product, StockLevelRow> rowMapper(Long sellerId, List<Product> products, Long warehouseId) {
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, Inventory> stock = ids.isEmpty() ? Map.of() : inventoryRepository.findByProductIdIn(ids).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        Map<Long, Integer> incoming = incoming(sellerId, ids);
        Map<Long, WarehouseStock> inWarehouse = new HashMap<>();
        if (warehouseId != null && !ids.isEmpty()) {
            stockRepository.findByProducts(ids).stream()
                    .filter(s -> s.getWarehouse().getId().equals(warehouseId))
                    .forEach(s -> inWarehouse.put(s.getProduct().getId(), s));
        }
        return p -> {
            Inventory inventory = stock.get(p.getId());
            int available = inventory == null ? 0 : inventory.getAvailableQuantity();
            int reserved = inventory == null ? 0 : inventory.getReservedQuantity();
            WarehouseStock warehouse = inWarehouse.get(p.getId());
            return new StockLevelRow(p.getId(), p.getSku(), p.getName(),
                    p.getCategory() == null ? null : p.getCategory().getName(), p.getUnit().name(), p.isListed(),
                    available, reserved, available + reserved, warehouse == null ? null : warehouse.getOnHand(),
                    warehouse == null ? null : warehouse.getBinLocation(), incoming.getOrDefault(p.getId(), 0),
                    p.getReorderPoint(), p.getReorderQuantity(), StockStatus.of(available, p.getReorderPoint()).name(),
                    p.getCostPrice(), ProductMapper.money(p.getCostPrice().multiply(BigDecimal.valueOf(
                            warehouse == null ? available + reserved : warehouse.getOnHand()))),
                    inventory == null ? null : inventory.getUpdatedAt());
        };
    }

    /** Units still expected from open purchase orders, per product (sellerId null = any seller). */
    public Map<Long, Integer> incoming(Long sellerId, Collection<Long> productIds) {
        Map<Long, Integer> incoming = new HashMap<>();
        if (!productIds.isEmpty()) {
            (sellerId == null ? purchaseOrders.incomingByProductIds(PurchaseOrderStatus.OPEN, productIds)
                    : purchaseOrders.incomingByProduct(sellerId, PurchaseOrderStatus.OPEN, productIds))
                    .forEach(row -> incoming.put((Long) row[0], ((Number) row[1]).intValue()));
        }
        return incoming;
    }

    /** Low-stock alert only when this change pushed the product across its reorder point (no repeated alerts). */
    private void alertIfCrossedReorderPoint(Product product, int unitsTaken) {
        int after = inventoryRepository.availableOf(product.getId()).orElse(0);
        int before = after + unitsTaken;
        if (before > product.getReorderPoint() && after <= product.getReorderPoint()) {
            events.publishEvent(new LowStockEvent(product.getSellerId(), product.getId(), product.getSku(),
                    product.getName(), after, product.getReorderPoint(), CurrentUser.token()));
        }
    }

    private Inventory ownedLocked(Long sellerId, Long productId) {
        Inventory inventory = ledger.lock(productId);
        if (!inventory.getProduct().isOwnedBy(sellerId) || !inventory.getProduct().isActive()) {
            throw ApiException.notFound("Product " + productId + " not found in your inventory");
        }
        return inventory;
    }

    private List<StockReservation> findReservations(String orderRef) {
        List<StockReservation> reservations = reservationRepository.findByOrderRefOrderByProductId(orderRef);
        if (reservations.isEmpty()) {
            throw ApiException.notFound("No stock reservation found for order " + orderRef);
        }
        return reservations;
    }

    private ReservationResponse toResponse(String orderRef, List<StockReservation> reservations) {
        Map<Long, Product> products = productRepository.findByIdIn(reservations.stream()
                        .map(StockReservation::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<ReservedLine> lines = reservations.stream().map(reservation -> {
            Product product = products.get(reservation.getProductId());
            return new ReservedLine(product.getId(), product.getSellerId(), product.getSellerName(), product.getSku(),
                    product.getName(), product.getCategory() == null ? null : product.getCategory().getName(),
                    reservation.getUnitPrice(), reservation.getUnitCost(), reservation.getQuantity(),
                    reservation.getStatus().name());
        }).toList();
        Instant expiresAt = reservations.stream().map(StockReservation::getExpiresAt).min(Instant::compareTo).orElse(null);
        return new ReservationResponse(orderRef, ProductMapper.CURRENCY, expiresAt, lines);
    }

    InventoryResponse toInventoryResponse(Inventory inventory, List<WarehouseStock> warehouses) {
        Product product = inventory.getProduct();
        int onHand = inventory.onHand();
        return new InventoryResponse(product.getId(), product.getSku(), product.getName(), product.isListed(),
                inventory.getAvailableQuantity(), inventory.getReservedQuantity(), onHand, product.getReorderPoint(),
                inventory.status(product.getReorderPoint()).name(), product.getCostPrice(), product.getPrice(),
                ProductMapper.money(product.getCostPrice().multiply(BigDecimal.valueOf(onHand))),
                warehouses.stream().map(w -> new WarehouseLevel(w.getWarehouse().getId(), w.getWarehouse().getCode(),
                        w.getWarehouse().getName(), w.getOnHand(), w.getBinLocation())).toList(),
                inventory.getUpdatedAt());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor() {
        try {
            return CurrentUser.require().username();
        } catch (ApiException ex) {
            return "system";
        }
    }

    /** Request values are logged without line breaks, so they cannot forge extra log lines. */
    private static String loggable(String value) {
        return value == null ? null : value.replaceAll("\\R", "_");
    }
}
