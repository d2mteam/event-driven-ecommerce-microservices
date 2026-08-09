package com.app.apigateway.admin;

import com.app.apigateway.admin.dto.AdminCatalogItemResponse;
import com.app.apigateway.admin.dto.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Validated
public class AdminCatalogController {

    private final AdminCatalogService catalogService;

    public AdminCatalogController(AdminCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/api/admin/catalog")
    public Mono<PageResponse<AdminCatalogItemResponse>> getCatalog(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "id,asc")
            @Pattern(regexp = "(id|name|price|status),(asc|desc)") String sort,
            @RequestParam(required = false)
            @Pattern(regexp = "DRAFT|ACTIVE|ARCHIVED") String status,
            @RequestParam(required = false) String query
    ) {
        return catalogService.getCatalog(page, size, sort, status, query);
    }
}
