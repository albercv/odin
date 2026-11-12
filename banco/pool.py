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
class _Trabajo:
    item: dict
    spec: SearcherSpec
    replica: int
    texto: str
    args: SimpleNamespace
    key: str


@dataclass
class _Estado:
    tope: float
    lock: threading.Lock = field(default_factory=threading.Lock)
    parar: threading.Event = field(default_factory=threading.Event)
    gastado: float = 0.0
    omitidas: int = 0
    motivos: list = field(default_factory=list)
    aislamiento: Exception | None = None


def _trabajos(items, specs, approaches_dir, settings):
    trabajos = []
    for spec in specs:
        texto = (Path(approaches_dir) / f"{spec.approach}.txt").read_text(encoding="utf-8")
        args = SimpleNamespace(claude_bin=settings.claude_bin, modelo=spec.model, timeout=settings.timeout, max_turns=settings.max_turns)
        for item in items:
            for replica in range(spec.count):
                key = run_key(item["id"], spec.approach, texto, spec.model, replica, settings.odin_commit)
                trabajos.append(_Trabajo(item, spec, replica, texto, args, key))
    return trabajos


def _con_extras(rec, t, cached):
    return {**rec, "approach": t.spec.approach, "replica": t.replica, "cached": cached}


def _ejecutar_trabajo(t, carga, cache, ctemp, estado):
    if estado.parar.is_set():
        return None
    previo = cache.get(t.key)
    if previo is not None:
        return _con_extras(previo, t, cached=True)
    with estado.lock:
        if estado.gastado >= estado.tope:
            estado.omitidas += 1
            return None
    try:
        rec = ejecutar.responder(t.item, carga, t.args, ctemp, t.texto)
    except ejecutar.Aislamiento as e:
        with estado.lock:
            estado.aislamiento = e
        estado.parar.set()
        return None
    with estado.lock:
        estado.gastado += rec["coste_usd"] or 0
    if rec["error"] and ejecutar.LIMITE.search(rec["error"]):
        with estado.lock:
            estado.motivos.append(f"cuota o límite en {t.item['id']}: {rec['error'][:200]}")
        estado.parar.set()
    cache.put(t.key, rec)
    return _con_extras(rec, t, cached=False)


def _parar(stop_file, mensaje):
    stop_file.write_text(mensaje + "\n", encoding="utf-8")


def _cerrar(estado, stop_file):
    if estado.aislamiento is not None:
        _parar(stop_file, f"AISLAMIENTO ROTO: {estado.aislamiento}")
        raise estado.aislamiento
    if estado.motivos:
        _parar(stop_file, "\n".join(estado.motivos))
        raise PoolStopped("; ".join(estado.motivos))
    if estado.omitidas:
        mensaje = (f"Tope de gasto alcanzado: gastado {estado.gastado:.4f} USD de un tope de {estado.tope:.4f} USD; "
                   f"{estado.omitidas} ejecuciones sin lanzar.")
        _parar(stop_file, mensaje)
        raise BudgetExceeded(mensaje)


def run_pool(items: list[dict], specs: tuple[SearcherSpec, ...], carga: dict, cache: RunCache,
             settings: PoolSettings, approaches_dir: Path, stop_file: Path) -> list[dict]:
    trabajos = _trabajos(items, specs, approaches_dir, settings)
    estado = _Estado(tope=settings.budget_usd)
    with ThreadPoolExecutor(max(1, settings.concurrency)) as pool:
        futuros = [pool.submit(_ejecutar_trabajo, t, carga, cache, stop_file.parent, estado) for t in trabajos]
        resultados = [f.result() for f in futuros]
    _cerrar(estado, stop_file)
    return [r for r in resultados if r is not None]
