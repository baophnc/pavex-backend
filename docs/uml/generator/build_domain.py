"""PAVEX domain model (conceptual, DDD-style) -> Visual Paradigm XML.

Conventions
-----------
* One UML package per bounded context; classes carry a DDD stereotype:
  «aggregate root», «entity», «value object», «enumeration».
* Structural references between entities are associations (with role names and
  multiplicities); composition (filled diamond) = lifecycle owned by the whole
  aggregate. Foreign-key attributes (xxxId) are therefore NOT repeated.
* Audit actors (createdBy, reviewedBy, ...) stay as identity references
  (UUID attributes) - DDD "reference other aggregates by identity".
* Pure technical columns (updatedAt, version, authorizationVersion, outbox,
  token tables) are excluded from the conceptual model.
* Value objects are used as attribute types (sender : ShipmentAddressSnapshot).
"""

from __future__ import annotations

import pathlib

from vpxml import Project, parse_attr

OUT = pathlib.Path(__file__).resolve().parent.parent

p = Project("PAVEX Domain Model", "PVXD")

# --------------------------------------------------------------------------
# primitives & stereotypes
# --------------------------------------------------------------------------
types_pkg = p.add("Package", "Primitive Types", doc="Kiểu dữ liệu nguyên thủy dùng trong mô hình (ánh xạ Kotlin/Java của backend).", key="pkg:types")
for t, d in [
    ("UUID", "Định danh duy nhất toàn cục"),
    ("String", "Chuỗi ký tự"),
    ("Boolean", "Đúng/Sai"),
    ("Int", "Số nguyên 32-bit"),
    ("Long", "Số nguyên 64-bit; tiền tệ lưu theo đơn vị nhỏ nhất (VND)"),
    ("BigDecimal", "Số thập phân chính xác (kg, cm, km, tỉ lệ)"),
    ("Instant", "Mốc thời gian UTC"),
    ("LocalDate", "Ngày (không có giờ)"),
    ("LocalTime", "Giờ trong ngày"),
]:
    p.add("DataType", t, parent=types_pkg, doc=d, tag="DT")

ST = {}
for key, name in [("ar", "aggregate root"), ("entity", "entity"), ("vo", "value object"), ("enum", "enumeration")]:
    ST[key] = p.add("Stereotype", name, tag="STE", key=f"st:{key}", BaseType="Class")

PKGS = {}


def pkg(key, name, doc):
    PKGS[key] = p.add("Package", name, doc=doc, key=f"pkg:{key}")
    return PKGS[key]


def cls(pk, name, st, doc, attrs):
    e = p.add("Class", name, parent=PKGS[pk], doc=doc, tag="CLS")
    e.stereotypes.append(ST[st])
    e._attrs = attrs  # resolved later (types may be declared afterwards)
    return e


def enum(pk, name, literals, doc=""):
    e = p.add("Class", name, parent=PKGS[pk], doc=doc or f"Tập giá trị {name}.", tag="ENU")
    e.stereotypes.append(ST["enum"])
    for lit in literals:
        lit_name, _, lit_doc = lit.partition("//")
        e.features.append(("EnumerationLiteral", p.nid("LIT"), lit_name.strip(), None, "", lit_doc.strip()))
    return e


def assoc(a, a_mult, b, b_mult, a_role="", b_role="", agg="None", name="", doc=""):
    """a is the 'whole' side when agg is Composited/Shared."""
    return p.rel("Association", a, b, src_mult=a_mult, dst_mult=b_mult, src_role=a_role, dst_role=b_role, src_agg=agg, name=name, doc=doc)


COMP, AGG = "Composited", "Shared"

# --------------------------------------------------------------------------
# Shared kernel
# --------------------------------------------------------------------------
pkg("shared", "Shared Kernel", "Value object dùng chung giữa các bounded context.")
cls("shared", "ContactAddress", "vo", "Địa chỉ liên hệ theo đơn vị hành chính 2 cấp (tỉnh/thành – phường/xã).", [
    "contactName:String", "contactPhone:String", "addressLine:String", "provinceCode:String", "wardCode:String"])
cls("shared", "Weight", "vo", "Bộ ba khối lượng: thực tế, quy đổi (thể tích / volumetricDivisor) và tính cước = max(thực tế, quy đổi).", [
    "actualWeightKg:BigDecimal", "volumetricWeightKg:BigDecimal", "chargeableWeightKg:BigDecimal"])
cls("shared", "Dimensions", "vo", "Kích thước kiện hàng (cm).", ["lengthCm:BigDecimal", "widthCm:BigDecimal", "heightCm:BigDecimal"])
cls("shared", "FeeBreakdown", "vo", "Cơ cấu cước phí (VND). total = tổng các thành phần.", [
    "baseFee:Long", "additionalWeightFee:Long", "areaSurcharge:Long", "codFee:Long", "insuranceFee:Long", "total:Long"])
cls("shared", "DeliveryEstimate", "vo", "Thời gian giao dự kiến (ngày).", ["estimatedMinDays:Int", "estimatedMaxDays:Int"])
cls("shared", "GeoPoint", "vo", "Tọa độ GPS.", ["latitude:BigDecimal", "longitude:BigDecimal"])
enum("shared", "DayOfWeek", ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
     "Ngày trong tuần (gom 7 cờ monday..sunday của LaneSchedule).")

# --------------------------------------------------------------------------
# Identity & Access
# --------------------------------------------------------------------------
pkg("identity", "Identity & Access", "Tài khoản, hồ sơ, sổ địa chỉ, vai trò và quyền (Identity service: /api/v1/auth, /users, /roles).")
cls("identity", "UserAccount", "ar", "Tài khoản đăng nhập của mọi người dùng (khách hàng, chủ shop, nhân viên, quản trị).", [
    "id:UUID", "email:String // duy nhất, dùng để đăng nhập", "emailVerified:Boolean", "status:AccountStatus",
    "profileStatus:ProfileStatus", "createdAt:Instant"])
