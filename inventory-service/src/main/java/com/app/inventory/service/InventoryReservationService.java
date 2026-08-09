package com.app.inventory.service;

import java.time.Instant;

import com.app.inventory.dto.CreateReservationRequest;
import com.app.inventory.dto.ReservationResponse;
import com.app.inventory.messaging.OrderConfirmedEvent;
import com.app.inventory.messaging.OrderFailedEvent;
import com.app.inventory.messaging.OrderCancelledEvent;

public interface InventoryReservationService {

    ReservationResponse reserve(CreateReservationRequest request);

    void settleConfirmedOrder(OrderConfirmedEvent event);

    void releaseFailedOrder(OrderFailedEvent event);

    void returnCancelledOrder(OrderCancelledEvent event);

    int releaseExpiredReservations(Instant expiresAt, int batchSize);
}
