# Banco de pruebas multiagente (fases 0 y 1) · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo.** Un banco de pruebas que se levanta con una orden y hace esto:

- lanza N buscadores Haiku con enfoques distintos sobre el MCP de Odín;
- combina sus respuestas;
- las hace juzgar por M jueces (Haiku y un modelo local);
- y calcula, por submuestreo, curvas de acierto según el número de buscadores, la mezcla de enfoques y el número de jueces, con su coste y su latencia por respuesta.

**Arquitectura.**

- Carpeta `banco/` en la raíz de Odín. Son módulos pequeños, uno por responsabilidad:
  - configuración;
  - caché;
  - grupo de buscadores;
  - combinadores;
  - jueces;
  - estadística;
  - submuestreo;
  - informe;
  - y una orden de entrada.
- Reutiliza el ejecutor aislado (`medir/ejecutar.py`), el puntuador (`medir/puntuar.py`), la pila (`pila/`) y la IA simulada del experimento del archivo ficticio.
- **El buscador es la pieza básica.** El redactor es un buscador cuyo enfoque lleva las respuestas de los demás. Así se reutiliza el mismo aislamiento.

**Tecnología.** Python 3.11 o posterior, solo con la biblioteca estándar (`tomllib`, `unittest`, `concurrent.futures`, `urllib`, `http.server` para el Ollama simulado). Ollama local para los jueces de otra familia. `claude -p` con la suscripción.

**Diseño:** `docs/superpowers/specs/2026-11-12-calidad-pruebas-recuperacion-design.md` (D-060). **Índice:** `docs/superpowers/plans/2026-11-12-calidad-orden.md`.

## Restricciones globales

- **Dependencias:** solo la biblioteca estándar de Python 3.11 o posterior. Nada se instala, ni global ni en el proyecto.
- **Idiomas:**
  - identificadores en inglés, en `snake_case` (convención de Python);
  - las funciones que se reutilizan de `medir/` conservan su nombre;
  - documentación, mensajes y commits en español.
- **Métodos de 30 líneas como máximo.** Primero las pruebas (TDD).
- **Git:**
  - una rama `task/calidad-recuperacion/<tarea>` por tarea, que sale de `feature/calidad-recuperacion` y se mezcla con `--no-ff`;
  - sin push;
  - sin atribución a Claude en los commits.
- **Pila del banco:**
  - proyecto Compose `odin-banco`, con la API en 48080, el panel en 48088 y el MCP en 48090;
  - estado en `banco/estado/`;
  - nunca los puertos 8080, 8088, 8090, 18080, 18088, 18090, 28080, 28088, 28090, 38080, 38088 ni 38090, y nunca `deploy/.env`.
- **Sin IA real** en ninguna prueba ni ninguna tarea de la fase 0. T11 y T12 solo con la cuota autorizada por el propietario (D-060).
- **La reserva** (`battery = "reserva"`) solo vale con `mode = "closing"`. El banco se niega en cualquier otro caso.
- **Datos:** solo el archivo ficticio en estas fases. Nada de `private/`.
- **Cada experimento escribe:**
  - `ORIGEN.txt`, con la rama y el commit;
  - el coste notificado y los tokens de cada llamada.
- **Caché y estado** fuera de git: `banco/cache/` y `banco/estado/`.

## Puntos que ninguna prueba cubre si no se añade

1. **Gasto por encima del tope con concurrencia.** Al llegar a `budgetUsd` puede haber ejecuciones en marcha.
   - Lo esperado: no lanzar ninguna más, esperar a las que estén en marcha y anotar el exceso en `PARADA.txt`.
   - Prueba en T04: `test_budget_stops_new_launches`.
