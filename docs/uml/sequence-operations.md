# Phương thức được dùng trong các sequence diagram

> Sinh tự động từ `docs/report/generator/sequences.py`. Mỗi thông điệp gửi tới lớp biên, lớp điều khiển
> hoặc lớp thực thể là một phương thức của lớp đó; class diagram toàn hệ thống phải có đủ các phương thức này.

## «entity»

| Lớp | Phương thức | Dùng trong |
|---|---|---|
| DeliveryAttempt | record() | SD-UC35 |
| Merchant | findById() | SD-UC13 |
| Merchant | findByOwner() | SD-UC17 |
| Merchant | reactivate() | SD-UC13 |
| Merchant | reject() | SD-UC13 |
| Merchant | search() | SD-UC13 |
| Merchant | suspend() | SD-UC13 |
| Merchant | verify() | SD-UC13 |
| OperationalAssignment | accept() | SD-UC33 |
| OperationalAssignment | assign() | SD-UC31 |
| OperationalAssignment | cancel() | SD-UC31 |
| OperationalAssignment | complete() | SD-UC33 |
| OperationalAssignment | create() | SD-UC17 |
| OperationalAssignment | createNextLeg() | SD-UC33 |
| OperationalAssignment | fail() | SD-UC33 |
| OperationalAssignment | findByAssignee() | SD-UC33 |
| OperationalAssignment | findById() | SD-UC31, SD-UC33 |
| OperationalAssignment | findPending() | SD-UC31 |
| OperationalAssignment | missingParcels() | SD-UC33 |
| OperationalAssignment | start() | SD-UC33 |
| Parcel | findByCode() | SD-UC33 |
| Parcel | updateCustody() | SD-UC33 |
| Permission | findByCodes() | SD-UC08 |
| QuoteRequest | create() | SD-UC15 |
| RatePlan | findActive() | SD-UC15 |
| RatePlan | price() | SD-UC15 |
| Role | findAll() | SD-UC08 |
| Role | findByAccount() | SD-UC01 |
| Role | findByCode() | SD-UC08 |
| Role | replacePermissions() | SD-UC08 |
| ServiceArea | resolve() | SD-UC15 |
| Shipment | applyException() | SD-UC40 |
| Shipment | canChange() | SD-UC40 |
| Shipment | create() | SD-UC17 |
| Shipment | findById() | SD-UC35, SD-UC40 |
| Shipment | findByTrackingCode() | SD-UC14 |
| Shipment | markDelivered() | SD-UC35 |
| Shipment | markRouted() | SD-UC17 |
| Shipment | markRoutingFailed() | SD-UC17 |
| Shipment | recordFailedAttempt() | SD-UC35 |
| ShipmentEvent | findByShipment() | SD-UC14 |
| ShipmentEvent | record() | SD-UC33, SD-UC35 |
| ShipmentExceptionRequest | approve() | SD-UC40 |
| ShipmentExceptionRequest | complete() | SD-UC40 |
| ShipmentExceptionRequest | findById() | SD-UC40 |
| ShipmentExceptionRequest | findPending() | SD-UC40 |
| ShipmentExceptionRequest | reject() | SD-UC40 |
| UserAccount | changeStatus() | SD-UC06 |
| UserAccount | existsByEmail() | SD-UC03 |
| UserAccount | findByEmail() | SD-UC01 |
| UserAccount | findById() | SD-UC06 |
| UserAccount | findByVerificationToken() | SD-UC03 |
| UserAccount | matchesPassword() | SD-UC01 |
| UserAccount | register() | SD-UC03 |
| UserAccount | revokeSessions() | SD-UC06 |
| UserAccount | search() | SD-UC06 |
| UserAccount | verifyEmail() | SD-UC03 |
| WorkShift | assign() | SD-UC25 |
| WorkShift | cancel() | SD-UC25 |
| WorkShift | create() | SD-UC25 |
| WorkShift | existsByCode() | SD-UC25 |
| WorkShift | findByHubAndDate() | SD-UC25 |
| WorkShift | findById() | SD-UC25 |
| WorkforceMember | canTakeAssignment() | SD-UC31 |
| WorkforceMember | findById() | SD-UC31 |
| WorkforceMember | findEligible() | SD-UC31 |
| WorkforceShiftAssignment | existsOverlap() | SD-UC25 |
| WorkforceShiftAssignment | findById() | SD-UC25 |
| WorkforceShiftAssignment | markAbsent() | SD-UC25 |

