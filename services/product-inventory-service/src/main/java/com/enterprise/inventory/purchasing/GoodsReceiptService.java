package com.enterprise.inventory.purchasing;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.Like;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.numbering.DocumentNumberService;
import com.enterprise.inventory.numbering.DocumentType;
import com.enterprise.inventory.purchasing.PurchasingDtos.GoodsReceiptLineResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.GoodsReceiptResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReceiveLineRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReceivePurchaseOrderRequest;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.MovementType;
import com.enterprise.inventory.stock.StockDtos.ReceiveRequest;
import com.enterprise.inventory.stock.StockLedger;
import com.enterprise.inventory.warehouse.Warehouse;
import com.enterprise.inventory.warehouse.WarehouseService;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Goods coming in: against a purchase order (partial deliveries allowed) or without one. */
@Service
public class GoodsReceiptService {

    private static final Logger log = LoggerFactory.getLogger(GoodsReceiptService.class);

    private final GoodsReceiptRepository repository;
    private final StockLedger ledger;
    private final DocumentNumberService numbers;
    private final WarehouseService warehouseService;
    private final SupplierService supplierService;
    private final ProductCache productCache;

    public GoodsReceiptService(GoodsReceiptRepository repository, StockLedger ledger, DocumentNumberService numbers,
                               WarehouseService warehouseService, SupplierService supplierService,
                               ProductCache productCache) {
        this.repository = repository;
        this.ledger = ledger;
        this.numbers = numbers;
        this.warehouseService = warehouseService;
        this.supplierService = supplierService;
        this.productCache = productCache;
    }

    /** Called by {@link PurchaseOrderService#receive} inside its transaction. */
    @Transactional
    public GoodsReceiptResponse receivePurchaseOrder(PurchaseOrder order, ReceivePurchaseOrderRequest request,
                                                     String actor) {
        Map<Long, PurchaseOrderLine> lines = new HashMap<>();
        order.getLines().forEach(line -> lines.put(line.getId(), line));
        Map<Long, Integer> quantities = new HashMap<>();
        for (ReceiveLineRequest line : request.lines()) {
            if (!lines.containsKey(line.lineId())) {
                throw ApiException.badRequest("Line " + line.lineId() + " is not part of " + order.getNumber());
            }
            if (line.quantity() > 0) {
                quantities.merge(line.lineId(), line.quantity(), Integer::sum);
            }
        }
        if (quantities.isEmpty()) {
            throw ApiException.badRequest("Enter the quantity received for at least one product");
        }
        Warehouse warehouse = order.getWarehouse();
        GoodsReceipt receipt = new GoodsReceipt(order.getSellerId(), numbers.next(order.getSellerId(),
                DocumentType.GOODS_RECEIPT), order, order.getSupplier(), warehouse, trim(request.supplierReference()),
                trim(request.note()), actor);
        // lock products in ascending id order
        List<PurchaseOrderLine> receiving = quantities.keySet().stream().map(lines::get)
                .sorted(Comparator.comparing(line -> line.getProduct().getId())).toList();
        for (PurchaseOrderLine line : receiving) {
            int quantity = quantities.get(line.getId());
            line.receive(quantity);
            Inventory inventory = ledger.lock(line.getProduct().getId());
            ledger.stockIn(inventory, warehouse, MovementType.RECEIVED, quantity, line.getUnitCost(), receipt.getNumber(),
                    "Received on " + order.getNumber() + " from " + order.getSupplier().getName(), actor);
            receipt.addLine(line.getProduct(), quantity, line.getUnitCost());
            productCache.evictProduct(line.getProduct().getId());
        }
        order.refreshReceiptStatus();
        repository.save(receipt);
        log.info("{} received {} unit(s) on {} into {} ({} AED)", actor, receipt.getTotalUnits(), order.getNumber(),
                warehouse.getCode(), receipt.getTotalAmount());
        return toResponse(receipt);
    }

    /** Goods received without a purchase order (e.g. a market purchase). */
    @Transactional
    public GoodsReceiptResponse receiveManual(Long sellerId, String actor, ReceiveRequest request) {
        Warehouse warehouse = warehouseService.resolve(sellerId, request.warehouseId());
        Supplier supplier = request.supplierId() == null ? null : supplierService.requireActive(sellerId, request.supplierId());
        Inventory inventory = ledger.lock(request.productId());
        Product product = inventory.getProduct();
        if (!product.isOwnedBy(sellerId) || !product.isActive()) {
            throw ApiException.notFound("Product " + request.productId() + " not found in your inventory");
        }
        GoodsReceipt receipt = new GoodsReceipt(sellerId, numbers.next(sellerId, DocumentType.GOODS_RECEIPT), null,
                supplier, warehouse, trim(request.reference()), trim(request.note()), actor);
        ledger.stockIn(inventory, warehouse, MovementType.RECEIVED, request.quantity(), request.unitCost(),
                receipt.getNumber(), "Received" + (supplier == null ? "" : " from " + supplier.getName())
                        + " (no purchase order)", actor);
        receipt.addLine(product, request.quantity(), request.unitCost());
        repository.save(receipt);
        productCache.evictProduct(product.getId());
        log.info("{} received {} unit(s) of {} into {} at {}", actor, request.quantity(), product.getSku(),
                warehouse.getCode(), request.unitCost());
        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public PageResponse<GoodsReceiptResponse> list(Long sellerId, String search, Long supplierId, Long warehouseId,
                                                   LocalDate from, LocalDate to, int page, int size) {
        return PageResponse.from(repository.findAll(specification(sellerId, search, supplierId, warehouseId, from, to),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "receivedAt", "id"))), this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptResponse> export(Long sellerId, String search, Long supplierId, Long warehouseId,
                                             LocalDate from, LocalDate to) {
        return repository.findAll(specification(sellerId, search, supplierId, warehouseId, from, to),
                        PageRequest.of(0, 2000, Sort.by(Sort.Direction.DESC, "receivedAt")))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponse get(Long sellerId, Long id) {
        return repository.findWithLinesByIdAndSellerId(id, sellerId).map(this::toResponse)
                .orElseThrow(() -> ApiException.notFound("Goods receipt " + id + " not found"));
    }

    private static Specification<GoodsReceipt> specification(Long sellerId, String search, Long supplierId,
                                                             Long warehouseId, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("sellerId"), sellerId));
            if (supplierId != null) {
                where.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (warehouseId != null) {
                where.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = Like.contains(search);
                where.add(cb.or(cb.like(cb.lower(root.get("number")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("supplierName"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("supplierReference"), "")), pattern, Like.ESCAPE)));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("receivedAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    GoodsReceiptResponse toResponse(GoodsReceipt r) {
        return new GoodsReceiptResponse(r.getId(), r.getNumber(),
                r.getPurchaseOrder() == null ? null : r.getPurchaseOrder().getId(),
                r.getPurchaseOrder() == null ? null : r.getPurchaseOrder().getNumber(), r.getSupplierName(),
                r.getWarehouse().getId(), r.getWarehouse().getCode(), r.getSupplierReference(), r.getNote(),
                r.getTotalAmount(), r.getTotalUnits(), r.getReceivedBy(), r.getReceivedAt(),
                r.getLines().stream().map(l -> new GoodsReceiptLineResponse(l.getProduct().getId(), l.getSku(),
                        l.getProductName(), l.getQuantity(), l.getUnitCost(), l.getLineTotal())).toList());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
