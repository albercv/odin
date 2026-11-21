"""Parejas viejo -> vigente del archivo ficticio: salen de las contradicciones de tipo `version` de claves.json.
Solo entran las que `PUT .../superseded-by` aceptaría (spec 5.1): misma empresa, vigente del mismo proyecto o sin proyecto,
sin cadenas circulares. En el archivo todos los documentos están READY."""


def _candidatas(claves):
    for c in claves:
        if c["tipo"] != "version":
            continue
        vigente = c["vigente"]["doc"]
        for viejo in dict.fromkeys(f["doc"] for f in c["fuentes"] if "doc" in f and f["doc"] != vigente):
            yield viejo, vigente


def _motivo(viejo, vigente, docs):
    if viejo not in docs or vigente not in docs:
        return "no está en el manifiesto"
    if docs[viejo]["empresa"] != docs[vigente]["empresa"]:
        return "son de empresas distintas"
    if docs[vigente].get("proyecto") not in (None, docs[viejo].get("proyecto")):
        return "el vigente es de otro proyecto"
    return None


def _clasificar(claves, manifiesto):
    docs = {d["ruta"]: d for d in manifiesto["documentos"]}
    validas, descartadas = {}, []
    for viejo, vigente in _candidatas(claves):
        motivo = _motivo(viejo, vigente, docs)
        if not motivo and viejo in validas:
            motivo = f"el viejo ya está sustituido por {validas[viejo]}"
        if motivo:
            descartadas.append((viejo, vigente, motivo))
        else:
            validas[viejo] = vigente
    ciclo = {v for v in validas if _vuelve(v, validas)}
    descartadas += [(v, validas[v], "cadena circular") for v in validas if v in ciclo]
    return [(v, n) for v, n in validas.items() if v not in ciclo], descartadas


def _vuelve(viejo, validas):
    actual = validas[viejo]
    for _ in range(len(validas)):
        if actual == viejo:
            return True
        if actual not in validas:
            return False
        actual = validas[actual]
    return True


def parejas(claves, manifiesto):
    """(ruta del viejo, ruta del vigente) de cada pareja que se puede marcar."""
    return _clasificar(claves, manifiesto)[0]


def descartadas(claves, manifiesto):
    """(viejo, vigente, motivo) de las parejas que no se marcan."""
    return _clasificar(claves, manifiesto)[1]


def aplicar(pares, ids, put):
    """Marca cada pareja con `put(ruta_http, cuerpo) -> (estado, datos)`. Para al primer fallo."""
    for viejo, vigente in pares:
        if viejo not in ids or vigente not in ids:
            raise SystemExit(f"La pareja {viejo} → {vigente} no está en el corpus")
        estado, datos = put(f"/v1/admin/documents/{ids[viejo]}/superseded-by", {"replacementId": ids[vigente]})
        if estado != 200:
            mensaje = (datos or {}).get("detail", datos)
            raise SystemExit(f"La pareja {viejo} → {vigente} no se puede marcar: {mensaje}")
