# Odín · Fase 2, bloque 1: lector OCR con el puente a Unlimited-OCR 2

- Fecha: 2026-11-11. Autor: CTO (segunda sesión). Encargo 11.
- Amplía `2026-11-09-api-central-design.md` («spec base»), apartado 6.4, y se apoya en `2026-11-10-identidad-pertenencias-design.md` («spec 2a»). Lo que este documento no cambia sigue como está allí.
- Rama: `feature/fase-2-conocimiento`, que sale de `feature/identidad-pertenencias`. Se ejecuta cuando cierre la fase 2a.
- Dos repositorios:
  - **Odín:** adaptador y caso de uso.
  - **OCR** (`/Users/albercv/Documents/personalProjects/OCR`): puente al modelo. El informe del lector está en `docs/report.md` de ese repositorio («informe del lector»).

## 1. Por qué

- **Problema:** hoy un PDF escaneado o una imagen se guarda y se queda en `PENDING_READER` sin leerse.
  - La demo empieza por «digitalizar», con documentos escaneados por OCR (D-021).
  - La definición del producto pide que los escaneados pasen por Unlimited-OCR 2 (canal 1).
- **Restricción del encargo 11 (requisito 3):** el lector solo corre hoy en un Mac con Apple Silicon (MLX y Metal), y Docker en el Mac no llega a Metal. Por eso entra como pieza intercambiable, fuera del núcleo.
- **Consecuencia:**
  - Odín habla con un servicio lector por HTTP, con un contrato que no sabe nada del Mac.
  - En el Mac, ese servicio es un puente que vive en el repositorio OCR y corre fuera de Docker.
  - Mañana puede ser otro servicio en un servidor Linux, sin tocar Odín.

## 2. Decisiones

**De negocio que se aplican:**
- El modelo es Unlimited-OCR 2 y nunca la v1, ni como respaldo (`CLAUDE.md` del repositorio OCR).
- Ningún LLM completa ni reinterpreta el texto extraído, y no se inventan metadatos.
- Los documentos no salen del equipo: la regla «todo local» del lector (D-017).
- Ninguna cifra sin medición (D-008).

**Del CTO.** Cada decisión dice lo que cuesta si resulta errónea:

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| El servicio lector va en un **carril propio**, separado del procesado de los lectores locales | Un escaneado tarda minutos y no debe frenar a un DOCX que llega detrás. Además, el servicio puede no estar arrancado | Juntar los carriles en un solo programador |
| Puerto nuevo `ReaderService`, en lugar de otro `DocumentTextReader` como decía el spec base 6.4 | Un servicio remoto y lento necesita saber si está disponible. Mezclarlo con los lectores locales obligaría a todos a tener esa comprobación | Un adaptador que envuelva al otro |
| El puente lee **una página por petición** (modo `single` del lector) | Odín guarda y cita por página. En modo `single` el número de página sale de la petición, así que no depende de que el modelo emita bien `<PAGE>`. El informe del lector midió que `single` leyó sin errores el documento real de 16 páginas, también sin las guardas anti-bucle (Resumen y §11). La ventaja del modo multipágina, que une párrafos y tablas entre páginas, no sirve aquí porque Odín guarda texto por página | Un parámetro del puente |
| Texto de cada página: el Markdown que ya genera `uocr.render.to_markdown` para los bloques de esa página, quitando los comentarios HTML (marcador de página y cabecera de imagen o figura con su caja) | Se reutiliza lo que ya está probado. Los comentarios no son texto del documento. Las tablas se quedan en HTML: la búsqueda ignora las etiquetas y una cita dentro de una celda sigue siendo literal | Una función de texto por página |
| Modelo de 8 bits por defecto | El informe del lector solo comparó 6 y 8 bits en un documento de una página | Una opción de arranque del puente |
| Fuera de alcance, leer las páginas escaneadas de un **PDF mixto** (unas páginas con texto y otras escaneadas). Queda `READY` con esas páginas vacías, como hoy, pero **el panel dice qué páginas no tienen texto y no se han leído** (D-040, D-041). Es la primera mejora posterior | Leerlas cambia el contrato y el modelo de estados de un documento que ya está `READY`, con fallos a medias. Primero, una pieza bien hecha | Un parámetro `pages` en el contrato y un carril de páginas sueltas |
| Un documento se lee entero o no se guarda nada | Una cita no puede apuntar a un documento al que le faltan páginas sin que nadie lo sepa | Guardar lecturas parciales con aviso |

