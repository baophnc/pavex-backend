# Danh mục domain model PAVEX

> Sinh tự động bởi `generator/build_domain.py` — đừng sửa tay.

## Shared Kernel

Value object dùng chung giữa các bounded context.

- **ContactAddress** «value object» — Địa chỉ liên hệ theo đơn vị hành chính 2 cấp (tỉnh/thành – phường/xã).<br>`contactName: String, contactPhone: String, addressLine: String, provinceCode: String, wardCode: String`
- **Weight** «value object» — Bộ ba khối lượng: thực tế, quy đổi (thể tích / volumetricDivisor) và tính cước = max(thực tế, quy đổi).<br>`actualWeightKg: BigDecimal, volumetricWeightKg: BigDecimal, chargeableWeightKg: BigDecimal`
- **Dimensions** «value object» — Kích thước kiện hàng (cm).<br>`lengthCm: BigDecimal, widthCm: BigDecimal, heightCm: BigDecimal`
- **FeeBreakdown** «value object» — Cơ cấu cước phí (VND). total = tổng các thành phần.<br>`baseFee: Long, additionalWeightFee: Long, areaSurcharge: Long, codFee: Long, insuranceFee: Long, total: Long`
- **DeliveryEstimate** «value object» — Thời gian giao dự kiến (ngày).<br>`estimatedMinDays: Int, estimatedMaxDays: Int`
- **GeoPoint** «value object» — Tọa độ GPS.<br>`latitude: BigDecimal, longitude: BigDecimal`
- **DayOfWeek** «enumeration»: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY

## Identity & Access

Tài khoản, hồ sơ, sổ địa chỉ, vai trò và quyền (Identity service: /api/v1/auth, /users, /roles).

- **UserAccount** «aggregate root» — Tài khoản đăng nhập của mọi người dùng (khách hàng, chủ shop, nhân viên, quản trị).<br>`id: UUID, email: String, emailVerified: Boolean, status: AccountStatus, profileStatus: ProfileStatus, createdAt: Instant`
- **UserProfile** «entity» — Hồ sơ cá nhân gắn 1-1 với tài khoản.<br>`firstName: String[0..1], lastName: String[0..1], displayName: String[0..1], gender: Gender[0..1], dateOfBirth: LocalDate[0..1], avatarUrl: String[0..1]`
- **UserAddress** «entity» — Sổ địa chỉ của người dùng (dùng làm địa chỉ gửi/nhận khi tạo đơn).<br>`id: UUID, label: String, address: ContactAddress, isDefault: Boolean`
- **Role** «aggregate root» — Vai trò (RBAC). Vai trò hệ thống (isSystem) không được sửa/xóa.<br>`id: UUID, code: String, name: String, description: String[0..1], isSystem: Boolean`
- **Permission** «entity» — Quyền nguyên tử, mã dạng <context>.<resource>.<action>, ví dụ identity.users.read.<br>`id: UUID, code: String, name: String, description: String[0..1]`
- **AccountStatus** «enumeration»: ACTIVE, SUSPENDED, DISABLED
- **ProfileStatus** «enumeration»: INCOMPLETE, COMPLETED, PENDING_VERIFICATION, REJECTED
- **Gender** «enumeration»: MALE, FEMALE, OTHER

## Partner

Cửa hàng (merchant) – người gửi hàng thương mại.

- **Merchant** «aggregate root» — Cửa hàng do một khách hàng đăng ký; phải được xác minh trước khi tạo đơn.<br>`id: UUID, merchantCode: String, businessName: String, contactName: String, contactPhone: String, contactEmail: String[0..1], taxCode: String[0..1], status: MerchantStatus, statusReasonCode: MerchantStatusReason[0..1], statusReasonDetail: String[0..1], statusChangedAt: Instant, statusChangedByUserId: UUID[0..1], verifiedAt: Instant[0..1], verifiedByUserId: UUID[0..1], createdAt: Instant`
- **MerchantPickupAddress** «entity» — Kho/điểm lấy hàng của cửa hàng.<br>`id: UUID, name: String, address: ContactAddress, isDefault: Boolean`
- **MerchantStatus** «enumeration»: PENDING_VERIFICATION, ACTIVE, REJECTED, SUSPENDED, CLOSED
- **MerchantStatusReason** «enumeration»: MISSING_DOCUMENTS, INVALID_INFORMATION, POLICY_VIOLATION, FRAUD_SUSPECTED, OWNER_REQUEST, OTHER

