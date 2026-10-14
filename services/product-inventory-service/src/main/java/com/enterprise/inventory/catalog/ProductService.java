package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.CreateProductRequest;
import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.catalog.CatalogDtos.UpdateProductRequest;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.InventoryRepository;
import com.enterprise.inventory.stock.MovementType;
import com.enterprise.inventory.stock.StockMovement;
import com.enterprise.inventory.stock.StockMovementRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final Set<String> SORTABLE = Set.of("name", "price", "createdAt");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final ProductCache productCache;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          InventoryRepository inventoryRepository, StockMovementRepository movementRepository,
                          ProductCache productCache) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.productCache = productCache;
    }

    /** Paginated search: one query for the page of products + one batched query for their stock rows. */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String search, Long categoryId, boolean inStockOnly,
                                                boolean includeInactive, int page, int size, String sort) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeInactive) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("sku")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (inStockOnly) {
                Subquery<Long> stock = query.subquery(Long.class);
                var inventory = stock.from(Inventory.class);
                stock.select(inventory.get("productId"))
                        .where(cb.equal(inventory.get("productId"), root.get("id")),
                                cb.greaterThan(inventory.get("availableQuantity"), 0));
                predicates.add(cb.exists(stock));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Product> result = productRepository.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100), parseSort(sort)));
        Map<Long, Inventory> stock = inventoryRepository.findAllById(
                        result.getContent().stream().map(Product::getId).toList())
                .stream().collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        return PageResponse.from(result, product -> ProductMapper.toResponse(product, stock.get(product.getId())));
    }

    /** Cache-aside: the first call reads PostgreSQL, later calls are served from Redis until evicted. */
    @Cacheable(cacheNames = ProductCache.PRODUCT, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        log.debug("Cache miss for product {} - loading from database", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product " + id + " not found"));
        return ProductMapper.toResponse(product, inventoryRepository.findById(id).orElse(null));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request, String actor) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("A product with SKU " + sku + " already exists");
        }
        Product product = productRepository.save(new Product(sku, request.name().trim(), request.description(),
                request.price(), findCategory(request.categoryId()), request.imageUrl()));
        int reorderLevel = request.reorderLevel() == null ? 5 : request.reorderLevel();
        Inventory inventory = inventoryRepository.save(new Inventory(product, request.initialStock(), reorderLevel));
        movementRepository.save(new StockMovement(product.getId(), MovementType.INITIAL, request.initialStock(),
                null, "Initial stock", actor));
        productCache.evictCategories();
        log.info("Product {} ({}) created by {}", product.getId(), sku, actor);
        return ProductMapper.toResponse(product, inventory);
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request, String actor) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product " + id + " not found"));
        product.update(request.name().trim(), request.description(), request.price(),
                findCategory(request.categoryId()), request.imageUrl(),
                request.active() == null ? product.isActive() : request.active());
        productRepository.flush();
        productCache.evictProduct(id);
        productCache.evictCategories();
        log.info("Product {} updated by {}", id, actor);
        return ProductMapper.toResponse(product, inventoryRepository.findById(id).orElse(null));
    }

    /** Soft delete: old orders still reference the product, so it is hidden instead of removed. */
    @Transactional
    public void deactivate(Long id, String actor) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product " + id + " not found"));
        product.deactivate();
        productCache.evictProduct(id);
        productCache.evictCategories();
        log.info("Product {} deactivated by {}", id, actor);
    }

    private Category findCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.badRequest("Category " + categoryId + " does not exist"));
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by("name");
        }
        String[] parts = sort.split(",");
        String property = SORTABLE.contains(parts[0]) ? parts[0] : "name";
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property).and(Sort.by("id"));
    }
}
