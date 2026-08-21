package com.app.productmanagement.dto;

import com.app.productmanagement.model.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Long categoryId;
    private String category;
    private BigDecimal price;
    private String description;
    private Map<String, String> attributes;
    private ProductStatus status;
    private List<String> imageUrls;
}
