package com.enterprise.inventory.warehouse;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.numbering.DocumentNumberService;
import com.enterprise.inventory.numbering.DocumentType;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.StockLedger;
import com.enterprise.inventory.warehouse.WarehouseDtos.TransferLineRequest;
import com.enterprise.inventory.warehouse.WarehouseDtos.TransferLineResponse;
import com.enterprise.inventory.warehouse.WarehouseDtos.TransferRequest;
import com.enterprise.inventory.warehouse.WarehouseDtos.TransferResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moves stock between a seller's warehouses as one document (all lines or nothing). */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final StockTransferRepository repository;
    private final WarehouseService warehouseService;
    private final StockLedger ledger;
    private final DocumentNumberService numbers;
    private final ProductCache productCache;

    public TransferService(StockTransferRepository repository, WarehouseService warehouseService, StockLedger ledger,
                           DocumentNumberService numbers, ProductCache productCache) {
        this.repository = repository;
        this.warehouseService = warehouseService;
        this.ledger = ledger;
        this.numbers = numbers;
        this.productCache = productCache;
    }

    @Transactional
    public TransferResponse transfer(Long sellerId, String actor, TransferRequest request) {
        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw ApiException.badRequest("Choose two different warehouses");
        }
        Warehouse from = warehouseService.resolve(sellerId, request.fromWarehouseId());
        Warehouse to = warehouseService.resolve(sellerId, request.toWarehouseId());
        // merge duplicate lines and lock products in ascending id order (no deadlocks between transfers)
        Map<Long, Integer> quantities = new TreeMap<>();
        for (TransferLineRequest line : request.lines()) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }
        StockTransfer transfer = new StockTransfer(sellerId, numbers.next(sellerId, DocumentType.STOCK_TRANSFER), from, to,
                blankToNull(request.note()), actor);
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            Inventory inventory = ledger.lock(line.getKey());
            Product product = inventory.getProduct();
            if (!product.isOwnedBy(sellerId) || !product.isActive()) {
                throw ApiException.notFound("Product " + line.getKey() + " not found in your inventory");
            }
            ledger.transfer(inventory, from, to, line.getValue(), transfer.getNumber(), transfer.getNote(), actor);
            transfer.addLine(product, line.getValue());
        }
        repository.save(transfer);
        log.info("Transfer {} by {}: {} unit(s) {} -> {}", transfer.getNumber(), actor, transfer.getTotalUnits(),
                from.getCode(), to.getCode());
        quantities.keySet().forEach(productCache::evictProduct);
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> list(Long sellerId, Long warehouseId, LocalDate from, LocalDate to, int page,
                                               int size) {
        Specification<StockTransfer> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("sellerId"), sellerId));
            if (warehouseId != null) {
                where.add(cb.or(cb.equal(root.get("fromWarehouse").get("id"), warehouseId),
                        cb.equal(root.get("toWarehouse").get("id"), warehouseId)));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt", "id"))), this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransferResponse get(Long sellerId, Long id) {
        return repository.findWithLinesByIdAndSellerId(id, sellerId).map(this::toResponse)
                .orElseThrow(() -> ApiException.notFound("Transfer " + id + " not found"));
    }

    private TransferResponse toResponse(StockTransfer t) {
        return new TransferResponse(t.getId(), t.getNumber(), t.getFromWarehouse().getId(),
                t.getFromWarehouse().label(), t.getToWarehouse().getId(), t.getToWarehouse().label(), t.getTotalUnits(),
                t.getNote(), t.getCreatedBy(), t.getCreatedAt(),
                t.getLines().stream().map(l -> new TransferLineResponse(l.getProduct().getId(), l.getSku(),
                        l.getProductName(), l.getQuantity())).toList());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
