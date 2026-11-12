#!/usr/bin/env bash
# Deja la instalación vacía, lista para el primer arranque, con copia previa de la base y los originales (D-056).
# Sin CONFIRMAR=BORRAR solo explica qué haría. Si falla a medias, vuelve sola a la copia.
set -euo pipefail
cd "$(dirname "$0")/../.."
. deploy/instalacion/comun.sh
MOTIVO="${MOTIVO:-reinicio}"

if [ "${CONFIRMAR:-}" != "BORRAR" ]; then
  echo "No se ha borrado nada. Con CONFIRMAR=BORRAR, esta orden haría esto en la instalación «$PROYECTO»:"
  echo "  1. copiar la base y los documentos originales en $COPIAS/;"
  echo "  2. borrar todos los datos: grupo, empresas, personas, claves, proyectos, documentos, temas y fichas;"
  echo "  3. arrancarla vacía, lista para el primer arranque o para make datos-prueba-cargar."
  if en_marcha; then echo "Hoy tiene: $(recuento)."; else echo "Ahora no está en marcha (make up)."; fi
  echo "Para hacerlo: make instalacion-reiniciar CONFIRMAR=BORRAR"
  exit 0
fi

en_marcha || { echo "La instalación «$PROYECTO» no está en marcha: arráncala con make up para poder copiarla antes. No se ha borrado nada." >&2; exit 1; }
antes=$(recuento)
copiar "$MOTIVO" || { echo "No se ha podido hacer la copia. No se ha borrado nada." >&2; exit 1; }
echo "Copia hecha: $COPIA_SQL (originales en $COPIA_ORIGINALES)."

if ! vaciar || ! arrancar; then
  echo "Ha fallado a medias: vuelvo a la copia." >&2
  if restaurar "$COPIA_SQL" "$COPIA_ORIGINALES"; then
    echo "La instalación ha vuelto a la copia: $(recuento)." >&2
  else
    echo "No ha podido volver sola. Prueba: make instalacion-restaurar COPIA=$COPIA_SQL CONFIRMAR=BORRAR" >&2
  fi
  exit 1
fi

echo "Instalación vacía y lista para el primer arranque. Ahora tiene: $(recuento)."
echo "Lo que había ($antes) queda en la copia: $COPIA_SQL"
echo "Para volver a ello: make instalacion-restaurar COPIA=$COPIA_SQL CONFIRMAR=BORRAR"
