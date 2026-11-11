# Odín · Fase 2, bloque 2: casos parecidos — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que el trabajador pida desde su IA los casos resueltos parecidos a uno nuevo y reciba cada caso con su texto literal y sus páginas, sin autores, respetando empresas y proyectos restringidos.

**Arquitectura:**
- Un caso de uso nuevo, `FindSimilarCases`, sobre el puerto `KnowledgeSearch`.
- Una consulta de PostgreSQL que reutiliza las piezas de la búsqueda y agrupa por documento.
- La regla de qué páginas se devuelven es una función pura del dominio.
- La marca de caso la pone el administrador. Una batería propia mide la calidad.

**Tecnología:** Java 21, Spring Boot 4.1.2, PostgreSQL 17.10 con Flyway y seguridad por filas, TypeScript con el SDK de MCP, React 19, Vitest y Playwright.

**Diseño:** `docs/superpowers/specs/2026-11-11-casos-parecidos-design.md` (aquí, «spec»), que amplía el spec base y el de la 2a. Quien ejecute una tarea lee los tres.

## Cuándo se ejecuta

- Tras el cierre de la 2a, cuando lo diga el CEO.
- Antes de empezar, `feature/fase-2-conocimiento` se actualiza con la punta de `feature/identidad-pertenencias`.
- Si las respuestas del CEO del spec 2 cambian algo, el CTO actualiza el spec y este plan antes de la tarea afectada.

## Restricciones globales

- **TDD con evidencia:** primero la prueba; se ejecuta y se guardan en el informe la orden y la salida en rojo antes del código. Un fallo de compilación solo vale como rojo para la primera prueba de una clase nueva.
- **Capas:** `odin-domain` solo `java.*`; `odin-application` solo `odin-domain` y `java.*`; Spring solo en los adaptadores y en `odin-bootstrap`. ArchUnit lo comprueba.
- **Clean Code (D-029):** métodos de 30 líneas como máximo, sin lógica duplicada y con comentarios mínimos. La consulta de casos **reutiliza** las piezas de `PostgresKnowledgeSearch` (`tsquery`, `anyMatch`, `boost`, `exactTokens`, `excerpt`); no las copia.
- **Nombres:** identificadores en inglés, explicativos y en camelCase. Mensajes, documentación y commits en español.
- **Aislamiento (D-014, spec 2a 6):**
  - cada caso de uso con actor lleva una prueba con un actor de otra empresa y otra con un proyecto restringido del que no es miembro;
  - cada consulta lleva su filtro explícito de empresa y de proyectos visibles;
  - la seguridad por filas es la segunda barrera.
- **Sin autores en los casos parecidos** (spec 5.2): ni en el dominio, ni en el JSON, ni en el MCP.
- **Contrato:** si cambia la API, en la misma tarea se regeneran `api/openapi.json` (`cd api && mvn -q verify -Dodin.updateOpenApi=true`) y los tipos (`npm run generate:types` en `panel/` y en `mcp/`).
- **Cierre de cada tarea:** `make check` y `make demo` terminan con 0, y `SearchBatteryIT` mantiene 27 de 30.
- **Dónde se trabaja:** en `.worktrees/fase-2`. No se ejecutan `make up` ni `make down` y no se toca `deploy/.env`.
- **Git:**
  - cada tarea en su rama `task/fase-2-conocimiento/<tarea>`;
  - commits en español, sin atribución a Claude ni `Co-Authored-By`;
  - sin push;
  - el merge `--no-ff` lo hace el CTO.
- **Migración:** `V11__similar_cases.sql`, o la siguiente libre si el bloque 1 o la 2a usaron otra.
- **Datos:** solo ficticios.

## Foco de revisión

