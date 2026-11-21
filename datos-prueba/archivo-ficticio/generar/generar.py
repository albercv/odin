#!/usr/bin/env python3
"""Construye datos/ de forma determinista (semilla 7). Solo biblioteca estándar, sin red, sin IA.

Uso: python3 generar/generar.py
"""
import datetime
import json
import os
import re
import shutil
import sys
import tempfile
from collections import Counter, defaultdict
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
EXP = HERE.parent
VENDOR = EXP / "vendor"
DATOS = EXP / "datos"
sys.path.insert(0, str(HERE))
import random  # noqa: E402

import expedientes  # noqa: E402
import textos as T  # noqa: E402
from textos import eur, fecha  # noqa: E402

rng = random.Random(7)
PAGE_MARK = re.compile(r"^=== Página (\d+) ===\s*$", re.MULTILINE)
EMPRESAS = {"ribera": ("Administraciones Ribera", "RIB"), "albar": ("Gestión Albar", "ALB")}
ACTOR = {"ribera": "t01", "albar": "t06"}
INFO = {}  # recuentos para el informe

DOCS = {}   # ruta -> texto
META = {}   # ruta -> dict
_pc = {}


# ----------------------------------------------------------------------------- utilidades de texto
def paginas(ruta):
    if ruta not in _pc:
        p = PAGE_MARK.split(DOCS[ruta])
        _pc[ruta] = {int(p[i]): p[i + 1].strip() for i in range(1, len(p) - 1, 2)}
    return _pc[ruta]


def buscar(ruta, patron, flags=0):
    if ruta not in DOCS:
        return None
    for n, t in paginas(ruta).items():
        m = re.search(patron, t, flags)
        if m:
            return n, m
    return None


class Hit:
    def __init__(self, ruta, patron, flags=0):
        r = buscar(ruta, patron, flags)
        if not r:
            raise KeyError((ruta, patron))
        self.ruta, self.pagina, self.m = ruta, r[0], r[1]
        self.lit = r[1].group(0)
        self.v = r[1].group(1) if r[1].groups() else self.lit


def hit(ruta, patron, flags=0):
    try:
        return Hit(ruta, patron, flags)
    except KeyError:
        return None


def num(s):
    return int(str(s).replace(".", ""))


def digitos(v):
    return str(v).replace(".", "")


def cita(h):
    return {"doc": h.ruta, "paginas": [h.pagina], "ancla": h.lit}


def fuente(h):
    return {"doc": h.ruta, "pagina": h.pagina, "frase": h.lit}


def seguro_prohibida(viejo, nuevo):
    """Valor viejo apto como 'prohibida': no puede estar contenido en el nuevo ni al revés."""
    a, b = str(viejo).lower(), str(nuevo).lower()
    return a not in b and b not in a


# ----------------------------------------------------------------------------- paso 1: base + escala.py
def construir_base():
    tmp = Path(tempfile.mkdtemp(prefix="archivo-ficticio-"))
    base = tmp / "base"
    shutil.copytree(VENDOR / "mundo-ficticio" / "corpus-limpio", base)
    shutil.copytree(VENDOR / "mundo-ficticio" / "casos-limite", base, dirs_exist_ok=True)
    os.environ["BDM_CORPUS"] = str(base)
    sys.path.insert(0, str(VENDOR / "scripts"))
    import escala  # noqa: E402  (se usa sin modificar)
    destino = tmp / "esc"
    escala.build(destino, 30)
    ficheros = list(destino.rglob("*.txt"))
    INFO["escala_documentos"] = len(ficheros)
    INFO["escala_comunidades"] = len(list(destino.iterdir()))
    INFO["escala_fugas"] = escala.leaks(destino)
    return tmp, destino, escala


def nombre_comunidad(carpeta):
    t = carpeta.name[:2]
    if t == "c1":
        h = re.search(r"Tomador del seguro: Comunidad de Propietarios (.+?), Puerto Albar", (carpeta / "poliza-seguro-2023.txt").read_text(encoding="utf-8"))
    else:
        h = re.search(r"Domicilio del riesgo: (.+?), Puerto Albar", (carpeta / "poliza-seguro-2024.txt").read_text(encoding="utf-8"))
    return h.group(1)


def indice_c2(nombre, escala):
    resto = nombre[len("Residencial "):]
    partes = resto.rsplit(" ", 1)
    if len(partes) == 2 and partes[1].isdigit() and partes[0] in escala.TREES:
        return escala.TREES.index(partes[0]) + 14 * (int(partes[1]) - 1)
    return escala.TREES.index(resto)


def subido(r):
    d = datetime.datetime(2024, 1, 8) + datetime.timedelta(days=r.randint(0, 900), hours=r.randint(8, 18), minutes=r.choice([0, 15, 30, 45]))
    return d.strftime("%Y-%m-%dT%H:%M:%SZ")


def titulo_de(nombre):
    s = nombre[:-4].replace("-", " ")
    return s[0].upper() + s[1:]


def registrar(ruta, empresa, proyecto, texto, por=None):
    assert ruta not in DOCS, ruta
    DOCS[ruta] = texto
    META[ruta] = {"ruta": ruta, "empresa": empresa, "proyecto": proyecto, "titulo": titulo_de(ruta.rsplit("/", 1)[1]),
                  "subido": subido(rng),
                  "subido_por": por or rng.choice(["t01", "t01", "t01", "t03", "t04", "t05"] if empresa == "ribera" else ["t06", "t06", "t06", "t07", "t08"])}


