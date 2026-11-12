# Informe: bloque 6, medición después de los cambios del MCP

- Fecha: 2026-11-12. Autor: CTO 2. Bloque 6 de la fase 2 (D-049, D-050 y D-052).
- **Odín medido:** `feature/fase-2-conocimiento` en 2d69792. Es la 2a cerrada (4224659) más el bloque 6.
- **Batería:** el archivo del encargo 12: 1.236 documentos, 300 fichas y 216 preguntas. Las mismas preguntas, la misma pila y el mismo puntuador que la medición de partida.
- **Límite:** datos ficticios y una sola ejecución. Las cifras no son una promesa.

## 1. Qué cambió

Todo está en el MCP. No cambian la API ni la base de datos.

- **`listProjects`:** devuelve los proyectos que ve el trabajador, sacados de `GET /v1/me`. La instrucción pide filtrar por el proyecto que nombra la pregunta.
- **`projectName`:** cada resultado de `searchKnowledge` y `listDocuments` lleva el nombre de su proyecto. `readPage` no lo lleva, porque haría falta cambiar la API.
- **`addedAt` en lugar de `date`,** descrito como fecha de alta en Odín.
- **Reglas de cita:**
  - cada fragmento literal por separado, sin «...»;
  - de una ficha, solo su texto.
- **Instrucción de fuente parcial.**

Además:
- el ejecutor de la batería guarda las consultas de la IA;
- el puntuador separa los fallos graves;
- `ArchiveSearchBatteryIT` entra en `make check`: es una búsqueda determinista sobre el archivo, que da 96 de 131 con el filtro de proyecto, y ese es su umbral.

## 2. Resultado

| | Partida | Bloque 6 |
|---|---|---|
| **Haiku, 216 preguntas** | **146 (67,6 %)** | **196 (90,7 %)** |
| Haiku, porcentaje estricto | 43,1 % | 57,4 % |
| Citas no literales | 36 de 374 | 0 de 466 |
| «No consta» falsos (grave) | 35 | 12 |
| Sustituidos presentados como vigentes (grave) | 7 | 2 |
| Fugas de aislamiento | 0 | 0 |
| Datos sin respaldo | 62 | 78 |
| Página imprecisa | 27 | 35 |
| **Sonnet, muestra de 30** | **28 (93,3 %)** | **29 (96,7 %)** |
| Haiku, en esa misma muestra | 21 | 28 |

- **Por tipo, Haiku:**

| Tipo | Partida | Bloque 6 |
|---|---|---|
| Identificador | 9/10 | 10/10 |
| Lista completa | 5/8 | 8/8 |
| Casos parecidos | 9/13 | 13/13 |
| Vocabulario | 27/34 | 33/34 |
| Ficha | 11/25 | 24/25 |
| Sin fuente | 14/19 | 18/19 |
| Versión | 30/46 | 41/46 |
| Aislamiento | 15/17 | 15/17 |
| Dato concreto | 20/28 | 24/28 |
| Contradicción | 6/16 | 10/16 |

- **Movimientos:** mejoran 52 preguntas y empeoran 2 (Q004 y Q151).
- **El 90,7 % pasa el mínimo por muy poco, en una sola ejecución:** 196 de 216, cuando el mínimo pide al menos 195. Un par de respuestas que cambien por azar lo dejan por debajo. La cifra que vale es la de la medición completa de cierre (D-050).

## 3. Qué falla todavía

- **12 «no consta» falsos.** Con las consultas guardadas se ve qué buscó la IA:
  - **Q201 y Q203:** un procedimiento del proyecto abierto. La pregunta no nombra ningún proyecto, y la búsqueda sin filtro no saca la página entre los resultados que la IA lee.
  - **Q151:** una norma interna sin proyecto. Probó cuatro formulaciones y no salió.
  - Lo que queda es búsqueda, no lectura.
  - **Ojo con el filtro:** al filtrar por un proyecto, la búsqueda deja fuera lo que no tiene proyecto (normas internas, procedimientos generales). Ahora que la IA filtra más, conviene que el filtro incluya también lo que no tiene proyecto. Es un cambio pequeño en `SearchKnowledge`, y lo propongo para el bloque 7.
- **2 sustituidos como vigentes (Q004 y Q024):** es lo que ataca el bloque 7.
- **Contradicciones (10/16):** siguen siendo lo más débil. Las marcas de sustitución del bloque 7 y la regla de «enseña las dos con su fecha» van a por ellas.
- **Datos sin respaldo (78) y página imprecisa (35) suben,** porque ahora la IA cita más. Casi siempre son datos de contexto del mismo documento. No entran en el mínimo, pero se siguen.

## 4. Máquina, tiempo y consumo

- **Máquina:** Mac15,11, Apple M3 Max, 36 GB, macOS 27.0.1, Docker 29.8.2.
- **Haiku, 216 preguntas:**
  - 14 min 9 s con la pila, la carga y la bajada incluidas, unos 15 s por pregunta;
  - 18,8 millones de tokens, casi todos de caché;
  - coste notificado: 1,25 USD.
- **Sonnet, 30 preguntas:**
  - 1,5 millones de tokens, unos 14 s por pregunta;
  - coste notificado: 2,13 USD.
- **Todo con la suscripción.**
- **Comprobaciones:**
  - `make check` con 0, en 1 min 15 s, con `SearchBatteryIT` en 27 de 30;
  - `make demo` con 0.

## 5. Siguiente

- **Bloque 7** (documentos sustituidos), cuando lo diga el CEO. Propongo añadirle que el filtro por proyecto incluya lo que no tiene proyecto (apartado 3).
- **Mediciones intermedias:** con Haiku completo.
- **Cierre de la recuperación** (al terminar el bloque 2): medición completa con Haiku y con Sonnet.

Las respuestas, las consultas, los mapas de identificadores y las puntuaciones están en `experimentos/2026-11-12-archivo-ficticio/resultados/2026-11-12-haiku-bloque6/` y `.../2026-11-12-sonnet-muestra-bloque6/`.
