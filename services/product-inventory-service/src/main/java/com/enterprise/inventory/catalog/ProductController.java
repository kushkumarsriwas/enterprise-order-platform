package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.CreateProductRequest;
import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.catalog.CatalogDtos.UpdateProductRequest;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Search the catalogue (public, paginated)")
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String search,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(defaultValue = "false") boolean inStockOnly,
                                                @RequestParam(defaultValue = "false") boolean includeInactive,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "12") int size,
                                                @RequestParam(defaultValue = "name,asc") String sort,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        boolean showInactive = includeInactive && user != null && user.isAdmin();
        return productService.search(search, categoryId, inStockOnly, showInactive, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Product details (public, cached in Redis)")
    public ProductResponse get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a product with its initial stock (admin)")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        ProductResponse created = productService.create(request, user.username());
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update product details (admin)")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return productService.update(id, request, user.username());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate (soft delete) a product (admin)")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        productService.deactivate(id, user.username());
        return ResponseEntity.noContent().build();
    }
}
