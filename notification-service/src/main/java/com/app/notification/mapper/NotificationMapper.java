package com.app.notification.mapper;

import com.app.notification.config.NotificationProperties;
import com.app.notification.entity.Notification;
import com.app.notification.event.OrderCreatedEvent;
import com.app.notification.event.OrderFailedEvent;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface NotificationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "sent", constant = "true")
    @Mapping(
            target = "message",
            expression = "java(properties.successMessageTemplate().formatted(event.orderId()))"
    )
    Notification toNotification(
            OrderCreatedEvent event,
            @Context NotificationProperties properties
    );

    default String toFailureMessage(
            OrderFailedEvent event,
            NotificationProperties properties
    ) {
        return properties.failureMessageTemplate().formatted(
                event.orderId(),
                event.reason()
        );
    }
}
