"""Report content as a list of blocks consumed by build_docx.cjs (same chapter layout as the earlier report)."""

from __future__ import annotations

import json
import pathlib
import sys

from specs import CORE
from tests import GROUPS, flat

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
FIG = ROOT / "figures"
UML = ROOT.parent / "uml"
sys.path.insert(0, str(UML / "generator"))

B: list[dict] = []
fig_no: dict[int, int] = {}
tab_no: dict[int, int] = {}
chapter = 0


def h1(t):
    global chapter
    chapter += 1
    B.append({"t": "h1", "text": f"CHƯƠNG {chapter}. {t}"})


def h2(t):
    B.append({"t": "h2", "text": t})


def h3(t):
    B.append({"t": "h3", "text": t})


def p(*ts):
    for t in ts:
        B.append({"t": "p", "text": t})


def bullets(items):
    B.append({"t": "bullets", "items": items})


def fig(path, caption, width_cm=15.5, max_h_cm=21.5):
    fig_no[chapter] = fig_no.get(chapter, 0) + 1
    B.append({"t": "img", "path": str(path), "w": width_cm, "maxh": max_h_cm,
              "caption": f"Hình {chapter}.{fig_no[chapter]}. {caption}"})


def placeholder(caption, note):
    fig_no[chapter] = fig_no.get(chapter, 0) + 1
    B.append({"t": "placeholder", "text": note, "caption": f"Hình {chapter}.{fig_no[chapter]}. {caption}"})


def table(header, rows, widths, caption=None, size=11):
    if caption:
        tab_no[chapter] = tab_no.get(chapter, 0) + 1
        B.append({"t": "caption", "text": f"Bảng {chapter}.{tab_no[chapter]}. {caption}"})
    B.append({"t": "table", "header": header, "rows": rows, "widths": widths, "size": size})


# ---------------------------------------------------------------------------
B.append({"t": "cover"})
B.append({"t": "front", "text": "LỜI CẢM ƠN"})
p("Lời đầu tiên, nhóm thực hiện đề tài xin gửi lời cảm ơn chân thành đến Ban Giám hiệu Trường Đại học Công nghiệp Thành phố Hồ Chí Minh và Ban lãnh đạo Khoa Công nghệ Thông tin đã tạo điều kiện về cơ sở vật chất và môi trường học tập để nhóm hoàn thành đồ án.",
  "Đặc biệt, nhóm xin bày tỏ lòng biết ơn sâu sắc tới Thầy Võ Ngọc Tấn Phước – người đã trực tiếp giảng dạy, tận tình hướng dẫn và đưa ra những định hướng quan trọng cho nhóm trong suốt quá trình thực hiện môn học Công nghệ mới trong phát triển ứng dụng CNTT. Những góp ý và yêu cầu nghiêm túc của Thầy đã giúp nhóm hoàn thiện hệ thống PAVEX một cách bài bản.",
  "Mặc dù đã nỗ lực hoàn thành báo cáo với tất cả sự nghiêm túc, do giới hạn về thời gian và kinh nghiệm thực tế, đồ án không tránh khỏi những thiếu sót. Nhóm kính mong nhận được những ý kiến góp ý của quý Thầy/Cô để đề tài được hoàn thiện hơn.")
B.append({"t": "front", "text": "MỤC LỤC"})
B.append({"t": "toc"})

# ---------------------------------------------------------------------------
h1("GIỚI THIỆU")
h2("1.1. Lý do chọn đề tài")
p("Thương mại điện tử tăng trưởng mạnh kéo theo nhu cầu vận chuyển hàng hóa nhanh, minh bạch và có thể theo dõi theo thời gian thực. Với một doanh nghiệp chuyển phát, mỗi đơn hàng phải đi qua nhiều công đoạn: lấy hàng tại người gửi, nhập hub, phân loại, trung chuyển giữa các hub, giao chặng cuối và có thể phát sinh hoàn hàng, đổi địa chỉ hoặc sự cố. Nếu các công đoạn này được quản lý thủ công hoặc rời rạc, doanh nghiệp rất khó kiểm soát vị trí kiện hàng, năng lực tuyến vận chuyển và trách nhiệm của từng nhân sự.",
  "Xuất phát từ thực tế đó, nhóm thực hiện đề tài “Xây dựng hệ thống quản lý vận chuyển PAVEX”: một nền tảng gồm cổng khách hàng, cổng quản trị và ứng dụng vận hành dùng chung một backend, quản lý toàn bộ vòng đời vận đơn từ báo giá, tạo đơn, định tuyến, điều phối công việc đến giao hàng và xử lý ngoại lệ.")
