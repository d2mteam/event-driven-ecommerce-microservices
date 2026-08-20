package com.app.notification.mapper;

import com.app.notification.config.NotificationProperties;
import com.app.notification.dto.NotificationResponse;
import com.app.notification.entity.Notification;
import com.app.notification.event.OrderConfirmedEvent;
import com.app.notification.event.OrderFailedEvent;
import com.app.notification.event.OrderCancelledEvent;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(
            target = "message",
            expression = "java(properties.successMessageTemplate().formatted(event.orderId()))"
    )
    Notification toNotification(
            OrderConfirmedEvent event,
            @Context NotificationProperties properties
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(
            target = "message",
            expression = "java(properties.failureMessageTemplate().formatted(event.orderId(), event.reason()))"
    )
    Notification toNotification(
            OrderFailedEvent event,
            NotificationProperties properties
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(
            target = "message",
            expression = "java(properties.cancellationMessageTemplate().formatted(event.orderId()))"
    )
    Notification toNotification(
            OrderCancelledEvent event,
            NotificationProperties properties
    );
}
