package com.enterprise.order.service;

import com.enterprise.order.client.ProductClient;
import com.enterprise.order.client.dto.ProductSnapshot;
import com.enterprise.order.dto.CreateOrderRequest;
import com.enterprise.order.dto.OrderItemRequest;
import com.enterprise.order.entity.Order;
import com.enterprise.order.entity.OrderStatus;
import com.enterprise.order.exception.OrderNotFoundException;
import com.enterprise.order.entity.OrderItem;
import com.enterprise.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(request.getUserId());
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductSnapshot product = productClient.getProduct(itemRequest.getProductId());

            if (product == null || product.getId() == null) {
                throw new IllegalArgumentException("Product not found: " + itemRequest.getProductId());
            }

            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for product: " + product.getSku());
            }

            OrderItem item = new OrderItem(
                    product.getId(),
                    itemRequest.getQuantity(),
                    product.getPrice()
            );

            order.addItem(item);
            total = total.add(item.getLineTotal());
        }

        order.setTotalAmount(total);
        return orderRepository.save(order);
    }


    @Transactional
    public Order markPaid(Long id) {
        Order order = getById(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot pay for a cancelled order");
        }
        order.setStatus(OrderStatus.PAID);
        return orderRepository.save(order);
    }
    public java.util.List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
    }
}
