package com.enterprise.order.order;

import com.enterprise.order.order.OrderDtos.PaymentIntent;
import com.enterprise.order.order.OrderDtos.PaymentOutcomeRequest;
import com.enterprise.order.order.OrderDtos.PaymentOutcomeResponse;
import com.enterprise.order.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API used by the payment service (internal key required, not routed by the API gateway):
 * <pre>
 * POST /api/payments (payment service) -> payment-intent -> charge (strategy) -> payment-outcome -> order confirmed
 * </pre>
 */
@RestController
@RequestMapping("/internal/orders")
@Tag(name = "Internal - payments")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/{orderId}/payment-intent")
    @Operation(summary = "Validate that the customer can pay this order now and lock it for one payment attempt")
    public PaymentIntent paymentIntent(@PathVariable Long orderId, @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.startPayment(user, orderId);
    }

    @PostMapping("/{orderId}/payment-outcome")
    @Operation(summary = "Result of a payment: confirm the order (stock sold) or tell the caller to refund")
    public PaymentOutcomeResponse paymentOutcome(@PathVariable Long orderId,
                                                 @Valid @RequestBody PaymentOutcomeRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.applyPaymentOutcome(user, orderId, request);
    }
}
