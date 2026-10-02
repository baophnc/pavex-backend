#!/usr/bin/env bash
set -euo pipefail

# Read-only startup diagnostics. Never starts services, changes keys or resets data.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
failed=0

problem() { printf '[CẦN SỬA] %s\n' "$1"; failed=1; }
ok() { printf '[OK] %s\n' "$1"; }

for tool in java docker openssl; do
  if ! command -v "$tool" >/dev/null 2>&1; then problem "Chưa cài $tool."; fi
done

if command -v java >/dev/null 2>&1; then
  java_version="$(java -version 2>&1 | head -n 1)"
  if [[ "$java_version" =~ \"([0-9]+)\. ]] && (( BASH_REMATCH[1] >= 21 )); then
    ok "Java 21 trở lên đã sẵn sàng."
  else
    problem "Cần chọn JDK 21 trở lên cho cả JAVA_HOME và IntelliJ Project SDK."
  fi
fi

for module in auth identity partners network pricing shipment operations billing fleet notifications platform/api-gateway platform/discovery-server; do
  config_dir="$repo_dir/$module/src/main/resources"
  [[ -f "$config_dir/application.yaml" ]] || problem "$module thiếu application.yaml."
  for profile in local prod; do
    for suffix in yaml yml properties; do
      [[ ! -f "$config_dir/application-$profile.$suffix" ]] || problem "$module còn cấu hình profile $profile."
    done
  done
done
if [[ -n "${SPRING_PROFILES_ACTIVE:-}" || -n "${SPRING_CONFIG_LOCATION:-}" || -n "${SPRING_CONFIG_ADDITIONAL_LOCATION:-}" || -n "${SPRING_CONFIG_IMPORT:-}" ]]; then
  problem "Shell đang có SPRING_PROFILES_ACTIVE hoặc SPRING_CONFIG_* ghi đè YAML; bỏ các biến này trước khi chạy."
fi

key_root="$HOME/.pavex/keys"
if command -v openssl >/dev/null 2>&1; then
  for issuer in auth identity partners pricing shipment operations billing fleet notifications; do
    private="$key_root/$issuer/private.pem"
    public="$key_root/$issuer/public.pem"
    if [[ ! -s "$private" || ! -s "$public" ]]; then
      problem "Thiếu cặp khóa $issuer. Chạy bash scripts/security-bootstrap.sh."
    elif ! openssl pkey -in "$private" -check -noout >/dev/null 2>&1; then
      problem "Khóa $issuer không hợp lệ; không tự xóa hay tạo đè."
    elif ! cmp -s <(openssl pkey -in "$private" -pubout 2>/dev/null) "$public"; then
      problem "Khóa công khai $issuer không khớp khóa riêng. Chạy bash scripts/security-bootstrap.sh để phục hồi public key."
    else
      ok "Cặp khóa $issuer hợp lệ, không thay đổi."
    fi
  done
fi

if command -v docker >/dev/null 2>&1; then
  if ! docker info >/dev/null 2>&1; then
    problem "Không kết nối được Docker Desktop. Mở Docker Desktop và chờ engine sẵn sàng."
  elif ! running_services="$(cd "$repo_dir" && docker compose ps --status running --services)"; then
    problem "Không đọc được trạng thái Docker Compose của PAVEX."
  else
    for service in auth-db identity-db partners-db network-db pricing-db shipment-db operations-db billing-db fleet-db notifications-db gateway-redis rabbitmq mailpit minio; do
      if ! printf '%s\n' "$running_services" | grep -Fxq "$service"; then
        problem "$service chưa chạy. Chạy docker compose up -d --wait."
      fi
    done
  fi
fi

if (( failed )); then exit 1; fi
ok "Điều kiện cơ bản đã đủ. Không cần profile; chạy Discovery, Identity rồi Auth trước các service còn lại."
printf '%s\n' 'Kiểm tra này không xác nhận Flyway, kết nối DB hay đăng nhập. Nếu app vẫn lỗi, cần xem log khởi động mới nhất.'
