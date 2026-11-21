"""Fichas, claves naturales y batería. Se carga desde generar.py, que le pasa su módulo en `G`."""
import datetime
import json
import re
from collections import Counter

G = None  # módulo generar (lo fija generar.main)

PAL = {0: "cero", 1: "un", 2: "dos", 3: "tres", 4: "cuatro", 5: "cinco", 6: "seis", 7: "siete", 8: "ocho", 9: "nueve", 10: "diez", 11: "once", 12: "doce",
       15: "quince", 18: "dieciocho", 24: "veinticuatro", 36: "treinta y seis", 72: "setenta y dos", 45: "cuarenta y cinco", 30: "treinta"}
NUM_DE = {"dos": 2, "tres": 3, "cuatro": 4, "cinco": 5, "seis": 6}
NOM_PAT = r"([A-ZÁÉÍÓÚÑ][\wáéíóúñ]+(?: [A-ZÁÉÍÓÚÑ][\wáéíóúñ]+){1,2})"

QS = []
DESC = Counter()
K_PREG = {}


def alts(n, unidad):
    r = [f"{n} {unidad}"]
    if int(n) in PAL:
        r.append(f"{PAL[int(n)]} {unidad}")
    return r


def apellido(nombre):
    return nombre.split()[1]


def alt_nombre(nombre):
    """Alternativas de clave para una persona: «nombre apellido» o «apellido apellido»; el apellido solo se repite entre vecinos."""
    t = nombre.split()
    return [" ".join(t[:2])] + ([" ".join(t[1:3])] if len(t) > 2 else [])


def Q(tipo, origen, actor, pregunta, citas=(), claves=(), fichas=(), todas=False, abstener=False, prohibidas=(), sustituidos=(),
      docs_prohibidos=(), kids=(), **extra):
    if any(c is None for c in citas):
        DESC[origen] += 1
        return None
    q = {"id": None, "tipo": tipo, "origen": origen, "actor": actor, "pregunta": pregunta,
         "citas_resueltas": [G.cita(c) if isinstance(c, G.Hit) else c for c in citas],
         "fichas_esperadas": list(fichas), "todas_las_citas": bool(todas), "claves": [list(k) for k in claves], "abstener": abstener,
         "prohibidas": list(prohibidas), "sustituidos": list(sustituidos), "docs_prohibidos": list(docs_prohibidos)}
    q.update(extra)
    QS.append(q)
    for k in kids:
        if k:
            K_PREG.setdefault(k, []).append(q)
    return q


def pick(opciones):
    return G.rng.choice(opciones)


def prohib(viejo, nuevo, sufijo=""):
    v = f"{viejo}{sufijo}"
    return [v] if G.seguro_prohibida(viejo, nuevo) else []


# ----------------------------------------------------------------------------- claves naturales
def claves_naturales(comms):
    for c in comms:
        if c["tpl"] == "c1" and "cuota_asc" not in c["ocup"]:
            con = G.hc(c, "contrato-ascensor", r"cuota mensual de ([\d.]+) euros")
            an = G.hc(c, "anexo-1-contrato-ascensor", r"pasa a ser de ([\d.]+) euros")
            if con and an and G.num(con.v) != G.num(an.v):
                G.nueva_clave("anexo", [G.fh(con), G.fh(an)], an.ruta, f"La cuota mensual vigente es de {an.v} euros (anexo 1), no {con.v} euros (contrato).")
            pc = G.hc(c, "contrato-ascensor", r"plazo máximo de respuesta ante averías de (\d+) horas")
            pa = G.hc(c, "anexo-1-contrato-ascensor", r"será de (\d+) horas")
            if pc and pa and pc.v != pa.v:
                G.nueva_clave("anexo", [G.fh(pc), G.fh(pa)], pa.ruta, f"Con personas atrapadas el plazo máximo es de {pa.v} horas (anexo 1), no {pc.v} (contrato).")
        if c["tpl"] == "c2":
            if "franq" not in c["ocup"]:
                a = G.hc(c, "poliza-seguro-2022", r"franquicia de ([\d.]+) euros por siniestro")
                b = G.hc(c, "poliza-seguro-2024", r"franquicia de ([\d.]+) euros por siniestro")
                if a and b and a.v != b.v:
                    G.nueva_clave("version", [G.fh(a), G.fh(b)], b.ruta, f"La franquicia vigente es de {b.v} euros (póliza 2024), que sustituye a la de {a.v} euros (póliza 2022).")
            rc = G.hc(c, "poliza-seguro-2024", r"hasta un límite de ([\d.]+) euros por siniestro")
            rp = G.hc(c, "contrato-mantenimiento-piscina", r"límite de ([\d.]+) euros")
            if rc and rp and rc.v != rp.v:
                G.nueva_clave("casos_distintos", [G.fh(rc), G.fh(rp)], None,
                              f"La póliza dice que el límite de responsabilidad civil es de {rc.v} euros y el contrato de la piscina declara {rp.v} euros; no cuadran y hay que enseñar los dos.")


# ----------------------------------------------------------------------------- fichas
JARDIN = ["Jardines del Faro", "Verde Costa", "Paisajes Albar", "Jardinería Tramontana", "Huertos y Jardines Marea"]
ANTENA = ["Antenas Bahía", "Telecomunicaciones Muelle", "Instalaciones Garcés", "Señal Norte"]
COLORES = ["verde botella", "azul grisáceo", "ocre claro", "granate", "gris perla", "crema"]
DIAS = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"]
PISOS = ["1º A", "1º B", "2º C", "2º D", "3º A", "3º B", "4º C", "5º A", "5º B", "6º D", "bajo B"]
INCID = ["que la luz del rellano de la segunda planta parpadea", "que la puerta del portal tarda en cerrar", "que hay una bicicleta abandonada en el trastero común",
         "que el telefonillo de su vivienda no suena", "que alguien ha dejado muebles en el rellano", "que la cerradura del buzón no gira bien",
         "que el cartel del horario de contenedores se ha caído", "que la puerta del garaje hace un ruido metálico al abrir", "que el césped de la zona común está muy alto",
         "que falta una bombilla en el cuarto de trasteros", "que el felpudo de la entrada se ha movido de sitio", "que las papeleras de la entrada están llenas",
         "que un vehículo aparca delante de la puerta del garaje", "que el reloj de la escalera va con retraso", "que el tablón de anuncios tiene un cristal suelto",
         "que han pintado una pared del portal sin avisar", "que la señal del vado está desgastada", "que el cierre de la puerta peatonal es muy duro"]
