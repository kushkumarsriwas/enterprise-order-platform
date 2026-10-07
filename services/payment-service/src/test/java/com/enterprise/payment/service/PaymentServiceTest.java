package com.enterprise.payment.service;

import com.enterprise.payment.entity.Payment;
import com.enterprise.payment.entity.PaymentStatus;
import com.enterprise.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processPayment_shouldCreateSuccessfulPayment() {
        Payment payment = new Payment(1L, new BigDecimal("3000.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        Payment result = paymentService.processPayment(1L, new BigDecimal("3000.00"));

        assertEquals(1L, result.getOrderId());
        assertEquals(new BigDecimal("3000.00"), result.getAmount());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void getByOrderId_shouldReturnPayment() {
        Payment payment = new Payment(1L, new BigDecimal("3000.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        Payment result = paymentService.getByOrderId(1L);

        assertEquals(1L, result.getOrderId());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
    }

    @Test
    void getByOrderId_shouldThrowWhenPaymentNotFound() {
        when(paymentRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> paymentService.getByOrderId(999L));

        assertEquals("Payment not found for order: 999", exception.getMessage());
    }
}
