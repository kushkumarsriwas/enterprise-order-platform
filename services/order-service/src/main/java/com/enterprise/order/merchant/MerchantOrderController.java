package com.enterprise.order.merchant;

import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.OrderDtos.CancelRequest;
import com.enterprise.order.order.OrderDtos.MerchantOrderResponse;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.ShipRequest;
import com.enterprise.order.order.OrderService;
import com.enterprise.order.order.OrderStatus;
import com.enterprise.order.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Order fulfilment for merchants: only orders of their own store. */
@RestController
@RequestMapping("/api/merchant/orders")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant - orders")
public class MerchantOrderController {

    private final OrderService orderService;

    public MerchantOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Orders of my store, newest first (filter by status)")
    public PageResponse<MerchantOrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "15") int size,
                                                    @AuthenticationPrincipal AuthenticatedUser merchant) {
        return orderService.listForMerchant(merchant, status, page, size);
    }

    @GetMapping("/{id}")
    public MerchantOrderResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser merchant) {
        return orderService.getForMerchant(merchant, id);
    }

    @PutMapping("/{id}/ship")
    @Operation(summary = "Ship a confirmed order with carrier and tracking number")
    public MerchantOrderResponse ship(@PathVariable Long id, @Valid @RequestBody ShipRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser merchant) {
        return orderService.ship(merchant, id, request.carrier(), request.trackingNumber(), request.note());
    }

    @PutMapping("/{id}/deliver")
    @Operation(summary = "Mark a shipped order as delivered (cash on delivery is collected)")
    public MerchantOrderResponse deliver(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser merchant) {
        return orderService.deliver(merchant, id);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order before shipping (refund + stock back to inventory)")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request,
                                @AuthenticationPrincipal AuthenticatedUser merchant) {
        return orderService.cancel(merchant, id, request == null ? null : request.reason());
    }
}
