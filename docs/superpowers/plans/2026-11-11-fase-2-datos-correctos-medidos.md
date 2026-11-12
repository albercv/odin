# Odín · Fase 2, bloque 6: datos correctos medidos — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`). Los pasos y tareas marcados «CTO» no los hace un subagente.

**Objetivo:** medir si Odín da datos correctos con el archivo ficticio del encargo 12, y subir ese porcentaje con cinco cambios del MCP, midiendo el efecto de cada grupo.

**Arquitectura:**
- **Batería de respuestas = el experimento del encargo 12**, que se trae a la rama con un merge y se queda en Python en `experimentos/2026-11-12-archivo-ficticio/` (generador, pila, ejecutor, puntuador). Es una herramienta de medición, no código del producto (D-052). Un objetivo de `make`, `answer-battery`, la ejecuta de punta a punta.
- **El ejecutor guarda las consultas** (herramienta, argumentos, número de resultados) y **el puntuador saca aparte los fallos graves**: «no consta» que sí constaba y sustituido presentado como vigente.
- **Batería de búsqueda determinista** (Java, en `make check`): carga el mismo archivo con el cargador de `SearchBatteryIT` y mide si la página esperada sale entre los 10 primeros con el filtro del proyecto. Sin IA.
- **Cambios del MCP** (TypeScript, sin tocar la API de Java), en el orden de D-052: `listProjects`; `projectName`; `addedAt`; reglas de cita literal; fuente parcial.
- **Mediciones** (CTO): Haiku completa tras los cambios 1 a 3, Haiku completa tras el 4 y el 5, y muestra de Sonnet al cerrar.

**Tecnología:** Python 3 de la biblioteca estándar (batería), Java 21 y PostgreSQL 17.10 con Testcontainers (búsqueda), TypeScript con el SDK de MCP y Vitest (MCP), Docker Compose (pila `odin-medicion`), Claude Code (`claude -p`, solo las mediciones del CTO).

**Diseño:** `docs/superpowers/specs/2026-11-11-datos-correctos-medidos-design.md` (aquí, «spec»). **El apartado 14 (D-052) manda sobre los apartados 2 a 10** donde no cuadren; siguen en pie el aislamiento de la IA, la regla de 2 de 3 y el cierre de la recuperación (6.6). Informe de partida: `docs/informes/2026-11-12-medicion-archivo-ficticio.md` (en `feature/medicion-archivo`, merge ca94774), sección 5.

## Cuándo se ejecuta

- Es el primero del orden de D-052, antes del bloque 7. Cuando lo diga el CEO y tras cerrar la 2a (D-037). Sin migración.
- Este plan parte de la punta de `feature/identidad-pertenencias`. Si la 2a cambia el MCP o `SearchBatteryIT` antes de cerrar, el CTO revisa las tareas 4 a 10.

## Restricciones globales

- **TDD con evidencia:** primero la prueba; se ejecuta y se guardan en el informe la orden y la salida en rojo antes del código. Un fallo de compilación solo vale como rojo para la primera prueba de una clase o un módulo nuevos.
- **Clean Code (D-029):** métodos y funciones de 30 líneas como máximo, también en TypeScript y Python; sin lógica duplicada; comentarios mínimos.
- **Nombres:** en `api/` y `mcp/`, identificadores en inglés y camelCase. En el experimento de Python se conservan los nombres y las claves JSON del experimento (ya en español: son datos de medición); lo nuevo que se añada ahí sigue su estilo.
- **API:** ninguna tarea cambia la API de Java. `api/openapi.json` y los tipos no cambian, y `OpenApiContractIT` lo comprueba.
- **`claude -p`:** nunca en `make check` ni en una prueba (las pruebas usan `fake_claude.py`). Las ejecuciones reales las lanza solo el CTO, con la cuenta de Claude del propietario. Sin clave de API ni servicios nuevos; si algo los necesitara, se pregunta antes al CEO.
- **Red:** ninguna tarea la usa, salvo `claude -p` en los pasos del CTO.
- **Pila de medición:** proyecto Compose propio `odin-medicion`, puertos 28080, 28088 y 28090, fichero `estado/medicion.env` ignorado por git. No se ejecutan `make up` ni `make down`, no se toca `deploy/.env` ni las pilas del propietario.
- **Secretos:** la clave de acceso de cada actor nunca llega a un fichero versionado ni a la salida del ejecutor (ya va en un fichero temporal 0600).
- **Datos:** solo ficticios. Nada global.
- **Cierre de cada tarea:**
  - `make check` y `make demo` terminan con 0;
  - `SearchBatteryIT` mantiene 27 de 30;
  - desde la tarea 5, `ArchiveSearchBatteryIT` cumple su umbral (si imprime `UMBRAL SUPERADO`, se sube `threshold.txt` en esa misma tarea);
  - en las tareas 1 a 3, además, `cd experimentos/2026-11-12-archivo-ficticio/medir && python3 -m unittest` termina con 0.
- **Dónde se trabaja:** en `.worktrees/fase-2`.
- **Git:**
  - cada tarea en su rama `task/fase-2-conocimiento/<tarea>`;
  - commits en español, sin atribución a Claude ni `Co-Authored-By`;
  - sin push;
  - el merge `--no-ff` lo hace el CTO.

## Foco de revisión

