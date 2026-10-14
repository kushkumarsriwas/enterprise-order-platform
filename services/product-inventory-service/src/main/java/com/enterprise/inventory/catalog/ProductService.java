package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.CategoryResponse;
import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.catalog.StorefrontDtos.FacetValue;
import com.enterprise.inventory.catalog.StorefrontDtos.HomeSections;
import com.enterprise.inventory.catalog.StorefrontDtos.RelatedProducts;
import com.enterprise.inventory.catalog.StorefrontDtos.SearchCriteria;
import com.enterprise.inventory.catalog.StorefrontDtos.SearchFacets;
import com.enterprise.inventory.catalog.StorefrontDtos.Suggestions;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.CriteriaPage;
import com.enterprise.inventory.common.Like;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.InventoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer-facing catalogue: products of every seller that are listed for sale. Search uses the JPA
 * Criteria API: every word must match, results are ordered by relevance, and the filter panel (facets)
 * is computed for the same search with GROUP BY queries.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final int MAX_WORDS = 6;

    private final EntityManager entityManager;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CategoryService categoryService;

    public ProductService(EntityManager entityManager, ProductRepository productRepository,
                          InventoryRepository inventoryRepository, CategoryService categoryService) {
        this.entityManager = entityManager;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(SearchCriteria criteria, int page, int size) {
        Page<Product> result = CriteriaPage.find(entityManager, Product.class, (cb, query, root, count) -> {
            if (!count) {
                root.fetch("category", JoinType.LEFT);
            }
            List<Predicate> where = filters(cb, query, root, criteria, true);
            return CriteriaPage.Parts.of(where, count ? List.of() : order(cb, root, criteria));
        }, page, size);
        return PageResponse.from(result, toPublic(result.getContent()));
    }

    /** Filter panel of the search results: categories, brands, sellers, price range, ratings, deals, in stock. */
    @Transactional(readOnly = true)
    public SearchFacets facets(SearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        // category facet ignores the selected category so the shopper can switch department
        SearchCriteria anyCategory = new SearchCriteria(criteria.query(), null, criteria.sellerIds(), criteria.brands(),
                criteria.minPrice(), criteria.maxPrice(), criteria.minRating(), criteria.inStockOnly(),
                criteria.dealsOnly(), null);
        CriteriaQuery<Object[]> byCategory = cb.createQuery(Object[].class);
        Root<Product> c = byCategory.from(Product.class);
        Join<Product, Category> category = c.join("category", JoinType.INNER);
        byCategory.multiselect(category.get("id"), category.get("name"), cb.count(c))
                .where(filters(cb, byCategory, c, anyCategory, true).toArray(Predicate[]::new))
                .groupBy(category.get("id"), category.get("name"))
                .orderBy(cb.desc(cb.count(c)));
        List<FacetValue> categories = entityManager.createQuery(byCategory).getResultList().stream()
                .map(r -> new FacetValue(r[0].toString(), (String) r[1], ((Number) r[2]).longValue())).toList();

        // brand and seller facets ignore their own selection (multi-select checkboxes)
        SearchCriteria anyBrand = new SearchCriteria(criteria.query(), criteria.categoryId(), criteria.sellerIds(), null,
                criteria.minPrice(), criteria.maxPrice(), criteria.minRating(), criteria.inStockOnly(),
                criteria.dealsOnly(), null);
        CriteriaQuery<Object[]> byBrand = cb.createQuery(Object[].class);
        Root<Product> b = byBrand.from(Product.class);
        List<Predicate> brandWhere = filters(cb, byBrand, b, anyBrand, true);
        brandWhere.add(cb.isNotNull(b.get("brand")));
        byBrand.multiselect(b.get("brand"), cb.count(b)).where(brandWhere.toArray(Predicate[]::new))
                .groupBy(b.get("brand")).orderBy(cb.desc(cb.count(b)), cb.asc(b.get("brand")));
        List<FacetValue> brands = entityManager.createQuery(byBrand).setMaxResults(15).getResultList().stream()
                .map(r -> new FacetValue((String) r[0], (String) r[0], ((Number) r[1]).longValue())).toList();

        SearchCriteria anySeller = new SearchCriteria(criteria.query(), criteria.categoryId(), null, criteria.brands(),
                criteria.minPrice(), criteria.maxPrice(), criteria.minRating(), criteria.inStockOnly(),
                criteria.dealsOnly(), null);
        CriteriaQuery<Object[]> bySeller = cb.createQuery(Object[].class);
        Root<Product> s = bySeller.from(Product.class);
        bySeller.multiselect(s.get("sellerId"), s.get("sellerName"), cb.count(s))
                .where(filters(cb, bySeller, s, anySeller, true).toArray(Predicate[]::new))
                .groupBy(s.get("sellerId"), s.get("sellerName")).orderBy(cb.desc(cb.count(s)));
        List<FacetValue> sellers = entityManager.createQuery(bySeller).setMaxResults(15).getResultList().stream()
                .map(r -> new FacetValue(r[0].toString(), (String) r[1], ((Number) r[2]).longValue())).toList();

        // totals, price range, rating buckets, deals and in-stock counts for the current search (one query)
        SearchCriteria scope = new SearchCriteria(criteria.query(), criteria.categoryId(), criteria.sellerIds(),
                criteria.brands(), null, null, null, false, false, null);
        CriteriaQuery<Object[]> stats = cb.createQuery(Object[].class);
        Root<Product> p = stats.from(Product.class);
        Expression<BigDecimal> rating = p.get("ratingAverage");
        Subquery<Long> stocked = stats.subquery(Long.class);
        Root<Inventory> inventory = stocked.from(Inventory.class);
        stocked.select(inventory.get("productId")).where(cb.equal(inventory.get("productId"), p.get("id")),
                cb.gt(inventory.get("availableQuantity"), 0));
        stats.multiselect(cb.count(p), cb.min(p.get("price")), cb.max(p.get("price")),
                        countIf(cb, cb.ge(rating, new BigDecimal("4"))), countIf(cb, cb.ge(rating, new BigDecimal("3"))),
                        countIf(cb, cb.ge(rating, new BigDecimal("2"))), countIf(cb, cb.ge(rating, new BigDecimal("1"))),
                        countIf(cb, cb.gt(p.get("compareAtPrice"), p.get("price"))), countIf(cb, cb.exists(stocked)))
                .where(filters(cb, stats, p, scope, true).toArray(Predicate[]::new));
        Object[] row = entityManager.createQuery(stats).getSingleResult();
        List<FacetValue> ratings = new ArrayList<>();
        for (int stars = 4; stars >= 1; stars--) {
            ratings.add(new FacetValue(String.valueOf(stars), stars + " stars & up", number(row[3 + (4 - stars)])));
        }
        return new SearchFacets(number(row[0]), categories, brands, sellers, (BigDecimal) row[1], (BigDecimal) row[2],
                ratings, number(row[7]), number(row[8]));
    }

    /** Storefront home page in one call; cached in Redis for a minute. */
    @Cacheable(cacheNames = ProductCache.HOME, key = "'home'")
    @Transactional(readOnly = true)
    public HomeSections home() {
        List<CategoryResponse> categories = categoryService.findAll().items();
        return new HomeSections(section("deals", 12), section("bestSellers", 12), section("newArrivals", 12),
                section("topRated", 12), categories);
    }

    @Transactional(readOnly = true)
    public Suggestions suggest(String text) {
        if (text == null || text.trim().length() < 2) {
            return new Suggestions(List.of(), List.of());
        }
        String pattern = Like.contains(text);
        List<String> names = entityManager.createQuery("""
                        select p.name from Product p
                        where p.active = true and p.listed = true and p.sellerActive = true and p.storeSuspended = false
                          and (lower(p.name) like :pattern escape '\\' or lower(coalesce(p.brand, '')) like :pattern escape '\\')
                        order by p.unitsSold desc, p.name""", String.class)
                .setParameter("pattern", pattern).setMaxResults(20).getResultList().stream()
                .distinct().limit(8).toList();
        String lower = text.trim().toLowerCase(Locale.ROOT);
        List<CategoryResponse> categories = categoryService.findAll().items().stream()
                .filter(category -> category.name().toLowerCase(Locale.ROOT).contains(lower)).limit(3).toList();
        return new Suggestions(names, categories);
    }

    /** Cache-aside: the first call reads PostgreSQL, later calls are served from Redis until evicted. */
    @Cacheable(cacheNames = ProductCache.PRODUCT, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        log.debug("Cache miss for product {} - loading from the database", id);
        Product product = productRepository.findWithCategoryById(id)
                .filter(Product::isPurchasable)
                .orElseThrow(() -> ApiException.notFound("Product " + id + " not found"));
        return ProductMapper.toPublic(product, inventoryRepository.findById(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public RelatedProducts related(Long id) {
        Product product = productRepository.findWithCategoryById(id).filter(Product::isPurchasable)
                .orElseThrow(() -> ApiException.notFound("Product " + id + " not found"));
        List<Product> similar = product.getCategory() == null ? List.of() : entityManager.createQuery("""
                        select p from Product p left join fetch p.category
                        where p.category.id = :categoryId and p.id <> :id and p.active = true and p.listed = true
                          and p.sellerActive = true and p.storeSuspended = false
                        order by p.unitsSold desc, p.ratingAverage desc, p.id""", Product.class)
                .setParameter("categoryId", product.getCategory().getId()).setParameter("id", id)
                .setMaxResults(8).getResultList();
        List<Product> sameSeller = entityManager.createQuery("""
                        select p from Product p left join fetch p.category
                        where p.sellerId = :sellerId and p.id <> :id and p.active = true and p.listed = true
                          and p.sellerActive = true and p.storeSuspended = false
                        order by p.unitsSold desc, p.id""", Product.class)
                .setParameter("sellerId", product.getSellerId()).setParameter("id", id)
                .setMaxResults(8).getResultList();
        return new RelatedProducts(similar.stream().map(toPublic(similar)).toList(),
                sameSeller.stream().map(toPublic(sameSeller)).toList());
    }

    // ------------------------------------------------------------------ query building

    /** WHERE clause shared by the search, the facets and the home page. */
    static List<Predicate> filters(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Product> root, SearchCriteria c,
                                   boolean marketplaceOnly) {
        List<Predicate> where = new ArrayList<>();
        if (marketplaceOnly) {
            where.add(cb.isTrue(root.get("active")));
            where.add(cb.isTrue(root.get("listed")));
            where.add(cb.isTrue(root.get("sellerActive")));
            where.add(cb.isFalse(root.get("storeSuspended")));
        }
        for (String word : words(c.query())) {
            String pattern = Like.contains(word);
            Subquery<Long> inCategory = query.subquery(Long.class);
            Root<Category> category = inCategory.from(Category.class);
            inCategory.select(category.get("id")).where(cb.equal(category.get("id"), root.get("category").get("id")),
                    cb.like(cb.lower(category.get("name")), pattern, Like.ESCAPE));
            where.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, Like.ESCAPE),
                    cb.like(cb.lower(cb.coalesce(root.get("brand"), "")), pattern, Like.ESCAPE),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern, Like.ESCAPE),
                    cb.like(cb.lower(root.get("sellerName")), pattern, Like.ESCAPE),
                    cb.like(cb.lower(root.get("sku")), pattern, Like.ESCAPE),
                    cb.exists(inCategory)));
        }
        if (c.categoryId() != null) {
            where.add(cb.equal(root.get("category").get("id"), c.categoryId()));
        }
        if (c.sellerIds() != null && !c.sellerIds().isEmpty()) {
            where.add(root.get("sellerId").in(c.sellerIds()));
        }
        if (c.brands() != null && !c.brands().isEmpty()) {
            where.add(cb.lower(root.get("brand")).in(c.brands().stream().map(b -> b.toLowerCase(Locale.ROOT)).toList()));
        }
        if (c.minPrice() != null) {
            where.add(cb.greaterThanOrEqualTo(root.get("price"), c.minPrice()));
        }
        if (c.maxPrice() != null) {
            where.add(cb.lessThanOrEqualTo(root.get("price"), c.maxPrice()));
        }
        if (c.minRating() != null && c.minRating() > 0) {
            where.add(cb.greaterThanOrEqualTo(root.get("ratingAverage"), BigDecimal.valueOf(c.minRating())));
        }
        if (c.dealsOnly()) {
            where.add(cb.gt(root.get("compareAtPrice"), root.get("price")));
        }
        if (c.inStockOnly()) {
            Subquery<Long> stock = query.subquery(Long.class);
            Root<Inventory> inventory = stock.from(Inventory.class);
            stock.select(inventory.get("productId")).where(cb.equal(inventory.get("productId"), root.get("id")),
                    cb.gt(inventory.get("availableQuantity"), 0));
            where.add(cb.exists(stock));
        }
        return where;
    }

    /** Relevance: the whole phrase at the start of the name, then in the name, then by popularity. */
    private static List<Order> order(CriteriaBuilder cb, Root<Product> root, SearchCriteria c) {
        String sort = c.sort() == null || c.sort().isBlank()
                ? (words(c.query()).isEmpty() ? "featured" : "relevance") : c.sort();
        List<Order> orders = new ArrayList<>();
        switch (sort) {
            case "price_asc" -> orders.add(cb.asc(root.get("price")));
            case "price_desc" -> orders.add(cb.desc(root.get("price")));
            case "rating" -> {
                orders.add(cb.desc(root.get("ratingAverage")));
                orders.add(cb.desc(root.get("ratingCount")));
            }
            case "newest" -> orders.add(cb.desc(root.get("createdAt")));
            case "bestselling" -> orders.add(cb.desc(root.get("unitsSold")));
            case "discount" -> orders.add(cb.desc(cb.diff(cb.coalesce(root.get("compareAtPrice"), root.get("price")),
                    root.get("price"))));
            case "relevance" -> {
                String phrase = c.query() == null ? "" : c.query().trim();
                Expression<Integer> score = cb.<Integer>selectCase()
                        .when(cb.like(cb.lower(root.get("name")), Like.startsWith(phrase), Like.ESCAPE), 3)
                        .when(cb.like(cb.lower(root.get("name")), Like.contains(phrase), Like.ESCAPE), 2)
                        .when(cb.like(cb.lower(cb.coalesce(root.get("brand"), "")), Like.contains(phrase), Like.ESCAPE), 1)
                        .otherwise(0);
                orders.add(cb.desc(score));
                orders.add(cb.desc(root.get("unitsSold")));
            }
            default -> {
                orders.add(cb.desc(root.get("unitsSold")));
                orders.add(cb.desc(root.get("ratingCount")));
                orders.add(cb.desc(root.get("createdAt")));
            }
        }
        orders.add(cb.asc(root.get("id")));
        return orders;
    }

    private List<ProductResponse> section(String name, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> root = query.from(Product.class);
        root.fetch("category", JoinType.LEFT);
        List<Predicate> where = filters(cb, query, root, new SearchCriteria(null, null, null, null, null, null, null,
                false, false, null), true);
        List<Order> orders = new ArrayList<>();
        switch (name) {
            case "deals" -> {
                where.add(cb.gt(root.get("compareAtPrice"), root.get("price")));
                orders.add(cb.desc(cb.quot(cb.diff(root.get("compareAtPrice"), root.get("price")),
                        root.get("compareAtPrice"))));
            }
            case "bestSellers" -> {
                where.add(cb.gt(root.get("unitsSold"), 0L));
                orders.add(cb.desc(root.get("unitsSold")));
            }
            case "topRated" -> {
                where.add(cb.gt(root.get("ratingCount"), 0));
                orders.add(cb.desc(root.get("ratingAverage")));
                orders.add(cb.desc(root.get("ratingCount")));
            }
            default -> orders.add(cb.desc(root.get("createdAt")));
        }
        orders.add(cb.desc(root.get("id")));
        query.select(root).where(where.toArray(Predicate[]::new)).orderBy(orders);
        List<Product> products = entityManager.createQuery(query).setMaxResults(limit).getResultList();
        return products.stream().map(toPublic(products)).toList();
    }

    private Function<Product, ProductResponse> toPublic(List<Product> products) {
        Map<Long, Inventory> stock = products.isEmpty() ? Map.of()
                : inventoryRepository.findByProductIdIn(products.stream().map(Product::getId).toList()).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        return product -> ProductMapper.toPublic(product, stock.get(product.getId()));
    }

    private static Expression<Long> countIf(CriteriaBuilder cb, Predicate condition) {
        return cb.sum(cb.<Long>selectCase().when(condition, 1L).otherwise(0L));
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    static List<String> words(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.trim().split("\\s+")).filter(w -> !w.isBlank()).limit(MAX_WORDS).toList();
    }
}