1. **Un caso de un proyecto restringido ajeno** no aparece entre los parecidos, ni siquiera cuando es el que mejor encaja. Prueba en la tarea 2 (`restrictedProjectOfOthersIsInvisible`).
2. **Un caso de dos páginas que coincide en las dos** sale una sola vez, con sus dos páginas. Prueba en la tarea 2 (`twoMatchingPagesGiveOneCase`).
3. **Un caso largo cuya mejor página es la última:** salen las primeras páginas más esa, y `truncated`. Prueba en la tarea 2 (`bestPageBeyondLimitIsAppended`).
4. **Un documento duplicado subido otra vez con otra marca de caso:** la marca no cambia. Prueba en la tarea 1 (`duplicateUploadKeepsCaseDocument`).
5. **Una empresa creada antes de la migración** tiene la capacidad nueva y su trabajador ve la herramienta. Prueba en la tarea 1 (`migrationGivesSimilarCasesToExistingCompanies`).

---

## Interfaces compartidas (fuente única de nombres)

```java
// domain/document — Document gana el componente final `boolean caseDocument`
public Document withCaseDocument(boolean caseDocument);

// domain/organization
public enum Capability { SEARCH_KNOWLEDGE, SAVE_KNOWLEDGE_CARD, SIMILAR_CASES }

// domain/knowledge
public enum QuestionKind { SEARCH, SIMILAR_CASES }
// QuestionRecord gana el componente final `QuestionKind kind`

// domain/search
public record CasePage(int number, String text) { }
public record SimilarCase(SourceType sourceType, UUID sourceId, String title, Optional<ProjectId> projectId,
        Instant addedAt, int pageCount, List<CasePage> pages, Optional<String> body, boolean truncated,
        String excerpt) { }
public record SimilarCasesResult(List<SimilarCase> cases) { public boolean noSource() { return cases.isEmpty(); } }
public record SimilarCasesQuery(List<String> terms, CompanyScope scope, ProjectVisibility visibility,
        Optional<ProjectId> projectId, int limit) { }
public final class CasePageSelection {
    public static final int MAX_PAGES = 3;
    public static final int MAX_CHARACTERS = 12_000;
    public static Selection select(List<CasePage> pagesInOrder, int bestPageNumber);
    public record Selection(List<CasePage> pages, boolean truncated) { }
}

// application/port — KnowledgeSearch gana:
SimilarCasesResult findSimilarCases(SimilarCasesQuery query);
// application/port — DocumentRepository.list gana el filtro `Optional<Boolean> caseDocument`

// application/search
public record SimilarCasesRequest(List<String> queries, Optional<ProjectId> projectId, Optional<Integer> limit) { }
public class FindSimilarCases { public SimilarCasesResult execute(Actor actor, SimilarCasesRequest request); }
// application/document
public class UpdateDocument { public Document execute(Actor actor, DocumentId documentId, boolean caseDocument); }
// UploadDocument.execute gana el parámetro final `boolean caseDocument`
```

- `ProjectVisibility` es el de la 2a (`domain/shared`).
- `SimilarCasesQuery` lleva la visibilidad como la lleve `SearchQuery` tras la tarea 3 de la 2a. Si allí se llama de otra forma, se sigue el nombre de la 2a y se corrige aquí.

**Rutas:**

| Ruta | Cuerpo | Respuesta | Tarea |
|---|---|---|---|
| `POST /v1/admin/companies/{companyId}/documents` | Campo multipart nuevo `caseDocument` (`true` o `false`, falso si falta) | `DocumentView` gana `caseDocument` | 1 |
| `PATCH /v1/admin/documents/{documentId}` | `UpdateDocumentRequest { caseDocument }` | `DocumentView` | 1 |
| `GET /v1/documents?caseDocument=` | — | `WorkerDocumentView` gana `caseDocument` | 2 |
| `POST /v1/similar-cases` | `SimilarCasesRequestBody { queries, projectId?, limit? }` | `SimilarCasesResponse { cases: SimilarCaseView[], noSource }` | 2 |

