# Odín

Odín es el cerebro digital de una empresa (Business Digital Mind). Centraliza los documentos y las fichas de conocimiento de cada empresa de un grupo, y responde a la IA del trabajador con fragmentos que citan su fuente: documento, página y texto literal. Si no hay fuente, dice «no consta». Cada empresa está aislada de las demás.

Piezas:
- `api/`: API central en Java 21, hexagonal, sobre PostgreSQL.
- `mcp/`: servidor MCP en TypeScript, por stdio (IA de escritorio) y por HTTP local.
- `panel/`: panel de administración en React.
- `deploy/`: Docker Compose con PostgreSQL, la API, el panel y el MCP por HTTP.

Diseño: `docs/superpowers/specs/2026-11-09-api-central-design.md`.

## Repositorio

- Privado en GitHub (`albercv/odin`). Solo entra quien invite el propietario.
- Ramas (gitflow, D-114):
  - `feature/<nombre>` sale de `develop`, y cada tarea va en `task/<feature>/<tarea>`, que se mezcla con `--no-ff` en su feature.
  - Antes de pedir el visto bueno, la feature se pone al día con `develop` y, si trae migración, se ensaya la actualización sobre una copia de la base.
  - Con el visto bueno del propietario, la feature entra en `develop` con `--no-ff` y la instalación del propietario se actualiza desde `develop`.
  - `main` recibe los cierres de sprint por una rama `release/<versión>` y lleva su etiqueta. Las instalaciones de clientes salen solo de `main`. Un arreglo urgente sale de `main` en `hotfix/<nombre>` y vuelve a `main` y a `develop`.
  - No hay ramas de integración aparte: las `integracion/…` quedan retiradas.
- Se suben `develop`, `main` y sus etiquetas cada vez que cambian. Lo que está en construcción vive en sus ramas `feature/…` y no se sube.
- Nunca se suben `deploy/.env`, `data/` ni documentos reales: los excluye `.gitignore`, y antes de cada subida se revisa el historial en busca de claves.

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
| `make answer-battery MODELO=haiku\|sonnet [MUESTRA=N]` | Mide si Odín da datos correctos con el archivo ficticio: levanta una pila desechable (`odin-medicion`), carga, deja responder a `claude -p`, puntúa en `experimentos/2026-11-12-archivo-ficticio/resultados/` y baja la pila. Gasta cuota de Claude; no entra en `make check` |
| `make instalacion-reiniciar`, `make datos-prueba-cargar`, `make mcp-claude-desktop` y `make instalacion-restaurar` | Vaciar la instalación con copia, cargar datos de prueba, registrar el conector de Claude Desktop y volver a una copia: apartado «Datos de prueba y conector» |
| `make primera-carga CARPETA=/ruta [ARGS="--si"]` | Primera carga del archivo de una empresa: la lanza su administrador en el servidor de la oficina, sube la carpeta por la API y deja la revisión en el panel, en «Primera carga». Paso a paso en la [guía de la primera carga](docs/guias/primera-carga.md) |

El lector de prueba (`reader-stub`) solo sirve para las pruebas automáticas de `make demo`. Una demostración ante una persona usa siempre el puente real con Unlimited-OCR 2.

El etiquetador de prueba (`tagger-stub`) solo sirve para las pruebas automáticas de `make demo`. Una demostración ante una persona usa siempre el modelo real.

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

### Dirección de Odín en la red (`ODIN_PUBLIC_URL`)

En una instalación en un cliente, `ODIN_PUBLIC_URL` debe ser la dirección del servidor en la red local del cliente (por ejemplo `http://servidor-odin.local:8088` o su IP de la red), con el puerto del panel publicado en esa interfaz y `ODIN_PANEL_ORIGIN` con el mismo valor. Si no, los enlaces de subida y del original solo funcionan en el propio servidor y ningún trabajador puede abrirlos desde su ordenador. `http://127.0.0.1:8088` solo vale para una instalación en un único equipo, como el Mac del propietario. `ODIN_TRUSTED_PROXIES` va fijo como `panel` en `deploy/compose.yaml`: no se toca salvo que haya otro proxy delante.

Desde el panel se crean las empresas, las personas con sus pertenencias y claves personales, los proyectos, se suben documentos y se revisan las fichas.

## Marca

El panel toma el nombre, el logo y los colores de la carpeta que sirve en `/brand/`. Por defecto es `panel/public/brand/` (E2D). Para usar otra, pon su ruta en `ODIN_BRAND_DIR` en `deploy/.env`; una ruta relativa se resuelve desde `deploy/`.

