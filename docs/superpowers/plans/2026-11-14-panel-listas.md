# Listas del panel con buscador y páginas (encargo 15): plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- Selector de empresa con una empresa por defecto.
- Proyectos y personas con buscador y 10 por página.
- Documentos con buscador, páginas y tamaño elegible (10, 25 o 50), buscando en el servidor.

**Architecture:**
- **API:** hexagonal. Un caso de uso de página de documentos (lista y total) sobre el puerto `DocumentRepository`, con el filtro SQL y RLS de siempre.
- **Panel:** tres piezas de interfaz (`SearchBox`, `Pagination` y `PageSizeSelect`) y dos ganchos (`usePagedList` en el navegador y `useServerPage` contra la API). Las páginas solo los combinan.

**Tech Stack:** Java 21 y Spring Boot, PostgreSQL 17 (`unaccent`), React con TypeScript, vitest y testing-library, Playwright con `@axe-core/playwright`.

**Spec:** `docs/superpowers/specs/2026-11-14-panel-listas-design.md`

**Se construye cuando el propietario lo diga.** Antes de empezar, la rama se rebasa sobre lo integrado.

## Global Constraints

- TDD; métodos y funciones de 30 líneas como máximo; identificadores en inglés y camelCase; textos de la interfaz en español y en el tono del panel; commits en español, sin atribución a Claude.
- Doble barrera intacta (`Sql.inScope`, `Sql.inVisibleProjects`, `Sql.notWithdrawn` y RLS). La búsqueda y el recuento van dentro del mismo filtro.
- Sin `window.confirm` (`noWindowConfirm.test.ts`). Los diálogos usan `useConfirm`.
- Cada control lleva un nombre accesible. En las pruebas de principio a fin, `AxeBuilder` sin violaciones graves ni críticas.
- Tras tocar la API, se regeneran `api/openapi.json`, `mcp/src/api/types.ts` y `panel/src/api/types.ts` (`npm run generate:types`), y `npm run check:types` pasa en los dos.
- No se tocan `deploy/.env`, la pila `odin` del propietario, su panel (127.0.0.1:8088, D-099) ni `.worktrees/integracion`.
- Al final, en verde: `make check` y `make demo` con `DEMO_PROJECT=odin-demo-cto2 DEMO_API_PORT=19080 DEMO_PANEL_PORT=19088 DEMO_MCP_PORT=19090`.

## Review Focus

1. **Teclear rápido en Documentos:** una respuesta vieja nunca pisa a la nueva (`useServerPage` descarta las respuestas de búsquedas anteriores). Prueba en la tarea 5.
2. **Borrar el último elemento de la última página:** la lista se queda en la última página que existe, no en una vacía. Prueba en la tarea 2.
3. **`q` con `%`, `_`, acentos o mayúsculas:** se busca como texto, sin distinguir mayúsculas ni acentos. Prueba en la tarea 1.
4. **Seleccionar documentos y cambiar de página:** la selección se vacía, para que ninguna acción caiga sobre documentos que no se ven. Prueba en la tarea 5.
5. **Administrador de grupo sin empresas:** el panel muestra un estado vacío claro y no se rompe (`EmptyState`). Prueba en la tarea 3.

---

### Task 1: Página de documentos en la API

**Files:**
- Modify: `api/odin-application/.../port/DocumentRepository.java`. Añadir `List<DocumentEntry> search(CompanyScope, ProjectVisibility, boolean withdrawn, Optional<String> titleQuery, int offset, int limit)` y `int count(CompanyScope, ProjectVisibility, boolean withdrawn, Optional<String> titleQuery)`, o ampliar `list`/`listWithdrawn` si encaja mejor; se decide leyendo la implementación y se justifica.
- Modify: `api/odin-adapter-postgres/.../PostgresDocumentRepository.java`. Filtro `unaccent(lower(d.title)) LIKE unaccent(lower(:q))`, con `%` y `_` escapados como en `SearchSql.bindTerms`.
- Modify: `api/odin-application/.../document/ListCompanyDocuments.java`. `DocumentPage page(Actor, CompanyId, int offset, int limit, boolean withdrawn, Optional<String> query)`, con `record DocumentPage(List<TaggedDocument> documents, int total)`. El `query` se recorta, y vacío equivale a no haber búsqueda.
- Modify: `AdminDocumentController.list`. Añade `@RequestParam(required = false) String q` (más de 100 caracteres dan 400) y responde `Responses.DocumentPage(List<DocumentView> documents, int total)`.
- Modify: `panel/src/api/client.ts`. `api.documents` se adapta a la forma nueva para quien la use, y se añade `api.documentPage(companyId, { q, offset, limit, withdrawn })`.
- Regenerate: `openapi.json` y los dos `types.ts`.
- Test: `ListCompanyDocumentsTest`, `AdminApiTest` (o la prueba del controlador de documentos que ya exista), `PostgresDocumentRepositoryIT` y la prueba de aislamiento de la API.

