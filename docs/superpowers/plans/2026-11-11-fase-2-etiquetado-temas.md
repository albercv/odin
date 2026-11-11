# Odín · Fase 2, bloque 3: etiquetado por tema — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que cada empresa tenga su lista de temas, que un modelo local etiquete sus documentos con las instrucciones medidas en T-010, que el administrador corrija los temas, y que el trabajador liste por tema sabiendo si la lista está completa.

**Arquitectura:**
- **Puerto `TopicTagger`** con un adaptador para Ollama en su propio módulo.
- **Carril interno `TagPendingDocuments`,** con su ventana horaria, sus tramos y sus intentos, como los carriles de lectura.
- **Datos:** las decisiones del administrador viven en `document_topic` y mandan sobre la IA. El estado del etiquetado vive en `document_tagging`.

**Tecnología:** Java 21, Spring Boot 4.1.2, `java.net.http`, Jackson, PostgreSQL 17.10 con Flyway y seguridad por filas, Ollama (imagen oficial fijada), TypeScript con el SDK de MCP, Vitest y Playwright.

**Diseño:** `docs/superpowers/specs/2026-11-11-etiquetado-temas-design.md` (aquí, «spec»), que amplía el spec base y el de la 2a. Quien ejecute una tarea lee los tres.

## Cuándo se ejecuta

- Tras el cierre de la 2a, cuando lo diga el CEO. Va después de los bloques 1 y 2, porque toca `ListDocuments`, `DescribeMe` y el hilo de programación.
- Antes de empezar, `feature/fase-2-conocimiento` se actualiza con la punta de `feature/identidad-pertenencias`.

## Restricciones globales

- **TDD con evidencia:** primero la prueba; se ejecuta y se guardan en el informe la orden y la salida en rojo antes del código.
- **Capas:** `odin-domain` solo `java.*`; `odin-application` solo `odin-domain` y `java.*`; los adaptadores no dependen entre sí; Spring solo en los adaptadores y en `odin-bootstrap`. ArchUnit lo comprueba.
- **Clean Code (D-029):** métodos de 30 líneas como máximo, sin lógica duplicada y con comentarios mínimos. La toma con intentos y plazo sigue el patrón de los carriles de lectura; no lo reinventa.
- **Nombres:** identificadores en inglés, explicativos y en camelCase. Mensajes, documentación y commits en español.
- **Aislamiento (D-014, spec 2a 6):**
  - cada caso de uso con actor lleva una prueba con un actor de otra empresa;
  - lo que lee un trabajador lleva además una con un proyecto restringido ajeno;
  - las tablas nuevas tienen seguridad por filas.
- **Lo generado se marca:** todo tema de la IA sale con `source: AI`. Un tema que no está en la lista de la empresa no se guarda nunca (D-031, D-008).
- **Instrucciones de etiquetado:** el texto del apartado «Instrucciones v1», literal. Cambiarlo obliga a volver a medir (tarea 6).
- **Contrato:** si cambia la API, en la misma tarea se regeneran `api/openapi.json` (`cd api && mvn -q verify -Dodin.updateOpenApi=true`) y los tipos (`npm run generate:types` en `panel/` y en `mcp/`).
- **Cierre de cada tarea:** `make check` y `make demo` terminan con 0, y `SearchBatteryIT` mantiene 27 de 30.
- **Dónde se trabaja:** en `.worktrees/fase-2`. No se ejecutan `make up` ni `make down` y no se toca `deploy/.env`.
- **Red:** solo `make tagger-model` descarga un modelo, y solo la ejecuta el CTO, con el modelo ya autorizado por el propietario (T-010). Los subagentes no descargan modelos.
- **Git:**
  - cada tarea en su rama `task/fase-2-conocimiento/<tarea>`;
  - commits en español, sin atribución a Claude ni `Co-Authored-By`;
  - sin push;
  - el merge `--no-ff` lo hace el CTO.
- **Migración:** `V12__topics.sql`, o la siguiente libre.
- **Datos:** solo ficticios.

## Foco de revisión

