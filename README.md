# Pavex Backend

10 service Kotlin/Spring Boot (Auth, Identity, Partners, Network, Pricing, Operations, Shipment, Billing, Fleet,
Notifications), API Gateway và Eureka.

**Tài liệu đầy đủ:** [docs/PAVEX-TAI-LIEU-TONG-HOP.md](docs/PAVEX-TAI-LIEU-TONG-HOP.md). Tài liệu gồm kiến trúc,
cách chạy, dữ liệu seed, vai trò và quyền, nghiệp vụ, màn hình, entity và API.

## Chạy cả hệ thống bằng một lệnh

Chỉ cần Docker Desktop (cấp cho Docker **ít nhất 8 GB RAM**), `git`, `curl` và OpenSSL. Không cần Java, Node hay
Flutter: backend, hai portal và app hiện trường đều được build trong Docker (hai portal và app lấy mã từ GitHub).

```bash
./pavex up        # lần đầu: sinh mật khẩu/khoá, build mọi image, chạy, nạp dữ liệu seed-mini
./pavex down      # dừng, giữ nguyên dữ liệu
./pavex reset     # xoá sạch dữ liệu rồi chạy lại với dữ liệu seed mới
./pavex logs auth-service client-portal   # xem log (bỏ tên service để xem tất cả)
./pavex ps        # trạng thái các container
```

| Ứng dụng | Địa chỉ |
|---|---|
| Client portal (khách hàng, shop) | http://localhost:3000 |
| Management portal (nhân viên) | http://localhost:3001 |
| App hiện trường (bản web) | http://localhost:3002, tải APK: `http://<IP máy trong mạng Wi-Fi>:3002/download/` |
| API Gateway | http://localhost:8080 (Swagger: http://localhost:8080/swagger-ui.html) |
| Email (Mailpit) | http://localhost:8025 |

Tài khoản test: mật khẩu `Pavex2026!` cho mọi tài khoản `@pavex.test` (danh sách ở
[scripts/seed-mini/README.md](scripts/seed-mini/README.md)), ví dụ `shop.daduyet@pavex.test` (client portal),
`admin@pavex.test` (management portal), `buuta.hn@pavex.test` (app hiện trường).
Ở stack này mọi nhân sự có ca 00:00–24:00 mỗi ngày, không ngày nghỉ, nên vào ca và nhận việc được bất cứ ngày giờ
nào; thử luồng không cần điện thoại: `scripts/sim/shop-invoice.sh`, `scripts/sim/line-haul.sh`.

Sau `git pull`: chạy lại `./pavex up` (chỉ build lại phần đã đổi). Chi tiết, cấu hình riêng của máy và lỗi thường
gặp: mục 2.0 của tài liệu.

## Chạy backend từ mã nguồn (để debug bằng IntelliJ)

Cần Java 21, Docker Desktop, `curl`, OpenSSL. **Dừng `./pavex` trước** (`./pavex down`): hai cách dùng chung cổng.

```bash
./scripts/dev-up.sh     # tạo khoá JWT, bật Docker, build, chạy mọi service theo thứ tự
./scripts/dev-down.sh   # dừng, giữ nguyên dữ liệu
```

- Gateway: http://localhost:8080 (Swagger: http://localhost:8080/swagger-ui.html)
- Mailpit: http://localhost:8025
- Log: `.local/logs/<service>.log`

Email thật, payOS, đăng nhập Google: sao chép `.env.example` thành `.env` rồi điền giá trị (xem mục 2.8 của tài liệu).

## Triển khai staging

Một VPS Linux có Docker: thư mục `deploy/`, mục 9.7 của tài liệu.
