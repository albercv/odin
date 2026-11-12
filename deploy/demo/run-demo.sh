#!/usr/bin/env bash
# Demostración de cierre (spec 3.1) en una pila Compose propia y desechable (-p odin-demo, o el de DEMO_PROJECT).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# DEMO_PROJECT permite pasar dos demos a la vez sin que una baje la pila de la otra.
DEMO_PROJECT="${DEMO_PROJECT:-odin-demo}"
export DEMO_API_PORT="${DEMO_API_PORT:-18080}" DEMO_PANEL_PORT="${DEMO_PANEL_PORT:-18088}" DEMO_MCP_PORT="${DEMO_MCP_PORT:-18090}"
TMP="$(mktemp -d)"
ENV_FILE="$TMP/demo.env"
cat > "$ENV_FILE" <<EOF
ODIN_DB_OWNER_USER=demo_owner
ODIN_DB_OWNER_PASSWORD=demo-falsa-dueno-$$
ODIN_DB_APP_PASSWORD=demo-falsa-app-$$
ODIN_DB_NAME=odin
ODIN_SETUP_TOKEN=demo-falso-token-$$
ODIN_PANEL_ORIGIN=http://127.0.0.1:$DEMO_PANEL_PORT
ODIN_UPLOAD_MAX_BYTES=52428800
ODIN_API_PORT=$DEMO_API_PORT
ODIN_PANEL_PORT=$DEMO_PANEL_PORT
ODIN_MCP_PORT=$DEMO_MCP_PORT
EOF
COMPOSE=(docker compose -p "$DEMO_PROJECT" -f "$ROOT/deploy/compose.yaml" -f "$ROOT/deploy/demo/compose.reader-stub.yaml" -f "$ROOT/deploy/demo/compose.tagger-stub.yaml" --env-file "$ENV_FILE")
cleanup() {
  [ $? -eq 0 ] || { echo "== últimas líneas del registro de postgres y la API =="; "${COMPOSE[@]}" logs --no-color --tail 40 postgres api 2>&1 || true; }
  "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true; rm -rf "$TMP"; }
trap cleanup EXIT

export DEMO_API_URL="http://127.0.0.1:$DEMO_API_PORT" DEMO_PANEL_URL="http://127.0.0.1:$DEMO_PANEL_PORT"
export DEMO_SETUP_TOKEN="demo-falso-token-$$" DEMO_STATE="$TMP/state.json" DEMO_PDF="$ROOT/deploy/demo/poliza-ficticia.pdf"
export DEMO_RENEWED="$ROOT/deploy/demo/poliza-ficticia-renovada.txt" DEMO_SCAN="$ROOT/deploy/demo/escaneo-ficticio.png"
export DEMO_CASE_1="$ROOT/deploy/demo/caso-ficticio-1.md" DEMO_CASE_2="$ROOT/deploy/demo/caso-ficticio-2.md"
export PLAYWRIGHT_BROWSERS_PATH=0

echo "== Demostración de cierre de la fase 1 =="
(cd "$ROOT/mcp" && npm ci --silent && npm run build --silent)
(cd "$ROOT/panel" && npm ci --silent && npx playwright install chromium >/dev/null)
COMPOSE_PROGRESS=quiet "${COMPOSE[@]}" up -d --build --wait
(cd "$ROOT/mcp" && npx tsx scripts/demo-client.ts prepare)
(cd "$ROOT/panel" && npx playwright test)
(cd "$ROOT/mcp" && npx tsx scripts/demo-client.ts confirm)
echo "== Demostración completa: todos los pasos han pasado =="
