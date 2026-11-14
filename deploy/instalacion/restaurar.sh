#!/usr/bin/env bash
# Vuelve la instalación a una copia de data/copias/ (la base y su carpeta de originales). Antes copia lo que hay.
# Sin CONFIRMAR=BORRAR solo explica qué haría.
set -euo pipefail
cd "$(dirname "$0")/../.."
. deploy/instalacion/comun.sh

if [ -z "${COPIA:-}" ] || [ ! -f "$COPIA" ]; then
  echo "Indica una copia que exista: make instalacion-restaurar COPIA=data/copias/odin-….sql CONFIRMAR=BORRAR" >&2
  echo "Copias disponibles:" >&2
  ls -1t "$COPIAS"/odin-*.sql 2> /dev/null | head -10 >&2 || true
  exit 1
fi
ORIGINALES="${ORIGINALES:-$COPIAS/originales-$(basename "$COPIA" .sql | sed -E 's/^odin-v[^-]*-//')}"

if [ "${CONFIRMAR:-}" != "BORRAR" ]; then
  echo "No se ha cambiado nada. Con CONFIRMAR=BORRAR, esta orden haría esto en la instalación «$PROYECTO»:"
  echo "  1. copiar lo que hay ahora en $COPIAS/;"
  echo "  2. sustituirlo por la copia $COPIA y los originales de $ORIGINALES."
  if en_marcha; then echo "Hoy tiene: $(recuento)."; else echo "Ahora no está en marcha (make up)."; fi
  exit 0
fi
[ -d "$ORIGINALES" ] || echo "Aviso: no está la carpeta de originales $ORIGINALES; se restaura solo la base." >&2

en_marcha || { echo "La instalación «$PROYECTO» no está en marcha: arráncala con make up. No se ha cambiado nada." >&2; exit 1; }
copiar restauracion || { echo "No se ha podido copiar lo que hay. No se ha cambiado nada." >&2; exit 1; }
echo "Copia de lo que había: $COPIA_SQL."

if ! restaurar "$COPIA" "$ORIGINALES"; then
  echo "Ha fallado a medias: vuelvo a lo que había." >&2
  restaurar "$COPIA_SQL" "$COPIA_ORIGINALES" || echo "No ha podido volver sola: make instalacion-restaurar COPIA=$COPIA_SQL CONFIRMAR=BORRAR" >&2
  exit 1
fi
echo "Instalación restaurada desde $COPIA. Ahora tiene: $(recuento)."
