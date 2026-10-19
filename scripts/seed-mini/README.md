# seed-mini: dữ liệu nhỏ để test

```bash
./scripts/seed-mini/apply.sh
```

Script sao lưu mọi DB (vào `.local/backups/<thời điểm>-seed-mini/`), xoá dữ liệu nghiệp vụ cũ, nạp bộ dữ liệu
dưới đây rồi khởi động lại backend. Chạy lại bao nhiêu lần cũng cho ra đúng dữ liệu này.

Giữ nguyên: `admin@pavex.com`, tài khoản riêng của bạn (ví dụ Gmail, nhưng mất vai trò cửa hàng), bảng giá
`DEMO-2026-08`, danh mục tỉnh/phường.

## Mạng lưới

| Bưu cục | Mã | Địa chỉ | Phục vụ |
|---|---|---|---|
| Bưu cục Ba Đình | `HN-BD` | Số 1 Đội Cấn, Phường Ba Đình, Hà Nội (tỉnh `01`, phường `00004`) | toàn Hà Nội |
| Bưu cục Tân Sơn Hoà | `HCM-TSH` | Số 10 Cộng Hoà, Phường Tân Sơn Hoà, TP.HCM (tỉnh `79`, phường `26977`) | toàn TP.HCM |

Tuyến trục đường bộ HN-BD ⇄ HCM-TSH, khoảng 30 giờ, mỗi chiều chạy 08:00 và 20:00 hằng ngày, 2 tấn mỗi chuyến.

## Tài khoản

Mật khẩu chung: **`Pavex2026!`**

| Email | Vai trò | Dùng ở |
|---|---|---|
| `admin@pavex.test` | Quản trị hệ thống | Management (3001) |
| `vanhanh@pavex.test` | Quản lý vận hành: điều hành công ty, xem mọi bưu cục, duyệt cửa hàng, tiền, đối soát | Management |
| `truongbc.hn@pavex.test` | Trưởng bưu cục Ba Đình | Management, app |
| `nhanvien.hn@pavex.test` | Nhân viên bưu cục Ba Đình | Management, app |
| `buuta.hn@pavex.test` | Bưu tá Ba Đình | App |
| `truongbc.hcm@pavex.test` | Trưởng bưu cục Tân Sơn Hoà | Management, app |
| `nhanvien.hcm@pavex.test` | Nhân viên bưu cục Tân Sơn Hoà | Management, app |
| `buuta.hcm@pavex.test` | Bưu tá Tân Sơn Hoà | App |
| `taixe@pavex.test` | Tài xế tuyến trục (Ba Đình, cả Tân Sơn Hoà) | App |
| `shop.daduyet@pavex.test` | Cửa hàng **đã duyệt** "Hoa Sen", lấy hàng ở Ba Đình | Client portal (3000) |
| `shop.choduyet@pavex.test` | Cửa hàng **chờ duyệt** "Mai Vàng" | Client portal |
| `khach@pavex.test` | Khách hàng thường | Client portal |

Ca làm việc do Operations tự xếp sau khi khởi động. Với `./pavex up`, mọi người có ca 00:00–24:00 mỗi ngày, không
ngày nghỉ (`OPERATIONS_SHIFT_FULL_DAY=true` trong `deploy/compose.local.yml`), nên vào ca được bất cứ lúc nào. Ở chế
độ thường (staging, hoặc `./scripts/dev-up.sh` khi `.env` không đặt biến này) lịch là 6 ngày làm, 1 ngày nghỉ: nếu hôm nay là ngày
nghỉ của ai đó thì người đó không vào ca được hôm nay, và `taixe@pavex.test` là tài xế duy nhất.

## Khôi phục dữ liệu trước khi seed

```bash
docker exec -i pavex-backend-<db>-db-1 sh -c 'pg_restore -U pavex -d pavex_<db> --clean' < .local/backups/<thời điểm>-seed-mini/<db>.dump
```
