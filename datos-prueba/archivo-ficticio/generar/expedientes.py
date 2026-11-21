"""Expedientes ficticios de casos y sus 40 preguntas. Lo llama generar.py al final; escribe solo datos/expedientes/,
datos/expedientes.json y datos/bateria-expedientes.json, con su propia semilla (13) para no alterar lo ya generado."""
import datetime
import json
import random
import re
import shutil
import struct
import zlib
from pathlib import Path

import textos as T
from textos import eur, fecha

PROYECTOS = {"ribera": ["c1-calle-abeto-16", "c1-calle-pino-26", "c1-calle-roble-46", "c2-los-naranjos-2"],
             "albar": ["c3-avenida-del-muelle-33", "c3-avenida-del-puerto-14"]}
TAMANOS = [14, 6, 12, 7, 13, 5, 8, 6, 9, 5, 7, 6]   # documentos propios por caso, en orden de proyecto
SUSTITUIDOS = {1, 3, 6, 8}                          # casos cuyo primer presupuesto se sustituye
ORDEN = ["informe-perito", "presupuesto-1", "factura", "correo-1", "foto-1", "presupuesto-2", "correo-2", "foto-2",
         "parte-trabajo", "correo-3", "foto-3", "presupuesto-3", "correo-4", "foto-4"]
MESES = {m: i for i, m in enumerate(T.MESES, 1)}


def png(semilla):
    """PNG sin texto (48x32) sin comprimir: determinista y sin dependencias."""
    filas = b"".join(b"\0" + bytes(((x * 5 + semilla * 37) % 256, (y * 7 + semilla * 11) % 256, (x + y + semilla * 53) % 256)[i] for x in range(48) for i in range(3)) for y in range(32))
    trozo = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d))
    return b"\x89PNG\r\n\x1a\n" + trozo(b"IHDR", struct.pack(">IIBBBBB", 48, 32, 8, 2, 0, 0, 0)) + trozo(b"IDAT", zlib.compress(filas, 0)) + trozo(b"IEND", b"")


def paginado(pags):
    return "\n".join(f"=== Página {i} ===\n{p.strip()}\n" for i, p in enumerate(pags, 1))


def redondeo(v):
    return int(round(v / 10) * 10)


def hechos_de(G, c, ruta_caso, i, r):
    nombre = ruta_caso.rsplit("/", 1)[1]
    anio, num, tipo = re.match(r"caso-(\d{4})-(\d+)-(.+)\.txt", nombre).groups()
    texto = G.DOCS[ruta_caso]
    d, m, y = re.search(r"Fecha de apertura: (\d+) de (\w+) de (\d{4})", texto).groups()
    base = datetime.date(int(y), MESES[m], int(d))
    f = lambda k: (lambda t: fecha(t.day, t.month, t.year))(base + datetime.timedelta(days=k))
    importe = re.search(r"([\d.]+) euros", texto)
    total = int(importe.group(1).replace(".", "")) if importe else redondeo(r.uniform(1200, 6000))
    trabajo, causa = T.OBRAS[tipo]
    p = r.sample(T.PROVEEDORES, 3)
    return {"caso": ruta_caso, "anio": anio, "num": num, "f": T.formas_numero(anio, num), "com": c["nombre"], "emp": G.emp_nombre(c),
            "trabajo": trabajo, "causa": causa, "perito": T.persona(r), "provs": p, "estimado": redondeo(total * 0.97),
            "t1": total, "t2": redondeo(total * 1.18), "t3": redondeo(total * 1.31), "t1r": redondeo(total * 0.93),
            "fechas": {k: f(v) for k, v in {"informe": 6, "ppto1": 12, "ppto2": 13, "revisado": 19, "parte": 30, "factura": 40}.items()},
            "sust": i in SUSTITUIDOS, "tam": TAMANOS[i], "fact": f"F-{anio}-{r.randint(100, 999)}"}


def filas_de(total):
    a, b = redondeo(total * 0.55), redondeo(total * 0.35)
    return [("Mano de obra", a), ("Materiales", b), ("Gestión de residuos y medios auxiliares", total - a - b)]