## Network

Mạng lưới: vùng, khu vực phục vụ, hub/bưu cục, tuyến, lịch chạy, mẫu lộ trình và giữ chỗ tải.

- **NetworkRegion** «aggregate root» — Vùng vận hành (ví dụ Miền Bắc/Trung/Nam).<br>`id: UUID, code: String, name: String, status: NetworkRegionStatus`
- **ServiceArea** «aggregate root» — Khu vực phục vụ do một hub chính đảm nhiệm.<br>`id: UUID, code: String, name: String, status: ServiceAreaStatus`
- **ServiceAreaCoverage** «entity» — Phạm vi phủ sóng theo tỉnh (và tùy chọn phường/xã) + hạng phủ sóng tính phụ phí.<br>`id: UUID, provinceCode: String, wardCode: String[0..1], tier: CoverageTier, isActive: Boolean`
- **Hub** «aggregate root» — Hub/bưu cục vật lý. Có thể hiển thị công khai để khách tra cứu.<br>`id: UUID, code: String, name: String, type: HubType, status: HubStatus, addressLine: String, provinceCode: String, wardCode: String, publicVisible: Boolean, publicName: String[0..1]`
- **HubLane** «aggregate root» — Tuyến trung chuyển có hướng giữa 2 hub.<br>`id: UUID, code: String, transportMode: TransportMode, estimatedTransitMinutes: Int, distanceKm: BigDecimal[0..1], maxShipmentWeightKg: BigDecimal, status: HubLaneStatus`
- **LaneSchedule** «entity» — Lịch khởi hành định kỳ của tuyến và tải trọng mỗi chuyến.<br>`id: UUID, departureTime: LocalTime, operatingDays: DayOfWeek[1..7], effectiveFrom: LocalDate, effectiveTo: LocalDate[0..1], capacityKg: BigDecimal, status: LaneScheduleStatus`
- **RouteTemplate** «aggregate root» — Mẫu lộ trình (chuỗi tuyến) giữa hub đi và hub đến; có phiên bản (revision).<br>`id: UUID, code: String, name: String, description: String[0..1], priority: Int, status: RouteTemplateStatus, revision: Int`
- **RouteTemplateLeg** «entity» — Một chặng (tuyến) theo thứ tự trong mẫu lộ trình.<br>`id: UUID, sequence: Int`
- **LaneCapacityReservation** «entity» — Giữ chỗ tải trọng trên một chuyến (tuyến + lịch + ngày) cho một chặng của vận đơn.<br>`id: UUID, batchId: UUID, routeRevision: Int, legSequence: Int, serviceDate: LocalDate, departureAt: Instant, arrivalAt: Instant, reservedWeightKg: BigDecimal, departureCapacityKg: BigDecimal, routeSource: CapacityRouteSource, routeTemplateRevision: Int[0..1], status: LaneCapacityReservationStatus`
- **NetworkRegionStatus** «enumeration»: ACTIVE, INACTIVE
- **ServiceAreaStatus** «enumeration»: ACTIVE, INACTIVE
- **CoverageTier** «enumeration»: URBAN, SUBURBAN, REMOTE, ISLAND
- **HubType** «enumeration»: SORTING_CENTER, TRANSIT_HUB, DELIVERY_STATION, POST_OFFICE
- **HubStatus** «enumeration»: ACTIVE, INACTIVE, CLOSED
- **TransportMode** «enumeration»: ROAD, AIR, SEA, RAIL
- **HubLaneStatus** «enumeration»: ACTIVE, INACTIVE
- **LaneScheduleStatus** «enumeration»: ACTIVE, INACTIVE
- **RouteTemplateStatus** «enumeration»: DRAFT, ACTIVE, ARCHIVED
- **CapacityRouteSource** «enumeration»: ROUTE_TEMPLATE, DIRECT_LANE
- **LaneCapacityReservationStatus** «enumeration»: RESERVED, CONSUMED, RELEASED

