package com.app.apigateway.admin;

import com.app.apigateway.admin.dto.AdminCatalogItemResponse;
import com.app.apigateway.admin.dto.AdminProductResponse;
import com.app.apigateway.admin.dto.InventorySummaryResponse;
import com.app.apigateway.admin.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCatalogMapperTest {

    private final AdminCatalogMapper mapper =
            Mappers.getMapper(AdminCatalogMapper.class);

    @Test
    void enrichesProductsByIdAndPreservesPageOrder() {
        AdminProductResponse first = product(2L, "Second");
        AdminProductResponse second = product(1L, "First");
        PageResponse<AdminProductResponse> productPage = new PageResponse<>(
                List.of(first, second),
                1,
                2,
                6,
                3,
                false
        );
        InventorySummaryResponse inventory = new InventorySummaryResponse(
                1L,
                20,
                3,
                17
        );

        PageResponse<AdminCatalogItemResponse> result = mapper.toPage(
                productPage,
                Map.of(1L, inventory)
        );

        assertThat(result.content())
                .extracting(item -> item.product().id())
                .containsExactly(2L, 1L);
        assertThat(result.content().get(0).inventory()).isNull();
        assertThat(result.content().get(0).inventoryState())
                .isEqualTo("NOT_INITIALIZED");
        assertThat(result.content().get(1).inventory()).isEqualTo(inventory);
        assertThat(result.content().get(1).inventoryState())
                .isEqualTo("READY");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(6);
    }

    private AdminProductResponse product(Long id, String name) {
        return new AdminProductResponse(
                id,
                name,
                "Category",
                new BigDecimal("50000.00"),
                "Description",
                Map.of(),
                "ACTIVE"
        );
    }
}
