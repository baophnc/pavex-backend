# Pavex — Khởi động và kiểm thử toàn bộ luồng

> Cập nhật 19/10/2026, theo cách chạy bằng một lệnh `./pavex`. Kiến trúc, quy tắc nghiệp vụ, entity và API xem ở
> [PAVEX-TAI-LIEU-TONG-HOP.md](PAVEX-TAI-LIEU-TONG-HOP.md).

Tài liệu gồm 4 phần:

1. Khởi động cả hệ thống trên máy.
2. Kiểm thử tự động trước khi push.
3. Kịch bản có sẵn chạy bằng script.
4. Kiểm thử từng luồng nghiệp vụ bằng giao diện của 3 app.

---

## 1. Khởi động

### 1.1 Cần có

- **Docker Desktop**, cấp cho Docker **ít nhất 8 GB RAM**. Cả hệ thống chiếm khoảng 6 GB.
- Repo `pavex-backend`. Hai portal và app hiện trường được tự lấy từ GitHub (nhánh `develop`), không cần clone riêng.
- (Tuỳ chọn) Điện thoại Android **cùng Wi‑Fi** với máy tính, để cài app hiện trường thật.

### 1.2 Các lệnh

Chạy trong thư mục `pavex-backend`:

| Lệnh | Tác dụng |
|---|---|
| `./pavex up` | Build những gì thay đổi rồi bật tất cả. Lần đầu mất khoảng 10–20 phút (tải image, build 12 service Java, 2 portal, app). Script tự tạo mật khẩu, khoá JWT và nạp dữ liệu seed-mini |
| `./pavex down` | Tắt tất cả, **giữ** dữ liệu |
| `./pavex reset` | Xoá **toàn bộ** dữ liệu (database, file, hàng đợi) rồi bật lại với seed-mini |
| `./pavex reset --seed=full` | Như trên nhưng nạp dữ liệu toàn quốc |
| `./pavex seed full` | Thay dữ liệu của stack đang chạy bằng dữ liệu toàn quốc (có sao lưu trước) |
| `./pavex ps` | Trạng thái từng container |
| `./pavex logs [service…]` | Xem log, ví dụ `./pavex logs shipment-service client-portal` |

Khi chạy xong, `./pavex up` in ra danh sách địa chỉ và tài khoản thử.

### 1.3 Địa chỉ

| Thành phần | Địa chỉ | Ai dùng |
|---|---|---|
| Client portal | http://localhost:3000 | Khách hàng, cửa hàng |
| Management portal | http://localhost:3001 | Nhân viên Pavex: quản trị, vận hành, bưu cục, kế toán |
| App hiện trường (bản web) | http://localhost:3002 | Bưu tá, nhân viên bưu cục, trưởng bưu cục, tài xế |
| Trang tải APK | `http://<IP máy tính>:3002/download/` (địa chỉ đúng được in ra cuối `./pavex up`) | Điện thoại Android |
| API Gateway | http://localhost:8080 (Swagger: `/swagger-ui.html`) | Gọi API trực tiếp |
| Mailpit (hộp thư thử) | http://localhost:8025 | Đọc mọi email hệ thống gửi |
| RabbitMQ, MinIO | http://localhost:15672, http://localhost:9001 | Mật khẩu ở `deploy/secrets/values/` |
| PostgreSQL | `localhost:15432`, user `postgres` | Mật khẩu ở `deploy/secrets/values/POSTGRES_PASSWORD` |

### 1.4 Chọn bộ dữ liệu

| Bộ | Có gì | Dùng khi |
|---|---|---|
| **seed-mini** (mặc định) | 2 bưu cục (Ba Đình – Hà Nội, Tân Sơn Hoà – TP.HCM), 1 tuyến trục, 12 tài khoản thử, 2 cửa hàng (1 đã duyệt, 1 chờ duyệt) | Thử nhanh các luồng ở mục 3 và 4 |
| **seed-full** (`./pavex seed full`) | 3 trung tâm miền, 34 bưu cục tổng, 3.320 bưu cục phường, 6.714 tuyến, ~10.700 nhân viên, **không có cửa hàng** | Thử mạng lưới thật và hành trình nhiều chặng (mục 4.10) |