ACCION = ["Se anotó en el parte de la semana.", "Se avisó al técnico habitual para que lo revise.", "Se le contestó por correo el mismo día.",
          "Se informó a la presidencia en la siguiente visita.", "Se le pidió una fotografía para el expediente.", "Queda pendiente de revisar en la próxima ronda.",
          "Se le agradeció el aviso y se archivó la nota.", "Se comentó con el conserje para que lo vigile."]
QUIEN = ["Una vecina", "Un propietario", "La inquilina", "El presidente", "Una propietaria", "El vecino"]


def fecha_iso(r, y0=2025, dias=640):
    d = datetime.datetime(y0, 1, 10) + datetime.timedelta(days=r.randint(0, dias), hours=r.randint(8, 19), minutes=r.choice([0, 10, 20, 30, 40, 50]))
    return d.strftime("%Y-%m-%dT%H:%M:%SZ")


def construir_fichas(comms):
    r = G.rng
    AUT = {"ribera": ["t03", "t04", "t05"], "albar": ["t07", "t08"]}
    copias = [c for c in comms if not c["base"] and not c["ambiguo"]]
    fichas = []

    def nueva(c, estado, titulo, texto, error=False, **extra):
        f = {"id": None, "empresa": c["emp"], "proyecto": c["key"], "autor": r.choice(AUT[c["emp"]]), "estado": estado, "titulo": titulo, "texto": texto,
             "creada": fecha_iso(r), "error_a_proposito": error}
        f.update(extra)
        fichas.append(f)
        return f

    # ---- errores a propósito (20, aprobadas)
    hechos = []  # (c, hecho, hit, ficha)
    usados = set()
    intentos = 0
    while sum(1 for h in hechos) < 20 and intentos < 400:
        intentos += 1
        c = r.choice(copias)
        hecho = r.choice(["franq", "franq", "limp", "empresa_asc", "derr", "pisc_h", "derr"])
        if (c["key"], hecho) in usados or hecho in c["ocup"]:
            continue
        info = None
        if hecho == "franq":
            h = G.hc(c, "poliza-seguro-2023" if c["tpl"] == "c1" else "poliza-seguro-2024", r"franquicia de ([\d.]+) euros por siniestro")
            if h:
                w = r.choice([x for x in (100, 120, 180, 200, 220, 250, 280, 300, 350, 400, 450, 500) if G.seguro_prohibida(x, G.num(h.v))])
                info = (h, str(w), "Hablando con el presidente me dice que la franquicia del seguro por daños de agua es de %d euros." % w, "Nota sobre la franquicia del seguro")
        elif hecho == "limp" and c["tpl"] in ("c1", "c3"):
            h = G.hc(c, "contrato-limpieza", r"limpieza de zonas comunes (\w+) días por semana")
            if h and h.v in NUM_DE:
                w = r.choice([x for x in ("dos", "tres", "cuatro", "cinco", "seis") if x != h.v])
                info = (h, w, f"En la reunión con la empresa de limpieza me confirman que vienen {w} días por semana.", "Nota sobre la limpieza")
        elif hecho == "empresa_asc" and c["tpl"] in ("c1", "c3"):
            h = G.hc(c, "contrato-ascensor", r"(Ascensores Norte|Elevadores Bahía) S\.L\.")
            if h:
                w = "Elevadores Bahía" if h.m.group(1) == "Ascensores Norte" else "Ascensores Norte"
                info = (h, w, f"El ascensor lo mantiene {w} S.L., según el último recibo que vi en la oficina.", "Nota sobre la empresa del ascensor")
        elif hecho == "derr" and c["tpl"] in ("c1", "c3"):
            h = G.hc(c, "acta-extraordinaria-2025-fachada" if c["tpl"] == "c1" else "acta-extraordinaria-2025-ascensor", r"derrama extraordinaria de ([\d.]+) euros")
            if h:
                real = G.num(h.v)
                w = max(500, round(real * r.choice([0.75, 0.85, 1.2]) / 500) * 500)
                if G.seguro_prohibida(real, w):
                    info = (h, f"{w:,}".replace(",", "."), "Según me comentó la presidencia, la derrama fue de %s euros." % f"{w:,}".replace(",", "."), "Nota sobre la derrama")
        elif hecho == "pisc_h" and c["tpl"] == "c2" and not G.hc(c, "estatutos", r"x^"):
            h = G.hc(c, "estatutos", r"en horario de (\d{2}:\d{2}) a (\d{2}:\d{2})")
            if h:
                w = r.choice([x for x in ("20:00", "22:00", "23:00") if x != h.m.group(2)])
                info = (h, w, f"La piscina cierra a las {w}, me lo dijo el socorrista.", "Nota sobre el horario de la piscina")
        if not info:
            continue
        h, w, texto, titulo = info
        f = nueva(c, "APPROVED", titulo, texto, error=True)
        usados.add((c["key"], hecho))
        c["ocup"].add(hecho)
        hechos.append((c, hecho, h, w, f))
    G.INFO["fichas_error"] = len(hechos)

    # ---- datos que solo están en fichas
    def unico(c, estado, tema):
        if tema == "jardin":
            e = r.choice(JARDIN)
            return nueva(c, estado, "Nota sobre el jardín", f"El mantenimiento del jardín de la comunidad lo hace la empresa {e}, que pasa a primeros de cada mes.", tema=tema, clave=e)
        if tema == "portero":
            n = G.T.persona(r)
            return nueva(c, estado, "Nota sobre el portero", f"El portero de la finca es {n}; entra a las {r.choice([7, 8, 9])}:00 de la mañana.", tema=tema, clave=apellido(n))
        if tema == "llave":
            n = G.T.persona(r)
            return nueva(c, estado, "Nota sobre la llave del cuarto de contadores", f"La llave del cuarto de contadores la guarda {n}, vecino del {r.choice(PISOS)}.", tema=tema, clave=apellido(n))
        if tema == "contenedores":
            a, b = r.sample(DIAS, 2)
            return nueva(c, estado, "Nota sobre los contenedores", f"Los contenedores de la comunidad se sacan los {a} y los {b}, por la noche.", tema=tema, clave=[a, b])
        if tema == "color":
            col = r.choice(COLORES)
            return nueva(c, estado, "Nota sobre la pintura del portal", f"Con el visto bueno de la presidencia, el portal se pintará de color {col}.", tema=tema, clave=col)
        e = r.choice(ANTENA)
        return nueva(c, estado, "Nota sobre la antena", f"La antena colectiva la revisa la empresa {e} una vez al año.", tema=tema, clave=e)

    temas = ["jardin", "portero", "llave", "contenedores", "color", "antena"]
    usados_ct = set()

    def lugar_unico(tema):
        while True:
            c = r.choice(copias)
            if (c["key"], tema) not in usados_ct:
                usados_ct.add((c["key"], tema))
                return c

    uni = {"APPROVED": [], "PENDING": [], "REJECTED": []}
    for i in range(15):
        t = temas[i % len(temas)]
        uni["APPROVED"].append(unico(lugar_unico(t), "APPROVED", t))
    for i in range(6):
        t = temas[(i + 1) % len(temas)]
        uni["PENDING"].append(unico(lugar_unico(t), "PENDING", t))
    for i in range(4):
        t = temas[(i + 3) % len(temas)]
        uni["REJECTED"].append(unico(lugar_unico(t), "REJECTED", t))

    # ---- relleno hasta 220 / 50 / 30
    cuentas = Counter(f["estado"] for f in fichas)
    meta = {"APPROVED": 220, "PENDING": 50, "REJECTED": 30}
    for estado, total in meta.items():
        while cuentas[estado] < total:
            c = r.choice(comms)
            inc = r.choice(INCID)
            texto = f"{r.choice(QUIEN)} del {r.choice(PISOS)} comunicó {inc}. {r.choice(ACCION)}"
            nueva(c, estado, "Aviso de vecino", texto)
            cuentas[estado] += 1

    r.shuffle(fichas)
    for i, f in enumerate(fichas, 1):
        f["id"] = f"F{i:03d}"
    return fichas, hechos, uni


