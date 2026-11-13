"""Configuración de un experimento del banco: TOML validado y convertido a objetos."""

import math
import re
import tomllib
from dataclasses import dataclass
from pathlib import Path

from banco.archivos import ARCHIVES, BATTERIES, missing_archives

MODES = ("explore", "closing")
COMBINER_KINDS = ("single", "majorityVote", "pageFusion", "composer")
AI_COMBINER_KINDS = ("pageFusion", "composer")
PLAIN_MODELS = ("haiku", "sonnet")
OLLAMA_MODEL = re.compile(r"ollama:[A-Za-z0-9._:-]+")
PLAIN_NAME = re.compile(r"[A-Za-z][A-Za-z0-9]*")
AT_LEAST = re.compile(r"atLeast:(\d+)")
JUDGE_TIMEOUT_S = 600
ROOT = "la configuración"


class ConfigError(ValueError):
    """La configuración del banco no es válida."""


@dataclass(frozen=True)
class SearcherSpec:
    approach: str
    count: int
    model: str


@dataclass(frozen=True)
class JudgeSpec:
    rubric: str
    count: int
    model: str
    think: bool = False  # solo jueces ollama: «think» de la petición /api/chat
    timeout_s: int = JUDGE_TIMEOUT_S  # solo jueces ollama


@dataclass(frozen=True)
class SampleSpec:
    size: int
    strata: str
    seed: int
    include: tuple = ()  # preguntas obligatorias: siempre en la muestra, dentro de su tamaño


@dataclass(frozen=True)
class CombinerSpec:
    kind: str
    model: str | None


@dataclass(frozen=True)
class ExperimentConfig:
    name: str
    archives: tuple[str, ...]
    batteries: tuple[str, ...]
    mode: str
    sample: SampleSpec | None
    repetitions: int
    budget_usd: float
    concurrency: int
    max_turns: int | None
    searchers: tuple[SearcherSpec, ...]
    combiner: CombinerSpec
    judges: tuple[JudgeSpec, ...]
    aggregation: str


def _need(table: dict, key: str, where: str):
    if key not in table:
        raise ConfigError(f"falta «{key}» en {where}")
    return table[key]


def _text(table: dict, key: str, where: str) -> str:
    value = _need(table, key, where)
    if not isinstance(value, str) or not value:
        raise ConfigError(f"«{key}» en {where} debe ser un texto no vacío")
    return value


def _table(raw: dict, key: str, where: str) -> dict:
    value = _need(raw, key, where)
    if not isinstance(value, dict):
        raise ConfigError(f"«{key}» en {where} debe ser una tabla")
    return value


def _positive(table: dict, key: str, where: str, kind=(int, float)):
    value = _need(table, key, where)
    if isinstance(value, bool) or not isinstance(value, kind) or not math.isfinite(value) or value <= 0:
        raise ConfigError(f"«{key}» en {where} debe ser un número mayor que 0")
    return value


def _choice(table: dict, key: str, where: str, options: tuple) -> str:
    value = _text(table, key, where)
    if value not in options:
        raise ConfigError(f"«{key}» en {where} debe ser uno de: {', '.join(options)}")
    return value


def _file_name(table: dict, key: str, where: str, folder: str, base_dir: Path) -> str:
    name = _text(table, key, where)
    if not PLAIN_NAME.fullmatch(name) or not (base_dir / folder / f"{name}.txt").is_file():
        raise ConfigError(f"«{name}» en {where} no tiene fichero en {folder}/")
    return name


def _model(table: dict, where: str, judge: bool) -> str:
    model = _text(table, "model", where)
    if model in PLAIN_MODELS or (judge and OLLAMA_MODEL.fullmatch(model)):
        return model
    allowed = "haiku, sonnet" + (", ollama:<nombre>" if judge else "")
    raise ConfigError(f"modelo «{model}» en {where} no válido; admite: {allowed}")


def _table_list(raw: dict, key: str) -> list:
    items = raw.get(key, [])
    if not isinstance(items, list) or not items or not all(isinstance(i, dict) for i in items):
        raise ConfigError(f"hace falta al menos una entrada [[{key}]]")
    return items


def _searchers(raw: dict, base_dir: Path) -> tuple[SearcherSpec, ...]:
    where = "[[searchers]]"
    return tuple(
        SearcherSpec(
            approach=_file_name(item, "approach", where, "enfoques", base_dir),
            count=_positive(item, "count", where, int),
            model=_model(item, where, judge=False),
        )
        for item in _table_list(raw, "searchers")
    )


def _judge_options(item: dict, model: str) -> tuple[bool, int]:
    """«think» y «timeoutS» solo tienen efecto en los jueces de Ollama: en otros se rechazan, no se ignoran."""
    where = "[[judges]]"
    if not model.startswith("ollama:") and ("think" in item or "timeoutS" in item):
        raise ConfigError(f"«think» y «timeoutS» en {where} solo se admiten en jueces ollama:")
    think = item.get("think", False)
    if not isinstance(think, bool):
        raise ConfigError(f"«think» en {where} debe ser true o false")
    timeout_s = _positive(item, "timeoutS", where, int) if "timeoutS" in item else JUDGE_TIMEOUT_S
    return think, timeout_s