La carpeta lleva un `brand.json` y, si quieres logo, su fichero (PNG, WebP o SVG):

- `name` y `logoAlt`: textos. El nombre aparece como «Odín de <name>» en la entrada.
- `logo`: nombre del fichero del logo, en la misma carpeta, con la extensión en minúsculas (`.png`, `.webp` o `.svg`); el servidor no sirve otra cosa desde `/brand/`. Se ve siempre sobre el color `base`, así que debe verse bien sobre él.
- `colors.accent` y `colors.base`: hexadecimales de 6 cifras. `base` es el color oscuro de la barra lateral y de la portada.
- `cover`: opcional (`title`, `subtitle` y `note`). Sin él, la instalación no tiene portada de demostración. Con él, la portada se abre en `/#cover`.

Si `brand.json` falta o no es válido, el panel arranca con un tema neutro y avisa en la consola. El cambio de marca se ve al recargar la página.

## Quién entra dónde

- Una persona tiene un nombre, unos apellidos y un código de persona al azar. En cada empresa donde está tiene una pertenencia, con su papel o sus dos papeles: administra la empresa, trabaja con su IA, o ambos. En el panel se ve como `<código de persona>-<código de la empresa>`.
- Quien administra una empresa entra en el panel con su correo y su contraseña. Si administra varias, elige empresa en el selector de la barra lateral.
- Quien trabaja con su IA usa Odín por MCP con su clave personal (`odin_...`), que un administrador le da desde el panel. Cada clave vale solo para la pertenencia que la recibió y da acceso a lo mismo que esa persona ve en esa empresa.
- Los proyectos nacen cerrados: solo los ven sus miembros. Para que lo vea toda la empresa hay que marcar «Visible para toda la empresa». Un trabajador no encuentra, lista ni lee nada de un proyecto cerrado al que no está asignado.
- Quitar a una persona de una empresa quita sus pertenencias a proyectos de esa empresa y deja de valer sus claves de ella.

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

## Datos de prueba y conector

Para probar Odín desde Claude Desktop con datos ficticios. Las órdenes actúan sobre la instalación de la copia principal del repositorio (su `deploy/.env`, la de `make up`), aunque se lancen desde una worktree. Sus copias, credenciales y cerrojo van al `data/` de esa copia principal. Llevan cerrojo (`data/instalacion.lock`): nunca van dos a la vez.

| Orden | Qué hace |
|---|---|
| `make instalacion-reiniciar CONFIRMAR=BORRAR` | Copia la base y los originales en `data/copias/`, borra todos los datos y deja la instalación lista para el primer arranque. Dice qué había y en qué copia queda. Sin `CONFIRMAR=BORRAR` no hace nada: explica qué haría |
| `make datos-prueba-cargar` | Hace el primer arranque y carga los datos de prueba por la API. Solo en una instalación vacía: si ya tiene datos, para sin tocarlos. Si falla a medias, la vuelve a dejar vacía |
| `make mcp-claude-desktop [TRABAJADOR=marta\|javier\|ignacio]` | Compila `mcp/` de la copia desde la que se lanza y lo copia en `data/mcp-claude-desktop/`. Añade o actualiza solo la entrada `odin` de `~/Library/Application Support/Claude/claude_desktop_config.json`, con copia previa en `data/copias/`, apuntando a esa copia del MCP: recompilar o cambiar de rama no cambia el MCP de Claude Desktop. Usa la clave de ese trabajador sin mostrarla (por defecto, `marta`). Después hay que reiniciar Claude Desktop |
| `make instalacion-restaurar COPIA=data/copias/odin-….sql CONFIRMAR=BORRAR` | Vuelve a una copia, con sus originales. Antes copia lo que hay |
| `make instalacion-actualizar CONFIRMAR=ACTUALIZAR` | Actualiza solo el código de la instalación a `develop` y **conserva la base y los originales**. Antes copia la base, los originales, `deploy/.env` y la configuración de Claude Desktop. Reintenta los documentos fallidos y renueva el MCP de Claude Desktop sin cambiar su trabajador. Si algo falla, vuelve a la copia. Es la orden para actualizar una instalación en uso |
| `make instalacion-anadir-proyecto DATOS_PROYECTO=<carpeta> CONFIRMAR=ANADIR` | Añade un proyecto nuevo, con sus documentos, sustituciones, fichas y expedientes, a una instalación en uso, **sin vaciarla ni tocar lo que ya tiene**. La carpeta tiene el formato del archivo ficticio (`manifiesto.json` con un solo proyecto de una empresa que ya existe, `corpus/` y, si hay, `expedientes.json` y `expedientes/`). Lo ven los trabajadores de prueba de `MIEMBROS` (por defecto `marta`), que escriben sus fichas. Antes copia la base y los originales, comprueba el aislamiento por la API y saca el recuento; si algo falla, vuelve a la copia. Sin `CONFIRMAR` solo revisa los datos y explica qué haría |
| `make instalacion-desplegar CONFIRMAR=DESPLEGAR [TRABAJADOR=marta]` | Como la anterior, pero **vacía la instalación** y carga el archivo ficticio completo. Borra lo que se haya hecho en ella (queda en la copia): solo con autorización expresa del propietario en cada ocasión |

