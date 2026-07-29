package com.app.inventory.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.inventory.config.InventoryReservationProperties;
import com.app.inventory.dto.CreateReservationRequest;
import com.app.inventory.dto.ReservationItemRequest;
import com.app.inventory.dto.ReservationResponse;
import com.app.inventory.entity.Inventory;
import com.app.inventory.entity.InventoryReservation;
import com.app.inventory.entity.ReservationItem;
import com.app.inventory.entity.ReservationStatus;
import com.app.inventory.exception.InventoryConflictException;
import com.app.inventory.mapper.InventoryReservationMapper;
import com.app.inventory.messaging.OrderCreatedEvent;
import com.app.inventory.repository.InventoryRepository;
import com.app.inventory.repository.InventoryReservationRepository;
import com.app.inventory.service.InventoryReservationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryReservationServiceImpl implements InventoryReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationProperties properties;
    private final InventoryReservationMapper reservationMapper;

    @Override
    @Transactional
    public ReservationResponse reserve(CreateReservationRequest request) {
        InventoryReservation existing = reservationRepository.findByOrderId(request.orderId())
                .orElse(null);
        if (existing != null) {
            return reservationMapper.toResponse(existing);
        }

        List<ReservationItem> items = normalize(request.items());
        List<Long> productIds = items.stream()
                .map(ReservationItem::productId)
                .toList();

        List<Inventory> lockedInventories =
                inventoryRepository.findAllByProductIdForUpdate(productIds);

        existing = reservationRepository.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            return reservationMapper.toResponse(existing);
        }

        Map<Long, Inventory> inventoryByProductId = lockedInventories.stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        List<Long> missingProductIds = productIds.stream()
                .filter(productId -> !inventoryByProductId.containsKey(productId))
                .toList();
        if (!missingProductIds.isEmpty()) {
            throw InventoryConflictException.productsNotFound(missingProductIds);
        }

        for (ReservationItem item : items) {
            Inventory inventory = inventoryByProductId.get(item.productId());
            if (inventory.availableQuantity() < item.quantity()) {
                throw InventoryConflictException.insufficientStock(
                        item.productId(),
                        item.quantity(),
                        inventory.availableQuantity()
                );
            }
        }

        for (ReservationItem item : items) {
            inventoryByProductId.get(item.productId()).reserve(item.quantity());
        }

        Instant createdAt = Instant.now();
        InventoryReservation reservation = InventoryReservation.held(
                request.orderId(),
                items,
                createdAt,
                createdAt.plus(properties.ttl())
        );

        return reservationMapper.toResponse(reservationRepository.save(reservation));
    }

    @Override
    @Transactional
    public void settle(OrderCreatedEvent event) {
        event.validate();

        InventoryReservation reservation = reservationRepository
                .findByIdForUpdate(event.reservationId())
                .orElse(null);

        if (reservation == null || !reservation.isHeld()) {
            return;
        }
        if (!reservation.getOrderId().equals(event.orderId())) {
            throw new IllegalArgumentException(
                    "Reservation does not belong to the event order"
            );
        }

        Map<Long, Inventory> inventoryByProductId =
                lockInventories(reservation.getItems());

        for (ReservationItem item : reservation.getItems()) {
            inventoryByProductId.get(item.productId()).settle(item.quantity());
        }
        reservation.settle();
    }

    @Override
    @Transactional
    public int releaseExpiredReservations() {
        List<InventoryReservation> reservations =
                reservationRepository.findExpiredForUpdate(
                        ReservationStatus.HELD,
                        Instant.now()
                );

        if (reservations.isEmpty()) {
            return 0;
        }

        Map<Long, Integer> quantitiesByProductId = new TreeMap<>();
        for (InventoryReservation reservation : reservations) {
            for (ReservationItem item : reservation.getItems()) {
                quantitiesByProductId.merge(
                        item.productId(),
                        item.quantity(),
                        Math::addExact
                );
            }
        }

        List<ReservationItem> aggregatedItems = quantitiesByProductId.entrySet().stream()
                .map(entry -> new ReservationItem(entry.getKey(), entry.getValue()))
                .toList();
        Map<Long, Inventory> inventoryByProductId = lockInventories(aggregatedItems);

        for (ReservationItem item : aggregatedItems) {
            inventoryByProductId.get(item.productId()).release(item.quantity());
        }
        for (InventoryReservation reservation : reservations) {
            reservation.expire(UUID.randomUUID());
        }

        return reservations.size();
    }

    private List<ReservationItem> normalize(List<ReservationItemRequest> requestedItems) {
        Map<Long, Integer> quantityByProductId = new TreeMap<>();
        for (ReservationItemRequest item : requestedItems) {
            quantityByProductId.merge(
                    item.productId(),
                    item.quantity(),
                    Math::addExact
            );
        }

        return quantityByProductId.entrySet().stream()
                .map(entry -> new ReservationItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<Long, Inventory> lockInventories(List<ReservationItem> items) {
        List<Long> productIds = items.stream()
                .map(ReservationItem::productId)
                .sorted()
                .toList();
        List<Inventory> inventories =
                inventoryRepository.findAllByProductIdForUpdate(productIds);

        Map<Long, Inventory> inventoryByProductId = inventories.stream()
                .collect(Collectors.toMap(
                        Inventory::getProductId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        List<Long> missingProductIds = new ArrayList<>();
        for (Long productId : productIds) {
            if (!inventoryByProductId.containsKey(productId)) {
                missingProductIds.add(productId);
            }
        }
        if (!missingProductIds.isEmpty()) {
            throw new IllegalStateException(
                    "Reservation references missing inventories: " + missingProductIds
            );
        }

        return inventoryByProductId;
    }
}
