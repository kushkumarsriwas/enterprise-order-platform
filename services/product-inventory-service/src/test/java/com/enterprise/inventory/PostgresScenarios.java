package com.enterprise.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.inventory.stock.InventoryService;
import com.enterprise.inventory.stock.StockDtos.ReserveItem;
import com.enterprise.inventory.stock.StockDtos.ReserveRequest;
import com.enterprise.inventory.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Scenarios that must be proven on real PostgreSQL rather than H2: row locking under concurrency, the
 * generated SQL of the Criteria searches and analytics queries, and gap-free document numbers.
 * Subclasses decide where PostgreSQL comes from (Testcontainers in CI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
abstract class PostgresScenarios {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected InventoryService inventoryService;

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String token, String body) throws Exception {
        request.header("Authorization", token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private long category() throws Exception {
        String json = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
    }

    private long product(String token, String sku, String name, int stock) throws Exception {
        String body = json(post("/api/seller/products"), token, "{\"sku\":\"" + sku + "\",\"name\":\"" + name
                + "\",\"brand\":\"Acme\",\"price\":120,\"compareAtPrice\":150,\"costPrice\":70,\"categoryId\":"
                + category() + ",\"initialStock\":" + stock + ",\"reorderPoint\":3,\"listed\":true}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void rowLocksPreventOversellingUnderConcurrency() throws Exception {
        long product = product("Bearer " + TestTokens.seller(901), "PG-RACE", "Race item", 10);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Boolean>> buyers = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String ref = "PG-RACE-" + i;
            buyers.add(() -> {
                try {
                    inventoryService.reserve(new ReserveRequest(ref, List.of(new ReserveItem(product, 1))));
                    inventoryService.commit(ref);
                    return true;
                } catch (RuntimeException ex) {
                    return false;
                }
            });
        }
        long sold = 0;
        for (Future<Boolean> result : pool.invokeAll(buyers)) {
            sold += result.get() ? 1 : 0;
        }
        pool.shutdown();
        assertThat(sold).isEqualTo(10);
        json(get("/api/inventory/" + product), "Bearer " + TestTokens.seller(901), null)
                .andExpect(jsonPath("$.availableQuantity").value(0))
                .andExpect(jsonPath("$.onHandQuantity").value(0));
        json(get("/api/seller/inventory/integrity"), "Bearer " + TestTokens.seller(901), null)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void purchasingNumbersAndWeightedAverageCostOnPostgres() throws Exception {
        String seller = "Bearer " + TestTokens.seller(902);
        long product = product(seller, "PG-PO", "Purchased item", 10);
        String supplier = json(post("/api/seller/suppliers"), seller, "{\"name\":\"PG Supplier\",\"leadTimeDays\":3}")
                .andReturn().getResponse().getContentAsString();
        String po = json(post("/api/seller/purchase-orders"), seller, "{\"supplierId\":" + JsonPath.read(supplier, "$.id")
                + ",\"lines\":[{\"productId\":" + product + ",\"quantity\":10,\"unitCost\":100}]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.number").value("PO-00001"))
                .andReturn().getResponse().getContentAsString();
        long poId = ((Number) JsonPath.read(po, "$.id")).longValue();
        json(post("/api/seller/purchase-orders/" + poId + "/submit"), seller, null).andExpect(status().isOk());
        json(post("/api/seller/purchase-orders/" + poId + "/receive"), seller, "{\"lines\":[{\"lineId\":"
                + JsonPath.read(po, "$.lines[0].id") + ",\"quantity\":10}]}")
                .andExpect(jsonPath("$.number").value("GRN-00001"));
        json(get("/api/seller/products/" + product), seller, null)
                .andExpect(jsonPath("$.product.costPrice").value(85.0));
        json(get("/api/seller/inventory/analytics"), seller, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseSpend").value(1000.0))
                .andExpect(jsonPath("$.flow").isArray());
    }

    @Test
    void marketplaceSearchAndFacetsRunOnPostgres() throws Exception {
        product("Bearer " + TestTokens.seller(903), "PG-S1", "Postgres coffee mug", 4);
        product("Bearer " + TestTokens.seller(903), "PG-S2", "Postgres 100% cotton tee", 0);
        mockMvc.perform(get("/api/products").param("q", "postgres mug").param("sort", "relevance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("PG-S1"));
        mockMvc.perform(get("/api/products").param("q", "100%"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/products/facets").param("q", "postgres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.inStock").value(1))
                .andExpect(jsonPath("$.deals").value(2));
        mockMvc.perform(get("/api/products/home")).andExpect(status().isOk());
    }
}