h2("1.2. Mục tiêu đề tài")
bullets([
    "Xây dựng hệ thống gồm 3 phân hệ: Cổng khách hàng (khách vãng lai, khách hàng, chủ cửa hàng), Cổng quản trị (quản trị nền tảng, điều phối viên, trưởng hub, quản lý vận hành) và Ứng dụng vận hành (bưu tá, tài xế trung chuyển, nhân viên kho).",
    "Tính cước tự động theo bảng giá có phiên bản: vùng giá, khối lượng quy đổi, phụ phí vùng xa/hải đảo, phí thu hộ (COD) và bảo hiểm.",
    "Tự động định tuyến vận đơn qua mạng lưới hub, giữ chỗ tải trọng trên từng chuyến và sinh công việc vận hành cho từng chặng.",
    "Quét mã kiện hàng để ghi nhận chuyển giao trách nhiệm (custody), vị trí và hành trình của từng kiện.",
    "Phân quyền chi tiết theo vai trò (RBAC) cho từng chức năng, bảo mật bằng JWT.",
])
h2("1.3. Đối tượng và phạm vi nghiên cứu")
bullets([
    "Đối tượng: doanh nghiệp chuyển phát, các hub/bưu cục, nhân sự vận hành và khách hàng gửi hàng (cá nhân và cửa hàng).",
    "Phạm vi: backend cung cấp REST API cho toàn hệ thống; cổng khách hàng và cổng quản trị trên nền web; ứng dụng vận hành tối ưu cho thiết bị di động. Hệ thống quản lý vận chuyển nội địa theo đơn vị hành chính 2 cấp (tỉnh/thành – phường/xã).",
])
h2("1.4. Phương pháp nghiên cứu")
bullets([
    "Phân tích, thiết kế hướng đối tượng với UML: sơ đồ use case, đặc tả use case, sơ đồ hoạt động (activity), sơ đồ tuần tự (sequence) và mô hình miền (domain model).",
    "Thiết kế hướng miền (Domain-Driven Design): chia hệ thống thành các bounded context, xác định aggregate, entity và value object.",
    "Phát triển theo mô hình Agile/Scrum, quản lý mã nguồn bằng Git.",
    "Kiểm thử hộp đen (black-box testing) dựa trên use case.",
])
h2("1.5. Bố cục báo cáo")
bullets([
    "Chương 1 – Giới thiệu: lý do, mục tiêu, phạm vi và phương pháp.",
    "Chương 2 – Cơ sở lý thuyết: bài toán vận chuyển, DDD, REST/JWT, kiến trúc web và mã kiện hàng.",
    "Chương 3 – Phân tích yêu cầu: yêu cầu kinh doanh, người dùng, chức năng và phi chức năng.",
    "Chương 4 – Thiết kế hệ thống: sơ đồ use case, đặc tả use case cốt lõi kèm activity và sequence, mô hình miền.",
    "Chương 5 – Giải pháp công nghệ: kiến trúc, bảo mật, định giá, định tuyến và quét mã.",
    "Chương 6 – Hiện thực, triển khai và kiểm thử: môi trường, giao diện và bộ test case.",
    "Chương 7 – Đánh giá và thảo luận. Chương 8 – Kết luận và hướng phát triển.",
])

