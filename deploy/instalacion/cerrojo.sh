#!/usr/bin/env bash
# Ejecuta una orden con el cerrojo de la instalación (D-056): nunca dos órdenes a la vez sobre ella.
set -euo pipefail
# El cerrojo vive en la copia principal: vale también para las órdenes lanzadas desde una worktree.
cd "$(dirname "$0")/../.."
PRINCIPAL=$(git rev-parse --path-format=absolute --git-common-dir 2> /dev/null || true)
DATOS=data
[ "${INSTALACION_PROYECTO:-odin}" = odin ] || DATOS="data/pilas/$INSTALACION_PROYECTO"
CERROJO="${PRINCIPAL:+${PRINCIPAL%/.git}/}$DATOS/instalacion.lock"
mkdir -p "$(dirname "$CERROJO")"
if ! mkdir "$CERROJO" 2> /dev/null; then
  echo "Otra orden está usando la instalación ($CERROJO). Espera a que termine." >&2
  echo "Si no hay ninguna en marcha, borra esa carpeta: rmdir \"$CERROJO\"" >&2
  exit 1
fi
trap 'rmdir "$CERROJO"' EXIT
"$@"
