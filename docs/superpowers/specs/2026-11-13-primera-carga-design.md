# Diseño técnico: primera carga (opción B)

- Fecha: 2026-11-13. Autor: CTO. Origen: estudio de la primera carga (`docs/informes/2026-11-13-estudio-primera-carga.md`, `feature/estudio-primera-carga`) y D-079.
- **Depende del encargo 13** (CTO 2, `feature/casos-expediente`, diseño `2026-11-13-casos-como-expediente-design.md`). Usa de él:
  - `AddToCaseFiles`;
  - la retirada de documentos;
  - la migración V24;
  - la componente de subida del panel.
  - No crea otro modelo de expediente.
- **Rama:** `feature/primera-carga`, que sale de `develop`. Se construye cuando el propietario decida D5 y lo priorice, y con el encargo 13 ya en `develop`.

## 1. Qué se construye

Un cliente nuevo deja su archivo en una carpeta y Odín:
1. **lo recibe en un lote,** por una de las tres vías de entrega (apartado 8), que comparten todo lo demás;
2. **lo lee:** el texto digital directamente y los escaneos con el OCR local, como hoy;
3. **lo deja «en revisión»:** ningún trabajador ve nada de lo que no está confirmado (D1);
4. **propone, con su motivo y su confianza:**
   - el proyecto, o «de toda la empresa»;
   - la ficha de caso y su número;
   - los enlaces a expedientes;
   - el escaneo que es el mismo documento que uno digital;
   - las versiones nuevas;
5. **deja confirmar al administrador por grupos,** en una bandeja del panel. Nada se aplica sin una persona (D2).

**Lo que no entra:**
- clasificar con IA externa (D4);
- confirmar automáticamente (D2);
- trocear exportaciones de muchas comunidades;
- leer `.eml` o `.msg` (encargo 13, 7.1);
- el tipo de documento como campo de Odín. Hoy no existe: aquí solo se usa para proponer y se enseña en la bandeja.

## 2. Arquitectura

Hexagonal, como el resto de la API, con las mismas reglas de ArchUnit.

- **Dominio `intake`:**
  - el lote, con sus estados;
  - las propuestas y los candidatos a proyecto;
  - los servicios puros que proponen: `ProjectRules`, `CaseFileRules` y `DuplicateFinder`. Son las reglas del estudio pasadas a Java, sin dependencias.
- **Casos de uso** en `application/intake`.
- **Un puerto, `ProjectProposer`,** para el modelo local, con su adaptador en `odin-adapter-tagger-ollama`. Comparte cliente HTTP, URL y modelo con el etiquetado (`ODIN_TAGGER_URL`, `ODIN_TAGGER_MODEL`).
- **Patrones:**
  - **El lote es una máquina de estados:** `OPEN` → `READING` → `IN_REVIEW` → `CLOSED`. Cada transición la hace un caso de uso.
  - **Cadena de proponentes:** primero las reglas y, solo para lo que queda sin proyecto, el modelo.
  - **Las propuestas son datos, no efectos.** Confirmar llama a los casos de uso que ya existen, así que la primera carga no tiene un camino propio para cambiar documentos:
    - `MarkCaseDocuments`, para la ficha de caso;
    - `SupersedeDocument`, para las versiones (D-050);
    - `AddToCaseFiles`, para los expedientes (encargo 13);
    - `WithdrawDocument`, para descartar (encargo 13).
- **Doble barrera de aislamiento,** como siempre:
  - el filtro SQL del trabajador excluye lo pendiente;
  - y la RLS lo exige por su cuenta (apartado 3).

## 3. Modelo de datos (migración `V25__intake.sql`)

Va por encima de la V24 del encargo 13. Se renumera al integrar si hace falta (T-020, T-021).

- **`intake_batch`:**
  - columnas: `id`, `company_id`, `started_by`, `channel` (`COMMAND` o `PANEL`), `state`, `created_at` y `closed_at`;
  - RLS por empresa;
  - solo hay un lote `OPEN` o `READING` por empresa a la vez (índice único parcial).
