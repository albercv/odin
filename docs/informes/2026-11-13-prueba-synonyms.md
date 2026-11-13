# Prueba de las reglas de `synonyms` frente a la instrucción de hoy (D-081)

- Fecha: 2026-11-13. Autor: CTO 3. Para el propietario, a través del CEO.
- Odín y banco: `feature/calidad-recuperacion`, 5aaece7. Pila `odin-banco` con el archivo ficticio completo.
- **Los USD de este informe son consumo equivalente (precio de API, sin cobro).**

## Resumen

1. **Qué mejora:** nada en la cifra que decide. En la peor de 3 ejecuciones, las reglas de `synonyms` aciertan 206 de 216 (95,4 %), igual que la instrucción de hoy.
2. **Cuánto:** la media sube 0,5 puntos (96,3 % frente a 95,8 %; 3 respuestas de 648). Es menos que la variación entre ejecuciones: 0,9 puntos con la instrucción de hoy y 1,9 con `synonyms`.
3. **Por dentro:** arregla preguntas en las que el documento usa otras palabras (Q153, Q187 y Q125 pasan a 3 de 3), pero estropea otras que hoy salen bien (listas, fichas y contradicciones). Las ganancias y las pérdidas se compensan.
4. **Precio:** consumo equivalente un 58 % mayor por respuesta (0,0085 frente a 0,0054 USD), 3 turnos y unos 3,5 s más por pregunta. Ninguna de las 6 ejecuciones tuvo fugas, citas inventadas ni sustituidos presentados como vigentes.
5. **Recomendación: no pasarlo al producto.** No cumple D-081: la peor de 3 no mejora, y por tipos baja en tres (listas, fichas y aislamiento, una respuesta en cada uno).

## 1. Qué se midió

- **Comparación:** la instrucción de hoy (`main`) frente a la misma instrucción con las reglas de `synonyms` (`banco/enfoques/synonyms.txt`).
  - Mismo banco, misma pila, mismo código y mismo modelo (Haiku).
  - Un buscador por pregunta, las 216 preguntas de la batería de desarrollo y 3 repeticiones de cada enfoque: 1.296 ejecuciones.
- **Puntuación:** la del script (`puntuar.score_item`), réplica a réplica. Cuenta la peor de las 3.
- **Dónde van las reglas en la prueba:** en el mensaje del buscador, delante de la pregunta. En el producto no caben en la instrucción del MCP, que está llena (2.180 de 2.200 caracteres). Tendrían que ir en la descripción de `searchKnowledge`, y su efecto podría no ser el mismo.
- **Datos:** `banco/resultados/2026-11-13-0353-synonyms-instruccion/`.
  - `por-replica.json` tiene las cifras de este informe.
  - `analisis_por_replica.py` es el script que las calcula, sin IA ni red.

## 2. Resultados por ejecución

| Enfoque | Ejecución | Acierto | Intervalo de Wilson al 95 % | Fugas | Citas inventadas | «No consta» falso | Sustituido como vigente |
|---|---|---|---|---|---|---|---|
| `main` | 1 | 206/216 (95,4 %) | 91,7–97,5 % | 0 | 0 | 4 | 0 |
| `main` | 2 | 208/216 (96,3 %) | 92,9–98,1 % | 0 | 0 | 4 | 0 |
| `main` | 3 | 207/216 (95,8 %) | 92,3–97,8 % | 0 | 0 | 3 | 0 |
| `synonyms` | 1 | 206/216 (95,4 %) | 91,7–97,5 % | 0 | 0 | 4 | 0 |
| `synonyms` | 2 | 210/216 (97,2 %) | 94,1–98,7 % | 0 | 0 | 2 | 0 |
| `synonyms` | 3 | 208/216 (96,3 %) | 92,9–98,1 % | 0 | 0 | 4 | 0 |

- **Peor de 3:** 95,4 % en los dos enfoques.
- **Pregunta a pregunta, en cada repetición:** `synonyms` acierta donde `main` falla en 5, 7 y 6 preguntas. `main` acierta donde `synonyms` falla en 5, 5 y 5. Es un empate dentro del ruido.
- **Contexto, no comparable:** el 95,4 % de `main` en el banco no se compara con el 94,4 % del cierre de la fase 2 (D-071), porque son de otro código y de otra pila.

## 3. Por tipo de pregunta

| Tipo | `main` (3 ejecuciones) | `synonyms` (3 ejecuciones) | Peor ejecución: `main` / `synonyms` |
|---|---|---|---|
| dato concreto | 75/84 | **80/84** | 25 / 26 de 28 |
| versión | 133/138 | 135/138 | 44 / 45 de 46 |
| contradicción | 42/48 | 43/48 | 13 / 14 de 16 |
| lista completa | 20/24 | 18/24 | 6 / **5** de 8 |
| ficha | 75/75 | 73/75 | 25 / **24** de 25 |
| aislamiento | 48/51 | 47/51 | 16 / **15** de 17 |
| identificador, vocabulario, casos parecidos y sin fuente | todo bien | todo bien | igual |

