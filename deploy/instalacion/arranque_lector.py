#!/usr/bin/env python3
"""Arranque automático del puente del lector OCR como agente de launchd del usuario (sin sudo, nada global).
Uso: arranque_lector.py instalar|quitar|estado   (CONFIRMAR=ARRANQUE, LECTOR_DIR y ODIN_LAUNCH_AGENTS_DIR por entorno)"""
import os
import plistlib
import shutil
import subprocess
import sys
import time
import urllib.request
from dataclasses import dataclass
from pathlib import Path

LABEL = "com.e2d.odin.puente-ocr"
PORT = 8077
HEALTH_URL = f"http://127.0.0.1:{PORT}/health"
MODEL_DIR = "models--mlx-community--Unlimited-OCR-2-8bit"
HEALTH_WAIT_SECONDS = 300


@dataclass
class Config:
    lector: Path
    uv: str
    agents: Path
    log: Path
    uid: int
    confirmar: str = ""

    @property
    def plist(self):
        return self.agents / f"{LABEL}.plist"

    @property
    def domain(self):
        return f"gui/{self.uid}"


def real_run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def real_health():
    try:
        with urllib.request.urlopen(HEALTH_URL, timeout=3) as r:
            return r.status == 200
    except OSError:
        return False


def find_hf_home(lector):
    """Los pesos están en la caché del repositorio OCR; la worktree cuelga de él (OCR/.worktrees/<rama>)."""
    for base in (lector, lector.parent.parent):
        home = base / ".cache" / "huggingface"
        if (home / "hub" / MODEL_DIR).is_dir():
            return home
    return None


def build_plist(cfg, hf_home):
    return plistlib.dumps({
        "Label": LABEL,
        "WorkingDirectory": str(cfg.lector),
        "ProgramArguments": [cfg.uv, "run", "python", "serve.py", "--quant", "8"],
        "EnvironmentVariables": {
            "HF_HOME": str(hf_home), "HF_HUB_OFFLINE": "1", "UV_OFFLINE": "1",
            "PATH": f"{Path(cfg.uv).parent}:/usr/bin:/bin:/usr/sbin:/sbin"},
        "RunAtLoad": True, "KeepAlive": True, "ThrottleInterval": 30,
        "StandardOutPath": str(cfg.log), "StandardErrorPath": str(cfg.log)})


def validate(cfg):
    """Devuelve la caché de pesos o termina explicando qué falta, sin escribir nada."""
    if cfg.confirmar != "ARRANQUE":
        sys.exit("Esto instala un agente de launchd en tu usuario (~/Library/LaunchAgents) que arranca el puente del lector al "
                 "iniciar sesión y lo levanta si se cae. Si quieres seguir: make lector-arranque-instalar CONFIRMAR=ARRANQUE")
    if not (cfg.lector / "serve.py").is_file():
        sys.exit(f"No encuentro {cfg.lector}/serve.py. Indica la carpeta del puente: "
                 "make lector-arranque-instalar CONFIRMAR=ARRANQUE LECTOR_DIR=/ruta/OCR/.worktrees/puente-odin")
    if not cfg.uv:
        sys.exit("No encuentro uv en el PATH. Instálalo (brew install uv) y repite.")
    hf_home = find_hf_home(cfg.lector)
    if not hf_home:
        sys.exit(f"No encuentro los pesos del modelo ({MODEL_DIR}) en la caché del repositorio OCR. "
                 "Descárgalos allí con make model y repite.")
    return hf_home


def serve_pids(run):
    """Procesos que escuchan en el 8077. Si alguno no es serve.py, termina sin tocar nada."""
    _, out = run(["lsof", "-t", f"-iTCP:{PORT}", "-sTCP:LISTEN"])
    pids = out.split()
    for pid in pids:
        if "serve.py" not in run(["ps", "-p", pid, "-o", "command="])[1]:
            sys.exit(f"El puerto {PORT} lo usa otro programa (pid {pid}) que no es el puente. No he cambiado nada.")
    return pids


def wait_health(health, sleep, seconds=HEALTH_WAIT_SECONDS, step=5):
    for _ in range(seconds // step):
        if health():
            return True
        sleep(step)
    return health()


def instalar(cfg, run=real_run, health=real_health, sleep=time.sleep):
    hf_home = validate(cfg)
    run(["launchctl", "bootout", f"{cfg.domain}/{LABEL}"])  # por si ya estaba cargado: se reinstala limpio
    pids = serve_pids(run)
    cfg.agents.mkdir(parents=True, exist_ok=True)
    cfg.log.parent.mkdir(parents=True, exist_ok=True)
    cfg.plist.write_bytes(build_plist(cfg, hf_home))
    print(f"Escrito {cfg.plist}")
    for pid in pids:
        run(["kill", pid])
        print(f"Parado el puente que estaba arrancado a mano (pid {pid}).")
    code, out = run(["launchctl", "bootstrap", cfg.domain, str(cfg.plist)])
    if code != 0:
        sys.exit(f"launchctl no ha cargado el agente: {out.strip()}")
    print(f"Agente {LABEL} cargado. Esperando al puente (carga el modelo, puede tardar)...")
    if not wait_health(health, sleep):
        sys.exit(f"El puente no responde en {HEALTH_URL}. Mira el registro: {cfg.log}")
    print("El puente responde. Arranque automático instalado.")
    return 0


def quitar(cfg, run=real_run):
    if not cfg.plist.exists():
        print("El arranque automático del lector no estaba instalado; no hay nada que quitar.")
        return 0
    run(["launchctl", "bootout", f"{cfg.domain}/{LABEL}"])
    cfg.plist.unlink()
    print(f"Agente quitado y borrado {cfg.plist}. El puente ya no arranca solo (si estaba en marcha, se ha parado).")
    return 0


def field(output, name):
    for line in output.splitlines():
        key, _, value = line.strip().partition(" = ")
        if key == name:
            return value
    return "desconocido"


def estado(cfg, run=real_run, health=real_health):
    code, out = run(["launchctl", "print", f"{cfg.domain}/{LABEL}"])
    if code != 0:
        print("Agente: no está cargado.")
    else:
        print(f"Agente: cargado, estado {field(out, 'state')}, pid {field(out, 'pid')}, "
              f"último código de salida {field(out, 'last exit code')}.")
    print(f"Puente en {HEALTH_URL}: {'responde' if health() else 'no responde'}.")
    return 0


def odin_main_dir():
    here = Path(__file__).resolve().parents[2]
    r = subprocess.run(["git", "-C", str(here), "rev-parse", "--path-format=absolute", "--git-common-dir"],
                       capture_output=True, text=True)
    return Path(r.stdout.strip()).parent if r.returncode == 0 and r.stdout.strip() else here


def config_from_env():
    odin = odin_main_dir()
    return Config(
        lector=Path(os.environ.get("LECTOR_DIR") or odin.parent / "OCR" / ".worktrees" / "puente-odin"),
        uv=shutil.which("uv"),
        agents=Path(os.environ.get("ODIN_LAUNCH_AGENTS_DIR") or Path.home() / "Library" / "LaunchAgents"),
        log=odin / "data" / "puente-ocr.log", uid=os.getuid(), confirmar=os.environ.get("CONFIRMAR", ""))


def main(argv):
    actions = {"instalar": instalar, "quitar": quitar, "estado": estado}
    if len(argv) != 2 or argv[1] not in actions:
        sys.exit(__doc__)
    return actions[argv[1]](config_from_env())


if __name__ == "__main__":
    sys.exit(main(sys.argv))