2. **Caché envenenada.** Una ejecución con `error` (tiempo agotado o cuota) no se guarda en caché, porque si no, las siguientes la reutilizarían como si fuera buena. Prueba en T04: `test_failed_run_is_not_cached`.
3. **Cuota agotada a mitad del grupo.** Se para todo, se escribe `PARADA.txt`, y al volver a lanzarlo se reanuda desde la caché. Prueba en T04: `test_quota_limit_stops_pool_and_resume_uses_cache`.
4. **Juez con salida inválida.** Ollama devuelve algo que no es JSON o le faltan campos.
   - Ese veredicto cuenta como inválido y no entra en la votación.
   - Si no queda ninguno válido, el resultado es «sin veredicto», no «incorrecta».
   - Prueba en T06: `test_invalid_verdicts_excluded_and_none_left_is_undecided`.
5. **Empate en `majorityVote`.** El resultado es «no consta», con el rastro de que los buscadores no coincidieron. No se elige al azar. Prueba en T05: `test_tie_returns_no_consta_with_trace`.

---

### Task 0 (T00): Acuerdo con el CTO 2

**Ficheros:** ninguno. Es un mensaje del CTO 3 al CTO 2 (`local_abe41379-e70a-4580-bc31-61f3f460f182`).

- [ ] **Paso 1.** Pedirle tres cambios pequeños, compatibles con lo que hay:
  1. `pila/pila.sh` y `pila/cargar.py` leen de variables de entorno, con los valores de hoy por defecto:
     - `PILA_PROYECTO` (`odin-medicion`);
     - `PILA_ESTADO` (`estado/`);
     - `ODIN_API_PORT` (28080), `ODIN_PANEL_PORT` (28088) y `ODIN_MCP_PORT` (28090).
  2. `medir/ejecutar.py`:
     - `responder(..., approach_text="")` mete el texto de enfoque entre el papel y la pregunta;
     - `construir_orden(..., max_turns=None)` añade `--max-turns`.
  3. `leer_consultas` añade a cada consulta `vistos`, una lista de `{"documentId", "pagina"}` o `{"cardId"}` sacada del resultado de `searchKnowledge`, `listDocuments` y `readPage`.
- [ ] **Paso 2.** Apuntar aquí su respuesta. Si prefiere hacerlo él, T01 y T02 pasan a ser suyas y el banco las espera. Si se niega, el banco usa sus propios envoltorios y lo apunta en el registro técnico.

### Task 1 (T01): Pila parametrizada

**Ficheros:**
- Modificar: `experimentos/2026-11-12-archivo-ficticio/pila/pila.sh`, `experimentos/2026-11-12-archivo-ficticio/pila/cargar.py`
- Prueba: `experimentos/2026-11-12-archivo-ficticio/medir/test_medir.py` (ampliar)

**Interfaces:**
- Produce: las variables de entorno de T00 (paso 1.1). Sin variables, se comporta igual que hoy.

- [ ] **Paso 1.** Prueba `test_pila_respects_env_overrides`: con `PILA_PROYECTO=odin-banco`, `ODIN_API_PORT=48080` y `PILA_ESTADO=<tmp>`, el `.env` generado tiene `ODIN_API_PORT=48080` y `ODIN_PANEL_ORIGIN=http://127.0.0.1:48088`, y se escribe en `<tmp>`. Usar `docker` simulado con un `PATH` falso.
- [ ] **Paso 2.** Prueba `test_cargar_uses_env_ports`: `cargar.API == "http://127.0.0.1:48080"` y `cargar.MCP == "http://127.0.0.1:48090/"` con esas variables.
- [ ] **Paso 3.** Ejecutar `cd experimentos/2026-11-12-archivo-ficticio/medir && python3 -m unittest`. Las pruebas nuevas tienen que fallar.
- [ ] **Paso 4.** Implementarlo. El texto «pila levantada en … (API :N…)» usa las variables.
- [ ] **Paso 5.** Volver a ejecutar las pruebas: todas en verde, las de hoy incluidas.
- [ ] **Paso 6.** Commit: `feat: pila del experimento con proyecto, puertos y estado configurables`.

