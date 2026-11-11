# Odín · API central — diseño

- Fecha: 2026-11-09. Autor: CTO. Encargo 11 (D-033).
- Estado: borrador. El propietario pidió el 2026-11-09 que el CTO trabaje autónomo y lleve sus dudas al CEO, que las resuelve; las dudas y sus respuestas están en el apartado 13.
- Fuentes (en el repositorio OCR, `docs/business-digital-mind/`):
  - el encargo `encargos/2026-11-09-api-central.md`, que está en la rama `feature/respuestas-cto`;
  - `decisiones.md` y `registro-tecnico.md`;
  - `tecnico/2026-11-05-propuesta-tecnica.md` y `tecnico/2026-11-06-estudio-busqueda.md`.

## 1. Qué es y por qué

Odín es la API central del cerebro digital: es el núcleo y guarda las reglas. Todo lo demás habla con ella:
- la IA de cada trabajador, a través de un servidor MCP;
- el panel del administrador;
- el lector de documentos.

Se construye primero porque cada canal futuro se enchufa a ella.

Tiene que ser muy desacoplada por dos motivos:
- **Replicable:** la misma instalación tiene que servir en cualquier equipo del cliente, sea Mac, Linux, Windows o un servidor en su oficina o en la nube (D-013, D-032).
- **Intercambiable:** cada tecnología (base de datos, lector OCR, IA, búsqueda) tiene que poder cambiarse sin tocar el núcleo.

Lo que hoy solo funciona en el Mac (el lector OCR con MLX y Metal) queda fuera del repositorio, detrás de un contrato.

## 2. Decisiones del propietario (sesión del CTO, 2026-11-09)

| Tema | Decisión |
|---|---|
| Nombre y ubicación | Odín. Repositorio `/Users/albercv/Documents/personalProjects/odin`, uno solo, con carpetas independientes `api/`, `mcp/`, `panel/` y `deploy/` |
| Lenguaje de la API | Java |
| Instalaciones | Una por grupo, o por empresa sin grupo. Dos clientes distintos nunca comparten instalación |
| Empresas del grupo | Cada una con sus documentos, temas, trabajadores y permisos. Separadas por defecto; solo comparten si un administrador lo configura |
| Administradores | El del grupo gestiona todas las empresas; el de empresa, solo la suya. Un único panel, en el que cada uno ve lo que le toca |
| Acceso de los trabajadores | Un solo servidor MCP. Una clave por persona, que da y retira el administrador. La clave dice quién es y de qué empresa. Las herramientas que ve dependen de lo que tenga activado su empresa. El cerebro filtra cada respuesta, no la IA del trabajador |
| Permisos dentro de la empresa | Por proyecto o comunidad |
| Autoría | Queda registrado quién sube cada documento, cada ficha y cada pregunta. Nunca se usa para evaluar a personas (D-005) |
| Demostración de cierre de la fase 1 | Apartado 3.1 |
| Forma de trabajo | El CTO, autónomo; las dudas, al CEO. El desarrollo, con subagentes Sonnet y Haiku (T-003) |

Decisiones del CTO que el propietario vio y no discutió:
- TypeScript para el servidor MCP y para el panel;
- todo en Docker;
- en el Mac, solo el OCR fuera.

## 3. Alcance

### 3.1 Fase 1: la demostración de cierre

La fase 1 está terminada cuando esto funciona de principio a fin, con datos ficticios y verificado ejecutando:

1. El administrador del grupo entra en el panel.
2. Crea dos empresas, A y B, con un trabajador cada una, y da a cada trabajador su clave personal.
3. Sube un PDF ficticio a la empresa A.
4. Desde una IA con MCP, el trabajador de A pregunta y recibe fragmentos con la cita (documento, página y texto literal).
5. El trabajador de B hace la misma pregunta y le sale «no consta».
6. El trabajador de A guarda una ficha («guárdalo»), que aparece en el panel pendiente de revisar. El administrador la revisa.

Para eso, la fase 1 construye:
- el grupo, las empresas, los proyectos, los usuarios, las claves personales y las sesiones del panel;
- la ingestión de documentos digitales (PDF con texto, DOCX, TXT y MD), con el original guardado y su huella;
- la búsqueda con cita, la lectura de una página, el listado completo de documentos y la comprobación de que una cita es literal;
- las fichas de conocimiento y su revisión;
- el servidor MCP (stdio y HTTP local) con las herramientas que tenga activadas la empresa;
- el panel para todo lo anterior;
- el despliegue en Docker Compose y la batería de búsqueda del estudio como prueba de regresión.

