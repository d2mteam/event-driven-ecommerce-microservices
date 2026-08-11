package com.app.order.client;

import com.app.order.config.RestClientConfig;
import com.app.order.exception.DownstreamServiceException;
import com.app.order.exception.ProductNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class ProductClient {

    private static final String BATCH_PRODUCTS_PATH = "/api/products/batch";

    private final RestClient restClient;

    public ProductClient(
            @Qualifier(RestClientConfig.PRODUCT_REST_CLIENT) RestClient restClient
    ) {
        this.restClient = restClient;
    }

    /** Trả về danh sách đã kiểm tra: đủ mọi productId đã hỏi, và mỗi phần tử có
     *  id/name/price hợp lệ. Caller không phải kiểm lại. */
    @CircuitBreaker(name = "product-service")
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
            validate(products, productIds);
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

    private void validate(
            List<ProductClientResponse> products,
            List<Long> requestedIds
    ) {
        if (products.stream().anyMatch(ProductClient::isInvalid)) {
            throw new DownstreamServiceException(
                    "Product Service returned invalid product data"
            );
        }

        Set<Long> missingIds = new LinkedHashSet<>(requestedIds);
        products.forEach(product -> missingIds.remove(product.id()));
        if (!missingIds.isEmpty()) {
            throw new ProductNotFoundException(missingIds);
        }
    }

    private static boolean isInvalid(ProductClientResponse product) {
        return product == null
                || product.id() == null
                || product.name() == null
                || product.name().isBlank()
                || product.price() == null
                || product.price().signum() < 0;
    }
}
