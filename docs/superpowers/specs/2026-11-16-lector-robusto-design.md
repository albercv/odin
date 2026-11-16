# Encargo 24 · Lector OCR que no se atasca

Fecha: 2026-11-16. Rama: `feature/lector-robusto`, que sale de `feature/lector-fiable` 986b186 y junta `develop` c881433 (V29).

## 1. Pausa entre reintentos y límite de intentos

**Hoy:**
- En el carril del lector, una lectura que falla vuelve a la cola sin espera, y el siguiente reclamo la toma otra vez si es la primera de la cola.
- Un fallo por tiempo agotado (30 min, `odin.reader.timeout`) se reintenta igual que cualquier otro, hasta 3 intentos (`ProcessingSettings.maxAttempts`).
- Un documento atascado puede ocupar el lector 3 × 30 = 90 minutos seguidos.
- Además, el puente atiende una petición cada vez. Cuando Odín abandona una lectura por tiempo, el puente sigue con ella hasta terminarla, y mientras no responde a nadie.

**Decisión:**
- **Pausa creciente con tope.**
  - Tras un intento fallido que se va a repetir, el documento no se puede reclamar hasta que pase `min(base × 2^(intentos − 1), tope)`, contado desde el fallo.
  - La base es `odin.reader.retry-delay` (PT10M) y el tope `odin.reader.max-retry-delay` (PT1H). Con los 3 intentos de hoy, las pausas son de 10 y 20 minutos.
  - Vale para todo fallo que se reintenta, no solo el tiempo agotado: repetir al instante un error del puente tampoco sirve.
  - «Lector no disponible» no cambia: no cuenta como intento ni lleva pausa, porque el carril ya espera a que `/health` responda.
- **Los reintentos van detrás.**
  - El orden de reclamo pasa a `ORDER BY (intake_batch_id IS NOT NULL), (attempt_count > 0), seq`: dentro de cada clase (lo subido a mano, la primera carga), lo que ya falló va detrás de lo que aún no se ha intentado.
  - Sin esto, la pausa de 10 minutos se gastaría mientras el puente sigue con la lectura abandonada, y al volver el documento atascado sería otra vez el primero.
  - Se aplica a las dos colas de lectura, que comparten la consulta. En la local no cambia nada práctico: allí la pausa es cero.
- **El tiempo agotado se repite una sola vez.**
  - Si una lectura agota el tiempo en el segundo intento o después, el documento queda fallido con el motivo `READER_TIMED_OUT`: «El lector OCR no terminó de leer el documento en el tiempo máximo, tampoco al reintentarlo: divídelo en partes más pequeñas o reinténtalo.»
  - El primer tiempo agotado se reintenta porque puede venir de un puente colgado (apartado 2) o de un Mac dormido.
  - Los demás fallos siguen con el límite de intentos de hoy (3) y, al agotarlo, el motivo de hoy (`READER_FAILED`).
- **Cuánto puede ocupar el lector un documento atascado:**
  - Un intento ocupa los 30 minutos de Odín, más hasta 5 minutos hasta que el vigía reinicia el puente.
  - El segundo intento llega solo cuando ha pasado la pausa y no queda en su clase nada sin intentar.
  - Como mucho son unos 70 minutos en dos tandas, nunca seguidos mientras haya otros documentos esperando.
- **Sin migración.**
  - Al devolver el documento a la cola tras un fallo, `processing_started_at` pasa a la hora del fallo, y la pausa se cuenta desde ahí.
  - En `PENDING_READER` esa columna no tiene otro uso: `startReading` la sobrescribe al reclamar.
  - Un documento con `attempt_count = 0` (recién llegado, o reintentado desde el panel) no espera.
- **Cómo se distingue el tiempo agotado:** `ReaderTimedOutException`, subclase de `ReaderFailedException`, que `HttpReaderService` lanza cuando la petición de lectura agota su tiempo. El tiempo agotado al conectar sigue siendo «lector no disponible».

## 2. Lector colgado: vigía del puente

**Hoy:**
- El agente de launchd del encargo 21 (`com.e2d.odin.puente-ocr`) arranca `serve.py` y lo vuelve a arrancar si el proceso muere.
- Un puente vivo que no responde no se detecta.

**Decisión:**
- **Vigía.**
  - Es un guion nuevo de la biblioteca estándar, `deploy/instalacion/vigia_lector.py`.
  - El agente arranca el vigía, y el vigía arranca el puente como proceso hijo, en su propio grupo de procesos.
  - Comprueba `GET /health` cada 60 s, con 10 s de tiempo máximo de respuesta.
- **Cuándo está colgado:** cuando lleva 35 minutos seguidos sin una respuesta correcta de `/health`. Es el tiempo máximo de lectura de Odín (30 min) más 5, el mismo margen con que Odín da por abandonada una lectura.
  - No puede ser menos: el puente atiende una petición cada vez y no responde a `/health` mientras lee, así que desde fuera una lectura larga y un cuelgue se ven igual.
  - Pasados 35 minutos, Odín ya abandonó esa lectura, y parar el puente no pierde nada.
  - El reloj empieza al arrancar el vigía, y la carga del modelo (uno o dos minutos) cabe de sobra.
- **Qué hace al detectarlo:**
  - Lo escribe en el registro del puente.
  - Para el grupo del puente: SIGTERM, 10 s de espera y SIGKILL.
  - Sale con código 1, y el `KeepAlive` del agente lo vuelve a arrancar todo, con su `ThrottleInterval` de 30 s. Es el arranque automático del encargo 21.
  - Si el puente termina solo, el vigía sale con su mismo código y launchd lo rearranca, igual que hoy.
  - Si launchd para el vigía (al quitar el agente), el vigía para el puente y sale.
- **Plist:**
  - `ProgramArguments` = [python3 absoluto (el que ejecuta la instalación), `<copia principal de Odín>/deploy/instalacion/vigia_lector.py`, `--`, uv, `run`, `python`, `serve.py`, `--quant`, `8`].
  - Lo demás no cambia.
  - Sigue siendo el único fichero fuera del proyecto.
  - `lector-arranque-instalar` comprueba además que el vigía existe en la copia principal.
- **Fuera de alcance, anotado:**
  - Que el puente responda a `/health` mientras lee, con un hilo aparte que diga «ocupado desde». Permitiría detectar antes un cuelgue en reposo.
  - Es un cambio del puente, que está en el repositorio OCR, y pide reiniciar el que tiene en marcha el propietario.
