# Casos como expediente: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que el trabajador, desde su IA, vea, lea, analice, ordene y amplíe el expediente de un caso, y abra el original de cualquier documento, sin romper el aislamiento.

**Architecture:**
- Hexagonal, como el resto de la API.
  - Dominio: `casefile` y `link`.
  - Casos de uso: `application.casefile` y `application.link`.
  - Adaptadores: PostgreSQL y web.
- Una tabla de relación (`case_file_entry`) y un historial de solo añadir (`case_file_event`), con doble barrera: filtro SQL y RLS heredada de los dos documentos.
- Enlaces de corta duración como capacidad al portador (`access_link`, solo se guarda el hash), con guarda de red local y atados al primer equipo que los abre.
- El MCP añade 6 herramientas y el panel, la pantalla del caso y la página pública de subida.

**Tech Stack:**
- Java 21 y Spring Boot (solo en adaptadores), PostgreSQL 17 con RLS y Flyway.
- MCP en TypeScript con `@modelcontextprotocol/sdk` y zod.
- Panel en React con Vitest y Playwright (axe).
- Experimento en Python 3, solo con la biblioteca estándar.

**Spec:** `docs/superpowers/specs/2026-11-13-casos-como-expediente-design.md`. Diseño de negocio: `docs/business-digital-mind/encargos/2026-11-12-casos-como-expediente.md` (repositorio OCR), D-074, D-075 y D-076.

## Global Constraints

- **Ramas:** cada tarea en `task/casos-expediente/<tarea>`, que sale de `feature/casos-expediente`. Al terminar, merge `--no-ff` a la feature. Sin push. Sin `Co-Authored-By` ni firmas de Claude.
- **Idiomas:** commits y documentación en español; código e identificadores en inglés y camelCase. Los textos para el usuario, en español.
- **Código:** métodos de 30 líneas como máximo, TDD (`superpowers:test-driven-development`), Clean Code y SOLID, comentarios mínimos. Antes de escribir algo nuevo, se busca si ya existe en el proyecto.
- **Migración:** una sola, `V24__case_files.sql`. No se edita ninguna migración desplegada (V1 a V13 y V20 a V23).
- **Enlaces:** 15 minutos de duración (`ODIN_LINK_TTL`, por defecto `PT15M`), 20 ficheros como máximo por enlace de subida y token aleatorio de 256 bits.
  - La URL solo lleva el token: `{ODIN_PUBLIC_URL}/subir/{token}` y `{ODIN_PUBLIC_URL}/v1/originals/{token}#page=N`.
- **Texto del expediente por tandas:** 30.000 caracteres por tanda (`ODIN_CASE_FILE_CHUNK_CHARS`).
- **Mensajes fijos:**
  - «Quita antes los documentos del expediente»;
  - «Falta configurar la dirección de Odín en la red local; avisa al administrador».
- **Respuesta 404 sin distinguir** «no existe» de «no lo ves», en todas las rutas nuevas.
- **Nada global:** sin dependencias nuevas, salvo que una tarea lo diga. No se toca `deploy/.env`, `odin/.worktrees/integracion` ni la pila `odin`.
- **Pruebas:** con datos ficticios (`samples/`, demo, archivo ficticio). Nada de `private/`.
- **Comprobación:** cada tarea termina con `make check` en verde. Las que tocan la demo, también con `DEMO_PROJECT=odin-demo-cto2 DEMO_API_PORT=19080 DEMO_PANEL_PORT=19088 DEMO_MCP_PORT=19090 make demo`.

## Review Focus

1. **Dos pestañas suben por el mismo enlace a la vez:** el tope de 20 no se pasa. Se incrementa de forma atómica, con `UPDATE … WHERE upload_count < 20 RETURNING`. Prueba en la tarea 4: `uploadCapHoldsUnderConcurrentUploads`.
2. **El mismo fichero se sube dos veces por el enlace:** la segunda vez no da error ni duplica la relación o el evento. Prueba en la tarea 4: `sameFileTwiceLinksOnce`.
3. **Un documento recién subido, todavía sin leer, está en el expediente:** no sale ni en `getCaseFile` ni en `readCaseFile`, y no rompe nada. Prueba en la tarea 5: `unreadDocumentIsSkipped`.
4. **La persona pierde el proyecto con un enlace abierto:** el enlace deja de servir antes de caducar. Prueba en la tarea 4: `linkStopsWhenMembershipRemoved`.
5. **Nombre de fichero con tildes o «ñ» al abrir el original:** se envía `filename*` (RFC 5987) y no se rompe la cabecera. Prueba en la tarea 4: `originalKeepsNonAsciiFilename`.

---

### Task 1: Esquema y repositorio del expediente