def add_doc(c, nombre, pags, por=None):
    ruta = f"{c['emp']}/{c['key']}/{nombre}"
    registrar(ruta, c["emp"], c["key"], "\n".join(f"=== Página {i} ===\n{p.strip()}\n" for i, p in enumerate(pags, 1)), por)
    c["docs"].append(ruta)
    return ruta


def add_general(emp, nombre, pags, proyecto=None):
    carpeta = proyecto or "_general"
    ruta = f"{emp}/{carpeta}/{nombre}"
    registrar(ruta, emp, proyecto, "\n".join(f"=== Página {i} ===\n{p.strip()}\n" for i, p in enumerate(pags, 1)), "t01" if emp == "ribera" else "t06")
    return ruta


def comunidades(destino, escala):
    base_keys = {"c1-olmo14", "c2-almendros", "c3-puerto3"}
    lista = []
    for carpeta in sorted(destino.iterdir()):
        key, tpl = carpeta.name, carpeta.name[:2]
        nombre = nombre_comunidad(carpeta)
        nombre = nombre[0].upper() + nombre[1:]  # escala.py deja a veces «calle Olmo N» en minúscula
        base = key in base_keys
        if base or tpl == "c1":
            emp = "ribera"
        elif tpl == "c3":
            emp = "albar"
        else:
            emp = "ribera" if indice_c2(nombre, escala) < 27 else "albar"
        lista.append({"key": key, "tpl": tpl, "nombre": nombre, "base": base, "emp": emp, "src": carpeta, "docs": [], "ocup": set()})
    nombres = [c["nombre"].lower() for c in lista]
    for c in lista:
        n = c["nombre"].lower()
        c["ambiguo"] = nombres.count(n) > 1 or any(o != n and o.startswith(n + " ") for o in nombres)
    # Odín no admite dos proyectos con el mismo nombre en una empresa: el repetido lleva « (bloque N)»
    vistos = {}
    for c in lista:
        c["nombre_orig"] = c["nombre"]
        k = (c["emp"], c["nombre"].lower())
        vistos[k] = vistos.get(k, 0) + 1
        if vistos[k] > 1:
            c["nombre"] = f"{c['nombre']} (bloque {vistos[k]})"
    return lista


def cargar_base(lista):
    for c in lista:
        for f in sorted(c["src"].glob("*.txt")):
            texto = f.read_text(encoding="utf-8")
            if c["emp"] == "albar":  # el generador del estudio deja al despacho como Ribera; en Albar se dice Gestión Albar
                texto = texto.replace("Administraciones Ribera", "Gestión Albar").replace("ADMINISTRACIONES RIBERA", "GESTIÓN ALBAR")
            if c["nombre"] != c["nombre_orig"]:  # mismo nombre nuevo en el texto, también en mayúsculas
                texto = re.sub(re.escape(c["nombre_orig"]) + r"(?!\d)", lambda m: c["nombre"].upper() if m.group(0).isupper() else c["nombre"], texto, flags=re.I)
            ruta = f"{c['emp']}/{c['key']}/{f.name}"
            registrar(ruta, c["emp"], c["key"], texto)
            c["docs"].append(ruta)


def doc(c, trozo):
    nombres = [(d, d.rsplit("/", 1)[1]) for d in c["docs"]]
    r = [d for d, n in nombres if n.startswith(trozo)] or [d for d, n in nombres if trozo in n]
    return r[0] if r else None


def hc(c, trozo, patron, flags=0):
    d = doc(c, trozo)
    return hit(d, patron, flags) if d else None


# ----------------------------------------------------------------------------- paso 2: ampliación
AMP = []          # registros de ampliación
CLAVES = []       # contradicciones
K_PREG = defaultdict(list)
ATL_USADOS = set()


def nueva_clave(tipo, fuentes, vigente, respuesta):
    fuentes = [f for f in fuentes if f]
    if len(fuentes) < 2:
        return None
    kid = f"K{len(CLAVES) + 1:03d}"
    CLAVES.append({"id": kid, "tipo": tipo, "fuentes": fuentes, "vigente": {"doc": vigente} if vigente else None, "respuesta_correcta": respuesta, "preguntas": []})
    return kid


def fh(h):
    return fuente(h) if h else None


def tomador_de(c):
    return c["nombre"]


def dom(c):
    return c["nombre"]


def emp_nombre(c):
    return EMPRESAS[c["emp"]][0]


def nuevo_num_poliza():
    while True:
        n = f"ATL-{rng.randint(1000, 9999)}-2025"
        if n not in ATL_USADOS:
            ATL_USADOS.add(n)
            return n


def mes_dia(y, ini=1, fin=12):
    return rng.randint(ini, fin), y


