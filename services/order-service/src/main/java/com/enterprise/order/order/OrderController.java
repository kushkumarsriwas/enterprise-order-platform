package com.enterprise.order.order;

import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.OrderDtos.CancelRequest;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.PayOrderRequest;
import com.enterprise.order.order.OrderDtos.PayOrderResponse;
import com.enterprise.order.order.OrderDtos.PlaceOrderRequest;
import com.enterprise.order.order.OrderDtos.StatusChangeRequest;
import com.enterprise.order.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place an order: reserves stock, prices are taken from the catalogue")
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        OrderResponse order = orderService.placeOrder(user, request);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
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

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Orders of one customer (the customer themself or an admin)")
    public PageResponse<OrderResponse> byCustomer(@PathVariable Long customerId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.list(user, customerId, null, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Order details with items and status history")
    public OrderResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.get(user, id);
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Pay for a pending order (CARD, UPI or COD)")
    public PayOrderResponse pay(@PathVariable Long id, @Valid @RequestBody PayOrderRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.pay(user, id, request);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order that has not shipped yet (refund + stock release)")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.cancel(user, id, request == null ? null : request.reason());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Move an order through fulfilment: SHIPPED, DELIVERED or CANCELLED (admin)")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.updateStatus(user, id, request.status(), request.note());
    }
}
