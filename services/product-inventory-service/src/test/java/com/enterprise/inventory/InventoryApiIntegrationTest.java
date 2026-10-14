package com.enterprise.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.stock.InventoryRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
class InventoryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventoryRepository inventoryRepository;

    private long createProduct(String sku, int stock) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + TestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Item " + sku + "\",\"price\":99.50,"
                                + "\"initialStock\":" + stock + ",\"reorderLevel\":1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void catalogueIsPublicButWritesNeedAdmin() throws Exception {
        long id = createProduct("PUB-1", 5);

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(5))
                .andExpect(jsonPath("$.currency").value("AED"));
        mockMvc.perform(get("/api/products").param("search", "pub-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("PUB-1"));
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + TestTokens.customer(5))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"X-1\",\"name\":\"X\",\"price\":1,\"initialStock\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateSkuIsRejected() throws Exception {
        createProduct("DUP-1", 1);
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + TestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"dup-1\",\"name\":\"Again\",\"price\":5,\"initialStock\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        long id = createProduct("RACE-1", 5);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Boolean>> buyers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String orderRef = "ORD-RACE-" + i;
            buyers.add(() -> {
                try {
                    inventoryService.reserve(new ReserveRequest(orderRef, List.of(new ReserveItem(id, 1))));
                    return true;
                } catch (ApiException ex) {
                    return false;
                }
            });
        }
        int succeeded = 0;
        for (Future<Boolean> result : pool.invokeAll(buyers)) {
            if (result.get()) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(5);
        var inventory = inventoryRepository.findById(id).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThat(inventory.getReservedQuantity()).isEqualTo(5);
    }

    @Test
    void reserveCommitAndReleaseThroughInternalApi() throws Exception {
        long id = createProduct("FLOW-1", 4);
        String customer = "Bearer " + TestTokens.customer(9);

        mockMvc.perform(post("/internal/inventory/reservations").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderRef\":\"ORD-FLOW\",\"items\":[{\"productId\":" + id + ",\"quantity\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPrice").value(99.5));
        mockMvc.perform(post("/internal/inventory/reservations").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderRef\":\"ORD-FLOW-2\",\"items\":[{\"productId\":" + id + ",\"quantity\":2}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for 'Item FLOW-1': requested 2, available 1"));
        mockMvc.perform(post("/internal/inventory/reservations/ORD-FLOW/commit").header("Authorization", customer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/inventory/reservations/ORD-FLOW/release").header("Authorization", customer))
                .andExpect(status().isOk());

        var inventory = inventoryRepository.findById(id).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(4);
        assertThat(inventory.getReservedQuantity()).isZero();
        mockMvc.perform(get("/api/inventory/" + id + "/movements").header("Authorization", "Bearer " + TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("RETURNED"));
    }

    @Test
    void categoriesShowActiveProductCounts() throws Exception {
        String body = mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + TestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Gadgets\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int categoryId = JsonPath.read(body, "$.id");
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + TestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"GAD-1\",\"name\":\"Gadget\",\"price\":10,\"initialStock\":3,\"categoryId\":"
                                + categoryId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Gadgets')].productCount").value(1));
    }
}