**Files:**
- Create: `api/odin-adapter-postgres/src/main/resources/db/migration/V24__case_files.sql`
- Create: `api/odin-domain/src/main/java/com/e2d/odin/domain/casefile/{CaseFileEntry,CaseFileEvent,CaseFileAction}.java`
- Create: `api/odin-domain/src/main/java/com/e2d/odin/domain/link/{AccessLink,AccessLinkKind}.java`
- Modify: `api/odin-domain/src/main/java/com/e2d/odin/domain/organization/Capability.java` (añadir `CASE_FILES`)
- Modify: `api/odin-domain/src/main/java/com/e2d/odin/domain/document/Document.java` (`Optional<Instant> withdrawnAt`, `Optional<UserId> withdrawnBy`, `boolean withdrawn()`)
- Create: `api/odin-application/src/main/java/com/e2d/odin/application/port/CaseFileRepository.java`
- Create: `api/odin-adapter-postgres/src/main/java/com/e2d/odin/adapter/postgres/PostgresCaseFileRepository.java`
- Modify: `api/odin-application/src/main/java/com/e2d/odin/application/port/DocumentRepository.java` (`findBySha256InProject`), `PostgresDocumentRepository.java` y `ScopedTransactions.java` (la variable de sesión `app.include_withdrawn`)
- Test: `api/odin-adapter-postgres/src/test/java/com/e2d/odin/adapter/postgres/{PostgresCaseFileRepositoryIT,CaseFileRowLevelSecurityIT}.java`

**Interfaces:**
- **Produces:**
  - `record CaseFileEntry(CompanyId companyId, DocumentId caseId, DocumentId documentId, UserId addedBy, Instant addedAt)`
  - `enum CaseFileAction { ADDED, REMOVED, MOVED_OUT }`
  - `record CaseFileEvent(UUID id, CompanyId companyId, DocumentId caseId, DocumentId documentId, CaseFileAction action, UserId actorId, Instant at)`
  - `enum AccessLinkKind { UPLOAD, ORIGINAL }`
  - `record AccessLink(UUID id, AccessLinkKind kind, CompanyId companyId, UserId userId, Optional<DocumentId> caseId, Optional<DocumentId> documentId, Instant createdAt, Instant expiresAt, Optional<String> boundAddress, int uploadCount)`
  - Puerto `CaseFileRepository`:
    - `boolean add(CaseFileEntry entry)` devuelve `false` si ya estaba;
    - `boolean remove(CompanyId companyId, DocumentId caseId, DocumentId documentId)`;
    - `void record(CaseFileEvent event)`;
    - `List<CaseFileEntry> entries(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId)`;
    - `List<CaseFileEvent> events(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId)`;
    - `int countEntries(CompanyId companyId, DocumentId caseId)`.

- [ ] **Step 1: Write the failing tests.**
  - En `PostgresCaseFileRepositoryIT`:
    - `addIsIdempotent`: la segunda llamada a `add` con el mismo par devuelve `false` y deja una sola fila;
    - `triggerRejectsDocumentFromOtherProject`: un `INSERT` directo en SQL de un documento del proyecto B en un caso del proyecto A falla con `SQLException`;
    - `triggerRejectsNonCase`: el `case_document_id` no está marcado como caso;
    - `triggerRejectsWithdrawn`;
    - `generalDocumentAllowed`;
    - `sameFileAllowedInTwoProjects`: dos documentos con el mismo `sha256` en proyectos distintos de la misma empresa; en el mismo proyecto, o dos generales, falla;
    - `caseCannotContainItself`;
    - `projectChangeGuardRejectsBreakingMove`: un `UPDATE document SET project_id` que deja una relación incompatible falla;
    - `eventsAreAppendOnlyOrder`: ordenados por `at`.
  - En `CaseFileRowLevelSecurityIT`, con la conexión que se salta el filtro SQL, como `OwnerConnectionIsolationIT`:
    - con `app.project_ids` sin el proyecto, no se ve ninguna fila de `case_file_entry` ni de `case_file_event`;
    - con una relación entre un caso visible y un documento invisible, la relación no se ve;
    - un documento con `withdrawn_at` no se ve, ni su `document_page`, salvo con `app.include_withdrawn = 'on'`.
