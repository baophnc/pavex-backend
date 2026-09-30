# Pavex — Kịch bản kiểm thử luồng trên 3 app

> Cập nhật ngày 30/09/2026. Tài liệu này hướng dẫn kiểm thử **bằng giao diện** của cả 3 app, theo đúng thứ tự
> nghiệp vụ thật. Kiến trúc, cách chạy và dữ liệu seed xem ở [PAVEX-TAI-LIEU-TONG-HOP.md](PAVEX-TAI-LIEU-TONG-HOP.md).

| App | Ai dùng | Mở ở đâu |
|---|---|---|
| **Client portal** | Khách hàng, shop | Trình duyệt, http://localhost:3000 (mặc định) |
| **Management portal** | Nhân viên văn phòng, trưởng bưu cục, kho, kế toán, CSKH | Trình duyệt, http://localhost:3001 (mặc định) |
| **App hiện trường** (`pavex-warehouse`) | Bưu tá, nhân viên bưu cục, trưởng bưu cục, tài xế | iPhone, iOS Simulator hoặc Android |

Nếu bạn chạy hai portal ở cổng khác (ví dụ management ở 3000, client ở 3001), thay cổng tương ứng trong tài liệu.

---

## 1. Chuẩn bị

### 1.1 Khởi động và làm sạch dữ liệu

1. Chạy backend:
   ```bash
   ./scripts/dev-up.sh
   ```
   Mở http://localhost:8080/actuator/health, phải thấy `UP`.
2. **Thanh toán giả lập.** Nếu `.env` đang bật payOS thật, chạy backend với cổng thanh toán giả, để không phát sinh
   giao dịch thật:
   ```bash
   BILLING_PAYMENT_PROVIDER=fake ./scripts/dev-up.sh
   ```
3. **Email:** chọn một trong hai cách.
   - **Mailpit:** thêm `#` trước các dòng `MAIL_*` trong `.env`, khởi động lại backend. Mọi thư hiện ở http://localhost:8025.
   - **Gmail thật:** đăng ký khách bằng hộp thư bạn đọc được. Có thể dùng địa chỉ phụ kiểu `tenban+shop1@gmail.com`,
     Gmail vẫn gửi về hộp thư chính.
4. **Muốn test lại từ đầu:**
   ```bash
   ./scripts/seed-nationwide/apply.sh --staff-only
   ```
   Lệnh này xoá shop, vận đơn, tiền, chuyến xe và seed lại nhân sự, có backup trước.
5. **Chạy hai portal:** `pnpm dev` trong từng repo. Chạy app bằng `flutter run` trong `pavex-warehouse`.
   - Ở màn đăng nhập của app, bấm **Máy chủ** để trỏ tới Gateway:
     - Simulator iOS: `http://localhost:8080`.
     - Android emulator: `http://10.0.2.2:8080`.
     - Điện thoại thật: `http://<IP máy Mac>:8080`, cùng Wi‑Fi với máy Mac. Nếu tường lửa macOS hỏi, cho phép Java
       nhận kết nối.

### 1.2 Tài khoản dùng trong kịch bản

Mọi tài khoản seed dùng chung **mật khẩu dev** (in ở cuối `apply.sh`, nằm trong `.local/seed/nationwide/staff.json`).
Danh sách đầy đủ nằm ở `.local/seed/nationwide/staff.csv`: lọc theo cột "Mã hub" để tìm người của một bưu cục.

