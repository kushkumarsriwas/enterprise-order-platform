package com.enterprise.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.inventory.stock.InventoryService;
import com.enterprise.inventory.stock.StockDtos.ReserveItem;
import com.enterprise.inventory.stock.StockDtos.ReserveRequest;
import com.enterprise.inventory.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Full Spring context + H2 + MockMvc: the seller's inventory management system end to end
 * (products, warehouses, transfers, adjustments, suppliers, purchase orders, counts, analytics, reports)
 * and how it feeds the marketplace and the order saga.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
class InventoryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryService inventoryService;

    // ------------------------------------------------------------------ helpers

    private static String seller(long id) {
        return "Bearer " + TestTokens.seller(id);
    }

    private ResultActions call(AbstractMockHttpServletRequestBuilder<?> request, String token, String json) throws Exception {
        if (token != null) {
            request.header("Authorization", token);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private long id(ResultActions actions) throws Exception {
        return ((Number) JsonPath.read(body(actions), "$.id")).longValue();
    }

    private long categoryId(String name) throws Exception {
        String json = body(call(get("/api/categories"), null, null).andExpect(status().isOk()));
        List<Integer> ids = JsonPath.read(json, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private long createProduct(String token, String sku, int stock, boolean listed) throws Exception {
        return createProduct(token, sku, "Item " + sku, stock, listed, null);
    }

    private long createProduct(String token, String sku, String name, int stock, boolean listed, Long warehouseId)
            throws Exception {
        String json = "{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\",\"brand\":\"Nexo\",\"price\":99.50,"
                + "\"costPrice\":60,\"categoryId\":" + categoryId("Electronics") + ",\"initialStock\":" + stock
                + ",\"reorderPoint\":2,\"reorderQuantity\":10,\"listed\":" + listed
                + (warehouseId == null ? "" : ",\"warehouseId\":" + warehouseId) + "}";
        return id(call(post("/api/seller/products"), token, json).andExpect(status().isCreated()));
    }

    private long createWarehouse(String token, String code) throws Exception {
        return id(call(post("/api/seller/warehouses"), token,
                "{\"code\":\"" + code + "\",\"name\":\"" + code + " store\",\"city\":\"Dubai\"}")
                .andExpect(status().isCreated()));
    }

    private long createSupplier(String token, String name, int leadTime) throws Exception {
        return id(call(post("/api/seller/suppliers"), token, "{\"name\":\"" + name + "\",\"email\":\"sales@"
                + name.toLowerCase().replace(" ", "") + ".ae\",\"leadTimeDays\":" + leadTime + "}")
                .andExpect(status().isCreated()));
    }

    private void assertLedgerBalanced(String token) throws Exception {
        call(get("/api/seller/inventory/integrity"), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------ marketplace visibility

    @Test
    void onlyListedProductsReachTheMarketplace() throws Exception {
        String s = seller(100);
        long listed = createProduct(s, "PUB-1", 5, true);
        long inventoryOnly = createProduct(s, "PUB-2", 5, false);

        call(get("/api/products/" + listed), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerName").value("Store 100"))
                .andExpect(jsonPath("$.costPrice").doesNotExist());
        call(get("/api/products/" + inventoryOnly), null, null).andExpect(status().isNotFound());
        call(get("/api/products").param("q", "pub"), null, null)
                .andExpect(jsonPath("$.totalElements").value(1));

        call(patch("/api/seller/products/" + inventoryOnly + "/listing"), s, "{\"listed\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listed").value(true))
                .andExpect(jsonPath("$.marginPercent").value(39.7));
        call(get("/api/products").param("q", "pub"), null, null).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listingNeedsACategoryAndAValidWasPrice() throws Exception {
        String s = seller(101);
        call(post("/api/seller/products"), s, "{\"sku\":\"NOCAT\",\"name\":\"No category\",\"price\":10,\"costPrice\":5,"
                + "\"listed\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a category before listing the product on the marketplace"));
        call(post("/api/seller/products"), s, "{\"sku\":\"WAS\",\"name\":\"Bad was price\",\"price\":10,\"costPrice\":5,"
                + "\"compareAtPrice\":8}")
                .andExpect(status().isBadRequest());
        call(post("/api/seller/products"), s, "{\"sku\":\"bad sku!\",\"name\":\"x\",\"price\":10,\"costPrice\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sku").exists());
    }

    @Test
    void vacationModeAndSuspensionHideEveryListing() throws Exception {
        String s = seller(102);
        long product = createProduct(s, "VAC-1", 3, true);

        call(put("/api/seller/store/settings"), s, "{\"vacationMode\":true,\"vacationMessage\":\"Back on Monday\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.vacationMode").value(true));
        call(get("/api/products/" + product), null, null).andExpect(status().isNotFound());
        // inventory work continues while on vacation; new products are hidden too
        long added = createProduct(s, "VAC-2", 1, true);
        call(get("/api/products/" + added), null, null).andExpect(status().isNotFound());
        call(put("/api/seller/store/settings"), s, "{\"vacationMode\":false}").andExpect(status().isOk());
        call(get("/api/products/" + product), null, null).andExpect(status().isOk());

        call(put("/internal/sellers/102/suspension"), "Bearer " + TestTokens.customer(5), "{\"suspended\":true}")
                .andExpect(status().isForbidden());
        call(put("/internal/sellers/102/suspension"), "Bearer " + TestTokens.admin(), "{\"suspended\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.products").value(2));
        call(get("/api/products/" + product), null, null).andExpect(status().isNotFound());
        call(get("/api/seller/products/" + product), s, null)
                .andExpect(jsonPath("$.product.storeSuspended").value(true));
        call(put("/internal/sellers/102/suspension"), "Bearer " + TestTokens.admin(), "{\"suspended\":false}")
                .andExpect(status().isOk());
        call(get("/api/products/" + product), null, null).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ warehouses, transfers, adjustments

    @Test
    void warehousesTransfersAndAdjustmentsKeepTheLedgerBalanced() throws Exception {
        String s = seller(110);
        long product = createProduct(s, "WH-1", 20, false);
        long dubai = createWarehouse(s, "DXB");

        call(get("/api/seller/warehouses"), s, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MAIN"))
                .andExpect(jsonPath("$[0].units").value(20));

        String transfer = body(call(post("/api/seller/transfers"), s, "{\"fromWarehouseId\":"
                + JsonPath.read(body(call(get("/api/seller/warehouses"), s, null)), "$[0].id") + ",\"toWarehouseId\":"
                + dubai + ",\"lines\":[{\"productId\":" + product + ",\"quantity\":8}],\"note\":\"Stock the shop\"}")
                .andExpect(status().isCreated()));
        assertThat((String) JsonPath.read(transfer, "$.number")).isEqualTo("TRF-00001");

        call(post("/api/seller/inventory/adjustments"), s, "{\"productId\":" + product + ",\"warehouseId\":" + dubai
                + ",\"quantityChange\":-3,\"reason\":\"DAMAGED\",\"note\":\"Water damage\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(5))
                .andExpect(jsonPath("$.reason").value("Damaged"));
        call(post("/api/seller/inventory/adjustments"), s, "{\"productId\":" + product + ",\"warehouseId\":" + dubai
                + ",\"quantityChange\":-50,\"reason\":\"LOST\"}")
                .andExpect(status().isConflict());

        call(get("/api/inventory/" + product), s, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(17))
                .andExpect(jsonPath("$.warehouses.length()").value(2));
        call(put("/api/inventory/" + product), s, "{\"warehouseId\":" + dubai + ",\"onHand\":4,\"binLocation\":\"A-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(16));

        call(get("/api/seller/inventory/movements").param("productId", String.valueOf(product))
                        .param("type", "TRANSFER_IN", "TRANSFER_OUT"), s, null)
                .andExpect(jsonPath("$.totalElements").value(2));
        call(get("/api/seller/inventory/movements").param("search", "water"), s, null)
                .andExpect(jsonPath("$.content[0].type").value("ADJUSTMENT"));
        call(get("/api/seller/inventory").param("warehouseId", String.valueOf(dubai)), s, null)
                .andExpect(jsonPath("$.content[0].warehouseOnHand").value(4))
                .andExpect(jsonPath("$.content[0].binLocation").value("A-01"));
        assertLedgerBalanced(s);

        // a warehouse can only be closed when empty and not the default
        call(patch("/api/seller/warehouses/" + dubai + "/active").param("active", "false"), s, null)
                .andExpect(status().isConflict());
    }

    @Test
    void sellersNeverSeeOrChangeEachOthersStock() throws Exception {
        long product = createProduct(seller(120), "OWN-1", 5, true);
        String other = seller(121);
        call(get("/api/seller/products/" + product), other, null).andExpect(status().isNotFound());
        call(get("/api/inventory/" + product), other, null).andExpect(status().isNotFound());
        call(post("/api/seller/inventory/adjustments"), other, "{\"productId\":" + product
                + ",\"quantityChange\":5,\"reason\":\"FOUND\"}").andExpect(status().isNotFound());
        call(get("/api/seller/products"), "Bearer " + TestTokens.customer(9), null).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ purchasing

    @Test
    void purchaseOrderLifecycleWithPartialDeliveries() throws Exception {
        String s = seller(130);
        long product = createProduct(s, "PO-ITEM", 10, true); // 10 units at cost 60
        long supplier = createSupplier(s, "Al Noor Trading", 5);

        String draft = body(call(post("/api/seller/purchase-orders"), s, "{\"supplierId\":" + supplier
                + ",\"lines\":[{\"productId\":" + product + ",\"quantity\":10,\"unitCost\":90}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("PO-00001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalAmount").value(900.0)));
        long po = ((Number) JsonPath.read(draft, "$.id")).longValue();
        long line = ((Number) JsonPath.read(draft, "$.lines[0].id")).longValue();

        call(post("/api/seller/purchase-orders/" + po + "/receive"), s, "{\"lines\":[{\"lineId\":" + line
                + ",\"quantity\":4}]}").andExpect(status().isConflict());
        call(post("/api/seller/purchase-orders/" + po + "/submit"), s, null)
                .andExpect(jsonPath("$.status").value("ORDERED"));
        call(get("/api/seller/products/" + product), s, null)
                .andExpect(jsonPath("$.product.incomingQuantity").value(10));

        call(post("/api/seller/purchase-orders/" + po + "/receive"), s, "{\"lines\":[{\"lineId\":" + line
                + ",\"quantity\":4}],\"supplierReference\":\"DN-778\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("GRN-00001"))
                .andExpect(jsonPath("$.totalAmount").value(360.0));
        // weighted average cost: (10 x 60 + 4 x 90) / 14 = 68.57
        call(get("/api/seller/products/" + product), s, null)
                .andExpect(jsonPath("$.product.costPrice").value(68.57))
                .andExpect(jsonPath("$.product.availableQuantity").value(14))
                .andExpect(jsonPath("$.product.incomingQuantity").value(6));
        call(post("/api/seller/purchase-orders/" + po + "/receive"), s, "{\"lines\":[{\"lineId\":" + line
                + ",\"quantity\":7}]}")
                .andExpect(status().isBadRequest());
        call(post("/api/seller/purchase-orders/" + po + "/cancel"), s, null).andExpect(status().isConflict());
        call(post("/api/seller/purchase-orders/" + po + "/receive"), s, "{\"lines\":[{\"lineId\":" + line
                + ",\"quantity\":6}]}").andExpect(jsonPath("$.number").value("GRN-00002"));
        call(get("/api/seller/purchase-orders/" + po), s, null)
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.receipts.length()").value(2))
                .andExpect(jsonPath("$.outstandingUnits").value(0));

        call(get("/api/seller/purchase-orders").param("search", "al noor").param("status", "RECEIVED"), s, null)
                .andExpect(jsonPath("$.totalElements").value(1));
        call(get("/api/seller/suppliers"), s, null)
                .andExpect(jsonPath("$.content[0].purchaseOrders").value(1))
                .andExpect(jsonPath("$.content[0].orderedAmount").value(900.0));
        call(get("/api/seller/purchase-orders/" + po + "/document"), s, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("PO-00001.pdf")));
        call(get("/api/seller/goods-receipts").param("search", "DN-778"), s, null)
                .andExpect(jsonPath("$.totalElements").value(1));
        assertLedgerBalanced(s);
    }

    @Test
    void replenishmentSuggestsOrdersAndCreatesDraftPurchaseOrders() throws Exception {
        String s = seller(140);
        long supplier = createSupplier(s, "Gulf Wholesale", 10);
        long low = createProduct(s, "LOW-1", 1, false); // reorder point 2 -> needs stock
        createProduct(s, "OK-1", 50, false);
        call(put("/api/seller/products/" + low), s, "{\"name\":\"Low item\",\"price\":99.5,\"costPrice\":60,"
                + "\"reorderPoint\":5,\"reorderQuantity\":25,\"preferredSupplierId\":" + supplier + ",\"listed\":false}")
                .andExpect(status().isOk());

        String suggestions = body(call(get("/api/seller/replenishment"), s, null).andExpect(status().isOk()));
        assertThat((List<String>) JsonPath.read(suggestions, "$[*].sku")).containsExactly("LOW-1");
        assertThat((Integer) JsonPath.read(suggestions, "$[0].suggestedQuantity")).isEqualTo(29);

        call(post("/api/seller/replenishment/purchase-orders"), s, "{\"items\":[{\"productId\":" + low
                + ",\"quantity\":29}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].supplierName").value("Gulf Wholesale"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void stockCountPostsTheDifferencesToTheLedger() throws Exception {
        String s = seller(150);
        long a = createProduct(s, "CNT-A", 10, false);
        long b = createProduct(s, "CNT-B", 4, false);
        String count = body(call(post("/api/seller/stock-counts"), s, "{\"note\":\"Month end\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("CNT-00001")));
        long countId = ((Number) JsonPath.read(count, "$.id")).longValue();
        List<Integer> lineIds = JsonPath.read(count, "$.items[*].id");
        List<Integer> products = JsonPath.read(count, "$.items[*].productId");
        int lineA = lineIds.get(products.indexOf((int) a));
        int lineB = lineIds.get(products.indexOf((int) b));

        call(post("/api/seller/stock-counts"), s, "{}").andExpect(status().isConflict());
        call(put("/api/seller/stock-counts/" + countId + "/lines"), s, "{\"lines\":[{\"lineId\":" + lineA
                + ",\"countedQuantity\":8},{\"lineId\":" + lineB + ",\"countedQuantity\":5}]}")
                .andExpect(jsonPath("$.linesWithDifference").value(2))
                .andExpect(jsonPath("$.varianceUnits").value(-1));
        call(post("/api/seller/stock-counts/" + countId + "/complete"), s, null)
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        call(get("/api/inventory/" + a), s, null).andExpect(jsonPath("$.availableQuantity").value(8));
        call(get("/api/inventory/" + b), s, null).andExpect(jsonPath("$.availableQuantity").value(5));
        call(get("/api/seller/inventory/movements").param("type", "COUNT_ADJUSTMENT"), s, null)
                .andExpect(jsonPath("$.totalElements").value(2));
        call(get("/api/seller/stock-counts/" + countId + "/sheet"), s, null).andExpect(status().isOk());
        assertLedgerBalanced(s);
    }

    // ------------------------------------------------------------------ order saga

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        long product = createProduct(seller(160), "RACE-1", 5, true);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Boolean>> buyers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String orderRef = "RACE-ORDER-" + i;
            buyers.add(() -> {
                try {
                    inventoryService.reserve(new ReserveRequest(orderRef, List.of(new ReserveItem(product, 1))));
                    return true;
                } catch (RuntimeException ex) {
                    return false;
                }
            });
        }
        long successes = 0;
        for (Future<Boolean> result : pool.invokeAll(buyers)) {
            successes += result.get() ? 1 : 0;
        }
        pool.shutdown();
        assertThat(successes).isEqualTo(5);
        call(get("/api/inventory/" + product), seller(160), null)
                .andExpect(jsonPath("$.availableQuantity").value(0))
                .andExpect(jsonPath("$.reservedQuantity").value(5));
    }

    @Test
    void paidOrdersShipFromTheDefaultWarehouseFirstAndReturnsGoBack() throws Exception {
        String s = seller(170);
        long product = createProduct(s, "SAGA-1", 3, true);
        long dubai = createWarehouse(s, "DXB");
        call(post("/api/seller/goods-receipts"), s, "{\"productId\":" + product + ",\"warehouseId\":" + dubai
                + ",\"quantity\":5,\"unitCost\":60}").andExpect(status().isCreated());
        String customer = "Bearer " + TestTokens.customer(7);

        call(post("/internal/inventory/reservations"), customer, "{\"orderRef\":\"ORD-SAGA\",\"items\":[{\"productId\":"
                + product + ",\"quantity\":4}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].sellerId").value(170))
                .andExpect(jsonPath("$.lines[0].categoryName").value("Electronics"));
        call(post("/internal/inventory/reservations/ORD-SAGA/commit"), customer, null).andExpect(status().isOk());
        // 3 from MAIN (default) + 1 from DXB
        call(get("/api/inventory/" + product), s, null)
                .andExpect(jsonPath("$.onHandQuantity").value(4))
                .andExpect(jsonPath("$.warehouses[?(@.code == 'MAIN')].onHand").value(0))
                .andExpect(jsonPath("$.warehouses[?(@.code == 'DXB')].onHand").value(4));
        call(get("/api/seller/products/" + product), s, null).andExpect(jsonPath("$.product.unitsSold").value(4));

        call(post("/internal/inventory/reservations/ORD-SAGA/release"), customer, null).andExpect(status().isOk());
        call(get("/api/inventory/" + product), s, null)
                .andExpect(jsonPath("$.availableQuantity").value(8))
                .andExpect(jsonPath("$.warehouses[?(@.code == 'MAIN')].onHand").value(3));
        call(get("/api/seller/inventory/movements").param("search", "ORD-SAGA").param("physicalOnly", "true"), s, null)
                .andExpect(jsonPath("$.totalElements").value(4));
        assertLedgerBalanced(s);
    }

    @Test
    void ratingsFromTheOrderServiceAppearOnTheProduct() throws Exception {
        long product = createProduct(seller(175), "RATE-1", 3, true);
        call(put("/internal/products/" + product + "/rating"), "Bearer " + TestTokens.customer(3),
                "{\"average\":4.5,\"count\":2}").andExpect(status().isNoContent());
        call(get("/api/products/" + product), null, null)
                .andExpect(jsonPath("$.ratingAverage").value(4.5))
                .andExpect(jsonPath("$.ratingCount").value(2));
        call(get("/api/products").param("minRating", "4").param("q", "rate-1"), null, null)
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ------------------------------------------------------------------ bulk tools

    @Test
    void excelImportValidatesEveryRowBeforeSavingAnything() throws Exception {
        String s = seller(180);
        call(get("/api/seller/products/import/template"), s, null).andExpect(status().isOk());

        byte[] broken = workbook(new Object[]{"IMP-1", "Imported mouse", "Electronics", 79, 45, "yes", 10},
                new Object[]{"IMP-2", "Bad row", "No such department", 10, 5, "no", 0});
        call(multipart("/api/seller/products/import").file(new MockMultipartFile("file", "p.xlsx",
                "application/octet-stream", broken)), s, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].row").value(3))
                .andExpect(jsonPath("$.created").value(0));
        call(get("/api/seller/products").param("search", "imp-"), s, null).andExpect(jsonPath("$.totalElements").value(0));

        byte[] valid = workbook(new Object[]{"IMP-1", "Imported mouse", "Electronics", 79, 45, "yes", 10},
                new Object[]{"IMP-2", "Imported cable", "", 10, 5, "no", 0});
        call(multipart("/api/seller/products/import").file(new MockMultipartFile("file", "p.xlsx",
                "application/octet-stream", valid)), s, null)
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.errors.length()").value(0));
        call(get("/api/seller/products").param("search", "imp-").param("listed", "true"), s, null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].availableQuantity").value(10));
    }

    @Test
    void productPhotoUploadChecksTheFileContent() throws Exception {
        String s = seller(185);
        long product = createProduct(s, "IMG-1", 1, true);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

        call(multipart(HttpMethod.PUT, "/api/seller/products/" + product + "/image").file(new MockMultipartFile("file",
                "fake.png", "image/png", "not really an image".getBytes())), s, null).andExpect(status().isBadRequest());
        String updated = body(call(multipart(HttpMethod.PUT, "/api/seller/products/" + product + "/image")
                .file(new MockMultipartFile("file", "photo.png", "image/png", png)), s, null).andExpect(status().isOk()));
        String url = JsonPath.read(updated, "$.imageUrl");
        assertThat(url).startsWith("/api/products/" + product + "/image?v=");
        String etag = mockMvc.perform(get("/api/products/" + product + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getHeader("ETag");
        mockMvc.perform(get("/api/products/" + product + "/image").header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void bulkActionsReportWhatCouldNotBeChanged() throws Exception {
        String s = seller(190);
        long a = createProduct(s, "BULK-1", 1, false);
        long b = createProduct(s, "BULK-2", 1, false);
        call(post("/api/seller/products/bulk"), s, "{\"productIds\":[" + a + "," + b
                + ",999999],\"action\":\"ADJUST_PRICE_PERCENT\",\"percent\":10}")
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.skipped.length()").value(1));
        call(get("/api/seller/products/" + a), s, null).andExpect(jsonPath("$.product.price").value(109.45));
        call(post("/api/seller/products/bulk"), s, "{\"productIds\":[" + a + "," + b + "],\"action\":\"LIST\"}")
                .andExpect(jsonPath("$.updated").value(2));
        call(delete("/api/seller/products/" + b), s, null).andExpect(status().isNoContent());
        call(get("/api/seller/products").param("search", "bulk-"), s, null).andExpect(jsonPath("$.totalElements").value(1));
    }

    // ------------------------------------------------------------------ analytics, reports, admin

    @Test
    void analyticsAndReportsCoverTheWholeInventory() throws Exception {
        String s = seller(200);
        createProduct(s, "AN-1", 10, true);
        createProduct(s, "AN-2", 3, false);
        call(get("/api/seller/inventory/summary"), s, null)
                .andExpect(jsonPath("$.products").value(2))
                .andExpect(jsonPath("$.listedProducts").value(1))
                .andExpect(jsonPath("$.inventoryOnlyProducts").value(1))
                .andExpect(jsonPath("$.stockValueAtCost").value(780.0));
        call(get("/api/seller/inventory/analytics"), s, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsOnHand").value(13))
                .andExpect(jsonPath("$.valueByCategory[0].label").value("Electronics"))
                .andExpect(jsonPath("$.abcSummary.length()").value(3));
        for (String report : List.of("valuation", "abc", "dead-stock", "shrinkage", "flow")) {
            call(get("/api/seller/reports/inventory/" + report + "/pdf"), s, null)
                    .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        }
        call(get("/api/seller/products/export/xlsx"), s, null).andExpect(status().isOk());
        call(get("/api/seller/inventory/export/pdf").param("stockStatus", "IN_STOCK"), s, null).andExpect(status().isOk());
        call(get("/api/seller/inventory/movements/export/xlsx"), s, null).andExpect(status().isOk());
        call(get("/api/seller/reports/inventory/unknown/pdf"), s, null).andExpect(status().isBadRequest());
    }

    @Test
    void theAdministratorSeesEveryStoreButCannotWork() throws Exception {
        String s = seller(210);
        long product = createProduct(s, "ADM-1", 4, true);
        String admin = "Bearer " + TestTokens.admin();

        call(get("/api/admin/catalog/products").param("sellerId", "210"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("ADM-1"))
                .andExpect(jsonPath("$.content[0].costPrice").value(60.0));
        call(get("/api/admin/catalog/products/" + product), admin, null)
                .andExpect(jsonPath("$.product.sellerName").value("Store 210"));
        call(get("/api/admin/inventory/summary"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellers").isNumber());
        call(get("/api/admin/inventory/sellers/210/warehouses"), admin, null)
                .andExpect(jsonPath("$[0].units").value(4));
        call(get("/api/admin/inventory/movements").param("sellerId", "210"), admin, null)
                .andExpect(jsonPath("$.content[0].type").value("OPENING_STOCK"));
        // the admin watches, it does not run the store
        call(get("/api/seller/products"), admin, null).andExpect(status().isForbidden());
        call(get("/api/admin/inventory/summary"), s, null).andExpect(status().isForbidden());
    }

    private static byte[] workbook(Object[]... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Products");
            sheet.createRow(0).createCell(0).setCellValue("SKU*");
            for (int i = 0; i < rows.length; i++) {
                Object[] r = rows[i];
                Row row = sheet.createRow(i + 1);
                // SKU, Name, Category, (Brand), (Barcode), (Unit), Price, (Was), Cost, (RP), (RQ), Listed, Opening
                row.createCell(0).setCellValue((String) r[0]);
                row.createCell(1).setCellValue((String) r[1]);
                row.createCell(2).setCellValue((String) r[2]);
                row.createCell(6).setCellValue(((Number) r[3]).doubleValue());
                row.createCell(8).setCellValue(((Number) r[4]).doubleValue());
                row.createCell(11).setCellValue((String) r[5]);
                row.createCell(12).setCellValue(((Number) r[6]).doubleValue());
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
