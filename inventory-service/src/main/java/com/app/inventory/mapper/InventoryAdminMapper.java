package com.app.inventory.mapper;

import com.app.inventory.dto.InventoryAdjustmentResponse;
import com.app.inventory.dto.InventorySummaryResponse;
import com.app.inventory.entity.Inventory;
import com.app.inventory.entity.InventoryAdjustment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryAdminMapper {

    @Mapping(target = "adjustmentId", source = "id")
    @Mapping(target = "availableQuantity", source = "resultingAvailable")
    InventoryAdjustmentResponse toResponse(InventoryAdjustment adjustment);

    @Mapping(
            target = "availableQuantity",
            expression = "java(inventory.availableQuantity())"
    )
    InventorySummaryResponse toSummary(Inventory inventory);
}
