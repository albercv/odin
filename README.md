# Odín

Odín es el cerebro digital de una empresa (Business Digital Mind). Centraliza los documentos y las fichas de conocimiento de cada empresa de un grupo, y responde a la IA del trabajador con fragmentos que citan su fuente: documento, página y texto literal. Si no hay fuente, dice «no consta». Cada empresa está aislada de las demás.

Piezas:
- `api/`: API central en Java 21, hexagonal, sobre PostgreSQL.
- `mcp/`: servidor MCP en TypeScript, por stdio (IA de escritorio) y por HTTP local.
- `panel/`: panel de administración en React.
- `deploy/`: Docker Compose con PostgreSQL, la API, el panel y el MCP por HTTP.

Diseño: `docs/superpowers/specs/2026-11-09-api-central-design.md`.

## Requisitos

- Para usarlo: Docker con Docker Compose.
- Para desarrollar: Java 21 o superior, Maven y Node 24.

## Configuración

```sh
cp deploy/.env.example deploy/.env
```

Cambia en `deploy/.env` las claves y el token; los de `.env.example` son ejemplos falsos. Los puertos se publican solo en `127.0.0.1`.

## Uso

| Orden | Qué hace |
|---|---|
| `make setup` | Instala las dependencias locales de la API, el MCP y el panel |
| `make up` | Construye y arranca la instalación con Docker Compose |
| `make down` | La para |
| `make check` | Pruebas, Checkstyle, ArchUnit, ESLint y tipos de los tres proyectos |
| `make demo` | Demostración de cierre y prueba de extremo a extremo de la administración del panel, en una pila desechable aparte |

## Primer arranque

Tras `make up`, crea el grupo y su primer administrador con el token de instalación de `deploy/.env`. Solo funciona una vez:

```sh
set -a; . deploy/.env; set +a
curl -X POST http://127.0.0.1:8080/v1/setup -H 'Content-Type: application/json' -d "{
  \"setupToken\": \"$ODIN_SETUP_TOKEN\",
  \"groupName\": \"Grupo Ejemplo\",
  \"adminName\": \"Administración\",
  \"adminEmail\": \"admin@ejemplo.invalid\",
  \"password\": \"una-clave-de-12-o-mas\"
}"
```

La contraseña necesita al menos 12 caracteres.

## Panel

Ábrelo en http://127.0.0.1:8088 y entra con ese correo y esa contraseña. Usa `127.0.0.1`, no `localhost`: la API comprueba que el `Origin` sea el de `ODIN_PANEL_ORIGIN` y, con `localhost`, la entrada da 403.

Desde el panel se crean las empresas, los trabajadores y sus claves personales, se suben documentos y se revisan las fichas.

## Marca

El panel toma el nombre, el logo y los colores de la carpeta que sirve en `/brand/`. Por defecto es `panel/public/brand/` (E2D). Para usar otra, pon su ruta en `ODIN_BRAND_DIR` en `deploy/.env`; una ruta relativa se resuelve desde `deploy/`.

La carpeta lleva un `brand.json` y, si quieres logo, su fichero (PNG, WebP o SVG):

- `name` y `logoAlt`: textos. El nombre aparece como «Odín de <name>» en la entrada.
- `logo`: nombre del fichero del logo, en la misma carpeta, con la extensión en minúsculas (`.png`, `.webp` o `.svg`); el servidor no sirve otra cosa desde `/brand/`. Se ve siempre sobre el color `base`, así que debe verse bien sobre él.
- `colors.accent` y `colors.base`: hexadecimales de 6 cifras. `base` es el color oscuro de la barra lateral y de la portada.
- `cover`: opcional (`title`, `subtitle` y `note`). Sin él, la instalación no tiene portada de demostración. Con él, la portada se abre en `/#cover`.

Si `brand.json` falta o no es válido, el panel arranca con un tema neutro y avisa en la consola. El cambio de marca se ve al recargar la página.

## Quién entra dónde

- Los administradores entran en el panel con su correo y su contraseña.
- Los trabajadores no entran en el panel. Usan Odín desde su IA, por MCP, con su clave personal (`odin_...`), que un administrador les da desde el panel.

## MCP por stdio (IA de escritorio)

Compila el servidor:

```sh
cd mcp && npm ci && npm run build
```