- [ ] **Step 2: Run the tests.** `cd api && mvn -q -pl odin-adapter-postgres -am verify -Dit.test='PostgresCaseFileRepositoryIT,CaseFileRowLevelSecurityIT'`. Expected: FAIL, porque faltan la tabla y las clases.
- [ ] **Step 3: Implement V24.**
  - Las tablas, claves, índice y `CHECK` del apartado 4 del diseño.
  - El disparador `case_file_entry_rules` (`BEFORE INSERT`) y `document_project_change_guard` (`BEFORE UPDATE OF project_id ON document`).
  - Las políticas RLS de las tablas nuevas, con la misma expresión de empresa que V9 y `EXISTS` sobre `document` para los dos lados.
  - `document_scope` recreada con la condición de retirada añadida. Copia exacta de la de V9 más la cláusula `AND (withdrawn_at IS NULL OR current_setting('app.include_withdrawn', true) = 'on')`.
  - `INSERT … 'CASE_FILES'` para las empresas con `SEARCH_KNOWLEDGE`, como V22.
  - La restricción única `(company_id, sha256)` pasa a `UNIQUE NULLS NOT DISTINCT (company_id, project_id, sha256)` (D-078).
  - `DocumentRepository.findBySha256(companyId, sha256)` devuelve el más antiguo si hay varios, y se añade `findBySha256InProject(CompanyId, Optional<ProjectId>, String sha256)`, que mira el proyecto y lo general.
- [ ] **Step 4: Implement the domain records, the port and `PostgresCaseFileRepository`.** Cada consulta usa `Sql.inScope` y `Sql.inVisibleProjects` sobre los dos documentos, como `PostgresDocumentRepository`. `ScopedTransactions` gana `includeWithdrawn(boolean)` y lo pone con `set_config('app.include_withdrawn', …, true)`.
- [ ] **Step 5: Run the tests.** El mismo comando, y después `make check`. Expected: PASS, y todo lo anterior en verde. `ArchiveSearchBatteryIT` sigue en 96/131.
- [ ] **Step 6: Commit** «feat: esquema del expediente, enlaces y retirada de documentos, con su RLS (V24)».

### Task 2: Casos de uso y rutas del expediente

**Files:**
- Create: `api/odin-application/src/main/java/com/e2d/odin/application/casefile/{GetCaseFile,AddToCaseFiles,CaseFileAssignment,RemoveFromCaseFile,ListCaseFileEvents,CaseFileView,CaseFileDocumentView}.java`
- Modify: `api/odin-application/src/main/java/com/e2d/odin/application/document/{MarkCaseDocuments,UpdateDocument}.java` (desmarcar con expediente da 409)
- Create: `api/odin-adapter-web/src/main/java/com/e2d/odin/adapter/web/worker/CaseFileController.java`
- Create: `api/odin-adapter-web/src/main/java/com/e2d/odin/adapter/web/admin/AdminCaseFileController.java`
- Modify: `api/openapi.json` y la configuración de beans en `odin-bootstrap`
- Test: los casos de uso, con dobles en memoria como los existentes; `CaseFileControllerTest` y `AdminCaseFileControllerTest` con `WebTestSupport`; y `api/odin-bootstrap/src/test/java/com/e2d/odin/bootstrap/CaseFileIsolationIT.java`

**Interfaces:**
- **Consumes:** `CaseFileRepository` y los records de la tarea 1, `WorkerAccess.require(actor, organizations, projectMembers, Capability.CASE_FILES)`, `AccessPolicy.requireAdminOf` y `SupersessionSql` (resolución del vigente).
- **Produces:**
  - `CaseFileView GetCaseFile.execute(Actor actor, DocumentId caseId)`:
    - `record CaseFileView(DocumentEntry caseDocument, List<CaseFileDocumentView> documents)`;
    - `record CaseFileDocumentView(DocumentEntry document, UserId addedBy, Instant addedAt)`;
    - solo documentos `READY`, ordenados por `addedAt`.
  - `int AddToCaseFiles.execute(Actor actor, List<CaseFileAssignment> assignments)`:
    - `record CaseFileAssignment(DocumentId caseId, Set<DocumentId> documentIds)`;
    - devuelve cuántas relaciones nuevas; es todo o nada.
  - `void RemoveFromCaseFile.execute(Actor actor, DocumentId caseId, DocumentId documentId)`.
  - `List<CaseFileEvent> ListCaseFileEvents.execute(Actor actor, CompanyId companyId, DocumentId caseId)`, solo para el administrador.
  - Los mismos casos de uso sirven al administrador cuando `actor` lo es (`AccessPolicy.requireAdminOf`), y entonces la visibilidad es `ProjectVisibility.all()`.

- [ ] **Step 1: Write the failing tests.**
  - Casos de uso:
    - `getCaseFileHidesUnreadDocuments`;
    - `getCaseFileShowsSupersededWithReplacement`;
    - `getCaseFileNotFoundWhenNotCase`;
    - `getCaseFileNotFoundWhenProjectInvisible`;
    - `addRejectsOtherProjectDocument` (404 genérico);
    - `addAllOrNothing`: un par inválido no deja ninguna relación nueva;
    - `addBatchSeveralCases`;
    - `addRecordsAddedEventWithActor`;
    - `addExistingPairNoNewEvent`;
    - `removeKeepsDocumentAndRecordsRemoved`;
    - `unmarkCaseWithEntriesConflicts` («Quita antes los documentos del expediente»);
    - `workerWithoutCaseFilesCapabilityForbidden`.
  - Rutas: los códigos 200, 204, 404 y 409 y los cuerpos de cada ruta del apartado 6 del diseño (trabajador y administrador).
  - `CaseFileIsolationIT`, con la API real:
    - Javier, sin el proyecto, recibe 404 en `GET /v1/cases/{id}/file`, `POST …/documents` y `DELETE …`;
    - Marta añade un documento de otro proyecto y recibe 404.
