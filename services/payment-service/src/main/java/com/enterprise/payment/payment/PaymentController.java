package com.enterprise.payment.payment;

import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.payment.PaymentDtos.PaymentResponse;
import com.enterprise.payment.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @Operation(summary = "My payments (admins see all)")
    public PageResponse<PaymentResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return paymentService.list(user, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Payment details")
    public PaymentResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return paymentService.get(id, user);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "All payment attempts of an order")
    public List<PaymentResponse> forOrder(@PathVariable Long orderId, @AuthenticationPrincipal AuthenticatedUser user) {
        return paymentService.forOrder(orderId, user);
    }
}
