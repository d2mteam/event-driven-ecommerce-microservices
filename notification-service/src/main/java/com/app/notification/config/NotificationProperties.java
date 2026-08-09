package com.app.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        String successMessageTemplate,
        String failureMessageTemplate,
        String cancellationMessageTemplate
) {
}
