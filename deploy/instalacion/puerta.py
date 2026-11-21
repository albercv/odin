#!/usr/bin/env python3
"""Puerta de la instalación (encargo 27): solo deja instalar un commit que está en origin/<rama> y tiene una
validación de GitHub Actions en verde. Cerrada ante la duda; sin interruptor para saltarla.

Escribe en la salida estándar solo el SHA aprobado. Con --explicar da el veredicto y sale siempre con 0."""
import json
import os
import re
import subprocess
import sys

RAMAS = ("develop", "main")
CAMPOS = ("head_sha", "head_branch", "event", "status", "conclusion", "created_at", "run_number", "html_url")
FIN = " No se ha cambiado nada."
REMOTO = re.compile(r"^(?:https://github\.com/|git@github\.com:|ssh://git@github\.com/)([\w.-]+)/([\w.-]+?)(?:\.git)?/?$")


class Negada(Exception):
    pass


def ejecutar(orden, que):
    try:
        r = subprocess.run(orden, capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired) as e:
        raise Negada(f"No se ha podido ejecutar {orden[0]} ({e}); {que}.{FIN}")
    if r.returncode != 0:
        raise Negada(f"Ha fallado {' '.join(orden[:3])}: {r.stderr.strip() or 'sin detalle'}.{FIN}")
    return r.stdout.strip()


def elegir_rama(env):
    rama = env.get("RAMA") or env.get("ODIN_RAMA_INSTALACION") or ""
    if not rama:
        raise Negada("No se sabe de qué rama instalar. Fija ODIN_RAMA_INSTALACION=develop (o main) en deploy/.env, "
                     f"o pasa RAMA=develop (o main) en la orden.{FIN}")
    if rama not in RAMAS:
        raise Negada(f"La rama «{rama}» no vale: solo se instala de develop o de main.{FIN}")
    return rama


def repositorio():
    url = ejecutar(["git", "config", "--get", "remote.origin.url"], "no se conoce el repositorio")
    m = REMOTO.match(url)
    if not m:
        raise Negada(f"El remoto origin ({url}) no es un repositorio de github.com.{FIN}")
    return f"{m[1]}/{m[2]}"


def commit_a_instalar(rama):
    ejecutar(["git", "fetch", "origin", rama], "sin red no se puede comprobar la rama")
    sha = ejecutar(["git", "rev-parse", f"{rama}^{{commit}}"], f"no existe la rama local {rama}")
    r = subprocess.run(["git", "merge-base", "--is-ancestor", sha, f"refs/remotes/origin/{rama}"], capture_output=True)
    if r.returncode != 0:
        raise Negada(f"El commit {sha[:7]} de {rama} no está en origin/{rama}. Súbelo y únelo por pull request.{FIN}")
    return sha


def ejecuciones(repo, rama, sha):
    ruta = f"repos/{repo}/actions/workflows/validacion.yml/runs?head_sha={sha}&event=push&per_page=20"
    salida = ejecutar(["gh", "api", ruta], "sin gh no se puede comprobar la validación")
    try:
        runs = json.loads(salida)["workflow_runs"]
        for r in runs:
            [r[k] for k in CAMPOS]
        propias = [r for r in runs if r["head_sha"] == sha and r["head_branch"] == rama and r["event"] == "push"]
        return sorted(propias, key=lambda r: (r["created_at"], r["run_number"]), reverse=True)
    except (ValueError, KeyError, TypeError):
        raise Negada(f"La respuesta de GitHub no tiene la forma esperada.{FIN}")


def veredicto(sha, rama, runs):
    if not runs:
        raise Negada(f"El commit {sha[:7]} de {rama} está sin validar: no hay ejecución de validacion.yml.{FIN}")
    ultima = runs[0]
    if ultima["status"] != "completed":
        raise Negada(f"La validación de {sha[:7]} está pendiente: espera a que termine ({ultima['html_url']}).{FIN}")
    if ultima["conclusion"] != "success":
        raise Negada(f"La validación de {sha[:7]} está en rojo ({ultima['html_url']}). Arréglalo con un pull request.{FIN}")
    return ultima["html_url"]


def comprobar(env):
    rama = elegir_rama(env)
    repo = repositorio()
    sha = commit_a_instalar(rama)
    url = veredicto(sha, rama, ejecuciones(repo, rama, sha))
    return sha, rama, url


def main(argv, env):
    try:
        sha, rama, url = comprobar(env)
    except Negada as e:
        if "--explicar" in argv:
            print(f"La puerta se negaría: {e.args[0].removesuffix(FIN)}")
            return 0
        print(e.args[0], file=sys.stderr)
        return 1
    if "--explicar" in argv:
        print(f"La puerta dejaría instalar {sha[:7]} de {rama}: validación en verde ({url})")
    else:
        print(sha)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], os.environ))
