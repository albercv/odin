# Odín · Fase 2, bloque 2: casos parecidos

- Fecha: 2026-11-11. Autor: CTO (segunda sesión). Encargo 11.
- Decisiones de negocio que lo originan: D-021, D-026 y la definición del producto (apartados 3 y 4.3, paso 5).
- Amplía `2026-11-09-api-central-design.md` («spec base») y se apoya en `2026-11-10-identidad-pertenencias-design.md` («spec 2a»). Lo que este documento no cambia sigue como está allí.
- Rama: `feature/fase-2-conocimiento`. Se ejecuta cuando cierre la fase 2a.

## 1. Por qué

- **Caso de uso principal:** al abrir un caso nuevo, ver cómo se resolvieron los parecidos, con sus documentos y su resultado, y con la fuente citada (definición del producto, apartado 3).
- **La demo lo pide de verdad** (D-026, «funcionan de verdad»). El recorrido:
  1. el trabajador entrega a su IA la ficha de un caso nuevo y pide casos parecidos;
  2. el cerebro devuelve los casos resueltos con su resolución y su resultado;
  3. si dos se resolvieron de forma distinta, enseña los dos y solo sugiere el que acabó bien, citando ese resultado;
  4. nunca compara ni menciona a las personas que los resolvieron (D-005).
- **Hoy:** Odín no sabe qué documentos son casos. La búsqueda devuelve páginas sueltas de cualquier documento, y la IA tendría que adivinar cuáles son casos y abrir cada uno.

## 2. Decisiones

**De negocio que se aplican:** nunca inventa y cita (D-008, D-024); conocimiento, no evaluación (D-005); proyectos restringidos (D-038); fichas pendientes y rechazadas fuera de las búsquedas de los compañeros (D-036).

**Respuestas del CEO** (D-040 y D-042, 2026-11-11):

| Pregunta | Respuesta |
|---|---|
| ¿Quién decide qué documento es un caso? | Una persona, nunca la IA. El administrador marca «Es la ficha de un caso» al subirlo y lo puede cambiar después. Puede marcar un lote entero de una vez, al subirlo o en la lista de documentos |
| ¿Salen las fichas de conocimiento aprobadas? | Sí, junto a los casos, pero rotuladas como fichas. No cuentan como casos resueltos, y la IA no les atribuye un resultado que su texto no diga |
| ¿Va el autor en los resultados? | No: ni quién subió el documento ni quién aportó la ficha. La ficha sale como «ficha aportada por un trabajador, redactada con IA», porque la redacta la IA del trabajador; no es un resumen. Se sigue citando el documento, la página y la fecha. En la búsqueda normal, el autor sigue saliendo |
| ¿Cuándo se sugiere una resolución? | Solo cuando el resultado está escrito en los documentos. Si ninguno de los casos lo tiene escrito, se enseñan las resoluciones sin sugerir ninguna |
| ¿Qué términos manda la IA? | Los del problema (qué falla, dónde, la causa), nunca nombres de personas. Así entra menos en el registro de preguntas (RGPD, D-036), y la semejanza entre casos está en el problema |

**Del CTO.** Cada decisión dice lo que cuesta si resulta errónea:

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| Sin vectores. Palabras en español sobre las páginas de los casos, con los términos que manda la IA del trabajador, como en la búsqueda | Es la forma medida en el estudio de búsqueda (T-007). La batería de este bloque dirá si basta | Añadir la variante híbrida ya medida en el estudio (apartado 4.8), con pgvector |
| Un resultado por caso, no por página. El orden es el de su mejor página | Se piden casos, y un caso de dos páginas no debe ocupar dos puestos | Un `GROUP BY` |
| Cada caso se devuelve con sus páginas literales, hasta 3 páginas y 12.000 caracteres | La resolución y el resultado están en el texto del caso. Leerlo en la misma respuesta evita una lectura por caso. Las fichas de caso del mundo de la demo tienen 2 páginas | Bajar el límite o devolver solo el extracto |
| El cerebro no busca ni recorta las secciones «Resolución» y «Resultado» | Cada cliente redacta sus casos a su manera. Recortar por títulos sería adivinar y podría dejar fuera el resultado. Lee la IA del trabajador, con instrucciones | Un recorte por plantilla de cliente |
| Capacidad nueva `SIMILAR_CASES`, activada por defecto, también en las empresas que ya existen | Prevista en el spec base 6.3. Solo usa lo que el trabajador ya puede leer | Desactivarla por defecto |
| Las búsquedas de casos parecidos se registran como preguntas, con su tipo | Así cuentan en las estadísticas y en los huecos «no consta» del bloque 4, sin otra tabla | Una tabla aparte |