def ampliacion(comms):
    copias = [c for c in comms if not c["base"] and not c["ambiguo"]]
    por = {t: [c for c in copias if c["tpl"] == t] for t in ("c1", "c2", "c3")}
    for c in comms:
        for d in c["docs"]:
            for n in re.findall(r"ATL-\d{4}-\d{4}", DOCS[d]):
                ATL_USADOS.add(n)

    # ---- pólizas renovadas (versión)
    for t, n in (("c1", 8), ("c2", 8), ("c3", 8)):
        for c in rng.sample(por[t], n):
            vigente = doc(c, "poliza-seguro-2023") if t == "c1" else doc(c, "poliza-seguro-2024")
            viejos = [vigente] + ([doc(c, "poliza-seguro-2022")] if t == "c2" else [])
            anterior = hit(vigente, r"Póliza nº (ATL-\d{4}-\d{4})")
            fq = hit(vigente, r"franquicia de ([\d.]+) euros por siniestro")
            if not anterior or not fq:
                continue
            f_old = num(fq.v)
            f_new = rng.choice([x for x in (100, 120, 180, 200, 220, 250, 280, 300, 350, 400, 450, 500) if seguro_prohibida(x, f_old)])
            numero = nuevo_num_poliza()
            prima = rng.randrange(1500, 4300, 10)
            m = rng.randint(7, 12)
            ini = datetime.date(2025, m, 1)
            fin = datetime.date(2028, m, 1) - datetime.timedelta(days=1)
            pags = T.poliza_renovada(emp_nombre(c), tomador_de(c), dom(c), numero, anterior.v, eur(f_new), eur(prima),
                                     fecha(1, m, 2025), fecha(fin.day, fin.month, 2028), rng)
            nuevo = add_doc(c, "poliza-seguro-2025-renovada.txt", pags)
            c["ocup"].add("franq")
            AMP.append({"tipo": "poliza", "c": c, "nuevo": nuevo, "viejos": viejos, "num": numero, "anterior": anterior.v,
                        "f_new": f_new, "f_old": f_old, "prima": prima})
            hn = hit(nuevo, r"franquicia de [\d.]+ euros por siniestro")
            fuentes = [fh(hit(d, r"franquicia de [\d.]+ euros por siniestro")) for d in viejos] + [fh(hn)]
            AMP[-1]["kid"] = nueva_clave("version", fuentes, nuevo,
                                         f"La franquicia vigente por daños por agua es de {eur(f_new)} (póliza {numero}, que sustituye a la {anterior.v}); la anterior era de {eur(f_old)}.")

    # ---- anexos de limpieza y de ascensor (anexo)
    palabra = {"dos": 2, "tres": 3, "cuatro": 4, "cinco": 5}
    cifras = ["dos", "tres", "cuatro", "cinco", "seis"]
    for t, n in (("c1", 6), ("c2", 6), ("c3", 6)):
        for c in rng.sample([x for x in por[t]], n):
            d = doc(c, "contrato-limpieza")
            dh, ih = hit(d, r"limpieza de zonas comunes (\w+) días por semana"), hit(d, r"importe mensual de ([\d.]+) euros")
            if not dh or not ih or dh.v not in palabra:
                continue
            dias_old = dh.v
            dias_new = rng.choice([x for x in cifras if x != dias_old])
            imp_old = num(ih.v)
            imp_new = max(10, round(imp_old * rng.uniform(1.08, 1.35) / 10) * 10)
            if not seguro_prohibida(imp_old, imp_new):
                continue
            m = rng.randint(1, 12)
            y = rng.choice([2025, 2026])
            hoy = fecha(rng.randint(1, 28), max(1, m - 1) if m > 1 else 1, y)
            ruta = add_doc(c, "anexo-1-contrato-limpieza.txt", T.anexo_limpieza(emp_nombre(c), tomador_de(c), fecha(1, m, y), hoy, dias_new, eur(imp_new), "Limpiezas Brillo S.L.", rng))
            c["ocup"].add("limp")
            rec = {"tipo": "anexo_limpieza", "c": c, "nuevo": ruta, "viejos": [d], "dias_old": dias_old, "dias_new": dias_new, "imp_old": imp_old, "imp_new": imp_new}
            AMP.append(rec)
            ant = f"{dias_old} días por semana y {eur(imp_old)} al mes"
            rec["kid_dias"] = nueva_clave("anexo", [fh(dh), fh(hit(ruta, r"se prestará \w+ días por semana"))], ruta, f"La limpieza se presta {dias_new} días por semana (anexo), no {dias_old} (contrato).")
            rec["kid_imp"] = nueva_clave("anexo", [fh(ih), fh(hit(ruta, r"importe mensual pasa a ser de [\d.]+ euros"))], ruta, f"El importe mensual vigente es de {eur(imp_new)} (anexo), no {eur(imp_old)} (contrato). Antes: {ant}.")
    for t, n in (("c1", 7), ("c3", 7)):
        for c in rng.sample(por[t], n):
            d_con = doc(c, "contrato-ascensor")
            if t == "c1":
                cur_doc = doc(c, "anexo-1-contrato-ascensor")
                cu = hit(cur_doc, r"pasa a ser de ([\d.]+) euros")
                pl = hit(cur_doc, r"será de (\d+) horas")
                empresa, numero = "Ascensores Norte S.L.", 2
                previos = [d_con, cur_doc]
            else:
                cu = hit(d_con, r"cuota mensual de ([\d.]+) euros")
                pl = hit(d_con, r"se reduce a (\d+) horas")
                empresa, numero = "Elevadores Bahía S.L.", 1
                previos = [d_con]
            if not cu or not pl:
                continue
            cuota_old, pl_old = num(cu.v), int(pl.v)
            cuota_new = round(cuota_old * rng.uniform(1.06, 1.25) / 5) * 5
            pl_new = rng.choice([x for x in (1, 2, 3, 4, 5) if x != pl_old])
            if not seguro_prohibida(cuota_old, cuota_new):
                continue
            m, y = rng.randint(1, 12), rng.choice([2025, 2026])
            hoy = fecha(rng.randint(1, 28), m, y)
            ruta = add_doc(c, f"anexo-{numero}-contrato-ascensor.txt", T.anexo_ascensor(emp_nombre(c), tomador_de(c), fecha(1, m, y), hoy, eur(cuota_new), pl_new, empresa, rng, numero))
            c["ocup"] |= {"cuota_asc", "plazo_asc"}
            rec = {"tipo": "anexo_ascensor", "c": c, "nuevo": ruta, "viejos": previos, "cuota_old": cuota_old, "cuota_new": cuota_new, "pl_old": pl_old, "pl_new": pl_new}
            AMP.append(rec)
            hc_new = hit(ruta, r"cuota mensual pasa a ser de [\d.]+ euros")
            hp_new = hit(ruta, r"será de \d+ horas")
            prev_c = [fh(hit(d_con, r"cuota mensual de [\d.]+ euros"))] + ([fh(cu)] if t == "c1" else [])
            prev_p = [fh(hit(d_con, r"plazo máximo de respuesta[^.]*?\d+ horas"))] + ([fh(pl)] if t == "c1" else [])
            rec["kid_cuota"] = nueva_clave("anexo", prev_c + [fh(hc_new)], ruta, f"La cuota mensual vigente del ascensor es de {eur(cuota_new)} (anexo {numero}), no {eur(cuota_old)}.")
            rec["kid_plazo"] = nueva_clave("anexo", prev_p + [fh(hp_new)], ruta, f"Con personas atrapadas el plazo máximo de respuesta es de {pl_new} horas (anexo {numero}), no {pl_old}.")

    # ---- datos desactualizados: acta posterior
    def acta(c, tema, puntos):
        m, y = rng.randint(1, 12), 2026
        hoy = fecha(rng.randint(1, 28), m, y)
        return add_doc(c, f"acta-extraordinaria-2026-{tema}.txt", T.acta_posterior(tomador_de(c), emp_nombre(c), hoy, rng.choice(["18:30", "19:00", "19:30", "20:00"]), puntos, rng))

    nom_pat = r"([A-ZÁÉÍÓÚÑ][\wáéíóúñ]+(?: [A-ZÁÉÍÓÚÑ][\wáéíóúñ]+){1,2})"
    for c in rng.sample(por["c1"], 5):
        d = doc(c, "acta-ordinaria-2025")
        old = hit(d, r"Se elige presidente de la Comunidad a D\. " + nom_pat)
        if not old:
            continue
        nuevo = T.persona(rng)
        piso = rng.choice(["1º A", "2º B", "3º C", "4º A", "2º D", "5º B"])
        ruta = acta(c, "presidencia", [f"Dimisión y elección de la presidencia. D. {old.v} presenta su dimisión como presidente de la Comunidad por motivos personales. Se elige presidente de la Comunidad a D. {nuevo}, propietario del {piso}, con la aprobación de la mayoría de los asistentes.",
                                       "Ruegos y preguntas. No se formulan."])
        c["ocup"].add("pres")
        rec = {"tipo": "pres", "c": c, "nuevo": ruta, "viejos": [d], "old": old.v, "new": nuevo}
        AMP.append(rec)
        rec["kid"] = nueva_clave("dato_desactualizado", [fh(old), fh(hit(ruta, r"Se elige presidente de la Comunidad a D\. " + nom_pat))], ruta, f"El presidente actual es D. {nuevo} (acta extraordinaria de 2026); D. {old.v}, elegido en la junta ordinaria de 2025, dimitió.")
    for c in rng.sample(por["c3"], 4):
        d = doc(c, "acta-ordinaria-2024")
        old = hit(d, r"se reelige presidenta de la Comunidad a Dña\. " + nom_pat)
        if not old:
            continue
        nuevo = T.persona(rng)
        ruta = acta(c, "presidencia", [f"Dimisión y elección de la presidencia. Dña. {old.v} presenta su dimisión como presidenta de la Comunidad. Se elige presidenta de la Comunidad a Dña. {nuevo}, con la aprobación de la mayoría de los asistentes.",
                                       "Ruegos y preguntas. No se formulan."])
        c["ocup"].add("pres")
        rec = {"tipo": "pres", "c": c, "nuevo": ruta, "viejos": [d], "old": old.v, "new": nuevo}
        AMP.append(rec)
        rec["kid"] = nueva_clave("dato_desactualizado", [fh(old), fh(hit(ruta, r"Se elige presidenta de la Comunidad a Dña\. " + nom_pat))], ruta, f"La presidenta actual es Dña. {nuevo} (acta extraordinaria de 2026); Dña. {old.v} dimitió.")
    for c in rng.sample([x for x in por["c1"] if "pres" not in x["ocup"]], 5):
        d = doc(c, "acta-extraordinaria-2025-fachada")
        old = hit(d, r"en (\d+) cuotas mensuales")
        if not old:
            continue
        nuevo = rng.choice([x for x in (18, 24, 36) if x != int(old.v)])
        ruta = acta(c, "derrama-fachada", [f"Modificación de la forma de pago de la derrama de la fachada. Para aliviar la carga de los propietarios, la derrama extraordinaria aprobada en febrero de 2025 pasa a pagarse en {nuevo} cuotas mensuales, sin variación del importe total.",
                                           "Ruegos y preguntas. No se formulan."])
        c["ocup"].add("derr")
        rec = {"tipo": "derrama", "c": c, "nuevo": ruta, "viejos": [d], "old": int(old.v), "new": nuevo}
        AMP.append(rec)
        rec["kid"] = nueva_clave("dato_desactualizado", [fh(old), fh(hit(ruta, r"pasa a pagarse en \d+ cuotas mensuales"))], ruta, f"La derrama de la fachada se paga en {nuevo} cuotas mensuales (acta de 2026), no en {old.v}.")
    for c in rng.sample(por["c2"], 5):
        d = doc(c, "estatutos")
        old = hit(d, r"en horario de (\d{2}:\d{2}) a (\d{2}:\d{2})")
        if not old:
            continue
        o_ini, o_fin = old.m.group(1), old.m.group(2)
        n_ini, n_fin = rng.choice([x for x in (("10:00", "22:00"), ("09:00", "20:00"), ("12:00", "23:00"), ("10:00", "19:00")) if x[1] != o_fin and x[0] != o_ini])
        ruta = acta(c, "horario-piscina", [f"Horario de la piscina. Para la temporada de baño de 2026, el horario de la piscina comunitaria pasa a ser de {n_ini} a {n_fin}. El resto de las normas de uso no varían.",
                                           "Ruegos y preguntas. No se formulan."])
        c["ocup"].add("pisc_h")
        rec = {"tipo": "piscina_h", "c": c, "nuevo": ruta, "viejos": [d], "old": o_fin, "new": n_fin, "old_ini": o_ini, "new_ini": n_ini}
        AMP.append(rec)
        rec["kid"] = nueva_clave("dato_desactualizado", [fh(old), fh(hit(ruta, r"pasa a ser de \d{2}:\d{2} a \d{2}:\d{2}"))], ruta, f"En 2026 la piscina cierra a las {n_fin} (acuerdo de la junta); el horario de los estatutos era de {o_ini} a {o_fin}.")
    for c in rng.sample([x for x in por["c2"] if "pisc_h" not in x["ocup"]], 5):
        d = doc(c, "estatutos")
        old = hit(d, r"un máximo de (\d) personas por día")
        if not old:
            continue
        nuevo = rng.choice([x for x in (2, 3, 5, 6, 8) if x != int(old.v)])
        ruta = acta(c, "invitados-piscina", [f"Invitados en la piscina. La Junta acuerda modificar el límite de invitados: cada vivienda podrá invitar a un máximo de {nuevo} personas por día a la piscina, que deberán ir acompañadas por un propietario.",
                                             "Ruegos y preguntas. No se formulan."])
        c["ocup"].add("invit")
        rec = {"tipo": "invitados", "c": c, "nuevo": ruta, "viejos": [d], "old": int(old.v), "new": nuevo}
        AMP.append(rec)
        rec["kid"] = nueva_clave("dato_desactualizado", [fh(old), fh(hit(ruta, r"un máximo de \d personas por día"))], ruta, f"Cada vivienda puede invitar a un máximo de {nuevo} personas por día (acuerdo de 2026); los estatutos decían {old.v}.")

    # ---- presupuestos anuales con tablas
    def limp_actual(c):
        for a in AMP:
            if a["c"] is c and a["tipo"] == "anexo_limpieza":
                return a["imp_new"]
        h = hc(c, "contrato-limpieza", r"importe mensual de ([\d.]+) euros")
        return num(h.v) if h else None

    def cuota_actual(c):
        for a in reversed(AMP):
            if a["c"] is c and a["tipo"] == "anexo_ascensor":
                return a["cuota_new"]
        if c["tpl"] == "c1":
            h = hc(c, "anexo-1-contrato-ascensor", r"pasa a ser de ([\d.]+) euros")
        else:
            h = hc(c, "contrato-ascensor", r"cuota mensual de ([\d.]+) euros")
        return num(h.v) if h else None

    for c in rng.sample(copias, 30):
        limp = limp_actual(c)
        if c["tpl"] == "c2":
            h = hc(c, "contrato-mantenimiento-piscina", r"importe de ([\d.]+) euros por temporada")
            otro = ("Mantenimiento de la piscina", num(h.v) if h else None)
        else:
            q = cuota_actual(c)
            otro = ("Mantenimiento del ascensor", q * 12 if q else None)
        prima = None
        for a in AMP:
            if a["c"] is c and a["tipo"] == "poliza":
                prima = a["prima"]
        if prima is None and c["tpl"] == "c3":
            h = hc(c, "poliza-seguro-2024", r"Prima anual total, impuestos y recargos incluidos: ([\d.]+) euros")
            prima = num(h.v) if h else None
        if prima is None:
            prima = rng.randrange(1500, 4300, 10)
        if limp is None or otro[1] is None:
            continue
        filas = [("Limpieza", limp * 12), otro, ("Seguro comunitario", prima),
                 ("Electricidad de zonas comunes", rng.randrange(1800, 3700, 10)), ("Fondo de reserva", rng.randrange(900, 2300, 10))]
        total = sum(v for _, v in filas)
        ruta = add_doc(c, "presupuesto-2026.txt", T.presupuesto(emp_nombre(c), tomador_de(c), 2026, [(n, eur(v)) for n, v in filas], eur(total), rng))
        AMP.append({"tipo": "presupuesto", "c": c, "nuevo": ruta, "filas": filas, "total": total})

    # ---- comunicaciones a los vecinos
    for c in rng.sample(copias, 34):
        tipo = rng.choice(["junta", "cuota", "obras"])
        y = 2026
        m = rng.randint(1, 11)
        hoy = fecha(rng.randint(1, 20), m, y)
        if tipo == "junta":
            d, hora = rng.randint(21, 28), rng.choice(["17:30", "18:00", "18:30", "19:00", "19:30", "20:00"])
            lugar = rng.choice(["el salón social de la comunidad", "el portal del edificio", "la sala de reuniones de la oficina de la administración"])
            tema = rng.choice(["la renovación de las zonas ajardinadas", "la sustitución de las luminarias comunes", "la pintura del portal y la escalera", "el cambio de la puerta de acceso al garaje"])
            ruta = add_doc(c, "comunicacion-junta-extraordinaria.txt", T.comunicacion_junta(emp_nombre(c), tomador_de(c), hoy, fecha(d, m, y), hora, lugar, tema, rng))
            AMP.append({"tipo": "com_junta", "c": c, "nuevo": ruta, "hora": hora, "fecha": fecha(d, m, y), "tema": tema, "lugar": lugar})
        elif tipo == "cuota":
            limite, cuota = fecha(rng.randint(5, 15), m + 1 if m < 12 else 12, y), rng.randrange(120, 430, 5)
            ruta = add_doc(c, "comunicacion-recibo-trimestral.txt", T.comunicacion_cuota(emp_nombre(c), tomador_de(c), hoy, limite, eur(cuota), rng))
            AMP.append({"tipo": "com_cuota", "c": c, "nuevo": ruta, "limite": limite, "cuota": cuota})
        else:
            empresa = rng.choice(["Reformas Faro S.L.", "Mantenimientos Costa S.L.", "Pinturas del Muelle S.L.", "Servicios Tramontana S.L."])
            inicio, dias = fecha(rng.randint(2, 25), m, y), rng.randint(3, 15)
            zona = rng.choice(["la fachada del patio interior", "la escalera y el portal", "la cubierta del edificio", "el cuarto de contadores"])
            ruta = add_doc(c, "comunicacion-obras-mantenimiento.txt", T.comunicacion_obras(emp_nombre(c), tomador_de(c), hoy, empresa, inicio, dias, zona, rng))
            AMP.append({"tipo": "com_obras", "c": c, "nuevo": ruta, "empresa": empresa, "inicio": inicio, "dias": dias, "zona": zona})

    # ---- dos avisos que no cuadran
    for c in rng.sample([x for x in copias if x["tpl"] != "c2" or True], 8):
        dia = fecha(rng.randint(2, 27), rng.randint(1, 11), 2026)
        a = rng.choice([8, 9])
        b = a + 2
        r1 = add_doc(c, "aviso-corte-agua-administracion.txt", T.aviso_corte("administración", tomador_de(c), dia, f"{a}:00", f"{a + 4}:00", rng))
        r2 = add_doc(c, "aviso-corte-agua-suministradora.txt", T.aviso_corte("empresa suministradora", tomador_de(c), dia, f"{b}:00", f"{b + 4}:00", rng))
        rec = {"tipo": "avisos", "c": c, "docs": [r1, r2], "dia": dia, "a": a, "b": b}
        AMP.append(rec)
        rec["kid"] = nueva_clave("casos_distintos", [fh(hit(r1, r"de \d+:00 a \d+:00")), fh(hit(r2, r"de \d+:00 a \d+:00"))], None,
                                 f"Los dos avisos no coinciden: la administración dice de {a}:00 a {a + 4}:00 y la empresa suministradora de {b}:00 a {b + 4}:00; hay que enseñar los dos.")

    # ---- clave de casos resueltos de forma distinta (base)
    c1b = next(c for c in comms if c["key"] == "c1-olmo14")
    c3b = next(c for c in comms if c["key"] == "c3-puerto3")
    nueva_clave("casos_distintos", [fh(hc(c1b, "filtracion-azotea", r"Se dio parte a Seguros Atalaya con cargo a la póliza ATL-4471-2023")),
                                   fh(hc(c3b, "filtracion-cubierta", r"sin dar parte al seguro"))], None,
                "Los dos casos de filtración se resolvieron de forma distinta: en Calle Olmo 14 se dio parte al seguro y la aseguradora reparó; en Avenida del Puerto 3 la comunidad pagó la reparación sin dar parte al seguro.")
    return por