## 3. Fuera de alcance

- Leer las páginas escaneadas de un PDF mixto (apartado 2). El aviso sí entra (apartado 5.5).
- OCR fuera de un Mac con Apple Silicon (encargo 8, pendiente).
  - Sin servicio lector configurado, los escaneados se guardan en `PENDING_READER`, como hoy.
  - El panel no dice que esperan: dice que esa instalación no lee documentos escaneados (D-041, apartado 5.5).
- Elegir qué páginas leer, releer con OCR un PDF que ya tiene texto, o comparar la lectura del OCR con la digital.
- Que el panel diga si el servicio lector responde. Lo que ve es el estado de cada documento.
- Coordenadas, tipos de bloque y otros datos de diseño de página que emite el modelo: no entran en Odín.

## 4. Contrato del servicio lector

Amplía el del spec base 6.4 sin romperlo: añade `GET /health` y los códigos de error. Se documenta en OpenAPI en `docs/contratos/servicio-lector.openapi.yaml`, en Odín. El puente lo implementa y sus pruebas comprueban sus respuestas con los ejemplos de ese fichero.

- **`POST /read`**, `multipart/form-data` con un único campo `file`.
  - **200:** `{ "pages": [{ "number": 1, "text": "…" }], "reader": { "name": "…", "version": "…" } }`.
    - Las páginas van todas, numeradas desde 1 y sin huecos.
    - El texto de una página puede venir vacío.
  - **Errores:** `{ "error": "<código>", "detail": "<motivo sin texto del documento>" }`:
    - **400 `bad_request`:** falta `file` o el cuerpo no es multipart;
    - **413 `too_large`:** el fichero supera el máximo del servicio;
    - **415 `unsupported`:** no es PDF, PNG, JPEG ni TIFF;
    - **422 `unreadable`:** el fichero está dañado;
    - **500 `reader_failed`:** alguna página no se pudo leer. No se devuelve ninguna página.
- **`GET /health`:** 200 `{ "status": "ok", "reader": { "name", "version" } }` cuando el modelo está cargado.
- **Secreto compartido, opcional:** si el servicio se arranca con un secreto, exige `Authorization: Bearer <secreto>` en las dos rutas y responde 401 `unauthorized` sin él. Odín lo envía si `ODIN_READER_TOKEN` tiene valor. Se fija ya para que el contrato no cambie si la red del apartado 7 obliga a usarlo.
- **Lo que no hace:** guardar el fichero, devolver texto en los errores o escribir el texto en sus registros.

## 5. Odín

### 5.1 Estados del documento

| Estado | Quién lo pone | Cambio |
|---|---|---|
| `PENDING_READER` | Carril local: imagen, o PDF sin texto en ninguna página | **El contador de intentos vuelve a 0**: los del carril local no cuentan para el lector |
| `READER_PROCESSING` (nuevo) | Carril del lector, al tomar el documento | Suma un intento y guarda la hora |
| `READY` | Carril del lector, con al menos una página con texto | Páginas guardadas, `paginated = true` |
| `FAILED` | Carril del lector | Motivo en el apartado 5.3 |

- El carril del lector vuelve a tomar un documento que lleva en `READER_PROCESSING` más de su tiempo de espera, que es el tiempo máximo de lectura más 5 minutos.
- **Intentos:** se cuentan igual que en el carril local.
  - Tomar el documento suma 1.
  - Si después de sumar el contador pasa del máximo (3), el documento pasa a `FAILED` sin leerse. Se leen, por tanto, los intentos 1, 2 y 3, y el «último intento» es el 3.
  - Cuando el servicio no responde (apartado 5.3), se resta el intento que se sumó al tomarlo.