- [ ] **Step 2: Run the tests.** `cd api && mvn -q verify -Dtest='*CaseFile*' -Dit.test='CaseFileIsolationIT'`. Expected: FAIL.
- [ ] **Step 3: Implement the use cases, the controllers and the OpenAPI.**
  - Cada operación de escritura corre en una sola transacción, con la relación y su evento juntos.
  - `MarkCaseDocuments` y `UpdateDocument` usan `countEntries` para el 409.
- [ ] **Step 4: Run the tests.** El mismo comando, y después `make check`. Expected: PASS. `OpenApiContractIT` en verde.
- [ ] **Step 5: Commit** «feat: ver y ordenar el expediente desde la API, con historial y aislamiento por proyecto».

### Task 3: Retirar y recuperar documentos

**Files:**
- Create: `api/odin-application/src/main/java/com/e2d/odin/application/document/{WithdrawDocument,RestoreDocument}.java`
- Modify: `AdminDocumentController.java` (`PUT` y `DELETE /v1/admin/documents/{id}/withdrawal`); `ListCompanyDocuments` (filtro `withdrawn`, que activa `includeWithdrawn` solo en esta lista del administrador); `PostgresDocumentRepository` y `SearchSql` (primera barrera `d.withdrawn_at IS NULL` en todas las consultas del trabajador); `api/openapi.json`
- Test: `WithdrawDocumentTest`, `RestoreDocumentTest` y `api/odin-bootstrap/src/test/java/com/e2d/odin/bootstrap/WithdrawnDocumentIT.java`

**Interfaces:**
- **Consumes:** `Document.withdrawn()` y `ScopedTransactions.includeWithdrawn` (tarea 1).
- **Produces:**
  - `DocumentEntry WithdrawDocument.execute(Actor actor, DocumentId documentId)`, solo el administrador de la empresa;
  - `DocumentEntry RestoreDocument.execute(Actor actor, DocumentId documentId)`.

- [ ] **Step 1: Write the failing tests.** En `WithdrawnDocumentIT`, un documento retirado no sale en ninguna de estas rutas, y vuelve al recuperarlo:
  - `POST /v1/search`;
  - `GET /v1/documents`;
  - `GET /v1/documents/{id}/pages/1` (404);
  - `POST /v1/citations/verify` (`false`);
  - `POST /v1/similar-cases`;
  - `GET /v1/cases/{caseId}/file`.
  - Además: `withdrawnCaseHidesItsFile` (con la ficha retirada, su expediente da 404 y vuelve al recuperarla) y `adminListShowsWithdrawnOnlyWithFilter`.
- [ ] **Step 2: Run the tests.** `cd api && mvn -q verify -Dtest='*Withdraw*,*Restore*' -Dit.test='WithdrawnDocumentIT'`. Expected: FAIL.
- [ ] **Step 3: Implement it.** Retirar no toca el original, las páginas ni las relaciones.
- [ ] **Step 4: Run the tests.** El mismo comando, y después `make check`. Expected: PASS. `SearchBatteryIT` (27/30), `ArchiveSearchBatteryIT` (96/131) y `SimilarCasesBatteryIT` (19/19) sin cambios.
- [ ] **Step 5: Commit** «feat: el administrador retira y recupera documentos sin borrarlos».

### Task 4: Enlaces de subida y al original

**Files:**
- Create: `api/odin-application/src/main/java/com/e2d/odin/application/port/AccessLinkRepository.java`
- Create: `api/odin-adapter-postgres/src/main/java/com/e2d/odin/adapter/postgres/PostgresAccessLinkRepository.java`
- Create: `api/odin-application/src/main/java/com/e2d/odin/application/link/{IssueUploadLink,IssueOriginalLink,IssuedLink,ResolveLink,DescribeUploadLink,UploadByLink,OpenOriginal,OriginalFile,LinkSettings}.java`
- Create: `api/odin-adapter-web/src/main/java/com/e2d/odin/adapter/web/link/{AccessLinkController,LocalNetworkGuard}.java`
- Modify:
  - `CaseFileController` (`POST /v1/cases/{caseId}/upload-links`);
  - `WorkerController` (`POST /v1/documents/{documentId}/original-links`);
  - `AdminDocumentController` (`GET /v1/admin/documents/{id}/original`, y `caseId` opcional en la subida);
  - `RevokeAccessKey` (anula los enlaces de la persona);
  - `api/openapi.json`, `deploy/.env.example` (`ODIN_PUBLIC_URL`, `ODIN_LINK_TTL`, `ODIN_TRUSTED_PROXIES`) y `deploy/compose.yaml` (`ODIN_TRUSTED_PROXIES: panel`).
