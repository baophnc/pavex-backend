"""PAVEX use case model -> Visual Paradigm XML.

Structure
---------
* UC-00  Phân cấp tác nhân: 10 vai trò backend + 1 tác nhân abstract cho mỗi frame
         (cổng khách hàng, cổng quản trị, ứng dụng vận hành) có chức năng dùng chung.
* UC-01  Biểu đồ use case tổng quát: mọi tác nhân + use case mức người dùng
         (user-goal) trong ranh giới hệ thống "PAVEX Logistics Platform".
* UC-02..UC-10  Biểu đồ phân rã theo nhóm chức năng: use case gốc + use case
         con nối bằng «include» (bước bắt buộc) / «extend» (chức năng tùy chọn).
All use cases are owned by the System (subject) element; actors live in an
"Actors" package. Generalization: From = general actor, To = specialized actor.
"""

from __future__ import annotations

import pathlib

from vpxml import Project

OUT = pathlib.Path(__file__).resolve().parent.parent

p = Project("PAVEX Use Case Model", "PVXU")

# --------------------------------------------------------------------------
# actors
# --------------------------------------------------------------------------
actors_pkg = p.add("Package", "Actors", doc="Tác nhân của hệ thống PAVEX.", key="pkg:actors")
# 10 concrete actors = the roles of the backend (as in the original diagram).
# An abstract actor is added only where several roles of the SAME frame
# (client portal / management portal / operations app) share use cases.
ACTORS = [
    # key, abstract, doc
    ("Public User", True, "[Cổng khách hàng] Tác nhân trừu tượng: chức năng tra cứu công khai dùng chung cho Guest và Customer."),
    ("Guest", False, "[Cổng khách hàng] Khách vãng lai, chưa có tài khoản."),
    ("Customer", False, "[Cổng khách hàng] Khách hàng đã có tài khoản."),
    ("Merchant", False, "[Cổng khách hàng] Khách hàng sở hữu cửa hàng đã xác minh; kế thừa mọi chức năng của Customer."),
    ("Back-office User", True, "[Cổng quản trị] Tác nhân trừu tượng: đăng nhập, hồ sơ cá nhân, bảng điều hành dùng chung cho mọi tài khoản nội bộ."),
    ("Platform Admin", False, "[Cổng quản trị] Quản trị nền tảng: người dùng, vai trò & quyền, cửa hàng, bảng giá, mạng lưới."),
    ("Hub Dispatch", False, "[Cổng quản trị] Điều phối viên hub: điều phối công việc, xử lý ngoại lệ, tạo đơn tại quầy."),
    ("Hub Manager", False, "[Cổng quản trị] Trưởng hub: kế thừa Hub Dispatch + quản lý nhân sự, ca, duyệt điều chỉnh khối lượng."),
    ("Operations Manager", False, "[Cổng quản trị] Quản lý vận hành vùng: nhân sự, ca, giám sát vận đơn, hồ sơ sự cố."),
    ("Operational Staff", True, "[Ứng dụng vận hành] Tác nhân trừu tượng: chức năng chung của nhân sự tuyến đầu (WorkforceMember)."),
    ("Courier", False, "[Ứng dụng vận hành] Bưu tá lấy/giao hàng (WorkforceMemberKind.COURIER)."),
    ("Line-haul Driver", False, "[Ứng dụng vận hành] Tài xế trung chuyển giữa các hub (LINE_HAUL_DRIVER)."),
    ("Warehouse Operator", False, "[Ứng dụng vận hành] Nhân viên kho/khai thác tại hub (WAREHOUSE_OPERATOR)."),
]
for name, abstract, doc in ACTORS:
    p.add("Actor", name, parent=actors_pkg, doc=doc, tag="ACT", Abstract="true" if abstract else "false")

GENERALIZATIONS = [  # (general, specific)
    ("Public User", "Guest"), ("Public User", "Customer"), ("Customer", "Merchant"),
    ("Back-office User", "Platform Admin"), ("Back-office User", "Hub Dispatch"),
    ("Back-office User", "Operations Manager"), ("Hub Dispatch", "Hub Manager"),
    ("Operational Staff", "Courier"), ("Operational Staff", "Line-haul Driver"), ("Operational Staff", "Warehouse Operator"),
]
for g, s in GENERALIZATIONS:
    p.rel("Generalization", g, s)

system = p.add("System", "PAVEX Logistics Platform", doc="Ranh giới hệ thống PAVEX (backend + cổng khách hàng + cổng quản trị + ứng dụng vận hành).",
               key="sys", tag="SYS")