Ejemplo de configuración para Claude Desktop (`claude_desktop_config.json`), con la clave personal que el panel da al trabajador:

```json
{
  "mcpServers": {
    "odin": {
      "command": "node",
      "args": ["/ruta/a/odin/mcp/dist/stdio.js"],
      "env": {
        "ODIN_API_URL": "http://127.0.0.1:8080",
        "ODIN_ACCESS_KEY": "odin_..."
      }
    }
  }
}
```

Registrarlo en un equipo es configuración fuera del repositorio: hazlo solo con permiso del propietario de ese equipo.

## MCP por HTTP

`make up` también arranca el MCP por Streamable HTTP en `http://127.0.0.1:8090/mcp`. Cada petición lleva la clave personal en `Authorization: Bearer odin_...`; una sesión abierta con una clave no acepta otra.

## Claves de la base de datos

Las claves de PostgreSQL se fijan en el primer arranque, cuando se crea el volumen de datos. Editar `deploy/.env` después no las cambia: PostgreSQL sigue aceptando las antiguas y la API no arranca. El error dice «PostgreSQL rechaza la clave del usuario de la base de datos.» y explica qué hacer.

Hay dos usuarios de base de datos: el propietario, cuyo nombre es `ODIN_DB_OWNER_USER` en `deploy/.env`, y el de la aplicación, que es siempre `odin_app` (clave `ODIN_DB_APP_PASSWORD`, que solo admite letras, dígitos, `-` y `_`, como dice `deploy/.env.example`). La base se llama `ODIN_DB_NAME` (por defecto, `odin`).

Para cambiar una clave sin perder datos, en este orden:

1. Cambia la clave en PostgreSQL con la instalación en marcha. Conéctate siempre como el propietario, tanto para `odin_app` como para el propio propietario:

   ```sh
   docker compose -f deploy/compose.yaml --env-file deploy/.env exec postgres psql -U <usuario> -d <base> -c "ALTER ROLE <rol> PASSWORD '<clave nueva>'"
   ```

2. Pon la misma clave nueva en `deploy/.env`.
3. Reinicia la instalación con `make down` y `make up`.

Borra los volúmenes (`make down; docker volume rm odin_pgdata odin_originals`) solo en una instalación nueva sin datos: se pierden todos los documentos y las fichas.

## Contraseña olvidada

Si olvidas la contraseña del administrador del grupo, restablécela con el token de instalación de `deploy/.env`. Quien tiene ese fichero ya controla la base de datos, así que esta orden no abre nada nuevo.

```sh
set -a; . deploy/.env; set +a
curl -X POST http://127.0.0.1:8080/v1/setup/admin-password -H 'Content-Type: application/json' -d "{
  \"setupToken\": \"$ODIN_SETUP_TOKEN\",
  \"newPassword\": \"una-clave-nueva-de-12-o-mas\"
}"
```

Responde 204: la contraseña queda cambiada, el administrador vuelve a estar activo y sus sesiones del panel se cierran. Si el token no vale responde 403; si la instalación aún no está configurada, 409. Tiene el mismo límite de intentos que el login.

## Sesión del panel

Hoy el panel usa un token aleatorio y opaco, en una cookie `HttpOnly` con `SameSite=Strict`. En la base solo se guarda su huella, nunca el token. La sesión caduca por inactividad y se revoca al cerrarla o al cambiar la contraseña.

Esa lógica está detrás del puerto `AdminSessions` (`api/odin-application/src/main/java/com/e2d/odin/application/port/AdminSessions.java`), implementado por `StoredAdminSessions`. Para pasar a JWT bastará con escribir otra clase que implemente `AdminSessions` y cambiar una línea en `UseCaseConfiguration`. `closeAllOf` exigirá entonces una versión de sesión por usuario, para seguir cerrando todas las sesiones de una persona sin consultar cada token.

## Fuera de la fase 1

- El lector OCR: los PDF escaneados y las imágenes quedan en «Pendiente de lector» hasta la fase 2.
- Lo demás de la fase 2: etiquetado por tema, casos parecidos, permisos por proyecto, compartir entre empresas del grupo, estadísticas descriptivas en el panel y motor de IA propio.
- Fuera de alcance: acceso desde internet, TLS y SSO.

## Datos

Todos los datos de la demostración y de las pruebas son ficticios. No se versionan documentos reales de clientes ni del propietario.
