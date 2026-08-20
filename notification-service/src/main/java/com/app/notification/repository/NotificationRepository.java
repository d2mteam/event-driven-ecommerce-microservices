package com.app.notification.repository;

import com.app.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByOrderId(UUID orderId);

    Page<Notification> findAllByUserIdOrderByCreatedAtDescIdDesc(
            UUID userId,
            Pageable pageable
    );

}
