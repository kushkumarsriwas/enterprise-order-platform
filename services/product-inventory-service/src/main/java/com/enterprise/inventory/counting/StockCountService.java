package com.enterprise.inventory.counting;

import com.enterprise.inventory.catalog.Category;
import com.enterprise.inventory.catalog.CategoryRepository;
import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.counting.CountDtos.CountLineResponse;
import com.enterprise.inventory.counting.CountDtos.CountResponse;
import com.enterprise.inventory.counting.CountDtos.CountedLine;
import com.enterprise.inventory.counting.CountDtos.RecordCountsRequest;
import com.enterprise.inventory.counting.CountDtos.StartCountRequest;
import com.enterprise.inventory.numbering.DocumentNumberService;
import com.enterprise.inventory.numbering.DocumentType;
import com.enterprise.inventory.stock.AdjustmentReason;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.MovementType;
import com.enterprise.inventory.stock.StockLedger;
import com.enterprise.inventory.stock.WarehouseStock;
import com.enterprise.inventory.stock.WarehouseStockRepository;
import com.enterprise.inventory.warehouse.Warehouse;
import com.enterprise.inventory.warehouse.WarehouseService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

/** Cycle counts: freeze expected quantities, record what is on the shelves, post the differences. */
@Service
public class StockCountService {

    private static final Logger log = LoggerFactory.getLogger(StockCountService.class);
    private static final int MAX_LINES = 2000;

    private final StockCountRepository repository;
    private final WarehouseService warehouseService;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseStockRepository stockRepository;
    private final StockLedger ledger;
    private final DocumentNumberService numbers;
    private final ProductCache productCache;

    public StockCountService(StockCountRepository repository, WarehouseService warehouseService,
                             CategoryRepository categoryRepository, ProductRepository productRepository,
                             WarehouseStockRepository stockRepository, StockLedger ledger,
                             DocumentNumberService numbers, ProductCache productCache) {
        this.repository = repository;
        this.warehouseService = warehouseService;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.ledger = ledger;
        this.numbers = numbers;
        this.productCache = productCache;
    }

    @Transactional
    public CountResponse start(Long sellerId, String actor, StartCountRequest request) {
        Warehouse warehouse = warehouseService.resolve(sellerId, request.warehouseId());
        if (repository.existsBySellerIdAndWarehouseIdAndStatus(sellerId, warehouse.getId(), StockCountStatus.IN_PROGRESS)) {
            throw ApiException.conflict("A stock count of " + warehouse.getCode()
                    + " is already in progress - complete or cancel it first");
        }
        Category category = request.categoryId() == null ? null : categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Category " + request.categoryId() + " does not exist"));
        Map<Long, WarehouseStock> stock = new HashMap<>();
        stockRepository.findByWarehouse(warehouse.getId()).forEach(s -> stock.put(s.getProduct().getId(), s));
        List<Product> products = productRepository.findBySellerIdAndActiveTrue(sellerId).stream()
                .filter(p -> category == null || (p.getCategory() != null && p.getCategory().getId().equals(category.getId())))
                .sorted(Comparator.comparing(Product::getName))
                .toList();
        if (products.isEmpty()) {
            throw ApiException.badRequest("There are no products to count" + (category == null ? "" : " in " + category.getName()));
        }
        if (products.size() > MAX_LINES) {
            throw ApiException.badRequest("Too many products for one count (" + products.size()
                    + "). Count one category at a time.");
        }
        StockCount count = new StockCount(sellerId, numbers.next(sellerId, DocumentType.STOCK_COUNT), warehouse, category,
                trim(request.note()), actor);
        for (Product product : products) {
            WarehouseStock current = stock.get(product.getId());
            count.addLine(new StockCountLine(product, current == null ? null : current.getBinLocation(),
                    current == null ? 0 : current.getOnHand()));
        }
        repository.save(count);
        log.info("Stock count {} of {} started by {} ({} product(s))", count.getNumber(), warehouse.getCode(), actor,
                products.size());
        return toResponse(count, true);
    }