### 3.2 Fase 2: el resto de la primera versión del encargo

- **Lector OCR:** PDF escaneados e imágenes, mediante el contrato del servicio lector (apartado 6.4). El puente del Mac al lector Unlimited-OCR 2 vive en el repositorio OCR.
- **Etiquetado por tema al ingresar** (D-031). Hoy es candidato (T-010).
- **Casos parecidos.**
- **Permisos por proyecto o comunidad dentro de la empresa.** La fase 1 ya guarda el proyecto en cada dato.
- **Panel:** temas de cada empresa, consumo de la IA externa, estadísticas descriptivas y configuración de la IA.
- **Compartir conocimiento entre empresas del grupo.** Cuándo y quién lo autoriza está pendiente de negocio (D-033).
- **Motor de IA propio para redactar respuestas,** con verificación de citas en el servidor.

### 3.3 Fuera de alcance

- ERP y actividad en programas, captura automática, avisos proactivos, borrador de resolución y conflictos entre documentos (encargo 11).
- IA local propia: el diseño la permite, pero no se construye.
- Varios clientes en una instalación.
- Acceso desde internet y conectores remotos (encargo 7), registro público de usuarios, inicio de sesión único (SSO) y TLS dentro de la instalación. Los puertos se publican solo en `127.0.0.1`.

### 3.4 Anti-objetivos: sería un fracaso aunque funcione

- Que un trabajador vea algo de otra empresa sin una regla de compartir.
- Que se devuelva un fragmento sin cita, o una cita cuyo texto no esté literal en su página.
- Que entre en el código común algo del Mac: rutas, MLX, `host.docker.internal`.
- Que haya reglas de negocio en el servidor MCP o en el panel.
- Que haya registros o estadísticas que puntúen, ordenen o comparen a personas.
- Que haya claves o contraseñas en claro en la base de datos, en los logs o en el repositorio.
- Que salga algún dato de la instalación hacia E2D.

## 4. Restricciones

- **Reglas no negociables:** las de `CLAUDE.md` de este repositorio (nunca inventa, aislamiento, E2D sin datos, conocimiento y no evaluación, datos ficticios, ninguna cifra sin medición).
- **Calidad (D-029):**
  - TDD;
  - arquitectura hexagonal comprobada con ArchUnit;
  - SOLID;
  - métodos de 30 líneas como máximo (Checkstyle en Java, ESLint en TypeScript);
  - nombres en inglés y en camelCase;
  - OpenAPI;
  - comentarios mínimos.
- **Docker desde el principio** (T-001).
- **PostgreSQL** (T-002). pgvector no se usa en la fase 1.
- **Nombres:** camelCase en los contratos (JSON y herramientas MCP) (T-005).
- **Búsqueda de la fase 1:** sin vectores ni reordenador en el cerebro. Palabras en español y filtros; la IA del trabajador reformula y lee (T-007; estudio de búsqueda, apartado 6).
- **Nada global:** dependencias solo en el proyecto o en sus contenedores. Registrar el conector en Claude Desktop requiere permiso del propietario.

## 5. Arquitectura

### 5.1 Vista

```
IA del trabajador (Claude Desktop u otra con MCP)
  └─ stdio ─▶ odin-mcp ──HTTP + clave personal──┐
IA que se conecta por URL ─HTTP─▶ odin-mcp ─────┤
Navegador del administrador ─▶ odin-panel ─HTTP + sesión─┤
                                                         ▼
                                              odin-api (Java 21)
                              núcleo: dominio + casos de uso (sin frameworks)
                              adaptadores: web (REST), PostgreSQL, ficheros,
                              lectores digitales; fase 2: servicio lector,
                              etiquetado, IA externa
                                    │                 │
                               PostgreSQL     volumen de originales
Fase 2: servicio lector (contrato HTTP). En el Mac, puente al lector OCR, fuera de este repositorio.
```

- **odin-api** es la única pieza con reglas.
- **odin-mcp** y **odin-panel** son clientes de la API: no guardan nada y no deciden nada.
- Si cambia el MCP, el panel o el lector, el núcleo no se entera.

### 5.2 Estructura del repositorio

