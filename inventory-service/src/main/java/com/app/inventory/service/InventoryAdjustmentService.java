package com.app.inventory.service;

import com.app.inventory.dto.InventoryAdjustmentRequest;
import com.app.inventory.dto.InventoryAdjustmentResponse;
import com.app.inventory.dto.InventorySummaryResponse;
import com.app.inventory.entity.Inventory;
import com.app.inventory.entity.InventoryAdjustment;
import com.app.inventory.exception.InventoryAdjustmentConflictException;
import com.app.inventory.mapper.InventoryAdminMapper;
import com.app.inventory.repository.InventoryAdjustmentRepository;
import com.app.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryStockFilter stockFilter;
    private final InventoryAdminMapper inventoryMapper;

    @Transactional
    public InventoryAdjustmentResponse adjust(
            Long productId,
            UUID userId,
            String idempotencyKey,
            InventoryAdjustmentRequest request
    ) {
        String normalizedKey = idempotencyKey.trim();
        inventoryRepository.ensureExists(productId);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow();

        InventoryAdjustment existing = adjustmentRepository
                .findByProductIdAndIdempotencyKey(productId, normalizedKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.getDelta().equals(request.delta())
                    || existing.getReason() != request.reason()) {
                throw InventoryAdjustmentConflictException
                        .idempotencyKeyReused(productId);
            }
            return inventoryMapper.toResponse(existing);
        }

        int previousOnHand = inventory.getOnHandQuantity();
        int resultingOnHand = Math.addExact(previousOnHand, request.delta());
        if (resultingOnHand < inventory.getReservedQuantity()) {
            throw InventoryAdjustmentConflictException.affectsReservations(
                    productId,
                    previousOnHand,
                    inventory.getReservedQuantity(),
                    request.delta()
            );
        }

        inventory.adjustOnHand(request.delta());
        int resultingAvailable = inventory.availableQuantity();
        InventoryAdjustment adjustment = adjustmentRepository.save(
                InventoryAdjustment.builder()
                        .productId(productId)
                        .idempotencyKey(normalizedKey)
                        .delta(request.delta())
                        .reason(request.reason())
                        .previousOnHand(previousOnHand)
                        .resultingOnHand(resultingOnHand)
                        .reservedQuantity(inventory.getReservedQuantity())
                        .resultingAvailable(resultingAvailable)
                        .createdBy(userId)
                        .createdAt(Instant.now())
                        .build()
        );

        stockFilter.refreshAfterCommit(Map.of(productId, resultingAvailable));
        return inventoryMapper.toResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public List<InventorySummaryResponse> findAll(Collection<Long> productIds) {
        return inventoryRepository.findAllByProductIdIn(productIds).stream()
                .map(inventoryMapper::toSummary)
                .toList();
    }
}