`SimilarCaseView` tiene los campos del spec 6. `origin` es `ORIGINAL_DOCUMENT` o `KNOWLEDGE_CARD`, y `originLabel` es `null` o «Ficha aportada por un trabajador, resumen hecho por IA».

---

### Tarea 1: Marca de caso, capacidad y tipo de pregunta · Sonnet

Rama `task/fase-2-conocimiento/marca-de-caso`. Spec 4 y 5.1.

**Ficheros:**
- **Crear:**
  - `V11__similar_cases.sql`;
  - `domain/knowledge/QuestionKind.java`;
  - `application/document/UpdateDocument.java` y `UpdateDocumentTest`.
- **Modificar:**
  - dominio: `Document`, `Capability` y `QuestionRecord`;
  - aplicación: `UploadDocument` y su prueba, y `SearchKnowledge`, que registra `QuestionKind.SEARCH`;
  - PostgreSQL: `PostgresDocumentRepository`, `PostgresQuestionLog` y `PostgresOrganizationRepository`, si hace falta;
  - web: `AdminDocumentController`, `Requests` y `Responses`;
  - cableado: `UseCaseConfiguration`;
  - pruebas: los dobles y la prueba de migración;
  - contrato y tipos.

**Valores:**
- `V11`:
  - `ALTER TABLE document ADD COLUMN case_document boolean NOT NULL DEFAULT false`;
  - `ALTER TABLE question_record ADD COLUMN kind text NOT NULL DEFAULT 'SEARCH'`;
  - `INSERT INTO company_capability (company_id, capability) SELECT id, 'SIMILAR_CASES' FROM company ON CONFLICT DO NOTHING`.
- `CreateCompany` ya usa `EnumSet.allOf(Capability.class)`: una empresa nueva nace con `SIMILAR_CASES` sin tocarlo.
- `UpdateDocument`: `AccessPolicy.requireAdminOf`, con el orden 404 y luego 403 de siempre.
- Duplicado: el documento que ya existía conserva su `caseDocument`.
- Panel: solo lo imprescindible para que compile. Las pantallas van en la tarea 4.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `UploadDocumentTest`: `uploadStoresCaseDocument` y `duplicateUploadKeepsCaseDocument`;
  - `UpdateDocumentTest`:
    - `marksAndUnmarksCase`;
    - `unknownDocumentIsNotFound`;
    - `adminOfAnotherCompanyIsForbidden`;
    - `workerIsForbidden`.
  - `SearchKnowledgeTest.recordsQuestionOfKindSearch`.
- [ ] **Paso 2:** implementa el dominio, los casos de uso y los dobles.
- [ ] **Paso 3:** PostgreSQL:
  - `PostgresDocumentRepositoryIT.storesAndListsCaseDocument`;
  - `PostgresQuestionLogIT.storesKind`, o la prueba del registro que exista;
  - `migrationGivesSimilarCasesToExistingCompanies`, en la prueba de migración: una empresa insertada antes de `V11` tiene `SIMILAR_CASES` después.
  - Escribe `V11`.
- [ ] **Paso 4:** web y contrato:
  - `AdminApiTest`: `uploadAcceptsCaseDocumentField` y `patchDocumentMarksCase`;
  - `IsolationTest.otherCompanyAdminCannotMarkForeignDocument`.
- [ ] **Paso 5:** regenera el contrato y los tipos. `make check` y `make demo` terminan con 0.
- [ ] **Paso 6:** commit «feat: marca de ficha de caso, capacidad de casos parecidos y tipo de pregunta».

### Tarea 2: Buscar casos parecidos · Sonnet

Rama `task/fase-2-conocimiento/buscar-casos`. Spec 5.2, 5.3 y 6.

