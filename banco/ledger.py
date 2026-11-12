"""Libro de gasto del experimento: un solo tope para buscadores, redactor y jueces, seguro entre hilos y persistido en disco."""

import json
import os
import tempfile
import threading
from datetime import datetime
from pathlib import Path


class BudgetAlreadySpent(Exception):
    """El libro del experimento ya alcanzó el tope (o tiene un coste desconocido): la orden no arranca."""


CONSECUTIVE_ERRORS = 3  # errores seguidos de claude de pago que paran el experimento


def _numeric(cost):
    return isinstance(cost, (int, float)) and not isinstance(cost, bool)


class Ledger:
    """Gasto acumulado frente al tope. Un registro de pago sin coste numérico agota el presupuesto.
    Con `path`, cada cargo se guarda de forma atómica bajo el cerrojo, así relanzar no vuelve a gastar."""

    def __init__(self, cap, path=None):
        self.cap = cap
        self.path = None if path is None else Path(path)
        self.spent = 0.0
        self.unknown = 0
        self.charges = []
        self.streak = 0  # errores seguidos de claude de pago (buscadores, redactor y jueces de Claude)
        self.lock = threading.Lock()
        if self.path is not None and self.path.exists():
            self._load()

    def _load(self):
        self.charges = json.loads(self.path.read_text(encoding="utf-8"))["cargos"]
        self.spent = sum(c["coste_usd"] for c in self.charges if c["coste_usd"] is not None)
        self.unknown = sum(c["coste_usd"] is None for c in self.charges)

    def _save(self):
        """Escritura atómica: primero un temporal y luego os.replace. Se llama con el cerrojo tomado."""
        self.path.parent.mkdir(parents=True, exist_ok=True)
        data = {"tope_usd": self.cap, "total_usd": self.spent, "desconocidos": self.unknown, "cargos": self.charges}
        fd, tmp = tempfile.mkstemp(dir=self.path.parent, suffix=".tmp")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
        os.replace(tmp, self.path)

    def can_launch(self):
        """¿Queda presupuesto para una llamada de pago nueva? Se decide bajo el cerrojo, antes de lanzarla."""
        with self.lock:
            return self.unknown == 0 and self.spent < self.cap

    def note_claude(self, error):
        """Racha de errores seguidos de claude de pago: un error suma y un éxito (error None) la pone a 0.
        Devuelve la racha. Los aciertos de caché y los jueces de Ollama no llegan aquí."""
        with self.lock:
            self.streak = self.streak + 1 if error else 0
            return self.streak

    def charge(self, cost):
        """Suma un coste real. Un coste que no es un número cuenta como desconocido, no como 0."""
        with self.lock:
            if _numeric(cost):
                self.spent += cost
            else:
                self.unknown += 1
                cost = None
            self.charges.append({"fecha": datetime.now().isoformat(timespec="seconds"), "coste_usd": cost})
            if self.path is not None:
                self._save()

    def stop_message(self, skipped=0):
        with self.lock:
            if self.unknown:
                return (f"Parada por coste desconocido: {self.unknown} registro(s) de pago sin coste numérico; "
                        f"gastado {self.spent:.4f} USD de un tope de {self.cap:.4f} USD; "
                        "no se lanzan más llamadas de pago.")
            return (f"Tope de gasto alcanzado: gastado {self.spent:.4f} USD de un tope de {self.cap:.4f} USD; "
                    f"{skipped} ejecuciones sin lanzar.")


def open_ledger(path, cap, reset=False):
    """Libro del experimento: lo gastado en órdenes anteriores cuenta. Se niega a arrancar si ya se llegó al tope
    o hay un coste desconocido; con `reset`, el libro anterior se archiva y se empieza en cero."""
    path = Path(path)
    if reset and path.exists():
        path.rename(path.with_name(f"{path.stem}.{datetime.now():%Y%m%d-%H%M%S}.json"))
    ledger = Ledger(cap, path)
    if ledger.unknown:
        raise BudgetAlreadySpent(f"el libro {path.name} tiene {ledger.unknown} registro(s) de pago con coste desconocido; "
                                 "no se lanza nada. Para empezar de nuevo, usa --reiniciar-presupuesto (make REINICIAR=1).")
    if ledger.spent >= cap:
        raise BudgetAlreadySpent(f"el libro {path.name} ya gastó {ledger.spent:.4f} USD de un tope de {cap:.4f} USD; "
                                 "no se lanza nada. Para empezar de nuevo, usa --reiniciar-presupuesto (make REINICIAR=1).")
    return ledger