**Protección de la instalación del propietario (D-099):** una pila de ensayo (`INSTALACION_PROYECTO` distinto de `odin`) para antes de nada si su entorno usa los puertos 8080, 8088 o 8090. En la instalación del propietario, lo que escribe y no tiene confirmación propia pide `CONFIRMAR_INSTALACION=ESCRIBIR`, con su permiso; por ejemplo, `make datos-prueba-cargar` o `make primera-carga` con `ARGS="--si"` (sin `--si`, su pregunta «¿Seguir? (s/n)» es la confirmación). Las órdenes que ya piden `CONFIRMAR` (desplegar, actualizar, añadir proyecto) la ponen solas.

**Carga completa:** `make datos-prueba-cargar ARCHIVO=completo` carga todo el archivo ficticio: 2 empresas, 94 proyectos, 1.236 documentos, 300 fichas con su estado, las parejas de sustitución, los 8 trabajadores con sus proyectos y un administrador de Ribera con contraseña (en el panel solo ve su empresa). 30 documentos de Ribera (semilla 13) se suben como PDF escaneado, fabricado con ImageMagick en `data/datos-prueba/escaneos/` y reutilizado después; sin lector OCR esperan, y sin `magick` se cargan los `.txt` con un aviso. Deja también el mapa ruta → `documentId` en `data/datos-prueba/mapa-ids.local.json` (600). Sin `ARCHIVO`, la carga es la pequeña de siempre.

**Recuento:** `make datos-prueba-recuento` cuenta en la base (SQL con el dueño) y escribe tablas Markdown: empresas, proyectos, personas por papel, proyectos que ve cada trabajador, documentos por formato y estado, páginas por lector, fichas, casos, sustituidos, temas y, si existe `case_file_entry`, expedientes. Las contradicciones sembradas las cuenta de `claves.json`: no están en la base.

Orden habitual: reiniciar, cargar y registrar. Para probar el aislamiento, vuelve a registrar con otro trabajador y reinicia Claude Desktop.

**Qué se carga** (sale del archivo ficticio de `experimentos/2026-11-12-archivo-ficticio/`):

- 2 empresas: Administraciones Ribera (`RIB`) y Gestión Albar (`ALB`).
- 10 comunidades, 8 de Ribera y 2 de Albar, todas cerradas. Además, en Ribera, el proyecto abierto «Procedimientos generales».
- Unos 160 documentos: casos con su resolución y resultado, actas, pólizas vieja y nueva con la marca de sustituida, anexos, contratos, presupuestos, avisos y normas sin proyecto.
  - Los `.txt` se guardan como una sola página.
  - Hay un escaneo para ver el lector OCR: sin lector, se queda esperando.
- La lista de temas de fincas en cada empresa.
  - Sin etiquetador, los casos llevan temas puestos por el administrador de prueba según su tipo.
  - Con etiquetador, la IA añade los suyos.
- 37 fichas: aprobadas, pendientes y rechazadas.
- Personas:
  - el administrador del grupo, que entra en el panel;
  - Marta Olivares Ríos (`marta`, Ribera, todas sus comunidades);
  - Javier Bermúdez Lara (`javier`, Ribera, solo Calle Pino 26 y Calle Roble 46);
  - Ignacio Salas Ferrer (`ignacio`, Albar).
  - Cada trabajador tiene su clave.
- Las credenciales van a `data/datos-prueba/credenciales.local.md` (y `.json`), con permisos 600. Nunca salen en pantalla ni van a git.

**Preguntas de ejemplo** para Claude Desktop con el conector:

