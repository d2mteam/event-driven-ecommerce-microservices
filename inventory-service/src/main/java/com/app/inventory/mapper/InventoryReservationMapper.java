package com.app.inventory.mapper;

import com.app.inventory.dto.ReservationResponse;
import com.app.inventory.entity.InventoryReservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryReservationMapper {

    @Mapping(target = "reservationId", source = "id")
    ReservationResponse toResponse(InventoryReservation reservation);
}
