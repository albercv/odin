# Plan de prueba: mejoras del expediente (D-093), con el formato de D-094

- **Fecha:** 2026-11-13. **Autor:** CTO 2. **Revisión:** el CEO o otro CTO, antes de pedir el sí (D-094, punto 2).
- **Estado:** aprobada por el propietario como medición de la semana (D-104, 2026-11-14). Tope dentro de la ejecución: **4,6 USD**, porque el CTO 3 mide después Valtierno con 0,4 y entre los dos no se pasa de 5. Se añaden las 2 preguntas del propietario (`bateria-conector.json`), una vez y solo informativas. Código: `feature/expediente-mejoras` en 37f7563 (5708afa más el arreglo del ejecutor), con el guion ajustado en esta rama.
- **Diseño y código:**
  - diseño en `docs/superpowers/specs/2026-11-13-expediente-mejoras-design.md`;
  - plan de implementación en `docs/superpowers/plans/2026-11-13-expediente-mejoras.md`.
- **Consumo:** los USD son consumo equivalente (precio de API, sin cobro).

## 1. Hipótesis (una sola)

> Con las tres mejoras de D-093, la peor de 3 ejecuciones en las 40 preguntas de expediente pasa del 90 %, sin citas inventadas ni fugas, y las 216 no empeoran.

**Se adopta si se cumplen las dos condiciones.** Cuentan los mismos criterios del puntuador que en la tarea 9 y en la base nueva.

| | Umbral que decide | Referencia medida |
|---|---|---|
| **Expediente (40)**, peor de 3 | **37 de 40 o más** (92,5 %; «pasa del 90 %» deja fuera 36, que es justo el 90 %) | Fase B de la tarea 9: 30, 34 y 32; la peor, 30 (75 %) |
| Expediente, citas no literales | **0**. Un título citado como texto de la página cuenta como inventada (D-050) | 1, en E016 |
| Expediente, fugas de aislamiento | **0** reales: un documento que quien pregunta no puede ver | 0 reales (la marca del puntuador en E037 era de un documento visible de otro proyecto) |
| **Las 216**, peor de 3 | **197 o más**: 202 menos 5 | Base nueva con todas las capacidades: 203 y 202 (la ejecución 2 quedó incompleta). El 5 es el ruido medido entre ejecuciones en el cierre de la fase 2 (de 204 a 209) |
| Las 216, fallos graves | **0** sustituidos presentados como vigentes, **0** citas no literales y **0** fugas | 0, 0 y 0 |

**Se descarta** si falla cualquiera de las filas. No se encadena otra prueba: lo que sugiera el resultado se anota en «Pendiente».

## 2. Qué se compara, y con qué

- **Mismo método que las referencias:** Haiku (`--modelo haiku`), pila con todas las capacidades (`cargar.py --marcar --todas-las-capacidades`) y el mismo puntuador.
- **Mismos datos** (en el repositorio, ficticios):
  - `experimentos/2026-11-12-archivo-ficticio/datos/bateria-expedientes.json`: 40 preguntas. Pila con expedientes, que se carga con `pila/cargar_expedientes.py --marcar-casos`.
  - `experimentos/2026-11-12-archivo-ficticio/datos/bateria.json`: 216 preguntas. Pila sin expedientes, como en la base nueva.
- **Lo único que cambia:** el commit de Odín.
  - Referencias: `feature/casos-expediente` en 0320222 (tarea 9) y en b522307 (base nueva).
  - Prueba: `feature/expediente-mejoras` en el commit de la sección 3.
- **El ejecutor** es el robusto de D-094 (1cfd36f, incluido en el commit de la prueba). Si una pregunta falla por infraestructura, queda como error y se reintenta; no cuenta como respuesta errónea.

## 3. Commit y datos

- **Commit:** `feature/expediente-mejoras` en `f5b4dd2`. Ahí están el código, con `make check` y `make demo` en verde y la revisión final aplicada, y la comprobación previa. Los commits posteriores solo cambian documentación.
- **Carpeta de resultados:** `experimentos/2026-11-12-archivo-ficticio/resultados/2026-11-NN-d093-*`, con su `ORIGEN.txt` (commit, modelo, batería y fecha).
- **Referencia de consumo (`REFERENCIA`):** va en `experimentos/2026-11-12-archivo-ficticio/estado/` (ya ignorada por git), por ejemplo `estado/referencia-d093.json`, para que la guarda de checkout limpio no la bloquee.

## 4. Comprobación previa, sin IA (obligatoria, D-094 punto 3)

Todo esto se hace antes de gastar nada. Si un paso falla, no se lanza.

1. **Código:**
   - el checkout está en `f5b4dd2` o en un commit posterior que solo cambie documentación, sin cambios (`git status` vacío);
   - en `medir/` y `pila/`, `python3 -m unittest` sale en verde. Esas pruebas incluyen los eventos mal formados y la excepción del ejecutor: el ejecutor no se para entero y distingue un error de una respuesta errónea.
2. **Pila y datos:**
   - `pila/pila.sh up`, la carga con todas las capacidades y, para las 40, `cargar_expedientes.py --marcar-casos`;
   - después, `medir/comprobar.py`, que se escribe en la tarea 6 del plan de implementación y no usa IA. Comprueba contra la pila:
     - que el trabajador tiene las capacidades `SEARCH_KNOWLEDGE`, `SIMILAR_CASES`, `CASE_FILES` y `SAVE_KNOWLEDGE_CARD`;
     - que el MCP ofrece las 15 herramientas y que `verifyCitation` pide `citations`;
     - que los documentos están cargados: el número de documentos y de expedientes del manifiesto;
     - que buscar el número de caso de E016 trae su ficha de caso con `caseDocument: true`;
     - que `verifyCitation` por lotes da `true` con una cita exacta y `false` con el título «Poliza seguro 2023».

