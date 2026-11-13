# Mejoras del expediente (D-093): plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- La búsqueda marca las fichas de caso y lleva a la IA a `getCaseFile`.
- `verifyCitation` comprueba todas las citas en una sola llamada.
- Las descripciones dicen qué documentos son del expediente y que un título no se cita.

**Architecture:** Hexagonal, como el resto de Odín.
- `caseDocument` sale del SQL de la búsqueda, pasa al dominio (`SearchHit`) y de ahí al DTO web y al MCP.
- El lote de citas es un caso de uso nuevo en `VerifyCitation`, con su propio endpoint, para que OpenAPI y los tipos generados queden limpios.
- Las reglas para la IA van en las descripciones de las herramientas, no en `INSTRUCTIONS`.

**Tech Stack:** Java 21 y Spring Boot (api), PostgreSQL 17, TypeScript con zod y el SDK de MCP (mcp), React (panel: solo tipos regenerados), Python 3 (experimento).

**Spec:** `docs/superpowers/specs/2026-11-13-expediente-mejoras-design.md`

## Global Constraints

- **Sin ninguna medición con IA** (D-094). Las pruebas usan dobles; `claude -p` no se lanza.
- **TDD; métodos de 30 líneas como máximo;** nombres en inglés y camelCase (Python, snake_case); commits en español, sin atribución a Claude.
- **Doble barrera intacta:** el filtro SQL (`Sql.inScope`, `Sql.inVisibleProjects`, `Sql.notWithdrawn`) más RLS. Un campo nuevo no abre ninguna fila que antes no se viera.
- **Sin push. No tocar `deploy/.env`,** la pila `odin` del propietario ni `.worktrees/integracion`.
- **`INSTRUCTIONS` no cambia** y se queda en 2.200 caracteres o menos.
- **Las descripciones siguen siendo neutras** (D-084, D-091), sin ejemplos de tipo de proyecto: `mcp/test/neutral.test.ts` sigue en verde.
- **Al final, en verde:** `make check` y `make demo`, este último con `DEMO_PROJECT=odin-demo-cto2 DEMO_API_PORT=19080 DEMO_PANEL_PORT=19088 DEMO_MCP_PORT=19090`.

## Review Focus

1. **Lote con un documento invisible, retirado o sin leer:** devuelve `verified: false` en su posición y no un 404 que tumbe el lote. Tampoco puede distinguir «no existe» de «no lo ves». Prueba en la tarea 2.
2. **Lote vacío o de más de 20 citas, o una cita sin `quote`, `documentId` o `pageNumber`:** 400 con un mensaje claro, sin comprobar nada. Prueba en la tarea 2.
3. **Una ficha de caso de otro proyecto:** no aparece en la búsqueda, igual que antes, y `caseDocument` no añade filas. La prueba de aislamiento existente sigue en verde, y la tarea 1 añade que una ficha (KNOWLEDGE_CARD) nunca lleva `caseDocument`.
4. **Volumen entregado:** una llamada por lotes cuenta en `DeliveryMeteringFilter` como hoy (una llamada a la ruta `VERIFY_CITATION`, con el tamaño de su respuesta). Prueba en la tarea 2.
5. **El ejecutor de la medición** cuenta las citas de un lote como resultados de `verifyCitation`, para que el informe pueda dar citas por llamada. Prueba en la tarea 3.

---

### Task 1: `caseDocument` en la búsqueda y guía hacia `getCaseFile`

**Files:**
- Modify: `api/odin-domain/src/main/java/com/e2d/odin/domain/search/SearchHit.java` (nuevo componente `boolean caseDocument`, después de `paginated`).
- Modify: `api/odin-adapter-postgres/src/main/java/com/e2d/odin/adapter/postgres/PostgresKnowledgeSearch.java`. El SQL de candidatos selecciona `d.case_document` para documentos y `false` para fichas; `hit()` lo lee.
- Modify: `api/odin-adapter-web/src/main/java/com/e2d/odin/adapter/web/dto/Responses.java` (`Hit` con `boolean caseDocument`).
- Modify: los demás `new SearchHit(` (tests: `WorkerApiTest`, `DeliveryMeteringFilterTest`, `SupersessionCheckTest`, `SearchKnowledgeTest`).
- Regenerate: `api/openapi.json` (`OpenApiContractIT` con `-Dodin.updateOpenApi=true`), `mcp/src/api/types.ts` y `panel/src/api/types.ts` (`npm run generate:types`).
- Modify: `mcp/src/tools/searchKnowledge.ts` (los resultados de documento llevan `caseDocument`; descripción) y `mcp/src/tools/caseFile.ts` (descripciones de `getCaseFile` y `readCaseFile`).
- Test: `PostgresKnowledgeSearchIT`, `WorkerApiTest`, `mcp/test/caseFile.test.ts`, `mcp/test/server.test.ts`.