# ---------------------------------------------------------------------------
h1("CƠ SỞ LÝ THUYẾT")
h2("2.1. Bài toán vận chuyển chặng đầu – chặng giữa – chặng cuối")
p("Một vận đơn trong mạng lưới chuyển phát đi qua ba chặng: chặng đầu (first mile) lấy hàng từ người gửi về hub gốc; chặng giữa (middle mile / line-haul) trung chuyển giữa các hub theo tuyến và lịch chạy cố định; chặng cuối (last mile) giao từ hub đích đến người nhận. Mô hình hub-and-spoke giúp gom hàng theo tuyến để tối ưu chi phí, nhưng đòi hỏi hệ thống phải biết năng lực (tải trọng) của từng chuyến và vị trí của từng kiện tại mọi thời điểm. Chặng cuối là chặng tốn kém và dễ phát sinh ngoại lệ nhất (không liên lạc được người nhận, sai địa chỉ, từ chối nhận), vì vậy cần cơ chế ghi nhận lần giao và xử lý ngoại lệ rõ ràng.")
h2("2.2. Thiết kế hướng miền (Domain-Driven Design)")
p("Domain-Driven Design tổ chức phần mềm xoay quanh nghiệp vụ. Hệ thống được chia thành các bounded context – mỗi context có mô hình và ngôn ngữ riêng. Trong mỗi context, aggregate root là thực thể gốc bảo vệ tính nhất quán của một cụm dữ liệu (ví dụ Shipment cùng các Parcel, ShipmentEvent, DeliveryAttempt); entity có định danh riêng; value object là giá trị bất biến không có định danh (ví dụ Weight, FeeBreakdown, ContactAddress). Các aggregate khác nhau tham chiếu nhau bằng định danh, giúp mỗi phần của hệ thống có thể phát triển độc lập.")
h2("2.3. REST API, JWT và phân quyền theo vai trò")
p("REST API cung cấp tài nguyên qua HTTP với các phương thức GET, POST, PUT, PATCH, DELETE và được phiên bản hóa theo đường dẫn (/api/v1/...). JSON Web Token (JWT) cho phép xác thực không trạng thái: sau khi đăng nhập, máy chủ cấp access token có thời hạn ngắn và refresh token có thời hạn dài hơn; client gửi access token trong header Authorization: Bearer cho mỗi yêu cầu và dùng refresh token để lấy token mới khi hết hạn. Phân quyền theo vai trò (RBAC) gán quyền nguyên tử (ví dụ identity.users.read) cho vai trò, rồi gán vai trò cho tài khoản.")
h2("2.4. Ứng dụng web hiện đại và mô hình Backend-for-Frontend")
p("Các cổng web được xây dựng bằng Next.js (React, TypeScript). Next.js cho phép kết hợp giao diện chạy trên trình duyệt với các route chạy trên máy chủ. PAVEX tận dụng điều này theo mô hình Backend-for-Frontend (BFF): trình duyệt chỉ gọi các route nội bộ của cổng web, còn route này giữ token trong cookie httpOnly, đính kèm token khi chuyển tiếp yêu cầu đến backend và tự động làm mới token khi nhận lỗi 401. Nhờ vậy token không bị lộ cho mã JavaScript phía trình duyệt.")
h2("2.5. Mã kiện hàng và chuỗi giám sát (chain of custody)")
p("Mỗi kiện hàng được gắn mã (mã vạch hoặc QR) in trên nhãn. Khi nhân sự quét mã ở mỗi công đoạn, hệ thống ghi nhận ai đang giữ kiện (custody), kiện đang ở hub nào và ở công đoạn nào, đồng thời thêm một sự kiện vào hành trình của vận đơn. Chuỗi các lần quét tạo thành chuỗi giám sát giúp truy vết trách nhiệm khi xảy ra thất lạc hoặc hư hỏng.")
h2("2.6. Kiểm thử hộp đen")
p("Kiểm thử hộp đen kiểm tra phần mềm dựa trên đặc tả mà không cần biết mã nguồn. Các kỹ thuật được dùng gồm phân vùng tương đương (dữ liệu hợp lệ/không hợp lệ), phân tích giá trị biên (khối lượng tối đa, mức COD tối đa) và kiểm thử theo luồng use case (luồng chính và luồng thay thế).")

