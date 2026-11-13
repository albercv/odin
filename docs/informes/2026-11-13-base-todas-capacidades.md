# Informe: base nueva con todas las capacidades (INCOMPLETA)

- **Fecha:** 2026-11-13. **Autor:** CTO 2. **Para:** CEO y propietario.
- **Estado:** medición **incompleta**. Por orden del propietario se pararon todas las mediciones antes de reanudar la ejecución 2. Lo que hay se da tal cual, y lo que falta queda anotado.
- **Código medido:** `feature/casos-expediente` en 0320222, con el código de 48c08a4, que incluye D-091. La carga usa `--todas-las-capacidades`, así que el conector ofrece por primera vez `findSimilarCases` y las herramientas del expediente. No hay expedientes cargados.
- **Condiciones:** Haiku, datos ficticios y Mac15,11 (Apple M3 Max, 36 GB). Consumo equivalente: 2,70 USD, a precio de API y sin cobro, con un tope aprobado de 4,75.

## Resultados

| Medición | Ejecución 1 | Ejecución 2 | Ejecución 3 |
|---|---|---|---|
| 216 preguntas | 203 (94,0 %) | **incompleta:** 102 de 104 respondidas; faltan 112 | 202 (93,5 %) |
| Estricto | 66,2 % | 69,2 % (sobre 104) | 68,1 % |
| Citas inventadas | 0 | 0 | 0 |
| Fugas | 0 | 0 | 0 |
| «No consta» falsos | 6 | 0 (sobre 104) | 9 |
| Sustituidos como vigentes | 0 | 0 | 0 |
| Llamadas a `findSimilarCases` | 7 | 5 | 4 |
| Casos parecidos | 13/13 | 13/13 | 13/13 |
| 2 del propietario | 1 de 2 | 1 de 2 | 1 de 2 |

- **No hay peor de 3 válida.** Falta la ejecución 2 completa. Con las dos completas, la peor es 202 (93,5 %), por encima del 90 %, pero no se puede dar como umbral nuevo.
- **Comparación con los textos neutros** (sin `findSimilarCases`, 207/206/207): las dos ejecuciones completas salen 4 o 5 preguntas por debajo. Con tres ejecuciones no se puede distinguir si es ruido (unas 5 a 10 preguntas por ejecución) o un efecto de tener la herramienta. **No es una conclusión.**
- **«No consta» falsos:** en las dos ejecuciones completas se repiten Q138, Q139, Q153 y Q201. Q153 es la que busca arreglar `feature/busqueda-general`, que aún no se ha medido con IA.
- **Preguntas del propietario:** «¿Quiénes son nuestros clientes?» sale bien en las tres ejecuciones. «¿Con qué empresas trabajamos?» falla en las tres, también con D-091; no se ha analizado por qué.

## Por qué se cortó la ejecución 2

- **Fallo del ejecutor de la medición, no de Odín.** En `medir/ejecutar.py`, `leer_consultas` lee `ev.get("message")` dando por hecho que cada evento de la salida de `claude -p` es un objeto. En la pregunta 105, un evento llegó como texto, la excepción tiró el proceso entero y no se escribió `PARADA.txt`.
- **El arreglo:** comprobar el tipo con `isinstance`, igual que el CTO 3 hizo en su rama con `vistos_de`. Está **anotado y sin hacer**, por la orden de parar.
- **Además:** el guion de la medición no comprueba el código de salida de `ejecutar.py`, solo `PARADA.txt`. Por eso el corte apareció como «incompleta» y no como error.

## Lo que queda pendiente

- **Reanudar la ejecución 2:** 112 preguntas, unos 0,6 USD.
- **Medir Q153 contra esta base**, con todas las capacidades.
- **Las mejoras del expediente (D-093):** el diseño está en `feature/expediente-mejoras`, 0a7e3ee.
- **Valtierno**, el expediente grande del CTO 3.

Todo está parado hasta nueva orden.

Los resultados están en `experimentos/2026-11-12-archivo-ficticio/resultados/2026-11-13-base-*`.