1. **Una consulta con herramienta sin resultado o con resultado ilegible:** el ejecutor guarda `resultados: null`, no inventa un número ni se cae. Prueba en la tarea 2 (`toolResultWithoutCountIsNull`).
2. **Pregunta con «no consta» correcto (`abstener`):** no cuenta como «no consta» falso. Prueba en la tarea 3 (`correctAbstentionIsNotFalseNoConsta`).
3. **Sustituido presentado como vigente cuando la respuesta lo salva:** no es fallo grave. Prueba en la tarea 3 (`supersededSavedByWordingIsNotSevere`).
4. **Proyecto borrado que el trabajador aún ve en su contenido, o resultado sin proyecto:** `projectName` es «Proyecto borrado» en el primer caso y `null` en el segundo, nunca el identificador ni un nombre de otro proyecto. Pruebas en la tarea 7.
5. **Un trabajador no miembro de un proyecto cerrado:** `listProjects` no lo lista y ningún `projectName` lo revela. Pruebas en las tareas 6 y 7.
6. **Pregunta de la batería que no nombra ninguna comunidad:** `ArchiveSearchBatteryIT` la deja fuera y la cuenta aparte, no la mide sin filtro. Prueba en la tarea 5 (`questionWithoutProjectNameIsSkippedAndCounted`).
7. **Una medición que se corta a mitad (cuota, sesión caída, aislamiento roto):** el ejecutor para con `PARADA.txt` y código 2, y `make answer-battery` baja la pila y sale con ese código sin puntuar. Prueba en la tarea 1.

---

## Interfaces compartidas (fuente única de nombres)

**Experimento** (`experimentos/2026-11-12-archivo-ficticio/`, ya existe tras el merge):

- `medir/ejecutar.py`: `HERRAMIENTAS` (lista de las herramientas de lectura permitidas), `responder(item, carga, args, ctemp) -> dict` (el registro de `respuestas.jsonl`), `main(argv)`.
- `medir/puntuar.py`: `score_item(item, answer, ctx) -> dict`, `summarise(rows) -> dict`, `_resumen(rows)`.
- `medir/fake_claude.py`: el `claude` falso, con modos por `FAKE_MODO`.
- Registro de `respuestas.jsonl` tras la tarea 2: el de hoy, con `herramientas` (nombres) sustituido por `consultas`:

```json
{"consultas": [{"herramienta": "searchKnowledge", "argumentos": {"queries": ["…"], "projectId": "…"}, "resultados": 7}]}
```

  `resultados` es: `hits` en `searchKnowledge`, `documents` en `listDocuments`, `projects` en `listProjects`, 1 en `readPage` y `verifyCitation`, 0 si la herramienta devuelve «No consta…», y `null` si no se puede contar o la herramienta dio error.
- `resumen` de `puntuacion.json` tras la tarea 3, clave nueva (también en cada entrada de `por_tipo`):

```json
{"fallos_graves": {"no_consta_falso": ["Q012"], "sustituido_vigente": ["Q101"], "total": 2}}
```

  `no_consta_falso` = `falsas_abstenciones` de hoy (la IA se abstiene en una pregunta que tenía respuesta). `sustituido_vigente` = `sustituidos_como_vigentes` de hoy. `total` cuenta preguntas distintas.

**Java** (`odin-adapter-postgres`, código de pruebas, paquete `com.e2d.odin.adapter.postgres`):

```java
final class ArchiveLoader {   // el cargador de SearchBatteryIT, movido y generalizado
    ArchiveLoader(AbstractPostgresIT it);   // usa orgs, docs y projectMembers de la IT
    LoadedArchive load(CompanyId company, UserId uploader, Path corpusDir, Map<String, String> projectNamesByFolder) throws IOException;
    static Map<Integer, String> splitPages(String content);   // la de SearchBatteryIT
}
record LoadedArchive(Map<String, ProjectId> projectByFolder, Map<String, Map<Integer, String>> pagesByDocument) {}
```

La clave de un documento es `carpeta/fichero.txt` (en `SearchBatteryIT`) o `empresa/carpeta/fichero.txt` (en el archivo), como en las citas de cada batería.

**MCP** (`mcp/src/`):

```ts
// tools/listProjects.ts
export function registerListProjects(server: McpServer, api: ApiClient): void;   // { projects: [{ projectId, name }] }
// projects.ts (nuevo): nombres de proyecto desde GET /v1/me, y el mapa documentId -> projectId visto en la sesión
export const DELETED_PROJECT = 'Proyecto borrado';
export interface ProjectNames {
  nameOf(projectId: string | undefined): string | null;
  rememberDocument(documentId: string, projectId: string | undefined): void;
  projectOfDocument(documentId: string): string | undefined;
}
export function createProjectNames(me: Me): ProjectNames;
```

---

### Tarea 1: Traer la batería y `make answer-battery` · CTO + subagente Sonnet

Rama `task/fase-2-conocimiento/traer-bateria`. Spec 14, primer punto.

**Ficheros:**
- **Traer (CTO):** `git merge --no-ff feature/medicion-archivo` en la rama de la tarea (o en `feature/fase-2-conocimiento` antes de ramificar, si el CTO lo prefiere). Trae `experimentos/2026-11-12-archivo-ficticio/` y `docs/informes/2026-11-12-medicion-archivo-ficticio.md`.
- **Crear:** `experimentos/2026-11-12-archivo-ficticio/medir/medir.sh`.
- **Modificar:** `Makefile` (objetivo y `.PHONY`), `README.md` (una sección corta, si ya explica `make check` y `make demo`).
- **Test:** `experimentos/2026-11-12-archivo-ficticio/medir/test_medir.py` (prueba el guion con órdenes falsas en `PATH`).

