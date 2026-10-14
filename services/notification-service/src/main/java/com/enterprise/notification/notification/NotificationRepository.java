package com.enterprise.notification.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n from Notification n
            where (n.audience = com.enterprise.notification.notification.Audience.USER and n.userId = :userId)
               or (:admin = true and n.audience = com.enterprise.notification.notification.Audience.ADMINS)""")
    Page<Notification> findVisibleTo(Long userId, boolean admin, Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where n.read = false
              and ((n.audience = com.enterprise.notification.notification.Audience.USER and n.userId = :userId)
               or (:admin = true and n.audience = com.enterprise.notification.notification.Audience.ADMINS))""")
    long countUnread(Long userId, boolean admin);

    @Modifying
    @Query("""
            update Notification n set n.read = true
            where n.read = false
              and ((n.audience = com.enterprise.notification.notification.Audience.USER and n.userId = :userId)
               or (:admin = true and n.audience = com.enterprise.notification.notification.Audience.ADMINS))""")
    int markAllRead(Long userId, boolean admin);
}