cls("identity", "UserProfile", "entity", "Hồ sơ cá nhân gắn 1-1 với tài khoản.", [
    "firstName:String[0..1]", "lastName:String[0..1]", "displayName:String[0..1]", "gender:Gender[0..1]",
    "dateOfBirth:LocalDate[0..1]", "avatarUrl:String[0..1]"])
cls("identity", "UserAddress", "entity", "Sổ địa chỉ của người dùng (dùng làm địa chỉ gửi/nhận khi tạo đơn).", [
    "id:UUID", "label:String", "address:ContactAddress", "isDefault:Boolean"])
cls("identity", "Role", "ar", "Vai trò (RBAC). Vai trò hệ thống (isSystem) không được sửa/xóa.", [
    "id:UUID", "code:String", "name:String", "description:String[0..1]", "isSystem:Boolean"])
cls("identity", "Permission", "entity", "Quyền nguyên tử, mã dạng <context>.<resource>.<action>, ví dụ identity.users.read.", [
    "id:UUID", "code:String", "name:String", "description:String[0..1]"])
enum("identity", "AccountStatus", ["ACTIVE // Hoạt động", "SUSPENDED // Tạm khóa", "DISABLED // Vô hiệu hóa"])
enum("identity", "ProfileStatus", ["INCOMPLETE // Chưa hoàn tất", "COMPLETED // Đã hoàn tất",
                                   "PENDING_VERIFICATION // Chờ xác minh", "REJECTED // Bị từ chối"])
enum("identity", "Gender", ["MALE", "FEMALE", "OTHER"])

assoc("UserAccount", "1", "UserProfile", "0..1", "account", "profile", COMP)
assoc("UserAccount", "1", "UserAddress", "0..*", "owner", "addressBook", COMP)
assoc("UserAccount", "0..*", "Role", "0..*", "users", "roles", name="được gán")
assoc("Role", "0..*", "Permission", "1..*", "roles", "permissions", name="cấp quyền")

# --------------------------------------------------------------------------
# Partner (Merchant)
# --------------------------------------------------------------------------
pkg("partner", "Partner", "Cửa hàng (merchant) – người gửi hàng thương mại.")
cls("partner", "Merchant", "ar", "Cửa hàng do một khách hàng đăng ký; phải được xác minh trước khi tạo đơn.", [
    "id:UUID", "merchantCode:String", "businessName:String", "contactName:String", "contactPhone:String",
    "contactEmail:String[0..1]", "taxCode:String[0..1]", "status:MerchantStatus",
    "statusReasonCode:MerchantStatusReason[0..1]", "statusReasonDetail:String[0..1]", "statusChangedAt:Instant",
    "statusChangedByUserId:UUID[0..1]", "verifiedAt:Instant[0..1]", "verifiedByUserId:UUID[0..1]", "createdAt:Instant"])
cls("partner", "MerchantPickupAddress", "entity", "Kho/điểm lấy hàng của cửa hàng.", [
    "id:UUID", "name:String", "address:ContactAddress", "isDefault:Boolean"])
enum("partner", "MerchantStatus", ["PENDING_VERIFICATION // Chờ xác minh", "ACTIVE // Đang hoạt động",
                                   "REJECTED // Bị từ chối", "SUSPENDED // Tạm ngưng", "CLOSED // Đã đóng"])
enum("partner", "MerchantStatusReason", ["MISSING_DOCUMENTS", "INVALID_INFORMATION", "POLICY_VIOLATION",
                                         "FRAUD_SUSPECTED", "OWNER_REQUEST", "OTHER"])

assoc("UserAccount", "1", "Merchant", "0..1", "owner", "merchant", name="sở hữu")
assoc("Merchant", "1", "MerchantPickupAddress", "0..*", "merchant", "pickupAddresses", COMP)

# --------------------------------------------------------------------------
# Network
# --------------------------------------------------------------------------
pkg("network", "Network", "Mạng lưới: vùng, khu vực phục vụ, hub/bưu cục, tuyến, lịch chạy, mẫu lộ trình và giữ chỗ tải.")
cls("network", "NetworkRegion", "ar", "Vùng vận hành (ví dụ Miền Bắc/Trung/Nam).", [
    "id:UUID", "code:String", "name:String", "status:NetworkRegionStatus"])
cls("network", "ServiceArea", "ar", "Khu vực phục vụ do một hub chính đảm nhiệm.", [
    "id:UUID", "code:String", "name:String", "status:ServiceAreaStatus"])
cls("network", "ServiceAreaCoverage", "entity", "Phạm vi phủ sóng theo tỉnh (và tùy chọn phường/xã) + hạng phủ sóng tính phụ phí.", [
    "id:UUID", "provinceCode:String", "wardCode:String[0..1] // null = toàn tỉnh", "tier:CoverageTier", "isActive:Boolean"])
cls("network", "Hub", "ar", "Hub/bưu cục vật lý. Có thể hiển thị công khai để khách tra cứu.", [
    "id:UUID", "code:String", "name:String", "type:HubType", "status:HubStatus", "addressLine:String",
    "provinceCode:String", "wardCode:String", "publicVisible:Boolean", "publicName:String[0..1]"])
cls("network", "HubLane", "ar", "Tuyến trung chuyển có hướng giữa 2 hub.", [
    "id:UUID", "code:String", "transportMode:TransportMode", "estimatedTransitMinutes:Int",
    "distanceKm:BigDecimal[0..1]", "maxShipmentWeightKg:BigDecimal", "status:HubLaneStatus"])
