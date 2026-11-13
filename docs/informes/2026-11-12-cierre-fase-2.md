# Odín · Cierre de la fase 2: medición

- Fecha: 2026-11-12. Autor: CTO 2. Para: propietario y CEO. D-050 y D-068.
- **Odín medido:** `feature/fase-2-conocimiento` en 785f1d1, con los siete bloques de la fase 2 (6, 7, 1, 3, 2, 5 y 4) y la feature de administración del CTO. Es lo que está desplegado en la instalación del propietario (`integracion/instalacion` 4963097, base V23).
- **Batería:** el archivo ficticio del encargo 12, con 1.236 documentos, 300 fichas, 216 preguntas y las 55 marcas de sustitución. Mismo puntuador para todas las ejecuciones.
- **Procedimiento (D-068):**
  - Haiku: tres ejecuciones completas y válidas; cuenta la peor, que tiene que superar el 90 %.
  - Sonnet: una ejecución completa; las otras dos, solo si queda por debajo del 93 %.
- **Límite:** datos ficticios. Las cifras no son una promesa.

## 1. Conclusión

La fase 2 pasa la medición de cierre:

- **Haiku:** la peor de tres ejecuciones completas saca 204 de 216 (94,4 %). El mínimo era más del 90 %.
- **Sonnet:** una ejecución completa saca 207 de 216 (95,8 %). Supera el 93 %, así que no hacen falta las otras dos.
- **Ningún documento sustituido presentado como vigente, ninguna fuga de aislamiento y ninguna cita no literal,** en ninguna de las cuatro ejecuciones.
- **Lo que queda son «no consta» falsos:** de 3 a 7 por ejecución. Son de búsqueda, no de lectura, y van al banco de pruebas del CTO 3 (D-059).

Desde lo medido (785f1d1) no ha cambiado código del producto: solo los scripts de medición y este informe.

## 2. Haiku: tres ejecuciones completas

| Ejecución | Correctas | % | Estricto | «No consta» falsos | Sustituidos como vigentes | Citas no literales | Fugas | Errores |
|---|---|---|---|---|---|---|---|---|
| 1 | 204 | 94,4 | 61,6 | 7 | 0 | 0 | 0 | 0 |
| 2 | 209 | 96,8 | 59,7 | 3 | 0 | 0 | 0 | 0 |
| 3 | 204 | 94,4 | 59,3 | 6 | 0 | 0 | 0 | 0 |
| **Peor de cada columna** | **204** | **94,4** | **59,3** | **7** | **0** | **0** | **0** | — |

- **La cifra que cuenta es 204 de 216 (94,4 %).** La sacan las ejecuciones 1 y 3. El mínimo pedía más del 90 %, es decir, al menos 195. Queda un margen de 9 preguntas.
- **Validez.** Las tres tienen las 216 preguntas, ninguna fila con error y ningún texto del límite de uso. El puntuador nuevo las marca como completas.
- **Ruido entre ejecuciones:** de 204 a 209. Hay 19 preguntas que fallan en alguna ejecución, pero solo 4 fallan en las tres.

**Por tipo:**

| Tipo | Ejecución 1 | Ejecución 2 | Ejecución 3 |
|---|---|---|---|
| Ficha | 25/25 | 25/25 | 25/25 |
| Sin fuente | 19/19 | 19/19 | 19/19 |
| Vocabulario | 34/34 | 34/34 | 34/34 |
| Casos parecidos | 13/13 | 13/13 | 13/13 |
| Identificador | 10/10 | 10/10 | 10/10 |
| Aislamiento | 16/17 | 17/17 | 17/17 |
| Versión | 43/46 | 43/46 | 43/46 |
| Contradicción | 15/16 | 15/16 | 13/16 |
| Dato concreto | 23/28 | 26/28 | 24/28 |
| Lista completa | 6/8 | 7/8 | 6/8 |

## 3. Sonnet

| Ejecución | Correctas | % | Estricto | «No consta» falsos | Sustituidos como vigentes | Citas no literales | Fugas | Errores |
|---|---|---|---|---|---|---|---|---|
| 1 | 207 | 95,8 | 67,1 | 4 | 0 | 0 | 0 | 0 |

- **Una sola ejecución.** Supera el 93 %, así que, según D-068, no se hacen las otras dos.
- **Validez.** Antes se hizo una llamada de prueba con `claude-sonnet-5-5`, que respondió bien. La ejecución tiene las 216 preguntas, ninguna fila con error y ningún texto del límite de uso, y está marcada como completa.
- **Por tipo:**
  - completos: versión 46/46, lista completa 8/8, vocabulario 34/34, casos parecidos 13/13, identificador 10/10;
  - dato concreto 26/28, ficha 23/25, sin fuente 18/19, aislamiento 16/17, contradicción 13/16.
- **El fallo de «sin fuente» (Q031) no es una respuesta inventada.** La pregunta no tiene respuesta en el archivo y la respuesta dice que no consta, con citas exactas, pero no se marca como «no consta» y añade un año sin respaldo.

## 4. Fallos graves, aparte

