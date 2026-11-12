# Odín · Fase 2, bloque 7: documentos sustituidos — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que el administrador marque que un documento sustituye entero a otro, y que la búsqueda, la lectura y la lista del trabajador den el viejo detrás y marcado, con el vigente que lo sustituye, sin saltarse empresas ni proyectos cerrados.

**Arquitectura:**
- La marca va en el documento viejo (`supersededBy` y `supersededAt`) y se escribe con su propia orden, nunca con `save`.
- Al leer, el repositorio y la búsqueda resuelven en la misma consulta el sustituto directo y el vigente del final de la cadena, con el filtro explícito y bajo la seguridad por filas. El SQL de la cadena está en una sola pieza compartida.
- Un caso de uso, `SupersedeDocument`, hace las comprobaciones del spec 5.1 en su orden.
- La búsqueda elige por relevancia como hoy y después pone los sustituidos detrás.
- Las baterías del bloque 6 miden el antes y el después.

**Tecnología:** Java 21, Spring Boot 4.1.2, PostgreSQL 17.10 con Flyway y seguridad por filas, TypeScript con el SDK de MCP, React 19, Vitest y Playwright.

**Diseño:** `docs/superpowers/specs/2026-11-11-documentos-sustituidos-design.md` (aquí, «spec»), que amplía el spec base y el de la 2a. Se mide con las baterías de `2026-11-11-datos-correctos-medidos-design.md` («spec 6») y de su plan. Quien ejecute una tarea lee el spec y, en las tareas 1 y 5, también el spec 6 y el plan del bloque 6.

## Cuándo se ejecuta

- Después del cierre del bloque 6 y antes del bloque 2, cuando lo diga el CEO. El bloque 7 espera su validación (D-037).
- `feature/fase-2-conocimiento` ya tiene los bloques 1, 3 y 6.
- **Nombres que dejan otros bloques.** Este plan parte de la punta de la 2a (f6c5019). Donde los bloques 1, 3 y 6 cambiaron algo, se sigue lo suyo:
  - `Document` ya lleva `pagesWithoutText` (bloque 1): los componentes nuevos van detrás;
  - `ListCompanyDocuments` y `DocumentView` ya llevan temas y estado del etiquetado (bloque 3): `DocumentEntry` ocupa el lugar de `Document` en lo que devuelvan;
  - el cargador compartido, los juegos y la cuenta aparte de sustituidos son los del plan del bloque 6. Aquí se llaman `CorpusLoader`, `sustituidos` y `sustituidos-escala`.
  - Si algo no encaja, el CTO corrige este plan antes de la tarea afectada.

## Restricciones globales

- **TDD con evidencia:** primero la prueba; se ejecuta y se guardan en el informe la orden y la salida en rojo antes del código. Un fallo de compilación solo vale como rojo para la primera prueba de una clase nueva.
- **Pruebas sueltas:**
  - una de unidad: `cd api && mvn -q -pl <módulo> -am test -Dtest=<Clase> -Dsurefire.failIfNoSpecifiedTests=false`;
  - una IT: `cd api && mvn -q -pl <módulo> -am verify -Dit.test=<Clase> -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`;
  - en `mcp/` y `panel/`: `npx vitest run <fichero>`.
- **Capas:** `odin-domain` solo `java.*`; `odin-application` solo `odin-domain` y `java.*`; Spring solo en los adaptadores y en `odin-bootstrap`. ArchUnit lo comprueba.
- **Clean Code (D-029):** TDD, SOLID, métodos de 30 líneas como máximo, sin lógica duplicada y con comentarios mínimos. El SQL de la cadena está solo en `SupersessionSql`, que usan el repositorio y la búsqueda.
- **Nombres:** identificadores en inglés, explicativos y en camelCase. Mensajes, documentación y commits en español.
- **Aislamiento (D-014, D-047, spec 2a 6):**
  - el sustituto y cada salto de la cadena llevan el filtro explícito de empresa y de proyectos (`Sql.inScope` y `Sql.inVisibleProjects`) y van bajo la seguridad por filas;
  - el trabajador resuelve con `WorkerAccess.Granted.content()`, como la búsqueda;
  - cada caso de uso con actor lleva una prueba con un actor de otra empresa, y cada lectura del trabajador, otra con un proyecto cerrado del que no es miembro.
- **Lo decide una persona (D-049):** solo `SupersedeDocument` pone o quita la marca. Ni la subida, ni el procesado, ni la IA la tocan.
- **La marca no pasa por `save`:** el `upsert` no la escribe ni la borra (spec 2).
- **Contrato:** si cambia la API, en la misma tarea se regeneran `api/openapi.json` (`cd api && mvn -q verify -Dodin.updateOpenApi=true`) y los tipos (`npm run generate:types` en `panel/` y en `mcp/`).
- **Cierre de cada tarea:** `make check` y `make demo` terminan con 0, `SearchBatteryIT` mantiene 27 de 30 y `SearchScaleBatteryIT` cumple su umbral.
- **Dónde se trabaja:** en `.worktrees/fase-2` (`feature/fase-2-conocimiento`). No se ejecutan `make up` ni `make down` y no se toca `deploy/.env`. Nada global.
- **Git:**
  - cada tarea en su rama `task/fase-2-conocimiento/<tarea>`;
  - commits en español, sin atribución a Claude ni `Co-Authored-By`;
  - sin push;
  - el merge `--no-ff` lo hace el CTO.
