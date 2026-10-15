package com.enterprise.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.order.client.InventoryClient;
import com.enterprise.order.client.NotificationPublisher;
import com.enterprise.order.client.PaymentClient;
import com.enterprise.order.client.StoreDirectoryClient;
import com.enterprise.order.security.InternalApiKey;
import com.enterprise.order.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
 * Order API end to end inside the service: MockMvc -> controllers -> services -> JPA -> H2 (PostgreSQL mode).
 * The other microservices are replaced by Mockito mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
class OrderApiIntegrationTest {

    private static final String KEY = "test-internal-key";
    private static final String ADDRESS = "\"address\":{\"recipient\":\"Kush Kumar\",\"phone\":\"+971 50 123 4567\","
            + "\"line1\":\"Marina Walk, Tower 3\",\"city\":\"Dubai\",\"region\":\"Dubai\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private InventoryClient inventoryClient;
    @MockitoBean
    private PaymentClient paymentClient;
    @MockitoBean
    private NotificationPublisher notifications;
    @MockitoBean
    private StoreDirectoryClient stores;

    @BeforeEach
    void mockOtherServices() {
        when(inventoryClient.lookup(anyList())).thenReturn(List.of(new InventoryClient.ProductInfo(3L, 100L,
                "Store 100", "Phone", new BigDecimal("2500.00"), true, 10)));
        when(inventoryClient.reserve(anyString(), anyList())).thenAnswer(inv -> new InventoryClient.Reservation(
                inv.getArgument(0), "AED", Instant.now(), List.of(new InventoryClient.Line(3L, 100L, "Store 100",
                "PHN-1", "Phone", "Electronics", new BigDecimal("2500.00"), new BigDecimal("1800.00"), 2))));
        when(stores.find(anyLong())).thenReturn(new StoreDirectoryClient.StoreDetails(100L, "Store 100",
                "+971 4 000 0000", "help@store100.ae", "Dubai", "United Arab Emirates", "TL-1", "100123456700003"));
    }

