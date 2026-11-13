# Informe: medición del expediente (tarea 9) y textos neutros del conector (D-084)

- **Fecha:** 2026-11-13. **Autor:** CTO 2. **Para:** CEO y propietario.
- **Encargos y decisiones:** encargo 13, D-078, D-084, D-086, D-087 y D-091.
- **Código medido:** `feature/casos-expediente` en 4a46dc6: conector neutro (a80931f) y puntuación de D-086 (6af124c). La fase B usa la carga con `--todas-las-capacidades` (211fa6c), que no cambia el código del producto. El ajuste de D-091 (40860ae) entró después y no está en estas cifras.
- **Condiciones:** Haiku, datos ficticios, Mac15,11 (Apple M3 Max, 36 GB) y tres ejecuciones por medición. El consumo equivalente usa el precio de la API, sin cobro.

## Resumen

1. **El expediente no cumple el mínimo.** La peor de 3 acierta 30 de 40 (75 %), frente al 90 % exigido. No hay ninguna fuga de aislamiento, pero sí una cita no literal: se cita un título como si fuera texto de la página.
2. **Causa principal:** la IA casi no usa las herramientas del expediente. `getCaseFile` aparece en 28 de 120 preguntas y `readCaseFile` en 16; el resto lo resuelve buscando y leyendo páginas sueltas.
3. **Consumo:** una pregunta de análisis del conjunto cuesta de mediana 0,0166 USD, 3,4 veces una suelta (0,0049), con un máximo de 0,0254 USD y 83 s. Una pregunta concreta cuesta 0,0032 USD (0,65 veces) y en ningún caso se leyó el expediente entero sin necesidad.
4. **Los textos neutros mantienen el umbral:** las 216 preguntas dan 207, 206 y 207. La peor iguala el 206, sin citas inventadas ni fugas.
5. **Las preguntas del propietario:** «¿Quiénes son nuestros clientes?» sale bien en las 3 ejecuciones. «¿Con qué empresas trabajamos?» sigue afirmando el sector sin cita literal (D-086) en las 3.

## 1. Qué se midió y cómo

- **Fase A, la pila de siempre:** las 216 preguntas y las 2 del propietario, tres veces. Así las cifras se pueden comparar con el umbral de 206.
- **Fase B, otra pila:** la misma carga más los 12 expedientes ficticios (`cargar_expedientes.py`) y las 40 preguntas de expediente, tres veces.
- **Repetición de la fase B:** la primera fase B no valió. La pila daba a las empresas solo `SEARCH_KNOWLEDGE` y `SAVE_KNOWLEDGE_CARD`, así que el conector no ofrecía las herramientas del expediente: en 1.804 llamadas no hubo ningún `getCaseFile`. Esos resultados están apartados como `…-invalida-sin-herramientas`, y la fase B se repitió con las cuatro capacidades.
- **Reanudación de la fase A:** en las ejecuciones 2 y 3 hubo 5 respuestas con error de salida estructurada. Se reanudaron sin repetir lo ya hecho, y las tres ejecuciones quedaron completas.

## 2. Resultados

| Medición | Ejecución 1 | Ejecución 2 | Ejecución 3 | Peor | Citas inventadas | Fugas de aislamiento |
|---|---|---|---|---|---|---|
| 216 preguntas (textos neutros) | 207 | 206 | 207 | **206 (95,4 %)** | 0 | 0 |
| 2 del propietario | 1 | 1 | 1 | 1 de 2 | 0 | 0 |
| 40 de expediente | 30 | 34 | 32 | **30 (75 %)** | 1 (en la 2) | 0 |

- **«Fugas» del expediente:** el puntuador marca una en la ejecución 1, pero no es una fuga de aislamiento. Es un correo de otro proyecto que cita el mismo número de referencia, que el trabajador sí ve (t01 y t06 ven todos los proyectos de su empresa). Es un error de pertenencia al expediente.
- **Fallos del expediente por tipo, sumando las tres ejecuciones:**
  - análisis del conjunto: 10 de 30;
  - qué contiene: 7 de 24;
  - documentos compartidos: 5 de 9;
  - documentos ajenos: 2 de 9;
  - concretas, lo que no consta y sustituidos: 0.
- **«No consta» falsos del expediente:** E039 y E040, de documentos compartidos entre dos expedientes.
- **La cita no literal:** en E016 (ejecución 2) se cita «Poliza seguro 2023» como fragmento de la página 1, pero es el título del documento y no está en su texto.

