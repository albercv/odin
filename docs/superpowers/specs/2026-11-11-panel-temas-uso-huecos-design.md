# Odín · Fase 2, bloque 4: panel con temas, uso, volumen entregado y huecos

- Fecha: 2026-11-11. Autor: CTO (segunda sesión). Encargo 11, apartado «Panel del administrador».
- Decisiones de negocio que lo originan: D-005, D-030, D-031, D-036 y D-039. Se aplican también D-047 y D-048.
- Amplía `2026-11-09-api-central-design.md` («spec base») y se apoya en `2026-11-10-identidad-pertenencias-design.md` («spec 2a»), `2026-11-10-direccion-visual-design.md` («dirección visual») y `2026-11-11-etiquetado-temas-design.md` («bloque 3»).
- Rama: `feature/fase-2-conocimiento`. Se ejecuta cuando cierre la fase 2a y es el último de la fase 2 (D-049), después de los bloques 1, 3, 6, 7, 2 y 5.

## 1. Por qué

El encargo 11 pide un panel desde el que el administrador controle, además de lo que ya hay:
- los **temas** de cada empresa y los que pone la IA (D-031);
- el **consumo de la IA externa**, que hace falta para decidir quién lo paga (D-030). Como Odín no usa hoy ninguna IA externa, lo que se enseña es el volumen que Odín entrega a la IA del trabajador (D-040, D-045);
- las **estadísticas de uso**: solo descriptivas, sin rankings, notas ni juicios sobre personas (D-005, D-036);
- y, por D-036, las preguntas que acabaron en «no consta», como **huecos de la documentación**, sin autor.

## 2. Decisiones

**De negocio que se aplican:**
- **D-005:** recuentos descriptivos por empresa o persona; nada que ordene, puntúe o compare personas.
- **D-036:**
  - el panel nunca lista las preguntas de una persona;
  - los huecos se enseñan sin autor;
  - no hay contadores de fichas rechazadas por persona (spec base 6.8).
- **D-039:** dirección visual, con sus componentes y su accesibilidad.

**Respuesta del CEO sobre el consumo** (D-040 y D-045, 2026-11-11):
- No se construye un registro para un motor propio que no existe: nada de `AiUsageLedger` ni de la tabla `ai_usage`. Se hará cuando haya motor o etiquetado externo.
- Se enseña el volumen que Odín entrega a la IA del trabajador, en bytes, por empresa, mes y herramienta. Nunca por persona.
- Se rotula «Volumen entregado a la IA del trabajador», nunca «consumo» ni «gasto», y la pantalla explica que no son tokens ni coste.
- Un texto fijo dice que Odín no usa ninguna IA externa, que la IA del trabajador es suya y que Odín no ve lo que gasta.

**Otras precisiones del CEO** (D-044 y D-045): el diálogo de revisión de fichas, el estado del etiquetado en la pantalla de temas, el día sin hora en los huecos y los textos sin referencias internas. Están en los apartados 5.1, 5.2, 5.4 y 5.5.

**Del CTO.** Cada decisión dice lo que cuesta si resulta errónea:

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| Por persona solo hay dos recuentos: preguntas hechas y fichas aportadas, en el periodo | Son actividad, no calidad. Aprobadas, corregidas y rechazadas se cuentan solo por empresa | Un recuento más |
| La tabla por persona está siempre ordenada por nombre. No se puede ordenar por cantidad ni hay «más activos», gráficos por persona, medias ni colores que destaquen a alguien | Ordenar por cantidad ya es un ranking (D-005) | — |
| No hay estadísticas por equipo | No existen equipos (spec 2a, 3) | Una dimensión más, cuando existan |
| Huecos: consultas con 0 resultados de búsqueda o de casos parecidos, agrupadas por texto igual (sin tildes, sin mayúsculas y con los espacios normalizados), con cuántas veces y la última fecha | D-036. Agrupar por significado sería otra IA interpretando. El tipo sale de `question_record.kind`, que añade el bloque 2; `hit_count` es el número de resultados en los dos tipos | Agrupar de otra forma |
| El texto del hueco es la consulta que mandó la IA del trabajador, y el panel lo dice | Es lo que se guarda (spec base 6.6), no lo que escribió la persona | — |
| Todo se calcula al pedirlo, con consultas sobre los datos que ya existen. Sin tablas de resumen | Con el volumen de una oficina basta. Se mide al verificar | Una vista materializada |
| Periodo: por meses, con el mes en curso por defecto, hasta 12 meses atrás | Simple y suficiente. Si la validación legal fija un plazo de conservación de preguntas menor (D-022, D-036), el periodo se acorta a ese plazo | Fechas libres |