    @Transactional
    public CountResponse record(Long sellerId, Long id, RecordCountsRequest request) {
        StockCount count = load(sellerId, id);
        count.requireInProgress();
        Map<Long, StockCountLine> lines = new HashMap<>();
        count.getLines().forEach(line -> lines.put(line.getId(), line));
        for (CountedLine counted : request.lines()) {
            StockCountLine line = lines.get(counted.lineId());
            if (line == null) {
                throw ApiException.badRequest("Line " + counted.lineId() + " is not part of " + count.getNumber());
            }
            line.record(counted.countedQuantity());
        }
        return toResponse(count, true);
    }

    /**
     * Posts every counted difference as a COUNT_ADJUSTMENT. If stock moved while the count was running, a
     * shortage is capped so the warehouse never goes negative and reserved units stay covered.
     */
    @Transactional
    public CountResponse complete(Long sellerId, Long id, String actor) {
        StockCount count = load(sellerId, id);
        count.requireInProgress();
        List<StockCountLine> differences = count.getLines().stream()
                .filter(line -> line.getCountedQuantity() != null && line.variance() != 0)
                .sorted(Comparator.comparing(line -> line.getProduct().getId()))
                .toList();
        for (StockCountLine line : differences) {
            Inventory inventory = ledger.lock(line.getProduct().getId());
            int change = line.variance();
            if (change < 0) {
                int inWarehouse = stockRepository.find(line.getProduct().getId(), count.getWarehouse().getId())
                        .map(WarehouseStock::getOnHand).orElse(0);
                change = Math.max(change, -Math.min(inWarehouse, inventory.getAvailableQuantity()));
            }
            if (change != 0) {
                ledger.adjust(inventory, count.getWarehouse(), MovementType.COUNT_ADJUSTMENT, change, AdjustmentReason.COUNT,
                        count.getNumber(), "Counted " + line.getCountedQuantity() + ", expected " + line.getExpectedQuantity(),
                        actor);
                productCache.evictProduct(line.getProduct().getId());
            }
        }
        count.complete(actor);
        log.info("Stock count {} completed by {}: {} difference(s)", count.getNumber(), actor, differences.size());
        return toResponse(count, true);
    }

    @Transactional
    public CountResponse cancel(Long sellerId, Long id, String actor) {
        StockCount count = load(sellerId, id);
        count.cancel(actor);
        return toResponse(count, true);
    }

    @Transactional(readOnly = true)
    public CountResponse get(Long sellerId, Long id) {
        return toResponse(load(sellerId, id), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<CountResponse> list(Long sellerId, StockCountStatus status, Long warehouseId, int page, int size) {
        Specification<StockCount> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("sellerId"), sellerId));
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (warehouseId != null) {
                where.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))), count -> toResponse(count, false));
    }

    private StockCount load(Long sellerId, Long id) {
        return repository.findWithLinesByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> ApiException.notFound("Stock count " + id + " not found"));
    }

    static CountResponse toResponse(StockCount c, boolean withLines) {
        int counted = 0;
        int different = 0;
        int varianceUnits = 0;
        BigDecimal varianceValue = BigDecimal.ZERO;
        List<CountLineResponse> items = new ArrayList<>();
        for (StockCountLine line : c.getLines()) {
            int variance = line.variance();
            BigDecimal value = line.getUnitCost().multiply(BigDecimal.valueOf(variance)).setScale(2, RoundingMode.HALF_UP);
            if (line.getCountedQuantity() != null) {
                counted++;
            }
            if (variance != 0) {
                different++;
                varianceUnits += variance;
                varianceValue = varianceValue.add(value);
            }
            if (withLines) {
                items.add(new CountLineResponse(line.getId(), line.getProduct().getId(), line.getSku(),
                        line.getProductName(), line.getBinLocation(), line.getExpectedQuantity(),
                        line.getCountedQuantity(), variance, line.getUnitCost(), value));
            }
        }
        return new CountResponse(c.getId(), c.getNumber(), c.getWarehouse().getId(), c.getWarehouse().getCode(),
                c.getWarehouse().getName(), c.getCategory() == null ? null : c.getCategory().getId(),
                c.getCategory() == null ? null : c.getCategory().getName(), c.getStatus().name(), c.getNote(),
                c.getLines().size(), counted, different, varianceUnits, varianceValue, c.getCreatedBy(), c.getCreatedAt(),
                c.getCompletedBy(), c.getCompletedAt(), items);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
