"""Black-box test cases covering every use case of the PAVEX use case model.

GROUPS: (group title, [(use case code + name, [(scenario, steps & data, expected)])])
"""

GROUPS = [
    ("Xác thực và tài khoản cá nhân", [
        ("UC01 Đăng nhập", [
            ("Đăng nhập thành công (khách hàng)", "Email: kh01@pavex.vn, mật khẩu đúng → nhấn Đăng nhập", "Cấp token, chuyển đến trang chủ cổng khách hàng, hiển thị tên người dùng"),
            ("Đăng nhập thành công (quản trị)", "Tài khoản có vai trò Platform Admin", "Chuyển đến Bảng điều hành; menu hiển thị theo quyền"),
            ("Sai mật khẩu", "Email đúng, mật khẩu sai", "Thông báo “Email hoặc mật khẩu không đúng”, không cấp token"),
            ("Email chưa đăng ký", "Email: khongtontai@pavex.vn", "Thông báo “Email hoặc mật khẩu không đúng”"),
            ("Bỏ trống trường bắt buộc", "Để trống email hoặc mật khẩu", "Báo lỗi tại trường bị trống, không gửi yêu cầu"),
            ("Email sai định dạng", "Email: abc@", "Báo lỗi định dạng email"),
            ("Tài khoản bị tạm khóa", "Tài khoản trạng thái SUSPENDED", "Thông báo tài khoản bị khóa, không cho đăng nhập"),
            ("Tự làm mới phiên", "Đợi access token hết hạn rồi gọi một trang có dữ liệu", "Hệ thống tự refresh token, trang vẫn tải được"),
            ("Refresh token không hợp lệ", "Xóa/sửa refresh token rồi gọi API", "Phiên bị xóa, chuyển về trang Đăng nhập"),
            ("Truy cập trang khi chưa đăng nhập", "Mở trực tiếp /dashboard", "Chuyển hướng đến /login kèm returnTo"),
        ]),
        ("UC01.1 Quên mật khẩu", [
            ("Gửi yêu cầu đặt lại mật khẩu", "Nhập email đã đăng ký", "Thông báo đã gửi email hướng dẫn"),
            ("Đặt lại mật khẩu bằng liên kết", "Mở liên kết hợp lệ, nhập mật khẩu mới hợp lệ", "Đổi mật khẩu thành công, đăng nhập được bằng mật khẩu mới"),
            ("Liên kết hết hạn", "Mở liên kết đã hết hạn", "Thông báo liên kết hết hạn, cho gửi lại"),
        ]),
        ("UC02 Đăng xuất", [
            ("Đăng xuất", "Nhấn Đăng xuất", "Thu hồi refresh token, xóa phiên, về trang Đăng nhập"),
            ("Dùng lại token sau khi đăng xuất", "Gọi API bằng refresh token cũ", "Bị từ chối (401)"),
        ]),
        ("UC03 Đăng ký tài khoản", [
            ("Đăng ký thành công", "Họ tên, email mới, mật khẩu mạnh, xác nhận khớp", "Tạo tài khoản, hồ sơ INCOMPLETE, gửi email xác minh"),
            ("Email đã tồn tại", "Dùng email đã đăng ký", "Thông báo email đã được đăng ký"),
            ("Mật khẩu xác nhận không khớp", "Mật khẩu ≠ xác nhận", "Báo lỗi tại trường xác nhận mật khẩu"),
            ("Mật khẩu yếu", "Mật khẩu: 123", "Báo lỗi yêu cầu độ mạnh mật khẩu"),
            ("Bỏ trống trường bắt buộc", "Để trống họ tên", "Báo lỗi tại trường họ tên"),
            ("Xác minh email", "Mở liên kết xác minh hợp lệ", "emailVerified = true, chuyển đến Đăng nhập"),
        ]),
        ("UC04 Quản lý hồ sơ cá nhân", [
            ("Xem hồ sơ", "Mở trang Hồ sơ", "Hiển thị email, họ tên, vai trò, trạng thái hồ sơ"),
            ("Cập nhật thông tin cá nhân", "Sửa họ, tên, giới tính, ngày sinh → Lưu", "Lưu thành công, hiển thị dữ liệu mới"),
            ("Ngày sinh không hợp lệ", "Ngày sinh ở tương lai", "Báo lỗi ngày sinh"),
            ("Cập nhật ảnh đại diện", "Tải ảnh JPG ≤ 2MB", "Ảnh đại diện được thay đổi"),
            ("Đổi mật khẩu", "Mật khẩu cũ đúng, mật khẩu mới hợp lệ", "Đổi thành công"),
            ("Đổi mật khẩu sai mật khẩu cũ", "Mật khẩu cũ sai", "Thông báo mật khẩu cũ không đúng"),
        ]),
        ("UC05 Quản lý sổ địa chỉ", [
            ("Thêm địa chỉ", "Tên, SĐT, tỉnh/thành, phường/xã, địa chỉ chi tiết", "Địa chỉ mới xuất hiện trong danh sách"),
            ("Thiếu trường bắt buộc", "Bỏ trống phường/xã", "Báo lỗi trường bắt buộc"),
            ("Số điện thoại sai định dạng", "SĐT: 12345", "Báo lỗi số điện thoại"),
            ("Sửa địa chỉ", "Đổi địa chỉ chi tiết → Lưu", "Cập nhật thành công"),
            ("Xóa địa chỉ", "Xóa một địa chỉ → xác nhận", "Địa chỉ bị xóa khỏi danh sách"),
            ("Đặt mặc định", "Chọn “Đặt làm mặc định”", "Chỉ một địa chỉ có nhãn mặc định"),
            ("Vượt số lượng tối đa", "Thêm địa chỉ thứ 21", "Thông báo đã đạt giới hạn"),
        ]),
    ]),
    ("Quản trị người dùng và phân quyền", [
        ("UC06 Quản lý người dùng", [
            ("Xem danh sách", "Mở Người dùng", "Danh sách có phân trang, hiển thị vai trò, trạng thái"),
            ("Tìm theo email/tên", "Từ khóa: “bao”", "Chỉ hiển thị tài khoản khớp"),
            ("Lọc theo vai trò và trạng thái", "Vai trò: Courier; trạng thái: ACTIVE", "Kết quả đúng điều kiện lọc"),
            ("Lọc theo trạng thái hồ sơ", "Hồ sơ: PENDING_VERIFICATION", "Kết quả đúng điều kiện lọc"),
            ("Xem chi tiết", "Chọn một tài khoản", "Hiển thị hồ sơ, vai trò, ngày tạo"),
            ("Tạm khóa tài khoản", "Đổi trạng thái sang SUSPENDED", "Cập nhật thành công; tài khoản đó không đăng nhập được"),
            ("Mở khóa tài khoản", "Đổi SUSPENDED → ACTIVE", "Tài khoản đăng nhập lại được"),
            ("Xung đột dữ liệu", "Hai admin cùng sửa một tài khoản", "Người lưu sau nhận thông báo xung đột (version)"),
            ("Tự khóa chính mình", "Admin khóa tài khoản đang đăng nhập", "Hệ thống không cho phép"),
            ("Không đủ quyền", "Tài khoản không có identity.users.read mở /identity/users", "Thông báo chưa được cấp quyền (403)"),
            ("Xác minh hồ sơ người dùng", "Duyệt hồ sơ PENDING_VERIFICATION", "Hồ sơ chuyển COMPLETED"),
            ("Từ chối hồ sơ người dùng", "Từ chối hồ sơ kèm lý do", "Hồ sơ chuyển REJECTED"),
        ]),
        ("UC07 Tạo tài khoản nhân viên", [
            ("Tạo tài khoản nhân viên", "Họ tên, email mới, vai trò Courier", "Tài khoản được tạo với vai trò đã chọn"),
            ("Email trùng", "Email đã tồn tại", "Báo lỗi email đã tồn tại"),
            ("Không chọn vai trò", "Bỏ trống vai trò", "Báo lỗi phải chọn ít nhất một vai trò"),
        ]),
        ("UC08 Quản lý vai trò và quyền", [
            ("Xem vai trò và quyền", "Mở Vai trò & quyền", "Danh sách vai trò, số quyền mỗi vai trò"),
            ("Tìm quyền", "Tìm “users”", "Hiển thị quyền có mã/tên khớp"),
            ("Tạo vai trò", "Mã, tên, mô tả", "Vai trò mới xuất hiện"),
            ("Phân quyền cho vai trò", "Gán identity.users.read", "Người có vai trò đó thấy menu Người dùng"),
            ("Sửa vai trò hệ thống", "Sửa vai trò isSystem = true", "Không cho phép"),
            ("Xóa vai trò tùy chỉnh", "Xóa vai trò không phải hệ thống", "Xóa thành công"),
        ]),
    ]),
    ("Cửa hàng (Merchant)", [
        ("UC09 Đăng ký cửa hàng", [
            ("Đăng ký cửa hàng", "Tên kinh doanh, người liên hệ, SĐT, MST", "Cửa hàng được tạo, chờ xác minh"),
            ("Thiếu thông tin bắt buộc", "Bỏ trống tên kinh doanh", "Báo lỗi trường bắt buộc"),
            ("Đã có cửa hàng", "Tài khoản đã sở hữu cửa hàng đăng ký lần nữa", "Thông báo mỗi tài khoản chỉ có một cửa hàng"),
        ]),
        ("UC10 Quản lý thông tin cửa hàng", [
            ("Xem thông tin cửa hàng", "Mở Cửa hàng của tôi", "Hiển thị mã, trạng thái, lý do trạng thái"),
            ("Cập nhật thông tin", "Sửa SĐT liên hệ → Lưu", "Cập nhật thành công"),
        ]),
        ("UC11 Quản lý địa chỉ lấy hàng", [
            ("Thêm địa chỉ lấy hàng", "Tên kho, liên hệ, địa chỉ", "Địa chỉ được thêm"),
            ("Đặt địa chỉ lấy hàng mặc định", "Chọn mặc định", "Được dùng làm người gửi mặc định khi tạo đơn"),
            ("Xóa địa chỉ lấy hàng", "Xóa → xác nhận", "Địa chỉ bị xóa"),
        ]),
        ("UC12 Yêu cầu xác minh cửa hàng", [
            ("Gửi yêu cầu xác minh", "Nhấn Gửi xác minh", "Trạng thái PENDING_VERIFICATION"),
            ("Gửi lại khi đang chờ", "Gửi khi đã PENDING_VERIFICATION", "Thông báo yêu cầu đang được xử lý"),
        ]),
        ("UC13 Duyệt và quản lý cửa hàng", [
            ("Lọc cửa hàng chờ xác minh", "Lọc trạng thái PENDING_VERIFICATION", "Chỉ hiện cửa hàng đang chờ"),
            ("Duyệt cửa hàng", "Chọn cửa hàng → Duyệt", "ACTIVE, ghi người duyệt và thời điểm"),
            ("Từ chối cửa hàng", "Chọn lý do MISSING_DOCUMENTS + ghi chú", "REJECTED kèm lý do"),
            ("Tạm ngưng cửa hàng", "Cửa hàng ACTIVE → Tạm ngưng kèm lý do", "SUSPENDED; cửa hàng không tạo được đơn"),
            ("Khôi phục cửa hàng", "SUSPENDED → Khôi phục", "ACTIVE"),
        ]),
    ]),
    ("Tra cứu công khai", [
        ("UC14 Tra cứu vận đơn", [
            ("Tra cứu đúng mã", "Mã vận đơn tồn tại", "Hiển thị trạng thái, tiến trình, timeline"),
            ("Mã không tồn tại", "Mã: PVX000000000", "Thông báo vận đơn không tồn tại"),
            ("Mã trống / sai định dạng", "Để trống", "Báo lỗi nhập mã"),
            ("Ẩn thông tin cá nhân", "Tra cứu khi không đăng nhập", "Ẩn SĐT và địa chỉ chi tiết"),
            ("Xem hành trình", "Vận đơn đã qua nhiều hub", "Sự kiện sắp theo thời gian, có địa điểm"),
        ]),
        ("UC15 Tra cứu giá cước", [
            ("Tính cước nội tỉnh", "Gửi và nhận cùng tỉnh, 1 kg", "Hiển thị các mức dịch vụ, vùng INTRA_PROVINCE"),
            ("Tính cước liên vùng", "Gửi Hà Nội, nhận TP.HCM", "Vùng INTER_REGION, thời gian giao dài hơn"),
            ("Khối lượng quy đổi", "1 kg, kích thước 50×50×50 cm", "Khối lượng tính cước = khối lượng quy đổi"),
            ("Có COD và bảo hiểm", "COD 2.000.000, giá trị 5.000.000", "Có phí COD và phí bảo hiểm"),
            ("Vượt khối lượng tối đa", "Khối lượng lớn hơn giới hạn", "Thông báo vượt giới hạn"),
            ("Ngoài vùng phục vụ", "Phường chưa có khu vực phục vụ", "Thông báo chưa hỗ trợ khu vực"),
            ("Vùng xa / hải đảo", "Điểm nhận hạng REMOTE / ISLAND", "Có phụ phí vùng xa / hải đảo"),
        ]),
        ("UC16 Tra cứu bưu cục", [
            ("Tìm bưu cục theo tỉnh", "Chọn tỉnh", "Danh sách hub công khai trong tỉnh"),
            ("Ẩn hub không công khai", "Hub có publicVisible = false", "Không xuất hiện trong kết quả"),
        ]),
    ]),
    ("Đơn hàng và vận đơn", [
        ("UC17 Tạo đơn hàng", [
            ("Tạo đơn thành công", "Người gửi, người nhận, 1 kiện 2 kg, dịch vụ Tiêu chuẩn", "Sinh mã vận đơn, trạng thái Chờ lấy hàng, có công việc lấy hàng"),
            ("Chọn địa chỉ từ sổ địa chỉ", "Chọn người nhận từ sổ địa chỉ", "Tự điền thông tin người nhận"),
            ("Nhiều kiện", "3 kiện khác khối lượng", "Lưu đủ 3 kiện, tổng khối lượng đúng"),
            ("SĐT người nhận sai", "SĐT: 0123", "Báo lỗi số điện thoại"),
            ("Kiện vượt giới hạn", "Kích thước vượt tối đa", "Báo lỗi vượt giới hạn"),
            ("COD vượt mức tối đa", "COD lớn hơn mức cho phép", "Báo mức COD tối đa"),
            ("Báo giá hết hạn", "Để quá thời hạn báo giá rồi xác nhận", "Hệ thống tính lại cước"),
            ("Không tìm được lộ trình", "Tuyến hết tải trọng", "Đơn được tạo với ROUTING_FAILED, vào danh sách ngoại lệ"),
            ("Cửa hàng chưa xác minh", "Merchant PENDING_VERIFICATION", "Không cho tạo đơn"),
            ("Người trả cước", "Chọn Người nhận trả", "shippingFeePayer = RECIPIENT"),
            ("Tạo đơn tại quầy (Hub Dispatch)", "Chọn khách hàng → nhập thông tin", "Đơn được tạo thay khách hàng"),
        ]),
        ("UC18 Quản lý đơn hàng", [
            ("Xem danh sách đơn", "Mở Đơn hàng của tôi", "Danh sách đơn của cửa hàng"),
            ("Lọc theo trạng thái", "Tab Đang xử lý / Đã giao / Đã hủy", "Kết quả đúng trạng thái"),
            ("Tìm đơn", "Tìm theo mã, tên hoặc SĐT người nhận", "Kết quả khớp"),
            ("Xem chi tiết", "Chọn một đơn", "Hiển thị kiện, cước, COD, trạng thái định tuyến"),
            ("Hủy đơn khi chưa lấy hàng", "Đơn Chờ lấy hàng → Hủy, nhập lý do", "Đơn CANCELLED, giải phóng giữ chỗ tải"),
            ("Hủy đơn đã lấy hàng", "Đơn đã lấy → Hủy", "Không cho hủy, gợi ý yêu cầu ngoại lệ"),
        ]),
        ("UC19 Theo dõi vận đơn", [
            ("Theo dõi đơn của cửa hàng", "Chọn đơn đang vận chuyển", "Hiển thị timeline và vị trí hiện tại"),
            ("Cập nhật trạng thái mới", "Nhân viên quét kiện tại hub mới", "Timeline có thêm sự kiện mới"),
        ]),
        ("UC20 Yêu cầu xử lý ngoại lệ vận đơn", [
            ("Yêu cầu hoàn hàng", "Chọn đơn → Yêu cầu hoàn hàng, nhập lý do", "Tạo yêu cầu PENDING"),
            ("Yêu cầu chuyển tiếp địa chỉ", "Nhập địa chỉ mới", "Yêu cầu có forwardAddress"),
            ("Yêu cầu giao lại", "Đơn giao thất bại → Giao lại", "Tạo yêu cầu REDELIVERY"),
            ("Yêu cầu hủy vận đơn", "Chọn Hủy vận đơn", "Tạo yêu cầu CANCEL_SHIPMENT"),
            ("Thiếu lý do", "Bỏ trống lý do", "Báo lỗi trường bắt buộc"),
        ]),
    ]),
    ("Bảng giá và mạng lưới", [
        ("UC21 Quản lý bảng giá", [
            ("Tạo bảng giá nháp", "Mã, tên, tiền tệ, TTL báo giá", "Bảng giá DRAFT"),
            ("Cấu hình quy tắc giá", "Mức dịch vụ × vùng giá, phí cơ bản, bước khối lượng", "Lưu quy tắc giá"),
            ("Cấu hình COD / bảo hiểm / giới hạn", "Nhập ngưỡng miễn phí, tỉ lệ, mức tối đa", "Lưu chính sách"),
            ("Kích hoạt bảng giá", "DRAFT → Kích hoạt", "ACTIVE; bảng giá cũ RETIRED"),
            ("Tạo phiên bản mới", "Sao chép bảng giá ACTIVE", "Revision mới ở DRAFT"),
            ("Ngừng áp dụng", "ACTIVE → Ngừng", "RETIRED, không dùng để báo giá"),
            ("Thiếu quy tắc giá", "Kích hoạt khi thiếu quy tắc cho một vùng", "Không cho kích hoạt"),
        ]),
        ("UC22 Quản lý mạng lưới", [
            ("Thêm vùng mạng lưới", "Mã, tên vùng", "Vùng mới ACTIVE"),
            ("Thêm khu vực phục vụ", "Chọn vùng, hub chính, phạm vi tỉnh/phường", "Khu vực phục vụ được tạo"),
            ("Thêm hub", "Mã, tên, loại hub, địa chỉ, hub cha", "Hub mới xuất hiện"),
            ("Trùng mã hub", "Mã hub đã có", "Báo lỗi trùng mã"),
            ("Thêm tuyến vận chuyển", "Hub đi, hub đến, phương thức, thời gian, tải tối đa", "Tuyến được tạo"),
            ("Tuyến trùng hub đi và đến", "Hub đi = hub đến", "Báo lỗi"),
            ("Thêm lịch chạy tuyến", "Giờ khởi hành, ngày chạy, tải trọng", "Lịch chạy được tạo"),
            ("Thêm mẫu lộ trình", "Chuỗi chặng theo thứ tự", "Mẫu lộ trình được tạo"),
        ]),
    ]),
    ("Nhân sự vận hành và ca làm việc", [
        ("UC23 Quản lý nhân sự vận hành", [
            ("Tạo hồ sơ nhân sự", "Tài khoản nhân viên, mã nhân viên, loại Courier", "Hồ sơ nhân sự ACTIVE"),
            ("Gán nhân sự vào hub", "Chọn hub, đặt hub chính", "Tạo HubMembership"),
            ("Gỡ nhân sự khỏi hub", "Gỡ khỏi hub", "Ghi unassignedAt"),
            ("Cập nhật trạng thái nhân sự", "ACTIVE → ON_LEAVE", "Không xuất hiện trong danh sách phân công"),
        ]),
        ("UC24 Xem nhân sự vận hành", [
            ("Xem nhân sự của hub", "Mở Nhân sự vận hành", "Danh sách nhân sự, trạng thái sẵn sàng, số việc đang làm"),
            ("Lọc theo loại nhân sự", "Loại: Line-haul Driver", "Kết quả đúng loại"),
        ]),
        ("UC25 Quản lý ca làm việc", [
            ("Tạo ca", "Mã, tên, 07:00–15:00", "Ca SCHEDULED"),
            ("Giờ kết thúc trước giờ bắt đầu", "15:00–07:00", "Báo lỗi thời gian"),
            ("Phân công nhân sự vào ca", "Chọn 3 nhân sự", "Tạo 3 phân công ASSIGNED, nhân sự nhận thông báo"),
            ("Nhân sự trùng ca", "Phân công nhân sự đã có ca trùng giờ", "Cảnh báo trùng ca"),
            ("Hủy ca", "Hủy ca kèm lý do", "Ca CANCELLED, phân công bị hủy"),
            ("Đánh dấu vắng mặt", "Chọn nhân sự không đến", "Phân công ABSENT"),
        ]),
        ("UC26 Xem ca làm việc", [
            ("Xem lịch ca của tôi", "Mở Ca làm việc trên ứng dụng", "Danh sách ca được phân công"),
            ("Không có ca", "Nhân sự chưa được phân công", "Thông báo chưa có ca"),
        ]),
        ("UC27 Check-in / Check-out ca", [
            ("Check-in đúng giờ", "Nhấn Check-in trong khung giờ ca", "CHECKED_IN, ghi checkedInAt"),
            ("Check-out", "Nhấn Check-out", "CHECKED_OUT, ghi checkedOutAt"),
            ("Check-in khi ca đã hủy", "Ca CANCELLED", "Không cho check-in"),
        ]),
        ("UC28 Cập nhật trạng thái sẵn sàng", [
            ("Chuyển sang sẵn sàng", "Bật Đang hoạt động", "AVAILABLE, xuất hiện khi điều phối"),
            ("Chuyển sang ngoại tuyến", "Tắt Đang hoạt động", "OFFLINE, không được phân công mới"),
        ]),
    ]),
    ("Điều phối và thực hiện vận hành", [
        ("UC29 Xem tổng quan vận hành", [
            ("Xem bảng điều hành", "Mở Tổng quan", "Số đơn, đơn đang xử lý, đã giao, công việc tồn"),
            ("Không có dữ liệu", "Hub mới chưa có đơn", "Hiển thị 0 và trạng thái rỗng"),
        ]),
        ("UC30 Theo dõi vận đơn vận hành", [
            ("Tra cứu vận đơn nội bộ", "Nhập mã vận đơn", "Hiển thị kiện, custody, lộ trình, công việc"),
            ("Tạm giữ vận đơn", "Tạm giữ kèm lý do", "isOnHold = true, ghi hub và người thao tác"),
            ("Giải phóng vận đơn", "Bỏ tạm giữ", "isOnHold = false"),
        ]),
        ("UC31 Điều phối công việc", [
            ("Phân công công việc", "Chọn công việc PICKUP → chọn Courier phù hợp", "ASSIGNED, nhân sự nhận việc trên ứng dụng"),
            ("Chỉ hiện nhân sự phù hợp", "Công việc yêu cầu LINE_HAUL_DRIVER", "Chỉ hiện tài xế trung chuyển đã check-in, sẵn sàng"),
            ("Không có nhân sự", "Không ai check-in ca", "Thông báo không có nhân sự sẵn sàng"),
            ("Nhân sự quá tải", "Nhân sự đã đạt số việc tối đa", "Cảnh báo, chọn người khác"),
            ("Phân công lại", "Đổi người thực hiện", "Cập nhật người thực hiện, ghi người thao tác"),
            ("Hủy công việc", "Hủy kèm ghi chú", "CANCELLED"),
        ]),
        ("UC32 Xem công việc được giao", [
            ("Xem công việc", "Mở Công việc của tôi", "Danh sách theo mức ưu tiên"),
            ("Lọc theo trạng thái", "Tab Đang xử lý / Đã xong", "Kết quả đúng trạng thái"),
        ]),
        ("UC33 Thực hiện công việc", [
            ("Nhận việc", "Nhấn Nhận việc", "ACCEPTED"),
            ("Bắt đầu", "Nhấn Bắt đầu", "IN_PROGRESS"),
            ("Hoàn thành", "Quét đủ kiện → Hoàn thành", "COMPLETED, tạo công việc chặng tiếp"),
            ("Hoàn thành khi thiếu kiện", "Quét 1/2 kiện → Hoàn thành", "Không cho hoàn thành, hiển thị kiện còn thiếu"),
            ("Báo không hoàn thành", "Nhập lý do", "FAILED, có resolutionNote"),
        ]),
        ("UC34 Quét kiện hàng", [
            ("Quét mã hợp lệ", "Quét mã kiện thuộc công việc", "Ghi nhận, cập nhật custody và sự kiện"),
            ("Mã không thuộc công việc", "Quét kiện của vận đơn khác", "Cảnh báo, không ghi nhận"),
            ("Quét trùng", "Quét lại kiện đã quét", "Báo quét trùng"),
            ("Chưa cấp quyền camera", "Từ chối quyền camera", "Yêu cầu cấp quyền / nhập mã thủ công"),
            ("Nhập mã thủ công", "Gõ mã kiện", "Xử lý như quét mã"),
        ]),
        ("UC35 Ghi nhận kết quả giao hàng", [
            ("Giao thành công có chữ ký", "Chọn Giao thành công, ký nhận, nhập tên người nhận", "DELIVERED, có DeliveryAttempt"),
            ("Giao thành công bằng OTP", "Nhập OTP đúng", "DELIVERED"),
            ("Thiếu bằng chứng", "Không chụp ảnh / không ký", "Yêu cầu bổ sung bằng chứng"),
            ("Giao thất bại", "Chọn lý do Không liên lạc được", "DELIVERY_FAILED, vào danh sách ngoại lệ"),
            ("Thu COD", "Đơn có COD, xác nhận đã thu", "Ghi nhận đã thu đủ COD"),
        ]),
        ("UC36 Bàn giao hàng trung chuyển", [
            ("Nhận hàng tại hub đi", "Quét các kiện của chuyến", "Kiện chuyển sang custody tài xế"),
            ("Bàn giao tại hub đến", "Quét kiện tại hub đến", "Kiện về custody hub đến, sự kiện Đến hub"),
            ("Kiện không thuộc chuyến", "Quét kiện của chuyến khác", "Cảnh báo, không ghi nhận"),
        ]),
        ("UC37 Điều chỉnh khối lượng kiện", [
            ("Cân lại kiện", "Nhập khối lượng mới và lý do", "Tạo điều chỉnh, vận đơn có hasPendingWeightReview"),
            ("Thiếu lý do", "Bỏ trống lý do", "Báo lỗi trường bắt buộc"),
        ]),
        ("UC38 Duyệt điều chỉnh khối lượng", [
            ("Duyệt điều chỉnh", "Chọn điều chỉnh → Duyệt kèm ghi chú", "reviewCompleted = true, cập nhật khối lượng tính cước"),
            ("Không có điều chỉnh chờ", "Mở danh sách khi rỗng", "Thông báo không có điều chỉnh chờ duyệt"),
        ]),
    ]),
    ("Ngoại lệ và sự cố", [
        ("UC39 Xem ngoại lệ vận đơn", [
            ("Xem danh sách ngoại lệ", "Mở Ngoại lệ", "Đơn giao thất bại, tạm giữ, chờ duyệt khối lượng, yêu cầu ngoại lệ"),
            ("Lọc theo loại ngoại lệ", "Chọn Giao thất bại", "Kết quả đúng loại"),
        ]),
        ("UC40 Xử lý yêu cầu ngoại lệ", [
            ("Phê duyệt hoàn hàng", "Yêu cầu RETURN_TO_SENDER → Phê duyệt", "COMPLETED, vận đơn chuyển chiều hoàn"),
            ("Phê duyệt đổi địa chỉ", "FORWARD_TO_NEW_ADDRESS → Phê duyệt", "Định tuyến lại, người nhận mới"),
            ("Từ chối yêu cầu", "Từ chối kèm ghi chú", "REJECTED, người yêu cầu được thông báo"),
            ("Vận đơn đã giao", "Phê duyệt yêu cầu của đơn DELIVERED", "Không cho phê duyệt"),
            ("Không tìm được lộ trình mới", "Địa chỉ mới ngoài vùng phục vụ", "Giữ APPROVED, cảnh báo định tuyến thất bại"),
        ]),
        ("UC41 Báo cáo sự cố", [
            ("Báo cáo hư hỏng", "Loại DAMAGED, mức độ HIGH, mô tả", "Tạo hồ sơ sự cố OPEN"),
            ("Kiện không xác định", "Loại UNIDENTIFIED_PARCEL, nhập mã vạch ngoài", "Tạo hồ sơ kèm externalBarcode"),
            ("Thiếu tiêu đề / mô tả", "Bỏ trống mô tả", "Báo lỗi trường bắt buộc"),
        ]),
        ("UC42 Quản lý hồ sơ sự cố", [
            ("Phân công xử lý", "Chọn người xử lý", "Ghi assignedToUserId"),
            ("Cập nhật tiến độ", "OPEN → IN_PROGRESS", "Trạng thái IN_PROGRESS"),
            ("Giải quyết sự cố", "Nhập kết quả xử lý", "RESOLVED, ghi resolvedAt"),
            ("Đóng hồ sơ", "RESOLVED → Đóng", "CLOSED, ghi closedAt"),
            ("Đóng khi chưa giải quyết", "OPEN → Đóng", "Không cho đóng"),
        ]),
    ]),
]


def flat():
    """[(id, group, uc, scenario, steps, expected)]"""
    out, n = [], 0
    for g, ucs in GROUPS:
        for uc, cases in ucs:
            for sc, st, ex in cases:
                n += 1
                out.append((f"TC{n:03d}", g, uc, sc, st, ex))
    return out
