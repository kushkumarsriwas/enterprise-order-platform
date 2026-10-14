package com.enterprise.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    private static final String MERCHANT = "Bearer " + TestTokens.merchant(100);
    private static final String OTHER_MERCHANT = "Bearer " + TestTokens.merchant(200);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventoryRepository inventoryRepository;

    private long createProduct(String token, String sku, int stock, boolean listed) throws Exception {
        String body = mockMvc.perform(post("/api/merchant/products").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Item " + sku + "\",\"price\":99.50,\"costPrice\":60,"
                                + "\"initialStock\":" + stock + ",\"reorderLevel\":1,\"listed\":" + listed + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void onlyListedProductsReachTheMarketplace() throws Exception {
        long listed = createProduct(MERCHANT, "PUB-1", 5, true);
        long hidden = createProduct(MERCHANT, "PUB-2", 5, false);

        mockMvc.perform(get("/api/products/" + listed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantName").value("Store 100"))
                .andExpect(jsonPath("$.costPrice").doesNotExist());
        mockMvc.perform(get("/api/products/" + hidden)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products").param("search", "pub-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/api/merchant/products/" + hidden + "/listing").header("Authorization", MERCHANT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listed").value(true))
                .andExpect(jsonPath("$.marginPercent").value(39.7));
        mockMvc.perform(get("/api/products").param("search", "pub-"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void suspendedStoresDisappearFromTheMarketplace() throws Exception {
        String store = "Bearer " + TestTokens.merchant(300);
        long product = createProduct(store, "SUS-1", 5, true);
        mockMvc.perform(get("/api/products").param("merchantId", "300"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(put("/internal/merchants/300/status").header("Authorization", store)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/internal/merchants/300/status").header("Authorization", "Bearer " + TestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").value(1));
        mockMvc.perform(get("/api/products").param("merchantId", "300"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/products/" + product)).andExpect(status().isNotFound());
        assertThatThrownBy(() -> inventoryService.reserve(new ReserveRequest("ORD-SUS-1", List.of(new ReserveItem(product, 1)))))
                .hasMessageContaining("not available");

        mockMvc.perform(put("/internal/merchants/300/status").header("Authorization", "Bearer " + TestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products/" + product)).andExpect(status().isOk());
    }

    @Test
    void merchantsOnlySeeAndChangeTheirOwnInventory() throws Exception {
        long id = createProduct(MERCHANT, "OWN-1", 3, true);

        mockMvc.perform(get("/api/merchant/products/" + id).header("Authorization", OTHER_MERCHANT))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/merchant/inventory/" + id).header("Authorization", OTHER_MERCHANT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"availableQuantity\":999}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/merchant/products").header("Authorization", "Bearer " + TestTokens.customer(5))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"X-1\",\"name\":\"X\",\"price\":1,\"costPrice\":1,\"initialStock\":1}"))
                .andExpect(status().isForbidden());
        // the same SKU is allowed for a different merchant
        createProduct(OTHER_MERCHANT, "OWN-1", 1, true);
        mockMvc.perform(post("/api/merchant/products").header("Authorization", MERCHANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"own-1\",\"name\":\"Again\",\"price\":5,\"costPrice\":1,\"initialStock\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void restockUpdatesWeightedAverageCostAndPurchases() throws Exception {
        long id = createProduct(MERCHANT, "WAC-1", 10, false);   // 10 units at 60
        mockMvc.perform(post("/api/merchant/inventory/" + id + "/restock").header("Authorization", MERCHANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":30,\"unitCost\":40,\"supplier\":\"Dragon Mart Trading\",\"reference\":\"PO-77\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(40))
                .andExpect(jsonPath("$.unitCost").value(45.0));   // (10*60 + 30*40) / 40
        mockMvc.perform(get("/api/merchant/purchases").header("Authorization", MERCHANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reference == 'PO-77')].totalCost").value(1200.0));
        mockMvc.perform(get("/api/merchant/inventory/summary").header("Authorization", MERCHANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isNumber());
    }

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        long id = createProduct(MERCHANT, "RACE-1", 5, true);
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
        long id = createProduct(MERCHANT, "FLOW-1", 4, true);
        String customer = "Bearer " + TestTokens.customer(9);

        mockMvc.perform(post("/internal/products/lookup").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productIds\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].merchantId").value(100));
        mockMvc.perform(post("/internal/inventory/reservations").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderRef\":\"ORD-FLOW\",\"items\":[{\"productId\":" + id + ",\"quantity\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPrice").value(99.5))
                .andExpect(jsonPath("$.lines[0].unitCost").value(60.0));
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
        mockMvc.perform(get("/api/merchant/inventory/movements").param("productId", String.valueOf(id))
                        .header("Authorization", MERCHANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("RETURNED"));
    }

    @Test
    void inventoryReportDownloadsAsExcelAndPdf() throws Exception {
        createProduct(MERCHANT, "REP-1", 7, true);
        byte[] xlsx = mockMvc.perform(get("/api/merchant/reports/inventory/xlsx").header("Authorization", MERCHANT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
        byte[] pdf = mockMvc.perform(get("/api/merchant/reports/inventory/pdf").header("Authorization", MERCHANT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        java.nio.file.Files.write(java.nio.file.Path.of("target", "inventory-report-sample.pdf"), pdf);
        java.nio.file.Files.write(java.nio.file.Path.of("target", "inventory-report-sample.xlsx"), xlsx);
    }

    @Test
    void categoriesShowListedProductCounts() throws Exception {
        String body = mockMvc.perform(post("/api/categories").header("Authorization", MERCHANT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Gadgets\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int categoryId = JsonPath.read(body, "$.id");
        mockMvc.perform(post("/api/merchant/products").header("Authorization", MERCHANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"GAD-1\",\"name\":\"Gadget\",\"price\":10,\"costPrice\":4,\"initialStock\":3,"
                                + "\"categoryId\":" + categoryId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Gadgets')].productCount").value(1));
    }
}