- **Métodos de `Document`:**
  - `awaitReader()`: `PENDING_READER` con el contador a 0, para el carril local;
  - `startReading(now)`: `READER_PROCESSING`, suma 1 y guarda la hora;
  - `returnToReaderQueue(countAttempt)`: `PENDING_READER`, conservando el intento o restándolo.
- **Documentos que ya esperan:** una migración pone a 0 el contador de los documentos que ya están en `PENDING_READER`. Así no pasan a `FAILED` por los intentos del carril local al llegar el lector.
- **El carril local no toma `PENDING_READER` ni `READER_PROCESSING`:** solo toma `RECEIVED` y los `PROCESSING` caducados, como hoy.
- **`RetryDocument`** sigue aceptando `FAILED` y `PENDING_READER`, que vuelven a `RECEIVED` y pasan por el carril local. Un `READER_PROCESSING` da 409, como cualquier otro estado.
- **Formatos:** Odín ya reconoce PNG, JPEG y TIFF como `IMAGE` (`MagicBytesFormatDetector`). No cambia la subida. Un TIFF de varias páginas vuelve del servicio con varias páginas, y el documento queda `paginated = true`.

### 5.2 Carril del lector

- **Puerto `ReaderService`** (`application/port`):
  - `boolean isAvailable()`;
  - `ReadResult read(byte[] content, DocumentFormat format)`, que reutiliza `ReadResult`.
  - Errores del puerto, en `application/port`:
    - `ReaderUnavailableException`: no responde o no se puede conectar;
    - `UnreadableDocumentException`: respuesta 415 o 422;
    - `DocumentTooLargeForReaderException`: respuesta 413, porque el fichero pasa del tamaño o de las páginas que admite el servicio (el puente admite 200 páginas por defecto);
    - `ReaderFailedException`: respuesta 500, tiempo agotado o respuesta que no cumple el contrato.
- **Caso de uso `ProcessDocumentsWithReader`** (`application/document`), con `processNext()` como el carril local:
  1. Si `isAvailable()` es falso, no toma nada.
  2. Toma el siguiente documento en `PENDING_READER`, o uno de `READER_PROCESSING` que haya superado el tiempo, con `FOR UPDATE SKIP LOCKED`.
  3. Si supera el máximo de intentos (3, el mismo de hoy), lo pasa a `FAILED`.
  4. Lee y guarda, o aplica el error del apartado 5.3.
- **Sin duplicar:** la conversión de `ReadResult` a páginas que hoy está en `ProcessPendingDocuments.store` pasa a un sitio común (`DocumentPages`, en `application/document`) que usan los dos carriles.
- **Programador:** `ReaderProcessingScheduler`, en `odin-bootstrap`. Se crea solo si `odin.reader.url` tiene valor y se ejecuta cada 30 segundos con `fixedDelay`, de modo que una ejecución no se solapa con la anterior. Sin URL, el carril no existe y nada cambia.
- **Dos hilos de programación:** el programador de Spring tiene un solo hilo por defecto, y una lectura de 30 minutos pararía el carril local. Se fija `spring.task.scheduling.pool.size=2`. Una prueba de `odin-bootstrap` comprueba que el carril local procesa un documento mientras el del lector está bloqueado.
- **Seguridad por filas:** el carril no tiene actor, así que usa `CompanyScope.all()` y `ProjectVisibility.all()`, como `ProcessPendingDocuments` (spec base 7).

### 5.3 Errores

| Qué pasa | Estado | Motivo que ve el administrador |
|---|---|---|
| `ReaderUnavailableException` | Vuelve a `PENDING_READER` y no cuenta el intento | — |
| `UnreadableDocumentException` | `FAILED` | «Documento ilegible: el fichero está dañado o no se puede leer.» (el de hoy) |
| `DocumentTooLargeForReaderException` | `FAILED` | «El documento es demasiado grande para el lector OCR: divídelo en partes más pequeñas.» |
| `ReaderFailedException` antes del último intento | Vuelve a `PENDING_READER`, conservando el contador | — |
| `ReaderFailedException` en el último intento | `FAILED` | «El lector OCR no pudo leer el documento. Reinténtalo.» |
| El lector no encuentra texto en ninguna página | `FAILED` | «El lector OCR no encontró texto en el documento.» |
| Se superan los intentos | `FAILED` | «demasiados intentos» (el de hoy) |