**Interfaces:**
- Consume: `pila/pila.sh up|down`, `pila/cargar.py`, `medir/ejecutar.py`, `medir/puntuar.py` y sus opciones (los `INFORME.md` de `pila/` y `medir/`).
- Produce: `make answer-battery MODELO=haiku|sonnet [MUESTRA=N]`; `medir.sh` escribe en `resultados/<etiqueta>/`. Variables de entorno opcionales `CLAUDE_BIN` y `ETIQUETA`.

- [ ] **Step 1 (CTO): el merge.** Sin tocar el contenido del experimento. Comprobar `cd experimentos/2026-11-12-archivo-ficticio/medir && python3 -m unittest` en verde (51 pruebas, sin cuota) y `make check` con 0 (el experimento no entra en `make check`).
- [ ] **Step 2: pruebas en rojo.** En `test_medir.py`, con `PATH` apuntando a una carpeta de órdenes falsas (`docker`, `claude`, `openssl`) que anotan lo que reciben:
  - `testRequiresModelo`: sin `MODELO`, `medir.sh` sale con 2 y un mensaje de uso, sin tocar `docker`;
  - `testRejectsUnknownModelo`: `MODELO=opus` sale con 2;
  - `testRunsInOrderAndAlwaysBringsStackDown`: con `MODELO=haiku MUESTRA=2` el orden anotado es `pila.sh up`, carga, ejecutor con `--muestra 2`, puntuador y `pila.sh down`;
  - `testBringsStackDownWhenExecutorStops`: si el ejecutor sale con 2 (`PARADA.txt`), se baja la pila, no se puntúa y el guion sale con 2 (foco 7).
  Ejecutar y guardar la salida en rojo.
- [ ] **Step 3: `medir.sh`.** `set -euo pipefail`; un `trap` que llama a `pila.sh down` al salir, sea cual sea el motivo. Valida `MODELO` (haiku o sonnet) y `MUESTRA` (entero positivo, opcional). La pila se construye con el código de la rama donde se ejecuta (`pila.sh up` hace `--build` sobre `deploy/compose.yaml`); el guion imprime la rama y el commit al empezar y los guarda en `resultados/<etiqueta>/ORIGEN.txt`. Etiqueta por defecto `<AAAA-MM-DD>-<modelo>`, más `-muestra<N>` si hay muestra. Llama, en este orden: `pila.sh up`, `cargar.py`, `ejecutar.py --modelo $MODELO --etiqueta $ETIQUETA [--muestra $MUESTRA] [--claude-bin $CLAUDE_BIN]`, `puntuar.py --respuestas resultados/$ETIQUETA/respuestas.jsonl`. Si el ejecutor sale distinto de 0, no puntúa y propaga el código.
- [ ] **Step 4: el objetivo.** En el `Makefile`, `answer-battery:` llama a `medir.sh` pasando `MODELO` y `MUESTRA`; no entra en `check`, `test` ni `demo`. Añadirlo a `.PHONY`.
- [ ] **Step 5: pasa.** `python3 -m unittest test_medir` en verde; `make -n answer-battery MODELO=haiku MUESTRA=3` imprime la orden esperada; `make check` y `make demo` con 0.
- [ ] **Step 6: commit.** `feat: make answer-battery levanta la pila, carga, mide, puntúa y la baja`.

---

### Tarea 2: El ejecutor guarda las consultas · subagente Sonnet

Rama `task/fase-2-conocimiento/ejecutor-consultas`. Spec 14, «el ejecutor guarda las consultas».

**Ficheros:**
- **Modificar:** `experimentos/2026-11-12-archivo-ficticio/medir/ejecutar.py`, `medir/fake_claude.py`, `medir/INFORME.md` (el registro nuevo y la lista de herramientas).
- **Test:** `medir/test_ejecutar.py`.

**Interfaces:**
- Consume: el flujo `stream-json` de `claude -p`: eventos `assistant` con bloques `tool_use` (`id`, `name`, `input`) y eventos `user` con bloques `tool_result` (`tool_use_id`, `content` como texto o lista de bloques de texto, `is_error`).
- Produce: el campo `consultas` del registro (Interfaces compartidas); sustituye a `herramientas`. `HERRAMIENTAS` pasa a incluir `listProjects`, para que el `--allowedTools` ya lo permita cuando llegue la tarea 6.

- [ ] **Step 1: pruebas en rojo**, con `fake_claude.py` ampliado (modo nuevo `consultas`, que emite un `tool_use` y su `tool_result` por cada herramienta: `listProjects` con 3 proyectos, `searchKnowledge` con 7 `hits`, `searchKnowledge` con el texto «No consta en los documentos de tu empresa.», `readPage`, `verifyCitation` y un resultado con `is_error`):
  - `testSavesEachToolCallWithArguments`: la segunda consulta es `{"herramienta": "searchKnowledge", "argumentos": {"queries": [...], "projectId": "p1"}, "resultados": 7}`;
  - `testCountsByTool`: 3 proyectos, 7 hits, 0 con «No consta…», 1 para `readPage` y `verifyCitation`;
  - `toolResultWithoutCountIsNull`: el resultado con error, o con texto que no es JSON, da `resultados: null`;
  - `testKeepsOrderOfCalls`: el orden de `consultas` es el de las llamadas;
  - `testStripsMcpPrefix`: `herramienta` no lleva `mcp__odin__`;
  - `testNoAccessKeyInRecords`: la clave de acceso del actor no aparece en `respuestas.jsonl`, con los argumentos ya guardados.
  Las pruebas existentes que miraban `herramientas` pasan a mirar `consultas`. Ejecutar y guardar la salida en rojo.