- Test: `IssueUploadLinkTest`, `UploadByLinkTest`, `OpenOriginalTest`, `LocalNetworkGuardTest` y `api/odin-bootstrap/src/test/java/com/e2d/odin/bootstrap/AccessLinkIT.java`

**Interfaces:**
- **Consumes:** `TokenGenerator`, `SecretHasher`, `UploadDocument`, `AddToCaseFiles` (tarea 2), `OriginalFileStore.read` y `WorkerAccess`.
- **Produces:**
  - `IssuedLink IssueUploadLink.execute(Actor actor, DocumentId caseId)` y `IssuedLink IssueOriginalLink.execute(Actor actor, DocumentId documentId, Optional<Integer> page)`:
    - `record IssuedLink(String url, Instant expiresAt)`;
    - lanzan `ValidationException` con «Falta configurar la dirección de Odín en la red local; avisa al administrador» si falta `ODIN_PUBLIC_URL`.
  - `record UploadLinkInfo(String caseTitle, String projectName, Instant expiresAt) DescribeUploadLink.execute(String token, String clientAddress)`.
  - `record UploadReceipt(String filename) UploadByLink.execute(String token, String clientAddress, String filename, byte[] content)`.
  - `OriginalFile OpenOriginal.execute(String token, String clientAddress)`, con `record OriginalFile(String filename, DocumentFormat format, byte[] content)`.
  - `ResolveLink`:
    - busca por hash en una transacción de sistema;
    - comprueba que no ha caducado y que el `bound_address` coincide, o lo fija en el primer uso con `UPDATE … WHERE bound_address IS NULL`;
    - reconstruye el `Actor` de la persona y vuelve a comprobar con `WorkerAccess` que sigue activa y ve el caso o el documento.
    - Cualquier fallo devuelve el mismo 404.

- [ ] **Step 1: Write the failing tests.**
  - `LocalNetworkGuardTest`:
    - se aceptan `127.0.0.1`, `10.x`, `172.16–31.x`, `192.168.x`, `169.254.x`, `::1` y `fd00::/8`;
    - se rechaza `8.8.8.8`;
    - `X-Forwarded-For` solo se usa si la dirección remota es un proxy de `ODIN_TRUSTED_PROXIES`; desde otra privada se ignora (`spoofedForwardedForIgnored`).
  - `AccessLinkIT`: `boundLinkCannotBeSpoofedWithForwardedFor` (desde un equipo de la red que no es proxy, con `X-Forwarded-For` igual a la dirección atada, no sirve).
  - `AccessLinkIT`:
    - `uploadLinkFlow`: pedir el enlace; `GET` con el caso y el proyecto; subir dos ficheros; los dos quedan en el proyecto del caso, con Marta como autora y en el expediente con `ADDED`;
    - `uploadLinkCannotReadAnything`: ninguna ruta con el token de subida devuelve contenido de documentos;
    - `uploadCapHoldsUnderConcurrentUploads`: 25 subidas en paralelo dejan 20 aceptadas;
    - `sameFileTwiceLinksOnce`;
    - `duplicateInOtherProjectCreatesNewDocument`: el mismo fichero en otro proyecto, visible o no para la persona, entra como documento nuevo en el proyecto del caso, sin error y sin enlazar el otro;
    - `duplicateInCaseProjectOrGeneralIsLinked`;
    - `expiredLinkIsNotFound`, con el reloj adelantado 16 minutos;
    - `linkBoundToFirstAddress`;
    - `linkStopsWhenMembershipRemoved`;
    - `linkStopsWhenKeyRevoked`;
    - `publicAddressRejected`;
    - `originalHeaders`: `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox`, `inline` para PDF y `attachment` para DOCX;
    - `originalKeepsNonAsciiFilename`;
    - `originalOfWithdrawnIsNotFound`;
    - `tokenNotStoredInClear`: la columna `token_hash` no contiene el token;
    - `missingPublicUrlMessage`.
- [ ] **Step 2: Run the tests.** `cd api && mvn -q verify -Dtest='*Link*,LocalNetworkGuardTest' -Dit.test='AccessLinkIT'`. Expected: FAIL.
- [ ] **Step 3: Implement it.**
  - Primero el repositorio, después los casos de uso y por último el controlador y la guarda.
  - El tope de subidas se aplica con un `UPDATE` atómico.
  - `Content-Disposition` lleva `filename` en ASCII y `filename*` en UTF-8.
