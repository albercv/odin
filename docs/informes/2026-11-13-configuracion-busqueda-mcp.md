# Cómo buscan las IAs en Odín a través del conector (MCP)

- Fecha: 2026-11-13. Autor: CTO 3. Para el propietario.
- Código descrito: `feature/fase-2-conocimiento` (lo que hoy se mide), más lo que el CTO 2 está construyendo para los expedientes (encargo 13), en `feature/casos-expediente`.
- Los USD son consumo equivalente (precio de API, sin cobro): lo que costaría por API; el propietario trabaja con los límites de su cuenta.

## 1. Idea general

La IA del trabajador (Claude, y en el futuro otras) no lee el archivo de una vez. Hace de bibliotecaria, con las herramientas que le da Odín:

1. pide la lista de proyectos;
2. busca;
3. lee páginas;
4. comprueba sus citas;
5. responde citando.

Odín no ve la respuesta final. Le da las herramientas y una instrucción, y garantiza que nadie ve lo que no le corresponde.

## 2. Herramientas del conector

**Las de hoy:**

| Herramienta | Para qué sirve | Tipo |
|---|---|---|
| `listProjects` | Los proyectos (comunidades, clientes u obras) que ve el trabajador, con su identificador para filtrar | Lectura |
| `searchKnowledge` | Buscar en los documentos y en las fichas aprobadas. Devuelve documento, página, extracto, proyecto, fecha de entrada y si está sustituido | Lectura |
| `listDocuments` | Listar documentos: hasta 2.000, filtrando por proyecto, por tema o por «ficha de caso». Avisa si quedan documentos sin etiquetar | Lectura |
| `readPage` | El texto literal de una página | Lectura |
| `verifyCitation` | Comprobar que una cita está literal en esa página | Lectura |
| `findSimilarCases` | Casos parecidos a un problema: 5 por defecto y 10 como máximo; solo documentos marcados como caso | Lectura |
| `listMyKnowledgeCards` | Las fichas del propio trabajador y su estado (pendiente, aprobada o rechazada) | Lectura |
| `listMyQuestions` | Las consultas recientes del propio trabajador | Lectura |
| `saveKnowledgeCard` | Guardar una ficha nueva; queda pendiente de que la apruebe el administrador | Escritura |

**Las del expediente (encargo 13).** El CTO 2 las está construyendo y todavía no están en lo que se mide:

| Herramienta | Para qué sirve | Tipo |
|---|---|---|
| `getCaseFile` | Ver el expediente de un caso: qué documentos tiene y en qué orden | Lectura |
| `readCaseFile` | Leer el expediente entero, por tandas, con cita por documento | Lectura |
| `openOriginal` | Un enlace para abrir el documento original | Lectura |
| `addToCaseFile` y `removeFromCaseFile` | Añadir o quitar documentos de un expediente; el documento sigue en Odín | Escritura |
| `requestUploadLink` | Un enlace a una página de Odín para subir documentos a ese caso | Escritura |

Además, `searchKnowledge` admitirá filtrar por caso.

## 3. La instrucción que reciben las IAs

**Tamaño:** 14 frases, 395 palabras y 2.180 caracteres. **El límite es de 2.200, así que está prácticamente llena.** Por eso las reglas nuevas van en la descripción de cada herramienta.

**Qué dice, en resumen:**
- citar el documento y la página de toda afirmación sobre el trabajo de la empresa;
- si no hay resultados, decir «no consta» y no inventar;
- si la pregunta nombra un proyecto, buscarlo y filtrar por él;
- comprobar las citas con `verifyCitation`; si una falla, no usarla;
- presentar las fichas como fichas aportadas por trabajadores y redactadas con IA, sin página, citando solo su texto;
- copiar cada fragmento literal por separado;
- afirmar solo lo que dicen las citas, y avisar si la fuente es parcial;
- un documento sustituido no se presenta como vigente: decir por cuál está sustituido;
- **si dos fuentes no cuadran:**
  - si una dice expresamente que cambia a la otra, o es un acuerdo posterior de la junta, se da lo que vale ahora y se citan las dos;
  - si no, se enseñan las dos y no se elige;
- una página escaneada puede tener errores de OCR: se cita tal cual;
- una página vacía no se ha leído: no se concluye nada de ella;
- el conocimiento general va aparte, nunca mezclado con lo que dicen los documentos.

## 4. Cómo busca Odín por dentro

- **Búsqueda por palabras en español** (texto completo de PostgreSQL, con raíces: «convocatoria» encuentra «convocatorias»).
  - Cada consulta se trata como «cualquiera de estas palabras», y los resultados se ordenan por lo bien que encajan.
