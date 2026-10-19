# scripts/

Chạy mọi lệnh từ thư mục gốc của repo.

| Lệnh | Khi nào dùng | Làm gì |
|---|---|---|
| `./pavex up` (thư mục gốc) | Cách khuyến nghị: cả hệ thống trong Docker | Không thuộc thư mục này; xem README.md và mục 2.0 của tài liệu. Dừng nó (`./pavex down`) trước khi dùng `dev-up.sh`: hai cách dùng chung cổng. |
| `./scripts/dev-up.sh` | Khi cần debug backend bằng IntelliJ | Tạo khoá JWT nếu chưa có, thêm giá trị local còn thiếu vào `.env` (`local-env.sh`), bật Docker (DB, RabbitMQ, Redis, Mailpit, MinIO), build rồi chạy 11 service theo thứ tự và chờ từng service sẵn sàng. |
| `./scripts/dev-down.sh` | Khi xong việc | Tắt các service và Docker. **Dữ liệu DB vẫn giữ.** |
| `./scripts/seed-mini/apply.sh` | Khi cần dữ liệu test sạch | Sao lưu DB rồi thay bằng bộ dữ liệu nhỏ: 2 bưu cục, 14 tài khoản, 2 cửa hàng. Xem `seed-mini/README.md`. |
| `./pavex seed full` (`scripts/seed-full/apply.sh`) | Khi cần mạng lưới toàn quốc | Sao lưu rồi thay dữ liệu của stack `./pavex` bằng 3 trung tâm miền, 34 bưu cục tổng tỉnh, 3.320 bưu cục phường, 6.714 tuyến và ~10.700 nhân viên (mỗi bưu cục 1 quản lý). Không có cửa hàng. Xem `seed-full/README.md`. |
| `./scripts/sim/line-haul.sh [HN-BD]` | Khi muốn thử xe trung chuyển (với `./pavex up` chạy được mọi ngày giờ: ca cả ngày) | Trên dữ liệu seed-mini: nhận gửi 1 đơn tại quầy, nhân viên kiểm kho, tài xế nhận việc và quét lên xe. Chuyến xe tự được tạo; bật `FLEET_SIMULATOR_ENABLED=true` trong `.env` để xe tự chạy trên bản đồ. |
| `./scripts/sim/shop-invoice.sh` | Khi muốn thử thu cước cửa hàng | Trên dữ liệu seed-mini: cửa hàng tạo đơn Hà Nội → TP.HCM, lấy hàng, trung chuyển, giao thành công, lập bảng đối soát ra một hoá đơn để thanh toán QR. |
| `bash scripts/local-env.sh` | Hiếm khi; `dev-up.sh` đã tự gọi | Thêm vào `.env` các giá trị chỉ dùng ở local mà `application.yaml` cố ý không có mặc định: mật khẩu DB/RabbitMQ/MinIO, khoá, admin local (mật khẩu sinh ngẫu nhiên), Eureka 127.0.0.1, Swagger. Không đổi khoá đã có trong `.env`. Chạy một lần nếu chỉ dùng IntelliJ. |
| `bash scripts/check-setup.sh` | Khi chạy không lên | Chỉ kiểm tra, không thay đổi gì: Java, Docker, khoá JWT, container. |
| `bash scripts/security-bootstrap.sh` | Hiếm khi; `dev-up.sh` đã tự gọi | Tạo cặp khoá JWT còn thiếu trong `~/.pavex/keys`. Không bao giờ ghi đè khoá đã có. |
| `node --test scripts/tests/*.test.mjs` | Sau khi sửa cấu hình | Kiểm tra mỗi service chỉ có một `application.yaml` và cấu hình IntelliJ không ghi đè profile. |

## Thư mục `.local/` (chỉ có trên máy bạn, không commit)

| Thư mục | Nội dung |
|---|---|
| `.local/logs/<service>.log` | Log của từng service khi chạy bằng `dev-up.sh`. **Xem ở đây đầu tiên khi có lỗi.** |
| `.local/pids/` | Tiến trình đang chạy, để `dev-down.sh` biết tắt cái nào. |
| `.local/backups/` | Bản sao lưu DB tạo ra trước mỗi lần nạp seed. |
| `.local/tools/` | Công cụ phụ, ví dụ file jar PlantUML để vẽ sơ đồ. |

Cấu hình riêng của máy bạn (Google, email thật, payOS) nằm ở file `.env` tại thư mục gốc; xem `.env.example`.
