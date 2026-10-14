package com.enterprise.order.order;

import com.enterprise.order.common.ApiException;
import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.SellerOrderResponse;
import com.enterprise.order.security.AuthenticatedUser;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of orders: customer history, the seller's order desk and the administrator's order explorer. */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    /** Largest export (Excel/PDF) produced in one request. */
    public static final int EXPORT_LIMIT = 5000;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public OrderQueryService(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    /** A customer's own order (the administrator can open any order). */
    public OrderResponse get(AuthenticatedUser user, Long orderId) {
        Order order = load(orderId);
        if (!user.isAdmin() && !order.isOwnedBy(user.id())) {
            throw ApiException.notFound("Order " + orderId + " not found");
        }
        return OrderMapper.toResponse(order, orderService.paymentWindow());
    }

    /** "My orders" of the signed-in customer, newest first. */
    public PageResponse<OrderResponse> listForCustomer(AuthenticatedUser caller, Long customerId, OrderStatus status,
                                                       int page, int size) {
        if (!caller.isAdmin() && !caller.id().equals(customerId)) {
            throw ApiException.forbidden("You can only view your own orders");
        }
        OrderFilter filter = new OrderFilter(null, customerId, status == null ? List.of() : List.of(status), null, null,
                null, null, null, null, null, null);
        return PageResponse.from(orderRepository.findAll(filter.toSpecification(), page(page, size, OrderFilter.sort(null))),
                order -> OrderMapper.toResponse(order, orderService.paymentWindow()));
    }

    public SellerOrderResponse getForSeller(AuthenticatedUser seller, Long orderId) {
        Order order = load(orderId);
        if (!order.isSoldBy(seller.id())) {
            throw ApiException.notFound("Order " + orderId + " not found");
        }
        return OrderMapper.toSellerResponse(order);
    }

    public SellerOrderResponse getWithDetails(Long orderId) {
        return OrderMapper.toSellerResponse(load(orderId));
    }

    /** The seller's (filter.sellerId set) or the administrator's (all sellers) order list. */
    public PageResponse<SellerOrderResponse> search(OrderFilter filter, String sort, int page, int size) {
        return PageResponse.from(orderRepository.findAll(filter.toSpecification(), page(page, size, OrderFilter.sort(sort))),
                OrderMapper::toSellerResponse);
    }

    public List<SellerOrderResponse> export(OrderFilter filter, String sort) {
        return orderRepository.findAll(filter.toSpecification(), PageRequest.of(0, EXPORT_LIMIT, OrderFilter.sort(sort)))
                .map(OrderMapper::toSellerResponse).getContent();
    }

    /** Order counts per status for the tabs of the order desk ("To ship (4)"); null seller = whole platform. */
    public Map<String, Long> statusCounts(Long sellerId) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, sellerId == null ? orderRepository.countByStatus(status)
                    : orderRepository.countBySellerAndStatus(sellerId, status));
        }
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("ALL", counts.values().stream().mapToLong(Long::longValue).sum());
        counts.forEach((status, count) -> result.put(status.name(), count));
        return result;
    }

    private Order load(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order " + orderId + " not found"));
    }

    private static PageRequest page(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
    }
}
