# Odín · Fase 2, bloque 7: documentos sustituidos — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que el administrador marque que un documento sustituye entero a otro, y que la búsqueda, la lectura y la lista del trabajador den el viejo detrás y marcado, con el vigente que lo sustituye, sin saltarse empresas ni proyectos cerrados.

**Arquitectura:**
- La marca va en el documento viejo (`supersededBy` y `supersededAt`) y se escribe con su propia orden, nunca con `save`.
- Al leer, el repositorio y la búsqueda resuelven en la misma consulta el sustituto directo y el vigente del final de la cadena, con el filtro explícito y bajo la seguridad por filas. El SQL de la cadena está en una sola pieza compartida.
- Un caso de uso, `SupersedeDocument`, hace las comprobaciones del spec 5.1 en su orden.
- La búsqueda elige por relevancia como hoy y después pone los sustituidos detrás.
- La batería de respuestas del archivo ficticio (bloque 6) mide el antes y el después con Haiku. Las marcas salen de las contradicciones de tipo `version` de ese archivo.

**Tecnología:** Java 21, Spring Boot 4.1.2, PostgreSQL 17.10 con Flyway y seguridad por filas, TypeScript con el SDK de MCP, React 19, Vitest y Playwright.

**Diseño:** `docs/superpowers/specs/2026-11-11-documentos-sustituidos-design.md` (aquí, «spec»), que amplía el spec base y el de la 2a. Se mide con la batería del archivo del encargo 12 (informe `docs/informes/2026-11-12-medicion-archivo-ficticio.md`, `experimentos/2026-11-12-archivo-ficticio/`), según `2026-11-11-datos-correctos-medidos-design.md` («spec 6»), cuyo apartado 14 (D-052) manda, y el plan del bloque 6. Quien ejecute una tarea lee el spec y, en las tareas 1 y 5, también el apartado 14 del spec 6 y el plan del bloque 6.

## Cuándo se ejecuta

- Justo después del bloque 6 y antes de los bloques 1 y 3 (orden de D-052, `2026-11-11-fase-2-orden.md`). Va cuando lo diga el CEO; el bloque 7 está validado (D-050, con los cambios de D-052).
- `feature/fase-2-conocimiento` solo tiene hecho el bloque 6. Este plan no da por hechos los bloques 1 y 3: ni lector OCR, ni temas, ni filtro por tema, ni `DescribeMe` del bloque 3.
- **Lo que encuentra hecho del bloque 6** (spec 6, apartado 14):
  - el MCP con `listProjects`, `projectName` y `addedAt` (en lugar de `date`) en `searchKnowledge`, `readPage` y `listDocuments`, y las reglas de cita y de fuente parcial en `INSTRUCTIONS`. La API no cambió: el MCP saca `projectName` de `GET /v1/me`;
  - la batería de respuestas del archivo (`make answer-battery`): el ejecutor, el puntuador de Python con «sustituido presentado como vigente» y «no consta» falsos aparte, y el cargador de la pila (`pila/cargar.py`);
  - la batería de búsqueda determinista del archivo, en `make check`, con su umbral fijado por la primera medición, y el cargador de Java compartido con `SearchBatteryIT`. Sustituye a `SearchScaleBatteryIT`;
  - la medición completa con Haiku del cierre del bloque 6: es la medición «antes» de este bloque, sin marcas.
- **Nombres que dejan los bloques 1 y 3:** ninguno. `Document` no lleva `pagesWithoutText`, y `ListCompanyDocuments`, `DocumentView` y `ListDocuments` no llevan temas. `DocumentEntry` ocupa el lugar de `Document` en lo que devuelven. Los bloques 1 y 3, que van después, añadirán lo suyo sobre `DocumentEntry`.
- Si el plan del bloque 6 nombra algo de otra manera (cargador de Java, clase de la batería de búsqueda del archivo), se sigue su nombre. Si algo no encaja, el CTO corrige este plan antes de la tarea afectada.

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
- **Cierre de cada tarea:** `make check` y `make demo` terminan con 0, `SearchBatteryIT` mantiene 27 de 30 y la batería de búsqueda del archivo (bloque 6) cumple su umbral, sin ningún sustituido delante de un vigente.
- **Dónde se trabaja:** en `.worktrees/fase-2` (`feature/fase-2-conocimiento`). No se ejecutan `make up` ni `make down` y no se toca `deploy/.env`. Nada global.
- **Git:**
  - cada tarea en su rama `task/fase-2-conocimiento/<tarea>`;
  - commits en español, sin atribución a Claude ni `Co-Authored-By`;
  - sin push;
  - el merge `--no-ff` lo hace el CTO.