## 3. Fuera de alcance

- **Configuración de la IA** («qué IA responde y con qué cuenta») y **motor propio**: no están en este encargo de la fase 2.
- Exportar las estadísticas.
- Gráficos. Se enseñan cifras en tablas. Si más adelante hay gráficos, nunca por persona.
- Marcar un hueco como resuelto.
- Estadísticas del grupo entero en una sola vista. El administrador del grupo las ve empresa a empresa.

## 4. Quién ve qué

| Pantalla | Administrador del grupo | Administrador de la empresa |
|---|---|---|
| Temas | Todas las empresas | La suya |
| Uso, con el volumen entregado | Todas las empresas, una a una | La suya |
| Huecos | Todas las empresas, una a una | La suya |
| Revisión de fichas, la que ya existe | Todas las empresas, una a una | La suya |

Las rutas siguen el orden de siempre: 404 si la empresa no existe; 403 si no la administra.

## 5. Pantallas

Cada pantalla está dentro de la empresa elegida, con el selector de empresa de la 2a, y se usan los componentes de la dirección visual: `PageHeader`, `Section`, `Table`, `EmptyState`, `StatusPill`, `FormDialog`, `ConfirmDialog` y `Toast`. El menú lateral gana «Temas», «Uso» y «Huecos». «Volumen entregado a la IA del trabajador» es una sección dentro de «Uso». Ningún texto del panel cita decisiones internas (`D-xxx`) ni términos del equipo técnico (D-045).

### 5.1 Temas

Usa la API del bloque 3.

- **Lista de temas:**
  - nombre y definición;
  - añadir y editar en un `FormDialog`;
  - borrar con `ConfirmDialog`;
  - errores de la API en el campo, con los mensajes del bloque 3.
  - Si la lista está vacía, un `EmptyState` explica que sin temas no se etiqueta.
- **Aviso al cambiar la lista:** «Los documentos de esta empresa se volverán a etiquetar». Cuántos están pendientes lo dice el estado del etiquetado, que se vuelve a cargar tras el cambio.
- **Estado del etiquetado, siempre a la vista** (D-043, D-045), con `GET /v1/admin/companies/{companyId}/tagging-status` del bloque 3:
  - «N documentos esperan a ser etiquetados. El más antiguo espera desde el <día>»;
  - si el más antiguo lleva más de un día, añade «El etiquetado se hace entre las <hora> y las <hora>: el equipo tiene que estar encendido a esas horas», con la ventana de la instalación;
  - con la ventana `always`, sin la segunda frase;
  - sin pendientes, «Todos los documentos están etiquetados».
- **En la lista de documentos:**
  - columna «Temas», con cada tema y la marca «IA» en los que puso la IA;
  - estado del etiquetado: «Sin etiquetar», «Etiquetando», «Etiquetado» o «Falló», con un `StatusPill`.
- **Detalle de un documento:**
  - sus temas con su origen;
  - añadir y quitar;
  - «Volver a etiquetar».
  - Lo quitado se ve aparte, como «Quitado por <administrador>», para poder recuperarlo.

### 5.2 Uso

