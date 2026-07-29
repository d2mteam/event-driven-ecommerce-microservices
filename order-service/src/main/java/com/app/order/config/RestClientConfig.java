package com.app.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    public static final String PRODUCT_REST_CLIENT = "productRestClient";
    public static final String INVENTORY_REST_CLIENT = "inventoryRestClient";

    @Bean(PRODUCT_REST_CLIENT)
    public RestClient productRestClient(
            RestClient.Builder builder,
            ClientProperties properties
    ) {
        return buildClient(
                builder,
                properties.getProductBaseUrl(),
                properties
        );
    }

    @Bean(INVENTORY_REST_CLIENT)
    public RestClient inventoryRestClient(
            RestClient.Builder builder,
            ClientProperties properties
    ) {
        return buildClient(
                builder,
                properties.getInventoryBaseUrl(),
                properties
        );
    }

    private RestClient buildClient(
            RestClient.Builder builder,
            String baseUrl,
            ClientProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