- **Migración:** `V13__superseded_documents.sql`, o la siguiente libre si el orden cambió. V11 es del bloque 1 y V12 del bloque 3.
- **Panel:** con los componentes y las reglas de la dirección visual (D-039): `FormDialog`, `Field`, `StatusPill`, `RowActions`, `Notice` y `Button`.
- **Datos:** solo ficticios.

## Foco de revisión

1. **El administrador vuelve a subir la póliza antigua ya marcada** (el mismo fichero): la marca no cambia y la respuesta de la subida la enseña. Prueba en la tarea 3 (`UploadDocumentTest.duplicateUploadKeepsTheMark`).
2. **Una cadena cortada por un documento que el trabajador no ve:** `replacement` es nulo, nunca el eslabón intermedio ni uno posterior. Pruebas en la tarea 2 (`brokenChainGivesNoReplacement`) y en la 4 (`replacementIsNullWhenTheWorkerCannotSeeIt`).
3. **Dos marcas cruzadas a la vez** dejan una cadena circular en la base que el caso de uso no vio: las lecturas terminan y dan `replacement` nulo. Prueba en la tarea 2 (`circularDataDoesNotHangReads`).
4. **Un `PUT` sin `replacementId` o con un identificador mal formado:** 400 con mensaje, no 500. Prueba en la tarea 3 (`supersedeWithoutOrWithBadReplacementIdIs400`).
5. **Una ficha de conocimiento entre los resultados:** nunca sale sustituida y sigue entre los vigentes por su relevancia. Prueba en la tarea 4 (`cardsAreNeverSupersededAndStayAmongCurrent`).

---

## Interfaces compartidas (fuente única de nombres)

```java
// domain/document
public record DocumentRef(DocumentId id, String title) {
    public static DocumentRef of(Document document);
}
// Document gana los componentes finales `Optional<DocumentId> supersededBy` y `Optional<Instant> supersededAt`, y:
public static final int MAX_SUPERSESSION_CHAIN = 50;
public boolean superseded();                         // supersededBy presente
public Document withSupersession(Optional<DocumentId> supersededBy, Optional<Instant> supersededAt);
/** Un documento leído con lo que depende de quien lee: su sustituto directo y el vigente del final de su cadena,
 *  cada uno solo si lo ve. */
public record DocumentEntry(Document document, Optional<DocumentRef> supersededBy, Optional<DocumentRef> replacement) { }

// domain/search — SearchHit gana los componentes finales
boolean superseded, Optional<DocumentRef> replacement

// application/port — DocumentRepository
Optional<DocumentEntry> findEntry(CompanyScope scope, ProjectVisibility visibility, DocumentId id);
List<DocumentEntry> list(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> projectId,
        Optional<DocumentStatus> status, int offset, int limit);           // antes devolvía List<Document>
void markSuperseded(CompanyId companyId, DocumentId id, DocumentId replacementId, Instant at);  // su propio UPDATE
void clearSuperseded(CompanyId companyId, DocumentId id);
// save sigue igual: no escribe ni borra la marca

// application/document
public class SupersedeDocument {
    public SupersedeDocument(DocumentRepository documents, Clock clock);
    public DocumentEntry mark(Actor actor, DocumentId documentId, DocumentId replacementId);
    public void unmark(Actor actor, DocumentId documentId);
}
public record UploadOutcome(DocumentEntry entry, boolean duplicate) { public Document document(); }
// GetDocument y RetryDocument devuelven DocumentEntry; ListCompanyDocuments, List<DocumentEntry>

// application/search
public record PageView(DocumentEntry entry, DocumentPage page) { public Document document(); }
// ListDocuments devuelve List<DocumentEntry>

// adapter/web/dto
Requests.SupersedeRequest(UUID replacementId)
Responses.DocumentLink(UUID id, String title)          // supersededBy del panel: el sustituto directo
Responses.Replacement(UUID documentId, String title)   // replacement del trabajador: el vigente del final
// DocumentView gana @Nullable DocumentLink supersededBy y @Nullable Instant supersededAt
// Hit, Page y WorkerDocumentView ganan boolean superseded y @Nullable Replacement replacement
```

**Rutas:**

| Ruta | Cuerpo | Respuesta | Tarea |
|---|---|---|---|
| `PUT /v1/admin/documents/{documentId}/superseded-by` | `SupersedeRequest { replacementId }` | `DocumentView` del viejo | 3 |
| `DELETE /v1/admin/documents/{documentId}/superseded-by` | — | 204 | 3 |
| `GET /v1/admin/companies/{companyId}/documents` y `GET /v1/admin/documents/{documentId}` | — | `DocumentView` gana `supersededBy` y `supersededAt`. La subida y el reintento, que también devuelven `DocumentView`, los llevan igual | 3 |
| `POST /v1/search` | — | `Hit` gana `superseded` y `replacement` | 4 |
| `GET /v1/documents/{documentId}/pages/{pageNumber}` | — | `Page` gana `superseded` y `replacement` | 4 |
| `GET /v1/documents` | — | `WorkerDocumentView` gana `superseded` y `replacement` | 4 |

---

### Tarea 1: Juego de sustituidos y su comprobación, medidos antes del cambio · Sonnet

Rama `task/fase-2-conocimiento/medicion-sustituidos`. Spec 8.

**Ficheros:**
- **Crear:**
  - `api/odin-adapter-postgres/src/test/resources/search-battery/sustituciones.json`;
  - `api/odin-adapter-postgres/src/test/resources/search-battery-scale/sustituciones.json`;
  - `mcp/scripts/answer-battery/data/bateria_sustituidos.json`.
