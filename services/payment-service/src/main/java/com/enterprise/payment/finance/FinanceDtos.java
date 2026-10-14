package com.enterprise.payment.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FinanceDtos {

    static final String MONTH_PATTERN = "\\d{4}-(0[1-9]|1[0-2])";

    private FinanceDtos() {
    }

    public record ExpenseRequest(
            @NotNull ExpenseCategory category,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 200) String description,
            @Size(max = 100) String vendor,
            @Size(max = 60) String reference,
            @NotNull LocalDate expenseDate) {
    }

    public record ExpenseResponse(Long id, String category, String categoryLabel, BigDecimal amount, String currency,
                                  String description, String vendor, String reference, LocalDate expenseDate,
                                  String createdBy, Instant createdAt, Instant updatedAt) {

        static ExpenseResponse from(Expense e) {
            return new ExpenseResponse(e.getId(), e.getCategory().name(), e.getCategory().label(), e.getAmount(),
                    e.getCurrency(), e.getDescription(), e.getVendor(), e.getReference(), e.getExpenseDate(),
                    e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
        }
    }

    public record CategoryOption(String value, String label) {
    }

    public record BudgetRequest(
            @NotNull @Pattern(regexp = MONTH_PATTERN, message = "must be a month like 2026-10") String month,
            @NotNull ExpenseCategory category,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record CopyBudgetsRequest(
            @NotNull @Pattern(regexp = MONTH_PATTERN, message = "must be a month like 2026-10") String fromMonth,
            @NotNull @Pattern(regexp = MONTH_PATTERN, message = "must be a month like 2026-10") String toMonth) {
    }

    /** status: ON_TRACK (below 80%), WARNING (80-100%), OVER (above the budget), NO_BUDGET (spent without a budget). */
    public record BudgetLine(Long id, String category, String categoryLabel, BigDecimal budget, BigDecimal spent,
                             BigDecimal remaining, BigDecimal percentUsed, String status) {
    }

    public record BudgetOverview(String month, String currency, BigDecimal totalBudget, BigDecimal totalSpent,
                                 BigDecimal remaining, BigDecimal percentUsed, BigDecimal unbudgetedSpending,
                                 List<BudgetLine> lines) {
    }

    public record CategoryAmount(String category, String label, long entries, BigDecimal amount,
                                 BigDecimal percentOfExpenses) {
    }

    public record MonthLine(String month, long orders, BigDecimal revenue, BigDecimal costOfGoodsSold,
                            BigDecimal grossProfit, BigDecimal platformFees, BigDecimal operatingExpenses,
                            BigDecimal netProfit) {
    }

    /**
     * Profit &amp; loss statement: revenue - cost of goods sold = gross profit;
     * gross profit - platform fees - operating expenses = net profit (negative = loss).
     */
    public record ProfitAndLoss(String from, String to, String currency, long orders, BigDecimal revenue,
                                BigDecimal costOfGoodsSold, BigDecimal grossProfit, BigDecimal grossMarginPercent,
                                BigDecimal platformFees, BigDecimal feeRatePercent, BigDecimal operatingExpenses,
                                BigDecimal netProfit, BigDecimal netMarginPercent, String result,
                                List<CategoryAmount> expensesByCategory, List<MonthLine> months) {
    }
}
