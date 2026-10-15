package com.enterprise.payment.payment;

import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.payment.PaymentDtos.CheckoutPaymentRequest;
import com.enterprise.payment.payment.PaymentDtos.CheckoutPaymentResult;
import com.enterprise.payment.payment.PaymentDtos.PayOrderRequest;
import com.enterprise.payment.payment.PaymentDtos.PaymentResponse;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentQueryService queries;

    public PaymentController(PaymentService paymentService, PaymentQueryService queries) {
        this.paymentService = paymentService;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Pay an order with CARD, UPI or COD (cash on delivery); confirms the order on success")
    public PaymentResponse pay(@Valid @RequestBody PayOrderRequest request,
                               @AuthenticationPrincipal AuthenticatedUser customer) {
        return paymentService.pay(customer, request);
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Pay all orders of a checkout (one order per seller) with the same payment method")
    public List<CheckoutPaymentResult> payCheckout(@Valid @RequestBody CheckoutPaymentRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser customer) {
        return paymentService.payCheckout(customer, request);
    }

    @GetMapping
    @Operation(summary = "My payments, newest first")
    public PageResponse<PaymentResponse> mine(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.mine(user, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Payment details (the customer, the seller of the order or the administrator)")
    public PaymentResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.get(id, user);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "All payment attempts of an order")
    public List<PaymentResponse> forOrder(@PathVariable Long orderId, @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.forOrder(orderId, user);
    }
}
