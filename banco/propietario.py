"""Protección de la instalación del propietario (D-099). El banco es un ejecutor de pruebas: no usa nunca la pila
«odin» ni los puertos 8080, 8088 y 8090 (API, panel y MCP reales). Solo stdlib; sin red ni Docker.
CLI: `python3 banco/propietario.py` sale con 0 o imprime el motivo en stderr y sale con 1."""

import os
import sys
from urllib.parse import urlsplit

PUERTOS_PROPIETARIO = {"8080", "8088", "8090"}  # API, panel y MCP de la instalación del propietario
PROYECTO_PROPIETARIO = "odin"
DEFECTOS_BANCO = {"PILA_PROYECTO": "odin-medicion", "ODIN_API_PORT": "28080",
                  "ODIN_PANEL_PORT": "28088", "ODIN_MCP_PORT": "28090"}
FIN = "No se ha cambiado nada."


class PropietarioError(SystemExit):
    """Una orden tocaría la instalación del propietario: se para antes de hacer nada."""


def _puertos_de(urls):
    return {str(urlsplit(url).port) for url in urls if urlsplit(url).port}


def proteger_propietario(entorno, proyecto, escribe, urls=()):
    puertos = {entorno.get("ODIN_API_PORT", "8080"), entorno.get("ODIN_PANEL_PORT", "8088"),
               entorno.get("ODIN_MCP_PORT", "8090")}
    puertos = (puertos | _puertos_de(urls)) & PUERTOS_PROPIETARIO
    if proyecto != PROYECTO_PROPIETARIO and puertos:
        raise PropietarioError(f"La pila «{proyecto}» apunta a puertos de la instalación del propietario "
                               f"({sorted(puertos)}). {FIN}")
    if proyecto == PROYECTO_PROPIETARIO and escribe and os.environ.get("CONFIRMAR_INSTALACION") != "PROPIETARIO":
        raise PropietarioError("Esto escribe en la instalación del propietario: hace falta "
                               f"CONFIRMAR_INSTALACION=PROPIETARIO, con su permiso. {FIN}")


def proteger_banco(entorno=None, urls=()):
    origen = os.environ if entorno is None else entorno
    e = {**DEFECTOS_BANCO, **{k: v for k, v in origen.items() if v}}  # un valor vacío cuenta como no puesto
    if e["PILA_PROYECTO"] == PROYECTO_PROPIETARIO:
        raise PropietarioError(f"El banco no usa nunca la pila «{PROYECTO_PROPIETARIO}» del propietario, "
                               f"ni para leer. {FIN}")
    proteger_propietario(e, e["PILA_PROYECTO"], escribe=True, urls=urls)


def main():
    try:
        proteger_banco()
    except PropietarioError as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
