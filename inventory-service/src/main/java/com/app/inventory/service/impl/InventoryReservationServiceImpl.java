package com.app.inventory.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.inventory.config.InventoryReservationProperties;
import com.app.inventory.dto.CreateReservationRequest;
import com.app.inventory.dto.ReservationResponse;
import com.app.inventory.entity.Inventory;
import com.app.inventory.entity.InventoryReservation;
import com.app.inventory.entity.ReservationItem;
import com.app.inventory.entity.ReservationStatus;
import com.app.inventory.exception.InventoryConflictException;
import com.app.inventory.exception.InventoryEventConflictException;
import com.app.inventory.mapper.InventoryReservationMapper;
import com.app.inventory.messaging.OrderConfirmedEvent;
import com.app.inventory.messaging.OrderFailedEvent;
import com.app.inventory.messaging.OrderCancelledEvent;
import com.app.inventory.repository.InventoryRepository;
import com.app.inventory.repository.InventoryReservationRepository;
import com.app.inventory.service.InventoryOutboxWriter;
import com.app.inventory.service.InventoryReservationService;
import com.app.inventory.service.ReservationItemNormalizer;
import com.app.inventory.service.InventoryStockFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryReservationServiceImpl implements InventoryReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationProperties properties;
    private final InventoryReservationMapper reservationMapper;
    private final InventoryOutboxWriter outboxWriter;
    private final InventoryStockFilter stockFilter;
    private final ReservationItemNormalizer itemNormalizer;

    @Override
    @Transactional
    public ReservationResponse reserve(CreateReservationRequest request) {
        InventoryReservation existing = reservationRepository.findByOrderId(request.orderId())
                .orElse(null);
        if (existing != null) {
            return reservationMapper.toResponse(existing);
        }

        List<ReservationItem> items = itemNormalizer.normalize(request.items());
        List<Long> productIds = items.stream()
                .map(ReservationItem::productId)
                .toList();

        // Đặt SAU bước tra findByOrderId ở trên: retry của một đơn đã giữ hàng
        // thành công phải trả về reservation cũ, không được rơi vào bộ lọc.
        stockFilter.rejectIfKnownInsufficient(items);

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
        stockFilter.refreshAfterCommit(availabilitySnapshot(inventoryByProductId));

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
    public void settleConfirmedOrder(OrderConfirmedEvent event) {
        InventoryReservation reservation = reservationRepository
                .findByIdForUpdate(event.reservationId())
                .orElse(null);

        if (reservation == null) {
            throw new InventoryEventConflictException(
                    "Reservation not found: " + event.reservationId()
            );
        }
        if (!reservation.getOrderId().equals(event.orderId())) {
            throw new InventoryEventConflictException(
                    "Reservation does not belong to the event order"
            );
        }
        if (reservation.getStatus() == ReservationStatus.SETTLED) {
            return;
        }
        if (!reservation.isHeld()) {
            throw new InventoryEventConflictException(
                    "Cannot settle reservation in status "
                            + reservation.getStatus()
            );
        }

        try {
            Map<Long, Inventory> inventoryByProductId =
                    lockInventories(reservation.getItems());

            for (ReservationItem item : reservation.getItems()) {
                inventoryByProductId.get(item.productId())
                        .settle(item.quantity());
            }
            stockFilter.refreshAfterCommit(availabilitySnapshot(inventoryByProductId));
            reservation.settle();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InventoryEventConflictException(
                    "Cannot settle reservation " + event.reservationId(),
                    exception
            );
        }
    }

    @Override
    @Transactional
    public void releaseFailedOrder(OrderFailedEvent event) {
        InventoryReservation reservation = reservationRepository
                .findByIdForUpdate(event.reservationId())
                .orElse(null);

        if (reservation == null) {
            throw new InventoryEventConflictException(
                    "Reservation not found: " + event.reservationId()
            );
        }
        if (!reservation.getOrderId().equals(event.orderId())) {
            throw new InventoryEventConflictException(
                    "Reservation does not belong to the event order"
            );
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return;
        }
        if (!reservation.isHeld()) {
            throw new InventoryEventConflictException(
                    "Cannot release reservation in status "
                            + reservation.getStatus()
            );
        }

        try {
            Map<Long, Inventory> inventoryByProductId =
                    lockInventories(reservation.getItems());

            for (ReservationItem item : reservation.getItems()) {
                inventoryByProductId.get(item.productId())
                        .release(item.quantity());
            }
            stockFilter.refreshAfterCommit(availabilitySnapshot(inventoryByProductId));
            reservation.release();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InventoryEventConflictException(
                    "Cannot release reservation " + event.reservationId(),
                    exception
            );
        }
    }

    @Override
    @Transactional
    public void returnCancelledOrder(OrderCancelledEvent event) {
        InventoryReservation reservation = reservationRepository
                .findByIdForUpdate(event.reservationId())
                .orElse(null);

        if (reservation == null) {
            throw new InventoryEventConflictException(
                    "Reservation not found: " + event.reservationId()
            );
        }
        if (!reservation.getOrderId().equals(event.orderId())) {
            throw new InventoryEventConflictException(
                    "Reservation does not belong to the event order"
            );
        }
        if (reservation.getStatus() == ReservationStatus.RETURNED) {
            return;
        }
        if (reservation.getStatus() != ReservationStatus.SETTLED) {
            throw new InventoryEventConflictException(
                    "Cannot return reservation in status "
                            + reservation.getStatus()
            );
        }

        try {
            Map<Long, Inventory> inventoryByProductId =
                    lockInventories(reservation.getItems());
            for (ReservationItem item : reservation.getItems()) {
                inventoryByProductId.get(item.productId())
                        .returnToStock(item.quantity());
            }
            stockFilter.refreshAfterCommit(
                    availabilitySnapshot(inventoryByProductId)
            );
            reservation.markReturned();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InventoryEventConflictException(
                    "Cannot return reservation " + event.reservationId(),
                    exception
            );
        }
    }

    @Override
    public int releaseExpiredReservations(Instant expiresAt, int batchSize) {
        List<InventoryReservation> reservations =
                reservationRepository.findExpiredForUpdate(
                        ReservationStatus.HELD.name(),
                        expiresAt,
                        batchSize
                );

        if (reservations.isEmpty()) {
            return 0;
        }

        try {
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
            stockFilter.refreshAfterCommit(availabilitySnapshot(inventoryByProductId));
            for (InventoryReservation reservation : reservations) {
                reservation.expire();
                outboxWriter.addReservationExpired(reservation);
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Inventory expiration batch failed cutoff={} reservationIds={}",
                    expiresAt,
                    reservations.stream().map(InventoryReservation::getId).toList(),
                    exception
            );
            throw exception;
        }

        return reservations.size();
    }

    /**
     * Chụp lượng còn bán được sau khi đã áp dụng thay đổi lên entity.
     *
     * <p>Giá trị lấy từ entity đang bị khoá trong transaction hiện tại, nên nó
     * đúng bằng giá trị sẽ được commit. Bộ lọc chỉ ghi ra Redis sau khi commit.
     */
    private Map<Long, Integer> availabilitySnapshot(
            Map<Long, Inventory> inventoryByProductId
    ) {
        Map<Long, Integer> snapshot = new LinkedHashMap<>();
        inventoryByProductId.forEach((productId, inventory) ->
                snapshot.put(productId, inventory.availableQuantity()));
        return snapshot;
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
            throw new InventoryEventConflictException(
                    "Reservation references missing inventories: " + missingProductIds
            );
        }

        return inventoryByProductId;
    }
}