- **Modificar:**
  - el puntuador (`mcp/scripts/answer-battery/score.ts`) y su prueba;
  - el ejecutor (`run.ts`) y, si enumera los juegos, `AnswerBatteryIT`;
  - `mcp/scripts/answer-battery/results/`, con la medición de partida (paso 5).

**Interfaces que consume:** el puntuador, el ejecutor, el mapa de identificador a fichero del corpus y la cuenta aparte de sustituidos del bloque 6 (spec 6, 6.2 a 6.5).

**Interfaces que produce:**

```ts
// clave del corpus del viejo → clave del sustituto
export function readSupersessions(file: string): Map<string, string>;
// las claves citadas que están sustituidas, si la respuesta no dice «sustituido» ni «sustituida»
export function supersededAsCurrent(answer: string, citedKeys: string[], supersededBy: Map<string, string>): string[];
```

**Valores:**
- `sustituciones.json`: lista de `{ "documento": <clave>, "sustituidoPor": <clave> }`. La clave es carpeta de la comunidad y fichero, la misma que el cargador pone de título.
  - Corpus pequeño: `c2-almendros/poliza-seguro-2022.txt` → `c2-almendros/poliza-seguro-2024.txt`.
  - Corpus a escala: la misma pareja en `c2-almendros` y en cada carpeta `c2-*` del corpus, que son las 30 copias de Los Almendros: 31 parejas. Se sacan una vez listando esas carpetas, no a mano.
- `bateria_sustituidos.json`, con el formato de `bateria_resuelta.json`:
  - `E1`, copiada tal cual de `bateria_resuelta.json`: espera `franquicia de 150 euros por siniestro`, en la póliza de 2024, página 2;
  - `S1`, nueva:
    - pregunta: «¿Qué franquicia por daños por agua tenía la póliza de Los Almendros de 2022?»;
    - `categoria`: `sustituido`;
    - cita: `c2-almendros/poliza-seguro-2022.txt`, con `con una franquicia de 200 euros por siniestro`, página 2;
    - `claves`: `[["200"], ["sustituida", "sustituido"]]`.
- **Juegos nuevos:** `sustituidos` (corpus pequeño) y `sustituidos-escala` (corpus a escala), los dos con `bateria_sustituidos.json` y con el proyecto deducido como en el bloque 6.
  - Tienen su propia cuenta y no entran en el mínimo de D-049, que se calcula solo con las preguntas del estudio (spec 6, 6.5).
- **Comprobación «sustituido presentado como vigente»:**
  - va en todos los juegos;
  - el puntuador lee el `sustituciones.json` del corpus del juego;
  - la palabra se busca en toda la respuesta, con la normalización del puntuador (minúsculas y sin tildes);
  - suma a la cuenta aparte del bloque 6, y la pregunta falla con la regla de 2 de 3.

- [ ] **Paso 1:** pruebas en la prueba del puntuador; comprueba que fallan:
  - `supersededCitedWithoutTheWordIsCounted`;
  - `sustituidaOrSustituidoAnywhereIsAccepted`, con «Sustituida» y «SUSTITUIDO»;
  - `answerWithoutSupersededCitationsIsNotChecked`;
  - `supersessionFilesPointToCorpusFiles`: cada clave de los dos `sustituciones.json` existe en su corpus, y el de escala tiene 31 parejas;
  - `supersededSetsDoNotCountForTheMinimum`.
- [ ] **Paso 2:** escribe los tres ficheros de datos e implementa la comprobación y los juegos.
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0.
- [ ] **Paso 4:** commit «test: juego de documentos sustituidos y comprobación de sustituido presentado como vigente».
- [ ] **Paso 5 (lo ejecuta el CTO):** `make answer-battery` con los juegos `sustituidos` y `sustituidos-escala` y los dos modelos, con el Odín de este momento. Es la medición «antes». Los resultados se versionan con el commit «test: medición de partida del juego de sustituidos».

### Tarea 2: La marca en el documento: modelo, migración y repositorio · Sonnet

Rama `task/fase-2-conocimiento/marca-sustituido`. Spec 2, 4 y 5.2.

**Ficheros:**
- **Crear:**
  - `api/odin-adapter-postgres/src/main/resources/db/migration/V13__superseded_documents.sql`;
  - `domain/document/DocumentRef.java` y `domain/document/DocumentEntry.java`;
  - `adapter/postgres/SupersessionSql.java`.
- **Modificar:**
  - dominio: `Document` y `DocumentTest` (créala si no existe);
  - aplicación: `DocumentRepository` e `InMemoryDocumentRepository`. `ListDocuments` y `ListCompanyDocuments` solo ganan `.map(DocumentEntry::document)` para compilar; las tareas 3 y 4 lo quitan;
  - PostgreSQL: `PostgresDocumentRepository`, `PostgresDocumentRepositoryIT`, `RowLevelSecurityIT` y la prueba de migración (`MigrationFromPhaseOneIT` o la que exista);
  - cada `new Document(...)`, que gana `Optional.empty(), Optional.empty()` al final.

**Interfaces que produce:** las de dominio y de `DocumentRepository` de «Interfaces compartidas», y:

```java
final class SupersessionSql {   // adapter/postgres
    /** Las columnas que añade joins: el sustituto directo y el vigente del final de la cadena. */
    static final String COLUMNS = "sd.id AS direct_id, sd.title AS direct_title, sc.current_id, sc.current_title";
    /** LEFT JOIN del sustituto directo (alias sd) y del vigente del final (alias sc) de la columna dada.
     *  Usa los parámetros de Sql.scoped y Sql.visible. */
    static String joins(String supersededByColumn);
    static Optional<DocumentRef> direct(ResultSet rs) throws SQLException;
    static Optional<DocumentRef> current(ResultSet rs) throws SQLException;
}
```

