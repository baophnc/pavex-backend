# Danh mục use case PAVEX

> Sinh tự động bởi `generator/build_usecase.py` — đừng sửa tay.

## Nhóm 1 · Xác thực & tài khoản cá nhân

| Mã | Use case | Tác nhân | Mô tả | Chức năng con |
|---|---|---|---|---|
| UC03 | Đăng ký tài khoản | Guest | Tạo tài khoản khách hàng mới; profileStatus = INCOMPLETE. *(Dữ liệu: UserAccount, UserProfile)* | «include» UC03.1 Xác minh email |
| UC01 | Đăng nhập | Customer, Back-office User, Operational Staff | Đăng nhập bằng email + mật khẩu; nhận access/refresh token (POST /api/v1/auth/login, /auth/refresh). Tài khoản SUSPENDED/DISABLED bị từ chối. *(Dữ liệu: UserAccount)* | «extend» UC01.1 Quên mật khẩu |
| UC02 | Đăng xuất | Customer, Back-office User, Operational Staff | Thu hồi refresh token, kết thúc phiên (POST /api/v1/auth/logout). *(Dữ liệu: UserAccount)* | — |
| UC04 | Quản lý hồ sơ cá nhân | Customer, Back-office User, Operational Staff | Xem và cập nhật hồ sơ cá nhân của chính mình (GET /api/v1/auth/me). *(Dữ liệu: UserProfile)* | «extend» UC04.1 Xem hồ sơ cá nhân<br>«extend» UC04.2 Cập nhật thông tin cá nhân<br>«extend» UC04.3 Cập nhật ảnh đại diện<br>«extend» UC04.4 Đổi mật khẩu |
| UC05 | Quản lý sổ địa chỉ | Customer | Quản lý danh sách địa chỉ gửi/nhận thường dùng. *(Dữ liệu: UserAddress)* | «extend» UC05.1 Thêm địa chỉ<br>«extend» UC05.2 Cập nhật địa chỉ<br>«extend» UC05.3 Xóa địa chỉ<br>«extend» UC05.4 Đặt địa chỉ mặc định |

## Nhóm 2 · Quản trị người dùng & phân quyền

| Mã | Use case | Tác nhân | Mô tả | Chức năng con |
|---|---|---|---|---|
| UC06 | Quản lý người dùng | Platform Admin | Quản lý tài khoản người dùng (GET /api/v1/users; quyền identity.users.read). *(Dữ liệu: UserAccount, UserProfile, Role)* | «extend» UC06.1 Tìm kiếm & lọc người dùng<br>«extend» UC06.2 Xem chi tiết người dùng<br>«extend» UC06.3 Cập nhật trạng thái tài khoản<br>«extend» UC06.4 Xác minh hồ sơ người dùng<br>«extend» UC06.5 Gán vai trò cho người dùng |
| UC07 | Tạo tài khoản nhân viên | Platform Admin | Tạo tài khoản nội bộ cho nhân sự vận hành/quản lý. *(Dữ liệu: UserAccount, Role)* | «include» UC06.5 Gán vai trò cho người dùng |
| UC08 | Quản lý vai trò & quyền | Platform Admin | Quản lý RBAC (GET /api/v1/roles; quyền identity.roles.read). Vai trò hệ thống không sửa/xóa. *(Dữ liệu: Role, Permission)* | «extend» UC08.1 Xem vai trò & quyền<br>«extend» UC08.2 Tạo vai trò<br>«extend» UC08.3 Cập nhật vai trò<br>«extend» UC08.4 Xóa vai trò<br>«extend» UC08.5 Phân quyền cho vai trò |

## Nhóm 3 · Cửa hàng (Merchant)

| Mã | Use case | Tác nhân | Mô tả | Chức năng con |
|---|---|---|---|---|
| UC09 | Đăng ký cửa hàng | Customer | Khách hàng đăng ký cửa hàng (tên kinh doanh, liên hệ, MST). Merchant ở trạng thái chờ xác minh. *(Dữ liệu: Merchant)* | — |
| UC10 | Quản lý thông tin cửa hàng | Merchant | Xem/cập nhật thông tin cửa hàng. *(Dữ liệu: Merchant)* | «extend» UC10.1 Xem thông tin cửa hàng<br>«extend» UC10.2 Cập nhật thông tin cửa hàng |
| UC11 | Quản lý địa chỉ lấy hàng | Merchant | Quản lý kho/điểm lấy hàng. *(Dữ liệu: MerchantPickupAddress)* | «extend» UC11.1 Thêm địa chỉ lấy hàng<br>«extend» UC11.2 Cập nhật địa chỉ lấy hàng<br>«extend» UC11.3 Xóa địa chỉ lấy hàng<br>«extend» UC11.4 Đặt địa chỉ lấy hàng mặc định |
| UC12 | Yêu cầu xác minh cửa hàng | Merchant | Gửi hồ sơ để quản trị xác minh (status → PENDING_VERIFICATION). *(Dữ liệu: Merchant)* | — |
| UC13 | Duyệt & quản lý cửa hàng | Platform Admin | Quản trị vòng đời cửa hàng; mọi thay đổi ghi statusReasonCode/Detail, statusChangedBy. *(Dữ liệu: Merchant)* | «extend» UC13.1 Xem danh sách cửa hàng<br>«extend» UC13.2 Duyệt xác minh cửa hàng<br>«extend» UC13.3 Từ chối xác minh cửa hàng<br>«extend» UC13.4 Tạm ngưng cửa hàng<br>«extend» UC13.5 Khôi phục cửa hàng |

