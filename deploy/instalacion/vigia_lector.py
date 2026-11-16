#!/usr/bin/env python3
"""Vigía del puente del lector: arranca el puente como hijo y lo reinicia si sigue vivo pero no responde a /health.
Uso: vigia_lector.py [--health-url URL] [--hang-after SEGUNDOS] [--interval SEGUNDOS] -- <orden del puente>"""
import argparse
import json
import os
import signal
import subprocess
import sys
import time
import urllib.request

HEALTH_URL = "http://127.0.0.1:8077/health"
HANG_AFTER = 2100  # lectura máxima de Odín (30 min) + 5: antes, una lectura larga y un cuelgue se ven igual
INTERVAL = 60
KILL_WAIT = 10


class Stop(Exception):
    pass


class HangWatch:
    """Cuelgue = hang_after segundos seguidos sin respuesta correcta (contados desde el arranque si no hubo ninguna)."""

    def __init__(self, hang_after, start):
        self.hang_after, self.last_ok = hang_after, start

    def healthy(self, now):
        self.last_ok = now

    def hung(self, now):
        return now - self.last_ok >= self.hang_after

    def silent_minutes(self, now):
        return int((now - self.last_ok) // 60)


def log(message):
    print(f"{time.strftime('%Y-%m-%d %H:%M:%S')} vigía: {message}", file=sys.stderr, flush=True)


def probe(url, interval):
    try:
        with urllib.request.urlopen(url, timeout=min(10, interval)) as r:
            return r.status == 200 and json.load(r).get("status") == "ok"
    except Exception:
        return False


def stop_group(child):
    """SIGTERM al grupo del puente, espera y SIGKILL; no deja nada vivo."""
    if child.poll() is not None:
        return
    os.killpg(child.pid, signal.SIGTERM)
    try:
        child.wait(KILL_WAIT)
    except subprocess.TimeoutExpired:
        pass
    try:
        os.killpg(child.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    child.wait()


def watch(child, hang_after, interval, probe=probe, sleep=time.sleep, now=time.monotonic, url=HEALTH_URL):
    watcher = HangWatch(hang_after, now())
    while child.poll() is None:
        if probe(url, interval):
            watcher.healthy(now())
        elif watcher.hung(now()):
            log(f"sin respuesta de /health desde hace {watcher.silent_minutes(now())} min: reinicio el puente")
            return 1
        sleep(interval)
    log(f"el puente ha terminado con código {child.returncode}")
    return child.returncode


def raise_stop(*_):
    raise Stop()


def run(cmd, url, hang_after, interval):
    signal.signal(signal.SIGTERM, raise_stop)
    child = subprocess.Popen(cmd, start_new_session=True)
    log(f"puente arrancado (pid {child.pid})")
    try:
        return watch(child, hang_after, interval, url=url)
    except Stop:
        log("parada pedida: paro el puente")
        return 0
    finally:
        stop_group(child)


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--health-url", default=HEALTH_URL)
    parser.add_argument("--hang-after", type=float, default=HANG_AFTER)
    parser.add_argument("--interval", type=float, default=INTERVAL)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    cmd = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not cmd:
        parser.error("falta la orden del puente tras --")
    return run(cmd, args.health_url, args.hang_after, args.interval)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