`GET /v1/admin/companies/{companyId}/usage-stats?month=YYYY-MM`. Devuelve:
- **De la empresa:**
  - preguntas, por tipo (búsqueda y casos parecidos);
  - preguntas sin resultados;
  - fichas aportadas, aprobadas, corregidas, rechazadas y pendientes;
  - documentos subidos, listos, que fallaron y que esperan al lector;
  - documentos etiquetados y pendientes, si hay temas.
- **Por persona:** `people[]`, con `userId`, `displayName` (nombre y apellidos, `User.displayName()`), `memberCode`, `questions` y `cardsSaved`, **ordenado por `displayName`**.
  - **`memberCode`** es el código de la persona con el de la empresa, `personCode-companyCode` (por ejemplo `7F3K-E2D`), calculado como en `MemberView.of` (D-048). Sale solo en el panel: nunca en el MCP, ni en las citas, ni en las fichas.
  - **Quién sale:** las personas con pertenencia viva en esa empresa, aunque tengan cero, y las que tienen preguntas o fichas en esa empresa ese mes. No hace falta saber desde cuándo pertenece cada una, un dato que la 2a no guarda.
  - **Personas borradas** (`app_user.deleted_at`): se suman en una sola fila, «Personas dadas de baja», sin `userId`, ni nombre, ni código. Así no se distinguen entre ellas.
  - `questions` cuenta las filas de `question_record` de la persona en esa empresa y mes, de los dos tipos. `cardsSaved` cuenta las de `knowledge_card` con ese `author_id`, que es obligatorio desde V2. Todas las fichas las aporta un trabajador: no hay fichas del sistema.
- **En la pantalla:**
  - tabla de la empresa y tabla por persona, con el nombre y el código de cada una (la fila de bajas no lleva código);
  - sin ordenar por columnas, sin destacar a nadie y sin totales por persona fuera de la tabla;
  - una nota fija: «Recuentos descriptivos. No sirven para valorar a nadie».

### 5.3 Volumen entregado a la IA del trabajador (D-040, D-045)

`GET /v1/admin/companies/{companyId}/delivered-volume?month=YYYY-MM`. Devuelve por herramienta (perfil, búsqueda, casos parecidos, leer página, listar documentos, verificar cita, guardar ficha, mis fichas y mis preguntas) las llamadas y los bytes de las respuestas.

- **Cómo se mide:**
  - un filtro del adaptador web cuenta los bytes de cada respuesta 200 de **todas las rutas del trabajador** y llama al puerto `DeliveryMeter`:
    - `GET /v1/me`;
    - `POST /v1/search`, `GET /v1/documents`, `GET /v1/documents/{documentId}/pages/{pageNumber}` y `POST /v1/citations/verify`;
    - `POST /v1/similar-cases`, del bloque 2;
    - `GET /v1/me/cards` y `GET /v1/me/questions`, del bloque 5.
    - `POST /v1/cards` (guardar ficha) recibe texto más que entregarlo, pero su respuesta también cuenta.
    - Una ruta del trabajador que añada un bloque futuro entra en el mismo filtro, con su herramienta;
  - cuenta el cuerpo tal como lo escribe la API, sin compresión, y la empresa sale del `Actor` de la clave, con su contexto de seguridad por filas;
  - se guarda en `delivery_usage (company_id, month, tool, calls, bytes)` con un `UPSERT` por petición.
- **Por empresa y herramienta,** nunca por persona.
- **En la pantalla,** una sección de «Uso» con el título «Volumen entregado a la IA del trabajador»:
  - la tabla «Herramienta · Llamadas · Bytes»;
  - el texto fijo «Odín no usa ninguna IA externa. La IA de cada trabajador es suya, y Odín no ve lo que gasta. Esto es el volumen de texto que Odín le entrega: no son tokens ni un coste.».
  - Nunca se usan las palabras «consumo» ni «gasto» como rótulo.
- **No se construye** un registro de IA externa del cerebro (`AiUsageLedger`, `ai_usage`): se hará cuando exista el motor propio o el etiquetado externo (D-040).

### 5.4 Huecos

