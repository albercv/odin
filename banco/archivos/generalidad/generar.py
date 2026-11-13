"""Genera la capa «generalidad» (D-084): la empresa ficticia Brumeral. Plantillas propias, sin IA ni red."""
import datetime as dt
import json
import re
import shutil
import sys
import unicodedata
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI.parents[2] / "experimentos" / "2026-11-12-archivo-ficticio" / "medir"))
import puntuar  # noqa: E402  el puntuador del archivo ficticio, importado sin modificarlo

DATOS = AQUI / "datos"
EMPRESA = "brumeral"
NOMBRE_EMPRESA = "Brumeral Ingeniería y Consultoría, S.L."
DOMICILIO = "Polígono Las Viñas, nave 12, 47999 Valdecerro"
SEMILLA = 7
PRIMERA_SUBIDA = dt.date(2024, 11, 1)
NO_AFIRMAR = ["comunidad", "propietario", "finca", "vecino", "junta de propietarios", "derrama"]
REDACCION = re.compile(r"\b(comunidad|propietario|vecino|finca|junta|derrama)")
AJENOS = ["ribera/", "albar/"]

NOMBRES = {
    "talleres-murcano": "Talleres Murcano, S.L.",
    "conservas-dalmira": "Conservas Dalmira, S.A.",
    "logistica-penedal": "Logística Penedal, S.L.",
    "alojamientos-cerrolago": "Alojamientos Cerrolago, S.L.",
    "bodegas-quintafria": "Bodegas Quintafría, S.L.",
}
TRABAJADORES = [
    {"clave": "t09", "empresa": EMPRESA, "nombre": "Irene", "apellidos": "Salcedo Moya", "proyectos": ["*"]},
    {"clave": "t10", "empresa": EMPRESA, "nombre": "Óscar", "apellidos": "Leiva Durán",
     "proyectos": ["talleres-murcano", "conservas-dalmira", "bodegas-quintafria"]},
    {"clave": "t11", "empresa": EMPRESA, "nombre": "Nuria", "apellidos": "Baeza Ortiz", "proyectos": ["*"]},
]
ANCLA_ADENDA = "Desde el 1 de febrero de 2025, la cuota mensual del mantenimiento pasa a ser de 1.240 euros más IVA."


# ---------- utilidades ----------
def ruta(proyecto, fichero):
    return f"{EMPRESA}/{proyecto}/{fichero}"


def eur(cents):
    """Céntimos a formato español: 1150000 -> '11.500,00'."""
    return f"{cents // 100:,}".replace(",", ".") + f",{cents % 100:02d}"


def dmy(fecha):
    return fecha.strftime("%d/%m/%Y")


def subido(fecha):
    return (max(fecha, PRIMERA_SUBIDA) + dt.timedelta(days=2)).isoformat() + "T09:00:00Z"


def sin_tildes(texto):
    descompuesto = unicodedata.normalize("NFD", texto.lower())
    return "".join(c for c in descompuesto if unicodedata.category(c) != "Mn")


def exigir(condicion, mensaje):
    if not condicion:
        raise AssertionError(mensaje)


# ---------- documentos ----------
def doc(proyecto, fichero, titulo, fecha, paginas):
    return {"proyecto": proyecto, "fichero": fichero, "titulo": titulo,
            "fecha": dt.date.fromisoformat(fecha), "cliente": NOMBRES.get(proyecto, "Uso interno"),
            "paginas": paginas}


def factura(proyecto, numero, fecha, concepto, base, vencimiento):
    base_c = base * 100
    iva_c = base_c * 21 // 100
    cuerpo = [f"Concepto: {concepto}", f"Base imponible: {eur(base_c)} euros", f"IVA 21 %: {eur(iva_c)} euros",
              f"Total factura: {eur(base_c + iva_c)} euros", f"Vencimiento: {vencimiento}"]
    return doc(proyecto, f"factura-{numero}.txt", f"Factura {numero}", fecha, [cuerpo])


def cabecera(d, n, total):
    return [NOMBRE_EMPRESA, DOMICILIO, d["titulo"],
            f"Fecha: {dmy(d['fecha'])}. Cliente: {d['cliente']}. Página {n} de {total}."]


def paginas_de(d):
    """Texto de cada página, sin la línea marcadora (que solo lleva el fichero)."""
    total = len(d["paginas"])
    return ["\n".join([*cabecera(d, n, total), "", *cuerpo]) for n, cuerpo in enumerate(d["paginas"], 1)]


