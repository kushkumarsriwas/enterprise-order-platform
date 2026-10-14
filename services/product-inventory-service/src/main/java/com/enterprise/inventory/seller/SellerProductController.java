package com.enterprise.inventory.merchant;

import com.enterprise.inventory.catalog.CatalogDtos.CreateProductRequest;
import com.enterprise.inventory.catalog.CatalogDtos.ListingRequest;
import com.enterprise.inventory.catalog.CatalogDtos.MerchantProductResponse;
import com.enterprise.inventory.catalog.CatalogDtos.UpdateProductRequest;
import com.enterprise.inventory.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/products")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant - products")
public class MerchantProductController {

    private final MerchantProductService service;

    public MerchantProductController(MerchantProductService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "My products with cost, margin and stock (listed and unlisted)")
    public List<MerchantProductResponse> list(@RequestParam(required = false) String search,
                                              @RequestParam(required = false) Boolean listed,
                                              @AuthenticationPrincipal AuthenticatedUser merchant) {
        return service.list(merchant, search, listed);
    }

    @GetMapping("/{id}")
    public MerchantProductResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser merchant) {
        return service.get(id, merchant);
    }

    @PostMapping
    @Operation(summary = "Add a product to my inventory (optionally listed on the marketplace)")
    public ResponseEntity<MerchantProductResponse> create(@Valid @RequestBody CreateProductRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser merchant) {
        MerchantProductResponse created = service.create(request, merchant);
        return ResponseEntity.created(URI.create("/api/merchant/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public MerchantProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser merchant) {
        return service.update(id, request, merchant);
    }

    @PatchMapping("/{id}/listing")
    @Operation(summary = "List or unlist a product on the marketplace")
    public MerchantProductResponse listing(@PathVariable Long id, @Valid @RequestBody ListingRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser merchant) {
        return service.setListing(id, request.listed(), merchant);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive a product (kept for order history)")
    public ResponseEntity<Void> archive(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser merchant) {
        service.archive(id, merchant);
        return ResponseEntity.noContent().build();
    }
}