**Valores:**
- `V13`, o la siguiente libre:
  - `ALTER TABLE document ADD COLUMN superseded_by uuid NULL REFERENCES document(id)`;
  - `ALTER TABLE document ADD COLUMN superseded_at timestamptz NULL`;
  - `CHECK (superseded_by <> id)` y `CHECK ((superseded_by IS NULL) = (superseded_at IS NULL))`, cada una con nombre;
  - `CREATE INDEX document_superseded_by_idx ON document (superseded_by)`.
  - Las políticas de V9 no se tocan: ya cubren las columnas nuevas.
- `Document`:
  - el constructor compacto exige `supersededBy` y `supersededAt` presentes a la vez o vacíos a la vez, y `supersededBy` distinto de `id`. Si no, `IllegalArgumentException`;
  - los cambios de estado (`startProcessing`, `markReady` y los que haya) conservan la marca.
- `markSuperseded` y `clearSuperseded`: un `UPDATE document SET superseded_by, superseded_at … WHERE id = :id AND company_id = :company`, en `runInScope(CompanyScope.of(company), ProjectVisibility.all())`.
- `COLUMNS` y el mapeo a `Document` leen `superseded_by` y `superseded_at`.
- `upsert` no nombra las dos columnas: el `INSERT` las deja nulas y el `ON CONFLICT … DO UPDATE` no las toca. El doble en memoria hace lo mismo: `save` conserva la marca guardada.
- **Resolución, en `SupersessionSql.joins`:**
  - directo: el documento de `superseded_by`, con `Sql.inScope` y `Sql.inVisibleProjects`;
  - vigente: un `WITH RECURSIVE` que sigue `superseded_by` desde el directo, con `Sql.inScope` y `Sql.inVisibleProjects` en cada salto y como mucho `Document.MAX_SUPERSESSION_CHAIN` saltos. Es la fila del final, la que tiene `superseded_by` nulo;
  - si la cadena se corta (un eslabón no se ve) o no termina (cadena circular), el vigente es nulo. Nunca un eslabón intermedio;
  - se monta como `LEFT JOIN LATERAL`. Si PostgreSQL no admite ahí la consulta recursiva, se monta como CTE de la consulta, con la misma interfaz.
- `findEntry` y `list` usan `joins` sobre `document d`. `findById` es `findEntry(...).map(DocumentEntry::document)`: no hay otra copia de la consulta.
- El doble en memoria resuelve igual: el directo con `findById(scope, visibility, …)` y la cadena salto a salto, con el mismo tope.

- [ ] **Paso 1:** pruebas de dominio; comprueba que fallan. `DocumentTest`:
  - `supersessionNeedsBothFieldsOrNeither`;
  - `documentCannotSupersedeItself`;
  - `stateChangesKeepTheMark`.
- [ ] **Paso 2:** implementa `Document`, `DocumentRef`, `DocumentEntry`, el puerto y el doble.
- [ ] **Paso 3:** pruebas de PostgreSQL; comprueba que fallan:
  - `PostgresDocumentRepositoryIT`:
    - `marksChangesAndClearsTheMark`;
    - `saveOfAStaleCopyKeepsTheMark`: se lee el documento, se marca y se guarda la copia leída antes con `startProcessing`; la marca sigue;
    - `entryResolvesDirectAndEndOfChain`: con A → B → C, A da directo B y vigente C;
    - `brokenChainGivesNoReplacement`: A sin proyecto → B en un proyecto cerrado → C sin proyecto. Con una visibilidad sin ese proyecto, A sale sustituido, sin directo y sin vigente;
    - `circularDataDoesNotHangReads`: A → B y B → A, puestas con el repositorio; `findEntry` termina y da vigente nulo;
    - `listCarriesEntries`;
    - `tableRejectsSelfMarkAndHalfMarks`, con un `UPDATE` directo.
  - `RowLevelSecurityIT.replacementInHiddenProjectIsNullEvenWithoutTheExplicitFilter`: una consulta con `SupersessionSql.joins` se lanza en un `tx.inScope` sin el proyecto del sustituto, pero con los parámetros del filtro explícito de `ProjectVisibility.all()`. El vigente sale nulo.
  - Prueba de migración, `existingDocumentsAreNotSuperseded`: un documento insertado antes de `V13` sale sin marca.
- [ ] **Paso 4:** escribe `V13`, `SupersessionSql` y el repositorio.
- [ ] **Paso 5:** `make check` y `make demo` terminan con 0; `SearchBatteryIT` da 27 de 30 y `SearchScaleBatteryIT` cumple su umbral.
- [ ] **Paso 6:** commit «feat: marca de documento sustituido en el modelo y el repositorio».

### Tarea 3: Marcar y desmarcar desde la administración · Sonnet

Rama `task/fase-2-conocimiento/marcar-sustituido`. Spec 5.1 y 6.

**Ficheros:**
- **Crear:** `application/document/SupersedeDocument.java` y `SupersedeDocumentTest`.
- **Modificar:**
  - aplicación: `GetDocument`, `RetryDocument`, `ListCompanyDocuments`, `UploadDocument` y `UploadOutcome`, con `AdminDocumentsTest` y `UploadDocumentTest`;
  - web: `AdminDocumentController`, `Requests` y `Responses`;
  - cableado: `UseCaseConfiguration` y `TestWiring`;
  - pruebas web: `AdminApiTest` e `IsolationTest`;
  - contrato y tipos.