## Pricing

Bảng giá có phiên bản, quy tắc giá và báo giá (quote) có thời hạn.

- **RatePlan** «aggregate root» — Bảng giá; mỗi lần thay đổi tạo revision mới. Chỉ một revision ACTIVE tại một thời điểm.<br>`id: UUID, code: String, revision: Int, name: String, currency: String, status: RatePlanStatus, effectiveFrom: Instant[0..1], effectiveTo: Instant[0..1], quoteTtlMinutes: Int, volumetricDivisor: BigDecimal, remoteEndpointSurcharge: Long, islandTransportRate: BigDecimal, codPolicy: CodPolicy, insurancePolicy: InsurancePolicy, limits: ShipmentLimits, createdBy: UUID[0..1], activatedBy: UUID[0..1], activatedAt: Instant[0..1], retiredBy: UUID[0..1], retiredAt: Instant[0..1], createdAt: Instant`
- **RateRule** «entity» — Đơn giá theo (mức dịch vụ × vùng giá).<br>`id: UUID, serviceLevel: ServiceLevel, pricingZone: PricingZone, baseFee: Long, includedWeightKg: BigDecimal, additionalWeightStepKg: BigDecimal, additionalWeightStepFee: Long, estimate: DeliveryEstimate`
- **CodPolicy** «value object» — Chính sách phí thu hộ (COD).<br>`freeThreshold: Long, rate: BigDecimal, minimumFee: Long, maximumCod: Long`
- **InsurancePolicy** «value object» — Chính sách phí bảo hiểm theo giá trị khai báo.<br>`freeThreshold: Long, rate: BigDecimal, maximumDeclaredValue: Long`
- **ShipmentLimits** «value object» — Giới hạn kích thước/khối lượng nhận gửi.<br>`maxParcelWeightKg: BigDecimal, maxShipmentChargeableWeightKg: BigDecimal, maxDimensionCm: BigDecimal`
- **QuoteRequest** «aggregate root» — Yêu cầu báo giá; hết hạn sau quoteTtlMinutes. Snapshot revision bảng giá đã dùng.<br>`id: UUID, rateRevision: Int, rateVersion: String, pricingZone: PricingZone, origin: QuoteEndpoint, destination: QuoteEndpoint, totalWeight: Weight, parcelCount: Int, codAmount: Long, declaredValue: Long, createdAt: Instant, expiresAt: Instant`
- **QuoteEndpoint** «value object» — Điểm đầu/cuối đã phân giải mạng lưới (khu vực phục vụ, vùng, hub chính, hạng phủ sóng).<br>`provinceCode: String, wardCode: String, serviceAreaId: UUID, regionId: UUID, primaryHubId: UUID, coverageTier: CoverageTier`
- **QuoteParcel** «entity» — Kiện hàng khai báo trong báo giá.<br>`id: UUID, sequence: Int, weight: Weight, dimensions: Dimensions`
- **QuoteOption** «entity» — Phương án giá cho một mức dịch vụ.<br>`id: UUID, serviceLevel: ServiceLevel, fees: FeeBreakdown, estimate: DeliveryEstimate`
- **RatePlanStatus** «enumeration»: DRAFT, ACTIVE, RETIRED
- **ServiceLevel** «enumeration»: ECONOMY, STANDARD, EXPRESS
- **PricingZone** «enumeration»: INTRA_PROVINCE, INTRA_REGION, INTER_REGION

## Shipment

Vận đơn, kiện hàng, hành trình (event) và lần giao.

