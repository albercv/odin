#!/usr/bin/env bash
# Compila mcp/ de esta copia del repositorio y registra la entrada «odin» de Claude Desktop con la clave de un
# trabajador de prueba (TRABAJADOR, por defecto marta). Copia antes el fichero de configuración y no toca lo demás.
set -euo pipefail
cd "$(dirname "$0")/../.."
CREDENCIALES=data/datos-prueba/credenciales.local.json
CONFIG="${CLAUDE_DESKTOP_CONFIG:-$HOME/Library/Application Support/Claude/claude_desktop_config.json}"

[ -f "$CREDENCIALES" ] || { echo "No hay datos de prueba cargados ($CREDENCIALES). Primero: make datos-prueba-cargar" >&2; exit 1; }
NODE=$(command -v node) || { echo "Falta Node 24 o superior." >&2; exit 1; }
[ "$("$NODE" -p 'process.versions.node.split(".")[0]')" -ge 24 ] || { echo "Hace falta Node 24 o superior ($NODE es $("$NODE" -v))." >&2; exit 1; }

echo "Compilando mcp/ de $(git rev-parse --short HEAD 2> /dev/null || echo 'esta copia')…"
(cd mcp && npm ci --silent && npm run build --silent)
python3 deploy/instalacion/claude_desktop.py --config "$CONFIG" --credenciales "$CREDENCIALES" \
  --trabajador "${TRABAJADOR:-marta}" --repo . --node "$NODE" --copias data/copias