# --------------------------------------------------------------------------
# use cases
# --------------------------------------------------------------------------
# (code, name, actors, description, data, subs)
# subs: (code, name, "include"|"extend", description) ; a sub given as ("=Name", kind) reuses an existing UC.
GROUPS = [
    ("UC-02", "Xác thực & tài khoản cá nhân", [
        ("UC03", "Đăng ký tài khoản", ["Guest"], "Tạo tài khoản khách hàng mới; profileStatus = INCOMPLETE.", "UserAccount, UserProfile", [
            ("UC03.1", "Xác minh email", "include", "Xác nhận email qua liên kết/mã; emailVerified = true.")]),
        ("UC01", "Đăng nhập", ["Customer", "Back-office User", "Operational Staff"], "Đăng nhập bằng email + mật khẩu; nhận access/refresh token (POST /api/v1/auth/login, /auth/refresh). Tài khoản SUSPENDED/DISABLED bị từ chối.", "UserAccount", [
            ("UC01.1", "Quên mật khẩu", "extend", "Yêu cầu đặt lại mật khẩu qua email khi không nhớ mật khẩu.")]),
        ("UC02", "Đăng xuất", ["Customer", "Back-office User", "Operational Staff"], "Thu hồi refresh token, kết thúc phiên (POST /api/v1/auth/logout).", "UserAccount", []),
        ("UC04", "Quản lý hồ sơ cá nhân", ["Customer", "Back-office User", "Operational Staff"], "Xem và cập nhật hồ sơ cá nhân của chính mình (GET /api/v1/auth/me).", "UserProfile", [
            ("UC04.1", "Xem hồ sơ cá nhân", "extend", "Xem thông tin tài khoản, vai trò, quyền."),
            ("UC04.2", "Cập nhật thông tin cá nhân", "extend", "Sửa họ tên, tên hiển thị, giới tính, ngày sinh."),
            ("UC04.3", "Cập nhật ảnh đại diện", "extend", "Tải lên/đổi avatarUrl."),
            ("UC04.4", "Đổi mật khẩu", "extend", "Đổi mật khẩu khi đã đăng nhập.")]),
        ("UC05", "Quản lý sổ địa chỉ", ["Customer"], "Quản lý danh sách địa chỉ gửi/nhận thường dùng.", "UserAddress", [
            ("UC05.1", "Thêm địa chỉ", "extend", "Thêm địa chỉ (tỉnh/thành, phường/xã, liên hệ)."),
            ("UC05.2", "Cập nhật địa chỉ", "extend", "Sửa địa chỉ."),
            ("UC05.3", "Xóa địa chỉ", "extend", "Xóa địa chỉ."),
            ("UC05.4", "Đặt địa chỉ mặc định", "extend", "Đánh dấu isDefault.")]),
    ]),
    ("UC-03", "Quản trị người dùng & phân quyền", [
        ("UC06", "Quản lý người dùng", ["Platform Admin"], "Quản lý tài khoản người dùng (GET /api/v1/users; quyền identity.users.read).", "UserAccount, UserProfile, Role", [
            ("UC06.1", "Tìm kiếm & lọc người dùng", "extend", "Tìm theo email/tên; lọc theo vai trò, trạng thái tài khoản, trạng thái hồ sơ; phân trang."),
            ("UC06.2", "Xem chi tiết người dùng", "extend", "Xem hồ sơ, vai trò, lịch sử trạng thái."),
            ("UC06.3", "Cập nhật trạng thái tài khoản", "extend", "Chuyển ACTIVE / SUSPENDED / DISABLED (kiểm tra version – optimistic lock)."),
            ("UC06.4", "Xác minh hồ sơ người dùng", "extend", "Duyệt hồ sơ PENDING_VERIFICATION → COMPLETED hoặc REJECTED."),
            ("UC06.5", "Gán vai trò cho người dùng", "extend", "Thêm/bớt vai trò của tài khoản.")]),
        ("UC07", "Tạo tài khoản nhân viên", ["Platform Admin"], "Tạo tài khoản nội bộ cho nhân sự vận hành/quản lý.", "UserAccount, Role", [
            ("=Gán vai trò cho người dùng", "include")]),
        ("UC08", "Quản lý vai trò & quyền", ["Platform Admin"], "Quản lý RBAC (GET /api/v1/roles; quyền identity.roles.read). Vai trò hệ thống không sửa/xóa.", "Role, Permission", [
            ("UC08.1", "Xem vai trò & quyền", "extend", "Danh sách vai trò, tìm quyền theo tên/mã."),
            ("UC08.2", "Tạo vai trò", "extend", "Tạo vai trò tùy chỉnh."),
            ("UC08.3", "Cập nhật vai trò", "extend", "Sửa tên, mô tả vai trò."),
            ("UC08.4", "Xóa vai trò", "extend", "Xóa vai trò không phải hệ thống."),
            ("UC08.5", "Phân quyền cho vai trò", "extend", "Gán/bỏ Permission cho vai trò.")]),
    ]),
    ("UC-04", "Cửa hàng (Merchant)", [
        ("UC09", "Đăng ký cửa hàng", ["Customer"], "Khách hàng đăng ký cửa hàng (tên kinh doanh, liên hệ, MST). Merchant ở trạng thái chờ xác minh.", "Merchant", []),
        ("UC10", "Quản lý thông tin cửa hàng", ["Merchant"], "Xem/cập nhật thông tin cửa hàng.", "Merchant", [
            ("UC10.1", "Xem thông tin cửa hàng", "extend", "Xem mã cửa hàng, trạng thái, lý do trạng thái."),
            ("UC10.2", "Cập nhật thông tin cửa hàng", "extend", "Sửa tên kinh doanh, liên hệ, MST.")]),
        ("UC11", "Quản lý địa chỉ lấy hàng", ["Merchant"], "Quản lý kho/điểm lấy hàng.", "MerchantPickupAddress", [
            ("UC11.1", "Thêm địa chỉ lấy hàng", "extend", ""),
            ("UC11.2", "Cập nhật địa chỉ lấy hàng", "extend", ""),
            ("UC11.3", "Xóa địa chỉ lấy hàng", "extend", ""),
            ("UC11.4", "Đặt địa chỉ lấy hàng mặc định", "extend", "")]),
        ("UC12", "Yêu cầu xác minh cửa hàng", ["Merchant"], "Gửi hồ sơ để quản trị xác minh (status → PENDING_VERIFICATION).", "Merchant", []),
        ("UC13", "Duyệt & quản lý cửa hàng", ["Platform Admin"], "Quản trị vòng đời cửa hàng; mọi thay đổi ghi statusReasonCode/Detail, statusChangedBy.", "Merchant", [
            ("UC13.1", "Xem danh sách cửa hàng", "extend", "Lọc theo trạng thái, tìm theo mã/tên."),
            ("UC13.2", "Duyệt xác minh cửa hàng", "extend", "PENDING_VERIFICATION → ACTIVE; ghi verifiedAt/verifiedBy."),
            ("UC13.3", "Từ chối xác minh cửa hàng", "extend", "→ REJECTED kèm lý do."),
            ("UC13.4", "Tạm ngưng cửa hàng", "extend", "ACTIVE → SUSPENDED kèm lý do."),
            ("UC13.5", "Khôi phục cửa hàng", "extend", "SUSPENDED → ACTIVE.")]),
    ]),
    ("UC-05", "Tra cứu công khai", [
        ("UC14", "Tra cứu vận đơn", ["Public User"], "Tra cứu trạng thái theo mã vận đơn (trackingCode).", "Shipment, ShipmentEvent", [
            ("UC14.1", "Xem hành trình vận đơn", "include", "Xem timeline ShipmentEvent (trạng thái, địa điểm, thời gian).")]),
        ("UC16", "Tra cứu bưu cục", ["Public User"], "Tìm hub/bưu cục công khai (publicVisible) theo tỉnh/phường.", "Hub", []),
        ("UC15", "Tra cứu giá cước", ["Public User"], "Nhập điểm đi/đến, kiện hàng (khối lượng, kích thước), COD, giá trị khai báo → các phương án giá theo mức dịch vụ; báo giá có hạn (quoteTtlMinutes).", "QuoteRequest, QuoteParcel, QuoteOption, RatePlan", []),
    ]),
    ("UC-06", "Đơn hàng & vận đơn", [
        ("UC17", "Tạo đơn hàng", ["Merchant", "Hub Dispatch"], "Tạo vận đơn từ báo giá còn hiệu lực; hệ thống snapshot giá & địa chỉ, sinh trackingCode và định tuyến/giữ chỗ tải.", "Shipment, Parcel, QuoteRequest, LaneCapacityReservation", [
            ("=Tra cứu giá cước", "include"),
            ("UC17.1", "Khai báo kiện hàng", "include", "Mô tả, loại hàng, dễ vỡ, khối lượng, kích thước từng kiện."),
            ("UC17.2", "Chọn địa chỉ từ sổ địa chỉ", "extend", "Dùng UserAddress/MerchantPickupAddress làm người gửi/nhận."),
            ("UC17.3", "Khai báo thu hộ (COD)", "extend", "Nhập codAmount (≤ maximumCod)."),
            ("UC17.4", "Khai báo giá trị bảo hiểm", "extend", "Nhập declaredValue (≤ maximumDeclaredValue).")]),
        ("UC18", "Quản lý đơn hàng", ["Merchant"], "Quản lý các vận đơn của cửa hàng.", "Shipment", [
            ("UC18.1", "Xem danh sách đơn hàng", "extend", "Lọc theo trạng thái, ngày tạo, mã tham chiếu."),
            ("UC18.2", "Xem chi tiết đơn hàng", "extend", "Xem kiện, cước, trạng thái định tuyến."),
            ("UC18.3", "Hủy đơn hàng", "extend", "Hủy khi chưa lấy hàng; ghi cancellationReason.")]),
        ("UC19", "Theo dõi vận đơn", ["Merchant"], "Theo dõi tiến trình các vận đơn của cửa hàng.", "Shipment, ShipmentEvent, DeliveryAttempt", [
            ("=Xem hành trình vận đơn", "include")]),
        ("UC20", "Yêu cầu xử lý ngoại lệ vận đơn", ["Merchant", "Hub Dispatch"], "Tạo ShipmentExceptionRequest (status PENDING) chờ duyệt.", "ShipmentExceptionRequest", [
            ("UC20.1", "Yêu cầu hoàn hàng", "extend", "kind = RETURN_TO_SENDER."),
            ("UC20.2", "Yêu cầu chuyển tiếp địa chỉ mới", "extend", "kind = FORWARD_TO_NEW_ADDRESS; nhập forwardAddress."),
            ("UC20.3", "Yêu cầu giao lại", "extend", "kind = REDELIVERY."),
            ("UC20.4", "Yêu cầu hủy vận đơn", "extend", "kind = CANCEL_SHIPMENT.")]),
    ]),
    ("UC-07", "Bảng giá & mạng lưới", [
        ("UC21", "Quản lý bảng giá", ["Platform Admin"], "Quản lý RatePlan có phiên bản; chỉ một revision ACTIVE.", "RatePlan, RateRule", [
            ("UC21.1", "Xem danh sách bảng giá", "extend", "Xem các revision và trạng thái."),
            ("UC21.2", "Tạo bảng giá", "extend", "Tạo RatePlan DRAFT."),
            ("UC21.3", "Tạo phiên bản bảng giá", "extend", "Sao chép thành revision mới để chỉnh sửa."),
            ("UC21.4", "Cấu hình quy tắc giá", "extend", "RateRule theo mức dịch vụ × vùng giá."),
            ("UC21.5", "Cấu hình phụ phí & giới hạn", "extend", "COD, bảo hiểm, vùng xa/hải đảo, giới hạn kích thước/khối lượng."),
            ("UC21.6", "Kích hoạt bảng giá", "extend", "DRAFT → ACTIVE; revision cũ RETIRED."),
            ("UC21.7", "Ngừng áp dụng bảng giá", "extend", "ACTIVE → RETIRED.")]),
        ("UC22", "Quản lý mạng lưới", ["Platform Admin"], "Cấu hình mạng lưới vận hành.", "NetworkRegion, ServiceArea, Hub, HubLane, LaneSchedule, RouteTemplate", [
            ("UC22.1", "Quản lý vùng mạng lưới", "extend", "NetworkRegion."),
            ("UC22.2", "Quản lý khu vực phục vụ", "extend", "ServiceArea + hub chính."),
            ("UC22.3", "Quản lý hub/bưu cục", "extend", "Hub: loại, trạng thái, địa chỉ, hub cha, hiển thị công khai."),
            ("UC22.4", "Quản lý tuyến vận chuyển", "extend", "HubLane giữa 2 hub: phương thức, thời gian, khoảng cách, tải tối đa."),
            ("UC22.5", "Quản lý lịch chạy tuyến", "extend", "LaneSchedule: giờ khởi hành, ngày chạy, hiệu lực, tải trọng."),
            ("UC22.6", "Quản lý mẫu lộ trình", "extend", "RouteTemplate + chặng (RouteTemplateLeg) theo thứ tự.")]),
    ]),
    ("UC-08", "Nhân sự vận hành & ca làm việc", [
        ("UC23", "Quản lý nhân sự vận hành", ["Hub Manager", "Operations Manager"], "Quản lý hồ sơ WorkforceMember và gán hub.", "WorkforceMember, HubMembership", [
            ("UC23.1", "Tạo hồ sơ nhân sự", "extend", "Liên kết tài khoản nhân viên, mã nhân viên, loại nhân sự."),
            ("UC23.2", "Gán nhân sự vào hub", "extend", "Tạo HubMembership (hub chính/phụ)."),
            ("UC23.3", "Gỡ nhân sự khỏi hub", "extend", "Kết thúc HubMembership (unassignedAt)."),
            ("UC23.4", "Cập nhật trạng thái nhân sự", "extend", "ACTIVE / ON_LEAVE / INACTIVE.")]),
        ("UC24", "Xem nhân sự vận hành", ["Hub Dispatch"], "Xem nhân sự tại hub và trạng thái sẵn sàng/tải việc hiện tại.", "WorkforceMember, WorkforceAvailability", []),
        ("UC25", "Quản lý ca làm việc", ["Hub Manager", "Operations Manager"], "Lập và điều chỉnh ca tại hub.", "WorkShift, WorkforceShiftAssignment", [
            ("UC25.1", "Tạo ca làm việc", "extend", "Mã, tên, giờ bắt đầu/kết thúc tại hub."),
            ("UC25.2", "Phân công nhân sự vào ca", "extend", "Tạo WorkforceShiftAssignment (ASSIGNED)."),
            ("UC25.3", "Hủy ca làm việc", "extend", "Ghi cancelReason, cancelledBy."),
            ("UC25.4", "Đánh dấu vắng mặt", "extend", "Assignment → ABSENT.")]),
        ("UC26", "Xem ca làm việc", ["Operational Staff"], "Xem lịch ca được phân công.", "WorkShift, WorkforceShiftAssignment", []),
        ("UC27", "Check-in / Check-out ca", ["Operational Staff"], "Ghi nhận checkedInAt/checkedOutAt.", "WorkforceShiftAssignment", []),
        ("UC28", "Cập nhật trạng thái sẵn sàng", ["Operational Staff"], "AVAILABLE / BUSY / OFFLINE.", "WorkforceAvailability", []),
    ]),
    ("UC-09", "Điều phối & thực hiện vận hành", [
        ("UC29", "Xem tổng quan vận hành", ["Back-office User"], "Bảng điều hành: sản lượng, vận đơn trễ/tạm giữ, công việc tồn, nhân sự.", "Shipment, OperationalAssignment", []),
        ("UC30", "Theo dõi vận đơn vận hành", ["Hub Dispatch", "Operations Manager"], "Tra cứu vận đơn nội bộ (kiện, custody, định tuyến, assignment).", "Shipment, Parcel, OperationalAssignment", [
            ("UC30.1", "Tạm giữ vận đơn", "extend", "isOnHold = true, ghi lý do, hub."),
            ("UC30.2", "Giải phóng vận đơn", "extend", "Bỏ tạm giữ.")]),
        ("UC31", "Điều phối công việc", ["Hub Dispatch"], "Phân công OperationalAssignment cho nhân sự phù hợp (requiredKind).", "OperationalAssignment, WorkforceMember", [
            ("=Xem nhân sự vận hành", "include"),
            ("UC31.1", "Phân công công việc", "extend", "PENDING → ASSIGNED."),
            ("UC31.2", "Phân công lại công việc", "extend", "Đổi người thực hiện."),
            ("UC31.3", "Hủy công việc", "extend", "→ CANCELLED kèm ghi chú.")]),
        ("UC32", "Xem công việc được giao", ["Operational Staff"], "Danh sách assignment của tôi theo ưu tiên.", "OperationalAssignment", []),
        ("UC33", "Thực hiện công việc", ["Operational Staff"], "Thực hiện assignment (lấy hàng, nhập/xuất hub, phân loại, trung chuyển, giao, hoàn).", "OperationalAssignment, Parcel", [
            ("UC33.1", "Nhận công việc", "extend", "ASSIGNED → ACCEPTED."),
            ("UC33.2", "Bắt đầu công việc", "extend", "→ IN_PROGRESS."),
            ("UC33.3", "Hoàn thành công việc", "extend", "→ COMPLETED."),
            ("UC33.4", "Báo không hoàn thành", "extend", "→ FAILED kèm resolutionNote."),
            ("UC34", "Quét kiện hàng", "include", "Quét mã kiện để chuyển giao custody / cập nhật vị trí, sinh ShipmentEvent.")]),
        ("UC36", "Bàn giao hàng trung chuyển", ["Line-haul Driver"], "Nhận hàng tại hub đi và bàn giao tại hub đến theo chuyến (LINE_HAUL).", "OperationalAssignment, LaneCapacityReservation", [
            ("=Quét kiện hàng", "include")]),
        ("UC35", "Ghi nhận kết quả giao hàng", ["Courier"], "Tạo DeliveryAttempt cho từng kiện.", "DeliveryAttempt, Parcel", [
            ("UC35.1", "Xác nhận giao thành công", "extend", "outcome = DELIVERED; bằng chứng: chữ ký/ảnh/OTP, người nhận, GPS."),
            ("UC35.2", "Ghi nhận giao thất bại", "extend", "outcome = FAILED; chọn DeliveryFailureReason.")]),
        ("UC37", "Điều chỉnh khối lượng kiện", ["Warehouse Operator"], "Cân lại kiện tại hub; tạo ShipmentWeightAdjustment, vận đơn chờ duyệt.", "ShipmentWeightAdjustment, Parcel", []),
        ("UC38", "Duyệt điều chỉnh khối lượng", ["Hub Manager"], "Duyệt/ghi chú điều chỉnh khối lượng; cập nhật hasPendingWeightReview.", "ShipmentWeightAdjustment", []),
    ]),
    ("UC-10", "Ngoại lệ & sự cố", [
        ("UC39", "Xem ngoại lệ vận đơn", ["Hub Dispatch"], "Danh sách vận đơn có ngoại lệ: giao thất bại, tạm giữ, chờ duyệt khối lượng, yêu cầu ngoại lệ.", "Shipment, ShipmentExceptionRequest", []),
        ("UC40", "Xử lý yêu cầu ngoại lệ", ["Hub Dispatch"], "Duyệt và hoàn tất ShipmentExceptionRequest.", "ShipmentExceptionRequest", [
            ("UC40.1", "Phê duyệt yêu cầu", "extend", "PENDING → APPROVED."),
            ("UC40.2", "Từ chối yêu cầu", "extend", "PENDING → REJECTED kèm reviewNote."),
            ("UC40.3", "Hoàn tất yêu cầu", "extend", "APPROVED → COMPLETED."),
            ("UC40.4", "Định tuyến lại vận đơn", "extend", "Khi routingRequired: tính lộ trình mới, giữ chỗ tải mới.")]),
        ("UC41", "Báo cáo sự cố", ["Operational Staff"], "Tạo ShipmentCase (hư hỏng, thất lạc, kiện không xác định...) tại hub.", "ShipmentCase", []),
        ("UC42", "Quản lý hồ sơ sự cố", ["Operations Manager"], "Theo dõi và xử lý hồ sơ sự cố.", "ShipmentCase", [
            ("UC42.1", "Phân công xử lý sự cố", "extend", "Gán assignedToUserId."),
            ("UC42.2", "Cập nhật tiến độ xử lý", "extend", "OPEN → IN_PROGRESS."),
            ("UC42.3", "Giải quyết sự cố", "extend", "→ RESOLVED kèm resolution."),
            ("UC42.4", "Đóng hồ sơ sự cố", "extend", "→ CLOSED.")]),
    ]),
]

