#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "$script_dir/.." && pwd)"
env_file="${BYAWASAYA_ENV_FILE:-$project_dir/.env.production}"
backup_dir="${BACKUP_DIR:-/var/backups/byawasaya}"
retention_days="${BACKUP_RETENTION_DAYS:-30}"

[[ -r "$env_file" ]] || { echo "Cannot read environment file: $env_file" >&2; exit 1; }
[[ "$retention_days" =~ ^[1-9][0-9]*$ ]] || { echo "BACKUP_RETENTION_DAYS must be a positive integer." >&2; exit 1; }
mkdir -p -- "$backup_dir"
chmod 700 -- "$backup_dir"
exec 9>"$backup_dir/.backup.lock"
flock -n 9 || { echo "A backup is already running." >&2; exit 1; }

timestamp="$(date -u +%Y%m%dT%H%M%SZ)-$$"
backup_file="$backup_dir/byawasaya-$timestamp.dump"
temporary_file="$backup_file.partial"
trap 'rm -f -- "$temporary_file"' EXIT

docker compose --env-file "$env_file" -f "$project_dir/docker-compose.production.yml" \
  exec -T postgres sh -ec 'pg_dump --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$temporary_file"

[[ -s "$temporary_file" ]] || { echo "The database backup is empty." >&2; exit 1; }
docker compose --env-file "$env_file" -f "$project_dir/docker-compose.production.yml" \
  exec -T postgres sh -ec 'pg_restore --list' < "$temporary_file" >/dev/null
mv -- "$temporary_file" "$backup_file"
sha256sum "$backup_file" > "$backup_file.sha256"

if [[ -n "${BACKUP_S3_URI:-}" ]]; then
  command -v aws >/dev/null || { echo "aws CLI is required when BACKUP_S3_URI is set." >&2; exit 1; }
  s3_encryption=(--sse AES256)
  if [[ -n "${BACKUP_S3_KMS_KEY_ID:-}" ]]; then
    s3_encryption=(--sse aws:kms --sse-kms-key-id "$BACKUP_S3_KMS_KEY_ID")
  fi
  aws s3 cp "$backup_file" "${BACKUP_S3_URI%/}/$(basename "$backup_file")" --only-show-errors "${s3_encryption[@]}"
  aws s3 cp "$backup_file.sha256" "${BACKUP_S3_URI%/}/$(basename "$backup_file").sha256" --only-show-errors "${s3_encryption[@]}"
fi

find "$backup_dir" -maxdepth 1 -type f \( -name 'byawasaya-*.dump' -o -name 'byawasaya-*.dump.sha256' \) \
  -mtime "+$retention_days" -delete
echo "Database backup created: $backup_file"