- **Shipment** «aggregate root» — Vận đơn. Snapshot giá & địa chỉ tại thời điểm tạo; theo dõi định tuyến, trạng thái và tạm giữ.<br>`id: UUID, trackingCode: String, merchantReference: String[0..1], createdByUserId: UUID, sender: ShipmentAddressSnapshot, recipient: ShipmentAddressSnapshot, shippingFeePayer: ShippingFeePayer, rateRevision: Int, rateVersion: String, currency: String, serviceLevel: ShipmentServiceLevel, pricingZone: ShipmentPricingZone, fees: FeeBreakdown, codAmount: Long, declaredValue: Long, estimate: DeliveryEstimate, quoteExpiresAt: Instant, origin: ShipmentEndpoint, destination: ShipmentEndpoint, routingStatus: ShipmentRoutingStatus, routeRevision: Int, reservedWeightKg: BigDecimal, routeReadyAt: Instant[0..1], estimatedDeliveryAt: Instant[0..1], estimatedTransitMinutes: Int[0..1], routeDistanceKm: BigDecimal[0..1], status: ShipmentStatus, lifecycleStatus: ShipmentLifecycleStatus, hold: ShipmentHold[0..1], hasPendingWeightReview: Boolean, note: String[0..1], cancellationReason: String[0..1], cancelledAt: Instant[0..1], completedAt: Instant[0..1], createdAt: Instant`
- **ShipmentAddressSnapshot** «value object» — Ảnh chụp địa chỉ người gửi/nhận (kèm tên tỉnh, phường) tại thời điểm tạo đơn.<br>`contactName: String, contactPhone: String, addressLine: String, provinceCode: String, provinceName: String, wardCode: String, wardName: String`
- **ShipmentEndpoint** «value object» — Snapshot mạng lưới của đầu đi/đến (khu vực, vùng, mã/tên hub, hạng phủ sóng).<br>`serviceAreaId: UUID, regionId: UUID, hubCode: String, hubName: String, coverageTier: ShipmentCoverageTier`
- **ShipmentHold** «value object» — Thông tin tạm giữ vận đơn.<br>`reason: String, actorUserId: UUID, hubId: UUID[0..1], heldAt: Instant`
- **Parcel** «entity» — Kiện hàng vật lý; theo dõi trạng thái, công đoạn, vị trí và bên đang giữ (custody).<br>`id: UUID, code: String, sequence: Int, description: String, quantity: Int, category: GoodsCategory, isFragile: Boolean, weight: Weight, dimensions: Dimensions, status: ParcelStatus, stage: ParcelStage, flowDirection: ParcelFlowDirection, currentLocationType: ParcelLocationType, currentHubName: String[0..1], currentRouteLegId: UUID[0..1], custodyType: ParcelCustodyType, custodyReferenceId: UUID[0..1]`
- **ShipmentEvent** «entity» — Sự kiện hành trình (timeline) của vận đơn/kiện.<br>`id: UUID, status: ShipmentStatus, code: String, description: String, actorUserId: UUID[0..1], locationName: String[0..1], dataJson: String[0..1], occurredAt: Instant, recordedAt: Instant`
- **DeliveryAttempt** «entity» — Một lần giao hàng của bưu tá cho một kiện.<br>`id: UUID, attemptNumber: Int, outcome: DeliveryAttemptOutcome, failureReason: DeliveryFailureReason[0..1], proofMethod: DeliveryProofMethod[0..1], recipientName: String[0..1], evidenceReference: String[0..1], note: String[0..1], location: GeoPoint[0..1], actorUserId: UUID, occurredAt: Instant`
- **ShippingFeePayer** «enumeration»: SENDER, RECIPIENT
- **ShipmentServiceLevel** «enumeration»: ECONOMY, STANDARD, EXPRESS
- **ShipmentPricingZone** «enumeration»: INTRA_PROVINCE, INTRA_REGION, INTER_REGION
- **ShipmentCoverageTier** «enumeration»: URBAN, SUBURBAN, REMOTE, ISLAND
- **ShipmentRoutingStatus** «enumeration»: PENDING, ROUTE_READY, ROUTING_FAILED, REROUTING
- **ShipmentStatus** «enumeration»: CREATED, AWAITING_PICKUP, PICKED_UP, AT_ORIGIN_HUB, IN_TRANSIT, AT_DESTINATION_HUB, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED, RETURNING, RETURNED, CANCELLED
- **ShipmentLifecycleStatus** «enumeration»: OPEN, COMPLETED, CANCELLED
- **GoodsCategory** «enumeration»: DOCUMENT, FASHION, ELECTRONICS, COSMETICS, FOOD, HOUSEHOLD, OTHER
- **ParcelStatus** «enumeration»: CREATED, IN_NETWORK, DELIVERED, RETURNED, LOST, DAMAGED, CANCELLED
- **ParcelStage** «enumeration»: FIRST_MILE, ORIGIN_HUB, LINE_HAUL, DESTINATION_HUB, LAST_MILE, COMPLETED
- **ParcelFlowDirection** «enumeration»: FORWARD, RETURN
- **ParcelLocationType** «enumeration»: SENDER, HUB, IN_TRANSIT, WITH_COURIER, RECIPIENT
- **ParcelCustodyType** «enumeration»: MERCHANT, HUB, WORKFORCE_MEMBER, RECIPIENT
- **DeliveryAttemptOutcome** «enumeration»: DELIVERED, FAILED
- **DeliveryFailureReason** «enumeration»: RECIPIENT_UNAVAILABLE, UNREACHABLE_PHONE, WRONG_ADDRESS, RECIPIENT_REFUSED, RESCHEDULE_REQUESTED, OTHER
- **DeliveryProofMethod** «enumeration»: SIGNATURE, PHOTO, OTP