### 4.1 Comprobación previa ya hecha contra la pila real (2026-11-13, sin IA)

- `medir/comprobar.py --expedientes` sale con 0, con la pila de medición cargada con expedientes:
  - capacidades y las 15 herramientas, con `citations` en `verifyCitation`;
  - 1.334 documentos READY y 20 fotos sin lector (PENDING_READER): la pila de medición no tiene OCR, igual que en las referencias;
  - 12 expedientes;
  - la ficha del caso de E016 sale con `caseDocument: true`;
  - el lote de citas da `[true, false]`, es decir, el título no se verifica.
- **Hallazgo:**
  - Ninguna de las 4 formas del número que usan las preguntas encuentra la ficha del caso de E016: «expte. 2024/530», «exp. 530/24», «su referencia 530» y «expediente nº 530».
  - La ficha dice «Caso 2024-530», y el analizador de PostgreSQL lo parte en '2024' y '-530', con el guion como signo. Así que «530» no coincide.
  - Buscando «Calle Pino 26 expediente 530» sale marcada la ficha de otro caso, el 2025/211.
  - La búsqueda tampoco mira los títulos.
  - Con esto, la mejora 1 no puede guiar a `getCaseFile` cuando la IA busca el caso por su número. Ver la sección 7.

## 5. Prueba de humo (D-094 punto 4)

- **5 preguntas de expediente, una ejecución:**
  - E007 (qué contiene), E010 (cronología) y E039 (compartidos): fallaron en las tres ejecuciones de la fase B;
  - E016: la del título citado;
  - E035: pertenencia, documentos que citan el mismo número.
- **Pasa si:**
  - las 5 terminan sin error;
  - al menos 3 llaman a `getCaseFile`;
  - al menos una llama a `verifyCitation` con 2 citas o más en un solo lote;
  - el consumo por pregunta no supera en más de 1,3 veces el de la fase B en esas mismas preguntas.
- **Si no pasa:** se para y se revisa, sin gastar más. No se lanza la tanda.
- **Consumo:** unos 0,06 USD (5 preguntas de análisis a la mediana medida de 0,0166, con margen).

## 6. Orden: primero lo barato (D-094 punto 5)

1. Las 40 de expediente, ejecución 1. Si saca menos de 37, la peor de 3 ya no puede llegar: **se para y se descarta**, sin gastar en el resto.
2. Las 40, ejecuciones 2 y 3, con la misma regla.
3. Las 216, ejecución 1. Si saca menos de 197, se para y se descarta.
4. Las 216, ejecuciones 2 y 3.

## 7. Consumo estimado y tope (D-094 punto 6)

Las cifras salen de lo medido con el mismo método.

| Tramo | Cálculo | Estimación |
|---|---|---|
| Humo | 5 preguntas | 0,06 |
| 40 de expediente × 3 | Fase B: 0,365, 0,361 y 0,396 por ejecución | 1,12 |
| 216 × 3 | Base nueva: 1,070 y 1,057 por ejecución | 3,21 |
| **Total** | | **4,39 USD** |

- **Tope: 4,75 USD acumulados.** El guion suma el consumo de cada fila y para la tanda al pasarlo. Una medición parada así queda incompleta y no decide nada.
- **Riesgo para la estimación:**
  - Las mejoras empujan a usar más `getCaseFile`, y una pregunta de expediente que lo usa cuesta más (mediana de 0,0166 frente a 0,0049 de una suelta).
  - Los lotes de `verifyCitation` bajan el número de llamadas: hoy la mediana es de 4 por pregunta, y hasta 23.
  - La prueba de humo mide el efecto neto antes de la tanda (sección 5).
- **Riesgo para la hipótesis (hallazgo de 4.1):**
  - Si la IA busca el caso por el número de la pregunta, no ve la ficha, así que tampoco ve `caseDocument: true`. Puede llegar a `getCaseFile` por `listDocuments` con `caseDocument`, como en la tarea 9, pero no por la mejora 1.
  - La prueba de humo lo detectaría si menos de 3 de las 5 preguntas usan `getCaseFile`, con un gasto de unos 0,06 USD.
  - **Recomendación técnica:** antes de gastar la medición de la semana, arreglar cómo se indexan los números con guion («2024-530»). Es otra hipótesis, que se comprueba sin IA: `comprobar.py` diría «la encuentran 4 de 4 formas», y la batería de búsqueda del CTO 3 lo mediría. La decisión es del propietario.
- **Tiempo:** la base nueva tardó 35 minutos en dos ejecuciones y media de las 216. Con eso, estimo unos 60 minutos para toda la prueba. Es una estimación, no una medición.

## 8. Al terminar: resumen de 5 líneas (D-094 punto 7)

1. Hipótesis y resultado: adoptada o descartada.
2. Expediente: peor de 3 (y las tres), citas no literales y fugas.
3. Las 216: peor de 3 (y las tres) frente a 202, y fallos graves.
4. Uso: preguntas con `getCaseFile` y llamadas a `verifyCitation` por pregunta, antes y después.
5. Consumo real frente a los 4,39 estimados, y lo que pasa a «Pendiente».

## 9. Fuera de esta prueba (Pendiente)

- Las 2 preguntas del propietario (`bateria-conector.json`): no tienen referencia en la base nueva.
- Sonnet.
- El expediente grande de Valtierno (CTO 3).
- Q153 con IA.
- Reanudar la ejecución 2 de la base nueva.
- Los números con guion en la búsqueda («2024-530»; sección 4.1).
- Que la búsqueda tenga en cuenta los títulos de los documentos.
