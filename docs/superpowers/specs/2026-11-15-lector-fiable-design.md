# Encargo 21 · Lector fiable

Fecha: 2026-11-15. Rama: `feature/lector-fiable`, que sale de `develop` b433e50 (V28).

## 1. Lo subido a mano se lee antes que la primera carga (D-116)

**Hoy:**
- Las dos colas de lectura se reclaman por orden de llegada (`ORDER BY d.seq ... FOR UPDATE SKIP LOCKED`) en `PostgresDocumentRepository.claim()`:
  - la local (`ProcessPendingDocuments`, estados `RECEIVED`/`PROCESSING`);
  - la del lector OCR (`ProcessDocumentsWithReader`, estados `PENDING_READER`/`READER_PROCESSING`).
- Una primera carga mete miles de documentos de golpe, y lo que sube un trabajador después espera detrás de todos.

**Decisión:**
- **El orden de reclamo pasa a ser `ORDER BY (d.intake_batch_id IS NOT NULL), d.seq`.**
  - Primero va todo lo que no es de una primera carga, por orden de llegada: subidas desde el panel, desde un enlace del chat o a un expediente.
  - Después, los documentos de la primera carga, por orden de llegada.
  - La primera carga no se para: avanza cada vez que no queda nada subido a mano por leer.
  - Se aplica en todas las colas que reclaman documentos pendientes por `seq`: las dos de lectura, y la de etiquetado si es una cola FIFO sobre `document`. Así el documento subido a mano tampoco espera detrás de 3.000 en el siguiente paso.
- **Sin migración.**
  - `intake_batch_id` ya existe (V26) y solo lo rellena la primera carga.
  - La cola pendiente es de miles de filas como mucho, así que ordenar el conjunto filtrado es barato.
  - Si al medir hiciera falta, un índice parcial iría en V31.
- **Límite que queda:**
  - Un documento subido a mano espera, como mucho, a que termine el documento que el lector está leyendo en ese momento. El lector lee un documento entero por petición, así que no se interrumpe una lectura a medias.
  - Ese tiempo lo marca el tamaño del documento en curso, no la cola.
- **Fuera de alcance, anotado:**
  - Los reintentos del lector van seguidos y sin espera: un documento que agota el tiempo puede ocupar el carril hasta 3 × 30 min.
  - Con la prioridad, lo subido a mano se cuela entre reintentos, pero sigue siendo una mejora pendiente.

## 2. El lector arranca solo y se recupera si se cae

**Hoy:**
- El puente del lector (`serve.py`, en el repositorio OCR, worktree `OCR/.worktrees/puente-odin`, puerto 8077 y `GET /health`) se arranca a mano con `nohup … &`.
- El 13 de noviembre se cayó y estuvo parado una hora y cuarto.

**Decisión:**
- **Agente de launchd del usuario**, `~/Library/LaunchAgents/com.e2d.odin.puente-ocr.plist`, como la propuesta del informe de instalación del 13 de noviembre:
  - `RunAtLoad` arranca el puente al iniciar sesión. Con inicio de sesión automático, eso es al encender el Mac; Docker Desktop arranca en el mismo momento.
  - `KeepAlive` lo vuelve a arrancar si se cae.
  - `ThrottleInterval` de 30 s evita un bucle de arranques, porque cargar el modelo pesa unos 6 GB.
  - Se descarta un LaunchDaemon: arranca antes del inicio de sesión, pero va como root en `/Library`. Es global, pide sudo y MLX/Metal no tiene sesión gráfica.
- **Órdenes**, en el `Makefile` de Odín, para el propietario:
  - `make lector-arranque-instalar CONFIRMAR=ARRANQUE [LECTOR_DIR=…]`:
    - valida que `LECTOR_DIR/serve.py`, `uv` y los pesos del modelo existen;
    - escribe el plist con el mismo arranque que la orden manual documentada (`uv run python serve.py --quant 8`, `HF_HUB_OFFLINE=1`, `UV_OFFLINE=1`, log en `odin/data/puente-ocr.log`);
    - si hay un puente arrancado a mano en el 8077 (un proceso `serve.py`), lo para y lo dice;
    - hace `launchctl bootstrap gui/<uid>` y espera a que `/health` responda.
  - `make lector-arranque-quitar`: hace `launchctl bootout` y borra el plist. No arranca nada a mano.
  - `make lector-arranque-estado`: dice si el agente está cargado, su pid, el último código de salida y `/health`.
- **Qué toca fuera del proyecto:** solo ese plist en `~/Library/LaunchAgents` y su carga en launchd del usuario. La orden lo explica al instalar y al quitar. Nada global, sin sudo.
- **No se instala en este encargo.** La lanza el propietario con su sí.
- **Pruebas:**
  - script Python `deploy/instalacion/arranque_lector.py` con pruebas `unittest`, como los demás guiones;
  - la carpeta de LaunchAgents y `launchctl` se inyectan, así que las pruebas no tocan launchd;
  - `plutil -lint` valida el plist generado.
- **Fuera de alcance:** detectar un puente colgado pero vivo. `KeepAlive` solo cubre que el proceso muera. Odín ya trata un puente que no responde como «lector no disponible», y los documentos esperan sin fallar.
