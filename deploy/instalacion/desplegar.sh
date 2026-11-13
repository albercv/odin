#!/usr/bin/env bash
# Despliegue conjunto en la instalación (D-056, D-082): copia, código de integracion/instalacion, lector y etiquetado,
# instalación vacía, archivo ficticio completo con expedientes, recuento y conector de Claude Desktop.
# Sin CONFIRMAR=DESPLEGAR solo explica qué haría. Si algo falla después de tocar la instalación, vuelve a la copia.
# MODO=actualizar (make instalacion-actualizar CONFIRMAR=ACTUALIZAR) solo cambia el código: conserva la base y los
# originales, reintenta los documentos fallidos y refresca el MCP sin cambiar el trabajador registrado.
# En una pila de ensayo (INSTALACION_PROYECTO distinto de odin) no cambia la copia principal ni su deploy/.env.
# CONSTRUIR=no arranca con las imágenes que ya hay (ensayos con imágenes hechas desde otra worktree).
set -euo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
. "$AQUI/comun.sh"
RAMA="${RAMA:-integracion/instalacion}"
TRABAJADOR="${TRABAJADOR:-marta}"
CONSTRUIR="${CONSTRUIR:-si}"
MODO="${MODO:-cargar}"
PALABRA=DESPLEGAR
[ "$MODO" = actualizar ] && PALABRA=ACTUALIZAR
REGISTRO="$DATOS/despliegue-$(date +%Y-%m-%d-%H%M%S).log"

explicar() {
  echo "No se ha cambiado nada. Con CONFIRMAR=$PALABRA, esta orden haría en la instalación «$PROYECTO»:"
  echo "  1. copia de la base, de los originales, de $ENV_FILE y de la configuración de Claude Desktop;"
  echo "  2. poner la copia principal en $RAMA ($(git rev-parse --short "$RAMA")) y añadir a $ENV_FILE lo que falte:"
  echo "     ODIN_PUBLIC_URL, ODIN_READER_URL (puente OCR en el Mac) y ODIN_TAGGER_URL (Ollama);"
  if [ "$MODO" = actualizar ]; then
    echo "  3. reconstruir y arrancar (make up) conservando la base y los originales, y reintentar los documentos fallidos;"
    echo "  4. recuento en la base, MCP de Claude Desktop renovado (mismo trabajador) y comprobaciones."
  else
    echo "  3. reconstruir y arrancar (make up), VACIAR la instalación y cargar el archivo ficticio completo;"
    echo "  4. recuento en la base, conector de Claude Desktop con «$TRABAJADOR» y comprobaciones."
  fi
  if en_marcha; then echo "Hoy tiene: $(recuento)."; fi
  git merge-base --is-ancestor feature/casos-expediente "$RAMA" 2> /dev/null \
    && echo "El expediente (encargo 13) está en $RAMA." || echo "AVISO: el expediente (encargo 13) NO está en $RAMA."
}

anadir_si_falta() {  # anadir_si_falta CLAVE VALOR: añade la variable a deploy/.env si no tiene valor
  grep -q "^$1=." "$ENV_FILE" || printf '%s=%s\n' "$1" "$2" >> "$ENV_FILE" || return 1
}

preparar_codigo() {
  [ "$PROYECTO" = odin ] || return 0
  [ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "La copia principal tiene cambios sin commit." >&2; return 1; }
  git checkout -q --detach "$RAMA" || return 1
  anadir_si_falta ODIN_PUBLIC_URL "${ODIN_PANEL_ORIGIN:-http://127.0.0.1:8088}" || return 1
  anadir_si_falta ODIN_READER_URL http://host.docker.internal:8077 || return 1
  anadir_si_falta ODIN_TAGGER_URL http://host.docker.internal:11434 || return 1
}

volver() {
  echo "Ha fallado: vuelvo a lo que había (detalle en $REGISTRO)." >&2
  if [ "$PROYECTO" = odin ]; then
    git checkout -q --detach "$ANTES" && cp -p "$COPIA_ENV" "$ENV_FILE" || true
  fi
  compose up -d --build --wait >> "$REGISTRO" 2>&1 && restaurar "$COPIA_SQL" "$COPIA_ORIGINALES" >> "$REGISTRO" 2>&1 \
    && echo "Instalación devuelta a la copia: $(recuento)." >&2 \
    || echo "No ha podido volver sola: make instalacion-restaurar COPIA=$COPIA_SQL CONFIRMAR=BORRAR" >&2
  exit 1
}

comprobar() {
  local api="http://127.0.0.1:${ODIN_API_PORT:-8080}" panel="http://127.0.0.1:${ODIN_PANEL_PORT:-8088}"
  local mcp="http://127.0.0.1:${ODIN_MCP_PORT:-8090}/mcp" cuerpo='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"c","version":"1"}}}'
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$api/v1/health")" = 200 ] || { echo "La API no responde" >&2; return 1; }
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$panel/")" = 200 ] || { echo "El panel no responde" >&2; return 1; }
  [ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$mcp" -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' -d "$cuerpo")" = 401 ] || { echo "El MCP no pide clave" >&2; return 1; }
  echo "API, panel y MCP responden; el MCP rechaza las peticiones sin clave."
}

[ "${CONFIRMAR:-}" = "$PALABRA" ] || { explicar; exit 0; }
export CONFIRMAR_INSTALACION=PROPIETARIO # D-099: confirmada con CONFIRMAR=$PALABRA
en_marcha || { echo "La instalación «$PROYECTO» no está en marcha (make up). No se ha cambiado nada." >&2; exit 1; }
mkdir -p "$DATOS/copias"
curl -s -m 3 http://127.0.0.1:8077/health > /dev/null || echo "Aviso: el puente OCR no responde; los escaneos esperarán al lector."
copiar "despliegue-$MODO" || { echo "No se ha podido copiar. No se ha cambiado nada." >&2; exit 1; }
COPIA_ENV="$DATOS/copias/env-antes-despliegue-$(date +%Y-%m-%d-%H%M%S)"
cp -p "$ENV_FILE" "$COPIA_ENV"
ANTES=$(git rev-parse HEAD)
echo "Copia hecha: $COPIA_SQL, $COPIA_ORIGINALES y $COPIA_ENV."

trap volver ERR
preparar_codigo
set -a; . "$ENV_FILE"; set +a
if [ "$CONSTRUIR" = si ]; then compose up -d --build --wait >> "$REGISTRO" 2>&1; else compose up -d --wait >> "$REGISTRO" 2>&1; fi
if [ "$MODO" = actualizar ]; then
  python3 "$AQUI/datos_prueba.py" --reintentar-fallidos | tee -a "$REGISTRO" \
    || echo "Aviso: no se han podido reintentar los documentos fallidos; se puede hacer desde el panel." >&2
else
  CONFIRMAR=BORRAR MOTIVO=antes-de-carga "$AQUI/reiniciar.sh" >> "$REGISTRO" 2>&1
  ARCHIVO=completo python3 "$AQUI/datos_prueba.py" | tee -a "$REGISTRO"
fi
"$AQUI/recuento.sh" > "$DATOS/recuento-$(date +%Y-%m-%d-%H%M%S).md"
[ "$MODO" = actualizar ] && export SOLO_COPIA=si
TRABAJADOR="$TRABAJADOR" "$AQUI/mcp-claude-desktop.sh" | tee -a "$REGISTRO"
comprobar
trap - ERR
echo "Desplegado $(git rev-parse --short HEAD) en «$PROYECTO». Ahora tiene: $(recuento)."
echo "Recuento en $DATOS/recuento-*.md. Reinicia Claude Desktop para que cargue el MCP nuevo."
