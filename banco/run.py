"""Órdenes run y sweep: preparan la carpeta, lanzan buscadores, respuesta final y jueces bajo un mismo tope, y escriben la salida."""

import hashlib
import json
import random
import subprocess
import tomllib
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, replace
from datetime import datetime
from pathlib import Path

from banco import combine, judge, report, scoring
from banco.cache import RunCache
from banco.compose import compose
from banco.config import ConfigError, SearcherSpec, load_config
from banco.layout import Layout
from banco.ledger import Ledger, open_ledger
from banco.medir_path import carga_mod, ejecutar
from banco.pool import BudgetExceeded, PoolSettings, PoolStopped, _build_jobs, run_pool
from banco.subsample import beats_single_frontier, curve

ANSWER_RULES = {"single": combine.single, "majorityVote": combine.majority_vote}
AI_KINDS = ("composer", "pageFusion")
DEFAULT_DRAWS = 20
DEFAULT_SINGLE_TURNS = [10, 20, 40]
BUDGET = "budget"  # motivo interno: el presupuesto se agotó antes de lanzar un juez de pago
NO_CONSTA_MOTIVO = "no juzgable: no consta"


@dataclass(frozen=True)
class SweepSpec:
    mixes: tuple
    draws: int
    single_max_turns: tuple


@dataclass
class Session:
    layout: Layout
    cfg: object
    items: list
    stack: dict
    ctx: object
    settings: PoolSettings
    folder: Path
    cache: RunCache
    ledger: Ledger
    sweep: SweepSpec | None = None

    @property
    def stop_file(self):
        return self.folder / "PARADA.txt"

    @property
    def seed(self):
        return self.cfg.sample.seed if self.cfg.sample else 0


# ---------- preparación ----------
def _check_supported(cfg):
    for archive in cfg.archives:
        if archive != "ficticio":
            raise ConfigError(f"capa no disponible todavía: {archive}")
    if cfg.battery != "desarrollo":
        raise ConfigError(f"la batería «{cfg.battery}» no existe todavía: en esta fase solo está «desarrollo»")


def _check_main(cfg, sweep):
    approaches = {s.approach for s in cfg.searchers}
    if (cfg.combiner.kind == "single" or sweep) and "main" not in approaches:
        raise ConfigError("hace falta un buscador «main» para el combinador single y para el barrido")


def _check_mix(mix, available):
    if not isinstance(mix, dict) or not mix:
        raise ConfigError("cada mezcla de [sweep] es una tabla enfoque = número")
    for approach, count in mix.items():
        limit = available.get(approach, 0)
        if isinstance(count, bool) or not isinstance(count, int) or not 1 <= count <= limit:
            raise ConfigError(f"mezcla {mix}: «{approach}» necesita entre 1 y {limit} ejecuciones")


def load_sweep(path, cfg):
    raw = tomllib.loads(Path(path).read_text(encoding="utf-8")).get("sweep")
    if not isinstance(raw, dict) or not isinstance(raw.get("mixes"), list) or not raw["mixes"]:
        raise ConfigError("el barrido necesita [sweep] con «mixes», una lista de tablas enfoque = número")
    available = Counter()
    for searcher in cfg.searchers:
        available[searcher.approach] += searcher.count
    for mix in raw["mixes"]:
        _check_mix(mix, available)
    draws = raw.get("draws", DEFAULT_DRAWS)
    turns = raw.get("singleMaxTurns", DEFAULT_SINGLE_TURNS)
    if isinstance(draws, bool) or not isinstance(draws, int) or draws < 1:
        raise ConfigError("«draws» en [sweep] debe ser un entero mayor que 0")
    if not isinstance(turns, list) or not turns or not all(isinstance(t, int) and t > 0 for t in turns):
        raise ConfigError("«singleMaxTurns» en [sweep] debe ser una lista de enteros mayores que 0")
    return SweepSpec(tuple(raw["mixes"]), draws, tuple(turns))


def _load_stack(layout):
    path = layout.state_dir / "carga.json"
    if not path.exists():
        raise ConfigError(f"no hay carga en {path}: ejecuta antes «make banco-cargar»")
    return carga_mod.leer(path)


def draw_sample(items, strata, size, seed):
    """Estratos por el campo `strata`, en orden alfabético; cada estrato se baraja y se reparte por turnos."""
    rng = random.Random(seed)
    groups = {}
    for item in items:
        groups.setdefault(item[strata], []).append(item)
    lists = [groups[key] for key in sorted(groups)]
    for group in lists:
        rng.shuffle(group)
    picked = [group[i] for i in range(max(map(len, lists), default=0)) for group in lists if i < len(group)]
    return picked[:size]