# ----------------------------------------------------------------------------- batería
def estudio(comms):
    mapa = {"exacta": "dato_concreto", "tabla": "dato_concreto", "vigencia": "version", "parafrasis": "vocabulario", "frontera": "dato_concreto",
            "negacion": "dato_concreto", "casi_duplicado": "casos_parecidos", "contradiccion": "contradiccion", "vocabulario_tecnico": "vocabulario",
            "agregada_exhaustiva": "lista_completa", "sin_respuesta": "sin_fuente", "premisa_falsa": "sin_fuente", "vocabulario_sin_respuesta": "sin_fuente"}
    sust = {"A4": ["contrato-ascensor.txt"], "B6": ["contrato-ascensor.txt"], "E1": ["poliza-seguro-2022.txt"], "E2": ["contrato-limpieza.txt"]}
    ident = {"A2", "A6"}
    c1 = next(c for c in comms if c["key"] == "c1-olmo14")
    for fichero in ("bateria_escala.json", "bateria_casos_limite_resuelta.json"):
        for q in json.load(open(G.VENDOR / fichero, encoding="utf-8")):
            citas = []
            for cr in q.get("citas_resueltas", []):
                ruta = "ribera/" + cr["doc"]
                ok = ruta in G.DOCS and all(cr["ancla"] in G.paginas(ruta).get(p, "") for p in cr["paginas"])
                citas.append({"doc": ruta, "paginas": cr["paginas"], "ancla": cr["ancla"]} if ok else None)
            tipo = "identificador" if q["id"] in ident else mapa[q["categoria"]]
            texto = q["pregunta"]
            if q["id"] in ("G1", "G2"):
                texto += " Limítate a las tres comunidades Calle Olmo 14, Residencial Los Almendros y Avenida del Puerto 3."
            base = q["id"].lower()
            extra = {}
            if "orden" in q:
                extra["orden"] = q["orden"]
            if "bonus" in q:
                extra["bonus"] = q["bonus"]
            sustituidos = []
            for s in sust.get(q["id"], []):
                carp = "c2-almendros" if q["id"] in ("E1", "E2") else "c1-olmo14"
                sustituidos.append(f"ribera/{carp}/{s}")
            # claves de una o dos cifras sueltas (casarían con casi cualquier respuesta): se exige la unidad
            duras = {"A5": [["48000"], ["12 cuotas", "doce cuotas", "12 mensualidades", "12 pagos", "12 plazos", "12 meses"]],
                     "B2": [["4 personas", "cuatro personas", "4 invitados", "cuatro invitados", "4 primos", "cuatro primos", "hasta 4", "hasta cuatro", "máximo de 4", "máximo de cuatro"]],
                     "B5": [["21:00", "21 horas", "21h", "21.00"]], "B7": [["brillo"], ["dos días", "2 días"]],
                     "B8": [["38 votos", "38 a favor", "38 propietarios", "38 votantes", "38 síes"]],
                     "D3": [["tres días", "3 días"]], "E2": [["cinco días", "5 días"]]}
            # «4 horas» casaría con «24 horas» (el puntuador compara por subcadena): con un viejo que lo contiene, la clave exige una preposición delante
            claves = [[v for k in alts for v in ([k] if not any(k != p and k in p for p in q.get("prohibidas", [])) else [f"{w} {k}" for w in ("de", "en", "a", "hasta", "es", "son")])]
                      for alts in q.get("claves", [])]
            claves = duras.get(q["id"], claves)
            Q(tipo, "estudio", "t01", texto, citas, claves, todas=q.get("todas_las_citas", False), abstener=q.get("abstener", False),
              prohibidas=q.get("prohibidas", []), sustituidos=sustituidos, docs_prohibidos=["ribera/" + d for d in q.get("docs_prohibidos", [])], **extra)


def elig(comms, tpl, fact=None):
    return [c for c in comms if c["tpl"] == tpl and not c["base"] and not c["ambiguo"] and (fact is None or fact not in c["ocup"])]


def act(c):
    return G.ACTOR[c["emp"]]


