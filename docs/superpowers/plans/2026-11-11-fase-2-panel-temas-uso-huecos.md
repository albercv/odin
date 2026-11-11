# Odín · Fase 2, bloque 4: panel con temas, uso, volumen entregado y huecos — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que el administrador pueda:
- gestionar los temas y revisar los que pone la IA, sabiendo cuántos documentos esperan a ser etiquetados;
- ver recuentos descriptivos de uso, sin nada que ordene o compare personas;
- ver el volumen que Odín entrega a la IA del trabajador;
- ver los huecos «no consta», sin autor ni hora;
- y que la revisión de fichas le pida que la nota trate del contenido.

**Arquitectura:**
- **Lectura:** un puerto `UsageStatistics` con su consulta de PostgreSQL, y casos de uso que solo comprueban permisos y el mes.
- **Volumen:** el puerto `DeliveryMeter`, alimentado por un filtro del adaptador web.
- **Panel:** tres pantallas nuevas y un cambio en la de revisión, con los componentes de la dirección visual.

**Tecnología:** Java 21, Spring Boot 4.1.2, PostgreSQL 17.10 con seguridad por filas, React 19, Vitest, axe y Playwright.

**Diseño:** `docs/superpowers/specs/2026-11-11-panel-temas-uso-huecos-design.md` (aquí, «spec»). Se apoya en:
- el spec base;
- el de la 2a;
- el de la dirección visual (`2026-11-10-direccion-visual-design.md`);
- el del bloque 3.

Quien ejecute una tarea lee el spec, el de la dirección visual y el del bloque 3.

## Cuándo se ejecuta

- Tras el cierre de la 2a y **después de los bloques 2 y 3**, cuando lo diga el CEO.
  - Del bloque 2 usa `question_record.kind`.
  - Del bloque 3 usa la API de temas y `tagging-status`.
- Las respuestas y la validación del CEO (D-040, D-043, D-044 y D-045) ya están aplicadas en el spec y en este plan.

## Restricciones globales

- **TDD con evidencia:** primero la prueba; se ejecuta y se guardan en el informe la orden y la salida en rojo antes del código.
- **Capas:** `odin-domain` solo `java.*`; `odin-application` solo `odin-domain` y `java.*`; Spring solo en los adaptadores y en `odin-bootstrap`. ArchUnit lo comprueba.
- **Clean Code (D-029):** métodos de 30 líneas como máximo (Checkstyle y ESLint), sin lógica duplicada y con comentarios mínimos.
- **Nombres:** identificadores en inglés, explicativos y en camelCase. Textos del panel, documentación y commits en español.
- **D-005, D-036 y D-045, sin excepciones:**
  - por persona, solo `questions` y `cardsSaved`, ordenados por nombre;
  - no se ordena por cantidad en ningún sitio, ni en la API ni en el panel;
  - no hay listas de preguntas por persona;
  - los huecos van sin autor y con el día, sin la hora, también en la API;
  - no hay recuentos por persona de aprobadas, corregidas ni rechazadas;
  - el volumen entregado va por empresa y herramienta, nunca por persona;
  - se rotula «Volumen entregado a la IA del trabajador», nunca «consumo» ni «gasto»;
  - ningún texto del panel cita decisiones internas (`D-xxx`).
- **No se construye** `AiUsageLedger` ni la tabla `ai_usage` (D-040, D-045).
- **Aislamiento (D-014):** cada caso de uso con actor lleva una prueba con un administrador de otra empresa. Las consultas llevan su filtro de empresa y la seguridad por filas.
- **Panel:**
  - componentes de la dirección visual (`PageHeader`, `Section`, `Table`, `EmptyState`, `StatusPill`, `FormDialog`, `ConfirmDialog` y `Toast`) y sus reglas de accesibilidad;
  - ninguna acción se da por terminada sin un paso de Playwright contra la API real (regla de la fase 1.1).
- **Contrato:** si cambia la API, en la misma tarea se regeneran `api/openapi.json` (`cd api && mvn -q verify -Dodin.updateOpenApi=true`) y los tipos (`npm run generate:types` en `panel/` y en `mcp/`).
- **Cierre de cada tarea:** `make check` y `make demo` terminan con 0, y `SearchBatteryIT` mantiene 27 de 30.
- **Dónde se trabaja:** en `.worktrees/fase-2`. No se ejecutan `make up` ni `make down`.
- **Git:**
  - cada tarea en su rama `task/fase-2-conocimiento/<tarea>`;
  - commits en español, sin atribución a Claude ni `Co-Authored-By`;
  - sin push;
  - el merge `--no-ff` lo hace el CTO.
