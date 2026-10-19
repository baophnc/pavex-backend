#!/usr/bin/env bash
# Thử luồng thu cước của cửa hàng trên dữ liệu seed-mini, không cần điện thoại:
#   1. Cửa hàng "Hoa Sen" (Ba Đình) tạo 1 đơn đi TP.HCM, cửa hàng trả cước.
#   2. Bưu tá Ba Đình lấy hàng, bưu cục nhập kho, tài xế chở vào Tân Sơn Hoà, bưu cục đích nhập kho.
#   3. Bưu tá Tân Sơn Hoà giao thành công (có ảnh).
#   4. Vận đơn đóng, Billing ghi cước cửa hàng phải trả; quản lý vận hành lập bảng đối soát.
# Kịch bản dùng đơn liên miền để đi qua cả chuyến xe; đơn nội thành (cùng một bưu cục) cũng giao được:
# bưu cục nhập kho xong là bưu tá xuất giao luôn, không có bước nhập kho bưu cục đích.
# Kết quả: một hoá đơn "Đã phát hành" ở http://localhost:3000/account/billing để thanh toán bằng QR.
#
#   scripts/sim/shop-invoice.sh
set -euo pipefail

API="${PAVEX_API:-http://localhost:8080}"
PASSWORD="${SEED_PASSWORD:-Pavex2026!}"
FROM_HUB=5eed0001-0000-4000-8000-000000000001 # HN-BD
TO_HUB=5eed0001-0000-4000-8000-000000000002   # HCM-TSH

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
login() {
  curl -fsS "$API/api/v1/auth/login" -H 'content-type: application/json' \
    -d "{\"email\":\"$1@pavex.test\",\"password\":\"$PASSWORD\"}" | json 'd["accessToken"]'
}
call() { local token=$1 method=$2 path=$3; shift 3; curl -fsS -X "$method" "$API$path" -H "authorization: Bearer $token" -H 'content-type: application/json' "$@"; }

# Ca đang diễn ra (hoặc mở check-in trong 30 phút tới) ở bưu cục [hub] trong danh sách ca của tôi,
# ưu tiên ca đã vào; rỗng nếu không có.
current_shift() {
  python3 -c '
import sys, json, datetime as dt
hub, now = sys.argv[1], dt.datetime.now(dt.timezone.utc)
at = lambda v: dt.datetime.fromisoformat(v.replace("Z", "+00:00"))
shifts = [s for s in json.load(sys.stdin) if s["hubCode"] == hub and s["assignmentStatus"] in ("SCHEDULED", "CHECKED_IN")
          and at(s["startsAt"]) - dt.timedelta(minutes=30) <= now < at(s["endsAt"])]
shifts.sort(key=lambda s: s["assignmentStatus"] != "CHECKED_IN")
print(shifts[0]["shiftId"] if shifts else "")
' "$1"
}

# Vào ca ở bưu cục của người đó (bỏ qua nếu đã vào) và bật "Sẵn sàng nhận việc".
on_duty() {
  local token=$1 hub=$2
  local shift
  shift=$(call "$token" GET /api/v1/operations/shifts/mine | current_shift "$hub")
  [ -n "$shift" ] || { echo "Không có ca nào đang diễn ra ở $hub (ngày nghỉ hoặc ngoài giờ; ./pavex up có ca cả ngày)." >&2; exit 1; }
  call "$token" POST "/api/v1/operations/shifts/$shift/check-in" -d '{}' >/dev/null
  call "$token" PUT /api/v1/operations/availability -d '{"status":"AVAILABLE"}' >/dev/null
}

# Chờ tối đa 30 giây tới khi việc [type] của đơn được giao cho người này, rồi nhận và bắt đầu việc; in mã việc.
take_job() {
  local token=$1 shipment=$2 type=$3 id=""
  for _ in $(seq 1 15); do
    id=$(call "$token" GET /api/v1/operations/assignments/mine | json "next((a['id'] for a in d if a['shipmentId']=='$shipment' and a['type']=='$type'), '')")
    [ -n "$id" ] && break
    sleep 2
  done
  [ -n "$id" ] || { echo "Không thấy việc $type được giao sau 30 giây." >&2; exit 1; }
  call "$token" POST "/api/v1/operations/assignments/$id/accept" -d '{}' >/dev/null 2>&1 || true
  call "$token" POST "/api/v1/operations/assignments/$id/start" -d '{}' >/dev/null 2>&1 || true
  echo "$id"
}