## 3. Fuera de alcance

- El borrador de resolución a partir de la plantilla y los conflictos entre documentos: en la demo se enseñan preparados (D-026).
- Juzgar qué resolución «funcionó»: lo dice el resultado escrito en el caso, y lo lee la IA del trabajador citándolo.
- Detectar por IA qué documentos son casos.
- Casos parecidos entre empresas del grupo: siguen separadas (D-033).
- Una pantalla de casos parecidos en el panel. Es una herramienta del trabajador, por MCP.

## 4. Modelo

| Concepto | Nombre en código | Qué cambia |
|---|---|---|
| Documento | `Document` | Gana `caseDocument` (falso por defecto): es la ficha de un caso. Lo pone el administrador, nunca la IA |
| Capacidad | `Capability.SIMILAR_CASES` | Nueva |
| Pregunta registrada | `QuestionRecord` | Gana `kind`: `SEARCH` o `SIMILAR_CASES` |
| Caso parecido | `SimilarCase` (valor) | Tipo de fuente, identificador, título, proyecto, `addedAt`, número de páginas, páginas devueltas (número y texto literal), si se recortó y el extracto que coincidió. Sin autor |

- **`addedAt`** es cuándo entró en Odín: `uploadedAt` del documento o `createdAt` de la ficha. No es la fecha del caso. Esa está en su texto, y el cerebro no la extrae porque sería adivinarla (D-008). La instrucción del MCP lo dice.
- **Abiertos o cerrados:** entra todo documento marcado como caso, esté como esté. El cerebro no sabe si un caso está resuelto. Lo dice el texto, y la instrucción pide a la IA que avise cuando un caso no tiene resolución o resultado.

- **Migración:**
  - `document.case_document boolean NOT NULL DEFAULT false`;
  - `question_record.kind text NOT NULL DEFAULT 'SEARCH'`;
  - una fila de `SIMILAR_CASES` en `company_capability` para cada empresa que ya existe.
- **Una empresa nueva** nace con todas las capacidades, `SIMILAR_CASES` incluida.

## 5. Reglas

### 5.1 Quién marca un caso

- **Al subir:** el campo `caseDocument` del multipart, falso si no llega.
- **Después:** `PATCH /v1/admin/documents/{documentId}` con `{ caseDocument }`, solo para administradores de esa empresa o el del grupo. El orden de las comprobaciones es el de siempre: 404 si no existe, 403 si es de otra empresa.
- **Por lotes (D-040, D-042):**
  - **Al subir:** el panel deja elegir varios ficheros a la vez, con una sola casilla «Son fichas de caso» para todos. Envía una subida por fichero, con el mismo `caseDocument`. Con varios ficheros, el título de cada uno es su nombre sin la extensión.
  - **En la lista:** el administrador selecciona varios documentos y elige «Marcar como caso» o «Quitar marca de caso».
    - Ruta: `POST /v1/admin/companies/{companyId}/documents/case-marks` con `{ documentIds, caseDocument }`, hasta 500 documentos.
    - Todo o nada, en una transacción. Si un identificador no existe o es de otra empresa, 404 y no cambia ninguno.
- **Documento duplicado** (misma SHA-256): se ignora el valor nuevo de `caseDocument`, igual que el título y el proyecto (spec base 6.1). Para cambiarlo está el `PATCH`.