```
odin/
  api/                        Maven multimódulo, Java 21
    odin-domain/              entidades, valores y reglas; solo java.*
    odin-application/         casos de uso y puertos; depende solo del dominio
    odin-adapter-postgres/    puertos de persistencia y búsqueda; Flyway; RLS
    odin-adapter-filestore/   originales en disco, con su huella SHA-256
    odin-adapter-readers/     PDFBox (PDF con texto), POI (DOCX), texto plano
    odin-adapter-web/         REST, autenticación, OpenAPI (springdoc)
    odin-bootstrap/           aplicación Spring Boot: configuración y cableado
  mcp/                        TypeScript, servidor MCP (stdio y Streamable HTTP)
  panel/                      TypeScript, React + Vite
  deploy/                     compose.yaml, Dockerfiles, .env.example
  docs/superpowers/           specs/ y plans/
  Makefile                    setup, test, check, up, down, demo
```

- Un módulo Maven por adaptador hace que las dependencias se comprueben al compilar. Además, permite que varios subagentes trabajen a la vez sin tocar los mismos ficheros.
- Spring solo aparece en `odin-adapter-web`, `odin-adapter-postgres` y `odin-bootstrap`. El dominio y los casos de uso son Java puro.
- Paquete base: `com.e2d.odin` (decisión del CTO).

### 5.3 Reglas de capas (ArchUnit)

- **`odin-domain`:** depende solo de `java.*`.
- **`odin-application`:** depende de `odin-domain` y de `java.*`.
- **Adaptadores:** dependen de `odin-application` y de `odin-domain`, nunca de otro adaptador.
- **`odin-bootstrap`:** es el único que conoce todos los módulos.
- **Fuera del dominio y de la aplicación:** ninguna clase de Spring, Jackson, JDBC ni Jakarta.

## 6. Núcleo

### 6.1 Modelo de dominio

| Concepto (español) | Nombre en código | Notas |
|---|---|---|
| Grupo | `Group` | Uno por instalación. Es el cliente |
| Empresa | `Company` | Pertenece al grupo. Tiene sus capacidades activadas (6.3) |
| Proyecto o comunidad | `Project` | Pertenece a una empresa. Es opcional en documentos y fichas |
| Usuario | `User` | Tiene un papel: `GROUP_ADMIN`, `COMPANY_ADMIN` o `WORKER`. Los papeles de empresa y de trabajador llevan su empresa; el administrador del grupo, ninguna. Se puede desactivar, pero no se borra (autoría) |
| Clave personal | `AccessKey` | Solo para trabajadores. Lleva una etiqueta, un prefijo visible y una huella; se puede revocar |
| Sesión del panel | `AdminSession` | Solo para administradores |
| Documento | `Document` | Tiene empresa, proyecto, título, nombre del fichero, tipo, SHA-256, estado, motivo del error, quién lo subió y cuándo |
| Página | `DocumentPage` | Tiene documento, número y texto literal, y qué lector lo produjo (nombre y versión) |
| Ficha de conocimiento | `KnowledgeCard` | Tiene empresa, proyecto, título y texto, autor, fecha y estado de revisión. Es siempre «generado por IA, aportado por su autor» |
| Revisión | `CardReview` | Decisión (aprobar, corregir o rechazar), quién revisó, cuándo y nota. Si se corrige, se guardan el texto original y el corregido |
| Pregunta registrada | `QuestionRecord` | Texto, autor, empresa, fecha y número de resultados. Sin ninguna puntuación de personas |
| Cita | `Citation` | Valor: documento o ficha, página si la hay, texto literal y título |

- **Estados del documento:**
  - `RECEIVED`;
  - `PROCESSING`;
  - `READY`;
  - `FAILED` (con su motivo);
  - `PENDING_READER`, cuando no hay lector para ese tipo, por ejemplo un escaneado en la fase 1. Se procesa cuando llegue el lector, sin perder nada.
- **Documentos sin páginas propias:** un DOCX, un TXT o un MD se guardan como una sola página número 1, con `paginated = false`. La cita dice «documento completo»; no se inventan números de página.
- **PDF sin texto en ninguna página:** pasa a `PENDING_READER`. Si tiene texto, queda `READY`, y las páginas en blanco se guardan vacías.
- **Mismo fichero dos veces en la misma empresa** (misma SHA-256): devuelve el documento que ya existe, con `duplicate: true`. Se ignoran el título y el proyecto nuevos. Si el que existía está en `FAILED`, vuelve a `RECEIVED` y se reintenta.
- **Proceso que se cae a medias:** cada documento guarda `processingStartedAt` y `attemptCount`.
  - El programador de tareas vuelve a tomar los documentos que llevan en `PROCESSING` más de 15 minutos (se puede configurar).
  - Al tercer intento pasa a `FAILED` con el motivo «demasiados intentos».
  - `RetryDocument` pone el contador a cero.

### 6.2 Quién hace qué