`GET /v1/admin/companies/{companyId}/gaps?month=YYYY-MM`. Devuelve hasta 200 grupos: `text` (la primera forma del texto), `kind`, `times` y `lastAskedOn`, de más reciente a más antiguo. Sin autor ni ningún dato de la persona.

- **`lastAskedOn` es el día** (fecha en `ODIN_TIMEZONE`), sin la hora (D-045). En una empresa pequeña, la hora bastaría para saber quién preguntó. La API tampoco da la hora.

En la pantalla:
- tabla con «Consulta», «Tipo», «Veces» y «Última vez», donde «Última vez» es el día;
- la nota «Consultas que hizo la IA de un trabajador y que no encontraron nada en los documentos. Pueden indicar documentación que falta».

### 5.5 Revisión de fichas: la nota del revisor (D-040, D-044, D-045)

La pantalla de revisión ya existe. Cambia el diálogo de rechazar y el de corregir: pide una nota y avisa, encima del campo, con este texto: «El autor verá esta nota tal cual. Escribe sobre el contenido de la ficha, no sobre la persona».

- Al rechazar, la nota sigue siendo obligatoria, como hoy.
- Al corregir, es opcional, y el aviso sale igual.

## 6. Contrato

| Método y ruta | Quién | Qué |
|---|---|---|
| `GET /v1/admin/companies/{companyId}/usage-stats?month=` | Administradores | Apartado 5.2 |
| `GET /v1/admin/companies/{companyId}/delivered-volume?month=` | Administradores | Apartado 5.3 |
| `GET /v1/admin/companies/{companyId}/gaps?month=` | Administradores | Apartado 5.4 |
| Rutas de temas y `tagging-status` | Administradores | Las del bloque 3, sin cambios |

- `month` con formato `YYYY-MM`. Si no llega, el mes en curso.
- Fuera de los 12 últimos meses, o con un formato que no es ese, da 400.
- Las fechas se agrupan con `ODIN_TIMEZONE` (bloque 3).

## 7. Pruebas y verificación

Aquí se dice qué se prueba. Los nombres exactos de las pruebas, y si cada una es de caso de uso o de PostgreSQL, los fija el plan.

- **Casos de uso:**
  - `UsageStatsTest`. Cubre:
    - cada recuento;
    - el orden por nombre, también cuando las cantidades dirían otro;
    - que no hay recuentos por persona de fichas rechazadas, aprobadas ni corregidas;
    - el código de cada persona (`personCode-companyCode`), igual que el de `MemberView.of`;
    - la persona dada de baja, sin código;
    - el mes;
    - el actor de otra empresa.
  - `ListGapsTest`. Cubre:
    - solo los que tienen 0 resultados;
    - la agrupación sin tildes ni mayúsculas;
    - que no hay autor en la respuesta;
    - el límite;
    - que la última vez es un día, sin hora;
    - el actor de otra empresa.
  - `DeliveredVolumeTest`: los bytes y las llamadas por herramienta, y el actor de otra empresa.
- **PostgreSQL:** las consultas con la seguridad por filas, y que el administrador de una empresa no cuenta nada de otra.
- **Web:**
  - el filtro de bytes, que solo cuenta las rutas del trabajador con respuesta 200;
  - una prueba por ruta, e `IsolationTest`.
- **Panel:** una prueba por acción, con la petición enviada y lo que se ve:
  - `Topics.test.tsx`: alta, edición, borrado, errores y el estado del etiquetado, con su recordatorio de más de un día;
  - `DocumentTopics.test.tsx`: añadir, quitar, recuperar y volver a etiquetar;
  - `Usage.test.tsx`: la tabla por persona, sin ordenar por columnas, la nota sin referencias internas y el volumen entregado con su texto fijo, y ningún rótulo con «consumo» ni «gasto»;
  - `Gaps.test.tsx`: el día sin hora;
  - `Review.test.tsx`: el aviso de la nota al rechazar y al corregir;
  - y las reglas de accesibilidad de la dirección visual (axe).