### 5.2 Buscar casos parecidos

- **Caso de uso:** `FindSimilarCases(actor, SimilarCasesRequest(queries, projectId?, limit?))`.
  - **Acceso:** `WorkerAccess` con `SIMILAR_CASES`. Los proyectos visibles se calculan como en la búsqueda (spec 2a, 5.4). Un proyecto restringido ajeno da 404.
  - **Términos:** igual que `SearchKnowledge`. Hace falta al menos uno con texto, o da 400. La IA los saca de la ficha del caso nuevo: qué falla, dónde, la causa y sus reformulaciones.
  - **Límite:** 5 por defecto y 10 como máximo.
- **Qué entra:**
  - las páginas de documentos `READY` con `caseDocument`;
  - las fichas `APPROVED`, que salen junto a los casos rotuladas como fichas y no cuentan como casos resueltos;
  - siempre de la empresa del actor y de sus proyectos visibles, con el filtro SQL explícito y la seguridad por filas (spec 2a, 6). Lo que no tiene proyecto lo ve toda la empresa, como en la búsqueda (D-038).
- **Orden:**
  - se reutiliza la consulta de `PostgresKnowledgeSearch`: `odin_spanish`, `plainto_tsquery` con `|`, el canal exacto de identificadores y `ts_rank_cd`;
  - se agrupa por documento o ficha y vale su mejor página;
  - documentos y fichas van en la misma lista, sin cupos, como ya hace la búsqueda. Si la batería muestra que unos tapan a los otros, se revisa;
  - a igual puntuación, se ordena por título y después por identificador.
  - Lo común de esa consulta se saca a un sitio compartido; no se copia.
- **Qué devuelve cada caso:**
  - **Documento:** sus páginas en orden, desde la primera, mientras no pasen de 3 páginas ni de 12.000 caracteres.
    - Si la página con mejor puntuación se queda fuera, se añade al final, aunque pase de los límites. Por ejemplo, las páginas 1, 2 y 5.
    - Si falta alguna página, `truncated: true` y `pageCount`, y la IA sigue con `readPage`.
  - **Ficha:** su texto completo y la marca «ficha aportada por un trabajador, redactada con IA».
  - **Extracto:** el que coincidió, literal, igual que en la búsqueda.
- **Sin resultados:** `{ cases: [], noSource: true }`.
- **Registro:** un `QuestionRecord` de tipo `SIMILAR_CASES`, con los términos y el número de casos. Hoy nada lee el registro. Las estadísticas y los huecos del bloque 4 cuentan los dos tipos y los enseñan por separado.
- **Autoría:** la respuesta no lleva ningún autor (D-040). El texto literal del documento no se toca: si menciona a una persona, la instrucción del MCP le pide a la IA que no compare ni mencione a quien resolvió cada caso.

### 5.3 Listado de documentos

- `ListDocuments` del trabajador gana el filtro opcional `caseDocument`, y cada documento lleva el campo, para que la IA pueda listar todos los casos (estudio de búsqueda, 5.1, punto 10).
- El filtro depende de `SEARCH_KNOWLEDGE`, como el listado, y no de `SIMILAR_CASES`.

## 6. Contrato

| Método y ruta | Quién | Qué |
|---|---|---|
| `POST /v1/similar-cases` | Trabajador | `{ queries: string[], projectId?, limit? }` → `{ cases: SimilarCaseView[], noSource }` |
| `GET /v1/documents?caseDocument=` | Trabajador | Filtro nuevo; `WorkerDocumentView` gana `caseDocument` |
| `POST /v1/admin/companies/{companyId}/documents` | Administradores | Campo multipart nuevo `caseDocument` |
| `PATCH /v1/admin/documents/{documentId}` | Administradores | `{ caseDocument }` → `DocumentView`, que gana `caseDocument` |
| `POST /v1/admin/companies/{companyId}/documents/case-marks` | Administradores | `{ documentIds, caseDocument }` → `{ updated }` |

