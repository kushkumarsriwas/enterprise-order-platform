package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.catalog.StorefrontDtos.HomeSections;
import com.enterprise.inventory.catalog.StorefrontDtos.RelatedProducts;
import com.enterprise.inventory.catalog.StorefrontDtos.SearchCriteria;
import com.enterprise.inventory.catalog.StorefrontDtos.SearchFacets;
import com.enterprise.inventory.catalog.StorefrontDtos.Suggestions;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The marketplace catalogue (public): only products that sellers listed for sale. */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Marketplace")
public class ProductController {

    private final ProductService productService;
    private final ProductImageRepository images;

    public ProductController(ProductService productService, ProductImageRepository images) {
        this.productService = productService;
        this.images = images;
    }

    @GetMapping
    @Operation(summary = "Search the marketplace: words, department, sellers, brands, price, rating, in stock, deals, sort")
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String search,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) List<Long> sellerId,
                                                @RequestParam(required = false) List<String> brand,
                                                @RequestParam(required = false) BigDecimal minPrice,
                                                @RequestParam(required = false) BigDecimal maxPrice,
                                                @RequestParam(required = false) Integer minRating,
                                                @RequestParam(defaultValue = "false") boolean inStock,
                                                @RequestParam(defaultValue = "false") boolean deals,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "24") int size) {
        return productService.search(criteria(q != null ? q : search, categoryId, sellerId, brand, minPrice, maxPrice,
                minRating, inStock, deals, sort), page, size);
    }

    @GetMapping("/facets")
    @Operation(summary = "Filter panel for a search: departments, brands, sellers, price range, ratings (with counts)")
    public SearchFacets facets(@RequestParam(required = false) String q,
                               @RequestParam(required = false) Long categoryId,
                               @RequestParam(required = false) List<Long> sellerId,
                               @RequestParam(required = false) List<String> brand,
                               @RequestParam(required = false) BigDecimal minPrice,
                               @RequestParam(required = false) BigDecimal maxPrice,
                               @RequestParam(required = false) Integer minRating,
                               @RequestParam(defaultValue = "false") boolean inStock,
                               @RequestParam(defaultValue = "false") boolean deals) {
        return productService.facets(criteria(q, categoryId, sellerId, brand, minPrice, maxPrice, minRating, inStock,
                deals, null));
    }

    @GetMapping("/home")
    @Operation(summary = "Home page sections: deals, best sellers, new arrivals, top rated, departments (cached)")
    public HomeSections home() {
        return productService.home();
    }

    @GetMapping("/suggest")
    @Operation(summary = "Search-box suggestions (product names and departments)")
    public Suggestions suggest(@RequestParam String q) {
        return productService.suggest(q);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Product details (cached in Redis)")
    public ProductResponse get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping("/{id}/related")
    @Operation(summary = "Similar products and more from the same seller")
    public RelatedProducts related(@PathVariable Long id) {
        return productService.related(id);
    }

    /** Product photo with HTTP caching: browsers re-validate with ETag and get 304 when unchanged. */
    @GetMapping("/{id}/image")
    @Transactional(readOnly = true)
    @Operation(summary = "Product photo uploaded by the seller")
    public ResponseEntity<byte[]> image(@PathVariable Long id,
                                        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        ProductImage image = images.findById(id).orElseThrow(() -> ApiException.notFound("No image for product " + id));
        String etag = "\"" + id + "-" + image.getUpdatedAt().toEpochMilli() + "\"";
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(7)).cachePublic();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getContentType())).eTag(etag)
                .cacheControl(cache).body(image.getData());
    }

    private static SearchCriteria criteria(String q, Long categoryId, List<Long> sellerIds, List<String> brands,
                                           BigDecimal minPrice, BigDecimal maxPrice, Integer minRating, boolean inStock,
                                           boolean deals, String sort) {
        return new SearchCriteria(q, categoryId, sellerIds, brands, minPrice, maxPrice, minRating, inStock, deals, sort);
    }
}
