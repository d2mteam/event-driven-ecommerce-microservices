package com.app.apigateway.admin;

import com.app.apigateway.admin.dto.AdminCatalogItemResponse;
import com.app.apigateway.admin.dto.AdminProductResponse;
import com.app.apigateway.admin.dto.InventorySummaryResponse;
import com.app.apigateway.admin.dto.PageResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminCatalogService {

    private final ProductAdminClient productClient;
    private final InventoryAdminClient inventoryClient;
    private final AdminCatalogMapper catalogMapper;

    public AdminCatalogService(
            ProductAdminClient productClient,
            InventoryAdminClient inventoryClient,
            AdminCatalogMapper catalogMapper
    ) {
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.catalogMapper = catalogMapper;
    }

    public Mono<PageResponse<AdminCatalogItemResponse>> getCatalog(
            int page,
            int size,
            String sort,
            String status,
            String name,
            String category
    ) {
        return productClient.findProducts(page, size, sort, status, name, category)
                .flatMap(productPage -> {
                    if (productPage.content().isEmpty()) {
                        return Mono.just(catalogMapper.toPage(
                                productPage,
                                Map.of()
                        ));
                    }

                    LinkedHashSet<Long> productIds = productPage.content().stream()
                            .map(product -> product.id())
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    return inventoryClient.findAll(productIds)
                            .map(inventories -> inventories.stream().collect(
                                    Collectors.toMap(
                                            InventorySummaryResponse::productId,
                                            Function.identity()
                                    )
                            ))
                            .map(inventoryByProductId ->
                                    catalogMapper.toPage(
                                            productPage,
                                            inventoryByProductId
                                    ));
                });
    }
}
