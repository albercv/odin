# Diseño técnico: mejoras del expediente (D-093)

- **Fecha:** 2026-11-13. **Autor:** CTO 2.
- **Origen:** la medición de la tarea 9 (`docs/informes/2026-11-13-expediente-medicion.md`). La peor de 3 en las 40 preguntas de expediente fue 30 (75 %), con una cita no literal. La IA usó `getCaseFile` en 28 de 120 preguntas y `verifyCitation` una vez por cita (hasta 23 llamadas en una pregunta).
- **Rama:** `feature/expediente-mejoras`, que sale de `feature/casos-expediente` (puesta al día en 1cfd36f, con el ejecutor robusto). Cada tarea va en su rama `task/expediente-mejoras/<tarea>`.
- **Objetivo (D-093):**
  - en las 40 de expediente, la peor de 3 por encima del 90 %, sin citas inventadas ni fugas; un título citado como si fuera texto de la página cuenta como cita inventada (D-050);
  - en las 216, sin bajar de la base nueva con todas las capacidades.

## 1. Fichas de caso marcadas y guía a `getCaseFile`

- **API:** cada resultado de documento en `POST /v1/search` lleva `caseDocument: boolean`. El dato ya existe en `DocumentEntry` y en la lista de documentos; solo falta en la respuesta de búsqueda. Se regeneran OpenAPI y los dos `types.ts`.
- **MCP, `searchKnowledge`:** la descripción dice que, si la pregunta trata de un caso o de un expediente y un resultado tiene `caseDocument: true`, la IA debe llamar a `getCaseFile` con su `documentId` antes de responder.
- **MCP, `getCaseFile`:** la descripción dice que se use siempre que la pregunta nombre un caso o un número de expediente, buscando antes la ficha con `searchKnowledge`.
- **Pruebas:**
  - el campo sale en la respuesta, tanto para una ficha como para un documento normal;
  - las descripciones contienen las frases que fijan la regla;
  - la primera barrera y la seguridad por filas no cambian, porque es un campo más del mismo documento visible.

## 2. `verifyCitation` por lotes

- **API:** `POST /v1/citations/verify-batch` acepta `{"citations": [{documentId, pageNumber, quote}, …]}`, con entre 1 y 20 citas, y devuelve `{"results": [{documentId, pageNumber, verified}, …]}` en el mismo orden. `POST /v1/citations/verify`, la de una sola cita, sigue igual, por compatibilidad. Va en un endpoint aparte porque dos formas de entrada y de respuesta en la misma ruta dejan OpenAPI y los tipos generados ambiguos.
  - Cada cita se comprueba con la visibilidad de la persona, como hoy. Una cita de un documento que no ve sale como `verified: false`, sin distinguir si el documento existe.
  - El volumen entregado cuenta como hoy.
- **MCP, `verifyCitation`:** acepta `citations` (una lista) y su descripción dice que se comprueben todas las citas de la respuesta en una sola llamada.
  - Se mantiene la regla de no usar una cita que dé `false`.
  - Se añade que el título del documento no es texto de la página: solo vale como cita lo que aparece en el texto.
  - Esa frase va dirigida a la cita no literal de la medición.
- **Pruebas:** un lote mezclado (exacta, inventada y de un documento invisible) devuelve tres resultados en orden; más de 20 citas dan 400; la forma de una sola cita sigue funcionando.

### 2.1 Un título citado como texto de la página (E016)

- **Qué pasó:** en la ejecución 2 de la fase B, E016 citó «Poliza seguro 2023» del documento 21845e1e, página 1.
  - Ese texto es el título del documento, que la IA vio en los resultados de `searchKnowledge` o de `listDocuments`.
  - No leyó ese documento con `readPage` ni comprobó la cita con `verifyCitation`. Las otras 8 citas sí las comprobó, y eran exactas.
  - Así que el servidor no tuvo ocasión de decir que no: `verifyCitation` habría devuelto `false`, porque el título no está en el texto de la página.
- **Qué se hace:**
  - el lote de `verifyCitation` (apartado 2) abarata comprobar todas las citas de la respuesta en una llamada;
  - la descripción de `verifyCitation` dice que se comprueben todas las citas, también las de documentos que no se han leído con `readPage`, y que una cita sin comprobar no se da;
  - la descripción de `verifyCitation` dice que el título del documento no es texto de la página;
  - la descripción de `searchKnowledge` dice que `title` es el nombre del documento y no se cita: se cita el extracto o el texto de la página.
