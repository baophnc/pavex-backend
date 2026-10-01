# scripts/

Chạy mọi lệnh từ thư mục gốc của repo.

| Lệnh | Khi nào dùng | Làm gì |
|---|---|---|
| `./scripts/dev-up.sh` | Mỗi lần bắt đầu làm việc | Tạo khoá JWT nếu chưa có, bật Docker (DB, RabbitMQ, Redis, Mailpit, MinIO), build rồi chạy 11 service theo thứ tự và chờ từng service sẵn sàng. |
| `./scripts/dev-down.sh` | Khi xong việc | Tắt các service và Docker. **Dữ liệu DB vẫn giữ.** |
| `./scripts/seed-mini/apply.sh` | Khi cần dữ liệu test sạch | Sao lưu DB rồi thay bằng bộ dữ liệu nhỏ: 2 bưu cục, 14 tài khoản, 2 cửa hàng. Xem `seed-mini/README.md`. |
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
