"""Sequence diagrams SD-UCxx: one per main use case, following the Entity-Control-Boundary model.

Naming
* boundary «boundary»: <Screen>Page - the screen the actor works on;
* control «control»: <Area>Controller - one per functional area, matching the REST API /api/v1;
* entity «entity»: the class of the class diagram; every message sent to an entity is one of its
  operations (class-scope finders such as findById are marked {static} in the class diagram);
* external systems «system» receive asynchronous signals (open arrowhead).
Fragments: break = an alternative flow that ends the use case, alt = alternatives that go on,
opt = optional step, loop = repetition, ref = interaction defined in another sequence diagram.
"""

from __future__ import annotations

from contextlib import contextmanager


class SD:
    def __init__(self, *parts):
        self.parts = list(parts)
        self.ix = {name: i for i, (_k, name) in enumerate(parts)}
        self.msgs, self.frags, self._stack = [], [], []

    def call(self, a, b, text):
        self.msgs.append((self.ix[a], self.ix[b], text))

    def ret(self, a, b, text):
        self.msgs.append((self.ix[a], self.ix[b], text, "ret"))

    def self_(self, a, text):
        self.msgs.append((self.ix[a], self.ix[a], text))

    @contextmanager
    def frag(self, op, cond):
        fr = dict(a=len(self.msgs), op=op, cond=cond, elses=[])
        self._stack.append(fr)
        yield
        self._stack.pop()
        fr["b"] = len(self.msgs) - 1
        assert fr["b"] >= fr["a"], f"empty fragment {op} {cond}"
        self.frags.append(fr)

    def else_(self, cond):
        self._stack[-1]["elses"].append((len(self.msgs), cond))

    def data(self):
        return dict(parts=self.parts, msgs=self.msgs, frags=self.frags)


A, B, C, E, X = "actor", "boundary", "control", "entity", "system"
SEQ = {}

# ---------------------------------------------------------------------------- UC01 Đăng nhập
s = SD((A, "Người dùng"), (B, "LoginPage"), (C, "AuthController"), (E, "UserAccount"), (E, "Role"))
s.call("Người dùng", "LoginPage", "Nhập email, mật khẩu, nhấn Đăng nhập")
s.self_("LoginPage", "validateForm()")
with s.frag("break", "[thiếu dữ liệu / email sai định dạng]"):
    s.call("LoginPage", "Người dùng", "Báo lỗi tại trường nhập")
s.call("LoginPage", "AuthController", "login(email, password)")
s.call("AuthController", "UserAccount", "findByEmail(email)")
s.ret("UserAccount", "AuthController", "account")
s.call("AuthController", "UserAccount", "matchesPassword(password)")
s.ret("UserAccount", "AuthController", "true / false")
with s.frag("break", "[không có tài khoản / sai mật khẩu]"):
    s.ret("AuthController", "LoginPage", "401 Unauthorized")
    s.call("LoginPage", "Người dùng", "Hiển thị “Email hoặc mật khẩu không đúng”")
with s.frag("break", "[status = SUSPENDED / DISABLED]"):
    s.ret("AuthController", "LoginPage", "403 – tài khoản bị khóa")
    s.call("LoginPage", "Người dùng", "Hiển thị “Tài khoản bị khóa”")
s.call("AuthController", "Role", "findByAccount(accountId)")
s.ret("Role", "AuthController", "roles, permissions")
s.self_("AuthController", "issueTokens(account, roles)")
s.ret("AuthController", "LoginPage", "accessToken, refreshToken, currentUser")
s.call("LoginPage", "Người dùng", "Chuyển đến trang chính theo vai trò")
SEQ["UC01"] = s.data()

# ---------------------------------------------------------------------------- UC03 Đăng ký tài khoản
s = SD((A, "Guest"), (B, "RegisterPage"), (C, "AuthController"), (E, "UserAccount"), (X, "EmailService"))
s.call("Guest", "RegisterPage", "Nhập họ tên, email, mật khẩu, nhấn Đăng ký")
s.self_("RegisterPage", "validateForm()")
with s.frag("break", "[thiếu thông tin / dữ liệu không hợp lệ]"):
    s.call("RegisterPage", "Guest", "Báo lỗi tại từng trường")
s.call("RegisterPage", "AuthController", "register(fullName, email, password)")
s.call("AuthController", "UserAccount", "existsByEmail(email)")
s.ret("UserAccount", "AuthController", "exists")
with s.frag("break", "[email đã tồn tại]"):
    s.ret("AuthController", "RegisterPage", "409 Conflict")
    s.call("RegisterPage", "Guest", "Báo email đã tồn tại, gợi ý Đăng nhập / Quên mật khẩu")
s.call("AuthController", "UserAccount", "register(email, password, fullName)")
s.ret("UserAccount", "AuthController", "account (profileStatus = INCOMPLETE)")
s.call("AuthController", "EmailService", "sendVerificationEmail(email, token)")
s.ret("AuthController", "RegisterPage", "201 Created")
s.call("RegisterPage", "Guest", "Thông báo đăng ký thành công, yêu cầu xác minh email")
s.call("Guest", "RegisterPage", "Mở liên kết xác minh trong email")
s.call("RegisterPage", "AuthController", "verifyEmail(token)")
s.call("AuthController", "UserAccount", "findByVerificationToken(token)")
s.ret("UserAccount", "AuthController", "account")
with s.frag("break", "[liên kết hết hạn]"):
    s.call("AuthController", "EmailService", "sendVerificationEmail(email, newToken)")
    s.ret("AuthController", "RegisterPage", "410 Gone")
    s.call("RegisterPage", "Guest", "Thông báo đã gửi lại email xác minh")
