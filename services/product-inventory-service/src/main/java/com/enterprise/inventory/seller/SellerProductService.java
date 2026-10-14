package com.enterprise.inventory.seller;

import com.enterprise.inventory.catalog.CatalogDtos.BulkRequest;
import com.enterprise.inventory.catalog.CatalogDtos.BulkResult;
import com.enterprise.inventory.catalog.CatalogDtos.CreateProductRequest;
import com.enterprise.inventory.catalog.CatalogDtos.IncomingPurchase;
import com.enterprise.inventory.catalog.CatalogDtos.SellerProductDetail;
import com.enterprise.inventory.catalog.CatalogDtos.SellerProductResponse;
import com.enterprise.inventory.catalog.CatalogDtos.UpdateProductRequest;
import com.enterprise.inventory.catalog.CatalogDtos.WarehouseQuantity;
import com.enterprise.inventory.catalog.Category;
import com.enterprise.inventory.catalog.CategoryRepository;
import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.ProductImage;
import com.enterprise.inventory.catalog.ProductImageRepository;
import com.enterprise.inventory.catalog.ProductMapper;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.catalog.SellerProductQuery;
import com.enterprise.inventory.catalog.Unit;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.purchasing.PurchaseOrderLine;
import com.enterprise.inventory.purchasing.PurchaseOrderRepository;
import com.enterprise.inventory.purchasing.PurchaseOrderStatus;
import com.enterprise.inventory.purchasing.Supplier;
import com.enterprise.inventory.purchasing.SupplierService;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.InventoryRepository;
import com.enterprise.inventory.stock.InventoryService;
import com.enterprise.inventory.stock.MovementType;
import com.enterprise.inventory.stock.StockDtos.MovementResponse;
import com.enterprise.inventory.stock.StockLedger;
import com.enterprise.inventory.stock.StockMovementRepository;
import com.enterprise.inventory.stock.WarehouseStockRepository;
import com.enterprise.inventory.warehouse.Warehouse;
import com.enterprise.inventory.warehouse.WarehouseService;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * A seller's catalogue: every product is inventory; {@code listed} decides whether customers can buy it.
 * Always scoped to one seller id (the logged-in seller, or the seller the administrator is looking at).
 */
@Service
public class SellerProductService {

    private static final Logger log = LoggerFactory.getLogger(SellerProductService.class);
    private static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final Set<MovementType> SALES = Set.of(MovementType.SOLD, MovementType.RETURNED);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final PurchaseOrderRepository purchaseOrders;
    private final ProductImageRepository images;
    private final SellerProductQuery productQuery;
    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private final SupplierService supplierService;
    private final StoreSettingsService storeSettings;
    private final StockLedger ledger;
    private final ProductCache productCache;

