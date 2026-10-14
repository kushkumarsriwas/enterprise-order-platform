package com.enterprise.order.order;

import com.enterprise.order.order.OrderDtos.OrderItemResponse;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.SellerOrderItemResponse;
import com.enterprise.order.order.OrderDtos.SellerOrderResponse;
import com.enterprise.order.order.OrderDtos.StatusHistoryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** DTO pattern: customers see their order; sellers and the administrator also see costs and profit. */
public final class OrderMapper {

    /** UAE VAT: prices include 5%, so VAT = total x 5 / 105. */
    static final BigDecimal VAT_RATE_PERCENT = BigDecimal.valueOf(5);
    static final Set<OrderStatus> INVOICED = EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private OrderMapper() {
    }

    public static BigDecimal vatIncluded(BigDecimal grossAmount) {
        return grossAmount.multiply(VAT_RATE_PERCENT)
                .divide(BigDecimal.valueOf(100).add(VAT_RATE_PERCENT), 2, RoundingMode.HALF_UP);
    }

    /** VAT of an order = sum of the VAT of its lines, so the order total and the tax invoice always agree. */
    public static BigDecimal vatOf(Order order) {
        return order.getItems().stream().map(item -> vatIncluded(item.getLineTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static boolean isInvoiced(OrderStatus status) {
        return INVOICED.contains(status);
    }

    public static OrderResponse toResponse(Order order, Duration paymentWindow) {
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getUserId(), order.getCustomerUsername(),
                order.getSellerId(), order.getSellerName(), order.getStatus().name(),
                order.getPaymentState().name(), order.getPaymentMethod(), order.getPaymentId(), order.getCurrency(),
                order.getTotalAmount(), vatOf(order), order.getShippingAddress(),
                order.getRecipientName(), order.getCarrier(), order.getTrackingNumber(), order.getShippedAt(),
                order.getDeliveredAt(), order.getCancelReason(), order.getCreatedAt(), order.getUpdatedAt(),
                order.getStatus() == OrderStatus.PENDING && order.getCreatedAt() != null
                        ? order.getCreatedAt().plus(paymentWindow) : null,
                INVOICED.contains(order.getStatus()),
                order.getItems().stream().map(item -> new OrderItemResponse(item.getProductId(), item.getSku(),
                        item.getProductName(), item.getCategoryName(), item.getUnitPrice(), item.getQuantity(),
                        item.getLineTotal())).toList(),
                history(order));
    }

    public static SellerOrderResponse toSellerResponse(Order order) {
        return new SellerOrderResponse(order.getId(), order.getOrderNumber(), order.getUserId(),
                order.getCustomerUsername(), order.getCustomerEmail(), order.getCustomerPhone(), order.getSellerId(),
                order.getSellerName(), order.getStatus().name(), order.getPaymentState().name(),
                order.getPaymentMethod(), order.getCurrency(), order.getTotalAmount(),
                vatOf(order), order.getTotalCost(),
                order.getTotalAmount().subtract(order.getTotalCost()), order.getShippingAddress(),
                order.getRecipientName(), order.getShippingCity(), order.getShippingRegion(), order.getCarrier(),
                order.getTrackingNumber(), order.getConfirmedAt(), order.getShippedAt(), order.getDeliveredAt(),
                order.getCancelReason(), order.getCreatedAt(), order.getUnits(),
                order.getItems().stream().map(item -> new SellerOrderItemResponse(item.getProductId(), item.getSku(),
                        item.getProductName(), item.getCategoryName(), item.getUnitPrice(), item.getUnitCost(),
                        item.getQuantity(), item.getLineTotal(), item.getLineTotal().subtract(item.getLineCost())))
                        .toList(),
                history(order));
    }

    private static List<StatusHistoryResponse> history(Order order) {
        return order.getHistory().stream().map(h -> new StatusHistoryResponse(
                h.getFromStatus() == null ? null : h.getFromStatus().name(), h.getToStatus().name(),
                h.getChangedBy(), h.getNote(), h.getChangedAt())).toList();
    }
}
