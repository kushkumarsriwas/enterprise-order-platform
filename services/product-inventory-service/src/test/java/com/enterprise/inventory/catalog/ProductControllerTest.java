package com.enterprise.inventory.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.config.SecurityConfig;
import com.enterprise.inventory.security.InternalApiKey;
import com.enterprise.inventory.security.RestAuthenticationHandlers;
import com.enterprise.inventory.support.TestTokens;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only (MockMvc): request mapping, query parameters, JSON shape, error handling, public access. */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, RestAuthenticationHandlers.class, InternalApiKey.class, TestTokens.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private ProductImageRepository images;

    private static ProductResponse phone() {
        return new ProductResponse(1L, 10L, "Gulf Gadgets", "PH-1", "Galaxy Phone", "Samsung", "Fast phone", "pc",
                new BigDecimal("2999.00"), new BigDecimal("3499.00"), 14, "AED", 3L, "Electronics", null, 4, true, true,
                new BigDecimal("4.50"), 12, 30, Instant.parse("2026-10-01T10:00:00Z"));
    }

    @Test
    void searchIsPublicAndPassesEveryFilter() throws Exception {
        when(productService.search(argThat(c -> "phone".equals(c.query()) && c.categoryId() == 3L
                        && c.sellerIds().equals(List.of(10L, 11L)) && c.brands().equals(List.of("Samsung"))
                        && c.minRating() == 4 && c.inStockOnly() && "price_asc".equals(c.sort())), eq(0), eq(24)))
                .thenReturn(new PageResponse<>(List.of(phone()), 0, 24, 1, 1, true));

        mockMvc.perform(get("/api/products").param("q", "phone").param("categoryId", "3")
                        .param("sellerId", "10", "11").param("brand", "Samsung").param("minRating", "4")
                        .param("inStock", "true").param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Galaxy Phone"))
                .andExpect(jsonPath("$.content[0].discountPercent").value(14))
                .andExpect(jsonPath("$.content[0].costPrice").doesNotExist());
    }

    @Test
    void unknownProductIsA404WithTheStandardErrorBody() throws Exception {
        when(productService.getById(99L)).thenThrow(ApiException.notFound("Product 99 not found"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product 99 not found"));
    }

    @Test
    void invalidParameterIsA400() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrice", "cheap"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sellerEndpointsNeedASellerToken() throws Exception {
        when(productService.search(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 24, 0, 0, true));
        mockMvc.perform(get("/api/seller/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seller/products").header("Authorization", "Bearer " + TestTokens.customer(5)))
                .andExpect(status().isForbidden());
    }
}