# ---------------------------------------------------------------------------
h1("PHÂN TÍCH YÊU CẦU")
h2("3.1. Phân tích yêu cầu kinh doanh")
p("Doanh nghiệp cần một hệ thống quản lý vận chuyển khép kín: khách hàng tự tra cứu giá và tạo đơn; mạng lưới hub, tuyến và lịch chạy được cấu hình tập trung; vận đơn được định tuyến và giữ chỗ tải tự động; công việc tại từng hub được điều phối cho đúng nhân sự trong ca; mọi lần chuyển giao kiện hàng đều được ghi nhận; các ngoại lệ (hoàn hàng, đổi địa chỉ, giao thất bại, sự cố, sai lệch khối lượng) được xử lý theo quy trình có kiểm soát. Bảng giá phải có phiên bản để mọi vận đơn giữ đúng giá tại thời điểm tạo.")
h2("3.2. Phân tích yêu cầu người dùng")
p("Hệ thống phục vụ 10 vai trò, thuộc 3 frame (phân hệ). Các vai trò cùng frame có chức năng dùng chung được gom vào một tác nhân trừu tượng để kế thừa.")
table(["Frame", "Tác nhân", "Nhu cầu chính"], [
    ["Cổng khách hàng", "Public User (trừu tượng)", "Tra cứu vận đơn, giá cước, bưu cục mà không cần đăng nhập"],
    ["", "Guest", "Đăng ký tài khoản"],
    ["", "Customer", "Đăng nhập, hồ sơ, sổ địa chỉ, đăng ký cửa hàng"],
    ["", "Merchant", "Tạo và quản lý đơn hàng, theo dõi vận đơn, yêu cầu xử lý ngoại lệ"],
    ["Cổng quản trị", "Back-office User (trừu tượng)", "Đăng nhập, hồ sơ, bảng điều hành"],
    ["", "Platform Admin", "Quản trị người dùng, vai trò, cửa hàng, bảng giá, mạng lưới"],
    ["", "Hub Dispatch", "Điều phối công việc, theo dõi vận đơn, xử lý ngoại lệ, tạo đơn tại quầy"],
    ["", "Hub Manager", "Kế thừa Hub Dispatch; quản lý nhân sự, ca làm việc, duyệt điều chỉnh khối lượng"],
    ["", "Operations Manager", "Nhân sự, ca làm việc, giám sát vận đơn, hồ sơ sự cố"],
    ["Ứng dụng vận hành", "Operational Staff (trừu tượng)", "Ca làm việc, check-in/out, công việc được giao, quét kiện, báo sự cố"],
    ["", "Courier", "Lấy hàng và giao hàng chặng cuối"],
    ["", "Line-haul Driver", "Trung chuyển hàng giữa các hub"],
    ["", "Warehouse Operator", "Nhập/xuất hub, phân loại, cân lại kiện"],
], [2300, 2600, 3406], "Tác nhân của hệ thống")
h2("3.3. Yêu cầu chức năng hệ thống")
p("Bảng sau liệt kê toàn bộ 41 chức năng (use case mức người dùng) của hệ thống, nhóm theo phân hệ. Các use case con (thêm, sửa, xóa, duyệt, từ chối…) được mô tả trong phần đặc tả và bộ test case.")
import build_usecase as BU  # noqa: E402  (use case model from docs/uml)

rows = []
for gcode, gname, ucs in BU.GROUPS:
    for code, name, actors, desc, data, subs in ucs:
        rows.append([code, name, ", ".join(actors), desc])
table(["Mã", "Chức năng", "Tác nhân", "Mô tả"], rows, [800, 2000, 1700, 3806], "Danh sách chức năng của hệ thống", size=10)
h2("3.4. Yêu cầu phi chức năng hệ thống")
bullets([
    "Bảo mật: mật khẩu được mã hóa một chiều; xác thực bằng JWT (access/refresh token); token phía web được giữ trong cookie httpOnly; mọi API kiểm tra quyền theo RBAC.",
    "Toàn vẹn dữ liệu: khóa lạc quan (version) khi cập nhật để tránh ghi đè; khóa chống lặp (Idempotency-Key) cho các thao tác tạo mới.",
    "Hiệu năng: tra cứu vận đơn và tính cước phản hồi dưới 1 giây trong điều kiện bình thường; danh sách lớn được phân trang.",
    "Khả dụng: giao diện responsive trên máy tính và điện thoại; ứng dụng vận hành tối ưu cho thao tác quét mã.",
    "Truy vết: mọi thay đổi trạng thái quan trọng đều ghi người thao tác và thời điểm; lỗi trả về kèm mã lỗi và traceId.",
    "Khả năng mở rộng: chia module theo bounded context; API phiên bản hóa để mở rộng mà không phá vỡ client cũ.",
])

# ---------------------------------------------------------------------------
h1("THIẾT KẾ HỆ THỐNG")
h2("4.1. Sơ đồ use case tổng thể")
p("Sơ đồ use case tổng thể gồm 13 tác nhân (10 vai trò và 3 tác nhân trừu tượng theo frame) và 41 use case trong ranh giới hệ thống PAVEX Logistics Platform. Quan hệ «include» thể hiện bước bắt buộc dùng chung (Xem hành trình vận đơn, Quét kiện hàng, Gán vai trò cho người dùng, Tra cứu giá cước, Xem nhân sự vận hành); quan hệ «extend» thể hiện chức năng mở rộng tùy chọn.")
fig(UML / "preview" / "usecase-diagram.png", "Sơ đồ use case tổng thể hệ thống PAVEX", 16, 22.5)
h2("4.2. Đặc tả chi tiết các use case cốt lõi")
p("Phần này đặc tả 12 use case cốt lõi – các luồng nghiệp vụ chính của hệ thống. Mỗi use case gồm bảng đặc tả, sơ đồ hoạt động và sơ đồ tuần tự. Trong sơ đồ tuần tự, lớp giao diện được đặt tiền tố GD_, lớp điều khiển CTRL_, lớp thực thể dùng tên trong mô hình miền.")
sections = [("4.2.1. Nhóm use case dùng chung và cổng khách hàng", ["UC01", "UC03", "UC15", "UC17", "UC14 / UC19"]),
            ("4.2.2. Nhóm use case của cổng quản trị", ["UC13", "UC31", "UC40", "UC25", "UC06 / UC08"]),
            ("4.2.3. Nhóm use case của ứng dụng vận hành", ["UC33 / UC34", "UC35"])]