s.call("AuthController", "UserAccount", "verifyEmail()")
s.ret("AuthController", "RegisterPage", "200 OK")
s.call("RegisterPage", "Guest", "Chuyển đến trang Đăng nhập")
SEQ["UC03"] = s.data()

# ---------------------------------------------------------------------------- UC06 Quản lý người dùng
s = SD((A, "Platform Admin"), (B, "UsersPage"), (C, "UserController"), (E, "UserAccount"))
s.call("Platform Admin", "UsersPage", "Chọn menu Người dùng")
s.call("UsersPage", "UserController", "listUsers(criteria, page)")
s.self_("UserController", "checkPermission(identity.users.read)")
with s.frag("break", "[không đủ quyền]"):
    s.ret("UserController", "UsersPage", "403 Forbidden")
    s.call("UsersPage", "Platform Admin", "Thông báo chưa được cấp quyền")
s.call("UserController", "UserAccount", "search(criteria, page)")
s.ret("UserAccount", "UserController", "accounts, totalCount")
s.ret("UserController", "UsersPage", "Page<UserAccount>")
s.call("UsersPage", "Platform Admin", "Hiển thị chỉ số và danh sách tài khoản")
s.call("Platform Admin", "UsersPage", "Nhập từ khóa, chọn bộ lọc")
s.call("UsersPage", "UserController", "listUsers(criteria, page)")
s.call("UserController", "UserAccount", "search(criteria, page)")
s.ret("UserAccount", "UserController", "accounts, totalCount")
s.ret("UserController", "UsersPage", "Page<UserAccount>")
s.call("UsersPage", "Platform Admin", "Hiển thị danh sách đã lọc (hoặc “Không tìm thấy tài khoản”)")
s.call("Platform Admin", "UsersPage", "Chọn một tài khoản")
s.call("UsersPage", "UserController", "getUser(userId)")
s.call("UserController", "UserAccount", "findById(userId)")
s.ret("UserAccount", "UserController", "account")
s.ret("UserController", "UsersPage", "account, profile, roles")
s.call("UsersPage", "Platform Admin", "Hiển thị chi tiết tài khoản")
s.call("Platform Admin", "UsersPage", "Chọn trạng thái mới, nhấn Lưu")
s.call("UsersPage", "UserController", "updateStatus(userId, status, version)")
s.call("UserController", "UserAccount", "findById(userId)")
s.ret("UserAccount", "UserController", "account")
with s.frag("break", "[version đã thay đổi]"):
    s.ret("UserController", "UsersPage", "409 Conflict")
    s.call("UsersPage", "Platform Admin", "Báo xung đột, tải lại dữ liệu mới nhất")
with s.frag("break", "[tài khoản của chính admin]"):
    s.ret("UserController", "UsersPage", "422 – không được tự khóa")
    s.call("UsersPage", "Platform Admin", "Báo không được tự khóa tài khoản")
s.call("UserController", "UserAccount", "changeStatus(status)")
with s.frag("opt", "[status = SUSPENDED / DISABLED]"):
    s.call("UserController", "UserAccount", "revokeSessions()")
s.ret("UserController", "UsersPage", "200 OK")
s.call("UsersPage", "Platform Admin", "Thông báo “Cập nhật thành công”")
SEQ["UC06"] = s.data()

# ---------------------------------------------------------------------------- UC08 Quản lý vai trò & quyền
s = SD((A, "Platform Admin"), (B, "RolesPage"), (C, "RoleController"), (E, "Role"), (E, "Permission"))
s.call("Platform Admin", "RolesPage", "Chọn menu Vai trò & quyền")
s.call("RolesPage", "RoleController", "listRoles()")
s.self_("RoleController", "checkPermission(identity.roles.read)")
with s.frag("break", "[không đủ quyền]"):
    s.ret("RoleController", "RolesPage", "403 Forbidden")
    s.call("RolesPage", "Platform Admin", "Thông báo chưa được cấp quyền")
s.call("RoleController", "Role", "findAll()")
s.ret("Role", "RoleController", "roles (kèm quyền)")
s.ret("RoleController", "RolesPage", "roles")
s.call("RolesPage", "Platform Admin", "Hiển thị danh sách vai trò, chọn sẵn vai trò đầu tiên")
s.call("Platform Admin", "RolesPage", "Chọn một vai trò")
s.self_("RolesPage", "showPermissions(role)")
s.call("Platform Admin", "RolesPage", "Nhập từ khóa tìm quyền")
s.self_("RolesPage", "filterPermissions(keyword)")
s.call("RolesPage", "Platform Admin", "Hiển thị quyền phù hợp (hoặc “Không có quyền phù hợp”)")
s.call("Platform Admin", "RolesPage", "Chọn cấp / thu hồi quyền, nhấn Lưu")
s.call("RolesPage", "RoleController", "updatePermissions(roleCode, permissionCodes)")
s.call("RoleController", "Role", "findByCode(roleCode)")
s.ret("Role", "RoleController", "role")
with s.frag("break", "[role.isSystem]"):
    s.ret("RoleController", "RolesPage", "422 – vai trò hệ thống")
    s.call("RolesPage", "Platform Admin", "Báo không được sửa vai trò hệ thống")
s.call("RoleController", "Permission", "findByCodes(permissionCodes)")
s.ret("Permission", "RoleController", "permissions")
with s.frag("break", "[permissions rỗng]"):
    s.ret("RoleController", "RolesPage", "422 – cần ít nhất một quyền")
    s.call("RolesPage", "Platform Admin", "Báo vai trò phải có ít nhất một quyền")
