package com.app.order.client;

import com.app.order.config.RestClientConfig;
import com.app.order.exception.DownstreamServiceException;
import com.app.order.exception.ProductNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class ProductClient {

    private static final String BATCH_PRODUCTS_PATH = "/api/products/batch";

    private final RestClient restClient;

    public ProductClient(
            @Qualifier(RestClientConfig.PRODUCT_REST_CLIENT) RestClient restClient
    ) {
        this.restClient = restClient;
    }

    public List<ProductClientResponse> findProducts(List<Long> productIds) {
        try {
            List<ProductClientResponse> products = restClient.post()
                    .uri(BATCH_PRODUCTS_PATH)
                    .body(productIds)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (products == null) {
                throw new DownstreamServiceException(
                        "Product Service returned an empty response"
                );
            }
            return products;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ProductNotFoundException(productIds);
        } catch (RestClientException exception) {
            throw new DownstreamServiceException(
                    "Cannot read products from Product Service",
                    exception
            );
        }
    }
}