## Nhóm 4 · Tra cứu công khai

| Mã | Use case | Tác nhân | Mô tả | Chức năng con |
|---|---|---|---|---|
| UC14 | Tra cứu vận đơn | Public User | Tra cứu trạng thái theo mã vận đơn (trackingCode). *(Dữ liệu: Shipment, ShipmentEvent)* | «include» UC14.1 Xem hành trình vận đơn |
| UC16 | Tra cứu bưu cục | Public User | Tìm hub/bưu cục công khai (publicVisible) theo tỉnh/phường. *(Dữ liệu: Hub)* | — |
| UC15 | Tra cứu giá cước | Public User | Nhập điểm đi/đến, kiện hàng (khối lượng, kích thước), COD, giá trị khai báo → các phương án giá theo mức dịch vụ; báo giá có hạn (quoteTtlMinutes). *(Dữ liệu: QuoteRequest, QuoteParcel, QuoteOption, RatePlan)* | — |

## Nhóm 5 · Đơn hàng & vận đơn

| Mã | Use case | Tác nhân | Mô tả | Chức năng con |
|---|---|---|---|---|
| UC17 | Tạo đơn hàng | Merchant, Hub Dispatch | Tạo vận đơn từ báo giá còn hiệu lực; hệ thống snapshot giá & địa chỉ, sinh trackingCode và định tuyến/giữ chỗ tải. *(Dữ liệu: Shipment, Parcel, QuoteRequest, LaneCapacityReservation)* | «include» UC15 Tra cứu giá cước<br>«include» UC17.1 Khai báo kiện hàng<br>«extend» UC17.2 Chọn địa chỉ từ sổ địa chỉ<br>«extend» UC17.3 Khai báo thu hộ (COD)<br>«extend» UC17.4 Khai báo giá trị bảo hiểm |
| UC18 | Quản lý đơn hàng | Merchant | Quản lý các vận đơn của cửa hàng. *(Dữ liệu: Shipment)* | «extend» UC18.1 Xem danh sách đơn hàng<br>«extend» UC18.2 Xem chi tiết đơn hàng<br>«extend» UC18.3 Hủy đơn hàng |
| UC19 | Theo dõi vận đơn | Merchant | Theo dõi tiến trình các vận đơn của cửa hàng. *(Dữ liệu: Shipment, ShipmentEvent, DeliveryAttempt)* | «include» UC14.1 Xem hành trình vận đơn |
| UC20 | Yêu cầu xử lý ngoại lệ vận đơn | Merchant, Hub Dispatch | Tạo ShipmentExceptionRequest (status PENDING) chờ duyệt. *(Dữ liệu: ShipmentExceptionRequest)* | «extend» UC20.1 Yêu cầu hoàn hàng<br>«extend» UC20.2 Yêu cầu chuyển tiếp địa chỉ mới<br>«extend» UC20.3 Yêu cầu giao lại<br>«extend» UC20.4 Yêu cầu hủy vận đơn |

## Nhóm 6 · Bảng giá & mạng lưới

| Mã | Use case | Tác nhân | Mô tả | Chức năng con |
|---|---|---|---|---|
| UC21 | Quản lý bảng giá | Platform Admin | Quản lý RatePlan có phiên bản; chỉ một revision ACTIVE. *(Dữ liệu: RatePlan, RateRule)* | «extend» UC21.1 Xem danh sách bảng giá<br>«extend» UC21.2 Tạo bảng giá<br>«extend» UC21.3 Tạo phiên bản bảng giá<br>«extend» UC21.4 Cấu hình quy tắc giá<br>«extend» UC21.5 Cấu hình phụ phí & giới hạn<br>«extend» UC21.6 Kích hoạt bảng giá<br>«extend» UC21.7 Ngừng áp dụng bảng giá |
| UC22 | Quản lý mạng lưới | Platform Admin | Cấu hình mạng lưới vận hành. *(Dữ liệu: NetworkRegion, ServiceArea, Hub, HubLane, LaneSchedule, RouteTemplate)* | «extend» UC22.1 Quản lý vùng mạng lưới<br>«extend» UC22.2 Quản lý khu vực phục vụ<br>«extend» UC22.3 Quản lý hub/bưu cục<br>«extend» UC22.4 Quản lý tuyến vận chuyển<br>«extend» UC22.5 Quản lý lịch chạy tuyến<br>«extend» UC22.6 Quản lý mẫu lộ trình |

