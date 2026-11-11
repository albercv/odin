# Odín · Fase 2, bloque 1: lector OCR — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que los PDF escaneados y las imágenes se lean con Unlimited-OCR 2 a través de un servicio lector, sin que nada del Mac entre en Odín.

**Arquitectura:**
- **En Odín:** un carril propio con su caso de uso, un puerto `ReaderService` y un adaptador HTTP en su propio módulo.
- **En el repositorio OCR:** un puente HTTP de la biblioteca estándar que reutiliza `uocr.pipeline.run` en modo `single`.
- **Entre los dos:** un contrato OpenAPI que es de Odín.

**Tecnología:**
- Odín: Java 21, Spring Boot 4.1.2, `java.net.http`, Jackson, PostgreSQL 17.10 con Flyway, React 19 y Vitest.
- Repositorio OCR: Python 3.12, `http.server`, `uocr`, pytest y uv.

**Diseño:** `docs/superpowers/specs/2026-11-11-lector-ocr-design.md` (aquí, «spec»). Amplía el spec base (`2026-11-09-api-central-design.md`) y se apoya en el de la 2a (`2026-11-10-identidad-pertenencias-design.md`). Quien ejecute una tarea lee los tres.

## Cuándo se ejecuta

- **Odín (tareas 1, 4 a 7):** cuando el CEO lo diga, tras el cierre de la 2a. Antes de empezar, se actualiza `feature/fase-2-conocimiento` con la punta de `feature/identidad-pertenencias`.
- **Repositorio OCR (tareas 2 y 3):** no tocan Odín ni dependen de la 2a. Pueden ir antes si el CEO lo autoriza, en una rama `feature/puente-odin` que sale de `develop`.

## PDF mixtos: opción tomada (D-040 y D-041)

El CEO dejó elegir entre leer solo las páginas sin texto, si cabía sin mucho coste, o avisar en el panel de qué páginas no se han leído. **Este plan avisa.**

Leerlas no cabe sin coste, por cuatro motivos:
- obliga a añadir al contrato un parámetro `pages`;
- obliga a un carril que trabaje sobre documentos que ya están `READY`, con su propio bloqueo y sus propios intentos, porque el documento tiene que seguir buscable mientras tanto;
- obliga a decidir qué pasa cuando algunas páginas sí se leen y otras fallan;
- obliga a otra regla de reintento.

Eso duplica el riesgo del modelo de estados de este bloque. El aviso cuesta una columna, su cálculo en el carril local, una migración que lo rellena y una etiqueta en el panel.

El CEO aceptó el motivo (D-041). Leer esas páginas queda como primera mejora posterior.

## Restricciones globales

- **TDD con evidencia:** primero la prueba; se ejecuta y se guardan en el informe la orden y la salida en rojo antes del código. Un fallo de compilación solo vale como rojo para la primera prueba de una clase nueva.
- **Capas (Odín):** `odin-domain` solo `java.*`; `odin-application` solo `odin-domain` y `java.*`; los adaptadores no dependen entre sí; Spring solo en los adaptadores y en `odin-bootstrap`. ArchUnit lo comprueba.
- **Clean Code (D-029):** métodos de 30 líneas como máximo (Checkstyle y ESLint), sin lógica duplicada y con comentarios mínimos.
- **Nombres:**
  - Odín: identificadores en inglés, explicativos y en camelCase.
  - Repositorio OCR: conserva su estilo (snake_case) y sus reglas de `CLAUDE.md`.
  - Mensajes, documentación y commits en español.
- **Unlimited-OCR 2, nunca la v1**, ni como respaldo. Ningún LLM toca el texto. No se guardan ni se devuelven coordenadas ni tipos de bloque.
- **Sin texto del documento** en registros ni en mensajes de error, en los dos repositorios.
- **Nada del Mac en el código común de Odín:** ni rutas, ni MLX, ni `host.docker.internal`. Lo de este equipo va en `deploy/.env`.
- **Nada global:** Maven desde `api/`, `node_modules` locales y `uv` en el repositorio OCR. Sin dependencias nuevas en Odín más allá de las que ya trae Spring Boot.
- **Contrato:** si cambia la API, en la misma tarea se regeneran `api/openapi.json` (`cd api && mvn -q verify -Dodin.updateOpenApi=true`) y los tipos (`npm run generate:types` en `panel/` y en `mcp/`).
- **Cierre de cada tarea:**
  - Odín: `make check` termina con 0; desde la tarea 5, también `make demo`.
  - Repositorio OCR: `make test` termina con 0.