TOP = []  # (uc elem, actors, group code)
SUBS = {}  # top name -> [(sub elem, kind)]


def mkuc(code, name, doc, level):
    return p.add("UseCase", name, parent=system, doc=doc, tag="UC", UserID=code, key=name)


for gcode, gname, ucs in GROUPS:
    for code, name, actors, desc, data, subs in ucs:
        doc = (f"Mã: {code}\nNhóm: {gname}\nTác nhân: {', '.join(actors)}\nMô tả: {desc}\n"
               f"Tiền điều kiện: {'Không' if set(actors) <= {'Public User', 'Guest'} else ('Chưa đăng nhập' if code == 'UC01' else 'Đã đăng nhập, có quyền tương ứng')}\n"
               f"Dữ liệu (domain): {data}")
        if name not in p.by_name:
            mkuc(code, name, doc, "User")
        else:
            p.get(name).doc = doc
        uc = p.get(name)
        TOP.append((uc, actors, gcode))
        for a in actors:
            p.rel("Association", a, uc, src_mult="", dst_mult="")
        SUBS[name] = []
        for sub in subs:
            if sub[0].startswith("="):
                target = p.get(sub[0][1:])
                kind = sub[1]
            else:
                scode, sname, kind, sdesc = sub
                target = p.by_name.get(sname) or mkuc(scode, sname, f"Mã: {scode}\nThuộc: {code} {name}\nMô tả: {sdesc}", "Subfunction")
            SUBS[name].append((target, kind))
            if kind == "include":  # base --include--> included
                p.rel("Include", uc, target)
            else:  # extension --extend--> base
                p.rel("Extend", target, uc)