- **Lo que no se hace:** el servidor no ve la respuesta final de la IA, así que no puede impedir una cita que no se comprueba. No se añade un rechazo específico para los títulos: `verifyCitation` ya devuelve `false`.

## 3. Pertenencia al expediente

- **MCP, `getCaseFile` y `readCaseFile`:** la descripción dice que un documento que cita el mismo número de caso pero no aparece en `getCaseFile` no forma parte del expediente. La IA no debe incluirlo, aunque puede mencionarlo aparte, diciendo que no está en el expediente.
- **Pruebas:** la frase está en las dos descripciones. `INSTRUCTIONS` no cambia y se queda en 2.200 caracteres o menos.

## 3.1 Números de caso en la búsqueda (aprobado por el CEO, 2026-11-13, sin IA)

- **Problema, visto en la comprobación previa contra la pila real:**
  - Ninguna forma del número de las preguntas («expte. 2024/530», «exp. 530/24», «su referencia 530», «expediente nº 530») trae la ficha «Caso 2024-530» entre los 20 primeros.
  - Causa 1: el analizador de PostgreSQL guarda «2024-530» como '2024' y '-530', con el guion como signo, así que «530» no coincide.
  - Causa 2: la búsqueda une los términos con OR, y los documentos con muchas apariciones de «expediente» y «nº» quedan por delante de la ficha.
- **Qué se hace (en la consulta; sin migración ni reindexado, así que no hace falta vaciar):**
  - Un número de 3 cifras o más que no sea un año (1900 a 2099) añade el término '-N'.
  - Un número compuesto de dos grupos de cifras separados por «/» o «-» («2024/530», «530/24») añade sus grupos de 3 cifras o más con las mismas reglas.
  - Esos números (no los años) añaden al canal literal (ILIKE, +100) sus formas compuestas: «-N», «/N» y «N/». Así quedan delante la ficha y los documentos que citan el número como referencia.
  - No reciben esa prioridad un importe con punto o coma («1.940»), un número suelto en el texto («530 euros») ni una fecha completa.
- **Pruebas:**
  - Batería determinista nueva `CaseNumberBatteryIT` sobre el archivo ficticio con los 12 expedientes cargados. Cada caso se busca con 6 formas: «AAAA-N», «AAAA/N» y sus 4 `formas_numero`. Son 72 búsquedas, sin filtrar por proyecto y con 10 resultados.
  - En cada una, la ficha del caso y al menos un documento de su expediente están entre los 10 primeros. El objetivo es 72 de 72.
  - Las baterías de siempre no bajan: `SearchBatteryIT` (recall@5 ≥ 90,00) y `ArchiveSearchBatteryIT` (recall@10 ≥ 73,28).
  - Pruebas unitarias de importes, fechas y años: «1.940», «12/11/2024» y «2025» no reciben la prioridad nueva (`pureNumbersAreNotBoosted` sigue en verde).

## 4. Medición

- **Plan de prueba con el formato de D-094:** `docs/superpowers/plans/2026-11-13-expediente-mejoras-prueba.md`. No se lanza sin que el propietario lo apruebe. Lo de abajo es el diseño original.


- **Haiku, todas las capacidades,** tres ejecuciones de cada:
  - las 40 de expediente, con la pila con expedientes;
  - las 216 y las 2 del propietario, con la pila sin expedientes.
- **Se compara con:**
  - la fase B de la tarea 9 (peor de 3: 30 de 40);
  - la base nueva con todas las capacidades.
- **El informe dice:**
  - cuántas preguntas usan `getCaseFile` y `readCaseFile`;
  - cuántas llamadas a `verifyCitation` se hacen por pregunta;
  - el consumo por tipo (mediana y máximo de tokens, segundos y consumo equivalente) frente a una pregunta suelta.
- **Estimación previa:** de 4,5 a 5 USD de consumo equivalente. La fijo con las cifras de la base nueva antes de pedir el visto bueno.

## 5. Lo que no entra

- Cambiar la búsqueda.
- Cambiar `INSTRUCTIONS`.
- Leer el expediente entero por defecto.
- Pedir a la IA que describa las fotos.