DOCUMENTOS = [
    doc("_general", "norma-facturacion.txt", "Norma interna de facturación", "2024-11-20", [[
        "Norma interna de facturación del despacho, de uso para todo el equipo.",
        "El plazo de pago por defecto con los clientes es de 45 días desde la fecha de la factura.",
        "Las facturas se numeran F-AAAA-NNN y las rectificativas R-AAAA-NNN.",
    ]]),
    doc("_general", "tarifas-honorarios-2024.txt", "Tarifas de honorarios 2024", "2024-12-02", [[
        "Tarifas por hora vigentes durante 2024.",
        "Hora de ingeniero sénior: 68 euros.",
        "Hora de ingeniero júnior: 45 euros.",
    ]]),
    doc("_general", "tarifas-honorarios-2025.txt", "Tarifas de honorarios 2025", "2024-12-18", [[
        "Estas tarifas sustituyen a las tarifas de honorarios 2024 desde el 1 de enero de 2025.",
        "Hora de ingeniero sénior: 72 euros.",
        "Hora de ingeniero júnior: 48 euros.",
    ]]),
    doc("_general", "procedimiento-alta-clientes.txt", "Procedimiento de alta de clientes", "2024-11-25", [[
        "Cuatro pasos:",
        "1. Pedir el CIF y los datos de facturación de la empresa cliente.",
        "2. Firmar el contrato o aceptar la oferta.",
        "3. Crear el proyecto del cliente en Odín.",
        "4. Asignar un ingeniero responsable.",
    ]]),
]

DOCUMENTOS += [
    doc("talleres-murcano", "contrato-mantenimiento-2024.txt", "Contrato de mantenimiento CM-2024-011", "2024-12-10", [
        ["Objeto: mantenimiento preventivo de la instalación de aire comprimido y de la red eléctrica de baja tensión.",
         "Periodicidad: una visita preventiva al mes."],
        ["Precio.", "La cuota mensual del mantenimiento es de 1.150 euros más IVA.",
         "Plazo de pago: 30 días desde la fecha de la factura."],
    ]),
    doc("talleres-murcano", "adenda-1-contrato-mantenimiento.txt", "Adenda 1 al contrato CM-2024-011", "2025-01-15", [[
        "En Valdecerro, a 15 de enero de 2025.",
        "Esta adenda modifica la cláusula de precio del contrato CM-2024-011.",
        ANCLA_ADENDA,
    ]]),
    factura("talleres-murcano", "F-2025-014", "2025-01-31", "Mantenimiento de enero de 2025.", 1150, "02/03/2025"),
    factura("talleres-murcano", "F-2025-047", "2025-03-31", "Mantenimiento de marzo de 2025.", 1240, "30/04/2025"),
    factura("talleres-murcano", "F-2025-089", "2025-06-30", "Reparación del compresor 2.", 2380, "30/07/2025"),
    doc("conservas-dalmira", "oferta-auditoria-energetica-v1.txt", "Oferta de auditoría energética (versión 1)", "2025-02-10", [[
        "Precio de la auditoría energética: 18.400 euros más IVA.",
        "Plazo de entrega del informe: 10 semanas.",
    ]]),
    doc("conservas-dalmira", "oferta-auditoria-energetica-v2.txt", "Oferta de auditoría energética (versión 2)", "2025-03-03", [[
        "Esta versión sustituye a la versión 1 de la oferta, de 10 de febrero de 2025.",
        "Precio de la auditoría energética: 16.900 euros más IVA.",
        "Plazo de entrega: 12 semanas.",
        "Forma de pago: anticipo del 40 % a la aceptación y el resto a la entrega del informe.",
    ]]),
    factura("conservas-dalmira", "F-2025-052", "2025-04-07", "Anticipo del 50 % de la auditoría energética.", 8450, "07/05/2025"),
    doc("conservas-dalmira", "factura-rectificativa-R-2025-003.txt", "Factura rectificativa R-2025-003", "2025-04-22", [[
        "Rectifica la factura F-2025-052.",
        "Motivo: el anticipo pactado en la oferta es del 40 %, no del 50 %.",
        "Base imponible rectificada: 6.760,00 euros.",
        "IVA 21 %: 1.419,60 euros.",
        "Total rectificado: 8.179,60 euros.",
    ]]),
    doc("conservas-dalmira", "informe-auditoria-energetica.txt", "Informe de auditoría energética", "2025-06-30", [
        ["Alcance: planta de envasado de la empresa cliente.", "Fecha del informe: 30/06/2025."],
        ["Tabla de consumos anuales.", "Electricidad: 1.284.000 kWh al año.", "Gas natural: 412.000 kWh al año."],
        ["Medidas propuestas: variadores en las bombas, recuperación de calor de los compresores e iluminación LED.",
         "Ahorro anual estimado con las medidas propuestas: 186.500 kWh, unos 27.900 euros al año."],
    ]),
]