s.call("RoleController", "Role", "replacePermissions(permissions)")
s.ret("RoleController", "RolesPage", "200 OK")
s.call("RolesPage", "Platform Admin", "Thông báo “Cập nhật thành công”")
SEQ["UC08"] = s.data()

# ---------------------------------------------------------------------------- UC13 Duyệt và quản lý cửa hàng
s = SD((A, "Platform Admin"), (B, "MerchantsPage"), (C, "MerchantController"), (E, "Merchant"), (X, "NotificationService"))
s.call("Platform Admin", "MerchantsPage", "Mở Quản lý cửa hàng, lọc theo trạng thái")
s.call("MerchantsPage", "MerchantController", "listMerchants(status, keyword)")
s.call("MerchantController", "Merchant", "search(status, keyword)")
s.ret("Merchant", "MerchantController", "merchants")
s.ret("MerchantController", "MerchantsPage", "merchants")
s.call("MerchantsPage", "Platform Admin", "Hiển thị danh sách cửa hàng")
s.call("Platform Admin", "MerchantsPage", "Chọn một cửa hàng")
s.call("MerchantsPage", "MerchantController", "getMerchant(merchantId)")
s.call("MerchantController", "Merchant", "findById(merchantId)")
s.ret("Merchant", "MerchantController", "merchant")
s.ret("MerchantController", "MerchantsPage", "merchant")
s.call("MerchantsPage", "Platform Admin", "Hiển thị chi tiết cửa hàng")
with s.frag("alt", "[Duyệt]"):
    s.call("Platform Admin", "MerchantsPage", "Nhấn Duyệt")
    s.call("MerchantsPage", "MerchantController", "verify(merchantId, version)")
    s.call("MerchantController", "Merchant", "findById(merchantId)")
    s.ret("Merchant", "MerchantController", "merchant")
    with s.frag("break", "[version đã thay đổi / trạng thái không cho phép]"):
        s.ret("MerchantController", "MerchantsPage", "409 Conflict")
        s.call("MerchantsPage", "Platform Admin", "Báo xung đột, tải lại chi tiết")
    s.call("MerchantController", "Merchant", "verify(adminId)")
    s.call("MerchantController", "NotificationService", "notify(owner, MERCHANT_VERIFIED)")
    s.ret("MerchantController", "MerchantsPage", "200 OK")
    s.call("MerchantsPage", "Platform Admin", "Thông báo “Duyệt cửa hàng thành công”")
    s.else_("[Từ chối]")
    s.call("Platform Admin", "MerchantsPage", "Chọn lý do, nhập chi tiết, nhấn Từ chối")
    s.call("MerchantsPage", "MerchantController", "reject(merchantId, reason, detail)")
    s.call("MerchantController", "Merchant", "findById(merchantId)")
    s.ret("Merchant", "MerchantController", "merchant")
    s.call("MerchantController", "Merchant", "reject(reason, detail)")
    s.call("MerchantController", "NotificationService", "notify(owner, MERCHANT_REJECTED)")
    s.ret("MerchantController", "MerchantsPage", "200 OK")
    s.call("MerchantsPage", "Platform Admin", "Thông báo đã từ chối")
    s.else_("[Tạm ngưng]")
    s.call("Platform Admin", "MerchantsPage", "Chọn lý do, nhấn Tạm ngưng")
    s.call("MerchantsPage", "MerchantController", "suspend(merchantId, reason)")
    s.call("MerchantController", "Merchant", "findById(merchantId)")
    s.ret("Merchant", "MerchantController", "merchant")
    s.call("MerchantController", "Merchant", "suspend(reason)")
    s.ret("MerchantController", "MerchantsPage", "200 OK")
    s.call("MerchantsPage", "Platform Admin", "Thông báo đã tạm ngưng")
    s.else_("[Khôi phục]")
    s.call("Platform Admin", "MerchantsPage", "Nhấn Khôi phục")
    s.call("MerchantsPage", "MerchantController", "reactivate(merchantId)")
    s.call("MerchantController", "Merchant", "findById(merchantId)")
    s.ret("Merchant", "MerchantController", "merchant")
    s.call("MerchantController", "Merchant", "reactivate()")
    s.ret("MerchantController", "MerchantsPage", "200 OK")
    s.call("MerchantsPage", "Platform Admin", "Thông báo đã khôi phục")
SEQ["UC13"] = s.data()

# ---------------------------------------------------------------------------- UC14 Tra cứu vận đơn
s = SD((A, "Public User"), (B, "TrackingPage"), (C, "TrackingController"), (E, "Shipment"), (E, "ShipmentEvent"))
s.call("Public User", "TrackingPage", "Nhập mã vận đơn, nhấn Tra cứu")
s.self_("TrackingPage", "validateTrackingCode(code)")
with s.frag("break", "[mã trống / sai định dạng]"):
    s.call("TrackingPage", "Public User", "Báo lỗi mã vận đơn")
s.call("TrackingPage", "TrackingController", "track(trackingCode)")
s.call("TrackingController", "Shipment", "findByTrackingCode(trackingCode)")
s.ret("Shipment", "TrackingController", "shipment")
with s.frag("break", "[không tìm thấy]"):
    s.ret("TrackingController", "TrackingPage", "404 Not Found")
    s.call("TrackingPage", "Public User", "Thông báo “Vận đơn không tồn tại”")
