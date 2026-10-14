package com.enterprise.order;

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
    void fullOrderLifecycle() throws Exception {
        when(inventoryClient.reserve(anyString(), anyList())).thenReturn(new InventoryClient.Reservation("ref", "AED",
                Instant.now(), List.of(new InventoryClient.Line(3L, "PHN-1", "Phone", new BigDecimal("2500.00"), 2))));
        when(paymentClient.charge(any())).thenReturn(new PaymentClient.PaymentResult(11L, "TXN-ABC", 1L,
                new BigDecimal("5000.00"), "UPI", "SUCCESS", "kush@okbank", null));
        String customer = "Bearer " + TestTokens.customer(7);
        String admin = "Bearer " + TestTokens.admin();

        String body = mockMvc.perform(post("/api/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":3,\"quantity\":2}],\"shippingAddress\":\"Marina Walk, Dubai, UAE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(5000.0))
                .andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + TestTokens.customer(8)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"UPI\",\"upiId\":\"kush@okbank\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.transactionRef").value("TXN-ABC"));

        mockMvc.perform(put("/api/orders/" + orderId + "/status").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/orders/" + orderId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/orders/" + orderId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\",\"note\":\"Aramex AWB 123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        mockMvc.perform(put("/api/orders/" + orderId + "/cancel").header("Authorization", customer))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/orders").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].history.length()").value(3));
        mockMvc.perform(get("/api/orders/stats").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordersByStatus.SHIPPED").value(1))
                .andExpect(jsonPath("$.topProducts[0].unitsSold").value(2))
                .andExpect(jsonPath("$.last7Days[0].orders").value(1));
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