1. **El administrador corrige mientras la IA etiqueta:** su corrección sobrevive al guardado de la IA. Prueba en la tarea 3 (`adminDecisionDuringTaggingSurvives`).
2. **Una lista de temas larga con un documento largo:** el tramo se parte hasta caber o el documento falla con el mensaje del spec. Nunca se guardan temas de un texto recortado. Prueba en la tarea 2 (`truncatedChunkIsSplitUntilItFits` y `chunkBelowMinimumFailsWithListTooLong`).
3. **Un trabajador sin acceso a un proyecto restringido:** el recuento de pendientes no cuenta sus documentos y el filtro por tema no los devuelve. Prueba en la tarea 5 (`pendingCountIgnoresInvisibleProjects`).
4. **Un tema que contiene a otro**, como «agua» frente a «agua y humedades»: se rechaza al darlo de alta y al renombrar. Prueba en la tarea 1 (`nameContainingAnotherIsRejected` y `renameIntoContainingNameIsRejected`).
5. **El modelo responde con un nombre que no está en la lista, o con nada:** no se guarda ningún tema y el documento queda `DONE`. Prueba en la tarea 4 (`unknownNamesAreIgnored`) y en la tarea 2 (`noTopicFromListLeavesDoneWithoutTopics`).

---

## Interfaces compartidas (fuente única de nombres)

```java
// domain/topic
public record TopicId(UUID value) { }
public record Topic(TopicId id, CompanyId companyId, String name, String definition) {
    public static final int MAX_NAME = 60, MAX_DEFINITION = 500, MAX_PER_COMPANY = 30;
}
public final class TopicNames { static boolean contains(String name, String other); } // sin tildes ni mayúsculas
public enum TopicSource { AI, ADMIN }
public enum TopicState { ACTIVE, REMOVED }
public record DocumentTopic(DocumentId documentId, TopicId topicId, TopicSource source, TopicState state,
        Optional<String> taggerVersion, Optional<UserId> decidedBy, Optional<Instant> decidedAt) { }
public enum TaggingStatus { PENDING, TAGGING, DONE, FAILED }
public record DocumentTagging(DocumentId documentId, CompanyId companyId, int topicListVersion, TaggingStatus status,
        int attemptCount, Optional<Instant> deadlineAt, Optional<String> failureReason, Optional<String> taggerVersion) { }

// application/port
public interface TopicRepository {
    List<Topic> list(CompanyId companyId);
    Optional<Topic> find(CompanyId companyId, TopicId topicId);
    void save(Topic topic);                       // sube topic_list_version si el nombre o la definición cambian
    void delete(CompanyId companyId, TopicId topicId, Instant at);   // lógico; no sube la versión
    int listVersion(CompanyId companyId);
}
public interface TaggingRepository {
    Optional<TaggingJob> claimNext(Instant now);  // documento READY, empresa con temas; orden del spec 5.2
    void saveResult(DocumentId documentId, Set<TopicId> aiTopics, String taggerVersion, Instant now); // bloquea la fila
    void markFailed(DocumentId documentId, String reason);
    void returnToQueue(DocumentId documentId, boolean countAttempt);
    void retag(CompanyScope scope, DocumentId documentId);
    List<DocumentTopic> topicsOf(CompanyScope scope, DocumentId documentId);
    void decide(CompanyScope scope, DocumentId documentId, TopicId topicId, TopicState state, UserId by, Instant at); // bloquea la fila
    int pendingCount(CompanyId companyId, ProjectVisibility visibility);
    TaggingBacklog backlog(CompanyId companyId);      // para el panel (D-043); todas las visibilidades
}
public record TaggingBacklog(int pendingDocuments, Optional<Instant> oldestPendingSince) { }
public record TaggingJob(Document document, List<DocumentPage> pages, List<Topic> topics, String companyName,
        int topicListVersion, int attemptCount) { }
public interface TopicTagger {
    boolean isAvailable();
    TaggerAnswer tag(String instructions);        // texto ya montado
}
public record TaggerAnswer(String text, int promptTokens, int contextTokens, String taggerVersion) {
    public boolean truncated() { return promptTokens >= contextTokens; }
}
// TaggerUnavailableException y TaggerFailedException, como en el bloque 1

// application/topic
public class CreateTopic, UpdateTopic, DeleteTopic, ListTopics      // Actor administrador de la empresa o del grupo
public class ReviewDocumentTopics { add(Actor, DocumentId, TopicId); remove(Actor, DocumentId, TopicId); retag(Actor, DocumentId); }
public class GetTaggingStatus { public TaggingStatusReport execute(Actor actor, CompanyId companyId); }
public record TaggingStatusReport(TaggingBacklog backlog, TaggingWindow window) { }
public final class TaggingInstructions { static String build(String companyName, List<Topic> topics, String text); }
public final class TaggingChunks { static List<String> of(List<DocumentPage> pages, int maxCharacters); static List<String> split(String chunk); }
public final class TopicMatcher { static Set<TopicId> match(String answer, List<Topic> topics); }
public class TagPendingDocuments { public boolean processNext(); }
// application/shared
public record TaggingSettings(TaggingWindow window, Duration chunkTimeout, int chunkCharacters, int maxAttempts) { }
public record TaggingWindow(LocalTime from, LocalTime to, boolean always, ZoneId zone) { boolean isOpen(Instant now); }
```

