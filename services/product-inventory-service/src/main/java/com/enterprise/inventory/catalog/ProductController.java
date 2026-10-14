package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Marketplace catalogue (public): only products that merchants listed for sale. */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Marketplace")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Search products of all merchants (paginated)")
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String search,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) Long merchantId,
                                                @RequestParam(required = false) BigDecimal minPrice,
                                                @RequestParam(required = false) BigDecimal maxPrice,
                                                @RequestParam(defaultValue = "false") boolean inStockOnly,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "12") int size,
                                                @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return productService.search(search, categoryId, merchantId, minPrice, maxPrice, inStockOnly, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Product details (cached in Redis)")
    public ProductResponse get(@PathVariable Long id) {
        return productService.getById(id);
    }
}