El detalle técnico va solo al registro del servidor, sin texto del documento (spec base 7).

### 5.4 Adaptador `odin-adapter-reader-service`

- Módulo Maven nuevo. Cumple las reglas de ArchUnit: depende de `odin-application` y de `odin-domain`, nunca de otro adaptador.
- `HttpReaderService` con `java.net.http.HttpClient` del JDK y Jackson para el JSON. No añade dependencias nuevas a la instalación.
- **Configuración:**

| Variable | Por defecto | Qué es |
|---|---|---|
| `ODIN_READER_URL` | vacía | URL base del servicio. Vacía: carril apagado |
| `ODIN_READER_TIMEOUT` | `PT30M` | Tiempo máximo de una lectura |
| `ODIN_READER_CONNECT_TIMEOUT` | `PT5S` | Tiempo máximo para conectar y para `GET /health` |
| `ODIN_READER_TOKEN` | vacía | Secreto compartido del apartado 4. Vacía: no se envía |

- `compose.yaml` pasa `ODIN_READER_URL` a la API, vacía por defecto. Nada del Mac entra en el código común (spec base 3.4): en este equipo, la URL va en `deploy/.env`, que no se versiona.
- **Validación de la respuesta:** páginas numeradas de 1 a n sin huecos ni repeticiones, `reader.name` y `reader.version` con texto. Si no, `ReaderFailedException`.

### 5.5 Lo que ven el panel y el trabajador

- **Panel, lista de documentos:**
  - `PENDING_READER`:
    - con servicio lector configurado, dice «Esperando al lector OCR»;
    - sin él, dice «Esta instalación no lee documentos escaneados» (D-041).
    - `GET /v1/admin/me` gana `scannedDocumentsSupported`, verdadero si `ODIN_READER_URL` tiene valor. `InstallationSettings` lo lleva al caso de uso.
  - `READER_PROCESSING` dice «Leyendo con OCR».
  - **Páginas sin leer (D-040, D-041):** un documento `READY` con páginas sin texto dice «Páginas sin texto, no leídas: 3, 5».
- **Páginas sin texto:**
  - `Document` gana `pagesWithoutText`, la lista de páginas cuyo texto queda vacío tras quitar los espacios.
  - La rellena el carril local al guardar un PDF con texto en alguna página, y queda vacía en todo lo que lee el lector, porque esas páginas sí se leyeron.
  - La migración la calcula para los documentos `READY` que ya existen, a partir de `document_page`.
  - `DocumentView` gana `pagesWithoutText`.
- **Trabajador:**
  - `readPage` añade `readerName` a su respuesta.
  - Las instrucciones del MCP ganan dos frases:
    - si la página la leyó el OCR, el texto viene de un escaneo y puede tener errores de lectura; se cita igual, tal como está;
    - una página con el texto vacío no se ha leído: no se concluye nada de ella.
  - Las rutas del trabajador no cambian de forma.

## 6. Puente en el repositorio OCR

- **Ficheros nuevos:**
  - `uocr/service.py`: lógica pura y probable sin modelo. Lee el multipart, reconoce el tipo, prepara las páginas, construye la respuesta y traduce los errores.
  - `serve.py`: arranque.
  - `make serve [QUANT=8]`.
  - Pruebas en `tests/`.
  - Conserva el estilo del repositorio OCR: Python, snake_case y sus reglas de `CLAUDE.md`.
- **Servidor:** `http.server` de la biblioteca estándar, con una petición cada vez (el modelo cargado no se comparte entre peticiones a la vez). Escucha en `127.0.0.1:8077` por defecto (`--host`, `--port`). Sin dependencias nuevas.
- **Al arrancar:**
  - Carga el modelo una sola vez, sin red (`HF_HUB_OFFLINE=1`, como hoy).
  - **Se niega a arrancar** si el identificador del modelo no contiene `Unlimited-OCR-2` o si el tokenizador no tiene `<PAGE>`. Es la barrera contra la v1.