- **Dónde se trabaja:**
  - Odín: en la copia de trabajo `.worktrees/fase-2`, nunca en la carpeta principal. No se ejecutan `make up` ni `make down` y no se toca `deploy/.env`.
  - Repositorio OCR: en una copia de trabajo propia, nunca en la carpeta principal, que es la del propietario.
- **Git:**
  - cada tarea en su rama: `task/fase-2-conocimiento/<tarea>` en Odín y `task/puente-odin/<tarea>` en el repositorio OCR;
  - commits en español, sin atribución a Claude ni `Co-Authored-By`;
  - sin push;
  - el merge `--no-ff` lo hace el CTO.
- **Migración:** `V10__reader_lane.sql`. Si la 2a cerró con otra numeración, la siguiente libre.
- **Datos:** solo ficticios. Las muestras del repositorio OCR (`samples/`) son sintéticas.

## Foco de revisión

1. **El servicio está apagado o la red no llega:** los documentos esperan sin consumir intentos y sin pasar a `FAILED`. Pruebas en la tarea 4 (`unavailableServiceKeepsDocumentWaitingWithoutSpendingAttempts`) y en la tarea 6 (`connectionRefusedIsUnavailable`).
2. **Una lectura larga no para el carril local:** un DOCX subido mientras se lee un escaneado de 30 minutos queda `READY`. Prueba en la tarea 6 (`localLaneKeepsRunningWhileReaderLaneIsBlocked`).
3. **Una página falla a mitad de documento:** no se guarda nada y la cita nunca apunta a un documento incompleto. Pruebas en la tarea 2 (`failedPageFailsWholeDocument`) y en la tarea 4 (`failureOnLastAttemptStoresNoPages`).
4. **Una respuesta del servicio que no cumple el contrato** (páginas que faltan, repetidas o sin `reader`): se trata como fallo y no se guarda. Prueba en la tarea 6 (`pagesWithGapIsReaderFailure`).
5. **Documentos que ya esperaban con intentos acumulados** en una instalación de la fase 1.1: se leen al llegar el lector. Prueba en la tarea 5 (`migrationResetsAttemptsOfDocumentsWaitingForReader`).
6. **Un PDF mixto ya guardado antes de este bloque:** el panel dice sus páginas sin texto sin volver a procesarlo. Prueba en la tarea 5 (`migrationFillsPagesWithoutTextOfExistingDocuments`).

---

## Interfaces compartidas (fuente única de nombres)

```java
// domain/document
public enum DocumentStatus { RECEIVED, PROCESSING, READY, FAILED, PENDING_READER, READER_PROCESSING }
// Document gana:
public Document awaitReader();                          // PENDING_READER, attemptCount 0
public Document startReading(Instant now);              // READER_PROCESSING, attemptCount + 1, processingStartedAt now
public Document returnToReaderQueue(boolean countAttempt); // PENDING_READER; sin contar: attemptCount - 1
// markPendingReader() desaparece: su único uso pasa a awaitReader().
// Document gana el componente final `List<Integer> pagesWithoutText` (vacía por defecto) y:
public Document markReady(boolean paginated, List<Integer> pagesWithoutText);  // sustituye a markReady(boolean)

// application/shared — InstallationSettings gana `boolean scannedDocumentsSupported`

// application/port
public interface ReaderService {
    boolean isAvailable();
    ReadResult read(byte[] content, DocumentFormat format);  // ReadResult ya existe
}
public class ReaderUnavailableException extends RuntimeException { }   // no responde, no conecta
public class UnreadableDocumentException extends RuntimeException { }  // 413, 415, 422
public class ReaderFailedException extends RuntimeException { }        // 500, tiempo agotado, contrato roto
// DocumentRepository gana:
Optional<Document> claimNextForReader(Instant now, Duration staleAfter);

// application/document
final class DocumentPages {
    static List<DocumentPage> from(Document document, ReadResult result);
    static List<Integer> withoutText(List<DocumentPage> pages);   // números de las páginas en blanco tras strip()
}
public class ProcessDocumentsWithReader {
    public ProcessDocumentsWithReader(DocumentRepository documents, OriginalFileStore files, ReaderService reader,
            ReaderSettings settings, Clock clock);
    public boolean processNext();
}
// application/shared
public record ReaderSettings(Duration readTimeout, int maxAttempts) {
    public Duration staleAfter() { return readTimeout.plusMinutes(5); }
}
```