cls("network", "LaneSchedule", "entity", "Lịch khởi hành định kỳ của tuyến và tải trọng mỗi chuyến.", [
    "id:UUID", "departureTime:LocalTime", "operatingDays:DayOfWeek[1..7]", "effectiveFrom:LocalDate",
    "effectiveTo:LocalDate[0..1]", "capacityKg:BigDecimal", "status:LaneScheduleStatus"])
cls("network", "RouteTemplate", "ar", "Mẫu lộ trình (chuỗi tuyến) giữa hub đi và hub đến; có phiên bản (revision).", [
    "id:UUID", "code:String", "name:String", "description:String[0..1]", "priority:Int", "status:RouteTemplateStatus", "revision:Int"])
cls("network", "RouteTemplateLeg", "entity", "Một chặng (tuyến) theo thứ tự trong mẫu lộ trình.", ["id:UUID", "sequence:Int"])
cls("network", "LaneCapacityReservation", "entity", "Giữ chỗ tải trọng trên một chuyến (tuyến + lịch + ngày) cho một chặng của vận đơn.", [
    "id:UUID", "batchId:UUID // nhóm giữ chỗ của 1 lần định tuyến", "routeRevision:Int", "legSequence:Int",
    "serviceDate:LocalDate", "departureAt:Instant", "arrivalAt:Instant", "reservedWeightKg:BigDecimal",
    "departureCapacityKg:BigDecimal", "routeSource:CapacityRouteSource", "routeTemplateRevision:Int[0..1]",
    "status:LaneCapacityReservationStatus"])
enum("network", "NetworkRegionStatus", ["ACTIVE", "INACTIVE"])
enum("network", "ServiceAreaStatus", ["ACTIVE", "INACTIVE"])
enum("network", "CoverageTier", ["URBAN // Nội thành", "SUBURBAN // Ngoại thành", "REMOTE // Vùng xa (phụ phí)", "ISLAND // Hải đảo (phụ phí)"])
enum("network", "HubType", ["SORTING_CENTER // Trung tâm khai thác", "TRANSIT_HUB // Hub trung chuyển",
                            "DELIVERY_STATION // Bưu cục giao nhận", "POST_OFFICE // Điểm gửi hàng"])
enum("network", "HubStatus", ["ACTIVE", "INACTIVE", "CLOSED"])
enum("network", "TransportMode", ["ROAD", "AIR", "SEA", "RAIL"])
enum("network", "HubLaneStatus", ["ACTIVE", "INACTIVE"])
enum("network", "LaneScheduleStatus", ["ACTIVE", "INACTIVE"])
enum("network", "RouteTemplateStatus", ["DRAFT", "ACTIVE", "ARCHIVED"])
enum("network", "CapacityRouteSource", ["ROUTE_TEMPLATE // theo mẫu lộ trình", "DIRECT_LANE // tuyến trực tiếp / tính động"])
enum("network", "LaneCapacityReservationStatus", ["RESERVED", "CONSUMED", "RELEASED"])

assoc("NetworkRegion", "1", "ServiceArea", "0..*", "region", "serviceAreas", AGG)
assoc("Hub", "1", "ServiceArea", "0..*", "primaryHub", "servedAreas", name="phục vụ chính")
assoc("ServiceArea", "1", "ServiceAreaCoverage", "1..*", "serviceArea", "coverages", COMP)
assoc("Hub", "0..1", "Hub", "0..*", "parentHub", "childHubs", name="trực thuộc")
assoc("Hub", "1", "HubLane", "0..*", "fromHub", "outboundLanes")
assoc("Hub", "1", "HubLane", "0..*", "toHub", "inboundLanes")
assoc("HubLane", "1", "LaneSchedule", "0..*", "lane", "schedules", COMP)
assoc("Hub", "1", "RouteTemplate", "0..*", "originHub", "originRoutes")
assoc("Hub", "1", "RouteTemplate", "0..*", "destinationHub", "destinationRoutes")
assoc("RouteTemplate", "1", "RouteTemplateLeg", "1..*", "routeTemplate", "legs {ordered}", COMP)
assoc("HubLane", "1", "RouteTemplateLeg", "0..*", "lane", "usedInLegs")
assoc("HubLane", "1", "LaneCapacityReservation", "0..*", "lane", "reservations")
assoc("LaneSchedule", "1", "LaneCapacityReservation", "0..*", "schedule", "reservations")
assoc("RouteTemplate", "0..1", "LaneCapacityReservation", "0..*", "routeTemplate", "reservations")

# --------------------------------------------------------------------------
# Pricing
# --------------------------------------------------------------------------
pkg("pricing", "Pricing", "Bảng giá có phiên bản, quy tắc giá và báo giá (quote) có thời hạn.")
cls("pricing", "RatePlan", "ar", "Bảng giá; mỗi lần thay đổi tạo revision mới. Chỉ một revision ACTIVE tại một thời điểm.", [
    "id:UUID", "code:String", "revision:Int", "name:String", "currency:String", "status:RatePlanStatus",
    "effectiveFrom:Instant[0..1]", "effectiveTo:Instant[0..1]", "quoteTtlMinutes:Int", "volumetricDivisor:BigDecimal",
    "remoteEndpointSurcharge:Long", "islandTransportRate:BigDecimal", "codPolicy:CodPolicy",
    "insurancePolicy:InsurancePolicy", "limits:ShipmentLimits", "createdBy:UUID[0..1]", "activatedBy:UUID[0..1]",
    "activatedAt:Instant[0..1]", "retiredBy:UUID[0..1]", "retiredAt:Instant[0..1]", "createdAt:Instant"])
cls("pricing", "RateRule", "entity", "Đơn giá theo (mức dịch vụ × vùng giá).", [
    "id:UUID", "serviceLevel:ServiceLevel", "pricingZone:PricingZone", "baseFee:Long", "includedWeightKg:BigDecimal",
    "additionalWeightStepKg:BigDecimal", "additionalWeightStepFee:Long", "estimate:DeliveryEstimate"])