| Vai trò trong kịch bản | Email | Nơi làm | Ngày nghỉ |
|---|---|---|---|
| Kinh doanh & CSKH | `chau.vuminh.cskh@pavex.com` | Văn phòng | — |
| Kế toán | `chi.nguyenthi.kt@pavex.com` | Văn phòng | — |
| Vận hành miền Bắc | `huong.tranminh.qlvh@pavex.com` | Văn phòng | — |
| Quản trị | `hung.phananh.qtht@pavex.com` | Văn phòng | — |
| Bưu tá lấy hàng | `quynh.nguyenkim.bt@pavex.com` (dự phòng `khang.phamthanh.bt@pavex.com`) | BC-HN-BADINH (Ba Đình) | Thứ Tư (dự phòng: Thứ Ba) |
| Nhân viên bưu cục gửi | `thanh.hoangbao.nvbc@pavex.com` | BC-HN-BADINH | Thứ Năm |
| Trưởng bưu cục gửi | `nga.nguyenngoc.tbc@pavex.com` | BC-HN-BADINH | Chủ nhật |
| Tài xế tuyến | `dung.nguyendinh.tx@pavex.com` (dự phòng `phong.nguyenxuan.tx2@pavex.com`) | KHO-HN, tuyến có Ba Đình và Ngọc Hà | Chủ nhật (dự phòng: Thứ Ba) |
| Nhân viên khai thác kho | `quang.nguyenthanh.nvkt4@pavex.com` | KHO-HN | Thứ Tư |
| Trưởng kho | `vy.hoangthuy.tk@pavex.com` | KHO-HN | Thứ Sáu |
| Nhân viên bưu cục phát | `chau.buikim.nvbc@pavex.com` (dự phòng `phong.nguyentrung.nvbc@pavex.com`) | BC-HN-NGOCHA (Ngọc Hà) | Thứ Tư (dự phòng: Thứ Năm) |
| Trưởng bưu cục phát | `yen.nguyenngoc.tbc@pavex.com` | BC-HN-NGOCHA | Thứ Hai |
| Bưu tá giao hàng | `dung.phamxuan.bt@pavex.com` (dự phòng `tam.leanh.bt@pavex.com`) | BC-HN-NGOCHA | Thứ Sáu (dự phòng: Thứ Hai) |

**Bốn quy tắc cần nhớ khi test:**

1. **Chỉ người đã "Vào ca" và bật "Sẵn sàng nhận việc" mới được giao việc.** Không ai tự vào ca, nên việc sẽ chỉ rơi vào
   đúng các tài khoản bạn đang dùng. Chưa có ai vào ca thì việc chờ; ai đó vào ca là hệ thống giao trong vài giây.
   - Việc được giao mà 15 phút chưa bấm **Nhận việc**, hoặc người đó ra ca, thì việc chuyển cho người khác đang trong ca.
   - Nhân viên kho và trưởng bưu cục **không cần Nhận việc**: quét **Nhập kho** là tự nhận việc khai thác của đơn.
     Bưu cục không có nhân viên kho thì trưởng bưu cục nhận việc khai thác.
2. **Mỗi người nghỉ 1 ngày trong tuần.** Nếu hôm nay là ngày nghỉ của người trong bảng, dùng người dự phòng hoặc tìm
   người khác cùng mã hub trong `staff.csv`.
3. **Chỉ vào ca được trong giờ ca.**
   - Bưu cục lớn có ca 07:00–15:00 và 13:00–21:00, bưu cục nhỏ có ca 07:30–17:30.
   - Kho và trung tâm có ca 06–14, 14–22, 22–06. Tài xế có ca 06–14 và 14–22.
   - Tab **Hôm nay** trong app cho biết ca của người đó. Test ngoài giờ ca thì trưởng bưu cục có thể thêm ca ở
     **Vận hành → Ca làm việc**.
4. **Xem ai đang giữ việc** ở Management → **Vận hành → Phân công**: tìm theo mã vận đơn.

Đổi vai trong app: tab **Hôm nay** → biểu tượng **Tài khoản** (góc trên phải) → **Đăng xuất**, rồi đăng nhập tài khoản khác. Dùng hai máy (iPhone và Simulator) thì
đỡ phải đổi qua lại. Bưu tá cũng có thể dùng màn **Giao hàng** trên Management thay cho app.

---

## 2. Kịch bản chính: một vận đơn đi trọn vòng

Đơn đi từ **Phường Ba Đình** tới **Phường Ngọc Hà** (Hà Nội), có thu hộ (COD), người nhận trả cước. Đường đi:
BC-HN-BADINH → xe tuyến → KHO-HN → xe tuyến → BC-HN-NGOCHA. Mỗi bước ghi rõ **app nào, ai làm, bấm gì, thấy gì**.

### Bước 1 — Khách đăng ký và mở shop *(Client portal)*

- [ ] **Đăng ký:** `/register`, nhập email bạn đọc được và mật khẩu 8+ ký tự. Kiểm tra thư, bấm link xác thực.
  → Trang báo đã xác thực.
