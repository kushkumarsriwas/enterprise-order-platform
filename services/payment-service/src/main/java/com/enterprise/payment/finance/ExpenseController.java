package com.enterprise.payment.finance;

import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.finance.FinanceDtos.CategoryOption;
import com.enterprise.payment.finance.FinanceDtos.ExpenseRequest;
import com.enterprise.payment.finance.FinanceDtos.ExpenseResponse;
import com.enterprise.payment.reporting.Report;
import com.enterprise.payment.reporting.ReportFiles;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/seller/expenses")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    @Operation(summary = "My expenses (filters: period, categories, amount range, text; sort by date or amount)")
    public PageResponse<ExpenseResponse> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(required = false) List<ExpenseCategory> category,
                                              @RequestParam(required = false) BigDecimal minAmount,
                                              @RequestParam(required = false) BigDecimal maxAmount,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "newest") String sort,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @AuthenticationPrincipal AuthenticatedUser seller) {
        return expenseService.list(seller.id(), new ExpenseFilter(from, to, category, minAmount, maxAmount, search),
                sort, page, size);
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Export the filtered expenses as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) List<ExpenseCategory> category,
                                         @RequestParam(required = false) BigDecimal minAmount,
                                         @RequestParam(required = false) BigDecimal maxAmount,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(defaultValue = "newest") String sort,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        List<ExpenseResponse> rows = expenseService.export(seller.id(),
                new ExpenseFilter(from, to, category, minAmount, maxAmount, search), sort);
        BigDecimal total = rows.stream().map(ExpenseResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Report report = new Report("Expenses - " + seller.displayName(),
                (from == null && to == null ? "All dates" : "Period " + (from == null ? "start" : from) + " to "
                        + (to == null ? "today" : to)) + " (amounts in AED)",
                List.of(new Report.Metric("Expenses", rows.size()), new Report.Metric("Total", total)),
                List.of(new Report.Table("Expenses",
                        List.of("Date", "Category", "Description", "Vendor", "Reference", "Amount"),
                        rows.stream().map(e -> List.<Object>of(e.expenseDate().toString(), e.categoryLabel(),
                                e.description(), e.vendor() == null ? "" : e.vendor(),
                                e.reference() == null ? "" : e.reference(), e.amount())).toList())));
        return ReportFiles.download(report, format, "expenses-" + LocalDate.now());
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
                                  @AuthenticationPrincipal AuthenticatedUser seller) {
        return expenseService.create(seller, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Correct an expense")
    public ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser seller) {
        return expenseService.update(seller, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an expense")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        expenseService.delete(seller.id(), id);
    }
}