- Mọi tài khoản `@pavex.test` dùng chung một mật khẩu, ghi trong `scripts/seed-mini/README.md` và in ra cuối `./pavex up`.
- Mật khẩu của `admin@pavex.com` nằm ở `deploy/secrets/values/PAVEX_BOOTSTRAPADMIN_PASSWORD`.

**Tài khoản seed-mini:**

| Email | Vai trò | Dùng ở |
|---|---|---|
| `admin@pavex.test` | Quản trị hệ thống | Management |
| `vanhanh@pavex.test` | Quản lý vận hành: xem mọi bưu cục, duyệt cửa hàng, tiền, đối soát | Management |
| `truongbc.hn@pavex.test`, `nhanvien.hn@pavex.test`, `buuta.hn@pavex.test` | Trưởng bưu cục, nhân viên, bưu tá Ba Đình | App (trưởng bưu cục và nhân viên dùng được cả Management) |
| `truongbc.hcm@pavex.test`, `nhanvien.hcm@pavex.test`, `buuta.hcm@pavex.test` | Như trên, ở Tân Sơn Hoà | App, Management |
| `taixe@pavex.test` | Tài xế tuyến trục Ba Đình ↔ Tân Sơn Hoà | App |
| `shop.daduyet@pavex.test` | Cửa hàng **đã duyệt** "Hoa Sen", lấy hàng ở Ba Đình | Client portal |
| `shop.choduyet@pavex.test` | Cửa hàng **chờ duyệt** "Mai Vàng" | Client portal |
| `khach@pavex.test` | Khách hàng thường | Client portal |

Khi chạy bằng `./pavex`, mọi nhân viên có ca **00:00–24:00 mỗi ngày, không có ngày nghỉ**, nên thử lúc nào cũng được.
Muốn thử lịch thật (làm 6 ngày nghỉ 1, ca ban ngày), thêm `OPERATIONS_SHIFT_FULL_DAY=false` vào
`deploy/.env.local` rồi chạy `./pavex up`.

### 1.5 Khởi động app mobile (app hiện trường)

App hiện trường (repo `pavex-warehouse`, Flutter) dùng cho bưu tá, nhân viên bưu cục, trưởng bưu cục và tài xế. Backend
phải đang chạy (`./pavex up`). Có 4 cách, từ nhanh nhất tới giống thật nhất:

| Cách | Cần gì | Khi nào dùng |
|---|---|---|
| **A. Bản web** | Chỉ trình duyệt | Thử nhanh mọi màn hình trên máy tính |
| **B. Cài APK lên điện thoại Android** | Điện thoại Android cùng Wi‑Fi | Thử camera quét mã, chụp ảnh giao hàng, GPS thật |
| **C. Chạy trên máy ảo** (iOS Simulator, Android Emulator) | Flutter, Xcode hoặc Android Studio | Sửa code app và xem ngay |
| **D. Chạy từ mã nguồn lên điện thoại thật** | Flutter, cáp USB (Android) hoặc Xcode + tài khoản Apple (iPhone) | Sửa code app và thử trên máy thật, iPhone |

**A. Bản web**

1. Mở http://localhost:3002, đăng nhập bằng tài khoản app, ví dụ `buuta.hn@pavex.test`.
2. Bản web không chạy GPS nền. Nếu máy không có camera: ở màn **Quét**, bấm biểu tượng bàn phím **Nhập mã** để gõ mã kiện.

**B. Cài APK lên điện thoại Android**

1. Điện thoại và máy tính cùng một mạng Wi‑Fi.
2. Trên điện thoại, mở trang tải APK. Địa chỉ được in ở cuối `./pavex up`, dạng `http://<IP máy tính>:3002/download/`;
   trang có mã QR để quét.