## «control»

| Lớp | Phương thức | Dùng trong |
|---|---|---|
| AssignmentController | accept() | SD-UC33 |
| AssignmentController | complete() | SD-UC33 |
| AssignmentController | fail() | SD-UC33 |
| AssignmentController | myAssignments() | SD-UC33 |
| AssignmentController | scan() | SD-UC33 |
| AssignmentController | start() | SD-UC33 |
| AuthController | issueTokens() | SD-UC01 |
| AuthController | login() | SD-UC01 |
| AuthController | register() | SD-UC03 |
| AuthController | verifyEmail() | SD-UC03 |
| DeliveryController | getDelivery() | SD-UC35 |
| DeliveryController | recordDelivery() | SD-UC35 |
| DeliveryController | recordFailure() | SD-UC35 |
| DeliveryController | validateProof() | SD-UC35 |
| DispatchController | assign() | SD-UC31 |
| DispatchController | cancel() | SD-UC31 |
| DispatchController | eligibleMembers() | SD-UC31 |
| DispatchController | listPending() | SD-UC31 |
| ExceptionRequestController | approve() | SD-UC40 |
| ExceptionRequestController | getRequest() | SD-UC40 |
| ExceptionRequestController | listPending() | SD-UC40 |
| ExceptionRequestController | reject() | SD-UC40 |
| MerchantController | getMerchant() | SD-UC13 |
| MerchantController | listMerchants() | SD-UC13 |
| MerchantController | reactivate() | SD-UC13 |
| MerchantController | reject() | SD-UC13 |
| MerchantController | suspend() | SD-UC13 |
| MerchantController | verify() | SD-UC13 |
| QuoteController | chargeableWeight() | SD-UC15 |
| QuoteController | createQuote() | SD-UC15, SD-UC17 |
| QuoteController | validateLimits() | SD-UC15 |
| QuoteController | validateQuote() | SD-UC17 |
| RoleController | checkPermission() | SD-UC08 |
| RoleController | listRoles() | SD-UC08 |
| RoleController | updatePermissions() | SD-UC08 |
| RoutingController | reroute() | SD-UC40 |
| RoutingController | route() | SD-UC17 |
| ShiftController | assignMembers() | SD-UC25 |
| ShiftController | cancelShift() | SD-UC25 |
| ShiftController | createShift() | SD-UC25 |
| ShiftController | listShifts() | SD-UC25 |
| ShiftController | markAbsent() | SD-UC25 |
| ShiftController | skip() | SD-UC25 |
| ShipmentController | createShipment() | SD-UC17 |
| ShipmentController | getDraftContext() | SD-UC17 |
| TrackingController | maskContactInfo() | SD-UC14 |
| TrackingController | track() | SD-UC14 |
| UserController | checkPermission() | SD-UC06 |
| UserController | getUser() | SD-UC06 |
| UserController | listUsers() | SD-UC06 |
| UserController | updateStatus() | SD-UC06 |

## «boundary»

