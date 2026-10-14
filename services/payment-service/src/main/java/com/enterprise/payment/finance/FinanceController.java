package com.enterprise.payment.finance;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.finance.FinanceDtos.CategoryAmount;
import com.enterprise.payment.finance.FinanceDtos.ExpenseResponse;
import com.enterprise.payment.finance.FinanceDtos.MonthLine;
import com.enterprise.payment.finance.FinanceDtos.ProfitAndLoss;
import com.enterprise.payment.reporting.ExcelReportWriter;
import com.enterprise.payment.reporting.PdfReportWriter;
import com.enterprise.payment.reporting.Report;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - profit and loss")
public class FinanceController {

    private final ProfitAndLossService profitAndLoss;
    private final ExpenseRepository expenses;

    public FinanceController(ProfitAndLossService profitAndLoss, ExpenseRepository expenses) {
        this.profitAndLoss = profitAndLoss;
        this.expenses = expenses;
    }

    @GetMapping("/api/seller/finance/profit-loss")
    @Operation(summary = "Profit & loss statement with a monthly breakdown (default: the last 6 months)")
    public ProfitAndLoss statement(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @AuthenticationPrincipal AuthenticatedUser seller) {
        LocalDate[] period = period(from, to);
        return profitAndLoss.statement(seller.id(), period[0], period[1]);
    }

    @GetMapping("/api/seller/reports/profit-loss/{format}")
    @Operation(summary = "Profit & loss report with all expenses, as xlsx or pdf")
    public ResponseEntity<byte[]> report(@PathVariable String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        boolean excel = "xlsx".equalsIgnoreCase(format);
        if (!excel && !"pdf".equalsIgnoreCase(format)) {
            throw ApiException.badRequest("Format must be xlsx or pdf");
        }
        LocalDate[] period = period(from, to);
        ProfitAndLoss pl = profitAndLoss.statement(seller.id(), period[0], period[1]);
        List<ExpenseResponse> expenseList = expenses
                .findBySellerIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(seller.id(), period[0], period[1])
                .stream().map(ExpenseResponse::from).toList();

        List<List<Object>> statement = new ArrayList<>();
        statement.add(List.of("Revenue (sales)", pl.revenue(), percentOfRevenue(pl.revenue(), pl)));
        statement.add(List.of("Cost of goods sold", pl.costOfGoodsSold().negate(), percentOfRevenue(pl.costOfGoodsSold(), pl)));
        statement.add(List.of("Gross profit", pl.grossProfit(), pl.grossMarginPercent()));
        statement.add(List.of("Platform fees (" + pl.feeRatePercent() + "%)", pl.platformFees().negate(),
                percentOfRevenue(pl.platformFees(), pl)));
        for (CategoryAmount category : pl.expensesByCategory()) {
            statement.add(List.of("   " + category.label(), category.amount().negate(),
                    percentOfRevenue(category.amount(), pl)));
        }
        statement.add(List.of("Operating expenses", pl.operatingExpenses().negate(),
                percentOfRevenue(pl.operatingExpenses(), pl)));
        statement.add(List.of(pl.netProfit().signum() < 0 ? "Net loss" : "Net profit", pl.netProfit(),
                pl.netMarginPercent()));

        Report report = new Report(
                "Profit and loss - " + seller.displayName(),
                "Period " + pl.from() + " to " + pl.to() + " (amounts in AED; costs shown as negative numbers)",
                List.of(new Report.Metric("Revenue", pl.revenue()),
                        new Report.Metric("Gross profit", pl.grossProfit()),
                        new Report.Metric(pl.netProfit().signum() < 0 ? "Net loss" : "Net profit", pl.netProfit()),
                        new Report.Metric("Cost of goods sold", pl.costOfGoodsSold()),
                        new Report.Metric("Platform fees", pl.platformFees()),
                        new Report.Metric("Operating expenses", pl.operatingExpenses()),
                        new Report.Metric("Gross margin %", pl.grossMarginPercent()),
                        new Report.Metric("Net margin %", pl.netMarginPercent()),
                        new Report.Metric("Orders sold", pl.orders())),
                List.of(new Report.Table("Statement", List.of("Line item", "Amount", "% of revenue"), statement),
                        new Report.Table("Monthly breakdown",
                                List.of("Month", "Orders", "Revenue", "Cost of goods", "Gross profit", "Fees",
                                        "Expenses", "Net profit"),
                                pl.months().stream().map(FinanceController::monthRow).toList()),
                        new Report.Table("Expenses",
                                List.of("Date", "Category", "Description", "Vendor", "Reference", "Amount"),
                                expenseList.stream().map(e -> List.<Object>of(e.expenseDate().toString(),
                                        e.categoryLabel(), e.description(), e.vendor() == null ? "" : e.vendor(),
                                        e.reference() == null ? "" : e.reference(), e.amount())).toList())));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(excel ? ExcelReportWriter.CONTENT_TYPE : PdfReportWriter.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("profit-and-loss-" + pl.from() + "-to-" + pl.to() + (excel ? ".xlsx" : ".pdf"))
                        .build().toString())
                .body(excel ? ExcelReportWriter.write(report) : PdfReportWriter.write(report));
    }

    private static List<Object> monthRow(MonthLine m) {
        return List.of(m.month(), m.orders(), m.revenue(), m.costOfGoodsSold(), m.grossProfit(), m.platformFees(),
                m.operatingExpenses(), m.netProfit());
    }

    private static BigDecimal percentOfRevenue(BigDecimal amount, ProfitAndLoss pl) {
        return Money.percent(amount, pl.revenue());
    }

    private static LocalDate[] period(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? YearMonth.from(end).minusMonths(5).atDay(1) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("'from' must be before 'to'");
        }
        if (start.plusYears(2).isBefore(end)) {
            throw ApiException.badRequest("The period can be at most 2 years");
        }
        return new LocalDate[] {start, end};
    }
}
