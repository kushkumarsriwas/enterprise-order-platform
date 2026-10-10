package com.enterprise.payment.service;

import com.enterprise.payment.entity.Payment;
import com.enterprise.payment.entity.PaymentStatus;
import com.enterprise.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                "http://localhost:8083",
                "http://localhost:8085"
        );
    }

    @Test
    void processPayment_shouldCreateSuccessfulPayment() {
        Payment payment = new Payment(1L, new BigDecimal("3000.00"));
        payment.setStatus(PaymentStatus.SUCCESS);

        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);

        ReflectionTestUtils.setField(paymentService, "restClient", restClient);

        when(restClient.get()
                .uri("http://localhost:8083/api/orders/1")
                .retrieve()
                .body(Map.class))
                .thenReturn(Map.of(
                        "totalAmount", new BigDecimal("3000.00"),
                        "userId", 1,
                        "status", "CREATED"
                ));

        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        when(restClient.patch()
                .uri("http://localhost:8083/api/orders/1/paid")
                .retrieve()
                .toBodilessEntity())
                .thenReturn(null);

        when(restClient.post()
                .uri("http://localhost:8085/api/notifications")
                .body(any(Map.class))
                .retrieve()
                .toBodilessEntity())
                .thenReturn(null);

        Payment result = paymentService.processPayment(
                1L, new BigDecimal("3000.00")
        );

        assertEquals(1L, result.getOrderId());
        assertEquals(new BigDecimal("3000.00"), result.getAmount());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void getByOrderId_shouldReturnPayment() {
        Payment payment = new Payment(1L, new BigDecimal("3000.00"));
        payment.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findByOrderId(1L))
                .thenReturn(Optional.of(payment));

        Payment result = paymentService.getByOrderId(1L);

        assertEquals(1L, result.getOrderId());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
    }

    @Test
    void getByOrderId_shouldThrowWhenPaymentNotFound() {
        when(paymentRepository.findByOrderId(999L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.getByOrderId(999L)
        );

        assertEquals(
                "Payment not found for order: 999",
                exception.getMessage()
        );
    }
}