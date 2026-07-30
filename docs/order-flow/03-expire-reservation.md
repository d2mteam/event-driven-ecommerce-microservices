# Reservation hết hạn — luồng ngoại lệ

Sweeper chỉ xử lý reservation vẫn còn `HELD` khi đã quá `expiresAt`.

```mermaid
sequenceDiagram
    autonumber

    participant Sweeper as Inventory Sweeper
    participant InventoryDB as Inventory DB
    participant ExpiryRelay as Expiration Relay
    participant Kafka
    participant Order as Order Consumer
    participant OrderDB as Order DB
    participant OrderRelay as Order Outbox Relay
    participant Notification as Notification Consumer
    participant NotificationDB as Notification DB

    Sweeper->>InventoryDB: Tìm HELD có expiresAt <= now
    InventoryDB->>InventoryDB: Trả reservedQuantity
    InventoryDB->>InventoryDB: HELD → EXPIRED
    InventoryDB->>InventoryDB: Lưu expirationEventId

    ExpiryRelay->>InventoryDB: Đọc expiration chưa publish
    ExpiryRelay-->>Kafka: ReservationExpired → inventory.events
    ExpiryRelay->>InventoryDB: Đánh dấu event đã publish

    Kafka-->>Order: ReservationExpired
    Order->>OrderDB: Tìm orderId + reservationId

    alt Không có Order tương ứng
        Note over Order,OrderDB: Reservation mồ côi<br/>Bỏ qua, không notification
    else Có đúng Order
        Order->>OrderDB: Cập nhật trong một transaction
        Note right of OrderDB: CONFIRMED → FAILED<br/>reason = RESERVATION_EXPIRED<br/>Outbox = OrderFailed

        OrderRelay->>OrderDB: Đọc OrderFailed outbox
        OrderRelay-->>Kafka: OrderFailed → order.events
        Kafka-->>Notification: OrderFailed
        Notification->>NotificationDB: Đổi thông báo thành thất bại
    end
```

Reservation mồ côi là trường hợp Inventory giữ hàng thành công nhưng Order
Service chết hoặc rollback trước khi lưu Order. Order bị người dùng hủy nên
có luồng release riêng, không nên chờ TTL.