- **Migración:** `V11__superseded_documents.sql` (la 2a terminó en V10 y el bloque 6 no migra). Los bloques 1 y 3, que van después, toman V12 y V13.
- **Panel:** con los componentes y las reglas de la dirección visual (D-039): `FormDialog`, `Field`, `StatusPill`, `RowActions`, `Notice` y `Button`.
- **Datos:** solo ficticios. El archivo ficticio se genera con su generador: no se editan a mano `datos/bateria.json` ni `datos/claves.json`.

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

### Tarea 1: Las parejas viejo → vigente del archivo, medidas antes del cambio · Sonnet

Rama `task/fase-2-conocimiento/medicion-sustituidos`. Spec 8 y spec 6, apartado 14.

No se crean juegos nuevos ni `sustituciones.json`: las marcas salen de las contradicciones de tipo `version` de `datos/claves.json` del archivo (47 en el del encargo 12), con su pareja viejo → vigente.

**Ficheros** (todo en `experimentos/2026-11-12-archivo-ficticio/`):
- **Crear:** `pila/sustituciones.py` y su prueba.
- **Modificar, solo si hace falta:** `generar/generar.py`, con `datos/` regenerado, para la pregunta nueva; y el puntuador (`medir/puntuar.py`) y su prueba, si la comprobación no cubre lo de abajo.

**Interfaces que consume:** `datos/claves.json`, `datos/manifiesto.json` y `datos/bateria.json`; el puntuador, que ya cuenta «sustituido presentado como vigente» con el campo `sustituidos` de cada pregunta.

**Interfaces que produce:**

```python
# pila/sustituciones.py
def parejas(claves: list[dict], manifiesto: dict) -> list[tuple[str, str]]:
    # (ruta del viejo, ruta del vigente) por cada entrada de tipo `version`. El viejo es la fuente `doc`
    # distinta de `vigente.doc`. Las rutas son las de `manifiesto.json`.
    ...
```

**Valores:**
- **Cada pareja tiene que poder marcarse con `PUT .../superseded-by`** (spec 5.1): las dos de la misma empresa, el sustituto en el mismo proyecto que el viejo o sin proyecto, y las dos `READY` (en el archivo, todas lo son). Una pareja que no cumpla se lista en el informe y se deja fuera de las marcas; no se fuerza.
- **Comprobación del puntuador:**
  - cada pregunta de tipo `version` lleva en `sustituidos` el viejo de su entrada de `claves.json`. Si falta alguno, se corrige en el generador, no a mano;
  - la palabra se busca en toda la respuesta, con la normalización del puntuador. Ya está hecho y validado con las cifras del estudio: aquí solo se comprueba que lo cubre.
- **Pregunta nueva, solo si hace falta:** si ninguna pregunta de la batería pide el valor del documento viejo (por ejemplo, la franquicia de la póliza antigua), se añade al generador, no a un juego aparte. Lleva cita del documento viejo, la clave con el valor viejo y la palabra «sustituida» o «sustituido». El CTO fija cuántas al ver el paso 1; si ya hay, no se añade ninguna.
  - Añadir preguntas cambia el total de 216. Las comparaciones con la medición de partida (146/216) se hacen sobre las 216 originales, por identificador.

- [ ] **Paso 1:** pruebas de `sustituciones.py` y del puntuador; comprueba que fallan:
  - `oneCouplePerVersionEntry`: tantas parejas como entradas `version`, con viejo distinto del vigente;
  - `couplesPointToManifestDocuments`;
  - `couplesAreMarkable`: misma empresa y mismo proyecto o sin proyecto el vigente;
  - `versionQuestionsListTheOldDocumentAsSuperseded`;
  - `supersededCitedWithoutTheWordIsCounted` y `sustituidaOrSustituidoAnywhereIsAccepted` (con «Sustituida» y «SUSTITUIDO»), si el puntuador aún no las tiene.