DOCUMENTOS += [
    doc("logistica-penedal", "contrato-legalizacion-fotovoltaica.txt", "Contrato de ingeniería CI-2025-004", "2025-02-12", [
        ["Objeto: legalización de la instalación fotovoltaica de autoconsumo de 380 kWp en la cubierta de la nave principal."],
        ["Honorarios: 9.600 euros más IVA, en tres pagos.", "Plazo de pago: 60 días desde la fecha de la factura."],
    ]),
    doc("logistica-penedal", "correo-2025-03-plazo-pago.txt", "Correo de Logística Penedal sobre el plazo de pago", "2025-03-18", [[
        "De: administración de Logística Penedal. Para: Irene Salcedo Moya, de Brumeral. Asunto: plazo de pago.",
        "Según lo hablado por teléfono, os pagaremos las facturas a 30 días.",
        "Un saludo.",
    ]]),
    factura("logistica-penedal", "F-2025-061", "2025-04-30", "Primer pago de honorarios del contrato CI-2025-004.", 3200, "29/06/2025"),
    factura("logistica-penedal", "F-2025-075", "2025-06-30", "Segundo pago de honorarios del contrato CI-2025-004.", 3200, "29/08/2025"),
    factura("logistica-penedal", "F-2025-098", "2025-09-30", "Tercer pago de honorarios del contrato CI-2025-004.", 3200, "29/11/2025"),
    doc("logistica-penedal", "acta-reunion-seguimiento-2025-05.txt", "Acta de la reunión de seguimiento de mayo de 2025", "2025-05-20", [[
        "Reunión de seguimiento del 20/05/2025.",
        "Punto 3. Legalización de la instalación fotovoltaica: la distribuidora pidió más documentación.",
        "Fecha prevista de la inspección: 15 de julio de 2025.",
    ]]),
]

DOCUMENTOS += [
    doc("alojamientos-cerrolago", "presupuesto-climatizacion-2024.txt", "Presupuesto P-2024-031, reforma de la climatización", "2024-11-05", [
        ["Objeto: reforma de la climatización de las 42 habitaciones y de las zonas de uso común del hotel."],
        ["Importe: 14.250 euros más IVA.", "Plazo de ejecución del proyecto: 10 semanas desde la aceptación."],
    ]),
    doc("alojamientos-cerrolago", "correo-2025-01-plazo.txt", "Correo de Brumeral a Alojamientos Cerrolago sobre el plazo", "2025-01-14", [[
        "De: Irene Salcedo Moya, de Brumeral. Para: Alojamientos Cerrolago, S.L. Asunto: plazo de la climatización.",
        "El proyecto de climatización estará listo en 8 semanas desde la aceptación.",
        "Un saludo, Irene Salcedo.",
    ]]),
    doc("alojamientos-cerrolago", "aceptacion-presupuesto.txt", "Aceptación del presupuesto P-2024-031", "2025-01-20", [[
        "Alojamientos Cerrolago, S.L. acepta el presupuesto P-2024-031 de reforma de la climatización.",
        "Firmado: la gerencia de Alojamientos Cerrolago, S.L., 20/01/2025.",
    ]]),
]

DOCUMENTOS += [
    doc("bodegas-quintafria", "contrato-servicios-2023.txt", "Contrato de servicios de ingeniería CS-2023-02", "2023-03-01", [[
        "Objeto: servicios de ingeniería para la bodega.",
        "Honorarios anuales: 7.800 euros más IVA.",
        "Duración hasta el 31 de marzo de 2025.",
    ]]),
    doc("bodegas-quintafria", "contrato-servicios-2025.txt", "Contrato de servicios de ingeniería CS-2025-07", "2025-03-25", [[
        "Este contrato sustituye y deja sin efecto el contrato CS-2023-02.",
        "Entrada en vigor: 1 de abril de 2025.",
        "Honorarios anuales: 8.400 euros más IVA.",
        "Servicios: ampliación de la sala de barricas, dirección de obra y legalización de la instalación frigorífica.",
    ]]),
    factura("bodegas-quintafria", "F-2025-033", "2025-04-30",
            "Honorarios del primer trimestre del contrato CS-2025-07.", 2100, "30/05/2025"),
]


# ---------- fichas ----------
def ficha(id_, proyecto, estado, titulo, texto, creada, error=False):
    return {"id": id_, "empresa": EMPRESA, "proyecto": proyecto, "autor": "t11", "estado": estado,
            "titulo": titulo, "texto": texto, "creada": creada, "error_a_proposito": error}


