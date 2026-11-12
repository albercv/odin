"""Grupo de buscadores: cada pregunta × enfoque × réplica es una sesión aislada de claude, con caché y tope de gasto."""

import threading
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path
from types import SimpleNamespace

from banco.cache import RunCache, run_key
from banco.config import SearcherSpec
from banco.ledger import Ledger
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
    stack_digest: str = ""  # huella de los mapas de ids de carga.json: una pila recargada no reutiliza caché


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
    ledger: Ledger
    lock: threading.Lock = field(default_factory=threading.Lock)
    stop: threading.Event = field(default_factory=threading.Event)
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
                              settings.odin_commit, settings.max_turns, settings.stack_digest)
                jobs.append(_Job(item, spec, replica, text, args, key))
    return jobs


def _with_extras(record, job, cached):
    return {**record, "approach": job.spec.approach, "replica": job.replica, "cached": cached}


def _carrying(error, records):
    """Adjunta a la excepción los registros ya hechos, también los fallidos, para que el banco los escriba."""
    error.records = records
    return error


def _never_launched(record):
    """Un error sin duración nunca llegó a lanzar claude (p. ej. sin clave de API): no hubo gasto."""
    return record.get("error") is not None and record.get("duracion_s") is None


def _halt(state, reason):
    with state.lock:
        state.reasons.append(reason)
        state.stop.set()


def _claim_launch(state):
    """Decide bajo el cerrojo si la ejecución se lanza: ni tras una parada ni por encima del tope."""
    with state.lock:
        if state.stop.is_set():
            return False
        if not state.ledger.can_launch():
            state.skipped += 1
            return False
        return True


def _process_job(job, carga, cache, temp_dir, state):
    previous = cache.get(job.key)
    if previous is not None:
        return _with_extras(previous, job, cached=True)
    if not _claim_launch(state):
        return None
    try:
        record = ejecutar.responder(job.item, carga, job.args, temp_dir, job.text)
    except Exception:  # la llamada pudo gastar antes de fallar: coste desconocido
        state.ledger.charge(None)
        raise
    if _never_launched(record):
        record["coste_usd"] = 0.0
    state.ledger.charge(record.get("coste_usd"))
    if record["error"] and ejecutar.LIMITE.search(record["error"]):
        _halt(state, f"cuota o límite en {job.item['id']}: {record['error'][:200]}")
    cache.put(job.key, record)
    return _with_extras(record, job, cached=False)


def _execute_job(job, carga, cache, temp_dir, state):
    if state.stop.is_set():
        return None
    try:
        return _process_job(job, carga, cache, temp_dir, state)
    except ejecutar.Aislamiento as error:
        state.isolation_error = error
        _halt(state, f"AISLAMIENTO ROTO en {job.item['id']}")
    except Exception as error:  # cualquier otro fallo para el grupo; se relanza al cerrar
        state.unexpected_error = error
        _halt(state, f"ERROR INESPERADO en {job.item['id']}: {error!r}")
    return None


def _write_stop(stop_file, message):
    stop_file.write_text(message + "\n", encoding="utf-8")


def _close(state, stop_file, records):
    if state.isolation_error is not None:
        _write_stop(stop_file, f"AISLAMIENTO ROTO: {state.isolation_error}")
        raise _carrying(state.isolation_error, records)
    if state.reasons:
        _write_stop(stop_file, "\n".join(state.reasons))
        if state.unexpected_error is not None:
            raise _carrying(state.unexpected_error, records)
        raise _carrying(PoolStopped("; ".join(state.reasons)), records)
    if state.skipped or state.ledger.unknown:
        message = state.ledger.stop_message(state.skipped)
        _write_stop(stop_file, message)
        raise _carrying(BudgetExceeded(message), records)


def run_pool(items: list[dict], specs: tuple[SearcherSpec, ...], carga: dict, cache: RunCache,
             settings: PoolSettings, approaches_dir: Path, stop_file: Path, ledger: Ledger | None = None) -> list[dict]:
    jobs = _build_jobs(items, specs, approaches_dir, settings)
    state = _State(ledger=ledger if ledger is not None else Ledger(settings.budget_usd))
    with ThreadPoolExecutor(max(1, settings.concurrency)) as pool:
        futures = [pool.submit(_execute_job, job, carga, cache, stop_file.parent, state) for job in jobs]
        results = [f.result() for f in futures]
    records = [r for r in results if r is not None]
    _close(state, stop_file, records)
    return records