- **Mensajes nuevos** (constantes en `ProcessDocumentsWithReader`, texto literal del spec 5.3):
  - `READER_FAILED` = «El lector OCR no pudo leer el documento. Reinténtalo.»;
  - `NO_TEXT_FOUND` = «El lector OCR no encontró texto en el documento.».
  - `UNREADABLE` y `TOO_MANY_ATTEMPTS` se reutilizan de `ProcessPendingDocuments`.
- **Contrato del servicio lector:** `docs/contratos/servicio-lector.openapi.yaml` (tarea 1). Es la fuente de las formas JSON en los dos repositorios.

---

### Tarea 1: Contrato del servicio lector · Haiku

Rama `task/fase-2-conocimiento/contrato-lector` (Odín). Spec 4.

**Ficheros:** crear `docs/contratos/servicio-lector.openapi.yaml` y `docs/contratos/ejemplos/servicio-lector/` (un JSON por respuesta).

**Valores:**
- OpenAPI 3.1, título «Servicio lector de Odín», versión `1.0.0`.
- Rutas `POST /read` (multipart, campo `file`) y `GET /health`.
- Seguridad `bearerAuth` opcional, descrita como «solo si el servicio se arrancó con secreto».
- Respuestas y códigos del spec 4, literales: 200, 400 `bad_request`, 401 `unauthorized`, 413 `too_large`, 415 `unsupported`, 422 `unreadable` y 500 `reader_failed`.
- Ejemplos:
  - `read-200.json`, con dos páginas, la segunda vacía, y `reader` `{ "name": "unlimited-ocr-2", "version": "mlx-community/Unlimited-OCR-2-8bit; uocr 0.1.0" }`;
  - uno por cada error;
  - `health-200.json`.

- [ ] **Paso 1:** escribe el fichero y los ejemplos.
- [ ] **Paso 2:** comprueba que es OpenAPI válido con el `openapi-typescript` que ya está instalado en `mcp/`, sin descargar nada: `cd mcp && npx openapi-typescript ../docs/contratos/servicio-lector.openapi.yaml -o "$TMPDIR/servicio-lector.d.ts"`. Sale con 0.
- [ ] **Paso 3:** commit «docs: contrato del servicio lector».

### Tarea 2: Núcleo del puente, sin modelo · Sonnet

Rama `task/puente-odin/nucleo` (repositorio OCR). Spec 6.

**Ficheros:**
- **Crear:** `uocr/service.py`, `tests/test_service.py` y `tests/fixtures/reader_contract/`, una copia de los ejemplos de la tarea 1.
- **Modificar:** `uocr/inputs.py`, que gana `load_pages_from_bytes`, y `tests/test_inputs.py`.

**Interfaces que produce:**

```python
class ServiceError(Exception):            # code: "bad_request" | "too_large" | "unsupported" | "unreadable" | "reader_failed"
    def __init__(self, code: str, status: int, detail: str): ...
def load_pages_from_bytes(content: bytes, dpi: int = 200) -> list[Image.Image]   # PDF, PNG, JPEG, TIFF con fotogramas
def parse_multipart_file(content_type: str, body: bytes) -> bytes                # el campo "file"
def page_texts(blocks: list[Block], page_count: int) -> list[str]                # to_markdown por página, sin comentarios HTML
def read_document(content: bytes, engine, max_bytes: int) -> dict                # {"pages": [...], "reader": {...}}
def reader_info(model_id: str) -> dict                                           # {"name": "unlimited-ocr-2", "version": "<model_id>; uocr 0.1.0"}
```