**Interfaces:**
- Produces: `SearchHit.caseDocument()`, JSON `hits[].caseDocument` en `POST /v1/search` y `caseDocument` en cada resultado de documento de `searchKnowledge`. Las fichas (KNOWLEDGE_CARD) no lo llevan en el MCP.

- [ ] **Step 1: Pruebas que fallan**
  - `PostgresKnowledgeSearchIT.caseDocumentFlagsCaseCards`: un documento con `case_document = true` y otro normal; los dos salen en la búsqueda, uno con `caseDocument() == true` y el otro con `false`; una ficha sale con `false`.
  - `WorkerApiTest.searchHitCarriesCaseDocument`: el JSON de `/v1/search` tiene `hits[0].caseDocument == true`.
  - `mcp/test/caseFile.test.ts`:
    - un resultado de documento de `searchKnowledge` con `caseDocument: true` lo conserva, y uno de ficha no tiene la clave;
    - la descripción de `searchKnowledge` contiene las frases A y B;
    - la de `getCaseFile` contiene C y D;
    - la de `readCaseFile` contiene D.
  - `server.test.ts`: `INSTRUCTIONS.length <= 2200`, si no lo comprueba ya.
- [ ] **Step 2: Comprobar que fallan.** `cd api && mvn -q -pl odin-adapter-postgres,odin-adapter-web -am test -Dtest='PostgresKnowledgeSearchIT,WorkerApiTest' -Dsurefire.failIfNoSpecifiedTests=false` (o el equivalente con failsafe para el IT) y `cd mcp && npm test`. Tienen que fallar por el campo y por las frases.
- [ ] **Step 3: Implementar.** Frases exactas, tal cual:
  - **A** (searchKnowledge): `title es el nombre del documento, no texto de la página: no lo cites; cita el extracto o el texto de readPage. `
  - **B** (searchKnowledge): `caseDocument true marca la ficha de un caso: si la pregunta trata de un caso o de un expediente, llama a getCaseFile con ese documentId como caseId antes de responder. `
  - **C** (getCaseFile): `Úsala siempre que la pregunta nombre un caso o un número de expediente; si no tienes su caseId, busca antes la ficha del caso con searchKnowledge (caseDocument true). `
  - **D** (getCaseFile y readCaseFile): `Un documento que cita el mismo número de caso pero no está en getCaseFile no forma parte del expediente: no lo incluyas; si lo mencionas, di aparte que no está en el expediente. `
- [ ] **Step 4: Regenerar el contrato y comprobar.**
  1. `cd api && mvn -q -pl odin-bootstrap -am verify -Dit.test=OpenApiContractIT -Dodin.updateOpenApi=true`.
  2. `cd mcp && npm run generate:types`.
  3. `cd panel && npm run generate:types`.
  4. `cd mcp && npm run check:types && npm test`.
  5. `cd panel && npm run check:types`.
  6. `cd api && mvn -q verify`.
- [ ] **Step 5: Commit** en `task/expediente-mejoras/fichas-caso`: `feat: la búsqueda marca las fichas de caso y guía a getCaseFile; pertenencia al expediente (D-093)`.

### Task 2: `verifyCitation` por lotes

**Files:**
- Modify: `api/odin-application/src/main/java/com/e2d/odin/application/search/VerifyCitation.java`. Añadir `List<Boolean> executeAll(Actor actor, List<CitationCheck> checks)`. Pide la capacidad una vez. Por cada cita: un documento invisible o que no está READY da `false`, sin lanzar excepción. Se reutiliza la comprobación de página de `execute`.
- Create: `api/odin-application/src/main/java/com/e2d/odin/application/search/CitationCheck.java`, con `record CitationCheck(DocumentId documentId, int pageNumber, String quote)`.
- Modify: `WorkerController`, nuevo `POST /v1/citations/verify-batch`. Recibe `Requests.VerifyCitations(List<VerifyCitation> citations)` y devuelve `Responses.Verifications(List<VerificationResult> results)`, con `VerificationResult(UUID documentId, int pageNumber, boolean verified)`, en el mismo orden. Responde 400 si la lista es nula, está vacía, tiene más de 20 citas o alguna cita no trae los tres campos.
- Modify: `WorkerRoutes`, que pone la ruta nueva bajo `WorkerTool.VERIFY_CITATION`.
- Regenerate: `api/openapi.json` y los dos `types.ts`.
- Modify: `mcp/src/api/client.ts` (`verifyCitations(body)`) y `mcp/src/tools/verifyCitation.ts`.
  - La entrada pasa a ser solo `citations`: una lista de entre 1 y 20 objetos `{documentId, pageNumber, quote}`.
  - Llama al endpoint por lotes y devuelve `{results: [{documentId, pageNumber, quote, verified}]}`.