by_code = {u["code"]: u for u in CORE}
k = 0
for title, codes in sections:
    h3(title)
    for code in codes:
        u = by_code[code]
        k += 1
        B.append({"t": "h4", "text": f"Use case {u['name'].lower()} ({u['code']})"})
        B.append({"t": "spec", "uc": u})
        slug = u["code"].split(" ")[0].lower()
        fig(FIG / f"{slug}-act.png", f"Sơ đồ hoạt động – {u['name']}", 15, 20)
        fig(FIG / f"{slug}-seq.png", f"Sơ đồ tuần tự – {u['name']}", 16, 18)

h2("4.3. Thiết kế kiến trúc và cơ sở dữ liệu")
h3("4.3.1. Mô hình miền")
p("Mô hình miền được thiết kế theo DDD gồm 8 package (bounded context): Identity & Access, Partner, Network, Pricing, Shipment, Exception Handling, Workforce & Operations và Shared Kernel. Quan hệ giữa các entity được biểu diễn bằng association có tên vai trò và bội số; composition (hình thoi đặc) thể hiện phần tử sống và mất cùng aggregate.")
fig(FIG / "domain-entities.png", "Mô hình miền – các entity và aggregate root", 16, 22)
table(["Bounded context", "Aggregate root / entity chính", "Vai trò"], [
    ["Identity & Access", "UserAccount, UserProfile, UserAddress, Role, Permission", "Tài khoản, hồ sơ, sổ địa chỉ, RBAC"],
    ["Partner", "Merchant, MerchantPickupAddress", "Cửa hàng và vòng đời xác minh"],
    ["Network", "NetworkRegion, ServiceArea, ServiceAreaCoverage, Hub, HubLane, LaneSchedule, RouteTemplate, LaneCapacityReservation", "Mạng lưới, tuyến, lịch chạy, giữ chỗ tải"],
    ["Pricing", "RatePlan, RateRule, QuoteRequest, QuoteParcel, QuoteOption", "Bảng giá có phiên bản, báo giá có hạn"],
    ["Shipment", "Shipment, Parcel, ShipmentEvent, DeliveryAttempt", "Vận đơn, kiện, hành trình, lần giao"],
    ["Exception Handling", "ShipmentExceptionRequest, ShipmentCase, ShipmentWeightAdjustment", "Yêu cầu ngoại lệ, sự cố, điều chỉnh khối lượng"],
    ["Workforce & Operations", "WorkforceMember, HubMembership, WorkforceAvailability, WorkShift, WorkforceShiftAssignment, OperationalAssignment", "Nhân sự, ca làm việc, công việc vận hành"],
    ["Shared Kernel", "ContactAddress, Weight, Dimensions, FeeBreakdown, DeliveryEstimate, GeoPoint", "Value object dùng chung"],
], [2000, 3606, 2700], "Các bounded context của mô hình miền", size=10)
h3("4.3.2. Kiến trúc tổng thể")
p("Hệ thống theo kiến trúc client – server nhiều lớp. Ba ứng dụng client (cổng khách hàng, cổng quản trị, ứng dụng vận hành) giao tiếp với một backend duy nhất qua REST API /api/v1. Backend được tổ chức theo module tương ứng với các bounded context, mỗi module gồm các lớp: API (controller), ứng dụng (service), miền (domain) và hạ tầng (repository). Dữ liệu được lưu trong cơ sở dữ liệu quan hệ; mỗi aggregate tương ứng một nhóm bảng.")
table(["Lớp", "Thành phần", "Trách nhiệm"], [
    ["Client", "Cổng khách hàng, Cổng quản trị (Next.js), Ứng dụng vận hành", "Giao diện người dùng, gọi API qua BFF"],
    ["BFF", "Route /api/backend/* của từng cổng web", "Giữ token trong cookie httpOnly, chuyển tiếp yêu cầu, tự làm mới token"],
    ["API", "REST controller /api/v1/...", "Xác thực JWT, kiểm tra quyền, kiểm tra dữ liệu đầu vào"],
    ["Ứng dụng / miền", "Service, aggregate, quy tắc nghiệp vụ", "Tính cước, định tuyến, điều phối, chuyển trạng thái"],
    ["Hạ tầng", "Repository, cơ sở dữ liệu quan hệ", "Lưu trữ, truy vấn, khóa lạc quan"],
], [1500, 3200, 3606], "Các lớp trong kiến trúc hệ thống", size=10)