def _select_items(layout, cfg):
    items = json.loads(layout.battery_file.read_text(encoding="utf-8"))
    if cfg.sample is None:
        return items
    return draw_sample(items, cfg.sample.strata, cfg.sample.size, cfg.sample.seed)


CODE_PATHS = ("banco", "experimentos/2026-11-12-archivo-ficticio/medir")


def code_fingerprint(repo):
    """Huella del código para la caché: HEAD y, si hay cambios sin commit en banco y medir, un hash de esos cambios."""
    def run_git(*args):
        return subprocess.run(["git", *args], cwd=repo, capture_output=True, text=True, check=True).stdout
    head = run_git("rev-parse", "HEAD").strip()
    branch = run_git("rev-parse", "--abbrev-ref", "HEAD").strip()
    changes = run_git("diff", "HEAD", "--", *CODE_PATHS)
    if changes:
        head += "+" + hashlib.sha256(changes.encode("utf-8")).hexdigest()[:16]
    return head, branch


def stack_digest(stack):
    """Huella de los mapas de ids (documentos y fichas) de la pila: recargarla cambia los ids y la caché no se reutiliza."""
    ids = {"doc_ruta": stack["doc_ruta"], "ficha_f": stack["ficha_f"]}
    return hashlib.sha256(json.dumps(ids, sort_keys=True).encode("utf-8")).hexdigest()


def _new_folder(results_dir, name):
    stamp = datetime.now().strftime("%Y-%m-%d-%H%M")
    folder = results_dir / f"{stamp}-{name}"
    n = 2
    while folder.exists():
        folder = results_dir / f"{stamp}-{name}-{n}"
        n += 1
    folder.mkdir(parents=True)
    return folder


def _write_start(sess, branch, commit):
    """ORIGEN.txt (para el informe) e ids.json (documentos y fichas, sin claves de API)."""
    cfg = sess.cfg
    sample_text = (f"{cfg.sample.size} (estrato {cfg.sample.strata}, semilla {cfg.sample.seed})" if cfg.sample
                   else "batería entera")
    origin = {"Configuración": cfg.name, "Rama": branch, "Commit": commit, "Modo": cfg.mode, "Batería": cfg.battery,
              "Archivos": ", ".join(cfg.archives), "Muestra": sample_text, "Preguntas": str(len(sess.items)),
              "Fecha": datetime.now().isoformat(timespec="minutes")}
    (sess.folder / "ORIGEN.txt").write_text("".join(f"{k}: {v}\n" for k, v in origin.items()), encoding="utf-8")
    ids = {"documentos": sess.stack["doc_ruta"], "fichas": sess.stack["ficha_f"],
           "preguntas": [item["id"] for item in sess.items]}
    (sess.folder / "ids.json").write_text(json.dumps(ids, ensure_ascii=False, indent=1), encoding="utf-8")


def prepare(config_path, layout, sweep=False, reset_budget=False):
    """Valida todo lo que puede fallar antes de crear la carpeta de resultados. El libro del experimento se abre
    antes de crearla: si ya se gastó el tope, no queda carpeta a medias."""
    cfg = load_config(Path(config_path), layout.banco)
    _check_supported(cfg)
    _check_main(cfg, sweep)
    spec = load_sweep(config_path, cfg) if sweep else None
    stack = _load_stack(layout)
    items = _select_items(layout, cfg)
    ctx = scoring.build_context(layout.exp, stack)
    commit, branch = code_fingerprint(layout.repo)
    settings = PoolSettings(claude_bin=layout.claude_bin, timeout=layout.timeout, concurrency=cfg.concurrency,
                            budget_usd=cfg.budget_usd, max_turns=cfg.max_turns, odin_commit=commit,
                            stack_digest=stack_digest(stack))
    ledger = open_ledger(layout.state_dir / f"libro-{cfg.name}.json", cfg.budget_usd, reset_budget)
    folder = _new_folder(layout.results_dir, cfg.name)
    sess = Session(layout=layout, cfg=cfg, items=items, stack=stack, ctx=ctx, settings=settings, folder=folder,
                   cache=RunCache(layout.cache), ledger=ledger, sweep=spec)
    _write_start(sess, branch, commit)
    return sess


# ---------- paradas ----------
def _stopped(sess):
    return sess.stop_file.exists()