- [ ] **Hoàn thiện hồ sơ:** đăng nhập, điền họ tên, SĐT, giới tính, ngày sinh ở `/complete-profile`.
- [ ] **Gửi hồ sơ shop:** `/merchant/onboarding`, điền tên shop, MST, người liên hệ, gửi. → Trạng thái "Chờ duyệt".
  Chưa có menu Vận đơn.

### Bước 2 — Duyệt shop *(Management, `chau.vuminh.cskh`)*

- [ ] **Mở hồ sơ:** **Khách hàng → Shop**, lọc hồ sơ chờ duyệt, mở shop vừa gửi.
  → Tab **Sẵn sàng tạo đơn** báo còn thiếu điểm lấy hàng mặc định.
- [ ] **Duyệt:** tab **Xét duyệt**, bấm **Phê duyệt Merchant**. → Trạng thái Đang hoạt động, lịch sử có dòng duyệt.

### Bước 3 — Điểm lấy hàng *(Client portal, tài khoản shop)*

- [ ] **Nhận quyền shop:** đăng xuất rồi đăng nhập lại. → Có menu **Vận đơn**, **Hoá đơn**, **Đối soát**.
- [ ] **Thử SĐT sai:** **Sổ địa chỉ** → thêm điểm lấy hàng, nhập số máy bàn (`024…`) hoặc thiếu số.
  → Báo lỗi "số di động Việt Nam".
- [ ] **Thêm điểm lấy hàng:** Hà Nội → **Phường Ba Đình**, SĐT `09xxxxxxxx`, đặt làm mặc định.

### Bước 4 — Tạo vận đơn và nhãn *(Client portal)*

- [ ] **Nhập đơn:** **Vận đơn → Tạo vận đơn**. Chọn điểm lấy hàng Ba Đình. Người nhận ở Hà Nội → **Phường Ngọc Hà**,
  SĐT di động. 1 kiện 2 kg, 20×20×20 cm. COD 300.000 ₫.
- [ ] **Tính cước:** bấm tính cước. → Có phương án Tiêu chuẩn và Nhanh, dòng "Làm tròn lên 1.000 ₫", vùng giá "Nội tỉnh".
- [ ] **Chọn người trả cước:** chọn **Người nhận trả**.
  → Ô "Người nhận trả khi nhận hàng" = COD + cước; "Shop chịu cước" = 0 ₫. Đổi sang **Người gửi trả** thì hai số đổi
  theo. Để lại **Người nhận trả**.
- [ ] **Tạo đơn:** tạo xong ghi lại **mã vận đơn** (`PAVEX…`). → Trạng thái **Chờ lấy hàng**.
- [ ] **Mở nhãn:** mở chi tiết đơn, bấm **In nhãn kiện**.
  → Mỗi kiện có **mã vạch Code 128** và **QR** của mã kiện (`PAVEX…-01`), số tiền thu, dòng "Thu một lần cho cả N kiện"
  nếu có nhiều kiện. **Để trang nhãn mở trên máy tính**, dùng để quét ở các bước sau.
- [ ] **Tra cứu công khai:** `/tracking`, nhập mã vận đơn, không cần đăng nhập. → Thấy hành trình.

### Bước 5 — Bưu tá lấy hàng *(App, `quynh.nguyenkim.bt`)*

- [ ] **Vào ca:** đăng nhập app, tab **Hôm nay** → **Vào ca**, bật **Sẵn sàng nhận việc**.
- [ ] **Nhận việc:** tab **Việc** → có việc **Lấy hàng** của mã vận đơn. Bấm **Nhận việc**, rồi **Bắt đầu**.
- [ ] **Quét lấy hàng:** tab **Quét** → **Lấy hàng**, đưa camera vào **mã vạch hoặc QR** trên trang nhãn.
  → "Đã chuyển sang: Đã lấy hàng". Client portal thấy trạng thái đổi.
  - Chưa bấm Bắt đầu mà đã quét thì app báo và có nút **Bắt đầu việc này và quét lại**.

### Bước 6 — Nhập kho bưu cục gửi *(App, `thanh.hoangbao.nvbc`)*