## Nhóm 7 · Nhân sự vận hành & ca làm việc

| Mã | Use case | Tác nhân | Mô tả | Chức năng con |
|---|---|---|---|---|
| UC23 | Quản lý nhân sự vận hành | Hub Manager, Operations Manager | Quản lý hồ sơ WorkforceMember và gán hub. *(Dữ liệu: WorkforceMember, HubMembership)* | «extend» UC23.1 Tạo hồ sơ nhân sự<br>«extend» UC23.2 Gán nhân sự vào hub<br>«extend» UC23.3 Gỡ nhân sự khỏi hub<br>«extend» UC23.4 Cập nhật trạng thái nhân sự |
| UC24 | Xem nhân sự vận hành | Hub Dispatch | Xem nhân sự tại hub và trạng thái sẵn sàng/tải việc hiện tại. *(Dữ liệu: WorkforceMember, WorkforceAvailability)* | — |
| UC25 | Quản lý ca làm việc | Hub Manager, Operations Manager | Lập và điều chỉnh ca tại hub. *(Dữ liệu: WorkShift, WorkforceShiftAssignment)* | «extend» UC25.1 Tạo ca làm việc<br>«extend» UC25.2 Phân công nhân sự vào ca<br>«extend» UC25.3 Hủy ca làm việc<br>«extend» UC25.4 Đánh dấu vắng mặt |
| UC26 | Xem ca làm việc | Operational Staff | Xem lịch ca được phân công. *(Dữ liệu: WorkShift, WorkforceShiftAssignment)* | — |
| UC27 | Check-in / Check-out ca | Operational Staff | Ghi nhận checkedInAt/checkedOutAt. *(Dữ liệu: WorkforceShiftAssignment)* | — |
| UC28 | Cập nhật trạng thái sẵn sàng | Operational Staff | AVAILABLE / BUSY / OFFLINE. *(Dữ liệu: WorkforceAvailability)* | — |

## Nhóm 8 · Điều phối & thực hiện vận hành

| Mã | Use case | Tác nhân | Mô tả | Chức năng con |
|---|---|---|---|---|
| UC29 | Xem tổng quan vận hành | Back-office User | Bảng điều hành: sản lượng, vận đơn trễ/tạm giữ, công việc tồn, nhân sự. *(Dữ liệu: Shipment, OperationalAssignment)* | — |
| UC30 | Theo dõi vận đơn vận hành | Hub Dispatch, Operations Manager | Tra cứu vận đơn nội bộ (kiện, custody, định tuyến, assignment). *(Dữ liệu: Shipment, Parcel, OperationalAssignment)* | «extend» UC30.1 Tạm giữ vận đơn<br>«extend» UC30.2 Giải phóng vận đơn |
| UC31 | Điều phối công việc | Hub Dispatch | Phân công OperationalAssignment cho nhân sự phù hợp (requiredKind). *(Dữ liệu: OperationalAssignment, WorkforceMember)* | «include» UC24 Xem nhân sự vận hành<br>«extend» UC31.1 Phân công công việc<br>«extend» UC31.2 Phân công lại công việc<br>«extend» UC31.3 Hủy công việc |
| UC32 | Xem công việc được giao | Operational Staff | Danh sách assignment của tôi theo ưu tiên. *(Dữ liệu: OperationalAssignment)* | — |
| UC33 | Thực hiện công việc | Operational Staff | Thực hiện assignment (lấy hàng, nhập/xuất hub, phân loại, trung chuyển, giao, hoàn). *(Dữ liệu: OperationalAssignment, Parcel)* | «extend» UC33.1 Nhận công việc<br>«extend» UC33.2 Bắt đầu công việc<br>«extend» UC33.3 Hoàn thành công việc<br>«extend» UC33.4 Báo không hoàn thành<br>«include» UC34 Quét kiện hàng |
| UC36 | Bàn giao hàng trung chuyển | Line-haul Driver | Nhận hàng tại hub đi và bàn giao tại hub đến theo chuyến (LINE_HAUL). *(Dữ liệu: OperationalAssignment, LaneCapacityReservation)* | «include» UC34 Quét kiện hàng |
| UC35 | Ghi nhận kết quả giao hàng | Courier | Tạo DeliveryAttempt cho từng kiện. *(Dữ liệu: DeliveryAttempt, Parcel)* | «extend» UC35.1 Xác nhận giao thành công<br>«extend» UC35.2 Ghi nhận giao thất bại |
| UC37 | Điều chỉnh khối lượng kiện | Warehouse Operator | Cân lại kiện tại hub; tạo ShipmentWeightAdjustment, vận đơn chờ duyệt. *(Dữ liệu: ShipmentWeightAdjustment, Parcel)* | — |
| UC38 | Duyệt điều chỉnh khối lượng | Hub Manager | Duyệt/ghi chú điều chỉnh khối lượng; cập nhật hasPendingWeightReview. *(Dữ liệu: ShipmentWeightAdjustment)* | — |