**Ficheros:**
- **Crear:**
  - dominio: `domain/search/CasePage.java`, `SimilarCase.java`, `SimilarCasesResult.java`, `SimilarCasesQuery.java` y `CasePageSelection.java`, con `CasePageSelectionTest`;
  - aplicación: `application/search/SimilarCasesRequest.java` y `FindSimilarCases.java`, con `FindSimilarCasesTest`;
  - PostgreSQL: `adapter/postgres/SearchSql.java`, con las piezas compartidas que hoy son privadas en `PostgresKnowledgeSearch`.
- **Modificar:**
  - `KnowledgeSearch`, `ScriptedKnowledgeSearch` y `PostgresKnowledgeSearch`, con su prueba IT;
  - `ListDocuments` y su prueba, `DocumentRepository` y su doble, y `PostgresDocumentRepository`;
  - `WorkerController`, `Requests`, `Responses`, `UseCaseConfiguration` y `TestWiring`;
  - contrato y tipos.

**Interfaces que consume:** `Document.caseDocument`, `Capability.SIMILAR_CASES` y `QuestionKind` (tarea 1). `WorkerAccess` y `ProjectVisibility` (2a).

**Valores:**
- `FindSimilarCases`:
  - `WorkerAccess.require(actor, organizations, Capability.SIMILAR_CASES)`;
  - proyecto como en `SearchKnowledge`, con 404 «Proyecto no encontrado» si no es visible;
  - términos: al menos uno con texto, o 400 «Indica al menos una consulta con texto»;
  - límite con `WorkerAccess.cap(limit, 5, 10)`;
  - registra `QuestionRecord(kind = SIMILAR_CASES, hitCount = número de casos)`.
- `CasePageSelection.select`:
  - recorre las páginas en orden mientras haya menos de `MAX_PAGES` y la suma no pase de `MAX_CHARACTERS`;
  - si la mejor página no entró, la añade al final;
  - `truncated` es verdadero si quedó fuera alguna página.
- Consulta en `PostgresKnowledgeSearch.findSimilarCases`:
  - candidatos: páginas de `document` con `status = 'READY' AND case_document`, y `knowledge_card` con `status = 'APPROVED'`;
  - filtro de empresa y de proyectos visibles explícito, `project_id IS NULL` incluido;
  - agrupa por fuente con `DISTINCT ON (id)`, quedándose con el mayor `rank` y su página;
  - ordena por `rank DESC, title, id` y aplica `LIMIT`;
  - una segunda consulta trae las páginas de los documentos elegidos, con el mismo filtro de empresa y proyectos.
  - Sin `author` en el `SELECT`.
- `ListDocuments` gana `Optional<Boolean> caseDocument`, ligado a `SEARCH_KNOWLEDGE`.

- [ ] **Paso 1:** pruebas de dominio y de caso de uso; comprueba que fallan:
  - `CasePageSelectionTest`:
    - `shortCaseIsReturnedWhole`;
    - `stopsAtThreePages`;
    - `stopsAtTwelveThousandCharacters`;
    - `bestPageBeyondLimitIsAppended`;
    - `bestPageLongerThanLimitIsStillReturned`.
  - `FindSimilarCasesTest`, con `ScriptedKnowledgeSearch`:
    - `passesTermsScopeVisibilityAndLimit`;
    - `limitDefaultsToFiveAndCapsAtTen`;
    - `blankTermsAreRejected`;
    - `capabilityDisabledIsForbidden`;
    - `adminActorIsForbidden`;
    - `restrictedProjectFilterIsNotFound`;
    - `recordsQuestionOfKindSimilarCases`;
    - `actorOfAnotherCompanySeesOnlyItsCompany`.
  - `ListDocumentsTest.filtersByCaseDocument`.
