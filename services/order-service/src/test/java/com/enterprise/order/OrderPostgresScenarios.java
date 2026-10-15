package com.enterprise.order;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.order.client.InventoryClient;
import com.enterprise.order.client.NotificationPublisher;
import com.enterprise.order.client.PaymentClient;
import com.enterprise.order.client.StoreDirectoryClient;
import com.enterprise.order.order.OrderService;
import com.enterprise.order.security.InternalApiKey;
import com.enterprise.order.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The order flow on real PostgreSQL: POST /api/orders -> controller -> service -> (inventory) -> PostgreSQL,
 * then payment, fulfilment, review and every analytics / search query, because their generated SQL
 * (nullable filters, GROUP BY expressions, date casts, sub-queries) must be valid for PostgreSQL, not only H2.
 * Subclasses decide where PostgreSQL comes from (Testcontainers in CI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
abstract class OrderPostgresScenarios {

    private static final String KEY = "test-internal-key";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected OrderService orderService;
    @MockitoBean
    protected InventoryClient inventoryClient;
    @MockitoBean
    protected PaymentClient paymentClient;
    @MockitoBean
    protected NotificationPublisher notifications;
    @MockitoBean
    protected StoreDirectoryClient stores;

    @Test
    void fullOrderLifecycleAndEveryReportRunOnPostgres() throws Exception {
        when(inventoryClient.lookup(anyList())).thenReturn(List.of(new InventoryClient.ProductInfo(31L, 700L,
                "PG Store", "Desk lamp", new BigDecimal("199.00"), true, 50)));
        when(inventoryClient.reserve(anyString(), anyList())).thenAnswer(inv -> new InventoryClient.Reservation(
                inv.getArgument(0), "AED", Instant.now(), List.of(new InventoryClient.Line(31L, 700L, "PG Store",
                "LMP-31", "Desk lamp", "Home & Kitchen", new BigDecimal("199.00"), new BigDecimal("120.00"), 3))));
        String customer = "Bearer " + TestTokens.customer(70);
        String seller = "Bearer " + TestTokens.seller(700);
        String admin = "Bearer " + TestTokens.admin();

        String checkout = mockMvc.perform(post("/api/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":31,\"quantity\":3}],\"address\":{\"recipient\":\"Kush\","
                                + "\"phone\":\"+971501234567\",\"line1\":\"Al Majaz 2\",\"city\":\"Sharjah\","
                                + "\"region\":\"Sharjah\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(checkout, "$.orders[0].id");
        mockMvc.perform(post("/internal/orders/" + orderId + "/payment-intent").header("Authorization", customer)
                        .header(InternalApiKey.HEADER, KEY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/orders/" + orderId + "/payment-outcome").header(InternalApiKey.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":9001,\"transactionRef\":\"TXN-PG\",\"method\":\"CARD\","
                                + "\"status\":\"SUCCESS\",\"maskedInstrument\":\"VISA **** 1111\"}"))
                .andExpect(jsonPath("$.confirmed").value(true));
        mockMvc.perform(put("/api/seller/orders/" + orderId + "/status").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\",\"carrier\":\"Emirates Post\",\"trackingNumber\":\"EP1\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/seller/orders/" + orderId + "/status").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reviews/pending").header("Authorization", customer))
                .andExpect(jsonPath("$[0].productName").value("Desk lamp"));
        mockMvc.perform(post("/api/reviews").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":31,\"rating\":4,\"comment\":\"Bright\"}"))
                .andExpect(status().isCreated());

        // Criteria search with every filter, including the product sub-query and LIKE escaping
        mockMvc.perform(get("/api/seller/orders").header("Authorization", seller)
                        .param("status", "DELIVERED", "SHIPPED").param("paymentMethod", "card")
                        .param("paymentState", "PAID").param("from", "2020-01-01").param("to", "2100-01-01")
                        .param("minTotal", "1").param("maxTotal", "100000").param("region", "sharjah")
                        .param("q", "lamp").param("sort", "total_desc"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/seller/orders").header("Authorization", seller).param("q", "100%_"))
                .andExpect(jsonPath("$.totalElements").value(0));

        // analytics: seller (sellerId set) and administrator (sellerId null) variants of every aggregate
        for (String granularity : List.of("day", "week", "month")) {
            mockMvc.perform(get("/api/seller/analytics").header("Authorization", seller)
                            .param("granularity", granularity))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.revenue").value(597.0))
                    .andExpect(jsonPath("$.categories[0].category").value("Home & Kitchen"))
                    .andExpect(jsonPath("$.regions[0].region").value("Sharjah"));
        }
        mockMvc.perform(get("/api/admin/sales/overview").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topSellers[0].sellerName").value("PG Store"));
        mockMvc.perform(get("/api/seller/customers").header("Authorization", seller).param("q", "customer"))
                .andExpect(jsonPath("$.customers").value(1));
        mockMvc.perform(get("/api/seller/reviews").header("Authorization", seller))
                .andExpect(jsonPath("$.averageRating").value(4.0));
        mockMvc.perform(get("/api/admin/reviews").header("Authorization", admin).param("q", "bright"))
                .andExpect(jsonPath("$.reviews.totalElements").value(1));
        mockMvc.perform(get("/api/admin/customers/70/purchases").header("Authorization", admin))
                .andExpect(jsonPath("$.orders").value(1));
        mockMvc.perform(get("/api/admin/orders/counts").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/internal/sellers/700/sales-summary").header(InternalApiKey.HEADER, KEY)
                        .param("from", java.time.LocalDate.now().minusDays(30).toString())
                        .param("to", java.time.LocalDate.now().toString()))
                .andExpect(jsonPath("$.orders").value(1));
        orderService.expireUnpaidOrders(10);
    }
}