cls("pricing", "CodPolicy", "vo", "Chính sách phí thu hộ (COD).", [
    "freeThreshold:Long", "rate:BigDecimal", "minimumFee:Long", "maximumCod:Long"])
cls("pricing", "InsurancePolicy", "vo", "Chính sách phí bảo hiểm theo giá trị khai báo.", [
    "freeThreshold:Long", "rate:BigDecimal", "maximumDeclaredValue:Long"])
cls("pricing", "ShipmentLimits", "vo", "Giới hạn kích thước/khối lượng nhận gửi.", [
    "maxParcelWeightKg:BigDecimal", "maxShipmentChargeableWeightKg:BigDecimal", "maxDimensionCm:BigDecimal"])
cls("pricing", "QuoteRequest", "ar", "Yêu cầu báo giá; hết hạn sau quoteTtlMinutes. Snapshot revision bảng giá đã dùng.", [
    "id:UUID", "rateRevision:Int", "rateVersion:String", "pricingZone:PricingZone", "origin:QuoteEndpoint",
    "destination:QuoteEndpoint", "totalWeight:Weight", "parcelCount:Int", "codAmount:Long", "declaredValue:Long",
    "createdAt:Instant", "expiresAt:Instant"])
cls("pricing", "QuoteEndpoint", "vo", "Điểm đầu/cuối đã phân giải mạng lưới (khu vực phục vụ, vùng, hub chính, hạng phủ sóng).", [
    "provinceCode:String", "wardCode:String", "serviceAreaId:UUID", "regionId:UUID", "primaryHubId:UUID", "coverageTier:CoverageTier"])
cls("pricing", "QuoteParcel", "entity", "Kiện hàng khai báo trong báo giá.", ["id:UUID", "sequence:Int", "weight:Weight", "dimensions:Dimensions"])
cls("pricing", "QuoteOption", "entity", "Phương án giá cho một mức dịch vụ.", [
    "id:UUID", "serviceLevel:ServiceLevel", "fees:FeeBreakdown", "estimate:DeliveryEstimate"])
enum("pricing", "RatePlanStatus", ["DRAFT // Nháp", "ACTIVE // Đang áp dụng", "RETIRED // Ngừng áp dụng"])
enum("pricing", "ServiceLevel", ["ECONOMY // Tiết kiệm", "STANDARD // Tiêu chuẩn", "EXPRESS // Nhanh"])
enum("pricing", "PricingZone", ["INTRA_PROVINCE // Nội tỉnh", "INTRA_REGION // Nội vùng", "INTER_REGION // Liên vùng"])

assoc("RatePlan", "1", "RateRule", "1..*", "ratePlan", "rules", COMP)
assoc("RatePlan", "1", "QuoteRequest", "0..*", "ratePlan", "quotes", name="định giá theo")
assoc("QuoteRequest", "1", "QuoteParcel", "1..*", "quote", "parcels", COMP)
assoc("QuoteRequest", "1", "QuoteOption", "1..*", "quote", "options", COMP)

# --------------------------------------------------------------------------
# Shipment
# --------------------------------------------------------------------------
pkg("shipment", "Shipment", "Vận đơn, kiện hàng, hành trình (event) và lần giao.")
cls("shipment", "Shipment", "ar", "Vận đơn. Snapshot giá & địa chỉ tại thời điểm tạo; theo dõi định tuyến, trạng thái và tạm giữ.", [
    "id:UUID", "trackingCode:String", "merchantReference:String[0..1]", "createdByUserId:UUID",
    "sender:ShipmentAddressSnapshot", "recipient:ShipmentAddressSnapshot", "shippingFeePayer:ShippingFeePayer",
    "rateRevision:Int", "rateVersion:String", "currency:String", "serviceLevel:ShipmentServiceLevel",
    "pricingZone:ShipmentPricingZone", "fees:FeeBreakdown", "codAmount:Long", "declaredValue:Long",
    "estimate:DeliveryEstimate", "quoteExpiresAt:Instant", "origin:ShipmentEndpoint", "destination:ShipmentEndpoint",
    "routingStatus:ShipmentRoutingStatus", "routeRevision:Int", "reservedWeightKg:BigDecimal",
    "routeReadyAt:Instant[0..1]", "estimatedDeliveryAt:Instant[0..1]", "estimatedTransitMinutes:Int[0..1]",
    "routeDistanceKm:BigDecimal[0..1]", "status:ShipmentStatus", "lifecycleStatus:ShipmentLifecycleStatus",
    "hold:ShipmentHold[0..1]", "hasPendingWeightReview:Boolean", "note:String[0..1]",
    "cancellationReason:String[0..1]", "cancelledAt:Instant[0..1]", "completedAt:Instant[0..1]", "createdAt:Instant"])
cls("shipment", "ShipmentAddressSnapshot", "vo", "Ảnh chụp địa chỉ người gửi/nhận (kèm tên tỉnh, phường) tại thời điểm tạo đơn.", [
    "contactName:String", "contactPhone:String", "addressLine:String", "provinceCode:String", "provinceName:String",
    "wardCode:String", "wardName:String"])
cls("shipment", "ShipmentEndpoint", "vo", "Snapshot mạng lưới của đầu đi/đến (khu vực, vùng, mã/tên hub, hạng phủ sóng).", [
    "serviceAreaId:UUID", "regionId:UUID", "hubCode:String", "hubName:String", "coverageTier:ShipmentCoverageTier"])
cls("shipment", "ShipmentHold", "vo", "Thông tin tạm giữ vận đơn.", [
    "reason:String", "actorUserId:UUID", "hubId:UUID[0..1]", "heldAt:Instant"])