- [ ] **Paso 2:** implementa el dominio, el caso de uso y los dobles.
- [ ] **Paso 3:** PostgreSQL. Saca las piezas comunes a `SearchSql` sin cambiar `search`; `SearchBatteryIT` tiene que seguir en 27 de 30. Pruebas en `PostgresKnowledgeSearchIT`:
  - `onlyCaseDocumentsAndApprovedCardsAreCandidates`;
  - `twoMatchingPagesGiveOneCase`;
  - `restrictedProjectOfOthersIsInvisible`;
  - `projectlessCaseIsVisibleToEveryone`;
  - `otherCompanyCasesAreNeverReturned`;
  - `noAuthorIsSelected`, que comprueba que `SimilarCase` no tiene campo de autor y que la respuesta JSON de la tarea no lo lleva;
  - `pendingAndRejectedCardsAreExcluded`.
- [ ] **Paso 4:** web y contrato:
  - `WorkerApiTest`:
    - `similarCasesReturnsCasesWithPages`;
    - `similarCasesWithoutResultsSaysNoSource`;
    - `similarCasesCapabilityDisabledIs403`;
    - `listDocumentsFiltersByCaseDocument`.
  - `IsolationTest.workerOfBNeverGetsCasesOfA`.
- [ ] **Paso 5:** regenera el contrato y los tipos. `make check` y `make demo` terminan con 0.
- [ ] **Paso 6:** commit «feat: búsqueda de casos parecidos».

### Tarea 3: Batería de casos parecidos · Sonnet, con un paso de Haiku

Rama `task/fase-2-conocimiento/bateria-casos`. Spec 9.

**Ficheros:**
- **Crear:**
  - `api/odin-adapter-postgres/src/test/resources/similar-cases-battery/` con:
    - `corpus/`, con las fichas de caso de `corpus-limpio`, `casos-limite` y `casos-control`, más los documentos que no son caso de `corpus-limpio`, copiados de la rama `feature/planificacion-tecnica` del repositorio OCR (`docs/business-digital-mind/tecnico/pruebas-busqueda/mundo-ficticio/`);
    - `casos-nuevos/`;
    - `claves.json`;
    - `terminos.json`;
    - `threshold.txt`.
  - `SimilarCasesBatteryIT`.

**Interfaces que consume:** `PostgresKnowledgeSearch.findSimilarCases` (tarea 2).

**Valores:**
- **Carga:** igual que `SearchBatteryIT`: páginas separadas por `=== Página N ===`, sin pasar por los lectores, y una empresa por comunidad (`c1-olmo14`, `c2-almendros`, `c3-puerto3`). Las fichas de caso van con `case_document = true` y el resto con `false`.
- **Casos nuevos:** 12 fichas ficticias.
  - Cada una se parece a un grupo de 1 a 3 casos del corpus por el mismo problema: qué falla, en qué elemento y por qué causa.
  - Dos de ellas reproducen G1 (esperados `caso-2024-017-filtracion-azotea` y `caso-2025-004-filtracion-cubierta`) y G2 (esperado `caso-2025-011-fuga-riego`) de `bateria.json`.
  - Cada una indica en `claves.json` su comunidad y sus casos esperados.
- **Términos:** un subagente **Haiku** distinto, que solo recibe el texto de cada caso nuevo y las instrucciones «Eres la IA de un trabajador; escribe de 3 a 6 consultas cortas para buscar casos parecidos ya resueltos». No ve `claves.json` ni el corpus. Su salida va literal a `terminos.json`.
- **Medida:** para cada caso nuevo, `findSimilarCases` en su comunidad con sus términos y límite 5. Dos datos:
  - porcentaje de casos esperados que salen;
  - número de casos nuevos con todos sus esperados.
- `threshold.txt` guarda el porcentaje de la primera medición. La prueba falla por debajo, con el mismo mensaje que `SearchBatteryIT`.

- [ ] **Paso 1 (Sonnet):** copia el corpus, escribe los 12 casos nuevos y `claves.json`. El CTO revisa las claves antes de seguir.
- [ ] **Paso 2 (Haiku, lo lanza el CTO):** escribe `terminos.json`.
- [ ] **Paso 3 (Sonnet):** `SimilarCasesBatteryIT`. La primera ejecución imprime los dos datos y los fallos. El CTO fija `threshold.txt` con ese valor y apunta las cifras en el informe del bloque.
- [ ] **Paso 4:** `make check` termina con 0 con el umbral fijado. Commit «test: batería de casos parecidos».