- **Actor:** cada petición llega con un `Actor`, que es el usuario, su papel y su empresa. Lo resuelve el adaptador web, a partir de la sesión del panel o de la clave personal.
- **Comprobación:** cada caso de uso recibe el `Actor` y comprueba el papel y la empresa. Es la garantía principal y no depende de la base de datos.
- **Administrador del grupo:** gestiona todas las empresas, sus administradores, sus trabajadores y las capacidades de cada empresa.
- **Administrador de empresa:** gestiona solo la suya: proyectos, trabajadores, claves, documentos y revisiones. No cambia capacidades (decisión del CTO).
- **Trabajador:** solo usa el MCP, en su empresa y con las capacidades activadas en ella.
- **Proyectos en la fase 1:** un trabajador o un administrador de empresa ve todos los proyectos de su empresa. Todo `projectId` que llegue se comprueba contra la empresa del actor: si no es suyo, 404. Los permisos por proyecto llegan en la fase 2, sobre el `projectId` que ya se guarda.
- **Separación de papeles:** los administradores no tienen claves MCP. Si un administrador trabaja con su IA, se le da también un usuario de trabajador (decisión del CTO: separa los papeles y simplifica los permisos).

### 6.3 Capacidades de cada empresa

Son un conjunto por empresa. Por defecto, todas las de la fase 1 están activadas.

| Capacidad | Herramientas MCP y llamadas de la API |
|---|---|
| `SEARCH_KNOWLEDGE` | `searchKnowledge`, `readPage`, `listDocuments`, `verifyCitation` |
| `SAVE_KNOWLEDGE_CARD` | `saveKnowledgeCard` |

- La fase 2 añade otras, como `SIMILAR_CASES`.
- La API rechaza con 403 cualquier llamada de una capacidad desactivada. El MCP, además, ni la muestra.

### 6.4 Puertos de salida

| Puerto | Fase 1 | Para qué |
|---|---|---|
| `OrganizationRepository` | PostgreSQL | Grupo, empresas, capacidades y proyectos |
| `UserRepository`, `AccessKeyRepository`, `AdminSessionRepository` | PostgreSQL | Identidad y acceso |
| `DocumentRepository` | PostgreSQL | Documentos y páginas; tomar el siguiente pendiente |
| `KnowledgeCardRepository` | PostgreSQL | Fichas y revisiones |
| `QuestionLog` | PostgreSQL | Registro de preguntas |
| `KnowledgeSearch` | PostgreSQL, texto completo | Buscar en páginas y fichas aprobadas |
| `OriginalFileStore` | Disco (volumen) | Guardar y leer originales por huella |
| `DocumentTextReader` | PDFBox, POI, texto plano | Pasa de bytes y tipo a páginas. Hay varios a la vez, elegidos por tipo. El OCR de la fase 2 es otro adaptador de este mismo puerto, que llama al servicio lector |
| `SecretHasher`, `PasswordHasher`, `Clock`, `IdGenerator` | Estándar | Se pueden sustituir en las pruebas |

- Todos los métodos de los repositorios que tocan datos de una empresa reciben la empresa (`CompanyId`) como parámetro obligatorio. No existe ninguno que lea sin empresa.
- La búsqueda recibe además el conjunto de empresas legibles (`ReadableCompanies`), que en la fase 1 es solo la del trabajador. Por ahí entrará compartir entre empresas en la fase 2.

**Contrato del servicio lector**, que se escribe en la fase 2 pero ya se fija aquí para que el Mac no condicione nada:
- `POST /read`, con el fichero en multipart;
- devuelve `{ pages: [{ number, text }], reader: { name, version } }`, o un error con su motivo;
- quien quiera ofrecer OCR (el puente del Mac, un servidor Linux en el futuro) implementa este contrato;
- la URL del servicio va en la configuración de cada instalación.

### 6.5 Casos de uso (puertos de entrada), fase 1

**Instalación y administración:**
- `SetUpInstallation`: crea el grupo y el primer administrador del grupo. Exige el `ODIN_SETUP_TOKEN` de la configuración y solo funciona una vez.
- `LogInAdmin` y `LogOutAdmin`.
- `CreateCompany`, `RenameCompany` y `SetCompanyCapabilities`.
- `CreateProject`.
- `RegisterUser` y `DeactivateUser`.
- `IssueAccessKey`, que devuelve el secreto una sola vez, y `RevokeAccessKey`.
- `UploadDocument`, `ListCompanyDocuments` y `RetryDocument`.
- `ListCardsForReview` y `ReviewCard`.

