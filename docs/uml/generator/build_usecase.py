"""PAVEX use case model -> Visual Paradigm XML.

Structure
---------
One single use case diagram ("PAVEX Use Case Diagram"):
* 10 actors = backend roles, plus one abstract actor per frame (client portal,
  management portal, operations app) where roles of that frame share use cases;
  actor generalizations are routed in lanes outside the actor columns.
* The 41 user-goal use cases inside the "PAVEX Logistics Platform" boundary.
* Sub-functions shared by several use cases become their own use case
  («include»/«extend»); the others are listed in the base use case documentation.
All use cases are owned by the System (subject) element; actors live in an
"Actors" package. Generalization: From = general actor, To = specialized actor.
"""

from __future__ import annotations

import pathlib

from vpxml import Project

OUT = pathlib.Path(__file__).resolve().parent.parent

p = Project("PAVEX Use Case Model", "PAVEXUC")

# --------------------------------------------------------------------------
# actors
# --------------------------------------------------------------------------
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
    p.add("Actor", name, doc=doc, tag="ACT", Abstract="true" if abstract else "false")

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
    ("Nhóm 1", "Xác thực & tài khoản cá nhân", [
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
    ("Nhóm 2", "Quản trị người dùng & phân quyền", [
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
    ("Nhóm 3", "Cửa hàng (Merchant)", [
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
    ("Nhóm 4", "Tra cứu công khai", [
        ("UC14", "Tra cứu vận đơn", ["Public User"], "Tra cứu trạng thái theo mã vận đơn (trackingCode).", "Shipment, ShipmentEvent", [
            ("UC14.1", "Xem hành trình vận đơn", "include", "Xem timeline ShipmentEvent (trạng thái, địa điểm, thời gian).")]),
        ("UC16", "Tra cứu bưu cục", ["Public User"], "Tìm hub/bưu cục công khai (publicVisible) theo tỉnh/phường.", "Hub", []),
        ("UC15", "Tra cứu giá cước", ["Public User"], "Nhập điểm đi/đến, kiện hàng (khối lượng, kích thước), COD, giá trị khai báo → các phương án giá theo mức dịch vụ; báo giá có hạn (quoteTtlMinutes).", "QuoteRequest, QuoteParcel, QuoteOption, RatePlan", []),
    ]),
    ("Nhóm 5", "Đơn hàng & vận đơn", [
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
    ("Nhóm 6", "Bảng giá & mạng lưới", [
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
    ("Nhóm 7", "Nhân sự vận hành & ca làm việc", [
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
    ("Nhóm 8", "Điều phối & thực hiện vận hành", [
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
    ("Nhóm 9", "Ngoại lệ & sự cố", [
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
SUBS = {}  # top name -> [(code, name, kind, elem or None)]

# A sub-function becomes its own use case only when several use cases share it;
# the others are listed in the documentation of their base use case.
REFS = {}
for _g, _n, _ucs in GROUPS:
    for *_x, _subs in _ucs:
        for sub in _subs:
            key = sub[0][1:] if sub[0].startswith("=") else sub[1]
            REFS[key] = REFS.get(key, 0) + 1


def mkuc(code, name, doc):
    return p.add("UseCase", name, parent=system, doc=doc, tag="UC", UserID=code, key=name)


SPEC = {}
for gcode, gname, ucs in GROUPS:
    for code, name, actors, desc, data, subs in ucs:
        SPEC[name] = (code, gname, actors, desc, data)
        if name not in p.by_name:
            mkuc(code, name, "")
        uc = p.get(name)
        TOP.append((uc, actors, gcode))
        for a in actors:
            p.rel("Association", a, uc, src_mult="", dst_mult="")
        SUBS[name] = []
        for sub in subs:
            if sub[0].startswith("="):
                target = p.get(sub[0][1:])
                scode, sname, kind = target.attrs.get("UserID", ""), target.name, sub[1]
            else:
                scode, sname, kind, sdesc = sub
                target = None
                if REFS[sname] > 1:
                    target = p.by_name.get(sname) or mkuc(scode, sname, f"Mã: {scode}\nMô tả: {sdesc}")
            SUBS[name].append((scode, sname, kind, target))
            if target is not None:
                if kind == "include":  # base --include--> included
                    p.rel("Include", uc, target)
                else:  # extension --extend--> base
                    p.rel("Extend", target, uc)

for name, (code, gname, actors, desc, data) in SPEC.items():
    subs = "".join(f"\n  - «{k}» {c} {n}" for c, n, k, _t in SUBS[name])
    pre = "Không" if set(actors) <= {"Public User", "Guest"} else ("Chưa đăng nhập" if code == "UC01" else "Đã đăng nhập, có quyền tương ứng")
    p.get(name).doc = (f"Mã: {code}\nNhóm: {gname}\nTác nhân: {', '.join(actors)}\nMô tả: {desc}\n"
                       f"Tiền điều kiện: {pre}\nDữ liệu (domain): {data}" + (f"\nChức năng con:{subs}" if subs else ""))

# --------------------------------------------------------------------------
# the single use case diagram
# --------------------------------------------------------------------------
d = p.diagram("UseCaseDiagram", "PAVEX Use Case Diagram",
              "Toàn bộ tác nhân (kế thừa theo frame), use case và quan hệ include/extend trên một sơ đồ. "
              "Chức năng con của từng use case xem phần Documentation.")
ROW = 64
BX, BW = 290, 1100
LX, LUC, MID, RUC, RX = 110, BX + 30, BX + BW / 2 - 115, BX + BW - 260, BX + BW + 90
by_actor = {}
for uc, actors, _g in TOP:
    by_actor.setdefault(actors[0], []).append(uc)
SHARED_ACCOUNT = [p.get(n) for n in ("Đăng nhập", "Đăng xuất", "Quản lý hồ sơ cá nhân")]


def arc(actor_shape, ucs, ys, sign, dx_min=300):
    """Place use cases on an arc around the actor: every association line then runs
    radially and never cuts through a neighbouring ellipse."""
    acx, acy = actor_shape.cx, actor_shape.cy - 10
    max_dy = max(abs(y_ + 23 - acy) for y_ in ys)
    R = (dx_min ** 2 + max_dy ** 2) ** 0.5
    for uc, y_ in zip(ucs, ys):
        dx = (R ** 2 - (y_ + 23 - acy) ** 2) ** 0.5
        d.place(uc, acx + sign * dx - 115, y_, 230, 46)


def band(actor, ucs, x_uc, x_actor, y, sign, actor_at="center", gap=None):
    """One actor with its use cases stacked beside it.
    gap=(row, n): leave n empty rows starting at `row` so lines to the middle column pass freely."""
    rows = list(range(len(ucs) + (gap[1] if gap else 0)))
    if gap:
        rows = [r for r in rows if not gap[0] <= r < gap[0] + gap[1]]
    n = len(ucs) + (gap[1] if gap else 0)
    h = max(n * ROW, 120)
    ys = [y + r * ROW for r in rows[:len(ucs)]]
    if actor_at == "center":
        arc(d.place(actor, x_actor, y + h / 2 - 50), ucs, ys, sign)
    else:
        for uc, y_ in zip(ucs, ys):
            d.place(uc, x_uc, y_, 230, 46)
    return y + h + 18


def mid(name, y_):
    """Place a use case in the middle column near y_, below anything already there."""
    taken = [s.y for s in d.shapes if s.elem.kind == "UseCase" and s.x == MID]
    while any(abs(y_ - t) < 60 for t in taken):
        y_ += 62
    d.place(name, MID, y_, 230, 46)


sysshape = d.place("sys", BX, 50, BW, 100)
ORDER_SHARED = ("Tạo đơn hàng", "Yêu cầu xử lý ngoại lệ vận đơn")
# upper part --------------------------------------------------------------
yl = band("Merchant", [u for u in by_actor["Merchant"] if u.name not in ORDER_SHARED], LUC, LX, 90, 1, gap=(3, 2))
for i, name in enumerate(ORDER_SHARED):
    d.place(name, MID, 90 + (3 + i) * ROW, 230, 46)
yl = band("Public User", sorted(by_actor["Public User"], key=lambda u: u.name != "Tra cứu giá cước"), LUC, LX, yl, 1)
yl = band("Guest", by_actor["Guest"], LUC, LX, yl, 1)
customer_own = [u for u in by_actor["Customer"] if u not in SHARED_ACCOUNT]
customer_ys = [yl + i * ROW for i in range(len(customer_own))]
yl += len(customer_own) * ROW + 18
yr = band("Hub Dispatch", [u for u in by_actor["Hub Dispatch"] if u.name not in ORDER_SHARED], RUC, RX, 90, -1, gap=(3, 2))
yr = band("Hub Manager", by_actor["Hub Manager"], RUC, RX, yr, -1)
yr = band("Operations Manager", by_actor["Operations Manager"], RUC, RX, yr, -1)
mid("Xem hành trình vận đơn", (d.by_elem[p.get("Theo dõi vận đơn").id].y + d.by_elem[p.get("Tra cứu vận đơn").id].y) / 2)
# corridor: account use cases in the middle, the "signed-in" actors level with them
yc = max(yl, yr) + 10
for i, uc in enumerate([p.get("Xem tổng quan vận hành")] + SHARED_ACCOUNT):
    d.place(uc, MID, yc + i * ROW, 230, 46)
d.place("Customer", LX, yc + 0.6 * ROW - 17)
arc(d.by_elem[p.get("Customer").id], customer_own, customer_ys, 1)
d.place("Operational Staff", LX, yc + 2.6 * ROW - 17)
d.place("Back-office User", RX, yc + 1.5 * ROW - 17)
y = yc + 4 * ROW + 30
# lower part --------------------------------------------------------------
admin = by_actor["Platform Admin"]
admin.insert(1, p.get("Gán vai trò cho người dùng"))  # between the two use cases that extend/include it
yr = band("Platform Admin", admin, RUC, RX, y, -1)
ops_own = by_actor["Operational Staff"]
arc(d.by_elem[p.get("Operational Staff").id], ops_own, [y + i * ROW for i in range(len(ops_own))], 1)
yl = y + len(ops_own) * ROW + 18
for actor in ("Courier", "Line-haul Driver", "Warehouse Operator"):
    yl = band(actor, by_actor[actor], LUC, LX, yl, 1)
mid("Quét kiện hàng", (d.by_elem[p.get("Thực hiện công việc").id].y + d.by_elem[p.get("Bàn giao hàng trung chuyển").id].y) / 2)
sysshape.h = max(yl, yr) - 40

# actor generalizations run in vertical lanes outside the actor columns
d.lanes = {}
lane_no = {"Customer": 0, "Operational Staff": 0, "Public User": 1, "Back-office User": 0, "Hub Dispatch": 1}
for r in p.rels:
    if r.kind == "Generalization":
        s_ = d.by_elem[r.src.id]
        k = lane_no[r.src.name]
        d.lanes[r.id] = s_.x - 60 - 14 * k if s_.x < BX else s_.x + s_.w + 60 + 14 * k

if __name__ == "__main__":
    (OUT / "pavex-usecase-model.xml").write_bytes(p.to_xml())
    (OUT / "preview" / "usecase-diagram.svg").write_text(d.to_svg(), encoding="utf-8")
    lines = ["# Danh mục use case PAVEX", "", "> Sinh tự động bởi `generator/build_usecase.py` — đừng sửa tay.", ""]
    for gcode, gname, ucs in GROUPS:
        lines += [f"## {gcode} · {gname}", "", "| Mã | Use case | Tác nhân | Mô tả | Chức năng con |", "|---|---|---|---|---|"]
        for code, name, actors, desc, data, subs in ucs:
            sub_txt = "<br>".join(f"«{k}» {c} {n}" for c, n, k, _t in SUBS[name]) or "—"
            lines.append(f"| {code} | {name} | {', '.join(actors)} | {desc} *(Dữ liệu: {data})* | {sub_txt} |")
        lines.append("")
    lines += ["## Tác nhân", "", "| Tác nhân | Trừu tượng | Mô tả |", "|---|---|---|"]
    lines += [f"| {n} | {'✔' if a else ''} | {d} |" for n, a, d in ACTORS]
    lines += ["", "Tổng quát hóa (cha → con): " + "; ".join(f"{g} → {s_}" for g, s_ in GENERALIZATIONS), ""]
    (OUT / "usecase-catalog.md").write_text("\n".join(lines), encoding="utf-8")
    n_uc = sum(1 for e in p.by_name.values() if e.kind == "UseCase")
    print(f"usecase: {len(ACTORS)} actors, {n_uc} use cases ({len(TOP)} top-level), {len(p.rels)} relationships, {len(p.diagrams)} diagrams")
