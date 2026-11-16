# Lector robusto — plan de implementación

> Ejecución con subagentes, sin rondas de revisión (instrucción del propietario del 2026-11-15). Cada tarea se verifica con sus pruebas y la feature con `make check`.

**Goal:** que un documento atascado no retenga el lector y que un puente colgado se reinicie solo.

**Spec:** `docs/superpowers/specs/2026-11-16-lector-robusto-design.md`

## Global Constraints

- Cada tarea va en `task/lector-robusto/<tarea>`, que sale de `feature/lector-robusto`, y se fusiona con `--no-ff`. Sin push.
- Commits en español, sin atribución a Claude ni `Co-Authored-By`.
- TDD. Métodos y funciones de 30 líneas como máximo. Identificadores en inglés. Comentarios mínimos.
- Sin migración.
- No instalar el agente de launchd ni llamar a `launchctl` de verdad. No tocar `~/Library/LaunchAgents`, el puente en marcha (8077), la pila del propietario ni `deploy/.env`. No usar los puertos 8080, 8088 ni 8090. No hacer `mvn install`.
- Las dos tareas tocan ficheros distintos y pueden ir a la vez.

## Review Focus

1. Un documento sin intentos (`attempt_count = 0`) nunca espera, aunque tenga `processing_started_at` de una lectura local anterior.
2. Con el carril local, un documento `RECEIVED` con intentos y `processing_started_at` reciente sigue saliendo en el reclamo: la pausa es solo del lector.
3. El vigía no deja el puente vivo al terminar: ni al detectar el cuelgue, ni con SIGTERM, ni si falla él mismo.
4. Con un puente que responde bien, el vigía no hace nada aunque pasen horas.

---

### Task 1: pausa y límite de reintentos del lector

Rama: `task/lector-robusto/reintentos`.

**Files:**
- `api/odin-application/.../port/ReaderTimedOutException.java` (nuevo, `extends ReaderFailedException`).
- `api/odin-application/.../shared/ReaderSettings.java`: añadir `retryDelay` y `maxRetryDelay`.
- `api/odin-application/.../shared/RetryPause.java` (nuevo): `record RetryPause(Duration base, Duration cap)` con `NONE`.
- `api/odin-application/.../port/DocumentRepository.java`: `claimNextForReader(Instant now, Duration staleAfter, RetryPause pause)`.
- `api/odin-domain/.../document/Document.java`: `retryReadingAfter(Instant failedAt)` (estado `PENDING_READER`, `processingStartedAt = failedAt`, mismos intentos).
- `api/odin-application/.../document/ProcessDocumentsWithReader.java`: el tiempo agotado y los demás fallos, con la nueva constante `READER_TIMED_OUT` (texto exacto en la spec).
- `api/odin-adapter-reader-service/.../HttpReaderService.java`: `HttpTimeoutException` (que no sea de conexión) → `ReaderTimedOutException`.
- `api/odin-adapter-postgres/.../PostgresDocumentRepository.java`: `claim()`.
- `api/odin-bootstrap/.../ReaderLaneConfiguration.java` y `application.yaml`: `odin.reader.retry-delay: ${ODIN_READER_RETRY_DELAY:PT10M}` y `odin.reader.max-retry-delay: ${ODIN_READER_MAX_RETRY_DELAY:PT1H}`.

**SQL de `claim()`:**
- Para filas en espera, se añade: `d.attempt_count = 0 OR d.processing_started_at IS NULL OR d.processing_started_at + LEAST(base · 2^(attempt_count − 1), cap) <= :now`.
- El orden pasa a `ORDER BY (d.intake_batch_id IS NOT NULL), (d.attempt_count > 0), d.seq`.
- `claimNextForProcessing` usa `RetryPause.NONE`.

**Tests:**
- `ProcessDocumentsWithReaderTest`:
  - un tiempo agotado en el intento 1 deja el documento en `PENDING_READER`, con `processingStartedAt` = ahora y 1 intento;
  - un tiempo agotado en el intento 2 lo deja `FAILED` con `READER_TIMED_OUT`;
  - otro fallo en el intento 2 (con máximo 3) se reintenta más tarde; en el intento 3 queda `FAILED` con `READER_FAILED`;
  - «no disponible» sigue igual.