**Valores:**
- Tipo por contenido: `%PDF-` va a pypdfium2; si no, `PIL.Image.open(BytesIO)`. Otra cosa, o un fichero que Pillow no abre, es `unsupported` (415). Un PDF que pypdfium2 no abre es `unreadable` (422).
- Sin escribir nada en disco.
- `read_document` llama a `run(pages, "single", 1, engine)`. Una página falla si su petición tiene `error` o si `generation_tokens == TOKENS_PER_PAGE`. Si falla una, `ServiceError("reader_failed", 500, "página N: …")`, con un detalle sin texto.
- `page_texts` agrupa los bloques por `page`, aplica `to_markdown` a cada grupo y quita todo `<!-- … -->`. Una página sin bloques da `""`.

- [ ] **Paso 1:** pruebas, reutilizando `FakeEngine` de `tests/test_pipeline.py`; comprueba que fallan:
  - `test_inputs.py`:
    - `test_bytes_pdf_renders_every_page`;
    - `test_bytes_png_is_one_page`;
    - `test_bytes_multiframe_tiff_gives_one_page_per_frame`;
    - `test_bytes_unknown_is_unsupported`;
    - `test_bytes_broken_pdf_is_unreadable`.
  - `test_service.py`:
    - `test_multipart_extracts_file_field`;
    - `test_multipart_without_file_is_bad_request`;
    - `test_pages_are_numbered_from_one_without_gaps`;
    - `test_page_text_has_no_html_comments`;
    - `test_empty_page_gives_empty_text`;
    - `test_failed_page_fails_whole_document`;
    - `test_max_tokens_page_fails_whole_document`;
    - `test_too_large_is_rejected_before_reading`;
    - `test_response_matches_contract_example`, que compara las claves y los tipos con `tests/fixtures/reader_contract/read-200.json`;
    - `test_reader_info_names_model_and_uocr_version`.
- [ ] **Paso 2:** implementa `load_pages_from_bytes` y `uocr/service.py`.
- [ ] **Paso 3:** `make test` termina con 0.
- [ ] **Paso 4:** commit «feat: núcleo del puente del servicio lector».

### Tarea 3: Servidor del puente y prueba con el modelo · Sonnet

Rama `task/puente-odin/servidor` (repositorio OCR). Spec 4, 6 y 7.

**Ficheros:**
- **Crear:** `serve.py`, `tests/test_serve.py` y `tests/test_service_model.py`.
- **Modificar:** `Makefile`, que gana `serve` con `QUANT ?= 8`, y `README.md`, con el apartado «Servicio lector para Odín».

**Interfaces que consume:** las de la tarea 2.

**Valores:**
- Opciones de `serve.py`:
  - `--host` (`127.0.0.1`), `--port` (`8077`) y `--quant` (8), o `--model`;
  - `--max-bytes` (52428800);
  - `--token-env` (nombre de la variable con el secreto; sin ella, no hay secreto).
- `http.server.HTTPServer`, una petición cada vez.
- **Al arrancar:**
  - se niega con salida 2 si el modelo no contiene `Unlimited-OCR-2` («Solo se admite Unlimited-OCR 2»);
  - se niega si el tokenizador no tiene `<PAGE>`;
  - se niega si `--host` no es `127.0.0.1` y no hay secreto («Fuera de 127.0.0.1 hace falta un secreto»).
- Con secreto, las dos rutas sin `Authorization: Bearer <secreto>` correcto dan 401 `unauthorized`. La comparación es de tiempo constante (`hmac.compare_digest`).
- Registro por lectura: páginas, segundos y pico MLX. Nunca el texto ni el nombre del fichero.
- `make serve`: `uv run python serve.py --quant $(QUANT) $(ARGS)`.

- [ ] **Paso 1:** pruebas con `FakeEngine` y el servidor en un puerto libre, en un hilo; comprueba que fallan:
  - `test_health_ok_with_reader`;
  - `test_read_returns_pages`;
  - `test_each_error_code_maps_to_its_status`;
  - `test_refuses_model_that_is_not_unlimited_ocr_2`;
  - `test_refuses_tokenizer_without_page_tag`;
  - `test_refuses_non_loopback_host_without_token`;
  - `test_token_required_when_configured`;
  - `test_log_has_no_document_text`, con `caplog`.