    private int checkout(String customer) throws Exception {
        String body = mockMvc.perform(post("/api/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":3,\"quantity\":2}]," + ADDRESS + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orders[0].status").value("PENDING"))
                .andExpect(jsonPath("$.orders[0].sellerName").value("Store 100"))
                .andExpect(jsonPath("$.orders[0].recipientName").value("Kush Kumar"))
                .andExpect(jsonPath("$.grandTotal").value(5000.0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.orders[0].id");
    }

    private void pay(String customer, int orderId, String method, String status) throws Exception {
        mockMvc.perform(post("/internal/orders/" + orderId + "/payment-intent").header("Authorization", customer)
                        .header(InternalApiKey.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(5000.0));
        mockMvc.perform(post("/internal/orders/" + orderId + "/payment-outcome").header("Authorization", customer)
                        .header(InternalApiKey.HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":" + (orderId + 500) + ",\"transactionRef\":\"TXN-" + orderId
                                + "\",\"method\":\"" + method + "\",\"status\":\"" + status
                                + "\",\"maskedInstrument\":\"kush@okbank\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"));
    }

    @Test
    void customerBuysSellerShipsCustomerReviewsAndEveryoneSeesTheRightData() throws Exception {
        String customer = "Bearer " + TestTokens.customer(7);
        String seller = "Bearer " + TestTokens.seller(100);
        String otherSeller = "Bearer " + TestTokens.seller(200);
        String admin = "Bearer " + TestTokens.admin();

        int orderId = checkout(customer);
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + TestTokens.customer(8)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/internal/orders/" + orderId + "/payment-intent").header("Authorization", customer))
                .andExpect(status().isForbidden());
        pay(customer, orderId, "UPI", "SUCCESS");

        // seller order desk with filters
        mockMvc.perform(get("/api/seller/orders").header("Authorization", seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].grossProfit").value(1400.0))
                .andExpect(jsonPath("$.content[0].customerEmail").value("customer7@example.com"));
        mockMvc.perform(get("/api/seller/orders").param("status", "CONFIRMED").param("q", "phone")
                        .param("minTotal", "1000").header("Authorization", seller))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/seller/orders").param("q", "no-such-thing").header("Authorization", seller))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/seller/orders").param("status", "SHIPPED").header("Authorization", seller))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/seller/orders").header("Authorization", otherSeller))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/seller/orders/counts").header("Authorization", seller))
                .andExpect(jsonPath("$.CONFIRMED").value(1))
                .andExpect(jsonPath("$.ALL").value(1));

        // fulfilment
        mockMvc.perform(put("/api/seller/orders/" + orderId + "/status").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\",\"carrier\":\"Aramex\",\"trackingNumber\":\"AWB1\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/seller/orders/" + orderId + "/status").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/seller/orders/" + orderId + "/status").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\",\"carrier\":\"Aramex\",\"trackingNumber\":\"AWB-998877\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        mockMvc.perform(post("/api/reviews").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":3,\"rating\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/seller/orders/" + orderId + "/status").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", customer))
                .andExpect(jsonPath("$.trackingNumber").value("AWB-998877"))
                .andExpect(jsonPath("$.invoiceAvailable").value(true))
                .andExpect(jsonPath("$.history.length()").value(4));

        // reviews
        mockMvc.perform(get("/api/reviews/pending").header("Authorization", customer))
                .andExpect(jsonPath("$[0].productId").value(3));
        String review = mockMvc.perform(post("/api/reviews").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":3,\"rating\":5,\"comment\":\"Great phone\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int reviewId = JsonPath.read(review, "$.id");
        mockMvc.perform(post("/api/reviews").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":3,\"rating\":4}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/reviews/pending").header("Authorization", customer))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/seller/reviews/" + reviewId + "/reply").header("Authorization", otherSeller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"Thanks!\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/seller/reviews").param("replied", "false").header("Authorization", seller))
                .andExpect(jsonPath("$.awaitingReply").value(1))
                .andExpect(jsonPath("$.reviews.totalElements").value(1));
        mockMvc.perform(put("/api/seller/reviews/" + reviewId + "/reply").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"Thank you, enjoy it!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reviews").param("productId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.distribution.5").value(1))
                .andExpect(jsonPath("$.reviews.content[0].comment").value("Great phone"))
                .andExpect(jsonPath("$.reviews.content[0].sellerReply").value("Thank you, enjoy it!"));

        // analytics, customers and reports
        mockMvc.perform(get("/api/seller/analytics").header("Authorization", seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(5000.0))
                .andExpect(jsonPath("$.costOfGoodsSold").value(3600.0))
                .andExpect(jsonPath("$.grossProfit").value(1400.0))
                .andExpect(jsonPath("$.grossMarginPercent").value(28.0))
                .andExpect(jsonPath("$.topProducts[0].unitsSold").value(2))
                .andExpect(jsonPath("$.categories[0].category").value("Electronics"))
                .andExpect(jsonPath("$.regions[0].region").value("Dubai"))
                .andExpect(jsonPath("$.trend.length()").value(30))
                .andExpect(jsonPath("$.customers").value(1))
                .andExpect(jsonPath("$.averageDispatchHours").value(0.0));
        mockMvc.perform(get("/api/seller/analytics").param("granularity", "month").header("Authorization", seller))
                .andExpect(jsonPath("$.granularity").value("month"));
        mockMvc.perform(get("/api/seller/customers").header("Authorization", seller))
                .andExpect(jsonPath("$.content[0].username").value("customer7"))
                .andExpect(jsonPath("$.content[0].segment").value("VIP"))
                .andExpect(jsonPath("$.customers").value(1));
        String today = LocalDate.now().toString();
        mockMvc.perform(get("/internal/sellers/100/sales-summary").param("from", today).param("to", today)
                        .header("Authorization", seller).header(InternalApiKey.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossProfit").value(1400.0));
        mockMvc.perform(get("/internal/sellers/100/sales-summary").param("from", today).param("to", today)
                        .header("Authorization", otherSeller).header(InternalApiKey.HEADER, KEY))
                .andExpect(status().isForbidden());

        assertPdf(get("/api/seller/reports/sales/pdf").header("Authorization", seller));
        assertPdf(get("/api/seller/orders/export/pdf").param("status", "DELIVERED").header("Authorization", seller));
        assertPdf(get("/api/seller/orders/" + orderId + "/invoice").header("Authorization", seller));
        assertPdf(get("/api/seller/orders/" + orderId + "/packing-slip").header("Authorization", seller));
        assertPdf(get("/api/orders/" + orderId + "/invoice").header("Authorization", customer));
        assertPdf(get("/api/seller/customers/export/pdf").header("Authorization", seller));
        mockMvc.perform(get("/api/seller/reports/sales/xlsx").header("Authorization", seller))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/seller/orders/export/xlsx").header("Authorization", seller))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/" + orderId + "/invoice").header("Authorization", "Bearer " + TestTokens.customer(8)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/customer/7").header("Authorization", customer))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/orders/customer/7").header("Authorization", "Bearer " + TestTokens.customer(8)))
                .andExpect(status().isForbidden());

        // the platform administrator sees everything
        mockMvc.perform(get("/api/admin/orders").param("q", "customer7").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sellerName").value("Store 100"))
                .andExpect(jsonPath("$.content[0].customerPhone").value("+971 50 123 4567"));
        mockMvc.perform(get("/api/admin/orders").header("Authorization", seller))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders/" + orderId).header("Authorization", admin))
                .andExpect(jsonPath("$.items[0].unitCost").value(1800.0));
        mockMvc.perform(get("/api/admin/sales/overview").header("Authorization", admin))
                .andExpect(jsonPath("$.analytics.revenue").value(5000.0))
                .andExpect(jsonPath("$.topSellers[0].sellerName").value("Store 100"));
        mockMvc.perform(get("/api/admin/customers/7/purchases").header("Authorization", admin))
                .andExpect(jsonPath("$.orders").value(1))
                .andExpect(jsonPath("$.spent").value(5000.0));
        mockMvc.perform(get("/api/orders/customer/7").header("Authorization", admin))
                .andExpect(jsonPath("$.totalElements").value(1));
        assertPdf(get("/api/admin/orders/export/pdf").header("Authorization", admin));
        assertPdf(get("/api/admin/sales/report/pdf").header("Authorization", admin));
        mockMvc.perform(get("/api/admin/reviews").param("q", "great").header("Authorization", admin))
                .andExpect(jsonPath("$.reviews.totalElements").value(1));
        mockMvc.perform(delete("/api/admin/reviews/" + reviewId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reviews").param("productId", "3"))
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void customerCancelsAPaidOrderAndGetsARefund() throws Exception {
        String customer = "Bearer " + TestTokens.customer(9);
        int orderId = checkout(customer);
        pay(customer, orderId, "CARD", "SUCCESS");

        mockMvc.perform(put("/api/orders/" + orderId + "/cancel").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ordered by mistake\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentState").value("REFUNDED"));
        verify(paymentClient).refund((long) orderId, "Ordered by mistake");
        mockMvc.perform(get("/api/orders/" + orderId + "/invoice").header("Authorization", customer))
                .andExpect(status().isConflict());
    }

    @Test
    void internalEndpointsNeedTheServiceKey() throws Exception {
        mockMvc.perform(post("/internal/orders/1/payment-outcome").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\",\"status\":\"SUCCESS\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/orders/1/payment-outcome").header(InternalApiKey.HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CARD\",\"status\":\"SUCCESS\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/orders/999999/payment-outcome").header(InternalApiKey.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CARD\",\"status\":\"SUCCESS\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsAreReported() throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + TestTokens.customer(7))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists());
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + TestTokens.customer(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":3,\"quantity\":1}],\"address\":{\"recipient\":\"K\","
                                + "\"phone\":\"call me\",\"line1\":\"x\",\"city\":\"Dubai\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['address.phone']").exists());
    }

    @Test
    void ordersRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    private void assertPdf(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        byte[] pdf = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