def copias(comms):
    H, num, dig = G.hit, G.num, G.digitos
    ident_count = Counter(m for c in comms for d in c["docs"] if "poliza" in d for m in re.findall(r"Póliza nº (ATL-\d{4}-\d{4})", G.DOCS[d]))

    def rep(tpl, fact, n, fn):
        hechas = 0
        cands = elig(comms, tpl, fact)
        G.rng.shuffle(cands)
        for c in cands:
            if hechas >= n:
                break
            if fn(c) is not None:
                hechas += 1

    # ---- c1
    def c1_franq(c):
        h = G.hc(c, "poliza-seguro-2023", r"franquicia de ([\d.]+) euros por siniestro")
        if not h:
            return None
        nm = c["nombre"]
        t = pick([f"Se me ha mojado el techo del salón por un escape del piso de arriba en {nm}. ¿Lo cubre el seguro y cuánto tengo que poner yo?",
                  f"Un vecino de {nm} ha tenido una rotura de tubería en casa. ¿Qué franquicia se aplica en el seguro de daños por agua de la comunidad?",
                  f"¿Cuánto pagamos de nuestro bolsillo si hay un siniestro de agua en {nm}?"])
        return Q("vocabulario", "copia", act(c), t, [h], [[dig(h.v)]])

    def c1_cuota(c):
        a = G.hc(c, "anexo-1-contrato-ascensor", r"pasa a ser de ([\d.]+) euros")
        b = G.hc(c, "contrato-ascensor", r"cuota mensual de ([\d.]+) euros")
        if not a or not b or num(a.v) == num(b.v):
            return None
        nm = c["nombre"]
        t = pick([f"¿Cuál es la cuota mensual actual del mantenimiento del ascensor de {nm}?", f"¿Cuánto le pagamos ahora al mantenedor del ascensor de {nm} cada mes?",
                  f"¿A cuánto está hoy la cuota de la empresa del ascensor en {nm}?"])
        k = next((k["id"] for k in G.CLAVES if k["tipo"] == "anexo" and k["fuentes"][0]["doc"] == b.ruta and "cuota" in k["respuesta_correcta"]), None)
        return Q("version", "copia", act(c), t, [a], [[dig(a.v)]], prohibidas=prohib(dig(b.v), dig(a.v), " euros"), sustituidos=[b.ruta], kids=[k])

    def c1_plazo(c):
        a = G.hc(c, "anexo-1-contrato-ascensor", r"será de (\d+) horas")
        b = G.hc(c, "contrato-ascensor", r"plazo máximo de respuesta ante averías de (\d+) horas")
        if not a or not b or a.v == b.v:
            return None
        nm = c["nombre"]
        t = pick([f"Si alguien se queda encerrado en el ascensor de {nm}, ¿cuánto puede tardar como máximo el técnico?", f"¿En cuántas horas tiene que acudir la empresa del ascensor de {nm} si hay una persona atrapada?"])
        k = next((k["id"] for k in G.CLAVES if k["tipo"] == "anexo" and k["fuentes"][0]["doc"] == b.ruta and "atrapadas" in k["respuesta_correcta"]), None)
        return Q("version", "copia", act(c), t, [a], [alts(a.v, "horas")], prohibidas=prohib(b.v, a.v, " horas"), sustituidos=[b.ruta], kids=[k])

    def c1_derrama(c):
        h = G.hc(c, "acta-extraordinaria-2025-fachada", r"derrama extraordinaria de ([\d.]+) euros para la rehabilitación de la fachada, pagadera en (\d+) cuotas")
        if not h:
            return None
        nm = c["nombre"]
        t = pick([f"¿De cuánto fue la derrama para la fachada de {nm} y en cuántas cuotas se paga?", f"¿Cuánto hubo que aportar entre todos para rehabilitar la fachada de {nm} y cómo se pagó?"])
        return Q("dato_concreto", "copia", act(c), t, [h], [[dig(h.v)], alts(h.m.group(2), "cuotas") + alts(h.m.group(2), "mensualidades") + alts(h.m.group(2), "pagos") + alts(h.m.group(2), "plazos") + alts(h.m.group(2), "meses")])

    def c1_pres25(c):
        h = G.hc(c, "acta-ordinaria-2025", r"Se elige presidente de la Comunidad a D\. " + NOM_PAT)
        h4 = G.hc(c, "acta-ordinaria-2024", r"presidenta de la Comunidad a Dña\. " + NOM_PAT)
        if not h or not h4 or apellido(h.v) == apellido(h4.v):
            return None
        nm = c["nombre"]
        return Q("casos_parecidos", "copia", act(c), pick([f"¿Quién fue elegido presidente de {nm} en la junta de 2025?", f"¿Quién salió presidente en la junta ordinaria de 2025 de {nm}?"]), [h], [alt_nombre(h.v)], prohibidas=prohib(apellido(h4.v), apellido(h.v)))

    def c1_pres24(c):
        h = G.hc(c, "acta-ordinaria-2024", r"presidenta de la Comunidad a Dña\. " + NOM_PAT)
        if not h:
            return None
        nm = c["nombre"]
        return Q("casos_parecidos", "copia", act(c), pick([f"¿Quién fue elegida presidenta de {nm} en la junta de 2024?", f"¿Quién presidía {nm} tras la junta ordinaria de 2024?"]), [h], [alt_nombre(h.v)])

    def c1_aire(c):
        h = G.hc(c, "estatutos", r"Artículo 9\. Queda prohibida la instalación de aparatos de aire acondicionado")
        if not h:
            return None
        nm = c["nombre"]
        return Q("vocabulario", "copia", act(c), pick([f"Quiero poner el aire acondicionado en el balcón que da a la calle en {nm}, ¿puedo hacerlo sin más?", f"¿Qué requisito hay para instalar una unidad de climatización exterior en la fachada principal de {nm}?"]), [h], [["junta"]])

    def c1_ident(c):
        h = G.hc(c, "poliza-seguro-2023", r"Póliza nº (ATL-\d{4}-\d{4})")
        if not h or ident_count[h.v] != 1:
            return None
        return Q("identificador", "copia", act(c), pick([f"¿A qué comunidad pertenece la póliza {h.v}?", f"Me ha llegado un recibo de la póliza {h.v}, ¿de qué comunidad es?"]), [h], [[re.sub(r'^(Calle|Avenida del) ', '', c["nombre"])]])

    def c1_bajante(c):
        h = G.hc(c, "bajante-patio", r"El perito de Seguros Atalaya rechazó el siniestro por tratarse de un deterioro gradual de la junta\.")
        i = G.hc(c, "bajante-patio", r"abonó ([\d.]+) euros por la sustitución")
        if not h or not i:
            return None
        nm = c["nombre"]
        return Q("vocabulario", "copia", act(c), f"¿Ha habido algún problema de agua en el patio de luces de {nm}? ¿Lo pagó el seguro?", [h], [[dig(i.v)], ["rechaz", "no lo pag", "no lo cubr", "no cubri", "deterioro gradual"]])

    def c1_cond(c):
        h = G.hc(c, "condensaciones", r"condensaciones superficiales por ventilación insuficiente de la vivienda")
        if not h:
            return None
        nm = c["nombre"]
        return Q("vocabulario", "copia", act(c), f"Una vecina de {nm} tiene moho negro en la pared del dormitorio. ¿Consta algún caso parecido y qué concluyó el técnico?", [h], [["condensaci"], ["ventila"]])

    rep("c1", "franq", 3, c1_franq)
    rep("c1", "cuota_asc", 4, c1_cuota)
    rep("c1", "plazo_asc", 3, c1_plazo)
    rep("c1", "derr", 3, c1_derrama)
    rep("c1", "pres", 2, c1_pres25)
    rep("c1", "pres", 2, c1_pres24)
    rep("c1", None, 2, c1_aire)
    rep("c1", None, 3, c1_ident)
    rep("c1", None, 2, c1_bajante)
    rep("c1", None, 2, c1_cond)

    # ---- c2
    def c2_invit(c):
        h = G.hc(c, "estatutos", r"un máximo de (\d) personas por día")
        if not h:
            return None
        return Q("vocabulario", "copia", act(c), f"¿Puedo llevar a mis siete primos a la piscina de {c['nombre']}?", [h], [alts(h.v, "personas") + alts(h.v, "invitados") + alts(h.v, "primos")])

    def c2_cierre(c):
        h = G.hc(c, "estatutos", r"en horario de (\d{2}:\d{2}) a (\d{2}:\d{2})")
        if not h:
            return None
        hh = h.m.group(2)
        return Q("vocabulario", "copia", act(c), pick([f"¿A qué hora cierran la piscina en {c['nombre']}?", f"¿Hasta qué hora se puede estar en la piscina de {c['nombre']}?"]), [h], [[hh, f"{int(hh[:2])} horas", f"{int(hh[:2])}h"]])

    def c2_franq(c):
        a = G.hc(c, "poliza-seguro-2022", r"franquicia de ([\d.]+) euros por siniestro")
        b = G.hc(c, "poliza-seguro-2024", r"franquicia de ([\d.]+) euros por siniestro")
        if not a or not b or a.v == b.v:
            return None
        k = next((k["id"] for k in G.CLAVES if k["tipo"] == "version" and k["fuentes"][0]["doc"] == a.ruta), None)
        return Q("version", "copia", act(c), pick([f"¿Qué franquicia por daños por agua tiene ahora mismo la póliza de {c['nombre']}?", f"Si hoy se rompe una tubería común en {c['nombre']}, ¿qué franquicia se aplica?"]), [b], [[dig(b.v)]],
                 prohibidas=prohib(dig(a.v), dig(b.v), " euros"), sustituidos=[a.ruta], kids=[k])

    def c2_piscina(c):
        h = G.hc(c, "vaso-piscina", r"localizó una microfisura en el pasamuros de una boquilla de impulsión")
        if not h:
            return None
        return Q("vocabulario", "copia", act(c), f"La piscina de {c['nombre']} perdía agua en julio de 2025. ¿Qué se encontró y quién lo arregló?", [h], [["microfisura"], ["Aquaservicios"]])

    def c2_desague(c):
        h = G.hc(c, "colector-garaje", r"obstrucción del colector por intrusión de raíces de los cipreses del jardín")
        i = G.hc(c, "colector-garaje", r"la Comunidad abonó ([\d.]+) euros con cargo al fondo de reserva")
        if not h or not i:
            return None
        return Q("vocabulario", "copia", act(c), f"¿Se ha atascado alguna vez el desagüe del garaje de {c['nombre']}? ¿Por qué pasó y cuánto costó?", [h], [["raíces", "raices"], [dig(i.v)]])

    rep("c2", "invit", 2, c2_invit)
    rep("c2", "pisc_h", 2, c2_cierre)
    rep("c2", "franq", 3, c2_franq)
    rep("c2", None, 2, c2_piscina)
    rep("c2", None, 2, c2_desague)

    # ---- c3
    def c3_seguro(c):
        h = G.hc(c, "acta-ordinaria-2025", r"\| Seguro comunitario \| ([\d.]+) euros \|")
        return h and Q("dato_concreto", "copia", act(c), pick([f"¿Cuánto se presupuestó para el seguro comunitario en {c['nombre']} en 2025?", f"En el presupuesto de 2025 de {c['nombre']}, ¿qué partida corresponde al seguro?"]), [h], [[dig(h.v)]])

    def c3_locales(c):
        h = G.hc(c, "acta-extraordinaria-2025-ascensor", r"quedan exentos de esta derrama")
        e = G.hc(c, "estatutos", r"Artículo 14\. Los locales comerciales")
        return h and e and Q("vocabulario", "copia", act(c), f"¿Los dueños de las tiendas de abajo pagan el ascensor nuevo en {c['nombre']}?", [h, e], [["exent", "exime", "exenc", "no pagan", "no particip"]])

    def c3_limpieza(c):
        h = G.hc(c, "contrato-limpieza", r"limpieza de zonas comunes (\w+) días por semana")
        if not h or h.v not in NUM_DE:
            return None
        return Q("vocabulario", "copia", act(c), f"¿Quién se encarga de limpiar la escalera en {c['nombre']} y cuántos días viene?", [h], [["brillo"], [f"{h.v} días", f"{NUM_DE[h.v]} días"]])

    def c3_empresa(c):
        h = G.hc(c, "contrato-ascensor", r"Elevadores Bahía S\.L\.")
        return h and Q("casos_parecidos", "copia", act(c), pick([f"¿Qué empresa mantiene el ascensor de {c['nombre']}?", f"¿Quién tiene el contrato de conservación del ascensor de {c['nombre']}?"]), [h], [["bahia"]], prohibidas=["ascensores norte"])

    def c3_franq(c):
        h = G.hc(c, "poliza-seguro-2024", r"franquicia de ([\d.]+) euros por siniestro")
        return h and Q("casos_parecidos", "copia", act(c), pick([f"¿Qué franquicia por daños por agua tiene la póliza de {c['nombre']}?", f"¿Cuánto se queda a cargo de la comunidad en un siniestro de agua en {c['nombre']}?"]), [h], [[dig(h.v)]])

    def c3_total(c):
        h = G.hc(c, "acta-ordinaria-2025", r"\| Total \| ([\d.]+) euros \|")
        return h and Q("dato_concreto", "copia", act(c), f"¿Cuál es el total del presupuesto de 2025 de {c['nombre']}?", [h], [[dig(h.v)]])

    def c3_caso(c):
        h = G.hc(c, "filtracion-cubierta", r"Caso (\d{4}-\d{3})")
        return h and Q("identificador", "copia", act(c), f"¿Qué número tiene el caso de la filtración desde la cubierta en {c['nombre']}?", [h], [[h.v]])

    def c3_pared(c):
        h = G.hc(c, "capilaridad", r"capilaridad ascendente desde la cimentación")
        i = G.hc(c, "capilaridad", r"con un presupuesto de ([\d.]+) euros")
        return h and i and Q("vocabulario", "copia", act(c), f"En {c['nombre']} hay una pared de la zona común que suelta un polvillo blanco y se le cae la pintura. ¿Sabemos por qué pasa y cuánto costaría arreglarlo?", [h], [["capilaridad"], [dig(i.v)]])

    for fn, fact in ((c3_seguro, None), (c3_locales, None), (c3_limpieza, "limp"), (c3_empresa, "empresa_asc"), (c3_franq, "franq"), (c3_total, None), (c3_caso, None), (c3_pared, None)):
        rep("c3", fact, 2, fn)

    # ---- listas completas de casos archivados
    def lista(c):
        casos = [d for d in c["docs"] if d.rsplit("/", 1)[1].startswith("caso-")]
        hs = [H(d, r"Caso (\d{4}-\d{3})") for d in casos]
        if len(hs) < 3 or any(x is None for x in hs):
            return None
        return Q("lista_completa", "copia", act(c), pick([f"Haz una lista de todos los casos archivados de {c['nombre']} con su número.", f"¿Qué incidencias o casos constan en {c['nombre']}? Dame el número de cada uno."]), hs, [[x.v] for x in hs], todas=True)

    for tpl, n in (("c1", 2), ("c2", 1), ("c3", 1)):
        rep(tpl, None, n, lista)


