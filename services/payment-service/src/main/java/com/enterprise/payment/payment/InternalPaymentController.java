package com.enterprise.payment.payment;

import com.enterprise.payment.payment.PaymentDtos.ChargeRequest;
import com.enterprise.payment.payment.PaymentDtos.PaymentResponse;
import com.enterprise.payment.payment.PaymentDtos.RefundRequest;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Called by the order service (saga orchestrator). Not routed by the API gateway. */
@RestController
@RequestMapping("/internal/payments")
@Tag(name = "Internal - payments")
public class InternalPaymentController {

    private final PaymentService paymentService;

    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Charge an order (idempotent per order)")
    public PaymentResponse charge(@Valid @RequestBody ChargeRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return paymentService.charge(request, user);
    }

    @PostMapping("/order/{orderId}/refund")
    @Operation(summary = "Refund (or void cash on delivery) when an order is cancelled")
    public PaymentResponse refund(@PathVariable Long orderId, @Valid @RequestBody(required = false) RefundRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return paymentService.refund(orderId, request == null ? null : request.reason(), user);
    }

    @PostMapping("/order/{orderId}/capture")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark cash on delivery as collected (admin)")
    public PaymentResponse capture(@PathVariable Long orderId) {
        return paymentService.capture(orderId);
    }
}
