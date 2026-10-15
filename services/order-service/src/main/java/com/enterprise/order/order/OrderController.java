package com.enterprise.order.order;

import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.InvoiceService.Document;
import com.enterprise.order.order.OrderDtos.CancelRequest;
import com.enterprise.order.order.OrderDtos.CheckoutResponse;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.PlaceOrderRequest;
import com.enterprise.order.reporting.ReportFiles;
import com.enterprise.order.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/** Customer side of orders. Payment is made through the payment service: POST /api/payments. */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders (customer)")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService queries;
    private final InvoiceService invoices;

    public OrderController(OrderService orderService, OrderQueryService queries, InvoiceService invoices) {
        this.orderService = orderService;
        this.queries = queries;
        this.invoices = invoices;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Checkout: creates one order per seller and reserves the stock until payment")
    public CheckoutResponse place(@Valid @RequestBody PlaceOrderRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.placeOrder(user, request);
    }

    @GetMapping
    @Operation(summary = "My orders, newest first")
    public PageResponse<OrderResponse> mine(@RequestParam(required = false) OrderStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.listForCustomer(user, user.id(), status, page, size);
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Order history of a customer (the customer themself or the administrator)")
    public PageResponse<OrderResponse> byCustomer(@PathVariable Long customerId,
                                                  @RequestParam(required = false) OrderStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.listForCustomer(user, customerId, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Order details with items, delivery tracking and status history")
    public OrderResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.get(user, id);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order that has not shipped yet (refund + stock back to the seller)")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.cancel(user, id, request == null ? null : request.reason());
    }

    @GetMapping("/{id}/invoice")
    @Operation(summary = "Tax invoice (PDF) of a confirmed order")
    public ResponseEntity<byte[]> invoice(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        Document document = invoices.invoice(user, id);
        return ReportFiles.pdf(document.fileName(), document.content());
    }
}