with s.frag("ref", "UC14.1 Xem hành trình vận đơn"):
    s.call("TrackingController", "ShipmentEvent", "findByShipment(shipmentId)")
    s.ret("ShipmentEvent", "TrackingController", "events")
with s.frag("opt", "[người tra cứu không phải chủ vận đơn]"):
    s.self_("TrackingController", "maskContactInfo(view)")
s.ret("TrackingController", "TrackingPage", "trackingView")
s.call("TrackingPage", "Public User", "Hiển thị trạng thái, tiến trình, thời gian giao dự kiến, timeline")
SEQ["UC14"] = s.data()

# ---------------------------------------------------------------------------- UC15 Tra cứu giá cước
s = SD((A, "Public User"), (B, "QuotePage"), (C, "QuoteController"), (E, "ServiceArea"), (E, "RatePlan"), (E, "QuoteRequest"))
s.call("Public User", "QuotePage", "Nhập điểm gửi / nhận, kiện hàng, COD, nhấn Tính cước")
s.call("QuotePage", "QuoteController", "createQuote(request)")
s.self_("QuoteController", "validateLimits(request)")
with s.frag("break", "[thiếu dữ liệu / vượt giới hạn / COD vượt mức]"):
    s.ret("QuoteController", "QuotePage", "422 – lỗi dữ liệu")
    s.call("QuotePage", "Public User", "Báo lỗi hoặc mức tối đa cho phép")
s.call("QuoteController", "ServiceArea", "resolve(provinceCode, wardCode)")
s.ret("ServiceArea", "QuoteController", "serviceArea, primaryHub, coverageTier")
with s.frag("break", "[ngoài vùng phục vụ]"):
    s.ret("QuoteController", "QuotePage", "422 – chưa hỗ trợ khu vực")
    s.call("QuotePage", "Public User", "Thông báo “Chưa hỗ trợ khu vực này”")
s.self_("QuoteController", "chargeableWeight(parcels)")
s.call("QuoteController", "RatePlan", "findActive()")
s.ret("RatePlan", "QuoteController", "ratePlan")
with s.frag("break", "[không có bảng giá ACTIVE]"):
    s.ret("QuoteController", "QuotePage", "503 Service Unavailable")
    s.call("QuotePage", "Public User", "Báo lỗi, đề nghị thử lại sau")
with s.frag("loop", "[với mỗi mức dịch vụ]"):
    s.call("QuoteController", "RatePlan", "price(level, zone, tier, weight, cod, declaredValue)")
    s.ret("RatePlan", "QuoteController", "quoteOption")
s.call("QuoteController", "QuoteRequest", "create(request, options, expiresAt)")
s.ret("QuoteRequest", "QuoteController", "quote")
s.ret("QuoteController", "QuotePage", "quoteId, options[], expiresAt")
s.call("QuotePage", "Public User", "Hiển thị bảng phương án và hạn báo giá")
SEQ["UC15"] = s.data()

# ---------------------------------------------------------------------------- UC17 Tạo đơn hàng
s = SD((A, "Merchant"), (B, "CreateShipmentPage"), (C, "ShipmentController"), (C, "QuoteController"), (C, "RoutingController"),
       (E, "Merchant "), (E, "Shipment"), (E, "OperationalAssignment"))
s.call("Merchant", "CreateShipmentPage", "Chọn Tạo đơn hàng")
s.call("CreateShipmentPage", "ShipmentController", "getDraftContext()")
s.call("ShipmentController", "Merchant ", "findByOwner(userId)")
s.ret("Merchant ", "ShipmentController", "merchant")
with s.frag("break", "[cửa hàng chưa xác minh]"):
    s.ret("ShipmentController", "CreateShipmentPage", "403 – cửa hàng chưa ACTIVE")
    s.call("CreateShipmentPage", "Merchant", "Hướng dẫn gửi yêu cầu xác minh")
s.ret("ShipmentController", "CreateShipmentPage", "pickupAddresses")
s.call("CreateShipmentPage", "Merchant", "Hiển thị bước người gửi / người nhận")
s.call("Merchant", "CreateShipmentPage", "Nhập người gửi, người nhận, kiện hàng, COD")
s.self_("CreateShipmentPage", "validateForm()")
with s.frag("break", "[SĐT / địa chỉ không hợp lệ, kiện vượt giới hạn]"):
    s.call("CreateShipmentPage", "Merchant", "Báo lỗi tại bước tương ứng")
with s.frag("ref", "SD-UC15 Tra cứu giá cước"):
    s.call("CreateShipmentPage", "QuoteController", "createQuote(request)")
    s.ret("QuoteController", "CreateShipmentPage", "quoteId, options[]")
with s.frag("break", "[COD / giá trị khai báo vượt mức]"):
    s.call("CreateShipmentPage", "Merchant", "Báo mức tối đa của bảng giá")
s.call("CreateShipmentPage", "Merchant", "Hiển thị các phương án dịch vụ")
s.call("Merchant", "CreateShipmentPage", "Chọn mức dịch vụ, nhấn Xác nhận tạo đơn")
s.call("CreateShipmentPage", "ShipmentController", "createShipment(quoteId, serviceLevel, sender, recipient, parcels)")
s.call("ShipmentController", "QuoteController", "validateQuote(quoteId)")
s.ret("QuoteController", "ShipmentController", "quote")
with s.frag("break", "[báo giá hết hạn]"):
    s.ret("ShipmentController", "CreateShipmentPage", "409 – báo giá hết hạn")
    s.call("CreateShipmentPage", "Merchant", "Tính lại cước (quay lại SD-UC15)")
