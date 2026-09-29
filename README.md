# Pavex Backend

8 service Kotlin/Spring Boot (Auth, Identity, Partners, Network, Pricing, Operations, Shipment, Billing),
API Gateway và Eureka.

**Tài liệu đầy đủ:** [docs/PAVEX-TAI-LIEU-TONG-HOP.md](docs/PAVEX-TAI-LIEU-TONG-HOP.md). Tài liệu gồm kiến trúc,
cách chạy, dữ liệu seed, vai trò và quyền, nghiệp vụ, màn hình, entity và API.

## Chạy nhanh

Cần Java 21, Docker Desktop, `curl`, OpenSSL.

```bash
./scripts/dev-up.sh     # tạo khoá JWT, bật Docker, build, chạy mọi service theo thứ tự
./scripts/dev-down.sh   # dừng, giữ nguyên dữ liệu
```

- Gateway: http://localhost:8080 (Swagger: http://localhost:8080/swagger-ui.html)
- Mailpit: http://localhost:8025
- Log: `.local/logs/<service>.log`

Email thật, payOS, đăng nhập Google: sao chép `.env.example` thành `.env` rồi điền giá trị (xem mục 2.8 của tài liệu).