**Interfaces:**
- Produces: `GET /v1/admin/companies/{id}/documents?q&offset&limit&withdrawn` → `{documents, total}`, y `api.documentPage`.

- [ ] **Step 1: Pruebas que fallan.**
  - IT, con dos empresas:
    - «poliza» encuentra «Póliza seguro 2023» y «PÓLIZA»;
    - «100%» y «a_b» se buscan como texto;
    - el total cuenta solo lo de la empresa y respeta `withdrawn`;
    - nada de la otra empresa sale ni cuenta.
  - Caso de uso: un `query` de solo espacios equivale a ninguno, y un administrador de otra empresa recibe la misma excepción que hoy.
  - Controlador: `q` de 101 caracteres da 400, y la respuesta lleva `documents` y `total`.
- [ ] **Step 2: Implementar y comprobar.** `cd api && mvn -q verify`; regenerar los tipos; `npm run check:types` en `mcp/` y en `panel/`.
- [ ] **Step 3: Commit** en `task/panel-listas/api-documentos`: `feat: la lista de documentos del administrador busca por título y da el total (encargo 15)`.

### Task 1b: Página de fichas pendientes en la API (D-105)

**Files:**
- Modify: `api/odin-application/.../knowledge/ListCardsForReview.java`. `CardPage page(Actor, CompanyId, CardStatus, Optional<String> query, int offset, int limit)`, con `record CardPage(List<KnowledgeCard> cards, int total)`.
- Modify: el puerto y `PostgresKnowledgeCardRepository` (hoy `listByStatus` sin `LIMIT`, l.61-66). La consulta añade búsqueda y recuento: `unaccent(lower(title || ' ' || body)) LIKE …`, con `%` y `_` escapados.
- Modify: `AdminCardController.java:35`, que añade `q`, `offset` y `limit` (de 10 a 50) y responde `{cards, total}`.
- Modify: `panel/src/api/client.ts`, `api.cardPage(companyId, { q, offset, limit })`.
- Regenerate: `openapi.json` y los dos `types.ts`.
- Test: `ListCardsForReviewTest`, la prueba del controlador y el IT del repositorio (dos empresas, total, acentos, `%`).

- [ ] **Step 1: Pruebas que fallan. Step 2: implementar y comprobar** (`mvn -q verify`; `check:types`).
- [ ] **Step 3: Commit** en `task/panel-listas/api-fichas`: `feat: la revisión de fichas pagina, busca y da el total (encargo 15, D-105)`.

### Task 2: Piezas del panel

**Files:**
- Create: `panel/src/ui/SearchBox.tsx` (con su `.module.css`): `SearchBox({ label, onSearch, resultCount, minLength = 3, delayMs = 300 })`.
- Create: `panel/src/ui/Pagination.tsx`: `Pagination({ label, page, pageCount, onPage })`.
- Create: `panel/src/ui/PageSizeSelect.tsx`: `PageSizeSelect({ value, options = [10, 25, 50], onChange })`.
- Create: `panel/src/components/usePagedList.ts`: `usePagedList<T>(items: T[], matches: (item: T, q: string) => boolean, pageSize: number) → { page, pageCount, visible, setQuery, setPage, total }`.
- Test: un `*.test.tsx` por pieza.