| # | Quién | Pregunta | Qué debe salir |
|---|---|---|---|
| 1 | Marta | ¿De cuánto fue la derrama para la fachada de Calle Olmo 14 y en cuántas cuotas se paga? | 48.000 euros en 12 cuotas mensuales, con la cita del acta extraordinaria de 2025 |
| 2 | Marta | ¿Qué franquicia por daños por agua tiene ahora mismo la póliza de Los Almendros? | 150 euros, de la póliza de 2024. La de 2022 (200 euros) sale como sustituida |
| 3 | Marta | Una vecina de Calle Olmo 14 tiene moho negro en la pared del dormitorio. ¿Consta algún caso parecido y qué concluyó el técnico? | El caso 2025-027: condensaciones por ventilación insuficiente |
| 4 | Marta | ¿Qué casos de agua y humedades constan en Calle Olmo 14? | Lista por tema: los casos 2024-017, 2025-014 y 2025-027 |
| 5 | Marta | ¿De qué color se va a pintar el portal de Calle Abeto 16? | Gris perla. Sale de una ficha aprobada de un trabajador, no de un documento |
| 6 | Marta | ¿Qué color le van a dar al portal de Residencial Los Naranjos 2? | No consta: la única ficha con ese dato está rechazada |
| 7 | Marta | ¿Cubre la póliza de Los Almendros los daños por terremoto? | No consta |
| 8 | Javier | ¿Qué franquicia tiene la póliza ATL-4417-2024 para daños por agua? | No consta: es de Los Almendros y Javier no es miembro. Marta, en cambio, recibe 150 euros |
| 9 | Javier | ¿Cuántas referencias exigimos para dar de alta a un proveedor? | Dos referencias. Está en «Procedimientos generales», que ve toda Ribera |
| 10 | Ignacio | ¿Dónde se guardan las llaves de los cuartos técnicos y de las azoteas en la oficina de Ribera? | No consta: es una norma de Ribera e Ignacio es de Albar. Marta: en la caja fuerte de la oficina |

Son datos ficticios: la IA puede fallar alguna. Lo que se mide está en `make answer-battery`.

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

Es la recuperación de emergencia. Si ningún administrador del grupo puede entrar, restablece la contraseña de uno con el token de instalación de `deploy/.env`. Quien tiene ese fichero ya controla la base de datos, así que esta orden no abre nada nuevo.

```sh
set -a; . deploy/.env; set +a
curl -X POST http://127.0.0.1:8080/v1/setup/admin-password -H 'Content-Type: application/json' -d "{
  \"setupToken\": \"$ODIN_SETUP_TOKEN\",
  \"email\": \"correo-del-administrador@ejemplo.com\",
  \"newPassword\": \"una-clave-nueva-de-12-o-mas\"
}"
```

Responde 204: la contraseña queda cambiada, el administrador vuelve a estar activo y sus sesiones del panel se cierran. El `email` es obligatorio si hay más de un administrador del grupo; con uno solo puedes omitirlo. Si el correo no es de un administrador del grupo responde 404. Si el token no vale responde 403; si la instalación aún no está configurada, 409. Tiene el mismo límite de intentos que el login.

## Cómo cambiar al administrador del grupo

Una instalación puede tener varios administradores del grupo, todos con la misma autoridad. El relevo se hace desde el panel, sin renombrar la cuenta: cada persona conserva su código y lo que escribió queda a su nombre (D-051). Lo hace un administrador del grupo, en «Administración del grupo» de la barra lateral:

1. **Añadir a la persona nueva.** Pulsa «Añadir administrador del grupo» e indica nombre, apellidos, correo y contraseña. Si ya existe en el grupo, solo se le da la marca: conserva su nombre y su código, y la contraseña solo se pide si no tiene.
2. **Desactivar a la anterior.** Entra la persona nueva, abre las acciones de la fila de la anterior y usa «Desactivar». Deja de entrar en el panel y sus claves dejan de valer en todo el grupo. Si prefieres que siga existiendo sin administrar, usa «Quitar administración»; si ya no debe figurar, «Borrar». Si se quita la marca a quien no tiene pertenencias, no aparece en ninguna empresa: para borrarlo, añádelo de nuevo como administrador del grupo por su correo y usa «Borrar».
3. **Revisar sus claves.** Si la anterior trabajaba con su IA, desactivarla ya invalida sus claves; si solo le quitas la marca, siguen valiendo hasta que las revoques en «Claves».

Siempre queda al menos un administrador del grupo activo: desactivar, quitar la marca o borrar al último da 409. Nadie se lo hace a sí mismo, y por eso su propia fila no tiene acciones.

