package com.app.productmanagement.dto;

import com.app.productmanagement.model.ProductStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void doesNotExposeAmazonImportMetadata() throws Exception {
        ProductResponse response = new ProductResponse();
        response.setId(1L);
        response.setName("Demo product");
        response.setCategoryId(2L);
        response.setCategory("Demo");
        response.setPrice(BigDecimal.TEN);
        response.setDescription("Description");
        response.setAttributes(Map.of());
        response.setStatus(ProductStatus.ACTIVE);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("sourceProductId")
                .doesNotContain("bulletPoints")
                .doesNotContain("productTypeId")
                .doesNotContain("productLength");
    }
}