`SimilarCaseView` lleva `originLabel`, que es nulo en un documento y es «Ficha aportada por un trabajador, redactada con IA» en una ficha. Además lleva: `sourceType`, `sourceId`, `title`, `projectId`, `addedAt`, `pageCount`, `pages` (`[{ number, text }]`, vacío en una ficha), `body` (solo en una ficha), `truncated`, `excerpt` y `origin`. `origin` es `ORIGINAL_DOCUMENT` o `KNOWLEDGE_CARD`, con el texto de la marca.

## 7. MCP

- **Herramienta `findSimilarCases`,** solo si la empresa tiene `SIMILAR_CASES`. Entrada con zod: `queries`, `projectId` y `limit`.
  - Su descripción pide términos del problema (qué falla, dónde, la causa) y prohíbe nombres de personas (D-042).
  - Si no hay casos: «No constan casos parecidos en los documentos de tu empresa».
- **`listDocuments`** gana el filtro `caseDocument`.
- **La instrucción que ya existe sobre las fichas** en la búsqueda cambia «resumen hecho por IA» por «redactada con IA» (D-040). En la búsqueda normal se sigue citando al autor.
- **Instrucciones nuevas:**
  - de cada caso parecido, presenta su resolución y su resultado con la cita del documento y la página;
  - si dos casos se resolvieron de forma distinta, enséñalos los dos con sus citas y su resultado, y sugiere solo el que acabó bien, citando ese resultado;
  - **sugiere una resolución solo si su resultado está escrito en el documento; si ninguno de los casos tiene el resultado escrito, enseña las resoluciones sin sugerir ninguna** (D-042);
  - nunca compares ni menciones a las personas que resolvieron cada caso;
  - una ficha aportada es una ficha redactada con IA por un trabajador: preséntala como ficha, no como caso resuelto, y no le atribuyas un resultado que su texto no diga;
  - `addedAt` es cuándo entró el documento en Odín, no la fecha del caso; esa fecha está en el texto;
  - si un caso no tiene resolución o resultado escritos, dilo; si es el mismo caso nuevo que te han dado, descártalo.

## 8. Panel

- **Subir documento:** varios ficheros a la vez, con la casilla «Son fichas de caso» para todos (5.1).
- **Lista de documentos:**
  - la marca «Caso»;
  - una acción por documento para marcarlo o desmarcarlo;
  - selección de varios documentos con «Marcar como caso» y «Quitar marca de caso» (5.1).
- **Capacidades de la empresa:** «Casos parecidos».
- Con los componentes y las reglas de la dirección visual (D-039).

## 9. Batería de casos parecidos

La forma de medir es la misma que la de la búsqueda (T-007, T-009). No se anuncia ninguna cifra que no salga de esta batería.

- **Corpus:**
  - todas las fichas de caso del mundo ficticio del estudio (`corpus-limpio`, `casos-limite` y `casos-control`), cargadas como `caseDocument`;
  - el resto de documentos del corpus limpio, cargados sin la marca, para comprobar que no salen.
  - Todo ficticio; una empresa por comunidad, como en la batería de búsqueda.
- **Casos nuevos:** 12 fichas de caso nuevas y ficticias, escritas por un subagente:
  - cada una se parece a un grupo de 1 a 3 casos del corpus y apunta cuáles son, en un fichero de claves aparte;
  - incluyen las preguntas G1 y G2 de la batería del estudio y la pareja de casos parecidos resueltos de forma distinta;
  - el CTO revisa las claves antes de medir.
