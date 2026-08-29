#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
cd "$repo_dir"

for tool in curl docker python3; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "$tool is required." >&2
    exit 1
  }
done

pass() { printf 'PASS %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1" >&2; exit 1; }

base_url="${PARTNERS_BASE_URL:-http://localhost:8083}"

health_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 \
  "$base_url/actuator/health" || true)"
[[ "$health_status" == "200" ]] || fail "Partners health (expected 200, got $health_status)"
pass "Partners health"

provinces_json="$(curl --fail --silent --max-time 10 "$base_url/api/v1/locations/provinces")"
hanoi_json="$(curl --fail --silent --max-time 10 "$base_url/api/v1/locations/provinces/01/wards")"
danang_json="$(curl --fail --silent --max-time 10 "$base_url/api/v1/locations/provinces/48/wards")"
hcm_json="$(curl --fail --silent --max-time 10 "$base_url/api/v1/locations/provinces/79/wards")"

PROVINCES_JSON="$provinces_json" \
HANOI_JSON="$hanoi_json" \
DANANG_JSON="$danang_json" \
HCM_JSON="$hcm_json" \
python3 <<'PY'
import json
import os
import sys


def fail(message: str) -> None:
    print(f"FAIL {message}", file=sys.stderr)
    raise SystemExit(1)


def expect(condition: bool, message: str) -> None:
    if not condition:
        fail(message)
    print(f"PASS {message}")


provinces = json.loads(os.environ["PROVINCES_JSON"])
by_code = {item["code"]: item for item in provinces}
expected_provinces = {
    "01": ("Hà Nội", "ha_noi"),
    "48": ("Đà Nẵng", "da_nang"),
    "79": ("Hồ Chí Minh", "ho_chi_minh"),
}
for code, (name, code_name) in expected_provinces.items():
    item = by_code.get(code)
    expect(item is not None, f"province {code} exists")
    expect(item["name"] == name, f"province {code} name is {name}")
    expect(item["codeName"] == code_name, f"province {code} codeName is {code_name}")

scopes = {
    "01": (json.loads(os.environ["HANOI_JSON"]), 126, 51, 75, 0),
    "48": (json.loads(os.environ["DANANG_JSON"]), 94, 23, 70, 1),
    "79": (json.loads(os.environ["HCM_JSON"]), 168, 113, 54, 1),
}

total = 0
for province_code, (items, expected_total, expected_wards, expected_communes, expected_special) in scopes.items():
    total += len(items)
    expect(len(items) == expected_total, f"province {province_code} has {expected_total} commune-level units")
    expect(all(item["provinceCode"] == province_code for item in items), f"province {province_code} response is internally consistent")
    expect(len({item["code"] for item in items}) == len(items), f"province {province_code} ward codes are unique")

    ward_count = sum(item["fullName"].startswith("Phường ") for item in items)
    commune_count = sum(item["fullName"].startswith("Xã ") for item in items)
    special_count = sum(item["fullName"].startswith("Đặc khu ") for item in items)
    expect(ward_count == expected_wards, f"province {province_code} has {expected_wards} wards")
    expect(commune_count == expected_communes, f"province {province_code} has {expected_communes} communes")
    expect(special_count == expected_special, f"province {province_code} has {expected_special} special regions")

expect(total == 388, "network demo scope exposes 388 commune-level units")

specials = {
    ("48", "20333"): "Hoàng Sa",
    ("79", "26732"): "Côn Đảo",
}
for (province_code, ward_code), expected_name in specials.items():
    items = scopes[province_code][0]
    item = next((row for row in items if row["code"] == ward_code), None)
    expect(item is not None and item["name"] == expected_name, f"{ward_code} is {expected_name}")
PY

# Database-level check mirrors the Flyway guardrail and catches API/filtering
# regressions separately from catalog contents.
db_summary="$(docker compose exec -T partners-db psql -U pavex -d pavex_partners -AtF '|' -c \
  "SELECT province_code,
          COUNT(*) AS total,
          COUNT(*) FILTER (WHERE administrative_unit_id = 3) AS wards,
          COUNT(*) FILTER (WHERE administrative_unit_id = 4) AS communes,
          COUNT(*) FILTER (WHERE administrative_unit_id = 5) AS special_regions
     FROM partners.wards
    WHERE province_code IN ('01','48','79')
    GROUP BY province_code
    ORDER BY province_code;")"

expected_db_summary=$'01|126|51|75|0\n48|94|23|70|1\n79|168|113|54|1'
[[ "$db_summary" == "$expected_db_summary" ]] || {
  printf 'Expected DB summary:\n%s\nActual DB summary:\n%s\n' "$expected_db_summary" "$db_summary" >&2
  fail "Partners demo location DB summary"
}
pass "Partners demo location DB summary"

echo "Partners network demo location catalog smoke passed."
