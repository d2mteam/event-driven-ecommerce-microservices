package com.app.inventory.controller;

import com.app.inventory.dto.InventoryAdjustmentRequest;
import com.app.inventory.dto.InventoryAdjustmentResponse;
import com.app.inventory.dto.InventorySummaryResponse;
import com.app.inventory.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
public class InventoryAdminController {

    private final InventoryAdjustmentService adjustmentService;

    @PostMapping("/api/admin/inventory/{productId}/adjustments")
    public InventoryAdjustmentResponse adjust(
            @PathVariable @Positive Long productId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("Idempotency-Key")
            @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        return adjustmentService.adjust(
                productId,
                userId,
                idempotencyKey,
                request
        );
    }

    @PostMapping("/internal/inventory/batch")
    public List<InventorySummaryResponse> findAll(
            @RequestBody
            @Size(max = 50) Set<@Positive Long> productIds
    ) {
        return adjustmentService.findAll(productIds);
    }
}