### Task 2 (T02): Ejecutor con enfoque, tope de turnos y resultados vistos

**Ficheros:**
- Modificar: `experimentos/2026-11-12-archivo-ficticio/medir/ejecutar.py`
- Prueba: `experimentos/2026-11-12-archivo-ficticio/medir/test_ejecutar.py` (ampliar); ampliar `fake_claude.py` solo si el modo `consultas` no trae resultados con `documentId`.

**Interfaces:**
- Produce:
  - `responder(item, carga, args, ctemp, approach_text: str = "") -> dict`;
  - `construir_orden(claude_bin, modelo, cfg_mcp, max_turns: int | None = None) -> list[str]`;
  - cada entrada de `consultas` gana `vistos: list[dict]`.

- [ ] **Paso 1.** Pruebas:
  - `test_approach_text_goes_between_role_and_question`: el mensaje por la entrada estándar contiene `ROL`, luego el enfoque y luego `Pregunta:`, en ese orden;
  - `test_max_turns_flag`: `"--max-turns" in orden` y el valor siguiente es `"10"` cuando `max_turns=10`; ausente si es `None`;
  - `test_consultas_record_seen_ids`: con `FAKE_MODO=consultas`, la consulta de `searchKnowledge` trae `vistos` con los `documentId` y las páginas de sus `hits`.
- [ ] **Paso 2.** Ejecutar `python3 -m unittest test_ejecutar`: fallan las tres.
- [ ] **Paso 3.** Implementarlo. Se añade un argumento `--max-turns` a la orden; el enfoque llega desde quien llama.
- [ ] **Paso 4.** Ejecutar `python3 -m unittest`: todas en verde, también las 51 de hoy.
- [ ] **Paso 5.** Commit: `feat: el ejecutor admite enfoque y tope de turnos, y guarda los resultados vistos`.

### Task 3 (T03): Configuración del banco

**Ficheros:**
- Crear: `banco/config.py`, `banco/test_config.py`
- Crear los enfoques: `banco/enfoques/main.txt` (vacío), `topic.txt`, `synonyms.txt`, `filterFirst.txt`, `documentFirst.txt` y `skeptic.txt`, con el texto de la tabla del apartado 5.2 del diseño.
- Crear las rúbricas: `banco/rubricas/strict.txt`, `answerOnly.txt` y `sourceRules.txt` (5.4).
- Crear los ejemplos: `banco/configs/ejemplo.toml` (el del apartado 5.5 del diseño) y `banco/configs/piloto.toml` (T11).

**Interfaces:**
- Produce:

```python
@dataclass(frozen=True)
class SearcherSpec: approach: str; count: int; model: str
@dataclass(frozen=True)
class JudgeSpec: rubric: str; count: int; model: str          # model: "haiku" | "sonnet" | "ollama:<nombre>"
@dataclass(frozen=True)
class SampleSpec: size: int; strata: str; seed: int
@dataclass(frozen=True)
class CombinerSpec: kind: str; model: str | None               # single | majorityVote | pageFusion | composer
@dataclass(frozen=True)
class ExperimentConfig:
    name: str; archives: tuple[str, ...]; battery: str; mode: str; sample: SampleSpec | None
    repetitions: int; budget_usd: float; concurrency: int; max_turns: int | None
    searchers: tuple[SearcherSpec, ...]; combiner: CombinerSpec; judges: tuple[JudgeSpec, ...]; aggregation: str
class ConfigError(ValueError): ...
def load_config(path: Path, base_dir: Path) -> ExperimentConfig
```

- **Claves del TOML:** las del ejemplo de 5.5 (`budgetUsd`, `maxTurns`…).
- `judgeSelect` queda fuera de estas fases: devuelve `ConfigError`.