def _stop(sess, message):
    with sess.stop_file.open("a", encoding="utf-8") as f:
        f.write(message + "\n")


def _budget_stop(sess, skipped):
    """Escribe la parada por presupuesto una sola vez, con lo gastado, el tope y las llamadas sin lanzar."""
    if not _stopped(sess):
        _stop(sess, sess.ledger.stop_message(skipped))


def _quota_stop(sess, error, where):
    if error and ejecutar.LIMITE.search(error):
        _stop(sess, f"cuota o límite en {where}: {error[:200]}")


def _exit_code(sess):
    return 2 if _stopped(sess) else 0


def _from_cache(cache, jobs, before):
    """Lo que quedó en la caché: las ejecuciones que ya estaban antes no cuentan como gasto nuevo."""
    out = []
    for job in jobs:
        record = cache.get(job.key)
        if record is not None:
            out.append({**record, "approach": job.spec.approach, "replica": job.replica, "cached": job.key in before})
    return out


def _merged(records, cached):
    """Lo que hizo el grupo y lo de la caché que no está ya, sin duplicar (pregunta, enfoque, réplica)."""
    key = lambda r: (r["id"], r["approach"], r["replica"])
    seen = {key(r) for r in records}
    return [*records, *(r for r in cached if key(r) not in seen)]


def _pool(sess, specs, settings, sink):
    """Lanza un grupo de buscadores con el libro compartido. Se pare como se pare, lo hecho (también lo fallido)
    y lo de la caché van a `sink`."""
    approaches = sess.layout.banco / "enfoques"
    jobs = _build_jobs(sess.items, specs, approaches, settings)
    before = {job.key for job in jobs if sess.cache.get(job.key) is not None}
    try:
        records = run_pool(sess.items, specs, sess.stack, sess.cache, settings, approaches, sess.stop_file,
                           ledger=sess.ledger)
    except (BudgetExceeded, PoolStopped, ejecutar.Aislamiento) as error:  # el pool ya escribió PARADA.txt
        records = _merged(getattr(error, "records", []), _from_cache(sess.cache, jobs, before))
    except Exception as error:
        sink.extend(_merged(getattr(error, "records", []), _from_cache(sess.cache, jobs, before)))
        raise
    sink.extend(records)
    return records


# ---------- respuesta final ----------
def _group(runs):
    by_id = {}
    for run in runs:
        by_id.setdefault(run["id"], []).append(run)
    return by_id


def _unanswered(item, kind, error):
    return {"id": item["id"], "answer": None, "correcta_script": False, "approach": kind, "coste_usd": 0.0,
            "tokens": None, "duracion_s": 0.0, "error": error}


def _compose_paid(sess, item, runs):
    """Redactor: una llamada de pago. Si falla, su coste queda desconocido y el presupuesto se agota."""
    try:
        return compose(item, runs, sess.stack, sess.settings, sess.cfg.combiner.kind, sess.cfg.combiner.model)
    except Exception:
        sess.ledger.charge(None)
        raise


def _final_row(sess, item, runs):
    kind = sess.cfg.combiner.kind
    if kind in ANSWER_RULES:
        answer = ANSWER_RULES[kind](runs)
        extra = {"coste_usd": 0.0, "tokens": None, "duracion_s": 0.0, "error": None}
    else:
        record = _compose_paid(sess, item, runs)
        sess.ledger.charge(record.get("coste_usd"))
        _quota_stop(sess, record.get("error"), f"el redactor de {item['id']}")
        answer = record["respuesta"]
        extra = {key: record.get(key) for key in ("coste_usd", "tokens", "duracion_s", "error")}
    return {"id": item["id"], "answer": answer, "correcta_script": scoring.is_correct(item, answer, sess.ctx),
            "approach": kind, **extra}


def answer_stage(sess, runs, finals):
    """Respuesta final por pregunta. Un fallo en una pregunta no pierde las demás; un tope o una cuota paran las llamadas."""
    kind = sess.cfg.combiner.kind
    by_id = _group(runs)
    for position, item in enumerate(sess.items):
        if _stopped(sess):
            finals.append(_unanswered(item, kind, "parada antes de componer la respuesta"))
            continue
        if kind in AI_KINDS and not sess.ledger.can_launch():
            _budget_stop(sess, len(sess.items) - position)
            finals.append(_unanswered(item, kind, "presupuesto agotado"))
            continue
        try:
            finals.append(_final_row(sess, item, by_id.get(item["id"], [])))
        except ejecutar.Aislamiento as error:
            _stop(sess, f"AISLAMIENTO ROTO en el redactor: {error}")
            finals.append(_unanswered(item, kind, f"aislamiento roto: {error}"))
        except Exception as error:  # un fallo de una pregunta no pierde las demás
            finals.append(_unanswered(item, kind, f"error inesperado: {error!r}"))
    if sess.ledger.unknown:
        _budget_stop(sess, 0)