FICHAS = [
    ficha("FB01", "talleres-murcano", "APPROVED", "Contacto de mantenimiento en Talleres Murcano",
          "El jefe de mantenimiento de Talleres Murcano prefiere que le llamen antes de las 8:30, porque luego está en el taller y no coge el teléfono.",
          "2025-02-03T10:00:00Z"),
    ficha("FB02", "bodegas-quintafria", "APPROVED", "Entrada en vigor del contrato con Bodegas Quintafría",
          "El contrato nuevo con Bodegas Quintafría empezó el 1 de marzo de 2025.",
          "2025-03-27T10:00:00Z", error=True),
    ficha("FB03", "logistica-penedal", "PENDING", "Acceso a la nave de Logística Penedal",
          "Para entrar en la nave hay que usar la puerta 3 y pedir la tarjeta en recepción.",
          "2025-05-21T10:00:00Z"),
    ficha("FB04", "conservas-dalmira", "APPROVED", "Rectificativa del anticipo de Conservas Dalmira",
          "La factura F-2025-052 cobró el 50 % de anticipo por error; se corrigió con la rectificativa R-2025-003 al 40 %.",
          "2025-04-28T10:00:00Z"),
    ficha("FB05", "alojamientos-cerrolago", "REJECTED", "Plazo de la climatización de Cerrolago",
          "El proyecto de Cerrolago se entrega en 6 semanas.",
          "2025-06-02T10:00:00Z"),
]
FICHAS_POR_ID = {f["id"]: f for f in FICHAS}


# ---------- preguntas ----------
def cita(proyecto, fichero, pagina, ancla):
    return {"doc": ruta(proyecto, fichero), "paginas": [pagina], "ancla": ancla}


def pregunta(n, tipo, actor, texto, citas=(), claves=(), fichas=(), abstener=False, todas=False,
             prohibidas=(), sustituidos=(), docs_prohibidos=()):
    q = {"id": f"GEN{n:02d}", "tipo": tipo, "origen": "generalidad", "actor": actor, "pregunta": texto,
         "citas_resueltas": list(citas), "fichas_esperadas": list(fichas), "todas_las_citas": todas,
         "claves": [list(g) for g in claves], "abstener": abstener, "prohibidas": list(prohibidas),
         "sustituidos": list(sustituidos), "docs_prohibidos": list(docs_prohibidos)}
    if actor in ("t09", "t10"):
        q["rol"] = "neutro"
        if not abstener:
            q["no_afirmar"] = list(NO_AFIRMAR)
    return q


MURCANO_CONTRATO = ruta("talleres-murcano", "contrato-mantenimiento-2024.txt")
DALMIRA_V1 = ruta("conservas-dalmira", "oferta-auditoria-energetica-v1.txt")
TARIFAS_2024 = ruta("_general", "tarifas-honorarios-2024.txt")
BODEGAS_2023 = ruta("bodegas-quintafria", "contrato-servicios-2023.txt")


def preguntas_datos():
    return preguntas_datos_a() + preguntas_datos_b()


def preguntas_datos_a():
    return [
        pregunta(1, "generica", "t09", "¿Con qué empresas trabajamos?", docs_prohibidos=AJENOS),
        pregunta(2, "generica", "t09", "¿Quiénes son nuestros clientes?", docs_prohibidos=AJENOS),
        pregunta(3, "generica", "t10", "¿Con qué clientes trabajo yo?",
                 docs_prohibidos=AJENOS + ["brumeral/logistica-penedal/", "brumeral/alojamientos-cerrolago/"]),
        pregunta(4, "generica", "t09", "¿Qué proyectos tenemos en Odín?", docs_prohibidos=AJENOS),
        pregunta(5, "dato_concreto", "t09", "¿Qué importe total tiene la factura F-2025-047 de Talleres Murcano?",
                 citas=[cita("talleres-murcano", "factura-F-2025-047.txt", 1, "Total factura: 1.500,40 euros")],
                 claves=[["1.500,40"]]),
        pregunta(6, "dato_concreto", "t09", "¿Cuándo vence la factura F-2025-061 de Logística Penedal?",
                 citas=[cita("logistica-penedal", "factura-F-2025-061.txt", 1, "Vencimiento: 29/06/2025")],
                 claves=[["29/06/2025"]]),
        pregunta(7, "version", "t09", "¿Cuál es la cuota mensual actual del mantenimiento de Talleres Murcano?",
                 citas=[cita("talleres-murcano", "adenda-1-contrato-mantenimiento.txt", 1, ANCLA_ADENDA)],
                 claves=[["1.240"]], prohibidas=["1.150"], sustituidos=[MURCANO_CONTRATO]),
        pregunta(8, "version", "t09", "¿Qué precio tiene la auditoría energética de Conservas Dalmira?",
                 citas=[cita("conservas-dalmira", "oferta-auditoria-energetica-v2.txt", 1,
                             "Precio de la auditoría energética: 16.900 euros más IVA.")],
                 claves=[["16.900"]], prohibidas=["18.400"], sustituidos=[DALMIRA_V1]),
    ]