# --------------------------------------------------------------------------
# diagrams
# --------------------------------------------------------------------------


def no_generalization(d):
    d.only_rels = {r.id for r in p.rels if r.kind != "Generalization"}


# UC-00 actor hierarchy -----------------------------------------------------
d = p.diagram("UseCaseDiagram", "UC-00 Phân cấp tác nhân", "Tổng quát hóa tác nhân theo 3 frame: cổng khách hàng | cổng quản trị | ứng dụng vận hành. Tác nhân in nghiêng là trừu tượng.")
d.only_rels = {r.id for r in p.rels if r.kind == "Generalization"}
levels = [  # columns: cổng khách hàng | cổng quản trị | ứng dụng vận hành
    [("Public User", 170), ("Back-office User", 760), ("Operational Staff", 1360)],
    [("Guest", 60), ("Customer", 280), ("Platform Admin", 520), ("Hub Dispatch", 760), ("Operations Manager", 1000),
     ("Courier", 1180), ("Line-haul Driver", 1360), ("Warehouse Operator", 1540)],
    [("Merchant", 280), ("Hub Manager", 760)],
]
for i, row in enumerate(levels):
    for name, x in row:
        d.place(name, x, 60 + i * 170)

# UC-01 overview ------------------------------------------------------------
d = p.diagram("UseCaseDiagram", "UC-01 Biểu đồ use case tổng quát",
              "Tất cả tác nhân và use case mức người dùng. Quan hệ tổng quát hóa tác nhân xem UC-00; phân rã chi tiết xem UC-02..UC-10.")
