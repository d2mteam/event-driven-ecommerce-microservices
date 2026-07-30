# Test sức tải `POST /orders` trên máy local

## Test này trả lời câu hỏi gì?

`capacity-orders.js` tăng dần số virtual users (VU) để tìm gần đúng điểm
mà API không còn giữ được đồng thời ba điều kiện:

- ít nhất 99% check thành công;
- tỷ lệ HTTP lỗi dưới 1%;
- p95 response time dưới 1 giây.

Đây là breakpoint của **môi trường local hiện tại**, không phải năng lực
production. Vì k6, bốn service, MariaDB và Kafka cùng chạy trên một máy,
chúng cạnh tranh CPU, RAM và I/O với nhau.

## Chạy

Khởi động stack trước, rồi chạy:

```bash
scripts/test-orders-k6.sh capacity
```

Profile mặc định:

| Nấc | VU |
|---:|---:|
| 1 | 10 |
| 2 | 25 |
| 3 | 50 |
| 4 | 100 |
| 5 | 200 |

Mỗi nấc ramp trong 10 giây và giữ 20 giây. Test dừng sớm nếu threshold
không đạt. Test này tạo order thật và làm giảm inventory thật.

Muốn chạy nhanh để kiểm tra script:

```bash
RAMP_DURATION=1s \
HOLD_DURATION=2s \
ABORT_DELAY=1s \
VUS_1=1 VUS_2=2 VUS_3=3 VUS_4=4 VUS_5=5 \
scripts/test-orders-k6.sh capacity
```

Muốn đổi profile:

```bash
MAX_P95_MS=1500 \
VUS_1=25 VUS_2=50 VUS_3=100 VUS_4=200 VUS_5=300 \
RAMP_DURATION=15s HOLD_DURATION=45s \
scripts/test-orders-k6.sh capacity
```

Mặc định VU gửi request liên tục để tìm điểm gãy của endpoint. Có thể
thêm thời gian nghỉ giữa hai order của mỗi VU:

```bash
THINK_TIME_SECONDS=1 scripts/test-orders-k6.sh capacity
```

## Đọc kết quả

Không kết luận từ `vus_max` một mình. Ghi lại cùng lúc:

- nấc VU cuối cùng hoàn thành trước khi test dừng;
- `http_reqs` theo giây, tức throughput;
- `http_req_duration p(95)`;
- `http_req_failed`;
- `checks`.

Nếu test chạy hết 200 VU và vẫn đạt threshold thì chỉ có thể kết luận
“chưa tìm thấy breakpoint trong khoảng 0–200 VU”. Tăng nấc cuối rồi chạy
lại; không gọi 200 VU là giới hạn.

Để kết quả dễ so sánh:

1. dùng cùng trạng thái database và cùng profile;
2. đóng ứng dụng nặng không liên quan;
3. chạy lại ít nhất hai lần;
4. theo dõi tài nguyên bằng `htop` và `docker stats`;
5. kiểm tra sau test rằng outbox/Kafka không tạo backlog ngày càng tăng.

API trả nhanh chưa đủ để chứng minh toàn bộ pipeline chịu tải nếu outbox
hoặc consumer vẫn đang tụt lại phía sau.

## Kết quả mẫu trên máy hiện tại

Lần chạy ngày 2026-07-29 trên máy 12 logical CPU, 15 GiB RAM, với k6 và
toàn bộ stack cùng chạy local:

| Chỉ số | Kết quả |
|---|---:|
| VU lớn nhất đã chạy | 200 |
| Request thành công | 98.851 / 98.851 |
| Throughput trung bình | 617,8 request/s |
| Response time p95 | 308 ms |
| Response time p99 | 539 ms |
| Response time lớn nhất | 1,66 s |

Kết luận cho đường đồng bộ: chưa tìm thấy breakpoint trong khoảng
0–200 VU.

Tuy nhiên, ngay sau test có 91.488 outbox message chưa publish, 91.488
reservation còn `HELD`, và mới có 9.153 notification. Vì vậy không được
kết luận hệ thống end-to-end chịu được 617,8 order/s. Nút thắt hiện tại
là outbox relay lấy tối đa 50 message mỗi nhịp và nghỉ 1 giây sau mỗi
batch; backlog lớn còn có nguy cơ làm reservation chạm TTL.

Tham khảo:

- [Grafana k6: ramping VUs](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/ramping-vus/)
- [Grafana k6: thresholds và abortOnFail](https://grafana.com/docs/k6/latest/using-k6/thresholds/)
- [Grafana k6: breakpoint test](https://grafana.com/docs/k6/latest/examples/get-started-with-k6/test-for-performance/)
