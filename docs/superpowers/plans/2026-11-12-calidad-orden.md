# Calidad de pruebas y recuperación · Índice del plan

- **Diseño:** `docs/superpowers/specs/2026-11-12-calidad-pruebas-recuperacion-design.md`, validado por el CEO (D-060).
- **Plan detallado de las fases 0 y 1:** `docs/superpowers/plans/2026-11-12-calidad-banco.md`.
- **Fases 2 a 5:** cada una tendrá su plan detallado cuando empiece. Dependen del trabajo del CTO 2 (bloques 2, 5 y 4) y de lo que diga el piloto del banco. Escribir hoy su código paso a paso sería adivinar.
- **Rama:** `feature/calidad-recuperacion`. Cada tarea va en su rama `task/calidad-recuperacion/<tarea>` y se mezcla con `--no-ff`.

## Reglas para todas las tareas

- **Subagentes:** Haiku por defecto. Sonnet solo si Haiku falla dos veces en la misma tarea. Opus no, porque nada de esto toca permisos ni aislamiento (D-055).
- **Cada tarea la revisa** un subagente distinto del que la hizo.
- **IA real:** no se llama a ninguna sin autorización de cuota del propietario (D-060). Las pruebas usan la IA simulada (`fake_claude.py`) y un Ollama simulado.
- **Coste de medir:** es el coste notificado de la suscripción, calculado con unos 0,006 USD por pregunta y buscador Haiku (lo ya medido). Lo que no se ha medido lo dice.
- **Choques:**
  - `medir/` y `pila/` no se tocan sin acuerdo con el CTO 2 (tarea T00);
  - la instrucción del MCP, `searchKnowledge`, `listDocuments` y `question_record` esperan a que sus bloques 2, 5 y 4 estén mezclados.
- **Al cerrar cada fase:**
  - entrada en el registro técnico (repositorio OCR, `feature/planificacion-tecnica`), con el siguiente número libre;
  - `encargos-cto.md` al día;
  - informe corto al CEO.

## Tareas