def normas_y_procedimientos():
    """Documentos sin proyecto (normas internas) y proyecto abierto de Ribera."""
    N = {}
    R, A = EMPRESAS["ribera"][0], EMPRESAS["albar"][0]

    def n(emp, clave, nombre, titulo, codigo, vigencia, cuerpo, sustituye=None):
        N[(emp, clave)] = add_general(emp, nombre, T.norma(titulo, codigo, vigencia, cuerpo, rng, sustituye))

    n("ribera", "sin24", "norma-gestion-siniestros-2024.txt", "Norma interna de gestión de siniestros", "NI-01", "desde el 1 de enero de 2024", [
        "Plazo de comunicación. El plazo máximo para comunicar un siniestro a la aseguradora es de 7 días naturales desde que el despacho tiene conocimiento del hecho.",
        "Documentación. Se adjuntarán fotografías, el parte del comunicante y la póliza afectada."])
    n("ribera", "sin25", "norma-gestion-siniestros-2025.txt", "Norma interna de gestión de siniestros", "NI-01", "desde el 1 de enero de 2025", [
        "Plazo de comunicación. El plazo máximo para comunicar un siniestro a la aseguradora es de 5 días naturales desde que el despacho tiene conocimiento del hecho.",
        "Documentación. Se adjuntarán fotografías, el parte del comunicante y la póliza afectada."], sustituye="norma-gestion-siniestros-2024")
    n("ribera", "atencion", "norma-atencion-vecinos.txt", "Norma interna de atención a los propietarios", "NI-02", "desde el 1 de marzo de 2024", [
        "Horario. El horario de atención telefónica a los propietarios es de 9:30 a 14:00, de lunes a viernes.",
        "Correo. Los correos de propietarios se contestan en un máximo de dos días laborables."])
    n("ribera", "gastos", "norma-aprobacion-gastos.txt", "Norma interna de aprobación de gastos", "NI-03", "desde el 1 de junio de 2024", [
        "Presupuestos. Todo gasto superior a 600 euros requiere tres presupuestos comparables y la autorización del responsable de administración.",
        "Urgencias. En caso de avería urgente podrá prescindirse de los presupuestos, con aviso posterior a la presidencia."])
    n("ribera", "llaves", "norma-custodia-llaves.txt", "Norma interna de custodia de llaves", "NI-04", "desde el 1 de febrero de 2024", [
        "Custodia. Las llaves de los cuartos técnicos y de las azoteas se custodian en la caja fuerte de la oficina, con registro de entrada y salida.",
        "Entrega. Solo se entregan a personal autorizado por la presidencia de cada comunidad."])
    n("ribera", "archivo", "norma-archivo-documentos.txt", "Norma interna de archivo y conservación", "NI-05", "desde el 1 de enero de 2024", [
        "Conservación. Los expedientes de cada comunidad se conservan durante 10 años después de la baja de la comunidad como cliente.",
        "Formato. Se archivan en papel y en copia digital."])
    n("ribera", "urg24", "norma-avisos-urgentes-2024.txt", "Norma interna de avisos urgentes", "NI-06", "desde el 1 de abril de 2024", [
        "Respuesta. Los avisos urgentes de los propietarios se atienden en un máximo de 4 horas desde su recepción."])
    n("ribera", "urg25", "norma-avisos-urgentes-2025.txt", "Norma interna de avisos urgentes", "NI-06", "desde el 1 de abril de 2025", [
        "Respuesta. Los avisos urgentes de los propietarios se atienden en un máximo de 2 horas desde su recepción."], sustituye="norma-avisos-urgentes-2024")
    n("ribera", "conv", "norma-convocatoria-juntas.txt", "Norma interna de convocatoria de juntas", "NI-07", "desde el 1 de enero de 2024", [
        "Antelación. Las convocatorias de junta se remiten a los propietarios con 8 días de antelación como mínimo."])
    n("albar", "sin", "norma-gestion-siniestros.txt", "Norma interna de gestión de siniestros", "GA-01", "desde el 1 de enero de 2025", [
        "Plazo de comunicación. El plazo máximo para comunicar un siniestro a la aseguradora es de 10 días naturales desde que la gestora tiene conocimiento del hecho."])
    n("albar", "atencion", "norma-atencion-vecinos.txt", "Norma interna de atención a los propietarios", "GA-02", "desde el 1 de marzo de 2025", [
        "Horario. El horario de atención telefónica a los propietarios es de 10:00 a 13:30, de lunes a jueves."])
    n("albar", "gastos", "norma-aprobacion-gastos.txt", "Norma interna de aprobación de gastos", "GA-03", "desde el 1 de junio de 2025", [
        "Presupuestos. Todo gasto superior a 450 euros requiere dos presupuestos comparables y la autorización de la gerencia."])
    n("albar", "urg24", "norma-avisos-urgentes-2024.txt", "Norma interna de avisos urgentes", "GA-04", "desde el 1 de abril de 2024", [
        "Respuesta. Los avisos urgentes de los propietarios se atienden en un máximo de 6 horas desde su recepción."])
    n("albar", "urg25", "norma-avisos-urgentes-2025.txt", "Norma interna de avisos urgentes", "GA-04", "desde el 1 de abril de 2025", [
        "Respuesta. Los avisos urgentes de los propietarios se atienden en un máximo de 3 horas desde su recepción."], sustituye="norma-avisos-urgentes-2024")
    n("albar", "archivo", "norma-archivo-documentos.txt", "Norma interna de archivo y conservación", "GA-05", "desde el 1 de enero de 2025", [
        "Conservación. Los expedientes de cada comunidad se conservan durante 8 años después de la baja de la comunidad como cliente."])
    n("albar", "conv", "norma-convocatoria-juntas.txt", "Norma interna de convocatoria de juntas", "GA-06", "desde el 1 de enero de 2025", [
        "Antelación. Las convocatorias de junta se remiten a los propietarios con 12 días de antelación como mínimo."])

    P = {}

    def p(clave, nombre, titulo, cuerpo):
        P[clave] = add_general("ribera", nombre, T.norma(titulo, "PG-" + clave.upper()[:3], "vigente", cuerpo, rng), "procedimientos-generales")

    p("alta", "procedimiento-alta-proveedores.txt", "Procedimiento general de alta de proveedores", [
        "Requisitos. El alta de un proveedor exige dos referencias de otras administraciones y el visto bueno del responsable de administración.",
        "Plazo. El alta se tramita en un máximo de 5 días hábiles."])
    p("atrap", "procedimiento-atrapamiento-ascensor.txt", "Procedimiento general ante personas atrapadas en un ascensor", [
        "Actuación. Se avisa de inmediato a la empresa conservadora. Si no responde en 30 minutos, se avisa a los bomberos."])
    p("impag", "procedimiento-recibos-impagados.txt", "Procedimiento general de recibos impagados", [
        "Reclamación. Cuando un recibo lleva 45 días vencido se envía una carta certificada al propietario con el detalle de la deuda."])
    p("extint", "procedimiento-revision-extintores.txt", "Procedimiento general de revisión de extintores", [
        "Calendario. Los extintores de todas las comunidades se revisan una vez al año, en el mes de enero."])
    p("actas", "procedimiento-envio-actas.txt", "Procedimiento general de envío de actas", [
        "Plazo. El acta de cada junta se remite a los propietarios en un máximo de 15 días tras la reunión."])
    p("mudanzas", "procedimiento-mudanzas.txt", "Procedimiento general de mudanzas", [
        "Aviso. Las mudanzas deben comunicarse a la administración con 48 horas de antelación."])
    return N, P