**Interfaces:**
- Produces: las cuatro firmas anteriores. Los textos se fijan aquí y las tareas siguientes los reutilizan:
  - «Buscar {lo que sea}» (lo pone quien llama);
  - «{n} resultados», «1 resultado» y «Ningún resultado para «{q}»»;
  - «Anterior», «Siguiente» y «Página {p} de {n}»;
  - «Por página».

- [ ] **Step 1: Pruebas que fallan.**
  - `SearchBox`:
    - 2 letras no llaman a `onSearch` con texto, y la tercera sí, a los 300 ms (con `vi.useFakeTimers`);
    - borrar hasta menos de 3 entrega «»;
    - Escape borra el texto;
    - el contador está en `aria-live="polite"`;
    - el campo es `searchbox` con su nombre accesible.
  - `Pagination`: «Anterior» desactivado en la página 1 y «Siguiente» en la última; con una sola página no se muestra.
  - `usePagedList`:
    - una búsqueda nueva vuelve a la página 1;
    - si se quita el último elemento de la última página, la lista se queda en la anterior (Review Focus 2);
    - la búsqueda no distingue mayúsculas ni acentos (`normalize('NFD')`).
- [ ] **Step 2: Implementar y comprobar.** `cd panel && npm run lint && npm run typecheck && npm test`.
- [ ] **Step 3: Commit** en `task/panel-listas/piezas`: `feat: buscador, páginas y tamaño de página para las listas del panel (encargo 15)`.

### Task 3: Selector de empresa con una por defecto

**Files:**
- Modify: `panel/src/layout/AppShell.tsx`, el `useCompanyId`.
  - Para el administrador de grupo, la primera empresa de `api.companies()`.
  - Se mantienen el `?empresa=` de los enlaces de caso y la empresa elegida al cambiar de pestaña.
- Modify: `panel/src/components/CompanySwitcher.tsx`. Sin la opción vacía.
- Modify: `AppShell.test.tsx`, `Sidebar.test.tsx` y `CompanySwitcher.test.tsx`, donde esperaban el estado vacío.

- [ ] **Step 1: Pruebas que fallan.**
  - El administrador de grupo entra con la primera empresa elegida, y la pestaña Empresa muestra sus datos sin elegir nada.
  - Cambiar de empresa y de pestaña mantiene la empresa.
  - Un grupo sin empresas muestra un `EmptyState` (Review Focus 5).
  - No queda la opción «Elige una empresa».
- [ ] **Step 2: Implementar y comprobar.** `npm test`.
- [ ] **Step 3: Commit** en `task/panel-listas/selector`: `feat: el menú siempre tiene una empresa elegida (encargo 15)`.

### Task 4: Empresa, proyectos y personas con buscador

**Files:**
- Modify: `panel/src/components/Projects.tsx`. `SearchBox` («Buscar proyectos», por nombre), `usePagedList` con 10 por página y `Pagination` («Páginas de proyectos»).
- Modify: `panel/src/components/Members.tsx`. Lo mismo («Buscar personas», por nombre y correo).
- Test: `Projects.test.tsx`, `Members.test.tsx` y `pages/Company.test.tsx`.

**Interfaces:**
- Consumes: lo que produce la tarea 2.

- [ ] **Step 1: Pruebas que fallan.**
  - Con 23 proyectos se ven 10 y «Página 1 de 3».
  - «sol» filtra y vuelve a la página 1.
  - «so» no filtra.
  - Crear un proyecto recarga y mantiene la búsqueda.
  - Las personas se encuentran también por el correo.
- [ ] **Step 2: Implementar y comprobar.** `npm test`.
- [ ] **Step 3: Commit** en `task/panel-listas/empresa`: `feat: proyectos y personas con buscador y 10 por página (encargo 15)`.

### Task 5: Documentos con búsqueda en el servidor

**Files:**
- Create: `panel/src/components/useServerPage.ts`: `useServerPage<T>(fetchPage: (q, offset, limit) => Promise<{ items: T[]; total: number }>, deps) → { items, total, page, pageCount, setQuery, setPage, pageSize, setPageSize, reload, loading }`. Descarta las respuestas de búsquedas anteriores.
- Modify: `panel/src/pages/Documents.tsx`. `SearchBox` («Buscar documentos»), `PageSizeSelect` (empieza en 25), `Pagination`, el interruptor «Retirados» y la selección.
- Modify: `panel/src/components/SupersedeDialog.tsx`. Busca los candidatos con `api.documentPage` (`q`) en lugar de recibir la lista entera.
- Test: `useServerPage.test.ts`, `pages/Documents.test.tsx` y `SupersedeDialog.test.tsx`.

