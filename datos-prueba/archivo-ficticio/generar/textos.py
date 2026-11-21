"""Plantillas de texto de la ampliación. Todo ficticio. Sin IA: bancos de frases y huecos."""

MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"]
NOMBRES = ["Alberto", "Rosa", "Pablo", "Elena", "Diego", "Nuria", "Óscar", "Pilar", "Iván", "Sara", "Rubén", "Alicia",
           "Marcos", "Lucía", "Hugo", "Irene", "Tomás", "Carmen", "Joaquín", "Beatriz", "Samuel", "Natalia"]
APELLIDOS = ["Benítez", "Crespo", "Delgado", "Esteban", "Fuentes", "Guerrero", "Hidalgo", "Lozano", "Medina", "Nieto",
             "Ortega", "Pascual", "Quintana", "Ramos", "Santos", "Toledo", "Vallejo", "Zamora", "Aguado", "Barrio",
             "Cabello", "Duarte", "Escobar", "Ferrero", "Galán", "Heredia", "Iglesias", "Jurado", "Lagos", "Montero"]


def eur(n):
    return (f"{n:,}".replace(",", ".") if n >= 1000 else str(n)) + " euros"


def fecha(d, m, y):
    return f"{d} de {MESES[m - 1]} de {y}"


def persona(rng):
    return f"{rng.choice(NOMBRES)} {rng.choice(APELLIDOS)} {rng.choice(APELLIDOS)}"


RELLENO_POLIZA = [
    "El presente documento recoge las condiciones particulares de la renovación y se completa con las condiciones generales que el tomador declara conocer.",
    "El capital de continente se actualiza automáticamente en cada vencimiento anual conforme al índice del coste de la construcción.",
    "Las cláusulas limitativas de los derechos del asegurado figuran destacadas y han sido aceptadas expresamente por el tomador mediante su firma.",
    "El tomador se compromete a comunicar a la aseguradora cualquier agravación del riesgo, como la realización de obras de importancia en el edificio.",
    "La administración de fincas actúa como mediadora y atenderá la comunicación de los siniestros y la tramitación de la documentación.",
    "En caso de siniestro, el tomador deberá comunicarlo a la aseguradora en el plazo legal desde que tenga conocimiento del mismo.",
]
RELLENO_ANEXO = [
    "Ambas partes han revisado las condiciones del contrato y consideran conveniente actualizarlas a las circunstancias actuales del servicio.",
    "La presidencia de la Comunidad ha prestado su conformidad y ha autorizado a la administración a formalizar este anexo en su nombre.",
    "Las demás cláusulas del contrato permanecen en vigor sin modificación y se aplicarán conforme a lo allí pactado.",
    "En caso de contradicción entre este anexo y el contrato, prevalecerá lo dispuesto en el anexo.",
    "Se firma por duplicado, en el lugar y la fecha indicados en el encabezamiento, para constancia de ambas partes.",
]
RELLENO_COM = [
    "Les rogamos que lo tengan en cuenta y que, si tienen cualquier duda, se pongan en contacto con la oficina de la administración.",
    "Agradecemos de antemano su colaboración y su comprensión por las molestias que pudiera ocasionar.",
    "Esta comunicación se publica también en el tablón de anuncios del portal y se envía por correo electrónico a los propietarios que lo han solicitado.",
    "La administración quedará a disposición de los vecinos para aclarar lo que sea necesario durante los días anteriores.",
]
RELLENO_ACTA = [
    "Se constituye la Junta con el quórum necesario para adoptar acuerdos y actúa como secretaria la administradora de fincas.",
    "Se da lectura al orden del día, que fue remitido con la antelación legal junto con la convocatoria, sin que se formulen objeciones.",
    "El acuerdo se adopta por mayoría de los asistentes y de las cuotas de participación, y queda recogido en este acta para su cumplimiento.",
    "Y no habiendo más asuntos que tratar, se levanta la sesión, de lo que, como secretaria, doy fe con el visto bueno de la presidencia.",
]
RELLENO_NORMA = [
    "Esta norma es de aplicación a todo el personal del despacho y a las comunidades que gestiona, y forma parte del manual interno de procedimientos.",
    "Cualquier excepción debe ser autorizada por el responsable de administración y quedar anotada en el expediente correspondiente.",
    "El incumplimiento reiterado se comunicará a la dirección, que valorará las medidas organizativas oportunas.",
    "Se revisará de forma periódica para adaptarla a los cambios legales y a la experiencia acumulada en la gestión diaria.",
]