d.only_rels = {r.id for r in p.rels if r.kind == "Association"}
ROW = 64
BX, BW = 200, 1100
LX, LUC, MID, RUC, RX = 40, BX + 50, BX + BW / 2 - 115, BX + BW - 290, BX + BW + 90
by_actor = {}
for uc, actors, _g in TOP:
    by_actor.setdefault(actors[0], []).append(uc)
SHARED_ACCOUNT = [p.get(n) for n in ("Đăng nhập", "Đăng xuất", "Quản lý hồ sơ cá nhân")]


def band(actor, ucs, x_uc, x_actor, y, sign, actor_at="center", gap=None):
    """Stack an actor's use cases in one column (zig-zag) and put the actor beside them.
    gap=(row, n): leave n empty rows starting at `row` so lines to the middle column pass freely."""
    rows = list(range(len(ucs) + (gap[1] if gap else 0)))
    if gap:
        rows = [r for r in rows if not gap[0] <= r < gap[0] + gap[1]]
    for i, (uc, r) in enumerate(zip(ucs, rows)):
        d.place(uc, x_uc + (i % 2) * 70 * sign, y + r * ROW, 230, 46)
    h = max((len(ucs) + (gap[1] if gap else 0)) * ROW, 120)
    d.place(actor, x_actor, y + (h / 2 - 50 if actor_at == "center" else 0))
    return y + h + 18


