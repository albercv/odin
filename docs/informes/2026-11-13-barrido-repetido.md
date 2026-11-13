# Barrido de 40 preguntas (tarea 12; D-069 y D-077): resultados completos

- Fecha: 2026-11-13. Autor: CTO 3. Para la revisión del propietario.
- Odín y banco: `feature/calidad-recuperacion`, a3e4b2d. Pila `odin-banco` con el archivo ficticio completo.
- **Límite:** batería de desarrollo y cifras exploratorias. La muestra incluye a propósito Q138, Q147 y Q153, que fallan siempre en el cierre del CTO 2.
- **Los USD de este informe son consumo equivalente (precio de API, sin cobro).** El propietario no paga API: trabaja con los límites de su cuenta. Los topes de D-061 se miden en esa misma unidad.

## 1. Tandas y consumo

| Tanda | Fechas | Qué hizo | Consumo equivalente |
|---|---|---|---|
| Primera ejecución (no válida, límite de 5 horas sin detectar) | 12 nov., de 18:59 a 19:17 | 157 buscadores válidos | 0,7774 USD |
| Tanda 1 | 12 nov., de 23:05 a 23:55 | 754 buscadores; se paró sola al llegar al tope de 5,5 USD | 4,7686 USD |
| Tanda 2 (opción A, D-077) | 13 nov., de 03:12 a 03:24 | 45 buscadores `synonyms` y la frontera de un solo buscador | 1,0173 USD |
| Jurado | 13 nov., de 03:24 a 03:48 | redactor y jueces de 40 respuestas (los buscadores salen de la caché, sin consumo) | 0,4325 USD |
| **Total del barrido** | | | **6,9958 USD** (límite de D-077: 7,25) |

- **Consumo acumulado del plan:** unos 7,6 USD de los unos 40 aprobados en D-061.
- **Consumo por buscador:** `main` 0,0051 USD, `topic` 0,0066 y `synonyms` 0,0090.
- **Resultados:**
  - `banco/resultados/2026-11-12-2305-barrido-40/` (tanda 1);
  - `banco/resultados/2026-11-13-0312-barrido-40/` (grupo completo, curvas y frontera);
  - `banco/resultados/2026-11-13-0324-barrido-40-jurado/` (redactor, jueces y acuerdo).

## 2. Un buscador solo, según el enfoque (40 preguntas)

| Enfoque | Acierto por ejecución | Consumo por respuesta |
|---|---|---|
| `main` (10 por pregunta) | 89,5 % | 0,0051 USD |
| `topic` (5 por pregunta) | 91,5 % | 0,0066 USD |
| `synonyms` (5 por pregunta) | **94,0 %** | 0,0090 USD |

- **La frontera de un solo `main` no sube con más turnos:** 92,5 % con 10 turnos, 87,2 % con 20 y 87,5 % con 40, con el mismo consumo (unos 0,005 USD). En la mediana usa 9 turnos, así que el tope casi nunca actúa.
- **La variación** de un `main` suelto va del 82,5 al 95,0 % entre sorteos. La regla del banco pide mejorar por encima de su mitad, 6,2 puntos.
- **`synonyms` en solitario:** el 94,0 % queda justo por encima del umbral de la frontera (87,5 % + 6,2 = 93,7 %), con 0,004 USD más por respuesta. **Es la única mejora barata que aparece.** Al ser tan justa, falta confirmarla con la batería completa y repeticiones (apartado 7).

## 3. Varios buscadores votando (curvas por submuestreo, 20 sorteos)

| Mezcla | Acierto medio (mín.–máx.) | Consumo por respuesta | ¿Supera a un solo buscador con el mismo consumo? |
|---|---|---|---|
| 1 `main` | 88,9 % (82,5–95,0) | 0,005 | — |
| 2 `main` | 79,1 % (67,5–85,0) | 0,010 | no (los empates acaban en «no consta») |
| 3 `main` | 89,7 % (85,0–95,0) | 0,015 | no |
| 4 `main` | 88,9 % (85,0–92,5) | 0,021 | no |
| 5 `main` | 91,4 % (85,0–95,0) | 0,026 | no |
| 1 `main`, 1 `topic` y 1 `synonyms` | 89,4 % (82,5–95,0) | 0,020 | no |
| 2 `main`, 1 `topic` y 1 `synonyms` | 90,6 % (85,0–95,0) | 0,026 | no |
| 3 `main`, 1 `topic` y 1 `synonyms` | 92,5 % (87,5–97,5) | 0,031 | no |
| Los 20 | 95,0 % | 0,129 | sí, con 25 veces el consumo |

- **No hay un número mínimo de buscadores que mejore de verdad** dentro del límite de D-060 (0,02 USD de más por respuesta).
- Solo los 20 juntos superan a uno solo, y cuestan unas 25 veces más.

## 4. El acuerdo entre buscadores como señal (20 buscadores; firma: «no consta» y documentos citados)