- [ ] **Paso 2:** implementa `sustituciones.py` y los cambios del generador y del puntuador que salgan de las pruebas.
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0.
- [ ] **Paso 4:** commit «test: parejas de documentos sustituidos del archivo ficticio y su comprobación».
- [ ] **Paso 5 (lo ejecuta el CTO):** la medición «antes» es la última medición completa con Haiku del cierre del bloque 6, sin marcas, si el Odín no ha cambiado desde entonces. Si ha cambiado, se repite (unos 14 minutos). Si la pregunta nueva existe, se mide con ella. Se anotan el porcentaje, los sustituidos presentados como vigentes (7 en la medición de partida, con 146/216) y los «no consta» falsos (35 en la de partida). Los resultados se versionan con el commit «test: medición de partida del bloque de sustituidos».

### Tarea 2: La marca en el documento: modelo, migración y repositorio · Sonnet

Rama `task/fase-2-conocimiento/marca-sustituido`. Spec 2, 4 y 5.2.

**Ficheros:**
- **Crear:**
  - `api/odin-adapter-postgres/src/main/resources/db/migration/V11__superseded_documents.sql`;
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
- `V11`, o la siguiente libre:
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
  - Prueba de migración, `existingDocumentsAreNotSuperseded`: un documento insertado antes de `V11` sale sin marca.
- [ ] **Paso 4:** escribe `V11`, `SupersessionSql` y el repositorio.
- [ ] **Paso 5:** `make check` y `make demo` terminan con 0; `SearchBatteryIT` da 27 de 30 y la batería de búsqueda del archivo cumple su umbral.
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
  - MCP: `mcp/src/tools/searchKnowledge.ts`, `mcp/src/server.ts` y sus pruebas (`mcp/test/server.test.ts`, o el fichero de cada herramienta si el bloque 6 los separó);
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
  - `searchKnowledge` añade `superseded` y `replacement` (o `null`) a los resultados de documento, junto a los `projectName` y `addedAt` que ya puso el bloque 6. Las fichas no los llevan;
  - `readPage` y `listDocuments` devuelven el JSON de la API con lo que el bloque 6 les añadió (`projectName`, `addedAt`): no cambian, y sus pruebas fijan los dos campos nuevos sin tocar los del 6;
  - `INSTRUCTIONS` gana las dos frases del spec 7, literales, detrás de las del bloque 6 (filtrar por proyecto con `listProjects`, reglas de cita y fuente parcial). Las frases del 6 no se tocan.

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
- [ ] **Paso 3:** regenera el contrato y los tipos. `make check` y `make demo` terminan con 0, y las líneas de resultado de `SearchBatteryIT` y de la batería de búsqueda del archivo (aciertos y fallos) no cambian.
- [ ] **Paso 4:** commit «feat: los documentos sustituidos salen detrás y marcados en la búsqueda, la lectura y la lista».

### Tarea 5: Las marcas en el archivo y en su batería de búsqueda · Sonnet

Rama `task/fase-2-conocimiento/baterias-sustituidos`. Spec 8 y spec 6, apartado 14.

**Ficheros:**
- **Crear:** `SupersessionCheck.java` y `SupersessionCheckTest`, junto al cargador de Java compartido del bloque 6.
- **Modificar:**
  - el cargador de Java compartido (hoy `ArchiveLoader` en el plan del 6), la batería de búsqueda del archivo y, si la usan, `SearchBatteryIT`;
  - `experimentos/2026-11-12-archivo-ficticio/pila/cargar.py`.

**Interfaces que consume:** `DocumentRepository.markSuperseded` (tarea 2), `SearchHit.superseded` y `replacement` (tarea 4), la ruta `PUT /v1/admin/documents/{documentId}/superseded-by` (tarea 3) y `parejas` de `pila/sustituciones.py` (tarea 1).

**Interfaces que produce:**