sysshape = d.place("sys", BX, 50, BW, 100)
# -- upper part: merchant/public (left) and hub roles (right); UCs shared by Merchant & Hub Dispatch in the middle
yl = band("Merchant", [u for u in by_actor["Merchant"] if len(next(a for x, a, _ in TOP if x is u)) == 1], LUC, LX, 90, 1, gap=(3, 2))
for i, name in enumerate(("Tạo đơn hàng", "Yêu cầu xử lý ngoại lệ vận đơn")):
    d.place(name, MID, 90 + (3 + i) * ROW, 230, 46)
yl = band("Public User", by_actor["Public User"], LUC, LX, yl, 1)
yl = band("Guest", by_actor["Guest"], LUC, LX, yl, 1)
customer_own = [u for u in by_actor["Customer"] if u not in SHARED_ACCOUNT]
yl = band("Customer", customer_own, LUC, LX, yl, 1, actor_at="none")  # actor re-placed in the corridor
yr = band("Hub Dispatch", [u for u in by_actor["Hub Dispatch"] if u.name not in ("Tạo đơn hàng", "Yêu cầu xử lý ngoại lệ vận đơn")], RUC, RX, 90, -1, gap=(3, 2))
yr = band("Hub Manager", by_actor["Hub Manager"], RUC, RX, yr, -1)
yr = band("Operations Manager", by_actor["Operations Manager"], RUC, RX, yr, -1)
# -- corridor: account use cases in the middle, the three "signed-in" actors level with them
yc = max(yl, yr) + 10
corridor = [p.get("Xem tổng quan vận hành")] + SHARED_ACCOUNT
for i, uc in enumerate(corridor):
    d.place(uc, MID, yc + i * ROW, 230, 46)
