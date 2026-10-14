package com.enterprise.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.payment.support.TestTokens;
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
class PaymentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static String charge(long orderId, long userId, String method, String extra) {
        return "{\"orderId\":" + orderId + ",\"orderNumber\":\"ORD-" + orderId + "\",\"userId\":" + userId
                + ",\"amount\":1200.00,\"currency\":\"AED\",\"method\":\"" + method + "\"" + extra + "}";
    }

    @Test
    void chargeIsIdempotentAndRefundable() throws Exception {
        String customer = "Bearer " + TestTokens.customer(17);
        String card = ",\"cardNumber\":\"5555555555554444\",\"cardHolder\":\"Kush\",\"expiry\":\"10/35\",\"cvv\":\"321\"";

        String first = mockMvc.perform(post("/internal/payments").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(charge(100, 17, "CARD", card)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.maskedInstrument").value("MASTERCARD **** 4444"))
                .andReturn().getResponse().getContentAsString();
        String ref = com.jayway.jsonpath.JsonPath.read(first, "$.transactionRef");

        mockMvc.perform(post("/internal/payments").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(charge(100, 17, "CARD", card)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionRef").value(ref));

        mockMvc.perform(post("/internal/payments/order/100/refund").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(get("/api/payments/order/100").header("Authorization", "Bearer " + TestTokens.customer(8)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payments").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void customersCannotChargeOnBehalfOfOthers() throws Exception {
        mockMvc.perform(post("/internal/payments").header("Authorization", "Bearer " + TestTokens.customer(7))
                        .contentType(MediaType.APPLICATION_JSON).content(charge(200, 99, "COD", "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashOnDeliveryIsCapturedByAdminOnDelivery() throws Exception {
        String customer = "Bearer " + TestTokens.customer(7);
        mockMvc.perform(post("/internal/payments").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(charge(300, 7, "COD", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/internal/payments/order/300/capture").header("Authorization", customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/payments/order/300/capture").header("Authorization", "Bearer " + TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void invalidCardReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/internal/payments").header("Authorization", "Bearer " + TestTokens.customer(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(charge(400, 7, "CARD", ",\"cardNumber\":\"1234\",\"cardHolder\":\"K\",\"expiry\":\"10/35\",\"cvv\":\"1\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The card number is not valid"));
    }
}
