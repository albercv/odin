#!/usr/bin/env python3
"""Registra la entrada `odin` de Claude Desktop con la clave de un trabajador de prueba. No toca las demás entradas.
Uso: claude_desktop.py --config FICHERO --credenciales FICHERO --trabajador NOMBRE --mcp DIR --node RUTA --copias DIR"""
import argparse
import json
import os
import shutil
import sys
import tempfile
from datetime import datetime
from pathlib import Path


def leer_config(config):
    if not config.exists():
        return {}
    try:
        return json.loads(config.read_text(encoding="utf-8") or "{}")
    except json.JSONDecodeError as error:
        raise SystemExit(f"{config} no es JSON válido ({error}); no se ha cambiado nada.")


def entrada(credenciales, trabajador, mcp, node):
    return {"command": node, "args": [str(mcp / "dist" / "stdio.js")],
            "env": {"ODIN_API_URL": credenciales["api"], "ODIN_ACCESS_KEY": trabajador["clave"]}}


def copiar(config, copias):
    copias.mkdir(parents=True, exist_ok=True)
    destino = copias / f"claude_desktop_config-{datetime.now():%Y-%m-%d-%H%M%S}.json"
    shutil.copy2(config, destino)
    os.chmod(destino, 0o600)
    return destino


def escribir(config, contenido):
    config.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=config.parent, delete=False) as tmp:
        json.dump(contenido, tmp, ensure_ascii=False, indent=2)
        tmp.write("\n")
    if config.exists():
        os.chmod(tmp.name, os.stat(config).st_mode & 0o777)
    os.replace(tmp.name, config)


def registrar(config, ruta_credenciales, nombre, mcp, node, copias):
    credenciales = json.loads(Path(ruta_credenciales).read_text(encoding="utf-8"))
    trabajadores = credenciales["trabajadores"]
    if nombre not in trabajadores:
        raise SystemExit(f"No hay ningún trabajador de prueba «{nombre}». Elige uno: {', '.join(sorted(trabajadores))}.")
    contenido = leer_config(config)
    if config.exists():
        print(f"Copia previa: {copiar(config, copias)}")
    contenido.setdefault("mcpServers", {})["odin"] = entrada(credenciales, trabajadores[nombre], mcp, node)
    escribir(config, contenido)
    t = trabajadores[nombre]
    print(f"Conector «odin» registrado en {config} con la clave de {t['nombre']} ({t['empresa']}).")
    print("Reinicia Claude Desktop (ciérralo del todo y vuelve a abrirlo) para que lo cargue.")


def main():
    a = argparse.ArgumentParser()
    for opcion in ("--config", "--credenciales", "--trabajador", "--mcp", "--node", "--copias"):
        a.add_argument(opcion, required=True)
    args = a.parse_args()
    registrar(Path(args.config), Path(args.credenciales), args.trabajador, Path(args.mcp).resolve(), args.node,
              Path(args.copias))


if __name__ == "__main__":
    sys.exit(main())