### Tarea 4: Panel, MCP y demostración · Sonnet

Rama `task/fase-2-conocimiento/casos-panel-mcp`. Spec 7, 8 y 10.

**Ficheros:**
- **Crear:** `mcp/src/tools/findSimilarCases.ts`, con su prueba, y `deploy/demo/caso-ficticio-1.md` y `caso-ficticio-2.md`.
  - Son dos fichas de caso ficticias sobre una filtración desde la azotea, resueltas de forma distinta: una cerrada y otra reabierta.
  - Cada una es un documento de una sola página (`paginated = false`), lo que basta para probar el camino.
  - El recorte por páginas ya lo prueban las tareas 2 y 3.
- **Modificar:**
  - MCP: `mcp/src/server.ts`, `mcp/src/tools/listDocuments.ts`, `mcp/src/api/client.ts` y sus pruebas;
  - panel: `panel/src/pages/Documents.tsx`, `panel/src/components/Capabilities.tsx` y sus pruebas;
  - demostración: `panel/e2e/admin.spec.ts` y `mcp/scripts/demo-client.ts`;
  - `README.md`.

**Valores:**
- `findSimilarCases` se registra solo con `SIMILAR_CASES`.
  - Descripción: «Busca casos ya resueltos parecidos a uno nuevo. Pasa en queries los términos del problema (qué falla, dónde, la causa) y sus reformulaciones.».
  - Sin resultados: «No constan casos parecidos en los documentos de tu empresa.».
- `listDocuments` gana `caseDocument`, opcional y booleano.
- `INSTRUCTIONS` gana las seis frases del spec 7, en ese orden.
- Panel:
  - casilla «Es la ficha de un caso» en la subida;
  - etiqueta «Caso» en la fila;
  - acción «Marcar como caso» o «Quitar marca de caso»;
  - en capacidades, «Casos parecidos».
  - Con los componentes de la dirección visual.
- `demo-client.ts`, paso nuevo:
  1. sube los dos casos ficticios marcados como caso y uno sin marcar;
  2. `findSimilarCases` devuelve los dos con `pages` y sin `authorName`, y no el otro documento.
- `admin.spec.ts`: marcar y desmarcar un documento contra la API real.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - MCP:
    - `findSimilarCases.test.ts`: `registeredOnlyWithCapability`, `sendsQueriesProjectAndLimit` y `noSourceSaysNoConstan`;
    - la prueba de `INSTRUCTIONS`, con las seis frases.
  - Panel:
    - `Documents.test.tsx`: `uploadSendsCaseDocument`, `rowShowsCaseLabel` y `togglesCaseMark`;
    - `Capabilities.test.tsx`: `showsSimilarCasesCapability`.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make demo` dos veces seguidas y `make check` terminan con 0.
- [ ] **Paso 4:** commit «feat: casos parecidos en el panel, el MCP y la demostración».

### Cierre del bloque · CTO

- Revisión final de toda la rama con Opus, con este foco:
  - aislamiento;
  - ausencia de autores;
  - que la consulta común no se ha duplicado.
- `make check` y `make demo` ejecutados por el CTO.
- Informe del bloque con las cifras de las dos baterías, T-016 en el registro técnico y copia del informe en la carpeta de notas para el CEO.

## Orden y paralelismo

- En serie: 1, 2, 3 y 4. Cada una usa el contrato o el modelo de la anterior.
- El paso 1 de la tarea 3 (escribir los casos nuevos) puede empezar en paralelo con la tarea 2: solo escribe ficheros de prueba.

## Modelos de los revisores

- **Opus:** tareas 1 y 2, que tocan capacidades, aislamiento y proyectos restringidos.
- **Sonnet:** tareas 3 y 4.
