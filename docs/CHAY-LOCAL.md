# Chạy Pavex trên máy của bạn

Một trang để bật mọi thứ và biết xem ở đâu. Chi tiết hơn: [PAVEX-TAI-LIEU-TONG-HOP.md](PAVEX-TAI-LIEU-TONG-HOP.md), mục 2.

## Cần cài

Java 21, Docker Desktop, Node.js ≥ 20.9 và pnpm 11 (hai portal), Flutter 3.44 (app hiện trường).

## Lần đầu

```bash
cd pavex-backend
cp .env.example .env             # để nguyên cũng chạy được; xem chú thích trong file
./scripts/dev-up.sh              # tạo khoá, bật Docker, build và chạy 11 service (vài phút)
./scripts/seed-mini/apply.sh     # dữ liệu test: 2 bưu cục, 14 tài khoản, 2 cửa hàng

cd ../pavex-client-portal && cp .env.example .env.local && pnpm install && pnpm dev       # cổng 3000
cd ../pavex-management-portal && cp .env.example .env.local && pnpm install && pnpm dev   # cổng 3001
```

## Hằng ngày

```bash
./scripts/dev-up.sh      # bật (service đang chạy thì giữ nguyên)
./scripts/dev-down.sh    # tắt, dữ liệu vẫn giữ
```

## Mở ở đâu

| Thứ | Địa chỉ |
|---|---|
| Client portal (khách, cửa hàng) | http://localhost:3000 |
| Management portal (nhân viên) | http://localhost:3001 |
| API Gateway / Swagger | http://localhost:8080 / http://localhost:8080/swagger-ui.html |
| Email đã gửi (Mailpit) | http://localhost:8025 |
| Ảnh và tệp (MinIO, đăng nhập `pavex` / `pavex-minio`) | http://localhost:9001 |
| RabbitMQ (`pavex` / `pavex`) | http://localhost:15672 |

Tài khoản test và mật khẩu: [`scripts/seed-mini/README.md`](../scripts/seed-mini/README.md).

## Khi có lỗi

1. Xem log của service liên quan: `.local/logs/<service>.log` (ví dụ `tail -f .local/logs/auth-service.log`).
2. `bash scripts/check-setup.sh` kiểm tra Java, Docker, khoá JWT, container.
3. Vừa đổi quyền hoặc vai trò: đăng xuất rồi đăng nhập lại để lấy quyền mới.

## Đăng nhập Google

Google chỉ chấp nhận Client ID thuộc dự án Google Cloud của chính bạn:

1. https://console.cloud.google.com → chọn hoặc tạo một project.
2. **APIs & Services → OAuth consent screen**: loại *External*, điền tên app và email, để trạng thái *Testing*,
   thêm email Google bạn dùng để thử vào **Test users**.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID**, loại **Web application**.
   Ở **Authorized JavaScript origins** thêm `http://localhost:3000` và `http://localhost`. Không cần redirect URI.
4. Chép Client ID (dạng `…apps.googleusercontent.com`) vào:
   - `pavex-backend/.env`: `GOOGLE_CLIENT_ID=…`
   - `pavex-client-portal/.env.local`: `GOOGLE_CLIENT_ID=…`
5. Chạy lại backend (`./scripts/dev-down.sh && ./scripts/dev-up.sh`) và client portal (`pnpm dev`).

Luôn mở client portal bằng `http://localhost:3000` (không dùng `127.0.0.1` hay IP LAN), vì Google so khớp đúng origin.

## Email thật (tuỳ chọn)

Mặc định email vào Mailpit. Muốn gửi thật qua Gmail: bỏ dấu `#` ở khối `MAIL_*` trong `.env` và điền App Password
(Google Account → Bảo mật → Xác minh 2 bước → Mật khẩu ứng dụng), rồi chạy lại backend.
