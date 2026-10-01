#!/usr/bin/env bash
# Thay dữ liệu local bằng bộ dữ liệu nhỏ để test (2 bưu cục, 14 tài khoản, 2 cửa hàng).
#
#   ./scripts/seed-mini/apply.sh            nạp seed rồi khởi động lại các service
#   ./scripts/seed-mini/apply.sh --no-start nạp seed, để bạn tự chạy ./scripts/dev-up.sh
#
# 1. sao lưu mọi DB vào .local/backups/<thời điểm>-seed-mini/ (khôi phục: pg_restore)
# 2. tắt các service đang chạy (DB, MinIO, RabbitMQ vẫn chạy)
# 3. xoá mạng lưới, nhân sự, cửa hàng, vận đơn, tiền, chuyến xe cũ và nạp dữ liệu mới
# 4. khởi động lại (scripts/dev-up.sh)
#
# Giữ lại: admin@pavex.com, tài khoản riêng của bạn (ví dụ Gmail), bảng giá, danh mục tỉnh/phường.
# Mọi tài khoản @pavex.test đăng nhập bằng mật khẩu SEED_PASSWORD bên dưới. Xem README.md.
set -euo pipefail

SEED_PASSWORD="Pavex2026!"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../.." && pwd)"
start=true
[[ "${1:-}" == "--no-start" ]] && start=false

command -v htpasswd >/dev/null 2>&1 || { echo "Cần htpasswd (có sẵn trên macOS) để mã hoá mật khẩu." >&2; exit 1; }

psql_in() {
  docker exec -i -e PGOPTIONS="-c client_min_messages=warning" "pavex-backend-$1-db-1" sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 '"${2:-}"
}

truncate_schema() {
  psql_in "$1" <<SQL
DO \$\$ DECLARE t text; BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = '$2' AND tablename NOT LIKE 'flyway%' LOOP
    EXECUTE format('TRUNCATE $2.%I CASCADE', t);
  END LOOP;
END \$\$;
SQL
}

cd "$repo_dir"
docker compose up -d --wait >/dev/null

backup_dir="$repo_dir/.local/backups/$(date +%Y%m%d-%H%M%S)-seed-mini"
mkdir -p "$backup_dir"
echo "==> Sao lưu DB vào $backup_dir"
for db in auth identity partners network pricing operations shipment billing fleet; do
  docker exec "pavex-backend-$db-db-1" sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > "$backup_dir/$db.dump"
done

echo "==> Tắt các service đang chạy"
for pid_file in "$repo_dir"/.local/pids/*.pid; do
  [[ -e "$pid_file" ]] || continue
  app_pid="$(<"$pid_file")"
  if kill -0 "$app_pid" 2>/dev/null; then kill "$app_pid"; fi
  while kill -0 "$app_pid" 2>/dev/null; do sleep 1; done
  rm -f "$pid_file"
done

echo "==> Mạng lưới: 2 bưu cục, 2 tuyến trục"
psql_in network < "$script_dir/network.sql"

echo "==> Tài khoản"
psql_in identity < "$script_dir/identity.sql"
# Auth keeps the credentials of the same accounts: same ids, one shared password.
password_hash="{bcrypt}$(htpasswd -bnBC 10 "" "$SEED_PASSWORD" | tr -d ':\n')"
seed_users="$(docker exec pavex-backend-identity-db-1 sh -c \
  'psql -At -F "	" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT id, email FROM identity.user_account WHERE email LIKE '"'"'%@pavex.test'"'"'"')"
{
  echo "BEGIN;"
  echo "DELETE FROM auth.user_account WHERE email LIKE '%@pavex.test' OR email LIKE '%@demo.pavex.local'"
  echo "  OR email LIKE '%@khachhang.pavex.com' OR email LIKE 'deleted+%@pavex.invalid'"
  echo "  OR (email LIKE '%@pavex.com' AND email <> 'admin@pavex.com');"
  echo "CREATE TEMP TABLE seed_user (id uuid, email text) ON COMMIT DROP;"
  echo "COPY seed_user (id, email) FROM stdin;"
  printf '%s\n' "$seed_users"
  echo '\.'
  echo "INSERT INTO auth.user_account (id, email, password_hash, email_verified, failed_login_attempts, created_at,"
  echo "  updated_at, version, identity_provisioned, must_change_password)"
  echo "SELECT id, email, :'pwhash', TRUE, 0, NOW(), NOW(), 0, TRUE, FALSE FROM seed_user;"
  echo "COMMIT;"
} | psql_in auth "-v pwhash='$password_hash'"

echo "==> Nhân sự vận hành"
psql_in operations < "$script_dir/operations.sql"

echo "==> Cửa hàng"
psql_in partners < "$script_dir/partners.sql"
# Partners cannot read Identity: drop saved addresses of accounts that no longer exist.
live_users="$(docker exec pavex-backend-identity-db-1 sh -c \
  'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT id FROM identity.user_account"')"
{
  echo "BEGIN;"
  echo "CREATE TEMP TABLE live_user (id uuid PRIMARY KEY) ON COMMIT DROP;"
  echo "COPY live_user (id) FROM stdin;"
  printf '%s\n' "$live_users"
  echo '\.'
  echo "DELETE FROM partners.user_addresses WHERE user_id NOT IN (SELECT id FROM live_user);"
  echo "COMMIT;"
} | psql_in partners

echo "==> Xoá vận đơn, báo giá, tiền, chuyến xe"
truncate_schema shipment shipments
truncate_schema billing billing
truncate_schema fleet fleet
psql_in pricing <<< "TRUNCATE pricing.quote_requests CASCADE;"
for queue in billing.shipment-events billing.shipment-events.dlq; do
  docker exec pavex-backend-rabbitmq-1 rabbitmqctl -q purge_queue "$queue" >/dev/null 2>&1 || true
done

if $start; then
  echo "==> Khởi động lại"
  "$repo_dir/scripts/dev-up.sh"
fi

echo
echo "Đã nạp seed-mini. Tài khoản @pavex.test dùng mật khẩu: $SEED_PASSWORD (danh sách trong scripts/seed-mini/README.md)"
echo "Bản sao lưu: $backup_dir"
