package com.enterprise.payment.seller;

import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.payment.PaymentDtos.PaymentSummary;
import com.enterprise.payment.payment.PaymentDtos.SellerPaymentResponse;
import com.enterprise.payment.payment.PaymentFilter;
import com.enterprise.payment.payment.PaymentMethod;
import com.enterprise.payment.payment.PaymentQueryService;
import com.enterprise.payment.payment.PaymentStatus;
import com.enterprise.payment.reporting.PaymentReports;
import com.enterprise.payment.reporting.ReportFiles;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/payments")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - payments received")
public class SellerPaymentController {

    private final PaymentQueryService queries;

    public SellerPaymentController(PaymentQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    @Operation(summary = "Payments for my orders with platform fee and net earnings (filters: status, method, "
            + "dates, amount, text)")
    public PageResponse<SellerPaymentResponse> list(@RequestParam(required = false) List<PaymentStatus> status,
                                                    @RequestParam(required = false) List<PaymentMethod> method,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                    @RequestParam(required = false) BigDecimal minAmount,
                                                    @RequestParam(required = false) BigDecimal maxAmount,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "newest") String sort,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @AuthenticationPrincipal AuthenticatedUser seller) {
        return queries.forSeller(new PaymentFilter(seller.id(), null, status, method, from, to, minAmount, maxAmount,
                q), sort, page, size);
    }

    @GetMapping("/summary")
    @Operation(summary = "Gross sales, collected, cash still to collect, platform fees, net earnings and daily trend")
    public PaymentSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @AuthenticationPrincipal AuthenticatedUser seller) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        PaymentQueryService.validateRange(start, end);
        return queries.sellerSummary(seller.id(), start, end);
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Export my filtered payments as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) List<PaymentStatus> status,
                                         @RequestParam(required = false) List<PaymentMethod> method,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) BigDecimal minAmount,
                                         @RequestParam(required = false) BigDecimal maxAmount,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "newest") String sort,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        PaymentFilter filter = new PaymentFilter(seller.id(), null, status, method, from, to, minAmount, maxAmount, q);
        return ReportFiles.download(PaymentReports.transactions("Payments - " + seller.displayName(),
                "Payments received for my orders (amounts in AED)", queries.export(filter, sort), false), format,
                "payments-" + LocalDate.now());
    }
}