- **Migración:** `V13__delivered_volume.sql`, solo en la tarea 2, o la siguiente libre.
- **Datos:** solo ficticios.

## Foco de revisión

1. **Dos personas con cantidades muy distintas:** la tabla sigue ordenada por nombre y nada destaca a ninguna. Prueba en la tarea 1 (`peopleAreOrderedByNameWhateverTheirCounts`) y en la tarea 4 (`peopleTableHasNoSortControls`).
2. **Varias personas borradas en el mismo mes:** salen sumadas en una sola fila anónima. Prueba en la tarea 1 (`departedPeopleAreSummedInOneAnonymousRow`).
3. **Un mes fuera de rango o mal escrito** (`2026-13`, `2024-01`, `abc`): 400 con un mensaje claro, sin datos. Prueba en la tarea 1 (`monthOutsideLastTwelveIsRejected` y `malformedMonthIsRejected`).
4. **El mismo hueco escrito con tildes y mayúsculas distintas:** sale una sola vez, con sus veces sumadas y el día de la última. Prueba en la tarea 1 (`gapsGroupIgnoringAccentsCaseAndSpaces`).
5. **Una respuesta de error del trabajador** (403 o 404): no cuenta como entregada. Prueba en la tarea 2 (`onlySuccessfulWorkerResponsesAreMetered`).
6. **Un equipo que pasa noches apagado:** la pantalla de temas lo recuerda cuando el más antiguo lleva más de un día esperando. Prueba en la tarea 3 (`remindsToKeepMachineOnAfterOneDay`).

---

## Interfaces compartidas (fuente única de nombres)

```java
// application/port
public interface UsageStatistics {
    CompanyUsage companyUsage(CompanyId companyId, YearMonth month, ZoneId zone);
    List<PersonUsage> peopleUsage(CompanyId companyId, YearMonth month, ZoneId zone);  // por nombre; bajas sumadas al final
    List<GapGroup> gaps(CompanyId companyId, YearMonth month, ZoneId zone, int limit);
}
// application/usage
public record QuestionCounts(int search, int similarCases) { }
public record CardCounts(int saved, int approved, int corrected, int rejected, int pending) { }
public record DocumentCounts(int uploaded, int ready, int failed, int waitingForReader) { }
public record TaggingCounts(int tagged, int pending) { }
public record CompanyUsage(QuestionCounts questions, int questionsWithoutResults, CardCounts cards,
        DocumentCounts documents, Optional<TaggingCounts> tagging) { }
public record PersonUsage(Optional<UserId> userId, String displayName, int questions, int cardsSaved,
        boolean departed) { }
public record GapGroup(String text, QuestionKind kind, int times, LocalDate lastAskedOn) { }  // día, sin hora
public final class ReportMonth { static YearMonth resolve(Optional<String> requested, Clock clock, ZoneId zone); }
public class GetUsageStats { public UsageReport execute(Actor actor, CompanyId companyId, Optional<String> month); }
public record UsageReport(YearMonth month, CompanyUsage company, List<PersonUsage> people) { }
public class ListGaps { public List<GapGroup> execute(Actor actor, CompanyId companyId, Optional<String> month); }

// Tarea 2, volumen entregado
public enum WorkerTool { DESCRIBE_ME, SEARCH, SIMILAR_CASES, READ_PAGE, LIST_DOCUMENTS, VERIFY_CITATION,
        SAVE_CARD, MY_CARDS, MY_QUESTIONS }
// rutas: GET /v1/me, POST /v1/search, POST /v1/similar-cases, GET /v1/documents/{id}/pages/{n}, GET /v1/documents,
//        POST /v1/citations/verify, POST /v1/cards, GET /v1/me/cards, GET /v1/me/questions
public interface DeliveryMeter {
    void record(CompanyId companyId, WorkerTool tool, long bytes, Instant at);
    List<DeliveryTotal> totals(CompanyId companyId, YearMonth month);
}
public record DeliveryTotal(WorkerTool tool, long calls, long bytes) { }
public class GetDeliveredVolume { public DeliveredVolumeReport execute(Actor actor, CompanyId companyId, Optional<String> month); }
public record DeliveredVolumeReport(YearMonth month, List<DeliveryTotal> totals) { }
```