def sin_fuente(comms):
    def uno(tpl, plantillas, n):
        cands = elig(comms, tpl)
        G.rng.shuffle(cands)
        for c in cands[:n]:
            Q("sin_fuente", "sin_fuente", act(c), pick(plantillas).format(nm=c["nombre"]), abstener=True)

    uno("c2", ["¿Qué empresa hace el mantenimiento del ascensor de {nm}?", "¿Cada cuánto revisan el ascensor de {nm}?"], 3)
    uno("c3", ["¿Cuánto costó la derrama de la fachada de {nm}?", "¿En cuántas cuotas se pagó la derrama de la fachada de {nm}?"], 2)
    uno("c1", ["¿Qué se acordó en la junta de 2023 de {nm}?", "¿Quién presidía {nm} en 2023?"], 2)
    uno("c2", ["¿Cubre la póliza de {nm} los daños por terremoto?"], 1)
    uno("c1", ["¿Cuál es el saldo bancario de la comunidad de {nm}?", "¿Cuántos propietarios de {nm} tienen recibos pendientes?"], 2)
    uno("c3", ["¿Cuál es el teléfono de urgencias de la aseguradora de {nm}?"], 1)


# ----------------------------------------------------------------------------- ampliación, fichas y aislamiento
def pregs_ampliacion(comms, normas, procs):
    H, dig = G.hit, G.digitos
    AMP = G.AMP
    por_tipo = {}
    for a in AMP:
        por_tipo.setdefault(a["tipo"], []).append(a)

    def muestra(tipo, n, filtro=lambda a: True):
        lst = [a for a in por_tipo.get(tipo, []) if filtro(a)]
        G.rng.shuffle(lst)
        return lst[:n]

    # pólizas
    todas = muestra("poliza", 12)
    for a in todas[:9]:
        c = a["c"]
        h = H(a["nuevo"], r"franquicia de [\d.]+ euros por siniestro")
        t = pick([f"¿Qué franquicia tiene ahora la póliza de daños por agua de {c['nombre']}?", f"Tras renovar el seguro, ¿qué franquicia se aplica en {c['nombre']} si se rompe una tubería común?",
                  f"¿Cuánto hay que pagar de franquicia en un siniestro de agua en {c['nombre']}?"])
        Q("version", "ampliacion", act(c), t, [h], [[str(a["f_new"])]], prohibidas=prohib(a["f_old"], a["f_new"], " euros"), sustituidos=a["viejos"], kids=[a["kid"]])
    for a in todas[9:12]:
        c = a["c"]
        h = H(a["nuevo"], r"Póliza nº (ATL-\d{4}-\d{4})")
        Q("identificador", "ampliacion", act(c), pick([f"¿Qué número de póliza está vigente en {c['nombre']}?", f"Necesito el número de la póliza de seguro que está en vigor ahora en {c['nombre']}."]), [h], [[a["num"]]],
          prohibidas=[a["anterior"]], sustituidos=a["viejos"])
    for a in muestra("poliza", 2, lambda a: a["c"]["tpl"] == "c2" and a not in todas):
        c = a["c"]
        docs = [a["viejos"][1], a["viejos"][0], a["nuevo"]]
        hs = [H(d, r"Póliza nº (ATL-\d{4}-\d{4})") for d in docs]
        Q("lista_completa", "ampliacion", act(c), pick([f"¿Qué pólizas de seguro ha tenido {c['nombre']} y cuáles son sus números?", f"Dame todos los números de póliza que constan de {c['nombre']}."]), hs, [[x.v] for x in hs], todas=True)

    # anexos de limpieza
    for i, a in enumerate(muestra("anexo_limpieza", 4)):
        c = a["c"]
        if i % 2 == 0:
            h = H(a["nuevo"], r"se prestará (\w+) días por semana")
            Q("version", "ampliacion", act(c), pick([f"¿Cuántos días por semana se limpian ahora las zonas comunes de {c['nombre']}?", f"¿Cuántos días viene el servicio de limpieza a {c['nombre']} actualmente?"]),
              [h], [[f"{h.v} días", f"{NUM_DE[h.v]} días"]], prohibidas=[f"{a['dias_old']} días"], sustituidos=a["viejos"], kids=[a["kid_dias"]])
        else:
            h = H(a["nuevo"], r"importe mensual pasa a ser de ([\d.]+) euros")
            Q("version", "ampliacion", act(c), pick([f"¿Cuánto cuesta al mes la limpieza de {c['nombre']} actualmente?", f"¿Qué importe mensual se paga hoy a la empresa de limpieza de {c['nombre']}?"]),
              [h], [[dig(h.v)]], prohibidas=prohib(a["imp_old"], a["imp_new"], " euros"), sustituidos=a["viejos"], kids=[a["kid_imp"]])
    # anexos de ascensor
    for i, a in enumerate(muestra("anexo_ascensor", 6)):
        c = a["c"]
        if i % 2 == 0:
            h = H(a["nuevo"], r"cuota mensual pasa a ser de ([\d.]+) euros")
            Q("version", "ampliacion", act(c), pick([f"¿Cuánto es la cuota mensual vigente del mantenimiento del ascensor de {c['nombre']}?", f"¿Cuánto cobra hoy al mes la empresa del ascensor de {c['nombre']}?"]),
              [h], [[dig(h.v)]], prohibidas=prohib(a["cuota_old"], a["cuota_new"], " euros"), sustituidos=a["viejos"], kids=[a["kid_cuota"]])
        else:
            h = H(a["nuevo"], r"será de (\d+) horas")
            Q("version", "ampliacion", act(c), pick([f"¿En cuántas horas tiene que atender el técnico si hay alguien encerrado en el ascensor de {c['nombre']}?", f"¿Cuál es hoy el plazo máximo de respuesta del ascensor de {c['nombre']} con una persona atrapada?"]),
              [h], [alts(h.v, "horas")], prohibidas=prohib(a["pl_old"], a["pl_new"], " horas"), sustituidos=a["viejos"], kids=[a["kid_plazo"]])
    # datos desactualizados
    for a in muestra("pres", 4):
        c = a["c"]
        h = H(a["nuevo"], r"Se elige presidenta? de la Comunidad a Dña?\.? " + NOM_PAT)
        if c["tpl"] == "c1":
            h = H(a["nuevo"], r"Se elige presidente de la Comunidad a D\. " + NOM_PAT)
        Q("version", "ampliacion", act(c), pick([f"¿Quién preside ahora {c['nombre']}?", f"¿Quién es el presidente o la presidenta actual de {c['nombre']}?"]), [h], [alt_nombre(a["new"])],
          prohibidas=prohib(apellido(a["old"]), apellido(a["new"])), sustituidos=a["viejos"], kids=[a["kid"]])
    for a in muestra("derrama", 2):
        c = a["c"]
        h = H(a["nuevo"], r"pasa a pagarse en (\d+) cuotas mensuales")
        Q("version", "ampliacion", act(c), f"¿En cuántas cuotas se paga la derrama de la fachada de {c['nombre']}?", [h], [alts(h.v, "cuotas")], prohibidas=[f"{a['old']} cuotas"], sustituidos=a["viejos"], kids=[a["kid"]])
    for a in muestra("piscina_h", 2):
        c = a["c"]
        h = H(a["nuevo"], r"pasa a ser de \d{2}:\d{2} a (\d{2}:\d{2})")
        Q("version", "ampliacion", act(c), pick([f"¿A qué hora cierra la piscina de {c['nombre']} esta temporada?", f"¿Hasta qué hora está abierta la piscina de {c['nombre']} en 2026?"]), [h], [[h.v, f"{int(h.v[:2])} horas"]],
          prohibidas=prohib(a["old"], a["new"]), sustituidos=a["viejos"], kids=[a["kid"]])
    for a in muestra("invitados", 2):
        c = a["c"]
        h = H(a["nuevo"], r"un máximo de (\d) personas por día")
        Q("version", "ampliacion", act(c), f"¿Cuántos invitados puede llevar cada vivienda a la piscina de {c['nombre']}?", [h], [alts(h.v, "personas") + alts(h.v, "invitados")], prohibidas=[f"{a['old']} personas"], sustituidos=a["viejos"], kids=[a["kid"]])

    # presupuestos con tablas
    partidas = ["Electricidad de zonas comunes", "Fondo de reserva", "Seguro comunitario", "Limpieza", "Total"]
    for i, a in enumerate(muestra("presupuesto", 4)):
        c = a["c"]
        p = partidas[i % len(partidas)]
        h = H(a["nuevo"], r"\| " + re.escape(p) + r" \| ([\d.]+) euros \|")
        texto = {"Total": f"¿Cuál es el total de gastos previstos en el presupuesto de 2026 de {c['nombre']}?"}.get(p, f"¿Cuánto se presupuesta para {p[0].lower() + p[1:]} en {c['nombre']} para 2026?")
        Q("dato_concreto", "ampliacion", act(c), texto, [h], [[dig(h.v)]])
    # comunicaciones
    for tipo, n in (("com_junta", 1), ("com_cuota", 2), ("com_obras", 1)):
        for a in muestra(tipo, n):
            c = a["c"]
            if tipo == "com_junta":
                h = H(a["nuevo"], r"a las (\d{2}:\d{2}) horas")
                Q("dato_concreto", "ampliacion", act(c), f"¿A qué hora es la junta extraordinaria que se ha convocado en {c['nombre']}?", [h], [[h.v]])
            elif tipo == "com_cuota":
                h = H(a["nuevo"], r"como máximo el (\d+ de \w+)( de 2026)")
                Q("dato_concreto", "ampliacion", act(c), f"¿Hasta qué día hay que pagar el recibo del trimestre en {c['nombre']}?", [h], [[h.m.group(1)]])
            else:
                h = H(a["nuevo"], r"duración prevista de (\d+) días laborables")
                Q("dato_concreto", "ampliacion", act(c), f"¿Cuántos días laborables van a durar los trabajos de mantenimiento que se avisaron en {c['nombre']}?", [h], [alts(h.v, "días")])
    # avisos que no cuadran
    for a in muestra("avisos", 5):
        c = a["c"]
        h1 = H(a["docs"][0], r"de \d+:00 a \d+:00")
        h2 = H(a["docs"][1], r"de \d+:00 a \d+:00")
        Q("contradiccion", "ampliacion", act(c), pick([f"¿Entre qué horas cortan el agua en {c['nombre']} el {a['dia']}?", f"¿A qué hora va a faltar el agua en {c['nombre']} el {a['dia']} y hasta cuándo?"]), [h1, h2],
          [[f"{a['a']}:00"], [f"{a['b']}:00"]], todas=True, kids=[a["kid"]])

    # normas internas
    def nrm(emp, clave, texto, patron, claves, tipo="dato_concreto", prohibidas=(), sust=(), actor=None, flags=0):
        h = H(normas[(emp, clave)], patron, flags)
        Q(tipo, "ampliacion", actor or G.ACTOR[emp], texto, [h], claves, prohibidas=prohibidas, sustituidos=[normas[(emp, s)] for s in sust])

    nrm("ribera", "sin25", "¿En cuántos días hay que comunicar un siniestro a la aseguradora?", r"es de 5 días naturales", [alts(5, "días")], "version", ["7 días"], ["sin24"])
    nrm("ribera", "urg25", "¿En cuánto tiempo hay que atender un aviso urgente de un propietario?", r"en un máximo de 2 horas", [alts(2, "horas")], "version", ["4 horas"], ["urg24"])
    nrm("albar", "urg25", "En Albar, ¿en cuántas horas como máximo hay que contestar un aviso urgente de un vecino?", r"en un máximo de 3 horas", [alts(3, "horas")], "version", ["6 horas"], ["urg24"])
    nrm("ribera", "atencion", "¿Hasta qué hora se atiende por teléfono a los propietarios?", r"de 9:30 a 14:00", [["14:00", "14 horas", "14h"]])
    nrm("ribera", "gastos", "¿A partir de qué importe de gasto hay que pedir varios presupuestos?", r"superior a (600) euros", [["600"]])
    nrm("ribera", "llaves", "¿Dónde se guardan las llaves de las azoteas y de los cuartos técnicos?", r"caja fuerte de la oficina", [["caja fuerte"]])
    nrm("albar", "archivo", "¿Cuántos años se guarda el expediente de una comunidad que se da de baja?", r"durante 8 años", [alts(8, "años")])
    nrm("albar", "conv", "¿Cuántos días antes hay que mandar a los propietarios la convocatoria de una junta en Gestión Albar?", r"con 12 días de antelación", [alts(12, "días")])


