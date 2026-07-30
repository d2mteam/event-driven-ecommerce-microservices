# Hoàn tất reservation — bất đồng bộ

Sơ đồ bắt đầu sau khi Order và `OrderCreated` outbox đã được commit. Hai
consumer group nhận cùng một event và xử lý độc lập.

```mermaid
sequenceDiagram
    autonumber

    participant Relay as Order Outbox Relay
    participant OrderDB as Order DB
    participant Kafka
    participant Inventory as Inventory Consumer
    participant InventoryDB as Inventory DB
    participant Notification as Notification Consumer
    participant NotificationDB as Notification DB

    Relay->>OrderDB: Đọc outbox chưa publish
    OrderDB->>Relay: OrderCreated

    Relay-->>Kafka: OrderCreated → order.events
    Relay->>OrderDB: Đánh dấu publishedAt

    par Consumer group inventory-service
        Kafka-->>Inventory: OrderCreated
        Inventory->>InventoryDB: Khóa reservation
        Inventory->>InventoryDB: HELD → SETTLED
        Inventory->>InventoryDB: Giảm reservedQuantity và onHandQuantity
    and Consumer group notification-service
        Kafka-->>Notification: OrderCreated
        Notification->>NotificationDB: Lưu thông báo thành công
    end
```

Quy ước:

- Mũi tên liền: thao tác nội bộ hoặc database.
- Mũi tên đứt: truyền event bất đồng bộ qua Kafka.
- Khối `par`: Inventory và Notification không chờ nhau.

Inventory consumer không tạo reservation `HELD` lần nữa. Reservation đã
được tạo trong luồng đồng bộ; consumer chỉ chuyển `HELD → SETTLED`.