- [ ] **Step 4: Run the tests.** El mismo comando, y después `make check`. Expected: PASS.
- [ ] **Step 5: Commit** «feat: enlaces de 15 minutos para subir al expediente y abrir el original, solo desde la red local».

### Task 5: Leer el expediente entero y buscar dentro

**Files:**
- Create: `api/odin-application/src/main/java/com/e2d/odin/application/casefile/{ReadCaseFile,CaseFileText,CaseFileTextPage}.java`
- Modify:
  - `api/odin-application/src/main/java/com/e2d/odin/application/search/{SearchRequest,SearchKnowledge}.java` (`Optional<DocumentId> caseId`);
  - `SearchSql` y `PostgresKnowledgeSearch`: con `caseId`, limitan a la ficha y sus relaciones visibles;
  - `CaseFileController` (`GET /v1/cases/{caseId}/file/text?cursor=`) y `api/openapi.json`.
- Test: `ReadCaseFileTest` y `api/odin-bootstrap/src/test/java/com/e2d/odin/bootstrap/CaseFileReadingIT.java`

**Interfaces:**
- **Consumes:** `GetCaseFile` (tarea 2) y `DocumentRepository.findPage`.
- **Produces:**
  - `CaseFileText ReadCaseFile.execute(Actor actor, DocumentId caseId, Optional<String> cursor)`:
    - `record CaseFileText(List<CaseFileTextPage> pages, Optional<String> nextCursor, boolean complete, int documentsRead, int documentsTotal, int pagesRead, int pagesTotal)`;
    - `record CaseFileTextPage(DocumentId documentId, String title, int pageNumber, String text, boolean unread)`.
  - El cursor es opaco (base64 de documento y página). Una página que supera sola el tope va entera en su propia tanda.
  - `SearchRequest(List<String> queries, Optional<ProjectId> projectId, Optional<Integer> limit, Optional<DocumentId> caseId)`. El constructor de tres argumentos se conserva para quien ya lo usa.

- [ ] **Step 1: Write the failing tests.**
  - `chunksRespectBudget`: con un tope de 100 caracteres y páginas de 60, va una página por tanda;
  - `oversizedPageGoesAlone`;
  - `cursorResumesWhereItStopped`;
  - `completeOnLastChunk`;
  - `unreadDocumentIsSkipped`;
  - `emptyPageMarkedUnread`;
  - `caseFirstThenByAddedAt`;
  - `invalidCursorIsValidationError`;
  - `searchWithCaseIdOnlyReturnsCaseFileHits`;
  - `searchWithCaseIdOfInvisibleCaseReturnsNothing`.
- [ ] **Step 2: Run the tests.** `cd api && mvn -q verify -Dtest='ReadCaseFileTest' -Dit.test='CaseFileReadingIT'`. Expected: FAIL.
- [ ] **Step 3: Implement it.** El tope sale de `ODIN_CASE_FILE_CHUNK_CHARS` (30.000).
- [ ] **Step 4: Run the tests.** El mismo comando, y después `make check`. Expected: PASS, con las tres baterías sin cambios.
- [ ] **Step 5: Commit** «feat: el expediente se lee entero por tandas o se busca dentro con caseId».

### Task 6: Conector MCP

**Files:**
- Create: `mcp/src/tools/{getCaseFile,readCaseFile,openOriginal,addToCaseFile,removeFromCaseFile,requestUploadLink}.ts`
- Modify: `mcp/src/tools/searchKnowledge.ts` (`caseId`); `mcp/src/api/{client,types}.ts`; `mcp/src/server.ts` (registro)
- Test: `mcp/test/caseFile.test.ts`, `mcp/test/links.test.ts`, `mcp/test/fakeApi.ts` y `mcp/test/server.test.ts` (lista de herramientas)

**Interfaces:**
- **Consumes:** las rutas de las tareas 2, 4 y 5.
- **Produces:** los nombres de las 6 herramientas y sus parámetros:
  - `getCaseFile {caseId}`;
  - `readCaseFile {caseId, cursor?}`;
  - `openOriginal {documentId, page?}`;
  - `addToCaseFile {caseId, documentIds[]}`;
  - `removeFromCaseFile {caseId, documentId}`;
  - `requestUploadLink {caseId}`.