- [ ] **Step 2: implementar.** En `ejecutar.py`: `leer_consultas(eventos) -> list[dict]`, aparte de `responder`, que empareja cada `tool_use` con su `tool_result` por `id` y llama a `contar_resultados(herramienta, texto) -> int | None`. Esta parsea el JSON del texto y cuenta la lista que dice la tabla de las Interfaces. `responder` no pasa de 30 líneas.
- [ ] **Step 3: pasa.** `python3 -m unittest` en `medir/` en verde. Una ejecución de `ejecutar.py` con el `claude` falso sobre 2 preguntas escribe `consultas` en `respuestas.jsonl`.
- [ ] **Step 4: commit.** `feat: el ejecutor guarda cada consulta con sus argumentos y el número de resultados`.

---

### Tarea 3: El puntuador separa los fallos graves · subagente Sonnet

Rama `task/fase-2-conocimiento/fallos-graves`. Spec 14, «fallos graves».

**Ficheros:**
- **Modificar:** `medir/puntuar.py` (`_resumen`), `medir/INFORME.md`.
- **Test:** `medir/test_puntuar.py`.

**Interfaces:**
- Consume: `score_item` (campos `abstiene`, `sustituido_como_vigente`, y `claves` solo en preguntas respondibles) y `_resumen` (`falsas_abstenciones`, `sustituidos_como_vigentes`).
- Produce: `fallos_graves` en el resumen total y en cada `por_tipo` (Interfaces compartidas). No cambia ninguna cifra existente: `porcentaje_minimo`, `porcentaje_estricto` y `fallos` quedan iguales.

- [ ] **Step 1: pruebas en rojo:**
  - `testFalseNoConstaListedAndCounted`: pregunta respondible con `no_lo_se: true` entra en `no_consta_falso` y en `total`;
  - `correctAbstentionIsNotFalseNoConsta`: pregunta con `abstener: true` y `no_lo_se: true` no entra (foco 2);
  - `testSupersededAsCurrentListed`: respuesta que cita un documento de `sustituidos` sin salvarlo entra en `sustituido_vigente`;
  - `supersededSavedByWordingIsNotSevere`: con «sustituido por…», o si la respuesta lidera con el vigente, no entra (foco 3);
  - `testTotalCountsQuestionsOnce`: una pregunta que cumple las dos cosas suma 1 en `total`;
  - `testPerTypeBreakdown`: `por_tipo["version"]["fallos_graves"]` lleva solo los de ese tipo;
  - `testReproducesBaselineNumbers`: sobre `resultados/2026-11-12-haiku/respuestas.jsonl` y `datos/` (versionados) da 35 en `no_consta_falso`, 7 en `sustituido_vigente` y `porcentaje_minimo` 67,6. Si el puntuador, ya revisado tras la primera medición, da otra cifra, se para y se avisa al CTO: la medición de partida no se redefine en silencio.
  Ejecutar y guardar la salida en rojo.
- [ ] **Step 2: implementar** en `_resumen`, con un helper `_fallos_graves(rows)` que reutiliza `falsas_abstenciones` y `sustituidos_como_vigentes` ya calculados (sin duplicar su lógica).
- [ ] **Step 3: pasa.** `python3 -m unittest` en verde; `python3 medir/puntuar.py --respuestas resultados/2026-11-12-haiku/respuestas.jsonl --salida <temporal>` imprime `fallos_graves` con los 35 y 7 de partida.
- [ ] **Step 4: commit.** `feat: el puntuador da aparte los no consta falsos y los sustituidos como vigentes`.

---

### Tarea 4: Cargador compartido de la batería de búsqueda · subagente Sonnet

Rama `task/fase-2-conocimiento/cargador-compartido`. Spec 14, «el cargador de Java es el mismo que el de `SearchBatteryIT`».

**Ficheros:**
- **Crear:** `api/odin-adapter-postgres/src/test/java/com/e2d/odin/adapter/postgres/ArchiveLoader.java`, `LoadedArchive.java`.
- **Modificar:** `SearchBatteryIT.java` (usa el cargador; `splitPages` y `PAGE_MARK` se mueven).
- **Test:** `ArchiveLoaderTest.java` (de unidad, sin base de datos, solo `splitPages`).

**Interfaces:**
- Consume: `AbstractPostgresIT` (`orgs`, `docs`, `NOW`, `newUser`).
- Produce: `ArchiveLoader` y `LoadedArchive` (Interfaces compartidas). Un proyecto por carpeta, cerrado; los documentos se guardan como hoy (`READY`, páginas con `replacePages`).

**Decisión:** el cargador queda en el mismo módulo, como clase de pruebas, y no en un `test-jar`: solo lo usan dos IT del mismo módulo, y la batería de respuestas carga por SQL desde Python. Si el bloque 2 lo necesita desde otro módulo, publica el `test-jar` entonces.