| Id | Tarea | Fase | Subagente | Cómo se verifica | Coste de medir | Depende de | Choque con el CTO 2 |
|---|---|---|---|---|---|---|---|
| T00 | Acordar con el CTO 2 tres cambios pequeños en `pila/` y `medir/ejecutar.py` | 0 | CTO 3 (mensaje) | Respuesta del CTO 2, apuntada en el plan | 0 | — | Es el acuerdo |
| T01 | Pila parametrizada: proyecto, puertos y carpeta de estado por variables, con los valores de hoy por defecto | 0 | Haiku | `test_medir` y `test_ejecutar` siguen en verde; `make banco-arriba` levanta `odin-banco` en 48080, sin tocar `odin-medicion` | 0 (Docker local) | T00 | `pila/` (acordado en T00) |
| T02 | Ejecutor: texto de enfoque, tope de turnos y resultados vistos en `consultas` | 0 | Haiku | Pruebas nuevas con la IA simulada; las 51 de hoy siguen en verde | 0 | T00 | `medir/ejecutar.py` (acordado en T00) |
| T03 | Banco: configuración TOML, validación, enfoques y rúbricas | 0 | Haiku | `test_config` | 0 | — | No |
| T04 | Banco: grupo de buscadores con caché y tope de gasto | 0 | Haiku | `test_pool` con la IA simulada: caché, tope, cuota agotada, sin cachear errores | 0 | T02, T03 | No |
| T05 | Banco: combinadores sin IA (`single`, `majorityVote`) y fusión de páginas (RRF) | 0 | Haiku | `test_combine` | 0 | T04 | No |
| T06 | Banco: redactor (`composer`, `pageFusion`) y jueces (Haiku y Ollama) | 0 | Haiku | `test_judge` y `test_compose` con IA simulada y Ollama simulado | 0 | T04, T05 | No |
| T07 | Banco: estadística (Wilson, kappa, aceptaciones falsas y PPI) | 0 | Haiku | `test_stats`, con Wilson de 201 de 216 = (0,8886; 0,9575) | 0 | — | No |
| T08 | Banco: submuestreo, curvas y comparación justa | 0 | Haiku | `test_subsample` | 0 | T05, T07 | No |
| T09 | Banco: orden `banco.py`, informe, `make`, `.gitignore` e `INFORME.md` | 0 | Haiku | `make banco-test` en verde; un experimento de punta a punta con la IA simulada | 0 | T03 a T08 | `Makefile` (fichero compartido: resuelve quien mezcle segundo) |
| T10 | Revisión del banco completo | 0 | Subagente revisor distinto | Lista de hallazgos resuelta | 0 | T09 | No |
| T11 | Piloto: 5 preguntas, 20 buscadores (10 `main`, 5 `topic` y 5 `synonyms`), `composer` y un jurado de 3 Haiku y 2 `qwen3.5:9b` | 1 | CTO 3 lo lanza | Informe con el coste real de buscadores, redactor y jueces | Unos 0,6 USD en buscadores; redactor y jueces, sin medir (es lo que mide) | T10 y **cuota del propietario** | No |
| T12 | Barrido de 40 preguntas: curvas por número de buscadores, mezcla y jueces, y calibración del jurado | 1 | CTO 3 lo lanza | Informe al CEO | Unos 4,8 USD en buscadores, más lo que mida T11 | T11 y visto bueno del CEO al coste | No |
| T13 | Capa ficticia ampliada: correos, hojas de cálculo en texto, documentos a medias y actas con erratas | 2 | Haiku | Pruebas de determinismo (misma semilla, mismo archivo) y anclas comprobadas | 0 | T10 | `generar/`: se reutiliza importándolo, sin tocarlo |
| T14 | Capa pública: lista, licencias, descarga autorizada (D-060), páginas y manifiesto | 2 | Haiku | SHA-256 de cada documento, fuente y licencia anotadas, comprobación de que no hay nombres de particulares; en git solo lo redistribuible | 0 (red autorizada para la lista de 3.2) | T10 | No |
| T15 | Capa alterada: las seis alteraciones de 3.3, con sus claves | 2 | Haiku | `test_alterado`: cada alteración, con lo que se espera de ella en `claves.json` | 0 | T13, T14 | No |
| T16 | Generadores de los tipos de pregunta nuevos, al menos 20 por tipo | 2 | Haiku | Anclas comprobadas; recuento por tipo | 0 | T13, T15 | `bateria.json`: las preguntas nuevas van en `banco/archivos/` |
| T17 | Preguntas de la capa pública validadas entre agentes (redactor, comprobador, programa y canal de desacuerdos) | 2 | Haiku (redactor y comprobador) | Porcentaje de acuerdo; desacuerdos decididos por el CEO | Sin medir: dos llamadas Haiku sin herramientas por pregunta, con tope `budgetUsd` | T14 y cuota | No |
| T18 | Batería de reserva: rama huérfana, `USOS.md`, regla en `CLAUDE.md` y el banco se niega a usarla fuera del modo de cierre | 2 | Haiku | `test_config` (negativa) y comprobación de que la rama no comparte historia | 0 | T16, T17 | No |
| T19 | Puntuador recalibrado, conjunto de oro y revisión de claves | 2 | Haiku; revisor distinto | Oro en verde; mediciones guardadas puntuadas otra vez, con la cifra de antes y la de después | 0 | Acuerdo con el CTO 2 tras su última medición de bloque | **`medir/puntuar.py`** |
| T20 | Modo de cierre: 3 repeticiones, regla del mínimo (la peor supera el 90 %), regresión y centinelas | 2 | Haiku | Pruebas con la IA simulada | 0 | T09, T19 | No |
| T21 | Línea base: 3 repeticiones con la batería de desarrollo | 2 | CTO 3 lo lanza | Informe | Unos 10 USD y 1 h 45 min | T20, bloques del CTO 2 mezclados y cuota | Se mide encima de sus bloques |
| T22 | Primera medición de la reserva | 2 | CTO que cierra la fase | `USOS.md` y resumen agregado | Unos 3,6 USD y 40 min | T18, T21 | No |
| T23 | Repetidor de consultas y clasificación de los «no consta» falsos | 3 | Haiku | Reproduce el diagnóstico del bloque 7 del CTO 2 | 0 | T02, T21 | No |
| T24 | «No consta» con rastro: `searched` y `hints` en `searchKnowledge`, la instrucción y la comprobación del rastro en el puntuador | 3 | Haiku (Sonnet si falla dos veces) | Pruebas del MCP; medición intermedia | Unos 0,4 USD por intento | T23 y bloque 2 mezclado | **`server.ts`, `searchKnowledge.ts` y `question_record` (bloque 4)** |
| T25 | Citar cada dato: la instrucción, y `checkAnswer` como estrategia del banco | 3 | Haiku | Medición intermedia con la cifra «limpia» | Unos 0,4 USD por intento | T19, T24 | **`server.ts`** |
| T26 | Listas completas: `listDocuments` con `query` (API y MCP), páginas por elemento en el puntuador y la instrucción | 3 | Haiku (Sonnet si falla dos veces) | Pruebas de API y MCP; medición intermedia | Unos 0,4 USD por intento | T19 y bloque 2 mezclado | **`listDocuments` y API de búsqueda** |
| T27 | Etiquetas de más: precisión frente a T-010, efecto en las listas y variantes solo si afectan | 3 | Haiku | Batería del etiquetado (local) | 0 (modelo local) | T21 | No |
| T28 | Reordenador y vectores: comprobar los disparadores con el repetidor | 3 | Haiku | Informe con los tres criterios de 7.5 | 0; descargar un reordenador necesita permiso | T23 | No |
| T29 | Sin las instrucciones del servidor (experimento del banco) | 3 | CTO 3 lo lanza | Informe | Unos 1,2 USD | T21 y cuota | No |
| T30 | Escaneados sintéticos (`make banco-escanear`), transcripción de la Gazeta entre dos agentes y medición por parejas | 4 | Haiku | CER y pérdida por OCR | Unos 1,1 USD | T14, T20 | No |
| T31 | Escaneados reales del propietario | 4 | Propietario y Haiku | Mismo procedimiento que T30 | Unos 0,5 USD | Propietario | No |
| T32 | Preguntas de administradores (`importar_preguntas.py`) | 4 | Haiku | Anclas comprobadas; reparto entre desarrollo y reserva | 0 | Entrevistas | No |
| T33 | Otras IAs: adaptador local de MCP a la API de OpenAI y de Google | 4 | Haiku | Pruebas con un proveedor simulado | Lo decide el propietario | Cuentas del propietario | No |
| T34 | Cierre: modo de cierre con las finalistas, muestra de Sonnet, informe y registro técnico | 5 | CTO 3 | Regla del mínimo y objetivos del apartado 8 | Unos 16 USD con un buscador; N veces más con N buscadores | Todo lo anterior | No |

## Lo que espera a otros

- **Propietario:**
  - cuota para T11 y siguientes;
  - escaneados reales (T31);
  - entrevistas (T32);
  - cuentas de otras IAs (T33);
  - permiso para descargar un reordenador, si se dispara T28.
- **CEO:**
  - desacuerdos de claves (T17 y T19);
  - visto bueno al coste del barrido (T12);
  - llevar a Odín `deepSearch` o `checkAnswer`, que vuelve al propietario (D-060).
- **CTO 2:**
  - acuerdo de T00 y de T19;
  - mezclar los bloques 2, 5 y 4 antes de T21 y de T24 a T26.
