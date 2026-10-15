package com.enterprise.inventory.seller;

import com.enterprise.inventory.catalog.CatalogDtos.CreateProductRequest;
import com.enterprise.inventory.catalog.CatalogDtos.SellerProductResponse;
import com.enterprise.inventory.catalog.CatalogDtos.UpdateProductRequest;
import com.enterprise.inventory.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The write half of the /api/products REST resource (the read half, GET, is the public catalogue in
 * {@code ProductController}). A seller creates, updates and archives their own products; another seller's product
 * answers 404, exactly like Seller Central's /api/seller/products, which these endpoints share their logic with.
 */
@RestController
@RequestMapping("/api/products")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Products (REST resource)")
public class ProductResourceController {

    private final SellerProductService service;

    public ProductResourceController(SellerProductService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a product in my store: 201 with its Location (409 when the SKU is already used)")
    public ResponseEntity<SellerProductResponse> create(@Valid @RequestBody CreateProductRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        SellerProductResponse created = service.create(seller.id(), seller.displayName(), seller.username(), request);
        // a product that is not for sale yet has no public page, so the Location points at the seller's view of it
        String location = (created.visibleToCustomers() ? "/api/products/" : "/api/seller/products/") + created.id();
        return ResponseEntity.created(URI.create(location)).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update one of my products: name, prices, category, reorder settings")
    public SellerProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.update(seller.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one of my products: it is archived (204), so past orders and the stock ledger keep it")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        service.archive(seller.id(), id);
        return ResponseEntity.noContent().build();
    }
}