def _judges(raw: dict, base_dir: Path) -> tuple[JudgeSpec, ...]:
    where = "[[judges]]"
    judges = []
    for item in _table_list(raw, "judges"):
        model = _model(item, where, judge=True)
        think, timeout_s = _judge_options(item, model)
        judges.append(JudgeSpec(
            rubric=_file_name(item, "rubric", where, "rubricas", base_dir),
            count=_positive(item, "count", where, int),
            model=model, think=think, timeout_s=timeout_s,
        ))
    return tuple(judges)


def _combiner(raw: dict) -> CombinerSpec:
    table = _table(raw, "combiner", ROOT)
    if table.get("kind") == "judgeSelect":
        raise ConfigError("el combinador «judgeSelect» queda fuera de esta fase")
    kind = _choice(table, "kind", "[combiner]", COMBINER_KINDS)
    if "model" in table:
        return CombinerSpec(kind, _model(table, "[combiner]", judge=False))
    if kind in AI_COMBINER_KINDS:
        raise ConfigError(f"el combinador «{kind}» necesita «model» en [combiner]")
    return CombinerSpec(kind, None)


def _aggregation(value: str, judge_count: int) -> str:
    match = AT_LEAST.fullmatch(value)
    if value in ("majority", "unanimous") or (match and 1 <= int(match.group(1)) <= judge_count):
        return value
    raise ConfigError(f"«aggregation» «{value}» no válido con {judge_count} jueces")


def _archives(raw: dict) -> tuple[str, ...]:
    archives = _need(raw, "archives", ROOT)
    if not isinstance(archives, list) or not archives or not all(isinstance(a, str) and a for a in archives):
        raise ConfigError("«archives» debe ser una lista de nombres de archivo")
    return tuple(archives)


def _sample(raw: dict, mode: str) -> SampleSpec | None:
    if "sample" not in raw:
        return None
    if mode == "closing":
        raise ConfigError("el modo closing no admite submuestreo: quita «sample»")
    table = _table(raw, "sample", ROOT)
    seed = _need(table, "seed", "[sample]")
    if isinstance(seed, bool) or not isinstance(seed, int):
        raise ConfigError("«seed» en [sample] debe ser un entero")
    size = _positive(table, "size", "[sample]", int)
    include = _include(table, size)
    return SampleSpec(size=size, strata=_text(table, "strata", "[sample]"), seed=seed, include=include)


def _include(table, size):
    """«include» opcional: ids de preguntas obligatorias, sin repetir y dentro del tamaño de la muestra."""
    include = table.get("include", [])
    if not isinstance(include, list) or not all(isinstance(q, str) for q in include):
        raise ConfigError("«include» en [sample] debe ser una lista de ids de pregunta")
    if len(set(include)) != len(include):
        raise ConfigError("«include» en [sample] repite alguna pregunta")
    if len(include) > size:
        raise ConfigError(f"«include» en [sample] tiene {len(include)} preguntas, más que «size» ({size})")
    return tuple(include)


def _check_battery(name: str, mode: str, archives: tuple[str, ...]) -> None:
    if name == "reserva":
        if mode != "closing":
            raise ConfigError("la reserva solo se usa en modo closing")
        return
    if name not in BATTERIES:
        raise ConfigError(f"batería desconocida «{name}»")
    # Con una capa desconocida (configs/ejemplo.toml las admite) la orden da el error de capa, antes que este.
    missing = missing_archives(name, archives) if all(a in ARCHIVES for a in archives) else []
    if missing:
        raise ConfigError(f"la batería «{name}» necesita el archivo «{missing[0]}» en «archives»")


def _batteries(raw: dict, mode: str, archives: tuple[str, ...]) -> tuple[str, ...]:
    """«battery» es un nombre o una lista de nombres; cada batería pide sus capas y la reserva solo va en closing."""
    value = _need(raw, "battery", ROOT)
    names = [value] if isinstance(value, str) else value
    if not isinstance(names, list) or not names or not all(isinstance(n, str) and n for n in names):
        raise ConfigError("«battery» debe ser un nombre de batería o una lista con al menos una batería")
    if len(set(names)) != len(names):
        raise ConfigError("«battery» repite alguna batería")
    for name in names:
        _check_battery(name, mode, archives)
    return tuple(names)


def load_config(path: Path, base_dir: Path) -> ExperimentConfig:
    try:
        raw = tomllib.loads(Path(path).read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as error:
        raise ConfigError(f"TOML no válido en {path}: {error}") from error
    mode = _choice(raw, "mode", ROOT, MODES)
    archives = _archives(raw)
    batteries = _batteries(raw, mode, archives)
    judges = _judges(raw, base_dir)
    judging = _table(raw, "judging", ROOT)
    aggregation = _aggregation(_text(judging, "aggregation", "[judging]"), sum(j.count for j in judges))
    max_turns = _positive(raw, "maxTurns", ROOT, int) if "maxTurns" in raw else None
    return ExperimentConfig(
        name=_text(raw, "name", ROOT),
        archives=archives,
        batteries=batteries,
        mode=mode,
        sample=_sample(raw, mode),
        repetitions=_positive(raw, "repetitions", ROOT, int),
        budget_usd=float(_positive(raw, "budgetUsd", ROOT)),
        concurrency=_positive(raw, "concurrency", ROOT, int),
        max_turns=max_turns,
        searchers=_searchers(raw, base_dir),
        combiner=_combiner(raw),
        judges=judges,
        aggregation=aggregation,
    )