# ---------------------------------------------------------------------------
h1("GIẢI PHÁP CÔNG NGHỆ")
p("Chương này trình bày các giải pháp kỹ thuật cốt lõi giúp PAVEX đáp ứng các yêu cầu nghiệp vụ: kiến trúc BFF, bảo mật, định giá có phiên bản, định tuyến với giữ chỗ tải và quét mã kiện hàng.")
h2("5.1. Kiến trúc client – BFF – backend")
p("Trình duyệt không gọi trực tiếp backend. Mỗi cổng web có một route BFF (/api/backend/[...path]) chạy trên máy chủ Next.js: route này đọc access token từ cookie httpOnly, gắn header Authorization: Bearer, chuyển tiếp nguyên phương thức, tham số truy vấn, nội dung và header Idempotency-Key đến backend /api/v1/... Khi backend trả 401, BFF dùng refresh token để lấy token mới, gửi lại yêu cầu và cập nhật cookie; nếu làm mới thất bại thì xóa phiên và phát sự kiện hết phiên để giao diện chuyển về trang đăng nhập. Một middleware kiểm tra cookie phiên và chuyển hướng người dùng chưa đăng nhập đến /login kèm tham số returnTo.")
h2("5.2. Xác thực, phân quyền và toàn vẹn dữ liệu")
bullets([
    "Xác thực: POST /api/v1/auth/login trả accessToken, refreshToken, expiresIn; /auth/refresh cấp token mới; /auth/me trả thông tin người dùng kèm danh sách vai trò và quyền; /auth/logout thu hồi refresh token.",
    "Phân quyền: quyền có mã dạng <context>.<tài nguyên>.<hành động> (ví dụ identity.users.read, identity.roles.read). Giao diện ẩn menu khi người dùng thiếu quyền, backend luôn kiểm tra lại và trả 403.",
    "Khóa lạc quan: các bản ghi có trường version; khi hai người cùng sửa, người lưu sau nhận lỗi xung đột kèm conflictVersion để tải lại dữ liệu.",
    "Lỗi chuẩn hóa: lỗi trả về dạng problem detail gồm message, code, traceId và fieldErrors cho từng trường, giúp giao diện hiển thị lỗi tại đúng ô nhập.",
])
h2("5.3. Định giá có phiên bản và báo giá có thời hạn")
p("Bảng giá (RatePlan) có mã và số phiên bản (revision). Mỗi lần thay đổi giá sẽ tạo một revision mới ở trạng thái DRAFT; khi kích hoạt, revision cũ chuyển RETIRED, đảm bảo luôn chỉ có một bảng giá đang áp dụng. Mỗi bảng giá gồm các quy tắc (RateRule) theo cặp mức dịch vụ × vùng giá, cùng chính sách COD, bảo hiểm và giới hạn nhận gửi.",
  "Khi tính cước, hệ thống phân giải điểm gửi và điểm nhận thành khu vực phục vụ, hub chính và hạng phủ sóng; suy ra vùng giá; tính khối lượng tính cước = max(khối lượng thực, khối lượng quy đổi theo thể tích / hệ số quy đổi) rồi áp dụng phí cơ bản, phí theo bước khối lượng, phụ phí vùng xa/hải đảo, phí COD và phí bảo hiểm. Kết quả được lưu thành QuoteRequest có thời hạn (quoteTtlMinutes). Vận đơn tạo từ báo giá lưu lại ảnh chụp (snapshot) giá và phiên bản bảng giá, nên thay đổi bảng giá sau này không ảnh hưởng đơn cũ.")