def pregs_fichas(comms, fichas, hechos, uni):
    H, dig = G.hit, G.digitos
    por_id = {f["id"]: f for f in fichas}
    P = {"jardin": "¿Quién se ocupa del jardín de {nm}?", "portero": "¿Cómo se llama el portero de {nm}?", "llave": "¿Quién guarda la llave del cuarto de contadores de {nm}?",
         "contenedores": "¿Qué días se sacan los contenedores en {nm}?", "color": "¿De qué color se va a pintar el portal de {nm}?", "antena": "¿Qué empresa revisa la antena colectiva de {nm}?"}
    V = {"jardin": "¿Qué empresa lleva los jardines de {nm}?", "portero": "¿Quién es el portero de {nm} y a qué hora entra?", "llave": "¿Quién tiene la llave de contadores de {nm}?",
         "contenedores": "¿Qué noches hay que sacar la basura en {nm}?", "color": "¿Qué color le van a dar al portal de {nm}?", "antena": "¿Quién se encarga de la antena de {nm}?"}
    cm = {c["key"]: c for c in comms}
    for estado in ("APPROVED", "PENDING", "REJECTED"):
        for f in uni[estado]:
            c = cm[f["proyecto"]]
            claves = [[x] for x in f["clave"]] if isinstance(f["clave"], list) else [[f["clave"]]]
            texto = pick([P, V])[f["tema"]].format(nm=c["nombre"])
            if estado == "APPROVED":
                Q("ficha", "ficha", act(c), texto, [], claves, fichas=[f["id"]])
            else:
                Q("ficha", "ficha", act(c), texto, [], [], abstener=True, fichas_prohibidas=[f["id"]])
    FQ = {"franq": "¿Qué franquicia tiene la póliza de daños por agua de {nm}?", "limp": "¿Cuántos días por semana viene la limpieza a {nm}?", "empresa_asc": "¿Qué empresa mantiene el ascensor de {nm}?",
          "derr": "¿De cuánto fue la derrama de {nm}?", "pisc_h": "¿A qué hora cierra la piscina de {nm}?"}
    n = 0
    for c, hecho, h, w, f in hechos:
        if n >= 10 or c["ambiguo"]:
            continue
        if hecho == "franq":
            claves = [[dig(h.v)], [w]]
        elif hecho == "limp":
            claves = [[f"{h.v} días", f"{NUM_DE[h.v]} días"], [f"{w} días", f"{NUM_DE[w]} días"]]
        elif hecho == "empresa_asc":
            claves = [[h.m.group(1)], [w]]
        elif hecho == "derr":
            claves = [[dig(h.v)], [dig(w)]]
        else:
            claves = [[h.m.group(2)], [w]]
        q = Q("contradiccion", "ficha", act(c), FQ[hecho].format(nm=c["nombre"]), [h], claves, fichas=[f["id"]], todas=True)
        f["_q"] = q
        n += 1
    return n


