package com.enterprise.inventory.merchant;

import com.enterprise.inventory.catalog.Category;
import com.enterprise.inventory.catalog.CategoryRepository;
import com.enterprise.inventory.catalog.CatalogDtos.CreateProductRequest;
import com.enterprise.inventory.catalog.CatalogDtos.MerchantProductResponse;
import com.enterprise.inventory.catalog.CatalogDtos.UpdateProductRequest;
import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.ProductMapper;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.InventoryRepository;
import com.enterprise.inventory.stock.MovementType;
import com.enterprise.inventory.stock.StockMovement;
import com.enterprise.inventory.stock.StockMovementRepository;
import com.enterprise.inventory.stock.StockPurchase;
import com.enterprise.inventory.stock.StockPurchaseRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A merchant's own product catalogue (listed and unlisted), always scoped to the logged-in merchant. */
@Service
public class MerchantProductService {

    private static final Logger log = LoggerFactory.getLogger(MerchantProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final StockPurchaseRepository purchaseRepository;
    private final ProductCache productCache;

    public MerchantProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                                  InventoryRepository inventoryRepository, StockMovementRepository movementRepository,
                                  StockPurchaseRepository purchaseRepository, ProductCache productCache) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.purchaseRepository = purchaseRepository;
        this.productCache = productCache;
    }

    @Transactional(readOnly = true)
    public List<MerchantProductResponse> list(AuthenticatedUser merchant, String search, Boolean listed) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Product> products = productRepository.findByMerchantIdAndActiveTrueOrderByName(merchant.id()).stream()
                .filter(p -> listed == null || p.isListed() == listed)
                .filter(p -> term.isEmpty() || p.getName().toLowerCase(Locale.ROOT).contains(term)
                        || p.getSku().toLowerCase(Locale.ROOT).contains(term))
                .toList();
        Map<Long, Inventory> stock = inventoryRepository.findAllById(products.stream().map(Product::getId).toList())
                .stream().collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        return products.stream().map(p -> ProductMapper.toMerchant(p, stock.get(p.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public MerchantProductResponse get(Long id, AuthenticatedUser merchant) {
        Product product = owned(id, merchant);
        return ProductMapper.toMerchant(product, inventoryRepository.findById(id).orElse(null));
    }

    @Transactional
    public MerchantProductResponse create(CreateProductRequest request, AuthenticatedUser merchant) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsByMerchantIdAndSkuIgnoreCase(merchant.id(), sku)) {
            throw ApiException.conflict("You already have a product with SKU " + sku);
        }
        boolean listed = request.listed() == null || request.listed();
        Product product = productRepository.save(new Product(merchant.id(), merchant.displayName(), sku,
                request.name().trim(), blankToNull(request.description()), request.price(), request.costPrice(),
                findCategory(request.categoryId()), blankToNull(request.imageUrl()), listed));
        int reorderLevel = request.reorderLevel() == null ? 5 : request.reorderLevel();
        Inventory inventory = inventoryRepository.save(new Inventory(product, request.initialStock(), reorderLevel));
        if (request.initialStock() > 0) {
            purchaseRepository.save(new StockPurchase(merchant.id(), product.getId(), sku, product.getName(),
                    request.initialStock(), request.costPrice(), blankToNull(request.supplier()), "OPENING-STOCK"));
        }
        movementRepository.save(new StockMovement(merchant.id(), product.getId(), MovementType.INITIAL,
                request.initialStock(), null, "Opening stock", merchant.username()));
        productCache.evictCategories();
        log.info("Merchant {} created product {} ({}), listed={}", merchant.username(), product.getId(), sku, listed);
        return ProductMapper.toMerchant(product, inventory);
    }

    @Transactional
    public MerchantProductResponse update(Long id, UpdateProductRequest request, AuthenticatedUser merchant) {
        Product product = owned(id, merchant);
        product.update(request.name().trim(), blankToNull(request.description()), request.price(), request.costPrice(),
                findCategory(request.categoryId()), blankToNull(request.imageUrl()), request.listed());
        productRepository.flush();
        productCache.evictProduct(id);
        productCache.evictCategories();
        return ProductMapper.toMerchant(product, inventoryRepository.findById(id).orElse(null));
    }

    /** Show or hide a product on the marketplace without touching its stock. */
    @Transactional
    public MerchantProductResponse setListing(Long id, boolean listed, AuthenticatedUser merchant) {
        Product product = owned(id, merchant);
        product.setListed(listed);
        productRepository.flush();
        productCache.evictProduct(id);
        productCache.evictCategories();
        log.info("Product {} is now {} by {}", id, listed ? "listed" : "unlisted", merchant.username());
        return ProductMapper.toMerchant(product, inventoryRepository.findById(id).orElse(null));
    }

    /** Soft delete: past orders still reference the product, so it is archived instead of removed. */
    @Transactional
    public void archive(Long id, AuthenticatedUser merchant) {
        Product product = owned(id, merchant);
        product.archive();
        productCache.evictProduct(id);
        productCache.evictCategories();
    }

    private Product owned(Long id, AuthenticatedUser merchant) {
        return productRepository.findByIdAndMerchantId(id, merchant.id())
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
