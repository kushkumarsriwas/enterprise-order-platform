package com.enterprise.inventory.purchasing;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.Like;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.numbering.DocumentNumberService;
import com.enterprise.inventory.numbering.DocumentType;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderLineRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderLineResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReceiptSummary;
import com.enterprise.inventory.purchasing.PurchasingDtos.ReceivePurchaseOrderRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.GoodsReceiptResponse;
import com.enterprise.inventory.warehouse.Warehouse;
import com.enterprise.inventory.warehouse.WarehouseService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Purchase orders: draft -> send to supplier -> receive (in one or several deliveries) -> received / closed. */
@Service
public class PurchaseOrderService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderService.class);
    private static final Map<String, Sort> SORTS = Map.of(
            "newest", Sort.by(Sort.Direction.DESC, "createdAt"),
            "oldest", Sort.by(Sort.Direction.ASC, "createdAt"),
            "expected", Sort.by(Sort.Order.asc("expectedDate").nullsLast()),
            "amount", Sort.by(Sort.Direction.DESC, "totalAmount"));

    private final PurchaseOrderRepository repository;
    private final GoodsReceiptRepository receipts;
    private final SupplierService supplierService;
    private final WarehouseService warehouseService;
    private final ProductRepository productRepository;
    private final DocumentNumberService numbers;
    private final GoodsReceiptService receiving;

    public PurchaseOrderService(PurchaseOrderRepository repository, GoodsReceiptRepository receipts,
                                SupplierService supplierService, WarehouseService warehouseService,
                                ProductRepository productRepository, DocumentNumberService numbers,
                                GoodsReceiptService receiving) {
        this.repository = repository;
        this.receipts = receipts;
        this.supplierService = supplierService;
        this.warehouseService = warehouseService;
        this.productRepository = productRepository;
        this.numbers = numbers;
        this.receiving = receiving;
    }

    public record Filter(PurchaseOrderStatus status, Long supplierId, Long warehouseId, String search,
                         LocalDate from, LocalDate to, Boolean overdue, String sort) {
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(Long sellerId, Filter filter, int page, int size) {
        Sort sort = SORTS.getOrDefault(filter.sort() == null ? "newest" : filter.sort(), SORTS.get("newest"));
        return PageResponse.from(repository.findAll(specification(sellerId, filter), PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100), sort.and(Sort.by(Sort.Direction.DESC, "id")))), po -> toResponse(po, false));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> export(Long sellerId, Filter filter) {
        Sort sort = SORTS.getOrDefault(filter.sort() == null ? "newest" : filter.sort(), SORTS.get("newest"));
        return repository.findAll(specification(sellerId, filter), PageRequest.of(0, 2000, sort)).stream()
                .map(po -> toResponse(po, false)).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(Long sellerId, Long id) {
        return toResponse(load(sellerId, id), true);
    }

    @Transactional
    public PurchaseOrderResponse create(Long sellerId, String actor, PurchaseOrderRequest request) {
        Supplier supplier = supplierService.requireActive(sellerId, request.supplierId());
        Warehouse warehouse = warehouseService.resolve(sellerId, request.warehouseId());
        PurchaseOrder order = new PurchaseOrder(sellerId, numbers.next(sellerId, DocumentType.PURCHASE_ORDER), supplier,
                warehouse, request.expectedDate() == null ? LocalDate.now().plusDays(supplier.getLeadTimeDays())
                : request.expectedDate(), trim(request.notes()), actor);
        addLines(sellerId, order, request.lines());
        repository.save(order);
        log.info("Purchase order {} drafted by {} for {} ({} line(s), {} AED)", order.getNumber(), actor,
                supplier.getName(), order.getLines().size(), order.getTotalAmount());
        return toResponse(order, true);
    }

    @Transactional
    public PurchaseOrderResponse update(Long sellerId, Long id, PurchaseOrderRequest request) {
        PurchaseOrder order = load(sellerId, id);
        Supplier supplier = supplierService.requireActive(sellerId, request.supplierId());
        Warehouse warehouse = warehouseService.resolve(sellerId, request.warehouseId());
        order.revise(supplier, warehouse, request.expectedDate(), trim(request.notes()));
        repository.flush(); // delete the old lines before the new ones are inserted
        addLines(sellerId, order, request.lines());
        return toResponse(order, true);
    }

    @Transactional
    public PurchaseOrderResponse submit(Long sellerId, Long id, String actor) {
        PurchaseOrder order = load(sellerId, id);
        order.submit();
        log.info("Purchase order {} sent to {} by {}", order.getNumber(), order.getSupplier().getName(), actor);
        return toResponse(order, true);
    }

    @Transactional
    public PurchaseOrderResponse cancel(Long sellerId, Long id, String actor) {
        PurchaseOrder order = load(sellerId, id);
        order.cancel();
        log.info("Purchase order {} cancelled by {}", order.getNumber(), actor);
        return toResponse(order, true);
    }

    @Transactional
    public PurchaseOrderResponse close(Long sellerId, Long id, String actor) {
        PurchaseOrder order = load(sellerId, id);
        order.close();
        log.info("Purchase order {} closed short by {}", order.getNumber(), actor);
        return toResponse(order, true);
    }

    /** One delivery from the supplier: stock in at the PO's unit costs, a goods receipt note, PO status update. */
    @Transactional
    public GoodsReceiptResponse receive(Long sellerId, Long id, String actor, ReceivePurchaseOrderRequest request) {
        PurchaseOrder order = load(sellerId, id);
        if (!order.isOpen()) {
            throw ApiException.conflict("Purchase order " + order.getNumber() + " is " + order.getStatus()
                    + " - only ordered purchase orders can be received");
        }
        return receiving.receivePurchaseOrder(order, request, actor);
    }

    // ------------------------------------------------------------------ helpers

    private void addLines(Long sellerId, PurchaseOrder order, List<PurchaseOrderLineRequest> lines) {
        Map<Long, PurchaseOrderLineRequest> merged = new LinkedHashMap<>();
        for (PurchaseOrderLineRequest line : lines) {
            merged.merge(line.productId(), line, (a, b) -> new PurchaseOrderLineRequest(a.productId(),
                    a.quantity() + b.quantity(), a.unitCost() != null ? a.unitCost() : b.unitCost()));
        }
        for (PurchaseOrderLineRequest line : merged.values()) {
            Product product = productRepository.findByIdAndSellerId(line.productId(), sellerId)
                    .filter(Product::isActive)
                    .orElseThrow(() -> ApiException.notFound("Product " + line.productId() + " not found in your inventory"));
            order.addLine(new PurchaseOrderLine(product, line.quantity(),
                    line.unitCost() == null ? product.getCostPrice() : line.unitCost()));
        }
    }

    private PurchaseOrder load(Long sellerId, Long id) {
        return repository.findWithLinesByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> ApiException.notFound("Purchase order " + id + " not found"));
    }

    private static Specification<PurchaseOrder> specification(Long sellerId, Filter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("sellerId"), sellerId));
            if (f.status() != null) {
                where.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.supplierId() != null) {
                where.add(cb.equal(root.get("supplier").get("id"), f.supplierId()));
            }
            if (f.warehouseId() != null) {
                where.add(cb.equal(root.get("warehouse").get("id"), f.warehouseId()));
            }
            if (Boolean.TRUE.equals(f.overdue())) {
                where.add(root.get("status").in(PurchaseOrderStatus.OPEN));
                where.add(cb.lessThan(root.get("expectedDate"), LocalDate.now()));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String pattern = Like.contains(f.search());
                Join<PurchaseOrder, Supplier> supplier = root.join("supplier", JoinType.INNER);
                Subquery<Long> line = query.subquery(Long.class);
                var lines = line.from(PurchaseOrderLine.class);
                line.select(lines.get("id")).where(cb.equal(lines.get("purchaseOrder"), root),
                        cb.or(cb.like(cb.lower(lines.get("sku")), pattern, Like.ESCAPE),
                                cb.like(cb.lower(lines.get("productName")), pattern, Like.ESCAPE)));
                where.add(cb.or(cb.like(cb.lower(root.get("number")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(supplier.get("name")), pattern, Like.ESCAPE), cb.exists(line)));
            }
            if (f.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (f.to() != null) {
                where.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    PurchaseOrderResponse toResponse(PurchaseOrder po, boolean withReceipts) {
        List<PurchaseOrderLineResponse> lines = po.getLines().stream().map(l -> new PurchaseOrderLineResponse(l.getId(),
                l.getProduct().getId(), l.getSku(), l.getProductName(), l.getQuantityOrdered(), l.getQuantityReceived(),
                l.getQuantityOutstanding(), l.getUnitCost(), l.getLineTotal())).toList();
        List<ReceiptSummary> receiptList = !withReceipts || po.getId() == null ? List.of()
                : receipts.findByPurchaseOrderIdOrderByReceivedAt(po.getId()).stream()
                .map(r -> new ReceiptSummary(r.getId(), r.getNumber(), r.getTotalUnits(), r.getTotalAmount(),
                        r.getReceivedBy(), r.getReceivedAt())).toList();
        return new PurchaseOrderResponse(po.getId(), po.getNumber(), po.getSupplier().getId(), po.getSupplier().getName(),
                po.getSupplier().getEmail(), po.getWarehouse().getId(), po.getWarehouse().getCode(),
                po.getWarehouse().getName(), po.getStatus().name(), po.getOrderDate(), po.getExpectedDate(),
                po.getReceivedAt(), po.isOverdue(), po.getNotes(), po.getTotalAmount(),
                lines.stream().mapToInt(PurchaseOrderLineResponse::quantityOrdered).sum(),
                lines.stream().mapToInt(PurchaseOrderLineResponse::quantityOutstanding).sum(), po.getCreatedBy(),
                po.getCreatedAt(), po.getUpdatedAt(), lines, receiptList);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