- Test: `VerifyCitationTest`, `WorkerApiTest`, `DeliveryMeteringFilterTest` y el test MCP de `verifyCitation` en `server.test.ts`. Las pruebas existentes que llaman a la herramienta con un solo argumento pasan a usar un lote de una cita.

**Interfaces:**
- Consumes: `VerifyCitation.execute`, que no cambia (el endpoint de una sola cita sigue igual, por compatibilidad).
- Produces: `POST /v1/citations/verify-batch` y la herramienta MCP `verifyCitation({citations})`.

**Decisión** (el diseño decía «el mismo endpoint acepta las dos formas»): se usa un endpoint aparte. Con dos formas de entrada y de respuesta en la misma ruta, OpenAPI y los tipos generados quedan ambiguos. Para la IA no cambia nada: ve una sola herramienta.

- [ ] **Step 1: Pruebas que fallan**
  - `VerifyCitationTest.batchKeepsOrderAndHidesInvisible`: un lote con una cita exacta, una inventada, una de otra empresa y una de un documento no READY da `[true, false, false, false]`.
  - `VerifyCitationTest.adminCannotVerifyBatch`: un administrador recibe la misma excepción que hoy con `execute`.
  - `WorkerApiTest.verifiesCitationsInBatch`: devuelve 200 y `results` en orden, con `documentId` y `pageNumber` de cada cita.
  - `WorkerApiTest.invalidBatchIs400`, parametrizada: `{}`, `{"citations":[]}`, 21 citas, y una cita sin `quote`.
  - `DeliveryMeteringFilterTest`: `/v1/citations/verify-batch` cuenta como `VERIFY_CITATION`.
  - MCP (`server.test.ts`):
    - `verifyCitation` con dos citas devuelve dos `results` en orden;
    - con 21 citas, o con 0, zod lo rechaza;
    - la descripción contiene la frase E.
- [ ] **Step 2: Comprobar que fallan** (las mismas órdenes que en la tarea 1).
- [ ] **Step 3: Implementar.** La descripción de la herramienta MCP es exactamente la frase **E**: `Comprueba en una sola llamada todas las citas literales de tu respuesta (de 1 a 20), también las de documentos que no hayas leído con readPage. Devuelve verified para cada una, en el mismo orden. Si una da false, no la uses; una cita sin comprobar no se da. El título del documento no es texto de la página: solo vale lo que aparece en el texto.`
- [ ] **Step 4: Regenerar el contrato y comprobar** (como en el paso 4 de la tarea 1).
- [ ] **Step 5: Commit** en `task/expediente-mejoras/citas-por-lotes`: `feat: verifyCitation comprueba las citas por lotes (D-093)`.

### Task 3: Herramientas del experimento

**Files:**
- Modify: `experimentos/2026-11-12-archivo-ficticio/pila/cargar_expedientes.py`. `guardar()` escribe en un fichero temporal de la misma carpeta, con modo `0o600`, y lo cambia por el bueno con `os.replace`.
- Modify: `experimentos/2026-11-12-archivo-ficticio/medir/ejecutar.py`. `LISTAS["verifyCitation"] = "results"`.
- Test: `pila/test_cargar_expedientes.py` y `medir/test_ejecutar.py`.

- [ ] **Step 1: Pruebas que fallan**
  - `test_guardar_es_atomico_y_privado`: tras guardar, el fichero tiene modo `0o600` y no queda ningún temporal en la carpeta. Si `json.dumps` falla a mitad, el fichero anterior sigue intacto.
  - `test_cuenta_las_citas_de_un_lote`: `contar_resultados("verifyCitation", '{"results":[{},{}]}') == 2`.
- [ ] **Step 2: Comprobar que fallan.** `cd experimentos/2026-11-12-archivo-ficticio/pila && python3 -m unittest test_cargar_expedientes` y `cd ../medir && python3 -m unittest`.
- [ ] **Step 3: Implementar y comprobar que pasan.**
- [ ] **Step 4: Commit** en `task/expediente-mejoras/herramientas-medicion`: `fix: guardar() atómico y privado; el ejecutor cuenta las citas de un lote (D-093)`.

### Task 4: Verificación completa

- [ ] `make check`: termina con 0.
- [ ] `make demo` con los puertos del apartado de restricciones: termina con 0. Si la demo llama a `verifyCitation` del MCP con la forma antigua, se adapta en esta tarea.
- [ ] Revisión final de la rama entera.
