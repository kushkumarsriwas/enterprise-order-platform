package com.enterprise.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.payment.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
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

    private static final String CARD =
            ",\"cardNumber\":\"5555555555554444\",\"cardHolder\":\"Kush\",\"expiry\":\"10/35\",\"cvv\":\"321\"";

    @Autowired
    private MockMvc mockMvc;

    private static String charge(long orderId, long userId, long merchantId, String method, String extra) {
        return "{\"orderId\":" + orderId + ",\"orderNumber\":\"ORD-" + orderId + "\",\"userId\":" + userId
                + ",\"merchantId\":" + merchantId + ",\"amount\":1200.00,\"currency\":\"AED\",\"method\":\""
                + method + "\"" + extra + "}";
    }

    @Test
    void chargeIsIdempotentKeepsThePlatformFeeAndIsRefundable() throws Exception {
        String customer = "Bearer " + TestTokens.customer(17);
        String merchant = "Bearer " + TestTokens.merchant(50);

        String first = mockMvc.perform(post("/internal/payments").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(charge(100, 17, 50, "CARD", CARD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.maskedInstrument").value("MASTERCARD **** 4444"))
                .andExpect(jsonPath("$.platformFee").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String ref = JsonPath.read(first, "$.transactionRef");

        mockMvc.perform(post("/internal/payments").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(charge(100, 17, 50, "CARD", CARD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionRef").value(ref));

        // the seller sees the 5% platform fee and the net amount
        mockMvc.perform(get("/api/merchant/payments").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].platformFee").value(60.0))
                .andExpect(jsonPath("$.content[0].merchantNet").value(1140.0));
        mockMvc.perform(get("/api/merchant/payments/summary").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossSales").value(1200.0))
                .andExpect(jsonPath("$.platformFees").value(60.0))
                .andExpect(jsonPath("$.netEarnings").value(1140.0))
                .andExpect(jsonPath("$.byMethod[0].method").value("CARD"));
        mockMvc.perform(get("/api/merchant/payments").header("Authorization", customer))
                .andExpect(status().isForbidden());

        // the seller cancels the order -> refund
        mockMvc.perform(post("/internal/payments/order/100/refund").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Out of stock\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(get("/api/merchant/payments/summary").header("Authorization", merchant))
                .andExpect(jsonPath("$.grossSales").value(0.0))
                .andExpect(jsonPath("$.refunded").value(1200.0));

        mockMvc.perform(get("/api/payments/order/100").header("Authorization", "Bearer " + TestTokens.customer(8)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payments/order/100").header("Authorization", "Bearer " + TestTokens.merchant(51)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payments").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void customersCannotChargeOnBehalfOfOthers() throws Exception {
        mockMvc.perform(post("/internal/payments").header("Authorization", "Bearer " + TestTokens.customer(7))
                        .contentType(MediaType.APPLICATION_JSON).content(charge(200, 99, 50, "COD", "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashOnDeliveryIsConfirmedByTheSellerOnDelivery() throws Exception {
        String customer = "Bearer " + TestTokens.customer(7);
        mockMvc.perform(post("/internal/payments").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(charge(300, 7, 60, "COD", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/api/merchant/payments/summary").header("Authorization", "Bearer " + TestTokens.merchant(60)))
                .andExpect(jsonPath("$.awaitingCollection").value(1200.0))
                .andExpect(jsonPath("$.collected").value(0.0));
        mockMvc.perform(post("/internal/payments/order/300/capture").header("Authorization", customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/payments/order/300/capture").header("Authorization", "Bearer " + TestTokens.merchant(61)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/payments/order/300/capture").header("Authorization", "Bearer " + TestTokens.merchant(60)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        mockMvc.perform(get("/api/payments/platform-summary").header("Authorization", "Bearer " + TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeRatePercent").value(5.0));
        mockMvc.perform(get("/api/payments/platform-summary").header("Authorization", customer))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCardReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/internal/payments").header("Authorization", "Bearer " + TestTokens.customer(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(charge(400, 7, 50, "CARD", ",\"cardNumber\":\"1234\",\"cardHolder\":\"K\",\"expiry\":\"10/35\",\"cvv\":\"1\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The card number is not valid"));
    }
}