# ----------------------------------------------------------------------------- escritura
def main():
    import resto
    resto.G = sys.modules[__name__]
    tmp, destino, escala = construir_base()
    comms = comunidades(destino, escala)
    cargar_base(comms)
    ampliacion(comms)
    N, P = normas_y_procedimientos()
    resto.claves_naturales(comms)
    fichas, hechos, uni = resto.construir_fichas(comms)
    for c, hecho, h, w, f in hechos:
        f["_k"] = nueva_clave("ficha_contra_documento", [fh(h), {"ficha": f["id"], "frase": f["texto"]}], None,
                              f"El documento dice «{h.lit}» y la ficha {f['id']} dice otra cosa ({w}); ninguna fuente prevalece y hay que enseñar las dos.")
    resto.estudio(comms)
    resto.copias(comms)
    resto.pregs_ampliacion(comms, N, P)
    resto.pregs_fichas(comms, fichas, hechos, uni)
    ribera_c = [c for c in comms if c["emp"] == "ribera" and not c["base"] and not c["ambiguo"]]
    t02 = sorted(c["key"] for c in rng.sample(ribera_c, 10))
    resto.pregs_aislamiento(comms, t02, P)
    resto.sin_fuente(comms)
    for i, q in enumerate(resto.QS, 1):
        q["id"] = f"Q{i:03d}"
    for kid, qs in resto.K_PREG.items():
        next(k for k in CLAVES if k["id"] == kid)["preguntas"] += [q["id"] for q in qs]
    for c, hecho, h, w, f in hechos:
        if "_q" in f:
            next(k for k in CLAVES if k["id"] == f["_k"])["preguntas"].append(f["_q"]["id"])

    # ---- manifiesto
    proyectos = [{"clave": c["key"], "empresa": c["emp"], "nombre": c["nombre"], "abierto": False} for c in comms]
    proyectos.append({"clave": "procedimientos-generales", "empresa": "ribera", "nombre": "Procedimientos generales", "abierto": True})
    trabajadores = [
        {"clave": "t01", "empresa": "ribera", "nombre": "Marta", "apellidos": "Olivares Ríos", "proyectos": ["*"]},
        {"clave": "t02", "empresa": "ribera", "nombre": "Javier", "apellidos": "Bermúdez Lara", "proyectos": t02},
        {"clave": "t03", "empresa": "ribera", "nombre": "Lorena", "apellidos": "Cano Vidal", "proyectos": ["*"]},
        {"clave": "t04", "empresa": "ribera", "nombre": "Raúl", "apellidos": "Soler Mena", "proyectos": ["*"]},
        {"clave": "t05", "empresa": "ribera", "nombre": "Daniela", "apellidos": "Arias Peña", "proyectos": ["*"]},
        {"clave": "t06", "empresa": "albar", "nombre": "Ignacio", "apellidos": "Salas Ferrer", "proyectos": ["*"]},
        {"clave": "t07", "empresa": "albar", "nombre": "Paula", "apellidos": "Núñez Gil", "proyectos": ["*"]},
        {"clave": "t08", "empresa": "albar", "nombre": "Gonzalo", "apellidos": "Pardo Ibáñez", "proyectos": ["*"]},
    ]
    fichas_out = [{k: v for k, v in f.items() if not k.startswith("_") and k not in ("tema", "clave")} for f in sorted(fichas, key=lambda f: f["id"])]
    manifiesto = {"semilla": 7, "empresas": [{"clave": k, "nombre": v[0], "codigo": v[1]} for k, v in EMPRESAS.items()], "proyectos": proyectos,
                  "trabajadores": trabajadores, "documentos": [META[r] for r in sorted(META)], "fichas": fichas_out}

    corpus = DATOS / "corpus"
    if corpus.exists():
        shutil.rmtree(corpus)
    for ruta, texto in DOCS.items():
        f = corpus / ruta
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(texto, encoding="utf-8")
    DATOS.mkdir(exist_ok=True)
    for nombre, obj in (("manifiesto.json", manifiesto), ("claves.json", CLAVES), ("bateria.json", resto.QS)):
        (DATOS / nombre).write_text(json.dumps(obj, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    shutil.rmtree(tmp)
    INFO["descartadas"] = dict(resto.DESC)
    (HERE / "_info.json").write_text(json.dumps({k: (v if k != "escala_fugas" else v) for k, v in INFO.items()}, ensure_ascii=False, indent=1), encoding="utf-8")
    n_exp, n_preg = expedientes.generar(sys.modules[__name__], comms, DATOS)
    print("expedientes:", n_exp, "| preguntas de expediente:", n_preg)
    print("escala.py:", INFO["escala_documentos"], "documentos,", INFO["escala_comunidades"], "comunidades, fugas:", INFO["escala_fugas"] or "ninguna")
    print("documentos totales:", len(DOCS), "| fichas:", len(fichas_out), "| claves:", len(CLAVES), "| preguntas:", len(resto.QS), "| descartadas:", dict(resto.DESC))


if __name__ == "__main__":
    main()
