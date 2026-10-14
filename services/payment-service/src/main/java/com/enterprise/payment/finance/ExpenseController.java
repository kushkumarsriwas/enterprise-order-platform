package com.enterprise.payment.finance;

import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.finance.FinanceDtos.CategoryOption;
import com.enterprise.payment.finance.FinanceDtos.ExpenseRequest;
import com.enterprise.payment.finance.FinanceDtos.ExpenseResponse;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/expenses")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant - expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    @Operation(summary = "My expenses, newest first (filter by period, category or text)")
    public PageResponse<ExpenseResponse> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(required = false) ExpenseCategory category,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @AuthenticationPrincipal AuthenticatedUser merchant) {
        return expenseService.list(merchant.id(), from, to, category, search, page, size);
    }

    @GetMapping("/categories")
    @Operation(summary = "Expense categories")
    public List<CategoryOption> categories() {
        return Arrays.stream(ExpenseCategory.values()).map(c -> new CategoryOption(c.name(), c.label())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an expense (alerts me when a budget reaches 80% or is exceeded)")
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser merchant) {
        return expenseService.create(merchant, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Correct an expense")
    public ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser merchant) {
        return expenseService.update(merchant, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an expense")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser merchant) {
        expenseService.delete(merchant.id(), id);
    }
}