- [ ] **Paso 2:** implementa `serve.py` y el `Makefile`.
- [ ] **Paso 3:** `tests/test_service_model.py`, marcado `model`: `read_document` con el motor real sobre `samples/factura_scan.pdf` y `samples/es_simple_png.png`. Comprueba con `check_amounts` los importes de sus `.ref.json`. Ejecuta `uv run pytest -m model tests/test_service_model.py` y guarda la salida en el informe.
- [ ] **Paso 4:** README: cómo arrancarlo, las opciones y que solo corre en Apple Silicon.
- [ ] **Paso 5:** `make test` termina con 0. Commit «feat: servidor del puente del servicio lector».

### Tarea 4: Carril del lector en el núcleo · Sonnet

Rama `task/fase-2-conocimiento/carril-lector` (Odín). Spec 5.1 a 5.3.

**Ficheros:**
- **Crear:**
  - `application/port/ReaderService.java`, `ReaderUnavailableException.java`, `UnreadableDocumentException.java` y `ReaderFailedException.java`;
  - `application/document/DocumentPages.java` y `ProcessDocumentsWithReader.java`;
  - `application/shared/ReaderSettings.java`;
  - el doble `application/fake/ScriptedReaderService.java`;
  - `ProcessDocumentsWithReaderTest` y `DocumentPagesTest`.
- **Modificar:** `DocumentStatus`, `Document`, `DocumentTest`, `ProcessPendingDocuments`, `ProcessPendingDocumentsTest`, `DocumentRepository` y `InMemoryDocumentRepository`, con `claimNextForReader` en el doble.

**Interfaces que produce:** las de «Interfaces compartidas».

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `DocumentTest`:
    - `awaitReaderResetsAttempts`;
    - `startReadingCountsAttempt`;
    - `returnToReaderQueueWithoutCountingUndoesTheAttempt`.
  - `ProcessPendingDocumentsTest`: `imageWithoutLocalReaderAwaitsReaderWithZeroAttempts` y `pdfWithoutTextAwaitsReaderWithZeroAttempts`.
  - `DocumentPagesTest`: `paginatedResultKeepsPages` y `nonPaginatedResultIsOnePage`, los dos casos que hoy cubre `store`, más `withoutTextListsBlankPages`.
  - `ProcessPendingDocumentsTest.mixedPdfIsReadyWithItsPagesWithoutText`: un PDF con texto en las páginas 1 y 3, y en blanco la 2 y la 4, queda `READY` con `pagesWithoutText` `[2, 4]`.
  - `ProcessDocumentsWithReaderTest`:
    - `unavailableServiceClaimsNothing`;
    - `readsPendingDocumentAndMarksReady`, con `pagesWithoutText` vacía;
    - `unavailableServiceKeepsDocumentWaitingWithoutSpendingAttempts`;
    - `unreadableIsFailedWithUnreadableReason`;
    - `readerFailureBeforeLastAttemptReturnsToQueueKeepingAttempts`;
    - `failureOnLastAttemptStoresNoPages`, que acaba en `FAILED` con `READER_FAILED`;
    - `allBlankPagesIsFailedWithNoTextFound`;
    - `fourthClaimIsTooManyAttempts`;
    - `staleReaderProcessingIsClaimedAgain`.
- [ ] **Paso 2:** implementa. `ProcessPendingDocuments.store` y `ProcessDocumentsWithReader` usan `DocumentPages.from`. Ninguno duplica la conversión.
- [ ] **Paso 3:** `make check` termina con 0.
- [ ] **Paso 4:** commit «feat: carril del servicio lector en el núcleo».

### Tarea 5: PostgreSQL: toma del carril y migración · Sonnet

Rama `task/fase-2-conocimiento/carril-lector-postgres` (Odín). Spec 5.1 y 5.2.

**Ficheros:**
- **Crear:** `V10__reader_lane.sql`.
- **Modificar:** `PostgresDocumentRepository`, `PostgresDocumentRepositoryIT` y `MigrationFromPhaseOneIT`, o la prueba de migración que exista tras la 2a.

**Interfaces que consume:** `DocumentRepository.claimNextForReader` y `Document.startReading` de la tarea 4.

**Valores:**
- `V10`:
  - `UPDATE document SET attempt_count = 0 WHERE status = 'PENDING_READER'`. La columna `status` es texto sin restricción, así que no hace falta nada para el estado nuevo;
  - `ALTER TABLE document ADD COLUMN pages_without_text integer[] NOT NULL DEFAULT '{}'`;
  - la rellena para los documentos `READY` y paginados con los números de sus páginas cuyo `btrim(text)` es vacío.