```java
// cargador de Java compartido, gana:
Map<String, String> applySupersessions(CompanyId company, Path claves); // ruta del viejo → ruta del vigente
// apoyo de pruebas
final class SupersessionCheck {
    static List<String> problems(String questionId, List<SearchHit> hits, Map<String, String> supersededBy);
}
```

```python
# pila/cargar.py gana la opción --marcar: aplica las parejas de sustituciones.parejas con el PUT
```

**Valores:**
- **Las marcas son las del archivo:** las parejas viejo → vigente de las entradas `version` de `datos/claves.json`, las mismas en Java y en Python.
  - `cargar.py --marcar` las pone con el `PUT .../superseded-by` con la sesión de administrador que ya usa la carga. Si responde 409 o 404, para con «La pareja <viejo> → <vigente> no se puede marcar: <mensaje>». La medición «sin marcas» es la carga sin `--marcar`.
  - Solo si hiciera falta medir sin la ruta, las marcas pueden ponerse por SQL (`UPDATE document SET superseded_by, superseded_at`), con el mismo resultado.
  - El cargador de Java las aplica después de cargar el corpus, con `markSuperseded` y la hora fija de las pruebas. Si una ruta no está en el corpus, para con «La pareja <viejo> → <vigente> no está en el corpus».
- `SupersessionCheck.problems` devuelve un texto, con la pregunta y el título, por cada:
  - resultado sustituido que sale delante de uno vigente;
  - resultado de un documento viejo de las parejas sin `superseded`, o con un `replacement` cuyo título no es el del vigente;
  - resultado con `superseded` que no está en las parejas.
- La batería de búsqueda del archivo (con el filtro de proyecto de cada pregunta) junta los problemas de todas sus consultas y falla al final si hay alguno, con la lista en el mensaje. Es la comprobación nueva: ningún sustituido sale delante de un vigente. La cuenta y el umbral siguen igual.
- No hay `sustituciones.json` ni juego aparte.

- [ ] **Paso 1:** `SupersessionCheckTest`; comprueba que falla:
  - `acceptsCurrentFirstAndMarkedSuperseded`;
  - `reportsSupersededBeforeCurrent`;
  - `reportsMissingMarkOrWrongReplacement`;
  - `reportsUnexpectedMark`.
- [ ] **Paso 2:** implementa la comprobación y la carga de las marcas en Java y en `cargar.py --marcar`, y llama a la comprobación desde la batería de búsqueda del archivo.
- [ ] **Paso 3:** ejecuta la batería de búsqueda del archivo, y `SearchBatteryIT` si carga marcas. Sus líneas de resultado (aciertos, total y fallos) son las mismas que antes del bloque, y no hay problemas de orden. Copia al informe las de antes y las de ahora.
- [ ] **Paso 4:** `make check` y `make demo` terminan con 0. Commit «test: la batería de búsqueda del archivo carga las marcas de sustitución y comprueba el orden».
- [ ] **Paso 5 (lo ejecuta el CTO):** pila `odin-medicion` con `cargar.py --marcar` y `make answer-battery` con Haiku completo. Es la medición «después». Se compara con la «antes» de la tarea 1 y con la de partida (146/216, 7 sustituidos como vigentes). Se versiona con el commit «test: medición del bloque de sustituidos, con marcas».

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
- Baterías de búsqueda: las mismas cifras y los mismos fallos que al cerrar el bloque 6, y ningún sustituido delante de un vigente.
- **Batería de respuestas (lo ejecuta el CTO):** `make answer-battery` con Haiku completo, con marcas (tarea 5, paso 5).
  - Se compara con la medición «antes» (sin marcas, tarea 1) y con la de partida (146/216, 7 sustituidos presentados como vigentes).
  - Ningún umbral empeora: ni el porcentaje ni los fallos graves (los «no consta» falsos y los sustituidos presentados como vigentes).
  - Los sustituidos presentados como vigentes bajan de 7; el objetivo es 0, y lo que quede se analiza pregunta a pregunta en el informe.
  - La pregunta nueva, si se añadió, da su propio resultado.
  - Sonnet, en la muestra de 30 preguntas, sin empeorar. La completa con Sonnet es del cierre de la recuperación.
  - Si algo empeora, el bloque no se cierra y el informe lo analiza.
