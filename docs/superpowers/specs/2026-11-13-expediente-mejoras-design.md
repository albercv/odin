# Diseño técnico: mejoras del expediente (D-093)

- **Fecha:** 2026-11-13. **Autor:** CTO 2.
- **Origen:** la medición de la tarea 9 (`docs/informes/2026-11-13-expediente-medicion.md`). La peor de 3 en las 40 preguntas de expediente fue 30 (75 %), con una cita no literal. La IA usó `getCaseFile` en 28 de 120 preguntas y `verifyCitation` una vez por cita (hasta 23 llamadas en una pregunta).
- **Rama:** `feature/expediente-mejoras`, que sale de `feature/casos-expediente` (0320222). Cada tarea va en su rama `task/expediente-mejoras/<tarea>`.
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

- **API:** `POST /v1/citations/verify` acepta también `{"citations": [{documentId, pageNumber, quote}, …]}`, con entre 1 y 20 citas, y devuelve `{"results": [{documentId, pageNumber, verified}, …]}` en el mismo orden. La forma de una sola cita sigue igual, por compatibilidad.
  - Cada cita se comprueba con la visibilidad de la persona, como hoy. Una cita de un documento que no ve sale como `verified: false`, sin distinguir si el documento existe.
  - El volumen entregado cuenta como hoy.
- **MCP, `verifyCitation`:** acepta `citations` (una lista) y su descripción dice que se comprueben todas las citas de la respuesta en una sola llamada.
  - Se mantiene la regla de no usar una cita que dé `false`.
  - Se añade que el título del documento no es texto de la página: solo vale como cita lo que aparece en el texto.
  - Esa frase va dirigida a la cita no literal de la medición.
- **Pruebas:** un lote mezclado (exacta, inventada y de un documento invisible) devuelve tres resultados en orden; más de 20 citas dan 400; la forma de una sola cita sigue funcionando.

## 3. Pertenencia al expediente

- **MCP, `getCaseFile` y `readCaseFile`:** la descripción dice que un documento que cita el mismo número de caso pero no aparece en `getCaseFile` no forma parte del expediente. La IA no debe incluirlo, aunque puede mencionarlo aparte, diciendo que no está en el expediente.
- **Pruebas:** la frase está en las dos descripciones. `INSTRUCTIONS` no cambia y se queda en 2.200 caracteres o menos.

## 4. Medición (con el visto bueno del CEO antes de lanzar)

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