s.call("ShipmentController", "Shipment", "create(quote, sender, recipient, parcels)")
s.ret("Shipment", "ShipmentController", "shipment (trackingCode)")
s.call("ShipmentController", "RoutingController", "route(shipment)")
s.ret("RoutingController", "ShipmentController", "routeResult")
with s.frag("alt", "[tìm được lộ trình, còn tải]"):
    s.call("ShipmentController", "Shipment", "markRouted(batchId)")
    s.call("ShipmentController", "OperationalAssignment", "create(PICKUP, shipment, originHub)")
    s.else_("[không có lộ trình / hết tải]")
    s.call("ShipmentController", "Shipment", "markRoutingFailed()")
s.ret("ShipmentController", "CreateShipmentPage", "trackingCode, status")
s.call("CreateShipmentPage", "Merchant", "Hiển thị mã vận đơn và trạng thái")
SEQ["UC17"] = s.data()

# ---------------------------------------------------------------------------- UC25 Quản lý ca làm việc
s = SD((A, "Hub Manager"), (B, "ShiftsPage"), (C, "ShiftController"), (E, "WorkShift"), (E, "WorkforceShiftAssignment"), (X, "NotificationService"))
s.call("Hub Manager", "ShiftsPage", "Mở Ca làm việc")
s.call("ShiftsPage", "ShiftController", "listShifts(hubId, date)")
s.call("ShiftController", "WorkShift", "findByHubAndDate(hubId, date)")
s.ret("WorkShift", "ShiftController", "shifts")
s.ret("ShiftController", "ShiftsPage", "shifts")
s.call("ShiftsPage", "Hub Manager", "Hiển thị lịch ca")
with s.frag("alt", "[Tạo ca]"):
    s.call("Hub Manager", "ShiftsPage", "Nhập mã, tên, giờ bắt đầu, giờ kết thúc")
    s.call("ShiftsPage", "ShiftController", "createShift(hubId, code, name, startsAt, endsAt)")
    s.call("ShiftController", "WorkShift", "existsByCode(hubId, code)")
    s.ret("WorkShift", "ShiftController", "exists")
    with s.frag("break", "[giờ kết thúc ≤ giờ bắt đầu / trùng mã ca]"):
        s.ret("ShiftController", "ShiftsPage", "422 – thông tin ca không hợp lệ")
        s.call("ShiftsPage", "Hub Manager", "Báo lỗi thông tin ca")
    s.call("ShiftController", "WorkShift", "create(hubId, code, name, startsAt, endsAt)")
    s.ret("WorkShift", "ShiftController", "shift (SCHEDULED)")
    s.ret("ShiftController", "ShiftsPage", "shift")
    s.call("Hub Manager", "ShiftsPage", "Chọn nhân sự, nhấn Phân công")
    s.call("ShiftsPage", "ShiftController", "assignMembers(shiftId, memberIds)")
    with s.frag("loop", "[với mỗi nhân sự được chọn]"):
        s.call("ShiftController", "WorkforceShiftAssignment", "existsOverlap(memberId, startsAt, endsAt)")
        s.ret("WorkforceShiftAssignment", "ShiftController", "overlap")
        with s.frag("alt", "[không trùng ca]"):
            s.call("ShiftController", "WorkShift", "assign(member)")
            s.call("ShiftController", "NotificationService", "notifyShift(member, shift)")
            s.else_("[trùng ca khác]")
            s.self_("ShiftController", "skip(member, warning)")
    s.ret("ShiftController", "ShiftsPage", "assigned, warnings")
    s.call("ShiftsPage", "Hub Manager", "Hiển thị kết quả phân công")
    s.else_("[Hủy ca]")
    s.call("Hub Manager", "ShiftsPage", "Chọn ca, nhập lý do, nhấn Hủy ca")
    s.call("ShiftsPage", "ShiftController", "cancelShift(shiftId, reason)")
    s.call("ShiftController", "WorkShift", "findById(shiftId)")
    s.ret("WorkShift", "ShiftController", "shift")
    s.call("ShiftController", "WorkShift", "cancel(reason)")
    s.ret("ShiftController", "ShiftsPage", "200 OK")
    s.call("ShiftsPage", "Hub Manager", "Thông báo đã hủy ca và các phân công")
    s.else_("[Đánh dấu vắng mặt]")
    s.call("Hub Manager", "ShiftsPage", "Chọn nhân sự không đến ca")
    s.call("ShiftsPage", "ShiftController", "markAbsent(assignmentId)")
    s.call("ShiftController", "WorkforceShiftAssignment", "findById(assignmentId)")
    s.ret("WorkforceShiftAssignment", "ShiftController", "assignment")
    s.call("ShiftController", "WorkforceShiftAssignment", "markAbsent()")
    s.ret("ShiftController", "ShiftsPage", "200 OK")
    s.call("ShiftsPage", "Hub Manager", "Hiển thị trạng thái ABSENT")
SEQ["UC25"] = s.data()