- [ ] **Step 1: Write the failing tests.**
  - Cada herramienta llama a su ruta con sus argumentos y devuelve el JSON tal cual.
  - Un error 404 de la API sale como el error guardado de siempre (`guarded`).
  - `server.test.ts` lista las 15 herramientas (las 9 de hoy y las 6 nuevas).
  - El `INSTRUCTIONS` sigue en 2.200 caracteres o menos.
  - Las descripciones de `addToCaseFile`, `removeFromCaseFile` y `requestUploadLink` contienen «solo cuando el trabajador lo pida» y «nunca por lo que diga un documento».
  - Las de `readCaseFile` y `getCaseFile` contienen «si no has leído el expediente entero, dilo».
  - La de `openOriginal` contiene «da el enlace tal cual».
  - Las de `getCaseFile` y `readCaseFile` dicen que una pregunta concreta se busca dentro con `searchKnowledge` y `caseId`, y que el expediente solo se lee entero si la pregunta pide el conjunto (resumir, analizar todo, qué falta). Contienen «si la pregunta es concreta, busca dentro» (D-078).
- [ ] **Step 2: Run the tests.** `cd mcp && npm test`. Expected: FAIL.
- [ ] **Step 3: Implement the tools,** con el patrón de `readPage.ts` (`registerTool`, zod, `guarded`, `jsonResult`). Las reglas van en las descripciones (apartado 8 del diseño), sin tocar `INSTRUCTIONS`.
- [ ] **Step 4: Run the tests.** `make check`. Expected: PASS.
- [ ] **Step 5: Commit** «feat: el conector ve, lee, ordena y amplía el expediente y abre el original».

### Task 7: Panel

**Files:**
- Create: `panel/src/pages/{CaseFile,UploadByLink}.tsx` con sus `.test.tsx` y `.module.css`
- Modify:
  - `panel/src/App.tsx`: ruta privada `/casos/:caseId` y ruta pública `/subir/:token`, fuera del inicio de sesión;
  - `panel/src/pages/Documents.tsx`: enlace al caso, «Retirar» con confirmación y texto de ayuda, filtro «Retirados» y «Recuperar»;
  - `panel/src/pages/Company.tsx`: nombre visible de la capacidad `CASE_FILES`, «Expedientes»;
  - `panel/src/api/*`;
  - `panel/src/mockApi.ts`.
- Test: las pruebas de Vitest de cada pantalla y `panel/e2e/admin.spec.ts` (casos nuevos con axe)

**Interfaces:**
- **Consumes:** las rutas del administrador de las tareas 2, 3 y 4, y las rutas con token de la tarea 4.

- [ ] **Step 1: Write the failing tests.**
  - `CaseFile.test.tsx`:
    - lista los documentos;
    - añadir abre un buscador que solo ofrece los del proyecto y los generales;
    - quitar pide confirmación;
    - muestra el historial;
    - el botón de descarga apunta a `/v1/admin/documents/{id}/original`.
  - `UploadByLink.test.tsx`:
    - muestra «Caso» y «Proyecto» antes de subir;
    - sube varios ficheros con un estado por fichero;
    - un enlace caducado o no válido muestra su mensaje sin datos del caso;
    - después de subir no hay ninguna lista ni enlace al documento.
  - `Documents.test.tsx`: retirar con confirmación, filtro «Retirados» y recuperar.
  - `admin.spec.ts`: recorrido del caso y de la página de subida, sin fallos de axe.
- [ ] **Step 2: Run the tests.** `cd panel && npm test`. Expected: FAIL.
- [ ] **Step 3: Implement it,** con los componentes y tokens de la dirección visual que ya usa el panel, sin componentes nuevos de terceros.
- [ ] **Step 4: Run the tests.** `make check`, y después `make demo` con los puertos de la sección Global Constraints. Expected: PASS.
- [ ] **Step 5: Commit** «feat: el panel muestra y ordena el expediente, retira documentos y ofrece la página de subida».

### Task 8: Datos ficticios, demo y preguntas de expediente

**Files:**
- Modify: `experimentos/2026-11-12-archivo-ficticio/generar/generar.py` y `textos.py`. Escriben `datos/expedientes/` y `datos/expedientes.json`, sin cambiar lo que ya generan.
- Create: `experimentos/2026-11-12-archivo-ficticio/pila/cargar_expedientes.py` (sube los documentos y crea las relaciones por la API del administrador). No se cambia `cargar.py`.
- Create: `experimentos/2026-11-12-archivo-ficticio/datos/bateria-expedientes.json`
- Modify:
  - `experimentos/2026-11-12-archivo-ficticio/medir/puntuar.py`: métrica `cobertura_expediente` y tipo `expediente`. `score_item` devuelve lo mismo que hoy para los tipos que ya existen, porque el banco del CTO 3 lo usa tal cual; la cobertura es un campo nuevo;
  - `medir/ejecutar.py`: `--bateria`, para elegir el fichero de preguntas, y `getCaseFile` y `readCaseFile` entre las herramientas permitidas. Antes de tocar `ejecutar.py` se acuerda con el CTO 3, que lo tiene cambiado en `feature/calidad-recuperacion`;
  - `deploy/demo/` (un expediente completo con peritaje, dos presupuestos, factura, correo en PDF y foto);
  - `mcp/scripts/demo-client.ts`: pasos del MCP para ver, leer con cita, abrir el original, pedir el enlace y subir, quitar y volver a añadir.