def _p(*bloques):
    return "\n\n".join(b.strip() for b in bloques if b)


def poliza_renovada(emp, tomador, domicilio, num, anterior, franq, prima, ini, fin, rng):
    p1 = _p(
        "CONDICIONES PARTICULARES DEL SEGURO MULTIRRIESGO DE COMUNIDADES DE PROPIETARIOS (RENOVACIÓN)",
        f"Póliza nº {num}\nEntidad aseguradora: Seguros Atalaya\nMediador: {emp}, administración de fincas, Puerto Albar",
        f"Tomador del seguro: Comunidad de Propietarios {tomador}, Puerto Albar.\nDomicilio del riesgo: {domicilio}, Puerto Albar.\n"
        f"Vigencia: del {ini} al {fin}.\nPrima anual total, impuestos y recargos incluidos: {prima}.",
        f"SUSTITUCIÓN DE LA PÓLIZA ANTERIOR\nEsta póliza sustituye a la póliza nº {anterior}, que queda sin efecto desde la fecha de inicio de la vigencia indicada. "
        "Desde esa fecha, las condiciones aplicables son únicamente las de la presente póliza.",
        *rng.sample(RELLENO_POLIZA, 3))
    p2 = _p(
        "CONDICIONES ESPECIALES POR GARANTÍA",
        "1. Incendio, explosión y caída de rayo: quedan cubiertos los daños materiales causados al edificio y a sus elementos comunes, incluidos los gastos de extinción y desescombro.",
        f"2. Daños por agua: cubiertos los daños causados por escapes de agua de conducciones comunitarias, con una franquicia de {franq} por siniestro.",
        "3. Daños eléctricos: cubiertos los daños en las instalaciones comunes causados por sobretensión, cortocircuito o caída de rayo.",
        "4. Responsabilidad civil de la comunidad y defensa jurídica, en los términos de las condiciones generales.",
        *rng.sample(RELLENO_POLIZA, 2))
    return [p1, p2]


def anexo_limpieza(emp, tomador, ini, hoy, dias, importe, empresa, rng):
    p = _p(
        "ANEXO AL CONTRATO DE LIMPIEZA DE ZONAS COMUNES",
        f"En Puerto Albar, a {hoy}.",
        f"REUNIDOS\nDe una parte, la Comunidad de Propietarios {tomador}, de Puerto Albar, representada por {emp}, administradora de la finca. "
        f"De otra parte, {empresa}, empresa encargada de la limpieza de zonas comunes.",
        f"ACUERDAN\nPrimera. Frecuencia. A partir del {ini} la limpieza de zonas comunes se prestará {dias} días por semana, en horario de mañana.\n"
        f"Segunda. Precio. A partir de la misma fecha, el importe mensual pasa a ser de {importe}, impuestos incluidos.",
        *rng.sample(RELLENO_ANEXO, 3))
    return [p]


def anexo_ascensor(emp, tomador, ini, hoy, cuota, plazo, empresa, rng, numero):
    p = _p(
        f"ANEXO Nº {numero} AL CONTRATO DE MANTENIMIENTO DE ASCENSOR",
        f"En Puerto Albar, a {hoy}.",
        f"REUNIDOS\nDe una parte, la Comunidad de Propietarios {tomador}, de Puerto Albar, representada por {emp}. De otra parte, {empresa}, empresa conservadora del ascensor.",
        f"ACUERDAN\nPrimera. Cuota mensual. A partir del {ini} la cuota mensual pasa a ser de {cuota} (IVA no incluido).\n"
        f"Segunda. Atención de personas atrapadas. En caso de personas atrapadas en la cabina, el plazo máximo de respuesta será de {plazo} horas.",
        *rng.sample(RELLENO_ANEXO, 3))
    return [p]