- [ ] **Step 1: prueba en rojo.** `ArchiveLoaderTest`: `splitPagesReadsPageMarks` (texto con `=== Página 1 ===` y `=== Página 2 ===` da dos páginas recortadas) y `splitPagesWithoutMarksIsOnePage` (sin marcas, una sola página con todo el texto: así lo guarda Odín y así lo lee el puntuador). Rojo: la clase no existe (vale como rojo de clase nueva).
- [ ] **Step 2: implementar `ArchiveLoader`** moviendo `loadCorpus`, `loadDocument` y `splitPages` de `SearchBatteryIT`, parametrizando la carpeta y el mapa carpeta → nombre de proyecto. `SearchBatteryIT` lo llama con sus `COMMUNITIES` y sigue haciendo su propia medición.
- [ ] **Step 3: pasa.** `cd api && mvn -q -pl odin-adapter-postgres -am test -Dtest=ArchiveLoaderTest -Dsurefire.failIfNoSpecifiedTests=false`; `cd api && mvn -q -pl odin-adapter-postgres -am verify -Dit.test=SearchBatteryIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` imprime `BATERIA 27/30` o más. `make check` con 0.
- [ ] **Step 4: commit.** `refactor: el cargador de la batería de búsqueda se comparte`.

---

### Tarea 5: Batería de búsqueda determinista sobre el archivo · subagente Sonnet

Rama `task/fase-2-conocimiento/busqueda-archivo`. Spec 14, «batería de búsqueda determinista en `make check`».

**Ficheros:**
- **Crear:** `api/odin-adapter-postgres/src/test/java/com/e2d/odin/adapter/postgres/ArchiveSearchBatteryIT.java`; `api/odin-adapter-postgres/src/test/resources/archive-battery/threshold.txt` (lo escribe la primera ejecución).
- **Modificar:** `api/odin-adapter-postgres/pom.xml` solo si el `failsafe` necesita la propiedad de la ruta.

**Interfaces:**
- Consume: `ArchiveLoader` (tarea 4), `PostgresKnowledgeSearch`, `datos/manifiesto.json` (empresas, y proyectos con `clave`, `empresa`, `nombre`) y `datos/bateria.json` (cada pregunta con `citas_resueltas: [{doc, paginas, ancla}]`, `actor`, `tipo`).
- Produce: la línea `ARCHIVO a/b (x%) fallos=[…] sin_proyecto=n`, el umbral en `threshold.txt` y la prueba que falla por debajo.

**Decisión de rutas:** los datos se leen de `experimentos/2026-11-12-archivo-ficticio/datos/` con una ruta relativa al módulo (`../../experimentos/…`) y la propiedad `odin.archive.dir` para cambiarla. No se copian a los recursos de prueba: son unos 5 MB y 1.236 ficheros, y una copia se desincroniza del generador (el experimento es la fuente). `threshold.txt` sí va en los recursos de prueba, como el de `SearchBatteryIT`. Si el directorio no existe, la prueba falla con un mensaje claro; no se salta en silencio.

- [ ] **Step 1: pruebas en rojo:**
  - `archiveSearchRecallAtTenDoesNotRegress`: carga las dos empresas del manifiesto (cada una con su empresa de pruebas y un actor miembro de sus proyectos cerrados) y mide las preguntas con `citas_resueltas` no vacías: acierta si alguna de las páginas esperadas (donde `QuoteMatcher.containsLiteral(texto, ancla)`) sale entre los 10 primeros con el filtro del proyecto de la pregunta;
  - `questionWithoutProjectNameIsSkippedAndCounted`: una pregunta cuyo texto no nombra ningún proyecto no se mide y suma en `sin_proyecto` (foco 6);
  - `thresholdIsWrittenOnFirstRunAndEnforced`: sin `threshold.txt` lo escribe (lo medido, redondeado a la baja con dos decimales, como `SearchBatteryIT`); con un umbral por encima de lo medido, falla.
  Ejecutar y guardar en rojo.
- [ ] **Step 2: implementar.** El proyecto de la pregunta es el del nombre de proyecto más largo del manifiesto que aparece en su texto (nombres como «Calle Abeto 1» y «Calle Abeto 16» se contienen: gana el más largo), dentro de la empresa del actor. La consulta es la pregunta tal cual (el archivo no tiene reformulaciones). Quedan fuera las preguntas sin documento esperado y las de solo ficha: esta batería mide encontrar el documento, y las fichas las carga por SQL la batería de respuestas.
- [ ] **Step 3: la primera medición.** Ejecutar la IT una vez, guardar la línea `ARCHIVO …` en el informe de la tarea y subir `threshold.txt`. Anotar el tiempo de la IT; si pasa de 2 minutos en `make check`, avisar al CTO antes de seguir.
- [ ] **Step 4: pasa.** `cd api && mvn -q -pl odin-adapter-postgres -am verify -Dit.test=ArchiveSearchBatteryIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`; después `make check` y `make demo` con 0 y `SearchBatteryIT` en 27 de 30.
- [ ] **Step 5: commit.** `feat: batería de búsqueda determinista sobre el archivo ficticio`.

---

### Tarea 6: MCP, `listProjects` · subagente Sonnet

Rama `task/fase-2-conocimiento/mcp-list-projects`. Spec 7 y 14, cambio 1.