- [ ] **Vào ca** ở tab Hôm nay.
- [ ] **Nhập kho:** tab **Quét** → **Nhập kho**, quét mã, không cần bấm Nhận việc trước. → "Đã đến bưu cục gửi".
  Management **Vận hành → Phân công**: việc khai thác bưu cục gửi thuộc người vừa quét, trạng thái Hoàn thành; việc
  **Chuyến xe** chặng 1 đã có tài xế nếu tài xế đang trong ca.
- [ ] **Kiểm kho:** thử **Kiểm kho** với cùng mã. → Xác nhận kiện đang trên kệ.
- [ ] **Thử quét sai chỗ:** nhân viên bưu cục khác (không phải Ba Đình) quét Nhập kho mã này.
  → App báo kiện không thuộc bưu cục này.

### Bước 7 — Tài xế chở lên kho *(App, `dung.nguyendinh.tx`)*

- [ ] **Vào ca:** tài xế thuộc KHO-HN, nên vào ca của KHO-HN, rồi bật Sẵn sàng.
  → Tab **Việc** có việc **Chuyến xe** xuất phát từ Ba Đình, dù tài xế không đứng ở Ba Đình.
- [ ] **Đồng ý chia sẻ vị trí:** tab **Chuyến**, bấm **Tôi đồng ý** (chỉ lần đầu).
  → Chuyến BC-HN-BADINH → KHO-HN hiện "Chờ chạy".
- [ ] **Bắt đầu việc:** tab **Việc** → **Nhận việc** → **Bắt đầu**.
- [ ] **Lên xe:** tab **Quét** → **Lên xe**, quét mã. → "Đang trung chuyển" và thông báo **"Đã bắt đầu chuyến … Đang gửi
  vị trí xe"**. Tab Chuyến có thẻ **Đang gửi vị trí xe**.
- [ ] **Giả lập xe chạy:** trên iOS Simulator chọn Features → Location → **Freeway Drive**. Trên điện thoại thật thì đi
  một đoạn. Sau khoảng 30 giây, dòng "Gửi lần cuối" có giờ.

### Bước 8 — Xem xe trên bản đồ *(Management)*

- [ ] **Trưởng bưu cục gửi thấy xe:** đăng nhập `nga.nguyenngoc.tbc`, mở **Vận hành → Xe trung chuyển**.
  → Có xe BC-HN-BADINH → KHO-HN. Bấm vào xe để xem lộ trình, tốc độ, pin, "vị trí cập nhật … trước".
- [ ] **Trưởng kho và vận hành miền thấy xe:** `vy.hoangthuy.tk` (hub đến) và `huong.tranminh.qlvh` (toàn quốc) cũng thấy.
- [ ] **Người ngoài tuyến không thấy:** trưởng một bưu cục khác, ví dụ trưởng BC-HN-NGOCHA lúc này, không thấy chuyến.
  Khách và shop không có trang này.
- [ ] **Nhãn trễ:** chuyến quá giờ tới dự kiến có nhãn **Trễ … phút**.
- [ ] **Mất tín hiệu:** tắt giả lập vị trí quá 5 phút → nhãn **Mất tín hiệu**.

### Bước 9 — Nhập kho tỉnh *(App, `quang.nguyenthanh.nvkt4`)*

- [ ] **Nhập kho:** vào ca KHO-HN, **Quét → Nhập kho**. → Kiện "Đã đến bưu cục trung chuyển". Việc khai thác KHO-HN
  tự hoàn tất theo người quét, và chặng 2 KHO-HN → BC-HN-NGOCHA được giao cho tài xế đang trong ca.
- [ ] **Chuyến tự kết thúc:** trong khoảng 1 phút, chuyến của tài xế chuyển **Đã tới** (lý do "Hub đến đã nhập đủ kiện")
  và app tài xế ngừng gửi vị trí. Nếu chờ không muốn, tài xế có thể bấm **Đã tới nơi**.

### Bước 10 — Tài xế chở về bưu cục phát *(App, `dung.nguyendinh.tx`)*

- [ ] **Chạy chặng 2:** vẫn trong ca, tab **Việc** có chặng KHO-HN → BC-HN-NGOCHA. Nhận việc, Bắt đầu,
  **Quét → Lên xe**. → Chuyến mới tự bắt đầu, lại gửi vị trí.
