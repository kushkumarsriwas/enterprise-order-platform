package com.enterprise.order.controller;

import com.enterprise.order.dto.CreateOrderRequest;
import com.enterprise.order.entity.Order;
import com.enterprise.order.entity.OrderItem;
import com.enterprise.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(toResponse(orderService.createOrder(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(orderService.getById(id)));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items);
    }

    public record OrderResponse(
            Long id,
            Long userId,
            String status,
            BigDecimal totalAmount,
            Instant createdAt,
            List<OrderItemResponse> items) {}

    public record OrderItemResponse(
            Long id,
            Long productId,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {}
}
