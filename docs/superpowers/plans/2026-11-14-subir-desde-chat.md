# Subir documentos desde el chat (encargo 17): plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que el trabajador, desde su IA, pida un enlace para subir documentos a un proyecto. Es el mismo enlace de 15 minutos que ya existe para el expediente, ahora con un permiso propio de empresa.

**Architecture:**
- La subida al expediente ya existe; esta ampliación reutiliza la página, el enlace y el `store` de `UploadDocument`.
- El enlace gana un destino: caso o proyecto.
- Un caso de uso nuevo, `UploadToProject`.
- Una capacidad nueva por empresa, `UPLOAD_DOCUMENTS`.

**Tech Stack:** Java 21 y Spring Boot, PostgreSQL 17 con RLS y Flyway, MCP en TypeScript con zod, React y Playwright.

**Spec:** `docs/superpowers/specs/2026-11-14-subir-desde-chat-design.md`. **Este plan sigue las recomendaciones de su sección 6.** Si el propietario elige otra cosa, se cambia antes de construir. La opción B de la decisión 1 (permiso por persona) añadiría tareas.

**Se construye cuando el propietario lo diga.**

## Global Constraints

- TDD; métodos de 30 líneas como máximo; identificadores en inglés; textos en español; commits en español, sin atribución a Claude.
- Doble barrera (filtro SQL y RLS) en todo lo nuevo. Un enlace nunca revela si un proyecto existe: todos los fallos dan el mismo 404.
- Enlaces: 15 minutos, solo red local (`LocalNetworkGuard`), atados al primer equipo, 20 ficheros, el token guardado como hash y nada más en la URL. Sin cambios.
- La migración no exige vaciar: las filas de hoy siguen siendo válidas.
- Herramientas de escritura: solo cuando el trabajador lo pide expresamente.
- Tras tocar la API, se regeneran OpenAPI y los dos `types.ts`, y `check:types` pasa en los dos.
- No se tocan `deploy/.env`, la pila del propietario ni su panel (D-099), ni `.worktrees/integracion`.
- Al final, en verde: `make check` y `make demo` (`DEMO_PROJECT=odin-demo-cto2 DEMO_API_PORT=19080 DEMO_PANEL_PORT=19088 DEMO_MCP_PORT=19090`).

## Review Focus

1. **Un trabajador que deja de ser miembro de un proyecto cerrado** con el enlace vivo: la siguiente subida falla con el mismo 404. Prueba en la tarea 2.
2. **Un enlace de caso de antes de la migración:** sigue funcionando igual. Prueba en la tarea 1.
3. **El mismo fichero subido dos veces al proyecto en paralelo:** un solo documento, y la segunda subida dice «ya estaba». Prueba en la tarea 2.
4. **`requestUploadLink` con `caseId` y `projectId` a la vez, o sin ninguno:** error claro, sin enlace. Prueba en la tarea 4.
5. **Sin `UPLOAD_DOCUMENTS` pero con `CASE_FILES`:** se sigue subiendo a casos y no se ofrece subir a proyectos. Prueba en las tareas 2 y 4.

---

### Task 1: Migración V25 y dominio

**Files:**
- Create: `api/odin-adapter-postgres/src/main/resources/db/migration/V25__upload_links_to_projects.sql`.
  - `access_link.project_id uuid NULL REFERENCES project(id)`.
  - El CHECK de `kind = 'UPLOAD'` acepta exactamente un destino: `case_document_id` o `project_id`.
- Modify: `Capability` (`UPLOAD_DOCUMENTS`), `AccessLink` (destino `Optional<ProjectId> projectId`, junto al caso) y `PostgresAccessLinkRepository`.
- Test: un IT de la migración y del repositorio.

- [ ] **Step 1: Pruebas que fallan.**
  - Un enlace de caso de antes (Review Focus 2) se lee igual.
  - Uno de proyecto se guarda y se lee.
  - La base rechaza un enlace de subida sin destino o con los dos.
  - RLS: una empresa no ve los enlaces de otra.
- [ ] **Step 2: Implementar y comprobar.** `cd api && mvn -q verify`.
- [ ] **Step 3: Commit** en `task/subir-desde-chat/migracion`: `feat: los enlaces de subida pueden ir a un proyecto (encargo 17)`.

### Task 2: Casos de uso

**Files:**
- Modify: `application/link/IssueUploadLink.java`. Añade `forProject(Actor, ProjectId)`: exige `UPLOAD_DOCUMENTS` y poder escribir en el proyecto (reutiliza la comprobación de escritura de `CaseFileAccess`, o la extrae a un sitio común).
- Modify: `ResolveLink.visibleTarget`, que comprueba en cada uso que la persona sigue viendo el proyecto.
- Create: `application/document/UploadToProject.java`. Duplicados con `findBySha256InProject`; reutiliza `UploadDocument.store`; si pierde la carrera contra el índice único, toma el documento ganador.
- Modify: `UploadByLink`, que elige `UploadToCaseFile` o `UploadToProject` según el destino.
- Test: `IssueUploadLinkTest`, `ResolveLinkTest`, `UploadToProjectTest` y un IT de aislamiento.