3. Tải file `pavex-field.apk`, cho phép "cài ứng dụng từ nguồn không xác định", rồi cài.
4. Mở app **Pavex Hiện trường** và đăng nhập. App đã được gắn sẵn địa chỉ gateway trên máy tính, không cần cài đặt gì
   thêm.
5. Nếu app báo không kết nối được:
   - kiểm tra hai máy cùng Wi‑Fi;
   - cho phép Docker nhận kết nối nếu tường lửa macOS hỏi;
   - nếu máy tính đổi IP, đặt `PAVEX_LAN_IP=<IP>` trong `deploy/.env.local` rồi chạy lại `./pavex up` để build lại APK.

**C. Chạy trên máy ảo** (cài Flutter 3.44; iOS cần Xcode, Android cần Android Studio)

```bash
cd ~/Documents/GitHub/pavex-warehouse
flutter pub get
open -a Simulator               # iOS; hoặc mở một máy ảo trong Android Studio
flutter run                      # chọn máy ảo nếu có nhiều thiết bị
```

Ở màn đăng nhập, bấm **Máy chủ** và nhập địa chỉ gateway:

- iOS Simulator: `http://localhost:8080`
- Android Emulator: `http://10.0.2.2:8080`

Nút **Máy chủ** chỉ có ở bản debug; bản phát hành dùng địa chỉ gắn lúc build.

**D. Chạy từ mã nguồn lên điện thoại thật**

- **Android qua cáp USB** (dễ nhất): bật *Tuỳ chọn nhà phát triển → Gỡ lỗi USB*, cắm cáp, rồi:

  ```bash
  ~/Library/Android/sdk/platform-tools/adb reverse tcp:8080 tcp:8080
  flutter run --dart-define=PAVEX_API_BASE_URL=http://localhost:8080
  ```

  Rút cáp ra cắm lại thì chạy lại lệnh `adb reverse`.
- **iPhone:**
  1. Mở `ios/Runner.xcworkspace` trong Xcode, chọn *Signing & Capabilities* với tài khoản Apple của bạn.
  2. Cắm iPhone, chạy `flutter run`.
  3. Trong app, bấm **Máy chủ** và nhập `http://<IP máy tính>:8080` (cùng Wi‑Fi).

  Docker không build được bản iOS.

**Đăng nhập app:** dùng tài khoản nhân viên, ví dụ:

- seed-mini: `buuta.hn`, `nhanvien.hn`, `truongbc.hn`, `taixe`, `buuta.hcm`, `nhanvien.hcm` (đuôi `@pavex.test`);
- seed-full: `buuta1.hn-badinh`, `truongbc.hn-badinh`, `taixe.kho-hn-t01`.

Tài khoản văn phòng và cửa hàng không dùng app này. Mỗi lần làm việc: tab **Hôm nay** → **Vào ca** → bật **Sẵn sàng nhận việc**
để nhận việc. Tài xế lần đầu vào tab **Chuyến** phải đồng ý chia sẻ vị trí.

### 1.6 Email và thanh toán khi chạy trên máy

- **Email** (xác thực tài khoản, quên mật khẩu, lời mời nhân viên): mọi thư được giữ lại trong Mailpit, không gửi ra ngoài.
- **Thanh toán:** dùng cổng **giả lập**. Mã QR là giả; kế toán bấm xác nhận để coi như đã nhận tiền.
- Thanh toán payOS thật chỉ thử được trên staging có tên miền (tài liệu tổng hợp, mục 9.7).

