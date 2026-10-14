package com.enterprise.payment.finance;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.finance.FinanceDtos.ExpenseRequest;
import com.enterprise.payment.finance.FinanceDtos.ExpenseResponse;
import com.enterprise.payment.security.AuthenticatedUser;
import com.enterprise.payment.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    public PageResponse<ExpenseResponse> list(Long merchantId, LocalDate from, LocalDate to, ExpenseCategory category,
                                              String search, int page, int size) {
        Specification<Expense> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("merchantId"), merchantId));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (search != null && !search.isBlank()) {
                // explicit escape character: user input like "50%" is matched literally on every database
                String like = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                        .replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("description")), like, '\\'),
                        cb.like(cb.lower(root.get("vendor")), like, '\\'),
                        cb.like(cb.lower(root.get("reference")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "expenseDate").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(expenses.findAll(spec, pageable), ExpenseResponse::from);
    }

    @Transactional
    public ExpenseResponse create(AuthenticatedUser merchant, ExpenseRequest request) {
        validateDate(request.expenseDate());
        Expense expense = expenses.save(new Expense(merchant.id(), request.category(), Money.of(request.amount()),
                "AED", request.description().trim(), trimToNull(request.vendor()), trimToNull(request.reference()),
                request.expenseDate(), merchant.username()));
        checkBudget(merchant, expense, BigDecimal.ZERO);
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public ExpenseResponse update(AuthenticatedUser merchant, Long id, ExpenseRequest request) {
        validateDate(request.expenseDate());
        Expense expense = find(merchant.id(), id);
        boolean sameBucket = expense.getCategory() == request.category()
                && YearMonth.from(expense.getExpenseDate()).equals(YearMonth.from(request.expenseDate()));
        BigDecimal previousAmount = expense.getAmount();
        expense.update(request.category(), Money.of(request.amount()), request.description().trim(),
                trimToNull(request.vendor()), trimToNull(request.reference()), request.expenseDate());
        checkBudget(merchant, expense, sameBucket ? previousAmount : BigDecimal.ZERO);
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public void delete(Long merchantId, Long id) {
        expenses.delete(find(merchantId, id));
    }

    private Expense find(Long merchantId, Long id) {
        return expenses.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> ApiException.notFound("Expense " + id + " not found"));
    }

    /**
     * Compares the category's spending for the month before and after this expense and raises an alert when it
     * crosses 80% or 100% of the budget. {@code alreadyCounted} is the old amount when an expense is edited.
     */
    private void checkBudget(AuthenticatedUser merchant, Expense expense, BigDecimal alreadyCounted) {
        YearMonth month = YearMonth.from(expense.getExpenseDate());
        budgets.findByMerchantIdAndBudgetMonthAndCategory(merchant.id(), month.toString(), expense.getCategory())
                .ifPresent(budget -> {
                    BigDecimal after = expenses.total(merchant.id(), expense.getCategory(), month.atDay(1),
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
                    events.publishEvent(new BudgetAlertEvent(merchant.id(), title, message, CurrentUser.token()));
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