**Instrucciones v1** (`TaggingInstructions.build`; adaptan las de `clasificar.py` con definiciones; lo que no es el preámbulo va literal):

```
Eres el sistema de archivo de {companyName}. Lee este documento y asígnale una o varias etiquetas de esta lista:
- {name}: {definition}
…

{text}

Si dudas, pon todas las que encajen: es peor que falte una etiqueta que poner una de más. Responde solo con las etiquetas que correspondan, separadas por punto y coma.
```

- `TopicMatcher`: un tema está en la respuesta si su nombre normalizado (minúsculas, sin tildes ni espacios repetidos) es parte de la respuesta normalizada, como en `clasificar.py`.
- Versión del etiquetador: `"<modelo>; instrucciones v1"`.

**Rutas:** las del spec 6. Cuerpos: `TopicRequest { name, definition }` y `TopicPatch { name?, definition? }`. Respuestas: `TopicView { id, name, definition }`, `DocumentTopicView { topicId, name, source, state, taggerVersion, decidedByName, decidedAt }`, `DocumentTopicsResponse { topics, tagging: { status, topicListVersion, failureReason } }`.

---

### Tarea 1: Lista de temas de cada empresa · Sonnet

Rama `task/fase-2-conocimiento/temas`. Spec 4 y 5.1.

**Ficheros:**
- **Crear:**
  - dominio: `domain/topic/`, con `TopicId`, `Topic`, `TopicNames`, `TopicSource`, `TopicState`, `DocumentTopic`, `TaggingStatus` y `DocumentTagging`, más sus pruebas;
  - aplicación: `application/port/TopicRepository.java`; `application/topic/CreateTopic.java`, `UpdateTopic.java`, `DeleteTopic.java` y `ListTopics.java`, con `ManageTopicsTest`;
  - PostgreSQL: `PostgresTopicRepository` con su IT, y `V12__topics.sql`;
  - pruebas y web: el doble `InMemoryTopicRepository` y `TopicController`.
- **Modificar:** `Company` o `PostgresOrganizationRepository`, para la versión de la lista; `UseCaseConfiguration`, `AdapterConfiguration`, `TestWiring`, `Requests`, `Responses`, el contrato y los tipos.

**Valores:**
- `V12` crea las cuatro piezas del spec 4:
  - `topic (id, company_id, name, definition, deleted_at)`, con un índice único `(company_id, lower(unaccent(name))) WHERE deleted_at IS NULL`;
  - `document_topic (document_id, topic_id, company_id, source, state, tagger_version, decided_by, decided_at)`, con clave primaria `(document_id, topic_id)`;
  - `document_tagging (document_id PK, company_id, topic_list_version, status, attempt_count, deadline_at, failure_reason, tagger_version, seq)`;
  - `company.topic_list_version` y `company.topic_list_changed_at timestamptz`.
  - Seguridad por filas: `topic` como `project`; las otras dos como `document_page`, con el proyecto de su documento (spec 2a, 6). `odin_app` recibe los mismos permisos que sobre `document_page`.
- **Mensajes:**
  - 409, «Ya hay un tema con ese nombre»;
  - 400, «Un nombre de tema no puede contener a otro: <nombre>»;
  - 400, «Una empresa puede tener como mucho 30 temas»;
  - 400 con `Validation.requireText` para el nombre y la definición vacíos;
  - 400, «El nombre del tema no puede pasar de 60 caracteres» y «La definición no puede pasar de 500 caracteres».
