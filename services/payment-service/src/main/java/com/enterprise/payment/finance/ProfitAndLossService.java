package com.enterprise.payment.finance;

import com.enterprise.payment.client.OrderClient;
import com.enterprise.payment.client.OrderClient.MonthAmounts;
import com.enterprise.payment.client.OrderClient.SalesSummary;
import com.enterprise.payment.finance.FinanceDtos.CategoryAmount;
import com.enterprise.payment.finance.FinanceDtos.MonthLine;
import com.enterprise.payment.finance.FinanceDtos.ProfitAndLoss;
import com.enterprise.payment.payment.PaymentRepository;
import com.enterprise.payment.payment.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Builds the profit &amp; loss statement from three sources: sales and cost of goods sold (order service),
 * platform fees (payments in this service) and operating expenses (this service).
 */
@Service
public class ProfitAndLossService {

    private final OrderClient orderClient;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final PaymentService paymentService;

    public ProfitAndLossService(OrderClient orderClient, PaymentRepository payments, ExpenseRepository expenses,
                                PaymentService paymentService) {
        this.orderClient = orderClient;
        this.payments = payments;
        this.expenses = expenses;
        this.paymentService = paymentService;
    }

    /** No surrounding transaction on purpose: the remote call must not hold a database connection. */
    public ProfitAndLoss statement(Long merchantId, LocalDate from, LocalDate to) {
        SalesSummary sales = orderClient.salesSummary(merchantId, from, to);

        Map<String, BigDecimal> feesByMonth = new HashMap<>();
        for (Object[] row : payments.monthlyFees(merchantId, PaymentService.SETTLED, PaymentService.startOf(from),
                PaymentService.endOf(to))) {
            feesByMonth.merge(monthKey(row[0], row[1]), Money.of(row[2]), BigDecimal::add);
        }
        Map<String, BigDecimal> expensesByMonth = new HashMap<>();
        for (Object[] row : expenses.totalsByMonth(merchantId, from, to)) {
            expensesByMonth.merge(monthKey(row[0], row[1]), Money.of(row[2]), BigDecimal::add);
        }
        Map<String, MonthAmounts> salesByMonth = new HashMap<>();
        if (sales.months() != null) {
            sales.months().forEach(month -> salesByMonth.put(month.month(), month));
        }

        List<MonthLine> months = new ArrayList<>();
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal operating = BigDecimal.ZERO;
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            String key = month.toString();
            MonthAmounts sold = salesByMonth.get(key);
            BigDecimal revenue = sold == null ? Money.of(0) : Money.of(sold.revenue());
            BigDecimal cogs = sold == null ? Money.of(0) : Money.of(sold.costOfGoodsSold());
            BigDecimal monthFees = feesByMonth.getOrDefault(key, Money.of(0));
            BigDecimal monthExpenses = expensesByMonth.getOrDefault(key, Money.of(0));
            BigDecimal gross = revenue.subtract(cogs);
            months.add(new MonthLine(key, sold == null ? 0 : sold.orders(), revenue, cogs, gross, monthFees,
                    monthExpenses, gross.subtract(monthFees).subtract(monthExpenses)));
            fees = fees.add(monthFees);
            operating = operating.add(monthExpenses);
        }

        List<CategoryAmount> byCategory = new ArrayList<>();
        for (Object[] row : expenses.totalsByCategory(merchantId, from, to)) {
            ExpenseCategory category = (ExpenseCategory) row[0];
            BigDecimal amount = Money.of(row[2]);
            byCategory.add(new CategoryAmount(category.name(), category.label(), ((Number) row[1]).longValue(),
                    amount, Money.percent(amount, operating)));
        }

        BigDecimal revenue = Money.of(sales.revenue());
        BigDecimal cogs = Money.of(sales.costOfGoodsSold());
        BigDecimal grossProfit = revenue.subtract(cogs);
        BigDecimal netProfit = grossProfit.subtract(fees).subtract(operating);
        String result = netProfit.signum() > 0 ? "PROFIT" : netProfit.signum() < 0 ? "LOSS" : "BREAK_EVEN";
        return new ProfitAndLoss(from.toString(), to.toString(), "AED", sales.orders(), revenue, cogs, grossProfit,
                Money.percent(grossProfit, revenue), Money.of(fees), paymentService.feeRatePercent(),
                Money.of(operating), Money.of(netProfit), Money.percent(netProfit, revenue), result, byCategory,
                months);
    }

    private static String monthKey(Object year, Object month) {
        return YearMonth.of(((Number) year).intValue(), ((Number) month).intValue()).toString();
    }
}