- Plan del bloque 2: el CTO comprueba que `SimilarCaseView` lleva `superseded` y `replacement` con el mismo orden (spec 5.2), con `SupersessionSql`.
- Informe del bloque en `docs/informes/`, con la partida, el antes y el después. Entrada en el registro técnico del repositorio OCR, con el siguiente número libre. Copia del informe en la carpeta de notas para el CEO.

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
7. **Un caso de uso con dos métodos,** `SupersedeDocument.mark` y `unmark` (el bloque 3, que va después, seguirá el mismo patrón con `ReviewDocumentTopics`).
8. **El doble en memoria imita al `upsert`:** `save` conserva la marca guardada, para que las pruebas de aplicación vean lo mismo que PostgreSQL.
9. **Panel:**
   - la confirmación sale en el mismo diálogo al elegir el sustituto, sin otro diálogo encima;
   - los candidatos salen de la lista ya cargada;
   - se crea `DocumentDetail`, porque aún no hay detalle de documento. El bloque 4 le añadirá los temas.
10. **Demostración:** la póliza renovada es un `.txt` ficticio sin el identificador de la antigua. La marca se pone desde el panel (paso 8b) y se comprueba con el MCP en `confirm`.
11. **Medición (D-052):**
    - la batería es la del archivo del encargo 12, no los juegos antiguos. No hay `bateria_sustituidos.json` ni `sustituciones.json`;
    - las marcas son las parejas viejo → vigente de las contradicciones de tipo `version` de `datos/claves.json`; las aplican `cargar.py --marcar` (con el `PUT`) y el cargador de Java, solo desde la tarea 5;
    - «sustituido presentado como vigente» ya lo cuenta el puntuador del archivo. Una pregunta nueva, si hace falta, va al generador;
    - tres cifras: la de partida (146/216, 7 sustituidos como vigentes), la «antes» (sin marcas, cierre del bloque 6) y la «después» (con marcas), con Haiku completo.
12. **Parejas:** salen de `claves.json`, sin lista aparte. Una que no se pueda marcar con las reglas del spec 5.1 se lista y se deja fuera.
13. **Nombres del bloque 6:** el cargador de Java compartido y la clase de la batería de búsqueda del archivo son los del plan del bloque 6. Si usa otros, se siguen los suyos.
14. **Orden en la búsqueda:** la consulta de hoy, con su `LIMIT`, va en una subconsulta, y el orden exterior pone los sustituidos detrás. Es la primera forma del spec 11.

## Revisión tras D-052 (2026-11-12)

- **Orden:** el bloque 7 va justo después del 6 y antes de los bloques 1 y 3. Se quitó todo lo que daba por hechos el lector (`pagesWithoutText`), los temas, el filtro por tema y `DescribeMe`. Encuentra hecho solo el bloque 6: `listProjects`, `projectName` y `addedAt` en el MCP, las reglas de cita, `make answer-battery` y la batería de búsqueda del archivo.
- **Migración:** `V11__superseded_documents.sql`; los bloques 1 y 3 pasan a V12 y V13. No queda ninguna mención a V13 como de este bloque.
- **Medición con el archivo del encargo 12:** la tarea 1 ya no crea `bateria_sustituidos.json` ni los `sustituciones.json`. Las marcas salen de las contradicciones de tipo `version` de `claves.json` (viejo → vigente) y las aplican `cargar.py --marcar` (con el `PUT`) y el cargador de Java.
- **Tres cifras con Haiku completo:** partida (146/216, 7 sustituidos como vigentes), antes (sin marcas) y después (con marcas). La pregunta nueva, si hace falta, se añade al generador del archivo.
- **Búsqueda:** `SearchScaleBatteryIT` y `search-battery-scale` pasan a la batería de búsqueda del archivo del bloque 6, con la comprobación nueva de que ningún sustituido sale delante de un vigente.
- **MCP:** la tarea 4 respeta `projectName`, `addedAt` y las instrucciones del bloque 6, y añade sus dos frases detrás.
- **Pendiente de otros planes:** los nombres del cargador de Java y de la clase de la batería de búsqueda del archivo los fija el plan del bloque 6, que se reescribe a la vez.