echo "1/5 Cửa hàng tạo đơn Hà Nội → TP.HCM"
SHOP=$(login shop.daduyet)
PICKUP=$(call "$SHOP" GET /api/v1/merchants/me/pickup-addresses | json 'next(a["id"] for a in (d if isinstance(d, list) else d.get("items", [])))')
QUOTE=$(call "$SHOP" POST /api/v1/pricing/quotes -d "{\"originProvinceCode\":\"01\",\"originWardCode\":\"00004\",\"destinationProvinceCode\":\"79\",\"destinationWardCode\":\"26977\",\"codAmount\":0,\"declaredValue\":100000,\"parcels\":[{\"weightKg\":1,\"lengthCm\":20,\"widthCm\":15,\"heightCm\":10}]}" | json 'd["options"][0]["quoteId"]')
CREATED=$(call "$SHOP" POST /api/v1/shipments -H "idempotency-key: $(uuidgen)" -d "{\"quoteId\":\"$QUOTE\",\"pickupAddressId\":\"$PICKUP\",\"recipient\":{\"contactName\":\"Người nhận thử nghiệm\",\"contactPhone\":\"0900000002\",\"addressLine\":\"Số 5 Cộng Hoà\",\"provinceCode\":\"79\",\"wardCode\":\"26977\"},\"shippingFeePayer\":\"SENDER\",\"parcels\":[{\"description\":\"Hàng thử nghiệm\",\"quantity\":1,\"category\":\"OTHER\",\"isFragile\":false}],\"note\":null}")
CODE=$(echo "$CREATED" | json 'd["trackingCode"]')
PARCEL=$(echo "$CREATED" | json 'd["parcels"][0]["code"]')
MANAGER=$(login vanhanh)
SHIPMENT=$(call "$MANAGER" GET "/api/v1/admin/shipments/$CODE" | json 'd["id"]')
echo "    Vận đơn $CODE, kiện $PARCEL"

echo "2/5 Bưu tá lấy hàng, bưu cục Ba Đình nhập kho"
COURIER=$(login buuta.hn)
on_duty "$COURIER" HN-BD
take_job "$COURIER" "$SHIPMENT" PICKUP >/dev/null
call "$COURIER" POST /api/v1/workforce/scans -d "{\"code\":\"$PARCEL\",\"action\":\"PICK_UP\"}" >/dev/null
STAFF=$(login nhanvien.hn)
on_duty "$STAFF" HN-BD
call "$STAFF" POST /api/v1/workforce/scans -d "{\"code\":\"$PARCEL\",\"action\":\"RECEIVE\",\"hubId\":\"$FROM_HUB\"}" >/dev/null

echo "3/5 Tài xế chở vào Tân Sơn Hoà, bưu cục đích nhập kho"
DRIVER=$(login taixe)
on_duty "$DRIVER" HN-BD
take_job "$DRIVER" "$SHIPMENT" LINE_HAUL >/dev/null
call "$DRIVER" POST /api/v1/workforce/scans -d "{\"code\":\"$PARCEL\",\"action\":\"DEPART\"}" >/dev/null
DEST_STAFF=$(login nhanvien.hcm)
on_duty "$DEST_STAFF" HCM-TSH
call "$DEST_STAFF" POST /api/v1/workforce/scans -d "{\"code\":\"$PARCEL\",\"action\":\"RECEIVE\",\"hubId\":\"$TO_HUB\"}" >/dev/null

echo "4/5 Bưu tá Tân Sơn Hoà giao thành công"
COURIER=$(login buuta.hcm)
on_duty "$COURIER" HCM-TSH
JOB=$(take_job "$COURIER" "$SHIPMENT" LAST_MILE_DELIVERY)
call "$COURIER" POST /api/v1/workforce/scans -d "{\"code\":\"$PARCEL\",\"action\":\"OUT_FOR_DELIVERY\"}" >/dev/null
PHOTO="$(mktemp -t pavex-proof).png"
python3 -c "import base64,sys; open(sys.argv[1],'wb').write(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='))" "$PHOTO"
EVIDENCE=$(curl -fsS -X POST "$API/api/v1/workforce/evidence" -H "authorization: Bearer $COURIER" -F "file=@$PHOTO;type=image/png" | json 'd["reference"]')
rm -f "$PHOTO"
call "$COURIER" POST /api/v1/deliveries/attempts -d "{\"assignmentId\":\"$JOB\",\"parcelCode\":\"$PARCEL\",\"idempotencyKey\":\"$(uuidgen)\",\"outcome\":\"DELIVERED\",\"proofMethod\":\"PHOTO\",\"recipientName\":\"Người nhận thử nghiệm\",\"evidenceReference\":\"$EVIDENCE\",\"collectedAmount\":0}" >/dev/null

echo "5/5 Lập bảng đối soát cho cửa hàng"
MERCHANT=$(call "$SHOP" GET /api/v1/merchants/me/profile | json 'd["id"]')
INVOICE=""
for _ in $(seq 1 15); do
  # The shipment.closed event reaches Billing through the outbox; generate once the fee is there.
  call "$MANAGER" POST /api/v1/admin/billing/statements/generate -d "{\"merchantId\":\"$MERCHANT\"}" >/dev/null 2>&1 || true
  INVOICE=$(call "$SHOP" GET /api/v1/billing/invoices | json 'next((i["id"] for i in (d if isinstance(d, list) else d.get("items", [])) if i["status"] in ("ISSUED","PARTIALLY_PAID")), "")')
  [ -n "$INVOICE" ] && break
  sleep 2
done
[ -n "$INVOICE" ] || { echo "Chưa có hoá đơn sau 30 giây." >&2; exit 1; }
call "$SHOP" GET "/api/v1/billing/invoices/$INVOICE" | json '"    Hoá đơn " + d["invoiceNumber"] + ": " + format(d["totalAmount"], ",") + " ₫, còn nợ " + format(d["balanceAmount"], ",") + " ₫"'

echo
echo "Xong. Đăng nhập http://localhost:3000 bằng shop.daduyet@pavex.test, mở Hoá đơn để thanh toán QR."
