package com.enterprise.order.order;

import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.OrderDtos.CancelRequest;
import com.enterprise.order.order.OrderDtos.CheckoutResponse;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.PayManyRequest;
import com.enterprise.order.order.OrderDtos.PayManyResult;
import com.enterprise.order.order.OrderDtos.PayOrderRequest;
import com.enterprise.order.order.OrderDtos.PayOrderResponse;
import com.enterprise.order.order.OrderDtos.PlaceOrderRequest;
import com.enterprise.order.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customer side of orders (admins can also view and cancel any order). */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders (customer)")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Checkout: creates one order per merchant and reserves the stock")
    public CheckoutResponse place(@Valid @RequestBody PlaceOrderRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.placeOrder(user, request);
    }

    @GetMapping
    @Operation(summary = "My orders (admins see all orders)")
    public PageResponse<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                            @RequestParam(required = false) Long customerId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.list(user, customerId, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Order details with items, tracking and status history")
    public OrderResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.get(user, id);
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Pay one pending order (CARD, UPI or COD)")
    public PayOrderResponse pay(@PathVariable Long id, @Valid @RequestBody PayOrderRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.pay(user, id, request);
    }

    @PostMapping("/pay")
    @Operation(summary = "Pay all orders of a checkout with one payment method")
    public List<PayManyResult> payMany(@Valid @RequestBody PayManyRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.payMany(user, request.orderIds(), request.payment());
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order that has not shipped yet (refund + stock release)")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.cancel(user, id, request == null ? null : request.reason());
    }
}
