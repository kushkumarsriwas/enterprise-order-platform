package com.enterprise.notification;

import com.enterprise.notification.controller.NotificationController;
import com.enterprise.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.enterprise.notification.entity.Notification;
import com.enterprise.notification.entity.NotificationStatus;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(NotificationController.class)
class NotificationServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void getNotificationsByUser_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/notifications/user/1"))
                .andExpect(status().isOk());
    }
}
