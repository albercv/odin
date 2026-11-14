#!/usr/bin/env bash
# Añade un proyecto nuevo a la instalación SIN vaciarla (encargo 14, D-088): copia de la base y los originales, proyecto
# con sus miembros, documentos, sustituciones, fichas y expedientes, comprobación de aislamiento y recuento.
# El lector OCR y el etiquetado siguen después solos. Lo que ya había no se toca.
# Sin CONFIRMAR=ANADIR solo comprueba los datos y la instalación y explica qué haría.
# Si algo falla después de la copia, vuelve a ella. MIEMBROS (por defecto marta) son quienes lo ven.
set -euo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
[ -d "${DATOS_PROYECTO:-}" ] || { echo "Indica la carpeta de datos: DATOS_PROYECTO=<carpeta con manifiesto.json>" >&2; exit 1; }
DATOS_PROYECTO=$(cd "$DATOS_PROYECTO" && pwd) # comun.sh cambia de carpeta
. "$AQUI/comun.sh"
REGISTRO="$DATOS/anadir-proyecto-$(date +%Y-%m-%d-%H%M%S).log"

en_marcha || { echo "La instalación «$PROYECTO» no está en marcha (make up). No se ha cambiado nada." >&2; exit 1; }
python3 "$AQUI/anadir_proyecto.py" --comprobar "$DATOS_PROYECTO"
if [ "${CONFIRMAR:-}" != ANADIR ]; then
  echo "No se ha cambiado nada. Con CONFIRMAR=ANADIR, esta orden haría en la instalación «$PROYECTO»:"
  echo "  1. copia de la base y de los originales en $COPIAS/;"
  echo "  2. añadir el proyecto, sus documentos, fichas y expedientes, sin tocar lo que ya hay;"
  echo "  3. comprobar el aislamiento por la API y sacar el recuento. El lector OCR y el etiquetado siguen solos."
  echo "Hoy tiene: $(recuento)."
  exit 0
fi

volver() {
  echo "Ha fallado: vuelvo a la copia (detalle en $REGISTRO)." >&2
  restaurar "$COPIA_SQL" "$COPIA_ORIGINALES" >> "$REGISTRO" 2>&1 \
    && echo "Instalación devuelta a la copia: $(recuento)." >&2 \
    || echo "No ha podido volver sola: make instalacion-restaurar COPIA=$COPIA_SQL CONFIRMAR=BORRAR" >&2
  exit 1
}

export CONFIRMAR_INSTALACION=PROPIETARIO # D-099: confirmada con CONFIRMAR=ANADIR
copiar anadir-proyecto || { echo "No se ha podido copiar. No se ha cambiado nada." >&2; exit 1; }
echo "Copia hecha: $COPIA_SQL y $COPIA_ORIGINALES. Antes: $(recuento)."
trap volver ERR
python3 "$AQUI/anadir_proyecto.py" "$DATOS_PROYECTO" 2>&1 | tee -a "$REGISTRO"
"$AQUI/recuento.sh" > "$DATOS/recuento-$(date +%Y-%m-%d-%H%M%S).md"
trap - ERR
echo "Hecho. Ahora tiene: $(recuento). Recuento en $DATOS/recuento-*.md."