- **Canal exacto:** los números y los identificadores (pólizas, casos) que aparecen literalmente puntúan mucho más.
- **Cuántos resultados:** 10 por defecto y 50 como máximo por búsqueda. La IA puede lanzar varias consultas a la vez.
- **Filtro por proyecto:** limita la búsqueda a un proyecto, más lo que no tiene proyecto (normas internas del despacho), que ve toda la empresa (D-047, D-053).
- **Qué entra:**
  - solo documentos ya procesados;
  - de las fichas, solo las aprobadas: las pendientes y las rechazadas no salen nunca en la búsqueda;
  - nunca nada de otra empresa ni de un proyecto que el trabajador no ve. Lo garantizan el código y la base de datos.
- **Sustituidos:** salen siempre detrás de los vigentes, marcados y con el documento que los sustituye (D-050).
- **Temas:** los pone un modelo local al entrar el documento. Sirven para listar por tema, que puede quedar incompleto si hay documentos sin etiquetar (la IA lo avisa).

**Un límite conocido, Q153 del barrido:**
- **El caso:** una norma interna sin proyecto no aparece entre los 50 primeros resultados de ninguna consulta razonable, tapada por actas casi iguales de 33 comunidades.
- **Hoy no hay forma** de pedir «busca solo en lo general».
- **Propuesto al CTO 2,** para después del encargo 13:
  - un filtro «solo lo general»;
  - o que el título pese más al ordenar.

## 5. Los enfoques del banco de pruebas

El banco pone delante de la pregunta un texto corto de enfoque, para comparar formas de buscar:

| Enfoque | Qué le pide a la IA |
|---|---|
| `main` | Nada más; es la instrucción de hoy, tal cual |
| `synonyms` | Buscar con palabras técnicas y corrientes, y con variantes (tildes, abreviaturas, mayúsculas), y comparar lo que da cada una antes de responder |
| `topic` | Pensar antes qué tipo de documento o qué tema lo diría, y leer esos documentos |
| `filterFirst` | Fijar primero el proyecto y el tema, y buscar solo dentro |
| `documentFirst` | Encontrar el documento que mejor encaja y leerlo entero antes de responder |
| `skeptic` | Buscar versiones más nuevas, sustituciones y lo que contradiga lo encontrado |

**Qué cambiaría si se adopta `synonyms`:**
- **Dónde iría:** sus tres frases (unos 230 caracteres) se añadirían a la descripción de `searchKnowledge`, porque en la instrucción ya no caben.
- **Qué hace la IA:** buscaría con más variantes de palabras. Gasta algo más: unos 0,009 USD por respuesta, frente a 0,005.
- **Lo medido en el barrido de 40 preguntas** (exploratorio):
  - acertó el 94,0 % por ejecución, frente al 89,5 % de la instrucción de hoy;
  - recuperó Q147 (4 de 5, frente a 1 de 10).
- **Pendiente:** la prueba con las 216 preguntas y 3 repeticiones (D-081), en marcha, decide si se adopta.

## 6. Qué se mide hoy y con qué umbrales

| Qué | Umbral | Dónde está hoy |
|---|---|---|
| **Respuestas correctas** (con su cita o un «no consta» bien dicho) | Más del 90 % en la peor de 3 repeticiones (D-049, D-060) | Medición del 12 de noviembre: Haiku 93,1 % con 216 preguntas, en una ejecución; el intervalo del 95 % va del 88,9 al 95,8 %. Las cifras de cierre del CTO 2 están en sus informes |
| Fugas entre empresas o proyectos | 0, siempre | 0 en todas las mediciones |
| Citas inventadas | 0 (D-050) | 0 desde el bloque 6 |
| «No consta» cuando sí constaba | 1,5 % como mucho | 6–9 de 216 (2,8–4,2 %) en las últimas mediciones; por encima del objetivo |
| Sustituidos dados por vigentes | 0 | 0 en las últimas |
| Respuestas «limpias» (cada dato con su cita) | 90 % de las correctas | Por medir con el puntuador recalibrado (fase 2 del plan) |
| Listas completas | 90 % | Con 8 preguntas de lista, la cifra baila entre 4 y 8 de 8: hay que ampliar la batería |
| Varios buscadores a la vez | Solo si mejora más que la variación con 0,02 USD de más por respuesta como mucho (D-060) | No compensa: ninguna mezcla de 2 a 5 mejora; los 20 juntos sí, pero con 25 veces el consumo |
| Jurado de IA para preguntas sin clave | Aceptaciones falsas del 3 % como mucho y kappa de al menos 0,8 (D-060) | No lo cumple (kappa −0,10): aparcado (D-067) |

- **Cómo se mide:**
  - con el banco de pruebas, contra un archivo ficticio de 1.236 documentos y 300 fichas, en una copia aparte de Odín;
  - un programa compara cada respuesta con su clave, sin una IA que haga de juez.
- **Las cifras no son de datos reales.** El plan prevé un archivo público real y preguntas escritas por administradores (fase 2).