- La fila de bajas es `PersonUsage(Optional.empty(), "Personas dadas de baja", n, m, true)`.
- Mensajes de `ReportMonth`:
  - 400, «El mes tiene que tener la forma AAAA-MM»;
  - 400, «Solo se pueden consultar los 12 últimos meses».

**Rutas:**

| Ruta | Respuesta | Tarea |
|---|---|---|
| `GET /v1/admin/companies/{companyId}/usage-stats?month=` | `UsageStatsView { month, company, people }` | 1 |
| `GET /v1/admin/companies/{companyId}/gaps?month=` | `GapView[] { text, kind, times, lastAskedOn }`, con `lastAskedOn` en formato `YYYY-MM-DD` | 1 |
| `GET /v1/admin/companies/{companyId}/delivered-volume?month=` | `DeliveredVolumeView { month, totals: [{ tool, calls, bytes }] }` | 2 |
| `GET /v1/admin/companies/{companyId}/tagging-status` | La del bloque 3 | — |

---

### Tarea 1: Uso y huecos en la API · Sonnet

Rama `task/fase-2-conocimiento/uso-y-huecos`. Spec 5.2, 5.4 y 6.

**Ficheros:**
- **Crear:**
  - aplicación: `application/port/UsageStatistics.java` y `application/usage/`, con los valores, `ReportMonth`, `GetUsageStats` y `ListGaps`, y sus pruebas;
  - PostgreSQL: `PostgresUsageStatistics`, con su IT;
  - web: `adapter/web/admin/UsageController.java`;
  - el doble `InMemoryUsageStatistics`.
- **Modificar:** `UseCaseConfiguration`, `AdapterConfiguration`, `TestWiring`, `Responses`, el contrato y los tipos.

**Valores:**
- Todos los recuentos, por mes y con `odin.timezone` (bloque 3): `date_trunc('month', at AT TIME ZONE :zone)`.
- **Quién sale en `peopleUsage`** (spec 5.2): las pertenencias vivas de la empresa, más los autores con preguntas o fichas en esa empresa y mes. Los que tienen `app_user.deleted_at` se suman en la fila de bajas.
- **Huecos:**
  - `hit_count = 0`, agrupados por `regexp_replace(lower(unaccent(text)), '\s+', ' ', 'g')`;
  - `text` es el `min(text)` del grupo;
  - `lastAskedOn` es `(max(asked_at) AT TIME ZONE :zone)::date`;
  - orden por `max(asked_at) DESC` y límite 200.
  - Ni `author_id` ni nada de la persona en el `SELECT`.
- `tagging` vacío si la empresa no tiene temas.
- Permisos: `AccessPolicy.requireAdminOf`, con 404 y luego 403.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `ReportMonthTest`:
    - `defaultsToCurrentMonthInZone`;
    - `monthOutsideLastTwelveIsRejected`;
    - `malformedMonthIsRejected`.
  - `GetUsageStatsTest`:
    - `returnsCompanyAndPeople`;
    - `workerIsForbidden`;
    - `adminOfAnotherCompanyIsForbidden`;
    - `unknownCompanyIsNotFound`.
  - `ListGapsTest`: `returnsGapsWithoutAuthor` y `adminOfAnotherCompanyIsForbidden`.
- [ ] **Paso 2:** implementa los casos de uso y el doble.
- [ ] **Paso 3:** `PostgresUsageStatisticsIT`, con datos ficticios de dos empresas:
  - `countsQuestionsByKindAndWithoutResults`;
  - `countsCardsByStatusAtCompanyLevelOnly`;
  - `countsDocumentsByStatus`;
  - `peopleAreOrderedByNameWhateverTheirCounts`;
  - `personWithLiveMembershipAndNoActivityAppearsWithZeros`;
  - `departedPeopleAreSummedInOneAnonymousRow`;
  - `gapsGroupIgnoringAccentsCaseAndSpaces`;
  - `gapsHaveNoAuthorAndOnlyTheDay`;
  - `nothingOfAnotherCompanyIsCounted`;
  - `monthBoundaryUsesInstallationZone`.
- [ ] **Paso 4:** web y contrato:
  - `AdminApiTest`: una prueba por ruta, una de mes mal escrito y `gapViewHasDateWithoutTime`;
  - `IsolationTest.otherCompanyAdminCannotReadUsageOrGaps`.
