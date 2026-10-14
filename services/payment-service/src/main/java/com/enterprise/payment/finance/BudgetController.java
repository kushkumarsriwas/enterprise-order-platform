package com.enterprise.payment.finance;

import com.enterprise.payment.finance.FinanceDtos.BudgetOverview;
import com.enterprise.payment.finance.FinanceDtos.BudgetRequest;
import com.enterprise.payment.finance.FinanceDtos.CopyBudgetsRequest;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/budgets")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant - budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @Operation(summary = "Budget vs actual spending per category for a month (default: this month)")
    public BudgetOverview overview(@RequestParam(required = false) String month,
                                   @AuthenticationPrincipal AuthenticatedUser merchant) {
        return budgetService.overview(merchant.id(), month == null || month.isBlank()
                ? YearMonth.now(ZoneOffset.UTC) : BudgetService.month(month));
    }

    @PutMapping
    @Operation(summary = "Set the budget of a category for a month")
    public BudgetOverview set(@Valid @RequestBody BudgetRequest request,
                              @AuthenticationPrincipal AuthenticatedUser merchant) {
        return budgetService.set(merchant.id(), request);
    }

    @PostMapping("/copy")
    @Operation(summary = "Copy all budgets of one month to another month")
    public BudgetOverview copy(@Valid @RequestBody CopyBudgetsRequest request,
                               @AuthenticationPrincipal AuthenticatedUser merchant) {
        return budgetService.copy(merchant.id(), request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a budget")
    public BudgetOverview delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser merchant) {
        return budgetService.delete(merchant.id(), id);
    }
}