El token de instalación queda solo para emergencias, cuando nadie puede entrar: ver «Contraseña olvidada», con el `email` del administrador.

## Sesión del panel

Hoy el panel usa un token aleatorio y opaco, en una cookie `HttpOnly` con `SameSite=Strict`. En la base solo se guarda su huella, nunca el token. La sesión caduca por inactividad y se revoca al cerrarla o al cambiar la contraseña.

Esa lógica está detrás del puerto `AdminSessions` (`api/odin-application/src/main/java/com/e2d/odin/application/port/AdminSessions.java`), implementado por `StoredAdminSessions`. Para pasar a JWT bastará con escribir otra clase que implemente `AdminSessions` y cambiar una línea en `UseCaseConfiguration`. `closeAllOf` exigirá entonces una versión de sesión por usuario, para seguir cerrando todas las sesiones de una persona sin consultar cada token.

## Lector OCR

Sin lector, los PDF escaneados y las imágenes no se leen: quedan «Esperando al lector OCR» y el panel dice «Esta instalación no lee documentos escaneados».

Para leerlos, el puente del lector tiene que estar en marcha en el Mac. Para que arranque solo al iniciar sesión y se levante si se cae: `make lector-arranque-instalar CONFIRMAR=ARRANQUE` (solo toca `~/Library/LaunchAgents`; `make lector-arranque-estado` lo comprueba y `make lector-arranque-quitar` lo deshace). El agente no arranca el puente directamente sino a través de un vigía (`deploy/instalacion/vigia_lector.py`), que lo reinicia si sigue vivo pero lleva 35 minutos seguidos sin responder a `/health`. Son 35 y no menos porque el puente atiende una petición cada vez y no responde mientras lee: desde fuera, una lectura larga y un cuelgue se ven igual, y 35 minutos es el máximo de lectura de Odín (30) más 5, cuando Odín ya ha abandonado esa lectura y parar el puente no pierde nada. Sin eso, se arranca a mano desde el repositorio OCR: `make serve`. En este equipo la URL del puente, vista desde Docker, va en `deploy/.env` (`ODIN_READER_URL`, y `ODIN_READER_TOKEN` si el puente lleva token). Al llegar el lector, los documentos que esperaban se leen solos. Un PDF con algunas páginas sin texto queda listo y el panel dice cuáles no se han leído.

## Etiquetado por tema

Cada empresa tiene su lista de temas (con una definición de cada uno) y un modelo local pone a cada documento leído los temas que le encajan. Lo hace el Ollama que ya está en el equipo, por defecto con `qwen3.5:9b` (con poca memoria, `qwen3.5:4b-q4_K_M`).

1. Descarga el modelo una vez: `make tagger-model MODEL=qwen3.5:9b` (única orden del etiquetado que usa la red).
2. Pon en `deploy/.env` `ODIN_TAGGER_URL=http://host.docker.internal:11434` (el Ollama del equipo visto desde Docker; no hay servicio de Ollama en Compose) y reinicia la API. Las demás variables están comentadas en `deploy/.env.example`.

El equipo tiene que estar encendido durante la ventana del etiquetado (por defecto de 22:00 a 07:00, hora de `ODIN_TIMEZONE`), o hay que cambiar `ODIN_TAGGER_WINDOW`. Sin eso los documentos se pueden buscar, pero no tienen temas.

## Casos parecidos

Un caso es un documento que el administrador marca como «Caso» (una ficha de incidencia resuelta, por ejemplo). Con la capacidad «Casos parecidos» activada en la empresa, la IA del trabajador tiene la herramienta `findSimilarCases`: le pasa los términos del problema y recibe los casos resueltos parecidos con su texto literal y sus páginas, sin nombres de autores. Solo ve casos de sus proyectos y de lo que no tiene proyecto.

- **Marcar:** en «Subir documento» se pueden elegir varios ficheros a la vez y marcar la casilla «Son fichas de caso». En la lista, «Marcar como caso» y «Quitar marca de caso» actúan sobre un documento o, con la casilla de cada fila, sobre los seleccionados (hasta 500).
- **Listar:** `listDocuments` acepta `caseDocument` para ver solo los casos, o solo lo que no lo es.

## Fuera de la fase 1

- Lo demás de la fase 2: compartir entre empresas del grupo, estadísticas descriptivas en el panel y motor de IA propio.
- Fuera de alcance: acceso desde internet, TLS y SSO.

## Datos

Todos los datos de la demostración y de las pruebas son ficticios. No se versionan documentos reales de clientes ni del propietario.