- **Sustituidos presentados como vigentes:** 0 en las tres ejecuciones de Haiku y en la de Sonnet. En el bloque 7 eran 3.
- **Fugas de aislamiento:** 0 en todas las ejecuciones.
- **Citas no literales:** 0. Todas las citas de las tres ejecuciones de Haiku se verifican como exactas, y las 530 de Sonnet.
- **«No consta» falsos:** la respuesta dice que no consta, pero la fuente está en el archivo.
  - Haiku: 7, 3 y 6. Siete preguntas distintas: Q125, Q138, Q139, Q147, Q149, Q150 y Q153.
  - Fallan en las tres ejecuciones: Q138, Q147 y Q153.
  - Son de búsqueda: la IA no encuentra la fuente con las consultas que hace. La consulta larga diluye los términos importantes (informe del experimento RRF). La mejora de la búsqueda queda para el banco de pruebas del CTO 3 (D-059).
  - Sonnet: 4 (Q137, Q153, Q154 y Q201).
  - Q153 falla en las cuatro ejecuciones.

## 5. Evolución en la fase 2 (Haiku, 216 preguntas)

| Medición | Correctas | % | «No consta» falsos | Sustituidos como vigentes | Citas no literales |
|---|---|---|---|---|---|
| Partida (encargo 12) | 146 | 67,6 | 35 | 7 | 36 |
| Bloque 6 | 196 | 90,7 | 12 | 2 | 0 |
| Bloque 7 | 200 | 92,6 | 6 | 3 | 0 |
| **Cierre, peor de tres** | **204** | **94,4** | **7** | **0** | **0** |

**Las mediciones no son del todo comparables:**
- Desde 9f17be8, el ejecutor de la batería permite a la IA las herramientas de lectura del trabajador. Antes solo eran `listProjects`, `listDocuments`, `searchKnowledge`, `readPage` y `verifyCitation`; se añadieron `findSimilarCases`, `listMyKnowledgeCards` y `listMyQuestions`.
  - **Corrección del 2026-11-13:** `findSimilarCases` no estuvo disponible en ninguna ejecución, aunque el ejecutor la permitía. La pila de medición (`pila/cargar.py`) crea las empresas solo con `SEARCH_KNOWLEDGE` y `SAVE_KNOWLEDGE_CARD`, y el conector solo ofrece las herramientas de las capacidades de la empresa. Las preguntas de casos parecidos (13/13) se respondieron sin esa herramienta. Las cifras de este informe son válidas para lo que se midió, pero no representan la instalación del propietario, que tiene todas las capacidades. La nueva línea base con todas las capacidades está pendiente (D-065 y D-088).
- Las mediciones anteriores eran de una sola ejecución; la de cierre es la peor de tres.
- Para «sustituido como vigente», el puntuador cuenta ahora solo las sustituciones enteras, no los anexos. Las mediciones anteriores se volvieron a puntuar con ese criterio.

## 6. Lo que queda

- **Las 4 preguntas que fallan en las tres ejecuciones:**
  - Q138, Q147 y Q153 son «no consta» falsos (apartado 4).
  - Q095 es de lista completa: da la lista entera, pero cita otra página del caso.
- **Contradicciones (de 13/16 a 15/16):** son el tipo que más varía entre ejecuciones.
- **Datos sin respaldo (74 a 81) y página imprecisa (38 a 39):** no entran en el mínimo y se siguen. Casi siempre son datos de contexto del mismo documento.
- **Mediciones reanudables.** La medición ya se para al llegar al límite de 5 horas o tras 3 errores seguidos, y se reanuda lanzando la misma orden con la misma etiqueta. Al reanudar solo se repiten las preguntas que no tienen respuesta válida, y el puntuador marca como incompleta la medición a la que le falte alguna pregunta. Está en 425ec9c y 553dc62.
  - Si la IA no devuelve la respuesta en el formato pedido, cuenta como error: al reanudar se vuelve a preguntar en lugar de contar como fallo.
  - En el cierre no ha pasado: las ejecuciones válidas no tienen ningún error.
- **Cortes por el límite.** Dos ejecuciones de Haiku se cortaron por el límite de 5 horas antes de que el detector estuviera corregido. Se apartaron como no válidas y se repitieron enteras. Están en `resultados/…-invalida-limite-de-uso`.

## 7. Máquina, tiempo y gasto

- **Máquina:** Mac15,11, Apple M3 Max, 36 GB, macOS 27.0.1, Docker 29.8.2.
- **Haiku:**
  - cada ejecución válida tarda unos 13 min (13:13, 13:23 y 13:24), con la pila, la carga y la bajada incluidas;
  - 18,9 a 19,1 millones de tokens por ejecución, casi todos de caché;
  - coste notificado de 1,15, 1,15 y 1,12 USD.
- **Las dos ejecuciones cortadas:** 0,41 y 0 USD notificados.
- **Sonnet:**
  - 11 min 43 s, 11,8 millones de tokens y 14,54 USD de coste notificado;
  - la llamada de prueba previa: 0,14 USD.
- **Total notificado del cierre:** 18,51 USD (Haiku: 3,42 en las válidas y 0,41 en las cortadas; Sonnet: 14,68).
- **Todo con la suscripción.** El coste notificado es el que informa la herramienta; no es un cobro aparte.

## 8. Para el propietario

- **Decisión:** si `feature/fase-2-conocimiento` pasa a `develop`. Cumple los mínimos de D-050 con cifras medidas.
- **Recomendación técnica:** sí. Lo que queda abierto (los «no consta» falsos de búsqueda) no bloquea, y tiene su sitio en el banco del CTO 3.
- **Sigue pendiente de tu permiso:** la verificación con una copia de tu instalación.

Los resultados de cada ejecución (respuestas, consultas, mapas de identificadores, puntuación y origen) están en `experimentos/2026-11-12-archivo-ficticio/resultados/2026-11-12-cierre-*`.
