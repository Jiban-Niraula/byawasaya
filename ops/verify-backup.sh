#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 /path/to/byawasaya-backup.dump" >&2
  exit 2
fi

backup_file="$(realpath -- "$1")"
checksum_file="$backup_file.sha256"
[[ -f "$checksum_file" ]] || { echo "Checksum file not found: $checksum_file" >&2; exit 1; }
(cd -- "$(dirname -- "$backup_file")" && sha256sum --check --status "$(basename -- "$checksum_file")") || {
  echo "Backup checksum verification failed." >&2
  exit 1
}

docker run --rm -i postgres:17-alpine pg_restore --list < "$backup_file" >/dev/null
echo "Checksum and PostgreSQL archive structure are valid. Restore into an isolated database to complete a recovery drill."