- **Entrada:**
  - Reconoce el tipo por el contenido, no por la extensión: `%PDF-`, o una imagen que abra Pillow (PNG, JPEG o TIFF).
  - Un TIFF de varias páginas da una página por fotograma.
  - El PDF se renderiza a 200 ppp, como hoy (`uocr/inputs.py`).
  - Se trabaja en memoria: el fichero no se escribe en disco.
  - Tamaño máximo: `--max-bytes`, 50 MB por defecto, igual que Odín.
- **Lectura:**
  - Se reutiliza `uocr.pipeline.run(pages, "single", 1, engine)`: cada página es una petición a `Engine.generate`, con las guardas anti-bucle y el límite de tokens que ya existen. Los bloques que devuelve llevan su página, así que el texto de cada página sale de `to_markdown` sobre los bloques de esa página.
  - Una página falla si su petición tiene `error` o si `generation_tokens` llega a `max_tokens`. Si falla una, falla todo (500).
  - Si el número de `<PAGE>` no es 1, se apunta en el registro y el texto se conserva: la página es la de la petición.
- **Respuesta:**
  - `reader.name` es `unlimited-ocr-2`.
  - `reader.version` es el identificador del modelo y la versión de `uocr`; por ejemplo, `mlx-community/Unlimited-OCR-2-8bit; uocr 0.1.0`.
- **Registro:** número de páginas, segundos y pico de memoria de cada lectura. Nunca el texto ni el nombre del fichero.

## 7. Red entre Odín y el puente

- En este Mac la API corre en Docker y el puente fuera, con `ODIN_READER_URL=http://host.docker.internal:8077` en `deploy/.env`.
- **Por verificar en el plan:** que un servicio que escucha en `127.0.0.1` del Mac responde desde el contenedor por `host.docker.internal`.
  - Si no responde, el puente escucha en la interfaz que use Docker Desktop y se arranca con el secreto compartido del apartado 4. Se anota en T-015.
- Nunca se escucha fuera de `127.0.0.1` sin ese secreto: el puente se niega a arrancar así.

## 8. Pruebas y verificación

- **Odín, sin el modelo:**
  - **Casos de uso:** `ProcessDocumentsWithReaderTest`, con un doble de `ReaderService`. Cubre:
    - cada fila de los apartados 5.1 y 5.3;
    - el servicio no disponible, que no consume intentos;
    - el documento tomado por tiempo agotado;
    - que el carril local deja una imagen en `PENDING_READER` con 0 intentos.
  - **Adaptador:** `HttpReaderServiceTest` contra un `com.sun.net.httpserver.HttpServer` del JDK en un puerto libre. Cubre:
    - cuerpo multipart correcto;
    - cada código de error del contrato;
    - tiempo agotado;
    - respuesta con páginas que faltan.
  - **PostgreSQL:** `claimNextForReader` con `SKIP LOCKED` y el tiempo agotado.
  - **De principio a fin:** `make demo` gana un paso con un servicio lector **de prueba** en Compose, solo en la pila de la demo. Devuelve un texto fijo para un PDF escaneado ficticio. Comprueba:
    - que el documento llega a `READY`;
    - que un trabajador lo encuentra con su cita y página;
    - que `readPage` dice qué lector lo leyó.

    Prueba el camino de Odín, no la calidad del OCR.
  - **El lector de prueba es solo para las pruebas automáticas** (D-041). Una demo que vea una persona usa siempre el puente real con Unlimited-OCR 2. Lo dicen este diseño y el `README` de la demo.
- **Puente, sin el modelo:** pruebas con un motor falso. Cubren:
  - multipart;
  - reconocimiento del tipo;
  - TIFF de varias páginas;
  - numeración;
  - fallo de una página, que da 500 sin páginas;
  - límite de tamaño;
  - negativa a arrancar con un modelo que no es Unlimited-OCR 2;
  - que el texto de una página no lleva comentarios HTML;
  - que el registro no lleva texto.

  Además, una prueba HTTP real en un puerto libre.
