package com.enterprise.inventory.seller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.inventory.catalog.CatalogDtos.SellerProductResponse;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.config.SecurityConfig;
import com.enterprise.inventory.security.InternalApiKey;
import com.enterprise.inventory.security.RestAuthenticationHandlers;
import com.enterprise.inventory.support.TestTokens;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** POST, PUT and DELETE /api/products: only a seller may write, always on their own products. */
@WebMvcTest(ProductResourceController.class)
@Import({SecurityConfig.class, RestAuthenticationHandlers.class, InternalApiKey.class, TestTokens.class})
class ProductResourceControllerTest {

    private static final String NEW_PRODUCT = """
            {"sku": "mug-01", "name": "Stoneware mug", "brand": "Kiln", "price": 39.00, "costPrice": 14.50,
             "listed": true, "initialStock": 40}""";
    /** PUT replaces the editable fields, so the required ones are always sent. */
    private static final String CHANGED_PRODUCT = """
            {"name": "Stoneware mug, 350 ml", "price": 42.00, "costPrice": 14.50, "reorderPoint": 10,
             "reorderQuantity": 20, "listed": true}""";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SellerProductService service;

    private static SellerProductResponse mug(boolean forSale) {
        return new SellerProductResponse(42L, 7L, "Store 7", "MUG-01", null, "Stoneware mug", "Kiln", null, "pc", null,
                null, new BigDecimal("39.00"), null, new BigDecimal("14.50"), new BigDecimal("62.82"), 0, "AED", null,
                forSale, true, forSale, false, 40, 0, 40, 0, 10, 20, "IN_STOCK", new BigDecimal("580.00"),
                new BigDecimal("1560.00"), null, null, null, 0, 0, Instant.parse("2026-10-01T10:00:00Z"),
                Instant.parse("2026-10-01T10:00:00Z"));
    }

    @Test
    void writingNeedsASellerToken() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + TestTokens.customer(5))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/products/42").header("Authorization", "Bearer " + TestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGED_PRODUCT))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/products/42")).andExpect(status().isUnauthorized());
        verify(service, never()).create(any(), any(), any(), any());
    }

    @Test
    void sellerCreatesAProductAndTheLocationPointsAtItsPublicPage() throws Exception {
        when(service.create(eq(7L), eq("Store 7"), eq("seller7"), argThat(r -> "mug-01".equals(r.sku()))))
                .thenReturn(mug(true));

        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + TestTokens.seller(7))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/42"))
                .andExpect(jsonPath("$.sku").value("MUG-01"))
                .andExpect(jsonPath("$.availableQuantity").value(40));
    }

    @Test
    void productThatIsNotForSalePointsAtTheSellersView() throws Exception {
        when(service.create(eq(7L), any(), any(), any())).thenReturn(mug(false));

        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + TestTokens.seller(7))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/seller/products/42"));
    }

    @Test
    void invalidProductIsA400NamingEachField() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + TestTokens.seller(7))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"sku": "no spaces!", "name": "", "price": 0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sku").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists())
                .andExpect(jsonPath("$.fieldErrors.costPrice").exists());
        verify(service, never()).create(any(), any(), any(), any());
    }

    @Test
    void duplicateSkuIsAConflict() throws Exception {
        when(service.create(eq(7L), any(), any(), any()))
                .thenThrow(ApiException.conflict("You already have a product with SKU MUG-01"));

        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + TestTokens.seller(7))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have a product with SKU MUG-01"));
    }

    @Test
    void updateAndDeleteActOnTheCallersOwnProduct() throws Exception {
        when(service.update(eq(7L), eq(42L), argThat(r -> r.price().compareTo(new BigDecimal("42.00")) == 0)))
                .thenReturn(mug(true));

        mockMvc.perform(put("/api/products/42").header("Authorization", "Bearer " + TestTokens.seller(7))
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGED_PRODUCT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
        mockMvc.perform(delete("/api/products/42").header("Authorization", "Bearer " + TestTokens.seller(7)))
                .andExpect(status().isNoContent());
        verify(service).archive(7L, 42L);
    }

    @Test
    void anotherSellersProductIsNotFound() throws Exception {
        when(service.update(eq(8L), eq(42L), any())).thenThrow(ApiException.notFound("Product 42 not found"));

        mockMvc.perform(put("/api/products/42").header("Authorization", "Bearer " + TestTokens.seller(8))
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGED_PRODUCT))
                .andExpect(status().isNotFound());
    }
}