def documentos_de(x, r):
    """[(nombre sin extensión de ruta, páginas | None si es foto)] en orden; los primeros `tam`."""
    f, pr = x["f"], x["provs"]
    acept = x["t1r"] if x["sust"] else x["t1"]
    cuerpo = lambda t: f"Buenos días: {t} Un saludo."
    docs = {
        "informe-perito": T.informe_perito(x),
        "presupuesto-1": T.presupuesto_exp(x, pr[0], f[2], filas_de(x["t1"]), x["t1"], x["fechas"]["ppto1"]),
        "presupuesto-2": T.presupuesto_exp(x, pr[1], f[2], filas_de(x["t2"]), x["t2"], x["fechas"]["ppto2"]),
        "presupuesto-3": T.presupuesto_exp(x, pr[2], f[1], filas_de(x["t3"]), x["t3"], x["fechas"]["ppto2"]),
        "presupuesto-1-revisado": T.presupuesto_exp(x, pr[0], f[2], filas_de(x["t1r"]), x["t1r"], x["fechas"]["revisado"], sustituye=x["fechas"]["ppto1"]),
        "factura": T.factura_exp(x, pr[0], f[1], acept, x["fechas"]["factura"], x["fact"]),
        "parte-trabajo": T.parte_trabajo(x, pr[0], f[1], x["fechas"]["parte"], r.randint(3, 9)),
    }
    asuntos = [("conserje", "Aviso de la incidencia", "Les aviso de que persiste el problema en la zona afectada."),
               ("presidencia", "Autorización del presupuesto", "La presidencia autoriza el presupuesto de la empresa elegida."),
               ("perito", "Fecha de la visita", "Confirmo la visita del perito para la próxima semana."),
               ("conserje", "Aviso a los vecinos", "He avisado a los vecinos afectados de las fechas de la obra.")]
    for k, (de, asunto, texto) in enumerate(asuntos, 1):
        docs[f"correo-{k}"] = T.correo_impreso(x, f"{de} de {x['com']}", x["emp"], x["fechas"]["informe"], asunto, cuerpo(texto), f[3] if k == 1 else f[k % 4])
    orden = list(ORDEN)
    if x["sust"]:
        orden.insert(2, "presupuesto-1-revisado")
    return orden[:x["tam"]], docs


def montar_caso(i, c, ruta_caso, G, r):
    x = hechos_de(G, c, ruta_caso, i, r)
    base = f"{c['emp']}/{c['key']}/expte-{x['anio']}-{x['num']}-"
    orden, docs = documentos_de(x, r)
    foto = lambda n: n.startswith("foto")
    x["textos"] = {f"{base}{n}.{'png' if foto(n) else 'txt'}": png(i * 20 + k) if foto(n) else paginado(docs[n]) for k, n in enumerate(orden)}
    x["rutas"], x["orden"] = list(x["textos"]), orden
    e = {"caso": ruta_caso, "proyecto": c["key"], "documentos": x["rutas"], "compartidos": [], "ajenos": [],
         "formas_numero": x["f"], "sustituciones": []}
    return e, x


def seleccionar_casos(G, comms):
    """Los dos primeros casos de cada proyecto elegido."""
    por_clave = {c["key"]: c for c in comms}
    return [(por_clave[k], d) for emp, claves in PROYECTOS.items() for k in claves
            for d in sorted(x for x in G.DOCS if x.startswith(f"{emp}/{k}/caso-"))[:2]]


def fijar_sustituciones(exps, hechos):
    for e, x in zip(exps, hechos):
        if x["sust"]:
            e["sustituciones"] = [[x["rutas"][1], x["rutas"][x["orden"].index("presupuesto-1-revisado")]]]
        else:
            del e["sustituciones"]