- **De principio a fin** (`make demo`, con Playwright contra la API real, regla de la fase 1.1):
  1. crear y editar un tema;
  2. quitar un tema de un documento;
  3. ver el uso del mes con las preguntas de la demo;
  4. ver un hueco con la pregunta de la demo que da «no consta».
- **Rendimiento:** con datos ficticios a escala (por ejemplo, 10.000 preguntas y 2.000 documentos en una empresa), las tres rutas se miden en la verificación del CTO. La cifra va al informe; no se promete ninguna antes.
- **Regresión:** la batería de búsqueda mantiene 27 de 30 y `SearchScaleBatteryIT` mantiene su umbral. Este bloque no cambia lo que ve la IA, así que no hace falta medir la batería de respuestas.

## 8. Criterio de cierre

- `make check` y `make demo` terminan con 0.
- Revisión de las pantallas de uso y de huecos contra D-005 y D-036, hecha por el revisor final, con esta lista:
  - nada ordena por persona;
  - nada destaca a nadie;
  - no hay listas de preguntas por persona;
  - los huecos van sin autor ni hora;
  - ningún texto cita decisiones internas.
- Decisiones registradas en el registro técnico (su entrada, con el siguiente número libre al escribirla).

## 9. Lo que queda al constructor

- Nombres internos de componentes y de consultas.
- Textos de botones y estados vacíos, dentro de la dirección visual.
- Cómo se cuenta la normalización de los huecos en SQL (`unaccent` y `lower`, ya disponibles).

## 10. Validación del CEO (D-040, D-043, D-044 y D-045, 2026-11-11)

El CEO validó el diseño con estas precisiones, ya aplicadas:
- el consumo pasa a ser el volumen entregado a la IA del trabajador, sin registro de IA externa y con su texto fijo (2 y 5.3);
- el diálogo de revisión de fichas avisa de que el autor verá la nota (5.5);
- la pantalla de temas dice cuántos documentos esperan y desde cuándo, con el recordatorio del equipo encendido (5.1);
- los huecos dan el día, no la hora (5.4);
- ningún texto del panel cita decisiones internas (5 y 5.2).

## 11. Revisión del constructor (2026-11-11)

Un subagente leyó el diseño como si tuviera que planificarlo. Hizo 6 preguntas, todas técnicas, y el CTO las decidió. Ya están en el texto:
- quién sale en la tabla por persona, sin fechas de pertenencia (5.2);
- las personas borradas, sumadas en una sola fila (5.2);
- `kind` y `hit_count`, que vienen del bloque 2 (2);
- `author_id`, que es obligatorio (5.2);
- qué rutas cuenta el filtro de bytes y cómo (5.3);
- el periodo frente al plazo legal de conservación (2).

## 12. Revisión tras la 2a y D-049 (2026-11-11)

- **Orden (D-049):** este bloque es el último de la fase 2. La migración pasa de V13 a V15 (orden de D-052: V11 es del bloque 7, V12 del 1, V13 del 3 y V14 del 2).
- **D-048:** cada fila de la tabla por persona lleva `userId`, `displayName` y `memberCode` (`personCode-companyCode`, como `MemberView.of`). La tabla sigue ordenada por `displayName`. La fila «Personas dadas de baja» no lleva código. El código sale solo en el panel: nunca en el MCP, ni en las citas, ni en las fichas.
- **D-047:** no cambia lo que ve el administrador. El volumen entregado y los huecos se miden por empresa, sin mirar proyectos.
- **Bloque 6:** `listProjects` lee `GET /v1/me`, así que cuenta como «Perfil» en el volumen entregado y no añade ninguna fila. La regresión incluye `SearchScaleBatteryIT`.
- **Bloque 7:** `GET /v1/documents` y las rutas de documentos ganan `superseded` y `replacement` o `supersededBy`; no cambian los recuentos de este bloque.
- **Registro técnico:** sin número fijo; el siguiente libre al escribirlo.