## Exception Handling

Yêu cầu ngoại lệ, hồ sơ sự cố và điều chỉnh khối lượng.

- **ShipmentExceptionRequest** «aggregate root» — Yêu cầu ngoại lệ (hoàn, đổi địa chỉ, hủy...) cần duyệt; có thể kéo theo định tuyến lại.<br>`id: UUID, kind: ShipmentExceptionRequestKind, status: ShipmentExceptionRequestStatus, reason: String, routingRequired: Boolean, forwardAddress: ContactAddress[0..1], requestedByUserId: UUID, requestedAt: Instant, reviewedByUserId: UUID[0..1], reviewedAt: Instant[0..1], reviewNote: String[0..1], completedByUserId: UUID[0..1], completedAt: Instant[0..1]`
- **ShipmentCase** «aggregate root» — Hồ sơ sự cố (hư hỏng, thất lạc, kiện không xác định...) tại hub.<br>`id: UUID, caseNumber: String, type: ShipmentCaseType, status: ShipmentCaseStatus, severity: ShipmentCaseSeverity, externalBarcode: String[0..1], title: String, description: String, reportedByUserId: UUID, assignedToUserId: UUID[0..1], resolution: String[0..1], createdAt: Instant, resolvedAt: Instant[0..1], closedAt: Instant[0..1]`
- **ShipmentWeightAdjustment** «entity» — Điều chỉnh khối lượng kiện tại hub; cần duyệt (Shipment.hasPendingWeightReview).<br>`id: UUID, oldActualWeightKg: BigDecimal, newActualWeightKg: BigDecimal, oldChargeableWeightKg: BigDecimal, newChargeableWeightKg: BigDecimal, reason: String, actorUserId: UUID, adjustedAt: Instant, reviewCompleted: Boolean, reviewedByUserId: UUID[0..1], reviewedAt: Instant[0..1], reviewNote: String[0..1]`
- **ShipmentExceptionRequestKind** «enumeration»: RETURN_TO_SENDER, FORWARD_TO_NEW_ADDRESS, REDELIVERY, CANCEL_SHIPMENT
- **ShipmentExceptionRequestStatus** «enumeration»: PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED
- **ShipmentCaseType** «enumeration»: DAMAGED, LOST, MISSORTED, UNIDENTIFIED_PARCEL, ADDRESS_ISSUE, OTHER
- **ShipmentCaseStatus** «enumeration»: OPEN, IN_PROGRESS, RESOLVED, CLOSED
- **ShipmentCaseSeverity** «enumeration»: LOW, MEDIUM, HIGH, CRITICAL

## Workforce & Operations

Nhân sự vận hành, gán hub, ca làm việc và công việc vận hành (assignment).