- [ ] **Paso 5:** regenera el contrato y los tipos. `make check` y `make demo` terminan con 0.
- [ ] **Paso 6:** commit «feat: uso descriptivo y huecos de la documentación en la API».

### Tarea 2: Volumen entregado a la IA del trabajador · Sonnet

Rama `task/fase-2-conocimiento/volumen-entregado`. Spec 5.3.

**Ficheros:**
- **Crear:**
  - aplicación: `application/port/DeliveryMeter.java` y, en `application/usage/`, `WorkerTool`, `DeliveryTotal`, `GetDeliveredVolume` y `DeliveredVolumeReport`, con sus pruebas;
  - PostgreSQL: `PostgresDeliveryMeter`, con su IT, y `V13__delivered_volume.sql`;
  - web: `adapter/web/security/DeliveryMeteringFilter.java`, con su prueba.
- **Modificar:** `UsageController`, `WebConfig` (orden de filtros), el cableado, el contrato y los tipos.

**Valores:**
- `V13`: `delivery_usage (company_id, month date, tool text, calls bigint, bytes bigint, PRIMARY KEY (company_id, month, tool))`, con seguridad por filas por empresa y los permisos de `odin_app` que tiene sobre `question_record`.
- `DeliveryMeteringFilter`:
  - va después de `ActorResolutionFilter` y actúa sobre todas las rutas del trabajador del spec 5.3, cada una con su `WorkerTool`;
  - una ruta del trabajador sin `WorkerTool` hace fallar una prueba (`everyWorkerRouteHasATool`), para que un bloque futuro no se quede fuera sin que nadie lo vea;
  - envuelve la salida contando bytes, sin copiar el cuerpo;
  - al terminar con 200, llama a `record(actor.requireCompany(), tool, bytes, now)`;
  - un fallo al registrar se escribe en el registro del servidor y no rompe la respuesta.
- `record` es un `INSERT … ON CONFLICT (company_id, month, tool) DO UPDATE SET calls = delivery_usage.calls + 1, bytes = delivery_usage.bytes + :bytes`. `month` es el primer día del mes en `odin.timezone`.
- `GetDeliveredVolume`: permisos como en la tarea 1. Devuelve todas las herramientas de `WorkerTool`, con 0 en las que no tienen filas.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `GetDeliveredVolumeTest`:
    - `returnsTotalsByToolWithZeros`;
    - `workerIsForbidden`;
    - `adminOfAnotherCompanyIsForbidden`.
  - `DeliveryMeteringFilterTest`:
    - `countsBytesOfSuccessfulSearch`;
    - `everyWorkerRouteHasATool`;
    - `onlySuccessfulWorkerResponsesAreMetered`;
    - `adminRoutesAreNotMetered`;
    - `meterFailureDoesNotBreakResponse`.
  - `PostgresDeliveryMeterIT`: `upsertSumsCallsAndBytes`, `monthUsesInstallationZone` y `rowLevelSecurityHidesOtherCompany`.
- [ ] **Paso 2:** implementa y escribe `V13`.
- [ ] **Paso 3:** web y contrato: `AdminApiTest.deliveredVolumeRoute` e `IsolationTest.otherCompanyAdminCannotReadDeliveredVolume`.
- [ ] **Paso 4:** regenera el contrato y los tipos. `make check` y `make demo` terminan con 0.
- [ ] **Paso 5:** commit «feat: volumen entregado a la IA del trabajador».

### Tarea 3: Pantallas de temas · Sonnet

Rama `task/fase-2-conocimiento/panel-temas`. Spec 5.1. Usa la API del bloque 3.

**Ficheros:**
- **Crear:** `panel/src/pages/Topics.tsx`, `panel/src/components/DocumentTopics.tsx`, `TaggingStatus.tsx` y `TaggingBacklog.tsx`, con sus pruebas y sus `.module.css`.
- **Modificar:**
  - `panel/src/layout/Sidebar.tsx`, con la entrada «Temas» y el icono que haya en `Icon`;
  - `panel/src/App.tsx`, `panel/src/pages/Documents.tsx` y `panel/src/api/client.ts`;
  - `panel/src/mockApi.ts`;
  - `panel/e2e/admin.spec.ts`.

