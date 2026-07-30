# Tạo đơn và giữ hàng — đồng bộ

Sơ đồ này chỉ mô tả phần xử lý đồng bộ của `POST /orders`. Nếu giữ hàng
thành công, Order được lưu với trạng thái `CONFIRMED` trước khi trả response.

```mermaid
sequenceDiagram
    autonumber

    actor User
    participant Order as Order Service
    participant Product as Product Service
    participant Inventory as Inventory Service
    participant InventoryDB as Inventory DB
    participant OrderDB as Order DB

    User->>Order: POST /orders
    Order->>Product: Kiểm tra các productId
    Product->>Order: Trả thông tin sản phẩm

    Order->>Inventory: reserve(orderId, items)
    Inventory->>InventoryDB: Khóa các dòng inventory
    Inventory->>InventoryDB: Kiểm tra availableQuantity

    alt Không đủ tồn kho
        Inventory->>Order: 409 OUT_OF_STOCK
        Order->>User: 409 OUT_OF_STOCK
    else Đủ tồn kho
        Inventory->>InventoryDB: Tăng reservedQuantity
        Inventory->>InventoryDB: Tạo reservation HELD + TTL
        Inventory->>Order: reservationId + HELD

        Order->>OrderDB: Lưu trong một transaction
        Note right of OrderDB: Order = CONFIRMED<br/>Outbox = OrderCreated

        Order->>User: 201 CREATED<br/>status = CONFIRMED
    end
```

Kết thúc sơ đồ này, `OrderCreated` mới chỉ nằm trong outbox. Kafka chưa tham
gia vào việc quyết định còn hàng hay không.
