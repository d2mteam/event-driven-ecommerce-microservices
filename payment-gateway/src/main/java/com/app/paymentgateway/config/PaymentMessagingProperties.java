package com.app.paymentgateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.messaging")
public class PaymentMessagingProperties {

    @Valid
    @NotNull
    private Topics topics = new Topics();

    @Valid
    @NotNull
    private Topic topic = new Topic();

    @Getter
    @Setter
    public static class Topics {

        @NotBlank
        private String paymentEvents;
    }

    @Getter
    @Setter
    public static class Topic {

        @Min(1)
        private int partitions;

        @Min(1)
        private int replicationFactor;

        @Min(1)
        private int minInSyncReplicas;
    }
}