- **Versión:**
  - `save` de un tema nuevo, o con nombre o definición distintos, suma 1 a `company.topic_list_version` y pone `topic_list_changed_at` a la hora actual, en la misma transacción;
  - `delete` no la cambia.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `TopicNamesTest`: `containsIgnoresAccentsAndCase` y `differentNamesDoNotContain`;
  - `ManageTopicsTest`:
    - `createsTopic`;
    - `duplicateNameIsConflict`;
    - `nameContainingAnotherIsRejected`;
    - `renameIntoContainingNameIsRejected`;
    - `thirtyFirstTopicIsRejected`;
    - `limitsOnNameAndDefinition`;
    - `deleteIsLogical`;
    - `workerIsForbidden`;
    - `adminOfAnotherCompanyIsForbidden`.
- [ ] **Paso 2:** implementa el dominio, los casos de uso y el doble.
- [ ] **Paso 3:** `PostgresTopicRepositoryIT`:
  - `createAndEditRaiseListVersion`;
  - `deleteKeepsListVersion`;
  - `sameEditTwiceRaisesOnce`;
  - `rowLevelSecurityHidesOtherCompanyTopics`.
  - Escribe `V12`.
- [ ] **Paso 4:** web y contrato: `AdminApiTest`, una prueba por ruta de temas, e `IsolationTest.otherCompanyAdminCannotManageTopics`.
- [ ] **Paso 5:** regenera el contrato y los tipos. `make check` y `make demo` terminan con 0.
- [ ] **Paso 6:** commit «feat: lista de temas de cada empresa».

### Tarea 2: Carril de etiquetado en el núcleo · Sonnet

Rama `task/fase-2-conocimiento/carril-etiquetado`. Spec 5.2.

**Ficheros:**
- **Crear:**
  - puertos: `application/port/TaggingRepository.java`, `TaggingJob.java`, `TopicTagger.java`, `TaggerAnswer.java`, `TaggerUnavailableException.java` y `TaggerFailedException.java`;
  - caso de uso y apoyo: `application/topic/TaggingInstructions.java`, `TaggingChunks.java`, `TopicMatcher.java` y `TagPendingDocuments.java`, con sus pruebas;
  - configuración: `application/shared/TaggingSettings.java` y `TaggingWindow.java`, con `TaggingWindowTest`;
  - dobles: `InMemoryTaggingRepository` y `ScriptedTopicTagger`.

**Interfaces que produce:** las de «Interfaces compartidas».

**Valores:**
- `TaggingWindow`: `22:00-07:00` cruza la medianoche; `always` siempre está abierta.
- `TaggingChunks.of`:
  - junta páginas en orden hasta `maxCharacters`;
  - una página más larga se parte por el límite, en el último espacio antes de él.
- `split`: parte el tramo en dos mitades por el espacio más cercano a la mitad. Mínimo, 1.000 caracteres.
- `TagPendingDocuments.processNext`:
  1. si la ventana está cerrada o el etiquetador no está disponible, no toma nada;
  2. `claimNext`;
  3. con más de 3 intentos, `markFailed(TOO_MANY_ATTEMPTS)`;
  4. por cada tramo, monta las instrucciones, llama, y si la respuesta está recortada, parte y reintenta;
  5. une los temas con `TopicMatcher` y llama a `saveResult`.
- **Mensajes:**
  - `LIST_TOO_LONG` = «La lista de temas es demasiado larga para el modelo: acorta las definiciones»;
  - `TAGGER_FAILED` = «El etiquetador no pudo procesar el documento. Reinténtalo.»;
  - `TOO_MANY_ATTEMPTS` = «demasiados intentos».