- `PostgresDocumentRepository` lee y guarda `pages_without_text`.
- `claimNextForReader`:
  - toma `status = 'PENDING_READER' OR (status = 'READER_PROCESSING' AND (processing_started_at IS NULL OR processing_started_at <= :threshold))`;
  - `ORDER BY seq LIMIT 1 FOR UPDATE SKIP LOCKED`, en `CompanyScope.all()` y `ProjectVisibility.all()`;
  - aplica `startReading(now)` y lo guarda en la misma transacción, como `claimNextForProcessing`.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `PostgresDocumentRepositoryIT`:
    - `claimNextForReaderTakesPendingReaderOnly`;
    - `claimNextForReaderSkipsLockedRows`;
    - `claimNextForReaderRetakesStaleReaderProcessing`;
    - `localClaimNeverTakesReaderStates`.
  - `migrationResetsAttemptsOfDocumentsWaitingForReader`: aplica hasta la migración anterior, inserta un `PENDING_READER` con 3 intentos, migra y comprueba que tiene 0.
  - `migrationFillsPagesWithoutTextOfExistingDocuments`: un `READY` con las páginas 1 y 3 en blanco queda con `[1, 3]`.
  - `PostgresDocumentRepositoryIT.storesPagesWithoutText`.
- [ ] **Paso 2:** escribe `V10` y `claimNextForReader`.
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0. `SearchBatteryIT` mantiene 27 de 30.
- [ ] **Paso 4:** commit «feat: toma del carril del lector en PostgreSQL».

### Tarea 6: Adaptador HTTP, programador y despliegue · Sonnet

Rama `task/fase-2-conocimiento/adaptador-lector` (Odín). Spec 5.2 y 5.4.

**Ficheros:**
- **Crear:**
  - el módulo `api/odin-adapter-reader-service/` (`pom.xml` y `HttpReaderService.java`, en `com.e2d.odin.adapter.readerservice`);
  - `HttpReaderServiceTest`;
  - `bootstrap/ReaderProcessingScheduler.java` y `ReaderLaneConfiguration.java`;
  - `ReaderLaneIT` en `odin-bootstrap`.
- **Modificar:**
  - `api/pom.xml` (módulo nuevo) y `odin-bootstrap/pom.xml`;
  - `application.yaml` (`odin.reader.*` y `spring.task.scheduling.pool.size: 2`);
  - `SettingsConfiguration`;
  - `deploy/compose.yaml` y `deploy/.env.example`.

**Interfaces que consume:** `ReaderService`, sus excepciones, `ReaderSettings` y `ProcessDocumentsWithReader` (tarea 4).

**Valores:**
- `HttpReaderService(URI baseUrl, Duration readTimeout, Duration connectTimeout, Optional<String> token, HttpClient client)`.
- `isAvailable()`: `GET /health` con `connectTimeout`; verdadero solo con 200 y `status: "ok"`.
- `read`:
  - `POST /read` multipart con el campo `file` y `filename="document"`;
  - mapeo de errores:
    - conexión rechazada o `HttpConnectTimeoutException`: `ReaderUnavailableException`;
    - 413, 415 o 422: `UnreadableDocumentException`;
    - 401: `ReaderUnavailableException`, porque es un fallo de configuración, no del documento, y se escribe en el registro del servidor;
    - 500, tiempo agotado o JSON fuera del contrato: `ReaderFailedException`.
  - Validación: páginas de 1 a n, sin huecos ni repetidas, y `reader.name` y `reader.version` con texto.
  - Devuelve `ReadResult(texts, true, name, version)`.
- Configuración en `application.yaml`:
  - `odin.reader.url: ${ODIN_READER_URL:}`;
  - `timeout: ${ODIN_READER_TIMEOUT:PT30M}`;
  - `connect-timeout: ${ODIN_READER_CONNECT_TIMEOUT:PT5S}`;
  - `token: ${ODIN_READER_TOKEN:}`.
