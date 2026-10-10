# PAVEX – Mô hình UML (Domain Model & Use Case)

Hai file XML import thẳng vào **Visual Paradigm**. Đây là bản viết lại của
`pavex_domain.vpp` và `pavex-usecase-v2.vpp`.

| File | Nội dung |
|---|---|
| [`pavex-domain-model.xml`](pavex-domain-model.xml) | Domain model gồm 8 package (bounded context), 95 class (entity, value object, enum) và 56 quan hệ, vẽ trên **1 sơ đồ duy nhất**. |
| [`pavex-usecase-model.xml`](pavex-usecase-model.xml) | Use case model gồm 13 tác nhân (10 vai trò thật + 3 tác nhân abstract) và 44 use case, vẽ trên **1 sơ đồ duy nhất**. |
| [`domain-catalog.md`](domain-catalog.md) | Bảng tra cứu class, thuộc tính, giá trị enum và quan hệ (sinh tự động). |
| [`usecase-catalog.md`](usecase-catalog.md) | Bảng tra cứu use case: mã, tác nhân, mô tả, dữ liệu và include/extend (sinh tự động). |
| [`preview/`](preview) | Ảnh xem trước 2 sơ đồ (SVG/PNG) để xem nhanh, không cần mở VP. |
| [`generator/`](generator) | Script Python sinh ra toàn bộ các file trên. |

## Cách import vào Visual Paradigm

1. **Tạo project mới, trống** (**Project ▸ New**). Đừng import đè lên project đã có bản cũ: VP gộp theo ID,
   nên các sơ đồ và use case cũ không bị xóa mà vẫn nằm lại trong project.
   Sau đó chọn **Project ▸ Import ▸ XML…** (bản cũ dùng **File ▸ Import ▸ XML…**).
2. Chọn `pavex-domain-model.xml`, giữ tùy chọn mặc định rồi bấm **Import**. Làm tương tự với `pavex-usecase-model.xml`.
   Hai file dùng tiền tố ID khác nhau (`PAVEXDM_` và `PAVEXUC_`), nên import cả hai vào cùng một project được.
3. Mỗi file có 1 sơ đồ, nằm trong **Diagram Navigator**. Nếu đường nối trông rối, dùng
   **Diagram ▸ Layout ▸ Orthogonal / Hierarchic**, hoặc kéo lại vài shape cho gọn.

> Định dạng dùng ở đây là VP XML "simple structure" (`Xml_structure="simple"`), viết theo đúng mẫu do chính
> VP 16.1 xuất ra (`project.xml` của use case v2): use case nằm trong `DiagramElementChildren` của khung System,
> mỗi shape có `ZOrder`, tác nhân có `Caption` hiện tên bên dưới, và model có `MasterView`.

## Mỗi sơ đồ một file XML (`xml/`)

Thư mục `xml/` có **một file cho mỗi sơ đồ** (giống các hình trong báo cáo):

| File | Sơ đồ |
|---|---|
| `01-usecase.xml` | Use case tổng thể |
| `02-domain-packages.xml` | Sơ đồ package các bounded context, phụ thuộc «use» |
| `03-domain-overview.xml` | Sơ đồ domain tổng thể (34 entity / aggregate root, 56 association) |
| `04-class-identity.xml` … `12-class-shared.xml` | Sơ đồ lớp từng bounded context (thuộc tính + phương thức) |

Mọi file domain (`02`–`12`) đều chứa **toàn bộ** domain model với cùng Id, chỉ khác sơ đồ. Import nhiều
file vào **cùng một project trống** thì Visual Paradigm gộp chung một model, mỗi file thêm một sơ đồ.
Phương thức (operation) trong lớp được rút ra từ use case và trạng thái của aggregate
(xem `OPS` trong `docs/report/generator/classdiag.py`).

## Domain model: cấu trúc

Mô hình viết theo kiểu DDD. Mỗi package là một bounded context:

| Package | Nội dung chính |
|---|---|
| **Identity & Access** | UserAccount (aggregate root), UserProfile, UserAddress, Role, Permission |
| **Partner** | Merchant (aggregate root), MerchantPickupAddress |
| **Network** | NetworkRegion, ServiceArea, ServiceAreaCoverage, Hub, HubLane, LaneSchedule, RouteTemplate, RouteTemplateLeg, LaneCapacityReservation |
| **Pricing** | RatePlan, RateRule, QuoteRequest, QuoteParcel, QuoteOption, cùng các value object CodPolicy, InsurancePolicy, ShipmentLimits, QuoteEndpoint |
| **Shipment** | Shipment (aggregate root), Parcel, ShipmentEvent, DeliveryAttempt, cùng các value object ShipmentAddressSnapshot, ShipmentEndpoint, ShipmentHold |
| **Exception Handling** | ShipmentExceptionRequest, ShipmentCase, ShipmentWeightAdjustment |
| **Workforce & Operations** | WorkforceMember, HubMembership, WorkforceAvailability, WorkShift, WorkforceShiftAssignment, OperationalAssignment |
| **Shared Kernel** | Các value object dùng chung: ContactAddress, Weight, Dimensions, FeeBreakdown, DeliveryEstimate, GeoPoint, DayOfWeek |

Sơ đồ duy nhất **PAVEX Domain Model** xếp các entity theo cụm bounded context; bên dưới là các value object
và enumeration. Các package vẫn giữ trong cây model (Model Explorer) để tra cứu.

Quy ước:

- Mỗi class mang một stereotype: `«aggregate root»`, `«entity»`, `«value object»` hoặc `«enumeration»`.
- Quan hệ giữa các entity vẽ bằng **association**, có ghi tên vai trò (role) và bội số. Vì vậy các thuộc tính
  khóa ngoại kiểu `xxxId` (`merchantId`, `hubId`, `laneId`…) bị bỏ khỏi class.
- **Composition ◆** nghĩa là phần đó sống và chết theo aggregate.
  Ví dụ: Shipment ◆ Parcel / ShipmentEvent / DeliveryAttempt, RatePlan ◆ RateRule, QuoteRequest ◆ QuoteParcel / QuoteOption.
- Tham chiếu tới người thực hiện (`createdByUserId`, `reviewedByUserId`, `actorUserId`…) vẫn giữ dạng UUID,
  vì DDD tham chiếu aggregate khác bằng định danh.
- Bỏ các cột kỹ thuật: `updatedAt`, `version`, `authorizationVersion`, outbox, token.
- Các cụm thuộc tính lặp lại được gom thành value object. Ví dụ: `weight : Weight`, `fees : FeeBreakdown`,
  `sender/recipient : ShipmentAddressSnapshot`, và 7 cờ `monday…sunday` gộp thành `operatingDays : DayOfWeek[1..7]`.

So với bản `.vpp` cũ, ngoài việc tái cấu trúc còn sửa và bổ sung:

- `ShipmentCase → Shipment` sửa từ `1` thành `0..1` (vì shipmentId được phép null).
- `HubLane` có đủ 2 đầu `fromHub`/`toHub`; `RouteTemplate` có đủ `originHub`/`destinationHub`.
- Thêm các quan hệ: `Shipment → originHub/destinationHub`, `Parcel → currentHub/currentAssignment`,
  `ShipmentEvent → Hub`, `UserAccount → WorkforceMember`, `ShipmentWeightAdjustment/ExceptionRequest → Hub`.
- Mỗi enum giờ là một class `«enumeration»` có liệt kê đủ giá trị, thay vì DataType rỗng.

## Use case model: cấu trúc