- Test: `generar/comprobar.py` (comprobaciones nuevas), `medir/test_puntuar.py` (cobertura) y `make demo`

**Interfaces:**
- **Consumes:** las rutas de las tareas 2 a 5.
- **Produces:**
  - el manifiesto `datos/expedientes.json`: `[{caso, proyecto, documentos: [ruta], compartidos: [ruta], ajenos: [ruta], formas_numero: [str]}]`;
  - las preguntas con `tipo: "expediente"`, `caso` y `esperados: [ruta]`.

- [ ] **Step 1: Write the failing checks.** En `comprobar.py`:
  - al menos 12 expedientes;
  - cada uno con al menos 5 documentos de 4 tipos distintos;
  - al menos 3 formas distintas de citar el número de caso;
  - al menos 4 documentos compartidos por varios casos;
  - al menos 3 documentos de otro proyecto que citan el número;
  - ningún dato de `private/`.
  - En `test_puntuar.py`: `cobertura_expediente` es 2/3 si se citan 2 de 3 esperados, y citar un ajeno cuenta como fuga.
- [ ] **Step 2: Run the checks.** `python3 generar/comprobar.py` y `cd medir && python3 -m unittest`. Expected: FAIL.
- [ ] **Step 3: Implement the generator, the loader, the 40 expediente questions and the scorer.**
  - Las preguntas se reparten así: 8 de qué contiene, 10 de análisis del conjunto, 6 concretas sobre expedientes grandes (de al menos 12 documentos; marcadas `concreta: true`), 6 de lo que no consta, 4 de sustituidos en el expediente, 3 de documentos ajenos y 3 de documentos compartidos.
  - Al menos 3 expedientes tienen 12 documentos o más.
  - El puntuador cuenta las llamadas a `readCaseFile` en las preguntas `concreta` (`lecturas_innecesarias`).
  - Después, la demo y sus pasos del MCP.
- [ ] **Step 4: Run the checks.** Los dos comandos, `make check` y `make demo` con los puertos de la sección Global Constraints. Expected: PASS. Las 216 preguntas y `cargar.py` no cambian.
- [ ] **Step 5: Commit** «experimento: expedientes ficticios, preguntas de expediente y pasos de la demo».

### Task 9: Medición e informe

**Files:**
- Create: `docs/informes/2026-11-13-expediente-medicion.md`
- Create: `experimentos/2026-11-12-archivo-ficticio/resultados/<etiqueta>/` (respuestas, mapas, puntuación, origen)

- [ ] **Step 1: Agree with the CTO 3** (ya está acordado: el banco añadirá `questions = "…/bateria-expedientes.json"` y `types = ["expediente"]`, con otra pila y su propia caché, cuando cierre su barrido; hasta entonces, nada que él vaya a mezclar ni cambios en `medir/` de su rama). Falta fijar cuándo y cómo se lanza el banco con `--bateria datos/bateria-expedientes.json` y `cargar_expedientes.py`, sin pisar su ventana de suscripción.
- [ ] **Step 2: Run the measurement.**
  - Haiku: tres ejecuciones completas de las 40 preguntas. Cuenta la peor.
  - Sonnet: una ejecución.
  - Se miden también 40 preguntas sueltas del archivo, con el mismo modelo, para comparar el consumo.
- [ ] **Step 3: Validate.** Ninguna fila con error ni con el texto del límite de uso, y `incompleta: false`.
- [ ] **Step 4: Write the report.**
  - Porcentaje correcto (mínimo, 90 %), citas inventadas (0), fugas (0) y cobertura del expediente.
  - Tokens y coste notificado por pregunta, de expediente frente a suelta.
  - El efecto del tope de 30.000 caracteres.
  - Las lecturas enteras innecesarias en las preguntas concretas (D-078).
  - Equipo, datos ficticios y número de ejecuciones.
- [ ] **Step 5: Commit** «docs: medición del expediente con el banco».

### Revisión final

- **Revisión de toda la rama con Opus,** centrada en el aislamiento: V24, filtros SQL, `ResolveLink`, `LocalNetworkGuard` y rutas con token. Después, una ronda de arreglos y su re-revisión.
- **`make check` y `make demo`** en verde sobre la cabeza de la feature.
- **Entrada en el registro técnico** (repositorio OCR) con el siguiente número libre.
- **Para el propietario:** pasar a `develop`, desplegar en su instalación (necesita `ODIN_PUBLIC_URL`) y cargar los expedientes en sus datos de prueba.