**Trabajador:**
- `DescribeMe`: quién soy, mi empresa, mis capacidades y los proyectos.
- `SearchKnowledge`, `ReadPage`, `ListDocuments`, `VerifyCitation` y `SaveKnowledgeCard`.

**Interno:**
- `ProcessPendingDocuments`: lo lanza un programador de tareas del adaptador. Toma un documento con `FOR UPDATE SKIP LOCKED`, lo lee, guarda sus páginas y cambia su estado.

### 6.6 Búsqueda con cita (fase 1)

Reproduce la variante medida en el estudio (script `pruebas-busqueda/scripts/fts.py`, en el repositorio OCR):
- **Configuración de texto:** una propia, copia de `spanish` con `unaccent` y `spanish_stem` para palabras y compuestos.
- **Índice:** columna `tsvector` generada sobre el texto de cada página y de cada ficha, con índice GIN. `pg_trgm` para el canal exacto.
- **Consulta:** cada término o frase que manda la IA se pasa por `plainto_tsquery` y se cambia el `&` por `|`. Si llegan varias reformulaciones, se unen con `||`.
- **Orden:** por `ts_rank_cd`.
- **Montaje en PostgreSQL:**
  - `spanish_stem` es el diccionario Snowball que ya trae PostgreSQL.
  - `unaccent` y `pg_trgm` son extensiones de confianza de la imagen oficial `postgres:17`. Las crea el rol de migraciones, dueño de la base, sin superusuario.
  - No hace falta una imagen propia.
- **Canal exacto:** los términos con cifras o identificadores, como `2024/113` o `B-12345678`, se buscan con `ILIKE` sobre el texto literal y suben primero en el orden.
- **Filtros:** la empresa (obligatoria, sale del `Actor`) y el proyecto (opcional). Solo entran las fichas `APPROVED`; las `PENDING` y las `REJECTED` no aparecen (D-036, D-037).
- **Resultado:** hasta `limit` resultados, 10 por defecto y 50 como máximo. Cada uno lleva:
  - el tipo de fuente (`ORIGINAL_DOCUMENT` o `KNOWLEDGE_CARD`) y su identificador;
  - título, página y si es paginado;
  - proyecto, autor y fecha;
  - un extracto **literal**, con `ts_headline` sin marcas o con un recorte del texto alrededor de la coincidencia.
- **Sin resultados:** `{ hits: [], noSource: true }`. El MCP lo traduce a «No consta en los documentos de tu empresa».
- **Registro:** cada búsqueda guarda su `QuestionRecord`.

### 6.7 Verificación de citas

`VerifyCitation(documentId, pageNumber, quote)` normaliza espacios y mayúsculas y dice si el texto está literal en esa página. Solo mira páginas de las empresas que el actor puede leer.

El MCP la ofrece para que la IA compruebe sus citas antes de responder. El cerebro no puede obligar a la IA del trabajador a usarla; en la fase 2, el motor propio la aplicará siempre.

### 6.8 Fichas de conocimiento

- **Al guardar** con `SaveKnowledgeCard(title, body, projectId?)`, la ficha queda `PENDING`, con su autor y su origen marcado: «ficha aportada por [trabajador], resumen hecho por IA».
- **La revisión** la hace un administrador de esa empresa o el del grupo:
  - `APPROVE`;
  - `CORRECT`: se guardan el texto original y el corregido, y la ficha queda aprobada con la marca «corregida por [administrador]»;
  - `REJECT`, con una nota.
- **La revisión es del contenido, nunca de la persona** (D-005). No hay contadores de fichas rechazadas por persona.

## 7. Seguridad y aislamiento

- **Claves personales:**
  - Formato `odin_<prefijo de 8>_<secreto>`, con un secreto de 32 bytes aleatorios en base64url.
  - Se guardan el prefijo y el SHA-256 del secreto: con ese nivel de azar, un hash rápido basta.
  - Se muestran una sola vez y se pueden revocar.
  - Un trabajador puede tener varias, cada una con su etiqueta, por ejemplo el portátil o el móvil.
  - Se usan como cabecera `Authorization: Bearer`.
  - No se guarda la última vez que se usó cada clave: sería un registro de actividad por persona que no hace falta (D-005).
- **Administradores:**
  - Entran con correo y contraseña: bcrypt de coste 12 (Spring Security Crypto) y 12 caracteres como mínimo.
  - La sesión es opaca: se guarda su huella en la base, con cookie `HttpOnly` y `SameSite=Strict`, y caduca a las 8 horas sin actividad.
  - En las peticiones que cambian algo se comprueba que la cabecera `Origin` sea la del panel configurado.
  - Se limitan los intentos de entrada a 5 por minuto por correo y por IP.