- **WorkforceMember** «aggregate root» — Hồ sơ nhân sự vận hành gắn với một tài khoản.<br>`id: UUID, employeeCode: String, kind: WorkforceMemberKind, status: WorkforceMemberStatus`
- **HubMembership** «entity» — Nhân sự thuộc hub (một hub chính).<br>`id: UUID, isPrimary: Boolean, isActive: Boolean, assignedAt: Instant, unassignedAt: Instant[0..1]`
- **WorkforceAvailability** «entity» — Trạng thái sẵn sàng nhận việc.<br>`status: WorkforceAvailabilityStatus, maxConcurrentAssignments: Int, updatedAt: Instant`
- **WorkShift** «aggregate root» — Ca làm việc tại hub.<br>`id: UUID, code: String, name: String, startsAt: Instant, endsAt: Instant, status: WorkShiftStatus, createdByUserId: UUID, cancelledByUserId: UUID[0..1], cancelReason: String[0..1], cancelledAt: Instant[0..1]`
- **WorkforceShiftAssignment** «entity» — Phân công nhân sự vào ca; ghi nhận check-in/out, vắng mặt.<br>`id: UUID, status: WorkforceShiftAssignmentStatus, assignedByUserId: UUID, statusChangedByUserId: UUID, assignedAt: Instant, checkedInAt: Instant[0..1], checkedOutAt: Instant[0..1], markedAbsentAt: Instant[0..1], cancelledAt: Instant[0..1], note: String[0..1]`
- **OperationalAssignment** «aggregate root» — Công việc vận hành cho một vận đơn tại hub (lấy, nhập, trung chuyển, giao...).<br>`id: UUID, code: String, sequence: Int, routeLegId: UUID[0..1], type: OperationalAssignmentType, requiredKind: WorkforceMemberKind, status: OperationalAssignmentStatus, priority: Int, assignedByUserId: UUID[0..1], assignedAt: Instant[0..1], acceptedAt: Instant[0..1], startedAt: Instant[0..1], completedAt: Instant[0..1], cancelledAt: Instant[0..1], resolutionNote: String[0..1], lastActorUserId: UUID[0..1], createdAt: Instant`
- **WorkforceMemberKind** «enumeration»: COURIER, LINE_HAUL_DRIVER, WAREHOUSE_OPERATOR
- **WorkforceMemberStatus** «enumeration»: ACTIVE, ON_LEAVE, INACTIVE
- **WorkforceAvailabilityStatus** «enumeration»: AVAILABLE, BUSY, OFFLINE
- **WorkShiftStatus** «enumeration»: SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
- **WorkforceShiftAssignmentStatus** «enumeration»: ASSIGNED, CHECKED_IN, CHECKED_OUT, ABSENT, CANCELLED
- **OperationalAssignmentType** «enumeration»: PICKUP, HUB_INBOUND, SORTING, LINE_HAUL, HUB_OUTBOUND, LAST_MILE_DELIVERY, RETURN_TO_SENDER
- **OperationalAssignmentStatus** «enumeration»: PENDING, ASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED

## Quan hệ

