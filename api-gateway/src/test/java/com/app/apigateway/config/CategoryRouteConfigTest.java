package com.app.apigateway.config;

import com.app.apigateway.ApiGatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ApiGatewayApplication.class)
class CategoryRouteConfigTest {

    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void routesPublicCategoryReadsToProductService() {
        RouteDefinition route = route("category-service");

        assertThat(predicate(route, "Path").getArgs().values())
                .containsExactly("/api/categories");
        assertThat(predicate(route, "Method").getArgs().values())
                .containsExactly("GET");
    }

    @Test
    void routesCategoryAdminCrudWithoutClaimingLocalCatalog() {
        RouteDefinition route = route("category-admin-service");

        assertThat(predicate(route, "Path").getArgs().values())
                .containsExactly(
                        "/api/admin/categories",
                        "/api/admin/categories/**"
                );
        assertThat(predicate(route, "Method").getArgs().values())
                .containsExactly("GET", "POST", "PUT", "DELETE");
        assertThat(predicate(route, "Path").getArgs().values())
                .doesNotContain("/api/admin/catalog");
    }

    private RouteDefinition route(String id) {
        return gatewayProperties.getRoutes().stream()
                .filter(route -> id.equals(route.getId()))
                .findFirst()
                .orElseThrow();
    }

    private PredicateDefinition predicate(RouteDefinition route, String name) {
        return route.getPredicates().stream()
                .filter(predicate -> name.equals(predicate.getName()))
                .findFirst()
                .orElseThrow();
    }
}
