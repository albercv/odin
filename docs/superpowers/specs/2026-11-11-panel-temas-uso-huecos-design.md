# Odín · Fase 2, bloque 4: panel con temas, uso, consumo y huecos

- Fecha: 2026-11-11. Autor: CTO (segunda sesión). Encargo 11, apartado «Panel del administrador».
- Decisiones de negocio que lo originan: D-005, D-030, D-031, D-036 y D-039.
- Amplía `2026-11-09-api-central-design.md` («spec base») y se apoya en `2026-11-10-identidad-pertenencias-design.md` («spec 2a»), `2026-11-10-direccion-visual-design.md` («dirección visual») y `2026-11-11-etiquetado-temas-design.md` («bloque 3»).
- Rama: `feature/fase-2-conocimiento`. Se ejecuta cuando cierre la fase 2a y después de los bloques 2 y 3.

## 1. Por qué

El encargo 11 pide un panel desde el que el administrador controle, además de lo que ya hay:
- los **temas** de cada empresa y los que pone la IA (D-031);
- el **consumo de la IA externa**, que hace falta para decidir quién lo paga (D-030);
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

**Pregunta al CEO** (enviada el 2026-11-11), de la que depende el apartado 5.3:
- Odín no llama hoy a ninguna IA externa. La IA del trabajador es suya y Odín no ve lo que gasta.
- **Recomendación del CTO, que sigue este diseño:**
  1. un registro de consumo preparado para cuando el cerebro use una IA externa, que hoy saldrá vacío y lo dirá;
  2. mientras tanto, el volumen que Odín entrega a la IA del trabajador, medido en bytes por empresa, mes y herramienta. No en tokens: los tokens solo los sabe el proveedor.
- **Alternativa:** dejar el consumo fuera hasta que exista el motor propio. Se quitan las tareas del apartado 5.3, que son independientes.

**Del CTO.** Cada decisión dice lo que cuesta si resulta errónea:

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| Por persona solo hay dos recuentos: preguntas hechas y fichas aportadas, en el periodo | Son actividad, no calidad. Aprobadas, corregidas y rechazadas se cuentan solo por empresa | Un recuento más |
| La tabla por persona está siempre ordenada por nombre. No se puede ordenar por cantidad ni hay «más activos», gráficos por persona, medias ni colores que destaquen a alguien | Ordenar por cantidad ya es un ranking (D-005) | — |
| No hay estadísticas por equipo | No existen equipos (spec 2a, 3) | Una dimensión más, cuando existan |
| Huecos: consultas con 0 resultados de búsqueda o de casos parecidos, agrupadas por texto igual (sin tildes, sin mayúsculas y con los espacios normalizados), con cuántas veces y la última fecha | D-036. Agrupar por significado sería otra IA interpretando | Agrupar de otra forma |
| El texto del hueco es la consulta que mandó la IA del trabajador, y el panel lo dice | Es lo que se guarda (spec base 6.6), no lo que escribió la persona | — |
| Todo se calcula al pedirlo, con consultas sobre los datos que ya existen. Sin tablas de resumen | Con el volumen de una oficina basta. Se mide al verificar | Una vista materializada |
| Periodo: por meses, con el mes en curso por defecto, hasta 12 meses atrás | Simple y suficiente | Fechas libres |

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
| Uso | Todas las empresas, una a una | La suya |
| Consumo | Todas las empresas, una a una | La suya |
| Huecos | Todas las empresas, una a una | La suya |

Las rutas siguen el orden de siempre: 404 si la empresa no existe; 403 si no la administra.

## 5. Pantallas

Cada pantalla está dentro de la empresa elegida, con el selector de empresa de la 2a, y se usan los componentes de la dirección visual: `PageHeader`, `Section`, `Table`, `EmptyState`, `StatusPill`, `FormDialog`, `ConfirmDialog` y `Toast`. El menú lateral gana «Temas», «Uso» y «Huecos». «Consumo» va dentro de «Uso».

### 5.1 Temas

Usa la API del bloque 3.

- **Lista de temas:**
  - nombre y definición;
  - añadir y editar en un `FormDialog`;
  - borrar con `ConfirmDialog`;
  - errores de la API en el campo, con los mensajes del bloque 3.
  - Si la lista está vacía, un `EmptyState` explica que sin temas no se etiqueta.
- **Aviso al cambiar la lista:** «Los documentos de esta empresa se volverán a etiquetar», y cuántos están pendientes.
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
- **Por persona:** `people[]`, con `userId`, `displayName`, `questions` y `cardsSaved`, **ordenado por nombre**.
  - Solo personas con pertenencia a esa empresa, o que la tuvieron en ese mes.
  - Una persona borrada sale como «Persona dada de baja», sin nombre.
- **En la pantalla:**
  - tabla de la empresa y tabla por persona;
  - sin ordenar por columnas, sin destacar a nadie y sin totales por persona fuera de la tabla;
  - una nota fija: «Recuentos descriptivos. No sirven para valorar a nadie (D-005)».

### 5.3 Consumo (pendiente del CEO)