- [ ] **Paso 1.** Pruebas en `test_config.py`:
  - `test_loads_example`: `ejemplo.toml` da 3 especificaciones de buscador que suman 20, `budget_usd == 6.0` y `aggregation == "majority"`;
  - `test_reserve_requires_closing`: `battery="reserva"` con `mode="explore"` lanza `ConfigError` con «la reserva solo se usa en modo closing»;
  - `test_closing_forbids_sample`;
  - `test_unknown_approach_file`: un `approach="nada"` sin fichero lanza `ConfigError`;
  - `test_at_least_k_bounds`: `atLeast:6` con 5 jueces lanza `ConfigError`; `atLeast:3` vale;
  - `test_counts_and_budget_positive`;
  - `test_ollama_only_for_judges`.
- [ ] **Paso 2.** Ejecutar `python3 -m unittest banco.test_config`: fallan.
- [ ] **Paso 3.** Implementar `load_config`. Lee con `tomllib`, valida y convierte a las clases de arriba.
- [ ] **Paso 4.** Ejecutar las pruebas: en verde.
- [ ] **Paso 5.** Commit: `feat(banco): configuración TOML validada, enfoques y rúbricas`.

### Task 4 (T04): Grupo de buscadores con caché y tope de gasto

**Ficheros:**
- Crear: `banco/cache.py`, `banco/pool.py`, `banco/test_pool.py`

**Interfaces:**
- Consume: `responder` y `construir_orden` (T02), `ExperimentConfig` (T03) y `Aislamiento` de `ejecutar.py`.
- Produce:

```python
def run_key(question_id: str, approach: str, approach_text: str, model: str, replica: int, odin_commit: str) -> str  # sha256 hex
class RunCache:
    def __init__(self, root: Path): ...
    def get(self, key: str) -> dict | None: ...
    def put(self, key: str, record: dict) -> None: ...   # escritura atómica (temporal y rename); no guarda registros con "error"
@dataclass(frozen=True)
class PoolSettings: claude_bin: str; timeout: int; concurrency: int; budget_usd: float; max_turns: int | None; odin_commit: str
class BudgetExceeded(Exception): ...
def run_pool(items: list[dict], specs: tuple[SearcherSpec, ...], carga: dict, cache: RunCache,
             settings: PoolSettings, approaches_dir: Path, stop_file: Path) -> list[dict]
```

- Cada registro que devuelve `run_pool` es el de `responder` más `approach`, `replica` (0..count-1) y `cached: bool`.

- [ ] **Paso 1.** Pruebas, con `fake_claude.py`:
  - `test_runs_count_times_per_spec`: 2 preguntas con `main`×3 y `topic`×2 dan 10 registros;
  - `test_second_run_hits_cache`: la segunda vez son todos `cached` y el coste nuevo es 0;
  - `test_failed_run_is_not_cached`: con `FAKE_MODO=error`, `cache.get(key) is None`;
  - `test_budget_stops_new_launches`: con un tope menor que el coste de 2 ejecuciones y concurrencia 1, se lanzan como mucho 2, salta `BudgetExceeded` y `PARADA.txt` dice el gasto y el tope;
  - `test_quota_limit_stops_pool_and_resume_uses_cache`: con `FAKE_MODO=limite` en la tercera, se para y escribe `PARADA.txt`; al relanzar sin el límite, las dos primeras salen de la caché;
  - `test_isolation_breach_stops_everything`: con `FAKE_MODO=tools_extra`, salta `Aislamiento`;
  - `test_approach_text_changes_key`: cambiar el texto del enfoque cambia la clave de caché.
- [ ] **Paso 2.** Ejecutar `python3 -m unittest banco.test_pool`: fallan.
- [ ] **Paso 3.** Implementarlo. Usa `ThreadPoolExecutor(max_workers=concurrency)`. Antes de cada lanzamiento, comprueba el gasto acumulado (`coste_usd` de las ejecuciones no cacheadas).
- [ ] **Paso 4.** Ejecutar las pruebas: en verde.
- [ ] **Paso 5.** Commit: `feat(banco): grupo de buscadores con caché y tope de gasto`.

