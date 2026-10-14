package com.enterprise.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.enterprise.order.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

/** Order API with a real database (H2) and the other services mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private InventoryClient inventoryClient;
    @MockitoBean
    private PaymentClient paymentClient;
    @MockitoBean
    private NotificationPublisher notifications;

    @Test
    void customerBuysMerchantShipsAndCustomerReviews() throws Exception {
        when(inventoryClient.lookup(anyList())).thenReturn(List.of(new InventoryClient.ProductInfo(3L, 100L, "Store 100",
                "Phone", new BigDecimal("2500.00"), true, 10)));
        when(inventoryClient.reserve(anyString(), anyList())).thenAnswer(inv -> new InventoryClient.Reservation(
                inv.getArgument(0), "AED", Instant.now(), List.of(new InventoryClient.Line(3L, 100L, "Store 100", "PHN-1",
                "Phone", new BigDecimal("2500.00"), new BigDecimal("1800.00"), 2))));
        when(paymentClient.charge(any())).thenReturn(new PaymentClient.PaymentResult(11L, "TXN-ABC", 1L,
                new BigDecimal("5000.00"), "UPI", "SUCCESS", "kush@okbank", null));
        String customer = "Bearer " + TestTokens.customer(7);
        String merchant = "Bearer " + TestTokens.merchant(100);
        String otherMerchant = "Bearer " + TestTokens.merchant(200);

        String body = mockMvc.perform(post("/api/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":3,\"quantity\":2}],\"shippingAddress\":\"Marina Walk, Dubai, UAE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orders[0].status").value("PENDING"))
                .andExpect(jsonPath("$.orders[0].merchantName").value("Store 100"))
                .andExpect(jsonPath("$.grandTotal").value(5000.0))
                .andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(body, "$.orders[0].id");

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + TestTokens.customer(8)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/pay").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[" + orderId + "],\"payment\":{\"method\":\"UPI\",\"upiId\":\"kush@okbank\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].success").value(true))
                .andExpect(jsonPath("$[0].result.order.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/merchant/orders").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].grossProfit").value(1400.0));
        mockMvc.perform(get("/api/merchant/orders").header("Authorization", otherMerchant))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(put("/api/merchant/orders/" + orderId + "/ship").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"carrier\":\"Aramex\",\"trackingNumber\":\"AWB1\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/merchant/orders/" + orderId + "/deliver").header("Authorization", merchant))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/merchant/orders/" + orderId + "/ship").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"carrier\":\"Aramex\",\"trackingNumber\":\"AWB-998877\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        mockMvc.perform(post("/api/reviews").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":3,\"rating\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/merchant/orders/" + orderId + "/deliver").header("Authorization", merchant))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", customer))
                .andExpect(jsonPath("$.trackingNumber").value("AWB-998877"))
                .andExpect(jsonPath("$.history.length()").value(4));

        mockMvc.perform(post("/api/reviews").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":3,\"rating\":5,\"comment\":\"Great phone\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/reviews").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":3,\"rating\":4}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/reviews").param("productId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviews.content[0].comment").value("Great phone"));

        mockMvc.perform(get("/api/merchant/analytics").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(5000.0))
                .andExpect(jsonPath("$.costOfGoodsSold").value(3600.0))
                .andExpect(jsonPath("$.grossProfit").value(1400.0))
                .andExpect(jsonPath("$.grossMarginPercent").value(28.0))
                .andExpect(jsonPath("$.topProducts[0].unitsSold").value(2));
        String today = LocalDate.now().toString();
        mockMvc.perform(get("/internal/merchants/100/sales-summary").param("from", today).param("to", today)
                        .header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossProfit").value(1400.0));
        mockMvc.perform(get("/internal/merchants/100/sales-summary").param("from", today).param("to", today)
                        .header("Authorization", otherMerchant))
                .andExpect(status().isForbidden());

        byte[] pdf = mockMvc.perform(get("/api/merchant/reports/sales/pdf").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/merchant/reports/sales/xlsx").header("Authorization", merchant))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/stats").header("Authorization", "Bearer " + TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topMerchants[0].merchantName").value("Store 100"));
    }

    @Test
    void validationErrorsAreReported() throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + TestTokens.customer(7))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[],\"shippingAddress\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").exists())
                .andExpect(jsonPath("$.fieldErrors.shippingAddress").exists());
    }

    @Test
    void ordersRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }
}