**Interfaces que consume:** `DocumentRepository.markSuperseded`, `clearSuperseded`, `findEntry` y `list`, y `Document.MAX_SUPERSESSION_CHAIN` (tarea 2).

**Interfaces que produce:** `SupersedeDocument`, `UploadOutcome`, `Requests.SupersedeRequest`, `Responses.DocumentLink` y los campos nuevos de `DocumentView`, como en «Interfaces compartidas».

**Valores:**
- **`SupersedeDocument.mark`, en este orden (spec 5.1):**
  1. `AccessPolicy.requireAdmin(actor)`, que da 403 al trabajador. El documento, con `findById(actor.scope(), ProjectVisibility.all(), documentId)`, o 404 «Documento no encontrado», como `GetDocument`;
  2. 400 «Un documento no puede sustituirse a sí mismo» si `replacementId` es el propio documento;
  3. el sustituto, con `findById(CompanyScope.of(documento.companyId()), ProjectVisibility.all(), replacementId)`, o 404 «Documento que lo sustituye no encontrado». Es el mismo texto si no existe o si es de otra empresa, también para el administrador del grupo;
  4. 409 si no está `READY`, con el texto del spec 5.1, punto 4;
  5. 409 si tiene proyecto y no es el del documento, con el texto del punto 5;
  6. 409 si, siguiendo las marcas desde el sustituto con `findById` de la misma empresa y como mucho `Document.MAX_SUPERSESSION_CHAIN` saltos, se llega al documento, con el texto del punto 6.
  - Después, `markSuperseded(empresa, documento, sustituto, clock.instant())`. Si ya estaba marcado, cambian la marca y su fecha.
  - Devuelve el `DocumentEntry` del documento, leído con `findEntry` y la visibilidad de toda su empresa.
- **`unmark`:** el punto 1 y después `clearSuperseded`. No falla si no estaba marcado.
- `GetDocument`, `RetryDocument` y `ListCompanyDocuments` devuelven `DocumentEntry`. `RetryDocument` lo vuelve a leer con `findEntry` después de guardar.
- `UploadDocument`:
  - un documento nuevo sale como `new DocumentEntry(document, Optional.empty(), Optional.empty())`;
  - un duplicado se lee con `findEntry`, después de reencolarlo si había fallado.
- **Web:**
  - `PUT` con `Requests.SupersedeRequest`; sin `replacementId`, 400 «Indica el documento que lo sustituye»;
  - `DELETE` con `@ResponseStatus(HttpStatus.NO_CONTENT)`;
  - `DocumentView.from(DocumentEntry)`, con `supersededBy` del directo y `supersededAt` del documento. Es el único `from` de `DocumentView`.
- Panel: solo lo necesario para compilar con los tipos nuevos. Las pantallas van en la tarea 6.

- [ ] **Paso 1:** pruebas de aplicación; comprueba que fallan:
  - `SupersedeDocumentTest`, con los textos de 400, 404 y 409 comprobados literales:
    - `marksAndReturnsTheDocumentWithItsReplacement`;
    - `changingTheMarkPointsToTheNewReplacementAndDate`;
    - `unmarkClearsAndIsIdempotent`;
    - `unknownDocumentIsNotFound`;
    - `documentOfAnotherCompanyIsNotFound`;
    - `selfReplacementIsRejected`;
    - `unknownReplacementIsNotFound`;
    - `replacementOfAnotherCompanyIsNotFoundEvenForGroupAdmin`, con el mismo texto que la anterior;
    - `replacementNotReadyIsConflict`;
    - `replacementInAnotherProjectIsConflict`;
    - `replacementInSameProjectOrWithoutProjectIsAccepted`;
    - `notReadyIsReportedBeforeAnotherProject`;
    - `directAndIndirectCyclesAreConflicts`: B → A sobre A → B, y C → A sobre A → B → C;
    - `workerIsForbidden`, en `mark` y en `unmark`;
    - `groupAdminMarksInAnyCompany`.
  - `AdminDocumentsTest`: `getAndListCarryTheDirectReplacement` y `retryKeepsTheMark`;
  - `UploadDocumentTest.duplicateUploadKeepsTheMark`.
- [ ] **Paso 2:** implementa el caso de uso y los cambios de los demás.
- [ ] **Paso 3:** pruebas web; comprueba que fallan:
  - `AdminApiTest`:
    - `putSupersededByReturnsTheDocumentView`;
    - `deleteSupersededByIs204EvenIfNotMarked`;
    - `supersedeWithoutOrWithBadReplacementIdIs400`, con `{}` y con un identificador que no es un UUID;
    - `supersedeConflictIs409WithItsMessage`;
    - `documentViewCarriesSupersededByAndAt`, en la lista y en el detalle.
  - `IsolationTest`:
    - `otherCompanyAdminCannotMarkOrUnmarkForeignDocument`: 404, y la marca no cambia;
    - `companyAdminCannotUseAReplacementOfAnotherCompany`: 404.
- [ ] **Paso 4:** implementa la web y regenera el contrato y los tipos. `make check` y `make demo` terminan con 0.
- [ ] **Paso 5:** commit «feat: marcar y desmarcar un documento como sustituido».

### Tarea 4: Búsqueda, lectura y lista del trabajador, y MCP · Sonnet

Rama `task/fase-2-conocimiento/sustituidos-trabajador`. Spec 5.2, 6 y 7.

