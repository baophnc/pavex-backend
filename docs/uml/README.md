# PAVEX — Use Case & Domain Model

Bản hiện trạng đọc từ source code ngày 07/09/2026, gồm **5 sơ đồ Use Case và 5 sơ đồ Domain Model**. Đây là mô hình phân tích nghiệp vụ có truy vết tới code, không phải chứng nhận UML và không phải bản mô tả mọi endpoint. Các thao tác CRUD gần nhau được nhóm theo mục tiêu ở những trang tổng quan. Không sửa hoặc khởi động backend khi tạo bộ sơ đồ này.

## Mở và sử dụng

- [Mở bộ sơ đồ trong trình xem](index.html).
- **SVG**: hình vector để phóng to, in hoặc đưa vào báo cáo.
- **PNG**: bản ảnh để chèn vào phần mềm không hỗ trợ SVG.
- **PlantUML (`.puml`)**: nguồn chỉnh sửa được; dùng cùng `style.puml`.

| Trang | Nội dung | Hình vector | Nguồn |
| --- | --- | --- | --- |
| 01 | Use Case tổng quan hệ thống | [SVG](01-usecase-overview.svg) | [PlantUML](01-usecase-overview.puml) |
| 02 | Danh tính và quản trị | [SVG](02-usecase-identity-administration.svg) | [PlantUML](02-usecase-identity-administration.puml) |
| 03 | Khách hàng và tạo vận đơn | [SVG](03-usecase-merchant.svg) | [PlantUML](03-usecase-merchant.puml) |
| 04 | Nhân sự và điều phối | [SVG](04-usecase-dispatch.svg) | [PlantUML](04-usecase-dispatch.puml) |
| 05 | Thực thi lấy hàng / trung chuyển / giao | [SVG](05-usecase-execution.svg) | [PlantUML](05-usecase-execution.puml) |
| 06 | Domain tổng quan | [SVG](06-domain-overview.svg) | [PlantUML](06-domain-overview.puml) |
| 07 | Danh tính, role, hub membership và ca | [SVG](07-domain-identity-workforce.svg) | [PlantUML](07-domain-identity-workforce.puml) |
| 08 | Vận đơn, kiện, nhiệm vụ, giao và sự cố | [SVG](08-domain-shipment-execution.svg) | [PlantUML](08-domain-shipment-execution.puml) |
| 09 | Mạng lưới, tuyến và capacity | [SVG](09-domain-network.svg) | [PlantUML](09-domain-network.puml) |
| 10 | Merchant và báo giá | [SVG](10-domain-merchant-pricing.svg) | [PlantUML](10-domain-merchant-pricing.puml) |

## Quy ước UML

