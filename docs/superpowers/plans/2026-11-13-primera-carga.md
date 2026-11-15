# Odín · Primera carga (opción B) · Plan

> Se ejecuta con `superpowers:subagent-driven-development`. Diseño: `docs/superpowers/specs/2026-11-13-primera-carga-design.md` («spec»).
>
> **No se ejecuta todavía.** Diseño validado por el CEO (D-080). Espera a dos cosas: que el propietario lo priorice (y diga si quiere también V2), y que el encargo 13 esté en `develop` (V24, `AddToCaseFiles`, `WithdrawDocument` y la componente de subida del panel).

## Restricciones globales

**Las de los planes anteriores** (`2026-11-12-administracion-grupo.md`):
- TDD con el rojo guardado;
- capas comprobadas con ArchUnit;
- métodos de 30 líneas como máximo;
- identificadores en inglés y textos al usuario en español;
- aislamiento D-014 con una prueba con actor de otra empresa;
- contrato y tipos regenerados en la misma tarea;
- `make check` y `make demo` terminan con 0;
- commits en español, sin atribución y sin push.

**Además:**
- **D1:** ningún trabajador ve un documento pendiente. Cada tarea que toque consultas de documentos añade su prueba de aislamiento con un documento pendiente.
- **D2:** ningún efecto sin la confirmación de una persona. Las propuestas no cambian documentos.
- **D4:** nada sale del equipo. El modelo es el Ollama local del etiquetado.
- **Migración:** `V25__intake.sql`, por encima de la V24 del encargo 13; se renumera al integrar.
- **Sin otro modelo de expediente:** los enlaces se confirman con `AddToCaseFiles` del encargo 13.
- **Recursos de prueba:** el archivo ficticio revuelto se genera desde `experimentos/2026-11-13-primera-carga/` (montaje determinista, semilla 13), con un subconjunto pequeño copiado a `src/test/resources/intake-battery/`, como `search-battery`.

## Tarea 1: Modelo, migración y D1 en la base · Sonnet

Rama `task/primera-carga/modelo`. Spec, apartados 3 y 2.

- **Ficheros:**
  - `V25__intake.sql`: `intake_batch`, `intake_project_candidate` e `intake_proposal`; las columnas nuevas de `document`; la política `document_scope` con las condiciones de lo retirado y de lo pendiente; RLS de las tablas nuevas;
  - el dominio `intake` (`IntakeBatch` con su máquina de estados, `IntakeProposal`, `ProjectCandidate`);
  - los repositorios PostgreSQL y en memoria;
  - `Sql` y `ScopedTransactions`, con `app.include_intake`.
- [ ] **Paso 1:** pruebas que fallan:
  - transiciones del lote (`OPEN` → `READING` → `IN_REVIEW` → `CLOSED`) y las prohibidas;
  - IT de la RLS: con la conexión sin el filtro SQL, un trabajador no ve un documento pendiente ni sus páginas, y con `app.include_intake` el administrador sí;
  - IT del índice único: dos lotes abiertos en la misma empresa fallan;
  - IT de las claves foráneas compuestas, con `scan_of` de otra empresa.
- [ ] **Paso 2:** migración, dominio y adaptadores.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: modelo de la primera carga y documentos pendientes ocultos en la base».

## Tarea 2: Lote y subida al lote · Sonnet

Rama `task/primera-carga/lote`, que sale de la tarea 1. Spec, apartado 4, pasos 1 a 3.

- **Ficheros:**
  - `StartIntake`, `AddFileToIntake` (reutiliza `UploadDocument`; no lo copia), `CloseIntakeUpload` y `GetIntakeBatch`;
  - `IntakeController`, el contrato y los tipos.
- [ ] **Paso 1:** pruebas que fallan:
  - el documento nace pendiente, sin proyecto y con `intake_path`;
  - un SHA-256 que ya está en la empresa no crea nada, cuenta como «ya estaba» y devuelve el documento que existe;
  - un fichero retirado en este lote no vuelve a entrar al reanudar; uno retirado fuera del lote sí entra como nuevo (V24 del encargo 13);
  - el mismo fichero dos veces da el mismo documento (idempotente);
  - `GET files?sha256=` responde qué huellas ya están;
  - un administrador de otra empresa recibe 404;
  - un trabajador recibe 403;
  - cerrar pasa el lote a `READING`.
- [ ] **Paso 2:** casos de uso, controlador, contrato y tipos.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: lote de primera carga con subida reanudable».

## Tarea 3: Lista de proyectos (D6) · Sonnet

Rama `task/primera-carga/lista-proyectos`. Spec, apartado 4, paso 4.