def preguntas_datos_b():
    return preguntas_datos_c() + preguntas_datos_d()


def preguntas_datos_c():
    return [
        pregunta(9, "version", "t10", "¿Cuánto cobramos por hora de ingeniero sénior?",
                 citas=[cita("_general", "tarifas-honorarios-2025.txt", 1, "Hora de ingeniero sénior: 72 euros.")],
                 claves=[["72"]], prohibidas=["68"], sustituidos=[TARIFAS_2024]),
        pregunta(10, "version", "t09", "¿Qué contrato tenemos en vigor con Bodegas Quintafría?",
                 citas=[cita("bodegas-quintafria", "contrato-servicios-2025.txt", 1,
                             "Este contrato sustituye y deja sin efecto el contrato CS-2023-02.")],
                 claves=[["CS-2025-07"]], sustituidos=[BODEGAS_2023]),
        pregunta(11, "identificador", "t09", "¿Qué número tiene la factura que rectificó el anticipo de Conservas Dalmira?",
                 citas=[cita("conservas-dalmira", "factura-rectificativa-R-2025-003.txt", 1, "Rectifica la factura F-2025-052.")],
                 claves=[["R-2025-003"]], fichas=["FB04"]),
        pregunta(12, "dato_concreto", "t09", "¿Cuál es el ahorro anual estimado en la auditoría energética de Conservas Dalmira?",
                 citas=[cita("conservas-dalmira", "informe-auditoria-energetica.txt", 3,
                             "Ahorro anual estimado con las medidas propuestas: 186.500 kWh, unos 27.900 euros al año.")],
                 claves=[["186.500"]]),
    ]


def preguntas_datos_d():
    return [
        pregunta(13, "dato_concreto", "t09", "¿Qué plazo de pago aplicamos por defecto a los clientes?",
                 citas=[cita("_general", "norma-facturacion.txt", 1,
                             "El plazo de pago por defecto con los clientes es de 45 días desde la fecha de la factura.")],
                 claves=[["45"]]),
        pregunta(14, "dato_concreto", "t09", "¿Qué potencia tiene la instalación fotovoltaica de Logística Penedal?",
                 citas=[cita("logistica-penedal", "contrato-legalizacion-fotovoltaica.txt", 1,
                             "instalación fotovoltaica de autoconsumo de 380 kWp en la cubierta de la nave principal")],
                 claves=[["380"]]),
        pregunta(15, "vocabulario", "t09", "¿Cuánto nos paga al mes Talleres Murcano por revisarle las máquinas?",
                 citas=[cita("talleres-murcano", "adenda-1-contrato-mantenimiento.txt", 1, ANCLA_ADENDA)],
                 claves=[["1.240"]], prohibidas=["1.150"], sustituidos=[MURCANO_CONTRATO]),
        pregunta(16, "vocabulario", "t09", "¿Para cuándo esperaban al inspector por lo de las placas solares de Penedal?",
                 citas=[cita("logistica-penedal", "acta-reunion-seguimiento-2025-05.txt", 1,
                             "Fecha prevista de la inspección: 15 de julio de 2025.")],
                 claves=[["15 de julio de 2025"]]),
    ]