### 1.7 Sự cố thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `Ports already in use` | Tắt cách chạy cũ (`./scripts/dev-down.sh`) và mọi `pnpm dev` của portal, hoặc đổi cổng trong `deploy/.env.local` |
| Chờ quá 15 phút chưa "ready" | `./pavex ps` để xem container nào chưa khoẻ, rồi `./pavex logs <tên>` |
| Container tự tắt, máy chậm | Tăng RAM cho Docker (≥ 8 GB) |
| Đăng nhập báo sai dù đúng mật khẩu | Sai 5 lần sẽ bị khoá 15 phút; chờ hoặc `./pavex reset` |
| Thay đổi trong portal/app không thấy | Push lên `develop` rồi `./pavex up`; hoặc trỏ tới thư mục máy mình trong `deploy/.env.local` (xem README) |

---

## 2. Kiểm thử tự động (trước khi push)

CI trên GitHub chạy đúng các lệnh này mỗi lần push hoặc mở PR.

| Repo | Lệnh | Kiểm tra gì |
|---|---|---|
| `pavex-backend` | `./mvnw test` (cần Docker đang chạy) | Toàn bộ test, gồm test chạy trên PostgreSQL thật |
| | `node --test scripts/tests/single-config.test.mjs` | Quy tắc cấu hình: không secret mặc định, tính năng chỉ dành cho local phải tắt sẵn |
| `pavex-client-portal`, `pavex-management-portal` | `pnpm check && pnpm test && pnpm build` | Lint, kiểu TypeScript, test, build production |
| `pavex-warehouse` | `flutter analyze && flutter test` | Phân tích mã và test của app |

---

## 3. Kịch bản có sẵn (seed-mini, không cần điện thoại)

Chạy trong `pavex-backend` khi `./pavex up` đang chạy với seed-mini:

| Lệnh | Làm gì | Xem kết quả ở đâu |
|---|---|---|
| `./scripts/sim/line-haul.sh` | Nhận 1 đơn tại quầy Tân Sơn Hoà, nhân viên kiểm kho, tài xế nhận việc và quét lên xe | http://localhost:3001/fleet: xe đang chạy |
| `./scripts/sim/shop-invoice.sh` | Shop Hoa Sen tạo đơn Hà Nội → TP.HCM, lấy hàng, trung chuyển, giao, lập đối soát | Client portal → Hoá đơn của `shop.daduyet`: 1 hoá đơn "Đã phát hành" |

---

## 4. Kiểm thử từng luồng bằng giao diện

Mặc định dùng seed-mini và làm theo thứ tự các luồng; luồng sau dùng kết quả của luồng trước. Mỗi bước ghi **Kết quả
mong đợi**; nếu khác, ghi lại mã vận đơn, tài khoản và ảnh màn hình.

### 4.1 Khách hàng đăng ký và trở thành cửa hàng

1. Client portal → **Đăng ký** bằng một email mới (ví dụ `toi.thu@pavex.test`).
   - **Kết quả mong đợi:** thư xác thực xuất hiện trong Mailpit.
2. Mở thư trong Mailpit, bấm link xác thực, rồi đăng nhập và điền **Hoàn thiện hồ sơ**.
3. Vào **Đăng ký cửa hàng** (`/merchant/onboarding`), điền tên shop và người liên hệ rồi gửi.
   - **Kết quả mong đợi:** trạng thái "Chờ duyệt".
4. Management, đăng nhập `vanhanh@pavex.test` → **Khách hàng → Cửa hàng** → mở shop vừa gửi → **Duyệt**.
   - **Kết quả mong đợi:** lịch sử ghi lại người duyệt.
5. Quay lại client portal, đăng xuất rồi đăng nhập lại.
   - **Kết quả mong đợi:** có menu Vận đơn.
6. **Sổ địa chỉ** → thêm **điểm lấy hàng** và đặt làm mặc định.
   - **Kết quả mong đợi:** số điện thoại không hợp lệ bị báo lỗi ngay trên form.

### 4.2 Tính cước và tạo đơn

1. Không cần đăng nhập: mở `/pricing`, tính thử Hà Nội → TP.HCM, 2 kg.
   - **Kết quả mong đợi:** có cước của dịch vụ Tiêu chuẩn và Nhanh, làm tròn lên bội số 1.000 ₫.
