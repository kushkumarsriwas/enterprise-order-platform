package com.enterprise.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.payment.client.NotificationPublisher;
import com.enterprise.payment.client.OrderClient.OrderSummary;
import com.enterprise.payment.client.OrderClient.PaymentIntent;
import com.enterprise.payment.client.OrderClient.PaymentOutcome;
import com.enterprise.payment.client.OrderClient;
import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.security.InternalApiKey;
import com.enterprise.payment.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
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
 * POST /api/payments end to end inside the service (MockMvc -> strategy -> mock provider -> H2 in Oracle mode),
 * with the order service mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
class PaymentApiIntegrationTest {

    private static final String KEY = "test-internal-key";
    private static final String CARD =
            ",\"cardNumber\":\"5555555555554444\",\"cardHolder\":\"Kush\",\"expiry\":\"10/35\",\"cvv\":\"321\"";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private OrderClient orderClient;
    @MockitoBean
    private NotificationPublisher notifications;

    private void orderAwaitsPayment(long orderId, long customerId, long sellerId, String amount) {
        when(orderClient.paymentIntent(orderId)).thenReturn(new PaymentIntent(orderId, "ORD-" + orderId, customerId,
                "customer" + customerId, null, sellerId, "Store " + sellerId, new BigDecimal(amount), "AED"));
        when(orderClient.reportOutcome(eq(orderId), any())).thenAnswer(invocation -> {
            OrderClient.OutcomeRequest outcome = invocation.getArgument(1);
            boolean ok = !"FAILED".equals(outcome.status());
            return new PaymentOutcome(ok, false, ok ? "Order confirmed" : outcome.failureReason(),
                    new OrderSummary(orderId, "ORD-" + orderId, ok ? "CONFIRMED" : "PENDING", ok ? "PAID" : "UNPAID"));
        });
    }

    private static String pay(long orderId, String method, String extra) {
        return "{\"orderId\":" + orderId + ",\"method\":\"" + method + "\"" + extra + "}";
    }