def preguntas_versiones_y_contradicciones():
    return [
        pregunta(17, "version", "t10", "¿Cuánto se cobró finalmente de anticipo por la auditoría de Conservas Dalmira?",
                 citas=[cita("conservas-dalmira", "factura-rectificativa-R-2025-003.txt", 1,
                             "Base imponible rectificada: 6.760,00 euros.")],
                 claves=[["6.760"]], prohibidas=["8.450"]),
        pregunta(18, "contradiccion", "t09", "¿Qué plazo de pago tiene Logística Penedal?",
                 citas=[cita("logistica-penedal", "contrato-legalizacion-fotovoltaica.txt", 2,
                             "Plazo de pago: 60 días desde la fecha de la factura."),
                        cita("logistica-penedal", "correo-2025-03-plazo-pago.txt", 1,
                             "Según lo hablado por teléfono, os pagaremos las facturas a 30 días.")],
                 claves=[["60"], ["30"]], todas=True),
        pregunta(19, "contradiccion", "t09", "¿En cuántas semanas se entrega el proyecto de climatización de Alojamientos Cerrolago?",
                 citas=[cita("alojamientos-cerrolago", "presupuesto-climatizacion-2024.txt", 2,
                             "Plazo de ejecución del proyecto: 10 semanas desde la aceptación."),
                        cita("alojamientos-cerrolago", "correo-2025-01-plazo.txt", 1,
                             "El proyecto de climatización estará listo en 8 semanas desde la aceptación.")],
                 claves=[["10"], ["8"]], todas=True),
        pregunta(20, "contradiccion", "t09", "¿Desde cuándo está en vigor el contrato con Bodegas Quintafría?",
                 citas=[cita("bodegas-quintafria", "contrato-servicios-2025.txt", 1, "Entrada en vigor: 1 de abril de 2025.")],
                 claves=[["1 de abril"], ["1 de marzo"]], fichas=["FB02"], todas=True),
        pregunta(21, "lista_completa", "t09", "¿Qué facturas hemos emitido a Logística Penedal?",
                 citas=[cita("logistica-penedal", f"factura-F-2025-{n}.txt", 1, f"Factura F-2025-{n}")
                        for n in ("061", "075", "098")],
                 claves=[["F-2025-061"], ["F-2025-075"], ["F-2025-098"]], todas=True),
        pregunta(22, "lista_completa", "t10", "Enumera las facturas de Talleres Murcano.",
                 citas=[cita("talleres-murcano", f"factura-F-2025-{n}.txt", 1, f"Factura F-2025-{n}")
                        for n in ("014", "047", "089")],
                 claves=[["F-2025-014"], ["F-2025-047"], ["F-2025-089"]], todas=True),
    ]


def preguntas_fichas_sin_fuente_y_aislamiento():
    return [
        pregunta(23, "ficha", "t09", "¿A qué hora conviene llamar al jefe de mantenimiento de Talleres Murcano?",
                 fichas=["FB01"], claves=[["8:30"]]),
        pregunta(24, "sin_fuente", "t09", "¿Qué comunidades de propietarios gestionamos?", abstener=True),
        pregunta(25, "sin_fuente", "t09", "¿Cuándo es la próxima junta de propietarios de Bodegas Quintafría?", abstener=True),
        pregunta(26, "sin_fuente", "t09", "¿Qué derrama se aprobó para Alojamientos Cerrolago?", abstener=True),
        pregunta(27, "sin_fuente", "t09", "¿Por qué puerta se entra en la nave de Logística Penedal?", abstener=True),
        pregunta(28, "sin_fuente", "t09", "¿Cuántos trabajadores tiene Conservas Dalmira?", abstener=True),
        pregunta(29, "aislamiento", "t10", "¿Qué importe tiene la última factura de Logística Penedal?", abstener=True,
                 docs_prohibidos=["brumeral/logistica-penedal/"]),
        pregunta(30, "aislamiento", "t01", "¿Cuánto le facturamos a Talleres Murcano en marzo de 2025?", abstener=True,
                 docs_prohibidos=["brumeral/"]),
        pregunta(31, "aislamiento", "t09", "¿Qué franquicia tiene la póliza de seguro de Calle Olmo 14?", abstener=True,
                 docs_prohibidos=AJENOS),
        pregunta(32, "aislamiento", "t06", "¿Qué contratos tenemos con Bodegas Quintafría?", abstener=True,
                 docs_prohibidos=["brumeral/"]),
    ]


def preguntas():
    return preguntas_datos() + preguntas_versiones_y_contradicciones() + preguntas_fichas_sin_fuente_y_aislamiento()


# ---------- claves ----------
def fuente(proyecto, fichero, pagina, frase):
    return {"doc": ruta(proyecto, fichero), "pagina": pagina, "frase": frase}


def fuente_ficha(id_, frase):
    return {"ficha": id_, "frase": frase}


def vigente(proyecto, fichero):
    return {"doc": ruta(proyecto, fichero)}


def claves_versiones():
    return claves_versiones_a() + claves_versiones_b()