- **Ficheros:**
  - `ProjectRules.discover`, en el dominio;
  - `ImportProjectList` (CSV o TXT, un nombre por línea), `ListProjectCandidates` y `ConfirmProjectCandidates`. Esta usa `CreateProject` o une a un proyecto que ya existe.
- [ ] **Paso 1:** pruebas que fallan:
  - el descubrimiento en el subconjunto de prueba saca las comunidades sin ninguna de más (en el estudio, 60 de 61, y falta solo la que no es comunidad);
  - importar una lista con nombres repetidos o vacíos;
  - confirmar crea los proyectos;
  - unir no crea ninguno.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: lista de proyectos de la primera carga, importada o descubierta».

## Tarea 4: Reglas de proyecto y de ficha de caso · Sonnet

Rama `task/primera-carga/reglas`. Spec, apartado 5.

- **Ficheros:**
  - `ProjectRules.match` (variantes del nombre, gana el más largo, la cabecera pesa más, la carpeta suma su señal) y `CaseFileRules`, en el dominio. Se pasan a Java desde `experimentos/2026-11-13-primera-carga/parte_b/clasificar.py`; el script es la referencia;
  - la batería `IntakeRulesBatteryTest` sobre `intake-battery`.
- [ ] **Paso 1:** pruebas que fallan:
  - las unitarias del script de la parte B, pasadas a JUnit;
  - «bloque 2» frente a la comunidad gemela;
  - una carpeta que contradice al texto da confianza `LOW`;
  - la batería: el proyecto con confianza alta acierta al 100 %, y el tipo, la ficha y el número de caso también.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: reglas de proyecto y de ficha de caso de la primera carga».

## Tarea 5: Duplicados, escaneos y versiones · Sonnet

Rama `task/primera-carga/duplicados`. Spec, apartado 5, duplicados.

- **Ficheros:**
  - `DuplicateFinder`, en el dominio: tejas de 5 palabras, MinHash de 64 funciones y 32 bandas, umbral de 0,8, solo dentro del mismo proyecto propuesto, y la señal «sustituye a … nº». Referencia: `parte_a/textlib.py` y `parte_a/duplicates.py`;
  - la batería `IntakeDuplicatesBatteryTest`.
- [ ] **Paso 1:** pruebas que fallan:
  - las unitarias de la parte A, pasadas a JUnit;
  - la misma plantilla en otra comunidad no se propone;
  - un escaneo con las mismas fechas e importes da `SAME_AS_SCAN`;
  - una póliza renovada da `SUPERSEDES`, y la más reciente sustituye a la anterior;
  - la batería: copias y escaneos con 1,00 y versiones con una precisión de 0,83 o más.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: duplicados, escaneos y versiones de la primera carga».

## Tarea 6: Modelo local para lo que queda · Sonnet

Rama `task/primera-carga/modelo-local`. Spec, apartado 5, modelo local.

- **Ficheros:**
  - el puerto `ProjectProposer`;
  - `OllamaProjectProposer`, en `odin-adapter-tagger-ollama`, con el mismo cliente y la misma configuración que el etiquetador.
- [ ] **Paso 1:** pruebas que fallan, con un Ollama falso como el del etiquetado:
  - una respuesta que no está en la lista se descarta;
  - «ninguno» no propone nada;
  - el tiempo agotado no propone nada y no tumba el lote;
  - sin `ODIN_TAGGER_URL` no se llama.
  - Batería con el modelo real bajo la marca `model`, fuera de `make check`, como `tagging-battery`: 14 de 15 o más en lo que dejan las reglas.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: propuesta de proyecto con el modelo local para lo que no resuelven las reglas».

## Tarea 7: Trabajo de clasificación · Sonnet

Rama `task/primera-carga/clasificar`. Spec, apartado 4, paso 5.

- **Ficheros:**
  - `ClassifyIntake`, un trabajo periódico como `TagPendingDocuments`;
  - su programación en `odin-bootstrap`.
- [ ] **Paso 1:** pruebas que fallan:
  - solo los documentos `READY` y pendientes sin propuestas;
  - primero las reglas, y el modelo solo si no hay `HIGH`;
  - el modelo solo con la cola del lector vacía;
  - sin lista confirmada no hay propuestas de proyecto;
  - cada propuesta lleva su `reason` literal;
  - al no quedar nada pendiente de leer ni de proponer, el lote pasa a `IN_REVIEW`.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: clasificación de los lotes de primera carga».

## Tarea 8: Confirmación · Sonnet, con revisión de permisos con Opus

Rama `task/primera-carga/confirmar`. Spec, apartado 6 (API y efectos).

- **Ficheros:**
  - `ListIntakeProposals` (agrupadas por proyecto y confianza), `ConfirmIntakeProposals` (un grupo, todo o nada) y `DecideIntakeProposal` (una a una);
  - los efectos llaman a `MarkCaseDocuments`, `SupersedeDocument`, `AddToCaseFiles` y `WithdrawDocument`, y ponen `scan_of`;
  - controlador, contrato y tipos.