- **Primer arranque:** `ODIN_SETUP_TOKEN` en el `.env`. Después de usarse, la API rechaza `setup`.
- **Base de datos, segunda barrera** (detalle en la lista que sigue a esta):
  - Las tablas de empresa tienen RLS: `company_id = current_setting('app.company_id')` o `current_setting('app.group_scope') = 'on'`.
  - El adaptador fija las dos variables con `SET LOCAL` en cada transacción, a partir del `Actor`.
  - La aplicación se conecta con un rol sin `BYPASSRLS` y sin ser dueño de las tablas. Las migraciones usan otro rol.
  - Si las variables no están fijadas, la política no deja ver nada.
  - **Tablas con RLS:** `company` (por su `id`), `project`, `document`, `document_page`, `knowledge_card`, `card_review` y `question_record`.
  - **Tablas de identidad sin RLS:** `group`, `app_user`, `access_key` y `admin_session`. Solo las leen los repositorios de identidad, y los casos de uso filtran por empresa.
  - **Contexto de cada transacción:**
    - trabajador o administrador de empresa: `app.company_id`;
    - administrador del grupo: `app.group_scope = 'on'`;
    - tarea interna `ProcessPendingDocuments`: `app.group_scope = 'on'`, porque no tiene actor;
    - `SetUpInstallation`, `LogInAdmin` y la resolución de una clave o de una sesión: solo tocan tablas de identidad, así que no necesitan contexto.
- **Pruebas de aislamiento, obligatorias:** un trabajador de B no ve nada de A, ni al buscar, ni al leer una página, ni al listar, ni al verificar, ni con identificadores de A puestos a mano. Se prueba en tres niveles:
  - casos de uso, con dobles en memoria;
  - PostgreSQL, con RLS y Testcontainers;
  - de principio a fin, con Compose.
- **Lo que nunca va a los logs:** secretos, contraseñas, cookies ni el texto de los documentos.

## 8. API REST

- **Formato:** base `/v1`, JSON en camelCase, errores RFC 9457 (`application/problem+json`).
- **Documentación:** OpenAPI generado por springdoc, que se exporta al construir a `api/openapi.json` y se versiona. El MCP y el panel generan sus tipos desde ese fichero con `openapi-typescript`, y una prueba falla si el fichero versionado no coincide con el generado.
- **Llamadas:**

| Método y ruta | Quién | Caso de uso |
|---|---|---|
| `POST /v1/setup` | Token de instalación | `SetUpInstallation` |
| `POST /v1/admin/session` · `DELETE /v1/admin/session` · `GET /v1/admin/me` | Administrador | Sesión |
| `GET, POST /v1/admin/companies` · `PATCH /v1/admin/companies/{companyId}` | Administrador del grupo (`GET`: cada uno ve las suyas) | Empresas |
| `PUT /v1/admin/companies/{companyId}/capabilities` | Administrador del grupo | Capacidades |
| `GET, POST /v1/admin/companies/{companyId}/projects` | Administradores | Proyectos |
| `GET, POST /v1/admin/companies/{companyId}/users` · `PATCH /v1/admin/users/{userId}` | Administradores (solo el del grupo crea `COMPANY_ADMIN`) | Usuarios |
| `GET, POST /v1/admin/users/{userId}/keys` · `DELETE /v1/admin/keys/{keyId}` | Administradores | Claves |
| `GET, POST /v1/admin/companies/{companyId}/documents` · `GET /v1/admin/documents/{documentId}` · `POST /v1/admin/documents/{documentId}/retry` | Administradores | Documentos |
| `GET /v1/admin/companies/{companyId}/cards?status=PENDING` · `POST /v1/admin/cards/{cardId}/review` | Administradores | Revisión |
| `GET /v1/me` | Trabajador | `DescribeMe` |
| `POST /v1/search` | Trabajador | `SearchKnowledge` |
| `GET /v1/documents?projectId=&offset=&limit=` | Trabajador | `ListDocuments`: orden por título y después por identificador; 100 por página por defecto y 500 como máximo |
| `GET /v1/documents/{documentId}/pages/{pageNumber}` | Trabajador | `ReadPage` |
| `POST /v1/citations/verify` | Trabajador | `VerifyCitation` |
| `POST /v1/cards` | Trabajador | `SaveKnowledgeCard` |
| `GET /v1/health` | Nadie | Comprobar que está viva |

- **Subidas:** multipart, con un máximo configurable de 50 MB por defecto. Se acepta un tipo según su contenido, no según su extensión.

## 9. Servidor MCP (`mcp/`)