d.by_elem.pop(p.get("Customer").id)
d.shapes = [sh for sh in d.shapes if sh.elem is not p.get("Customer")]
d.place("Customer", LX, yc + 1.5 * ROW - 17)
d.place("Back-office User", RX, yc + 0.5 * ROW - 17)
d.place("Operational Staff", RX, yc + 2.6 * ROW - 17)
y = yc + len(corridor) * ROW + 30
# -- lower part
yl = band("Platform Admin", by_actor["Platform Admin"], LUC, LX, y, 1)
yr = y
ops_own = [u for u in by_actor["Operational Staff"] if u not in SHARED_ACCOUNT]
for i, uc in enumerate(ops_own):
    d.place(uc, RUC - (i % 2) * 70, yr + i * ROW, 230, 46)
yr += len(ops_own) * ROW + 18
for actor in ("Courier", "Line-haul Driver", "Warehouse Operator"):
    yr = band(actor, by_actor[actor], RUC, RX, yr, -1)
sysshape.h = max(yl, yr) - 40

# UC-02..UC-10 decomposition ------------------------------------------------
for gcode, gname, ucs in GROUPS:
    d = p.diagram("UseCaseDiagram", f"{gcode} Phân rã: {gname}",
                  "Use case gốc liên kết tác nhân; «include» = bước bắt buộc, «extend» = chức năng tùy chọn mở rộng use case gốc.")
    no_generalization(d)
    sysshape = d.place("sys", 200, 40, 900, 100)
    y = 100
    placed_subs = set()
    actor_y = {}
    for code, name, actors, *_ in ucs:
        subs = [s for s in SUBS[name] if s[0].id not in placed_subs and s[0].id not in d.by_elem]
        band = max(1, len(subs)) * 62
        base_y = y + band / 2 - 23
        d.place(name, 290, base_y, 230, 46)
        for i, (sub, kind) in enumerate(subs):
            d.place(sub, 680 + (i % 2) * 60, y + i * 62, 230, 46)
            placed_subs.add(sub.id)
        for a in actors:
            actor_y.setdefault(a, []).append(base_y)
        y += band + 40
    sysshape.h = y - 20
    # actors on the left, next to the use cases they start
    used = []
    for a, ys in actor_y.items():
        ay = sum(ys) / len(ys) - 17
        while any(abs(ay - u) < 110 for u in used):
            ay += 110
        used.append(ay)
        d.place(a, 60, ay)

