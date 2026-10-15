package com.enterprise.payment.payment;

import com.enterprise.payment.payment.PaymentDtos.PaymentResponse;
import com.enterprise.payment.payment.PaymentDtos.RefundRequest;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Called by the order service (internal key required; not routed by the API gateway). */
@RestController
@RequestMapping("/internal/payments")
@Tag(name = "Internal - payments")
public class InternalPaymentController {

    private final PaymentService paymentService;

    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/order/{orderId}/refund")
    @Operation(summary = "Refund (or void cash on delivery) when an order is cancelled")
    public PaymentResponse refund(@PathVariable Long orderId, @Valid @RequestBody(required = false) RefundRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return paymentService.refund(orderId, request == null ? null : request.reason(), user);
    }

    @PostMapping("/order/{orderId}/capture")
    @Operation(summary = "Mark cash on delivery as collected (the seller, when the order is delivered)")
    public PaymentResponse capture(@PathVariable Long orderId, @AuthenticationPrincipal AuthenticatedUser user) {
        return paymentService.capture(orderId, user);
    }
}
