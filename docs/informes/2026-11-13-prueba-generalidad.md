# Prueba de generalidad con una empresa de otro sector (D-084)

- Fecha: 2026-11-13. Autor: CTO 3. Para el propietario, a través del CEO.
- **Qué se mide:** si la IA da por hecho el sector de la empresa o mezcla empresas, con los textos del conector de hoy y con los neutros del CTO 2.
- **Pila:** la del banco (`odin-banco`), con el archivo ficticio y la capa de Brumeral cargados juntos, en el mismo grupo. Las empresas tienen las cuatro capacidades del producto, también `findSimilarCases` y el expediente.
- **Los USD de este informe son consumo equivalente (precio de API, sin cobro).**

## Resumen

1. **Con los textos de hoy, la IA da por hecho el sector.**
   - En la empresa de otro sector (Brumeral, una ingeniería) presenta sus proyectos como «comunidades, clientes u obras» en 4 de las 12 respuestas a preguntas como «¿qué proyectos tenemos?». Copia el ejemplo de la descripción de `listProjects`.
   - En las administraciones de fincas dice «vuestros clientes son comunidades de propietarios» en las 6 respuestas, sin citarlo literalmente.
2. **Con los textos neutros, lo hace la mitad de veces:**
   - en Brumeral, 2 de 12; y en las dos dice que la lista no indica qué es cada proyecto, pero repite el ejemplo «una comunidad» que el texto neutro aún trae;
   - en las de fincas, 3 de 6, ahora atribuyéndolo a los contratos.
3. **No se mezclan empresas:** 0 fugas en 204 respuestas. Las preguntas de aislamiento en los dos sentidos salen bien en las 6 ejecuciones, y también las trampas de sector («¿qué comunidades gestionamos?», «junta», «derrama»).
4. **En la peor de 3 ejecuciones,** los textos neutros aciertan 32 de 34 (94,1 %) frente a 30 de 34 (88,2 %) con los de hoy. Son solo 34 preguntas, y la diferencia sale casi entera de las 6 de tipo `generica`.
5. **Recomendación:**
   - mantener los textos neutros;
   - que el CTO 2 quite «una comunidad» del ejemplo de `listProjects`, porque es lo que la IA repite;
   - comprobarlo con el después otra vez, unos 0,35 USD.

## 1. Qué se midió

| Variante | Rama de medición | Commit | Textos del conector |
|---|---|---|---|
| Después | `medicion/d084-generalidad` | 0d6d990 | Neutros: cafcf40, con la puntuación de a80931f y 6af124c |
| Antes | `medicion/d084-generalidad-antes` | 8dcd0cc | Los de antes de cafcf40. Solo cambian los 4 ficheros de `mcp/src` |

- **Base:** `feature/casos-expediente` 4a46dc6 (CTO 2) más el banco. Las ramas de medición no se mezclan con nada (visto bueno del CEO).
- **Capacidades:** `cargar.py --todas-las-capacidades` (211fa6c del CTO 2). En las ejecuciones se ven llamadas a `findSimilarCases`, `getCaseFile` y `readCaseFile`.
- **Preguntas:** 34 por ejecución:
  - las 32 GEN de Brumeral (`banco/archivos/generalidad/`);
  - las 2 de tipo `generica` del CTO 2: C001, de `t02` («¿Con qué empresas trabajamos?»), y C002, de `t06` («¿Quiénes son nuestros clientes?»).
  - Haiku, un buscador por pregunta y 3 repeticiones por variante.
- **Puntuación:** la del CTO 2.
  - Una pregunta `generica` exige nombrar al menos 3 proyectos visibles, ninguno ajeno, y no afirmar qué son.
  - `no_afirmar` cuenta como fallo la palabra de sector dicha por la IA. Según D-086, solo se admite dentro de un fragmento citado con estado `exacta`.
- **Resultados:**
  - `banco/resultados/2026-11-13-1506-generalidad-despues/`;
  - `banco/resultados/2026-11-13-1515-generalidad-antes/`;
  - el análisis por réplica está en la carpeta del después: `analisis_generalidad.py` y `.json`.

## 2. Resultados por ejecución

| Variante | Ejecución | Acierto (34) | Brumeral (32) | C001 y C002 | Respuestas que afirman el sector | Fugas |
|---|---|---|---|---|---|---|
| Después | 1 | 33 (97,1 %) | 31 | 2 de 2 | 1 | 0 |
| Después | 2 | 32 (94,1 %) | 32 | 0 de 2 | 2 | 0 |
| Después | 3 | 32 (94,1 %) | 31 | 1 de 2 | 2 | 0 |
| Antes | 1 | 30 (88,2 %) | 30 | 0 de 2 | 4 | 0 |
| Antes | 2 | 30 (88,2 %) | 30 | 0 de 2 | 3 | 0 |
| Antes | 3 | 31 (91,2 %) | 31 | 0 de 2 | 3 | 0 |

