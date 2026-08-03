package com.app.order.client;

import com.app.order.config.RestClientConfig;
import com.app.order.dto.PaymentResponse;
import com.app.order.exception.DownstreamServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PaymentClient {

    private static final String PAYMENTS_PATH = "/internal/payments";

    private final RestClient restClient;

    public PaymentClient(
            @Qualifier(RestClientConfig.PAYMENT_REST_CLIENT) RestClient restClient
    ) {
        this.restClient = restClient;
    }

    public PaymentResponse create(CreatePaymentRequest request) {
        try {
            PaymentResponse response = restClient.post()
                    .uri(PAYMENTS_PATH)
                    .body(request)
                    .retrieve()
                    .body(PaymentResponse.class);

            if (response == null) {
                throw new DownstreamServiceException(
                        "Payment Service returned an empty response"
                );
            }
            if (response.id() == null
                    || !request.orderId().equals(response.orderId())
                    || response.amount() == null
                    || request.amount().compareTo(response.amount()) != 0
                    || response.status() == null
                    || response.expiresAt() == null) {
                throw new DownstreamServiceException(
                        "Payment Service returned invalid payment data"
                );
            }
            return response;
        } catch (RestClientException exception) {
            throw new DownstreamServiceException(
                    "Cannot create payment through Payment Service",
                    exception
            );
        }
    }
}