# ---------------------------------------------------------------------------- UC31 Điều phối công việc
s = SD((A, "Hub Dispatch"), (B, "DispatchPage"), (C, "DispatchController"), (E, "OperationalAssignment"), (E, "WorkforceMember"), (X, "NotificationService"))
s.call("Hub Dispatch", "DispatchPage", "Mở Điều phối công việc")
s.call("DispatchPage", "DispatchController", "listPending(hubId)")
s.call("DispatchController", "OperationalAssignment", "findPending(hubId)")
s.ret("OperationalAssignment", "DispatchController", "assignments")
s.ret("DispatchController", "DispatchPage", "assignments")
s.call("DispatchPage", "Hub Dispatch", "Hiển thị công việc chờ theo ưu tiên")
with s.frag("alt", "[Phân công / phân công lại]"):
    s.call("Hub Dispatch", "DispatchPage", "Chọn công việc")
    with s.frag("ref", "UC24 Xem nhân sự vận hành"):
        s.call("DispatchPage", "DispatchController", "eligibleMembers(assignmentId)")
        s.call("DispatchController", "WorkforceMember", "findEligible(kind, hubId)")
        s.ret("WorkforceMember", "DispatchController", "members")
        s.ret("DispatchController", "DispatchPage", "members")
    with s.frag("break", "[không có nhân sự phù hợp]"):
        s.call("DispatchPage", "Hub Dispatch", "Thông báo không có nhân sự sẵn sàng")
    s.call("DispatchPage", "Hub Dispatch", "Hiển thị nhân sự phù hợp")
    s.call("Hub Dispatch", "DispatchPage", "Chọn nhân sự, nhấn Phân công")
    s.call("DispatchPage", "DispatchController", "assign(assignmentId, memberId)")
    s.call("DispatchController", "WorkforceMember", "findById(memberId)")
    s.ret("WorkforceMember", "DispatchController", "member")
    s.call("DispatchController", "WorkforceMember", "canTakeAssignment()")
    s.ret("WorkforceMember", "DispatchController", "true / false")
    with s.frag("break", "[quá tải / đã hết ca]"):
        s.ret("DispatchController", "DispatchPage", "409 Conflict")
        s.call("DispatchPage", "Hub Dispatch", "Cảnh báo, yêu cầu chọn nhân sự khác")
    s.call("DispatchController", "OperationalAssignment", "findById(assignmentId)")
    s.ret("OperationalAssignment", "DispatchController", "assignment")
    s.call("DispatchController", "OperationalAssignment", "assign(member, dispatcherId)")
    s.call("DispatchController", "NotificationService", "pushAssignment(member, assignment)")
    s.ret("DispatchController", "DispatchPage", "200 OK")
    s.call("DispatchPage", "Hub Dispatch", "Thông báo “Phân công thành công”")
    s.else_("[Hủy công việc]")
    s.call("Hub Dispatch", "DispatchPage", "Chọn công việc, nhập ghi chú, nhấn Hủy")
    s.call("DispatchPage", "DispatchController", "cancel(assignmentId, note)")
    s.call("DispatchController", "OperationalAssignment", "findById(assignmentId)")
    s.ret("OperationalAssignment", "DispatchController", "assignment")
    s.call("DispatchController", "OperationalAssignment", "cancel(note)")
    s.ret("DispatchController", "DispatchPage", "200 OK")
    s.call("DispatchPage", "Hub Dispatch", "Thông báo đã hủy công việc")
SEQ["UC31"] = s.data()

# ---------------------------------------------------------------------------- UC33 Thực hiện công việc
s = SD((A, "Operational Staff"), (B, "MyAssignmentsPage"), (C, "AssignmentController"), (E, "OperationalAssignment"), (E, "Parcel"), (E, "ShipmentEvent"))
s.call("Operational Staff", "MyAssignmentsPage", "Mở Công việc của tôi")
s.call("MyAssignmentsPage", "AssignmentController", "myAssignments()")
s.call("AssignmentController", "OperationalAssignment", "findByAssignee(memberId)")
s.ret("OperationalAssignment", "AssignmentController", "assignments")
s.ret("AssignmentController", "MyAssignmentsPage", "assignments")
s.call("MyAssignmentsPage", "Operational Staff", "Hiển thị công việc theo ưu tiên")
s.call("Operational Staff", "MyAssignmentsPage", "Chọn công việc, nhấn Nhận việc")
s.call("MyAssignmentsPage", "AssignmentController", "accept(assignmentId)")
s.call("AssignmentController", "OperationalAssignment", "findById(assignmentId)")
s.ret("OperationalAssignment", "AssignmentController", "assignment")
s.call("AssignmentController", "OperationalAssignment", "accept()")
s.ret("AssignmentController", "MyAssignmentsPage", "ACCEPTED")
s.call("Operational Staff", "MyAssignmentsPage", "Nhấn Bắt đầu")
s.call("MyAssignmentsPage", "AssignmentController", "start(assignmentId)")
s.call("AssignmentController", "OperationalAssignment", "findById(assignmentId)")
s.ret("OperationalAssignment", "AssignmentController", "assignment")
s.call("AssignmentController", "OperationalAssignment", "start()")
s.ret("AssignmentController", "MyAssignmentsPage", "IN_PROGRESS")
s.call("MyAssignmentsPage", "Operational Staff", "Mở màn hình quét mã")
with s.frag("opt", "[chưa cấp quyền camera]"):
    s.call("MyAssignmentsPage", "Operational Staff", "Yêu cầu cấp quyền hoặc cho nhập mã thủ công")
