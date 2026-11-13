#!/usr/bin/env python3
"""Añade un proyecto nuevo, con sus documentos, fichas y expedientes, a una instalación con datos, sin vaciarla (encargo 14).
Los datos van en una carpeta con el formato del archivo ficticio: manifiesto.json (un solo proyecto, de una empresa que ya
existe), corpus/<ruta>, y si hay expedientes, expedientes.json y expedientes/<ruta>. Solo lo ven los miembros que se
indiquen (MIEMBROS, por defecto marta), que escriben también las fichas. Con --comprobar solo revisa, sin cambiar nada.
Lo lanza deploy/instalacion/anadir-proyecto.sh, que hace la copia antes y vuelve a ella si algo falla.
Uso: anadir_proyecto.py [--comprobar] <carpeta de datos>"""
import importlib.util
import os
import sys
import time
from collections import namedtuple
from pathlib import Path

import datos_prueba as dp

ESTADOS = {"PENDING", "APPROVED", "REJECTED", "CORRECTED"}
TIPOS = {".csv": "text/csv", ".md": "text/markdown", ".txt": "text/plain", ".pdf": "application/pdf",
         ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg"}
CONSULTAS = 4  # documentos de texto con cuya frase más larga se comprueba el aislamiento

Destino = namedtuple("Destino", "empresa miembros")


def leer(carpeta):
    expedientes = carpeta / "expedientes.json"
    return dp.leer_json(carpeta / "manifiesto.json"), (dp.leer_json(expedientes) if expedientes.exists() else [])


def tipo_de(fichero):
    return TIPOS.get(fichero.suffix.lower(), "application/octet-stream")


# ---------- Datos: se revisan antes de tocar nada ----------

def problemas(m, expedientes, carpeta, max_bytes):
    if len(m["proyectos"]) != 1:
        return [f"el manifiesto tiene {len(m['proyectos'])} proyectos y tiene que tener uno"]
    rutas = {d["ruta"] for d in m["documentos"]}
    return (problemas_documentos(m, carpeta, max_bytes) + problemas_fichas(m)
            + [f"sustitución con un documento que no está en el manifiesto: {s}" for s in m.get("sustituciones", [])
               if not {s["viejo"], s["nuevo"]} <= rutas]
            + problemas_expedientes(expedientes, rutas, carpeta))


def problemas_documentos(m, carpeta, max_bytes):
    p = m["proyectos"][0]
    fuera = [d["ruta"] for d in m["documentos"] if (d["empresa"], d["proyecto"]) != (p["empresa"], p["clave"])]
    ficheros = [carpeta / "corpus" / d["ruta"] for d in m["documentos"]]
    return ([f"documento fuera del proyecto nuevo: {r}" for r in fuera]
            + [f"falta el fichero {f}" for f in ficheros if not f.is_file()]
            + [f"{f.name} pasa de {max_bytes} bytes" for f in ficheros if f.is_file() and f.stat().st_size > max_bytes]
            + ([] if any(f.suffix == ".txt" for f in ficheros) else ["hace falta algún .txt para comprobar el aislamiento"]))


def problemas_fichas(m):
    clave = m["proyectos"][0]["clave"]
    return ([f"ficha {f['id']}: estado {f['estado']} desconocido" for f in m["fichas"] if f["estado"] not in ESTADOS]
            + [f"ficha {f['id']}: CORRECTED sin correccion" for f in m["fichas"]
               if f["estado"] == "CORRECTED" and not f.get("correccion")]
            + [f"ficha {f['id']}: fuera del proyecto nuevo" for f in m["fichas"] if f["proyecto"] != clave])


def problemas_expedientes(expedientes, rutas, carpeta):
    malos = [f"expediente sin su documento de caso (caso-…) en el manifiesto: {e['caso']}" for e in expedientes
             if e["caso"] not in rutas or not dp.es_caso(e["caso"])]
    ficheros = [r for e in expedientes for r in e["documentos"] + e["compartidos"] + e["ajenos"]]
    return malos + [f"falta el fichero expedientes/{r}" for r in ficheros if not (carpeta / "expedientes" / r).is_file()]


# ---------- Instalación ----------

def destino(api, m, expedientes, cred, miembros):
    """La empresa y los miembros donde se añade. Para sin cambiar nada si algo no cuadra."""
    p, codigo = m["proyectos"][0], next(e["codigo"] for e in m["empresas"] if e["clave"] == m["proyectos"][0]["empresa"])
    empresa = next((c for c in api.admin("GET", "/v1/admin/companies") if c["code"] == codigo), None)
    if empresa is None:
        raise SystemExit(f"No hay empresa con código {codigo}. No se ha cambiado nada.")
    if any(x["name"] == p["nombre"] for x in api.admin("GET", f"/v1/admin/companies/{empresa['id']}/projects")):
        raise SystemExit(f"El proyecto «{p['nombre']}» ya está en la instalación. No se ha cambiado nada.")
    if expedientes and "CASE_FILES" not in empresa["capabilities"]:
        raise SystemExit("La empresa no tiene la capacidad de expedientes (CASE_FILES). No se ha cambiado nada.")
    sin_clave = [n for n in miembros if n not in cred["trabajadores"]]
    if sin_clave:
        raise SystemExit(f"Sin clave en las credenciales de prueba: {sin_clave}. No se ha cambiado nada.")
    correos = {x["email"]: x["userId"] for x in api.admin("GET", f"/v1/admin/companies/{empresa['id']}/members")}
    ids = {n: correos.get(f"{n}@{p['empresa']}.invalid") for n in miembros}
    if None in ids.values():
        raise SystemExit(f"Miembros que no están en la empresa: {[n for n, i in ids.items() if i is None]}. "
                         "No se ha cambiado nada.")
    return Destino(empresa["id"], ids)


def crear_proyecto(api, destino, p):
    pid = api.admin("POST", f"/v1/admin/companies/{destino.empresa}/projects", {"name": p["nombre"]})["id"]
    if p.get("abierto"):
        api.admin("PATCH", f"/v1/admin/companies/{destino.empresa}/projects/{pid}", {"restricted": False})
    for usuario in destino.miembros.values():
        api.admin("PUT", f"/v1/admin/companies/{destino.empresa}/projects/{pid}/members/{usuario}")
    return pid


def subir_documentos(api, empresa, proyecto, documentos, carpeta):
    mapa = {}
    for d in documentos:
        fichero = carpeta / "corpus" / d["ruta"]
        mapa[d["ruta"]] = dp.subir(api, empresa, fichero, d["titulo"], proyecto, dp.es_caso(d["ruta"]), tipo_de(fichero))[0]
    return mapa


def esperar(api, empresa, ids, limite=3600):
    """Espera a que los documentos nuevos salgan de lectura (los escaneos se quedan en la cola del lector OCR)."""
    fin = time.time() + limite
    while True:
        docs = [d for d in dp.listar_documentos(api, empresa) if d["id"] in ids]
        if not any(d["status"] in dp.EN_LECTURA for d in docs):
            break
        if time.time() > fin:
            raise RuntimeError(f"Documentos nuevos todavía en lectura tras {limite} s")
        time.sleep(3)
    fallidos = [d["title"] for d in docs if d["status"] == "FAILED"]
    if fallidos:
        raise RuntimeError(f"No se han podido leer {len(fallidos)} documentos nuevos: {fallidos[:5]}")
    return docs


def revision(ficha):
    if ficha["estado"] == "APPROVED":
        return {"decision": "APPROVE"}
    if ficha["estado"] == "CORRECTED":
        return {"decision": "CORRECT", "correctedBody": ficha["correccion"]}
    if ficha["estado"] == "REJECTED":
        return {"decision": "REJECT", "note": ficha.get("nota", dp.MOTIVO_RECHAZO)}
    return None


def crear_fichas(api, fichas, proyecto, clave):
    for f in fichas:
        cid = api.admin("POST", "/v1/cards", {"title": f["titulo"], "body": f["texto"], "projectId": proyecto}, clave=clave)["id"]
        if revision(f):
            api.admin("POST", f"/v1/admin/cards/{cid}/review", revision(f))
    return {e: sum(f["estado"] == e for f in fichas) for e in sorted({f["estado"] for f in fichas})}


def comprobar_aislamiento(api, consultas, nuevos, cred, miembros):
    """Los miembros encuentran documentos nuevos y ningún otro trabajador de prueba ve ninguno. Devuelve cuántos no lo ven."""
    for nombre, t in cred["trabajadores"].items():
        vistos = {h["sourceId"] for c in consultas for h in dp.buscar(api, t["clave"], c)} & nuevos
        if nombre in miembros and not vistos:
            raise RuntimeError(f"{nombre} no encuentra los documentos del proyecto nuevo")
        if nombre not in miembros and vistos:
            raise RuntimeError(f"Fuga de aislamiento: {nombre} ve {len(vistos)} documentos del proyecto nuevo")
    return len(cred["trabajadores"]) - len(miembros)


def consultas_de(m, carpeta):
    textos = [carpeta / "corpus" / d["ruta"] for d in m["documentos"] if d["ruta"].endswith(".txt")][:CONSULTAS]
    return [" ".join(max(f.read_text(encoding="utf-8").splitlines(), key=len).split()[:40]) for f in textos]


def con_proyecto(cred, miembros, nombre):
    for n in miembros:
        cred["trabajadores"][n]["proyectos"].append(nombre)
    return cred


# ---------- Orden ----------

def anadir(api, entorno, m, expedientes, carpeta, cred, miembros):
    inicio, p = time.time(), m["proyectos"][0]
    sitio = destino(api, m, expedientes, cred, miembros)
    pid = crear_proyecto(api, sitio, p)
    mapa = subir_documentos(api, sitio.empresa, pid, m["documentos"], carpeta)
    guardar_mapa(mapa)
    docs = esperar(api, sitio.empresa, set(mapa.values()))
    for s in m.get("sustituciones", []):
        api.admin("PUT", f"/v1/admin/documents/{mapa[s['viejo']]}/superseded-by", {"replacementId": mapa[s["nuevo"]]})
    fichas = crear_fichas(api, m["fichas"], pid, cred["trabajadores"][miembros[0]]["clave"])
    resumen_exp = cargar_expedientes(api, mapa, expedientes, carpeta) if expedientes else "Expedientes: ninguno."
    otros = comprobar_aislamiento(api, consultas_de(m, carpeta), {mapa[d["ruta"]] for d in m["documentos"]}, cred, miembros)
    dp.escribir_credenciales(dp.CREDENCIALES, con_proyecto(cred, miembros, p["nombre"]))
    escaneados = {mapa[d["ruta"]] for d in m["documentos"] if tipo_de(Path(d["ruta"])).startswith(("image/", "application/pdf"))}
    return [f"Proyecto «{p['nombre']}» añadido; lo ven {', '.join(miembros)} y los administradores en el panel.",
            f"Documentos: {len(m['documentos'])} ({sum(map(dp.es_caso, mapa))} casos), "
            f"{len(m.get('sustituciones', []))} sustituidos. Fichas: {fichas}.", resumen_exp,
            f"Aislamiento comprobado por la API: {', '.join(miembros)} lo encuentra; {otros} trabajadores de prueba no.",
            *dp.estado_lectores(api, entorno, {p["empresa"]: sitio.empresa}, docs, escaneados),
            f"Tiempo: {time.time() - inicio:.0f} s."]


def guardar_mapa(mapa):
    """Añade lo nuevo al mapa de ids de la instalación (ruta -> documentId), sin quitar nada."""
    fichero = dp.CREDENCIALES / "mapa-ids.local.json"
    dp.escribir_mapa(dp.CREDENCIALES, (dp.leer_json(fichero) if fichero.exists() else {}) | mapa)


def cargar_expedientes(api, mapa, expedientes, carpeta):
    import cargar_expedientes
    cuentas = cargar_expedientes.cargar_expedientes(api, mapa, expedientes, carpeta, guardar=guardar_mapa)
    return dp.resumen_expedientes(cuentas)


def main(argv):
    rutas = [a for a in argv if not a.startswith("--")]
    if len(rutas) != 1:
        raise SystemExit(__doc__)
    carpeta = Path(rutas[0]).resolve()
    m, expedientes = leer(carpeta)
    entorno = dp.leer_entorno(dp.PRINCIPAL / os.environ.get("INSTALACION_ENV", "deploy/.env"))
    fallos = problemas(m, expedientes, carpeta, int(entorno.get("ODIN_UPLOAD_MAX_BYTES", 52428800)))
    if expedientes and importlib.util.find_spec("cargar_expedientes") is None:
        fallos.append("hay expedientes y falta su cargador (encargo 13): lanza la orden desde integracion/instalacion")
    if fallos:
        raise SystemExit("Los datos no se pueden cargar. No se ha cambiado nada.\n- " + "\n- ".join(fallos))
    api, cred = dp.conectar(entorno), dp.leer_json(dp.CREDENCIALES / "credenciales.local.json")
    dp.entrar(api, cred["administrador"])
    miembros = os.environ.get("MIEMBROS", "marta").split(",")
    if "--comprobar" in argv:
        destino(api, m, expedientes, cred, miembros)
        print(f"Datos y instalación listos: «{m['proyectos'][0]['nombre']}», {len(m['documentos'])} documentos, "
              f"{len(m['fichas'])} fichas y {len(expedientes)} expedientes, para {', '.join(miembros)}.")
        return 0
    print("\n".join(anadir(api, entorno, m, expedientes, carpeta, cred, miembros)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
