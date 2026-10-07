package com.enterprise.notification.service;

import com.enterprise.notification.entity.Notification;
import com.enterprise.notification.entity.NotificationStatus;
import com.enterprise.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void sendNotification_shouldCreateSentNotification() {
        Notification notification = new Notification(1L, "ORDER_CONFIRMED", "Your order has been confirmed");
        notification.setStatus(NotificationStatus.SENT);
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = notificationService.sendNotification(1L, "ORDER_CONFIRMED", "Your order has been confirmed");

        assertEquals(1L, result.getUserId());
        assertEquals("ORDER_CONFIRMED", result.getType());
        assertEquals(NotificationStatus.SENT, result.getStatus());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void getByUserId_shouldReturnNotifications() {
        Notification notification = new Notification(1L, "ORDER_CONFIRMED", "Your order has been confirmed");
        notification.setStatus(NotificationStatus.SENT);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(notification));

        List<Notification> result = notificationService.getByUserId(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getUserId());
        assertEquals(NotificationStatus.SENT, result.get(0).getStatus());
    }
}