- [ ] **Step 1: Pruebas que fallan.**
  - Sin la capacidad, `ForbiddenException`.
  - Un proyecto cerrado sin ser miembro da el mismo 404 que uno inexistente.
  - Al dejar de ser miembro, la siguiente subida falla (Review Focus 1).
  - Duplicado en el proyecto: un documento y «ya estaba»; en paralelo, uno solo (Review Focus 3).
  - Duplicado en otro proyecto que no ve: un documento nuevo.
  - El autor es quien pidió el enlace.
  - Con `CASE_FILES` sin `UPLOAD_DOCUMENTS`, el caso sigue funcionando (Review Focus 5).
- [ ] **Step 2: Implementar y comprobar.**
- [ ] **Step 3: Commit** en `task/subir-desde-chat/casos-de-uso`: `feat: subir a un proyecto por enlace, con su capacidad (encargo 17)`.

### Task 3: API

**Files:**
- Modify: `CaseFileController`, o un `ProjectUploadController` nuevo: `POST /v1/projects/{projectId}/upload-links` responde `{ url, expiresAt }`, como el del caso.
- Modify: `AccessLinkController` y `Responses.UploadLinkInfo`, que pasan a `{ target, caseTitle?, projectName, expiresAt }`. Respuesta de una subida duplicada: `{ filename, duplicate: true }`.
- Modify: `WorkerRoutes`, para la ruta nueva y su medición de volumen.
- Regenerate: OpenAPI y los dos `types.ts`.
- Test: `WorkerApiTest`, `AccessLinkControllerTest` y `IsolationTest`.

- [ ] **Step 1: Pruebas que fallan. Step 2: implementar y comprobar. Step 3: Commit** en `task/subir-desde-chat/api`: `feat: API de enlaces de subida a proyectos (encargo 17)`.

### Task 4: MCP

**Files:**
- Modify: `mcp/src/tools/caseFile.ts` o un fichero propio de `requestUploadLink`.
  - Entrada `{ caseId?: string, projectId?: string }`; exactamente uno. Si no, error claro: «Indica un caso o un proyecto, no los dos».
  - La herramienta se registra si la empresa tiene `CASE_FILES` o `UPLOAD_DOCUMENTS`, y la descripción solo ofrece lo que la empresa tiene.
  - Descripción con `WRITE_RULES`.
- Modify: `mcp/src/server.ts` (registro), `mcp/src/api/client.ts` (la ruta nueva) y `neutral.test.ts`, que debe seguir en verde.
- Test: `caseFile.test.ts` y `server.test.ts`.

- [ ] **Step 1: Pruebas que fallan.**
  - Con `projectId` da un enlace.
  - Con los dos o con ninguno, error (Review Focus 4).
  - Con solo `CASE_FILES`, la descripción no ofrece proyectos (Review Focus 5).
  - Sin ninguna de las dos capacidades, no hay herramienta.
- [ ] **Step 2: Implementar y comprobar.** `npm run check:types && npm run lint && npm run typecheck && npm test`.
- [ ] **Step 3: Commit** en `task/subir-desde-chat/mcp`: `feat: requestUploadLink sube también a un proyecto (encargo 17)`.

### Task 5: Panel

**Files:**
- Modify: `panel/src/pages/Company.tsx`, que añade el interruptor «Subir documentos» a las capacidades, con su descripción.
- Modify: `panel/src/pages/UploadByLink.tsx`. Título según `target` («Subir documentos al proyecto» o «Subir documentos al caso»), destino en `Facts` y «Ya estaba en el proyecto» para un duplicado.
- Test: `Company.test.tsx` y `UploadByLink.test.tsx`.

- [ ] **Step 1: Pruebas que fallan. Step 2: implementar y comprobar. Step 3: Commit** en `task/subir-desde-chat/panel`: `feat: el panel activa la subida y la página muestra el destino (encargo 17)`.

### Task 6: De principio a fin y demo

**Files:**
- Modify: `panel/e2e/admin.spec.ts`, o un fichero nuevo, y `mcp/scripts/demo-client.ts`.
  - Activar «Subir documentos».
  - Pedir el enlace por el MCP con `projectId`.
  - Abrir la página, ver «Proyecto …», subir dos ficheros y verlos en Documentos con su autor.
  - Repetir uno y ver «Ya estaba en el proyecto».
  - `AxeBuilder` en la página de subida.

- [ ] **Step 1: Escribir y comprobar** con `make demo`.
- [ ] **Step 2: Commit** en `task/subir-desde-chat/e2e`: `test: subida a un proyecto de principio a fin (encargo 17)`.

### Task 7: Verificación y revisión final

- [ ] `make check` y `make demo` terminan con 0.
- [ ] Revisión de aislamiento y revisión final de la rama entera.

## Orden

- Las tareas 1, 2 y 3 van en serie (base, casos de uso, API).
- La 4 (MCP) y la 5 (panel) pueden ir a la vez después de la 3.
- La 6 va al final.
- Modelos: estándar para la 1, la 2 y la 6; barato o medio para la 3, la 4 y la 5; el más capaz para la revisión final.
