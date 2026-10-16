#!/bin/sh
set -eu
: "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"
umask 077
printf 'appendonly yes\nrequirepass %s\n' "$REDIS_PASSWORD" > /tmp/redis.conf
exec redis-server /tmp/redis.conf