- **Términos:** otro subagente (Haiku), que hace de IA del trabajador, lee solo cada ficha nueva y escribe sus términos, sin ver las claves. Se guardan como fichero fijo, como `reformulaciones.json`, para que la prueba no dependa de un modelo.
- **Medida:** porcentaje de casos esperados que salen entre los 5 primeros, y cuántos casos nuevos los tienen todos. La primera medición fija el umbral de regresión.
- **Prueba:** `SimilarCasesBatteryIT`, con Testcontainers, junto a `SearchBatteryIT`. Ficheros en `api/odin-adapter-postgres/src/test/resources/similar-cases-battery/`.
  - Las fichas del corpus se **copian** ahí desde la rama `feature/planificacion-tecnica` del repositorio OCR, como ya se hizo con la batería de búsqueda. La prueba no lee nada de otro repositorio.

## 10. Pruebas

Aquí se dice qué se prueba. Los nombres exactos de las pruebas, y si cada una es de caso de uso o de PostgreSQL, los fija el plan.

- **Casos de uso:** `FindSimilarCasesTest`. Cubre:
  - solo entran documentos con `caseDocument`;
  - las fichas aprobadas entran y las pendientes y rechazadas no;
  - un caso de dos páginas sale una vez;
  - el recorte por páginas y por caracteres, con la mejor página siempre dentro;
  - sin autor en la respuesta;
  - capacidad desactivada, 403;
  - proyecto restringido ajeno, invisible y 404 al filtrar;
  - actor de otra empresa (D-014);
  - el registro de tipo `SIMILAR_CASES`.
- **Marcar un caso:** `UpdateDocumentTest` y `MarkCaseDocumentsTest` (todo o nada, el máximo de 500 y el identificador de otra empresa), cada uno con su caso de otra empresa.
- **PostgreSQL:** `PostgresKnowledgeSearchIT.findsSimilarCasesGroupedByDocument` y `filtersByVisibleProjects`. La migración, con una base que ya tiene empresas.
- **Web y contrato:** una prueba por ruta, y `IsolationTest` para el `PATCH` y la búsqueda de casos.
- **De principio a fin:** `make demo` gana un paso.
  1. Se suben dos fichas de caso ficticias, marcadas como caso, y un documento que no es caso.
  2. Con el MCP, `findSimilarCases` devuelve las dos con sus páginas y no el otro documento.
  3. El panel marca y desmarca un documento.
- **Regresión:** la batería de búsqueda mantiene 27 de 30, y la de casos parecidos, el umbral que fije su primera medición.

## 11. Criterio de cierre

- `make check` y `make demo` terminan con 0.
- Las dos baterías, medidas, con sus cifras en el informe del bloque.
- Decisiones registradas en el registro técnico (T-016), y respuestas del CEO aplicadas.

## 12. Lo que queda al constructor

- Cómo se agrupa en SQL y cómo se cargan las páginas de los casos elegidos, siempre con el filtro de empresa y de proyectos en cada consulta.
- Los textos exactos de la casilla y de la marca en el panel, dentro de la dirección visual.
- El formato interno de los ficheros de la batería.

## 13. Validación del CEO (D-040 y D-042, 2026-11-11)

El CEO validó el diseño con estas precisiones, ya aplicadas:
- el rótulo «redactada con IA» (2, 5.2 y 7);
- las fichas, que no son casos resueltos (2, 5.2 y 7);
- sugerir solo con el resultado escrito (2 y 7);
- marcar por lotes (5.1, 6 y 8);
- términos sin nombres de personas (2 y 7).

## 14. Revisión del constructor (2026-11-11)

Un subagente leyó el diseño como si tuviera que planificarlo. Hizo 8 preguntas, todas técnicas, y el CTO las decidió. Ya están en el texto:
- casos abiertos y el propio caso nuevo: entran, y la IA avisa o los descarta (4 y 7);
- `date` pasa a `addedAt`, que es cuándo entró en Odín (4);
- lo que no tiene proyecto lo ve toda la empresa (5.2);
- documentos y fichas en una sola lista, sin cupos (5.2);
- recorte con la mejor página añadida al final (5.2);
- tipos de pregunta en las estadísticas del bloque 4 (5.2);
- fichas de la batería copiadas al repositorio de Odín (9);
- el filtro del listado no depende de `SIMILAR_CASES` (5.3).
