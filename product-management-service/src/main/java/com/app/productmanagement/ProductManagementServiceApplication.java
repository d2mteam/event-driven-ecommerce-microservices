package com.app.productmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class ProductManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductManagementServiceApplication.class, args);
    }

}