def presupuesto(emp, tomador, anio, filas, total, rng):
    tabla = "| Partida | Importe |\n" + "\n".join(f"| {n} | {v} |" for n, v in filas) + f"\n| Total | {total} |"
    p1 = _p(
        f"PRESUPUESTO ANUAL DE GASTOS {anio}",
        f"Comunidad de Propietarios {tomador}, Puerto Albar. Elaborado por {emp} para su presentación a la Junta.",
        "El presupuesto recoge los gastos ordinarios previstos para el ejercicio, calculados a partir de los contratos vigentes y del consumo de los años anteriores.",
        tabla)
    p2 = _p(
        "NOTAS AL PRESUPUESTO",
        "Las partidas de limpieza y de mantenimiento se han calculado multiplicando por doce la cuota mensual vigente de cada contrato.",
        "La partida de seguro corresponde a la prima anual de la póliza vigente. El fondo de reserva se dota con el porcentaje legal sobre el presupuesto ordinario.",
        "Las cuotas se girarán por trimestres, en la forma habitual, y cualquier desviación se informará a la Junta en la reunión ordinaria siguiente.")
    return [p1, p2]


def comunicacion_junta(emp, tomador, hoy, fecha_j, hora, lugar, tema, rng):
    return [_p(
        "COMUNICACIÓN A LOS PROPIETARIOS",
        f"Comunidad de Propietarios {tomador}, Puerto Albar. {hoy}.",
        f"Se convoca Junta General Extraordinaria el día {fecha_j}, a las {hora} horas en primera convocatoria, en {lugar}. Asunto principal: {tema}.",
        *rng.sample(RELLENO_COM, 2), f"Atentamente, {emp}.")]


def comunicacion_cuota(emp, tomador, hoy, limite, cuota, rng):
    return [_p(
        "COMUNICACIÓN A LOS PROPIETARIOS",
        f"Comunidad de Propietarios {tomador}, Puerto Albar. {hoy}.",
        f"Les recordamos que el recibo del trimestre en curso, por un importe de {cuota} por vivienda tipo, debe abonarse como máximo el {limite}. "
        "Pasada esa fecha se aplicará el recargo previsto en los estatutos.",
        *rng.sample(RELLENO_COM, 2), f"Atentamente, {emp}.")]


def comunicacion_obras(emp, tomador, hoy, empresa, inicio, dias, zona, rng):
    return [_p(
        "COMUNICACIÓN A LOS PROPIETARIOS",
        f"Comunidad de Propietarios {tomador}, Puerto Albar. {hoy}.",
        f"La empresa {empresa} realizará trabajos de mantenimiento en {zona}. Comenzarán el {inicio} y tendrán una duración prevista de {dias} días laborables.",
        *rng.sample(RELLENO_COM, 2), f"Atentamente, {emp}.")]


def aviso_corte(origen, tomador, dia, desde, hasta, rng):
    return [_p(
        f"AVISO DE CORTE PROGRAMADO DEL SUMINISTRO DE AGUA ({origen.upper()})",
        f"Comunidad de Propietarios {tomador}, Puerto Albar.",
        f"Se informa de que el día {dia} se realizará un corte programado del suministro en el edificio, de {desde} a {hasta}, para trabajos en la acometida.",
        *rng.sample(RELLENO_COM, 2))]


def acta_posterior(tomador, emp, fecha_a, hora, puntos, rng):
    cuerpo = "\n\n".join(f"Punto {i}. {t}" for i, t in enumerate(puntos, 1))
    p1 = _p(
        "ACTA DE LA JUNTA GENERAL EXTRAORDINARIA DE PROPIETARIOS",
        f"COMUNIDAD DE PROPIETARIOS {tomador.upper()}",
        f"En Puerto Albar, a {fecha_a}, a las {hora} horas, se reúnen los propietarios, previa convocatoria remitida por {emp}.",
        rng.choice(RELLENO_ACTA[:2]), cuerpo)
    p2 = _p("CIERRE", rng.choice(RELLENO_ACTA[2:3]), RELLENO_ACTA[3], "Vº Bº La presidencia                         La secretaria-administradora")
    return [p1, p2]


