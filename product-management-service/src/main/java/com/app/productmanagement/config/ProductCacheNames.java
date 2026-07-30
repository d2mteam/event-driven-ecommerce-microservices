package com.app.productmanagement.config;

public final class ProductCacheNames {

    public static final String PRODUCT_DETAIL = "product-detail";
    public static final String PRODUCT_PAGE = "product-page";
    public static final String PRODUCT_PAGE_KEY =
            "#pageable.pageNumber + ':' + "
                    + "#pageable.pageSize + ':' + #pageable.sort";

    private ProductCacheNames() {
    }
}
