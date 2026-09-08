# Đặt mật khẩu lần đầu và bộ mẫu giao hàng tiếng Việt

## 1. Nhân viên tự đặt mật khẩu

1. Admin tạo người dùng tại **Tài khoản** (vai trò nền tảng) hoặc **Nhân sự vận hành** (quản lý bưu cục, điều phối, kho, giao nhận, tài xế). Cần nhập email nhân viên thực sự sở hữu.
2. Nhân viên mở Management Portal → **Quên mật khẩu / đặt mật khẩu lần đầu**.
3. Nhập email được admin cấp. Hệ thống luôn trả thông báo chung, không tiết lộ email có tồn tại hay không.
4. Mở thư → đặt mật khẩu mới → đăng nhập. Liên kết chứng minh quyền sở hữu email nên đồng thời xác minh email cho tài khoản được admin tạo.

Liên kết dùng một lần, mặc định hết hạn sau 1 giờ; yêu cầu thư mới làm liên kết trước mất hiệu lực. Sau khi đặt mật khẩu, liên kết mời cũ và refresh session cũ bị thu hồi. Access JWT đã phát hành vẫn tuân theo thời hạn/kiểm tra thu hồi hiện có. Không đổi vai trò, không tự mở khóa tài khoản Identity bị đình chỉ. Không đăng nhập tự động sau khi đổi mật khẩu.

Backend Auth dùng hai URL cố định trong `auth/src/main/resources/application.yaml`:

- Client: `pavex.email.password-reset-url`, mặc định `http://localhost:3000/reset-password`.
- Management: `pavex.email.management-password-reset-url`, mặc định `http://localhost:3001/reset-password`.

Nếu chạy frontend ở cổng khác, sửa URL tương ứng trong YAML hoặc biến `PASSWORD_RESET_URL` / `MANAGEMENT_PASSWORD_RESET_URL`, rồi restart Auth. Request Management gửi `portal=MANAGEMENT`; client cũ không truyền trường này vẫn dùng `CLIENT`. Không nhận URL chuyển hướng tùy ý từ request.

Local nhận thư tại **Mailpit: http://localhost:8025**. Mailpit không chuyển thư đến hộp thư thật; không dùng cấu hình này trên máy chủ công khai. Trang quên/đặt lại mật khẩu không yêu cầu đăng nhập và dùng nội dung tiếng Việt.

## 2. Chạy seed có chủ đích, không tự seed khi bật service

Cần Node.js 22+, toàn bộ backend với code mới, PostgreSQL, Redis, RabbitMQ và Mailpit đang chạy. Network/Pricing cần có catalog local có sẵn. Script không tự bật/tắt service, không xóa database và không dùng SQL để bỏ qua nghiệp vụ.

Để test **điều phối viên tự phân công**, đặt `OPERATIONS_AUTO_ASSIGN_ENABLED=false` trong cấu hình chạy **Operations service** (hoặc sửa `operations.dispatch.auto-assign-enabled` trong YAML) trước khi khởi động. Tùy chọn này tắt việc tự chọn người ở cả lúc tạo kế hoạch, mở chặng kế tiếp và redispatch; phân công thủ công vẫn kiểm tra đầy đủ quyền, bưu cục, ca và tải công việc. Mặc định vẫn `true` để giữ hành vi cũ. Chỉ tắt `OPERATIONS_REDISPATCH_ENABLED` là chưa đủ. Tắt tùy chọn không gỡ các phân công đã có.

Tại thư mục backend:

```bash
# Chỉ xem kế hoạch, không gọi API, không tạo dữ liệu
node scripts/seed-luong-giao-hang-viet.mjs

# Nạp qua API sau khi bạn đã chạy backend
node scripts/seed-luong-giao-hang-viet.mjs --apply
```