h2("5.4. Định tuyến và giữ chỗ tải trọng")
p("Mạng lưới gồm hub, tuyến có hướng giữa hai hub (HubLane) và lịch khởi hành định kỳ của tuyến (LaneSchedule) kèm tải trọng mỗi chuyến. Khi tạo đơn, hệ thống chọn mẫu lộ trình (RouteTemplate) phù hợp giữa hub gốc và hub đích – hoặc tuyến trực tiếp – rồi với mỗi chặng tìm chuyến khởi hành gần nhất còn đủ tải và tạo bản ghi giữ chỗ (LaneCapacityReservation). Các bản ghi giữ chỗ của một lần định tuyến được gom theo batchId; khi đơn bị hủy hoặc định tuyến lại, giữ chỗ cũ được giải phóng. Nếu không tìm được lộ trình, vận đơn được đánh dấu ROUTING_FAILED và đưa vào danh sách ngoại lệ cho điều phối viên.")
h2("5.5. Điều phối công việc theo ca và năng lực nhân sự")
p("Mỗi chặng của vận đơn sinh ra công việc vận hành (OperationalAssignment) tại một hub với loại nhân sự yêu cầu. Khi điều phối, hệ thống chỉ đề xuất nhân sự đúng loại, thuộc hub, đã check-in ca, đang ở trạng thái sẵn sàng và chưa vượt số công việc đồng thời tối đa. Vòng đời công việc gồm PENDING → ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED / FAILED / CANCELLED, mỗi bước đều ghi người thao tác và thời điểm.")
h2("5.6. Quét mã kiện hàng và hành trình vận đơn")
p("Ứng dụng vận hành dùng camera thiết bị để quét mã kiện. Mỗi lần quét, backend kiểm tra kiện thuộc vận đơn của công việc và đúng hub/công đoạn, sau đó cập nhật bên đang giữ kiện (custody), vị trí hiện tại, công đoạn và ghi một ShipmentEvent. Chuỗi ShipmentEvent tạo thành hành trình mà khách hàng nhìn thấy khi tra cứu. Ứng dụng có cơ chế nhập mã thủ công khi không thể dùng camera và cảnh báo khi quét trùng hoặc quét sai kiện.")

# ---------------------------------------------------------------------------
h1("HIỆN THỰC, TRIỂN KHAI VÀ KIỂM THỬ")
h2("6.1. Môi trường phát triển và công cụ")
table(["Thành phần", "Công nghệ / công cụ"], [
    ["Backend", "REST API /api/v1, xác thực JWT, cơ sở dữ liệu quan hệ"],
    ["Cổng quản trị", "Next.js (App Router), React, TypeScript, Tailwind CSS, pnpm"],
    ["Cổng khách hàng", "Next.js, React, TypeScript"],
    ["Ứng dụng vận hành", "Giao diện web tối ưu cho di động, quét mã bằng camera"],
    ["Thiết kế", "Visual Paradigm (use case, domain model, activity, sequence)"],
    ["Quản lý mã nguồn", "Git, GitHub"],
    ["Kiểm thử API", "Postman"],
], [2600, 5706], "Môi trường và công cụ phát triển")
h2("6.2. Giao diện cổng khách hàng")
for cap in ["Trang chủ và tra cứu vận đơn", "Trang tính cước vận chuyển", "Tạo đơn hàng", "Đơn hàng của tôi và chi tiết vận đơn", "Sổ địa chỉ"]:
    placeholder(cap, f"[Chèn ảnh màn hình: {cap}]")
h2("6.3. Giao diện cổng quản trị")
for cap in ["Đăng nhập cổng quản trị", "Bảng điều hành", "Quản lý người dùng", "Vai trò và quyền", "Điều phối công việc", "Yêu cầu ngoại lệ"]:
    placeholder(cap, f"[Chèn ảnh màn hình: {cap}]")
h2("6.4. Giao diện ứng dụng vận hành")
for cap in ["Công việc của tôi", "Quét mã kiện hàng", "Ghi nhận kết quả giao hàng", "Ca làm việc và check-in"]:
    placeholder(cap, f"[Chèn ảnh màn hình: {cap}]")
h2("6.5. Kiểm thử hệ thống")
cases = flat()
p(f"Nhóm áp dụng kiểm thử hộp đen theo use case: mỗi chức năng có ít nhất một ca kiểm thử luồng chính và các ca kiểm thử luồng thay thế, dữ liệu không hợp lệ và giá trị biên. Tổng cộng {len(cases)} ca kiểm thử phủ toàn bộ 41 chức năng. Cột “Kết quả” được nhóm điền khi thực hiện kiểm thử (Đạt / Không đạt).")
summary = []
for g, ucs in GROUPS:
    summary.append([g, str(len(ucs)), str(sum(len(c) for _u, c in ucs))])
