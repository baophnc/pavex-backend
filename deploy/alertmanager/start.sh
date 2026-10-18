#!/bin/sh
# Writes the Alertmanager configuration from the environment (Alertmanager does not read variables itself),
# then starts it. Without ALERT_EMAIL_TO alerts are only visible in Prometheus and Alertmanager.
set -eu
config=/alertmanager/alertmanager.yml
password_file=/alertmanager/smtp-password
umask 077
printf '%s' "${MAIL_PASSWORD:-}" >"$password_file"

if [ -z "${ALERT_EMAIL_TO:-}" ]; then
  cat >"$config" <<YAML
route:
  receiver: none
receivers:
  - name: none
YAML
else
  case "${MAIL_STARTTLS:-true}" in true) tls=true ;; *) tls=false ;; esac
  {
    echo "global:"
    echo "  smtp_smarthost: '${MAIL_HOST}:${MAIL_PORT:-587}'"
    echo "  smtp_from: '${MAIL_FROM}'"
    echo "  smtp_require_tls: ${tls}"
    if [ -n "${MAIL_USERNAME:-}" ]; then
      echo "  smtp_auth_username: '${MAIL_USERNAME}'"
      echo "  smtp_auth_password_file: ${password_file}"
    fi
    cat <<YAML
route:
  receiver: email
  group_by: [alertname, service]
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
receivers:
  - name: email
    email_configs:
      - to: '${ALERT_EMAIL_TO}'
        send_resolved: true
        headers:
          Subject: '[Pavex ${PAVEX_DOMAIN}] {{ .Status | toUpper }} {{ .CommonLabels.alertname }}'
YAML
  } >"$config"
fi

exec /bin/alertmanager --config.file="$config" --storage.path=/alertmanager \
  --web.external-url=http://localhost:9093/
