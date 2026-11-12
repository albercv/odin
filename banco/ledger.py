"""Libro de gasto del experimento: un solo tope para buscadores, redactor y jueces, seguro entre hilos."""

import threading


class Ledger:
    """Gasto acumulado frente al tope. Un registro de pago sin coste numérico agota el presupuesto."""

    def __init__(self, cap):
        self.cap = cap
        self.spent = 0.0
        self.unknown = 0
        self.lock = threading.Lock()

    def can_launch(self):
        """¿Queda presupuesto para una llamada de pago nueva? Se decide bajo el cerrojo, antes de lanzarla."""
        with self.lock:
            return self.unknown == 0 and self.spent < self.cap

    def charge(self, cost):
        """Suma un coste real. Un coste que no es un número cuenta como desconocido, no como 0."""
        with self.lock:
            if isinstance(cost, (int, float)) and not isinstance(cost, bool):
                self.spent += cost
            else:
                self.unknown += 1

    def stop_message(self, skipped=0):
        with self.lock:
            if self.unknown:
                return (f"Parada por coste desconocido: {self.unknown} registro(s) de pago sin coste numérico; "
                        f"gastado {self.spent:.4f} USD de un tope de {self.cap:.4f} USD; "
                        "no se lanzan más llamadas de pago.")
            return (f"Tope de gasto alcanzado: gastado {self.spent:.4f} USD de un tope de {self.cap:.4f} USD; "
                    f"{skipped} ejecuciones sin lanzar.")
