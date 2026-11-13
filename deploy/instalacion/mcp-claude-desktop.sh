#!/usr/bin/env bash
# Compila mcp/ de esta copia del repositorio y registra la entrada «odin» de Claude Desktop con la clave de un
# trabajador de prueba (TRABAJADOR, por defecto marta). Copia antes el fichero de configuración y no toca lo demás.
# Claude Desktop arranca una copia del MCP compilado en data/mcp-claude-desktop/ de la copia principal: recompilar o
# cambiar de rama no le cambia el MCP; para actualizarlo, se vuelve a lanzar esta orden.
set -euo pipefail
cd "$(dirname "$0")/../.."
PRINCIPAL=$(git rev-parse --path-format=absolute --git-common-dir 2> /dev/null || true)
PRINCIPAL="${PRINCIPAL:+${PRINCIPAL%/.git}}"
PRINCIPAL="${PRINCIPAL:-$PWD}"
DATOS=$PRINCIPAL/data
[ "${INSTALACION_PROYECTO:-odin}" = odin ] || DATOS="$PRINCIPAL/data/pilas/$INSTALACION_PROYECTO"
CREDENCIALES=$DATOS/datos-prueba/credenciales.local.json
DESTINO=$DATOS/mcp-claude-desktop
# Solo la instalación «odin» toca el Claude Desktop de verdad; una pila de ensayo usa un fichero suyo.
CONFIG_REAL="$HOME/Library/Application Support/Claude/claude_desktop_config.json"
[ "${INSTALACION_PROYECTO:-odin}" = odin ] || CONFIG_REAL="$DATOS/claude_desktop_config.json"
CONFIG="${CLAUDE_DESKTOP_CONFIG:-$CONFIG_REAL}"

[ -f "$CREDENCIALES" ] || { echo "No hay datos de prueba cargados ($CREDENCIALES). Primero: make datos-prueba-cargar" >&2; exit 1; }
NODE=$(command -v node) || { echo "Falta Node 24 o superior." >&2; exit 1; }
[ "$("$NODE" -p 'process.versions.node.split(".")[0]')" -ge 24 ] || { echo "Hace falta Node 24 o superior ($NODE es $("$NODE" -v))." >&2; exit 1; }

echo "Compilando mcp/ de $(git rev-parse --short HEAD 2> /dev/null || echo 'esta copia')…"
(cd mcp && npm ci --silent && npm run build --silent)
rm -rf "$DESTINO.nuevo" "$DESTINO.viejo"
mkdir -p "$DESTINO.nuevo"
cp -R mcp/dist mcp/package.json mcp/package-lock.json "$DESTINO.nuevo/"
(cd "$DESTINO.nuevo" && npm ci --omit=dev --ignore-scripts --silent)
git rev-parse --short HEAD > "$DESTINO.nuevo/VERSION" 2> /dev/null || true
if [ -d "$DESTINO" ]; then mv "$DESTINO" "$DESTINO.viejo"; fi
mv "$DESTINO.nuevo" "$DESTINO"
rm -rf "$DESTINO.viejo"
echo "MCP copiado en $DESTINO."
# SOLO_COPIA=si (al actualizar): Claude Desktop ya apunta a esta copia; no se cambia su trabajador ni su clave
[ "${SOLO_COPIA:-}" = si ] && { echo "Conector de Claude Desktop sin cambios: sigue con el mismo trabajador."; exit 0; }
# Claude Desktop reescribe su configuración desde memoria: si está abierto, puede deshacer este registro
[ "$CONFIG" = "$HOME/Library/Application Support/Claude/claude_desktop_config.json" ] && pgrep -xq Claude \
  && echo "AVISO: Claude Desktop está abierto y puede volver a la configuración anterior al guardar sus preferencias." \
     "Ciérralo del todo (Cmd+Q), vuelve a lanzar esta orden y ábrelo después." >&2
python3 deploy/instalacion/claude_desktop.py --config "$CONFIG" --credenciales "$CREDENCIALES" \
  --trabajador "${TRABAJADOR:-marta}" --mcp "$DESTINO" --node "$NODE" --copias "$DATOS/copias"