- `ReaderLaneConfiguration` crea el adaptador, el caso de uso y el programador solo si `odin.reader.url` no está vacía (`@ConditionalOnExpression`). El programador usa `@Scheduled(fixedDelay = 30000)`.
- `SettingsConfiguration` rellena `InstallationSettings.scannedDocumentsSupported` con `!odin.reader.url.isBlank()`.
- `compose.yaml`: en `api`, `ODIN_READER_URL: ${ODIN_READER_URL:-}` y `ODIN_READER_TOKEN: ${ODIN_READER_TOKEN:-}`.
- `.env.example`: las dos variables comentadas, con una línea que diga que en un Mac con el puente del repositorio OCR la URL es la del puente vista desde Docker. Sin escribir `host.docker.internal`, que se documenta en el `README` de operación.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `HttpReaderServiceTest`, contra `com.sun.net.httpserver.HttpServer` en un puerto libre:
    - `sendsMultipartWithFileField`;
    - `sendsBearerTokenOnlyWhenConfigured`;
    - `healthOkIsAvailable`;
    - `connectionRefusedIsUnavailable`;
    - `unauthorizedIsUnavailable`;
    - `each4xxIsUnreadable`, para 413, 415 y 422;
    - `serverErrorIsReaderFailure`;
    - `timeoutIsReaderFailure`;
    - `pagesWithGapIsReaderFailure`;
    - `missingReaderIsReaderFailure`;
    - `returnsPagesInOrderAsPaginated`.
  - `ReaderLaneIT`: `noUrlMeansNoReaderLaneBeans`, `scannedDocumentsSupportedFollowsUrl` y `localLaneKeepsRunningWhileReaderLaneIsBlocked`. Esta última usa un `ReaderService` que se bloquea hasta que se le suelta y un DOCX subido mientras, que llega a `READY`.
- [ ] **Paso 2:** implementa el módulo, la configuración y el despliegue. Comprueba que `LayerRulesTest` incluye el módulo nuevo y sigue en verde.
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0. `make demo` no configura lector y debe seguir igual.
- [ ] **Paso 4:** commit «feat: adaptador HTTP del servicio lector y su carril».

### Tarea 7: Panel, MCP y demostración · Haiku

Rama `task/fase-2-conocimiento/lector-demo` (Odín). Spec 5.5 y 8.

**Ficheros:**
- **Crear:** `deploy/demo/reader-stub.mjs`, `deploy/demo/compose.reader-stub.yaml` y `deploy/demo/escaneo-ficticio.png`, copiado de `samples/es_simple_png.png` del repositorio OCR, que es sintético.
- **Modificar:**
  - `panel/src/pages/Documents.tsx` y su prueba;
  - `api/odin-adapter-web/src/main/java/com/e2d/odin/adapter/web/dto/Responses.java`, en la respuesta de `readPage`, en `DocumentView` (`pagesWithoutText`) y en `AdminMe` (`scannedDocumentsSupported`);
  - `SessionController`, o donde se monte `GET /v1/admin/me`;
  - `WorkerApiTest` y `AdminApiTest`;
  - `mcp/src/server.ts`, `mcp/src/tools/readPage.ts` y sus pruebas;
  - `deploy/demo/run-demo.sh`, `mcp/scripts/demo-client.ts` y `README.md`.

**Valores:**
- **Panel:**
  - `PENDING_READER` dice «Esperando al lector OCR» si `me.scannedDocumentsSupported`, y «Esta instalación no lee documentos escaneados» si no;
  - `READER_PROCESSING` dice «Leyendo con OCR», con el tono `pending`;
  - un documento con `pagesWithoutText` no vacía lleva debajo del estado «Páginas sin texto, no leídas: 3, 5».
- La respuesta de `readPage` gana `readerName`.
- **Instrucciones nuevas en `INSTRUCTIONS`:**
  - «Si readerName es unlimited-ocr-2, la página viene de un escaneo leído por OCR y puede tener errores de lectura: cítala tal como está.»;
  - «Una página con el texto vacío no se ha leído: no concluyas nada de ella.».
- `reader-stub.mjs`:
  - Node sin dependencias;
  - `GET /health` da `ok`;
  - `POST /read` da una página con el texto fijo «Acta ficticia escaneada: la junta aprobó revisar la bajante del patio el 3 de marzo.» y `reader { name: "reader-stub", version: "1" }`.
