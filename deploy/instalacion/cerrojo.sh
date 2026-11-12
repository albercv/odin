#!/usr/bin/env bash
# Ejecuta una orden con el cerrojo de la instalación (D-056): nunca dos órdenes a la vez sobre ella.
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p data
if ! mkdir data/instalacion.lock 2> /dev/null; then
  echo "Otra orden está usando la instalación (data/instalacion.lock). Espera a que termine." >&2
  echo "Si no hay ninguna en marcha, borra esa carpeta: rmdir data/instalacion.lock" >&2
  exit 1
fi
trap 'rmdir data/instalacion.lock' EXIT
"$@"