def claves_versiones_a():
    return [
        {"id": "KB01", "tipo": "version", "fuentes": [
            fuente("_general", "tarifas-honorarios-2024.txt", 1, "Hora de ingeniero sénior: 68 euros."),
            fuente("_general", "tarifas-honorarios-2025.txt", 1, "Hora de ingeniero sénior: 72 euros.")],
         "vigente": vigente("_general", "tarifas-honorarios-2025.txt"),
         "respuesta_correcta": "72 euros la hora de ingeniero sénior (tarifas 2025, que sustituyen a las de 2024; antes 68 euros).",
         "preguntas": ["GEN09"]},
        {"id": "KB02", "tipo": "version", "fuentes": [
            fuente("conservas-dalmira", "oferta-auditoria-energetica-v1.txt", 1, "Precio de la auditoría energética: 18.400 euros más IVA."),
            fuente("conservas-dalmira", "oferta-auditoria-energetica-v2.txt", 1, "Precio de la auditoría energética: 16.900 euros más IVA.")],
         "vigente": vigente("conservas-dalmira", "oferta-auditoria-energetica-v2.txt"),
         "respuesta_correcta": "16.900 euros más IVA (oferta v2, que sustituye a la v1 de 18.400 euros).",
         "preguntas": ["GEN08"]},
        {"id": "KB03", "tipo": "version", "fuentes": [
            fuente("bodegas-quintafria", "contrato-servicios-2023.txt", 1, "Honorarios anuales: 7.800 euros más IVA."),
            fuente("bodegas-quintafria", "contrato-servicios-2025.txt", 1,
                   "Este contrato sustituye y deja sin efecto el contrato CS-2023-02.")],
         "vigente": vigente("bodegas-quintafria", "contrato-servicios-2025.txt"),
         "respuesta_correcta": "El contrato CS-2025-07; el CS-2023-02 queda sin efecto.",
         "preguntas": ["GEN10"]},
    ]


def claves_versiones_b():
    return [
        {"id": "KB04", "tipo": "anexo", "fuentes": [
            fuente("talleres-murcano", "contrato-mantenimiento-2024.txt", 2,
                   "La cuota mensual del mantenimiento es de 1.150 euros más IVA."),
            fuente("talleres-murcano", "adenda-1-contrato-mantenimiento.txt", 1, ANCLA_ADENDA)],
         "vigente": vigente("talleres-murcano", "adenda-1-contrato-mantenimiento.txt"),
         "respuesta_correcta": "1.240 euros más IVA al mes (adenda 1, que modifica el precio del contrato CM-2024-011, que fijaba 1.150).",
         "preguntas": ["GEN07", "GEN15"]},
        {"id": "KB05", "tipo": "anexo", "fuentes": [
            fuente("conservas-dalmira", "factura-F-2025-052.txt", 1, "Anticipo del 50 % de la auditoría energética."),
            fuente("conservas-dalmira", "factura-rectificativa-R-2025-003.txt", 1, "Base imponible rectificada: 6.760,00 euros.")],
         "vigente": vigente("conservas-dalmira", "factura-rectificativa-R-2025-003.txt"),
         "respuesta_correcta": "Anticipo de 6.760 euros de base (rectificativa R-2025-003, que rectifica la F-2025-052).",
         "preguntas": ["GEN11", "GEN17"]},
    ]


def claves_discrepancias():
    return [
        {"id": "KB06", "tipo": "discrepancia", "fuentes": [
            fuente("logistica-penedal", "contrato-legalizacion-fotovoltaica.txt", 2,
                   "Plazo de pago: 60 días desde la fecha de la factura."),
            fuente("logistica-penedal", "correo-2025-03-plazo-pago.txt", 1,
                   "Según lo hablado por teléfono, os pagaremos las facturas a 30 días.")],
         "vigente": None,
         "respuesta_correcta": "No consta un plazo único: el contrato dice 60 días y un correo de marzo de 2025 dice 30. Enseñar los dos.",
         "preguntas": ["GEN18"]},
        {"id": "KB07", "tipo": "discrepancia", "fuentes": [
            fuente("alojamientos-cerrolago", "presupuesto-climatizacion-2024.txt", 2,
                   "Plazo de ejecución del proyecto: 10 semanas desde la aceptación."),
            fuente("alojamientos-cerrolago", "correo-2025-01-plazo.txt", 1,
                   "El proyecto de climatización estará listo en 8 semanas desde la aceptación.")],
         "vigente": None,
         "respuesta_correcta": "No consta un plazo único: el presupuesto dice 10 semanas y un correo de enero de 2025 dice 8. Enseñar los dos.",
         "preguntas": ["GEN19"]},
        {"id": "KB08", "tipo": "ficha_contra_documento", "fuentes": [
            fuente("bodegas-quintafria", "contrato-servicios-2025.txt", 1, "Entrada en vigor: 1 de abril de 2025."),
            fuente_ficha("FB02", "El contrato nuevo con Bodegas Quintafría empezó el 1 de marzo de 2025.")],
         "vigente": None,
         "respuesta_correcta": "No consta la fecha: el contrato CS-2025-07 dice 1 de abril de 2025 y la ficha FB02 dice 1 de marzo de 2025. Enseñar las dos.",
         "preguntas": ["GEN20"]},
    ]


def claves():
    return claves_versiones() + claves_discrepancias()