summary.append(["Tổng cộng", str(sum(int(r[1]) for r in summary)), str(len(cases))])
table(["Nhóm chức năng", "Số chức năng", "Số test case"], summary, [4706, 1800, 1800], "Tổng hợp số lượng test case")
B.append({"t": "landscape_start"})
tab_no[chapter] = tab_no.get(chapter, 0) + 1
B.append({"t": "caption", "text": f"Bảng {chapter}.{tab_no[chapter]}. Bộ test case chi tiết"})
B.append({"t": "testcases", "rows": [[i, uc, sc, st, ex, ""] for (i, _g, uc, sc, st, ex) in cases],
          "groups": [g for g, _ in GROUPS], "group_of": [g for (_i, g, *_r) in cases]})
B.append({"t": "landscape_end"})

# ---------------------------------------------------------------------------
h1("ĐÁNH GIÁ VÀ THẢO LUẬN")
h2("7.1. Kết quả đạt được")
bullets([
    "Hoàn thành phân tích và thiết kế đầy đủ: sơ đồ use case tổng thể (13 tác nhân, 41 use case), mô hình miền theo DDD (8 bounded context), đặc tả 12 use case cốt lõi kèm sơ đồ hoạt động và tuần tự.",
    "Xây dựng backend REST API với xác thực JWT, refresh token, phân quyền RBAC, khóa lạc quan và lỗi chuẩn hóa.",
    "Xây dựng cổng quản trị theo mô hình BFF: đăng nhập, bảng điều hành, quản lý người dùng, vai trò và quyền.",
    "Thiết kế cơ chế tính cước có phiên bản, định tuyến với giữ chỗ tải và điều phối công việc theo ca.",
    "Xây dựng bộ test case phủ toàn bộ chức năng của hệ thống.",
])
h2("7.2. Hạn chế còn tồn tại")
bullets([
    "Chưa tích hợp cổng thanh toán trực tuyến và đối soát COD tự động.",
    "Định tuyến hiện dựa trên mẫu lộ trình cấu hình sẵn, chưa tối ưu theo thời gian thực hay chi phí.",
    "Chưa có thông báo đẩy (push notification) và chế độ làm việc ngoại tuyến cho ứng dụng vận hành.",
    "Chưa thực hiện kiểm thử tải với số lượng người dùng đồng thời lớn.",
])
h2("7.3. Thảo luận và bài học kinh nghiệm")
p("Việc thiết kế mô hình miền theo DDD ngay từ đầu giúp nhóm tách bạch các phần nghiệp vụ phức tạp (giá, mạng lưới, vận đơn, nhân sự) và giảm phụ thuộc chéo. Nhóm rút ra rằng các quy tắc chuyển trạng thái (vận đơn, công việc, cửa hàng, yêu cầu ngoại lệ) cần được đặc tả rõ ràng trước khi lập trình, và mọi thao tác thay đổi trạng thái phải ghi lại người thao tác để truy vết. Mô hình BFF giúp bảo vệ token nhưng đòi hỏi xử lý cẩn thận việc làm mới phiên.")

# ---------------------------------------------------------------------------
h1("KẾT LUẬN")
h2("8.1. Kết luận")
p("Đồ án đã xây dựng hệ thống quản lý vận chuyển PAVEX với ba phân hệ dùng chung một backend, quản lý toàn bộ vòng đời vận đơn: báo giá, tạo đơn, định tuyến, điều phối, quét kiện, giao hàng và xử lý ngoại lệ. Hệ thống được phân tích, thiết kế bài bản bằng UML và DDD, bảo mật bằng JWT và RBAC, và có bộ test case phủ toàn bộ chức năng.")
h2("8.2. Hướng phát triển")
bullets([
    "Tích hợp cổng thanh toán và đối soát COD với cửa hàng.",
    "Tối ưu tuyến giao chặng cuối (bài toán VRP) và gợi ý lộ trình cho bưu tá.",
    "Cập nhật hành trình theo thời gian thực qua WebSocket và gửi thông báo đẩy.",
    "Ứng dụng di động native cho nhân sự vận hành với chế độ ngoại tuyến.",
    "Phân tích dữ liệu để dự báo sản lượng và điều phối nhân sự theo khu vực.",
])
for t in ["NHẬN XÉT CỦA GIÁO VIÊN HƯỚNG DẪN", "NHẬN XÉT CỦA GIÁO VIÊN PHẢN BIỆN 1", "NHẬN XÉT CỦA GIÁO VIÊN PHẢN BIỆN 2"]:
    B.append({"t": "sign", "text": t})


if __name__ == "__main__":
    (HERE / "content.json").write_text(json.dumps(B, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(B)} blocks, {len(cases)} test cases")