- Nếu việc rơi vào tài xế khác của KHO-HN đang vào ca: xem ở **Vận hành → Phân công** rồi đăng nhập đúng người đó.

### Bước 11 — Nhập kho bưu cục phát *(App, `chau.buikim.nvbc`)*

- [ ] **Nhập kho:** vào ca BC-HN-NGOCHA, **Quét → Nhập kho**. → "Đã đến bưu cục phát". Chuyến chặng 2 tự kết thúc.
  Việc khai thác bưu cục phát tự hoàn tất, và việc **Giao hàng** được giao cho bưu tá Ngọc Hà đang trong ca.

### Bước 12 — Giao hàng và thu tiền *(App, `dung.phamxuan.bt`)*

- [ ] **Nhận việc giao:** vào ca, bật Sẵn sàng. Tab **Việc** có việc **Giao hàng**, bấm **Nhận việc** → **Bắt đầu**.
- [ ] **Xuất giao:** **Quét → Xuất giao**, quét mã. → "Đang giao".
- [ ] **Giao thành công:** trên thẻ việc bấm **Giao hàng** → **Thành công**. Nhập tên người nhận, chọn **Chụp ảnh** (hoặc
  **Chữ ký**). Ô số tiền hiện đúng COD + cước, kèm dòng "Thu đúng số tiền này, không thu thiếu hay thừa". Gửi.
  → Kiện "Đã giao", vận đơn hoàn tất.
- [ ] **Tiền đang giữ:** tab **Tiền** có khoản vừa thu ("… đơn chưa nộp về bưu cục").
- [ ] **Shop thấy kết quả:** client portal thấy đơn "Đã giao", thu tiền "Đã thu".

### Bước 13 — Nộp tiền và duyệt *(App bưu tá → Management kế toán)*

- [ ] **Bưu tá nộp tiền:** tab **Tiền**, chọn khoản thu, bấm **Nộp … về bưu cục** → xác nhận **Nộp tiền**.
  → "Đã tạo lô nộp tiền …", khoản tiền chuyển sang đã nộp.
- [ ] **Trưởng bưu cục phát xem tiền:** `yen.nguyenngoc.tbc` mở tab **Tiền** trong app, mục **Tiền tại bưu cục**.
- [ ] **Kế toán duyệt:** `chi.nguyenthi.kt` mở Management **Tài chính → Duyệt tiền nộp**, mở lô, bấm **Duyệt** (hoặc
  **Đã nhận đủ tiền**). → Lô đã duyệt.
- [ ] **Nhánh từ chối:** thử **Từ chối** kèm lý do. → Tiền nằm lại bưu cục. Trưởng bưu cục **Gửi lại** trong tab Tiền,
  kế toán duyệt lại.

### Bước 14 — Đối soát và hoá đơn *(Management kế toán → Client shop)*

- [ ] **Tài khoản nhận tiền:** shop vào **Đối soát → Tài khoản nhận tiền**, nhập ngân hàng, số tài khoản, chủ tài khoản.
- [ ] **Lập đối soát:** kế toán mở **Tài chính → Đối soát shop**, chọn shop, "Chốt số liệu đến" = bây giờ, bấm
  **Lập đối soát**.
  → Hiện COD đã duyệt, cước shop phải trả (0 ₫ vì người nhận đã trả), cấn trừ, số tiền chuyển cho shop.
- [ ] **Ghi nhận chuyển khoản:** bấm **Ghi nhận đã chuyển khoản**, nhập mã giao dịch. → Bảng đối soát "Đã chuyển".
- [ ] **Shop xem đối soát:** client **Đối soát** thấy bảng, số tiền và tài khoản đã chuyển.
- [ ] **Tạo hoá đơn cước:** lặp lại bước 4–13 với một đơn **Người gửi trả**, rồi lập đối soát. → Có **hoá đơn cước**.
- [ ] **Thanh toán hoá đơn:** shop mở **Hoá đơn**, bấm **Thanh toán bằng mã QR**.
  - Cổng giả: kế toán bấm **Xác nhận (giả lập)** ở **Tài chính → Hóa đơn**.
  - Hoặc kế toán bấm **Ghi nhận tiền mặt**. → Hoá đơn "Đã thanh toán".