2. `shop.daduyet` → **Tạo vận đơn**: người nhận ở TP.HCM, COD 300.000 ₫, người nhận trả cước.
   - **Kết quả mong đợi:** vận đơn "Chờ lấy hàng", có mã vận đơn và mã kiện; số tiền thu người nhận = COD + cước.
3. Mở **Nhãn kiện**.
   - **Kết quả mong đợi:** mỗi kiện một nhãn có QR, ghi số tiền thu.
4. **Sửa đơn** (tên, SĐT, địa chỉ) khi chưa lấy hàng.
   - **Kết quả mong đợi:** lưu được. Huỷ được khi đơn chưa lấy hàng.
5. `/tracking`: nhập mã vận đơn.
   - **Kết quả mong đợi:** thấy hành trình, chỉ tới cấp phường, không lộ tên hay SĐT.

### 4.3 Tạo đơn hàng loạt từ Excel

1. `shop.daduyet` → **Vận đơn → Tạo từ Excel** → **Tải file mẫu**.
2. Điền 4–5 đơn vào sheet "Don hang", chọn tỉnh, phường, loại hàng… trong danh sách thả xuống. Cố ý làm sai 1 dòng (ví dụ
   SĐT `028…`) và cho 2 dòng trùng **Mã đơn của shop**.
3. Tải file lên.
   - **Kết quả mong đợi:** bảng xem trước báo "N hợp lệ, M cần sửa"; dòng sai có lý do bằng tiếng Việt.
4. Bấm **Tạo … vận đơn hợp lệ**.
   - **Kết quả mong đợi:** có thanh tiến độ. Mỗi dòng tạo được hiện mã vận đơn và cước. Dòng trùng mã đơn bị từ chối, kèm
     tên vận đơn đã dùng mã đó.
5. Tải **lại cùng file** và tạo lần nữa.
   - **Kết quả mong đợi:** các dòng đã tạo hiện "đã tạo trước đó" với đúng mã cũ; số đơn trong danh sách không tăng.

### 4.4 Lấy hàng, trung chuyển, giao hàng (3 app)

Dùng đơn ở bước 4.2 (Hà Nội → TP.HCM). Ở app, mỗi tài khoản vào tab **Hôm nay**, vào ca và bật **Sẵn sàng**.

| Bước | Ai, ở đâu | Làm gì | Kết quả mong đợi |
|---|---|---|---|
| 1 | `buuta.hn`, app | Tab **Việc** → nhận việc **Lấy hàng** → **Bắt đầu** → **Quét → Lấy hàng**, quét (hoặc nhập) mã kiện | Kiện "Đã lấy hàng" |
| 2 | `nhanvien.hn`, app | **Quét → Nhập kho** mã kiện | Kiện "Đã đến bưu cục gửi"; việc khai thác tự hoàn tất |
| 3 | `taixe`, app | Tab **Chuyến** → đồng ý chia sẻ vị trí (lần đầu) → tab **Việc** nhận việc trung chuyển → **Quét → Lên xe** | Kiện "Đang trung chuyển"; chuyến xe tự bắt đầu; app gửi GPS (bản Android) |
| 4 | `vanhanh`, Management | **Xe trung chuyển** (`/fleet`) | Thấy xe trên bản đồ, lộ trình và giờ tới dự kiến |
| 5 | `nhanvien.hcm`, app | **Quét → Nhập kho** mã kiện | Kiện "Đến bưu cục phát"; chuyến tự kết thúc khi đủ kiện; mở việc giao cho bưu tá |
| 6 | `buuta.hcm`, app | Tab **Việc** → nhận việc giao → **Bắt đầu** → **Quét → Xuất giao** | Kiện "Đang giao" |
| 7 | `buuta.hcm`, app | **Giao hàng** → chụp ảnh hoặc ký, nhập tên người nhận, xác nhận **đúng số tiền thu** | Vận đơn "Đã giao". Thu sai số tiền hoặc thu lần hai bị từ chối |
| 8 | Shop, client portal | Mở chi tiết vận đơn; `/tracking` | Hành trình đủ các bước; trạng thái thu tiền "Đã thu" |