Mật khẩu mặc định **chỉ dành cho bộ mẫu local**: `1205@Phuc` (không có dấu `\`). Có thể cấu hình `PAVEX_DEMO_PASSWORD`, `PAVEX_ADMIN_EMAIL`, `PAVEX_ADMIN_PASSWORD`, `PAVEX_API_URL`, `PAVEX_MAILPIT_URL`. Script chỉ chấp nhận HTTP localhost. Không đổi mật khẩu tài khoản có sẵn ngoài bộ mẫu.

Script tạo **1 đối tác + 14 nhân viên + 4 ca làm việc + 1 đơn hàng/1 kiện**, dùng admin bootstrap có sẵn. Nhân sự được onboard đúng API, tự đặt mật khẩu bằng token đọc từ thư Mailpit đúng người nhận, rồi đăng nhập kiểm tra. Đối tác tự đăng ký, hoàn thành hồ sơ, được admin duyệt và chờ đồng bộ role trước khi tạo đơn.

Tạo thêm tuyến `MAU-HN-HCM-DUONGBO`, tên **Tuyến mẫu Hà Nội → TP.HCM — đường bộ**, ưu tiên 1, sao chép 3 chặng đường bộ từ `HN-HCM-STANDARD`. Đây là thay đổi cấu hình routing local: các đơn mới cùng cặp bưu cục cũng có thể chọn tuyến này. Có thể tắt tuyến mẫu tại Network sau khi đã tạo đơn để trở lại ưu tiên cũ; không sửa/xóa các tuyến gốc. Tuyến hàng không gốc không bị giả mạo thành chuyến xe.

Ca dài 8 giờ, được xếp nhân viên trước giờ bắt đầu rồi chấm công bằng chính tài khoản nhân viên. **Đơn dừng ở “Chờ lấy hàng”**, chưa quét, chưa giao thành công — để bạn tự test. Script không gọi API phân công. Nếu đã tắt tự phân công như trên, công việc đầu tiên ở “Sẵn sàng phân công”; nếu giữ mặc định tự động, hệ thống có thể đã chọn nhân viên.

Kết quả và danh sách account: `.local/seed-luong-viet/ket-qua.md`. Nhật ký tiếp tục: `.local/seed-luong-viet/state.json`, không lưu mật khẩu hoặc token. Chạy lại dùng email và mã tham chiếu cố định để tìm dữ liệu cũ, không tự tạo thêm đơn hay đẩy lùi tiến độ. Không chạy song song.

Nếu dữ liệu đã được bạn đổi mật khẩu/đình chỉ/chuyển bưu cục, seed dừng thay vì ghi đè. Nếu ca đã hết, cần xếp ca và chấm công ca mới trên giao diện. Nếu reset database bằng cách riêng của bạn, hãy lưu trữ/move nhật ký cũ trước khi seed database mới. Không xóa nhật ký khi database cũ vẫn còn vì script sẽ từ chối nhận tài khoản không có bằng chứng do nó tạo. Nếu tiến trình bị kill giữa API và lưu nhật ký, cần đối chiếu thủ công tài khoản vừa tạo; không tự nhận là account của seed. File khóa còn sót chỉ được gỡ khi chắc chắn không có tiến trình seed đang chạy.

## 3. Tài khoản mẫu

Tất cả tên, số điện thoại và địa chỉ là giả lập để kiểm thử; không đại diện người thật. Email bên dưới thêm hậu tố **`@demo.pavex.local`**.

| Email trước @ | Họ tên | Vai trò | Phạm vi |
|---|---|---|---|
| minhanh | Nguyễn Minh Anh | Chủ cửa hàng May Mộc | Đơn của cửa hàng |
| quocbao | Trần Quốc Bảo | Quản lý vận hành | Toàn mạng, điều phối tại hai tổng kho |
| vanhung | Nguyễn Văn Hùng | Quản lý bưu cục | Hoàn Kiếm |
| ngocmai | Trần Ngọc Mai | Văn phòng / điều phối | Hoàn Kiếm |
| hoangnam | Lê Hoàng Nam | Lấy hàng | Hoàn Kiếm |
| thanhha | Phạm Thanh Hà | Nhân viên kho | Hoàn Kiếm |
| ducthinh | Vũ Đức Thịnh | Tài xế gom hàng | Hoàn Kiếm → tổng kho Hà Nội |
| tuananh | Bùi Tuấn Anh | Nhân viên kho | Tổng kho Hà Nội |
| minhduc | Đỗ Minh Đức | Tài xế liên tỉnh | Tổng kho Hà Nội → tổng kho TP.HCM |
| hongnhung | Lê Hồng Nhung | Nhân viên kho | Tổng kho TP.HCM |
| quanghuy | Nguyễn Quang Huy | Tài xế chuyển hàng | Tổng kho TP.HCM → Sài Gòn |
| khanhlinh | Võ Khánh Linh | Quản lý bưu cục | Sài Gòn |
| baotran | Trần Bảo Trân | Văn phòng / điều phối | Sài Gòn |
| minhchau | Phan Minh Châu | Nhân viên kho | Sài Gòn |
| giahao | Lâm Gia Hào | Giao chặng cuối | Sài Gòn |

Người nhận **Phạm Thảo Vy** không cần tài khoản hệ thống. Một role `COURIER` dùng cho cả lấy và giao; công việc và bưu cục quyết định người đó làm chặng nào. Admin không kiêm nhân viên giao hàng; quản lý bưu cục không mặc định có quyền toàn mạng.

## 4. Luồng kiểm thử theo đúng thứ tự

Tìm đơn bằng mã vận đơn trong báo cáo hoặc mã tham chiếu **PAVEX-MAU-HN-HCM-01**. Chọn nhân sự theo tên/email/mã nhân viên trên giao diện, không phải tự tìm UUID. API nội bộ vẫn dùng UUID quan hệ như hiện tại.

| Bước | Người thực hiện | Công việc / kết quả hiển thị |
|---|---|---|
| 1 | Trần Ngọc Mai phân công Lê Hoàng Nam | Lấy hàng → nhận việc → bắt đầu → quét “Đã lấy hàng” |
| 2 | Phạm Thanh Hà | Nhận công việc xử lý bưu cục gửi; quét nhập “Đã đến bưu cục gửi” |
| 3 | Vũ Đức Thịnh | Nhận chuyến gom; quét xuất “Đang trung chuyển” kèm đúng chặng |
| 4 | Bùi Tuấn Anh | Quét nhận tại tổng kho Hà Nội “Đã đến bưu cục trung chuyển”, kết thúc chuyến đến; nhận/bắt đầu công việc kho vừa được mở rồi quét xử lý tại kho |
| 5 | Đỗ Minh Đức | Nhận chuyến liên tỉnh; quét xuất “Đang trung chuyển” |
| 6 | Lê Hồng Nhung | Quét nhận tại tổng kho TP.HCM “Đã đến bưu cục trung chuyển”; nhận/bắt đầu công việc kho rồi quét xử lý |
| 7 | Nguyễn Quang Huy | Nhận chuyến đến bưu cục Sài Gòn; quét xuất “Đang trung chuyển” |
| 8 | Phan Minh Châu | Quét nhận “Đã đến bưu cục phát”; nhận/bắt đầu công việc kho vừa được mở rồi quét xử lý |
| 9 | Trần Bảo Trân phân công Lâm Gia Hào | Giao chặng cuối → nhận việc → bắt đầu → quét “Đang giao hàng” → ghi nhận giao thành công có bằng chứng |

Quản lý vận hành Trần Quốc Bảo điều phối các chuyến và xử lý kho thuộc hai tổng kho. Với quét nhận chuyến đến, chọn đúng chặng tuyến; công việc xử lý tại kho được mở sau khi chuyến đến hoàn tất. Quét xử lý tại kho không đổi trạng thái kiện (`nextStatus=null`) khi kiện đã ở đúng kho. Không nhận trước công việc đang “Chờ chặng trước”. Không dùng quét tùy ý để ép trạng thái “Đã giao hàng”; cần nghiệp vụ ghi nhận kết quả giao và bằng chứng.

Các dòng trạng thái trên giao diện và lịch sử hệ thống dùng tiếng Việt; mã enum/API như `IN_TRANSIT`, `READY`, `DELIVERED` giữ nguyên để không phá frontend/integration. Lịch sử tiếng Anh theo mẫu cũ được dịch khi trả response; ghi chú tự do của người dùng giữ nguyên.

## 5. Kiểm thử âm cần làm

- Nhân viên chưa xác minh dùng liên kết đặt mật khẩu hợp lệ: đặt xong đăng nhập được đúng role.
- Token thiếu/sai/hết hạn/đã dùng: không đổi mật khẩu, không xác minh email.
- Yêu cầu liên kết lần hai: lần một không còn dùng được.
- Đặt mật khẩu cho tài khoản bị đình chỉ: không mở lại trạng thái hoạt động hay cấp thêm quyền.
- Quản lý Hoàn Kiếm không quản lý nhân sự Sài Gòn; courier không nhận việc của người khác.
- Nhân viên chưa vào ca, đã kết ca hoặc không thuộc bưu cục: không đủ điều kiện nhận phân công.
- Không quét trung chuyển/giao hàng khi công việc chưa đúng người, chưa bắt đầu hoặc sai chặng.

## 6. Kiểm tra mã nguồn

```bash
./mvnw -pl auth,shipment,operations -am test
node --check scripts/seed-luong-giao-hang-viet.mjs
node --test scripts/tests/seed-luong-viet.test.mjs
```

Management Portal chạy `pnpm check`. Test script dùng API giả lập để kiểm tra trình tự và việc chạy lại; không thay thế kiểm thử tích hợp trên backend thật. Khi backend đang dừng, chưa thể xác nhận dữ liệu đã được nạp hoặc email đã gửi trong môi trường của bạn.