**Ficheros:**
- **Modificar:**
  - dominio: `SearchHit`;
  - aplicación: `ListDocuments`, `ReadPage` y `PageView`, con sus pruebas, y `SearchKnowledgeTest`, por el constructor de `SearchHit`;
  - PostgreSQL: `PostgresKnowledgeSearch` y `PostgresKnowledgeSearchIT`;
  - web: `WorkerController`, `Responses` y `WorkerApiTest`;
  - MCP: `mcp/src/tools/searchKnowledge.ts`, `mcp/src/server.ts` y sus pruebas (`mcp/test/server.test.ts`, o el fichero de cada herramienta si los bloques anteriores los separaron);
  - contrato y tipos.

**Interfaces que consume:** `DocumentEntry`, `DocumentRef`, `SupersessionSql`, `findEntry` y `list` (tarea 2).

**Interfaces que produce:** los campos `superseded` y `replacement` de `SearchHit`, `Hit`, `Page` y `WorkerDocumentView`, y `Responses.Replacement`, como en «Interfaces compartidas».

**Valores:**
- `SearchHit` gana `superseded` y `replacement`. Una ficha va con `false` y vacío.
- **`PostgresKnowledgeSearch`:**
  - los candidatos de documento llevan `d.superseded_by`, y los de ficha `NULL::uuid`;
  - la consulta de hoy, con su `ORDER BY rank DESC, title, page NULLS FIRST, id LIMIT :limit`, pasa a ser una subconsulta;
  - la consulta exterior añade `SupersessionSql.joins` sobre su `superseded_by` y ordena por `superseded_by IS NOT NULL` y después por las mismas claves de antes;
  - `superseded` es `superseded_by` no nulo, y `replacement` sale de `SupersessionSql.current`.
- `SearchKnowledge` no cambia: ya pasa `access.content()`.
- `ListDocuments` devuelve `List<DocumentEntry>`, y `ReadPage` lee con `findEntry` en lugar de `findById`, los dos con `access.content()`.
- Web: `Hit`, `Page` y `WorkerDocumentView` ganan `superseded` y `replacement`, que es `Replacement { documentId, title }` o nulo.
- **MCP:**
  - `searchKnowledge` añade `superseded` y `replacement` (o `null`) a los resultados de documento. Las fichas no los llevan;
  - `readPage` y `listDocuments` ya devuelven el JSON de la API y no cambian; sus pruebas fijan los dos campos;
  - `INSTRUCTIONS` gana, en este orden, las dos frases del spec 7, literales.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `PostgresKnowledgeSearchIT`:
    - `supersededHitGoesBehindCurrentOnesEvenWithBetterRank`: el viejo lleva un identificador exacto (rango de 100 o más) y aun así sale detrás del nuevo, con `superseded` y `replacement`;
    - `topFiveAreTheSameWithAndWithoutMarks`: siete candidatos y límite 5; el conjunto de los 5 no cambia al marcar dos de ellos;
    - `supersededAndCurrentKeepTheirRelativeOrder`;
    - `replacementIsTheEndOfTheChain`;
    - `replacementIsNullWhenTheWorkerCannotSeeIt`: el viejo sin proyecto y el sustituto en un proyecto cerrado, con la marca puesta con el repositorio;
    - `restrictedProjectDocumentsStayInvisibleMarkedOrNot`;
    - `cardsAreNeverSupersededAndStayAmongCurrent`.
  - `ListDocumentsTest.listsSupersededWithItsReplacement` y `ReadPageTest.pageCarriesSupersededAndReplacement`;
  - `WorkerApiTest`: `searchHitCarriesSupersededAndReplacement`, `pageCarriesSupersededAndReplacement` y `workerListingCarriesSupersededAndReplacement`;
  - MCP:
    - `searchMapsSupersededAndReplacementForDocumentsOnly`;
    - `readPageAndListDocumentsPassSupersededAndReplacement`;
    - `instructionsTellHowToTreatSupersededAndConflictingSources`, con las dos frases literales.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** regenera el contrato y los tipos. `make check` y `make demo` terminan con 0, y las líneas de resultado de las dos baterías de búsqueda (aciertos y fallos) no cambian.
- [ ] **Paso 4:** commit «feat: los documentos sustituidos salen detrás y marcados en la búsqueda, la lectura y la lista».

### Tarea 5: Las marcas en las baterías de búsqueda · Sonnet

Rama `task/fase-2-conocimiento/baterias-sustituidos`. Spec 8.

**Ficheros:**
- **Crear:** `SupersessionCheck.java` y `SupersessionCheckTest`, junto al cargador compartido del bloque 6.
- **Modificar:** el cargador compartido (`CorpusLoader`), `SearchBatteryIT` y `SearchScaleBatteryIT`.

**Interfaces que consume:** `DocumentRepository.markSuperseded` (tarea 2), `SearchHit.superseded` y `replacement` (tarea 4) y los `sustituciones.json` (tarea 1).

**Interfaces que produce:**

```java
// CorpusLoader gana:
Map<String, String> applySupersessions(CompanyId company, Path supersessionsFile); // clave del viejo → clave del sustituto
// apoyo de pruebas
final class SupersessionCheck {
    static List<String> problems(String questionId, List<SearchHit> hits, Map<String, String> supersededBy);
}
```

**Valores:**
- El cargador aplica `sustituciones.json` si está en la carpeta de la batería, después de cargar el corpus, con `markSuperseded` y la hora fija de las pruebas.
  - Si una clave no está en el corpus, para con «La pareja <viejo> → <nuevo> no está en el corpus».
  - La batería de respuestas usa el mismo cargador, así que también carga las marcas.
