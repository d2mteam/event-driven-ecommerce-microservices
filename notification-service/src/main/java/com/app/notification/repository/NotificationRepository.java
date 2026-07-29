package com.app.notification.repository;

import com.app.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByOrderId(UUID orderId);

    Optional<Notification> findByOrderId(UUID orderId);
}