- **Base:** TypeScript con el SDK oficial `@modelcontextprotocol/sdk` y Node 24 (el instalado).
- **Dos transportes, con las mismas herramientas:**
  - **stdio,** para las IAs de escritorio. Lee `ODIN_API_URL` y `ODIN_ACCESS_KEY` del entorno.
  - **Streamable HTTP,** en `127.0.0.1`. Cada sesión trae su `Authorization: Bearer` y el servidor lo pasa tal cual a la API.
- **Herramientas:** al abrir sesión llama a `GET /v1/me` y registra solo las herramientas de las capacidades activadas.
  - Nombres en camelCase: `searchKnowledge`, `readPage`, `listDocuments`, `verifyCitation` y `saveKnowledgeCard`.
  - Entradas definidas con zod. Las salidas llevan siempre los campos de la cita.
  - `listDocuments` recorre todas las páginas de la API y devuelve la lista completa, hasta 2.000 documentos. Si hay más, lo dice con `truncated: true`.
- **Instrucciones del servidor** (campo `instructions` de MCP):
  - citar documento y página en toda afirmación sobre el trabajo de la empresa;
  - si no hay resultados, decir «no consta»;
  - comprobar las citas con `verifyCitation`;
  - presentar el conocimiento general aparte, como información añadida (D-024).
- **Sin estado ni reglas:** no guarda contenido, no lo cachea y no decide permisos. Un 403 o un 404 de la API se devuelve como error de la herramienta, con un texto claro.
- **Registro en Claude Desktop:** se documenta en el README, pero hacerlo en este equipo requiere permiso del propietario (es configuración fuera del repositorio). La demostración de cierre usa un cliente MCP del propio SDK, en un script.

## 10. Panel (`panel/`)

- **Tecnología:** TypeScript con React y Vite. Lo sirve nginx en su contenedor, que además reenvía `/v1` a la API: al ser el mismo origen, no hay CORS y la cookie funciona.
- **Pantallas de la fase 1, todas en español:**
  1. Entrada.
  2. Empresas: listar y crear, renombrar y capacidades (solo el administrador del grupo).
  3. Empresa: proyectos, usuarios (alta de administrador de empresa o trabajador, desactivar) y claves (dar una, con la clave a la vista una sola vez y botón de copiar; revocar).
  4. Documentos: subir a una empresa y proyecto, lista con estado y motivo del error, reintentar.
  5. Revisión: fichas pendientes, con aprobar, corregir o rechazar.
- **Administrador de empresa:** entra directamente en la suya. La API impone el límite y el panel solo oculta lo que no puede hacer.
- **Accesibilidad básica:** etiquetas en todos los campos, uso con teclado y contraste suficiente.
- **Sin estadísticas** en la fase 1. Cuando lleguen, solo descriptivas y sin rankings.

## 11. Despliegue (`deploy/`)

- **`compose.yaml`:**
  - `postgres`: versión 17 fijada, la misma de las pruebas;
  - `api`: Temurin 21 JRE, multiarquitectura;
  - `panel`: nginx;
  - `mcp-http`: Node.
  - Puertos solo en `127.0.0.1`.
  - Volúmenes `pgdata` y `originals`.
- **Imágenes:** Dockerfiles en varias etapas, para que `docker compose build` solo necesite Docker en el equipo.
- **Configuración:**
  - Cada instalación lleva su `.env`, que git ignora. El repositorio lleva solo `.env.example`.
  - Nada de un equipo concreto en `compose.yaml`. Lo que sea de una sola máquina (en la fase 2, la URL del servicio lector en el Mac) va en su `.env` o en un `compose.override.yaml` no versionado.
- **`Makefile` en la raíz:**
  - `make setup`: dependencias locales;
  - `make test`: pruebas de los tres proyectos;
  - `make check`: pruebas, Checkstyle, ArchUnit, ESLint y cobertura;
  - `make up` y `make down`;
  - `make demo`: crea datos ficticios y ejecuta la demostración de cierre.

## 12. Calidad y verificación

