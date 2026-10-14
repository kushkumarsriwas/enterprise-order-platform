package com.enterprise.payment.admin;

import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.finance.FinanceDtos.ProfitAndLoss;
import com.enterprise.payment.finance.ProfitAndLossService;
import com.enterprise.payment.payment.PaymentDtos.AdminPaymentResponse;
import com.enterprise.payment.payment.PaymentDtos.PaymentSummary;
import com.enterprise.payment.payment.PaymentDtos.PlatformSummary;
import com.enterprise.payment.payment.PaymentFilter;
import com.enterprise.payment.payment.PaymentMethod;
import com.enterprise.payment.payment.PaymentQueryService;
import com.enterprise.payment.payment.PaymentStatus;
import com.enterprise.payment.reporting.PaymentReports;
import com.enterprise.payment.reporting.ReportFiles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Nexora Control Center: every transaction on the marketplace and the money flows of every store. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - payments and finance")
public class AdminPaymentController {

    private final PaymentQueryService queries;
    private final ProfitAndLossService profitAndLoss;

    public AdminPaymentController(PaymentQueryService queries, ProfitAndLossService profitAndLoss) {
        this.queries = queries;
        this.profitAndLoss = profitAndLoss;
    }

    @GetMapping("/payments")
    @Operation(summary = "All transactions (filters: store, customer, status, method, dates, amount, text)")
    public PageResponse<AdminPaymentResponse> list(@RequestParam(required = false) Long sellerId,
                                                   @RequestParam(required = false) Long customerId,
                                                   @RequestParam(required = false) List<PaymentStatus> status,
                                                   @RequestParam(required = false) List<PaymentMethod> method,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(required = false) BigDecimal minAmount,
                                                   @RequestParam(required = false) BigDecimal maxAmount,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "newest") String sort,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size) {
        return queries.forAdmin(new PaymentFilter(sellerId, customerId, status, method, from, to, minAmount,
                maxAmount, q), sort, page, size);
    }

    @GetMapping("/payments/{id}")
    @Operation(summary = "One transaction with provider, reference, fee and seller net")
    public AdminPaymentResponse get(@PathVariable Long id) {
        return queries.getForAdmin(id);
    }

    @GetMapping("/payments/summary")
    @Operation(summary = "Payment volume, platform revenue (commission), refunds, failures, methods, top stores")
    public PlatformSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        PaymentQueryService.validateRange(start, end);
        return queries.platformSummary(start, end);
    }

    @GetMapping("/payments/export/{format}")
    @Operation(summary = "Export the filtered transactions as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) Long sellerId,
                                         @RequestParam(required = false) Long customerId,
                                         @RequestParam(required = false) List<PaymentStatus> status,
                                         @RequestParam(required = false) List<PaymentMethod> method,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) BigDecimal minAmount,
                                         @RequestParam(required = false) BigDecimal maxAmount,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "newest") String sort) {
        PaymentFilter filter = new PaymentFilter(sellerId, customerId, status, method, from, to, minAmount, maxAmount, q);
        return ReportFiles.download(PaymentReports.transactions("Nexora transactions",
                "All marketplace transactions matching the filters (amounts in AED)", queries.export(filter, sort),
                true), format, "nexora-transactions-" + LocalDate.now());
    }

    @GetMapping("/sellers/{sellerId}/earnings")
    @Operation(summary = "A store's earnings: gross sales, collected, fees and net for a period")
    public PaymentSummary sellerEarnings(@PathVariable Long sellerId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        PaymentQueryService.validateRange(start, end);
        return queries.sellerSummary(sellerId, start, end);
    }

    @GetMapping("/sellers/{sellerId}/profit-loss")
    @Operation(summary = "A store's profit & loss statement (sales, cost of goods, fees, expenses)")
    public ProfitAndLoss sellerProfitAndLoss(@PathVariable Long sellerId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? YearMonth.from(end).minusMonths(5).atDay(1) : from;
        PaymentQueryService.validateRange(start, end);
        return profitAndLoss.statement(sellerId, start, end);
    }
}
