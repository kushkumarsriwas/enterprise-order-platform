package com.enterprise.payment.finance;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.finance.FinanceDtos.BudgetLine;
import com.enterprise.payment.finance.FinanceDtos.BudgetOverview;
import com.enterprise.payment.finance.FinanceDtos.BudgetRequest;
import com.enterprise.payment.finance.FinanceDtos.CopyBudgetsRequest;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monthly budgets per expense category, compared with what was actually spent. */
@Service
public class BudgetService {

    private static final BigDecimal WARNING_PERCENT = BigDecimal.valueOf(80);
    private static final BigDecimal FULL_PERCENT = BigDecimal.valueOf(100);

    private final BudgetRepository budgets;
    private final ExpenseRepository expenses;

    public BudgetService(BudgetRepository budgets, ExpenseRepository expenses) {
        this.budgets = budgets;
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public BudgetOverview overview(Long merchantId, YearMonth month) {
        Map<ExpenseCategory, BigDecimal> spent = new EnumMap<>(ExpenseCategory.class);
        for (Object[] row : expenses.totalsByCategory(merchantId, month.atDay(1), month.atEndOfMonth())) {
            spent.put((ExpenseCategory) row[0], Money.of(row[2]));
        }
        List<BudgetLine> lines = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        for (Budget budget : budgets.findByMerchantIdAndBudgetMonth(merchantId, month.toString())) {
            BigDecimal used = spent.getOrDefault(budget.getCategory(), Money.of(0));
            BigDecimal percent = Money.percent(used, budget.getAmount());
            String status = percent.compareTo(FULL_PERCENT) > 0 ? "OVER"
                    : percent.compareTo(WARNING_PERCENT) >= 0 ? "WARNING" : "ON_TRACK";
            lines.add(new BudgetLine(budget.getId(), budget.getCategory().name(), budget.getCategory().label(),
                    budget.getAmount(), used, budget.getAmount().subtract(used), percent, status));
            totalBudget = totalBudget.add(budget.getAmount());
            spent.remove(budget.getCategory());
        }
        BigDecimal unbudgeted = BigDecimal.ZERO;
        for (Map.Entry<ExpenseCategory, BigDecimal> entry : spent.entrySet()) {
            lines.add(new BudgetLine(null, entry.getKey().name(), entry.getKey().label(), null, entry.getValue(), null,
                    null, "NO_BUDGET"));
            unbudgeted = unbudgeted.add(entry.getValue());
        }
        lines.sort(Comparator.comparing(line -> ExpenseCategory.valueOf(line.category())));
        BigDecimal totalSpent = lines.stream().map(BudgetLine::spent).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BudgetOverview(month.toString(), "AED", Money.of(totalBudget), Money.of(totalSpent),
                Money.of(totalBudget.subtract(totalSpent)), Money.percent(totalSpent, totalBudget),
                Money.of(unbudgeted), lines);
    }

    /** Creates the budget or changes its amount (one budget per month and category). */
    @Transactional
    public BudgetOverview set(Long merchantId, BudgetRequest request) {
        YearMonth month = month(request.month());
        BigDecimal amount = Money.of(request.amount());
        budgets.findByMerchantIdAndBudgetMonthAndCategory(merchantId, month.toString(), request.category())
                .ifPresentOrElse(existing -> existing.changeAmount(amount),
                        () -> budgets.save(new Budget(merchantId, month.toString(), request.category(), amount)));
        return overview(merchantId, month);
    }

    @Transactional
    public BudgetOverview delete(Long merchantId, Long id) {
        Budget budget = budgets.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> ApiException.notFound("Budget " + id + " not found"));
        budgets.delete(budget);
        budgets.flush();
        return overview(merchantId, month(budget.getBudgetMonth()));
    }

    /** Copies last month's budgets to a new month; categories that already have a budget are kept. */
    @Transactional
    public BudgetOverview copy(Long merchantId, CopyBudgetsRequest request) {
        YearMonth from = month(request.fromMonth());
        YearMonth to = month(request.toMonth());
        if (from.equals(to)) {
            throw ApiException.badRequest("Choose two different months");
        }
        List<Budget> source = budgets.findByMerchantIdAndBudgetMonth(merchantId, from.toString());
        if (source.isEmpty()) {
            throw ApiException.badRequest("There are no budgets in " + from + " to copy");
        }
        for (Budget budget : source) {
            if (budgets.findByMerchantIdAndBudgetMonthAndCategory(merchantId, to.toString(), budget.getCategory())
                    .isEmpty()) {
                budgets.save(new Budget(merchantId, to.toString(), budget.getCategory(), budget.getAmount()));
            }
        }
        return overview(merchantId, to);
    }

    static YearMonth month(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("Month must look like 2026-10");
        }
    }
}
