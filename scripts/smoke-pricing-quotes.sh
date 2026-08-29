#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
cd "$repo_dir"

for tool in curl python3; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "$tool is required." >&2
    exit 1
  }
done

base_url="${PRICING_BASE_URL:-http://localhost:8086}"

db_query() {
  local sql="$1"
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${PRICING_DB_PASSWORD:-pavex}" psql \
      "${PRICING_DB_URL_PSQL:-postgresql://pavex@localhost:5437/pavex_pricing}" \
      -Atqc "$sql"
    return
  fi
  docker compose exec -T pricing-db psql -U pavex -d pavex_pricing -Atqc "$sql"
}

quote() {
  local destination_province="$1"
  local destination_ward="$2"
  curl --fail --silent --show-error \
    -H 'content-type: application/json' \
    -X POST "$base_url/api/v1/pricing/quotes" \
    --data-binary @- <<JSON
{
  "originProvinceCode": "01",
  "originWardCode": "00070",
  "destinationProvinceCode": "$destination_province",
  "destinationWardCode": "$destination_ward",
  "weightKg": 1,
  "lengthCm": 10,
  "widthCm": 10,
  "heightCm": 10,
  "codAmount": 0,
  "declaredValue": 0
}
JSON
}

same_area="$(quote '01' '00082')"
python3 - "$same_area" <<'PY'
import json, sys
q=json.loads(sys.argv[1])
assert q['lane']=='Local', q
assert len(q['options'])==2, q
assert [x['serviceLevel'] for x in q['options']]==['Standard','Express'], q
assert [x['total'] for x in q['options']]==[20000,30000], q
assert q['chargeableWeightKg']==1.0, q
print('PASS same-service-area quote = Local / 20,000 / 30,000 VND')
PY

cross_region="$(quote '79' '26740')"
request_id="$(python3 - "$cross_region" <<'PY'
import json, sys
q=json.loads(sys.argv[1])
assert q['lane']=='National', q
assert len(q['options'])==2, q
assert [x['total'] for x in q['options']]==[30000,50000], q
print(q['requestId'])
PY
)"
echo "PASS cross-region quote = National / 30,000 / 50,000 VND"

request_count="$(db_query "SELECT COUNT(*) FROM pricing.quote_requests WHERE id='$request_id';")"
option_count="$(db_query "SELECT COUNT(*) FROM pricing.quote_options WHERE quote_request_id='$request_id';")"
parcel_count="$(db_query "SELECT COUNT(*) FROM pricing.quote_parcels WHERE quote_request_id='$request_id';")"
[[ "$request_count" == "1" ]] || { echo "FAIL persisted quote request" >&2; exit 1; }
[[ "$option_count" == "2" ]] || { echo "FAIL persisted quote options: $option_count" >&2; exit 1; }
[[ "$parcel_count" == "1" ]] || { echo "FAIL persisted quote parcels: $parcel_count" >&2; exit 1; }
echo "PASS normalized persistence = 1 request / 1 parcel / 2 options"

echo
echo "Pricing quote smoke passed."