cls("shipment", "Parcel", "entity", "Kiện hàng vật lý; theo dõi trạng thái, công đoạn, vị trí và bên đang giữ (custody).", [
    "id:UUID", "code:String", "sequence:Int", "description:String", "quantity:Int", "category:GoodsCategory",
    "isFragile:Boolean", "weight:Weight", "dimensions:Dimensions", "status:ParcelStatus", "stage:ParcelStage",
    "flowDirection:ParcelFlowDirection", "currentLocationType:ParcelLocationType", "currentHubName:String[0..1]",
    "currentRouteLegId:UUID[0..1]", "custodyType:ParcelCustodyType", "custodyReferenceId:UUID[0..1]"])
cls("shipment", "ShipmentEvent", "entity", "Sự kiện hành trình (timeline) của vận đơn/kiện.", [
    "id:UUID", "status:ShipmentStatus", "code:String", "description:String", "actorUserId:UUID[0..1]",
    "locationName:String[0..1]", "dataJson:String[0..1]", "occurredAt:Instant", "recordedAt:Instant"])
cls("shipment", "DeliveryAttempt", "entity", "Một lần giao hàng của bưu tá cho một kiện.", [
    "id:UUID", "attemptNumber:Int", "outcome:DeliveryAttemptOutcome", "failureReason:DeliveryFailureReason[0..1]",
    "proofMethod:DeliveryProofMethod[0..1]", "recipientName:String[0..1]", "evidenceReference:String[0..1]",
    "note:String[0..1]", "location:GeoPoint[0..1]", "actorUserId:UUID", "occurredAt:Instant"])
enum("shipment", "ShippingFeePayer", ["SENDER // Người gửi trả", "RECIPIENT // Người nhận trả"])
enum("shipment", "ShipmentServiceLevel", ["ECONOMY", "STANDARD", "EXPRESS"], "Bản sao ServiceLevel trong context Shipment.")
enum("shipment", "ShipmentPricingZone", ["INTRA_PROVINCE", "INTRA_REGION", "INTER_REGION"], "Bản sao PricingZone trong context Shipment.")
enum("shipment", "ShipmentCoverageTier", ["URBAN", "SUBURBAN", "REMOTE", "ISLAND"], "Bản sao CoverageTier trong context Shipment.")
enum("shipment", "ShipmentRoutingStatus", ["PENDING // Chờ định tuyến", "ROUTE_READY // Đã có lộ trình & giữ chỗ",
                                           "ROUTING_FAILED // Không tìm được lộ trình", "REROUTING // Đang định tuyến lại"])
enum("shipment", "ShipmentStatus", ["CREATED", "AWAITING_PICKUP", "PICKED_UP", "AT_ORIGIN_HUB", "IN_TRANSIT",
                                    "AT_DESTINATION_HUB", "OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_FAILED",
                                    "RETURNING", "RETURNED", "CANCELLED"])
enum("shipment", "ShipmentLifecycleStatus", ["OPEN // Đang xử lý", "COMPLETED // Hoàn tất", "CANCELLED // Đã hủy"])
enum("shipment", "GoodsCategory", ["DOCUMENT", "FASHION", "ELECTRONICS", "COSMETICS", "FOOD", "HOUSEHOLD", "OTHER"])
enum("shipment", "ParcelStatus", ["CREATED", "IN_NETWORK", "DELIVERED", "RETURNED", "LOST", "DAMAGED", "CANCELLED"])
enum("shipment", "ParcelStage", ["FIRST_MILE // Lấy hàng", "ORIGIN_HUB", "LINE_HAUL // Trung chuyển",
                                 "DESTINATION_HUB", "LAST_MILE // Giao hàng", "COMPLETED"])
enum("shipment", "ParcelFlowDirection", ["FORWARD // Chiều đi", "RETURN // Chiều hoàn"])
enum("shipment", "ParcelLocationType", ["SENDER", "HUB", "IN_TRANSIT", "WITH_COURIER", "RECIPIENT"])
enum("shipment", "ParcelCustodyType", ["MERCHANT", "HUB", "WORKFORCE_MEMBER", "RECIPIENT"])
enum("shipment", "DeliveryAttemptOutcome", ["DELIVERED", "FAILED"])
enum("shipment", "DeliveryFailureReason", ["RECIPIENT_UNAVAILABLE", "UNREACHABLE_PHONE", "WRONG_ADDRESS",
                                           "RECIPIENT_REFUSED", "RESCHEDULE_REQUESTED", "OTHER"])
enum("shipment", "DeliveryProofMethod", ["SIGNATURE", "PHOTO", "OTP"])

assoc("Merchant", "1", "Shipment", "0..*", "merchant", "shipments", name="gửi")
assoc("RatePlan", "1", "Shipment", "0..*", "ratePlan", "shipments")
assoc("QuoteRequest", "1", "Shipment", "0..*", "quote", "shipments", name="chốt giá từ")
assoc("Hub", "1", "Shipment", "0..*", "originHub", "originShipments")
assoc("Hub", "1", "Shipment", "0..*", "destinationHub", "destinationShipments")
assoc("Shipment", "1", "Parcel", "1..*", "shipment", "parcels", COMP)
assoc("Shipment", "1", "ShipmentEvent", "0..*", "shipment", "events {ordered}", COMP)
assoc("Parcel", "0..1", "ShipmentEvent", "0..*", "parcel", "events")
assoc("Hub", "0..1", "ShipmentEvent", "0..*", "hub", "events")
assoc("Shipment", "1", "DeliveryAttempt", "0..*", "shipment", "deliveryAttempts", COMP)
assoc("Parcel", "1", "DeliveryAttempt", "0..*", "parcel", "deliveryAttempts")
assoc("Hub", "0..1", "Parcel", "0..*", "currentHub", "parcelsOnHand", name="đang ở")
assoc("Shipment", "1", "LaneCapacityReservation", "0..*", "shipment", "capacityReservations")