- Test del cliente HTTP: un tiempo agotado de lectura da `ReaderTimedOutException`, y uno de conexión sigue dando `ReaderUnavailableException`.
- `PostgresDocumentRepositoryIT`, con pausa de 10 min y tope de 1 h, para un documento devuelto a la cola a la hora T:
  - con 1 intento: a T+5 min no sale y a T+11 min sí;
  - con 2 intentos: a T+15 min no sale y a T+21 min sí;
  - con 10 intentos: a T+61 min sale (el tope);
  - un documento con intentos y la pausa cumplida sale detrás de uno sin intentos de su misma clase, aunque tenga `seq` menor;
  - uno subido a mano con intentos sale antes que uno de la primera carga sin intentos;
  - `claimNextForProcessing` toma un `RECEIVED` con intentos y `processing_started_at` = ahora (Review Focus 2);
  - un `PENDING_READER` con 0 intentos y `processing_started_at` = ahora sale (Review Focus 1).
- Las pruebas de orden del encargo 21 siguen pasando.

- [ ] Las pruebas fallan → cambio → pasan, con el resto del adaptador de Postgres y de la aplicación → commit.

### Task 2: vigía del puente colgado

Rama: `task/lector-robusto/vigia`.

**Files:**
- `deploy/instalacion/vigia_lector.py` (nuevo) y `deploy/instalacion/test_vigia_lector.py` (nuevo).
- `deploy/instalacion/arranque_lector.py` y `test_arranque_lector.py`: el plist arranca el vigía; `validate` comprueba que existe.
- `README.md`: la sección del arranque automático.

**Interfaces:**
- `python3 vigia_lector.py [--health-url URL] [--hang-after SEGUNDOS] [--interval SEGUNDOS] -- <orden del puente>`.
- Por defecto: `http://127.0.0.1:8077/health`, 2100 s y 60 s. Cada comprobación, 10 s como máximo, o el intervalo si es menor.
- Hijo con `start_new_session=True`. Para pararlo: `os.killpg` con SIGTERM, 10 s de espera y SIGKILL.
- Códigos de salida: 1 si está colgado; el del hijo si este termina; 0 con SIGTERM. Un `finally` para el hijo si el vigía falla.
- Mensajes al registro (stderr), en español, con la hora: arranque del puente (pid), cuelgue detectado («sin respuesta de /health desde hace N min: reinicio el puente») y fin del puente (código).
- Plist: `ProgramArguments` = [`sys.executable`, `<odin_main_dir()>/deploy/instalacion/vigia_lector.py`, `--`, uv, `run`, `python`, `serve.py`, `--quant`, `8`]. Lo demás, como hoy.

**Tests** (`unittest`):
- Lógica pura del cuelgue, con reloj inyectado:
  - con respuestas correctas no salta nunca;
  - salta al pasar `hang_after` desde la última respuesta correcta, o desde el arranque si no hubo ninguna;
  - una respuesta correcta reinicia la cuenta.
- Con procesos reales y tiempos cortos:
  - hijo `python3 -c "import time; time.sleep(60)"`, URL a un puerto cerrado, `--hang-after 1 --interval 0.2`: el vigía sale con 1 en menos de 5 s y el hijo ya no existe;
  - hijo que sale con 3: el vigía sale con 3;
  - SIGTERM al vigía: sale con 0 y el hijo ya no existe;
  - un hijo que responde a `/health` (un `http.server` mínimo en un puerto libre) con `--hang-after 1`: tras 3 s el vigía sigue vivo (Review Focus 4).
- Instalador:
  - el plist nuevo tiene ese `ProgramArguments`, y `plutil -lint` da OK;
  - sin el vigía en la copia principal, termina con error, sin escribir nada y con un mensaje que dice qué falta.

- [ ] Las pruebas fallan → implementación → `python3 -m unittest discover -s deploy/instalacion -p 'test_*.py'` en verde → commit.

### Cierre

Merge de las dos tareas, `make check` en la feature y pilas de prueba bajadas. Sin migración, así que sin ensayo.
