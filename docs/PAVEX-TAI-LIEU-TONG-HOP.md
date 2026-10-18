# Pavex — Tài liệu tổng hợp

> Cập nhật ngày 18/10/2026. Đây là tài liệu duy nhất của dự án: kiến trúc, cách chạy, dữ liệu seed, vai trò và quyền,
> nghiệp vụ, màn hình, entity và thuộc tính, API.
> Bảng entity, enum và API ở mục 6 được trích tự động từ mã nguồn ngày 29/09/2026, nên chưa có Notifications và các
> bảng, API thêm sau ngày đó. Nếu lệch với code thì code đúng (nguồn gốc: các class `@Entity` và `@RestController`
> trong từng service).

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Cách chạy hệ thống và dữ liệu seed](#2-cách-chạy-hệ-thống-và-dữ-liệu-seed)
3. [Vai trò, quyền và phạm vi dữ liệu](#3-vai-trò-quyền-và-phạm-vi-dữ-liệu)
4. [Nghiệp vụ theo từng mảng](#4-nghiệp-vụ-theo-từng-mảng)
5. [Màn hình của hai portal](#5-màn-hình-của-hai-portal)
6. [Entity, thuộc tính, enum và API theo service](#6-entity-thuộc-tính-enum-và-api-theo-service)
7. [Migration database](#7-migration-database)
8. [Giới hạn hiện tại](#8-giới-hạn-hiện-tại)
9. [Triển khai thật](#9-triển-khai-thật)

---

## 1. Tổng quan

Pavex Express là hệ thống giao vận cho shop online: shop tạo vận đơn và in nhãn, bưu tá lấy hàng rồi chuyển qua mạng
lưới bưu cục toàn quốc, giao cho người nhận và thu tiền, sau đó Pavex đối soát tiền thu hộ (COD) với shop.

### 1.1 Bốn repo

| Repo | Đường dẫn | Nội dung |
|---|---|---|
| `pavex-backend` | `~/IdeaProjects/pavex-backend` | 10 service nghiệp vụ Kotlin/Spring Boot 4, API Gateway, Eureka, thư viện dùng chung (`libs/`), script (`scripts/`) và tài liệu này |
| `pavex-client-portal` | `~/Documents/GitHub/pavex-client-portal` | Website cho khách hàng và shop (Next.js 16, pnpm) |
| `pavex-management-portal` | `~/Documents/GitHub/pavex-management-portal` | Trang quản trị cho nhân viên Pavex (Next.js 16, pnpm). Bưu tá làm việc trên app hiện trường |
| `pavex-warehouse` | `~/Documents/GitHub/pavex-warehouse` | App hiện trường cho bưu tá, nhân viên bưu cục, trưởng bưu cục và tài xế trung chuyển (Flutter) |

### 1.2 Quy mô

| Hạng mục | Số lượng |
|---|---|
| Service | 10 service nghiệp vụ + API Gateway + Eureka |
| Entity (bảng) | 81 |
| Enum | 98 |
| API | 333 (290 qua Gateway, 43 nội bộ giữa các service) |
| Trang client portal | 37 |
| Trang management portal | 55 |

Đếm từ code ngày 18/10/2026: class `@Entity`, `enum class` và hàm `@Get/Post/Put/Patch/DeleteMapping` trong 10
service (API nội bộ là đường dẫn `/internal/...`); trang là file `page.tsx` trên nhánh `develop` của từng portal
(management portal không tính trang gốc `/` chỉ để chuyển hướng).

### 1.3 Kiến trúc

```text
Trình duyệt ──▶ Client portal :3000 ─────┐   Next.js làm BFF: giữ token trong cookie httpOnly,
Trình duyệt ──▶ Management portal :3001 ─┤   trình duyệt không bao giờ thấy token
App hiện trường (Flutter) ──────────────┤   gọi thẳng Gateway, token nằm trong keychain của máy
                                         ▼
                          API Gateway :8080  (định tuyến qua Eureka, CORS, giới hạn tần suất bằng Redis)
                                         ▼
  Auth · Identity · Partners · Network · Pricing · Operations · Shipment · Billing · Fleet · Notifications
     │  mỗi service một PostgreSQL riêng (cổng 5433–5442)
     └─ RabbitMQ: email, đồng bộ vai trò, sự kiện vận hành, sự kiện tiền (Shipment → Billing)
```

| Service | Làm gì | Gọi sang |
|---|---|---|
| **Auth** | Đăng ký, đăng nhập (mật khẩu, Google), phiên và refresh token, xác thực email, quên mật khẩu, lời mời nhân viên | Identity (lấy quyền khi phát token) |
| **Identity** | Tài khoản, hồ sơ cá nhân, vai trò, quyền, khoá/mở tài khoản | Auth (thu hồi phiên khi khoá tài khoản) |
| **Partners** | Danh mục tỉnh/phường, sổ địa chỉ, hồ sơ shop, điểm lấy hàng, yêu cầu hỗ trợ | Identity (điều kiện làm shop, đồng bộ vai trò MERCHANT) |
| **Network** | Vùng, bưu cục/kho, vùng phục vụ, tuyến, lịch chạy, hành trình, giữ chỗ trên tuyến | — |
| **Pricing** | Bảng giá, tính cước, báo giá | Network (địa chỉ thuộc vùng nào, vùng xa/hải đảo) |
| **Operations** | Nhân sự vận hành, thành viên bưu cục, ca làm, phân công việc | Identity, Network |
| **Shipment** | Vận đơn, kiện, nhãn, quét kiện, giao hàng, thu tiền khi giao, ngoại lệ, sự cố, giám sát | Partners, Pricing, Network, Operations; gửi sự kiện cho Billing |
| **Billing** | Tiền bưu tá thu, phiếu nộp tiền, đối soát COD, hoá đơn cước, thanh toán payOS | Partners, Shipment, Operations |
| **Fleet** | Chuyến xe trung chuyển giữa các hub: vị trí GPS do app tài xế gửi, lộ trình, trễ và mất tín hiệu | Operations (việc LINE_HAUL, bưu cục của người xem), Network (tuyến, giờ đi/đến) |
| **Notifications** | Hộp thông báo của từng người: tạo từ sự kiện vận đơn, phân công và nộp tiền (RabbitMQ), và tin nhắn nhân viên gửi cho người của một bưu cục hoặc cho shop | Operations (ai làm ở bưu cục nào) |

### 1.4 Nguyên tắc kỹ thuật chung

- **Mỗi service sở hữu database của mình.** Không service nào đọc database của service khác. Khi cần dữ liệu thì gọi
  API nội bộ `/internal/v1/...` bằng **JWT dịch vụ** (`typ=service`, `aud` là service nhận, `scope` là đúng việc được phép).
  Mỗi service có cặp khoá RSA riêng trong `~/.pavex/keys/<service>/`, tạo bằng `scripts/security-bootstrap.sh`
  (`dev-up.sh` tự gọi). Khoá riêng không bao giờ commit.
- **Token người dùng:** Auth phát access token chứa quyền lấy từ Identity. Identity giữ "phiên bản quyền" của từng tài
  khoản: đổi vai trò hoặc khoá tài khoản thì token cũ hết hiệu lực. Sau khi đổi quyền, người dùng cần đăng nhập lại.
- **Quyền khác phạm vi dữ liệu.** Quyền quyết định *được làm gì*; phạm vi quyết định *trên những dòng nào* (mục 3.2).
  Ẩn/hiện menu ở portal chỉ là giao diện; backend luôn kiểm tra lại cả hai.
- **Outbox + RabbitMQ** cho mọi việc phải xảy ra sau khi dữ liệu đã lưu: email, đồng bộ vai trò MERCHANT, sự kiện
  vận hành, sự kiện `shipment.cash-collected` và `shipment.closed` sang Billing. Bên nhận ghi lại message đã xử lý để
  không xử lý trùng.
- **Chống ghi trùng và ghi đè:** thao tác tạo quan trọng (tạo vận đơn, ghi nhận giao hàng, nộp tiền…) dùng khoá
  idempotency; entity quan trọng có `@Version` để hai người sửa cùng lúc không đè nhau.
- **Tiền** lưu bằng số nguyên VND (`Long`), không dùng số thực. Cước làm tròn lên bội số 1.000 ₫.
- **Schema** do Flyway quản lý (`src/main/resources/db/migration`), Hibernate chỉ `validate`. Không sửa migration đã
  chạy; muốn đổi thì thêm file `V<n+1>__...sql`.
- **Cấu hình:** mỗi service đúng một `src/main/resources/application.yaml`, không có profile. Biến môi trường ghi đè giá
  trị trong YAML (mục 2.8). Eureka quảng bá `127.0.0.1`, nên cấu hình hiện tại chỉ dành cho chạy trên một máy.
- **Thời gian** lưu UTC, service dùng `Clock` được inject để test được. API phân trang đánh số từ 1.
- **Số điện thoại** Việt Nam dùng chung một bộ chuẩn hoá (`libs/platform-common/.../contact/VietnamesePhone.kt`):
  chấp nhận `+84`, `84`, dấu cách, chấm, gạch, ngoặc; lưu dạng `0xxxxxxxxx` (10 số).

---

## 2. Cách chạy hệ thống và dữ liệu seed

### 2.1 Bản đồ cổng

**Hạ tầng (Docker Compose trong `pavex-backend/compose.yml`):**

| Thành phần | Cổng | Ghi chú |
|---|---|---|
| auth-db | 5433 | database `pavex_auth` |
| identity-db | 5434 | `pavex_identity` |
| partners-db | 5435 | `pavex_partners` |
| network-db | 5436 | `pavex_network` |
| pricing-db | 5437 | `pavex_pricing` |
| shipment-db | 5438 | `pavex_shipments` |
| operations-db | 5439 | `pavex_operations` |
| billing-db | 5440 | `pavex_billing` |
| fleet-db | 5441 | `pavex_fleet` |
| Redis (rate limit của gateway) | 6379 | |
| RabbitMQ | 5672, giao diện 15672 | đăng nhập `pavex` / `pavex` |
| Mailpit (hộp thư giả) | SMTP 1025, giao diện **http://localhost:8025** | mọi email xác thực, quên mật khẩu, xác nhận yêu cầu hỗ trợ đều vào đây |

User/mật khẩu của mọi database: `pavex` / `pavex`.

**Backend:**

| Service | Cổng | Chức năng chính |
|---|---|---|
| Discovery (Eureka) | 8761 | Đăng ký service |
| Identity | 8082 | Người dùng, vai trò, quyền |
| Auth | 8081 | Đăng nhập, token, phiên, email xác thực |
| Partners | 8083 | Tỉnh/phường, merchant, địa chỉ, điểm lấy hàng, **yêu cầu hỗ trợ** |
| Network | 8085 | Vùng, bưu cục, tuyến, lịch chạy, sức chứa |
| Pricing | 8086 | Bảng giá, báo giá |
| Operations | 8087 | Nhân sự, ca làm, phân công |
| Shipment | 8084 | Vận đơn, quét kiện, giao hàng, thu tiền |
| Billing | 8088 | Tiền mặt bưu tá nộp, đối soát, hoá đơn, payOS |
| Fleet | 8089 | Chuyến xe trung chuyển, vị trí GPS |
| **API Gateway** | **8080** | Cửa vào duy nhất cho hai frontend; Swagger: **http://localhost:8080/swagger-ui.html** |

**Frontend:**

| Ứng dụng | Cổng |
|---|---|
| Client portal (khách hàng, shop) | **http://localhost:3000** |
| Management portal (nhân viên) | **http://localhost:3001** (phải chỉ định cổng, xem mục 2.4.2) |
| App hiện trường (Flutter) | Chạy trên iPhone/Android hoặc simulator, xem mục 2.4.4 |

---

### 2.2 Cài đặt cần có

- Java 21, Docker Desktop, `curl`, OpenSSL (backend)
- Node.js ≥ 20.9 và pnpm 11 (hai frontend)
- Tuỳ chọn: IntelliJ IDEA (repo backend có sẵn cấu hình chạy trong `.run/`)

---

### 2.3 Chạy backend

Mọi lệnh chạy trong thư mục `pavex-backend`.

#### 2.3.1 Lần đầu trên một máy

```bash
cp .env.example .env               # cấu hình riêng của máy (Google, email thật, payOS); để nguyên cũng chạy được
./scripts/dev-up.sh                # tạo khoá JWT, thêm giá trị local vào .env, bật container, build và chạy mọi service
./scripts/seed-mini/apply.sh       # nạp dữ liệu test nhỏ: 2 bưu cục, 14 tài khoản (mục 2.5)
bash scripts/check-setup.sh        # khi chạy không lên: kiểm tra khoá, container và module
```

#### 2.3.2 Chạy toàn bộ bằng một lệnh (khuyến nghị)

```bash
./scripts/dev-up.sh
```

Script này làm lần lượt:
1. Tạo khoá JWT còn thiếu, thêm vào `.env` các giá trị chỉ dùng ở local còn thiếu (`scripts/local-env.sh`: mật khẩu
   DB/RabbitMQ/MinIO, khoá, admin local, Eureka 127.0.0.1, Swagger, bảng giá demo) và bật container (`docker compose up -d --wait`): 9 PostgreSQL, Redis, RabbitMQ, Mailpit, MinIO.
2. Build lại toàn bộ: `./mvnw -DskipTests clean install`. Vì vậy mọi thay đổi code đều được áp dụng.
3. Chạy các service theo thứ tự: Discovery → Identity → Auth → Partners → Network → Pricing →
   Operations → Shipment → Billing → Fleet → Gateway. Mỗi service phải báo healthy thì mới chạy service tiếp theo.
4. Flyway tự áp các migration còn thiếu khi mỗi service khởi động.

Log nằm ở `.local/logs/<tên-service>.log`, PID ở `.local/pids/`.

Kiểm tra nhanh sau khi chạy xong:

```bash
curl -s http://localhost:8080/actuator/health
```

#### 2.3.3 Dừng

```bash
./scripts/dev-down.sh
```

Script dừng các service rồi chạy `docker compose down`. **Dữ liệu vẫn được giữ** vì volume không bị xoá.
Nếu chỉ muốn xem dữ liệu mà không chạy app, bật riêng database: `docker compose up -d --wait`.

#### 2.3.4 Chạy từng service bằng IntelliJ

1. Reload Maven project.
2. Chạy các cấu hình **PAVEX - …** trong `.run/` theo đúng thứ tự ở mục 2.3.2. Mỗi service tự đọc `.env` ở thư mục gốc,
   nên không cần plugin EnvFile. Chưa từng chạy `dev-up.sh` trên máy này thì chạy một lần `bash scripts/local-env.sh`;
   thiếu bước này service dừng ngay với lỗi `Missing required settings: …`.
3. Đợi Identity và Auth khởi động xong rồi mới đăng nhập.

`.run/` hiện **chưa có cấu hình cho Billing và Fleet**. Chạy hai service này bằng `dev-up.sh`, hoặc
`java -jar billing/target/billing-service-0.0.1-SNAPSHOT.jar` (và `fleet/target/fleet-service-0.0.1-SNAPSHOT.jar`) sau khi build.

#### 2.3.5 Migration mới thêm

Các migration dưới đây được thêm ngày 27–29/09/2026. Database tạo trước đó sẽ tự áp chúng ở lần khởi động kế tiếp:

| Service | Migration | Nội dung |
|---|---|---|
| identity | `V15__support_request_permissions.sql` | Quyền `support.requests.read` / `.manage` cho ADMIN và Kinh doanh & CSKH |
| partners | `V7__create_support_requests.sql` | Bảng yêu cầu hỗ trợ, tệp đính kèm, lịch sử xử lý |
| shipment | `V16__charge_cancellation_after_pickup.sql` | Đơn huỷ sau khi đã lấy hàng vẫn tính cước cho merchant |
| identity | `V16__fleet_trip_permissions.sql` | Quyền `fleet.trips.report-own` (tài xế) và `fleet.trips.read-hub` (trưởng bưu cục, vận hành miền, quản trị) |
| fleet | `V1__create_fleet.sql` | Database mới `pavex_fleet`: chuyến xe, điểm GPS chia bảng theo ngày, đồng ý chia sẻ vị trí, nhật ký xem lộ trình |

Sau khi khởi động lại, **đăng xuất rồi đăng nhập lại** các tài khoản ADMIN, Kinh doanh & CSKH, trưởng bưu cục,
vận hành miền và tài xế để token nhận quyền mới.

#### 2.3.6 Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Gateway báo `UnknownHostException` khi đăng nhập | Dừng các JVM, chạy lại Discovery trước để xoá registry cũ, rồi chạy lại các service và Gateway |
| Cổng đang bị chiếm | Chạy `./scripts/dev-down.sh`, hoặc `lsof -i :<cổng>` để tìm tiến trình cũ |
| Không nhận được email | Mở Mailpit ở http://localhost:8025. Email chỉ ra hộp thư thật khi bạn bật khối `MAIL_*` trong `.env` |

**Không xoá thư mục `.local/`:** trong đó có log của từng service và bản sao lưu DB trước mỗi lần seed (xem `scripts/README.md`).

#### 2.3.7 Giới hạn tần suất theo IP khi deploy

Gateway giới hạn số request theo IP của người gọi (Redis, 30 req/s, tối đa 60 một lúc). Trình duyệt không
gọi thẳng Gateway mà đi qua server Next.js của hai portal, nên cơ chế hoạt động như sau:

- Hai portal chuyển tiếp header `X-Forwarded-For` mà ingress/load balancer đặt, tức địa chỉ trình duyệt
  (`lib/api/client.ts` ở client portal; `lib/api/backend.ts` và proxy `app/api/backend` ở management portal).
- Gateway chỉ tin header này khi request đến từ một địa chỉ nằm trong `GATEWAY_TRUSTED_PROXIES`.

| Môi trường | Cấu hình |
|---|---|
| Local | Để trống `GATEWAY_TRUSTED_PROXIES` (mặc định). Mọi request được tính theo IP máy chạy Next.js; với một người dev thì không sao |
| Deploy thật | Đặt `GATEWAY_TRUSTED_PROXIES` bằng địa chỉ hoặc CIDR của các server Next.js, ví dụ `10.0.1.0/24`. Ingress phía trước Next.js phải **nối thêm** IP client vào `X-Forwarded-For`, không được để nguyên giá trị trình duyệt gửi lên |

Nếu thiếu cấu hình này, mọi khách dùng chung một hạn mức theo IP của server Next.js. Khi đó form hỗ trợ
công khai chỉ còn được bảo vệ bằng ô bẫy bot và giới hạn 5 yêu cầu mỗi giờ cho mỗi email.

---

### 2.4 Chạy frontend

**Chạy backend trước** và đợi Gateway (8080) healthy. Frontend chỉ gọi Gateway, không gọi thẳng service nào.

#### 2.4.1 Client portal (khách hàng, shop)

```bash
cd ~/Documents/GitHub/pavex-client-portal
pnpm install
cp .env.example .env.local   # chỉ lần đầu, nếu chưa có .env.local
pnpm dev                     # http://localhost:3000
```

`.env.local` cần có:

```bash
API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_SITE_URL=http://localhost:3000
GOOGLE_CLIENT_ID=<lấy nguyên giá trị trong .env.example>
```

Muốn chạy bằng Docker: `docker compose up -d --build`. Khi đó `API_BASE_URL` mặc định là
`http://host.docker.internal:8080`.

#### 2.4.2 Management portal (nhân viên)

```bash
cd ~/Documents/GitHub/pavex-management-portal
pnpm install
pnpm dev -p 3001             # http://localhost:3001
```

Cả hai portal đều mặc định dùng cổng 3000, nên management portal phải chạy với `-p 3001`.
`API_BASE_URL` mặc định là `http://localhost:8080`; chỉ cần đặt lại nếu Gateway chạy ở địa chỉ khác.

Chỉ tài khoản nhân viên mới đăng nhập được. Riêng bưu tá chỉ thấy mục "Giao hàng".

#### 2.4.3 Kiểm tra code trước khi commit

```bash
pnpm check                   # lint + typecheck, chạy trong từng portal
./mvnw -pl <module> -am test # test backend, ví dụ -pl shipment
flutter analyze && flutter test  # app hiện trường
```

#### 2.4.4 App hiện trường (Flutter)

```bash
cd ~/Documents/GitHub/pavex-warehouse
flutter pub get
flutter run                  # chọn iPhone, simulator hoặc Android
```

- Ở màn đăng nhập, bấm **Máy chủ** để trỏ tới Gateway: simulator iOS và macOS dùng `http://localhost:8080`,
  Android emulator dùng `http://10.0.2.2:8080`, điện thoại thật dùng `http://<IP máy Mac>:8080` (cùng Wi‑Fi,
  và cho phép Java nhận kết nối nếu tường lửa macOS hỏi).
- iPhone thật: mở `ios/Runner.xcworkspace` bằng Xcode, chọn Team (Apple ID cá nhân) ở Signing rồi chạy.
- GPS cho chuyến xe: thử trên simulator bằng Features → Location → Freeway Drive (iOS) hoặc Extended controls →
  Location → Routes (Android). Bản macOS không có GPS.

---

### 2.5 Dữ liệu seed

Dữ liệu test là **seed-mini**: 2 bưu cục (Ba Đình, Hà Nội và Tân Sơn Hoà, TP.HCM) nối bằng tuyến trục, 14 tài khoản
`@pavex.test` cho mọi vai trò (mật khẩu chung), 1 cửa hàng đã duyệt và 1 cửa hàng chờ duyệt. Không có sẵn vận đơn:
tạo khi test.

Danh sách tài khoản, mật khẩu và chi tiết mạng lưới: [`scripts/seed-mini/README.md`](../scripts/seed-mini/README.md).

Tài khoản `admin@pavex.com` (tự tạo khi khởi động) dùng mật khẩu `PAVEX_BOOTSTRAPADMIN_PASSWORD` trong `.env`:
`scripts/local-env.sh` sinh ngẫu nhiên cho từng máy. Muốn đổi thì sửa giá trị đó rồi khởi động lại Auth.

---

### 2.6 Seed lại hoặc thêm dữ liệu

| Lệnh (chạy trong `pavex-backend`) | Tác dụng | Lưu ý |
|---|---|---|
| `./scripts/seed-mini/apply.sh` | Xoá mạng lưới, nhân sự, cửa hàng, vận đơn, báo giá, tiền, chuyến xe; nạp lại seed-mini rồi chạy `dev-up.sh` | **Xoá và thay dữ liệu hiện có.** Luôn sao lưu vào `.local/backups/<thời điểm>-seed-mini/` trước. Thêm `--no-start` để không khởi động lại |

**Chạy thử lại mà không seed lại:** đặt `PAVEX_DEMO_RESET_ENABLED=true` trong `.env` rồi chạy `dev-up.sh`. Quản trị
(vai trò `ADMIN`) mở **Hệ thống → Giả lập → Đặt lại dữ liệu thử** ở management portal, gõ `DAT LAI` rồi bấm nút.

- Bị xoá: vận đơn, kiện, lượt quét, giao hàng, sự cố (kèm ảnh chứng từ trong MinIO); việc đã phân công và sự kiện
  điều phối chờ gửi; chỗ giữ trên tuyến; báo giá; chuyến xe và điểm GPS; tiền thu, phiếu nộp, đối soát, hoá đơn;
  thông báo; sự kiện còn trong hàng đợi RabbitMQ.
- Giữ nguyên: tài khoản, cửa hàng và điểm lấy hàng, mạng lưới, bảng giá, nhân sự, ca làm và trạng thái vào ca, tài khoản
  nhận tiền của cửa hàng, đồng ý chia sẻ vị trí của tài xế. Không ai bị đăng xuất, không cần khởi động lại backend.
- Shipment được xoá trước, rồi lần lượt Operations, Network, Pricing, Fleet, Billing, Notifications (mỗi service tự xoá
  dữ liệu của mình qua `POST .../demo-reset`). Khi cờ tắt (mặc định), các endpoint này không tồn tại (404).
- App tài xế đang gửi GPS cho chuyến đã xoá sẽ tự dừng.

Khôi phục một database từ backup, ví dụ shipment:

```bash
docker exec -i pavex-backend-shipment-db-1 \
  pg_restore -U pavex -d pavex_shipments --clean --if-exists \
  < .local/backups/20260925-170608/shipment.dump
```

Thay `shipment` / `pavex_shipments` bằng service và database tương ứng ở bảng mục 2.1.
Nên dừng các service trước khi khôi phục.

---

### 2.7 Thử nhanh theo từng vai trò

**Kịch bản kiểm thử chi tiết từng bước trên cả 3 app:** [KIEM-THU-LUONG-TREN-3-APP.md](KIEM-THU-LUONG-TREN-3-APP.md).

Danh sách tài khoản thử và mật khẩu ở `scripts/seed-mini/README.md`. Tóm tắt một luồng đầy đủ:

1. **Cửa hàng:** `shop.daduyet@pavex.test` đã được duyệt sẵn, có điểm lấy hàng ở Phường Ba Đình. Muốn thử luồng
   duyệt: dùng `shop.choduyet@pavex.test`, rồi duyệt bằng `cskh@pavex.test` ở management :3001.
2. **Tạo vận đơn:** đăng nhập `shop.daduyet@pavex.test` ở client portal :3000, tạo vận đơn có COD gửi vào TP.HCM, in nhãn.
3. **Lấy hàng và nhập kho:** bưu tá Ba Đình check-in ca trong app, nhận việc lấy hàng rồi quét Lấy hàng. Nhân viên
   hoặc trưởng bưu cục Ba Đình quét Nhập kho, không cần bấm Nhận việc trước.
4. **Trung chuyển:** `taixe@pavex.test` check-in ca, nhận việc trung chuyển, quét Lên xe (chuyến tự bắt đầu và gửi
   GPS). Nhân viên Tân Sơn Hoà quét Nhập kho khi xe tới.
5. **Giao và tiền:** tiếp tục tới bưu cục phát; bưu tá giao, thu tiền, nộp tiền; kế toán duyệt và đối soát.

Đơn gửi và giao trong cùng một bưu cục (ví dụ trong Phường Ba Đình) không có bước 4: nhập kho ở bước 3 xong là việc
giao hàng mở cho bưu tá, bưu tá quét Xuất giao ngay từ kho đó.

Email phát sinh khi thử (xác thực, xác nhận yêu cầu hỗ trợ…) xem ở Mailpit: http://localhost:8025.

---

### 2.8 Bật chức năng thật: email, thanh toán payOS, đăng nhập Google

Mặc định mọi thứ chạy giả lập: email vào Mailpit, thanh toán dùng cổng `fake` (QR giả, admin bấm xác nhận).
Muốn dùng thật, điền biến vào **`.env` ở thư mục gốc backend** rồi chạy lại `./scripts/dev-up.sh`.

- **Mẫu và giải thích từng biến:** `.env.example`. Chạy `cp .env.example .env` để bắt đầu.
- **Cách nạp:** mỗi service tự đọc `.env` ở thư mục gốc, dù chạy bằng `dev-up.sh` hay IntelliJ.
  - Mỗi dòng `KEY=giá-trị`, không dùng ngoặc kép, **không để trống `KEY=`** (muốn dùng mặc định thì giữ dấu `#`).
  - Biến đã `export` trong shell được ưu tiên hơn file, ví dụ tạm quay về thanh toán giả:
    `BILLING_PAYMENT_PROVIDER=fake ./scripts/dev-up.sh`.
- **Không commit `.env`** (đã có trong `.gitignore`); chỉ commit `.env.example`.

| Chức năng | Service | Biến chính |
|---|---|---|
| Email thật | auth, partners | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM`; link trong email: `EMAIL_VERIFICATION_URL`, `PASSWORD_RESET_URL`, `MANAGEMENT_PASSWORD_RESET_URL`, `INVITATION_URL` |
| Thanh toán payOS | billing | `BILLING_PAYMENT_PROVIDER=payos`, `PAYOS_ENABLED=true`, `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL`, `BILLING_RECONCILIATION_ENABLED` |
| Đăng nhập Google | auth + client portal | `GOOGLE_CLIENT_ID` (cùng một giá trị ở `.env` backend và `.env.local` client portal) |

**Lưu ý với payOS:**
- `BILLING_PAYMENT_PROVIDER=payos` nghĩa là **QR chuyển khoản thật**. Thử ở local sẽ phát sinh giao dịch thật.
- **Webhook phải đăng ký trong trang quản lý payOS:** `https://<domain gateway>/api/v1/integrations/payos/webhook`.
  payOS không gọi được `localhost`: ở local, dùng tunnel (ngrok, cloudflared), hoặc bật
  `BILLING_RECONCILIATION_ENABLED=true` để Billing định kỳ hỏi lại trạng thái.
- **Thời lượng viết theo ISO-8601:** `PT15S` = 15 giây, `PT2M` = 2 phút. Định dạng `00:00:15` của .NET sẽ làm Billing lỗi khi khởi động.

**Khi deploy thật:** danh sách biến đầy đủ ở mục 9 (Triển khai thật).

---

## 3. Vai trò, quyền và phạm vi dữ liệu

### 3.1 Mười vai trò hệ thống

Identity sở hữu danh mục vai trò và quyền; Auth chỉ đọc lại khi phát token.

| Vai trò | Tên | Mô tả | Quyền |
|---|---|---|---|
| `ADMIN` | Quản trị hệ thống | Toàn quyền, gồm cấp tài khoản và vai trò | toàn bộ quyền |
| `COMMERCIAL_MANAGER` | Kinh doanh & CSKH | Duyệt shop, quản lý bảng giá và xử lý khiếu nại trên toàn quốc | `network.read`, `partners.merchants.manage-status`, `partners.merchants.read`, `pricing.manage`, `pricing.read`, `scope.all-hubs`, `shipments.admin.read`, `shipments.cases.manage`, `shipments.cases.read`, `shipments.exceptions.read`, `support.requests.manage`, `support.requests.read` |
| `COURIER` | Bưu tá | Lấy hàng, giao hàng và thu tiền theo việc được giao | `assignments.accept-own`, `assignments.execute-own`, `assignments.read-own`, `billing.cash.collect`, `billing.cash.submit-own`, `deliveries.complete-own`, `scans.create`, `shipments.read-assigned` |
| `CUSTOMER` | Khách cá nhân | Tài khoản khách hàng mặc định | — (chỉ dữ liệu của chính mình) |
| `FINANCE_ACCOUNTANT` | Kế toán | Duyệt tiền nộp, đối soát và hóa đơn trên toàn quốc | `billing.cash.approve`, `billing.cash.read-hub`, `billing.manage`, `partners.merchants.read`, `scope.all-hubs`, `shipments.admin.read` |
| `HUB_MANAGER` | Trưởng bưu cục | Điều phối, nhân sự, vận đơn và tiền thu hộ của bưu cục được phân công | `assignments.accept-own`, `assignments.execute-own`, `assignments.read-own`, `billing.cash.collect`, `billing.cash.read-hub`, `fleet.trips.read-hub`, `operations.assignments.read`, `operations.dispatch.manage`, `operations.hub.manage`, `operations.hub.read`, `operations.workforce.read`, `scans.create`, `shipments.cases.manage`, `shipments.cases.read`, `shipments.control-tower.read`, `shipments.exceptions.manage`, `shipments.exceptions.read`, `shipments.read-hub`, `shipments.weight.adjust` |
| `LINE_HAUL_DRIVER` | Tài xế trung chuyển | Chạy các chuyến giữa bưu cục và kho theo việc được giao | `assignments.accept-own`, `assignments.execute-own`, `assignments.read-own`, `fleet.trips.report-own`, `scans.create`, `shipments.read-assigned` |
| `MERCHANT` | Shop | Tạo và theo dõi vận đơn, hóa đơn của shop mình | `billing.invoices.pay-own`, `billing.invoices.read-own`, `partners.merchants.manage-own`, `shipments.cancel-own`, `shipments.create`, `shipments.read-own` |
| `OPERATIONS_MANAGER` | Quản lý vận hành | Vận đơn, sự cố, nhân sự và mạng lưới trên toàn quốc | `fleet.trips.read-hub`, `network.manage`, `network.read`, `operations.assignments.manage`, `operations.assignments.read`, `operations.dispatch.manage`, `operations.hub.manage`, `operations.hub.read`, `operations.workforce.manage`, `operations.workforce.read`, `partners.merchants.read`, `pricing.read`, `scope.all-hubs`, `shipments.admin.read`, `shipments.cases.manage`, `shipments.cases.read`, `shipments.control-tower.read`, `shipments.exceptions.manage`, `shipments.exceptions.read`, `shipments.weight.adjust` |
| `WAREHOUSE_OPERATOR` | Nhân viên bưu cục | Quét nhập/xuất kho và nhận tiền thu hộ tại bưu cục được phân công | `assignments.accept-own`, `assignments.execute-own`, `assignments.read-own`, `billing.cash.collect`, `billing.cash.read-hub`, `operations.hub.read`, `scans.create`, `shipments.exceptions.read`, `shipments.read-hub` |

<details>
<summary>Danh mục 51 quyền</summary>

| Quyền | Ý nghĩa |
|---|---|
| `assignments.accept-own` | Accept own assignments |
| `assignments.execute-own` | Execute own operational assignments |
| `assignments.read-own` | Read own assignments |
| `billing.cash.approve` | Approve or reject cash collections and remittances |
| `billing.cash.collect` | Collect cash for customer invoices |
| `billing.cash.read-hub` | Read cash held at assigned hubs |
| `billing.cash.submit-own` | Submit own courier cash remittances |
| `billing.invoices.pay-own` | Pay own customer invoices |
| `billing.invoices.read-own` | Read own customer invoices |
| `billing.manage` | Manage customer invoices and payments |
| `deliveries.complete-own` | Complete own deliveries |
| `fleet.trips.read-hub` | Watches line-haul trips leaving or arriving at the hubs in scope, with their GPS track |
| `fleet.trips.report-own` | Starts and ends own line-haul trips and sends their GPS positions |
| `identity.roles.assign` | Assign roles to users |
| `identity.roles.manage` | Create, update and delete custom roles and manage their permission matrix |
| `identity.roles.read` | Read roles |
| `identity.users.create` | Create users |
| `identity.users.invite` | Invite users |
| `identity.users.manage-profile` | Manage user profiles |
| `identity.users.manage-status` | Manage user account status |
| `identity.users.read` | Read users |
| `network.manage` | Manage logistics network |
| `network.read` | View network regions, hubs, service areas, coverages and audit records |
| `operations.assignments.manage` | Manage operational assignments |
| `operations.assignments.read` | View operational assignments within the actor data scope |
| `operations.dispatch.manage` | Assign, reassign and cancel operational work within the actor hub scope |
| `operations.hub.manage` | Manage workforce shifts and assignments within hubs |
| `operations.hub.read` | View operational data for hubs within the actor data scope |
| `operations.workforce.manage` | Manage operations workforce and hub memberships |
| `operations.workforce.read` | View workforce records within the actor data scope |
| `partners.merchants.manage-own` | Manage own merchant profile |
| `partners.merchants.manage-status` | Manage merchant verification and status |
| `partners.merchants.read` | Read merchant profiles |
| `pricing.manage` | Create and edit draft pricing rate plans and rate rules |
| `pricing.read` | View pricing rate plans and rate rules |
| `scans.create` | Create parcel scans |
| `scope.all-hubs` | Sees and handles data of every hub instead of only the hubs the user is assigned to |
| `shipments.admin.read` | View shipment operations, parcel snapshots and shipment timelines across merchants |
| `shipments.cancel-own` | Cancel own shipments |
| `shipments.cases.manage` | Manage shipment operational cases |
| `shipments.cases.read` | View shipment cases within the actor data scope |
| `shipments.control-tower.read` | Read shipment control tower alerts |
| `shipments.create` | Create shipments |
| `shipments.exceptions.manage` | Manage shipment exception workflows |
| `shipments.exceptions.read` | View shipment exceptions within the actor data scope |
| `shipments.read-assigned` | View shipments attached to operational assignments owned by the actor |
| `shipments.read-hub` | View shipments currently associated with hubs in the actor data scope |
| `shipments.read-own` | View shipments owned by the authenticated merchant or customer context |
| `shipments.weight.adjust` | Adjust parcel weights |
| `support.requests.manage` | Takes, resolves and closes customer requests (V15) |
| `support.requests.read` | Views customer contact, support, claim and career requests (V15) |

</details>

- Ma trận quyền của 10 vai trò hệ thống được khoá, không sửa được trên giao diện. Có thể tạo vai trò tuỳ chỉnh từ danh
  mục quyền, nhưng phạm vi dữ liệu vẫn do service kiểm tra.
- Không khoá hoặc gỡ được quyền của quản trị viên cuối cùng.
- Shop có đồng thời vai trò MERCHANT và CUSTOMER. Vai trò MERCHANT được cấp tự động khi hồ sơ shop được duyệt và bị gỡ
  khi shop ngừng hoạt động.

### 3.2 Phạm vi dữ liệu

| Nhóm | Thấy dữ liệu nào |
|---|---|
| Văn phòng: ADMIN, OPERATIONS_MANAGER, COMMERCIAL_MANAGER, FINANCE_ACCOUNTANT | Toàn quốc, nhờ quyền `scope.all-hubs`. Service không bao giờ suy ra "toàn quốc" từ tên vai trò |
| Tại bưu cục: HUB_MANAGER, WAREHOUSE_OPERATOR, COURIER, LINE_HAUL_DRIVER | Chỉ các bưu cục mà người đó đang là thành viên (dữ liệu của Operations; xem qua `GET /api/v1/operations/me/hub-scope`). Nhân sự bị tạm ngưng thì phạm vi rỗng |
| Bưu tá, tài xế | Thêm nữa: chỉ việc được giao cho chính mình |
| Shop | Chỉ vận đơn, hoá đơn, đối soát của shop mình |
| Khách hàng | Chỉ hồ sơ, sổ địa chỉ của mình |

Dữ liệu theo bưu cục gồm: danh sách và chi tiết vận đơn (đơn đi, đến, đang nằm hoặc từng qua bưu cục), hồ sơ sự cố,
ngoại lệ, giám sát, nhân sự, ca làm, phân công, tiền mặt tại bưu cục. Dòng dữ liệu cũ không xác định được bưu cục thì
chỉ văn phòng thấy (an toàn là mặc định). Không tạo quyền kiểu "quản lý bưu cục A".

---

## 4. Nghiệp vụ theo từng mảng

### 4.1 Tài khoản và đăng nhập

- **Khách tự đăng ký** bằng email và mật khẩu, bấm link trong email để xác thực (ở local, email vào Mailpit), rồi hoàn
  thiện hồ sơ (họ tên, SĐT…) ở `/complete-profile`. Có thể đăng nhập bằng Google.
- **Nhân viên không tự đăng ký.** Quản trị tạo tài khoản ở **Tài khoản → Tạo tài khoản** hoặc **Nhân sự bưu cục**.
  - Tài khoản mới có **mật khẩu tạm** (mặc định `123456`, đổi bằng biến `STAFF_TEMPORARY_PASSWORD`). Email do công ty
    cấp nên được coi là đã xác thực.
  - Lần đăng nhập đầu, trang quản lý và app hiện trường **bắt đặt mật khẩu riêng** trước khi làm việc; mọi trang khác
    đều chuyển về màn đặt mật khẩu.
  - Mật khẩu tạm **hết hạn sau 72 giờ** (`STAFF_TEMPORARY_PASSWORD_TTL`). Quá hạn thì nhân viên dùng Quên mật khẩu.
  - Tài khoản seed đã được kích hoạt sẵn với mật khẩu dev.
- **Phiên đăng nhập:** access token ngắn hạn và refresh token xoay vòng; mỗi phiên có thời hạn tuyệt đối. Có đăng xuất
  và đăng xuất mọi thiết bị. Đăng nhập sai nhiều lần thì tài khoản bị khoá tạm.
- **Quên mật khẩu** gửi link đặt lại (hiệu lực 1 giờ). Khách dùng link của client portal, nhân viên dùng link của
  management portal.
  - App hiện trường có "Quên mật khẩu?" ở màn đăng nhập: gửi email, rồi dán liên kết trong thư vào app để đặt mật khẩu
    mới ngay trên điện thoại. Vì vậy không cần mở được trang web trên điện thoại.
- Mọi sự kiện bảo mật (đăng nhập, đổi mật khẩu, thu hồi phiên) được ghi nhật ký.
- Quản trị viên khởi tạo `admin@pavex.com` do Auth và Identity tự tạo khi khởi động, chỉ khi
  `PAVEX_BOOTSTRAPADMIN_ENABLED=true`. Mật khẩu lấy từ `PAVEX_BOOTSTRAPADMIN_PASSWORD` (bật mà thiếu thì Auth dừng ngay);
  ở local `scripts/local-env.sh` đặt cả hai trong `.env`.

### 4.2 Shop và điểm lấy hàng

1. Khách đã đăng nhập gửi hồ sơ shop ở `/merchant/onboarding`: tên doanh nghiệp, MST, người liên hệ, địa chỉ.
2. Kinh doanh & CSKH (`partners.merchants.manage-status`) duyệt, từ chối hoặc tạm ngưng shop. Mọi lần đổi trạng thái
   đều ghi lý do và lịch sử.
3. Khi shop đang hoạt động, Partners đồng bộ vai trò MERCHANT sang Identity qua outbox. Shop cần đăng nhập lại để có
   menu Vận đơn.
4. Shop khai báo **điểm lấy hàng**, trong đó có một điểm mặc định. Điểm lấy hàng được dùng làm người gửi trên vận đơn.
   SĐT phải là số di động Việt Nam hợp lệ. Nếu điểm lấy hàng cũ có SĐT sai, form tạo đơn báo lỗi và có nút mở thẳng hộp
   sửa (`/account/addresses?editPickup=<id>`).
5. Shipment hỏi Partners (API nội bộ) xem shop có được tạo đơn không, trước mỗi lần tạo đơn.

Địa chỉ dùng danh mục hành chính **2 cấp** (tỉnh/thành → phường/xã).

### 4.3 Mạng lưới

- **3 vùng** (Bắc, Trung, Nam). **Hub** có 3 cấp:
  - trung tâm khai thác miền (`TT-…`);
  - kho trung chuyển tỉnh (`KHO-…`);
  - bưu cục phường/xã (`BC-…`).
- **Vùng phục vụ:** mỗi phường/xã thuộc một vùng phục vụ, gắn với một bưu cục chính. Mỗi phường/xã có một **mức phủ**
  (thường, vùng xa, hải đảo); Pricing dùng mức phủ để tính phụ phí.
- **Tuyến** nối hai hub, có phương tiện, thời gian chạy, sức chứa và lịch chạy theo ngày trong tuần. **Hành trình mẫu**
  là chuỗi tuyến ưu tiên giữa hai hub.
- Khi tạo vận đơn, Network **giữ chỗ** trên từng chặng. Chỗ giữ được dùng khi hàng lên xe và được trả lại khi đơn huỷ.
- Mọi thay đổi cấu hình mạng lưới được ghi nhật ký (`/network/audit`).

### 4.4 Bảng giá và tính cước

- **Bảng giá** có vòng đời `DRAFT → ACTIVE → RETIRED`. Chỉ bản nháp được sửa. Tại một thời điểm chỉ có một bảng giá có hiệu lực.
- **Kích hoạt bảng giá mới khi đang có bảng giá khác:**
  - Phải xác nhận thay thế (`replaceActivePlan`).
  - Bảng cũ kết thúc đúng lúc bảng mới bắt đầu, trong cùng một giao dịch. Nhật ký ghi `SUPERSEDED`.
  - Thời điểm bắt đầu không bao giờ lùi về quá khứ: là ngày hiệu lực đã đặt, hoặc "ngay bây giờ" nếu ngày đó đã qua.
  - Không cho phép khoảng trống không có bảng giá nào.
  - Giao diện hiện trước mốc bàn giao, để người kích hoạt biết khi nào bảng cũ ngừng áp dụng.
- **Mỗi bảng giá có 6 quy tắc:** 2 dịch vụ (Tiêu chuẩn, Nhanh) × 3 vùng giá (nội tỉnh, nội miền, liên miền). Bảng giá
  còn có phụ phí vùng xa/hải đảo, phí COD, phí bảo hiểm theo giá trị khai báo và các giới hạn (số kiện, cân nặng, COD tối đa).
- **Cước một đơn được tính như sau:**
  1. Mỗi kiện tính theo cân nặng lớn hơn giữa cân nặng thật và cân nặng quy đổi từ kích thước.
  2. Cộng phụ phí và phí COD/bảo hiểm.
  3. Làm tròn lên bội số 1.000 ₫. Màn chi tiết cước có hiện dòng làm tròn này.
- **Báo giá** là ảnh chụp cố định của lần tính cước: có hạn dùng, và mỗi báo giá chỉ tạo được một vận đơn. Đơn đã tạo
  không đổi cước khi bảng giá đổi, kể cả khi cân lại kiện.
- Ai cũng tính thử cước được ở `/pricing` (client, không cần đăng nhập). Nhân viên có màn tính thử và soi cách tính của
  một báo giá (`/pricing/simulator`, `/pricing/quotes`).

### 4.5 Vận đơn

**Tạo đơn** (shop, `/shipments/new`):
1. Chọn điểm lấy hàng (người gửi), nhập người nhận (SĐT di động Việt Nam, địa chỉ 2 cấp), khai báo kiện (kích thước,
   cân nặng), loại hàng, giá trị hàng, tiền thu hộ (COD).
2. Tính cước, chọn dịch vụ, chọn **người trả cước**: người gửi (shop) hoặc người nhận.
3. Form hiện ngay "Người nhận trả khi nhận hàng" và "Shop chịu cước", đổi theo lựa chọn.
4. Backend kiểm tra shop, báo giá và SĐT, giữ chỗ trên tuyến, rồi tạo vận đơn ở trạng thái **Chờ lấy hàng** kèm mã vận
   đơn và mã từng kiện.

**Số tiền thu người nhận** = COD + cước (nếu người nhận trả). Số này cố định từ lúc tạo đơn.

**Nhãn kiện** (`/shipments/<id>/labels`): mỗi kiện một nhãn có mã QR (mã kiện), người gửi/nhận, số tiền thu và dòng
"Thu một lần cho cả N kiện".

**Hành trình của kiện:**

```text
Chờ lấy hàng → Đã lấy hàng → Đến bưu cục gửi → Đang trung chuyển (qua kho) → Đến bưu cục phát
  → Đang giao → Đã giao
              ↘ Giao chưa thành công → giao lại, hoặc Đang hoàn → Đã hoàn
Huỷ: shop huỷ được khi chưa lấy hàng; sau khi đã lấy thì phải qua yêu cầu ngoại lệ được duyệt.
```

- Mỗi bước là một lần **quét kiện** (`scans.create`) của nhân viên có việc được giao. Trạng thái vận đơn tổng hợp từ
  các kiện; lẫn kiện giao được và kiện hoàn thì vận đơn là **Đã giao một phần**.
- **Giao hàng** (bưu tá, màn "Giao hàng" trên điện thoại):
  - Giao thành công phải có chứng từ: ảnh (được thu nhỏ trước khi gửi), tên người nhận, mã chứng từ.
  - Giao không thành công phải chọn lý do.
- **Thu tiền:**
  - Thu **một lần, đủ tiền, ở kiện đầu tiên giao thành công**. Bưu tá phải xác nhận đúng số tiền; thiếu, thừa hoặc thu
    lần hai đều bị từ chối.
  - Các kiện sau thu 0 ₫. Trạng thái thu tiền của vận đơn: Không thu / Chờ thu / Đã thu / Không thu (đơn đã kết thúc).
- **Ngoại lệ cần duyệt** (`/shipments/exceptions`): hoàn hàng, chuyển tiếp, huỷ sau khi lấy hàng. Thao tác theo phạm vi bưu cục.
- **Cân lại kiện:** nhân viên bưu cục ghi cân nặng mới, người có quyền duyệt. Cước không tính lại.
- **Hồ sơ sự cố** (`/shipments/cases`): kiện có vấn đề, kiện không xác định; có lịch sử xử lý.
- **Giám sát** (`/control-tower`): tín hiệu trễ hạn, lệch tuyến… theo phạm vi bưu cục.
- **Đóng vận đơn:** khi mọi kiện ở trạng thái cuối (đã giao, đã hoàn, đã huỷ), vòng đời chuyển sang Hoàn tất/Đã huỷ. Shipment
  gửi sự kiện `shipment.closed` cho Billing kèm **cước shop phải chịu**:

| Người trả cước | Kết cục | Shop chịu |
|---|---|---|
| Người gửi (shop) | Huỷ trước khi lấy hàng | 0 ₫ |
| Người gửi (shop) | Mọi trường hợp khác (giao, hoàn, huỷ sau khi lấy) | Cước đầy đủ |
| Người nhận | Đã thu được tiền | 0 ₫ (người nhận đã trả) |
| Người nhận | Không thu được (hoàn, huỷ sau khi lấy) | Cước gốc |
| Người nhận | Huỷ trước khi lấy hàng | 0 ₫ |

Chưa có phí hoàn hàng riêng.

- **Tra cứu công khai** (`/tracking`, không cần đăng nhập): nhập mã vận đơn để xem hành trình.

### 4.6 Vận hành

- **Nhân sự vận hành:** mỗi nhân viên hiện trường là một hồ sơ nhân sự (loại, trạng thái, năng lực), thuộc một hoặc nhiều
  bưu cục, trong đó có một bưu cục chính. Phạm vi dữ liệu ở mục 3.2 lấy từ đây.
- **Ca làm:**
  - Tạo ca tại bưu cục, phân người vào ca, check-in/check-out, đánh vắng.
  - Hệ thống **tự giữ sẵn ca 7 ngày tới** theo lịch luân phiên (làm 6 ngày nghỉ 1), và tự check-out người quên
    check-out sau khi ca kết thúc 1 giờ.
- **Điều kiện được giao việc:**
  - Là thành viên của hub có việc, đang check-in một ca, và đang bật Sẵn sàng.
  - Riêng tài xế trung chuyển: check-in ca ở kho tỉnh là đủ để nhận việc ở mọi bưu cục trên tuyến của mình.
- **Phân công việc:** mỗi vận đơn sinh các việc lấy hàng, khai thác, trung chuyển, giao. Việc được giao cho người phù
  hợp; người nhận việc chấp nhận rồi thực hiện. Mỗi việc có lịch sử. Trưởng bưu cục điều phối trong bưu cục của mình;
  vận hành miền điều phối toàn mạng (`/operations/dispatch`, có cả hàng sự kiện lỗi để gửi lại).
  - **Việc khai thác kho** ưu tiên nhân viên kho. Bưu cục không có nhân viên kho (phần lớn bưu cục nhỏ) thì trưởng bưu
    cục nhận thay, cả khi giao tự động lẫn khi phân công tay.
  - **Quét "Nhập kho" là nhận việc khai thác.** Nhân viên kho hoặc trưởng bưu cục quét thì tự nhận việc khai thác của
    đơn ở bưu cục đó nếu chưa ai chấp nhận, không cần bấm "Nhận việc". Ở kho trung chuyển và bưu cục phát, quét Nhập
    kho đủ kiện là xong bước khai thác và mở chặng xe tiếp theo hoặc việc giao hàng. Việc đã có người chấp nhận thì
    vẫn là của người đó.
  - **Trả việc chưa nhận:** người ra ca (tự ra ca, hệ thống tự ra ca, bị huỷ ca hoặc đánh vắng) hay tắt Sẵn sàng thì các
    việc được giao mà chưa chấp nhận quay về hàng chờ. Việc giao tự động mà 15 phút không ai chấp nhận
    (`OPERATIONS_ACCEPT_TIMEOUT`) cũng được thu lại và giao người khác; việc trưởng bưu cục phân công tay thì giữ nguyên.
  - **Hàng chờ công bằng:** mỗi 5 giây hệ thống thử giao 25 việc đã lâu chưa được thử nhất, nên việc tồn nhiều cũng
    không che mất việc khác. Ai vừa vào ca hoặc bật Sẵn sàng thì việc chờ ở bưu cục của họ được thử ngay trong lượt kế
    tiếp. Người vừa bị thu việc được hỏi sau cùng.

### 4.7 Thu tiền, nộp tiền, đối soát, hoá đơn (Billing)

```text
Bưu tá thu tiền khi giao ──(shipment.cash-collected)──▶ Tiền bưu tá đang giữ
  → Bưu tá lập phiếu nộp tại bưu cục (khai đúng tổng số tiền)
  → Kế toán duyệt ── hoặc từ chối: tiền nằm lại bưu cục, bưu cục nộp lại
  → Tiền đã duyệt chờ đối soát

Vận đơn đóng ──(shipment.closed)──▶ Cước shop phải chịu (nếu > 0)

Kế toán lập bảng đối soát cho một shop đến một mốc thời gian:
  COD đã duyệt  vs  cước chưa đối soát
  cấn trừ = min(COD, cước)
  COD còn lại → Pavex chuyển khoản cho shop (kế toán ghi mã giao dịch vào tài khoản shop đã khai)
  cước còn lại → hoá đơn cho shop, thanh toán bằng QR payOS hoặc tiền mặt
```

- **Tiền bưu tá thu** gồm COD và cước (nếu người nhận trả). Tiền đi theo chuỗi: bưu tá giữ → đã nộp bưu cục → kế toán đã duyệt.
- **Phiếu nộp tiền:** bưu tá chọn các khoản đang giữ (tối đa 200) và khai tổng tiền. Tổng khai phải bằng đúng tổng các
  khoản. Mỗi khoản chỉ nằm trong một phiếu.
- **Bảng đối soát:** mỗi khoản tiền và mỗi khoản cước chỉ vào đúng một bảng. Chỉ phần COD **đã được kế toán duyệt**
  mới được trả cho shop. Nếu phải chuyển khoản, shop cần khai tài khoản nhận tiền ở `/account/settlements`.
- **Hoá đơn:**
  - Mỗi vận đơn là một dòng. Phần cấn trừ COD được ghi thành một khoản thanh toán.
  - Trạng thái: Đã phát hành → Thanh toán một phần → Đã thanh toán, hoặc Đã huỷ (chỉ huỷ được khi chưa thu đồng nào).
  - Mã QR đang chờ thanh toán giữ chỗ phần tiền của nó, nên hai lần thanh toán không vượt quá số còn nợ.
- **payOS:**
  - Mặc định dùng cổng giả `fake`: QR giả, kế toán bấm xác nhận.
  - Bật thật thì payOS gọi webhook `/api/v1/integrations/payos/webhook`; webhook được kiểm tra chữ ký và chống trùng.
  - Tuỳ chọn: một job định kỳ tự hỏi lại trạng thái thanh toán (mục 2.8).

### 4.8 Yêu cầu hỗ trợ

- **Form trên website:** các trang Liên hệ, Hỗ trợ, Khiếu nại, Tuyển dụng dùng chung một form, gửi được cả khi chưa
  đăng nhập.
  - Khiếu nại được đính kèm tối đa 5 tệp ảnh/PDF, mỗi tệp tối đa 5 MB. Loại tệp được kiểm tra bằng nội dung thật của
    tệp, không tin phần đuôi.
  - Chống spam bằng ô bẫy bot ẩn, giới hạn 5 yêu cầu mỗi giờ cho mỗi email, và giới hạn tần suất theo IP ở Gateway.
- **Sau khi gửi:**
  - Khách nhận mã yêu cầu dạng `YCyyMMdd-XXXXX` trên màn hình và qua email xác nhận. Email chỉ được gửi sau khi yêu
    cầu đã lưu, và không chép lại nội dung khách nhập.
  - Nếu khách đang đăng nhập, yêu cầu được gắn với tài khoản.
- **Xử lý** (Kinh doanh & CSKH, Quản trị; `/support` trong trang quản trị):
  - Luồng trạng thái: Mới → Đang xử lý → Đã giải quyết → Đã đóng; mở lại được.
  - Mỗi nhân viên chỉ tự nhận yêu cầu cho mình. Yêu cầu đã có người nhận thì người khác không nhận được.
  - Trả yêu cầu lại thì trạng thái về Mới. Giải quyết hoặc đóng phải có ghi chú. Mọi bước có lịch sử.
- Bảng điều hành hiện số yêu cầu mới.

### 4.9 Xe trung chuyển (Fleet)

Theo dõi xe chạy giữa các hub (bưu cục → kho tỉnh → trung tâm miền và chiều ngược lại). Không theo dõi bưu tá đi
giao, và khách hàng không bao giờ thấy vị trí.

- **Chuyến là gì:** mỗi chặng của vận đơn sinh một việc LINE_HAUL cho tài xế. Các việc cùng tuyến, cùng giờ xuất phát
  theo lịch nằm trên cùng một xe, nên Fleet gom chúng thành **một chuyến** = (tài xế, tuyến, giờ xuất phát).
- **Đồng ý trước:** lần đầu, tài xế phải đồng ý điều khoản chia sẻ vị trí trong app. Đổi phiên bản điều khoản thì hỏi lại.
- **Bắt đầu:** tài xế bấm "Bắt đầu chuyến" (có thể nhập biển số xe), hoặc chuyến **tự bắt đầu** khi quét Lên xe kiện
  đầu tiên. Mỗi tài xế chỉ chạy một chuyến tại một thời điểm.
- **Gửi vị trí:**
  - App ghi GPS khi xe đi được trên 30 m (khoảng 15 giây một lần). Khi xe đứng yên, app hỏi vị trí mỗi phút để xe
    không bị báo mất tín hiệu.
  - App gửi theo lô mỗi 30 giây. Lúc mất sóng thì giữ lại trên máy và gửi bù khi có mạng. Gửi lặp lại không tạo điểm trùng.
  - Server bỏ điểm có giờ ở tương lai hoặc trước lúc bắt đầu chuyến, và điểm nhảy xa bất thường (trên ~200 km/h).
  - Vị trí giả (Android báo) được ghi cờ để cảnh báo.
- **Kết thúc:** tài xế bấm "Đã tới", hoặc chuyến **tự kết thúc** khi hub đến nhập đủ kiện (các việc LINE_HAUL đã
  hoàn tất). Hết chuyến thì app ngừng gửi.
- **Ai xem được:**
  - Trưởng bưu cục thấy các chuyến **đi hoặc đến** bưu cục mình.
  - Vận hành miền và quản trị thấy toàn quốc.
  - Mỗi lần mở lộ trình đều được ghi nhật ký.
- **Trang "Xe trung chuyển"** (`/fleet`):
  - Bản đồ các xe đang chạy, tự làm mới mỗi 10 giây. Lọc theo bưu cục.
  - Trạng thái **trễ** (quá giờ tới dự kiến) và **mất tín hiệu** (quá 5 phút không có vị trí).
  - Bấm vào một xe để xem lộ trình, quãng đường, tốc độ và pin điện thoại.
- **Lưu trữ:** điểm GPS thô lưu theo từng ngày, tự xoá sau 30 ngày. Vị trí mới nhất và quãng đường nằm ngay trên chuyến.

---

## 5. Màn hình của hai portal

### 5.1 Client portal (http://localhost:3000)

Menu tài khoản của shop có: Vận đơn, Hoá đơn, Đối soát COD, Sổ địa chỉ, Hồ sơ, Bảo mật.

| URL | Trang | Ai dùng |
|---|---|---|
| `/` | Giao vận rõ ràng cho mọi hành trình | Công khai |
| `/about` | Về Pavex | Công khai |
| `/accept-invite` | Kích hoạt tài khoản | Công khai |
| `/account/addresses` | Sổ địa chỉ | Đã đăng nhập |
| `/account/billing` | Hóa đơn Merchant | Shop |
| `/account/billing/[invoiceId]` | Chi tiết hóa đơn | Shop |
| `/account/profile` | Hồ sơ cá nhân | Đã đăng nhập |
| `/account/security` | Bảo mật tài khoản | Đã đăng nhập |
| `/account/settlements` | Đối soát tiền thu hộ | Shop |
| `/account/settlements/[statementId]` | Chi tiết đối soát | Shop |
| `/blog` | Blog giao vận | Công khai |
| `/blog/[slug]` | Bài viết blog | Công khai |
| `/careers` | Tuyển dụng | Công khai |
| `/claims` | Khiếu nại và bồi thường | Công khai |
| `/complete-profile` | Hoàn thiện hồ sơ | Đã đăng nhập |
| `/contact` | Liên hệ Pavex | Công khai |
| `/forgot-password` | Quên mật khẩu | Công khai |
| `/guide/packing` | Hướng dẫn đóng gói | Công khai |
| `/login` | Đăng nhập | Công khai |
| `/merchant/onboarding` | Đăng ký đối tác Merchant | Đã đăng nhập |
| `/network` | Mạng lưới trung tâm khai thác | Công khai |
| `/notifications` | Thông báo | Đã đăng nhập |
| `/post-offices` | Chuyển hướng (không có nội dung riêng) | Công khai |
| `/pricing` | Ước tính cước giao hàng | Công khai |
| `/privacy` | Chính sách quyền riêng tư | Công khai |
| `/prohibited` | Hàng cấm và hạn chế vận chuyển | Công khai |
| `/register` | Đăng ký | Công khai |
| `/reset-password` | Đặt lại mật khẩu | Công khai |
| `/shipments` | Vận đơn của tôi | Shop (MERCHANT) |
| `/shipments/[shipmentId]` | Chi tiết vận đơn | Shop (MERCHANT) |
| `/shipments/[shipmentId]/labels` | Nhãn kiện | Shop (MERCHANT) |
| `/shipments/new` | Tạo vận đơn | Shop (MERCHANT) |
| `/support` | Trung tâm hỗ trợ | Công khai |
| `/terms` | Điều khoản sử dụng | Công khai |
| `/tracking` | Tra cứu vận đơn | Công khai |
| `/verify-email` | Xác minh email | Công khai |

### 5.2 Management portal (http://localhost:3001)

Menu chỉ hiện mục người dùng có quyền. Bưu tá chỉ thấy "Giao hàng".

| URL | Trang | Ai dùng |
|---|---|---|
| `/` | Chuyển tới trang đầu tiên người dùng được phép xem | Mọi nhân viên đã đăng nhập |
| `/account` | Tài khoản của tôi | Mọi nhân viên đã đăng nhập |
| `/control-tower` | Giám sát vận đơn | `shipments.control-tower.read` + quyền phân công |
| `/dashboard` | Bảng điều hành | Mọi nhân viên đã đăng nhập |
| `/deliveries` | Giao hàng | Bưu tá (`deliveries.complete-own`) |
| `/finance/cash` | Duyệt tiền nộp | Kế toán (`billing.cash.approve`) |
| `/finance/invoices` | Hóa đơn Merchant | Kế toán (`billing.manage`) |
| `/finance/settlements` | Đối soát Merchant | Kế toán (`billing.manage`) |
| `/fleet` | Xe trung chuyển | Trưởng bưu cục, vận hành (`fleet.trips.read-hub`) |
| `/forgot-password` | Quên mật khẩu | Công khai |
| `/identity/roles` | Vai trò & quyền hạn | `identity.roles.read` |
| `/identity/users` | Người dùng | `identity.users.read` |
| `/identity/users/[userId]` | Chi tiết tài khoản | `identity.users.read` |
| `/identity/users/new` | Tạo tài khoản | `identity.users.create` + `identity.roles.assign` |
| `/login` | Đăng nhập | Công khai |
| `/network` | Tổng quan mạng lưới | `network.read` / `network.manage` |
| `/network/audit` | Nhật ký mạng lưới | `network.read` / `network.manage` |
| `/network/capacity` | Sức chứa tuyến | `network.read` / `network.manage` |
| `/network/coverage` | Vùng phục vụ | `network.read` / `network.manage` |
| `/network/hubs` | Điểm trung chuyển | `network.read` / `network.manage` |
| `/network/lanes` | Tuyến vận chuyển | `network.read` / `network.manage` |
| `/network/regions` | Vùng mạng lưới | `network.read` / `network.manage` |
| `/network/route-planner` | Lập hành trình | `network.read` / `network.manage` |
| `/network/route-templates` | Hành trình mẫu | `network.read` / `network.manage` |
| `/network/routes` | Tuyến và hành trình | `network.read` / `network.manage` |
| `/network/service-areas` | Vùng phục vụ | `network.read` / `network.manage` |
| `/operations` | Điều phối vận hành | Quyền operations bất kỳ |
| `/operations/assignments` | Phân công vận hành | `operations.assignments.*` / `operations.dispatch.manage` |
| `/operations/assignments/[assignmentId]` | Chi tiết phân công | `operations.assignments.*` / `operations.dispatch.manage` |
| `/operations/dispatch` | Điều phối toàn mạng và hàng sự kiện | Vận hành toàn mạng (`scope.all-hubs` + quyền phân công) |
| `/operations/shifts` | Ca làm việc | `operations.workforce.*` / `operations.hub.manage` |
| `/operations/shifts/[shiftId]` | Chi tiết ca làm việc | `operations.workforce.*` / `operations.hub.manage` |
| `/operations/workforce` | Nhân sự vận hành | `operations.workforce.*` / `operations.hub.manage` |
| `/operations/workforce/[workforceId]` | Chi tiết nhân sự vận hành | `operations.workforce.*` / `operations.hub.manage` |
| `/partners/merchants` | Merchant | `partners.merchants.read` |
| `/partners/merchants/[merchantId]` | Hồ sơ Merchant | `partners.merchants.read` |
| `/pricing` | Bảng giá & cước | `pricing.read` / `pricing.manage` |
| `/pricing/audit` | Nhật ký giá cước | `pricing.read` / `pricing.manage` |
| `/pricing/quotes` | Chi tiết cách tính cước | `pricing.read` / `pricing.manage` |
| `/pricing/rate-plans` | Bảng giá | `pricing.read` / `pricing.manage` |
| `/pricing/simulator` | Tính thử cước | `pricing.read` / `pricing.manage` |
| `/reset-password` | Đặt mật khẩu mới | Công khai |
| `/shipments` | Vận đơn | `shipments.admin.read` / `shipments.read-hub` |
| `/shipments/[trackingCode]` | Chi tiết vận đơn | `shipments.admin.read` / `shipments.read-hub` |
| `/shipments/cases` | Hồ sơ sự cố | `shipments.cases.read` / `.manage` |
| `/shipments/cases/[caseId]` | Chi tiết hồ sơ sự cố | `shipments.cases.read` / `.manage` |
| `/shipments/exceptions` | Duyệt ngoại lệ & huỷ vận đơn | `shipments.exceptions.read` / `.manage` |
| `/support` | Yêu cầu hỗ trợ | CSKH (`support.requests.read`) |

---

## 6. Entity, thuộc tính, enum và API theo service

Mỗi service có: entity (bảng, cột, kiểu, ràng buộc), enum trạng thái, và API kèm quyền cần có. Mọi API người dùng đi qua
Gateway `http://localhost:8080`. API `/internal/...` chỉ dành cho service gọi nhau bằng JWT dịch vụ, Gateway không mở ra
ngoài. Swagger của từng service: http://localhost:8080/swagger-ui.html.

Cách đọc cột "Ràng buộc": *bắt buộc* = `NOT NULL`; *có thể rỗng* = cho phép `NULL`; *tối đa n ký tự* = độ dài cột;
*khoá lạc quan* = cột `version` chống ghi đè đồng thời.

### 6.1 Auth — `pavex_auth` (cổng DB 5433, service 8081)

Chỉ giữ thông tin đăng nhập. Hồ sơ, vai trò và quyền nằm ở Identity.

<details>
<summary><b>AuthAuditLog</b> — `auth.auth_audit_log` · 10 thuộc tính — Nhật ký bảo mật: đăng nhập, đổi mật khẩu, thu hồi phiên</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `action` | `action` | `String` | tối đa 96 ký tự, bắt buộc |
| `outcome` | `outcome` | `String` | tối đa 16 ký tự, bắt buộc |
| `reasonCode` | `reason_code` | `String?` | tối đa 96 ký tự, có thể rỗng |
| `actorUserId` | `actor_user_id` | `UUID?` | có thể rỗng |
| `subjectHash` | `subject_hash` | `String?` | tối đa 64 ký tự, có thể rỗng |
| `requestId` | `request_id` | `String?` | tối đa 128 ký tự, có thể rỗng |
| `ipAddress` | `ip_address` | `String?` | tối đa 64 ký tự, có thể rỗng |
| `userAgent` | `user_agent` | `String?` | tối đa 512 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>ConsumedMessage</b> — `auth.consumed_message` · 4 thuộc tính — Đánh dấu message đã xử lý để không gửi email trùng</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `messageId` | `message_id` | `UUID` | bắt buộc |
| `consumer` | `consumer` | `String` | tối đa 128 ký tự, bắt buộc |
| `processedAt` | `processed_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>EmailVerificationToken</b> — `auth.email_verification_token` · 6 thuộc tính — Mã xác thực email gửi khi đăng ký</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `userId` | `user_id` | `UUID` | bắt buộc |
| `tokenHash` | `token_hash` | `String` | tối đa 128 ký tự, duy nhất, bắt buộc |
| `expiresAt` | `expires_at` | `Instant` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `usedAt` | `used_at` | `Instant?` | có thể rỗng |

</details>

<details>
<summary><b>ExternalIdentity</b> — `auth.external_identity` · 6 thuộc tính — Liên kết tài khoản với đăng nhập Google</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `userId` | `user_id` | `UUID` | bắt buộc |
| `provider` | `provider` | `String` | tối đa 50 ký tự, bắt buộc |
| `providerUserId` | `provider_user_id` | `String` | tối đa 255 ký tự, bắt buộc |
| `email` | `email` | `String?` | tối đa 320 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>InvitationToken</b> — `auth.invitation_token` · 6 thuộc tính — Lời mời kích hoạt tài khoản nhân viên</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `userId` | `user_id` | `UUID` | bắt buộc |
| `tokenHash` | `token_hash` | `String` | tối đa 128 ký tự, duy nhất, bắt buộc |
| `expiresAt` | `expires_at` | `Instant` | bắt buộc |
| `usedAt` | `used_at` | `Instant?` | có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>OutboxMessage</b> — `auth.outbox_message` · 14 thuộc tính — Hàng đợi email chờ gửi (outbox → RabbitMQ), nội dung được mã hoá</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `aggregateType` | `aggregate_type` | `String?` | tối đa 80 ký tự, có thể rỗng |
| `aggregateId` | `aggregate_id` | `UUID?` | có thể rỗng |
| `eventType` | `event_type` | `String` | tối đa 160 ký tự, bắt buộc |
| `eventVersion` | `event_version` | `Int` | bắt buộc |
| `routingKey` | `routing_key` | `String` | tối đa 160 ký tự, bắt buộc |
| `payloadCiphertext` | `payload_ciphertext` | `String` | bắt buộc |
| `status` | `status` | `OutboxStatus` | tối đa 16 ký tự, enum, bắt buộc |
| `attempts` | `attempts` | `Int` | bắt buộc |
| `nextAttemptAt` | `next_attempt_at` | `Instant` | bắt buộc |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |
| `publishedAt` | `published_at` | `Instant?` | có thể rỗng |
| `lastError` | `last_error` | `String?` | tối đa 1000 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>PasswordResetToken</b> — `auth.password_reset_token` · 7 thuộc tính — Mã đặt lại mật khẩu (khách hàng hoặc nhân viên)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `userId` | `user_id` | `UUID` | bắt buộc |
| `tokenHash` | `token_hash` | `String` | tối đa 128 ký tự, duy nhất, bắt buộc |
| `expiresAt` | `expires_at` | `Instant` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `usedAt` | `used_at` | `Instant?` | có thể rỗng |
| `revokedAt` | `revoked_at` | `Instant?` | có thể rỗng |

</details>

<details>
<summary><b>RefreshSession</b> — `auth.refresh_session` · 11 thuộc tính — Phiên đăng nhập (refresh token xoay vòng), giới hạn thời gian sống tuyệt đối</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `userId` | `user_id` | `UUID` | bắt buộc |
| `tokenHash` | `token_hash` | `String` | tối đa 128 ký tự, duy nhất, bắt buộc |
| `expiresAt` | `expires_at` | `Instant` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `sessionStartedAt` | `session_started_at` | `Instant` | bắt buộc |
| `revokedAt` | `revoked_at` | `Instant?` | có thể rỗng |
| `replacedBySessionId` | `replaced_by_session_id` | `UUID?` | có thể rỗng |
| `ipAddress` | `ip_address` | `String?` | tối đa 64 ký tự, có thể rỗng |
| `userAgent` | `user_agent` | `String?` | tối đa 512 ký tự, có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>UserAccount</b> — `auth.user_account` · 11 thuộc tính — Thông tin đăng nhập: email, mật khẩu (băm), trạng thái xác thực email, khoá tạm do đăng nhập sai</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `email` | `email` | `String` | tối đa 255 ký tự, duy nhất, bắt buộc |
| `passwordHash` | `password_hash` | `String` | tối đa 512 ký tự, bắt buộc |
| `emailVerified` | `email_verified` | `Boolean` | bắt buộc |
| `identityProvisioned` | `identity_provisioned` | `Boolean` | bắt buộc |
| `failedLoginAttempts` | `failed_login_attempts` | `Int` | bắt buộc |
| `lockoutEndAt` | `lockout_end_at` | `Instant?` | có thể rỗng |
| `lastLoginAt` | `last_login_at` | `Instant?` | có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary>Enum trạng thái và danh mục</summary>

| Enum | Giá trị |
|---|---|
| `AuthRateLimitPolicy` | `REGISTER_EMAIL`, `LOGIN_EMAIL`, `REFRESH_TOKEN`, `VERIFY_EMAIL_TOKEN`, `RESEND_EMAIL`, `FORGOT_PASSWORD_EMAIL`, `RESET_PASSWORD_TOKEN`, `ACCEPT_INVITE_TOKEN`, `GOOGLE_TOKEN` |
| `OutboxStatus` | `PENDING`, `PUBLISHED`, `DEAD` |
| `PasswordResetPortal` | `CLIENT`, `MANAGEMENT` |

</details>

<details>
<summary>API (17 endpoint)</summary>

| Method | Đường dẫn | Quyền |
|---|---|---|
| `POST` | `/api/v1/auth/accept-invite` | Công khai |
| `POST` | `/api/v1/auth/change-password` | Đăng nhập (dữ liệu của chính mình) |
| `POST` | `/api/v1/auth/forgot-password` | Công khai |
| `POST` | `/api/v1/auth/google` | Công khai |
| `POST` | `/api/v1/auth/login` | Công khai |
| `POST` | `/api/v1/auth/logout` | Đăng nhập (dữ liệu của chính mình) |
| `POST` | `/api/v1/auth/logout-all` | Đăng nhập (dữ liệu của chính mình) |
| `POST` | `/api/v1/auth/refresh` | Công khai |
| `POST` | `/api/v1/auth/register` | Công khai |
| `POST` | `/api/v1/auth/resend-verification` | Công khai |
| `POST` | `/api/v1/auth/reset-password` | Công khai |
| `GET` | `/api/v1/auth/sessions` | Đăng nhập (dữ liệu của chính mình) |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | Đăng nhập (dữ liệu của chính mình) |
| `POST` | `/api/v1/auth/verify-email` | Công khai |
| `POST` | `/internal/v1/credentials/invitations/provision` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/credentials/invitations/send` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/credentials/{userId}/revoke-sessions` | Nội bộ (JWT dịch vụ) |

</details>


### 6.2 Identity — `pavex_identity` (5434, service 8082)

Nguồn duy nhất về tài khoản, hồ sơ, vai trò và quyền. Khoá tài khoản thì Identity gọi Auth thu hồi mọi phiên.

<details>
<summary><b>Permission</b> — `identity.permission` · 4 thuộc tính — Quyền nguyên tử, ví dụ `shipments.create`</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 128 ký tự, duy nhất, bắt buộc |
| `name` | `name` | `String` | tối đa 128 ký tự, bắt buộc |
| `description` | `description` | `String?` | tối đa 512 ký tự, có thể rỗng |

</details>

<details>
<summary><b>Role</b> — `identity.role` · 5 thuộc tính — Vai trò (ADMIN, COURIER, MERCHANT…)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 64 ký tự, duy nhất, bắt buộc |
| `name` | `name` | `String` | tối đa 128 ký tự, bắt buộc |
| `description` | `description` | `String?` | tối đa 512 ký tự, có thể rỗng |
| `isSystem` | `is_system` | `Boolean` | bắt buộc |

</details>

<details>
<summary><b>RolePermission</b> — `identity.role_permission` · 2 thuộc tính — Vai trò nào có quyền nào</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `roleId` | `role_id` | `UUID` | khoá chính |
| `permissionId` | `permission_id` | `UUID` | khoá chính |

</details>

<details>
<summary><b>UserAccount</b> — `identity.user_account` · 9 thuộc tính — Hồ sơ danh tính: trạng thái tài khoản, phiên bản quyền (token cũ tự hết hiệu lực khi quyền đổi)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `email` | `email` | `String` | tối đa 255 ký tự, duy nhất, bắt buộc |
| `status` | `status` | `AccountStatus` | tối đa 32 ký tự, enum, bắt buộc |
| `profileStatus` | `profile_status` | `ProfileStatus` | tối đa 32 ký tự, enum, bắt buộc |
| `emailVerified` | `email_verified` | `Boolean` | bắt buộc |
| `authorizationVersion` | `authorization_version` | `Long` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>UserProfile</b> — `identity.user_profile` · 9 thuộc tính — Hồ sơ cá nhân: họ tên, SĐT, giới tính, ngày sinh, trạng thái hoàn thiện</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `userId` | `user_id` | `UUID` | khoá chính |
| `firstName` | `first_name` | `String?` | tối đa 100 ký tự, có thể rỗng |
| `lastName` | `last_name` | `String?` | tối đa 100 ký tự, có thể rỗng |
| `displayName` | `display_name` | `String?` | tối đa 200 ký tự, có thể rỗng |
| `gender` | `gender` | `Gender` | tối đa 32 ký tự, enum, bắt buộc |
| `dateOfBirth` | `date_of_birth` | `LocalDate?` | có thể rỗng |
| `avatarUrl` | `avatar_url` | `String?` | tối đa 2048 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>UserRole</b> — `identity.user_role` · 4 thuộc tính — Người dùng nào có vai trò nào</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `userId` | `user_id` | `UUID` | khoá chính |
| `roleId` | `role_id` | `UUID` | khoá chính |
| `assignedAt` | `assigned_at` | `Instant` | bắt buộc |
| `assignedBy` | `assigned_by` | `UUID?` | có thể rỗng |

</details>

<details>
<summary>Enum trạng thái và danh mục</summary>

| Enum | Giá trị |
|---|---|
| `AccountStatus` | `ACTIVE`, `SUSPENDED`, `DISABLED` |
| `ProfileStatus` | `INCOMPLETE`, `COMPLETED`, `PENDING_VERIFICATION`, `REJECTED` |
| `Gender` | `UNSPECIFIED`, `MALE`, `FEMALE`, `OTHER` |

</details>

<details>
<summary>API (25 endpoint)</summary>

| Method | Đường dẫn | Quyền |
|---|---|---|
| `GET` | `/api/v1/auth/me` | Đăng nhập (dữ liệu của chính mình) |
| `PATCH` | `/api/v1/auth/me/profile` | Đăng nhập (dữ liệu của chính mình) |
| `GET` | `/api/v1/identities/me` | Đăng nhập (dữ liệu của chính mình) |
| `PATCH` | `/api/v1/identities/me` | Đăng nhập (dữ liệu của chính mình) |
| `GET` | `/api/v1/permissions` | `identity.roles.read` (kiểm tra trong service) |
| `GET` | `/api/v1/roles` | `identity.roles.read` (kiểm tra trong service) |
| `POST` | `/api/v1/roles` | `identity.roles.manage` (kiểm tra trong service) |
| `DELETE` | `/api/v1/roles/{roleId}` | `identity.roles.manage` (kiểm tra trong service) |
| `PATCH` | `/api/v1/roles/{roleId}` | `identity.roles.manage` (kiểm tra trong service) |
| `PUT` | `/api/v1/roles/{roleId}/permissions` | `identity.roles.manage` (kiểm tra trong service) |
| `GET` | `/api/v1/users` | `identity.users.read` (kiểm tra trong service) |
| `POST` | `/api/v1/users` | `identity.users.create` (kiểm tra trong service) |
| `GET` | `/api/v1/users/{userId}` | `identity.users.read` (kiểm tra trong service) |
| `POST` | `/api/v1/users/{userId}/invite` | `identity.users.invite` (kiểm tra trong service) |
| `PUT` | `/api/v1/users/{userId}/profile` | `identity.users.manage-profile` (kiểm tra trong service) |
| `POST` | `/api/v1/users/{userId}/roles` | `identity.roles.assign` (kiểm tra trong service) |
| `DELETE` | `/api/v1/users/{userId}/roles/{roleCode}` | `identity.roles.assign` (kiểm tra trong service) |
| `PATCH` | `/api/v1/users/{userId}/status` | `identity.users.manage-status` (kiểm tra trong service) |
| `POST` | `/internal/v1/identities` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/identities/{userId}/auth-context` | Nội bộ (JWT dịch vụ) |
| `PATCH` | `/internal/v1/identities/{userId}/email-verification` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/identities/{userId}/merchant-eligibility` | Nội bộ (JWT dịch vụ) |
| `PUT` | `/internal/v1/identities/{userId}/merchant-role` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/operations/identities/provision` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/operations/identities/snapshots` | Nội bộ (JWT dịch vụ) |

</details>


### 6.3 Partners — `pavex_partners` (5435, service 8083)

Danh mục hành chính, sổ địa chỉ, shop, điểm lấy hàng, yêu cầu hỗ trợ.

<details>
<summary><b>Province</b> — `partners.provinces` · 6 thuộc tính — Tỉnh/thành (danh mục hành chính 2 cấp mới)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `code` | `code` | `String` | khoá chính, tối đa 20 ký tự |
| `name` | `name` | `String` | tối đa 255 ký tự, bắt buộc |
| `nameEn` | `name_en` | `String` | tối đa 255 ký tự, bắt buộc |
| `fullName` | `full_name` | `String` | tối đa 255 ký tự, bắt buộc |
| `fullNameEn` | `full_name_en` | `String` | tối đa 255 ký tự, bắt buộc |
| `codeName` | `code_name` | `String` | tối đa 255 ký tự, bắt buộc |

</details>

<details>
<summary><b>Ward</b> — `partners.wards` · 7 thuộc tính — Phường/xã thuộc tỉnh</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `code` | `code` | `String` | khoá chính, tối đa 20 ký tự |
| `name` | `name` | `String` | tối đa 255 ký tự, bắt buộc |
| `nameEn` | `name_en` | `String` | tối đa 255 ký tự, bắt buộc |
| `fullName` | `full_name` | `String` | tối đa 255 ký tự, bắt buộc |
| `fullNameEn` | `full_name_en` | `String` | tối đa 255 ký tự, bắt buộc |
| `codeName` | `code_name` | `String` | tối đa 255 ký tự, bắt buộc |
| `provinceCode` | `province_code` | `String` | tối đa 20 ký tự, bắt buộc |

</details>

<details>
<summary><b>Merchant</b> — `partners.merchants` · 18 thuộc tính — Shop/đối tác gửi hàng: mã, tên doanh nghiệp, MST, liên hệ, trạng thái duyệt</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `merchantCode` | `merchant_code` | `String` | tối đa 20 ký tự, duy nhất, bắt buộc |
| `userId` | `user_id` | `UUID` | duy nhất, bắt buộc |
| `businessName` | `business_name` | `String` | tối đa 255 ký tự, bắt buộc |
| `contactName` | `contact_name` | `String` | tối đa 200 ký tự, bắt buộc |
| `contactPhone` | `contact_phone` | `String` | tối đa 30 ký tự, bắt buộc |
| `contactEmail` | `contact_email` | `String?` | tối đa 320 ký tự, có thể rỗng |
| `taxCode` | `tax_code` | `String?` | tối đa 50 ký tự, có thể rỗng |
| `status` | `status` | `MerchantStatus` | tối đa 50 ký tự, enum, bắt buộc |
| `statusReasonCode` | `status_reason_code` | `MerchantStatusReason?` | tối đa 50 ký tự, enum, có thể rỗng |
| `statusReasonDetail` | `status_reason_detail` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `statusChangedAt` | `status_changed_at` | `Instant` | bắt buộc |
| `statusChangedByUserId` | `status_changed_by_user_id` | `UUID?` | có thể rỗng |
| `verifiedAt` | `verified_at` | `Instant?` | có thể rỗng |
| `verifiedByUserId` | `verified_by_user_id` | `UUID?` | có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>MerchantStatusHistory</b> — `partners.merchant_status_history` · 8 thuộc tính — Lịch sử đổi trạng thái merchant và lý do</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `merchantId` | `merchant_id` | `UUID` | bắt buộc |
| `fromStatus` | `from_status` | `MerchantStatus?` | tối đa 50 ký tự, enum, có thể rỗng |
| `toStatus` | `to_status` | `MerchantStatus` | tối đa 50 ký tự, enum, bắt buộc |
| `reasonCode` | `reason_code` | `MerchantStatusReason?` | tối đa 50 ký tự, enum, có thể rỗng |
| `reasonDetail` | `reason_detail` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `changedByUserId` | `changed_by_user_id` | `UUID?` | có thể rỗng |
| `changedAt` | `changed_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>MerchantPickupAddress</b> — `partners.merchant_pickup_addresses` · 11 thuộc tính — Điểm lấy hàng của shop (dùng làm người gửi trên vận đơn), có điểm mặc định</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `merchantId` | `merchant_id` | `UUID` | bắt buộc |
| `name` | `name` | `String` | tối đa 100 ký tự, bắt buộc |
| `contactName` | `contact_name` | `String` | tối đa 200 ký tự, bắt buộc |
| `contactPhone` | `contact_phone` | `String` | tối đa 30 ký tự, bắt buộc |
| `addressLine` | `address_line` | `String` | tối đa 500 ký tự, bắt buộc |
| `provinceCode` | `province_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `wardCode` | `ward_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `isDefault` | `is_default` | `Boolean` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>MerchantRoleOutboxMessage</b> — `partners.merchant_role_outbox` · 11 thuộc tính — Hàng đợi đồng bộ vai trò MERCHANT sang Identity</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `merchantId` | `merchant_id` | `UUID` | bắt buộc |
| `userId` | `user_id` | `UUID` | bắt buộc |
| `enabled` | `enabled` | `Boolean` | bắt buộc |
| `assignedByUserId` | `assigned_by_user_id` | `UUID?` | có thể rỗng |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |
| `availableAt` | `available_at` | `Instant` | bắt buộc |
| `processedAt` | `processed_at` | `Instant?` | có thể rỗng |
| `attemptCount` | `attempt_count` | `Int` | bắt buộc |
| `lastError` | `last_error` | `String?` | tối đa 2000 ký tự, có thể rỗng |
| `supersededAt` | `superseded_at` | `Instant?` | có thể rỗng |

</details>

<details>
<summary><b>SupportRequest</b> — `partners.support_requests` · 20 thuộc tính — Yêu cầu liên hệ / hỗ trợ / khiếu nại / ứng tuyển gửi từ website</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 20 ký tự, bắt buộc |
| `kind` | `kind` | `SupportRequestKind` | tối đa 20 ký tự, enum, bắt buộc |
| `status` | `status` | `SupportRequestStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `fullName` | `full_name` | `String` | tối đa 100 ký tự, bắt buộc |
| `email` | `email` | `String` | tối đa 160 ký tự, bắt buộc |
| `phone` | `phone` | `String?` | tối đa 20 ký tự, có thể rỗng |
| `topic` | `topic` | `String` | tối đa 100 ký tự, bắt buộc |
| `trackingCode` | `tracking_code` | `String?` | tối đa 40 ký tự, có thể rỗng |
| `incidentDate` | `incident_date` | `LocalDate?` | có thể rỗng |
| `claimedAmount` | `claimed_amount` | `Long?` | có thể rỗng |
| `portfolioUrl` | `portfolio_url` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `message` | `message` | `String` | tối đa 2000 ký tự, bắt buộc |
| `submittedByUserId` | `submitted_by_user_id` | `UUID?` | có thể rỗng |
| `assigneeUserId` | `assignee_user_id` | `UUID?` | có thể rỗng |
| `resolutionNote` | `resolution_note` | `String?` | tối đa 1000 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `closedAt` | `closed_at` | `Instant?` | có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>SupportRequestAttachment</b> — `partners.support_request_attachments` · 7 thuộc tính — Tệp bằng chứng của khiếu nại (ảnh/PDF, tối đa 5 × 5 MB)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `requestId` | `request_id` | `UUID` | bắt buộc |
| `fileName` | `file_name` | `String` | tối đa 200 ký tự, bắt buộc |
| `contentType` | `content_type` | `String` | tối đa 50 ký tự, bắt buộc |
| `sizeBytes` | `size_bytes` | `Int` | bắt buộc |
| `data` | `data` | `ByteArray` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>SupportRequestEvent</b> — `partners.support_request_events` · 8 thuộc tính — Lịch sử xử lý yêu cầu hỗ trợ</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `requestId` | `request_id` | `UUID` | bắt buộc |
| `actorUserId` | `actor_user_id` | `UUID?` | có thể rỗng |
| `action` | `action` | `SupportRequestAction` | tối đa 30 ký tự, enum, bắt buộc |
| `fromStatus` | `from_status` | `SupportRequestStatus?` | tối đa 20 ký tự, enum, có thể rỗng |
| `toStatus` | `to_status` | `SupportRequestStatus?` | tối đa 20 ký tự, enum, có thể rỗng |
| `note` | `note` | `String?` | tối đa 1000 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>UserAddress</b> — `partners.user_addresses` · 11 thuộc tính — Sổ địa chỉ cá nhân của khách</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `userId` | `user_id` | `UUID` | bắt buộc |
| `label` | `label` | `String` | tối đa 100 ký tự, bắt buộc |
| `contactName` | `contact_name` | `String` | tối đa 200 ký tự, bắt buộc |
| `contactPhone` | `contact_phone` | `String` | tối đa 30 ký tự, bắt buộc |
| `addressLine` | `address_line` | `String` | tối đa 500 ký tự, bắt buộc |
| `provinceCode` | `province_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `wardCode` | `ward_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `isDefault` | `is_default` | `Boolean` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |

</details>

<details>
<summary>Enum trạng thái và danh mục</summary>

| Enum | Giá trị |
|---|---|
| `MerchantStatus` | `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `REJECTED` |
| `MerchantStatusReason` | `DOCUMENT_INVALID`, `FRAUD_REVIEW`, `POLICY_VIOLATION`, `MERCHANT_REQUEST`, `OTHER` |
| `SupportRequestKind` | `CONTACT`, `SUPPORT`, `CLAIM`, `CAREER` |
| `SupportRequestStatus` | `NEW`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `SupportRequestAction` | `SUBMITTED`, `STATUS_CHANGED`, `ASSIGNED`, `UNASSIGNED` |

</details>

<details>
<summary>API (34 endpoint)</summary>

| Method | Đường dẫn | Quyền |
|---|---|---|
| `GET` | `/api/v1/admin/merchants` | `partners.merchants.read` |
| `GET` | `/api/v1/admin/merchants/{merchantId}` | `partners.merchants.read` |
| `GET` | `/api/v1/admin/merchants/{merchantId}/readiness` | `partners.merchants.read` |
| `PATCH` | `/api/v1/admin/merchants/{merchantId}/status` | `partners.merchants.manage-status` |
| `GET` | `/api/v1/admin/support/requests` | `support.requests.read` |
| `GET` | `/api/v1/admin/support/requests/counts` | `support.requests.read` |
| `GET` | `/api/v1/admin/support/requests/{id}` | `support.requests.read` |
| `POST` | `/api/v1/admin/support/requests/{id}/assignee` | `support.requests.manage` |
| `GET` | `/api/v1/admin/support/requests/{id}/attachments/{attachmentId}` | `support.requests.read` |
| `POST` | `/api/v1/admin/support/requests/{id}/status` | `support.requests.manage` |
| `GET` | `/api/v1/locations/provinces` | Công khai |
| `GET` | `/api/v1/locations/provinces/{provinceCode}/wards` | Công khai |
| `GET` | `/api/v1/locations/wards/{wardCode}` | Công khai |
| `GET` | `/api/v1/merchants/me/pickup-addresses` | Đăng nhập (dữ liệu của chính mình) |
| `POST` | `/api/v1/merchants/me/pickup-addresses` | Đăng nhập (dữ liệu của chính mình) |
| `DELETE` | `/api/v1/merchants/me/pickup-addresses/{addressId}` | Đăng nhập (dữ liệu của chính mình) |
| `GET` | `/api/v1/merchants/me/pickup-addresses/{addressId}` | Đăng nhập (dữ liệu của chính mình) |
| `PUT` | `/api/v1/merchants/me/pickup-addresses/{addressId}` | Đăng nhập (dữ liệu của chính mình) |
| `PATCH` | `/api/v1/merchants/me/pickup-addresses/{addressId}/default` | Đăng nhập (dữ liệu của chính mình) |
| `GET` | `/api/v1/merchants/me/profile` | Đăng nhập (dữ liệu của chính mình) |
| `POST` | `/api/v1/merchants/me/profile` | Đăng nhập (dữ liệu của chính mình) |
| `PUT` | `/api/v1/merchants/me/profile` | Đăng nhập (dữ liệu của chính mình) |
| `POST` | `/api/v1/merchants/me/profile/submit-verification` | Đăng nhập (dữ liệu của chính mình) |
| `GET` | `/api/v1/partners/me/addresses` | Đăng nhập (dữ liệu của chính mình) |
| `POST` | `/api/v1/partners/me/addresses` | Đăng nhập (dữ liệu của chính mình) |
| `DELETE` | `/api/v1/partners/me/addresses/{addressId}` | Đăng nhập (dữ liệu của chính mình) |
| `GET` | `/api/v1/partners/me/addresses/{addressId}` | Đăng nhập (dữ liệu của chính mình) |
| `PUT` | `/api/v1/partners/me/addresses/{addressId}` | Đăng nhập (dữ liệu của chính mình) |
| `PATCH` | `/api/v1/partners/me/addresses/{addressId}/default` | Đăng nhập (dữ liệu của chính mình) |
| `POST` | `/api/v1/support/requests` | Công khai |
| `POST` | `/internal/v1/locations/resolve` | Nội bộ (JWT dịch vụ, scope `partners.locations.read`) |
| `GET` | `/internal/v1/merchants/by-user/{userId}` | Nội bộ (JWT dịch vụ, scope `partners.merchants.read`) |
| `GET` | `/internal/v1/merchants/by-user/{userId}/order-capability` | Nội bộ (JWT dịch vụ, scope `partners.orders.evaluate`) |
| `GET` | `/internal/v1/merchants/{merchantId}/pickup-addresses/{pickupAddressId}` | Nội bộ (JWT dịch vụ, scope `partners.pickup-addresses.read`) |

</details>


### 6.4 Network — `pavex_network` (5436, service 8085)

<details>
<summary><b>Hub</b> — `network.hubs` · 14 thuộc tính — Điểm khai thác: trung tâm miền, kho tỉnh, bưu cục phường/xã</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 50 ký tự, duy nhất, bắt buộc |
| `name` | `name` | `String` | tối đa 200 ký tự, bắt buộc |
| `type` | `type` | `HubType` | tối đa 30 ký tự, enum, bắt buộc |
| `status` | `status` | `HubStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `addressLine` | `address_line` | `String` | tối đa 500 ký tự, bắt buộc |
| `provinceCode` | `province_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `wardCode` | `ward_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `parentHubId` | `parent_hub_id` | `UUID?` | có thể rỗng |
| `publicVisible` | `public_visible` | `Boolean` | bắt buộc |
| `publicName` | `public_name` | `String?` | tối đa 200 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>HubLane</b> — `network.hub_lanes` · 12 thuộc tính — Tuyến vận chuyển giữa hai hub (phương tiện, thời gian, sức chứa)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 50 ký tự, duy nhất, bắt buộc |
| `fromHubId` | `from_hub_id` | `UUID` | bắt buộc |
| `toHubId` | `to_hub_id` | `UUID` | bắt buộc |
| `transportMode` | `transport_mode` | `TransportMode` | tối đa 20 ký tự, enum, bắt buộc |
| `estimatedTransitMinutes` | `estimated_transit_minutes` | `Int` | bắt buộc |
| `distanceKm` | `distance_km` | `BigDecimal?` | có thể rỗng |
| `maxShipmentWeightKg` | `max_shipment_weight_kg` | `BigDecimal` | bắt buộc |
| `status` | `status` | `HubLaneStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>LaneCapacityReservation</b> — `network.lane_capacity_reservations` · 22 thuộc tính — Chỗ giữ trên từng chặng cho một vận đơn</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `batchId` | `batch_id` | `UUID` | bắt buộc |
| `shipmentId` | `shipment_id` | `UUID` | bắt buộc |
| `routeRevision` | `route_revision` | `Int` | bắt buộc |
| `laneId` | `lane_id` | `UUID` | bắt buộc |
| `scheduleId` | `schedule_id` | `UUID` | bắt buộc |
| `legSequence` | `leg_sequence` | `Int` | bắt buộc |
| `serviceDate` | `service_date` | `LocalDate` | bắt buộc |
| `departureAt` | `departure_at` | `Instant` | bắt buộc |
| `arrivalAt` | `arrival_at` | `Instant` | bắt buộc |
| `reservedWeightKg` | `reserved_weight_kg` | `BigDecimal` | bắt buộc |
| `departureCapacityKg` | `departure_capacity_kg` | `BigDecimal` | bắt buộc |
| `routeSource` | `route_source` | `CapacityRouteSource` | tối đa 30 ký tự, enum, bắt buộc |
| `routeTemplateId` | `route_template_id` | `UUID?` | có thể rỗng |
| `routeTemplateCode` | `route_template_code` | `String?` | tối đa 80 ký tự, có thể rỗng |
| `routeTemplateRevision` | `route_template_revision` | `Int?` | có thể rỗng |
| `status` | `status` | `LaneCapacityReservationStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `confirmationDeadlineAt` | `confirmation_deadline_at` | `Instant?` | có thể rỗng |
| `confirmedAt` | `confirmed_at` | `Instant?` | có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>LaneSchedule</b> — `network.lane_schedules` · 17 thuộc tính — Lịch chạy của tuyến (giờ xuất phát, ngày trong tuần)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `laneId` | `lane_id` | `UUID` | bắt buộc |
| `departureTime` | `departure_time` | `LocalTime` | bắt buộc |
| `monday` | `monday` | `Boolean` | bắt buộc |
| `tuesday` | `tuesday` | `Boolean` | bắt buộc |
| `wednesday` | `wednesday` | `Boolean` | bắt buộc |
| `thursday` | `thursday` | `Boolean` | bắt buộc |
| `friday` | `friday` | `Boolean` | bắt buộc |
| `saturday` | `saturday` | `Boolean` | bắt buộc |
| `sunday` | `sunday` | `Boolean` | bắt buộc |
| `effectiveFrom` | `effective_from` | `LocalDate` | bắt buộc |
| `effectiveTo` | `effective_to` | `LocalDate?` | có thể rỗng |
| `capacityKg` | `capacity_kg` | `BigDecimal` | bắt buộc |
| `status` | `status` | `LaneScheduleStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>NetworkAuditLog</b> — `network.audit_logs` · 9 thuộc tính — Nhật ký thay đổi cấu hình mạng lưới</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `entityType` | `entity_type` | `NetworkAuditEntityType` | tối đa 30 ký tự, enum, bắt buộc |
| `entityId` | `entity_id` | `UUID` | bắt buộc |
| `action` | `action` | `NetworkAuditAction` | tối đa 30 ký tự, enum, bắt buộc |
| `actorUserId` | `actor_user_id` | `UUID` | bắt buộc |
| `beforeJson` | `before_json` | `String?` | có thể rỗng |
| `afterJson` | `after_json` | `String?` | có thể rỗng |
| `reason` | `reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>NetworkRegion</b> — `network.regions` · 7 thuộc tính — Vùng mạng lưới (Bắc, Trung, Nam)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 50 ký tự, duy nhất, bắt buộc |
| `name` | `name` | `String` | tối đa 160 ký tự, bắt buộc |
| `status` | `status` | `NetworkRegionStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>RouteTemplate</b> — `network.route_templates` · 12 thuộc tính — Hành trình ưu tiên giữa hai hub</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 80 ký tự, duy nhất, bắt buộc |
| `name` | `name` | `String` | tối đa 160 ký tự, bắt buộc |
| `description` | `description` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `originHubId` | `origin_hub_id` | `UUID` | bắt buộc |
| `destinationHubId` | `destination_hub_id` | `UUID` | bắt buộc |
| `priority` | `priority` | `Int` | bắt buộc |
| `status` | `status` | `RouteTemplateStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `revision` | `revision` | `Int` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>RouteTemplateLeg</b> — `network.route_template_legs` · 4 thuộc tính — Các chặng của hành trình ưu tiên</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `routeTemplateId` | `route_template_id` | `UUID` | bắt buộc |
| `sequence` | `sequence` | `Int` | bắt buộc |
| `laneId` | `lane_id` | `UUID` | bắt buộc |

</details>

<details>
<summary><b>ServiceArea</b> — `network.service_areas` · 9 thuộc tính — Vùng phục vụ gắn với một hub chính</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 50 ký tự, duy nhất, bắt buộc |
| `name` | `name` | `String` | tối đa 200 ký tự, bắt buộc |
| `regionId` | `region_id` | `UUID` | bắt buộc |
| `primaryHubId` | `primary_hub_id` | `UUID` | bắt buộc |
| `status` | `status` | `ServiceAreaStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>ServiceAreaCoverage</b> — `network.service_area_coverages` · 8 thuộc tính — Phường/xã nào thuộc vùng phục vụ nào, mức phủ (thường/vùng xa/hải đảo)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `serviceAreaId` | `service_area_id` | `UUID` | bắt buộc |
| `provinceCode` | `province_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `wardCode` | `ward_code` | `String?` | tối đa 20 ký tự, có thể rỗng |
| `tier` | `tier` | `CoverageTier` | tối đa 20 ký tự, enum, bắt buộc |
| `isActive` | `is_active` | `Boolean` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |

</details>

<details>
<summary>Enum trạng thái và danh mục</summary>

| Enum | Giá trị |
|---|---|
| `CapacityReservationBatchStatus` | `RESERVED`, `IN_TRANSIT`, `CONSUMED`, `RELEASED`, `PARTIALLY_CONSUMED`, `NOT_REQUIRED` |
| `RoutePlanningMode` | `PREFER_TEMPLATE`, `GRAPH_ONLY`, `TEMPLATE_ONLY` |
| `RoutePlanSource` | `LOCAL`, `ROUTE_TEMPLATE`, `GRAPH` |
| `CoverageMatch` | `WARD`, `PROVINCE` |
| `HubType` | `REGIONAL_GATEWAY`, `PROVINCIAL`, `LOCAL` |
| `HubStatus` | `INACTIVE`, `ACTIVE` |
| `TransportMode` | `ROAD`, `AIR`, `RAIL`, `SEA` |
| `HubLaneStatus` | `INACTIVE`, `ACTIVE` |
| `LaneCapacityReservationStatus` | `RESERVED`, `CONSUMED`, `RELEASED` |
| `CapacityRouteSource` | `ROUTE_TEMPLATE`, `GRAPH` |
| `LaneScheduleStatus` | `INACTIVE`, `ACTIVE` |
| `NetworkAuditEntityType` | `REGION`, `HUB`, `SERVICE_AREA`, `COVERAGE`, `LANE`, `LANE_SCHEDULE`, `ROUTE_TEMPLATE`, `CAPACITY_RESERVATION` |
| `NetworkAuditAction` | `CREATED`, `UPDATED`, `ACTIVATED`, `DEACTIVATED`, `DELETED`, `RESERVED`, `CONSUMED`, `RELEASED` |
| `NetworkRegionStatus` | `INACTIVE`, `ACTIVE` |
| `RouteTemplateStatus` | `INACTIVE`, `ACTIVE` |
| `ServiceAreaStatus` | `INACTIVE`, `ACTIVE` |
| `CoverageTier` | `STANDARD`, `REMOTE`, `ISLAND` |

</details>

<details>
<summary>API (56 endpoint)</summary>

| Method | Đường dẫn | Quyền |
|---|---|---|
| `GET` | `/api/v1/admin/network/audit` | `network.read` hoặc `network.manage` |
| `GET` | `/api/v1/admin/network/audit/{id}` | `network.read` hoặc `network.manage` |
| `POST` | `/api/v1/admin/network/capacity/reservations` | `network.manage` |
| `GET` | `/api/v1/admin/network/capacity/reservations/{shipmentId}/revisions/{routeRevision}` | `network.read` hoặc `network.manage` |
| `POST` | `/api/v1/admin/network/capacity/reservations/{shipmentId}/revisions/{routeRevision}/legs/{legSequence}/consume` | `network.manage` |
| `POST` | `/api/v1/admin/network/capacity/reservations/{shipmentId}/revisions/{routeRevision}/release` | `network.manage` |
| `GET` | `/api/v1/admin/network/coverages` | `network.read` hoặc `network.manage` |
| `POST` | `/api/v1/admin/network/coverages` | `network.manage` |
| `GET` | `/api/v1/admin/network/coverages/{id}` | `network.read` hoặc `network.manage` |
| `PUT` | `/api/v1/admin/network/coverages/{id}` | `network.manage` |
| `PATCH` | `/api/v1/admin/network/coverages/{id}/status` | `network.manage` |
| `GET` | `/api/v1/admin/network/hubs` | `network.read` hoặc `network.manage` |
| `POST` | `/api/v1/admin/network/hubs` | `network.manage` |
| `GET` | `/api/v1/admin/network/hubs/{id}` | `network.read` hoặc `network.manage` |
| `PUT` | `/api/v1/admin/network/hubs/{id}` | `network.manage` |
| `PATCH` | `/api/v1/admin/network/hubs/{id}/status` | `network.manage` |
| `GET` | `/api/v1/admin/network/lanes` | `network.read` hoặc `network.manage` |
| `POST` | `/api/v1/admin/network/lanes` | `network.manage` |
| `GET` | `/api/v1/admin/network/lanes/{id}` | `network.read` hoặc `network.manage` |
| `PUT` | `/api/v1/admin/network/lanes/{id}` | `network.manage` |
| `PATCH` | `/api/v1/admin/network/lanes/{id}/status` | `network.manage` |
| `GET` | `/api/v1/admin/network/lanes/{laneId}/capacity` | `network.read` hoặc `network.manage` |
| `GET` | `/api/v1/admin/network/lanes/{laneId}/schedules` | `network.read` hoặc `network.manage` |
| `POST` | `/api/v1/admin/network/lanes/{laneId}/schedules` | `network.manage` |
| `GET` | `/api/v1/admin/network/lanes/{laneId}/schedules/{scheduleId}` | `network.read` hoặc `network.manage` |
| `PUT` | `/api/v1/admin/network/lanes/{laneId}/schedules/{scheduleId}` | `network.manage` |
| `PATCH` | `/api/v1/admin/network/lanes/{laneId}/schedules/{scheduleId}/status` | `network.manage` |
| `GET` | `/api/v1/admin/network/regions` | `network.read` hoặc `network.manage` |
| `POST` | `/api/v1/admin/network/regions` | `network.manage` |
| `GET` | `/api/v1/admin/network/regions/{id}` | `network.read` hoặc `network.manage` |
| `PUT` | `/api/v1/admin/network/regions/{id}` | `network.manage` |
| `PATCH` | `/api/v1/admin/network/regions/{id}/status` | `network.manage` |
| `GET` | `/api/v1/admin/network/route-templates` | `network.read` hoặc `network.manage` |
| `POST` | `/api/v1/admin/network/route-templates` | `network.manage` |
| `GET` | `/api/v1/admin/network/route-templates/journey` | `network.read` hoặc `network.manage` |
| `DELETE` | `/api/v1/admin/network/route-templates/{id}` | `network.manage` |
| `GET` | `/api/v1/admin/network/route-templates/{id}` | `network.read` hoặc `network.manage` |
| `PUT` | `/api/v1/admin/network/route-templates/{id}` | `network.manage` |
| `PATCH` | `/api/v1/admin/network/route-templates/{id}/status` | `network.manage` |
| `GET` | `/api/v1/admin/network/routing/plan` | `network.read` hoặc `network.manage` |
| `GET` | `/api/v1/admin/network/routing/resolve` | `network.read` hoặc `network.manage` |
| `GET` | `/api/v1/admin/network/service-areas` | `network.read` hoặc `network.manage` |
| `POST` | `/api/v1/admin/network/service-areas` | `network.manage` |
| `GET` | `/api/v1/admin/network/service-areas/{id}` | `network.read` hoặc `network.manage` |
| `PUT` | `/api/v1/admin/network/service-areas/{id}` | `network.manage` |
| `PATCH` | `/api/v1/admin/network/service-areas/{id}/status` | `network.manage` |
| `GET` | `/api/v1/network/hubs` | Công khai |
| `POST` | `/internal/v1/network/fleet/route-legs` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/network/locations/resolve` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/network/operations/hubs/snapshots` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/network/operations/hubs/snapshots-by-codes` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/network/shipments/capacity/reservations` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/network/shipments/capacity/reservations/{shipmentId}/revisions/{routeRevision}/confirm` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/network/shipments/capacity/reservations/{shipmentId}/revisions/{routeRevision}/release` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/network/shipments/hubs/{hubId}` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/network/shipments/routes/{shipmentId}/revisions/{routeRevision}/hubs/{hubId}` | Nội bộ (JWT dịch vụ) |

</details>


### 6.5 Pricing — `pavex_pricing` (5437, service 8086)

<details>
<summary><b>PricingAuditLog</b> — `pricing.audit_logs` · 9 thuộc tính — Nhật ký thay đổi bảng giá</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `entityType` | `entity_type` | `PricingAuditEntityType` | tối đa 30 ký tự, enum, bắt buộc |
| `entityId` | `entity_id` | `UUID` | bắt buộc |
| `action` | `action` | `PricingAuditAction` | tối đa 30 ký tự, enum, bắt buộc |
| `actorUserId` | `actor_user_id` | `UUID` | bắt buộc |
| `beforeJson` | `before_json` | `String?` | có thể rỗng |
| `afterJson` | `after_json` | `String?` | có thể rỗng |
| `reason` | `reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>QuoteOption</b> — `pricing.quote_options` · 11 thuộc tính — Phương án giá của lần tính cước (Tiêu chuẩn / Nhanh)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `quoteRequest` | `quote_request_id` | `QuoteRequest` | quan hệ n-1, bắt buộc |
| `serviceLevel` | `service_level` | `ServiceLevel` | tối đa 20 ký tự, enum, bắt buộc |
| `baseFee` | `base_fee` | `Long` | bắt buộc |
| `additionalWeightFee` | `additional_weight_fee` | `Long` | bắt buộc |
| `areaSurcharge` | `area_surcharge` | `Long` | bắt buộc |
| `codFee` | `cod_fee` | `Long` | bắt buộc |
| `insuranceFee` | `insurance_fee` | `Long` | bắt buộc |
| `total` | `total` | `Long` | bắt buộc |
| `estimatedMinDays` | `estimated_min_days` | `Int` | bắt buộc |
| `estimatedMaxDays` | `estimated_max_days` | `Int` | bắt buộc |

</details>

<details>
<summary><b>QuoteParcel</b> — `pricing.quote_parcels` · 9 thuộc tính — Kiện trong lần tính cước (cân nặng thật, quy đổi, tính cước, kích thước)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `quoteRequest` | `quote_request_id` | `QuoteRequest` | quan hệ n-1, bắt buộc |
| `sequence` | `sequence` | `Int` | bắt buộc |
| `actualWeightKg` | `actual_weight_kg` | `BigDecimal` | bắt buộc |
| `volumetricWeightKg` | `volumetric_weight_kg` | `BigDecimal` | bắt buộc |
| `chargeableWeightKg` | `chargeable_weight_kg` | `BigDecimal` | bắt buộc |
| `lengthCm` | `length_cm` | `BigDecimal` | bắt buộc |
| `widthCm` | `width_cm` | `BigDecimal` | bắt buộc |
| `heightCm` | `height_cm` | `BigDecimal` | bắt buộc |

</details>

<details>
<summary><b>QuoteRequest</b> — `pricing.quote_requests` · 28 thuộc tính — Một lần tính cước (tuyến, cân nặng quy đổi, COD, hạn hiệu lực)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `ratePlanId` | `rate_plan_id` | `UUID` | bắt buộc |
| `rateCode` | `rate_code` | `String` | tối đa 60 ký tự, bắt buộc |
| `rateRevision` | `rate_revision` | `Int` | bắt buộc |
| `rateVersion` | `rate_version` | `String` | tối đa 80 ký tự, bắt buộc |
| `pricingZone` | `pricing_zone` | `PricingZone` | tối đa 30 ký tự, enum, bắt buộc |
| `originProvinceCode` | `origin_province_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `originWardCode` | `origin_ward_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `originServiceAreaId` | `origin_service_area_id` | `UUID` | bắt buộc |
| `originRegionId` | `origin_region_id` | `UUID` | bắt buộc |
| `originPrimaryHubId` | `origin_primary_hub_id` | `UUID` | bắt buộc |
| `originCoverageTier` | `origin_coverage_tier` | `CoverageTier` | tối đa 20 ký tự, enum, bắt buộc |
| `destinationProvinceCode` | `destination_province_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `destinationWardCode` | `destination_ward_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `destinationServiceAreaId` | `destination_service_area_id` | `UUID` | bắt buộc |
| `destinationRegionId` | `destination_region_id` | `UUID` | bắt buộc |
| `destinationPrimaryHubId` | `destination_primary_hub_id` | `UUID` | bắt buộc |
| `destinationCoverageTier` | `destination_coverage_tier` | `CoverageTier` | tối đa 20 ký tự, enum, bắt buộc |
| `actualWeightKg` | `actual_weight_kg` | `BigDecimal` | bắt buộc |
| `volumetricWeightKg` | `volumetric_weight_kg` | `BigDecimal` | bắt buộc |
| `chargeableWeightKg` | `chargeable_weight_kg` | `BigDecimal` | bắt buộc |
| `parcelCount` | `parcel_count` | `Int` | bắt buộc |
| `codAmount` | `cod_amount` | `Long` | bắt buộc |
| `declaredValue` | `declared_value` | `Long` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `expiresAt` | `expires_at` | `Instant` | bắt buộc |
| `parcels` | — | `MutableList<QuoteParcel>` | quan hệ 1-n |
| `options` | — | `MutableList<QuoteOption>` | quan hệ 1-n |

</details>

<details>
<summary><b>RatePlan</b> — `pricing.rate_plans` · 31 thuộc tính — Bảng giá (phiên bản, khoảng hiệu lực, phụ phí, COD, bảo hiểm, giới hạn)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 60 ký tự, bắt buộc |
| `revision` | `revision` | `Int` | bắt buộc |
| `name` | `name` | `String` | tối đa 200 ký tự, bắt buộc |
| `currency` | `currency` | `String` | tối đa 3 ký tự, bắt buộc |
| `status` | `status` | `RatePlanStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `effectiveFrom` | `effective_from` | `Instant?` | có thể rỗng |
| `effectiveTo` | `effective_to` | `Instant?` | có thể rỗng |
| `quoteTtlMinutes` | `quote_ttl_minutes` | `Int` | bắt buộc |
| `volumetricDivisor` | `volumetric_divisor` | `BigDecimal` | bắt buộc |
| `remoteEndpointSurcharge` | `remote_endpoint_surcharge` | `Long` | bắt buộc |
| `islandTransportRate` | `island_transport_rate` | `BigDecimal` | bắt buộc |
| `codFreeThreshold` | `cod_free_threshold` | `Long` | bắt buộc |
| `codRate` | `cod_rate` | `BigDecimal` | bắt buộc |
| `codMinimumFee` | `cod_minimum_fee` | `Long` | bắt buộc |
| `maximumCod` | `maximum_cod` | `Long` | bắt buộc |
| `insuranceFreeThreshold` | `insurance_free_threshold` | `Long` | bắt buộc |
| `insuranceRate` | `insurance_rate` | `BigDecimal` | bắt buộc |
| `maximumDeclaredValue` | `maximum_declared_value` | `Long` | bắt buộc |
| `maxParcelWeightKg` | `max_parcel_weight_kg` | `BigDecimal` | bắt buộc |
| `maxShipmentChargeableWeightKg` | `max_shipment_chargeable_weight_kg` | `BigDecimal` | bắt buộc |
| `maxDimensionCm` | `max_dimension_cm` | `BigDecimal` | bắt buộc |
| `createdBy` | `created_by` | `UUID?` | có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `activatedBy` | `activated_by` | `UUID?` | có thể rỗng |
| `activatedAt` | `activated_at` | `Instant?` | có thể rỗng |
| `retiredBy` | `retired_by` | `UUID?` | có thể rỗng |
| `retiredAt` | `retired_at` | `Instant?` | có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |
| `rules` | — | `MutableList<RateRule>` | quan hệ 1-n |

</details>

<details>
<summary><b>RateRule</b> — `pricing.rate_rules` · 12 thuộc tính — Quy tắc giá theo dịch vụ × vùng giá (6 quy tắc mỗi bảng)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `ratePlan` | `rate_plan_id` | `RatePlan` | quan hệ n-1, bắt buộc |
| `serviceLevel` | `service_level` | `ServiceLevel` | tối đa 20 ký tự, enum, bắt buộc |
| `pricingZone` | `pricing_zone` | `PricingZone` | tối đa 30 ký tự, enum, bắt buộc |
| `baseFee` | `base_fee` | `Long` | bắt buộc |
| `includedWeightKg` | `included_weight_kg` | `BigDecimal` | bắt buộc |
| `additionalWeightStepKg` | `additional_weight_step_kg` | `BigDecimal` | bắt buộc |
| `additionalWeightStepFee` | `additional_weight_step_fee` | `Long` | bắt buộc |
| `estimatedMinDays` | `estimated_min_days` | `Int` | bắt buộc |
| `estimatedMaxDays` | `estimated_max_days` | `Int` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |

</details>

<details>
<summary>Enum trạng thái và danh mục</summary>

| Enum | Giá trị |
|---|---|
| `PricingAuditEntityType` | `RATE_PLAN`, `RATE_RULE` |
| `PricingAuditAction` | `CREATED`, `UPDATED`, `RULE_UPSERTED`, `RULE_DELETED`, `REVISION_CREATED`, `ACTIVATED`, `RETIRED`, `DELETED`, `SUPERSEDED` |
| `RatePlanStatus` | `DRAFT`, `ACTIVE`, `RETIRED` |
| `ServiceLevel` | `STANDARD`, `EXPRESS` |
| `PricingZone` | `SAME_PROVINCE`, `SAME_REGION`, `CROSS_REGION` |
| `CoverageTier` | `STANDARD`, `REMOTE`, `ISLAND` |

</details>

<details>
<summary>API (16 endpoint)</summary>

| Method | Đường dẫn | Quyền |
|---|---|---|
| `GET` | `/api/v1/admin/pricing/audit` | `pricing.read` hoặc `pricing.manage` |
| `GET` | `/api/v1/admin/pricing/audit/{id}` | `pricing.read` hoặc `pricing.manage` |
| `GET` | `/api/v1/admin/pricing/quotes/{quoteId}` | `pricing.read` hoặc `pricing.manage` |
| `GET` | `/api/v1/admin/pricing/rate-plans` | `pricing.read` hoặc `pricing.manage` |
| `POST` | `/api/v1/admin/pricing/rate-plans` | `pricing.manage` |
| `GET` | `/api/v1/admin/pricing/rate-plans/current` | `pricing.read` hoặc `pricing.manage` |
| `DELETE` | `/api/v1/admin/pricing/rate-plans/{id}` | `pricing.manage` |
| `GET` | `/api/v1/admin/pricing/rate-plans/{id}` | `pricing.read` hoặc `pricing.manage` |
| `PUT` | `/api/v1/admin/pricing/rate-plans/{id}` | `pricing.manage` |
| `POST` | `/api/v1/admin/pricing/rate-plans/{id}/activate` | `pricing.manage` |
| `POST` | `/api/v1/admin/pricing/rate-plans/{id}/retire` | `pricing.manage` |
| `POST` | `/api/v1/admin/pricing/rate-plans/{id}/revisions` | `pricing.manage` |
| `DELETE` | `/api/v1/admin/pricing/rate-plans/{id}/rules/{serviceLevel}/{pricingZone}` | `pricing.manage` |
| `PUT` | `/api/v1/admin/pricing/rate-plans/{id}/rules/{serviceLevel}/{pricingZone}` | `pricing.manage` |
| `POST` | `/api/v1/pricing/quotes` | Công khai |
| `GET` | `/internal/v1/pricing/quotes/{quoteId}` | Nội bộ (JWT dịch vụ) |

</details>


### 6.6 Operations — `pavex_operations` (5439, service 8087)

<details>
<summary><b>HubMembership</b> — `operations.hub_memberships` · 9 thuộc tính — Nhân sự thuộc hub nào, hub chính</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `workforceMemberId` | `workforce_member_id` | `UUID` | bắt buộc |
| `hubId` | `hub_id` | `UUID` | bắt buộc |
| `isPrimary` | `is_primary` | `Boolean` | bắt buộc |
| `isActive` | `is_active` | `Boolean` | bắt buộc |
| `assignedAt` | `assigned_at` | `Instant` | bắt buộc |
| `unassignedAt` | `unassigned_at` | `Instant?` | có thể rỗng |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>OperationalAssignment</b> — `operations.operational_assignments` · 22 thuộc tính — Việc vận hành của một vận đơn (lấy hàng, khai thác, trung chuyển, giao)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `code` | `code` | `String` | tối đa 24 ký tự, duy nhất, bắt buộc |
| `shipmentId` | `shipment_id` | `UUID` | bắt buộc |
| `sequence` | `sequence` | `Int` | bắt buộc |
| `routeLegId` | `route_leg_id` | `UUID?` | có thể rỗng |
| `type` | `type` | `OperationalAssignmentType` | tối đa 40 ký tự, enum, bắt buộc |
| `requiredKind` | `required_kind` | `WorkforceMemberKind` | tối đa 32 ký tự, enum, bắt buộc |
| `hubId` | `hub_id` | `UUID` | bắt buộc |
| `assigneeWorkforceMemberId` | `assignee_workforce_member_id` | `UUID?` | có thể rỗng |
| `status` | `status` | `OperationalAssignmentStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `priority` | `priority` | `Int` | bắt buộc |
| `assignedByUserId` | `assigned_by_user_id` | `UUID?` | có thể rỗng |
| `assignedAt` | `assigned_at` | `Instant?` | có thể rỗng |
| `acceptedAt` | `accepted_at` | `Instant?` | có thể rỗng |
| `startedAt` | `started_at` | `Instant?` | có thể rỗng |
| `completedAt` | `completed_at` | `Instant?` | có thể rỗng |
| `cancelledAt` | `cancelled_at` | `Instant?` | có thể rỗng |
| `resolutionNote` | `resolution_note` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `lastActorUserId` | `last_actor_user_id` | `UUID?` | có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

Cột `last_dispatch_attempt_at` (V10) không có trong entity: chỉ hàng chờ giao việc đọc và ghi nó bằng SQL, để việc thử
giao lâu nhất được thử trước.

</details>

<details>
<summary><b>OperationalAssignmentEventLog</b> — `operations.operational_assignment_event_logs` · 10 thuộc tính — Lịch sử của một việc vận hành</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `assignmentId` | `assignment_id` | `UUID` | bắt buộc |
| `shipmentId` | `shipment_id` | `UUID` | bắt buộc |
| `eventType` | `event_type` | `OperationalAssignmentEventType` | tối đa 30 ký tự, enum, bắt buộc |
| `previousStatus` | `previous_status` | `OperationalAssignmentStatus?` | tối đa 20 ký tự, enum, có thể rỗng |
| `newStatus` | `new_status` | `OperationalAssignmentStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `workforceMemberId` | `workforce_member_id` | `UUID?` | có thể rỗng |
| `actorUserId` | `actor_user_id` | `UUID?` | có thể rỗng |
| `reason` | `reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>OperationsOutboxMessage</b> — `operations.outbox_messages` · 16 thuộc tính — Hàng đợi sự kiện vận hành (RabbitMQ), có hàng lỗi để gửi lại</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `eventType` | `event_type` | `String` | tối đa 200 ký tự, bắt buộc |
| `aggregateType` | `aggregate_type` | `String` | tối đa 100 ký tự, bắt buộc |
| `aggregateId` | `aggregate_id` | `UUID` | bắt buộc |
| `payload` | `payload` | `String` | bắt buộc |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |
| `availableAt` | `available_at` | `Instant` | bắt buộc |
| `processedAt` | `processed_at` | `Instant?` | có thể rỗng |
| `deadLetteredAt` | `dead_lettered_at` | `Instant?` | có thể rỗng |
| `discardedAt` | `discarded_at` | `Instant?` | có thể rỗng |
| `discardedByUserId` | `discarded_by_user_id` | `UUID?` | có thể rỗng |
| `discardReason` | `discard_reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `attemptCount` | `attempt_count` | `Int` | bắt buộc |
| `lastError` | `last_error` | `String?` | tối đa 2000 ký tự, có thể rỗng |
| `lockId` | `lock_id` | `UUID?` | có thể rỗng |
| `lockedUntil` | `locked_until` | `Instant?` | có thể rỗng |

</details>

<details>
<summary><b>WorkShift</b> — `operations.work_shifts` · 14 thuộc tính — Ca làm việc tại hub</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `hubId` | `hub_id` | `UUID` | bắt buộc |
| `code` | `code` | `String` | tối đa 24 ký tự, duy nhất, bắt buộc |
| `name` | `name` | `String` | tối đa 100 ký tự, bắt buộc |
| `startsAt` | `starts_at` | `Instant` | bắt buộc |
| `endsAt` | `ends_at` | `Instant` | bắt buộc |
| `status` | `status` | `WorkShiftStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `createdByUserId` | `created_by_user_id` | `UUID` | bắt buộc |
| `cancelledByUserId` | `cancelled_by_user_id` | `UUID?` | có thể rỗng |
| `cancelReason` | `cancel_reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `cancelledAt` | `cancelled_at` | `Instant?` | có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>WorkforceAuditLog</b> — `operations.workforce_audit_logs` · 9 thuộc tính — Nhật ký thay đổi nhân sự</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `workforceMemberId` | `workforce_member_id` | `UUID` | bắt buộc |
| `hubId` | `hub_id` | `UUID?` | có thể rỗng |
| `actorUserId` | `actor_user_id` | `UUID` | bắt buộc |
| `action` | `action` | `WorkforceAuditAction` | tối đa 40 ký tự, enum, bắt buộc |
| `previousValue` | `previous_value` | `String?` | tối đa 100 ký tự, có thể rỗng |
| `newValue` | `new_value` | `String?` | tối đa 100 ký tự, có thể rỗng |
| `reason` | `reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>WorkforceAvailability</b> — `operations.workforce_availabilities` · 5 thuộc tính — Trạng thái sẵn sàng nhận việc</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `workforceMemberId` | `workforce_member_id` | `UUID` | khoá chính |
| `status` | `status` | `WorkforceAvailabilityStatus` | tối đa 24 ký tự, enum, bắt buộc |
| `maxConcurrentAssignments` | `max_concurrent_assignments` | `Int` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>WorkforceMember</b> — `operations.workforce_members` · 8 thuộc tính — Nhân sự vận hành (loại, trạng thái, năng lực)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `userId` | `user_id` | `UUID` | duy nhất, bắt buộc |
| `employeeCode` | `employee_code` | `String` | tối đa 32 ký tự, duy nhất, bắt buộc |
| `kind` | `kind` | `WorkforceMemberKind` | tối đa 32 ký tự, enum, bắt buộc |
| `status` | `status` | `WorkforceMemberStatus` | tối đa 32 ký tự, enum, bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>WorkforceShiftAssignment</b> — `operations.workforce_shift_assignments` · 14 thuộc tính — Phân ca: ai làm ca nào, check-in/check-out/vắng</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shiftId` | `shift_id` | `UUID` | bắt buộc |
| `workforceMemberId` | `workforce_member_id` | `UUID` | bắt buộc |
| `status` | `status` | `WorkforceShiftAssignmentStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `assignedByUserId` | `assigned_by_user_id` | `UUID` | bắt buộc |
| `statusChangedByUserId` | `status_changed_by_user_id` | `UUID` | bắt buộc |
| `assignedAt` | `assigned_at` | `Instant` | bắt buộc |
| `checkedInAt` | `checked_in_at` | `Instant?` | có thể rỗng |
| `checkedOutAt` | `checked_out_at` | `Instant?` | có thể rỗng |
| `markedAbsentAt` | `marked_absent_at` | `Instant?` | có thể rỗng |
| `cancelledAt` | `cancelled_at` | `Instant?` | có thể rỗng |
| `note` | `note` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary>Enum trạng thái và danh mục</summary>

| Enum | Giá trị |
|---|---|
| `WorkforceOnboardingGender` | `MALE`, `FEMALE`, `OTHER` |
| `OperationalAssignmentType` | `PICKUP`, `ORIGIN_HUB_HANDLING`, `LINE_HAUL`, `TRANSIT_HUB_HANDLING`, `DESTINATION_HUB_HANDLING`, `LAST_MILE_DELIVERY` |
| `OperationalAssignmentStatus` | `BLOCKED`, `READY`, `PENDING`, `ASSIGNED`, `ACCEPTED`, `REJECTED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `OperationalAssignmentEventType` | `PLANNED`, `ACTIVATED`, `ASSIGNED`, `REASSIGNED`, `ACCEPTED`, `REJECTED`, `STARTED`, `COMPLETED`, `CANCELLED`, `REQUEUED` |
| `WorkShiftStatus` | `SCHEDULED`, `CANCELLED` |
| `WorkforceAuditAction` | `MEMBER_CREATED`, `STATUS_CHANGED`, `HUB_ASSIGNED`, `HUB_UNASSIGNED`, `CAPACITY_CHANGED` |
| `WorkforceAvailabilityStatus` | `OFF_DUTY`, `AVAILABLE`, `UNAVAILABLE` |
| `WorkforceMemberKind` | `COURIER`, `WAREHOUSE_OPERATOR`, `HUB_MANAGER`, `LINE_HAUL_DRIVER` |
| `WorkforceMemberStatus` | `ACTIVE`, `SUSPENDED`, `OFFBOARDED` |
| `WorkforceShiftAssignmentStatus` | `SCHEDULED`, `CHECKED_IN`, `CHECKED_OUT`, `ABSENT`, `CANCELLED` |

</details>

<details>
<summary>API (50 endpoint)</summary>

| Method | Đường dẫn | Quyền |
|---|---|---|
| `GET` | `/api/v1/admin/operations/assignments` | `operations.assignments.read` hoặc `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/assignments/{id}/assign` | `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/assignments/{id}/cancel` | `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/admin/operations/assignments/{id}/events` | `operations.assignments.read` hoặc `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/admin/operations/dispatch-events` | `operations.assignments.read` hoặc `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/admin/operations/outbox/dead-letters` | `operations.assignments.read` hoặc `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/admin/operations/outbox/dead-letters/{id}` | `operations.assignments.read` hoặc `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/outbox/dead-letters/{id}/discard` | `operations.assignments.read` hoặc `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/outbox/dead-letters/{id}/retry` | `operations.assignments.read` hoặc `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/admin/operations/outbox/summary` | `operations.assignments.read` hoặc `operations.assignments.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/admin/operations/shifts` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/shifts` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/admin/operations/shifts/{id}` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `PUT` | `/api/v1/admin/operations/shifts/{id}` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/shifts/{id}/cancel` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/shifts/{id}/members` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `DELETE` | `/api/v1/admin/operations/shifts/{id}/members/{memberId}` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/shifts/{id}/members/{memberId}/absent` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/admin/operations/workforce` | `operations.workforce.read` hoặc `operations.workforce.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/workforce` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/workforce/onboard` | `operations.workforce.manage` |
| `GET` | `/api/v1/admin/operations/workforce/{id}` | `operations.workforce.read` hoặc `operations.workforce.manage` hoặc `operations.dispatch.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/admin/operations/workforce/{id}/audit` | `operations.workforce.read` hoặc `operations.workforce.manage` hoặc `operations.hub.manage` |
| `PATCH` | `/api/v1/admin/operations/workforce/{id}/capacity` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `POST` | `/api/v1/admin/operations/workforce/{id}/hubs` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `DELETE` | `/api/v1/admin/operations/workforce/{id}/hubs/{hubId}` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `PATCH` | `/api/v1/admin/operations/workforce/{id}/status` | `operations.workforce.manage` hoặc `operations.hub.manage` |
| `GET` | `/api/v1/operations/assignments/mine` | `assignments.read-own` |
| `POST` | `/api/v1/operations/assignments/{id}/accept` | `assignments.accept-own` |
| `POST` | `/api/v1/operations/assignments/{id}/complete` | `assignments.execute-own` |
| `POST` | `/api/v1/operations/assignments/{id}/reject` | `assignments.execute-own` |
| `POST` | `/api/v1/operations/assignments/{id}/start` | `assignments.execute-own` |
| `GET` | `/api/v1/operations/availability` | `assignments.read-own` |
| `PUT` | `/api/v1/operations/availability` | `assignments.execute-own` |
| `GET` | `/api/v1/operations/me/hub-scope` | Đăng nhập (dữ liệu của chính mình) |
| `GET` | `/api/v1/operations/shifts/mine` | `assignments.read-own` |
| `POST` | `/api/v1/operations/shifts/{id}/check-in` | `assignments.execute-own` |
| `POST` | `/api/v1/operations/shifts/{id}/check-out` | `assignments.execute-own` |
| `POST` | `/internal/v1/operations/access/check` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/operations/fleet/users/{userId}/hub-scope` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/operations/fleet/users/{userId}/line-haul-assignments` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/operations/shipments/access/check` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/operations/shipments/assignments/by-user/{userId}` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/operations/shipments/case-admin-scope/{userId}` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/operations/shipments/{shipmentId}/assignments/{assignmentId}/authorize` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/operations/shipments/{shipmentId}/assignments/{assignmentId}/claim-handling` | Nội bộ (JWT dịch vụ): lượt quét tại kho nhận việc khai thác chưa ai chấp nhận |
| `POST` | `/internal/v1/operations/shipments/{shipmentId}/assignments/{assignmentId}/complete` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/operations/shipments/{shipmentId}/cancel` | Nội bộ (JWT dịch vụ) |
| `POST` | `/internal/v1/operations/shipments/{shipmentId}/plan` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/operations/shipments/{shipmentId}/route-legs/{routeLegId}/assignment` | Nội bộ (JWT dịch vụ) |
| `GET` | `/internal/v1/operations/shipments/{shipmentId}/types/{type}/hubs/{hubId}/assignment` | Nội bộ (JWT dịch vụ) |

</details>


### 6.7 Shipment — `pavex_shipments` (5438, service 8084)

<details>
<summary><b>DeliveryAttempt</b> — `shipments.delivery_attempts` · 17 thuộc tính — Lần giao hàng: thành công/thất bại, lý do, chứng từ</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shipment` | `shipment_id` | `Shipment` | quan hệ n-1, bắt buộc |
| `parcel` | `parcel_id` | `Parcel` | quan hệ n-1, bắt buộc |
| `actorUserId` | `actor_user_id` | `UUID` | bắt buộc |
| `attemptNumber` | `attempt_number` | `Int` | bắt buộc |
| `idempotencyKey` | `idempotency_key` | `String` | tối đa 100 ký tự, bắt buộc |
| `requestFingerprint` | `request_fingerprint` | `String` | tối đa 64 ký tự, bắt buộc |
| `outcome` | `outcome` | `DeliveryAttemptOutcome` | tối đa 20 ký tự, enum, bắt buộc |
| `failureReason` | `failure_reason` | `DeliveryFailureReason?` | tối đa 40 ký tự, enum, có thể rỗng |
| `proofMethod` | `proof_method` | `DeliveryProofMethod?` | tối đa 20 ký tự, enum, có thể rỗng |
| `recipientName` | `recipient_name` | `String?` | tối đa 200 ký tự, có thể rỗng |
| `evidenceReference` | `evidence_reference` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `note` | `note` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `latitude` | `latitude` | `BigDecimal?` | có thể rỗng |
| `longitude` | `longitude` | `BigDecimal?` | có thể rỗng |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>DeliveryEvidence</b> — `shipments.delivery_evidence` · 7 thuộc tính — Ảnh chứng từ giao hàng</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `uploadedByUserId` | `uploaded_by_user_id` | `UUID` | bắt buộc |
| `contentType` | `content_type` | `String` | tối đa 50 ký tự, bắt buộc |
| `sizeBytes` | `size_bytes` | `Int` | bắt buộc |
| `sha256` | `sha256` | `String` | tối đa 64 ký tự, bắt buộc |
| `data` | `data` | `ByteArray` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>Parcel</b> — `shipments.parcels` · 27 thuộc tính — Kiện hàng: mã kiện, kích thước, cân nặng, trạng thái, ai đang giữ</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shipment` | `shipment_id` | `Shipment` | quan hệ n-1, bắt buộc |
| `code` | `code` | `String` | tối đa 40 ký tự, bắt buộc |
| `sequence` | `sequence` | `Int` | bắt buộc |
| `description` | `description` | `String` | tối đa 500 ký tự, bắt buộc |
| `quantity` | `quantity` | `Int` | bắt buộc |
| `category` | `category` | `GoodsCategory` | tối đa 30 ký tự, enum, bắt buộc |
| `isFragile` | `is_fragile` | `Boolean` | bắt buộc |
| `actualWeightKg` | `actual_weight_kg` | `BigDecimal` | bắt buộc |
| `volumetricWeightKg` | `volumetric_weight_kg` | `BigDecimal` | bắt buộc |
| `chargeableWeightKg` | `chargeable_weight_kg` | `BigDecimal` | bắt buộc |
| `lengthCm` | `length_cm` | `BigDecimal` | bắt buộc |
| `widthCm` | `width_cm` | `BigDecimal` | bắt buộc |
| `heightCm` | `height_cm` | `BigDecimal` | bắt buộc |
| `status` | `status` | `ParcelStatus` | tối đa 30 ký tự, enum, bắt buộc |
| `stage` | `stage` | `ParcelStage` | tối đa 30 ký tự, enum, bắt buộc |
| `flowDirection` | `flow_direction` | `ParcelFlowDirection` | tối đa 20 ký tự, enum, bắt buộc |
| `currentLocationType` | `current_location_type` | `ParcelLocationType` | tối đa 20 ký tự, enum, bắt buộc |
| `currentHubId` | `current_hub_id` | `UUID?` | có thể rỗng |
| `currentHubName` | `current_hub_name` | `String?` | tối đa 200 ký tự, có thể rỗng |
| `currentRouteLegId` | `current_route_leg_id` | `UUID?` | có thể rỗng |
| `currentAssignmentId` | `current_assignment_id` | `UUID?` | có thể rỗng |
| `custodyType` | `custody_type` | `ParcelCustodyType` | tối đa 20 ký tự, enum, bắt buộc |
| `custodyReferenceId` | `custody_reference_id` | `UUID?` | có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>Shipment</b> — `shipments.shipments` · 76 thuộc tính — Vận đơn: người gửi/nhận, bảng giá đã chốt, tuyến, trạng thái, tiền thu hộ, vòng đời</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `trackingCode` | `tracking_code` | `String` | tối đa 32 ký tự, bắt buộc |
| `merchantId` | `merchant_id` | `UUID` | bắt buộc |
| `createdByUserId` | `created_by_user_id` | `UUID` | bắt buộc |
| `idempotencyKey` | `idempotency_key` | `String` | tối đa 100 ký tự, bắt buộc |
| `requestFingerprint` | `request_fingerprint` | `String` | tối đa 64 ký tự, bắt buộc |
| `merchantReference` | `merchant_reference` | `String?` | tối đa 100 ký tự, có thể rỗng |
| `pickupAddressId` | `pickup_address_id` | `UUID?` | có thể rỗng |
| `sender` | — | `ShipmentAddressSnapshot` | nhúng |
| `recipient` | — | `ShipmentAddressSnapshot` | nhúng |
| `shippingFeePayer` | `shipping_fee_payer` | `ShippingFeePayer` | tối đa 20 ký tự, enum, bắt buộc |
| `quoteId` | `quote_id` | `UUID` | bắt buộc |
| `quoteRequestId` | `quote_request_id` | `UUID` | bắt buộc |
| `ratePlanId` | `rate_plan_id` | `UUID` | bắt buộc |
| `rateCode` | `rate_code` | `String` | tối đa 60 ký tự, bắt buộc |
| `rateRevision` | `rate_revision` | `Int` | bắt buộc |
| `rateVersion` | `rate_version` | `String` | tối đa 100 ký tự, bắt buộc |
| `currency` | `currency` | `String` | tối đa 3 ký tự, bắt buộc |
| `serviceLevel` | `service_level` | `ShipmentServiceLevel` | tối đa 20 ký tự, enum, bắt buộc |
| `pricingZone` | `pricing_zone` | `ShipmentPricingZone` | tối đa 30 ký tự, enum, bắt buộc |
| `baseFee` | `base_fee` | `Long` | bắt buộc |
| `additionalWeightFee` | `additional_weight_fee` | `Long` | bắt buộc |
| `areaSurcharge` | `area_surcharge` | `Long` | bắt buộc |
| `codFee` | `cod_fee` | `Long` | bắt buộc |
| `insuranceFee` | `insurance_fee` | `Long` | bắt buộc |
| `shippingFee` | `shipping_fee` | `Long` | bắt buộc |
| `codAmount` | `cod_amount` | `Long` | bắt buộc |
| `declaredValue` | `declared_value` | `Long` | bắt buộc |
| `recipientCollectAmount` | `recipient_collect_amount` | `Long` | bắt buộc |
| `collectedAmount` | `collected_amount` | `Long?` | có thể rỗng |
| `collectedAt` | `collected_at` | `Instant?` | có thể rỗng |
| `collectedByUserId` | `collected_by_user_id` | `UUID?` | có thể rỗng |
| `collectionAttemptId` | `collection_attempt_id` | `UUID?` | có thể rỗng |
| `estimatedMinDays` | `estimated_min_days` | `Int` | bắt buộc |
| `estimatedMaxDays` | `estimated_max_days` | `Int` | bắt buộc |
| `quoteExpiresAt` | `quote_expires_at` | `Instant` | bắt buộc |
| `originServiceAreaId` | `origin_service_area_id` | `UUID` | bắt buộc |
| `originRegionId` | `origin_region_id` | `UUID` | bắt buộc |
| `originHubId` | `origin_hub_id` | `UUID` | bắt buộc |
| `originHubCode` | `origin_hub_code` | `String` | tối đa 60 ký tự, bắt buộc |
| `originHubName` | `origin_hub_name` | `String` | tối đa 200 ký tự, bắt buộc |
| `originCoverageTier` | `origin_coverage_tier` | `ShipmentCoverageTier` | tối đa 20 ký tự, enum, bắt buộc |
| `destinationServiceAreaId` | `destination_service_area_id` | `UUID` | bắt buộc |
| `destinationRegionId` | `destination_region_id` | `UUID` | bắt buộc |
| `destinationHubId` | `destination_hub_id` | `UUID` | bắt buộc |
| `destinationHubCode` | `destination_hub_code` | `String` | tối đa 60 ký tự, bắt buộc |
| `destinationHubName` | `destination_hub_name` | `String` | tối đa 200 ký tự, bắt buộc |
| `destinationCoverageTier` | `destination_coverage_tier` | `ShipmentCoverageTier` | tối đa 20 ký tự, enum, bắt buộc |
| `routingStatus` | `routing_status` | `ShipmentRoutingStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `routeRevision` | `route_revision` | `Int` | bắt buộc |
| `capacityReservationBatchId` | `capacity_reservation_batch_id` | `UUID?` | có thể rỗng |
| `reservedWeightKg` | `reserved_weight_kg` | `BigDecimal` | bắt buộc |
| `routeReadyAt` | `route_ready_at` | `Instant?` | có thể rỗng |
| `estimatedDeliveryAt` | `estimated_delivery_at` | `Instant?` | có thể rỗng |
| `estimatedTransitMinutes` | `estimated_transit_minutes` | `Int?` | có thể rỗng |
| `routeDistanceKm` | `route_distance_km` | `BigDecimal?` | có thể rỗng |
| `status` | `status` | `ShipmentStatus` | tối đa 30 ký tự, enum, bắt buộc |
| `lifecycleStatus` | `lifecycle_status` | `ShipmentLifecycleStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `completedAt` | `completed_at` | `Instant?` | có thể rỗng |
| `note` | `note` | `String?` | tối đa 1000 ký tự, có thể rỗng |
| `cancellationReason` | `cancellation_reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `cancelledAt` | `cancelled_at` | `Instant?` | có thể rỗng |
| `cancelledAfterPickup` | `cancelled_after_pickup` | `Boolean` | bắt buộc |
| `isOnHold` | `is_on_hold` | `Boolean` | bắt buộc |
| `holdReason` | `hold_reason` | `String?` | tối đa 1000 ký tự, có thể rỗng |
| `holdActorUserId` | `hold_actor_user_id` | `UUID?` | có thể rỗng |
| `holdHubId` | `hold_hub_id` | `UUID?` | có thể rỗng |
| `heldAt` | `held_at` | `Instant?` | có thể rỗng |
| `hasPendingWeightReview` | `has_pending_weight_review` | `Boolean` | bắt buộc |
| `networkConfirmedAt` | `network_confirmed_at` | `Instant?` | có thể rỗng |
| `networkConfirmationError` | `network_confirmation_error` | `String?` | tối đa 200 ký tự, có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `parcels` | — | `MutableList<Parcel>` | quan hệ 1-n |
| `events` | — | `MutableList<ShipmentEvent>` | quan hệ 1-n |

</details>

<details>
<summary><b>ShipmentAddressSnapshot</b> — (nhúng, không có bảng riêng) · 7 thuộc tính — Ảnh chụp địa chỉ người gửi/nhận tại thời điểm tạo đơn (nhúng trong Shipment)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `contactName` | `contact_name` | `String` | tối đa 120 ký tự, bắt buộc |
| `contactPhone` | `contact_phone` | `String` | tối đa 20 ký tự, bắt buộc |
| `addressLine` | `address_line` | `String` | tối đa 500 ký tự, bắt buộc |
| `provinceCode` | `province_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `provinceName` | `province_name` | `String` | tối đa 160 ký tự, bắt buộc |
| `wardCode` | `ward_code` | `String` | tối đa 20 ký tự, bắt buộc |
| `wardName` | `ward_name` | `String` | tối đa 160 ký tự, bắt buộc |

</details>

<details>
<summary><b>ShipmentCase</b> — `shipments.shipment_cases` · 20 thuộc tính — Hồ sơ sự cố (kiện có vấn đề, kiện không xác định)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `caseNumber` | `case_number` | `String` | tối đa 32 ký tự, duy nhất, bắt buộc |
| `type` | `case_type` | `ShipmentCaseType` | tối đa 30 ký tự, enum, bắt buộc |
| `status` | `status` | `ShipmentCaseStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `severity` | `severity` | `ShipmentCaseSeverity` | tối đa 20 ký tự, enum, bắt buộc |
| `hubId` | `hub_id` | `UUID` | bắt buộc |
| `shipmentId` | `shipment_id` | `UUID?` | có thể rỗng |
| `parcelId` | `parcel_id` | `UUID?` | có thể rỗng |
| `externalBarcode` | `external_barcode` | `String?` | tối đa 120 ký tự, có thể rỗng |
| `title` | `title` | `String` | tối đa 160 ký tự, bắt buộc |
| `description` | `description` | `String` | tối đa 2000 ký tự, bắt buộc |
| `reportedByUserId` | `reported_by_user_id` | `UUID` | bắt buộc |
| `assignedToUserId` | `assigned_to_user_id` | `UUID?` | có thể rỗng |
| `resolution` | `resolution` | `String?` | tối đa 2000 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `resolvedAt` | `resolved_at` | `Instant?` | có thể rỗng |
| `closedAt` | `closed_at` | `Instant?` | có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |
| `events` | — | `MutableList<ShipmentCaseEvent>` | quan hệ 1-n |

</details>

<details>
<summary><b>ShipmentCaseEvent</b> — `shipments.shipment_case_events` · 7 thuộc tính — Lịch sử hồ sơ sự cố</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shipmentCase` | `case_id` | `ShipmentCase` | quan hệ n-1, bắt buộc |
| `code` | `code` | `String` | tối đa 80 ký tự, bắt buộc |
| `description` | `description` | `String` | tối đa 2000 ký tự, bắt buộc |
| `actorUserId` | `actor_user_id` | `UUID` | bắt buộc |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |
| `dataJson` | `data_json` | `String?` | có thể rỗng |

</details>

<details>
<summary><b>ShipmentControlSignal</b> — `shipments.shipment_control_signals` · 11 thuộc tính — Tín hiệu giám sát (cảnh báo trễ, lệch tuyến…)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shipmentId` | `shipment_id` | `UUID` | bắt buộc |
| `parcelId` | `parcel_id` | `UUID?` | có thể rỗng |
| `type` | `signal_type` | `ShipmentControlSignalType` | tối đa 40 ký tự, enum, bắt buộc |
| `code` | `code` | `String` | tối đa 80 ký tự, bắt buộc |
| `hubId` | `hub_id` | `UUID` | bắt buộc |
| `routeLegId` | `route_leg_id` | `UUID?` | có thể rỗng |
| `actorUserId` | `actor_user_id` | `UUID?` | có thể rỗng |
| `description` | `description` | `String` | tối đa 500 ký tự, bắt buộc |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>ShipmentEvent</b> — `shipments.shipment_events` · 12 thuộc tính — Mốc hành trình (quét, giao, hoàn…)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shipment` | `shipment_id` | `Shipment` | quan hệ n-1, bắt buộc |
| `parcelId` | `parcel_id` | `UUID?` | có thể rỗng |
| `status` | `status` | `ShipmentStatus` | tối đa 30 ký tự, enum, bắt buộc |
| `code` | `code` | `String` | tối đa 100 ký tự, bắt buộc |
| `description` | `description` | `String` | tối đa 500 ký tự, bắt buộc |
| `actorUserId` | `actor_user_id` | `UUID?` | có thể rỗng |
| `hubId` | `hub_id` | `UUID?` | có thể rỗng |
| `locationName` | `location_name` | `String?` | tối đa 200 ký tự, có thể rỗng |
| `dataJson` | `data_json` | `String?` | có thể rỗng |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |
| `recordedAt` | `recorded_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>ShipmentExceptionRequest</b> — `shipments.shipment_exception_requests` · 21 thuộc tính — Yêu cầu hoàn / chuyển tiếp / huỷ cần duyệt</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shipmentId` | `shipment_id` | `UUID` | bắt buộc |
| `kind` | `kind` | `ShipmentExceptionRequestKind` | tối đa 20 ký tự, enum, bắt buộc |
| `status` | `status` | `ShipmentExceptionRequestStatus` | tối đa 30 ký tự, enum, bắt buộc |
| `reason` | `reason` | `String` | tối đa 1000 ký tự, bắt buộc |
| `hubId` | `hub_id` | `UUID?` | có thể rỗng |
| `requestedByUserId` | `requested_by_user_id` | `UUID` | bắt buộc |
| `requestedAt` | `requested_at` | `Instant` | bắt buộc |
| `reviewedByUserId` | `reviewed_by_user_id` | `UUID?` | có thể rỗng |
| `reviewedAt` | `reviewed_at` | `Instant?` | có thể rỗng |
| `reviewNote` | `review_note` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `routingRequired` | `routing_required` | `Boolean` | bắt buộc |
| `forwardContactName` | `forward_contact_name` | `String?` | tối đa 120 ký tự, có thể rỗng |
| `forwardContactPhone` | `forward_contact_phone` | `String?` | tối đa 20 ký tự, có thể rỗng |
| `forwardAddressLine` | `forward_address_line` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `forwardProvinceCode` | `forward_province_code` | `String?` | tối đa 20 ký tự, có thể rỗng |
| `forwardWardCode` | `forward_ward_code` | `String?` | tối đa 20 ký tự, có thể rỗng |
| `completedByUserId` | `completed_by_user_id` | `UUID?` | có thể rỗng |
| `completedAt` | `completed_at` | `Instant?` | có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>ShipmentWeightAdjustment</b> — `shipments.shipment_weight_adjustments` · 16 thuộc tính — Cân lại kiện và duyệt</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shipmentId` | `shipment_id` | `UUID` | bắt buộc |
| `parcelId` | `parcel_id` | `UUID` | bắt buộc |
| `oldActualWeightKg` | `old_actual_weight_kg` | `BigDecimal` | bắt buộc |
| `newActualWeightKg` | `new_actual_weight_kg` | `BigDecimal` | bắt buộc |
| `oldChargeableWeightKg` | `old_chargeable_weight_kg` | `BigDecimal` | bắt buộc |
| `newChargeableWeightKg` | `new_chargeable_weight_kg` | `BigDecimal` | bắt buộc |
| `reason` | `reason` | `String` | tối đa 1000 ký tự, bắt buộc |
| `actorUserId` | `actor_user_id` | `UUID` | bắt buộc |
| `hubId` | `hub_id` | `UUID?` | có thể rỗng |
| `adjustedAt` | `adjusted_at` | `Instant` | bắt buộc |
| `reviewCompleted` | `review_completed` | `Boolean` | bắt buộc |
| `reviewedByUserId` | `reviewed_by_user_id` | `UUID?` | có thể rỗng |
| `reviewedAt` | `reviewed_at` | `Instant?` | có thể rỗng |
| `reviewNote` | `review_note` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>ShipmentOutboxEvent</b> — `shipments.outbox_events` · 13 thuộc tính — Hàng đợi sự kiện vận đơn gửi sang Billing (RabbitMQ)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `aggregateId` | `aggregate_id` | `UUID` | bắt buộc |
| `aggregateCode` | `aggregate_code` | `String` | tối đa 40 ký tự, bắt buộc |
| `eventType` | `event_type` | `String` | tối đa 120 ký tự, bắt buộc |
| `eventVersion` | `event_version` | `Int` | bắt buộc |
| `payloadJson` | `payload_json` | `String` | bắt buộc |
| `status` | `status` | `ShipmentOutboxStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `attempts` | `attempts` | `Int` | bắt buộc |
| `nextAttemptAt` | `next_attempt_at` | `Instant` | bắt buộc |
| `occurredAt` | `occurred_at` | `Instant` | bắt buộc |
| `publishedAt` | `published_at` | `Instant?` | có thể rỗng |
| `lastError` | `last_error` | `String?` | tối đa 2000 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary>Enum trạng thái và danh mục</summary>

| Enum | Giá trị |
|---|---|
| `ShipmentControlAlertType` | `SENT_BUT_NOT_ARRIVED`, `MISSING_ARRIVAL_SCAN`, `ARRIVED_NOT_OUT_FOR_DELIVERY`, `ROUTE_DEVIATION`, `SHIPMENT_STUCK`, `CASE_OVERDUE`, `SIGNATURE_WITHOUT_DELIVERY`, `DELIVERED_WITHOUT_SIGNATURE` |
| `ShipmentControlAlertSeverity` | `INFO`, `WARNING`, `CRITICAL` |
| `ShipmentExceptionQueueKind` | `RETURN`, `FORWARD`, `CANCELLATION`, `WEIGHT_REVIEW` |
| `ShipmentExceptionQueueStatus` | `PENDING_APPROVAL`, `APPROVED`, `PROCESSING`, `PENDING_REVIEW`, `REJECTED`, `COMPLETED` |
| `WorkforceScanAction` | `PICK_UP`, `RECEIVE`, `DEPART`, `OUT_FOR_DELIVERY`, `CHECK` |
| `WorkforceReportKind` | `DELIVERED`, `DELIVERY_FAILED`, `CANCELLED`, `RETURNING`, `RETURNED`, `IN_STOCK` |
| `DeliveryAttemptOutcome` | `DELIVERED`, `FAILED` |
| `DeliveryFailureReason` | `RECIPIENT_UNAVAILABLE`, `RECIPIENT_REFUSED`, `INVALID_ADDRESS`, `UNABLE_TO_CONTACT`, `PAYMENT_NOT_COLLECTED`, `PARCEL_DAMAGED`, `UNSAFE_LOCATION`, `OTHER` |
| `DeliveryProofMethod` | `OTP`, `SIGNATURE`, `PHOTO` |
| `ShipmentCaseType` | `PROBLEM_PARCEL`, `UNIDENTIFIED_PARCEL` |
| `ShipmentCaseStatus` | `OPEN`, `ASSIGNED`, `INVESTIGATING`, `RESOLVED`, `CLOSED` |
| `ShipmentCaseSeverity` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `ShipmentControlSignalType` | `ROUTE_DEVIATION` |
| `ShipmentStatus` | `AWAITING_PICKUP`, `PICKED_UP`, `AT_ORIGIN_HUB`, `IN_TRANSIT`, `AT_DESTINATION_HUB`, `OUT_FOR_DELIVERY`, `DELIVERY_FAILED`, `PARTIALLY_DELIVERED`, `DELIVERED`, `RETURNING`, `RETURNED`, `CANCELLED` |
| `ShipmentLifecycleStatus` | `ACTIVE`, `COMPLETED`, `CANCELLED` |
| `ShippingFeePayer` | `SENDER`, `RECIPIENT` |
| `ShipmentCollectionStatus` | `NOT_REQUIRED`, `PENDING`, `COLLECTED`, `VOIDED` |
| `ShipmentRoutingStatus` | `UNPLANNED`, `RESERVED`, `IN_TRANSIT`, `CONSUMED`, `RELEASED` |
| `ShipmentServiceLevel` | `STANDARD`, `EXPRESS` |
| `ShipmentPricingZone` | `SAME_PROVINCE`, `SAME_REGION`, `CROSS_REGION` |
| `ShipmentCoverageTier` | `STANDARD`, `REMOTE`, `ISLAND` |
| `GoodsCategory` | `DOCUMENTS`, `APPAREL`, `ELECTRONICS`, `DRY_FOOD`, `COSMETICS`, `HOUSEHOLD`, `FRAGILE_GOODS`, `OTHER` |
| `ParcelStatus` | `AWAITING_PICKUP`, `PICKED_UP`, `AT_ORIGIN_HUB`, `IN_TRANSIT`, `AT_TRANSIT_HUB`, `AT_DESTINATION_HUB`, `OUT_FOR_DELIVERY`, `DELIVERY_FAILED`, `DELIVERED`, `RETURNING`, `RETURNED`, `CANCELLED` |
| `ParcelStage` | `AWAITING_PICKUP`, `PICKED_UP`, `AT_HUB`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERY_EXCEPTION`, `DELIVERED`, `RETURNING`, `RETURNED`, `CANCELLED` |
| `ParcelFlowDirection` | `FORWARD`, `RETURN` |
| `ParcelLocationType` | `SENDER`, `HUB`, `ROUTE_LEG`, `COURIER`, `RECIPIENT`, `UNKNOWN` |
| `ParcelCustodyType` | `SENDER`, `HUB`, `LINE_HAUL`, `COURIER`, `RECIPIENT`, `UNKNOWN` |
| `ShipmentOutboxStatus` | `PENDING`, `PUBLISHED`, `FAILED` |
| `ShipmentExceptionRequestKind` | `RETURN`, `FORWARD`, `CANCELLATION` |
| `ShipmentExceptionRequestStatus` | `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `PROCESSING`, `COMPLETED` |
| `NetworkConfirmationResult` | `CONFIRMED`, `ALREADY_RELEASED`, `NOT_FOUND` |

</details>

<details>
<summary>API (48 endpoint)</summary>

| Method | Đường dẫn | Quyền |
|---|---|---|
| `GET` | `/api/v1/admin/control-tower/alerts` | `shipments.control-tower.read` |
| `GET` | `/api/v1/admin/control-tower/summary` | `shipments.control-tower.read` |
| `GET` | `/api/v1/admin/shipment-cases` | `shipments.cases.read` hoặc `shipments.cases.manage` |
| `POST` | `/api/v1/admin/shipment-cases/problem-parcels` | `shipments.cases.manage` |
| `GET` | `/api/v1/admin/shipment-cases/shipment-lookup/{trackingCode}` | `shipments.cases.manage` |
| `GET` | `/api/v1/admin/shipment-cases/summary` | `shipments.cases.read` hoặc `shipments.cases.manage` |
| `POST` | `/api/v1/admin/shipment-cases/unidentified-parcels` | `shipments.cases.manage` |
| `GET` | `/api/v1/admin/shipment-cases/{caseId}` | `shipments.cases.read` hoặc `shipments.cases.manage` |
| `POST` | `/api/v1/admin/shipment-cases/{caseId}/assign` | `shipments.cases.manage` |
| `POST` | `/api/v1/admin/shipment-cases/{caseId}/close` | `shipments.cases.manage` |
| `POST` | `/api/v1/admin/shipment-cases/{caseId}/investigate` | `shipments.cases.manage` |
| `POST` | `/api/v1/admin/shipment-cases/{caseId}/link` | `shipments.cases.manage` |
| `POST` | `/api/v1/admin/shipment-cases/{caseId}/notes` | `shipments.cases.manage` |
| `POST` | `/api/v1/admin/shipment-cases/{caseId}/resolve` | `shipments.cases.manage` |
| `GET` | `/api/v1/admin/shipment-exceptions` | `shipments.exceptions.read` hoặc `shipments.exceptions.manage` |
| `GET` | `/api/v1/admin/shipment-exceptions/summary` | `shipments.exceptions.read` hoặc `shipments.exceptions.manage` |
| `GET` | `/api/v1/admin/shipments` | `shipments.admin.read` hoặc `shipments.read-hub` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/cancellation-requests` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/cancellation-requests/{requestId}/complete` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/cancellation-requests/{requestId}/review` | `shipments.exceptions.manage` |
| `GET` | `/api/v1/admin/shipments/{shipmentId}/exceptions` | `shipments.exceptions.read` hoặc `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/forward-requests` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/forward-requests/{requestId}/complete` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/forward-requests/{requestId}/review` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/hold` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/hold/release` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/return-requests` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/return-requests/{requestId}/complete` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/return-requests/{requestId}/review` | `shipments.exceptions.manage` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/weight-adjustments` | `shipments.weight.adjust` |
| `POST` | `/api/v1/admin/shipments/{shipmentId}/weight-adjustments/{adjustmentId}/review` | `shipments.exceptions.manage` |
| `GET` | `/api/v1/admin/shipments/{trackingCode}` | `shipments.admin.read` hoặc `shipments.read-hub` |
| `POST` | `/api/v1/deliveries/attempts` | `deliveries.complete-own` |
| `GET` | `/api/v1/deliveries/mine` | `deliveries.complete-own` |
| `POST` | `/api/v1/parcel-scans` | `scans.create` |
| `GET` | `/api/v1/shipments` | `shipments.read-own` |
| `POST` | `/api/v1/shipments` | `shipments.create` |
| `GET` | `/api/v1/shipments/{trackingCode}` | `shipments.read-own` |
| `POST` | `/api/v1/shipments/{trackingCode}/cancel` | `shipments.cancel-own` |
| `GET` | `/api/v1/shipments/{trackingCode}/labels` | `shipments.read-own` |
| `GET` | `/api/v1/tracking/{code}` | Công khai |
| `POST` | `/api/v1/workforce/evidence` | `deliveries.complete-own` |
| `GET` | `/api/v1/workforce/evidence/{id}` | `deliveries.complete-own` hoặc `shipments.read-hub` hoặc `shipments.admin.read` |
| `GET` | `/api/v1/workforce/lookup/{code}` | `assignments.read-own` hoặc `shipments.read-hub` hoặc `shipments.admin.read` |
| `GET` | `/api/v1/workforce/reports/items` | `assignments.read-own` hoặc `shipments.read-hub` |
| `GET` | `/api/v1/workforce/reports/summary` | `assignments.read-own` hoặc `shipments.read-hub` |
| `POST` | `/api/v1/workforce/scans` | `scans.create` |
| `GET` | `/api/v1/workforce/tasks` | `assignments.read-own` |

</details>


### 6.8 Billing — `pavex_billing` (5440, service 8088)

<details>
<summary><b>CashCollection</b> — `billing.cash_collections` · 27 thuộc tính — Tiền bưu tá thu của người nhận (COD + cước nếu người nhận trả), chuỗi giữ tiền</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shipmentId` | `shipment_id` | `UUID` | bắt buộc |
| `trackingCode` | `tracking_code` | `String` | tối đa 40 ký tự, bắt buộc |
| `merchantId` | `merchant_id` | `UUID` | bắt buộc |
| `deliveryAttemptId` | `delivery_attempt_id` | `UUID` | bắt buộc |
| `amount` | `amount` | `Long` | bắt buộc |
| `codAmount` | `cod_amount` | `Long` | bắt buộc |
| `feeAmount` | `fee_amount` | `Long` | bắt buộc |
| `currency` | `currency` | `String` | tối đa 3 ký tự, bắt buộc |
| `reference` | `reference` | `String` | tối đa 100 ký tự, bắt buộc |
| `collectedByUserId` | `collected_by_user_id` | `UUID` | bắt buộc |
| `originHubId` | `origin_hub_id` | `UUID` | bắt buộc |
| `status` | `status` | `CashCollectionStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `currentCustodianUserId` | `current_custodian_user_id` | `UUID?` | có thể rỗng |
| `currentHubId` | `current_hub_id` | `UUID?` | có thể rỗng |
| `currentRemittanceId` | `current_remittance_id` | `UUID?` | có thể rỗng |
| `collectedAt` | `collected_at` | `Instant` | bắt buộc |
| `submittedAt` | `submitted_at` | `Instant?` | có thể rỗng |
| `approvedAt` | `approved_at` | `Instant?` | có thể rỗng |
| `approvedByUserId` | `approved_by_user_id` | `UUID?` | có thể rỗng |
| `lastRejectedAt` | `last_rejected_at` | `Instant?` | có thể rỗng |
| `lastRejectedByUserId` | `last_rejected_by_user_id` | `UUID?` | có thể rỗng |
| `lastRejectionReason` | `last_rejection_reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `statementId` | `statement_id` | `UUID?` | có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>CashRemittance</b> — `billing.cash_remittances` · 17 thuộc tính — Phiếu nộp tiền của bưu tá về bưu cục</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `remittanceNumber` | `remittance_number` | `String` | tối đa 60 ký tự, bắt buộc |
| `courierUserId` | `courier_user_id` | `UUID` | bắt buộc |
| `hubId` | `hub_id` | `UUID` | bắt buộc |
| `status` | `status` | `CashRemittanceStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `expectedAmount` | `expected_amount` | `Long` | bắt buộc |
| `declaredAmount` | `declared_amount` | `Long` | bắt buộc |
| `submittedAt` | `submitted_at` | `Instant` | bắt buộc |
| `approvedAt` | `approved_at` | `Instant?` | có thể rỗng |
| `approvedByUserId` | `approved_by_user_id` | `UUID?` | có thể rỗng |
| `rejectedAt` | `rejected_at` | `Instant?` | có thể rỗng |
| `rejectedByUserId` | `rejected_by_user_id` | `UUID?` | có thể rỗng |
| `rejectionReason` | `rejection_reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |
| `items` | — | `MutableList<CashRemittanceItem>` | quan hệ 1-n |

</details>

<details>
<summary><b>CashRemittanceItem</b> — `billing.cash_remittance_items` · 6 thuộc tính — Khoản tiền trong phiếu nộp</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `remittance` | `remittance_id` | `CashRemittance` | quan hệ n-1, bắt buộc |
| `sequence` | `sequence` | `Int` | bắt buộc |
| `collectionId` | `collection_id` | `UUID` | bắt buộc |
| `amount` | `amount` | `Long` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>CustomerInvoice</b> — `billing.customer_invoices` · 19 thuộc tính — Hoá đơn cước merchant còn phải trả</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `invoiceNumber` | `invoice_number` | `String` | tối đa 50 ký tự, bắt buộc |
| `merchantId` | `merchant_id` | `UUID` | bắt buộc |
| `currency` | `currency` | `String` | tối đa 3 ký tự, bắt buộc |
| `status` | `status` | `InvoiceStatus` | tối đa 30 ký tự, enum, bắt buộc |
| `totalAmount` | `total_amount` | `Long` | bắt buộc |
| `paidAmount` | `paid_amount` | `Long` | bắt buộc |
| `issuedAt` | `issued_at` | `Instant` | bắt buộc |
| `dueAt` | `due_at` | `Instant` | bắt buộc |
| `paidAt` | `paid_at` | `Instant?` | có thể rỗng |
| `cancelledAt` | `cancelled_at` | `Instant?` | có thể rỗng |
| `cancelledByUserId` | `cancelled_by_user_id` | `UUID?` | có thể rỗng |
| `cancellationReason` | `cancellation_reason` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `note` | `note` | `String?` | tối đa 500 ký tự, có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |
| `lines` | — | `MutableList<InvoiceLine>` | quan hệ 1-n |
| `payments` | — | `MutableList<InvoicePayment>` | quan hệ 1-n |

</details>

<details>
<summary><b>InvoiceLine</b> — `billing.customer_invoice_lines` · 7 thuộc tính — Dòng hoá đơn (mỗi vận đơn một dòng)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `invoice` | `invoice_id` | `CustomerInvoice` | quan hệ n-1, bắt buộc |
| `sequence` | `sequence` | `Int` | bắt buộc |
| `shipmentId` | `shipment_id` | `UUID` | bắt buộc |
| `trackingCode` | `tracking_code` | `String` | tối đa 40 ký tự, bắt buộc |
| `amount` | `amount` | `Long` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>InvoicePayment</b> — `billing.invoice_payments` · 18 thuộc tính — Khoản thanh toán hoá đơn (tiền mặt, QR, cấn trừ COD)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `invoice` | `invoice_id` | `CustomerInvoice` | quan hệ n-1, bắt buộc |
| `method` | `method` | `InvoicePaymentMethod` | tối đa 20 ký tự, enum, bắt buộc |
| `status` | `status` | `InvoicePaymentStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `amount` | `amount` | `Long` | bắt buộc |
| `reference` | `reference` | `String` | tối đa 100 ký tự, bắt buộc |
| `provider` | `provider` | `String?` | tối đa 30 ký tự, có thể rỗng |
| `providerPaymentId` | `provider_payment_id` | `String?` | tối đa 200 ký tự, có thể rỗng |
| `providerReference` | `provider_reference` | `String?` | tối đa 200 ký tự, có thể rỗng |
| `checkoutUrl` | `checkout_url` | `String?` | tối đa 2048 ký tự, có thể rỗng |
| `qrPayload` | `qr_payload` | `String?` | tối đa 2000 ký tự, có thể rỗng |
| `qrExpiresAt` | `qr_expires_at` | `Instant?` | có thể rỗng |
| `collectedByUserId` | `collected_by_user_id` | `UUID?` | có thể rỗng |
| `completedAt` | `completed_at` | `Instant?` | có thể rỗng |
| `cancelledAt` | `cancelled_at` | `Instant?` | có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>MerchantFeeCharge</b> — `billing.merchant_fee_charges` · 10 thuộc tính — Cước merchant phải trả cho một vận đơn đã đóng</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `shipmentId` | `shipment_id` | `UUID` | bắt buộc |
| `trackingCode` | `tracking_code` | `String` | tối đa 40 ký tự, bắt buộc |
| `merchantId` | `merchant_id` | `UUID` | bắt buộc |
| `amount` | `amount` | `Long` | bắt buộc |
| `shippingFeePayer` | `shipping_fee_payer` | `String` | tối đa 20 ký tự, bắt buộc |
| `finalStatus` | `final_status` | `String` | tối đa 30 ký tự, bắt buộc |
| `closedAt` | `closed_at` | `Instant` | bắt buộc |
| `statementId` | `statement_id` | `UUID?` | có thể rỗng |
| `createdAt` | `created_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>MerchantPayoutAccount</b> — `billing.merchant_payout_accounts` · 8 thuộc tính — Tài khoản ngân hàng nhận tiền của merchant</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `merchantId` | `merchant_id` | `UUID` | khoá chính |
| `bankName` | `bank_name` | `String` | tối đa 100 ký tự, bắt buộc |
| `accountNumber` | `account_number` | `String` | tối đa 40 ký tự, bắt buộc |
| `accountName` | `account_name` | `String` | tối đa 200 ký tự, bắt buộc |
| `updatedByUserId` | `updated_by_user_id` | `UUID` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>PaymentWebhookReceipt</b> — `billing.payment_webhook_receipts` · 13 thuộc tính — Webhook payOS đã nhận (chống trùng, kiểm tra chữ ký)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `provider` | `provider` | `String` | tối đa 30 ký tự, bắt buộc |
| `externalEventId` | `external_event_id` | `String` | tối đa 200 ký tự, bắt buộc |
| `eventType` | `event_type` | `String` | tối đa 100 ký tự, bắt buộc |
| `payloadHash` | `payload_hash` | `String` | tối đa 128 ký tự, bắt buộc |
| `paymentId` | `payment_id` | `UUID?` | có thể rỗng |
| `status` | `status` | `PaymentWebhookReceiptStatus` | tối đa 30 ký tự, enum, bắt buộc |
| `receivedAt` | `received_at` | `Instant` | bắt buộc |
| `processedAt` | `processed_at` | `Instant?` | có thể rỗng |
| `failedAt` | `failed_at` | `Instant?` | có thể rỗng |
| `failureReason` | `failure_reason` | `String?` | tối đa 1000 ký tự, có thể rỗng |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary><b>ProcessedEvent</b> — `billing.processed_events` · 3 thuộc tính — Sự kiện RabbitMQ đã xử lý (chống xử lý trùng)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `eventId` | `event_id` | `UUID` | khoá chính |
| `eventType` | `event_type` | `String` | tối đa 120 ký tự, bắt buộc |
| `processedAt` | `processed_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>SettlementStatement</b> — `billing.settlement_statements` · 20 thuộc tính — Bảng đối soát định kỳ của merchant (COD cấn trừ cước, số tiền chuyển)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `statementNumber` | `statement_number` | `String` | tối đa 40 ký tự, bắt buộc |
| `merchantId` | `merchant_id` | `UUID` | bắt buộc |
| `cutoffAt` | `cutoff_at` | `Instant` | bắt buộc |
| `codTotal` | `cod_total` | `Long` | bắt buộc |
| `feeTotal` | `fee_total` | `Long` | bắt buộc |
| `offsetAmount` | `offset_amount` | `Long` | bắt buộc |
| `payoutAmount` | `payout_amount` | `Long` | bắt buộc |
| `invoiceId` | `invoice_id` | `UUID?` | có thể rỗng |
| `payoutStatus` | `payout_status` | `PayoutStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `payoutReference` | `payout_reference` | `String?` | tối đa 100 ký tự, có thể rỗng |
| `payoutBankName` | `payout_bank_name` | `String?` | tối đa 100 ký tự, có thể rỗng |
| `payoutAccountNumber` | `payout_account_number` | `String?` | tối đa 40 ký tự, có thể rỗng |
| `payoutAccountName` | `payout_account_name` | `String?` | tối đa 200 ký tự, có thể rỗng |
| `paidAt` | `paid_at` | `Instant?` | có thể rỗng |
| `paidByUserId` | `paid_by_user_id` | `UUID?` | có thể rỗng |
| `createdByUserId` | `created_by_user_id` | `UUID` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary>Enum trạng thái và danh mục</summary>

| Enum | Giá trị |
|---|---|
| `CashCollectionStatus` | `HELD`, `SUBMITTED`, `APPROVED` |
| `CashRemittanceStatus` | `SUBMITTED`, `APPROVED`, `REJECTED` |
| `InvoiceStatus` | `ISSUED`, `PARTIALLY_PAID`, `PAID`, `CANCELLED` |
| `InvoicePaymentMethod` | `CASH`, `QR`, `COD_OFFSET` |
| `InvoicePaymentStatus` | `PENDING`, `COMPLETED`, `CANCELLED`, `EXPIRED` |
| `PaymentWebhookReceiptStatus` | `RECEIVED`, `PROCESSED`, `FAILED` |
| `PayoutStatus` | `NOT_REQUIRED`, `PENDING`, `PAID` |
| `PayOsWebhookOutcome` | `PROCESSED`, `DUPLICATE`, `IGNORED`, `INVALID`, `RETRY` |
| `PaymentGatewayPaymentStatus` | `PENDING`, `PROCESSING`, `PAID`, `CANCELLED`, `UNKNOWN` |
| `Result` | `SETTLED`, `CANCELLED`, `PENDING`, `FAILED` |

</details>

<details>
<summary>API (30 endpoint)</summary>

| Method | Đường dẫn | Quyền |
|---|---|---|
| `GET` | `/api/v1/admin/billing/cash/collections` | `billing.cash.approve` |
| `POST` | `/api/v1/admin/billing/cash/collections/{collectionId}/approve` | `billing.cash.approve` |
| `POST` | `/api/v1/admin/billing/cash/collections/{collectionId}/reject` | `billing.cash.approve` |
| `GET` | `/api/v1/admin/billing/cash/remittances` | `billing.cash.approve` |
| `POST` | `/api/v1/admin/billing/cash/remittances/{remittanceId}/approve` | `billing.cash.approve` |
| `POST` | `/api/v1/admin/billing/cash/remittances/{remittanceId}/reject` | `billing.cash.approve` |
| `GET` | `/api/v1/admin/billing/invoices` | `billing.manage` |
| `GET` | `/api/v1/admin/billing/invoices/{invoiceId}` | `billing.manage` |
| `POST` | `/api/v1/admin/billing/invoices/{invoiceId}/cancel` | `billing.manage` |
| `POST` | `/api/v1/admin/billing/invoices/{invoiceId}/payments/cash` | `billing.manage` |
| `GET` | `/api/v1/admin/billing/merchants/{merchantId}/payout-account` | `billing.manage` |
| `POST` | `/api/v1/admin/billing/payments/reconcile` | `billing.manage` |
| `POST` | `/api/v1/admin/billing/payments/{paymentId}/confirm` | `billing.manage` |
| `GET` | `/api/v1/admin/billing/statements` | `billing.manage` |
| `POST` | `/api/v1/admin/billing/statements/generate` | `billing.manage` |
| `GET` | `/api/v1/admin/billing/statements/{statementId}` | `billing.manage` |
| `POST` | `/api/v1/admin/billing/statements/{statementId}/payout` | `billing.manage` |
| `GET` | `/api/v1/billing/cash/collections/mine` | `billing.cash.collect` |
| `POST` | `/api/v1/billing/cash/collections/{collectionId}/resubmit` | `billing.cash.read-hub` |
| `GET` | `/api/v1/billing/cash/hubs/{hubId}/collections` | `billing.cash.read-hub` |
| `POST` | `/api/v1/billing/cash/remittances` | `billing.cash.submit-own` |
| `GET` | `/api/v1/billing/cash/remittances/mine` | `billing.cash.submit-own` |
| `GET` | `/api/v1/billing/invoices` | `billing.invoices.read-own` |
| `GET` | `/api/v1/billing/invoices/{invoiceId}` | `billing.invoices.read-own` |
| `POST` | `/api/v1/billing/invoices/{invoiceId}/payments/qr` | `billing.invoices.pay-own` |
| `GET` | `/api/v1/billing/payout-account` | `billing.invoices.read-own` |
| `PUT` | `/api/v1/billing/payout-account` | `partners.merchants.manage-own` |
| `GET` | `/api/v1/billing/statements` | `billing.invoices.read-own` |
| `GET` | `/api/v1/billing/statements/{statementId}` | `billing.invoices.read-own` |
| `POST` | `/api/v1/integrations/payos/webhook` | Công khai (kiểm tra chữ ký payOS) |

</details>


### 6.9 Fleet — `pavex_fleet` (5441, service 8089)

Điểm GPS thô nằm trong bảng `fleet.trip_locations`, chia theo ngày (không qua JPA nên không có trong bảng entity dưới).
Mỗi dòng gồm: chuyến, số thứ tự trên máy, thời điểm ghi, thời điểm nhận, lat, lng, độ chính xác, tốc độ, hướng, pin, cờ vị trí giả.

<details>
<summary><b>DriverConsent</b> — `fleet.driver_consents` · 3 thuộc tính — Tài xế đã đồng ý chia sẻ vị trí (theo phiên bản điều khoản)</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `userId` | `user_id` | `UUID` | khoá chính |
| `termsVersion` | `terms_version` | `String` | tối đa 20 ký tự, bắt buộc |
| `acceptedAt` | `accepted_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>TripAccessLog</b> — `fleet.trip_access_log` · 4 thuộc tính — Ai đã mở lộ trình GPS của chuyến nào, lúc nào</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `tripId` | `trip_id` | `UUID` | bắt buộc |
| `viewerUserId` | `viewer_user_id` | `UUID` | bắt buộc |
| `viewedAt` | `viewed_at` | `Instant` | bắt buộc |

</details>

<details>
<summary><b>FleetTrip</b> — `fleet.trips` · 34 thuộc tính — Chuyến xe trung chuyển: tài xế, tuyến, hub đi/đến, giờ kế hoạch và thực tế, vị trí mới nhất, quãng đường</summary>

| Thuộc tính | Cột | Kiểu | Ràng buộc |
|---|---|---|---|
| `id` | `id` | `UUID` | khoá chính |
| `driverUserId` | `driver_user_id` | `UUID` | bắt buộc |
| `driverEmail` | `driver_email` | `String?` | tối đa 320 ký tự, có thể rỗng |
| `laneId` | `lane_id` | `UUID` | bắt buộc |
| `laneCode` | `lane_code` | `String` | tối đa 80 ký tự, bắt buộc |
| `fromHubId` | `from_hub_id` | `UUID` | bắt buộc |
| `fromHubCode` | `from_hub_code` | `String` | tối đa 50 ký tự, bắt buộc |
| `fromHubName` | `from_hub_name` | `String` | tối đa 200 ký tự, bắt buộc |
| `toHubId` | `to_hub_id` | `UUID` | bắt buộc |
| `toHubCode` | `to_hub_code` | `String` | tối đa 50 ký tự, bắt buộc |
| `toHubName` | `to_hub_name` | `String` | tối đa 200 ký tự, bắt buộc |
| `plannedDepartureAt` | `planned_departure_at` | `Instant` | bắt buộc |
| `plannedArrivalAt` | `planned_arrival_at` | `Instant` | bắt buộc |
| `distanceKm` | `distance_km` | `BigDecimal?` | có thể rỗng |
| `vehiclePlate` | `vehicle_plate` | `String?` | tối đa 20 ký tự, có thể rỗng |
| `status` | `status` | `FleetTripStatus` | tối đa 20 ký tự, enum, bắt buộc |
| `startSource` | `start_source` | `TripStartSource` | tối đa 20 ký tự, enum, bắt buộc |
| `startedAt` | `started_at` | `Instant` | bắt buộc |
| `endSource` | `end_source` | `TripEndSource?` | tối đa 20 ký tự, enum, có thể rỗng |
| `arrivedAt` | `arrived_at` | `Instant?` | có thể rỗng |
| `lastLat` | `last_lat` | `Double?` | có thể rỗng |
| `lastLng` | `last_lng` | `Double?` | có thể rỗng |
| `lastAccuracyM` | `last_accuracy_m` | `Double?` | có thể rỗng |
| `lastSpeedMps` | `last_speed_mps` | `Double?` | có thể rỗng |
| `lastHeadingDeg` | `last_heading_deg` | `Double?` | có thể rỗng |
| `lastBatteryPct` | `last_battery_pct` | `Int?` | có thể rỗng |
| `lastMocked` | `last_mocked` | `Boolean` | bắt buộc |
| `lastRecordedAt` | `last_recorded_at` | `Instant?` | có thể rỗng |
| `lastReceivedAt` | `last_received_at` | `Instant?` | có thể rỗng |
| `pointCount` | `point_count` | `Long` | bắt buộc |
| `travelledMeters` | `travelled_meters` | `Double` | bắt buộc |
| `createdAt` | `created_at` | `Instant` | bắt buộc |
| `updatedAt` | `updated_at` | `Instant` | bắt buộc |
| `version` | `version` | `Long` | khoá lạc quan (@Version), bắt buộc |

</details>

<details>
<summary>Enum trạng thái và danh mục</summary>

| Enum | Giá trị |
|---|---|
| `DriverTripStatus` | `PLANNED`, `IN_PROGRESS`, `ARRIVED` |
| `FleetTripStatus` | `IN_PROGRESS`, `ARRIVED` |
| `TripStartSource` | `DRIVER`, `DEPART_SCAN` |
| `TripEndSource` | `DRIVER`, `ALL_RECEIVED` |

</details>

<details>
<summary>API (10 endpoint)</summary>

| Method | Đường dẫn | Quyền |
|---|---|---|
| `GET` | `/api/v1/fleet/me/consent` | `fleet.trips.report-own` |
| `PUT` | `/api/v1/fleet/me/consent` | `fleet.trips.report-own` |
| `GET` | `/api/v1/fleet/me/trips` | `fleet.trips.report-own` |
| `POST` | `/api/v1/fleet/me/trips/start` | `fleet.trips.report-own` |
| `POST` | `/api/v1/fleet/me/trips/start-from-scan` | `fleet.trips.report-own` |
| `POST` | `/api/v1/fleet/me/trips/{tripId}/arrive` | `fleet.trips.report-own` |
| `POST` | `/api/v1/fleet/me/trips/{tripId}/locations` | `fleet.trips.report-own` |
| `GET` | `/api/v1/fleet/trips` | `fleet.trips.read-hub` |
| `GET` | `/api/v1/fleet/trips/{tripId}` | `fleet.trips.read-hub` |
| `GET` | `/api/v1/fleet/trips/{tripId}/track` | `fleet.trips.read-hub` |

</details>

### 6.10 Thư viện dùng chung (`libs/`)

| Thư viện | Nội dung chính |
|---|---|
| `libs/platform-common` | `PavexActor` / `requirePavexActor()` (đọc người gọi từ JWT), `HubAccessScope` (phạm vi bưu cục), chuẩn hoá SĐT `VietnamesePhone` |
| `libs/platform-contracts` | DTO của API nội bộ, để service gọi và service nhận dùng chung một định nghĩa |
| `libs/platform-config` | `RequiredSettingsCheck`: service dừng ngay khi khởi động nếu `application.yaml` dùng một biến không có mặc định (`${BILLING_DB_PASSWORD}`) mà không ai đặt, và liệt kê các biến còn thiếu |
| `libs/platform-test` | Chỉ dùng trong test: `@PostgresIntegrationTest` khởi động cả service trên PostgreSQL thật (Testcontainers, Flyway, Hibernate `validate`), không cần RabbitMQ, Eureka hay khoá thật (mục 7) |
| `platform/discovery-server` | Eureka (cổng 8761) |
| `platform/api-gateway` | Gateway (cổng 8080): định tuyến, CORS, giới hạn tần suất theo IP; riêng `/api/v1/fleet/**` giới hạn theo người dùng, vì điện thoại 4G hay dùng chung IP |

---

## 7. Migration database

Flyway tự áp migration còn thiếu khi service khởi động. File nằm ở `<service>/src/main/resources/db/migration/`.

| Service | Số migration | Mới nhất |
|---|---:|---|
| Auth | 18 | `V20__staff_temporary_password.sql` |
| Identity | 21 | `V22__exception_requests_by_hub_staff.sql` |
| Partners | 11 | `V11__merchant_business_details.sql` |
| Network | 6 | `V6__hub_location.sql` |
| Pricing | 5 | `V5__province_pricing_zones.sql` |
| Operations | 12 | `V12__same_hub_shipments_skip_destination_handling.sql` |
| Shipment | 18 | `V19__case_outcome.sql` |
| Billing | 2 | `V2__counter_cash.sql` |
| Fleet | 2 | `V2__trip_endpoints.sql` |
| Notifications | 1 | `V1__create_notifications.sql` |

Mỗi service có một test (`<Service>DatabaseTest`) chạy toàn bộ migration trên PostgreSQL thật (cùng image
`postgres:18.6-alpine` với `compose.yml`, khởi động bằng Testcontainers) rồi để Hibernate `validate` mọi entity với bảng
vừa tạo. Migration mới mà sai cú pháp hoặc lệch với entity thì `./mvnw test` báo lỗi ngay. Cần Docker; máy không có
Docker thì các test này được bỏ qua chứ không báo lỗi. Phần dùng chung nằm ở `libs/platform-test`.

---

## 8. Giới hạn hiện tại

| Mảng | Hiện trạng |
|---|---|
| Thông báo | Hai portal và app hiện trường đọc hộp thông báo của service Notifications. Chưa gửi thông báo đẩy (push) về điện thoại hay email khi có thông báo mới |
| Chứng từ giao hàng dạng mã | Bưu tá nhập tay; hệ thống chưa gửi OTP cho người nhận để đối chiếu |
| Cước | Không tính lại khi cân lại kiện; chưa có phí hoàn hàng |
| App hiện trường | Bưu tá chỉ làm việc trên app Flutter `pavex-warehouse`; management portal không còn màn Giao hàng |
| Kế toán | Chưa xuất dữ liệu sang phần mềm kế toán |
| IntelliJ | `.run/` chưa có cấu hình chạy Billing, Fleet và Notifications (mục 2.3.4) |
| Bản đồ xe | Dùng tile OpenStreetMap công cộng, chỉ hợp cho dev. Chạy thật nên đổi sang nhà cung cấp Việt Nam (Goong, VietMap) để hiển thị đúng chủ quyền |
| Theo dõi xe | Bưu cục đã có toạ độ (có thể để trống), nhưng Fleet chưa dùng để tự nhận biết xe vào/rời hub; chưa có danh mục xe (biển số do tài xế tự nhập); chưa dự báo giờ tới |
| GPS nền trên iPhone | Cần tài xế cho phép vị trí; iOS có thể dừng app nếu tài xế vuốt tắt hẳn app giữa chuyến |
| Kiểm thử | Phần lớn là unit test. Test trên PostgreSQL thật mới có ở mức migration và entity cho mỗi service (mục 7), cộng một luồng đối soát COD của Billing; chưa có test chạy nhiều service cùng lúc qua RabbitMQ |
| Triển khai thật | Mặc định đã an toàn để deploy và có Dockerfile cho mọi service (mục 9). Chưa chọn nơi chạy; chưa có CI, giám sát, nơi giữ secret và cấu hình chạy (compose/manifest) cho môi trường thật |

---

## 9. Triển khai thật

Mục này chỉ nói về backend: biến môi trường và image. Chọn nơi chạy, CI, giám sát và nơi giữ secret là quyết định riêng.

### 9.1 Nguyên tắc

- `application.yaml` của mọi service **an toàn để deploy nguyên trạng**: không chứa secret. Mật khẩu và khoá không có
  giá trị mặc định; thiếu một biến thì service **dừng ngay khi khởi động** với lỗi
  `Missing required settings: BILLING_DB_PASSWORD, …` (`libs/platform-config`).
- Những thứ chỉ hợp cho local thì mặc định **tắt**: admin khởi tạo, Swagger/API docs, bảng giá demo, nút đặt lại dữ liệu
  thử, xe giả lập, ca cả ngày.
- **Không dùng `.env` của máy local, không chạy `scripts/local-env.sh` ở môi trường thật.** Các giá trị trong đó
  (mật khẩu `pavex`, khoá local, `STAFF_TEMPORARY_PASSWORD=123456`) đã nằm trong git. Image không chứa `.env`
  (`.dockerignore`).
- Mật khẩu admin `admin@pavex.com` từng được ghi thẳng trong `auth/.../application.yaml`, file `.http` và test. Đã xoá
  khỏi code nhưng **vẫn còn trong lịch sử git**: không bao giờ dùng giá trị đó (hay biến thể của nó) cho một admin
  thật, và đổi ngay ở mọi tài khoản thật nào đang dùng nó.

### 9.2 Image

Một `Dockerfile` ở thư mục gốc cho mọi service (build nhiều tầng, chạy bằng JRE 21, user không phải root `pavex`):

```bash
docker build --build-arg MODULE=auth -t pavex/auth-service .
docker build --build-arg MODULE=platform/api-gateway -t pavex/api-gateway .
```

`MODULE` là thư mục module: `auth`, `identity`, `partners`, `network`, `pricing`, `shipment`, `operations`, `billing`,
`fleet`, `notifications`, `platform/discovery-server`, `platform/api-gateway`. Cổng của từng service ở mục 2.1;
container nên được kiểm tra sẵn sàng qua `GET /actuator/health/readiness`. Thứ tự khởi động như mục 2.3.2
(Discovery → Identity → Auth → … → Gateway). Flyway tự tạo bảng ở lần chạy đầu. Cờ JVM thêm đặt qua `JAVA_TOOL_OPTIONS`.

### 9.3 Secret bắt buộc

Thiếu biến nào trong bảng này thì service tương ứng không khởi động.

| Biến | Service | Ghi chú |
|---|---|---|
| `<SERVICE>_DB_PASSWORD` | 10 service có DB: `AUTH`, `IDENTITY`, `PARTNERS`, `NETWORK`, `PRICING`, `SHIPMENT`, `OPERATIONS`, `BILLING`, `FLEET`, `NOTIFICATIONS` | Đặt cùng `<SERVICE>_DB_URL` (mặc định trỏ `localhost`) và `<SERVICE>_DB_USERNAME` (mặc định `pavex`). Mỗi service một database và một user riêng |
| `RABBITMQ_PASSWORD` | auth, billing, fleet, notifications, operations, shipment | Cùng `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_VHOST` |
| `MINIO_SECRET_KEY` | identity, partners, shipment | Cùng `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`; MinIO hoặc S3 bất kỳ |
| `AUTH_KEY_HASH_SECRET` | auth | `openssl rand -base64 32`. Đổi giá trị thì mọi người dùng bị đăng xuất |
| `AUTH_OUTBOX_PAYLOAD_ENCRYPTION_KEY` | auth | `openssl rand -base64 32`. Đổi giá trị thì tin outbox chưa gửi không đọc được nữa |
| `BILLING_QR_SIGNING_KEY` | billing | Ít nhất 32 byte, ví dụ `openssl rand -base64 48` |
| `STAFF_TEMPORARY_PASSWORD` | auth | Mật khẩu tạm của tài khoản do quản trị viên tạo (bắt đổi ở lần đăng nhập đầu, hết hạn sau `STAFF_TEMPORARY_PASSWORD_TTL`). Không dùng `123456` |
| Khoá JWT | mọi service trừ Discovery và Gateway | Thư mục `PAVEX_KEYSDIR` (trong image: `/etc/pavex/keys`, mount chỉ đọc) có cùng bố cục với `~/.pavex/keys`: `<service>/private.pem` và `<service>/public.pem` cho `auth`, `identity`, `partners`, `pricing`, `shipment`, `operations`, `billing`, `fleet`, `notifications`. Tạo bằng `openssl` như `scripts/security-bootstrap.sh`, riêng cho môi trường thật; mỗi service chỉ cần private key của chính nó và public key của các service khác |
| `SPRING_DATA_REDIS_PASSWORD` | api-gateway | Chỉ khi Redis có mật khẩu (nên có). Cùng `REDIS_HOST`, `REDIS_PORT` |

**Admin khởi tạo** (auth và identity, mặc định tắt):

1. Lần deploy đầu đặt `PAVEX_BOOTSTRAPADMIN_ENABLED=true`, `PAVEX_BOOTSTRAPADMIN_EMAIL=<email thật>` và
   `PAVEX_BOOTSTRAPADMIN_PASSWORD=<mật khẩu mạnh, 8–128 ký tự>` cho **cả auth và identity** (cùng email). Bật mà thiếu
   mật khẩu thì auth dừng ngay.
2. Admin đăng nhập, đổi mật khẩu, tạo các quản trị viên khác.
3. Đặt lại `PAVEX_BOOTSTRAPADMIN_ENABLED=false` và xoá mật khẩu khỏi nơi giữ secret.

Để nguyên `PAVEX_BOOTSTRAPADMIN_RESET_PASSWORD` (mặc định `false`): khi `true`, mỗi lần khởi động auth ghi đè mật khẩu
admin bằng giá trị trong biến (chỉ dùng ở local).

### 9.4 Địa chỉ, domain và email

| Biến | Service | Ghi chú |
|---|---|---|
| `EUREKA_DEFAULT_ZONE` | mọi service và gateway | Ví dụ `http://discovery-server:8761/eureka/`. Mặc định `localhost` chỉ đúng khi mọi thứ chạy trên một máy |
| `EUREKA_INSTANCE_IP_ADDRESS`, `EUREKA_INSTANCE_HOSTNAME` | mọi service và gateway | Không bắt buộc: mặc định là địa chỉ máy/container tự phát hiện. Chỉ đặt khi máy có nhiều card mạng và địa chỉ phát hiện sai. Không đặt `127.0.0.1` (chỉ dành cho local) |
| `PAVEX_PUBLIC_BASE_URL` | mọi service có DB | Địa chỉ công khai của gateway, ví dụ `https://api.pavex.vn`; dùng cho link ảnh đại diện, tệp và OpenAPI |
| `GATEWAY_ALLOWED_ORIGIN` | api-gateway | Origin được gọi API qua CORS, ví dụ `https://pavex.vn` (mặc định `http://localhost:3000`) |
| `GATEWAY_TRUSTED_PROXIES` | api-gateway | Địa chỉ/CIDR của các server Next.js, xem mục 2.3.7 |
| `EMAIL_VERIFICATION_URL`, `PASSWORD_RESET_URL`, `MANAGEMENT_PASSWORD_RESET_URL`, `INVITATION_URL`, `MANAGEMENT_INVITATION_URL` | auth | Link trong email, trỏ về domain thật của hai portal (mặc định `localhost:3000`/`3001`) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`, `MAIL_FROM` | auth, partners | SMTP thật; mặc định gửi vào Mailpit `localhost:1025` |
| `GOOGLE_CLIENT_ID` | auth | Nếu bật đăng nhập Google (mục 2.8) |
| `JWT_ISSUER`, `<SERVICE>_SERVICE_JWT_ISSUER` | nhiều service | Không bắt buộc. Nếu đổi thì đổi đồng loạt ở mọi service, vì service nhận token so khớp đúng chuỗi này |

### 9.5 Thanh toán payOS

| Biến | Giá trị |
|---|---|
| `BILLING_PAYMENT_PROVIDER` | `payos` (mặc định `fake`: QR giả, tài chính tự bấm xác nhận) |
| `PAYOS_ENABLED` | `true` |
| `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` | Khoá kênh thanh toán trong trang quản lý payOS (secret) |
| `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL` | Trang hoá đơn của client portal trên domain thật |
| `BILLING_RECONCILIATION_ENABLED` | **`true`**: Billing định kỳ hỏi lại payOS, phòng khi webhook bị lỡ (mặc định `false`) |

Đăng ký webhook trong trang quản lý payOS: `https://<domain gateway>/api/v1/integrations/payos/webhook`.

### 9.6 Phải giữ tắt

Các cờ dưới đây mặc định đã tắt; **không đặt** chúng ở môi trường thật.

| Biến | Vì sao |
|---|---|
| `PAVEX_DEMO_RESET_ENABLED` | **Phải luôn `false`**. Khi bật, quản trị viên xoá được mọi vận đơn, tiền và thông báo bằng một nút |
| `PRICING_DEMO_SEED_ENABLED` | Tự tạo bảng giá demo. Môi trường thật nhập bảng giá thật |
| `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED` | Mở mô tả mọi API và Swagger UI ra ngoài. Nếu cần, chỉ bật ở môi trường thử nội bộ |
| `FLEET_SIMULATOR_ENABLED` | Xe giả lập tự chạy trên bản đồ |
| `OPERATIONS_SHIFT_FULL_DAY`, `OPERATIONS_ACCEPT_TIMEOUT` | Chế độ test ca làm và nhận việc (`.env.example`, mục 6) |
| `PAVEX_BOOTSTRAPADMIN_RESET_PASSWORD` | Xem mục 9.3 |