def generar(G, comms, datos):
    r = random.Random(13)
    montados = [montar_caso(i, c, d, G, r) for i, (c, d) in enumerate(seleccionar_casos(G, comms))]
    exps, hechos = [m[0] for m in montados], [m[1] for m in montados]
    compartidos(G, comms, exps, hechos, r)
    ajenos(G, {c["key"]: c for c in comms}, exps, hechos, r)
    fijar_sustituciones(exps, hechos)
    escribir(datos / "expedientes", hechos)
    preguntas = construir_preguntas(exps, hechos)
    for nombre, obj in (("expedientes.json", exps), ("bateria-expedientes.json", preguntas)):
        (datos / nombre).write_text(json.dumps(obj, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return len(exps), len(preguntas)


def compartidos(G, comms, exps, hechos, r):
    nuevos = {}
    for emp in PROYECTOS:
        general = f"{emp}/_general/expte-protocolo-peritajes.txt"
        nuevos[general] = paginado(T.protocolo_peritajes(G.EMPRESAS[emp][0]))
    for e, x in zip(exps, hechos):
        carpeta = "/".join(e["caso"].split("/")[:2])
        con, pol = f"{carpeta}/expte-contrato-perito.txt", f"{carpeta}/expte-poliza-obras.txt"
        if con not in nuevos:
            nuevos[con] = paginado(T.contrato_perito(x["com"], r.choice([70, 80, 85, 90, 95]), r.choice([30, 40, 45]), x["emp"]))
            nuevos[pol] = paginado(T.poliza_obras(x["com"], f"OBR-{r.randint(1000, 9999)}-2024", r.randrange(300, 900, 10), x["emp"]))
        e["compartidos"] = [con, pol, f"{e['caso'].split('/')[0]}/_general/expte-protocolo-peritajes.txt"]
    hechos[0].setdefault("extra", {}).update(nuevos)


def ajenos(G, por_clave, exps, hechos, r):
    """Un señuelo por proyecto: un correo en el proyecto vecino de su empresa que cita el número del primer caso del proyecto."""
    vistos = set()
    for e, x in zip(exps, hechos):
        emp, k = e["caso"].split("/")[:2]
        if k in vistos:
            continue
        vistos.add(k)
        lista = PROYECTOS[emp]
        otro = lista[(lista.index(k) + 1) % len(lista)]
        ruta = f"{emp}/{otro}/correo-proveedor-ref-{x['num']}.txt"
        hechos[0]["extra"][ruta] = paginado(T.correo_ajeno(por_clave[otro]["nombre"], x["f"][2], x["emp"]))
        e["ajenos"].append(ruta)


def escribir(destino, hechos):
    if destino.exists():
        shutil.rmtree(destino)
    archivos = {r: t for x in hechos for r, t in x["textos"].items()} | hechos[0]["extra"]
    for ruta, contenido in archivos.items():
        f = destino / ruta
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_bytes(contenido if isinstance(contenido, bytes) else contenido.encode("utf-8"))


# ----------------------------------------------------------------------------- preguntas
def pagina_de(x, ruta):
    return x["textos"][ruta] if ruta in x["textos"] else x["extra"][ruta]


def ancla(x, tipo, quien=None):
    return {"informe-perito": T.l_perito(x["perito"]), "factura": T.l_total_fact(x["t1r"] if x["sust"] else x["t1"]),
            "presupuesto-2": T.l_total_ppto(x["t2"]), "presupuesto-1": T.l_total_ppto(x["t1"]),
            "presupuesto-1-revisado": T.l_total_ppto(x["t1r"])}[tipo]


def cita(x, tipo):
    ruta = x["rutas"][x["orden"].index(tipo)]
    a = ancla(x, tipo)
    paginas = [int(n) for n, t in re.findall(r"=== Página (\d+) ===\n(.*?)(?==== Página|\Z)", x["textos"][ruta], re.S) if a in t]
    return {"doc": ruta, "paginas": paginas, "ancla": a}


def base(e, x, sub, pregunta, claves, esperados, citas, **kw):
    q = {"id": "", "tipo": "expediente", "origen": "expediente", "actor": "t01" if e["caso"].startswith("ribera") else "t06",
         "pregunta": pregunta, "citas_resueltas": citas, "fichas_esperadas": [], "todas_las_citas": True, "claves": claves,
         "abstener": False, "prohibidas": [], "sustituidos": [], "docs_prohibidos": list(e["ajenos"]), "caso": e["caso"],
         "esperados": esperados, "subtipo": sub, "concreta": sub == "concreta"}
    return q | kw


def utiles(e, x, con_compartidos):
    """Documentos de texto del expediente que no están sustituidos."""
    viejos = {v for v, _ in e.get("sustituciones", [])}
    propios = [d for d in e["documentos"] if d.endswith(".txt") and d not in viejos]
    return propios + (e["compartidos"] if con_compartidos else [])


def ref(x, i):
    return f"{x['f'][(0, 1, 3)[i % 3]]} de {x['com']}"


def importe(x):
    return eur(x["t1r"] if x["sust"] else x["t1"]).split()[0]


def preguntas_contiene(exps, hechos):
    return [base(exps[i], hechos[i], "contiene", f"¿Qué documentos tiene el {ref(hechos[i], i)}?", [["informe"], ["factura"]],
                 utiles(exps[i], hechos[i], True), [cita(hechos[i], "informe-perito"), cita(hechos[i], "factura")])
            for i in [0, 2, 4, 6, 8, 10, 3, 5]]


PLANTILLAS_ANALISIS = [  # preguntan por el expediente entero: una respuesta correcta cita todos sus documentos
    ("Resume todo el {ref}: qué pasó, quién intervino, qué presupuestos hubo y cuánto se pagó. Cita cada documento en el que te apoyes.",
     lambda x: [[importe(x)], [x["perito"]]]),
    ("Haz la cronología de todo el {ref}: informe, presupuestos, obra y factura, con la empresa que la hizo y la fecha de la factura. Cita cada documento.",
     lambda x: [[x["provs"][0]], [x["fechas"]["factura"]]]),
    ("Explica todo el {ref}: causa de la incidencia, presupuestos comparados, empresa elegida e importe final. Cita cada documento.",
     lambda x: [[x["causa"][:30]], [importe(x)]]),
]


def preguntas_analisis(exps, hechos):
    P = []
    for k, i in enumerate([1, 3, 5, 7, 9, 11, 0, 2, 4, 6]):
        texto, claves = PLANTILLAS_ANALISIS[k % 3]
        x = hechos[i]
        P.append(base(exps[i], x, "analisis", texto.format(ref=ref(x, i)), claves(x), utiles(exps[i], x, False),
                      [cita(x, "informe-perito"), cita(x, "factura")]))
    return P


def preguntas_concretas(exps, hechos):
    P = []
    for i in [0, 2, 4]:
        e, x = exps[i], hechos[i]
        p2, fa = x["rutas"][x["orden"].index("presupuesto-2")], x["rutas"][x["orden"].index("factura")]
        P.append(base(e, x, "concreta", f"¿Cuánto cuesta el presupuesto de {x['provs'][1]} en el {ref(x, i)}?", [[eur(x["t2"]).split()[0]]],
                      [p2], [cita(x, "presupuesto-2")]))
        P.append(base(e, x, "concreta", f"¿En qué fecha se emitió la factura del {ref(x, i)}?", [[x["fechas"]["factura"]]], [fa], [cita(x, "factura")]))
    return P


AUSENTES = [("¿Qué garantía ofrece la empresa que hizo la obra del {ref}?", ["garantía"]),
            ("¿Cuál es el número de colegiado del perito del {ref}?", ["colegiado"]),
            ("¿Qué licencia de obras se pidió en el {ref}?", ["licencia"]),
            ("¿A qué IBAN se pagó la factura del {ref}?", ["IBAN"]),
            ("¿Se presentó una reclamación judicial en el {ref}?", ["judicial"]),
            ("¿Qué matrícula tiene la furgoneta de la empresa del {ref}?", ["matrícula"])]


def preguntas_no_consta(exps, hechos):
    return [base(exps[i], hechos[i], "no_consta", texto.format(ref=ref(hechos[i], i)), [], [], [], abstener=True,
                 todas_las_citas=False, ausente=ausente)
            for (texto, ausente), i in zip(AUSENTES, [1, 3, 5, 7, 9, 11])]


def preguntas_sustituidos(exps, hechos):
    P = []
    for i in sorted(SUSTITUIDOS):
        e, x = exps[i], hechos[i]
        viejo, nuevo = e["sustituciones"][0]
        P.append(base(e, x, "sustituidos", f"¿Cuál es el importe vigente del presupuesto de {x['provs'][0]} en el {ref(x, i)}?",
                      [[eur(x["t1r"]).split()[0]]], [nuevo], [cita(x, "presupuesto-1-revisado")],
                      prohibidas=[eur(x["t1"]).split()[0]], sustituidos=[viejo]))
    return P


def preguntas_ajenos_y_compartidos(exps, hechos):
    P = []
    for i in [0, 4, 8]:
        e, x = exps[i], hechos[i]
        P.append(base(e, x, "ajenos", f"Busca todo lo que cite «{x['f'][2]}» y dime qué documentos forman parte del expediente de {x['com']} ({x['f'][0]}).",
                      [["informe"]], utiles(e, x, True), [cita(x, "informe-perito")]))
    for i in [0, 4, 8]:
        e, x, y = exps[i], hechos[i], hechos[i + 1]
        con, pol = e["compartidos"][:2]
        P.append(base(e, x, "compartidos", f"¿Qué documentos comparten los expedientes {x['f'][0]} y {y['f'][0]} de {x['com']}?",
                      [["contrato"], ["póliza"]], e["compartidos"],
                      [{"doc": con, "paginas": [1], "ancla": "Tarifa del perito"}, {"doc": pol, "paginas": [1], "ancla": "Prima anual"}]))
    return P


def construir_preguntas(exps, hechos):
    P = [q for f in (preguntas_contiene, preguntas_analisis, preguntas_concretas, preguntas_no_consta, preguntas_sustituidos,
                     preguntas_ajenos_y_compartidos) for q in f(exps, hechos)]
    for n, q in enumerate(P, 1):
        q["id"] = f"E{n:03d}"
    return P
