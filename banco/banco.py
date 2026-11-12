"""CLI del banco. Uso:
  python3 banco/banco.py run --config X
  python3 banco/banco.py sweep --config X
  python3 banco/banco.py report --results DIR
"""

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from banco import report, run  # noqa: E402
from banco.config import ConfigError  # noqa: E402
from banco.layout import default_layout  # noqa: E402


def _parser():
    ap = argparse.ArgumentParser(prog="banco")
    orders = ap.add_subparsers(dest="orden", required=True)
    for name in ("run", "sweep"):
        orders.add_parser(name).add_argument("--config", required=True)
    orders.add_parser("report").add_argument("--results", required=True)
    return ap


def main(argv=None, layout=None):
    args = _parser().parse_args(argv)
    layout = layout or default_layout()
    try:
        if args.orden == "report":
            report.rebuild(Path(args.results), layout)
            return 0
        order = run.run_experiment if args.orden == "run" else run.sweep_experiment
        return order(Path(args.config), layout)
    except ConfigError as error:
        print(f"error de configuración: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