## Nhóm 9 · Ngoại lệ & sự cố

| Mã | Use case | Tác nhân | Mô tả | Chức năng con |
|---|---|---|---|---|
| UC39 | Xem ngoại lệ vận đơn | Hub Dispatch | Danh sách vận đơn có ngoại lệ: giao thất bại, tạm giữ, chờ duyệt khối lượng, yêu cầu ngoại lệ. *(Dữ liệu: Shipment, ShipmentExceptionRequest)* | — |
| UC40 | Xử lý yêu cầu ngoại lệ | Hub Dispatch | Duyệt và hoàn tất ShipmentExceptionRequest. *(Dữ liệu: ShipmentExceptionRequest)* | «extend» UC40.1 Phê duyệt yêu cầu<br>«extend» UC40.2 Từ chối yêu cầu<br>«extend» UC40.3 Hoàn tất yêu cầu<br>«extend» UC40.4 Định tuyến lại vận đơn |
| UC41 | Báo cáo sự cố | Operational Staff | Tạo ShipmentCase (hư hỏng, thất lạc, kiện không xác định...) tại hub. *(Dữ liệu: ShipmentCase)* | — |
| UC42 | Quản lý hồ sơ sự cố | Operations Manager | Theo dõi và xử lý hồ sơ sự cố. *(Dữ liệu: ShipmentCase)* | «extend» UC42.1 Phân công xử lý sự cố<br>«extend» UC42.2 Cập nhật tiến độ xử lý<br>«extend» UC42.3 Giải quyết sự cố<br>«extend» UC42.4 Đóng hồ sơ sự cố |

## Tác nhân

| Tác nhân | Trừu tượng | Mô tả |
|---|---|---|
| Public User | ✔ | [Cổng khách hàng] Tác nhân trừu tượng: chức năng tra cứu công khai dùng chung cho Guest và Customer. |
| Guest |  | [Cổng khách hàng] Khách vãng lai, chưa có tài khoản. |
| Customer |  | [Cổng khách hàng] Khách hàng đã có tài khoản. |
| Merchant |  | [Cổng khách hàng] Khách hàng sở hữu cửa hàng đã xác minh; kế thừa mọi chức năng của Customer. |
| Back-office User | ✔ | [Cổng quản trị] Tác nhân trừu tượng: đăng nhập, hồ sơ cá nhân, bảng điều hành dùng chung cho mọi tài khoản nội bộ. |
| Platform Admin |  | [Cổng quản trị] Quản trị nền tảng: người dùng, vai trò & quyền, cửa hàng, bảng giá, mạng lưới. |
| Hub Dispatch |  | [Cổng quản trị] Điều phối viên hub: điều phối công việc, xử lý ngoại lệ, tạo đơn tại quầy. |
| Hub Manager |  | [Cổng quản trị] Trưởng hub: kế thừa Hub Dispatch + quản lý nhân sự, ca, duyệt điều chỉnh khối lượng. |
| Operations Manager |  | [Cổng quản trị] Quản lý vận hành vùng: nhân sự, ca, giám sát vận đơn, hồ sơ sự cố. |
| Operational Staff | ✔ | [Ứng dụng vận hành] Tác nhân trừu tượng: chức năng chung của nhân sự tuyến đầu (WorkforceMember). |
| Courier |  | [Ứng dụng vận hành] Bưu tá lấy/giao hàng (WorkforceMemberKind.COURIER). |
| Line-haul Driver |  | [Ứng dụng vận hành] Tài xế trung chuyển giữa các hub (LINE_HAUL_DRIVER). |
| Warehouse Operator |  | [Ứng dụng vận hành] Nhân viên kho/khai thác tại hub (WAREHOUSE_OPERATOR). |

Tổng quát hóa (cha → con): Public User → Guest; Public User → Customer; Customer → Merchant; Back-office User → Platform Admin; Back-office User → Hub Dispatch; Back-office User → Operations Manager; Hub Dispatch → Hub Manager; Operational Staff → Courier; Operational Staff → Line-haul Driver; Operational Staff → Warehouse Operator