    public SellerProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                                InventoryRepository inventoryRepository, WarehouseStockRepository stockRepository,
                                StockMovementRepository movementRepository, PurchaseOrderRepository purchaseOrders,
                                ProductImageRepository images, SellerProductQuery productQuery,
                                InventoryService inventoryService, WarehouseService warehouseService,
                                SupplierService supplierService, StoreSettingsService storeSettings,
                                StockLedger ledger, ProductCache productCache) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.purchaseOrders = purchaseOrders;
        this.images = images;
        this.productQuery = productQuery;
        this.inventoryService = inventoryService;
        this.warehouseService = warehouseService;
        this.supplierService = supplierService;
        this.storeSettings = storeSettings;
        this.ledger = ledger;
        this.productCache = productCache;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<SellerProductResponse> list(Long sellerId, SellerProductQuery.Filter filter, int page, int size) {
        Page<Product> products = productQuery.find(sellerId, filter, page, size);
        return PageResponse.from(products, mapper(sellerId, products.getContent()));
    }

    @Transactional(readOnly = true)
    public List<SellerProductResponse> export(Long sellerId, SellerProductQuery.Filter filter) {
        List<Product> products = productQuery.all(sellerId, filter, 5000);
        return products.stream().map(mapper(sellerId, products)).toList();
    }

    @Transactional(readOnly = true)
    public SellerProductDetail detail(Long sellerId, Long id) {
        Product product = owned(sellerId, id);
        Inventory inventory = inventoryRepository.findById(id).orElse(null);
        List<WarehouseQuantity> warehouses = stockRepository.findByProduct(id).stream()
                .map(s -> new WarehouseQuantity(s.getWarehouse().getId(), s.getWarehouse().getCode(),
                        s.getWarehouse().getName(), s.getWarehouse().isDefaultWarehouse(), s.getOnHand(),
                        s.getBinLocation())).toList();
        List<MovementResponse> movements = movementRepository.recentForProduct(id, Limit.of(25)).stream()
                .map(MovementResponse::from).toList();
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long units = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (Object[] row : movementRepository.salesByProduct(sellerId, SALES, monthAgo, Instant.now().plusSeconds(1))) {
            if (row[0].equals(id)) {
                units = ((Number) row[1]).longValue();
                revenue = row[2] == null ? BigDecimal.ZERO : new BigDecimal(row[2].toString());
            }
        }
        BigDecimal daily = BigDecimal.valueOf(Math.max(units, 0)).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        int available = inventory == null ? 0 : inventory.getAvailableQuantity();
        Integer daysOfCover = daily.signum() > 0
                ? BigDecimal.valueOf(available).divide(daily, 0, RoundingMode.FLOOR).intValue() : null;
        List<PurchaseOrderLine> open = purchaseOrders.openLinesForProduct(sellerId, PurchaseOrderStatus.OPEN, id);
        List<IncomingPurchase> incoming = open.stream().map(l -> new IncomingPurchase(l.getPurchaseOrder().getId(),
                l.getPurchaseOrder().getNumber(), l.getPurchaseOrder().getSupplier().getName(),
                l.getPurchaseOrder().getStatus().name(),
                l.getPurchaseOrder().getExpectedDate() == null ? null : l.getPurchaseOrder().getExpectedDate().toString(),
                l.getQuantityOutstanding())).toList();
        int incomingUnits = incoming.stream().mapToInt(IncomingPurchase::quantityOutstanding).sum();
        return new SellerProductDetail(ProductMapper.toSeller(product, inventory, incomingUnits), warehouses, movements,
                units, revenue.setScale(2, RoundingMode.HALF_UP), daily, daysOfCover, incoming);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public SellerProductResponse create(Long sellerId, String sellerName, String actor, CreateProductRequest request) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsBySellerIdAndSkuIgnoreCase(sellerId, sku)) {
            throw ApiException.conflict("You already have a product with SKU " + sku);
        }
        String barcode = blankToNull(request.barcode());
        if (barcode != null && productRepository.existsBySellerIdAndBarcode(sellerId, barcode)) {
            throw ApiException.conflict("Barcode " + barcode + " is already used by another of your products");
        }
        boolean listed = Boolean.TRUE.equals(request.listed());
        Category category = findCategory(request.categoryId());
        validate(request.price(), request.compareAtPrice(), listed, category);
        Product product = new Product(sellerId, sellerName, sku, request.name().trim(), request.price(), request.costPrice());
        product.describe(request.name().trim(), blankToNull(request.brand()), barcode, blankToNull(request.description()),
                request.unit(), category, blankToNull(request.imageUrl()));
        product.price(request.price(), request.compareAtPrice(), request.costPrice());
        product.planning(request.reorderPoint() == null ? 5 : request.reorderPoint(),
                request.reorderQuantity() == null ? 20 : request.reorderQuantity(), findSupplier(sellerId,
                        request.preferredSupplierId()));
        product.setListed(listed);
        SellerSettings settings = storeSettings.settingsOf(sellerId);
        product.setStoreVisibility(!settings.isVacationMode(), settings.isSuspended());
        productRepository.save(product);
        inventoryRepository.save(new Inventory(product));
        int opening = request.initialStock() == null ? 0 : request.initialStock();
        if (opening > 0) {
            Warehouse warehouse = warehouseService.resolve(sellerId, request.warehouseId());
            ledger.stockIn(ledger.lock(product.getId()), warehouse, MovementType.OPENING_STOCK, opening,
                    request.costPrice(), "OPENING", "Opening stock", actor);
        }
        productCache.evictListings();
        log.info("Seller {} created product {} ({}), listed={}, opening stock {}", actor, product.getId(), sku, listed,
                opening);
        return ProductMapper.toSeller(product, inventoryRepository.findById(product.getId()).orElse(null), 0);
    }

    @Transactional
    public SellerProductResponse update(Long sellerId, Long id, UpdateProductRequest request) {
        Product product = owned(sellerId, id);
        String barcode = blankToNull(request.barcode());
        if (barcode != null && productRepository.existsBySellerIdAndBarcodeAndIdNot(sellerId, barcode, id)) {
            throw ApiException.conflict("Barcode " + barcode + " is already used by another of your products");
        }
        Category category = findCategory(request.categoryId());
        validate(request.price(), request.compareAtPrice(), request.listed(), category);
        product.describe(request.name().trim(), blankToNull(request.brand()), barcode, blankToNull(request.description()),
                request.unit() == null ? product.getUnit() : request.unit(), category,
                product.getImageUrl() != null && product.getImageUrl().startsWith("/api/products/")
                        && blankToNull(request.imageUrl()) == null ? product.getImageUrl() : blankToNull(request.imageUrl()));
        product.price(request.price(), request.compareAtPrice(), request.costPrice());
        product.planning(request.reorderPoint(), request.reorderQuantity(),
                findSupplier(sellerId, request.preferredSupplierId()));
        product.setListed(request.listed());
        productRepository.flush();
        productCache.evictProduct(id);
        productCache.evictListings();
        return toResponse(sellerId, product);
    }

    /** Show or hide a product for customers without touching its stock. */
    @Transactional
    public SellerProductResponse setListing(Long sellerId, Long id, boolean listed) {
        Product product = owned(sellerId, id);
        if (listed) {
            validate(product.getPrice(), product.getCompareAtPrice(), true, product.getCategory());
        }
        product.setListed(listed);
        productRepository.flush();
        productCache.evictProduct(id);
        productCache.evictListings();
        log.info("Product {} is now {}", product.getSku(), listed ? "listed on the marketplace" : "inventory only");
        return toResponse(sellerId, product);
    }

    /** One action on many products; products that can't take the action are reported, not failed. */
    @Transactional
    public BulkResult bulk(Long sellerId, BulkRequest request) {
        List<String> skipped = new ArrayList<>();
        int updated = 0;
        Category category = null;
        if (request.action() == com.enterprise.inventory.catalog.CatalogDtos.BulkAction.SET_CATEGORY) {
            category = findCategory(request.categoryId());
            if (category == null) {
                throw ApiException.badRequest("Choose the category to apply");
            }
        }
        for (Long id : request.productIds()) {
            Product product = productRepository.findByIdAndSellerId(id, sellerId).filter(Product::isActive).orElse(null);
            if (product == null) {
                skipped.add("#" + id + ": not found");
                continue;
            }
            switch (request.action()) {
                case LIST -> {
                    if (product.getCategory() == null) {
                        skipped.add(product.getSku() + ": choose a category before listing");
                        continue;
                    }
                    product.setListed(true);
                }
                case UNLIST -> product.setListed(false);
                case ARCHIVE -> product.archive();
                case SET_CATEGORY -> product.setCategory(category);
                case ADJUST_PRICE_PERCENT -> {
                    if (request.percent() == null) {
                        throw ApiException.badRequest("Enter the percentage to change prices by");
                    }
                    BigDecimal factor = BigDecimal.ONE.add(request.percent().divide(BigDecimal.valueOf(100), 6,
                            RoundingMode.HALF_UP));
                    BigDecimal price = product.getPrice().multiply(factor).setScale(2, RoundingMode.HALF_UP);
                    if (price.compareTo(new BigDecimal("0.01")) < 0) {
                        skipped.add(product.getSku() + ": price would drop below 0.01");
                        continue;
                    }
                    product.setPriceOnly(price);
                }
                case SET_REORDER_POINT -> {
                    if (request.reorderPoint() == null) {
                        throw ApiException.badRequest("Enter the reorder point");
                    }
                    product.planning(request.reorderPoint(), product.getReorderQuantity(), product.getPreferredSupplier());
                }
            }
            productCache.evictProduct(id);
            updated++;
        }
        productCache.evictListings();
        log.info("Bulk {} on {} product(s) of seller {} ({} skipped)", request.action(), updated, sellerId, skipped.size());
        return new BulkResult(updated, skipped);
    }

    /** Soft delete: orders and the stock ledger still reference the product, so it is archived, not removed. */
    @Transactional
    public void archive(Long sellerId, Long id) {
        Product product = owned(sellerId, id);
        Inventory inventory = inventoryRepository.findById(id).orElse(null);
        if (inventory != null && inventory.getReservedQuantity() > 0) {
            throw ApiException.conflict(inventory.getReservedQuantity()
                    + " unit(s) are reserved for orders awaiting payment - archive the product later");
        }
        product.archive();
        productCache.evictProduct(id);
        productCache.evictListings();
    }

    /** Upload or replace the product photo (JPEG, PNG or WebP, max 2 MB; the file content is checked too). */
    @Transactional
    public SellerProductResponse uploadImage(Long sellerId, Long id, MultipartFile file) {
        Product product = owned(sellerId, id);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose an image file");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw ApiException.badRequest("The image must be 2 MB or smaller");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("The image could not be read");
        }
        String contentType = detectImageType(data);
        if (contentType == null) {
            throw ApiException.badRequest("Only JPEG, PNG or WebP images are accepted");
        }
        ProductImage image = images.findById(id).orElseGet(() -> new ProductImage(id));
        image.replace(contentType, data);
        images.save(image);
        product.setImageUrl("/api/products/" + id + "/image?v=" + image.getUpdatedAt().toEpochMilli());
        productRepository.flush();
        productCache.evictProduct(id);
        productCache.evictListings();
        return toResponse(sellerId, product);
    }

    @Transactional
    public SellerProductResponse deleteImage(Long sellerId, Long id) {
        Product product = owned(sellerId, id);
        images.findById(id).ifPresent(images::delete);
        product.setImageUrl(null);
        productRepository.flush();
        productCache.evictProduct(id);
        productCache.evictListings();
        return toResponse(sellerId, product);
    }

    // ------------------------------------------------------------------ helpers

    /** Magic bytes, not the file name or the Content-Type header (both can lie). */
    static String detectImageType(byte[] data) {
        if (data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static void validate(BigDecimal price, BigDecimal compareAtPrice, boolean listed, Category category) {
        if (compareAtPrice != null && compareAtPrice.compareTo(price) <= 0) {
            throw ApiException.badRequest("The 'was' price must be higher than the selling price");
        }
        if (listed && category == null) {
            throw ApiException.badRequest("Choose a category before listing the product on the marketplace");
        }
    }

    private Product owned(Long sellerId, Long id) {
        return productRepository.findByIdAndSellerId(id, sellerId)
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Product " + id + " not found in your inventory"));
    }

    private Category findCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.badRequest("Category " + categoryId + " does not exist"));
    }

    private Supplier findSupplier(Long sellerId, Long supplierId) {
        return supplierId == null ? null : supplierService.requireActive(sellerId, supplierId);
    }

    private SellerProductResponse toResponse(Long sellerId, Product product) {
        return ProductMapper.toSeller(product, inventoryRepository.findById(product.getId()).orElse(null),
                inventoryService.incoming(sellerId, List.of(product.getId())).getOrDefault(product.getId(), 0));
    }

    private Function<Product, SellerProductResponse> mapper(Long sellerId, List<Product> products) {
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, Inventory> stock = ids.isEmpty() ? Map.of() : inventoryRepository.findByProductIdIn(ids).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        Map<Long, Integer> incoming = inventoryService.incoming(sellerId, ids);
        return p -> ProductMapper.toSeller(p, stock.get(p.getId()), incoming.getOrDefault(p.getId(), 0));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static Unit parseUnit(String value) {
        return value == null || value.isBlank() ? Unit.PIECE : Unit.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
