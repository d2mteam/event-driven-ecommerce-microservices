package com.app.inventory.service;

import com.app.inventory.dto.CreateReservationRequest;
import com.app.inventory.dto.ReservationResponse;
import com.app.inventory.messaging.OrderConfirmedEvent;
import com.app.inventory.messaging.OrderFailedEvent;

public interface InventoryReservationService {

    ReservationResponse reserve(CreateReservationRequest request);

    void settleConfirmedOrder(OrderConfirmedEvent event);

    void releaseFailedOrder(OrderFailedEvent event);

    int releaseExpiredReservations();
}