- **Tác nhân**: giữ đúng **10 vai trò** như trong backend (theo diagram gốc): Guest, Customer, Merchant,
  Platform Admin, Hub Dispatch, Hub Manager, Operations Manager, Courier, Line-haul Driver, Warehouse Operator.
  Chỉ thêm tác nhân **abstract** khi các vai trò **cùng một frame** dùng chung chức năng:

  | Frame | Tác nhân abstract | Kế thừa | Chức năng dùng chung |
  |---|---|---|---|
  | Cổng khách hàng | *Public User* | Guest, Customer (Merchant kế thừa Customer) | Tra cứu vận đơn, giá cước, bưu cục |
  | Cổng quản trị | *Back-office User* | Platform Admin, Hub Dispatch, Operations Manager (Hub Manager kế thừa Hub Dispatch) | Đăng nhập, Đăng xuất, Hồ sơ cá nhân, Xem tổng quan vận hành |
  | Ứng dụng vận hành | *Operational Staff* | Courier, Line-haul Driver, Warehouse Operator | Đăng nhập, ca làm việc, công việc được giao, quét kiện, báo sự cố |

  Các nhóm trung gian cũ không ứng với vai trò backend (Authenticated User, Operations Supervisor, Hub Coordinator,
  Workforce Manager) đã bị bỏ.
- **Sơ đồ duy nhất PAVEX Use Case Diagram** gồm: tất cả tác nhân cùng quan hệ kế thừa (vẽ ở hai mép ngoài),
  41 use case chính trong ranh giới *PAVEX Logistics Platform*, và các quan hệ «include»/«extend».
  Các use case tài khoản dùng chung cho 3 frame được đặt ở giữa, ngang hàng với Customer, Back-office User
  và Operational Staff.
- Chỉ 3 chức năng con được tách thành use case riêng vì nhiều use case dùng chung:
  *Xem hành trình vận đơn* (Tra cứu vận đơn, Theo dõi vận đơn «include»),
  *Quét kiện hàng* (Thực hiện công việc, Bàn giao hàng trung chuyển «include»),
  *Gán vai trò cho người dùng* («extend» Quản lý người dùng, «include» từ Tạo tài khoản nhân viên).
  Các chức năng con khác (thêm/sửa/xóa, duyệt/từ chối…) được ghi trong **Documentation** của use case gốc
  và trong `usecase-catalog.md`.
- Mỗi use case có **User ID** (UC01…UC42) và phần **Documentation** ghi: nhóm, tác nhân, mô tả,
  tiền điều kiện, các entity domain liên quan và danh sách chức năng con.

## ⚠️ Giả định cần đối chiếu

Repo `pavex-backend` hiện chỉ có README, chưa có mã nguồn. Mô hình được dựng từ ba nguồn:

1. Domain model trong `pavex_domain.vpp` cũ. File này ghi rõ là *"aligned with PAVEX backend entities"*, nên dùng làm nguồn chính.
2. Contract API trong `pavex-management-portal` (nhánh `develop`): `/api/v1/auth/*`, `/users`, `/roles`,
   AccountStatus, ProfileStatus, các mã quyền `identity.users.read` / `identity.roles.read`.
3. Use case diagram v2 cũ.

**Giá trị của các enum** chỉ xác nhận được cho `AccountStatus` và `ProfileStatus`. Các enum còn lại
(MerchantStatus, ShipmentStatus, ParcelStage, HubType…) được **suy luận** từ tên thuộc tính và nghiệp vụ.
Cần đối chiếu với enum trong backend, rồi sửa trong `generator/build_domain.py` và chạy lại script.

## Sinh lại các file

```bash
cd docs/uml/generator
python3 build_domain.py     # tạo pavex-domain-model.xml, domain-catalog.md, preview/domain-model.svg
python3 build_usecase.py    # tạo pavex-usecase-model.xml, usecase-catalog.md, preview/usecase-diagram.svg
python3 build_xml.py        # tạo xml/: mỗi sơ đồ một file (chạy sau build_usecase.py)
```

Script chỉ dùng thư viện chuẩn của Python 3. Ảnh PNG trong `preview/` được render từ file SVG bằng Chromium headless.