# ---------- jueces ----------
def _judge_jobs(sess):
    jobs = []
    for spec in sess.cfg.judges:
        rubric = (sess.layout.banco / "rubricas" / f"{spec.rubric}.txt").read_text(encoding="utf-8")
        for _ in range(spec.count):
            jobs.append((len(jobs), spec, rubric))
    return jobs


def _is_paid(spec):
    return not spec.model.startswith(judge.OLLAMA_PREFIX)


def _one_judge(sess, index, spec, rubric, item, answer):
    rng = random.Random(sess.seed + index)
    material = judge.judge_material(item, answer, lambda ident, pagina: scoring.page_text(ident, pagina, sess.ctx), rng)
    if not _is_paid(spec):
        return judge.run_ollama_judge(material, rubric, spec.model, sess.layout.ollama_url, sess.layout.timeout)
    return judge.run_claude_judge(material, rubric, spec.model, sess.layout.claude_judge_bin, sess.layout.timeout)


def _call_judge(sess, index, spec, rubric, item, answer):
    """Un juez de pago que falla después de llamar a claude deja su coste desconocido."""
    try:
        return _one_judge(sess, index, spec, rubric, item, answer)
    except Exception:
        if _is_paid(spec):
            sess.ledger.charge(None)
        raise


def _unjudged(item, records, error, motivo=None):
    return {"id": item["id"], "judges": records, "jury": None, "error": error, "motivo": motivo}


def _may_launch(sess, spec):
    """Un juez de pago arranca solo sin parada y con presupuesto; los de Ollama son gratis y siguen."""
    return not _is_paid(spec) or (not _stopped(sess) and sess.ledger.can_launch())


def _judge_or_skip(sess, jobs, item, answer):
    """Un «no consta» no llega al jurado: fila sin veredicto, con su motivo y sin gasto de jueces."""
    if judge.is_no_consta(answer):
        return _unjudged(item, [], None, NO_CONSTA_MOTIVO), None, 0
    return _judge_question(sess, jobs, item, answer)


def _judge_question(sess, jobs, item, answer):
    """Veredictos de los jueces de una pregunta. Devuelve (fila, motivo de parada o None, llamadas de pago sin lanzar).
    Sin presupuesto, los jueces de Ollama (gratis) siguen corriendo; la pregunta queda sin veredicto.
    Una cuota en un juez de Claude para el experimento, igual que en el redactor."""
    records, skipped = [], 0
    try:
        for index, spec, rubric in jobs:
            if not _may_launch(sess, spec):
                skipped += 1
                continue
            record = _call_judge(sess, index, spec, rubric, item, answer)
            if _is_paid(spec):
                sess.ledger.charge(record.get("coste_usd"))
                _quota_stop(sess, record.get("error"), f"el juez de {item['id']}")
            records.append(record)
    except ejecutar.Aislamiento as error:
        return _unjudged(item, records, str(error)), f"AISLAMIENTO ROTO en juez: {error}", skipped
    except Exception as error:  # un juez roto no pierde los veredictos de las demás preguntas
        return _unjudged(item, records, repr(error)), None, skipped
    if skipped:
        return _unjudged(item, records, "parada o presupuesto agotado"), BUDGET, skipped
    jury = judge.aggregate([r["verdict"] for r in records], sess.cfg.aggregation)
    return {"id": item["id"], "judges": records, "jury": jury, "error": None, "motivo": None}, None, 0


def judge_stage(sess, finals, verdicts):
    by_id = {item["id"]: item for item in sess.items}
    jobs = _judge_jobs(sess)
    answered = [f for f in finals if f["answer"] is not None]
    with ThreadPoolExecutor(max(1, sess.cfg.concurrency)) as pool:
        results = list(pool.map(lambda f: _judge_or_skip(sess, jobs, by_id[f["id"]], f["answer"]), answered))
    skipped_total, budget_hit = 0, False
    for row, reason, skipped in results:
        verdicts.append(row)
        skipped_total += skipped
        if reason == BUDGET:
            budget_hit = True
        elif reason:
            _stop(sess, reason)
    if budget_hit or sess.ledger.unknown:
        _budget_stop(sess, skipped_total)