**Đơn cùng bưu cục** (người nhận cũng ở Hà Nội): làm bước 1, 2, rồi 6, 7, không cần xe.

### 4.5 Giao không thành công, hoàn hàng, sự cố

1. Tạo một đơn mới, đưa tới bước "Đang giao" như 4.4.
2. Bưu tá chọn **Giao không thành công** và chọn lý do.
   - **Kết quả mong đợi:** vận đơn "Giao chưa thành công", bưu tá có thể giao lại.
3. Management (`truongbc.hcm` hoặc `vanhanh`), mở vận đơn → **Yêu cầu hoàn hàng**.
4. Người có quyền duyệt ở **Duyệt ngoại lệ** (`/shipments/exceptions`).
   - **Kết quả mong đợi:** đơn chuyển "Đang hoàn"; hàng đi ngược về bưu cục gửi; nhập kho ở đó thì thành "Đã hoàn".
5. Huỷ sau khi đã lấy hàng: shop không tự huỷ được; phải đi qua yêu cầu ngoại lệ như trên.
6. **Hồ sơ sự cố** (`/shipments/cases`): mở hồ sơ cho một kiện, giao người xử lý, đóng hồ sơ.
   - **Kết quả mong đợi:** có lịch sử xử lý.
7. **Giám sát vận đơn** (`/control-tower`).
   - **Kết quả mong đợi:** đơn trễ hạn hoặc kẹt hiện trong danh sách cảnh báo.

### 4.6 Tiền: nộp tiền, duyệt, đối soát, hoá đơn

| Bước | Ai, ở đâu | Làm gì | Kết quả mong đợi |
|---|---|---|---|
| 1 | `buuta.hcm`, app tab **Tiền** | Chọn các khoản đã thu ở 4.4 → **Nộp … về bưu cục** | Khoản tiền chuyển sang "Đã nộp, chờ duyệt"; tổng khai phải bằng đúng tổng các khoản |
| 2 | `truongbc.hcm`, app hoặc Management | Xem tiền của bưu cục | Thấy phiếu nộp của bưu tá |
| 3 | `vanhanh`, Management **Tài chính → Duyệt tiền nộp** | **Duyệt** phiếu (thử cả **Từ chối** với một phiếu khác) | Duyệt: tiền vào "Đã duyệt". Từ chối: tiền quay về bưu cục, nộp lại được |
| 4 | `vanhanh`, **Đối soát cửa hàng** | **Lập bảng đối soát** cho Hoa Sen | Bảng cấn trừ COD đã duyệt với cước; COD còn lại chờ chuyển cho shop; cước còn lại thành hoá đơn |
| 5 | `shop.daduyet`, client portal **Hoá đơn** | Mở hoá đơn → **Tạo mã QR thanh toán** | Có mã QR (giả lập khi chạy trên máy) |
| 6 | `vanhanh`, Management **Hoá đơn** | Xác nhận thanh toán QR | Hoá đơn "Đã thanh toán"; shop thấy ngay |
| 7 | Shop | **Tài khoản nhận tiền** (`/account/settlements`): khai tài khoản; xem bảng đối soát | Kế toán ghi được mã giao dịch chuyển COD |

### 4.7 Gửi hàng tại quầy

1. `truongbc.hn` → Management **Gửi hàng tại quầy** (`/counter/new`): nhập người gửi (khách vãng lai), người nhận, kiện,
   tính cước, thu tiền mặt → **In nhãn**.
   - **Kết quả mong đợi:** đơn bắt đầu ở bưu cục gửi, không có bước lấy hàng.