- `compose.reader-stub.yaml` añade el servicio `reader-stub` (imagen `node`, la misma versión que `mcp.Dockerfile`) y `ODIN_READER_URL: http://reader-stub:8077` en `api`. Solo lo usa `run-demo.sh`.
- `demo-client.ts`, paso nuevo:
  1. sube `escaneo-ficticio.png`;
  2. espera `READY`;
  3. `searchKnowledge(["bajante del patio"])` encuentra el documento en la página 1;
  4. `readPage` devuelve `readerName: "reader-stub"`.
- **`README`, apartado «Lector OCR».** Cuenta:
  - que sin lector los escaneados no se leen y el panel lo dice;
  - cómo se arranca el puente en el Mac (repositorio OCR, `make serve`);
  - que en este equipo la URL va en `deploy/.env`.
- **`README`, apartado de la demostración,** con esta frase literal: «El lector de prueba (`reader-stub`) solo sirve para las pruebas automáticas de `make demo`. Una demostración ante una persona usa siempre el puente real con Unlimited-OCR 2.».

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `Documents.test.tsx`:
    - `pendingReaderAndReaderProcessingLabels`;
    - `pendingReaderSaysInstallationDoesNotReadScansWhenUnsupported`;
    - `listsPagesWithoutText`.
  - `WorkerApiTest.readPageIncludesReaderName`;
  - `AdminApiTest`: `meSaysWhetherScannedDocumentsAreSupported` y `documentViewIncludesPagesWithoutText`;
  - la prueba del servidor MCP que comprueba las dos instrucciones.
- [ ] **Paso 2:** implementa. Regenera el contrato y los tipos.
- [ ] **Paso 3:** `make demo` dos veces seguidas y `make check` terminan con 0.
- [ ] **Paso 4:** commit «feat: lector OCR en el panel, el MCP y la demostración».

### Tarea 8: Verificación real y cierre · CTO

No es de un subagente: la hace el CTO. Spec 7, 8 y 9.

1. **Arrancar el puente:** en la copia de trabajo del repositorio OCR, `make serve`.
2. **Red:** desde un contenedor de una pila propia de Odín, distinta de la del propietario, `curl` a `http://host.docker.internal:8077/health`.
   - Se anota el resultado.
   - Si no responde, se arranca el puente con `--host` en la interfaz de Docker Desktop y `--token-env`, y se anota en T-015.
3. **Lectura de extremo a extremo:** pila propia de Odín con `ODIN_READER_URL`.
   - Se suben `samples/factura_scan.pdf` y `samples/long_report.pdf`, este último pasado antes a solo imagen (por ejemplo, rasterizado con pypdfium2 en un script del scratchpad), para tener un escaneado de 30 páginas.
   - El trabajador encuentra un importe de la factura con su cita y su página.
   - Se miden los segundos por página (registro del puente) y el pico MLX.
   - Esas cifras, y solo esas, van al informe, con la máquina en la que se midieron: modelo, chip, memoria y sistema (D-041).
4. **Documentos que ya esperaban:**
   - Primero con una base de prueba de la fase 1.1 con datos ficticios: los `PENDING_READER` se leen solos y un PDF mixto ya existente sale con sus páginas sin texto.
   - Con una copia de la instalación del propietario, **solo si él da permiso**, que pide el CEO. El CTO avisa al CEO antes de esta verificación.
     - La copia se queda en el equipo, fuera de git y con las reglas de `private/`, y se borra al terminar.
     - El informe da recuentos, nunca texto (D-041).
5. **Cierre:**
   - revisión final de toda la rama con Opus;
   - `make check` y `make demo`;
   - informe `docs/informes/2026-11-XX-lector-ocr.md`;
   - T-015 en el registro técnico del repositorio OCR;
   - copia del informe en la carpeta de notas para el CEO.

## Orden y paralelismo

- **Repositorio OCR:** 2 y luego 3. Solo dependen de la tarea 1, que es documentación.
- **Odín:** 1 y luego 4, 5, 6 y 7, en serie: cada una usa lo anterior, y 5 y 6 tocan los mismos ficheros de cableado.
- Las tareas 2 y 3 pueden ir en paralelo con las 4 y 5, porque no comparten ficheros.
- La tarea 8 va al final.

## Modelos de los revisores

- **Opus:** tareas 4 y 5, que tocan estados, intentos y seguridad por filas.
- **Sonnet:** el resto.
- La revisión final de la rama, con Opus.