def norma(titulo, codigo, vigencia, cuerpo, rng, sustituye=None):
    bloques = [titulo.upper(), f"Código: {codigo}. Vigencia: {vigencia}."]
    if sustituye:
        bloques.append(f"Esta norma sustituye a la anterior {sustituye}, que queda sin efecto.")
    bloques.append("ÁMBITO Y RESPONSABLES\nLa norma obliga al personal de administración y a los gestores de cada comunidad. El responsable de administración vela por su cumplimiento y resuelve las dudas de interpretación.")
    bloques.append("CONTENIDO")
    bloques += cuerpo
    bloques.append("Las comunicaciones que se deriven de esta norma se registrarán en el expediente de la comunidad afectada, con la fecha y la persona que las realiza.")
    p1 = _p(*bloques)
    p2 = _p("DISPOSICIONES FINALES", *rng.sample(RELLENO_NORMA, 3))
    return [p1, p2]


# ----------------------------------------------------------------------------- expedientes de casos
OBRAS = {  # tipo de caso: (trabajo, causa)
    "filtracion-azotea": ("impermeabilización de la azotea", "pérdida de estanquidad de la lámina de la azotea en los encuentros con los petos"),
    "filtracion-cubierta": ("reparación de la cubierta", "rotura de varias tejas y falta de solape en la cumbrera"),
    "bajante-patio": ("sustitución del tramo de bajante del patio", "fisuración de la bajante de fibrocemento junto a la abrazadera"),
    "condensaciones-4a": ("mejora de la ventilación de la vivienda 4.º A", "falta de ventilación y puente térmico en el cerramiento de fachada"),
    "capilaridad-contadores": ("tratamiento de humedad por capilaridad en el cuarto de contadores", "ascenso de humedad desde la solera por falta de barrera"),
    "colector-garaje": ("sustitución del tramo de colector del garaje", "intrusión de raíces en las juntas del colector"),
    "fuga-riego": ("reparación de la red de riego", "rotura de un ramal enterrado de la red de riego"),
    "vaso-piscina": ("reparación del vaso de la piscina", "pérdida de agua por fisuras en el revestimiento del vaso"),
    "ruido-ascensor": ("aislamiento de la maquinaria del ascensor", "transmisión de vibraciones de la máquina a la estructura"),
    "ruido-piscina": ("insonorización del cuarto de depuración", "vibración del grupo de bombeo apoyado sin amortiguación"),
    "humos-local": ("sellado de la chimenea del local", "salida de humos por una conducción sin la altura necesaria"),
}
PROVEEDORES = ["Reformas Atalaya S.L.", "Construcciones Mirador S.L.", "Obras y Servicios Levante S.L.", "Impermeabilizaciones Norte S.L.",
               "Reparaciones Alcor S.L.", "Instalaciones Sierra S.L.", "Mantenimientos Delta S.L."]


def formas_numero(anio, numero):
    n = f"{int(numero):03d}"
    return [f"expte. {anio}/{n}", f"exp. {int(n)}/{str(anio)[2:]}", f"su referencia {n}", f"expediente nº {n}"]


def l_perito(p):
    return f"Perito: {p}"


def l_total_ppto(t):
    return f"Total del presupuesto: {eur(t)}"


def l_total_fact(t):
    return f"Importe total de la factura: {eur(t)}"


def _pag(titulo, *bloques):
    return _p(titulo, *bloques)


def informe_perito(x):
    p1 = _pag(f"INFORME PERICIAL ({x['f'][0]})", f"Comunidad de Propietarios {x['com']}, Puerto Albar.", l_perito(x["perito"]),
              f"Fecha del informe: {x['fechas']['informe']}.",
              f"OBJETO\nEl perito visita el inmueble a petición de {x['emp']} para determinar el origen de la incidencia del caso y proponer la reparación: {x['trabajo']}.",
              "METODOLOGÍA\nInspección visual de las zonas afectadas, toma de fotografías y comprobación de las instalaciones comunes con los medios habituales.")
    p2 = _pag("CONCLUSIONES", f"Causa probable: {x['causa']}.",
              f"Se recomienda la {x['trabajo']}. El perito estima el coste de la reparación en {eur(x['estimado'])}, que deberá contrastarse con al menos dos presupuestos.",
              "El presente informe se emite con la información disponible a la fecha y sin carácter vinculante para la Comunidad.")
    return [p1, p2]