- [ ] **Paso 1:** pruebas que fallan:
  - confirmar el proyecto hace visible el documento al trabajador del proyecto y a nadie más (D1, D-047);
  - «de toda la empresa» lo deja sin proyecto y visible para toda la empresa;
  - «no lo sé» lo deja pendiente y oculto;
  - una pareja o un enlace con algún proyecto sin confirmar da 409;
  - `CASE_LINK` llama a `AddToCaseFiles` agrupado por caso, con el administrador como actor;
  - el grupo es todo o nada;
  - quién y cuándo quedan guardados;
  - un administrador de otra empresa recibe 404 y un trabajador 403.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** revisión de permisos con Opus. Después, `make check` termina con 0. Commit «feat: confirmación de la primera carga por grupos».

## Tarea 9: Panel, bandeja «Primera carga» · Sonnet

Rama `task/primera-carga/panel`. Spec, apartado 6 (panel).

- **Ficheros:** `pages/Intake.tsx` y sus componentes, con el sistema visual D-039; la entrada en la barra lateral, solo para administradores.
- [ ] **Paso 1:** pruebas de componente que fallan:
  - los cinco pasos;
  - un grupo se despliega con documentos y motivos antes de confirmar;
  - confirmar un grupo con `ConfirmDialog`;
  - una a una con «de toda la empresa» y «no lo sé»;
  - los avisos de sin lector y sin modelo;
  - recuentos.
  - Pantalla en `visual.spec.ts` con axe.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: bandeja de la primera carga en el panel».

## Tarea 10a: Vía de la orden (V3; V1 si instala personal del cliente) · Haiku

Vía por defecto (D-080). Rama `task/primera-carga/orden`. Spec, apartado 8.

- **Ficheros:**
  - `deploy/instalacion/primera_carga.py` y su `make primera-carga CARPETA=…`, con el cerrojo de `deploy/instalacion/`;
  - `docs/guias/primera-carga.md`, la guía para el administrador del cliente.
- [ ] **Paso 1:** pruebas `unittest` que fallan:
  - recorrer la carpeta (sin enlaces simbólicos ni ocultos);
  - las huellas ya subidas se saltan;
  - la contraseña se pide y nunca se escribe;
  - los avisos de partida.
- [ ] **Paso 2:** implementa y escribe la guía.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: orden de primera carga y guía para el administrador del cliente».

## Tarea 10b: Vía del panel (V2) · Sonnet

Solo si el propietario decide construirla (D-080). Rama `task/primera-carga/subida-carpeta`. Spec, apartado 8.

- **Ficheros:** el paso «Subir carpeta» de `Intake.tsx`, con `webkitdirectory`. Reutiliza la lista de ficheros y el progreso de la página de subida del encargo 13.
- [ ] **Paso 1:** pruebas que fallan:
  - el SHA-256 se calcula en el navegador;
  - lo que ya está no se sube;
  - como mucho dos subidas a la vez;
  - un fallo se reintenta;
  - tras recargar, elegir la misma carpeta sigue donde se quedó.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: subida de carpeta reanudable en la primera carga».

## Tarea 11: De principio a fin y medición · Sonnet

Rama `task/primera-carga/medicion`. Spec, apartado 9.

- [ ] **Paso 1:** extremo a extremo en una pila desechable, con la vía elegida, sobre el archivo ficticio revuelto:
  - subir;
  - cortar y reanudar;
  - leer;
  - clasificar;
  - confirmar por grupos;
  - el trabajador ve solo lo confirmado de sus proyectos;
  - la prueba de aislamiento D1 completa.
- [ ] **Paso 2:** informe con tiempo, memoria y acierto, frente a las cifras del estudio. Cada cifra con el equipo, los modelos y el número de ejecuciones. Si se usa `claude -p`, el gasto se llama «consumo equivalente (precio de API, sin cobro)».
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0. Commit «test: primera carga de principio a fin y medición».

## Orden

- Las tareas van en serie: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10a (V3, por defecto), 10b solo si el propietario la pide, y 11.
- La 8 lleva revisión de permisos con Opus.
- Al final, revisión de la rama con Opus, centrada en D1 (que nada pendiente se vea por ningún camino) y en que ningún efecto se aplique sin confirmación (D2).
- Después, `make check` y `make demo` los ejecuta el CTO.
- **Dependencias:**
  - la tarea 8 necesita `AddToCaseFiles` y `WithdrawDocument`;
  - la 10b necesita la componente de subida del encargo 13;
  - la medición del enlace con expedientes necesita el archivo ampliado de ese encargo.