2. Cho đơn đi hết luồng 4.4 rồi hoàn về (4.5).
3. **Trả thu hộ tại quầy** (`/counter/settlements`).
   - **Kết quả mong đợi:** trả COD hoặc thu phí hoàn cho khách vãng lai.

### 4.8 Ca làm và phân việc

1. Management `vanhanh` → **Nhân sự & ca → Ca làm việc**.
   - **Kết quả mong đợi:** ca được tự xếp cho 7 ngày tới.
2. **Điều phối vận hành** và **Phân công**: xem việc của từng đơn; phân tay một việc cho người khác.
3. **Hàng đợi sự kiện** (`/operations/dispatch`): xem sự kiện gửi lỗi.
4. Ở app, tắt **Sẵn sàng** khi đang có việc chưa nhận.
   - **Kết quả mong đợi:** việc quay về hàng chờ và được giao cho người khác.

### 4.9 Tài khoản, phân quyền, hỗ trợ, thông báo

1. `admin` → **Hệ thống → Tài khoản → Tạo tài khoản** cho một nhân viên mới.
   - **Kết quả mong đợi:** nhân viên phải đổi mật khẩu tạm ở lần đăng nhập đầu, cả trên web lẫn app.
2. **Quên mật khẩu** (client portal và app).
   - **Kết quả mong đợi:** thư trong Mailpit; link đặt lại dùng được 1 giờ.
3. Khoá một tài khoản.
   - **Kết quả mong đợi:** người đó bị đăng xuất ngay (phiên cũ hết hiệu lực).
4. Phạm vi: `truongbc.hn` chỉ thấy đơn và tiền liên quan tới Ba Đình; `buuta.hn` không mở được trang quản trị.
5. Client portal **Hỗ trợ / Khiếu nại** (`/support`, `/claims`, đính kèm ảnh) → Management **Yêu cầu hỗ trợ**: nhận, xử lý,
   đóng.
   - **Kết quả mong đợi:** khách nhận thư xác nhận kèm mã `YC…`.
6. **Thông báo:** sau mỗi bước ở 4.4–4.6, chuông thông báo trên portal và app có tin mới.

### 4.10 Trên dữ liệu toàn quốc

`./pavex seed full` (hoặc `./pavex reset --seed=full`), rồi:

1. Tạo một cửa hàng theo 4.1 với điểm lấy hàng ở Hà Nội.
2. Tạo đơn tới một phường ở TP.HCM, Đà Nẵng và Lào Cai.
   - **Kết quả mong đợi:** đơn liên miền có 5 chặng xe (phường → bưu cục tổng → trung tâm miền → trung tâm miền →
     bưu cục tổng → phường); đơn nội tỉnh có 2 chặng; xã vùng cao có phụ phí vùng xa.
3. Đăng nhập theo mẫu email:
   - quản lý: `truongbc.hn-badinh@pavex.test`, `quanly.kho-hn@…`, `quanly.tt-mienbac@…`;
   - bưu tá: `buuta1.hn-badinh@…`;
   - tài xế: `taixe.kho-hn-t01@…` (tuyến có Ba Đình), `taixe.kho-hn-truc1@…`, `taixe.mienbac-miennam-1@…`.

   Danh sách đầy đủ ở `.local/seed/full/staff.csv`.
4. Management: **Tuyến vận chuyển**, **Tìm tuyến**, **Hành trình mẫu** chọn được bất kỳ bưu cục nào trong 3.357 bưu cục;
   **Xe trung chuyển** lọc theo bưu cục.

---

## 5. Làm lại từ đầu

- `./pavex reset` đưa mọi thứ về seed-mini. Các kịch bản ở mục 3 và 4 lại chạy được từ đầu.
- Nút **Hệ thống → Giả lập → Đặt lại dữ liệu thử** ở Management **tắt** trong stack `./pavex`. Nó chỉ bật khi chạy bằng
  `./scripts/dev-up.sh` với `PAVEX_DEMO_RESET_ENABLED=true` (tài liệu tổng hợp, mục 2.6).