# ---------- construcción ----------
def construir():
    paginas = {ruta(d["proyecto"], d["fichero"]): paginas_de(d) for d in DOCUMENTOS}
    manifiesto = {
        "semilla": SEMILLA,
        "empresas": [{"clave": EMPRESA, "nombre": NOMBRE_EMPRESA, "codigo": "BRM"}],
        "proyectos": [{"clave": c, "empresa": EMPRESA, "nombre": n, "abierto": False} for c, n in NOMBRES.items()],
        "trabajadores": TRABAJADORES,
        "documentos": [{"ruta": ruta(d["proyecto"], d["fichero"]), "empresa": EMPRESA,
                        "proyecto": None if d["proyecto"] == "_general" else d["proyecto"],
                        "titulo": d["titulo"], "subido": subido(d["fecha"]), "subido_por": "t09"}
                       for d in DOCUMENTOS],
        "fichas": FICHAS,
    }
    corpus = {r: "".join(f"=== Página {n} ===\n{t}\n" for n, t in enumerate(ps, 1)) for r, ps in paginas.items()}
    return {"paginas": paginas, "corpus": corpus, "manifiesto": manifiesto,
            "bateria": preguntas(), "claves": claves()}


def escribir(modelo, destino):
    shutil.rmtree(destino / "corpus", ignore_errors=True)
    for r, texto in modelo["corpus"].items():
        fichero = destino / "corpus" / r
        fichero.parent.mkdir(parents=True, exist_ok=True)
        fichero.write_text(texto, encoding="utf-8")
    for nombre in ("manifiesto", "claves", "bateria"):
        salida = json.dumps(modelo[nombre], ensure_ascii=False, indent=1) + "\n"
        (destino / f"{nombre}.json").write_text(salida, encoding="utf-8")


# ---------- autocomprobaciones ----------
def comprobar_ids(modelo):
    ids = [q["id"] for q in modelo["bateria"]] + [k["id"] for k in modelo["claves"]]
    ids += [f["id"] for f in FICHAS]
    exigir(len(set(ids)) == len(ids), "ids duplicados")


def comprobar_anclas(bateria, paginas):
    for q in bateria:
        exigir(not q["abstener"] or (not q["citas_resueltas"] and not q["claves"]), f"{q['id']}: abstención con citas")
        for c in q["citas_resueltas"]:
            exigir(c["doc"] in paginas, f"{q['id']}: doc {c['doc']} inexistente")
            for p in c["paginas"]:
                exigir(c["ancla"] in paginas[c["doc"]][p - 1], f"{q['id']}: ancla no está en la pág. {p}")


def comprobar_claves(bateria, paginas):
    for q in bateria:
        textos = [paginas[c["doc"]][p - 1] for c in q["citas_resueltas"] for p in c["paginas"]]
        textos += [FICHAS_POR_ID[f]["texto"] for f in q["fichas_esperadas"]]
        for grupo in q["claves"]:
            exigir(any(puntuar.has_key(t, grupo) for t in textos), f"{q['id']}: clave {grupo} no aparece")


def comprobar_fuentes_claves(claves_kb, bateria, paginas):
    ids_preguntas = {q["id"] for q in bateria}
    for k in claves_kb:
        for f in k["fuentes"]:
            texto = FICHAS_POR_ID[f["ficha"]]["texto"] if "ficha" in f else paginas[f["doc"]][f["pagina"] - 1]
            exigir(f["frase"] in texto, f"{k['id']}: frase no literal «{f['frase']}»")
        exigir(k["vigente"] is None or k["vigente"]["doc"] in paginas, f"{k['id']}: vigente inexistente")
        exigir(set(k["preguntas"]) <= ids_preguntas, f"{k['id']}: pregunta inexistente")


def comprobar_rutas(manifiesto, destino):
    for d in manifiesto["documentos"]:
        exigir((destino / "corpus" / d["ruta"]).is_file(), f"falta {d['ruta']}")


def comprobar_redaccion(paginas):
    for r, ps in paginas.items():
        exigir(not REDACCION.search(sin_tildes(" ".join(ps))), f"palabra prohibida en {r}")
    for f in FICHAS:
        exigir(not REDACCION.search(sin_tildes(f["titulo"] + " " + f["texto"])), f"palabra prohibida en {f['id']}")


def verificar(modelo, destino):
    comprobar_ids(modelo)
    comprobar_anclas(modelo["bateria"], modelo["paginas"])
    comprobar_claves(modelo["bateria"], modelo["paginas"])
    comprobar_fuentes_claves(modelo["claves"], modelo["bateria"], modelo["paginas"])
    comprobar_rutas(modelo["manifiesto"], destino)
    comprobar_redaccion(modelo["paginas"])


def generar(destino=DATOS):
    destino = Path(destino)
    destino.mkdir(parents=True, exist_ok=True)
    modelo = construir()
    escribir(modelo, destino)
    verificar(modelo, destino)


if __name__ == "__main__":
    generar()
