package com.app.order.client;

import com.app.order.config.RestClientConfig;
import com.app.order.exception.DownstreamServiceException;
import com.app.order.exception.InventoryConflictException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class InventoryClient {

    private static final String RESERVATIONS_PATH = "/internal/inventory/reservations";

    private final RestClient restClient;

    public InventoryClient(
            @Qualifier(RestClientConfig.INVENTORY_REST_CLIENT) RestClient restClient
    ) {
        this.restClient = restClient;
    }

    public InventoryReservationResponse reserve(
            InventoryReservationRequest request
    ) {
        try {
            InventoryReservationResponse response = restClient.post()
                    .uri(RESERVATIONS_PATH)
                    .body(request)
                    .retrieve()
                    .body(InventoryReservationResponse.class);

            if (response == null) {
                throw new DownstreamServiceException(
                        "Inventory Service returned an empty response"
                );
            }
            return response;
        } catch (HttpClientErrorException.Conflict exception) {
            throw new InventoryConflictException();
        } catch (RestClientException exception) {
            throw new DownstreamServiceException(
                    "Cannot reserve inventory through Inventory Service",
                    exception
            );
        }
    }
}