| Lớp | Phương thức | Dùng trong |
|---|---|---|
| CreateShipmentPage | Chọn Tạo đơn hàng() | SD-UC17 |
| CreateShipmentPage | Chọn mức dịch vụ, nhấn Xác nhận tạo đơn() | SD-UC17 |
| CreateShipmentPage | Nhập người gửi, người nhận, kiện hàng, COD() | SD-UC17 |
| CreateShipmentPage | validateForm() | SD-UC17 |
| DeliveryPage | Chọn Giao thành công() | SD-UC35 |
| DeliveryPage | Chọn Giao thất bại và lý do() | SD-UC35 |
| DeliveryPage | Cung cấp bằng chứng, xác nhận đã thu COD() | SD-UC35 |
| DeliveryPage | Mở vận đơn trong công việc giao hàng() | SD-UC35 |
| DispatchPage | Chọn công việc() | SD-UC31 |
| DispatchPage | Chọn công việc, nhập ghi chú, nhấn Hủy() | SD-UC31 |
| DispatchPage | Chọn nhân sự, nhấn Phân công() | SD-UC31 |
| DispatchPage | Mở Điều phối công việc() | SD-UC31 |
| ExceptionRequestsPage | Chọn một yêu cầu() | SD-UC40 |
| ExceptionRequestsPage | Mở Yêu cầu ngoại lệ() | SD-UC40 |
| ExceptionRequestsPage | Nhập ghi chú, nhấn Phê duyệt() | SD-UC40 |
| ExceptionRequestsPage | Nhập ghi chú, nhấn Từ chối() | SD-UC40 |
| LoginPage | Nhập email, mật khẩu, nhấn Đăng nhập() | SD-UC01 |
| LoginPage | validateForm() | SD-UC01 |
| MerchantsPage | Chọn lý do, nhấn Tạm ngưng() | SD-UC13 |
| MerchantsPage | Chọn lý do, nhập chi tiết, nhấn Từ chối() | SD-UC13 |
| MerchantsPage | Chọn một cửa hàng() | SD-UC13 |
| MerchantsPage | Mở Quản lý cửa hàng, lọc theo trạng thái() | SD-UC13 |
| MerchantsPage | Nhấn Duyệt() | SD-UC13 |
| MerchantsPage | Nhấn Khôi phục() | SD-UC13 |
| MyAssignmentsPage | Chọn công việc, nhấn Nhận việc() | SD-UC33 |
| MyAssignmentsPage | Mở Công việc của tôi() | SD-UC33 |
| MyAssignmentsPage | Nhấn Bắt đầu() | SD-UC33 |
| MyAssignmentsPage | Nhấn Hoàn thành() | SD-UC33 |
| MyAssignmentsPage | Nhập lý do, nhấn Báo không hoàn thành() | SD-UC33 |
| MyAssignmentsPage | Quét mã kiện hàng() | SD-UC33 |
| QuotePage | Nhập điểm gửi / nhận, kiện hàng, COD, nhấn Tính cước() | SD-UC15 |
| RegisterPage | Mở liên kết xác minh trong email() | SD-UC03 |
| RegisterPage | Nhập họ tên, email, mật khẩu, nhấn Đăng ký() | SD-UC03 |
| RegisterPage | validateForm() | SD-UC03 |
| RolesPage | Chọn cấp / thu hồi quyền, nhấn Lưu() | SD-UC08 |
| RolesPage | Chọn menu Vai trò & quyền() | SD-UC08 |
| RolesPage | Chọn một vai trò() | SD-UC08 |
| RolesPage | Nhập từ khóa tìm quyền() | SD-UC08 |
| RolesPage | filterPermissions() | SD-UC08 |
| RolesPage | showPermissions() | SD-UC08 |
| ShiftsPage | Chọn ca, nhập lý do, nhấn Hủy ca() | SD-UC25 |
| ShiftsPage | Chọn nhân sự không đến ca() | SD-UC25 |
| ShiftsPage | Chọn nhân sự, nhấn Phân công() | SD-UC25 |
| ShiftsPage | Mở Ca làm việc() | SD-UC25 |
| ShiftsPage | Nhập mã, tên, giờ bắt đầu, giờ kết thúc() | SD-UC25 |
| TrackingPage | Nhập mã vận đơn, nhấn Tra cứu() | SD-UC14 |
| TrackingPage | validateTrackingCode() | SD-UC14 |
| UsersPage | Chọn menu Người dùng() | SD-UC06 |
| UsersPage | Chọn một tài khoản() | SD-UC06 |
| UsersPage | Chọn trạng thái mới, nhấn Lưu() | SD-UC06 |
| UsersPage | Nhập từ khóa, chọn bộ lọc() | SD-UC06 |