**Ficheros:**
- **Crear:** `mcp/src/tools/listProjects.ts`.
- **Modificar:** `mcp/src/server.ts` (registro e `INSTRUCTIONS`), `mcp/test/server.test.ts`; `mcp/scripts/demo-client.ts` y `deploy/demo/run-demo.sh` solo si comprueban la lista de herramientas.

**Interfaces:**
- Consume: `ApiClient.me()` → `Me.projects: { id, name }[]` (solo lo que ve el trabajador, D-047). No se añade ningún método al cliente.
- Produce: `registerListProjects(server, api)`; la herramienta `listProjects`, sin argumentos, devuelve `{ projects: [{ projectId, name }] }`. Solo se registra con `SEARCH_KNOWLEDGE`.
- Frase nueva de `INSTRUCTIONS`, exacta: «Si la pregunta nombra un proyecto (una comunidad, un cliente, una obra), búscalo con listProjects y filtra la búsqueda con su projectId.»

- [ ] **Step 1: pruebas en rojo**, en `server.test.ts`:
  - `registersListProjectsWithSearchCapability`: con `SEARCH_KNOWLEDGE` la lista de herramientas incluye `listProjects`; con solo `SAVE_KNOWLEDGE_CARD`, no (se actualiza `registersOnlyEnabledCapabilities`);
  - `listProjectsReturnsIdAndName`: con `/v1/me` devolviendo dos proyectos, el resultado es `{ projects: [{ projectId, name }, …] }`;
  - `listProjectsOnlyShowsMyProjects`: la herramienta no usa otra ruta que `/v1/me`; lo que `/v1/me` no devuelve (un proyecto cerrado del que no es miembro), no sale (foco 5);
  - `instructionsTellToFilterByProject`: `getInstructions()` contiene la frase exacta.
  Ejecutar (`cd mcp && npx vitest run test/server.test.ts`) y guardar en rojo.
- [ ] **Step 2: implementar** `listProjects.ts`, registrarla y añadir la frase a `INSTRUCTIONS`.
- [ ] **Step 3: pasa.** `npx vitest run`; `make check` y `make demo` con 0 (si `demo-client.ts` lista herramientas, se actualiza su lista esperada en esta tarea).
- [ ] **Step 4: commit.** `feat: el MCP lista los proyectos del trabajador y pide filtrar por proyecto`.

---

### Tarea 7: MCP, `projectName` en los resultados · subagente Sonnet

Rama `task/fase-2-conocimiento/mcp-project-name`. Spec 14, cambio 2.

**Ficheros:**
- **Crear:** `mcp/src/projects.ts`.
- **Modificar:** `mcp/src/server.ts` (crea `createProjectNames(me)` y lo pasa a las herramientas), `mcp/src/tools/searchKnowledge.ts`, `readPage.ts`, `listDocuments.ts`.
- **Test:** `mcp/test/server.test.ts` y `mcp/test/projects.test.ts` (para `createProjectNames`).

**Interfaces:**
- Consume: `Me.projects`; `Hit.projectId?` (`searchKnowledge`); `WorkerDocumentView.projectId?` (`listDocuments`). `Page` **no trae `projectId`**.
- Produce: `ProjectNames`, `DELETED_PROJECT` y `createProjectNames` (Interfaces compartidas). Cada resultado de las tres herramientas lleva `projectName: string | null`. `registerSearchKnowledge`, `registerReadPage` y `registerListDocuments` ganan un tercer argumento, `projects: ProjectNames`.

**Decisión:** `readPage` no puede saber el proyecto sin cambiar la API, porque `Page` no lo trae. El MCP lo resuelve con lo que ya vio en la sesión: `searchKnowledge` y `listDocuments` anotan `documentId → projectId` (`rememberDocument`) y `readPage` lo consulta (`projectOfDocument`). Si la IA lee una página de un documento que no salió antes en la sesión, `projectName` es `null`. No hay llamadas nuevas a la API.

- [ ] **Step 1: pruebas en rojo:**
  - `searchHitsCarryProjectName`: documentos y fichas llevan el nombre del proyecto de `GET /v1/me`;
  - `deletedProjectShowsDeletedProjectName`: un `projectId` que no está en `Me.projects` sale como «Proyecto borrado» (foco 4);
  - `hitWithoutProjectHasNullProjectName`: sin `projectId` el nombre es `null`, no «Proyecto borrado»;
  - `listDocumentsCarriesProjectName`;
  - `readPageUsesProjectSeenInThisSession`: tras un `searchKnowledge` que devolvió el documento, `readPage` da su `projectName`;
  - `readPageWithUnseenDocumentHasNullProjectName`;
  - `projectNameNeverRevealsAnotherProject`: con dos proyectos en `Me`, cada resultado lleva el nombre de su proyecto y nunca el del otro (foco 5);
  - `searchResultStillHasProjectId`: `projectId` se mantiene junto a `projectName`.
  Ejecutar y guardar en rojo.
- [ ] **Step 2: implementar** `createProjectNames` (mapa id → nombre construido de `Me.projects`; `nameOf` da `null` si el id es `undefined` y `DELETED_PROJECT` si no está) y usarlo en las tres herramientas. Las descripciones dicen que `projectName` es el proyecto (comunidad, cliente u obra) del resultado.
- [ ] **Step 3: pasa.** `npx vitest run`; `make check` y `make demo` con 0; `OpenApiContractIT` sin cambios.
- [ ] **Step 4: commit.** `feat: los resultados del MCP traen el nombre del proyecto`.