# --------------------------------------------------------------------------
# Exception handling
# --------------------------------------------------------------------------
pkg("exception", "Exception Handling", "Yêu cầu ngoại lệ, hồ sơ sự cố và điều chỉnh khối lượng.")
cls("exception", "ShipmentExceptionRequest", "ar", "Yêu cầu ngoại lệ (hoàn, đổi địa chỉ, hủy...) cần duyệt; có thể kéo theo định tuyến lại.", [
    "id:UUID", "kind:ShipmentExceptionRequestKind", "status:ShipmentExceptionRequestStatus", "reason:String",
    "routingRequired:Boolean", "forwardAddress:ContactAddress[0..1]", "requestedByUserId:UUID", "requestedAt:Instant",
    "reviewedByUserId:UUID[0..1]", "reviewedAt:Instant[0..1]", "reviewNote:String[0..1]",
    "completedByUserId:UUID[0..1]", "completedAt:Instant[0..1]"])
cls("exception", "ShipmentCase", "ar", "Hồ sơ sự cố (hư hỏng, thất lạc, kiện không xác định...) tại hub.", [
    "id:UUID", "caseNumber:String", "type:ShipmentCaseType", "status:ShipmentCaseStatus",
    "severity:ShipmentCaseSeverity", "externalBarcode:String[0..1] // kiện chưa định danh", "title:String",
    "description:String", "reportedByUserId:UUID", "assignedToUserId:UUID[0..1]", "resolution:String[0..1]",
    "createdAt:Instant", "resolvedAt:Instant[0..1]", "closedAt:Instant[0..1]"])
cls("exception", "ShipmentWeightAdjustment", "entity", "Điều chỉnh khối lượng kiện tại hub; cần duyệt (Shipment.hasPendingWeightReview).", [
    "id:UUID", "oldActualWeightKg:BigDecimal", "newActualWeightKg:BigDecimal", "oldChargeableWeightKg:BigDecimal",
    "newChargeableWeightKg:BigDecimal", "reason:String", "actorUserId:UUID", "adjustedAt:Instant",
    "reviewCompleted:Boolean", "reviewedByUserId:UUID[0..1]", "reviewedAt:Instant[0..1]", "reviewNote:String[0..1]"])
enum("exception", "ShipmentExceptionRequestKind", ["RETURN_TO_SENDER // Hoàn hàng", "FORWARD_TO_NEW_ADDRESS // Chuyển tiếp/đổi địa chỉ",
                                                   "REDELIVERY // Giao lại", "CANCEL_SHIPMENT // Hủy vận đơn"])