- **Columnas nuevas en `document`:**
  - `intake_batch_id`, con clave foránea compuesta con la empresa;
  - `intake_pending` (`boolean`, falso por defecto);
  - `intake_path`: la ruta relativa en la carpeta del cliente. Es una pista más, con su propia confianza, y sale en la bandeja;
  - `scan_of`: el documento digital del que este es el escaneo (D3), con clave foránea compuesta con la empresa.
- **D1 en la base:** la política `document_scope` vuelve a crearse con la condición de lo retirado del encargo 13 y una más: `NOT intake_pending OR current_setting('app.include_intake', true) = 'on'`.
  - `document_page`, los temas, los expedientes y los enlaces al original lo heredan por su `EXISTS` sobre `document`, igual que con lo retirado.
  - Solo las rutas de la bandeja activan `app.include_intake`.
- **`intake_project_candidate`** (D6):
  - columnas: `batch_id`, `name`, `origin` (`IMPORTED` o `DISCOVERED`), `document_count`, `state` (`PROPOSED`, `CONFIRMED` o `REJECTED`) y `project_id`;
  - confirmar un candidato crea el proyecto con `CreateProject`, o lo une a uno que ya existe.
- **`intake_proposal`:**
  - columnas: `id`, `company_id`, `batch_id`, `document_id`, `kind`, `target_project_id`, `target_document_id`, `case_number`, `confidence` (`HIGH` o `LOW`), `source` (`RULES`, `FOLDER` o `MODEL`), `reason` (cita literal de hasta 300 caracteres), `state` (`PROPOSED`, `CONFIRMED` o `REJECTED`), `decided_by` y `decided_at`;
  - `kind` puede ser `PROJECT`, `COMPANY_WIDE`, `CASE_MARK`, `CASE_LINK`, `SAME_AS_SCAN` o `SUPERSEDES`.
- **Escaneo enlazado (D3):**
  - el escaneo no sale en las búsquedas ni en las listas del trabajador: el filtro añade `scan_of IS NULL`;
  - se ve como «copia escaneada» en la ficha del documento principal y en el expediente;
  - su original se abre como cualquier otro.

## 4. Recorrido de un lote

1. **`StartIntake(admin, channel)`** abre el lote en estado `OPEN`.
2. **`AddFileToIntake(admin, batchId, path, file)`** reutiliza `UploadDocument` con estas diferencias:
   - el documento nace con `intake_pending`, sin proyecto y con `intake_path`;
   - si el SHA-256 ya está en la empresa (`findBySha256` del encargo 13: el más antiguo, sin contar lo retirado), no crea nada. Lo cuenta como «ya estaba» y devuelve el documento que existe;
   - **dentro del mismo lote, la huella cuenta aunque el documento esté retirado.** Si el administrador descartó un fichero en la bandeja y la subida se reanuda, ese fichero no vuelve a entrar: se cuenta como «descartado en este lote». Fuera del lote vale la regla del encargo 13: subir un fichero retirado lo hace entrar como documento nuevo (V24, índice único parcial `WHERE withdrawn_at IS NULL`);
   - es idempotente, así que reintentar un fichero no duplica nada (reanudable);
   - `GET .../intake-batches/{id}/files?sha256=…` responde qué huellas ya están en la empresa o en el lote, retiradas incluidas, para que el cliente no vuelva a subirlas.
3. **`CloseIntakeUpload`** pasa el lote a `READING`.
   - La lectura es la de siempre: `ProcessPendingDocuments` y `ProcessDocumentsWithReader`.
   - Los escaneos esperan al OCR si no hay lector, y lo dice la bandeja.
