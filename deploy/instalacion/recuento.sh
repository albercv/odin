#!/usr/bin/env bash
# Recuento contado en la base (SQL con el dueño de la base), en tablas Markdown por la salida estándar.
# Variables: INSTALACION_ENV e INSTALACION_PROYECTO, como el resto de órdenes de la instalación.
set -euo pipefail
cd "$(dirname "$0")/../.."
RAIZ=$PWD # comun.sh pasa a la copia principal; el archivo ficticio se lee de esta copia
. deploy/instalacion/comun.sh
asegurar_archivo_ficticio || exit 1

# tabla "Título" "Col1 | Col2" "SELECT ... (columnas separadas por ' | ' al imprimir)"
tabla() {
  printf '\n### %s\n\n| %s |\n' "$1" "$2"
  echo "| $2 |" | sed -E 's/[^|]+/ --- /g'
  sql -F ' | ' -c "$3" | sed 's/^/| /; s/$/ |/'
}

echo "# Recuento de la instalación «$PROYECTO» (contado en la base)"
tabla "Empresas" "Empresas" "SELECT count(*) FROM company"
tabla "Proyectos por empresa" "Empresa | Abiertos | Cerrados" \
  "SELECT c.name, count(*) FILTER (WHERE NOT p.restricted), count(*) FILTER (WHERE p.restricted)
   FROM company c LEFT JOIN project p ON p.company_id = c.id AND p.deleted_at IS NULL GROUP BY c.id ORDER BY c.name"
tabla "Personas por papel" "Papel | Personas" \
  "SELECT 'administrador del grupo', count(*) FROM app_user WHERE group_admin AND deleted_at IS NULL
   UNION ALL SELECT 'administrador de empresa', count(DISTINCT user_id) FROM membership WHERE is_company_admin AND deleted_at IS NULL
   UNION ALL SELECT 'trabajador', count(DISTINCT user_id) FROM membership WHERE is_worker AND deleted_at IS NULL"
tabla "Proyectos que ve cada trabajador (los suyos y los abiertos de su empresa)" "Trabajador | Empresa | Proyectos" \
  "SELECT coalesce(u.first_name,'') || ' ' || coalesce(u.last_name,''), c.name,
     (SELECT count(*) FROM project p WHERE p.company_id = m.company_id AND p.deleted_at IS NULL
        AND (NOT p.restricted OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = p.id AND pm.user_id = m.user_id AND pm.removed_at IS NULL)))
   FROM membership m JOIN app_user u ON u.id = m.user_id JOIN company c ON c.id = m.company_id
   WHERE m.is_worker AND m.deleted_at IS NULL ORDER BY c.name, 1"
tabla "Documentos por formato y estado" "Formato | Listos | Esperando al lector | Fallidos | Otros | Total" \
  "SELECT format, count(*) FILTER (WHERE status = 'READY'), count(*) FILTER (WHERE status = 'PENDING_READER'),
     count(*) FILTER (WHERE status = 'FAILED'), count(*) FILTER (WHERE status NOT IN ('READY','PENDING_READER','FAILED')),
     count(*) FROM document GROUP BY format ORDER BY format"
tabla "Páginas por lector" "Lector (reader_name) | Páginas" \
  "SELECT reader_name, count(*) FROM document_page GROUP BY reader_name UNION ALL SELECT 'total', count(*) FROM document_page ORDER BY 1"
tabla "Fichas por estado" "Estado | Fichas" "SELECT status, count(*) FROM knowledge_card GROUP BY status ORDER BY status"
tabla "Casos y sustituidos" "Documentos marcados como caso | Sustituidos" \
  "SELECT count(*) FILTER (WHERE case_document), count(*) FILTER (WHERE superseded_by IS NOT NULL) FROM document"
tabla "Temas por empresa" "Empresa | Temas" \
  "SELECT c.name, count(t.id) FROM company c LEFT JOIN topic t ON t.company_id = c.id AND t.deleted_at IS NULL GROUP BY c.id ORDER BY c.name"
tabla "Documentos con tema, por origen" "Origen | Documentos | Asignaciones" \
  "SELECT source, count(DISTINCT document_id), count(*) FROM document_topic GROUP BY source ORDER BY source"

echo; echo "### Expedientes"; echo
if [ "$(sql -c "SELECT to_regclass('public.case_file_entry') IS NOT NULL")" = "t" ]; then
  tabla "Documentos por expediente" "Expedientes con documentos | Mínimo | Media | Máximo | Enlaces" \
    "SELECT count(*), min(n), round(avg(n), 1), max(n), sum(n) FROM (SELECT count(*) n FROM case_file_entry GROUP BY case_document_id) x"
else
  echo "Esta base no tiene la tabla case_file_entry (la crea la migración V24): no hay expedientes que contar."
fi

echo; echo "### Contradicciones sembradas"; echo
echo "No están en la base: se cuentan de claves.json del archivo ficticio."; echo
echo "| Tipo | Contradicciones |"; echo "| --- | --- |"
python3 - << PY
import json, collections
claves = json.load(open("$RAIZ/datos-prueba/archivo-ficticio/datos/claves.json", encoding="utf-8"))
for tipo, n in sorted(collections.Counter(c["tipo"] for c in claves).items()):
    print(f"| {tipo} | {n} |")
print(f"| total | {len(claves)} |")
PY