**Valores:**
- **`Topics`:**
  - tabla «Nombre · Definición · acciones»;
  - «Añadir tema» y «Editar» abren un `FormDialog` con «Nombre» y «Definición»;
  - «Borrar» abre un `ConfirmDialog` con «¿Borrar el tema <nombre>? Dejará de verse en los documentos.»;
  - el aviso tras añadir o editar es un `Toast`: «Los documentos de esta empresa se volverán a etiquetar». Después, `TaggingBacklog` se vuelve a cargar con el recuento nuevo.
  - Sin temas, un `EmptyState`: «Sin temas no se etiqueta ningún documento».
- **`TaggingBacklog`** (spec 5.1), encima de la tabla, con `tagging-status`:
  - con pendientes, «<N> documentos esperan a ser etiquetados. El más antiguo espera desde el <día en español>.»;
  - si `oldestPendingSince` tiene más de 24 horas y la ventana no es `always`, añade «El etiquetado se hace entre las <desde> y las <hasta>: el equipo tiene que estar encendido a esas horas.»;
  - sin pendientes, «Todos los documentos están etiquetados.».
- **`Documents`:** columna «Temas», donde cada tema de la IA lleva una marca «IA», con texto accesible «asignado por IA», y un `StatusPill` del etiquetado:
  - `PENDING` o sin fila: «Sin etiquetar»;
  - `TAGGING`: «Etiquetando»;
  - `DONE`: «Etiquetado»;
  - `FAILED`: «Falló».
- **`DocumentTopics`**, en el detalle del documento:
  - temas activos con su origen;
  - un selector para añadir uno de la lista;
  - «Quitar» en cada tema;
  - la sección «Quitados», con «Quitado por <nombre>» y «Recuperar»;
  - el botón «Volver a etiquetar».

- [ ] **Paso 1:** pruebas, una por acción, que comprueban la petición enviada y lo que se ve; comprueba que fallan:
  - `Topics.test.tsx`:
    - `listsTopics`;
    - `addsTopic`;
    - `editsTopic`;
    - `deletesAfterConfirmation`;
    - `showsApiErrorInField`;
    - `emptyStateWithoutTopics`;
    - `noticeAfterChangeAndBacklogReloads`.
  - `TaggingBacklog.test.tsx`:
    - `showsPendingCountAndOldestDay`;
    - `remindsToKeepMachineOnAfterOneDay`;
    - `noReminderWithAlwaysWindow`;
    - `saysAllTaggedWithoutPending`.
  - `DocumentTopics.test.tsx`:
    - `marksAiTopics`;
    - `addsTopic`;
    - `removesTopic`;
    - `recoversRemovedTopic`;
    - `requestsRetag`.
  - `Documents.test.tsx`: `showsTaggingStatusLabels`.
  - Todas pasan axe sin infracciones.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** Playwright, en `admin.spec.ts`, contra la API real:
  - crear y editar un tema;
  - ver el estado del etiquetado;
  - quitar y recuperar un tema de un documento.
- [ ] **Paso 4:** `make check` y `make demo` terminan con 0.
- [ ] **Paso 5:** commit «feat: panel de temas y revisión de los temas de cada documento».

### Tarea 4: Pantallas de uso y huecos, y nota de la revisión · Sonnet

Rama `task/fase-2-conocimiento/panel-uso-huecos`. Spec 5.2 a 5.5.

**Ficheros:**
- **Crear:** `panel/src/pages/Usage.tsx`, `panel/src/pages/Gaps.tsx` y `panel/src/components/MonthPicker.tsx`, con sus pruebas y sus `.module.css`.
- **Modificar:**
  - `Sidebar.tsx`, con «Uso» y «Huecos»;
  - `App.tsx`, `api/client.ts` y `mockApi.ts`;
  - `panel/src/pages/Review.tsx` y `Review.test.tsx`, o el componente del diálogo de rechazar y corregir que use;
  - `panel/e2e/admin.spec.ts`.

**Interfaces que consume:** las rutas de las tareas 1 y 2.

