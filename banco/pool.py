"""Grupo de buscadores: cada pregunta × enfoque × réplica es una sesión aislada de claude, con caché y tope de gasto."""

import threading
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path
from types import SimpleNamespace

from banco.cache import RunCache, run_key
from banco.config import SearcherSpec
from banco.medir_path import ejecutar


class BudgetExceeded(Exception):
    """El gasto alcanzó el tope y quedaron ejecuciones sin lanzar."""


class PoolStopped(Exception):
    """Un límite de cuota paró el grupo; al relanzar, las ejecuciones hechas salen de la caché."""


@dataclass(frozen=True)
class PoolSettings:
    claude_bin: str
    timeout: int
    concurrency: int
    budget_usd: float
    max_turns: int | None
    odin_commit: str


@dataclass(frozen=True)
class _Job:
    item: dict
    spec: SearcherSpec
    replica: int
    text: str
    args: SimpleNamespace
    key: str


@dataclass
class _State:
    cap: float
    lock: threading.Lock = field(default_factory=threading.Lock)
    stop: threading.Event = field(default_factory=threading.Event)
    spent: float = 0.0
    skipped: int = 0
    reasons: list = field(default_factory=list)
    isolation_error: Exception | None = None
    unexpected_error: Exception | None = None


def _build_jobs(items, specs, approaches_dir, settings):
    jobs = []
    for spec in specs:
        text = (Path(approaches_dir) / f"{spec.approach}.txt").read_text(encoding="utf-8")
        args = SimpleNamespace(claude_bin=settings.claude_bin, modelo=spec.model, timeout=settings.timeout,
                               max_turns=settings.max_turns)
        for item in items:
            for replica in range(spec.count):
                key = run_key(item["id"], item["pregunta"], spec.approach, text, spec.model, replica,
                              settings.odin_commit, settings.max_turns)
                jobs.append(_Job(item, spec, replica, text, args, key))
    return jobs


def _with_extras(record, job, cached):
    return {**record, "approach": job.spec.approach, "replica": job.replica, "cached": cached}


def _process_job(job, carga, cache, temp_dir, state):
    previous = cache.get(job.key)
    if previous is not None:
        return _with_extras(previous, job, cached=True)
    with state.lock:
        if state.spent >= state.cap:
            state.skipped += 1
            return None
    record = ejecutar.responder(job.item, carga, job.args, temp_dir, job.text)
    with state.lock:
        state.spent += record["coste_usd"] or 0
    if record["error"] and ejecutar.LIMITE.search(record["error"]):
        with state.lock:
            state.reasons.append(f"cuota o límite en {job.item['id']}: {record['error'][:200]}")
        state.stop.set()
    cache.put(job.key, record)
    return _with_extras(record, job, cached=False)


def _execute_job(job, carga, cache, temp_dir, state):
    if state.stop.is_set():
        return None
    try:
        return _process_job(job, carga, cache, temp_dir, state)
    except ejecutar.Aislamiento as error:
        state.isolation_error = error
    except Exception as error:  # cualquier otro fallo para el grupo; se relanza al cerrar
        state.unexpected_error = error
    state.stop.set()
    return None


def _write_stop(stop_file, message):
    stop_file.write_text(message + "\n", encoding="utf-8")


def _close(state, stop_file):
    if state.isolation_error is not None:
        _write_stop(stop_file, f"AISLAMIENTO ROTO: {state.isolation_error}")
        raise state.isolation_error
    if state.unexpected_error is not None:
        _write_stop(stop_file, f"ERROR INESPERADO: {state.unexpected_error!r}")
        raise state.unexpected_error
    if state.reasons:
        _write_stop(stop_file, "\n".join(state.reasons))
        raise PoolStopped("; ".join(state.reasons))
    if state.skipped:
        message = (f"Tope de gasto alcanzado: gastado {state.spent:.4f} USD de un tope de {state.cap:.4f} USD; "
                   f"{state.skipped} ejecuciones sin lanzar.")
        _write_stop(stop_file, message)
        raise BudgetExceeded(message)


def run_pool(items: list[dict], specs: tuple[SearcherSpec, ...], carga: dict, cache: RunCache,
             settings: PoolSettings, approaches_dir: Path, stop_file: Path) -> list[dict]:
    jobs = _build_jobs(items, specs, approaches_dir, settings)
    state = _State(cap=settings.budget_usd)
    with ThreadPoolExecutor(max(1, settings.concurrency)) as pool:
        futures = [pool.submit(_execute_job, job, carga, cache, stop_file.parent, state) for job in jobs]
        results = [f.result() for f in futures]
    _close(state, stop_file)
    return [r for r in results if r is not None]