---

### Tarea 8: MCP, `addedAt` en lugar de `date` · subagente Sonnet

Rama `task/fase-2-conocimiento/mcp-added-at`. Spec 14, cambio 3.

**Ficheros:**
- **Modificar:** `mcp/src/tools/searchKnowledge.ts` (`toCitation`), `mcp/src/server.ts` (la frase de las fichas), `mcp/test/server.test.ts`; `mcp/scripts/demo-client.ts` si lee `date`.

**Interfaces:**
- Consume: `Hit.date` (la API no cambia).
- Produce: en cada resultado de `searchKnowledge`, `addedAt` ocupa el lugar de `date`. La descripción de la herramienta dice: «`addedAt` es la fecha en que entró en Odín, no la del documento.» Las fichas se siguen presentando por título, autor y fecha; la instrucción pasa a decir «fecha de alta» para las fichas.

- [ ] **Step 1: pruebas en rojo:** se actualizan `searchMapsHitsWithCitation` y `searchMapsCardsAsCardsWithOriginAndWithoutPage` (esperan `addedAt`, no `date`); `searchDescriptionExplainsAddedAt` (la descripción contiene «no la del documento»); `instructionsKeepCardCitationRule` (sigue pidiendo título, autor y fecha de alta, y conserva `no tienen página` y `verifyCitation es solo para documentos`). Ejecutar y guardar en rojo.
- [ ] **Step 2: implementar** el cambio de nombre en `toCitation` y en las descripciones.
- [ ] **Step 3: pasa.** `npx vitest run`; `make check` y `make demo` con 0.
- [ ] **Step 4: commit.** `feat: el MCP da addedAt, la fecha de alta, en lugar de date`.

---

### Tarea 9: MCP, reglas de cita literal · subagente Sonnet

Rama `task/fase-2-conocimiento/mcp-cita-literal`. Spec 14, cambio 4.

**Ficheros:**
- **Modificar:** `mcp/src/server.ts` (`INSTRUCTIONS`), `mcp/test/server.test.ts`.

**Interfaces:**
- Produce: dos frases exactas en `INSTRUCTIONS`: «Copia cada fragmento literal por separado; no unas trozos con puntos suspensivos.» y «De una ficha, cita solo su texto, sin el título, el autor ni la fecha.»

- [ ] **Step 1: prueba en rojo:** `instructionsRequireLiteralQuotes` comprueba las dos frases exactas y que la regla de fichas de la 2a («cítalas por título, autor y fecha») sigue ahí. Ejecutar y guardar en rojo.
- [ ] **Step 2: implementar.** Redactar para que la presentación de la ficha (título, autor y fecha) no choque con la cita (solo su texto): lo nuevo habla del fragmento de la cita. Si no se puede sin cambiar el sentido de la regla de la 2a, parar y avisar al CTO.
- [ ] **Step 3: pasa.** `npx vitest run`; `make check` y `make demo` con 0.
- [ ] **Step 4: commit.** `feat: la instrucción del MCP pide citas literales`.

---

### Tarea 10: MCP, instrucción de fuente parcial · subagente Sonnet

Rama `task/fase-2-conocimiento/mcp-fuente-parcial`. Spec 7 y 14, cambio 5.

**Ficheros:**
- **Modificar:** `mcp/src/server.ts` (`INSTRUCTIONS`), `mcp/test/server.test.ts`.

**Interfaces:**
- Produce: la frase exacta: «Afirma solo lo que dicen las citas. Si la fuente es parcial, porque falta el documento que lo regula o el que tienes no lo dice todo, dilo y no completes la conclusión.»

- [ ] **Step 1: pruebas en rojo:** `instructionsAskForPartialSourceWarning` (la frase exacta) e `instructionsStayUnderLengthBudget` (la instrucción completa no supera 2.200 caracteres; con cinco cambios acumulados, un tope evita que crezca sin freno. Si hoy ya lo supera, el tope se fija en el valor medido y se anota). Ejecutar y guardar en rojo.
- [ ] **Step 2: implementar.**
- [ ] **Step 3: pasa.** `npx vitest run`; `make check` y `make demo` con 0.
- [ ] **Step 4: commit.** `feat: la instrucción del MCP pide avisar de la fuente parcial`.

---

### Tarea 11: Medición completa con Haiku tras los cambios 1 a 3 · CTO

Rama de medición: `feature/fase-2-conocimiento` con las tareas 1 a 8 mergeadas. **No se ejecuta hasta que lo diga el CEO.**

- [ ] **Step 1:** `make answer-battery MODELO=haiku` (unos 14 minutos, 216 preguntas). El guion construye la pila con el código de la rama y la baja al terminar.
- [ ] **Step 2: qué se guarda,** en `experimentos/2026-11-12-archivo-ficticio/resultados/<AAAA-MM-DD>-haiku-listprojects/`: `respuestas.jsonl` (con `consultas`), `puntuacion.json`, `ORIGEN.txt` (rama y commit), el modelo exacto que dio `claude`, los tokens, la duración y el coste notificado.
- [ ] **Step 3: qué se compara** con la medición de partida (146/216, 67,6 %), en una tabla para el informe:
  - porcentaje total y por tipo;
  - **fallos graves frente a la partida: 35 «no consta» falsos y 7 sustituidos como vigentes**;
  - cuántas preguntas llaman a `listProjects` (de `consultas`) y el porcentaje de las que lo usan frente a las que no;
  - de las 48 de causa «proyecto», cuántas se arreglan;
  - citas no literales (36 de 374), datos sin respaldo (62) y fugas de aislamiento (0, deben seguir en 0).
