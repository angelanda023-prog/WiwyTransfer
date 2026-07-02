#!/usr/bin/env bash
# Hace un "ping" a Supabase (lectura + escritura) para evitar que el proyecto
# se pause por inactividad (plan gratuito ~7 días sin actividad).
#
# Variables requeridas:
#   SUPABASE_URL  -> https://xxxxxxxx.supabase.co
#   SUPABASE_KEY  -> service_role key (recomendado) o anon key
#
# Requiere una tabla public.keepalive (ver scripts/supabase-keepalive.sql).
set -euo pipefail

: "${SUPABASE_URL:?falta SUPABASE_URL}"
: "${SUPABASE_KEY:?falta SUPABASE_KEY}"

url="${SUPABASE_URL%/}"
ts="$(date -u +%FT%TZ)"

echo "→ Ping a Supabase: ${url}  (${ts})"

# 1) Escritura (upsert) -> actividad fuerte en la BD
code=$(curl -sS -o /tmp/ka.out -w "%{http_code}" \
  -X POST "${url}/rest/v1/keepalive?on_conflict=id" \
  -H "apikey: ${SUPABASE_KEY}" \
  -H "Authorization: Bearer ${SUPABASE_KEY}" \
  -H "Content-Type: application/json" \
  -H "Prefer: resolution=merge-duplicates,return=minimal" \
  -d "[{\"id\":1,\"last_ping\":\"${ts}\"}]" || true)
echo "  upsert -> HTTP ${code}"

# 2) Si el upsert falla (p. ej. no existe la tabla), intenta solo lectura
if [ "${code:-0}" -ge 400 ] || [ "${code:-0}" -eq 0 ]; then
  echo "  (upsert falló) respuesta:"; sed 's/^/    /' /tmp/ka.out || true
  code=$(curl -sS -o /tmp/ka.out -w "%{http_code}" \
    "${url}/rest/v1/keepalive?select=id&limit=1" \
    -H "apikey: ${SUPABASE_KEY}" \
    -H "Authorization: Bearer ${SUPABASE_KEY}" || true)
  echo "  select -> HTTP ${code}"
fi

if [ "${code:-0}" -ge 400 ] || [ "${code:-0}" -eq 0 ]; then
  echo "✗ Supabase keepalive FALLÓ (HTTP ${code})"; sed 's/^/    /' /tmp/ka.out || true
  exit 1
fi

echo "✓ Supabase activo (${ts})"