- `SupersessionCheck.problems` devuelve un texto, con la pregunta y el título, por cada:
  - resultado sustituido que sale delante de uno vigente;
  - resultado de un documento de `sustituciones.json` sin `superseded`, o con un `replacement` cuyo título no es la clave del sustituto;
  - resultado con `superseded` que no está en `sustituciones.json`.
- Cada batería junta los problemas de todas sus consultas y falla al final si hay alguno, con la lista en el mensaje. La cuenta y el umbral siguen igual.

- [ ] **Paso 1:** `SupersessionCheckTest`; comprueba que falla:
  - `acceptsCurrentFirstAndMarkedSuperseded`;
  - `reportsSupersededBeforeCurrent`;
  - `reportsMissingMarkOrWrongReplacement`;
  - `reportsUnexpectedMark`.
- [ ] **Paso 2:** implementa la comprobación y la carga de las marcas, y llama a la comprobación desde las dos baterías.
- [ ] **Paso 3:** ejecuta las dos baterías. Sus líneas de resultado (aciertos, total y fallos) son las mismas que antes del bloque, y no hay problemas de orden. Copia al informe del bloque las líneas de antes y de ahora.
- [ ] **Paso 4:** `make check` y `make demo` terminan con 0. Commit «test: las baterías de búsqueda cargan las marcas de sustitución y comprueban el orden».

### Tarea 6: Panel y demostración · Sonnet

Rama `task/fase-2-conocimiento/sustituidos-panel`. Spec 5.3 y 9.

**Ficheros:**
- **Crear:**
  - `panel/src/components/SupersedeDialog.tsx` y `panel/src/components/DocumentDetail.tsx`, con sus estilos si hacen falta;
  - `deploy/demo/poliza-ficticia-renovada.txt`.
- **Modificar:**
  - panel: `panel/src/pages/Documents.tsx`, `panel/src/pages/Documents.test.tsx` (créala si no existe) y `panel/src/api/client.ts`;
  - demostración: `deploy/demo/run-demo.sh`, `mcp/scripts/demo-client.ts` y `panel/e2e/demo.spec.ts`.

**Interfaces que consume:** las rutas de administración (tarea 3) y `searchKnowledge` con `superseded` y `replacement` (tarea 4).

**Valores:**
- `client.ts`: `supersede(documentId, replacementId)` hace el `PUT`, y `unsupersede(documentId)`, el `DELETE`.
- **Fila de la lista:**
  - si tiene `supersededBy`, bajo el título van un `StatusPill tone="off"` «Sustituido» y el texto «por <título del sustituto>»;
  - `RowActions` «Más acciones de <título>», con:
    - «Ver detalle», con nombre accesible «Ver detalle de <título>»;
    - «Marcar como sustituido…», con nombre «Marcar como sustituido <título>»;
    - «Quitar marca de sustituido», con nombre «Quitar marca de sustituido de <título>», solo si está marcado. Quita la marca sin otro diálogo y recarga la lista.
  - «Reintentar» se queda como está.
- **`SupersedeDialog`**, un `FormDialog` «Marcar como sustituido»:
  - la ayuda del spec 5.3, literal;
  - un campo «Buscar por título», que filtra sin distinguir mayúsculas la lista ya cargada;
  - un grupo de opciones «Documento que lo sustituye»: los `READY` de la empresa menos el propio, cada uno como «<título> · <proyecto> · <fecha de alta>», con `projectName` y `formatDate` de `reviewNames.ts` y los proyectos de `api.projects`;
  - sin coincidencias, «Ningún documento listo coincide con la búsqueda.»;
  - al elegir uno, sale la confirmación del spec 5.3, literal, y el botón «Marcar como sustituido»;
  - un error de la API, como los 409, sale en un `Notice` dentro del diálogo, que sigue abierto.
- **`DocumentDetail`**, un `FormDialog` con el título del documento: estado, proyecto y fecha de alta; si está marcado, «Sustituido por <título>» y «Marcado el <fecha>». El bloque 4 le añadirá los temas.
- **Demostración:**
  - `poliza-ficticia-renovada.txt`: póliza ficticia de una página, número `ATL-5120-2026`, con «franquicia de 300 euros por siniestro» para daños por agua. No lleva `ATL-4417-2024`: así la antigua puntúa mejor por el identificador y aun así sale detrás;
  - `run-demo.sh` exporta `DEMO_RENEWED` con su ruta;
  - `demo-client.ts`:
    - `uploadPdf` pasa a ser `uploadFile(companyId, path, title, projectId?)`, sin duplicar la subida;
    - `prepare`, «Paso 7d»: sube la renovada a la empresa A con el título «Póliza ficticia renovada» y espera `READY`. Guarda en el estado la clave del trabajador de A y los dos identificadores;
    - `confirm`, «Paso 10»: `searchKnowledge` con la consulta de la demostración. Ningún resultado sustituido sale delante de uno vigente, la renovada sale, y cada resultado de la antigua lleva `superseded: true` y `replacement` con el identificador y el título de la renovada.
  - `demo.spec.ts`, «paso 8b», contra la API real: el administrador entra, abre «Empresa A», va a «Documentos» y marca «Póliza ficticia» como sustituida por «Póliza ficticia renovada». Ve la confirmación literal y, al aceptar, la fila con «Sustituido» y «por Póliza ficticia renovada».

- [ ] **Paso 1:** pruebas; comprueba que fallan. `Documents.test.tsx`:
  - `showsSupersededMarkWithReplacementTitle`;
  - `candidatesAreReadyDocumentsWithProjectAndDateFilteredByTitle`;
  - `marksAsSupersededAfterTheConfirmationText`, que comprueba el cuerpo `{ replacementId }`;
  - `conflictMessageStaysInTheOpenDialog`;
  - `removesTheMark`;
  - `detailShowsReplacementAndMarkDate`.