- **Mejora** donde hay que buscar con otras palabras: datos concretos y versiones.
- **Empeora un poco** en listas, fichas y aislamiento.
- El fallo de aislamiento de `synonyms` (Q198) es una página citada imprecisa, no una fuga.

## 4. Qué arregla y qué estropea

- **Arregla** (pasa de 0 o 1 de 3 a 3 de 3):
  - Q153: la norma interna de convocatoria de juntas;
  - Q187: una contradicción;
  - Q125: una versión.
- **Estropea** (pasa de 3 de 3 a 1 o 2 de 3): Q041 y Q097 (listas), Q159 y Q178 (fichas), Q179, Q181 y Q182 (contradicciones) y Q198 (aislamiento).
- **Lo que hay detrás:** más búsquedas con más palabras encuentran lo escondido, pero también traen más ruido. En las listas y en las contradicciones, ese ruido acaba en respuestas incompletas o con la página equivocada.

**Q153, en detalle:**

- Con `main`, 0 de 3: no llega nunca a la norma.
- Con `synonyms`, 3 de 3: la encuentra tras unas 8 búsquedas, con fórmulas como «antelación mínima», «días naturales convocatoria» o «seis días naturales».
- En el barrido de 40, `synonyms` hizo 0 de 5 en esta pregunta. Encontrarla depende de insistir, y eso varía mucho.
- **La propuesta al CTO 2 sigue en pie:** buscar solo en lo general, o dar más peso al título. Así el documento se encontraría a la primera, sin depender de la insistencia.

## 5. Por qué el barrido de 40 apuntaba a una mejora

- El barrido daba 94,0 % a `synonyms` frente a 89,5 % de `main`.
- Esa muestra llevaba a propósito las preguntas difíciles (Q138, Q147 y Q153). En esas 40 preguntas, esta prueba da:
  - `main`: 35, 36 y 37 de 40;
  - `synonyms`: 36, 40 y 38.
- `synonyms` también gana ahí en la peor ejecución, pero por una sola respuesta.
- En las otras 176 preguntas pierde lo que gana en las difíciles.
- **Lección:** una muestra cargada de preguntas difíciles exagera las mejoras. Por eso D-081 pedía la batería completa con 3 repeticiones. El método ha hecho su trabajo.

## 6. Otras señales de esta prueba

- **Acuerdo entre buscadores** (6 por pregunta: 3 `main` y 3 `synonyms`):
  - las ejecuciones que coinciden con la mayoría aciertan el 99,2 %;
  - las que se apartan, el 81,2 %;
  - el 82,3 % de los fallos queda fuera de la mayoría.
  - Confirma con la batería completa lo que vio el barrido.
- **Jurado:** el juez local `qwen3.5:9b` (rúbrica estricta, sin consumo) da un kappa de 0,03 frente al script. Sigue aparcado (D-081).

## 7. Consumo y tiempo

| Tanda | Horas (13 nov.) | Qué hizo | Consumo equivalente |
|---|---|---|---|
| 1 | 03:53–04:24 | 460 llamadas de pago; la paró el límite de 5 horas de la cuenta | 2,6172 USD |
| 2 | 08:02–09:30 | 840 llamadas de pago; reanudada sin repetir las hechas | 6,3991 USD |
| **Total** | | | **9,0163 USD** (estimado: unos 9,1; tope de D-081: 11,4) |

- **Consumo por respuesta:** `main`, 0,0054 USD (mediana de 9 turnos y unos 14 s); `synonyms`, 0,0085 USD (12 turnos y unos 17,5 s).
- **Consumo acumulado del plan:** unos 16,6 USD de los unos 40 aprobados en D-061.

## 8. Lo que no se ha medido

- **Otras baterías:** la de reserva, la de expedientes (encargo 13) y la de los demás archivos. Solo se ha medido la batería de desarrollo del archivo ficticio.
- **La ubicación real de las reglas:** en el producto irían en la descripción de `searchKnowledge`.
- **Otros modelos:** Sonnet y las IAs de otros proveedores.

## 9. Qué propongo

1. **No integrar las reglas de `synonyms`.** No hace falta que el CTO 2 las lleve al producto después del encargo 13.
2. **Q153 se arregla en la búsqueda, no en la instrucción.** Es la propuesta que ya tiene el CTO 2, y se mide con el repetidor.
3. **No probar ahora variantes de la regla**, como «reformula solo si la primera búsqueda no da el dato». Es una hipótesis sin datos, y medirla costaría unos 9 USD de nuevo. Que la decida el CEO si la quiere.
4. **El banco y la cuota quedan libres** para las preguntas de expedientes, junto con el CTO 2.