enum("exception", "ShipmentExceptionRequestStatus", ["PENDING", "APPROVED", "REJECTED", "COMPLETED", "CANCELLED"])
enum("exception", "ShipmentCaseType", ["DAMAGED", "LOST", "MISSORTED", "UNIDENTIFIED_PARCEL", "ADDRESS_ISSUE", "OTHER"])
enum("exception", "ShipmentCaseStatus", ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"])
enum("exception", "ShipmentCaseSeverity", ["LOW", "MEDIUM", "HIGH", "CRITICAL"])

assoc("Shipment", "1", "ShipmentExceptionRequest", "0..*", "shipment", "exceptionRequests")
assoc("Hub", "0..1", "ShipmentExceptionRequest", "0..*", "hub", "exceptionRequests")
assoc("Hub", "1", "ShipmentCase", "0..*", "hub", "cases")
assoc("Shipment", "0..1", "ShipmentCase", "0..*", "shipment", "cases")
assoc("Parcel", "0..1", "ShipmentCase", "0..*", "parcel", "cases")
assoc("Shipment", "1", "ShipmentWeightAdjustment", "0..*", "shipment", "weightAdjustments")
assoc("Parcel", "1", "ShipmentWeightAdjustment", "0..*", "parcel", "weightAdjustments")
assoc("Hub", "0..1", "ShipmentWeightAdjustment", "0..*", "hub", "weightAdjustments")

# --------------------------------------------------------------------------
# Workforce & Operations
# --------------------------------------------------------------------------
pkg("workforce", "Workforce & Operations", "Nhân sự vận hành, gán hub, ca làm việc và công việc vận hành (assignment).")
cls("workforce", "WorkforceMember", "ar", "Hồ sơ nhân sự vận hành gắn với một tài khoản.", [
    "id:UUID", "employeeCode:String", "kind:WorkforceMemberKind", "status:WorkforceMemberStatus"])
cls("workforce", "HubMembership", "entity", "Nhân sự thuộc hub (một hub chính).", [
    "id:UUID", "isPrimary:Boolean", "isActive:Boolean", "assignedAt:Instant", "unassignedAt:Instant[0..1]"])
cls("workforce", "WorkforceAvailability", "entity", "Trạng thái sẵn sàng nhận việc.", [
    "status:WorkforceAvailabilityStatus", "maxConcurrentAssignments:Int", "updatedAt:Instant"])
cls("workforce", "WorkShift", "ar", "Ca làm việc tại hub.", [
    "id:UUID", "code:String", "name:String", "startsAt:Instant", "endsAt:Instant", "status:WorkShiftStatus",
    "createdByUserId:UUID", "cancelledByUserId:UUID[0..1]", "cancelReason:String[0..1]", "cancelledAt:Instant[0..1]"])
cls("workforce", "WorkforceShiftAssignment", "entity", "Phân công nhân sự vào ca; ghi nhận check-in/out, vắng mặt.", [
    "id:UUID", "status:WorkforceShiftAssignmentStatus", "assignedByUserId:UUID", "statusChangedByUserId:UUID",
    "assignedAt:Instant", "checkedInAt:Instant[0..1]", "checkedOutAt:Instant[0..1]", "markedAbsentAt:Instant[0..1]",
    "cancelledAt:Instant[0..1]", "note:String[0..1]"])
cls("workforce", "OperationalAssignment", "ar", "Công việc vận hành cho một vận đơn tại hub (lấy, nhập, trung chuyển, giao...).", [
    "id:UUID", "code:String", "sequence:Int", "routeLegId:UUID[0..1]", "type:OperationalAssignmentType",
    "requiredKind:WorkforceMemberKind", "status:OperationalAssignmentStatus", "priority:Int",
    "assignedByUserId:UUID[0..1]", "assignedAt:Instant[0..1]", "acceptedAt:Instant[0..1]", "startedAt:Instant[0..1]",
    "completedAt:Instant[0..1]", "cancelledAt:Instant[0..1]", "resolutionNote:String[0..1]",
    "lastActorUserId:UUID[0..1]", "createdAt:Instant"])
enum("workforce", "WorkforceMemberKind", ["COURIER // Bưu tá", "LINE_HAUL_DRIVER // Tài xế trung chuyển",
                                          "WAREHOUSE_OPERATOR // Nhân viên kho"])
enum("workforce", "WorkforceMemberStatus", ["ACTIVE", "ON_LEAVE", "INACTIVE"])
enum("workforce", "WorkforceAvailabilityStatus", ["AVAILABLE", "BUSY", "OFFLINE"])
enum("workforce", "WorkShiftStatus", ["SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED"])
enum("workforce", "WorkforceShiftAssignmentStatus", ["ASSIGNED", "CHECKED_IN", "CHECKED_OUT", "ABSENT", "CANCELLED"])
enum("workforce", "OperationalAssignmentType", ["PICKUP", "HUB_INBOUND", "SORTING", "LINE_HAUL", "HUB_OUTBOUND",
                                                "LAST_MILE_DELIVERY", "RETURN_TO_SENDER"])
enum("workforce", "OperationalAssignmentStatus", ["PENDING", "ASSIGNED", "ACCEPTED", "IN_PROGRESS", "COMPLETED",
                                                  "FAILED", "CANCELLED"])

assoc("UserAccount", "1", "WorkforceMember", "0..1", "account", "workforceMember", name="là nhân sự")
assoc("WorkforceMember", "1", "HubMembership", "0..*", "member", "memberships", COMP)
assoc("Hub", "1", "HubMembership", "0..*", "hub", "memberships")
assoc("WorkforceMember", "1", "WorkforceAvailability", "1", "member", "availability", COMP)
assoc("Hub", "1", "WorkShift", "0..*", "hub", "shifts")
assoc("WorkShift", "1", "WorkforceShiftAssignment", "0..*", "shift", "assignments", COMP)
assoc("WorkforceMember", "1", "WorkforceShiftAssignment", "0..*", "member", "shiftAssignments")
assoc("Shipment", "1", "OperationalAssignment", "0..*", "shipment", "assignments {ordered}")
assoc("Hub", "1", "OperationalAssignment", "0..*", "hub", "assignments")
assoc("WorkforceMember", "0..1", "OperationalAssignment", "0..*", "assignee", "assignments")
assoc("OperationalAssignment", "0..1", "Parcel", "0..*", "currentAssignment", "parcels", name="đang xử lý")

# --------------------------------------------------------------------------
# resolve attributes
# --------------------------------------------------------------------------
for e in list(p.by_name.values()):
    for spec in getattr(e, "_attrs", []):
        name, typ, mult, doc = parse_attr(spec)
        if typ not in p.by_name:
            raise SystemExit(f"{e.name}.{name}: unknown type {typ}")
        e.features.append(("Attribute", p.nid("ATT"), name, typ, mult, doc))

# package dependencies (context map)
for a, b in [("partner", "identity"), ("workforce", "identity"), ("shipment", "partner"), ("shipment", "pricing"),
             ("shipment", "network"), ("pricing", "network"), ("workforce", "network"), ("workforce", "shipment"),
             ("exception", "shipment"), ("exception", "network")]:
    p.rel("Dependency", f"pkg:{a}", f"pkg:{b}", name="")
for k in ["identity", "partner", "pricing", "shipment", "exception", "network"]:
    p.rel("Dependency", f"pkg:{k}", "pkg:shared", name="")

# --------------------------------------------------------------------------
# diagrams
# --------------------------------------------------------------------------


def enums_of(key):
    return [c.name for c in PKGS[key].children if any(s.name == "enumeration" for s in c.stereotypes)]


def chunks(xs, n):
    return [xs[i:i + n] for i in range(0, len(xs), n)]


d = p.diagram("ClassDiagram", "DM-00 Context Map",
              "Bản đồ bounded context và phụ thuộc giữa các gói. Mọi context đều dùng Shared Kernel (không vẽ để tránh rối).")
d.rows([["pkg:identity", ("pkg:partner", 80), ("pkg:pricing", 80)],
        [("pkg:workforce", 0), ("pkg:shipment", 80), ("pkg:network", 80)],
        [("pkg:exception", 360)]], hgap=80, vgap=120)


def own(d, key):
    """Package diagram: draw only relationships touching a class of that package."""
    mine = {c.id for c in PKGS[key].children}
    d.only_rels = {r.id for r in p.rels if r.src.id in mine or r.dst.id in mine}

d = p.diagram("ClassDiagram", "DM-01 Identity & Access")
own(d, "identity")
y = d.rows([["UserProfile", "UserAccount", "UserAddress"], ["Permission", "Role", ("Merchant", 60), "WorkforceMember"]])
d.rows([enums_of("identity") + ["ContactAddress"]], y0=y)

d = p.diagram("ClassDiagram", "DM-02 Partner")
own(d, "partner")
y = d.rows([["UserAccount", "Merchant", "MerchantPickupAddress"], [("Shipment", 260)]])
d.rows([enums_of("partner") + ["ContactAddress"]], y0=y)

d = p.diagram("ClassDiagram", "DM-03 Network")
own(d, "network")
y = d.rows([["NetworkRegion", "ServiceArea", "ServiceAreaCoverage", "RouteTemplate", "RouteTemplateLeg"],
            [("Hub", 330), ("HubLane", 280), "LaneSchedule", "LaneCapacityReservation"]], vgap=110)
for row in chunks(enums_of("network") + ["DayOfWeek"], 6):
    y = d.rows([row], y0=y, vgap=40)

d = p.diagram("ClassDiagram", "DM-04 Pricing")
own(d, "pricing")
y = d.rows([["RatePlan", "RateRule", "CodPolicy", "InsurancePolicy", "ShipmentLimits"],
            [("QuoteRequest", 0), "QuoteParcel", "QuoteOption", "QuoteEndpoint", ("Shipment", 40)]], vgap=100)
d.rows([enums_of("pricing") + ["Weight", "Dimensions", "FeeBreakdown", "DeliveryEstimate"]], y0=y)

d = p.diagram("ClassDiagram", "DM-05 Shipment")
own(d, "shipment")
y = d.rows([["Merchant", "QuoteRequest", "RatePlan", ("Hub", 120)],
            ["Shipment", ("Parcel", 40), ("ShipmentEvent", 40), "DeliveryAttempt"],
            ["ShipmentAddressSnapshot", "ShipmentEndpoint", "ShipmentHold", ("OperationalAssignment", 60), "LaneCapacityReservation"]],
           vgap=100)
for row in chunks(enums_of("shipment"), 7):
    y = d.rows([row], y0=y, vgap=40)

d = p.diagram("ClassDiagram", "DM-06 Exception Handling")
own(d, "exception")
y = d.rows([[("Shipment", 0), ("Parcel", 80), ("Hub", 80)],
            ["ShipmentExceptionRequest", ("ShipmentCase", 60), ("ShipmentWeightAdjustment", 60)]], vgap=110)
d.rows([enums_of("exception") + ["ContactAddress"]], y0=y)

d = p.diagram("ClassDiagram", "DM-07 Workforce & Operations")
own(d, "workforce")
y = d.rows([["UserAccount", ("WorkforceMember", 40), "WorkforceAvailability", ("Hub", 120)],
            ["HubMembership", ("WorkforceShiftAssignment", 40), "WorkShift", ("OperationalAssignment", 60), "Shipment", "Parcel"]],
           vgap=110)
for row in chunks(enums_of("workforce"), 7):
    y = d.rows([row], y0=y, vgap=40)

d = p.diagram("ClassDiagram", "DM-08 Shared Kernel & Value Objects")
d.rows([["ContactAddress", "Weight", "Dimensions", "FeeBreakdown", "DeliveryEstimate", "GeoPoint", "DayOfWeek"],
        ["ShipmentAddressSnapshot", "ShipmentEndpoint", "ShipmentHold", "QuoteEndpoint", "CodPolicy", "InsurancePolicy", "ShipmentLimits"]])

d = p.diagram("ClassDiagram", "DM-09 Full Domain Model (entities)",
              "Toàn bộ entity/aggregate root và quan hệ; enum & value object xem ở các sơ đồ theo gói.")
d.rows([["UserProfile", "UserAccount", "UserAddress", "Role", "Permission", ("Merchant", 60), "MerchantPickupAddress"],
        ["RateRule", "RatePlan", "QuoteRequest", "QuoteParcel", "QuoteOption", ("WorkforceMember", 60), "WorkforceAvailability", "HubMembership"],
        ["DeliveryAttempt", "ShipmentEvent", "Shipment", "Parcel", "OperationalAssignment", "WorkforceShiftAssignment", "WorkShift"],
        ["ShipmentExceptionRequest", "ShipmentCase", "ShipmentWeightAdjustment", ("Hub", 80), "HubLane", "LaneSchedule", "LaneCapacityReservation"],
        ["NetworkRegion", "ServiceArea", "ServiceAreaCoverage", ("RouteTemplate", 400), "RouteTemplateLeg"]], vgap=140, hgap=70)

if __name__ == "__main__":
    (OUT / "pavex-domain-model.xml").write_bytes(p.to_xml())
    for dg in p.diagrams:
        slug = dg.name.split(" ")[0].lower()
        (OUT / "preview" / f"{slug}.svg").write_text(dg.to_svg(), encoding="utf-8")
    lines = ["# Danh mục domain model PAVEX", "", "> Sinh tự động bởi `generator/build_domain.py` — đừng sửa tay.", ""]
    for key, pk in PKGS.items():
        lines += [f"## {pk.name}", "", pk.doc, ""]
        for c in pk.children:
            st = c.stereotypes[0].name
            if st == "enumeration":
                lits = ", ".join(f[2] for f in c.features)
                lines.append(f"- **{c.name}** «enumeration»: {lits}")
            else:
                attrs = ", ".join(f"{f[2]}: {f[3]}{'' if f[4] in ('', '1') else '[' + f[4] + ']'}" for f in c.features)
                lines.append(f"- **{c.name}** «{st}» — {c.doc}<br>`{attrs}`")
        lines.append("")
    lines += ["## Quan hệ", "", "| Từ | Bội số | Vai trò | Loại | Đến | Bội số | Vai trò |", "|---|---|---|---|---|---|---|"]
    for r in p.rels:
        if r.kind == "Association":
            kind = {"Composited": "◆ composition", "Shared": "◇ aggregation"}.get(r.src_agg, "association")
            lines.append(f"| {r.src.name} | {r.src_mult} | {r.src_role} | {kind} | {r.dst.name} | {r.dst_mult} | {r.dst_role} |")
    (OUT / "domain-catalog.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    n_cls = sum(1 for e in p.by_name.values() if e.kind == "Class")
    print(f"domain: {n_cls} classes, {len(p.rels)} relationships, {len(p.diagrams)} diagrams")