| Từ | Bội số | Vai trò | Loại | Đến | Bội số | Vai trò |
|---|---|---|---|---|---|---|
| UserAccount | 1 | account | ◆ composition | UserProfile | 0..1 | profile |
| UserAccount | 1 | owner | ◆ composition | UserAddress | 0..* | addressBook |
| UserAccount | 0..* | users | association | Role | 0..* | roles |
| Role | 0..* | roles | association | Permission | 1..* | permissions |
| UserAccount | 1 | owner | association | Merchant | 0..1 | merchant |
| Merchant | 1 | merchant | ◆ composition | MerchantPickupAddress | 0..* | pickupAddresses |
| NetworkRegion | 1 | region | ◇ aggregation | ServiceArea | 0..* | serviceAreas |
| Hub | 1 | primaryHub | association | ServiceArea | 0..* | servedAreas |
| ServiceArea | 1 | serviceArea | ◆ composition | ServiceAreaCoverage | 1..* | coverages |
| Hub | 0..1 | parentHub | association | Hub | 0..* | childHubs |
| Hub | 1 | fromHub | association | HubLane | 0..* | outboundLanes |
| Hub | 1 | toHub | association | HubLane | 0..* | inboundLanes |
| HubLane | 1 | lane | ◆ composition | LaneSchedule | 0..* | schedules |
| Hub | 1 | originHub | association | RouteTemplate | 0..* | originRoutes |
| Hub | 1 | destinationHub | association | RouteTemplate | 0..* | destinationRoutes |
| RouteTemplate | 1 | routeTemplate | ◆ composition | RouteTemplateLeg | 1..* | legs {ordered} |
| HubLane | 1 | lane | association | RouteTemplateLeg | 0..* | usedInLegs |
| HubLane | 1 | lane | association | LaneCapacityReservation | 0..* | reservations |
| LaneSchedule | 1 | schedule | association | LaneCapacityReservation | 0..* | reservations |
| RouteTemplate | 0..1 | routeTemplate | association | LaneCapacityReservation | 0..* | reservations |
| RatePlan | 1 | ratePlan | ◆ composition | RateRule | 1..* | rules |
| RatePlan | 1 | ratePlan | association | QuoteRequest | 0..* | quotes |
| QuoteRequest | 1 | quote | ◆ composition | QuoteParcel | 1..* | parcels |
| QuoteRequest | 1 | quote | ◆ composition | QuoteOption | 1..* | options |
| Merchant | 1 | merchant | association | Shipment | 0..* | shipments |
| RatePlan | 1 | ratePlan | association | Shipment | 0..* | shipments |
| QuoteRequest | 1 | quote | association | Shipment | 0..* | shipments |
| Hub | 1 | originHub | association | Shipment | 0..* | originShipments |
| Hub | 1 | destinationHub | association | Shipment | 0..* | destinationShipments |
| Shipment | 1 | shipment | ◆ composition | Parcel | 1..* | parcels |
| Shipment | 1 | shipment | ◆ composition | ShipmentEvent | 0..* | events {ordered} |
| Parcel | 0..1 | parcel | association | ShipmentEvent | 0..* | events |
| Hub | 0..1 | hub | association | ShipmentEvent | 0..* | events |
| Shipment | 1 | shipment | ◆ composition | DeliveryAttempt | 0..* | deliveryAttempts |
| Parcel | 1 | parcel | association | DeliveryAttempt | 0..* | deliveryAttempts |
| Hub | 0..1 | currentHub | association | Parcel | 0..* | parcelsOnHand |
| Shipment | 1 | shipment | association | LaneCapacityReservation | 0..* | capacityReservations |
| Shipment | 1 | shipment | association | ShipmentExceptionRequest | 0..* | exceptionRequests |
| Hub | 0..1 | hub | association | ShipmentExceptionRequest | 0..* | exceptionRequests |
| Hub | 1 | hub | association | ShipmentCase | 0..* | cases |
| Shipment | 0..1 | shipment | association | ShipmentCase | 0..* | cases |
| Parcel | 0..1 | parcel | association | ShipmentCase | 0..* | cases |
| Shipment | 1 | shipment | association | ShipmentWeightAdjustment | 0..* | weightAdjustments |
| Parcel | 1 | parcel | association | ShipmentWeightAdjustment | 0..* | weightAdjustments |
| Hub | 0..1 | hub | association | ShipmentWeightAdjustment | 0..* | weightAdjustments |
| UserAccount | 1 | account | association | WorkforceMember | 0..1 | workforceMember |
| WorkforceMember | 1 | member | ◆ composition | HubMembership | 0..* | memberships |
| Hub | 1 | hub | association | HubMembership | 0..* | memberships |
| WorkforceMember | 1 | member | ◆ composition | WorkforceAvailability | 1 | availability |
| Hub | 1 | hub | association | WorkShift | 0..* | shifts |
| WorkShift | 1 | shift | ◆ composition | WorkforceShiftAssignment | 0..* | assignments |
| WorkforceMember | 1 | member | association | WorkforceShiftAssignment | 0..* | shiftAssignments |
| Shipment | 1 | shipment | association | OperationalAssignment | 0..* | assignments {ordered} |
| Hub | 1 | hub | association | OperationalAssignment | 0..* | assignments |
| WorkforceMember | 0..1 | assignee | association | OperationalAssignment | 0..* | assignments |
| OperationalAssignment | 0..1 | currentAssignment | association | Parcel | 0..* | parcels |