Theo [OMG UML 2.5.1](https://www.omg.org/spec/UML/2.5.1/PDF), dùng ký hiệu Use Case và Class Diagram để biểu diễn hai loại mô hình. Công cụ render dùng cú pháp [PlantUML Use Case](https://plantuml.com/use-case-diagram) và [PlantUML Class](https://plantuml.com/class-diagram).

- Actor ở ngoài subject boundary; use case ở trong. Các service nội bộ, database, Eureka và gateway không phải actor của hệ thống PAVEX tổng thể. Google Identity và dịch vụ gửi email là hệ thống hỗ trợ bên ngoài.
- Actor association là đường liền, **không phải thứ tự quy trình**. Generalization là đường liền có tam giác rỗng hướng về actor tổng quát. Tên actor trừu tượng được in nghiêng.
- `«include»`: mũi tên nét đứt từ use case bao hàm sang hành vi bắt buộc. Trang 03: tạo đơn hợp lệ phải kiểm tra điều kiện, giữ chỗ tuyến và lập nhiệm vụ. Trang 05: giao thành công phải ghi thông tin bằng chứng. Tên use case con là cách đặt tên phân tích cho hành vi trong service, không khẳng định có API riêng.
- **Không gắn `include Đăng nhập` vào mọi use case.** Phiên đăng nhập/quyền phù hợp là tiền điều kiện. Không dùng `«extend»` chỉ để trang trí hoặc biểu diễn bước tiếp theo; bộ hiện trạng này không cần quan hệ đó.
- Bội số đặt cạnh lớp nào là số đối tượng lớp đó tương ứng với một đối tượng đầu kia. Ví dụ `Merchant 1 — 0..* Shipment`: một merchant có thể chưa có đơn hoặc có nhiều đơn; một shipment có một merchant.
- Hình thoi đen ở phía toàn thể chỉ dùng khi có quan hệ sở hữu thành phần rõ: Shipment–Parcel, Shipment–ShipmentEvent, ShipmentCase–ShipmentCaseEvent, RatePlan–RateRule, QuoteRequest–QuoteParcel/QuoteOption. Không suy ra composition chỉ vì có một trường UUID hoặc foreign key.
- `ShipmentAddressSnapshot` là value object; `«value object»` là nhãn quy ước của bộ tài liệu này, không phải stereotype chuẩn mặc định của UML. Hai association có tên `sender` và `recipient` là hai thuộc tính snapshot độc lập.
- Domain Model dùng khái niệm, thuộc tính tiêu biểu và liên kết; bỏ ID kỹ thuật, annotation JPA, repository, controller, token và outbox khỏi mô hình lõi. Nó không phải ERD, không mô tả foreign key xuyên database.
- Hình phản ánh các trạng thái đối tượng hợp lệ ở nhiều giai đoạn: nhiệm vụ có thể chưa có người được gán; tuyến mẫu/bảng giá chưa hoàn thiện có thể chưa có thành phần; case hàng vô chủ có thể chưa nhận diện được vận đơn.

## Điểm quan trọng về phân quyền

| Vai trò / khái niệm | Cách hiểu đúng trong code hiện tại |
| --- | --- |
| `PLATFORM_SUPER_ADMIN` | Toàn bộ permission catalog; không bỏ qua điều kiện ownership/custody/assignment của API thực thi. |
| `ADMIN` | Role quản trị cũ, vẫn còn để tương thích; không hoàn toàn đồng nhất với super admin ở API own/assigned. |
| `IAM_ADMIN` | Quản trị người dùng, lời mời, role và gán quyền; không phải mặc định được điều hành logistics. |
| `NETWORK_MANAGER` | Quản lý cấu hình mạng lưới; có pricing.read, không mặc định pricing.manage. |
| `OPERATIONS_MANAGER` | Vận hành toàn cục, nhân sự, nhiệm vụ, ngoại lệ và hồ sơ sự cố. |
| `HUB_MANAGER` | Quản lý nhân sự/ca/nhiệm vụ trong các hub được gán; mặc định chỉ đọc case, không có cases.manage. |
| `HUB_DISPATCHER` | Nhân viên điều phối: xem/gán/gán lại/hủy nhiệm vụ trong phạm vi hub; không mặc định quản trị role hoặc lịch ca. |
| `WAREHOUSE_OPERATOR` | Xử lý kho; scan phải có quyền, membership/capability và điều kiện trạng thái phù hợp. |
| `LINE_HAUL_DRIVER` | Chạy nhiệm vụ trung chuyển; không được quyền xác nhận giao last-mile như Courier. |
| `COURIER` | Lấy hàng và giao cuối; giao thành công/thất bại đi qua DeliveryAttempt. |
| `MERCHANT` | Đơn hàng của chính merchant; không suy ra quyền xem đơn toàn hệ thống từ màn hình có trong frontend. |
| `CUSTOMER` / người nhận | Có tài khoản cá nhân hoặc tra cứu công khai; không tự động có năng lực tạo đơn của merchant. |

Role hệ thống và actor không có quan hệ 1:1. Một người có thể đóng nhiều vai trò. `WorkforceMember.kind` chỉ có COURIER, WAREHOUSE_OPERATOR, DISPATCHER, LINE_HAUL_DRIVER; Hub Manager hiện được biểu diễn bằng role + workforce DISPATCHER + hub membership, không phải một subclass riêng. Các role cũ `DRIVER`, `WAREHOUSE_STAFF` vẫn được chấp nhận trong giai đoạn chuyển đổi.

Điều kiện tổng quát: **permission + phạm vi dữ liệu + điều kiện nghiệp vụ**. Đặc biệt, một nhân viên hợp lệ để assign cần ACTIVE, đúng kind và role, thuộc hub, có ca hiện tại đã check-in, đang sẵn sàng và chưa hết sức tải.

## Truy vết về code

Các liên kết dưới đây là nguồn cho quan hệ và mục tiêu nghiệp vụ trên hình, không phải tuyên bố rằng mỗi entity tương ứng một use case.

| Trang | Nguồn chính |
| --- | --- |
| 01–02, role | [Migration role V10](../../identity/src/main/resources/db/migration/V10__rebalance_system_role_permissions.sql), [V12](../../identity/src/main/resources/db/migration/V12__introduce_separated_platform_and_workforce_roles.sql), [RoleService](../../identity/src/main/kotlin/com/pavex/identity/service/RoleService.kt), [AuthController](../../auth/src/main/kotlin/com/pavex/auth/controller/AuthController.kt) |
| 02, quản trị | [Identity controllers](../../identity/src/main/kotlin/com/pavex/identity/controller), [Network controllers](../../network/src/main/kotlin/com/pavex/network/controller), [RatePlanController](../../pricing/src/main/kotlin/com/pavex/pricing/controller/RatePlanController.kt), [ShipmentCaseAccessService](../../shipment/src/main/kotlin/com/pavex/shipment/service/ShipmentCaseAccessService.kt) |
| 03 | [ShipmentController](../../shipment/src/main/kotlin/com/pavex/shipment/controller/ShipmentController.kt), [CreateShipmentService](../../shipment/src/main/kotlin/com/pavex/shipment/service/CreateShipmentService.kt), [Pricing SecurityConfig](../../pricing/src/main/kotlin/com/pavex/pricing/config/SecurityConfig.kt), [MerchantController](../../partners/src/main/kotlin/com/pavex/partners/controller/MerchantController.kt) |
| 04 | [Operations controllers](../../operations/src/main/kotlin/com/pavex/operations/controller), [AssignmentAccessScopeService](../../operations/src/main/kotlin/com/pavex/operations/service/AssignmentAccessScopeService.kt), [OperationalAssignmentService](../../operations/src/main/kotlin/com/pavex/operations/service/OperationalAssignmentService.kt) |
| 05 | [ShipmentOperationsScanGuard](../../shipment/src/main/kotlin/com/pavex/shipment/service/ShipmentOperationsScanGuard.kt), [RecordDeliveryAttemptService](../../shipment/src/main/kotlin/com/pavex/shipment/service/RecordDeliveryAttemptService.kt), [WorkforceRolePolicy](../../operations/src/main/kotlin/com/pavex/operations/service/WorkforceRolePolicy.kt) |
| 06–07 | [Identity entities](../../identity/src/main/kotlin/com/pavex/identity/entity), [Operations entities](../../operations/src/main/kotlin/com/pavex/operations/entity), [WorkforceHubScopeResolver](../../operations/src/main/kotlin/com/pavex/operations/service/WorkforceHubScopeResolver.kt) |
| 08 | [Shipment entities](../../shipment/src/main/kotlin/com/pavex/shipment/entity), [OperationalAssignment](../../operations/src/main/kotlin/com/pavex/operations/entity/OperationalAssignment.kt) |
| 09 | [Network entities](../../network/src/main/kotlin/com/pavex/network/entity), [RouteTemplateService](../../network/src/main/kotlin/com/pavex/network/service/RouteTemplateService.kt) |
| 10 | [Partners entities](../../partners/src/main/kotlin/com/pavex/partners/entity), [Pricing entities](../../pricing/src/main/kotlin/com/pavex/pricing/entity), [PricingQuoteContractService](../../pricing/src/main/kotlin/com/pavex/pricing/service/PricingQuoteContractService.kt), [RatePlanService](../../pricing/src/main/kotlin/com/pavex/pricing/service/RatePlanService.kt) |

## Phần đề xuất / chưa được khẳng định là đã triển khai

Phần này **không trộn vào các hình hiện trạng**:

1. Nếu muốn văn phòng duyệt/kích hoạt đơn trước khi lấy hàng, cần thêm workflow duyệt riêng. Code tạo đơn hiện giữ chỗ và lập chuỗi công việc ngay; không có bước duyệt văn phòng bắt buộc.
2. Nếu cần quản trị chuyến xe thực tế, cân nhắc Trip, Vehicle, Manifest và bàn giao theo chuyến. Hiện HubLane + LaneSchedule + Reservation + Assignment chưa tương đương đầy đủ những khái niệm này.
3. Không vẽ Billing/Invoice/Payment/COD settlement, call monitoring hay support ticket như chức năng hoàn chỉnh chỉ vì permission hoặc giao diện có tên tương ứng. Cần đối chiếu triển khai riêng trước khi đưa vào use case chính thức.
4. DeliveryAttempt lưu proofMethod và evidenceReference. Điều đó chưa chứng minh đã có hệ thống phát/kiểm tra OTP, chữ ký số hoặc lưu trữ ảnh hoàn chỉnh.
5. Việc cấp thêm quyền quản lý case cho Hub Manager cần được quyết định nghiệp vụ và thực thi đồng thời với hub scope, không chỉ thêm một đường nối trên sơ đồ.

## Render lại

Yêu cầu Java và PlantUML **1.2026.8**. Script sử dụng Smetana đi kèm JAR, không cần Graphviz và không gửi sơ đồ ra server:

```bash
bash docs/uml/render.sh
# Hoặc trỏ đến JAR được cài ở nơi khác:
PLANTUML_JAR=/absolute/path/plantuml-1.2026.8.jar bash docs/uml/render.sh
```

JAR local hiện đặt trong `.local/tools/` (không đưa vào Git). Không chạy backend/database để render. Nguồn và SVG/PNG cùng tên; nếu sửa nguồn phải render lại trước khi sử dụng hình.
