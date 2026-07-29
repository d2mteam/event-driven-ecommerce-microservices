package com.app.inventory.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.inventory.dto.CreateReservationRequest;
import com.app.inventory.dto.ReservationResponse;
import com.app.inventory.service.InventoryReservationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(InventoryReservationController.RESERVATIONS_PATH)
@RequiredArgsConstructor
public class InventoryReservationController {

    static final String RESERVATIONS_PATH = "/internal/inventory/reservations";

    private final InventoryReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(
            @Valid @RequestBody CreateReservationRequest request
    ) {
        ReservationResponse response = reservationService.reserve(request);
        return ResponseEntity
                .created(URI.create(RESERVATIONS_PATH + "/" + response.reservationId()))
                .body(response);
    }
}
