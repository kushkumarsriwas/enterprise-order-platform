package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.InventoryRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
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

/** The public marketplace: products of every merchant that are listed for sale. */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final Set<String> SORTABLE = Set.of("name", "price", "createdAt");

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /** One query for the page of products + one batched query for their stock rows (no N+1). */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String search, Long categoryId, Long merchantId, BigDecimal minPrice,
                                                BigDecimal maxPrice, boolean inStockOnly, int page, int size,
                                                String sort) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.isTrue(root.get("listed")));
            predicates.add(cb.isTrue(root.get("merchantActive")));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("sku")), like),
                        cb.like(cb.lower(root.get("merchantName")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (merchantId != null) {
                predicates.add(cb.equal(root.get("merchantId"), merchantId));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
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
        return PageResponse.from(result, product -> ProductMapper.toPublic(product, stock.get(product.getId())));
    }

    /** Cache-aside: the first call reads PostgreSQL, later calls are served from Redis until evicted. */
    @Cacheable(cacheNames = ProductCache.PRODUCT, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        log.debug("Cache miss for product {} - loading from database", id);
        Product product = productRepository.findById(id)
                .filter(Product::isPurchasable)
                .orElseThrow(() -> ApiException.notFound("Product " + id + " not found"));
        return ProductMapper.toPublic(product, inventoryRepository.findById(id).orElse(null));
    }

    static Sort parseSort(String sort) {
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