**Valores:**
- **`MonthPicker`:** un `<select>` con la etiqueta «Mes» y los 12 últimos meses en español («noviembre de 2026»).
- **`Usage`:**
  - `Section` «Empresa», con la tabla de recuentos;
  - `Section` «Por persona», con la tabla «Persona · Preguntas · Fichas aportadas». La tabla:
    - no tiene botones de orden;
    - se presenta en el orden de la API;
    - no lleva fila de total.
  - debajo, la nota fija «Recuentos descriptivos. No sirven para valorar a nadie.»;
  - `Section` «Volumen entregado a la IA del trabajador»:
    - tabla «Herramienta · Llamadas · Bytes»;
    - el texto fijo «Odín no usa ninguna IA externa. La IA de cada trabajador es suya, y Odín no ve lo que gasta. Esto es el volumen de texto que Odín le entrega: no son tokens ni un coste.».
- **`Gaps`:**
  - tabla «Consulta · Tipo · Veces · Última vez»:
    - el tipo es «Búsqueda» o «Casos parecidos»;
    - «Última vez» es el día, con el formato largo en español, sin hora.
  - nota: «Consultas que hizo la IA de un trabajador y que no encontraron nada en los documentos. Pueden indicar documentación que falta.»;
  - sin huecos, un `EmptyState`: «Este mes no hay consultas sin resultados».
- **`Review`:** los diálogos de rechazar y de corregir muestran, encima del campo de la nota, «El autor verá esta nota tal cual. Escribe sobre el contenido de la ficha, no sobre la persona.».
  - Al rechazar, la nota sigue siendo obligatoria.
  - Al corregir, es opcional.
- **Ningún texto lleva `D-0`,** y ningún **rótulo** (títulos, encabezados de tabla y del menú) lleva «consumo» ni «gasto». Lo comprueba una prueba que recorre las tres pantallas.
  - El texto fijo del volumen entregado sí dice «lo que gasta», porque lo aprobó el CEO (D-045). Por eso la prueba mira solo los rótulos.
- **Nombres de las herramientas en la tabla:** «Perfil», «Búsqueda», «Casos parecidos», «Leer página», «Listar documentos», «Verificar cita», «Guardar ficha», «Mis fichas» y «Mis preguntas».

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `Usage.test.tsx`:
    - `showsCompanyCounts`;
    - `peopleTableKeepsApiOrder`;
    - `peopleTableHasNoSortControls`;
    - `showsDescriptiveNoteWithoutInternalReferences`;
    - `showsDeliveredVolumeWithFixedText`;
    - `neverLabelsItConsumptionOrSpending`;
    - `changingMonthReloads`.
  - `Gaps.test.tsx`:
    - `listsGapsWithoutAuthor`;
    - `lastTimeIsTheDayWithoutHour`;
    - `emptyStateWithoutGaps`;
    - `showsKindLabels`.
  - `Review.test.tsx`: `rejectDialogWarnsTheAuthorWillSeeTheNote` y `correctDialogWarnsTheAuthorWillSeeTheNote`.
  - Todas pasan axe sin infracciones.
- [ ] **Paso 2:** implementa.
- [ ] **Paso 3:** Playwright, contra la API real:
  - ver el uso del mes con las preguntas que hace la demo, y el volumen entregado;
  - ver el hueco de su pregunta «no consta»;
  - rechazar una ficha y ver el aviso de la nota.
- [ ] **Paso 4:** `make check` y `make demo` terminan con 0.
- [ ] **Paso 5:** commit «feat: panel de uso, volumen entregado y huecos, y aviso de la nota del revisor».

### Cierre del bloque · CTO

- **Revisión final de la rama con Opus**, con la lista del spec 8:
  - nada ordena por persona;
  - nada destaca a nadie;
  - no hay listas de preguntas por persona;
  - los huecos van sin autor ni hora;
  - ningún texto cita decisiones internas.
- **Rendimiento:** con datos ficticios a escala (10.000 preguntas y 2.000 documentos en una empresa, generados por un script del scratchpad), se miden las tres rutas. La cifra va al informe, con la máquina.
- `make check` y `make demo` ejecutados por el CTO.
- Informe del bloque, T-018 en el registro técnico y copia del informe en la carpeta de notas para el CEO.

## Orden y paralelismo

- 1 y luego 2: tocan el mismo controlador y el mismo cableado.
- La tarea 3 puede ir en paralelo con la 1 y la 2, porque solo toca `panel/`. Se mergea después de ellas, por los tipos regenerados.
- La 4, al final.

## Modelos de los revisores

- **Opus:** tareas 1 y 2 (D-005, D-036 y aislamiento) y tarea 4, por lo que se ve de las personas.
- **Sonnet:** tarea 3.