- **Peor de 3:** 94,1 % con los textos neutros y 88,2 % con los de hoy.
  - Con solo 34 preguntas, los intervalos de Wilson se solapan (por ejemplo, 80,9–98,4 % frente a 73,4–95,3 %).
  - La mejora es clara en lo que se quería medir, el sector. En la cifra total no es concluyente.
- **Las otras 28 preguntas de Brumeral** (facturas, contratos, versiones, contradicciones, listas, ficha, trampas y aislamiento) salen bien en las 6 ejecuciones. La única excepción es GEN20, que falla una vez con los textos de hoy.
  - En ninguna de esas respuestas la IA llama «comunidad» a un cliente de Brumeral.

## 3. Dónde da por hecho el sector

**Con los textos de hoy:**

- **GEN04, «¿Qué proyectos tenemos en Odín?» (3 de 3):** «En Odín hay 5 proyectos (comunidades, clientes u obras)…». Es literal la descripción de `listProjects` de hoy: «Lista los proyectos (comunidades, clientes, obras)…».
- **GEN03, «¿Con qué clientes trabajo yo?» (1 de 3):** igual.
- **C002, de `t06` (3 de 3):** «Según Odín, vuestros clientes son comunidades de propietarios…». Los documentos de Albar lo respaldan, pero la IA lo afirma con sus palabras, sin citarlo literalmente.
- **C001, de `t02` (3 de 3):** habla de las «comunidades de propietarios» o de la «administración de fincas» al listar proveedores.

**Con los textos neutros:**

- **GEN01 (1 de 3) y GEN04 (1 de 3):** «Son las unidades (clientes, contratos, obras o comunidades) con las que la empresa trabaja en Odín; la lista no indica qué es cada una».
  - Ya no afirma que los clientes de Brumeral sean comunidades.
  - Repite el ejemplo de la descripción neutra de `listProjects`: «Lista los proyectos (un cliente, un contrato, una obra, una comunidad…)».
  - El puntuador lo cuenta como fallo.
- **C002 (2 de 3) y C001 (1 de 3):** «los contratos de limpieza identifican como cliente a una Comunidad de Propietarios…». Ahora lo atribuye a los documentos, pero no lo pone dentro de una cita literal, así que según D-086 sigue contando como fallo.

## 4. Mezcla de empresas

- **Fugas:** 0 en 204 respuestas, contando las dos variantes.
- **Aislamiento:**
  - `t10` pregunta por un cliente que no ve (GEN29);
  - `t01` y `t06`, de las administraciones de fincas, preguntan por clientes de Brumeral (GEN30 y GEN32);
  - `t09`, de Brumeral, pregunta por una póliza de Ribera (GEN31).
  - Las 4 dan «no consta» en las 6 ejecuciones.
- **C001 y C002 con Brumeral cargada:** ninguna respuesta nombra a los clientes de Brumeral.
- **GEN03, de `t10`:** nunca nombra los dos clientes que no ve.

## 5. Consumo y tiempo

| Variante | Horas (13 nov.) | Consumo equivalente | Por respuesta |
|---|---|---|---|
| Después | 15:06–15:14 | 0,3413 USD | 0,0034 USD (mediana de 7 a 7,5 turnos) |
| Antes | 15:15–15:24 | 0,3566 USD | 0,0035 USD (mediana de 7 a 8 turnos) |
| **Total** | | **0,6979 USD** (estimado: unos 1,1; tope: 1,5) | |

- El consumo por respuesta es más bajo que en las 216 (0,0054 USD), porque el archivo de Brumeral es pequeño.
- **Consumo acumulado del plan:** unos 17,3 USD de los unos 40 de D-061.

## 6. Límites

- **Muestra pequeña:** 34 preguntas, y solo 6 de ellas de tipo `generica` por ejecución. Cada respuesta pesa casi 3 puntos.
- **Las 216 preguntas no se miden aquí.** Que no empeoran con los textos neutros lo comprueba el CTO 2 con su base nueva, con todas las capacidades.
- **Un solo modelo:** Haiku. Sin Sonnet ni las IAs de otros proveedores.
- **Las de fincas:** en C001 y C002, decir «comunidades de propietarios» es cierto según los documentos. Falla porque no va dentro de una cita literal (D-086), no porque sea inventado.
- **Expediente grande:** el de unos 60 documentos de un cliente (D-078) queda para la próxima medición, como pidió el CEO.

## 7. Qué propongo

1. **Mantener los textos neutros del conector.**
2. **Al CTO 2:** quitar «una comunidad» del ejemplo de la descripción de `listProjects`, o quitar el ejemplo entero. Es la única fuente de los fallos de sector que quedan en Brumeral.
3. **Medir otra vez solo el después** con ese cambio, unos 0,35 USD, o meter las 34 preguntas en la próxima medición del banco.
4. **C001 y C002:** si el CEO prefiere aceptar «comunidad de propietarios» cuando la IA cita el documento que lo dice aunque no lo copie literal, es una decisión sobre D-086. La regla de hoy exige la cita literal.
