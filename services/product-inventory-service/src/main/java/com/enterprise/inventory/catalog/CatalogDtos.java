package com.enterprise.inventory.catalog;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "letters, digits and '-' only")
            String sku,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            Long categoryId,
            @Size(max = 500) String imageUrl,
            @NotNull @Min(0) @Max(1000000) Integer initialStock,
            @Min(0) @Max(100000) Integer reorderLevel,
            @Size(max = 100) String supplier,
            Boolean listed) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            Long categoryId,
            @Size(max = 500) String imageUrl,
            @NotNull Boolean listed) {
    }

    public record ListingRequest(@NotNull Boolean listed) {
    }

    /** What customers see. Never contains the merchant's cost price. */
    public record ProductResponse(
            Long id,
            Long merchantId,
            String merchantName,
            String sku,
            String name,
            String description,
            BigDecimal price,
            String currency,
            Long categoryId,
            String categoryName,
            String imageUrl,
            int availableQuantity,
            boolean inStock,
            boolean lowStock,
            Instant createdAt) {
    }

    /** What the owning merchant sees: costs, margins, stock value and listing state. */
    public record MerchantProductResponse(
            Long id,
            String sku,
            String name,
            String description,
            BigDecimal price,
            BigDecimal costPrice,
            BigDecimal marginPercent,
            String currency,
            Long categoryId,
            String categoryName,
            String imageUrl,
            boolean listed,
            boolean active,
            int availableQuantity,
            int reservedQuantity,
            int reorderLevel,
            boolean lowStock,
            BigDecimal stockValueAtCost,
            BigDecimal stockValueAtPrice,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateCategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 255) String description) {
    }

    public record CategoryResponse(Long id, String name, String description, Long productCount) {
    }

    /** Wrapper so the category list can be cached in Redis with a concrete JSON type. */
    public record CategoryList(List<CategoryResponse> items) {
    }
}
