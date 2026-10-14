package com.enterprise.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.payment.client.NotificationPublisher;
import com.enterprise.payment.client.OrderClient;
import com.enterprise.payment.client.OrderClient.MonthAmounts;
import com.enterprise.payment.client.OrderClient.SalesSummary;
import com.enterprise.payment.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
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

/** Expenses, budgets with alerts, and the profit & loss statement (order service mocked). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
class FinanceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private OrderClient orderClient;
    @MockitoBean
    private NotificationPublisher notifications;

    private static String expense(String category, String amount, String description, String date) {
        return "{\"category\":\"" + category + "\",\"amount\":" + amount + ",\"description\":\"" + description
                + "\",\"vendor\":\"Meta\",\"reference\":\"INV-1\",\"expenseDate\":\"" + date + "\"}";
    }

    @Test
    void expensesBudgetsAndProfitAndLoss() throws Exception {
        String merchant = "Bearer " + TestTokens.merchant(70);
        String otherMerchant = "Bearer " + TestTokens.merchant(71);
        String customer = "Bearer " + TestTokens.customer(7);
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        String today = LocalDate.now(ZoneOffset.UTC).toString();

        mockMvc.perform(get("/api/merchant/expenses").header("Authorization", customer))
                .andExpect(status().isForbidden());

        String budgets = mockMvc.perform(put("/api/merchant/budgets").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + month + "\",\"category\":\"MARKETING\",\"amount\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBudget").value(1000.0))
                .andExpect(jsonPath("$.lines[0].status").value("ON_TRACK"))
                .andReturn().getResponse().getContentAsString();
        int budgetId = JsonPath.read(budgets, "$.lines[0].id");
        mockMvc.perform(put("/api/merchant/budgets").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-13\",\"category\":\"MARKETING\",\"amount\":1000}"))
                .andExpect(status().isBadRequest());

        // 850 of a 1,000 marketing budget -> "almost used" alert
        mockMvc.perform(post("/api/merchant/expenses").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON).content(expense("MARKETING", "850", "Instagram ads", today)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryLabel").value("Marketing and advertising"));
        verify(notifications, timeout(2000)).toUser(any(), eq(70L), eq("BUDGET_ALERT"),
                contains("Budget almost used"), contains("85.0%"));

        String rent = mockMvc.perform(post("/api/merchant/expenses").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON).content(expense("RENT", "300", "Warehouse rent", today)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int rentId = JsonPath.read(rent, "$.id");
        mockMvc.perform(put("/api/merchant/expenses/" + rentId).header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON).content(expense("RENT", "400", "Warehouse rent", today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(400.0));
        mockMvc.perform(put("/api/merchant/expenses/" + rentId).header("Authorization", otherMerchant)
                        .contentType(MediaType.APPLICATION_JSON).content(expense("RENT", "1", "Hacked", today)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/merchant/expenses").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expense("RENT", "10", "Future", LocalDate.now(ZoneOffset.UTC).plusDays(5).toString())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/merchant/expenses").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"RENT\",\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists());

        mockMvc.perform(get("/api/merchant/expenses").param("category", "RENT").header("Authorization", merchant))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/merchant/expenses").param("search", "instagram").header("Authorization", merchant))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].vendor").value("Meta"));
        mockMvc.perform(get("/api/merchant/expenses").header("Authorization", otherMerchant))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/merchant/expenses/categories").header("Authorization", merchant))
                .andExpect(jsonPath("$.length()").value(12));

        mockMvc.perform(get("/api/merchant/budgets").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent").value(1250.0))
                .andExpect(jsonPath("$.unbudgetedSpending").value(400.0))
                .andExpect(jsonPath("$.lines[0].category").value("RENT"))
                .andExpect(jsonPath("$.lines[0].status").value("NO_BUDGET"))
                .andExpect(jsonPath("$.lines[1].category").value("MARKETING"))
                .andExpect(jsonPath("$.lines[1].status").value("WARNING"))
                .andExpect(jsonPath("$.lines[1].percentUsed").value(85.0));

        // a 1,000 sale paid by card: the platform keeps 5% = 50
        mockMvc.perform(post("/internal/payments").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":900,\"orderNumber\":\"ORD-900\",\"userId\":7,\"merchantId\":70,"
                                + "\"amount\":1000.00,\"currency\":\"AED\",\"method\":\"UPI\",\"upiId\":\"kush@okbank\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        when(orderClient.salesSummary(eq(70L), any(), any())).thenReturn(new SalesSummary(new BigDecimal("10000.00"),
                new BigDecimal("6000.00"), new BigDecimal("4000.00"), 4, List.of(new MonthAmounts(month,
                new BigDecimal("10000.00"), new BigDecimal("6000.00"), 4))));
        mockMvc.perform(get("/api/merchant/finance/profit-loss").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(10000.0))
                .andExpect(jsonPath("$.grossProfit").value(4000.0))
                .andExpect(jsonPath("$.grossMarginPercent").value(40.0))
                .andExpect(jsonPath("$.platformFees").value(50.0))
                .andExpect(jsonPath("$.operatingExpenses").value(1250.0))
                .andExpect(jsonPath("$.netProfit").value(2700.0))
                .andExpect(jsonPath("$.netMarginPercent").value(27.0))
                .andExpect(jsonPath("$.result").value("PROFIT"))
                .andExpect(jsonPath("$.months.length()").value(6))
                .andExpect(jsonPath("$.months[5].month").value(month))
                .andExpect(jsonPath("$.months[5].netProfit").value(2700.0))
                .andExpect(jsonPath("$.expensesByCategory[0].category").value("MARKETING"));
        mockMvc.perform(get("/api/merchant/finance/profit-loss").param("from", today).param("to", "2020-01-01")
                        .header("Authorization", merchant))
                .andExpect(status().isBadRequest());

        byte[] pdf = mockMvc.perform(get("/api/merchant/reports/profit-loss/pdf").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        byte[] xlsx = mockMvc.perform(get("/api/merchant/reports/profit-loss/xlsx").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
        mockMvc.perform(get("/api/merchant/reports/profit-loss/csv").header("Authorization", merchant))
                .andExpect(status().isBadRequest());

        String next = YearMonth.now(ZoneOffset.UTC).plusMonths(1).toString();
        mockMvc.perform(post("/api/merchant/budgets/copy").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromMonth\":\"" + month + "\",\"toMonth\":\"" + next + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(next))
                .andExpect(jsonPath("$.lines[0].budget").value(1000.0))
                .andExpect(jsonPath("$.lines[0].spent").value(0.0));
        mockMvc.perform(delete("/api/merchant/budgets/" + budgetId).header("Authorization", otherMerchant))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/merchant/budgets/" + budgetId).header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBudget").value(0.0));
        mockMvc.perform(delete("/api/merchant/expenses/" + rentId).header("Authorization", merchant))
                .andExpect(status().isNoContent());
    }

    @Test
    void exceedingTheBudgetSendsAnAlert() throws Exception {
        String merchant = "Bearer " + TestTokens.merchant(80);
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        mockMvc.perform(put("/api/merchant/budgets").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + month + "\",\"category\":\"SHIPPING\",\"amount\":200}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/merchant/expenses").header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON).content(expense("SHIPPING", "250", "Courier", today)))
                .andExpect(status().isCreated());
        verify(notifications, timeout(2000)).toUser(any(), eq(80L), eq("BUDGET_ALERT"),
                contains("Budget exceeded"), anyString());
        mockMvc.perform(get("/api/merchant/budgets").header("Authorization", merchant))
                .andExpect(jsonPath("$.lines[0].status").value("OVER"))
                .andExpect(jsonPath("$.lines[0].remaining").value(-50.0));
    }
}