def pregs_aislamiento(comms, t02_proyectos, procs):
    H, dig = G.hit, G.digitos
    ribera_c = [c for c in comms if c["emp"] == "ribera" and not c["base"] and not c["ambiguo"]]
    no_miembro = [c for c in ribera_c if c["key"] not in t02_proyectos]

    def facts(c):
        if c["tpl"] == "c1":
            return [f"Dime la franquicia del seguro de agua de {c['nombre']}.", f"¿Quién salió presidente de {c['nombre']} en la junta ordinaria de 2025?", f"¿Qué importe mensual se le paga a la empresa de limpieza de {c['nombre']}?"]
        if c["tpl"] == "c2":
            return [f"¿Hasta qué hora está abierta la piscina de {c['nombre']}?", f"¿Cuánto cuesta por temporada el mantenimiento de la piscina de {c['nombre']}?"]
        return [f"¿Cuánto se paga cada mes por la limpieza de {c['nombre']}?"]

    cands = list(ribera_c)
    G.rng.shuffle(cands)
    for c in cands[:6]:
        Q("aislamiento", "aislamiento", "t06", pick(facts(c)), abstener=True, docs_prohibidos=[f"ribera/{c['key']}/"])
    cands = list(no_miembro)
    G.rng.shuffle(cands)
    for c in cands[:5]:
        Q("aislamiento", "aislamiento", "t02", pick(facts(c)), abstener=True, docs_prohibidos=[f"ribera/{c['key']}/"])
    # t02 pregunta por el proyecto abierto: sí responde
    esp = [("alta", "¿Cuántas referencias exigimos para dar de alta a un proveedor?", r"exige (dos) referencias", [["dos referencias", "2 referencias"]]),
           ("atrap", "Si hay una persona atrapada en un ascensor y la empresa no contesta, ¿cuánto esperamos antes de llamar a los bomberos?", r"en (30) minutos", [alts(30, "minutos")]),
           ("impag", "¿Cuándo se manda la carta certificada por un recibo impagado?", r"lleva (45) días vencido", [alts(45, "días")]),
           ("mudanzas", "¿Con cuánta antelación hay que avisar de una mudanza?", r"con (48) horas de antelación", [alts(48, "horas")])]
    for clave, texto, patron, claves in esp:
        h = H(procs[clave], patron)
        Q("aislamiento", "aislamiento", "t02", texto, [h], claves)
    # t06 pregunta por documentos generales de Ribera
    Q("aislamiento", "aislamiento", "t06", "¿Dónde se guardan las llaves de los cuartos técnicos y de las azoteas en la oficina de Ribera?", abstener=True, docs_prohibidos=["ribera/_general/"])
    Q("aislamiento", "aislamiento", "t06", "¿Cuándo hay que revisar los extintores de las comunidades?", abstener=True, docs_prohibidos=["ribera/procedimientos-generales/"])
