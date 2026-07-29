package com.app.inventory.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateReservationRequest(
        @NotNull UUID orderId,
        @NotEmpty List<@NotNull @Valid ReservationItemRequest> items
) {
}