---

## 3. Kiểm thử theo nhóm chức năng

Mỗi nhóm chạy độc lập được, trừ khi ghi cần một vận đơn ở bước nào của mục 2.

### 3.1 Tài khoản và mật khẩu

| # | Kiểm tra | App | Kết quả đúng |
|---|---|---|---|
| A1 | Đăng nhập Google | Client `/login` | Vào được, tài khoản được tạo nếu chưa có |
| A2 | Quên mật khẩu (khách) | Client `/forgot-password` | Thư có link về client portal; đặt lại xong đăng nhập bằng mật khẩu mới |
| A3 | Quên mật khẩu (nhân viên) | Management `/forgot-password` | Link về management portal |
| A4 | Quên mật khẩu trong app | App → **Quên mật khẩu?** | Nhập email → thư; dán liên kết trong thư vào app, đặt mật khẩu mới, đăng nhập lại được |
| A5 | Admin tạo nhân viên | Management **Tài khoản → Tạo tài khoản** (`hung.phananh.qtht`) | Tạo xong có thông báo mật khẩu tạm |
| A6 | Đăng nhập bằng mật khẩu tạm `123456` | Management | Bị đưa tới **Đặt mật khẩu của bạn**; mở trang khác vẫn bị đưa về; không cho giữ `123456`; lưu xong phải đăng nhập lại |
| A7 | Như A6 nhưng trong app | App | Hiện màn **Đặt mật khẩu của bạn**; lưu xong app tự đăng nhập lại |
| A8 | Thêm nhân viên bưu cục | Management **Vận hành → Nhân sự bưu cục** (trưởng bưu cục) | Nhân viên mới đăng nhập bằng mật khẩu tạm; từ lần chạy xếp ca kế tiếp (tối đa 1 giờ) có ca |
| A9 | Đổi mật khẩu, đăng xuất thiết bị khác | Management **Tài khoản của tôi**, client **Bảo mật** | Đổi xong phải đăng nhập lại; phiên ở máy khác bị đăng xuất |
| A10 | Đăng nhập sai 5 lần | Bất kỳ | Tài khoản khoá tạm, app báo "tạm khoá … thử lại sau" |
| A11 | Tài khoản bị khoá, đã nghỉ việc, mới mời | App hoặc Management (lấy email trong `staff.csv`, cột Trạng thái) | Không đăng nhập được, có thông báo phù hợp |
| A12 | Khoá tài khoản đang đăng nhập | Management **Tài khoản** → đổi trạng thái | Phiên của người đó bị thu hồi: khi phiên cần làm mới (vài phút) thì bị đăng xuất và không đăng nhập lại được |

### 3.2 Phân quyền: mỗi người chỉ thấy phần của mình

- [ ] Bưu tá đăng nhập Management chỉ thấy **Việc của tôi → Giao hàng**.
- [ ] Trưởng bưu cục chỉ thấy vận đơn, nhân sự, ca làm, tiền của bưu cục mình; bộ lọc bưu cục chỉ có bưu cục mình.
- [ ] Kinh doanh & CSKH không thấy mục Tài chính. Kế toán không sửa được bảng giá.
- [ ] Tài khoản khách hoặc shop đăng nhập Management bị từ chối ("chưa được cấp quyền").
- [ ] Shop A không mở được vận đơn hoặc hoá đơn của shop B (thử dán link).

### 3.3 Bảng giá và báo giá

- [ ] Client `/pricing` (không đăng nhập): tính cước, thấy dòng làm tròn 1.000 ₫, vùng giá Nội tỉnh / Nội miền / Liên miền.
- [ ] Management **Bảng giá & cước → Bảng giá**: **Tạo phiên bản mới**, sửa giá, **Bắt đầu áp dụng**.
  → Có xem trước thời điểm bảng cũ ngừng áp dụng. Báo giá mới dùng bảng mới; đơn đã tạo giữ nguyên cước.
- [ ] **Tính thử cước** và **Chi tiết cách tính cước** (dán mã báo giá) hiện đúng từng phần phí. **Nhật ký giá** có dòng thay bảng.

### 3.4 Các nhánh của vận đơn