with s.frag("loop", "[còn kiện cần quét] (UC34 Quét kiện hàng)"):
    s.call("Operational Staff", "MyAssignmentsPage", "Quét mã kiện hàng")
    s.call("MyAssignmentsPage", "AssignmentController", "scan(assignmentId, parcelCode)")
    s.call("AssignmentController", "Parcel", "findByCode(parcelCode)")
    s.ret("Parcel", "AssignmentController", "parcel")
    with s.frag("alt", "[mã sai / kiện không thuộc công việc]"):
        s.ret("AssignmentController", "MyAssignmentsPage", "422 – kiện không hợp lệ")
        s.call("MyAssignmentsPage", "Operational Staff", "Cảnh báo, không ghi nhận lần quét")
        s.else_("[kiện đã được quét]")
        s.ret("AssignmentController", "MyAssignmentsPage", "409 – quét trùng")
        s.call("MyAssignmentsPage", "Operational Staff", "Báo quét trùng và bỏ qua")
        s.else_("[không xác định được vận đơn]")
        s.ret("AssignmentController", "MyAssignmentsPage", "404 – không xác định vận đơn")
        s.call("MyAssignmentsPage", "Operational Staff", "Gợi ý Báo cáo sự cố UNIDENTIFIED_PARCEL")
        s.else_("[hợp lệ]")
        s.call("AssignmentController", "Parcel", "updateCustody(type, refId, hub, stage)")
        s.call("AssignmentController", "ShipmentEvent", "record(SCANNED, parcel, hub, actorId)")
        s.ret("AssignmentController", "MyAssignmentsPage", "đã ghi nhận")
        s.call("MyAssignmentsPage", "Operational Staff", "Hiển thị kiện đã quét")
with s.frag("alt", "[Hoàn thành]"):
    s.call("Operational Staff", "MyAssignmentsPage", "Nhấn Hoàn thành")
    s.call("MyAssignmentsPage", "AssignmentController", "complete(assignmentId)")
    s.call("AssignmentController", "OperationalAssignment", "findById(assignmentId)")
    s.ret("OperationalAssignment", "AssignmentController", "assignment")
    s.call("AssignmentController", "OperationalAssignment", "missingParcels()")
    s.ret("OperationalAssignment", "AssignmentController", "missing")
    with s.frag("break", "[còn thiếu kiện]"):
        s.ret("AssignmentController", "MyAssignmentsPage", "422 – còn thiếu kiện")
        s.call("MyAssignmentsPage", "Operational Staff", "Hiển thị danh sách kiện còn thiếu")
    s.call("AssignmentController", "OperationalAssignment", "complete()")
    s.call("AssignmentController", "OperationalAssignment", "createNextLeg(shipment)")
    s.ret("AssignmentController", "MyAssignmentsPage", "COMPLETED")
    s.call("MyAssignmentsPage", "Operational Staff", "Thông báo hoàn thành công việc")
    s.else_("[Không thể hoàn thành]")
    s.call("Operational Staff", "MyAssignmentsPage", "Nhập lý do, nhấn Báo không hoàn thành")
    s.call("MyAssignmentsPage", "AssignmentController", "fail(assignmentId, note)")
    s.call("AssignmentController", "OperationalAssignment", "findById(assignmentId)")
    s.ret("OperationalAssignment", "AssignmentController", "assignment")
    s.call("AssignmentController", "OperationalAssignment", "fail(note)")
    s.ret("AssignmentController", "MyAssignmentsPage", "FAILED")
    s.call("MyAssignmentsPage", "Operational Staff", "Thông báo công việc không hoàn thành")
SEQ["UC33"] = s.data()

# ---------------------------------------------------------------------------- UC35 Ghi nhận kết quả giao hàng
s = SD((A, "Courier"), (B, "DeliveryPage"), (C, "DeliveryController"), (E, "DeliveryAttempt"), (E, "Shipment"), (E, "ShipmentEvent"))
s.call("Courier", "DeliveryPage", "Mở vận đơn trong công việc giao hàng")
s.call("DeliveryPage", "DeliveryController", "getDelivery(shipmentId)")
s.call("DeliveryController", "Shipment", "findById(shipmentId)")
s.ret("Shipment", "DeliveryController", "shipment")
s.ret("DeliveryController", "DeliveryPage", "recipient, codAmount, parcels")
s.call("DeliveryPage", "Courier", "Hiển thị người nhận, số tiền COD, danh sách kiện")
with s.frag("alt", "[Giao thành công]"):
    s.call("Courier", "DeliveryPage", "Chọn Giao thành công")
    s.call("DeliveryPage", "Courier", "Yêu cầu bằng chứng giao và tên người nhận")
    s.call("Courier", "DeliveryPage", "Cung cấp bằng chứng, xác nhận đã thu COD")
    s.call("DeliveryPage", "DeliveryController", "recordDelivery(shipmentId, proof, recipientName, codCollected, gps)")
    s.self_("DeliveryController", "validateProof(proof, codCollected)")
    with s.frag("break", "[thiếu bằng chứng / COD không khớp]"):
        s.ret("DeliveryController", "DeliveryPage", "422 – cần bổ sung")
        s.call("DeliveryPage", "Courier", "Yêu cầu bổ sung bằng chứng / kiểm tra lại COD")
    s.call("DeliveryController", "DeliveryAttempt", "record(DELIVERED, proof, recipientName, gps)")
    s.call("DeliveryController", "Shipment", "markDelivered(at)")
    s.call("DeliveryController", "ShipmentEvent", "record(DELIVERED, shipment, hub, actorId)")
    s.ret("DeliveryController", "DeliveryPage", "DELIVERED")
    s.call("DeliveryPage", "Courier", "Hiển thị giao thành công")
    s.else_("[Không giao được]")
    s.call("Courier", "DeliveryPage", "Chọn Giao thất bại và lý do")
    s.call("DeliveryPage", "DeliveryController", "recordFailure(shipmentId, reason, note)")
    s.call("DeliveryController", "DeliveryAttempt", "record(FAILED, reason, note)")
    s.call("DeliveryController", "Shipment", "recordFailedAttempt(reason)")
    s.call("DeliveryController", "ShipmentEvent", "record(DELIVERY_FAILED, shipment, hub, actorId)")
    s.ret("DeliveryController", "DeliveryPage", "DELIVERY_FAILED")
    s.call("DeliveryPage", "Courier", "Thông báo đã đưa vận đơn vào danh sách ngoại lệ")