**Interfaces:**
- Consumes: `api.documentPage` (tarea 1) y lo que produce la tarea 2.

- [ ] **Step 1: Pruebas que fallan.**
  - La URL lleva `q`, `offset` y `limit`.
  - Cambiar el tamaño a 50 vuelve a la página 1.
  - Una respuesta lenta de «pol» no pisa la de «poli» (Review Focus 1).
  - La selección se vacía al cambiar de página, de búsqueda o de tamaño (Review Focus 4).
  - «Retirados» vuelve a la página 1.
  - El diálogo «Sustituir» encuentra un candidato que no estaba en la página.
- [ ] **Step 2: Implementar y comprobar.** `npm test`.
- [ ] **Step 3: Commit** en `task/panel-listas/documentos`: `feat: documentos con buscador, páginas y tamaño elegible (encargo 15)`.

### Task 5b: Revisión con páginas y buscador (D-105)

**Files:**
- Modify: `panel/src/pages/Review.tsx`. `useServerPage` (tarea 5) sobre `api.cardPage`, `SearchBox` («Buscar fichas») y `Pagination` con 10 por página. El número del menú sale del `total` (`onPendingChanged`). Se quita el esconder en el navegador de `useResolvedCards`.
- Test: `pages/Review.test.tsx`.

**Interfaces:**
- Consumes: `api.cardPage` (tarea 1b), `useServerPage` (tarea 5) y lo que produce la tarea 2.

- [ ] **Step 1: Pruebas que fallan.**
  - Con 15 pendientes se ven 10 y «Página 1 de 2».
  - Aprobar la única ficha de la página 2 vuelve a la página 1.
  - El número del menú baja de 15 a 14.
  - «tejado» filtra por el texto de la ficha.
- [ ] **Step 2: Implementar y comprobar.** `npm test`.
- [ ] **Step 3: Commit** en `task/panel-listas/revision`: `feat: revisión de fichas con páginas y buscador (encargo 15, D-105)`.

### Task 6: De principio a fin y accesibilidad

**Files:**
- Create: `panel/e2e/listas.spec.ts`.
  - Prepara por la API, en una empresa de la demo, 23 proyectos, 12 personas y 60 documentos de texto. Reutiliza los ayudantes de `admin.spec.ts`.
  - Recorre los flujos del spec (§4) y llama a `AxeBuilder` en cada estado: con resultados, sin resultados y en la segunda página.
- Modify: `panel/e2e/visual.spec.ts` y sus capturas, solo si cambian, revisándolas.
- Modify: `deploy/demo/run-demo.sh`, si hace falta para incluir el fichero nuevo.

- [ ] **Step 1: Escribir la prueba y verla fallar** contra la rama sin las tareas 3 a 5, o razonar por qué no aplica si ya están fusionadas.
- [ ] **Step 2: Comprobar.** `make demo` con los puertos del apartado de restricciones termina con 0, con la prueba nueva incluida.
- [ ] **Step 3: Commit** en `task/panel-listas/e2e`: `test: listas del panel de principio a fin con accesibilidad (encargo 15)`.

### Task 7: Verificación y revisión final

- [ ] `make check` termina con 0.
- [ ] `make demo` termina con 0.
- [ ] Revisión final de la rama entera.

## Orden y paralelo

- Las tareas 1 y 1b (API) y las tareas 2 y 3 (panel) no comparten ficheros: pueden ir a la vez, en worktrees distintos. La 1b va después de la 1 (regeneran el mismo `openapi.json`).
- La tarea 4 necesita la 2.
- La tarea 5 necesita la 1 y la 2. La 5b necesita la 1b y la 5.
- La tarea 6 va al final.
- Modelos: tareas 1, 1b, 5 y 6 con un modelo estándar (varias capas o el navegador); tareas 2, 3 y 4 con uno barato o medio; revisión final con el más capaz.
