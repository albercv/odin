# Funciones de las órdenes de la instalación (D-056). Se carga desde la raíz del repositorio:
#   . deploy/instalacion/comun.sh
# INSTALACION_PROYECTO (por defecto odin) e INSTALACION_ENV (por defecto deploy/.env) eligen la pila.
# Las funciones devuelven error en cada paso con «|| return 1»: set -e no actúa dentro de un if ni de un trap.

PROYECTO="${INSTALACION_PROYECTO:-odin}"
ENV_FILE="${INSTALACION_ENV:-deploy/.env}"
COPIAS=data/copias
umask 077 # las copias llevan datos de la instalación: solo para su dueño

[ -f "$ENV_FILE" ] || { echo "Falta $ENV_FILE (cópialo de deploy/.env.example)." >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

compose() { docker compose -p "$PROYECTO" -f deploy/compose.yaml --env-file "$ENV_FILE" "$@"; }

sql() { compose exec -T postgres psql -U "$ODIN_DB_OWNER_USER" -d "$ODIN_DB_NAME" -v ON_ERROR_STOP=1 -q -tA "$@"; }

en_marcha() {
  [ -n "$(compose ps -q --status running postgres 2>/dev/null)" ] && [ -n "$(compose ps -q --status running api 2>/dev/null)" ]
}

recuento() {
  sql -c "SELECT (SELECT count(*) FROM app_user WHERE deleted_at IS NULL) || ' personas, '
    || (SELECT count(*) FROM company) || ' empresas, '
    || (SELECT count(*) FROM project WHERE deleted_at IS NULL) || ' proyectos, '
    || (SELECT count(*) FROM document) || ' documentos y '
    || (SELECT count(*) FROM knowledge_card) || ' fichas'" 2>/dev/null || echo "nada (base sin tablas)"
}

# copiar <motivo>: copia la base y los originales en data/copias/. Deja las rutas en COPIA_SQL y COPIA_ORIGINALES.
copiar() {
  local fecha version
  fecha=$(date +%Y-%m-%d-%H%M%S)
  version=$(sql -c "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null || true)
  mkdir -p "$COPIAS" || return 1
  COPIA_SQL="$COPIAS/odin-v${version:-0}-antes-$1-$fecha.sql"
  COPIA_ORIGINALES="$COPIAS/originales-antes-$1-$fecha"
  compose exec -T postgres pg_dump -U "$ODIN_DB_OWNER_USER" -d "$ODIN_DB_NAME" > "$COPIA_SQL" || return 1
  grep -q "PostgreSQL database dump complete" "$COPIA_SQL" || { echo "La copia de la base ha salido incompleta: $COPIA_SQL" >&2; return 1; }
  compose cp api:/var/lib/odin/originals "$COPIA_ORIGINALES" || return 1
  recuento > "$COPIAS/recuento-antes-$1-$fecha.txt" || return 1
}

# vaciar: borra todos los datos. Conserva el rol odin_app, que vive fuera de la base (V4).
vaciar() {
  compose stop api mcp-http || return 1
  sql -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public AUTHORIZATION pg_database_owner; GRANT USAGE ON SCHEMA public TO PUBLIC;" || return 1
  compose rm -f api || return 1
  docker volume rm "${PROYECTO}_originals" > /dev/null || return 1
}

# arrancar: levanta la pila con las imágenes que ya tiene; Flyway crea el esquema si está vacío.
arrancar() {
  compose up -d --wait || return 1
}

# restaurar <copia.sql> [carpeta de originales]: vuelve a una copia hecha con copiar.
restaurar() {
  vaciar || return 1
  sql < "$1" > /dev/null || return 1
  if [ -n "${2:-}" ] && [ -d "$2" ]; then
    compose run --rm --no-deps --user root -v "$(cd "$2" && pwd):/copia:ro" --entrypoint sh api \
      -c 'cp -a /copia/. /var/lib/odin/originals/ && chown -R odin /var/lib/odin/originals' || return 1
  fi
  arrancar || return 1
}
