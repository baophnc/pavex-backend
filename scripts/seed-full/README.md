# seed-full: mạng lưới toàn quốc

```bash
./pavex seed full              # thay dữ liệu của stack ./pavex đang chạy
./pavex reset --seed=full      # xoá sạch rồi bật lại với dữ liệu toàn quốc
./pavex reset                  # quay về seed-mini
```

Script sao lưu mọi database vào `.local/backups/<thời điểm>-seed-full/`, dừng các service Java, nạp dữ liệu, bật lại
service và tự kiểm tra kết quả. Mất khoảng 1–2 phút. Bộ dữ liệu được sinh từ danh mục tỉnh/phường đang có trong
Partners (34 tỉnh/thành, 3.321 phường/xã), chạy lại bao nhiêu lần cũng ra đúng dữ liệu đó.

## Mạng lưới

| Cấp | Quy tắc | Mã | Số lượng |
|---|---|---|---|
| Trung tâm miền | 1 cho mỗi miền, đặt ở Hà Nội, Đà Nẵng, TP.HCM | `TT-MIENBAC`, `TT-MIENTRUNG`, `TT-MIENNAM` | 3 |
| Bưu cục tổng tỉnh | 1 cho mỗi tỉnh/thành, thuộc trung tâm miền của tỉnh | `KHO-HN`, `KHO-HCM`, … | 34 |
| Bưu cục phường | 1 cho mỗi phường/xã, thuộc bưu cục tổng của tỉnh (Đặc khu Hoàng Sa không phục vụ) | `BC-HN-BADINH`, … | 3.320 |

- **Vùng phục vụ:** mỗi phường gắn với đúng bưu cục của nó. Xã vùng cao 7 tỉnh phía Bắc tính phụ phí vùng xa, đặc khu
  tính phụ phí hải đảo (đi tàu).
- **Tuyến** (6.714 tuyến, 13.404 chuyến mỗi ngày):
  - bưu cục phường ↔ bưu cục tổng: đi 11:00 và 17:00, về 06:00 và 13:00 (đặc khu 1 chuyến tàu);
  - bưu cục tổng ↔ trung tâm miền: đi 12:00 và 20:00, về 04:00 và 14:00;
  - trung tâm ↔ trung tâm (Bắc–Trung, Trung–Nam, Bắc–Nam): 10:00 và 22:00 mỗi chiều.
- **Toạ độ** gần đúng: quanh trung tâm tỉnh, đủ để bản đồ xe vẽ được, không đúng tới từng phường.

Đơn từ phường A ở Hà Nội tới phường B ở TP.HCM đi 5 chặng xe: phường A → bưu cục tổng Hà Nội → trung tâm miền Bắc →
trung tâm miền Nam → bưu cục tổng TP.HCM → phường B, rồi bưu tá giao.

## Nhân sự (10.740 tài khoản)

Mỗi bưu cục có đúng 1 quản lý.

| Nơi | Ai | Email |
|---|---|---|
| Bưu cục phường | 1 trưởng bưu cục + 2 bưu tá | `truongbc.hn-badinh@pavex.test`, `buuta1.hn-badinh@…`, `buuta2.hn-badinh@…` |
| Bưu cục tổng tỉnh | 1 quản lý + 4 nhân viên kho | `quanly.kho-hn@…`, `kho1.kho-hn@…` … `kho4.kho-hn@…` |
| | 1 tài xế cho mỗi tuyến ~8 bưu cục phường liền kề (427 tuyến) | `taixe.kho-hn-t01@…` |
| | 1 tài xế dự phòng cho mỗi 6 tuyến, chạy được mọi bưu cục của tỉnh | `taixe.kho-hn-dp1@…` |
| | 2 tài xế trục lên trung tâm miền | `taixe.kho-hn-truc1@…`, `taixe.kho-hn-truc2@…` |
| Trung tâm miền | 1 quản lý + 6 nhân viên khai thác | `quanly.tt-mienbac@…`, `kho1.tt-mienbac@…` |
| | 2 tài xế cho mỗi cặp miền | `taixe.mienbac-mientrung-1@…`, `taixe.mienbac-mientrung-2@…` |
| Văn phòng | Quản trị, quản lý vận hành | `admin@pavex.test`, `vanhanh@pavex.test` |

Mã bưu cục phường lấy từ tên phường không dấu; hai phường trùng tên trong một tỉnh có thêm hậu tố `-2`. Danh sách đầy đủ
(email, họ tên, chức vụ, mã nhân viên, SĐT, bưu cục, các bưu cục trên tuyến của tài xế) nằm ở
`.local/seed/full/staff.csv`. Mọi tài khoản `@pavex.test` dùng chung mật khẩu của seed-mini (`../seed-mini/README.md`).
Tên người là tên giả.

Tài xế tuyến thuộc bưu cục tổng và là thành viên của mọi bưu cục trên tuyến, nên vào ca ở bưu cục tổng là nhận được
việc ở các bưu cục đó.

## Không có trong bộ này

Cửa hàng, khách hàng, vận đơn, tiền, chuyến xe: tạo bằng tay khi thử. Các kịch bản `scripts/sim/*.sh` cần cửa hàng và
mã bưu cục của seed-mini, nên chạy chúng sau `./pavex reset`. `admin@pavex.com` và tài khoản riêng của bạn (ví dụ Gmail)
được giữ, nhưng bị gỡ vai trò cửa hàng.

## Sửa bộ dữ liệu

Mọi quy tắc nằm trong `generate.py` (số bưu cục mỗi tuyến, giờ chạy, biên chế…). Bộ sinh tự kiểm tra: mỗi phường đúng
1 bưu cục, mỗi tỉnh đúng 1 bưu cục tổng, mỗi bưu cục đúng 1 quản lý và 2 bưu tá, mọi bưu cục phường có tuyến và nằm trên
đúng 1 tuyến xe. Sai một điều là dừng. Sau khi nạp, `apply.sh` kiểm tra lại các điều đó trên database.
