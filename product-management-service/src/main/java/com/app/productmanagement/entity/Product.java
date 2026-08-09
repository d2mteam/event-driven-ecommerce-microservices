package com.app.productmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.app.productmanagement.model.ProductStatus;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, String> attributes;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) default 'ACTIVE'")
    private ProductStatus status = ProductStatus.ACTIVE;
}
