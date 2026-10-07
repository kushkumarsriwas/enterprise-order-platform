package com.enterprise.payment;

import com.enterprise.payment.controller.PaymentController;
import com.enterprise.payment.dto.CreatePaymentRequest;
import com.enterprise.payment.entity.Payment;
import com.enterprise.payment.entity.PaymentStatus;
import com.enterprise.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getPaymentByOrderId_shouldReturnOk() throws Exception {
        Payment payment = new Payment(1L, new BigDecimal("3000.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentService.getByOrderId(1L)).thenReturn(payment);

        mockMvc.perform(get("/api/payments/order/1"))
                .andExpect(status().isOk());
    }

    @Test
    void createPayment_shouldReturnOk() throws Exception {
        Payment payment = new Payment(1L, new BigDecimal("3000.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentService.processPayment(1L, new BigDecimal("3000.00"))).thenReturn(payment);

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(1L);
        request.setAmount(new BigDecimal("3000.00"));

        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