### Task 5 (T05): Combinadores sin IA y fusión de páginas

**Ficheros:**
- Crear: `banco/combine.py`, `banco/test_combine.py`

**Interfaces:**
- Consume: los registros de `run_pool` (T04), `normalise` y `TOKEN_NUM` de `puntuar.py`.
- Produce:

```python
def answer_signature(answer: dict) -> tuple[bool, frozenset[str]]   # (no_lo_se, cifras e identificadores normalizados de "respuesta")
def single(runs: list[dict]) -> dict                                 # la respuesta de la réplica 0 del primer buscador "main"
def majority_vote(runs: list[dict]) -> dict                         # la del grupo de firma más numeroso; dentro de él, la que más citas tenga
def rrf_pages(runs: list[dict], k: int = 60, top: int = 5) -> list[tuple[str, int]]  # (documentId, página)
```

- **El orden de cada buscador para RRF:** primero sus páginas citadas, en orden, y después sus `vistos` (T02). k = 60 es el valor habitual y se puede cambiar.

- [ ] **Paso 1.** Pruebas:
  - `test_signature_ignores_wording`: «La franquicia es de 300 €» y «300 euros de franquicia» tienen la misma firma;
  - `test_majority_picks_largest_group`;
  - `test_tie_returns_no_consta_with_trace`: con 2 frente a 2, `no_lo_se` es `True`, no hay citas y `respuesta` contiene «no coincidieron»;
  - `test_rrf_ranks_page_seen_by_more_runs_first`;
  - `test_rrf_ignores_runs_with_error`;
  - `test_single_requires_main`: sin `main`, `ValueError`.
- [ ] **Paso 2.** Ejecutar las pruebas: fallan.
- [ ] **Paso 3.** Implementarlo.
- [ ] **Paso 4.** Ejecutar las pruebas: en verde.
- [ ] **Paso 5.** Commit: `feat(banco): combinadores por voto y fusión de páginas`.

### Task 6 (T06): Redactor y jueces

**Ficheros:**
- Crear: `banco/compose.py`, `banco/judge.py`, `banco/test_compose.py`, `banco/test_judge.py`
- Crear las IA simuladas: `banco/fakes/fake_judge.py` (un `claude` sin MCP que devuelve un veredicto según `FAKE_VEREDICTO`) y `banco/fakes/fake_ollama.py` (un `http.server` con `/api/chat`, que responde según `FAKE_OLLAMA`: `ok`, `no_json` o `sin_campos`).

**Interfaces:**
- Consume: `responder` (T02), `rrf_pages` (T05), `read_pages` de `puntuar.py` e `ids.json` o `carga.json` para traducir `documentId` a ruta.
- Produce:

```python
def compose(item: dict, runs: list[dict], carga: dict, settings: PoolSettings, kind: str,
            pages: list[tuple[str, int]] | None = None) -> dict        # registro como el de responder, con approach="composer"|"pageFusion"
VERDICT_SCHEMA: dict   # {"correcta": bool, "respaldada": bool, "datosSinRespaldo": [str], "motivo": str}
def judge_material(item: dict, answer: dict, page_text: Callable[[str, int], str], rng: random.Random) -> str
def run_claude_judge(material: str, rubric_text: str, model: str, claude_bin: str, timeout: int) -> dict
def run_ollama_judge(material: str, rubric_text: str, model: str, url: str, timeout: int) -> dict
def aggregate(verdicts: list[dict], rule: str) -> bool | None    # None = sin veredicto
```

- **El redactor** es un buscador cuyo `approach_text` lleva las respuestas de los demás (`composer`) o las páginas de RRF (`pageFusion`), con la orden de citar solo lo que compruebe con `verifyCitation`.
- **Aislamiento de los jueces Haiku:** `--restricted`, `--tools ""`, `--disable-slash-commands`, `--strict-mcp-config` sin servidores y `--json-schema VERDICT_SCHEMA`. El evento de inicio no puede traer ninguna herramienta salvo `StructuredOutput`, ni ningún MCP.
- **Ollama:** `POST /api/chat` con `stream: false` y `format: VERDICT_SCHEMA`, en `http://127.0.0.1:11434` por defecto.
- **Un veredicto inválido** se guarda como `{"invalido": true, "motivo": ...}`.