| # | Kịch bản | Làm thế nào | Kết quả đúng |
|---|---|---|---|
| S1 | Shop huỷ trước khi lấy hàng | Client chi tiết đơn → **Hủy vận đơn** | Đơn Đã huỷ; shop không bị tính cước; việc lấy hàng biến mất khỏi app bưu tá |
| S2 | Không có COD, người gửi trả | Tạo đơn COD 0 | Nhãn "Thu người nhận 0 ₫"; khi giao, app không hỏi thu tiền |
| S3 | Giao thất bại rồi giao lại | Bước 12 chọn **Thất bại** + lý do | Kiện "Giao chưa thành công"; bưu tá xuất giao lại lần sau |
| S4 | Hoàn hàng | Management chi tiết vận đơn → khu ngoại lệ → tạo yêu cầu **Hoàn hàng**; người có quyền **Duyệt** ở **Duyệt ngoại lệ** | Đơn Đang hoàn; làm tiếp các việc hệ thống tạo ra tới khi kiện về bưu cục gửi và được **Nhập kho** → Đã hoàn; shop chịu cước gốc |
| S5 | Huỷ sau khi đã lấy hàng | Yêu cầu huỷ ở khu ngoại lệ, được duyệt | Đơn huỷ nhưng shop vẫn chịu cước |
| S6 | Cân lại kiện | Chi tiết vận đơn → **Ghi nhận cân lại kiện hàng** → duyệt | Khối lượng mới được ghi; cước **không đổi** |
| S7 | Giữ đơn | **Hold / Release shipment** | Đơn tạm dừng, mở lại tiếp tục bình thường |
| S8 | Hồ sơ sự cố | **Vận hành → Hồ sơ sự cố**: tạo, nhận xử lý, ghi chú, đóng | Lịch sử đầy đủ; trưởng bưu cục chỉ thấy sự cố của bưu cục mình |
| S9 | Giám sát | **Giám sát vận đơn** khi có đơn trễ hoặc lệch tuyến | Có cảnh báo tương ứng |
| S10 | Quét trùng | Quét lại một mã đã quét | App báo "đã làm rồi", không lỗi |
| S11 | Quét mã vận đơn thay vì mã kiện | Quét mã `PAVEX…` không có `-01` | Áp dụng cho mọi kiện của đơn |

### 3.5 Mã vạch và tra cứu

- [ ] Trang nhãn (client): quét **mã vạch** bằng app (Quét → Tra cứu). → Mở đúng đơn. Làm lại với **QR**.
- [ ] Management chi tiết vận đơn → **Hiện mã vạch** ở từng kiện, quét được từ màn hình (dùng khi nhãn rách).
- [ ] App **Quét → Nhập mã** (gõ tay) khi camera không đọc được.

### 3.6 Xe trung chuyển

- [ ] Tài xế chưa đồng ý chia sẻ vị trí mà quét Lên xe. → App nhắc đồng ý ở tab Chuyến; đồng ý xong bấm **Bắt đầu chuyến**.
- [ ] **Bắt đầu chuyến** bằng tay (nhập biển số `29C-123.45`). → Biển số hiện trên bản đồ.
- [ ] Đang có một chuyến chạy mà bắt đầu chuyến khác. → Bị từ chối.
- [ ] Tắt mạng điện thoại 2–3 phút rồi bật lại. → "điểm chờ gửi" tăng rồi về 0, lộ trình trên bản đồ không bị đứt.
- [ ] Đóng app giữa chuyến rồi mở lại. → Tab Chuyến tự tiếp tục gửi vị trí.
- [ ] Sau khi xe tới: chuyến sang mục **Đã tới (24 giờ)** trên Management, có thời điểm tới và lý do kết thúc.

### 3.7 Ca làm tự động