4. **Lista de proyectos (D6):**
   - si el administrador importa la lista exportada del programa de gestión (CSV o TXT, un nombre por línea; Excel, cuando las entrevistas lo pidan), los candidatos son `IMPORTED`;
   - si no, `ProjectRules.discover` propone candidatos `DISCOVERED` con los patrones del estudio;
   - el administrador los confirma, une o rechaza;
   - sin una lista confirmada no se proponen proyectos.
5. **`ClassifyIntake`,** un trabajo periódico como el etiquetado, hace en cada documento `READY` y pendiente que aún no tiene propuestas:
   - `PROJECT` o `COMPANY_WIDE` con `ProjectRules`, con la carpeta como pista;
   - si no encaja, `ProjectProposer` (el modelo local), con confianza `LOW` y solo si `ODIN_TAGGER_URL` está configurada;
   - `CASE_MARK` y el número de caso con `CaseFileRules`;
   - `SAME_AS_SCAN` y `SUPERSEDES` con `DuplicateFinder`, solo entre documentos del mismo proyecto propuesto;
   - `CASE_LINK`: un documento que cita el número de una ficha de caso del mismo proyecto, o la referencia de incidencia de una exportación.
   - Cuando ya no queda nada por leer ni por proponer, el lote pasa a `IN_REVIEW`.
6. **Bandeja** (apartado 6). Cuando no queda ningún documento pendiente, el lote pasa a `CLOSED`.
   - Lo que el administrador marca «no lo sé» sigue pendiente y oculto, y el lote sigue en `IN_REVIEW` hasta que se resuelve o se retira.

## 5. Reglas de propuesta

Son las del estudio, con sus cifras sobre el archivo ficticio como mínimo de aceptación.

- **Proyecto:**
  - nombre normalizado (minúsculas, sin tildes ni signos), con variantes: «C/» y «Calle», número con o sin coma, «Residencial» y «Avenida» abreviados;
  - gana el más largo: «Calle Olmo 24 (bloque 2)» frente a «Calle Olmo 24»;
  - pesa más lo que sale en la cabecera;
  - un solo proyecto da confianza `HIGH`; varios sin desempate dan `LOW` con todos los candidatos;
  - una carpeta del cliente que se llame como un proyecto suma su señal (`FOLDER`), pero nunca contradice al texto en silencio: si no coinciden, la confianza es `LOW`.
- **Modelo local** (`ProjectProposer`):
  - recibe la lista confirmada y los primeros 2.000 caracteres;
  - responde con el nombre exacto de un proyecto o con «ninguno»; cualquier otra respuesta se descarta;
  - temperatura 0, `think: false` y contexto de 4.096, los parámetros del estudio;
  - **nunca** se usa para los documentos que las reglas ya resuelven con `HIGH`: en la muestra del estudio fue peor que ellas (96 frente a 99 de 100).
- **Ficha de caso:** «FICHA DE CASO» en la cabecera y el número en sus dos formatos («Caso 2024-342» y «Número de caso: …»).
- **Duplicados** (umbral 0,8 sobre tejas de 5 palabras, con índice MinHash):
  - copia exacta: SHA-256 igual, y ya no entra (apartado 4);
  - **`SAME_AS_SCAN`:** uno de los dos lo ha leído el OCR, son del mismo proyecto y tienen las mismas fechas e importes;
  - **`SUPERSEDES`:** mismo tipo y mismo proyecto, con fechas o importes que cambian; el más reciente según la fecha de su texto sustituye al anterior. También vale «sustituye a la póliza nº X» con el número de otro documento del proyecto;
  - **nunca entre proyectos distintos:** es lo que en el estudio elimina el 99 % de los falsos parecidos.
- **Cada propuesta lleva el fragmento literal que la justifica** (`reason`). Nada se propone sin motivo citable (D-008).

## 6. Bandeja de confirmación

Rutas del administrador de la empresa, `/v1/admin/companies/{companyId}/intake-batches/{batchId}/...`:

| Ruta | Qué hace |
|---|---|
| `GET` (el lote) | Estado, recuentos (subidos, ya estaban, en lectura, pendientes y por clase de propuesta) y avisos (sin lector OCR, sin modelo local) |
| `GET project-candidates` · `POST project-candidates/confirm` | Lista de proyectos (D6) |
| `GET proposals?kind=&confidence=&project=` | Propuestas agrupadas por proyecto y confianza, con el título y el motivo |
| `POST proposals/confirm` `{proposalIds}` | Confirma un grupo, todo o nada |
| `POST proposals/{id}/decide` `{decision, projectId?}` | Una a una: confirmar, corregir el proyecto, «de toda la empresa», «no lo sé» o rechazar |
| `POST documents/{id}/withdraw` | Descarta, con el `WithdrawDocument` del encargo 13 |

**Efectos, en una transacción por acción:**

| Propuesta | Efecto |
|---|---|
| `PROJECT` o `COMPANY_WIDE` | Pone `project_id`, o lo deja nulo si es de toda la empresa, y quita `intake_pending`. Desde ese momento lo ven quienes ven el proyecto |
| `CASE_MARK` | `MarkCaseDocuments` |
| `SUPERSEDES` | `SupersedeDocument` (D-050) |
| `SAME_AS_SCAN` | Pone `scan_of` (D3) |
| `CASE_LINK` | `AddToCaseFiles` con el administrador como actor. Agrupa los enlaces por caso: un caso y sus documentos en una sola llamada |

- **Orden obligatorio:** las parejas y los enlaces solo se pueden confirmar cuando los proyectos de sus documentos están confirmados. Así se cumplen las reglas de proyecto de `SupersedeDocument` y del encargo 13.
- **Quién y cuándo:** cada propuesta guarda quién la decidió y cuándo. No hay recuentos por persona (D-005).
- **El panel** tiene una página «Primera carga» con cinco pasos:
  1. subir, solo en la variante del panel;
  2. lista de proyectos;
  3. proyectos por grupos;
  4. parejas (escaneos y versiones);
  5. fichas y expedientes.
  - Cada grupo se despliega para ver sus documentos y motivos antes de confirmarlo.

## 7. Qué sale del equipo, memoria y tiempo

- **Nada sale del equipo:** el OCR, el modelo y las reglas son locales (D-016, D4).
- **Tiempo:**
  - el OCR manda: 4,3 s por página en el M3 Max (estudio);
  - las reglas, los duplicados y el modelo sobre lo que queda tardan segundos por cada mil documentos.
- **Memoria:**
  - el OCR (7,2 GB de pico en MLX) y el modelo (5,5 GB en la GPU) no deberían estar cargados a la vez en un equipo pequeño;
  - por eso `ClassifyIntake` solo llama al modelo cuando la cola del lector está vacía;
  - el etiquetado sigue en su ventana nocturna.

## 8. Vías de entrega (D5, abierta)

Las tres usan la misma API del apartado 4: abrir el lote, subir un fichero cada vez con su ruta relativa, preguntar qué huellas ya están y cerrar. Todo lo demás es común, así que la elección del propietario no cambia nada del servidor.

| | V1 · Orden, la lanza el instalador | V2 · Página del panel con subida de carpeta | V3 · Orden, la lanza el administrador del cliente con una guía |
|---|---|---|---|
| Cómo | `make primera-carga CARPETA=/ruta` en el servidor de la oficina | «Primera carga» → «Subir carpeta» en el panel (`webkitdirectory`) | La misma orden que V1, con una guía paso a paso (`docs/guias/primera-carga.md`) |
| Quién entra | Un administrador de la empresa: la orden pide su correo y su contraseña, sin guardarlos | El administrador con su sesión | El administrador del cliente, con su correo y su contraseña |
| Por dónde pasan los ficheros | Del disco del servidor a la API local, sin red | Del ordenador del administrador al servidor, por la red de la oficina | Como V1 |
| Reanudar | La orden pregunta las huellas ya subidas y sigue | El navegador calcula el SHA-256 (`crypto.subtle`), pregunta y sube lo que falta. Tras un corte, se vuelve a elegir la misma carpeta | Como V1 |
| Avisos al empezar | Sin lector OCR, sin modelo local, espacio libre, otro lote abierto | Los mismos, en la página | Los mismos, en lenguaje de la guía |
| Qué se reutiliza | El patrón de `deploy/instalacion/` (Python con la biblioteca estándar, cerrojo y copia previa) | La lista de ficheros y el progreso de la página de subida del encargo 13. **No** su enlace de 15 minutos, que es de un caso y de 20 ficheros | Todo lo de V1 |
| Riesgo | **D-019:** si el instalador es de E2D, toca el archivo del cliente | Navegadores y miles de ficheros: se sube de uno en uno, con dos a la vez como mucho | Que el administrador del cliente no maneje una terminal |

