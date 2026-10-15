package com.enterprise.payment.finance;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.finance.FinanceDtos.ExpenseRequest;
import com.enterprise.payment.finance.FinanceDtos.ExpenseResponse;
import com.enterprise.payment.security.AuthenticatedUser;
import com.enterprise.payment.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private static final BigDecimal WARNING_LEVEL = new BigDecimal("0.80");

    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final ApplicationEventPublisher events;

    public ExpenseService(ExpenseRepository expenses, BudgetRepository budgets, ApplicationEventPublisher events) {
        this.expenses = expenses;
        this.budgets = budgets;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(Long sellerId, ExpenseFilter filter, String sort, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort(sort));
        return PageResponse.from(expenses.findAll(filter.toSpecification(sellerId), pageable), ExpenseResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> export(Long sellerId, ExpenseFilter filter, String sort) {
        return expenses.findAll(filter.toSpecification(sellerId), PageRequest.of(0, 5000, sort(sort)))
                .map(ExpenseResponse::from).getContent();
    }

    /** newest (default), oldest, amount_desc, amount_asc. */
    static Sort sort(String sort) {
        Sort newest = Sort.by(Sort.Direction.DESC, "expenseDate").and(Sort.by(Sort.Direction.DESC, "id"));
        if (sort == null) {
            return newest;
        }
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "expenseDate").and(Sort.by(Sort.Direction.ASC, "id"));
            case "amount_desc" -> Sort.by(Sort.Direction.DESC, "amount").and(newest);
            case "amount_asc" -> Sort.by(Sort.Direction.ASC, "amount").and(newest);
            default -> newest;
        };
    }

    @Transactional
    public ExpenseResponse create(AuthenticatedUser seller, ExpenseRequest request) {
        validateDate(request.expenseDate());
        Expense expense = expenses.save(new Expense(seller.id(), request.category(), Money.of(request.amount()),
                "AED", request.description().trim(), trimToNull(request.vendor()), trimToNull(request.reference()),
                request.expenseDate(), seller.username()));
        checkBudget(seller, expense, BigDecimal.ZERO);
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public ExpenseResponse update(AuthenticatedUser seller, Long id, ExpenseRequest request) {
        validateDate(request.expenseDate());
        Expense expense = find(seller.id(), id);
        boolean sameBucket = expense.getCategory() == request.category()
                && YearMonth.from(expense.getExpenseDate()).equals(YearMonth.from(request.expenseDate()));
        BigDecimal previousAmount = expense.getAmount();
        expense.update(request.category(), Money.of(request.amount()), request.description().trim(),
                trimToNull(request.vendor()), trimToNull(request.reference()), request.expenseDate());
        checkBudget(seller, expense, sameBucket ? previousAmount : BigDecimal.ZERO);
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public void delete(Long sellerId, Long id) {
        expenses.delete(find(sellerId, id));
    }

    private Expense find(Long sellerId, Long id) {
        return expenses.findByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> ApiException.notFound("Expense " + id + " not found"));
    }

    /**
     * Compares the category's spending for the month before and after this expense and raises an alert when it
     * crosses 80% or 100% of the budget. {@code alreadyCounted} is the old amount when an expense is edited.
     */
    private void checkBudget(AuthenticatedUser seller, Expense expense, BigDecimal alreadyCounted) {
        YearMonth month = YearMonth.from(expense.getExpenseDate());
        budgets.findBySellerIdAndBudgetMonthAndCategory(seller.id(), month.toString(), expense.getCategory())
                .ifPresent(budget -> {
                    BigDecimal after = expenses.total(seller.id(), expense.getCategory(), month.atDay(1),
                            month.atEndOfMonth());
                    BigDecimal before = after.subtract(expense.getAmount()).add(alreadyCounted);
                    BigDecimal limit = budget.getAmount();
                    BigDecimal warning = limit.multiply(WARNING_LEVEL);
                    String title;
                    if (before.compareTo(limit) <= 0 && after.compareTo(limit) > 0) {
                        title = "Budget exceeded: " + expense.getCategory().label();
                    } else if (before.compareTo(warning) < 0 && after.compareTo(warning) >= 0) {
                        title = "Budget almost used: " + expense.getCategory().label();
                    } else {
                        return;
                    }
                    String message = Money.format(after) + " spent of the " + Money.format(limit) + " budget for "
                            + month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear()
                            + " (" + Money.percent(after, limit) + "%).";
                    events.publishEvent(new BudgetAlertEvent(seller.id(), title, message, CurrentUser.token()));
                });
    }

    private static void validateDate(LocalDate date) {
        // one day of slack: the seller may be ahead of UTC (Dubai is UTC+4)
        if (date.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1))) {
            throw ApiException.badRequest("The expense date cannot be in the future");
        }
        if (date.isBefore(LocalDate.of(2000, 1, 1))) {
            throw ApiException.badRequest("The expense date is too far in the past");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