- [ ] **Paso 2:** implementa el panel y la demostración.
- [ ] **Paso 3:** `make demo` dos veces seguidas y `make check` terminan con 0.
- [ ] **Paso 4:** commit «feat: documentos sustituidos en el panel y en la demostración».

### Cierre del bloque · CTO

- Revisión final de toda la rama con Opus, con este foco:
  - las dos barreras en el sustituto y en cada salto de la cadena;
  - que la marca no entra en `save` ni en el `upsert`;
  - que el SQL de la cadena solo está en `SupersessionSql`.
- `make check` y `make demo`, ejecutados por el CTO.
- Baterías de búsqueda: las mismas cifras y los mismos fallos que al cerrar el bloque 6, sin problemas de orden.
- **Batería de respuestas (lo ejecuta el CTO):** `make answer-battery`, con todos los juegos y los dos modelos.
  - Se compara con la última medición del bloque 6 y con la medición «antes» de la tarea 1.
  - Ningún umbral empeora: ni el porcentaje ni las cuentas aparte de ningún juego y modelo.
  - E1 da la franquicia de 150 euros de la póliza de 2024, en los dos corpus y con los dos modelos.
  - Los juegos de sustituidos dan su propia cuenta, fuera del mínimo.
  - Si algo empeora, el bloque no se cierra y el informe lo analiza.
- Plan del bloque 2: el CTO comprueba que `SimilarCaseView` lleva `superseded` y `replacement` con el mismo orden (spec 5.2), con `SupersessionSql`.
- Informe del bloque en `docs/informes/`, con el antes y el después. Entrada en el registro técnico del repositorio OCR, con el siguiente número libre. Copia del informe en la carpeta de notas para el CEO.

## Orden y paralelismo

- La 1, primero: su medición «antes» se hace con el Odín anterior a la tarea 2.
- La 2, la 3 y la 4, en serie: cada una usa el modelo o el contrato de la anterior, y la 3 y la 4 tocan `api/openapi.json`.
- La 5 y la 6 pueden ir en paralelo después de la 4: no comparten ficheros.

## Modelos de los revisores

- **Opus:** tareas 2, 3 y 4, que tocan aislamiento, seguridad por filas y proyectos cerrados.
- **Sonnet:** tareas 1, 5 y 6.

## Decisiones del plan

El spec no cambia. Esto es lo que el plan decide donde el spec lo deja abierto o el código pide una forma:

1. **Lectura con sustituciones (`DocumentEntry`).** El vigente depende de quien lee, así que no va en `Document`. El repositorio lo resuelve en la misma consulta (`findEntry` y `list`), y `findById` reutiliza esa consulta.
2. **Dos referencias con nombres distintos,** como fija el contrato del spec 6: el panel ve el sustituto directo (`supersededBy { id, title }`, `DocumentLink`) y el trabajador, el vigente del final (`replacement { documentId, title }`, `Replacement`).
3. **La cadena en SQL** es un `WITH RECURSIVE` con las dos barreras en cada salto y un tope de 50 saltos (`Document.MAX_SUPERSESSION_CHAIN`). Si se corta o no termina, el vigente es nulo.
4. **La cadena circular se detecta en el caso de uso,** recorriendo las marcas con `findById`. Dos marcas cruzadas a la vez no se bloquean; el tope evita que una cadena circular cuelgue las lecturas. Coste si es erróneo: un bloqueo por empresa al marcar.
5. **Textos que el spec no fija:** 400 «Un documento no puede sustituirse a sí mismo», 400 «Indica el documento que lo sustituye» y 404 «Documento que lo sustituye no encontrado».
6. **La subida duplicada y el reintento también devuelven la marca,** porque usan el mismo `DocumentView`: `UploadOutcome` y `RetryDocument` pasan a `DocumentEntry`.
7. **Un caso de uso con dos métodos,** `SupersedeDocument.mark` y `unmark`, como `ReviewDocumentTopics` del bloque 3.
8. **El doble en memoria imita al `upsert`:** `save` conserva la marca guardada, para que las pruebas de aplicación vean lo mismo que PostgreSQL.
9. **Panel:**
   - la confirmación sale en el mismo diálogo al elegir el sustituto, sin otro diálogo encima;
   - los candidatos salen de la lista ya cargada;
   - se crea `DocumentDetail`, porque aún no hay detalle de documento. El bloque 4 le añadirá los temas.
10. **Demostración:** la póliza renovada es un `.txt` ficticio sin el identificador de la antigua. La marca se pone desde el panel (paso 8b) y se comprueba con el MCP en `confirm`.
11. **Medición:**
    - `sustituciones.json` y `bateria_sustituidos.json` se crean en la tarea 1, para medir el «antes» con la comprobación nueva;
    - el cargador aplica las marcas solo desde la tarea 5;
    - la pregunta nueva es S1, con la palabra «sustituida» también en sus claves.
12. **Corpus a escala:** 31 parejas explícitas, Los Almendros y sus 30 copias, sacadas una vez del corpus.
13. **Nombres del bloque 6:** aquí el cargador se llama `CorpusLoader`, y los juegos, `sustituidos` y `sustituidos-escala`. Si el plan del bloque 6 usa otros, se siguen los suyos.
14. **Orden en la búsqueda:** la consulta de hoy, con su `LIMIT`, va en una subconsulta, y el orden exterior pone los sustituidos detrás. Es la primera forma del spec 11.