- **Puente con el modelo** (`uv run pytest -m model`): lee `samples/factura_scan.pdf` y `samples/es_simple_png.png` y comprueba los importes con `check_amounts`, que ya existe. La precisión del lector la mide su batería (`make eval`), no esta prueba.
- **Verificación del CTO, al final:** puente real y Odín en una pila propia. Se sube un escaneado ficticio, el trabajador lo encuentra con su cita y se mide:
  - segundos por página;
  - pico de memoria del puente.

  Esas cifras, y solo esas, van al informe del bloque, con la máquina en la que se midieron (D-041). Con ellas el CEO estimará cuánto tarda un despacho en digitalizar su historial; hasta entonces no se promete ningún plazo.
- **Regresión:** la batería de búsqueda mantiene 27 de 30.

## 9. Criterio de cierre

- `make check` y `make demo` de Odín terminan con 0.
- `make test` del repositorio OCR termina con 0, y `uv run pytest -m model` también.
- La verificación del CTO del apartado 8, hecha y anotada con sus cifras.
- Los documentos que estaban en `PENDING_READER` se leen solos al configurar el servicio, sin perder nada.
  - Se verifica primero con una base de prueba de la fase 1.1 con datos ficticios.
  - Con una copia de la instalación del propietario, solo si él da permiso. Lo pide el CEO, y el CTO le avisa antes de esa verificación (D-041).
  - La copia se queda en el equipo, fuera de git y con las reglas de `private/`, y se borra al terminar.
  - El informe da recuentos, nunca texto.
- Decisiones registradas en el registro técnico (T-015).

## 10. Lo que queda al constructor

- Nombres internos, estructura de paquetes y cómo se monta el multipart.
- El puerto por defecto del puente, si el 8077 está ocupado en este equipo.
- Cómo se prueba la red del apartado 7. El resultado sí se anota.

## 11. Validación del CEO (D-040 y D-041, 2026-11-11)

El CEO validó el diseño con estas precisiones, ya aplicadas:
- el aviso de páginas sin texto en los PDF mixtos (2, 3 y 5.5);
- el texto del panel en una instalación sin lector (3 y 5.5);
- el lector de prueba, solo para las pruebas automáticas (8);
- las condiciones para usar una copia de la instalación del propietario (9);
- las cifras de tiempo y memoria, con su máquina (8).

## 12. Revisión del constructor (2026-11-11)

Un subagente leyó el diseño como si tuviera que planificarlo. Hizo 7 preguntas, todas técnicas, y el CTO las decidió. Ya están en el texto:
- el contador de los documentos que ya esperan al lector, que se pone a 0 con una migración (5.1);
- qué es un intento y cuándo se resta (5.1);
- qué estados toma cada carril y qué acepta `RetryDocument` (5.1);
- TIFF, que Odín ya reconoce como imagen (5.1);
- reutilizar `pipeline.run` en modo `single` (6);
- el secreto compartido, que se fija ya en el contrato aunque sea opcional (4 y 7);
- los dos hilos de programación, para que una lectura larga no pare el carril local (5.2).

## 13. Lo que dejaron las tareas 1 a 3 (2026-11-11)

El CEO autorizó adelantar el contrato y el puente (D-046). Su revisión final dejó cuatro ajustes para las tareas que esperan a la 2a, ya aplicados en el plan:
- **413 tiene motivo propio.** El puente admite 200 páginas por defecto (`--max-pages`), y un documento demasiado grande no se trata como «ilegible» (5.2 y 5.3).
- **Cuerpo con `Content-Length`.** El puente no admite el envío por trozos (*chunked*), así que el adaptador envía el cuerpo entero (plan, tarea 6).
- **400 y 404 son fallo del cliente,** no del documento: se tratan como fallo del lector y quedan en el registro del servidor (plan, tarea 6).
- **Tiempo máximo en `/health`.** El puente atiende una petición cada vez y, mientras lee, `/health` no responde. Por eso el adaptador fija un tiempo máximo en la petición, no solo al conectar (plan, tarea 6).