- [ ] **Step 4:** si Haiku empeora el porcentaje o los fallos graves respecto de la partida, el CTO analiza las consultas antes de seguir con la tarea 12.

### Tarea 12: Medición completa con Haiku tras los cambios 4 y 5 · CTO

Con las tareas 1 a 10 mergeadas. **No se ejecuta hasta que lo diga el CEO.**

- [ ] **Step 1:** `make answer-battery MODELO=haiku`. Se guarda en `resultados/<AAAA-MM-DD>-haiku-instruccion/`, con lo mismo que la tarea 11.
- [ ] **Step 2: se compara** con la partida y con la tarea 11: porcentaje total y por tipo, fallos graves (en total y por tipo) y citas no literales, para separar el efecto de los cambios 1 a 3 del de los cambios 4 y 5.
- [ ] **Step 3:** si el segundo grupo empeora algo, el CTO revisa la instrucción antes de cerrar.

### Tarea 13: Muestra de Sonnet al cerrar · CTO

Con las tareas 1 a 10 mergeadas. **No se ejecuta hasta que lo diga el CEO.**

- [ ] **Step 1:** `make answer-battery MODELO=sonnet MUESTRA=30` (la muestra de partida son 30 preguntas, 3 por tipo; con la semilla del ejecutor se obtiene la misma muestra si no cambió la batería; si cambió, se dice). Se guarda en `resultados/<AAAA-MM-DD>-sonnet-muestra/`.
- [ ] **Step 2: se compara** con la muestra de partida (28/30, 93,3 %; 1 «no consta» falso) y con Haiku en las mismas preguntas. La completa con Sonnet es del cierre de la recuperación (spec 6.6), no de este bloque.

### Tarea 14: Informe y cierre del bloque · CTO

- [ ] **Step 1:** revisión final de la rama con Opus.
- [ ] **Step 2:** informe en `docs/informes/` con: las cifras de las tres mediciones frente a la de partida (146/216, 67,6 %; 35 «no consta» falsos; 7 sustituidos como vigentes), el efecto de cada grupo de cambios, el umbral de `ArchiveSearchBatteryIT` y su primera medición, la máquina, los modelos exactos, los tokens y la duración.
- [ ] **Step 3:** su entrada en el registro técnico del repositorio OCR, con el siguiente número libre, y copia del informe a la carpeta de notas para el CEO.
- [ ] **Step 4:** el informe dice que este bloque no cierra la recuperación (spec 14). Si Haiku ya pasa del 90 % en la segunda medición, lo dice, pero el umbral de regresión se fija al final del bloque 2.

---

## Cobertura del spec

- Spec 14, batería = archivo, merge, puntuador en Python: tarea 1.
- Spec 14, el ejecutor guarda las consultas: tarea 2. Fallos graves aparte: tarea 3.
- Spec 14, batería de búsqueda en `make check` con el cargador compartido: tareas 4 y 5.
- Spec 14, cambios 1 a 5 del MCP, en su orden: tareas 6 a 10. Spec 7: tareas 6 y 10.
- Spec 14, mediciones: tareas 11 a 13. Informe y registro: tarea 14.
- Sin cambios, y sin tarea: el aislamiento de la IA y la regla de 2 de 3 (ya en el ejecutor del experimento) y el cierre de la recuperación (6.6, en el índice de orden).
- Descartado por D-052: `SearchScaleBatteryIT`, el corpus de 899, el puntuador en TypeScript, `AnswerBatteryIT`, `sets.json`, el contrafactual y la sonda.

## Orden y paralelismo

- Orden: 1 a 14; las mediciones 11 a 13 esperan al CEO.
- Se pueden hacer a la vez: 2 y 3 (dos ficheros de Python distintos), y 4 y 5 (Java) frente a 6 a 10 (TypeScript). Las tareas 6 a 10 van en serie: todas tocan `server.ts` y `server.test.ts`.
- La tarea 2 va antes de la 11: sin `consultas` no se separan los fallos de búsqueda de los de lectura.
- La tarea 5 va antes de la 6 para que cada cambio del MCP cierre ya con `ArchiveSearchBatteryIT` en `make check`, como pide la regla común de cierre desde este bloque.

## Decisiones del CTO al ejecutar (2026-11-12)

- **Tres encargos, no catorce,** para ahorrar suscripción (D-052):
  - tareas 1 a 3 (Python y `make answer-battery`);
  - tareas 4 y 5 (Java);
  - tareas 6 a 10 (MCP).
  Cada encargo va con su rama, sus pruebas y su revisión.
- **Una sola medición completa con Haiku** después de las tareas 6 a 10, y la muestra de Sonnet. Es lo que pide el CEO. Las tareas 11 y 12 se juntan, así que no se separa el efecto de cada cambio del MCP.
- **`readPage` no lleva `projectName`:** su respuesta no trae el proyecto, y para darlo habría que cambiar la API. La IA ya recibe el nombre en `searchKnowledge` y `listDocuments`. Se descarta la memoria de sesión en el MCP, porque el servidor HTTP puede no conservar estado entre llamadas.
