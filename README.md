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
| `make demo` | Demostración de cierre de principio a fin, en una pila desechable aparte |

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

## Fuera de la fase 1

- El lector OCR: los PDF escaneados y las imágenes quedan en «Pendiente de lector» hasta la fase 2.
- Lo demás de la fase 2: etiquetado por tema, casos parecidos, permisos por proyecto, compartir entre empresas del grupo, estadísticas descriptivas en el panel y motor de IA propio.
- Fuera de alcance: acceso desde internet, TLS y SSO.

## Datos

Todos los datos de la demostración y de las pruebas son ficticios. No se versionan documentos reales de clientes ni del propietario.