- **V1 y V3 son el mismo código:** solo cambian quién la lanza y la guía.
- **V2 es independiente:** se puede construir antes, después o en lugar de V1 y V3.
- **Decidido (D-080):**
  - **V3 es la vía por defecto y la recomendada:** la lanza el administrador del cliente con la guía, y nadie de E2D toca el archivo (D-019).
  - **V1 solo vale si quien instala es personal del cliente.** Lanzada por alguien de E2D, queda descartada por D-019.
  - **V2:** si se construye también y cuándo, lo decide el propietario.

## 9. Cómo se acepta

- **Aislamiento (D1),** por la API y con la clave de un trabajador. Nada pendiente sale:
  - en `search`, `listDocuments`, `readPage`, `findSimilarCases` ni `verifyCitation`;
  - en el expediente ni en el enlace al original;
  - en los temas.
  - Se comprueba también con la RLS sola, por la conexión sin el filtro SQL, como en las revisiones de aislamiento anteriores.
- **Batería de propuestas** sobre el archivo ficticio revuelto del estudio, con los recursos de prueba sacados de `experimentos/2026-11-13-primera-carga/`. Como mínimo, las cifras del estudio:
  - proyecto con confianza alta, 100 % de acierto;
  - tipo y ficha de caso, 100 %;
  - copias y escaneos, precisión y cobertura de 1,00;
  - versiones, precisión de 0,83 o más.
- **Reanudar:** se corta la subida a mitad, se vuelve a lanzar, y el resultado es el mismo número de documentos y ningún duplicado.
- **De principio a fin, en una pila desechable:**
  - la vía elegida sube la carpeta;
  - el lote se lee y se clasifica;
  - el administrador confirma por grupos;
  - y el trabajador ve, con su clave, solo lo confirmado de sus proyectos.
- **`make check` y `make demo`** terminan con 0.
- **Enlace con expedientes:** se mide con el archivo ampliado con expedientes del encargo 13.

## 10. Descartado

- **Un almacén aparte para lo pendiente, sin crear documentos.** Obligaría a duplicar la lectura (OCR, páginas). Con `intake_pending` y la RLS se reutiliza todo, con el mismo patrón que lo retirado.
- **Un estado «propuesto» en el enlace del expediente.** El encargo 13 no lo tiene, y las propuestas viven aparte.
- **El enlace de subida de 15 minutos del encargo 13 para la carpeta entera.** Es de un caso, admite 20 ficheros y caduca a mitad de una subida larga.
- **El modelo local como clasificador principal:** peor que las reglas en lo medido.
- **Confirmar solo por encima de un umbral** (D2).

## 11. Decisiones de negocio (D-080)

1. **Vía de entrega:**
   - V3 por defecto;
   - V1 solo si quien instala es personal del cliente; lanzada por alguien de E2D, se descarta por D-019;
   - V2: si se construye y cuándo, lo decide el propietario.
2. **Los documentos en «no lo sé»** siguen ocultos, con un aviso en el panel y sin plazo.
3. **Exportaciones del programa de gestión:** CSV y TXT ahora. Excel y los informes de incidencias, después de las entrevistas.