- [ ] App **Hôm nay** hiện ca hôm nay. Người nghỉ hôm nay thấy "Hôm nay bạn chưa được xếp ca".
- [ ] Management **Vận hành → Ca làm việc**: ca 7 ngày tới đã có sẵn, không cần tạo.
- [ ] **Vào ca** rồi không **Ra ca**. → Khoảng 1 giờ sau khi ca kết thúc, hệ thống tự ra ca; hôm sau vào ca mới bình thường.
- [ ] Tắt **Sẵn sàng nhận việc**. → Không nhận thêm việc mới; việc đã được giao mà chưa nhận chuyển cho người khác.
- [ ] **Việc chưa nhận khi ra ca:** bưu tá A có việc Lấy hàng chưa bấm Nhận việc, bấm **Ra ca**. → Trong vài giây việc
  chuyển cho bưu tá B đang trong ca (hoặc về hàng chờ nếu không còn ai). Lịch sử việc có dòng "Đưa lại hàng đợi".
- [ ] **Quá 15 phút chưa nhận:** để một việc được giao tự động 15 phút không bấm Nhận việc. → Việc chuyển cho người
  khác đang trong ca. Việc trưởng bưu cục phân công tay thì giữ nguyên người được chọn.
- [ ] **Bưu cục không có nhân viên kho** (ví dụ BC-HN-DONGANH): trưởng bưu cục vào ca. → Nhận việc khai thác của đơn
  gửi từ đó; quét Nhập kho là xong. Ở Phân công, trưởng bưu cục cũng có trong danh sách chọn người cho việc khai thác.

### 3.8 Hỗ trợ khách hàng

- [ ] Client **Liên hệ**, **Hỗ trợ**, **Khiếu nại** (đính kèm 1–5 ảnh/PDF ≤ 5 MB), **Tuyển dụng**: gửi được cả khi chưa
  đăng nhập.
  → Màn hình hiện mã `YC…`, email xác nhận không chép lại nội dung.
- [ ] Đính kèm tệp đổi đuôi (ví dụ `.exe` đổi thành `.pdf`). → Bị từ chối.
- [ ] Gửi quá 5 yêu cầu trong 1 giờ bằng cùng email. → Bị chặn.
- [ ] Management **Khách hàng → Yêu cầu hỗ trợ** (`chau.vuminh.cskh`): **Nhận xử lý** → ghi chú → giải quyết (bắt buộc
  ghi chú) → **Đóng (spam/trùng)** hoặc **Mở lại**.
  - Người khác không nhận được yêu cầu đã có người nhận. **Trả lại hàng chờ** thì yêu cầu về Mới.
- [ ] **Bảng điều hành** hiện số yêu cầu mới.

### 3.9 Báo cáo và bảng điều hành

- [ ] App tab **Báo cáo**: số việc, kiện đã quét, tiền trong khoảng ngày. Trưởng bưu cục xem được cả bưu cục.
- [ ] Management **Bảng điều hành**: các thẻ số liệu đúng với dữ liệu vừa test, chỉ hiện thẻ người đó có quyền xem.

---

## 4. Khi gặp lỗi

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Việc không được giao cho ai | Chưa ai của hub đó **Vào ca** và bật **Sẵn sàng**, hoặc người đó nghỉ hôm nay | Vào ca đúng người (xem cột Ngày nghỉ); việc được giao trong vài giây |
| Không vào ca được | Ngoài giờ ca | Chọn người đang trong ca, hoặc trưởng bưu cục thêm ca ở **Vận hành → Ca làm việc** |
| Quét báo "Bấm Bắt đầu việc này trước khi quét" | Việc chưa ở trạng thái Đang làm | Bấm **Bắt đầu việc này và quét lại** |
| App không kết nối được | Sai địa chỉ Máy chủ, hoặc tường lửa macOS chặn Java | Sửa ở màn đăng nhập; cho phép Java nhận kết nối |
| Menu không đúng quyền sau khi seed lại | Token cũ | Đăng xuất, đăng nhập lại |
| Bản đồ không thấy xe | Tài xế chưa đồng ý chia sẻ vị trí hoặc chưa cho app dùng vị trí | Tab Chuyến → đồng ý; Cài đặt máy → cho phép vị trí |
| Không nhận được email | Mail đang gửi qua Gmail thật tới địa chỉ `@pavex.com` | Dùng Mailpit hoặc email bạn đọc được (mục 1.1) |

Xem log backend ở `.local/logs/<service>.log`. Log quan trọng nhất khi luồng bị kẹt là `operations-service.log` (giao
việc, xếp ca) và `shipment-service.log` (quét, giao hàng).