- **Errores:** `TaggerUnavailableException` llama a `returnToQueue(false)`; `TaggerFailedException`, a `returnToQueue(true)`, y en el último intento a `markFailed(TAGGER_FAILED)`.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `TaggingWindowTest`: `nightWindowCrossesMidnight`, `alwaysIsOpen` y `usesInstallationZone`;
  - `TaggingChunksTest`: `joinsPagesUpToLimit`, `splitsLongPageAtLastSpace` y `splitGivesTwoHalves`;
  - `TopicMatcherTest`: `matchesIgnoringAccentsAndCase` y `ignoresNamesNotInList`;
  - `TaggingInstructionsTest`: `buildsMeasuredInstructionsWithCompanyNameAndDefinitions`, que compara con el texto literal de «Instrucciones v1»;
  - `TagPendingDocumentsTest`:
    - `closedWindowClaimsNothing`;
    - `unavailableTaggerClaimsNothing`;
    - `unionOfChunkTopicsIsSaved`;
    - `truncatedChunkIsSplitUntilItFits`;
    - `chunkBelowMinimumFailsWithListTooLong`;
    - `noTopicFromListLeavesDoneWithoutTopics`;
    - `unavailableDuringTaggingReturnsWithoutSpendingAttempt`;
    - `failureOnLastAttemptIsFailed`;
    - `fourthClaimIsTooManyAttempts`.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make check` termina con 0.
- [ ] **Paso 4:** commit «feat: carril de etiquetado por tema en el núcleo».

### Tarea 3: PostgreSQL del etiquetado · Sonnet

Rama `task/fase-2-conocimiento/etiquetado-postgres`. Spec 4, 5.2 y 5.3.

**Ficheros:**
- **Crear:** `PostgresTaggingRepository`, con `PostgresTaggingRepositoryIT`.
- **Modificar:** `PostgresDocumentRepository.replacePages`, que borra el `document_tagging` del documento en la misma transacción, con su prueba, y `AdapterConfiguration`.

**Interfaces que consume:** `TaggingRepository` y `TaggingJob` (tarea 2), y `TopicRepository` (tarea 1).

**Valores:**
- `claimNext`:
  - `FOR UPDATE SKIP LOCKED` sobre `document` con `status = 'READY'`, cuya empresa tiene algún tema vivo;
  - candidatos y orden del spec 5.2: primero los que no tienen fila, después los de versión anterior que no están en `TAGGING`, y los `TAGGING` con `deadline_at < now`. Dentro de cada grupo, por `document.seq`;
  - crea o actualiza su fila a `TAGGING`, con un intento más y `deadline_at = now + chunkTimeout × tramos + 5 min`. El número de tramos lo da `TaggingChunks.of` sobre las páginas, que se cargan en la misma transacción;
  - en `CompanyScope.all()` y `ProjectVisibility.all()`.
- `saveResult` y `decide`:
  - `SELECT … FROM document_tagging WHERE document_id = :id FOR UPDATE`, creando la fila si no existe;
  - `saveResult` borra los `document_topic` con `source = 'AI'` que no tengan `decided_by` e inserta los nuevos, saltándose los temas con una decisión del administrador;
  - deja la fila `DONE` con la versión con la que se tomó.
- `pendingCount`: documentos `READY` visibles, con el filtro de empresa y de proyectos, sin fila `DONE` en la versión actual.
- `backlog`: los mismos documentos, sin filtro de proyectos, porque lo pide un administrador.
  - `pendingDocuments` es su número;
  - `oldestPendingSince` es el mínimo, entre ellos, de `greatest(d.uploaded_at, d.processing_started_at, c.topic_list_changed_at)`, que es vacío si no hay ninguno (spec 5.2 bis).

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `claimsUntaggedBeforeOutdated`;
  - `skipsLockedRows`;
  - `retakesExpiredTagging`;
  - `failedWithCurrentVersionIsNotRetaken`;
  - `failedWithOldVersionIsRetaken`;
  - `saveResultKeepsAdminDecisions`;
  - `adminDecisionDuringTaggingSurvives`: dos conexiones; la decisión entra entre la toma y el guardado;
  - `replacingPagesRequeuesTagging`;
  - `pendingCountCountsFailedAndRespectsVisibility`;
  - `backlogGivesCountAndOldestWaitingSince`, que incluye un documento leído por OCR después de subirlo y un cambio de la lista;
  - `rowLevelSecurityHidesOtherCompanyTagging`.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0.
- [ ] **Paso 4:** commit «feat: etiquetado por tema en PostgreSQL».

### Tarea 4: Adaptador de Ollama, programador y despliegue · Sonnet

Rama `task/fase-2-conocimiento/adaptador-ollama`. Spec 7.

**Ficheros:**
- **Crear:**
  - el módulo `api/odin-adapter-tagger-ollama/` (`OllamaTopicTagger.java` en `com.e2d.odin.adapter.taggerollama`), con `OllamaTopicTaggerTest`;
  - `bootstrap/TaggingScheduler.java` y `TaggingConfiguration.java`.
- **Modificar:** `api/pom.xml`, `odin-bootstrap/pom.xml`, `application.yaml`, `deploy/compose.yaml`, `deploy/.env.example`, el `Makefile` (objetivo `tagger-model`) y el `README.md`.

**Interfaces que consume:** `TopicTagger`, `TaggerAnswer` y `TagPendingDocuments` (tarea 2).

**Valores:**
- `OllamaTopicTagger(URI baseUrl, String model, Duration timeout, OptionalInt numGpu, OptionalInt numThread, HttpClient client)`.
- `isAvailable()`: `GET /api/tags` en 5 s, y el modelo está en la lista.
- `tag`: `POST /api/generate` con:
  - `{ model, prompt, stream: false, think: false, options: { temperature: 0, num_predict: 60, num_ctx: 4096, num_gpu?, num_thread? } }`.
  - La respuesta da `TaggerAnswer(response, prompt_eval_count, 4096, "<modelo>; instrucciones v1")`.
  - Conexión rechazada: `TaggerUnavailableException`. Otro 4xx o 5xx, o el tiempo agotado: `TaggerFailedException`.
- `application.yaml`: `odin.tagger.url`, `model`, `window`, `timeout`, `chunk-characters`, `num-gpu` y `num-thread`, con los valores por defecto del spec 7, y `odin.timezone: ${ODIN_TIMEZONE:Europe/Madrid}`.
- `TaggingConfiguration`: solo con `odin.tagger.url` no vacía. `@Scheduled(fixedDelay = 60000)`. Si el bloque 1 no fijó `spring.task.scheduling.pool.size`, se fija aquí a 2, y si ya está, a 3.
- `compose.yaml`:
  - servicio `tagger` con `ollama/ollama:<versión fijada>`, `profiles: ["tagging"]`, el volumen `tagger-models` y sin puertos publicados;
  - en `api`, `ODIN_TAGGER_*` con valor por defecto vacío.
- `make tagger-model MODEL=qwen3.5:9b`: `docker compose … --profile tagging run --rm tagger ollama pull $(MODEL)`.
- `.env.example`: las variables comentadas, y que en el Mac vale también el Ollama del propio equipo.
- **`README`, apartado «Etiquetado por tema»** (guía de instalación, D-043):
  - el equipo tiene que estar encendido durante la ventana del etiquetado, o hay que cambiar `ODIN_TAGGER_WINDOW`;
  - sin eso los documentos se pueden buscar, pero no tienen temas.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `OllamaTopicTaggerTest`, contra `HttpServer` del JDK:
    - `sendsMeasuredOptions`;
    - `sendsNumGpuOnlyWhenConfigured`;
    - `answerCarriesPromptTokensAndContext`;
    - `unknownNamesAreIgnored`, de punta a punta con `TopicMatcher`;
    - `modelMissingIsUnavailable`;
    - `connectionRefusedIsUnavailable`;
    - `serverErrorIsFailure`;
    - `timeoutIsFailure`.
  - `TaggingConfigurationIT.noUrlMeansNoTaggingBeans`.
- [ ] **Paso 2:** implementa. `LayerRulesTest` incluye el módulo nuevo y sigue en verde.
- [ ] **Paso 3:** comprueba con `docker compose -f deploy/compose.yaml --profile tagging config` que el servicio es válido, sin arrancarlo. `make check` y `make demo` terminan con 0.
- [ ] **Paso 4:** commit «feat: adaptador de Ollama para el etiquetado y su carril».

### Tarea 5: Revisión, trabajador, MCP y demostración · Sonnet

Rama `task/fase-2-conocimiento/temas-trabajador`. Spec 5.3, 5.4 y 6.

**Ficheros:**
- **Crear:**
  - `application/topic/ReviewDocumentTopics.java` y `GetTaggingStatus.java`, con sus pruebas;
  - `deploy/demo/tagger-stub.mjs` y `deploy/demo/compose.tagger-stub.yaml`.
- **Modificar:**
  - aplicación: `ListDocuments`, `DescribeMe`, `MeView`, `ListCompanyDocuments` y sus pruebas;
  - web: `AdminDocumentController`, `WorkerController`, `Requests` y `Responses`;
  - MCP: `mcp/src/tools/listDocuments.ts`, `mcp/src/server.ts`, `mcp/src/api/client.ts` y sus pruebas;
  - demostración: `deploy/demo/run-demo.sh` y `mcp/scripts/demo-client.ts`;
  - contrato y tipos.

**Valores:**
- `ReviewDocumentTopics`: el administrador de la empresa o el del grupo, con 404 y luego 403.
  - `add` deja `ADMIN` y `ACTIVE`;
  - `remove` deja `REMOVED`, con quién y cuándo;
  - `retag` llama a `TaggingRepository.retag`.
- `ListDocuments` gana `Optional<TopicId> topicId`. Un tema de otra empresa o borrado da 404, «Tema no encontrado».
- `DescribeMe`: `topics` y `tagging.pendingDocuments` con `pendingCount(company, visibility)`.
- MCP `listDocuments`:
  - gana `topic`, un nombre; el MCP lo traduce con `me.topics`, y un nombre desconocido es un error de la herramienta que lista los temas válidos;
  - con tema, la respuesta añade `taggingPending`.
- `INSTRUCTIONS` gana las tres frases del spec 5.4.
- `tagger-stub.mjs`:
  - responde a `GET /api/tags` con el modelo `stub`;
  - responde a `/api/generate` con «agua y humedades» si el texto lleva «filtración» o «fuga», y con «otros» en caso contrario;
  - `prompt_eval_count` es 100.
- `GetTaggingStatus`: administradores de la empresa o el del grupo, con 404 y luego 403. Ruta `GET /v1/admin/companies/{companyId}/tagging-status`.
- `run-demo.sh` usa el etiquetador de prueba con `ODIN_TAGGER_WINDOW=always`.
- `README`, apartado de la demostración, con esta frase literal: «El etiquetador de prueba (`tagger-stub`) solo sirve para las pruebas automáticas de `make demo`. Una demostración ante una persona usa siempre el modelo real.».
- `demo-client.ts`, paso nuevo:
  1. crea los temas «agua y humedades» y «otros»;
  2. espera a que un documento con «filtración» quede `DONE`;
  3. `listDocuments(topic: "agua y humedades")` lo devuelve con `source: AI`;
  4. el administrador quita el tema y pide volver a etiquetar;
  5. al terminar sigue quitado.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `ReviewDocumentTopicsTest`:
    - `addsAdminTopic`;
    - `removesAiTopic`;
    - `readdingRemovedTopicMakesItAdminActive`;
    - `retagRequeues`;
    - `adminOfAnotherCompanyIsForbidden`.
  - `GetTaggingStatusTest`: `returnsBacklogAndWindow`, `workerIsForbidden` y `adminOfAnotherCompanyIsForbidden`.
  - `ListDocumentsTest`:
    - `filtersByTopicWithActiveTopicsOnly`;
    - `foreignTopicIsNotFound`;
    - `restrictedProjectOfOthersIsInvisible`.
  - `DescribeMeTest`: `listsTopicsAndPendingCount` y `pendingCountIgnoresInvisibleProjects`.
  - Web: `WorkerApiTest.listDocumentsByTopic`, `AdminApiTest`, una prueba por ruta de temas de documento, e `IsolationTest.otherCompanyAdminCannotReviewForeignDocumentTopics`.
  - MCP:
    - `listDocuments.test.ts`: `translatesTopicNameToId`, `unknownTopicListsValidOnes` y `reportsTaggingPending`;
    - la prueba de `INSTRUCTIONS`.
- [ ] **Paso 2:** implementa. Regenera el contrato y los tipos.
- [ ] **Paso 3:** `make demo` dos veces seguidas y `make check` terminan con 0.
- [ ] **Paso 4:** commit «feat: revisión de temas, listado por tema y demostración».

### Tarea 6: Batería con el modelo real · Sonnet, y la ejecuta el CTO

Rama `task/fase-2-conocimiento/bateria-etiquetado`. Spec 8 y 9.

**Ficheros:**
- **Crear:**
  - `api/odin-adapter-tagger-ollama/src/test/resources/tagging-battery/`, con:
    - las fichas de `casos-control/control-1`, `control-2` y `control-3`;
    - sus claves (`claves-casos-control/control-*.json`);
    - los documentos de varias páginas de `corpus-limpio`.
    - Todo copiado de la rama `feature/planificacion-tecnica` del repositorio OCR.
  - `TaggingBatteryIT`, con `@Tag("model")`, fuera de `make check`.
- **Modificar:** el `Makefile`, con el objetivo `tagging-battery`, y el `pom.xml` del módulo, para excluir la etiqueta `model` por defecto.

**Valores:**
- La lista de temas es la de `clasificar.py` (`DEFINITIONS`), copiada como datos de prueba. La empresa se llama «Administraciones Ribera».
- Usa `TaggingChunks`, `TaggingInstructions`, `OllamaTopicTagger` y `TopicMatcher` reales contra `ODIN_TAGGER_URL`.
- **Salida en `target/tagging-battery.json`, por cada conjunto de control:**
  - exhaustividad por tema y global;
  - etiquetas de más;
  - segundos por documento (medio y máximo).
- **Además:** memoria según `GET /api/ps`, y si corrió en CPU (`size_vram == 0`).
- **Documentos largos:** número de tramos, tramos partidos por recorte, temas y segundos.
- `make tagging-battery`: `cd api && mvn -q -pl odin-adapter-tagger-ollama verify -Dgroups=model`, con `ODIN_TAGGER_URL`, `ODIN_TAGGER_MODEL` y `ODIN_TAGGER_NUM_GPU` del entorno.

- [ ] **Paso 1 (Sonnet):** copia los datos y escribe `TaggingBatteryIT` con una aserción mínima: el fichero de salida existe y cada conjunto tiene todas sus fichas. Comprueba que `make check` no la ejecuta.
- [ ] **Paso 2 (CTO):** ejecuta `make tagging-battery` contra el Ollama de este equipo, en CPU (`ODIN_TAGGER_NUM_GPU=0`, `ODIN_TAGGER_NUM_THREAD=4`), con `qwen3.5:9b` y con `qwen3.5:4b-q4_K_M`. Los dos modelos ya están descargados (T-010).
  - Una de las dos ejecuciones se hace con el puente del lector OCR arrancado y el modelo cargado (bloque 1). Se anota la memoria de los dos juntos: lo que dice Ollama más el pico del puente (D-043).
  - Se anota la máquina: modelo, chip, memoria y sistema.
- [ ] **Paso 3 (CTO):** compara con T-010 con el mismo modelo.
  - Si una exhaustividad baja, se investiga primero el preámbulo generalizado: se repite con el preámbulo medido para aislar la causa.
  - Las cifras van al informe del bloque y a T-017. Si siguen peor, se plantea al CEO.
  - Si la memoria o el tiempo superan lo que da un equipo modesto, también se plantea al CEO (D-030, D-043).
- [ ] **Paso 4:** commit «test: batería de etiquetado con el modelo real».

### Cierre del bloque · CTO

- Revisión final de toda la rama con Opus, con este foco:
  - seguridad por filas de las tres tablas nuevas;
  - la carrera con el administrador;
  - que no se guardan temas de texto recortado.
- `make check` y `make demo` ejecutados por el CTO.
- **Memoria de Docker Desktop** (spec 7): si se quiere usar el servicio `tagger` de Compose en este Mac, el CTO comprueba antes que la máquina virtual tiene memoria para `qwen3.5:9b`. Si no la tiene, se usa el Ollama del equipo.
- Informe del bloque, T-017 en el registro técnico y copia del informe en la carpeta de notas para el CEO.

## Orden y paralelismo

- En serie: 1, 2, 3, 4, 5 y 6. Cada una usa la anterior.
- La tarea 4 puede ir en paralelo con la 3: el adaptador solo depende de los puertos de la tarea 2. Las dos tocan el cableado de `odin-bootstrap`, así que se mergean una detrás de otra.

## Modelos de los revisores

- **Opus:** tareas 1, 3 y 5, que tocan seguridad por filas, aislamiento y la visibilidad del trabajador.
- **Sonnet:** tareas 2, 4 y 6.
