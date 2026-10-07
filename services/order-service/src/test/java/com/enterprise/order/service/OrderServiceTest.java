package com.enterprise.order.service;

import com.enterprise.order.client.ProductClient;
import com.enterprise.order.client.dto.ProductSnapshot;
import com.enterprise.order.dto.CreateOrderRequest;
import com.enterprise.order.dto.OrderItemRequest;
import com.enterprise.order.entity.Order;
import com.enterprise.order.exception.OrderNotFoundException;
import com.enterprise.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productClient);
    }

    @Test
    void createOrder_shouldCalculateTotalAndSaveOrder() {
        ProductSnapshot product = new ProductSnapshot();
        product.setId(6L);
        product.setSku("SKU-001");
        product.setName("Laptop");
        product.setPrice(new BigDecimal("1500.00"));
        product.setStockQuantity(10);

        when(productClient.getProduct(6L)).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(6L);
        item.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        request.setItems(List.of(item));

        Order result = orderService.createOrder(request);

        assertEquals(1L, result.getUserId());
        assertEquals(new BigDecimal("3000.00"), result.getTotalAmount());
        assertEquals(1, result.getItems().size());
        assertEquals(6L, result.getItems().get(0).getProductId());
        assertEquals(new BigDecimal("1500.00"), result.getItems().get(0).getUnitPrice());
        verify(productClient).getProduct(6L);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_shouldRejectInsufficientStock() {
        ProductSnapshot product = new ProductSnapshot();
        product.setId(6L);
        product.setSku("SKU-001");
        product.setPrice(new BigDecimal("1500.00"));
        product.setStockQuantity(1);

        when(productClient.getProduct(6L)).thenReturn(product);

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(6L);
        item.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        request.setItems(List.of(item));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request));

        assertEquals("Insufficient stock for product: SKU-001", exception.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void getById_shouldReturnOrderWhenFound() {
        Order order = new Order(1L);
        order.setTotalAmount(new BigDecimal("3000.00"));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order result = orderService.getById(1L);

        assertEquals(1L, result.getUserId());
        assertEquals(new BigDecimal("3000.00"), result.getTotalAmount());
        verify(orderRepository).findById(1L);
    }

    @Test
    void getById_shouldThrowOrderNotFoundExceptionWhenMissing() {
        when(orderRepository.findById(99999L)).thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> orderService.getById(99999L));

        assertEquals("Order not found: 99999", exception.getMessage());
        verify(orderRepository).findById(99999L);
    }
}