- [ ] **Paso 1.** Pruebas:
  - `test_composer_prompt_contains_all_answers_and_rule`;
  - `test_page_fusion_prompt_contains_rrf_pages_only`;
  - `test_judge_material_shuffles_pages_with_seed`: dos semillas dan dos órdenes distintos y la misma semilla, el mismo;
  - `test_judge_material_has_no_key_and_no_config_name`: el texto no contiene `claves`, ni `ancla`, ni el nombre del experimento;
  - `test_claude_judge_isolation`: con un inicio que trae `Bash`, salta `Aislamiento`;
  - `test_ollama_judge_ok_and_invalid`: con `ok` devuelve el veredicto; con `no_json` o `sin_campos`, `invalido`;
  - `test_invalid_verdicts_excluded_and_none_left_is_undecided`: `aggregate([inválido, inválido], "majority") is None`;
  - `test_aggregate_rules`: `majority` con 3 sí y 2 no da `True`; `unanimous` con un no da `False`; `atLeast:4` con 3 sí da `False`.
- [ ] **Paso 2.** Ejecutar las pruebas: fallan.
- [ ] **Paso 3.** Implementarlo.
- [ ] **Paso 4.** Ejecutar las pruebas: en verde.
- [ ] **Paso 5.** Commit: `feat(banco): redactor y jueces de Haiku y de Ollama`.

### Task 7 (T07): Estadística

**Ficheros:**
- Crear: `banco/stats.py`, `banco/test_stats.py`

**Interfaces:**

```python
def wilson(successes: int, n: int, z: float = 1.96) -> tuple[float, float]
def cohen_kappa(a: list[bool], b: list[bool]) -> float          # con acuerdo esperado 1: 1.0 si coinciden en todo, 0.0 si no
def false_accept_rate(judge: list[bool], script: list[bool]) -> float   # juez sí y script no, sobre las que el script da por malas
def false_reject_rate(judge: list[bool], script: list[bool]) -> float
def ppi_mean(judge_unlabeled: list[bool], judge_labeled: list[bool], truth_labeled: list[bool],
             z: float = 1.96) -> tuple[float, float, float]     # (estimación, mínimo, máximo)
```

- **PPI:** la estimación es la media del juez en las preguntas sin etiquetar, más la media de (verdad − juez) en las etiquetadas. La varianza es la suma de las dos varianzas, cada una dividida por su tamaño.

- [ ] **Paso 1.** Pruebas:
  - `test_wilson_201_of_216`: `(0.8886, 0.9575)`, con 4 decimales;
  - `test_wilson_edges`: n = 0 lanza `ValueError`; 0 de n da un mínimo de 0;
  - `test_kappa_perfect_and_constant`;
  - `test_kappa_known_value`: `a=[T,T,F,F]` y `b=[T,F,F,F]` dan 0,5;
  - `test_false_accept_rate`;
  - `test_ppi_corrects_biased_judge`: un juez que dice siempre `True`, con etiquetas al 80 %, da una estimación de 0,8.
- [ ] **Paso 2.** Ejecutar las pruebas: fallan.
- [ ] **Paso 3.** Implementarlo.
- [ ] **Paso 4.** Ejecutar las pruebas: en verde.
- [ ] **Paso 5.** Commit: `feat(banco): intervalos, acuerdo entre jueces y PPI`.

### Task 8 (T08): Submuestreo, curvas y comparación justa

**Ficheros:**
- Crear: `banco/subsample.py`, `banco/test_subsample.py`