## 3. Consumo por tipo de pregunta

Mediana y máximo por pregunta, con las tres ejecuciones. «Suelta» son las 216 preguntas (648 respuestas).

| Tipo | Tokens (mediana / máximo) | Segundos | Consumo eq. USD | Veces una suelta (mediana) |
|---|---|---|---|---|
| Suelta | 84.279 / 310.711 | 13,8 / 52,3 | 0,0049 / 0,0191 | 1,0 |
| Análisis del conjunto | 311.579 / 552.571 | 48,2 / 83,1 | 0,0166 / 0,0254 | 3,4 |
| Documentos compartidos | 179.037 / 235.347 | 25,7 / 32,5 | 0,0109 / 0,0123 | 2,2 |
| Documentos ajenos | 136.192 / 187.226 | 28,1 / 36,3 | 0,0094 / 0,0144 | 1,9 |
| Qué contiene | 108.285 / 206.285 | 22,5 / 40,2 | 0,0084 / 0,0123 | 1,7 |
| Lo que no consta | 127.939 / 176.589 | 17,4 / 21,7 | 0,0062 / 0,0074 | 1,3 |
| Sustituidos | 69.851 / 127.218 | 11,4 / 15,7 | 0,0040 / 0,0068 | 0,8 |
| Concretas (expedientes grandes) | 65.127 / 114.907 | 9,6 / 19,2 | 0,0032 / 0,0052 | 0,65 |

- **Lecturas enteras innecesarias en las preguntas concretas:** 0 en las tres ejecuciones (D-078).
- **Consumo equivalente:**
  - fase B válida: 1,12 USD;
  - total de la tarea 9: 5,58 USD, con la fase B no válida (0,38) y la reanudación;
  - tope aprobado: 5,9 + 1,4 USD.

## 4. De dónde sale el volumen

- **El 81 % de los tokens son lecturas de caché.** En cada llamada a una herramienta, la conversación entera (instrucciones, herramientas y lo leído hasta ese momento) se vuelve a enviar al modelo. Se cobra más barato, pero cuenta.
- **El volumen depende sobre todo del número de llamadas.** Una pregunta de expediente hace 12 de mediana y hasta 42; una suelta, 8.
- **`verifyCitation` es la llamada más repetida:** 4 por pregunta de mediana y hasta 23. Cada cita se comprueba en una llamada aparte, y cada llamada vuelve a enviar todo el contexto.
- **La IA lee página a página en vez de usar el expediente:** 353 llamadas a `readPage` frente a 16 a `readCaseFile`.

## 5. Qué propongo (sin cambiar nada todavía)

1. **Llevar a la IA a usar el expediente.** Cuando la pregunta nombra un caso o un número de expediente, debe localizar la ficha y llamar a `getCaseFile`. Dos piezas:
   - que la descripción de `getCaseFile` lo diga;
   - que `searchKnowledge` marque en cada resultado si el documento es una ficha de caso, para que la IA tenga a mano el `caseId`.
   Es lo que más debería subir el acierto en las preguntas de análisis y de qué contiene.
2. **`verifyCitation` por lotes.** Una sola llamada que compruebe varias citas a la vez reduce mucho las llamadas y el contexto que se reenvía, sin perder ninguna comprobación.
3. **Pertenencia al expediente.** Añadir a la descripción de `getCaseFile` que un documento que cita el mismo número pero no está en el expediente no forma parte de él. Va dirigido a los fallos de compartidos y ajenos.
4. **Medirlo antes de adoptarlo:** las 40 de expediente y las 216, tres veces cada una, contra la base nueva con todas las capacidades.

## 6. Límites

- **Expedientes pequeños:** los expedientes ficticios tienen entre 8 y 17 documentos y unos 4.000 caracteres. Un expediente real puede ser mucho mayor. El CTO 3 preparará uno de unos 60 documentos.
- **Un solo modelo:** solo se ha medido Haiku. Con Sonnet el consumo por pregunta se multiplica.
- **Sin `findSimilarCases`:** ni las 216 de este informe ni las mediciones anteriores lo tenían. La base nueva con todas las capacidades lo incluye.

Los resultados de cada ejecución están en `experimentos/2026-11-12-archivo-ficticio/resultados/2026-11-13-t9-*` y `…-t9b-*`.