if __name__ == "__main__":
    (OUT / "pavex-usecase-model.xml").write_bytes(p.to_xml())
    for dg in p.diagrams:
        slug = dg.name.split(" ")[0].lower()
        (OUT / "preview" / f"{slug}.svg").write_text(dg.to_svg(), encoding="utf-8")
    lines = ["# Danh mục use case PAVEX", "", "> Sinh tự động bởi `generator/build_usecase.py` — đừng sửa tay.", ""]
    for gcode, gname, ucs in GROUPS:
        lines += [f"## {gcode} · {gname}", "", "| Mã | Use case | Tác nhân | Mô tả | Use case con |", "|---|---|---|---|---|"]
        for code, name, actors, desc, data, subs in ucs:
            sub_txt = "<br>".join(f"«{k}» {t.attrs.get('UserID', '')} {t.name}" for t, k in SUBS[name]) or "—"
            lines.append(f"| {code} | {name} | {', '.join(actors)} | {desc} *(Dữ liệu: {data})* | {sub_txt} |")
        lines.append("")
    lines += ["## Tác nhân", "", "| Tác nhân | Trừu tượng | Mô tả |", "|---|---|---|"]
    lines += [f"| {n} | {'✔' if a else ''} | {d} |" for n, a, d in ACTORS]
    lines += ["", "Tổng quát hóa (cha → con): " + "; ".join(f"{g} → {s_}" for g, s_ in GENERALIZATIONS), ""]
    (OUT / "usecase-catalog.md").write_text("\n".join(lines), encoding="utf-8")
    n_uc = sum(1 for e in p.by_name.values() if e.kind == "UseCase")
    print(f"usecase: {len(ACTORS)} actors, {n_uc} use cases ({len(TOP)} top-level), {len(p.rels)} relationships, {len(p.diagrams)} diagrams")