**Interfaces:**
- Consume: los registros de `run_pool`, `single` y `majority_vote` (T05), `wilson` (T07) y una función de puntuar `score(item, answer) -> bool`.
- Produce:

```python
def draw_subsets(runs_by_question: dict[str, list[dict]], mix: dict[str, int], draws: int, seed: int) -> list[dict[str, list[dict]]]
def curve(runs_by_question, items, score, combiner: Callable[[list[dict]], dict],
          mixes: list[dict[str, int]], draws: int, seed: int) -> list[dict]
# cada fila: {"mix", "n", "accuracy_mean", "accuracy_min", "accuracy_max", "tokens_mean", "cost_mean", "latency_p50", "latency_p95"}
def latency_of(subset: list[dict], extra_s: float = 0.0) -> float   # el buscador más lento del subconjunto, más el redactor y los jueces
def beats_single_frontier(row: dict, single_rows: list[dict], variation: float) -> bool
```

- **Tokens:** entrada + salida + caché leída + caché escrita. Se guardan también por clase.
- **La frontera de `single`:** las filas de `single` con `max_turns` 10, 20 y 40, interpoladas en línea recta por `tokens_mean`.

- [ ] **Paso 1.** Pruebas:
  - `test_draws_respect_mix_and_seed`;
  - `test_draw_fails_when_pool_too_small`: pedir 6 `topic` con 5 en el grupo lanza `ValueError`;
  - `test_curve_rows_have_all_fields`;
  - `test_latency_is_max_plus_extra`;
  - `test_beats_frontier_needs_margin_over_variation`: con un acierto mayor que el de la frontera pero dentro de la variación, `False`.
- [ ] **Paso 2.** Ejecutar las pruebas: fallan.
- [ ] **Paso 3.** Implementarlo.
- [ ] **Paso 4.** Ejecutar las pruebas: en verde.
- [ ] **Paso 5.** Commit: `feat(banco): curvas por submuestreo y comparación con un solo buscador`.

### Task 9 (T09): Orden de entrada, informe y `make`

**Ficheros:**
- Crear: `banco/banco.py`, `banco/report.py`, `banco/test_banco.py`, `banco/INFORME.md` (cómo se usa, siguiendo el estilo de `medir/INFORME.md`)
- Modificar: `Makefile` (objetivos de 5.1), `.gitignore` (`banco/cache/` y `banco/estado/`)

**Interfaces:**
- Consume: todo lo anterior.
- Produce:
  - `python3 banco/banco.py run --config X` y `python3 banco/banco.py sweep --config X`: escriben `banco/resultados/<fecha>-<nombre>/` con `ORIGEN.txt`, `buscadores.jsonl`, `final.jsonl`, `veredictos.jsonl`, `resumen.json` e `informe.md`;
  - `python3 banco/banco.py report --results DIR`: vuelve a escribir el informe sin llamar a la IA;
  - `write_report(out_dir: Path, summary: dict) -> None` en `report.py`.
- **El informe en español** lleva lo de 5.6:
  - acierto del script con su intervalo;
  - fallos graves;
  - recuperación y acierto por enfoque;
  - si el acuerdo entre buscadores predice el acierto (acierto según cuántos buscadores coinciden);
  - jueces: kappa, aceptaciones falsas, rechazos falsos y PPI;
  - tokens, coste y latencia (percentiles 50 y 95).
- **`make`:**
  - `banco-arriba`, `banco-cargar`, `banco-abajo`: llaman a `pila.sh` y `cargar.py` con `PILA_PROYECTO=odin-banco`, los puertos 48080, 48088 y 48090, y `PILA_ESTADO=banco/estado`;
  - `banco`, `banco-barrido`, `banco-informe`, `banco-test`.

- [ ] **Paso 1.** Prueba `test_end_to_end_with_fakes`: un experimento de 3 preguntas del archivo de prueba con `fake_claude.py`, `fake_judge.py` y `fake_ollama.py`.
  - Escribe los seis ficheros;
  - `resumen.json` tiene `accuracy`, `judges.kappa`, `judges.falseAcceptRate`, `latency.p95` y `cost.notifiedUsd`;
  - `informe.md` contiene «Acierto» y «Jueces».