- **Pruebas:**
  - **Unitarias:** dominio y casos de uso, con dobles en memoria de cada puerto. Esos dobles se reutilizan en las pruebas de todos los módulos (`testFixtures`).
  - **De integración:** adaptadores con Testcontainers (PostgreSQL 17) y ficheros temporales.
  - **De contrato:** el OpenAPI versionado coincide con el generado; el MCP y el panel compilan contra sus tipos.
  - **De aislamiento:** apartado 7.
  - **Batería de búsqueda:**
    - Se copian a `api/odin-adapter-postgres/src/test/resources/search-battery/` estos ficheros del repositorio OCR (`docs/business-digital-mind/tecnico/pruebas-busqueda/`, rama `feature/planificacion-tecnica`), todos ficticios:
      - `mundo-ficticio/corpus-limpio/`: 29 documentos de 3 comunidades, con las páginas marcadas como `=== Página N ===`;
      - `bateria.json`: preguntas con documento y frase literal esperados;
      - `reformulaciones.json`: las consultas que escribió Haiku, que hacen de IA del trabajador.
    - La prueba carga cada documento separando las páginas por esa marca, sin pasar por los lectores, y una empresa por comunidad.
    - La página esperada es la que contiene la frase.
    - Una prueba da el porcentaje de páginas esperadas que salen entre las 5 primeras.
    - La primera medición fija el umbral de regresión. No se anuncia ninguna cifra que no salga de esa prueba.
  - **De principio a fin:** `make demo` ejecuta los 6 pasos del apartado 3.1 contra Compose, usando la API y un cliente MCP. El paso 1 (panel) se prueba con Playwright, con los navegadores instalados dentro del proyecto (`PLAYWRIGHT_BROWSERS_PATH=0`).
- **Herramientas:**
  - Java: Checkstyle (`MethodLength` 30), ArchUnit y JaCoCo; el umbral de cobertura se fija con los datos de la fase 1.
  - TypeScript: ESLint con `max-lines-per-function` 30, `strict` y Vitest.
- **Definición de terminado:**
  - para cada tarea: `make check` en verde y su verificación, ejecutada;
  - para la fase: `make demo` en verde y un informe de lo que funciona y lo que falta.

## 13. Dudas para el CEO, resueltas (D-036 y D-037, 2026-11-09)

El CEO las resolvió con la delegación del propietario. Respuestas:

- **Fichas pendientes:** los compañeros no las ven hasta que se aprueban. Que el autor vea las suyas pendientes, marcadas «pendiente de revisión», queda para la fase 2 y será por MCP (D-037); en la fase 1 basta el aviso de `saveKnowledgeCard`. Las rechazadas no aparecen en ninguna búsqueda. Qué se dice al autor de una rechazada, en la fase 2.
- **Registro de preguntas:** se guardan el texto y el autor. El trabajador verá sus propias preguntas (fase 2). El panel nunca lista las preguntas de una persona concreta; en la fase 2, solo recuentos descriptivos. Las preguntas que acabaron en «no consta» podrán salir como lista de huecos de la documentación, sin autor. Plazo de conservación: pendiente de la validación legal (D-022).
- **Autorrevisión** (D-037): un administrador que además tiene usuario de trabajador puede revisar sus propias fichas; en una microempresa puede no haber nadie más. La marca de quién revisó deja constancia.

Preguntas originales y propuesta del CTO:

1. **¿Una ficha pendiente de revisar aparece en las búsquedas?** Propuesta: no, solo las aprobadas. El trabajador no tiene panel, así que en la fase 1 no ve sus fichas pendientes; la herramienta `saveKnowledgeCard` le dice que queda pendiente de revisión.
2. **Registro de preguntas:** ¿se guarda el texto de cada pregunta junto a su autor (el encargo pide registrar el autor de cada pregunta) y quién puede verlo? Propuesta: se guarda; en la fase 1 nadie lo ve en el panel, y en la fase 2 solo sale en estadísticas descriptivas.

## 14. Lo que queda al constructor

- **Versiones:** la última estable de cada librería al crear el módulo, fijada.
- **Detalles internos:** nombres internos, estructura de paquetes dentro de cada módulo y estilos del panel.
- **Recortes:** cómo se recorta el extracto literal, siempre que sea texto literal de la página.
- **Límite de intentos de entrada:** cómo se implementa, en memoria y sin dependencias nuevas si es posible.
- **Reparto del trabajo:** cómo se reparten las tareas entre subagentes y en qué orden es cosa del plan.

## 15. Revisión del constructor (2026-11-09)

Un subagente leyó el diseño como si tuviera que planificarlo.
- De sus 8 preguntas, 6 las decidió el CTO y ya están en el texto:
  - el contexto de RLS;
  - los proyectos en la fase 1;
  - los estados de la ingestión;
  - el listado completo;
  - la configuración de texto;
  - la ruta de la batería.
- El reparto entre subagentes es cosa del plan.
- Las 2 del apartado 13 son de negocio y van al CEO.
- El plan las aísla: cambiar una de ellas solo cambia un filtro y una columna, sin rehacer el resto.