# ---------- salida ----------
def _write_jsonl(path, rows):
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def _write_outputs(sess, runs, finals, verdicts, turn_runs=None):
    """Se llama siempre (finally): lo que haya se escribe aunque una etapa haya fallado."""
    _write_jsonl(sess.folder / "buscadores.jsonl", runs)
    _write_jsonl(sess.folder / "final.jsonl", finals)
    _write_jsonl(sess.folder / "veredictos.jsonl", verdicts)
    if turn_runs is not None:
        _write_jsonl(sess.folder / "turnos.jsonl", turn_runs)
    ledger = {"spent": sess.ledger.spent, "unknown": sess.ledger.unknown, "cap": sess.ledger.cap}
    (sess.folder / "libro.json").write_text(json.dumps(ledger, indent=1), encoding="utf-8")
    report.rebuild(sess.folder, sess.layout)


def run_experiment(config_path, layout, reset_budget=False):
    sess = prepare(config_path, layout, reset_budget=reset_budget)
    runs, finals, verdicts = [], [], []
    try:
        _pool(sess, sess.cfg.searchers, sess.settings, runs)
        if not _stopped(sess):
            answer_stage(sess, runs, finals)
        if finals and not _stopped(sess):
            judge_stage(sess, finals, verdicts)
    finally:
        _write_outputs(sess, runs, finals, verdicts)
    return _exit_code(sess)


def _single_run(runs):
    """Respuesta de la única ejecución usable (combinador single sin exigir la réplica 0)."""
    usable = [r for r in runs if r.get("error") is None and r.get("respuesta") is not None]
    return usable[0]["respuesta"] if usable else None


def _by_question(records):
    out = {}
    for record in records:
        out.setdefault(record["id"], []).append(record)
    return out


def _turn_runs(sess, sink):
    """Un solo buscador main con cada tope de turnos, con el mismo libro de gasto; sin redactor ni jueces."""
    model = next(s.model for s in sess.cfg.searchers if s.approach == "main")
    spec = SearcherSpec("main", 1, model)
    for turns in sess.sweep.single_max_turns:
        mark = len(sink)
        try:
            _pool(sess, (spec,), replace(sess.settings, max_turns=turns), sink)
        finally:
            for record in sink[mark:]:
                record["max_turns"] = turns
        if _stopped(sess):
            break


def _curves(sess, runs, turn_runs):
    score = lambda item, answer: scoring.is_correct(item, answer, sess.ctx)
    draws, seed, items = sess.sweep.draws, sess.seed, sess.items
    main = _by_question(runs)
    multi = [m for m in sess.sweep.mixes if sum(m.values()) > 1]
    single_mixes = [m for m in sess.sweep.mixes if sum(m.values()) == 1]
    multi_rows = curve(main, items, score, combine.majority_vote, multi, draws, seed)
    single_mix_rows = curve(main, items, score, _single_run, single_mixes, draws, seed)
    turn_rows = []
    for turns in sess.sweep.single_max_turns:
        by_question = _by_question([r for r in turn_runs if r["max_turns"] == turns])
        turn_rows.append({**curve(by_question, items, score, combine.single, [{"main": 1}], 1, seed)[0],
                          "max_turns": turns})
    default = curve(main, items, score, _single_run, [{"main": 1}], draws, seed)[0]
    variation = (default["accuracy_max"] - default["accuracy_min"]) / 2
    mix_rows = [{**row, "supera_single": beats_single_frontier(row, turn_rows, variation)} for row in multi_rows]
    mix_rows += [{**row, "supera_single": None} for row in single_mix_rows]
    return {"variacion": variation, "mezclas": mix_rows, "single": turn_rows, "single_por_defecto": default}


def sweep_experiment(config_path, layout, reset_budget=False):
    sess = prepare(config_path, layout, sweep=True, reset_budget=reset_budget)
    runs, turn_runs = [], []
    try:
        _pool(sess, sess.cfg.searchers, sess.settings, runs)
        if not _stopped(sess):
            _turn_runs(sess, turn_runs)
        if not _stopped(sess):
            (sess.folder / "curvas.json").write_text(
                json.dumps(_curves(sess, runs, turn_runs), ensure_ascii=False, indent=1), encoding="utf-8")
    finally:
        _write_outputs(sess, runs, [], [], turn_runs)
    return _exit_code(sess)