SEQ["UC35"] = s.data()

# ---------------------------------------------------------------------------- UC40 Xử lý yêu cầu ngoại lệ
s = SD((A, "Hub Dispatch"), (B, "ExceptionRequestsPage"), (C, "ExceptionRequestController"), (E, "ShipmentExceptionRequest"),
       (E, "Shipment"), (C, "RoutingController"), (X, "NotificationService"))
s.call("Hub Dispatch", "ExceptionRequestsPage", "Mở Yêu cầu ngoại lệ")
s.call("ExceptionRequestsPage", "ExceptionRequestController", "listPending(hubId)")
s.call("ExceptionRequestController", "ShipmentExceptionRequest", "findPending(hubId)")
s.ret("ShipmentExceptionRequest", "ExceptionRequestController", "requests")
s.ret("ExceptionRequestController", "ExceptionRequestsPage", "requests")
s.call("ExceptionRequestsPage", "Hub Dispatch", "Hiển thị yêu cầu đang chờ")
s.call("Hub Dispatch", "ExceptionRequestsPage", "Chọn một yêu cầu")
s.call("ExceptionRequestsPage", "ExceptionRequestController", "getRequest(requestId)")
s.call("ExceptionRequestController", "ShipmentExceptionRequest", "findById(requestId)")
s.ret("ShipmentExceptionRequest", "ExceptionRequestController", "request")
s.ret("ExceptionRequestController", "ExceptionRequestsPage", "request, shipment")
s.call("ExceptionRequestsPage", "Hub Dispatch", "Hiển thị loại, lý do, vận đơn, địa chỉ mới")
with s.frag("alt", "[Phê duyệt]"):
    s.call("Hub Dispatch", "ExceptionRequestsPage", "Nhập ghi chú, nhấn Phê duyệt")
    s.call("ExceptionRequestsPage", "ExceptionRequestController", "approve(requestId, note)")
    s.call("ExceptionRequestController", "Shipment", "findById(shipmentId)")
    s.ret("Shipment", "ExceptionRequestController", "shipment")
    s.call("ExceptionRequestController", "Shipment", "canChange()")
    s.ret("Shipment", "ExceptionRequestController", "true / false")
    with s.frag("break", "[vận đơn đã giao / đã hủy]"):
        s.ret("ExceptionRequestController", "ExceptionRequestsPage", "409 Conflict")
        s.call("ExceptionRequestsPage", "Hub Dispatch", "Thông báo không thể phê duyệt")
    s.call("ExceptionRequestController", "ShipmentExceptionRequest", "approve(reviewerId, note)")
    with s.frag("opt", "[routingRequired]"):
        s.call("ExceptionRequestController", "RoutingController", "reroute(shipment, newEndpoint)")
        s.ret("RoutingController", "ExceptionRequestController", "routeResult")
        with s.frag("break", "[không tìm được lộ trình mới]"):
            s.ret("ExceptionRequestController", "ExceptionRequestsPage", "202 – định tuyến lại thất bại")
            s.call("ExceptionRequestsPage", "Hub Dispatch", "Cảnh báo định tuyến lại thất bại")
    s.call("ExceptionRequestController", "Shipment", "applyException(request)")
    s.call("ExceptionRequestController", "ShipmentExceptionRequest", "complete(actorId)")
    s.call("ExceptionRequestController", "NotificationService", "notify(requester, REQUEST_COMPLETED)")
    s.ret("ExceptionRequestController", "ExceptionRequestsPage", "COMPLETED")
    s.call("ExceptionRequestsPage", "Hub Dispatch", "Thông báo “Xử lý thành công”")
    s.else_("[Từ chối]")
    s.call("Hub Dispatch", "ExceptionRequestsPage", "Nhập ghi chú, nhấn Từ chối")
    s.call("ExceptionRequestsPage", "ExceptionRequestController", "reject(requestId, note)")
    s.call("ExceptionRequestController", "ShipmentExceptionRequest", "findById(requestId)")
    s.ret("ShipmentExceptionRequest", "ExceptionRequestController", "request")
    s.call("ExceptionRequestController", "ShipmentExceptionRequest", "reject(reviewerId, note)")
    s.call("ExceptionRequestController", "NotificationService", "notify(requester, REQUEST_REJECTED)")
    s.ret("ExceptionRequestController", "ExceptionRequestsPage", "REJECTED")
    s.call("ExceptionRequestsPage", "Hub Dispatch", "Thông báo đã từ chối yêu cầu")
SEQ["UC40"] = s.data()


def operations():
    """{(kind, class): {operation: [UCxx, ...]}} - every message received by a boundary, control or
    entity lifeline (self calls included). The class diagram must offer each of them."""
    out = {}
    for code, sd in SEQ.items():
        for m in sd["msgs"]:
            if len(m) > 3:
                continue
            kind, name = sd["parts"][m[1]]
            if kind in ("boundary", "control", "entity"):
                op = m[2].split("(")[0].strip()
                out.setdefault((kind, name.strip()), {}).setdefault(op, [])
                if code not in out[(kind, name.strip())][op]:
                    out[(kind, name.strip())][op].append(code)
    return out
