package com.enterprise.order.order;

import com.enterprise.order.order.OrderDtos.OrderItemResponse;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.StatusHistoryResponse;
import java.time.Duration;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order, Duration paymentWindow) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getCustomerUsername(),
                order.getStatus().name(),
                order.getPaymentState().name(),
                order.getPaymentMethod(),
                order.getPaymentId(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getCancelReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getStatus() == OrderStatus.PENDING && order.getCreatedAt() != null
                        ? order.getCreatedAt().plus(paymentWindow) : null,
                order.getItems().stream().map(item -> new OrderItemResponse(item.getProductId(), item.getSku(),
                        item.getProductName(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal())).toList(),
                order.getHistory().stream().map(h -> new StatusHistoryResponse(
                        h.getFromStatus() == null ? null : h.getFromStatus().name(), h.getToStatus().name(),
                        h.getChangedBy(), h.getNote(), h.getChangedAt())).toList());
    }
}
