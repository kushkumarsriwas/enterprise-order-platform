package com.enterprise.order.order;

import com.enterprise.order.order.OrderDtos.MerchantOrderItemResponse;
import com.enterprise.order.order.OrderDtos.MerchantOrderResponse;
import com.enterprise.order.order.OrderDtos.OrderItemResponse;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.StatusHistoryResponse;
import java.time.Duration;
import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order, Duration paymentWindow) {
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getUserId(), order.getCustomerUsername(),
                order.getMerchantId(), order.getMerchantName(), order.getStatus().name(),
                order.getPaymentState().name(), order.getPaymentMethod(), order.getPaymentId(), order.getCurrency(),
                order.getTotalAmount(), order.getShippingAddress(), order.getCarrier(), order.getTrackingNumber(),
                order.getShippedAt(), order.getDeliveredAt(), order.getCancelReason(), order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getStatus() == OrderStatus.PENDING && order.getCreatedAt() != null
                        ? order.getCreatedAt().plus(paymentWindow) : null,
                order.getItems().stream().map(item -> new OrderItemResponse(item.getProductId(), item.getSku(),
                        item.getProductName(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal())).toList(),
                history(order));
    }

    public static MerchantOrderResponse toMerchantResponse(Order order) {
        return new MerchantOrderResponse(order.getId(), order.getOrderNumber(), order.getUserId(),
                order.getCustomerUsername(), order.getCustomerEmail(), order.getCustomerPhone(),
                order.getStatus().name(), order.getPaymentState().name(), order.getPaymentMethod(),
                order.getCurrency(), order.getTotalAmount(), order.getTotalCost(),
                order.getTotalAmount().subtract(order.getTotalCost()), order.getShippingAddress(), order.getCarrier(),
                order.getTrackingNumber(), order.getShippedAt(), order.getDeliveredAt(), order.getCancelReason(),
                order.getCreatedAt(),
                order.getItems().stream().map(item -> new MerchantOrderItemResponse(item.getProductId(), item.getSku(),
                        item.getProductName(), item.getUnitPrice(), item.getUnitCost(), item.getQuantity(),
                        item.getLineTotal(), item.getLineTotal().subtract(item.getLineCost()))).toList(),
                history(order));
    }

    private static List<StatusHistoryResponse> history(Order order) {
        return order.getHistory().stream().map(h -> new StatusHistoryResponse(
                h.getFromStatus() == null ? null : h.getFromStatus().name(), h.getToStatus().name(),
                h.getChangedBy(), h.getNote(), h.getChangedAt())).toList();
    }
}
