package com.app.inventory.service;

import com.app.inventory.dto.CreateReservationRequest;
import com.app.inventory.dto.ReservationResponse;
import com.app.inventory.messaging.OrderCreatedEvent;

public interface InventoryReservationService {

    ReservationResponse reserve(CreateReservationRequest request);

    void settle(OrderCreatedEvent event);

    int releaseExpiredReservations();
}