def presupuesto_exp(x, prov, ref, filas, total, fecha_p, sustituye=None):
    tabla = "| Partida | Importe |\n" + "\n".join(f"| {n} | {eur(v)} |" for n, v in filas)
    nota = f"Este presupuesto revisado sustituye al anterior de {sustituye}, que queda sin efecto." if sustituye else ""
    return [_pag(f"PRESUPUESTO DE OBRA ({ref})", f"{prov}. Para: Comunidad de Propietarios {x['com']}, Puerto Albar.",
                 f"Fecha: {fecha_p}.", f"Trabajo presupuestado: {x['trabajo']}.", tabla, l_total_ppto(total), nota,
                 "Validez de la oferta: 30 días. IVA incluido.")]


def factura_exp(x, prov, ref, total, fecha_f, num):
    return [_pag(f"FACTURA Nº {num} ({ref})", f"Emisor: {prov}. Cliente: Comunidad de Propietarios {x['com']}, Puerto Albar.",
                 f"Fecha de la factura: {fecha_f}.", f"Concepto: {x['trabajo']}, según presupuesto aceptado por la Comunidad.",
                 l_total_fact(total), "Forma de pago: transferencia a 30 días desde la fecha de la factura.")]


def correo_impreso(x, de, para, fecha_c, asunto, cuerpo, ref):
    return [_pag("IMPRESIÓN DE CORREO ELECTRÓNICO", f"De: {de}\nPara: {para}\nFecha: {fecha_c}\nAsunto: {asunto} ({ref})", cuerpo)]


def parte_trabajo(x, prov, ref, fecha_p, dias):
    return [_pag(f"PARTE DE TRABAJO ({ref})", f"Empresa: {prov}. Comunidad de Propietarios {x['com']}.", f"Fecha de cierre del parte: {fecha_p}.",
                 f"Trabajos realizados: {x['trabajo']}, en {dias} días laborables. Retirada de escombros y limpieza de la zona al terminar.")]


def contrato_perito(com, tarifa, desplaz, emp):
    return [_pag("CONTRATO MARCO DE SERVICIOS PERICIALES", f"Comunidad de Propietarios {com}, Puerto Albar, representada por {emp}.",
                 f"Tarifa del perito: {eur(tarifa)} por hora de visita o de redacción. Desplazamiento: {eur(desplaz)} por visita.",
                 "Los informes se entregan en un plazo máximo de 10 días desde la visita. Este contrato se aplica a todos los informes que encargue la Comunidad.")]


def poliza_obras(com, num, prima, emp):
    return [_pag("CONDICIONES DE LA PÓLIZA DE OBRAS Y REPARACIONES", f"Póliza nº {num}. Tomador: Comunidad de Propietarios {com}, Puerto Albar.",
                 f"Mediador: {emp}. Prima anual: {eur(prima)}.", "Cubre la responsabilidad civil de las obras de reparación contratadas por la Comunidad en las zonas comunes.")]


def protocolo_peritajes(emp):
    return [_pag("PROTOCOLO DE PERITAJES Y OBRAS", f"{emp}.", "Todo informe pericial se encarga con el contrato marco de la comunidad.",
                 "Para toda reparación se piden al menos dos presupuestos comparables y se acepta uno por escrito antes de empezar.",
                 "Los correos con proveedores y peritos se imprimen y se archivan en el expediente del caso.")]


def correo_ajeno(com_otra, ref, emp):
    return [_pag("IMPRESIÓN DE CORREO ELECTRÓNICO", f"De: Reformas Atalaya S.L.\nPara: {emp}\nAsunto: Visita de obra en {com_otra}",
                 f"Les confirmamos la visita de obra en la Comunidad de Propietarios {com_otra}, a la que se refiere {ref} de nuestros archivos. "
                 "Es una obra distinta de las demás que gestionamos con ustedes.")]
