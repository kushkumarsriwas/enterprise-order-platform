package com.enterprise.payment.service;

import com.enterprise.payment.entity.Payment;
import com.enterprise.payment.entity.PaymentStatus;
import com.enterprise.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RestClient restClient;
    private final String orderServiceUrl;
    private final String notificationServiceUrl;

    public PaymentService(
            PaymentRepository paymentRepository,
            @Value("${ORDER_SERVICE_URL:http://enterprise-order:8083}") String orderServiceUrl,
            @Value("${NOTIFICATION_SERVICE_URL:http://enterprise-notification:8085}") String notificationServiceUrl) {
        this.paymentRepository = paymentRepository;
        this.orderServiceUrl = orderServiceUrl;
        this.notificationServiceUrl = notificationServiceUrl;
        this.restClient = RestClient.builder().build();
    }

    @Transactional
    public Payment processPayment(Long orderId, BigDecimal amount) {
        Map<?, ?> order = restClient.get()
                .uri(orderServiceUrl + "/api/orders/" + orderId)
                .retrieve()
                .body(Map.class);

        if (order == null || order.get("totalAmount") == null || order.get("userId") == null) {
            throw new IllegalArgumentException("Order response is incomplete: " + orderId);
        }

        BigDecimal orderAmount = new BigDecimal(order.get("totalAmount").toString());
        if (amount.compareTo(orderAmount) != 0) {
            throw new IllegalArgumentException("Payment amount does not match order total");
        }
        if ("CANCELLED".equals(String.valueOf(order.get("status")))) {
            throw new IllegalArgumentException("Cannot pay for a cancelled order");
        }

        Payment existing = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == PaymentStatus.SUCCESS) {
                return existing;
            }
            throw new IllegalArgumentException("A payment already exists for order: " + orderId);
        }

        Payment payment = new Payment(orderId, amount);
        payment.setStatus(PaymentStatus.SUCCESS);
        Payment saved = paymentRepository.save(payment);

        restClient.patch()
                .uri(orderServiceUrl + "/api/orders/" + orderId + "/paid")
                .retrieve()
                .toBodilessEntity();

        try {
            restClient.post()
                    .uri(notificationServiceUrl + "/api/notifications")
                    .body(Map.of(
                            "userId", Long.valueOf(order.get("userId").toString()),
                            "type", "PAYMENT_SUCCESS",
                            "message", "Payment successful for order #" + orderId + " (amount: " + amount + ")"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            System.err.println("Payment succeeded but notification failed for order "
                    + orderId + ": " + ex.getMessage());
        }

        return saved;
    }

    public java.util.List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));
    }
}