`GET /v1/admin/companies/{companyId}/ai-usage?month=YYYY-MM`. Devuelve dos bloques:
- **IA externa del cerebro:**
  - llamadas, tokens de entrada y de salida, y modelo, por finalidad;
  - hoy siempre vacío, con el texto «Odín no ha usado ninguna IA externa este mes».
  - **Puerto `AiUsageLedger`** (`application/port`) y tabla `ai_usage` (empresa, finalidad, proveedor, modelo, tokens de entrada y de salida, fecha). Lo escribirá cualquier adaptador futuro que llame a una IA externa, con los tokens que diga el proveedor. Nada lo escribe todavía, y por eso es una tarea pequeña y separada.
- **Entregado a la IA del trabajador:**
  - por herramienta (búsqueda, casos parecidos, leer página, listar documentos, verificar cita): llamadas y bytes de las respuestas;
  - un filtro del adaptador web cuenta los bytes de cada respuesta 200 de esas rutas y llama al puerto `DeliveryMeter`;
  - se guarda en `delivery_usage (company_id, month, tool, calls, bytes)` con un `UPSERT` por petición.
  - Por empresa, nunca por persona.
  - La pantalla explica que son bytes y no tokens.

### 5.4 Huecos

`GET /v1/admin/companies/{companyId}/gaps?month=YYYY-MM`. Devuelve hasta 200 grupos: `text` (la primera forma del texto), `kind`, `times` y `lastAskedAt`, de más reciente a más antiguo. Sin autor ni ningún dato de la persona.

En la pantalla:
- tabla con «Consulta», «Tipo», «Veces» y «Última vez»;
- la nota «Consultas que hizo la IA de un trabajador y que no encontraron nada en los documentos. Pueden indicar documentación que falta».

## 6. Contrato

| Método y ruta | Quién | Qué |
|---|---|---|
| `GET /v1/admin/companies/{companyId}/usage-stats?month=` | Administradores | Apartado 5.2 |
| `GET /v1/admin/companies/{companyId}/ai-usage?month=` | Administradores | Apartado 5.3 |
| `GET /v1/admin/companies/{companyId}/gaps?month=` | Administradores | Apartado 5.4 |
| Rutas de temas | Administradores | Las del bloque 3, sin cambios |

- `month` con formato `YYYY-MM`. Si no llega, el mes en curso.
- Fuera de los 12 últimos meses, o con un formato que no es ese, da 400.
- Las fechas se agrupan con `ODIN_TIMEZONE` (bloque 3).

## 7. Pruebas y verificación

- **Casos de uso:**
  - `UsageStatsTest`. Cubre:
    - cada recuento;
    - el orden por nombre, también cuando las cantidades dirían otro;
    - que no hay recuentos por persona de fichas rechazadas, aprobadas ni corregidas;
    - la persona dada de baja;
    - el mes;
    - el actor de otra empresa.
  - `ListGapsTest`. Cubre:
    - solo los que tienen 0 resultados;
    - la agrupación sin tildes ni mayúsculas;
    - que no hay autor en la respuesta;
    - el límite;
    - el actor de otra empresa.
  - `AiUsageTest`: el registro vacío, los bytes por herramienta y el actor de otra empresa.
- **PostgreSQL:** las consultas con la seguridad por filas, y que el administrador de una empresa no cuenta nada de otra.
- **Web:**
  - el filtro de bytes, que solo cuenta las rutas del trabajador con respuesta 200;
  - una prueba por ruta, e `IsolationTest`.
- **Panel:** una prueba por acción, con la petición enviada y lo que se ve:
  - `Topics.test.tsx`: alta, edición, borrado y errores;
  - `DocumentTopics.test.tsx`: añadir, quitar, recuperar y volver a etiquetar;
  - `Usage.test.tsx`: la tabla por persona, sin ordenar por columnas, y la nota de D-005;
  - `Gaps.test.tsx`;
  - y las reglas de accesibilidad de la dirección visual (axe).
- **De principio a fin** (`make demo`, con Playwright contra la API real, regla de la fase 1.1):
  1. crear y editar un tema;
  2. quitar un tema de un documento;
  3. ver el uso del mes con las preguntas de la demo;
  4. ver un hueco con la pregunta de la demo que da «no consta».
- **Rendimiento:** con datos ficticios a escala (por ejemplo, 10.000 preguntas y 2.000 documentos en una empresa), las tres rutas se miden en la verificación del CTO. La cifra va al informe; no se promete ninguna antes.
- **Regresión:** la batería de búsqueda mantiene 27 de 30.

## 8. Criterio de cierre

- `make check` y `make demo` terminan con 0.
- Revisión de las pantallas de uso y de huecos contra D-005 y D-036, hecha por el revisor final, con esta lista:
  - nada ordena por persona;
  - nada destaca a nadie;
  - no hay listas de preguntas por persona;
  - los huecos van sin autor.
- La respuesta del CEO sobre el consumo, aplicada.
- Decisiones registradas en el registro técnico (T-018).

## 9. Lo que queda al constructor

- Nombres internos de componentes y de consultas.
- Textos de botones y estados vacíos, dentro de la dirección visual.
- Cómo se cuenta la normalización de los huecos en SQL (`unaccent` y `lower`, ya disponibles).
