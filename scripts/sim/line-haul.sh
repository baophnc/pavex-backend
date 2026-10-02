#!/usr/bin/env bash
# Thử luồng xe trung chuyển trên dữ liệu seed-mini, không cần điện thoại:
#   1. Quản lý vận hành nhận gửi 1 đơn tại quầy bưu cục đi.
#   2. Nhân viên bưu cục đi vào ca và quét "Kiểm kho" (xong việc ở bưu cục gửi).
#   3. Tài xế vào ca, nhận việc trung chuyển, bắt đầu và quét "Lên xe".
# Chuyến xe được tạo tự động; bật FLEET_SIMULATOR_ENABLED=true để xe tự chạy trên bản đồ.
#
#   scripts/sim/line-haul.sh            # HCM-TSH → HN-BD
#   scripts/sim/line-haul.sh HN-BD      # HN-BD → HCM-TSH
set -euo pipefail

API="${PAVEX_API:-http://localhost:8080}"
PASSWORD="${SEED_PASSWORD:-Pavex2026!}"
FROM="${1:-HCM-TSH}"

# Seed hubs: id, province and ward, and the hub's staff account (macOS ships bash 3, no maps).
hub_id() { case "$1" in HN-BD) echo 5eed0001-0000-4000-8000-000000000001 ;; HCM-TSH) echo 5eed0001-0000-4000-8000-000000000002 ;; esac; }
hub_ward() { case "$1" in HN-BD) echo "01 00004" ;; HCM-TSH) echo "79 26977" ;; esac; }
hub_staff() { case "$1" in HN-BD) echo nhanvien.hn ;; HCM-TSH) echo nhanvien.hcm ;; esac; }
case "$FROM" in HN-BD) TO=HCM-TSH ;; HCM-TSH) TO=HN-BD ;; *) echo "Bưu cục đi phải là HN-BD hoặc HCM-TSH" >&2; exit 1 ;; esac

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
login() {
  curl -fsS "$API/api/v1/auth/login" -H 'content-type: application/json' \
    -d "{\"email\":\"$1@pavex.test\",\"password\":\"$PASSWORD\"}" | json 'd["accessToken"]'
}
call() { local token=$1 method=$2 path=$3; shift 3; curl -fsS -X "$method" "$API$path" -H "authorization: Bearer $token" -H 'content-type: application/json' "$@"; }

# Vào ca ở bưu cục của người đó (bỏ qua nếu đã vào) và bật "Sẵn sàng nhận việc".
on_duty() {
  local token=$1 hub=$2
  local shift
  shift=$(call "$token" GET /api/v1/operations/shifts/mine | json "next((s['shiftId'] for s in d if s['hubCode']=='$hub' and s['assignmentStatus'] in ('SCHEDULED','CHECKED_IN')), '')")
  [ -n "$shift" ] && call "$token" POST "/api/v1/operations/shifts/$shift/check-in" -d '{}' >/dev/null 2>&1 || true
  call "$token" PUT /api/v1/operations/availability -d '{"status":"AVAILABLE"}' >/dev/null
}

# Chờ tối đa 30 giây tới khi việc [type] của đơn được giao cho người này; in mã việc.
wait_job() {
  local token=$1 shipment=$2 type=$3
  for _ in $(seq 1 15); do
    local id
    id=$(call "$token" GET /api/v1/operations/assignments/mine | json "next((a['id'] for a in d if a['shipmentId']=='$shipment' and a['type']=='$type'), '')")
    [ -n "$id" ] && { echo "$id"; return; }
    sleep 2
  done
  echo "Không thấy việc $type được giao sau 30 giây." >&2; exit 1
}

read -r FROM_PROVINCE FROM_WARD <<<"$(hub_ward "$FROM")"
read -r TO_PROVINCE TO_WARD <<<"$(hub_ward "$TO")"

echo "1/3 Nhận gửi tại quầy $FROM → $TO"
MANAGER=$(login vanhanh)
QUOTE=$(call "$MANAGER" POST /api/v1/pricing/quotes -d "{\"originProvinceCode\":\"$FROM_PROVINCE\",\"originWardCode\":\"$FROM_WARD\",\"destinationProvinceCode\":\"$TO_PROVINCE\",\"destinationWardCode\":\"$TO_WARD\",\"codAmount\":0,\"declaredValue\":200000,\"parcels\":[{\"weightKg\":2,\"lengthCm\":30,\"widthCm\":20,\"heightCm\":15}]}" | json 'd["options"][0]["quoteId"]')
CODE=$(call "$MANAGER" POST /api/v1/admin/shipments/counter -H "idempotency-key: $(uuidgen)" -d "{\"hubId\":\"$(hub_id "$FROM")\",\"quoteId\":\"$QUOTE\",\"sender\":{\"contactName\":\"Khách thử nghiệm\",\"contactPhone\":\"0900000001\",\"addressLine\":\"Tại quầy\",\"provinceCode\":\"$FROM_PROVINCE\",\"wardCode\":\"$FROM_WARD\"},\"recipient\":{\"contactName\":\"Người nhận thử nghiệm\",\"contactPhone\":\"0900000002\",\"addressLine\":\"Số 1\",\"provinceCode\":\"$TO_PROVINCE\",\"wardCode\":\"$TO_WARD\"},\"shippingFeePayer\":\"RECIPIENT\",\"parcels\":[{\"description\":\"Hàng thử nghiệm\",\"quantity\":1,\"category\":\"OTHER\",\"isFragile\":false}],\"note\":null,\"cashReceived\":0}" | json 'd["trackingCode"]')
SHIPMENT=$(call "$MANAGER" GET "/api/v1/admin/shipments/$CODE" | json 'd["id"]')
echo "    Vận đơn $CODE"

echo "2/3 $(hub_staff "$FROM") kiểm kho tại $FROM"
STAFF_TOKEN=$(login "$(hub_staff "$FROM")")
on_duty "$STAFF_TOKEN" "$FROM"
call "$STAFF_TOKEN" POST /api/v1/workforce/scans -d "{\"code\":\"$CODE\",\"action\":\"CHECK\",\"hubId\":\"$(hub_id "$FROM")\"}" >/dev/null

echo "3/3 Tài xế nhận việc và quét lên xe"
DRIVER=$(login taixe)
on_duty "$DRIVER" HN-BD
JOB=$(wait_job "$DRIVER" "$SHIPMENT" LINE_HAUL)
call "$DRIVER" POST "/api/v1/operations/assignments/$JOB/accept" -d '{}' >/dev/null
call "$DRIVER" POST "/api/v1/operations/assignments/$JOB/start" -d '{}' >/dev/null
call "$DRIVER" POST /api/v1/workforce/scans -d "{\"code\":\"$CODE\",\"action\":\"DEPART\"}" | json '"    " + d["results"][0]["message"]'

echo
echo "Xong. Mở http://localhost:3001/fleet hoặc http://localhost:3001/shipments/$CODE để xem xe."
