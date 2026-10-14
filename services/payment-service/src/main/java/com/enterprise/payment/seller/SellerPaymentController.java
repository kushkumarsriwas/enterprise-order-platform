package com.enterprise.payment.merchant;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.payment.PaymentDtos.MerchantPaymentResponse;
import com.enterprise.payment.payment.PaymentDtos.PaymentSummary;
import com.enterprise.payment.payment.PaymentService;
import com.enterprise.payment.payment.PaymentStatus;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/payments")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant - payments received")
public class MerchantPaymentController {

    private final PaymentService paymentService;

    public MerchantPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @Operation(summary = "Payments for my orders with the platform fee and my net earnings")
    public PageResponse<MerchantPaymentResponse> list(@RequestParam(required = false) PaymentStatus status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @AuthenticationPrincipal AuthenticatedUser merchant) {
        return paymentService.merchantPayments(merchant.id(), status, page, size);
    }

    @GetMapping("/summary")
    @Operation(summary = "Gross sales, cash still to collect, platform fees and net earnings for a period")
    public PaymentSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @AuthenticationPrincipal AuthenticatedUser merchant) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("'from' must be before 'to'");
        }
        return paymentService.merchantSummary(merchant.id(), start, end);
    }
}