    @Test
    void cardPaymentConfirmsTheOrderKeepsThePlatformFeeAndIsRefundable() throws Exception {
        String customer = "Bearer " + TestTokens.customer(17);
        String seller = "Bearer " + TestTokens.seller(50);
        String admin = "Bearer " + TestTokens.admin();
        orderAwaitsPayment(100, 17, 50, "1200.00");

        String body = mockMvc.perform(post("/api/payments").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(pay(100, "CARD", CARD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.orderConfirmed").value(true))
                .andExpect(jsonPath("$.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.amount").value(1200.0))
                .andExpect(jsonPath("$.maskedInstrument").value("MASTERCARD **** 4444"))
                .andExpect(jsonPath("$.platformFee").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        int paymentId = JsonPath.read(body, "$.id");
        mockMvc.perform(get("/api/payments/" + paymentId).header("Authorization", customer))
                .andExpect(jsonPath("$.transactionReference").exists());
        mockMvc.perform(get("/api/payments/" + paymentId).header("Authorization", "Bearer " + TestTokens.customer(8)))
                .andExpect(status().isNotFound());

        // the seller sees the 5% platform fee and the net amount, with filters
        mockMvc.perform(get("/api/seller/payments").header("Authorization", seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerUsername").value("customer17"))
                .andExpect(jsonPath("$.content[0].platformFee").value(60.0))
                .andExpect(jsonPath("$.content[0].sellerNet").value(1140.0));
        mockMvc.perform(get("/api/seller/payments").param("method", "UPI").header("Authorization", seller))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/seller/payments").param("q", "4444").param("minAmount", "1000")
                        .header("Authorization", seller))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/seller/payments/summary").header("Authorization", seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossSales").value(1200.0))
                .andExpect(jsonPath("$.platformFees").value(60.0))
                .andExpect(jsonPath("$.netEarnings").value(1140.0))
                .andExpect(jsonPath("$.successRatePercent").value(100.0))
                .andExpect(jsonPath("$.daily.length()").value(30))
                .andExpect(jsonPath("$.byMethod[0].method").value("CARD"));
        byte[] pdf = mockMvc.perform(get("/api/seller/payments/export/pdf").header("Authorization", seller))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/seller/payments").header("Authorization", customer))
                .andExpect(status().isForbidden());

        // the administrator sees every transaction with the provider details
        mockMvc.perform(get("/api/admin/payments").param("q", "customer17").header("Authorization", admin))
                .andExpect(jsonPath("$.content[0].provider").value("mock"))
                .andExpect(jsonPath("$.content[0].providerReference").exists())
                .andExpect(jsonPath("$.content[0].sellerName").value("Store 50"));
        mockMvc.perform(get("/api/admin/payments/summary").header("Authorization", admin))
                .andExpect(jsonPath("$.platformRevenue").isNumber())
                .andExpect(jsonPath("$.topSellers[?(@.sellerName == 'Store 50')].platformFees").value(60.0));
        mockMvc.perform(get("/api/admin/payments/export/xlsx").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/payments").header("Authorization", seller))
                .andExpect(status().isForbidden());

        // the order is cancelled -> the order service asks for a refund
        mockMvc.perform(post("/internal/payments/order/100/refund").header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Out of stock\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/payments/order/100/refund").header("Authorization", seller)
                        .header(InternalApiKey.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Out of stock\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(get("/api/seller/payments/summary").header("Authorization", seller))
                .andExpect(jsonPath("$.grossSales").value(0.0))
                .andExpect(jsonPath("$.refunded").value(1200.0));
        mockMvc.perform(get("/api/payments").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cashOnDeliveryIsConfirmedByTheSellerOnDelivery() throws Exception {
        String customer = "Bearer " + TestTokens.customer(7);
        orderAwaitsPayment(300, 7, 60, "1200.00");
        mockMvc.perform(post("/api/payments").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(pay(300, "COD", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderConfirmed").value(true));
        mockMvc.perform(get("/api/seller/payments/summary").header("Authorization", "Bearer " + TestTokens.seller(60)))
                .andExpect(jsonPath("$.awaitingCollection").value(1200.0))
                .andExpect(jsonPath("$.collected").value(0.0));
        mockMvc.perform(post("/internal/payments/order/300/capture").header("Authorization", customer)
                        .header(InternalApiKey.HEADER, KEY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/payments/order/300/capture").header("Authorization",
                        "Bearer " + TestTokens.seller(61)).header(InternalApiKey.HEADER, KEY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/payments/order/300/capture").header("Authorization",
                        "Bearer " + TestTokens.seller(60)).header(InternalApiKey.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void declinedCardsAndInvalidDetails() throws Exception {
        String customer = "Bearer " + TestTokens.customer(7);
        orderAwaitsPayment(400, 7, 50, "99.00");
        mockMvc.perform(post("/api/payments").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                        .content(pay(400, "CARD", ",\"cardNumber\":\"1234\",\"cardHolder\":\"K\",\"expiry\":\"10/35\","
                                + "\"cvv\":\"1\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The card number is not valid"));
        mockMvc.perform(post("/api/payments").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                        .content(pay(400, "CARD", ",\"cardNumber\":\"4000000000000002\",\"cardHolder\":\"K\","
                                + "\"expiry\":\"10/35\",\"cvv\":\"123\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.orderConfirmed").value(false))
                .andExpect(jsonPath("$.message").value("Card declined by the issuing bank."));
        mockMvc.perform(post("/api/payments").header("Authorization", "Bearer " + TestTokens.seller(50))
                        .contentType(MediaType.APPLICATION_JSON).content(pay(400, "COD", "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void oneCheckoutWithTwoSellersIsPaidOrderByOrder() throws Exception {
        String customer = "Bearer " + TestTokens.customer(9);
        orderAwaitsPayment(501, 9, 50, "100.00");
        when(orderClient.paymentIntent(502L)).thenThrow(ApiException.conflict(
                "The payment window for order ORD-502 has expired. Please place a new order."));
        mockMvc.perform(post("/api/payments/checkout").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[501,502],\"method\":\"UPI\",\"upiId\":\"kush@okbank\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].success").value(true))
                .andExpect(jsonPath("$[1].success").value(false))
                .andExpect(jsonPath("$[1].message").value(
                        "The payment window for order ORD-502 has expired. Please place a new order."));
    }

    @Test
    void internalEndpointsNeedTheServiceKey() throws Exception {
        mockMvc.perform(post("/internal/payments/order/1/capture")).andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/payments/order/1/capture").header(InternalApiKey.HEADER, "nope"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/payments/order/987654/capture").header(InternalApiKey.HEADER, KEY))
                .andExpect(status().isNotFound());
    }
}
