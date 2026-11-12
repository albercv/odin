"""CLI del banco. Uso:
  python3 banco/banco.py run --config X
  python3 banco/banco.py sweep --config X
  python3 banco/banco.py report --results DIR
  python3 banco/banco.py reanudar --config X   (continúa la última tanda parada, en la misma carpeta)

El tope de gasto es del experimento (libro en banco/estado/libro-<nombre>.json): relanzar no vuelve a gastar.
--reiniciar-presupuesto archiva ese libro y empieza en cero.
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
from banco.ledger import BudgetAlreadySpent  # noqa: E402


def _parser():
    ap = argparse.ArgumentParser(prog="banco")
    orders = ap.add_subparsers(dest="orden", required=True)
    for name in ("run", "sweep"):
        order = orders.add_parser(name)
        order.add_argument("--config", required=True)
        order.add_argument("--reiniciar-presupuesto", action="store_true",
                           help="archiva el libro de gasto del experimento y empieza el tope en cero")
        order.add_argument("--sin-prueba", action="store_true",
                           help="no hace la llamada de prueba a claude antes del primer grupo")
    orders.add_parser("report").add_argument("--results", required=True)
    orders.add_parser("reanudar").add_argument("--config", required=True)
    return ap


def main(argv=None, layout=None):
    args = _parser().parse_args(argv)
    layout = layout or default_layout()
    try:
        if args.orden == "report":
            report.rebuild(Path(args.results), layout)
            return 0
        if args.orden == "reanudar":
            return run.resume_experiment(Path(args.config), layout)
        order = run.run_experiment if args.orden == "run" else run.sweep_experiment
        return order(Path(args.config), layout, reset_budget=args.reiniciar_presupuesto, sin_prueba=args.sin_prueba)
    except ConfigError as error:
        print(f"error de configuración: {error}", file=sys.stderr)
        return 1
    except BudgetAlreadySpent as error:
        print(f"error de presupuesto: {error}", file=sys.stderr)
        return 2
    except run.ResumeError as error:
        print(f"error de reanudación: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