- Las ejecuciones que coinciden con la mayoría de su pregunta aciertan el **97,0 %** (663 ejecuciones).
- Las que se apartan aciertan el **62,8 %** (137).
- **El 71,8 % de los fallos está fuera de la mayoría.**
- **Por pregunta:**
  - con al menos el 60 % de acuerdo, la respuesta final acierta en las 35 preguntas;
  - con menos del 60 %, acierta en 3 de 5.
- **La señal se confirma, y con más datos que en el piloto.** Serviría para avisar de que una respuesta es poco segura, pero en el producto obliga a lanzar varios buscadores por pregunta. Esa decisión se la lleva el CEO al propietario (D-016).

## 5. Redactor y jurado

- **Respuesta del redactor sobre los 20 buscadores:** 38 de 40 (95,0 %; intervalo del 95 %: 83,5 a 98,6 %). Sin fugas, sin citas inventadas, sin «no consta» falsos y sin sustituidos dados por vigentes.
- **Jurado** (3 Haiku y 2 `qwen3.5:9b` sin razonamiento; los «no consta» no se juzgan, D-067):
  - kappa frente al script: **−0,10**;
  - aceptó como buena **la única respuesta errónea que juzgó**;
  - rechazó el 22,6 % de las correctas;
  - 7 «no consta» sin juzgar.
- **El jurado no cumple D-060** (aceptaciones falsas como mucho del 3 % y kappa de al menos 0,8). No sirve para puntuar donde no hay clave, ni como verificador de respuestas (`checkAnswer`).
- **Lo prudente es aparcarlo.** Habría que probar otra rúbrica u otro modelo antes de volver a contar con él.
- **Latencia de la estrategia completa** (20 buscadores, redactor y jueces): mediana de 62 s y percentil 95 de 145 s.

## 6. Las tres preguntas que fallan siempre

| Pregunta | Tipo | `main` | `topic` | `synonyms` | Respuesta final con 20 buscadores |
|---|---|---|---|---|---|
| Q138 | dato concreto | 5/10 | 4/5 | 4/5 | bien |
| Q147 | versión | 1/10 | 2/5 | **4/5** | bien |
| Q153 | dato concreto | 0/10 | 1/5 | 0/5 | bien |

- **Q147 se recupera con `synonyms`,** y Q138 mejora con los enfoques nuevos.
- **Q153 es un problema de búsqueda, no de enfoque.**
  - La respuesta («con 12 días de antelación») está en una norma interna de Gestión Albar sin proyecto: `albar/_general/norma-convocatoria-juntas.txt`.
  - Al repetir en la pila del banco las consultas de los buscadores («convocatoria junta días antelación», «antelación convocatoria», «plazo convocatoria junta», e incluso «norma interna convocatoria juntas»), esa norma no sale entre los 50 primeros resultados. Las tapan las «Acta ordinaria 2025» casi iguales de las 33 comunidades.
  - Solo 1 de los 20 buscadores llegó a ver la página. Ninguna búsqueda filtró por proyecto, y además no hay forma de pedir «solo lo general».
- **Propuesta para la búsqueda** (es del CTO 2, y se mide con el repetidor):
  - que `searchKnowledge` pueda limitarse a lo que no tiene proyecto (normas internas del despacho);
  - o que el título pese más en la clasificación;
  - y una regla en la instrucción: para preguntas sobre normas de la empresa, buscar en lo general.

## 7. Propuesta para la fase siguiente

1. **Probar las reglas de `synonyms` en la instrucción del MCP** (aprobado por el CEO, D-077). Hay que medir como siempre: 3 ejecuciones completas de Haiku, cuenta la peor, y no puede empeorar ninguna batería. La estimación va en el apartado 8.
2. **No llevar varios buscadores a Odín.** El acuerdo como aviso de poca seguridad lo decide el propietario.
3. **Aparcar el jurado** hasta tener otra rúbrica u otro modelo que cumpla D-060.
4. **Q153:** la búsqueda en lo general, para el CTO 2, medida con el repetidor.
5. **Expedientes (encargo 13):** el banco añade su fichero de preguntas y el filtro por tipo, junto con el CTO 2.

## 8. Estimación de consumo de la prueba de `synonyms` en la instrucción

- **Comparación justa:** en el mismo banco y con la misma pila, la instrucción de hoy frente a la instrucción con las reglas de `synonyms`. Un buscador por pregunta, las 216 preguntas y 3 repeticiones de cada una.
- **Consumo equivalente:**
  - `synonyms`: 3 × 216 × 0,0090 = unos 5,8 USD;
  - instrucción de hoy: 3 × 216 × 0,0051 = unos 3,3 USD;
  - **total: unos 9,1 USD.** El plan pasaría de unos 7,6 a unos 16,7 de los 40.
- **Tiempo:** unas 2 horas de Haiku, que se pueden repartir en varias tandas porque el banco se para y se reanuda.
- **Si se reutilizaran las ejecuciones de cierre del CTO 2 como referencia,** se ahorrarían unos 3,3 USD. Pero son de otro código y de otra pila, y la comparación dejaría de ser justa. **Recomiendo medir las dos en el banco.**