- [ ] **Paso 2.** Prueba `test_report_rebuild_without_ai`: `report` sobre esa carpeta no lanza ningún proceso (`subprocess.run` simulado que falla si se llama).
- [ ] **Paso 3.** Ejecutar las pruebas: fallan.
- [ ] **Paso 4.** Implementarlo.
- [ ] **Paso 5.** Ejecutar `make banco-test`: todas las pruebas de `banco/` y las de `medir/` en verde.
- [ ] **Paso 6.** Ejecutar `make banco-arriba && make banco-cargar ARCHIVOS=ficticio && make banco-abajo`: la pila sube en 48080 y baja sin tocar `odin-medicion` (`docker compose ls`). Es solo Docker local, sin IA.
- [ ] **Paso 7.** Commit: `feat(banco): orden de entrada, informe y objetivos de make`.

### Task 10 (T10): Revisión del banco

- [ ] **Paso 1.** Un subagente que no haya escrito nada del banco revisa la rama contra este plan y el diseño. Mira sobre todo:
  - los cinco puntos del principio;
  - que no haya duplicados de `medir/`;
  - el límite de 30 líneas por método;
  - y que la reserva no se pueda usar en modo `explore`.
- [ ] **Paso 2.** Resolver los hallazgos, cada uno con su prueba.
- [ ] **Paso 3.** Mezclar en `feature/calidad-recuperacion` con `--no-ff`. Registro técnico: entrada con la arquitectura del banco y las alternativas descartadas (un banco dentro de `medir/`; LangChain y RAGAS, descartados porque añaden dependencias).

### Task 11 (T11): Piloto (solo con la cuota autorizada)

- [ ] **Paso 1.** Confirmar que el CEO ha transmitido la autorización de cuota del propietario.
- [ ] **Paso 2.** `banco/configs/piloto.toml`:
  - 5 preguntas estratificadas, con semilla 7;
  - 10 `main`, 5 `topic` y 5 `synonyms`;
  - `composer` con Haiku;
  - jueces: 3 `strict` con Haiku y 2 `strict` con `ollama:qwen3.5:9b`;
  - `budgetUsd = 1.5`.
- [ ] **Paso 3.** Ejecutar `make banco-arriba && make banco-cargar ARCHIVOS=ficticio && make banco CONFIG=banco/configs/piloto.toml; make banco-abajo`.
- [ ] **Paso 4.** Comprobar en `informe.md`:
  - el coste notificado por buscador, por redactor y por juez;
  - la latencia;
  - que ningún inicio rompió el aislamiento.
- [ ] **Paso 5.** Informe corto al CEO con el coste medido y lo que costaría el barrido de 40 preguntas.

### Task 12 (T12): Barrido de 40 preguntas (con el visto bueno del CEO al coste)

- [ ] **Paso 1.** `banco/configs/barrido-40.toml`:
  - la misma mezcla que el piloto, con 40 preguntas;
  - combinadores `single`, `majorityVote` y `composer`;
  - `single` con `maxTurns` 10, 20 y 40 para la frontera;
  - jurado: 3 Haiku y 2 Ollama;
  - el `budgetUsd` que apruebe el CEO.
- [ ] **Paso 2.** `make banco-barrido CONFIG=banco/configs/barrido-40.toml`.
- [ ] **Paso 3.** Informe al CEO:
  - curvas de acierto según el número de buscadores (1, 3, 5, 10 y 20) y la mezcla;
  - qué combinación queda por encima de la frontera de `single`;
  - calibración del jurado (kappa, aceptaciones falsas y PPI);
  - coste y latencia por respuesta frente al umbral de D-060 (como mucho 10 s más en la mediana y 0,02 USD más).
