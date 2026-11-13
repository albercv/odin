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
CIF_EMPRESA = "B-00000001"
REP_EMPRESA = "Carlos Montesinos Vega"
DOMICILIO = "Polígono Las Viñas, nave 12, 47999 Valdecerro"
IBAN = "ES00 0000 0000 00 0000000000"
SEMILLA = 7
PRIMERA_SUBIDA = dt.date(2024, 11, 1)
NO_AFIRMAR = ["comunidad", "propietario", "finca", "vecino", "junta de propietarios", "derrama"]
REDACCION = re.compile(r"\b(comunidad|propietario|vecino|finca|junta|derrama)")
AJENOS = ["ribera/", "albar/"]
ANCLA_ADENDA = "Desde el 1 de febrero de 2025, la cuota mensual del mantenimiento pasa a ser de 1.240 euros más IVA."

CLIENTES = {
    "talleres-murcano": {"nombre": "Talleres Murcano, S.L.", "cif": "B-00000021",
                         "dom": "Polígono Industrial El Cerro, Valdecerro", "rep": "Teodoro Peña Lozano"},
    "conservas-dalmira": {"nombre": "Conservas Dalmira, S.A.", "cif": "B-00000022",
                          "dom": "Camino de los Huertos, Valdecerro", "rep": "Marina Esteban Vidal"},
    "logistica-penedal": {"nombre": "Logística Penedal, S.L.", "cif": "B-00000023",
                          "dom": "Polígono Penedal Norte, Valdecerro", "rep": "Gonzalo Robledo Ibáñez"},
    "alojamientos-cerrolago": {"nombre": "Alojamientos Cerrolago, S.L.", "cif": "B-00000024",
                               "dom": "Paseo del Cerrolago, Valdecerro", "rep": "Pilar Amorós Serrano"},
    "bodegas-quintafria": {"nombre": "Bodegas Quintafría, S.L.", "cif": "B-00000025",
                           "dom": "Viñedo de Quintafría, Valdecerro", "rep": "Lucía Ferrer Sanz"},
    "ceramicas-valtierno": {"nombre": "Cerámicas Valtierno, S.A.", "cif": "B-00000026",
                            "dom": "Camino de la Tejera, 14, Valdecerro", "rep": "Rebeca Quintero Aranda"},
}
NOMBRES = {k: v["nombre"].split(",")[0] for k, v in CLIENTES.items()}  # el proyecto, sin la forma jurídica
TRABAJADORES = [
    {"clave": "t09", "empresa": EMPRESA, "nombre": "Irene", "apellidos": "Salcedo Moya", "proyectos": ["*"]},
    {"clave": "t10", "empresa": EMPRESA, "nombre": "Óscar", "apellidos": "Leiva Durán",
     "proyectos": ["talleres-murcano", "conservas-dalmira", "bodegas-quintafria"]},
    {"clave": "t11", "empresa": EMPRESA, "nombre": "Nuria", "apellidos": "Baeza Ortiz", "proyectos": ["*"]},
]
CLAUSULAS_FINALES = [
    ("Confidencialidad", "Las partes se obligan a mantener en secreto la información técnica y comercial a la que accedan con ocasión de este contrato, y a usarla solo para su ejecución."),
    ("Protección de datos", "Los datos personales de las personas de contacto se tratarán conforme al Reglamento general de protección de datos y a la normativa española vigente, solo para ejecutar este contrato."),
    ("Resolución", "Cualquiera de las partes podrá resolver el contrato si la otra incumple una obligación esencial y no la corrige tras un aviso escrito."),
    ("Jurisdicción", "Para cualquier controversia derivada de este contrato, las partes se someten a los juzgados y tribunales de Valdecerro, con renuncia a cualquier otro fuero."),
]


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


# ---------- piezas de plantilla ----------
def doc(proyecto, fichero, titulo, fecha, paginas):
    return {"proyecto": proyecto, "fichero": fichero, "titulo": titulo,
            "fecha": dt.date.fromisoformat(fecha), "paginas": paginas}


def cabecera(d, n, total):
    cli = CLIENTES.get(d["proyecto"])
    ambito = f"Cliente: {cli['nombre']} · CIF {cli['cif']}" if cli else "Uso interno"
    return [f"{NOMBRE_EMPRESA} · CIF {CIF_EMPRESA}", DOMICILIO, d["titulo"],
            f"Fecha: {dmy(d['fecha'])} · {ambito} · Página {n} de {total}"]


def paginas_de(d):
    """Texto de cada página, sin la línea marcadora (que solo lleva el fichero)."""
    total = len(d["paginas"])
    return ["\n".join([*cabecera(d, n, total), "", *cuerpo]) for n, cuerpo in enumerate(d["paginas"], 1)]


def numeradas(pares, inicio=1):
    return [f"{n}. {titulo}. {texto}" for n, (titulo, texto) in enumerate(pares, inicio)]


def partes(proyecto):
    cli = CLIENTES[proyecto]
    return ["REUNIDOS",
            f"De una parte, {NOMBRE_EMPRESA}, con CIF {CIF_EMPRESA} y domicilio en {DOMICILIO}, representada por {REP_EMPRESA}, administrador.",
            f"De otra parte, {cli['nombre']}, con CIF {cli['cif']} y domicilio en {cli['dom']}, representada por {cli['rep']}, en calidad de representante legal.",
            "Las dos partes se reconocen capacidad para firmar este contrato y lo suscriben de buena fe."]


def firmas(proyecto):
    cli = CLIENTES[proyecto]
    return ["Y en prueba de conformidad, las partes firman este documento por duplicado en Valdecerro.", "",
            f"Por {NOMBRE_EMPRESA}          Por {cli['nombre']}",
            f"Fdo.: {REP_EMPRESA}          Fdo.: {cli['rep']}"]


def factura(proyecto, numero, fecha, lineas, venc):
    """lineas: (concepto, cantidad, precio en céntimos). La base es la suma de los importes."""
    cli = CLIENTES[proyecto]
    base_c = sum(cant * precio for _, cant, precio in lineas)
    iva_c = base_c * 21 // 100
    tabla = [f"{c} | {n} | {eur(p)} euros | {eur(n * p)} euros" for c, n, p in lineas]
    cuerpo = [f"EMISOR: {NOMBRE_EMPRESA}, CIF {CIF_EMPRESA}, {DOMICILIO}.",
              f"CLIENTE: {cli['nombre']}, CIF {cli['cif']}, {cli['dom']}.",
              f"Número de factura: {numero}", "",
              "Concepto | Cantidad | Precio unitario | Importe", *tabla, "",
              f"Base imponible: {eur(base_c)} euros", f"IVA 21 %: {eur(iva_c)} euros",
              f"Total factura: {eur(base_c + iva_c)} euros", "",
              f"Forma de pago: transferencia bancaria a la cuenta {IBAN}.",
              f"Vencimiento: {venc}",
              "Factura emitida según la norma interna de facturación de Brumeral."]
    return doc(proyecto, f"factura-{numero}.txt", f"Factura {numero}", fecha, [cuerpo])


def correo(fecha_txt, de, para, asunto, cuerpo, firma):
    return [f"De: {de}", f"Para: {para}", f"Fecha: {fecha_txt}", f"Asunto: {asunto}", "", *cuerpo, "", *firma]


def oferta_cuerpo(cliente_corto, precio_linea, plazo, pago=None):
    """Texto común de las dos versiones de la oferta; solo cambian el precio, el plazo y la forma de pago."""
    cuerpo = [f"Oferta dirigida a {cliente_corto}, para la auditoría de su planta de envasado.", "",
              *precio_linea, plazo]
    if pago:
        cuerpo.append(pago)
    cuerpo += ["", *numeradas([
        ("Alcance", "Auditoría energética de la planta de envasado: medición de consumos, revisión de equipos y propuesta de medidas de ahorro."),
        ("Entregables", "Informe técnico con tabla de consumos, medidas propuestas y estimación de ahorro."),
        ("Condiciones", "La oferta se acepta por escrito y mantiene su validez mientras no se modifique el alcance."),
        ("Visitas", "Las visitas a la planta se coordinan con el responsable de mantenimiento del cliente."),
        ("Confidencialidad", "La información recibida se usa solo para esta auditoría."),
    ]), "", "Atentamente,", f"Carlos Montesinos Vega, administrador de {NOMBRE_EMPRESA}"]
    return cuerpo


# ---------- documentos ----------
DOCUMENTOS = [
    doc("_general", "norma-facturacion.txt", "Norma interna de facturación", "2024-11-20", [[
        "Norma interna de facturación, aprobada por la dirección de Brumeral para todo el equipo.",
        "1. Ámbito. Se aplica a todas las facturas y rectificativas que emite la empresa, sea cual sea el proyecto.",
        "2. Numeración. Las facturas se numeran F-AAAA-NNN y las rectificativas R-AAAA-NNN.",
        "3. Plazo de pago. El plazo de pago por defecto con los clientes es de 45 días desde la fecha de la factura.",
        "4. Contenido. Cada factura incluye los datos del emisor y del cliente, el número, la fecha, el detalle de los conceptos, la base imponible, el IVA y el total.",
        "5. Forma de pago. Salvo acuerdo escrito, el cobro se hace por transferencia a la cuenta que figure en la factura.",
        "6. Rectificativas. Una rectificativa indica la factura que corrige, el motivo y los importes corregidos.",
        "7. Archivo. Las facturas emitidas se guardan en Odín junto al proyecto del cliente.",
        "8. Revisión. La dirección revisa esta norma una vez al año y comunica los cambios por escrito.",
        "Ante cualquier duda sobre una factura, se consulta con la dirección antes de emitirla.",
    ]]),
    doc("_general", "tarifas-honorarios-2024.txt", "Tarifas de honorarios 2024", "2024-12-02", [[
        "Tarifas de honorarios vigentes durante 2024, para trabajos de ingeniería y consultoría.",
        "Hora de ingeniero sénior: 68 euros.",
        "Hora de ingeniero júnior: 45 euros.",
        "Las tarifas no incluyen el IVA, que se aplica según la legislación vigente.",
        "Los desplazamientos fuera de la provincia se facturan aparte, según lo acordado con el cliente.",
        "Las horas dedicadas a la redacción de informes se facturan con la tarifa del ingeniero que las realiza.",
        "Las tarifas se revisan una vez al año, y la revisión se comunica por escrito a los clientes.",
        "Ante cualquier duda sobre la tarifa aplicable, se consulta con la dirección antes de facturar.",
    ]]),
    doc("_general", "tarifas-honorarios-2025.txt", "Tarifas de honorarios 2025", "2024-12-18", [[
        "Estas tarifas sustituyen a las tarifas de honorarios 2024 desde el 1 de enero de 2025.",
        "Hora de ingeniero sénior: 72 euros.",
        "Hora de ingeniero júnior: 48 euros.",
        "Las tarifas no incluyen el IVA, que se aplica según la legislación vigente.",
        "Los desplazamientos fuera de la provincia se facturan aparte, según lo acordado con el cliente.",
        "Las horas dedicadas a la redacción de informes se facturan con la tarifa del ingeniero que las realiza.",
        "Las tarifas se revisan una vez al año, y la revisión se comunica por escrito a los clientes.",
    ]]),
    doc("_general", "procedimiento-alta-clientes.txt", "Procedimiento de alta de clientes", "2024-11-25", [[
        "Procedimiento de alta de clientes, de uso para todo el equipo de Brumeral.",
        "Cuatro pasos:",
        "1. Pedir el CIF y los datos de facturación de la empresa cliente.",
        "2. Firmar el contrato o aceptar la oferta.",
        "3. Crear el proyecto del cliente en Odín.",
        "4. Asignar un ingeniero responsable.",
        "Responsable del procedimiento: la dirección de Brumeral.",
        "El alta no se da por completa hasta que el proyecto figura en Odín con su ingeniero responsable.",
        "Los datos de facturación se comprueban antes de emitir la primera factura.",
        "Cualquier cambio de titularidad del cliente se trata como una nueva alta.",
    ]]),
]

DOCUMENTOS += [
    doc("talleres-murcano", "contrato-mantenimiento-2024.txt", "Contrato de mantenimiento CM-2024-011", "2024-12-10", [
        ["CONTRATO DE MANTENIMIENTO", *partes("talleres-murcano"), "", "ESTIPULACIONES", *numeradas([
            ("Objeto", "Brumeral se obliga a prestar al cliente el mantenimiento preventivo de la instalación de aire comprimido y de la red eléctrica de baja tensión."),
            ("Periodicidad", "Se realizará una visita preventiva al mes, en fecha pactada con el cliente, con informe escrito de cada visita."),
            ("Obligaciones de Brumeral", "Brumeral revisará los equipos, realizará los ajustes preventivos y atenderá las incidencias que el cliente comunique por escrito."),
            ("Obligaciones del cliente", "El cliente facilitará el acceso a las instalaciones, designará una persona de contacto y comunicará cualquier anomalía observada."),
        ])],
        ["ESTIPULACIONES (continuación)", *numeradas([
            ("Precio", "La cuota mensual del mantenimiento es de 1.150 euros más IVA."),
            ("Forma de pago", "Plazo de pago: 30 días desde la fecha de la factura."),
            ("Limitaciones", "El mantenimiento no incluye las reparaciones mayores ni las reformas, que se presupuestan aparte."),
        ], inicio=5), *numeradas(CLAUSULAS_FINALES, inicio=8), "", *firmas("talleres-murcano")],
    ]),
    doc("talleres-murcano", "adenda-1-contrato-mantenimiento.txt", "Adenda 1 al contrato CM-2024-011", "2025-01-15", [[
        "ADENDA 1 AL CONTRATO CM-2024-011", "En Valdecerro, a 15 de enero de 2025.", "",
        *partes("talleres-murcano"), "", "EXPONEN",
        "Que las partes suscribieron el contrato CM-2024-011 y acuerdan modificar una de sus cláusulas.", "",
        "ESTIPULACIONES", *numeradas([
            ("Modificación de la cláusula de precio", "Esta adenda modifica la cláusula de precio del contrato CM-2024-011."),
            ("Nuevo precio", ANCLA_ADENDA),
            ("Resto del contrato", "Se mantienen sin cambios el objeto, la periodicidad y las demás cláusulas del contrato CM-2024-011."),
            ("Efectos", "Esta adenda forma parte del contrato CM-2024-011 y se firma en el mismo número de ejemplares."),
        ]), "", *firmas("talleres-murcano"),
    ]]),
    factura("talleres-murcano", "F-2025-014", "2025-01-31",
            [("Mantenimiento de enero de 2025, cuota mensual del contrato CM-2024-011", 1, 115000)], "02/03/2025"),
    factura("talleres-murcano", "F-2025-047", "2025-03-31",
            [("Mantenimiento de marzo de 2025, cuota mensual del contrato CM-2024-011", 1, 124000)], "30/04/2025"),
    factura("talleres-murcano", "F-2025-089", "2025-06-30",
            [("Mano de obra de la reparación del compresor 2", 1, 150000),
             ("Repuestos del compresor 2", 1, 88000)], "30/07/2025"),
    doc("conservas-dalmira", "oferta-auditoria-energetica-v1.txt", "Oferta de auditoría energética (versión 1)", "2025-02-10", [
        ["OFERTA DE AUDITORÍA ENERGÉTICA", *oferta_cuerpo("Conservas Dalmira, S.A.",
                                                          ["Precio de la auditoría energética: 18.400 euros más IVA."],
                                                          "Plazo de entrega del informe: 10 semanas.")]]),
    doc("conservas-dalmira", "oferta-auditoria-energetica-v2.txt", "Oferta de auditoría energética (versión 2)", "2025-03-03", [
        ["OFERTA DE AUDITORÍA ENERGÉTICA (VERSIÓN 2)", *oferta_cuerpo("Conservas Dalmira, S.A.",
                                                                    ["Esta versión sustituye a la versión 1 de la oferta, de 10 de febrero de 2025.",
                                                                     "Precio de la auditoría energética: 16.900 euros más IVA."],
                                                                    "Plazo de entrega: 12 semanas.",
                                                                    "Forma de pago: anticipo del 40 % a la aceptación y el resto a la entrega del informe.")]]),
    factura("conservas-dalmira", "F-2025-052", "2025-04-07",
            [("Anticipo del 50 % de la auditoría energética.", 1, 845000)], "07/05/2025"),
    doc("conservas-dalmira", "factura-rectificativa-R-2025-003.txt", "Factura rectificativa R-2025-003", "2025-04-22", [[
        "Número de factura rectificativa: R-2025-003",
        "Rectifica la factura F-2025-052.",
        "Motivo: el anticipo pactado en la oferta es del 40 %, no del 50 %.",
        f"EMISOR: {NOMBRE_EMPRESA}, CIF {CIF_EMPRESA}, {DOMICILIO}.",
        "CLIENTE: Conservas Dalmira, S.A., CIF B-00000022, Camino de los Huertos, Valdecerro.",
        "Concepto | Cantidad | Precio unitario | Importe",
        "Anticipo de la auditoría energética, corregido al 40 % | 1 | 6.760,00 euros | 6.760,00 euros", "",
        "Base imponible rectificada: 6.760,00 euros.",
        "IVA 21 %: 1.419,60 euros.",
        "Total rectificado: 8.179,60 euros.", "",
        f"Forma de pago: transferencia bancaria a la cuenta {IBAN}.",
        "Esta rectificativa se emite según la norma interna de facturación de Brumeral.",
    ]]),
    doc("conservas-dalmira", "informe-auditoria-energetica.txt", "Informe de auditoría energética", "2025-06-30", [
        ["INFORME DE AUDITORÍA ENERGÉTICA", "Cliente: Conservas Dalmira, S.A.", "Índice",
         "1. Alcance del estudio, página 1", "2. Consumos de partida, página 2",
         "3. Medidas propuestas, página 3", "4. Ahorro estimado, página 3", "",
         "1. Alcance del estudio", "Alcance: planta de envasado de la empresa cliente.",
         "Fecha del informe: 30/06/2025.",
         "El estudio cubre las líneas de envasado, los compresores, las bombas de la red de agua y la iluminación de las naves.",
         "La información de partida procede de las facturas de electricidad y gas de los últimos doce meses.",
         "Las mediciones de potencia se hicieron en jornadas laborables, con la producción normal de la planta."],
        ["2. Consumos de partida", "Tabla de consumos anuales.",
         "Electricidad: 1.284.000 kWh al año.", "Gas natural: 412.000 kWh al año.",
         "La electricidad se reparte entre compresores, bombas, frío industrial e iluminación.",
         "El gas se usa sobre todo en las calderas de agua caliente de la línea de limpieza.",
         "El frío industrial es el mayor consumo de la planta, y por eso se revisa en primer lugar.",
         "Los datos se contrastaron con las lecturas de los contadores de la planta.",
         "Cualquier diferencia entre facturas y lecturas se anotó en el anexo de mediciones."],
        ["3. Medidas propuestas",
         "Medidas propuestas: variadores en las bombas, recuperación de calor de los compresores e iluminación LED.",
         "Variadores en las bombas: ajustan el caudal a la demanda real de cada línea.",
         "Recuperación de calor de los compresores: aprovecha el calor residual para calentar el agua de limpieza.",
         "Iluminación LED: sustituye los fluorescentes de naves y pasillos, con sensores de presencia en los almacenes.",
         "4. Ahorro estimado",
         "Ahorro anual estimado con las medidas propuestas: 186.500 kWh, unos 27.900 euros al año.",
         "La estimación es orientativa y se afinará con las mediciones posteriores a la implantación.",
         "Conclusión: las medidas son viables y se recomienda ejecutarlas por fases, empezando por los variadores."],
    ]),
]

DOCUMENTOS += [
    doc("logistica-penedal", "contrato-legalizacion-fotovoltaica.txt", "Contrato de ingeniería CI-2025-004", "2025-02-12", [
        ["CONTRATO DE INGENIERÍA CI-2025-004", "En Valdecerro, a 12 de febrero de 2025.", "", *partes("logistica-penedal"),
         "", "ESTIPULACIONES", *numeradas([
             ("Objeto", "Brumeral se obliga a legalizar la instalación fotovoltaica de autoconsumo de 380 kWp en la cubierta de la nave principal ante la administración competente."),
             ("Alcance", "Memoria técnica, tramitación de la legalización y dirección de las pruebas de puesta en servicio."),
             ("Obligaciones de Brumeral", "Preparar la documentación técnica, responder a los requerimientos de la distribuidora y mantener informado al cliente por escrito."),
             ("Obligaciones del cliente", "Facilitar los planos, los certificados de los equipos y el acceso a la cubierta para las mediciones."),
         ])],
        ["ESTIPULACIONES (continuación)", *numeradas([
            ("Precio", "Honorarios: 9.600 euros más IVA, en tres pagos."),
            ("Forma de pago", "Plazo de pago: 60 días desde la fecha de la factura."),
        ], inicio=5), *numeradas(CLAUSULAS_FINALES, inicio=7), "", *firmas("logistica-penedal")],
    ]),
    doc("logistica-penedal", "correo-2025-03-plazo-pago.txt", "Correo de Logística Penedal sobre el plazo de pago", "2025-03-18", [
        correo("18/03/2025", "administración de Logística Penedal, Mónica Vega Rubio",
               "Irene Salcedo Moya, de Brumeral", "Plazo de pago de las facturas",
               ["Hola, Irene:", "", "Te escribo para dejar por escrito lo que hablamos ayer.",
                "Según lo hablado por teléfono, os pagaremos las facturas a 30 días.",
                "Si os parece bien, lo tratamos en la próxima reunión de seguimiento.", "", "Gracias y un saludo."],
               ["Mónica Vega Rubio", "Administración, Logística Penedal, S.L."]),
    ]),
    factura("logistica-penedal", "F-2025-061", "2025-04-30",
            [("Primer pago de honorarios del contrato CI-2025-004.", 1, 320000)], "29/06/2025"),
    factura("logistica-penedal", "F-2025-075", "2025-06-30",
            [("Segundo pago de honorarios del contrato CI-2025-004.", 1, 320000)], "29/08/2025"),
    factura("logistica-penedal", "F-2025-098", "2025-09-30",
            [("Tercer pago de honorarios del contrato CI-2025-004.", 1, 320000)], "29/11/2025"),
    doc("logistica-penedal", "acta-reunion-seguimiento-2025-05.txt", "Acta de la reunión de seguimiento de mayo de 2025", "2025-05-20", [[
        "ACTA DE LA REUNIÓN DE SEGUIMIENTO DE MAYO DE 2025", "Reunión de seguimiento del 20/05/2025.",
        "Asistentes: Irene Salcedo Moya (Brumeral), Nuria Baeza Ortiz (Brumeral) y Gonzalo Robledo Ibáñez (Logística Penedal).",
        "Orden del día:", "1. Estado de la legalización de la instalación fotovoltaica.",
        "2. Documentación pendiente.", "3. Próximas fechas con la distribuidora.", "",
        "Acuerdos:",
        "Punto 3. Legalización de la instalación fotovoltaica: la distribuidora pidió más documentación.",
        "Fecha prevista de la inspección: 15 de julio de 2025.",
        "Brumeral enviará la memoria técnica completada y el certificado de los equipos antes de la inspección.",
        "Logística Penedal aportará los planos actualizados de la cubierta.", "",
        "Y no habiendo más asuntos, se levanta la reunión.",
        "Firman el acta: Irene Salcedo Moya, por Brumeral; Gonzalo Robledo Ibáñez, por Logística Penedal.",
    ]]),
]

DOCUMENTOS += [
    doc("alojamientos-cerrolago", "presupuesto-climatizacion-2024.txt", "Presupuesto P-2024-031, reforma de la climatización", "2024-11-05", [
        ["PRESUPUESTO P-2024-031, REFORMA DE LA CLIMATIZACIÓN", "Fecha del presupuesto: 05/11/2024.", "",
         *partes("alojamientos-cerrolago"), "", "OBJETO",
         "Objeto: reforma de la climatización de las 42 habitaciones y de las zonas de uso común del hotel.", "",
         *numeradas([
             ("Alcance", "Anteproyecto, proyecto de ejecución, dirección de obra y coordinación con el instalador."),
             ("Exclusiones", "La obra civil, los trabajos de pintura y la gestión de licencias municipales, que corren a cargo del cliente."),
             ("Medios", "El cliente facilitará el acceso a las habitaciones y a las zonas comunes en el horario acordado."),
             ("Entregables", "Proyecto técnico, planos de la instalación y certificados de los equipos."),
         ])],
        ["PRESUPUESTO (continuación)", "Importe: 14.250 euros más IVA.",
         "Plazo de ejecución del proyecto: 10 semanas desde la aceptación.",
         "Forma de pago: a convenir con el cliente en la aceptación.", "", *numeradas([
             ("Validez", "Este presupuesto es válido mientras no se modifique el alcance descrito."),
             ("Modificaciones", "Cualquier cambio de alcance se presupuesta por separado y requiere aceptación escrita."),
         ], inicio=5), *numeradas(CLAUSULAS_FINALES, inicio=7), "", *firmas("alojamientos-cerrolago")],
    ]),
    doc("alojamientos-cerrolago", "correo-2025-01-plazo.txt", "Correo de Brumeral a Alojamientos Cerrolago sobre el plazo", "2025-01-14", [
        correo("14/01/2025", "Irene Salcedo Moya, de Brumeral", "Alojamientos Cerrolago, S.L.",
               "Plazo de la climatización",
               ["Hola:", "", "Te confirmo lo que comentamos en la visita a la obra.",
                "El proyecto de climatización estará listo en 8 semanas desde la aceptación.",
                "La gerencia ya conoce el calendario de las habitaciones, así que lo cerramos cuando nos confirméis.",
                "", "Un saludo, Irene."],
               ["Irene Salcedo Moya", NOMBRE_EMPRESA]),
    ]),
    doc("alojamientos-cerrolago", "aceptacion-presupuesto.txt", "Aceptación del presupuesto P-2024-031", "2025-01-20", [[
        "ACEPTACIÓN DEL PRESUPUESTO P-2024-031", "Fecha: 20/01/2025.", "",
        "Alojamientos Cerrolago, S.L. acepta el presupuesto P-2024-031 de reforma de la climatización.",
        "Se aceptan el alcance, las exclusiones y las condiciones descritas en el presupuesto, sin cambios.",
        "El cliente recibe copia firmada de esta aceptación para su archivo.", "",
        "Firmado: la gerencia de Alojamientos Cerrolago, S.L., 20/01/2025.",
        f"Por {NOMBRE_EMPRESA}: {REP_EMPRESA}, administrador.",
        "Representante del cliente: Pilar Amorós Serrano, gerente.",
    ]]),
]

DOCUMENTOS += [
    doc("bodegas-quintafria", "contrato-servicios-2023.txt", "Contrato de servicios de ingeniería CS-2023-02", "2023-03-01", [[
        "CONTRATO DE SERVICIOS DE INGENIERÍA CS-2023-02", *partes("bodegas-quintafria"), "", "ESTIPULACIONES",
        *numeradas([
            ("Objeto", "Servicios de ingeniería para la bodega, incluidas las revisiones técnicas de la instalación frigorífica y la asistencia en la vendimia."),
            ("Honorarios", "Honorarios anuales: 7.800 euros más IVA."),
            ("Duración", "Duración hasta el 31 de marzo de 2025."),
            ("Forma de pago", "Los honorarios se facturan por trimestres vencidos, mediante transferencia bancaria."),
        ]), *numeradas(CLAUSULAS_FINALES, inicio=5), "", *firmas("bodegas-quintafria"),
    ]]),
    doc("bodegas-quintafria", "contrato-servicios-2025.txt", "Contrato de servicios de ingeniería CS-2025-07", "2025-03-25", [[
        "CONTRATO DE SERVICIOS DE INGENIERÍA CS-2025-07", *partes("bodegas-quintafria"), "", "ESTIPULACIONES",
        *numeradas([
            ("Sustitución", "Este contrato sustituye y deja sin efecto el contrato CS-2023-02."),
            ("Vigencia", "Entrada en vigor: 1 de abril de 2025."),
            ("Servicios", "Servicios: ampliación de la sala de barricas, dirección de obra y legalización de la instalación frigorífica."),
            ("Honorarios", "Honorarios anuales: 8.400 euros más IVA."),
            ("Forma de pago", "Facturación trimestral, mediante transferencia bancaria."),
            ("Duración", "El contrato tiene una duración de un año, prorrogable por acuerdo escrito de las partes."),
        ]), *numeradas(CLAUSULAS_FINALES, inicio=7), "", *firmas("bodegas-quintafria"),
    ]]),
    factura("bodegas-quintafria", "F-2025-033", "2025-04-30",
            [("Honorarios del primer trimestre del contrato CS-2025-07.", 1, 210000)], "30/05/2025"),
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
                 claves=[["29/06/2025", "29 de junio de 2025"]]),
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
                 claves=[["72 euros"]], prohibidas=["68"], sustituidos=[TARIFAS_2024]),
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
                 claves=[["186.500", "27.900"]]),
    ]


def preguntas_datos_d():
    return [
        pregunta(13, "dato_concreto", "t09", "¿Qué plazo de pago aplicamos por defecto a los clientes?",
                 citas=[cita("_general", "norma-facturacion.txt", 1,
                             "El plazo de pago por defecto con los clientes es de 45 días desde la fecha de la factura.")],
                 claves=[["45 días"]]),
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
                 claves=[["15 de julio de 2025", "15/07/2025"]]),
    ]


def preguntas_versiones_y_contradicciones():
    return [
        pregunta(17, "version", "t10", "¿Cuánto se cobró finalmente de anticipo por la auditoría de Conservas Dalmira?",
                 citas=[cita("conservas-dalmira", "factura-rectificativa-R-2025-003.txt", 1,
                             "Base imponible rectificada: 6.760,00 euros.")],
                 claves=[["6.760", "8.179,60"]], prohibidas=["8.450"]),
        pregunta(18, "contradiccion", "t09", "¿Qué plazo de pago tiene Logística Penedal?",
                 citas=[cita("logistica-penedal", "contrato-legalizacion-fotovoltaica.txt", 2,
                             "Plazo de pago: 60 días desde la fecha de la factura."),
                        cita("logistica-penedal", "correo-2025-03-plazo-pago.txt", 1,
                             "Según lo hablado por teléfono, os pagaremos las facturas a 30 días.")],
                 claves=[["60 días"], ["30 días"]], todas=True),
        pregunta(19, "contradiccion", "t09", "¿En cuántas semanas se entrega el proyecto de climatización de Alojamientos Cerrolago?",
                 citas=[cita("alojamientos-cerrolago", "presupuesto-climatizacion-2024.txt", 2,
                             "Plazo de ejecución del proyecto: 10 semanas desde la aceptación."),
                        cita("alojamientos-cerrolago", "correo-2025-01-plazo.txt", 1,
                             "El proyecto de climatización estará listo en 8 semanas desde la aceptación.")],
                 claves=[["10 semanas"], ["8 semanas"]], todas=True),
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
                 fichas=["FB01"], claves=[["8:30", "08:30", "8.30", "8,30"]]),
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


# ---------- expediente grande (D-078) ----------
# Un caso con 60 documentos de una sola página. El caso va en el manifiesto; los documentos, en datos/expedientes/.
EXP_PROY = "ceramicas-valtierno"
EXP_DIR = ruta(EXP_PROY, "")
EXP_PREF = "expte-2025-017"
FORMAS_EXP = ["expte. 2025/017", "exp. 017/25", "su referencia 017", "expediente nº 17"]
FRASE_EXP = {"expte. 2025/017": "el expte. 2025/017", "exp. 017/25": "el exp. 017/25",
             "su referencia 017": "su referencia 017", "expediente nº 17": "el expediente nº 17"}
CASO_FICHERO = "caso-2025-017-ampliacion-horno.txt"
CASO = ruta(EXP_PROY, CASO_FICHERO)
DECOY = ruta("logistica-penedal", "correo-ref-017.txt")  # señuelo: va en datos/expedientes/, fuera del manifiesto
FACTURA_BASE = 870000  # céntimos: cada pago de honorarios son 8.700 euros
FACTURA_TOTAL = FACTURA_BASE + FACTURA_BASE * 21 // 100
# La suma no se escribe en ningún documento: es la cifra que piden GX07 y GX08, y la comprueba `comprobar_claves`.
SUMA_FACTURAS = (f"Suma de las seis facturas: {eur(6 * FACTURA_BASE)} euros de base y "
                 f"{eur(6 * FACTURA_TOTAL)} euros con IVA.")
HECHOS_FIJOS = {  # cadena literal -> único documento del expediente que puede llevarla
    "48.600": "expte-2025-017-contrato-ingenieria.txt",
    "Directora facultativa: Irene Salcedo Moya.": "expte-2025-017-contrato-ingenieria.txt",
    "Plazo de pago: 60 días": "expte-2025-017-contrato-ingenieria.txt",
    "52.200": "expte-2025-017-adenda-1.txt",
    "35.400": "expte-2025-017-presupuesto-2.txt",
    "33.900": "expte-2025-017-presupuesto-2-revisado.txt",
    "1.180": "expte-2025-017-informe-tecnico-3.txt",
    "12 de septiembre": "expte-2025-017-acta-obra-5.txt",
}
PERSONAS = {  # clave -> (línea «De»/«Para», líneas de firma)
    "irene": ("Irene Salcedo Moya, de Brumeral", ["Irene Salcedo Moya", NOMBRE_EMPRESA]),
    "nuria": ("Nuria Baeza Ortiz, de Brumeral", ["Nuria Baeza Ortiz", NOMBRE_EMPRESA]),
    "rebeca": ("Rebeca Quintero Aranda, de Cerámicas Valtierno, S.A.", ["Rebeca Quintero Aranda", "Cerámicas Valtierno, S.A."]),
    "julian": ("Julián Mesa Beltrán, de Cerámicas Valtierno, S.A.", ["Julián Mesa Beltrán", "Cerámicas Valtierno, S.A."]),
    "tomas": ("Tomás Alcalde Varela, de Refractarios Sierra del Mar, S.L.", ["Tomás Alcalde Varela", "Refractarios Sierra del Mar, S.L."]),
    "ana": ("Ana Belén Ruano Soria, de Montajes Garbea, S.L.", ["Ana Belén Ruano Soria", "Montajes Garbea, S.L."]),
    "felipe": ("Felipe Ortega Requena, de Electroindustrial Prado Alto, S.L.", ["Felipe Ortega Requena", "Electroindustrial Prado Alto, S.L."]),
    "rafael": ("Rafael Olmedo Pina, de Aislamientos Térmicos Olmedo, S.A.", ["Rafael Olmedo Pina", "Aislamientos Térmicos Olmedo, S.A."]),
    "gerardo": ("Gerardo Hidalgo Campos, de Transportes Hidalgo Cruz, S.L.", ["Gerardo Hidalgo Campos", "Transportes Hidalgo Cruz, S.L."]),
    "elena": ("Elena Ribazo Gil, de Ingeniería Civil Ribazo, S.L.", ["Elena Ribazo Gil", "Ingeniería Civil Ribazo, S.L."]),
}
PROVEEDORES = [  # (n, empresa, CIF, representante, objeto, plazo, fecha, importe en euros)
    (1, "Refractarios Sierra del Mar, S.L.", "B-00000031", "Tomás Alcalde Varela",
     "el suministro del refractario de la bóveda y de la cámara de cocción", "8 semanas", "2025-02-03", 21750),
    (2, "Montajes Garbea, S.L.", "B-00000032", "Ana Belén Ruano Soria",
     "el montaje mecánico de la estructura y de los transportadores", "12 semanas", "2025-02-10", 35400),
    (3, "Electroindustrial Prado Alto, S.L.", "B-00000033", "Felipe Ortega Requena",
     "la acometida, el cuadro eléctrico y el control de la línea", "10 semanas", "2025-02-17", 27480),
    (4, "Aislamientos Térmicos Olmedo, S.A.", "B-00000034", "Rafael Olmedo Pina",
     "el aislamiento térmico de la cámara de cocción y de los conductos de humos", "6 semanas", "2025-02-19", 12960),
    (5, "Transportes Hidalgo Cruz, S.L.", "B-00000035", "Gerardo Hidalgo Campos",
     "el transporte y la descarga de las piezas pesadas", "4 semanas", "2025-02-24", 6240),
    (6, "Ingeniería Civil Ribazo, S.L.", "B-00000036", "Elena Ribazo Gil",
     "la cimentación y el soporte de hormigón de la nueva zona del horno", "9 semanas", "2025-02-26", 41300),
]
FECHAS_FACTURA = ["2025-02-28", "2025-04-30", "2025-06-30", "2025-08-29", "2025-10-31", "2025-12-15"]
ACTAS = [  # (n, fecha, orden del día, acuerdos)
    (1, "2025-02-05", ["Calendario de la obra civil.", "Accesos a la nave de hornos.", "Documentación de los suministradores."],
     ["Se confirma el calendario de la obra civil y la fecha de inicio de la demolición parcial.",
      "Cerámicas Valtierno facilitará el acceso a la nave de hornos desde el lunes siguiente."]),
    (2, "2025-03-20", ["Estado de los presupuestos de los suministradores.", "Montaje mecánico de la línea 2.", "Próximas visitas."],
     ["Se acepta el presupuesto revisado de Montajes Garbea, S.L. para el montaje mecánico de la línea 2.",
      "Brumeral comunicará la aceptación por escrito a los suministradores."]),
    (3, "2025-04-17", ["Avance de la cimentación.", "Estado del refractario."],
     ["La cimentación de la nueva zona del horno avanza según el calendario.",
      "Refractarios Sierra del Mar, S.L. enviará los certificados de calidad del lote."]),
    (4, "2025-05-29", ["Plan de trabajo para la conexión eléctrica.", "Pedidos pendientes."],
     ["Se aprueba el plan de trabajo para la conexión eléctrica durante la parada de julio.",
      "Los pedidos pendientes se confirman antes del 15 de junio."]),
    (5, "2025-07-22", ["Estado de la línea 2 antes de las pruebas.", "Fecha de la prueba en caliente."],
     ["Se fija la prueba en caliente del horno para el 12 de septiembre de 2025.",
      "Electroindustrial Prado Alto, S.L. completará las pruebas de los relés antes de esa fecha."]),
    (6, "2025-08-26", ["Preparación de la prueba en caliente.", "Incidencias abiertas."],
     ["Quedan abiertas tres incidencias menores de aislamiento, que Aislamientos Térmicos Olmedo corregirá antes de la prueba.",
      "La preparación de la prueba se ajustará a lo acordado en el acta de obra nº 5."]),
    (7, "2025-09-23", ["Seguimiento de la prueba preparatoria.", "Documentación final."],
     ["Se recibe el certificado de conformidad eléctrica.", "Brumeral preparará el informe final de ensayos."]),
    (8, "2025-10-28", ["Cierre de la obra.", "Entrega de documentación."],
     ["Se da por cerrada la obra de la línea 2, salvo las incidencias que figuren en el acta de la prueba.",
      "Brumeral entregará el dossier de la obra antes de fin de año."]),
]
INFORMES = [  # (n, fecha, frases)
    (1, "2025-02-14", ["El terreno de la nueva zona tiene capacidad suficiente para la cimentación prevista, según el estudio geotécnico.",
                       "La losa de hormigón se arma según el plano de ingeniería de Brumeral.",
                       "Se recomienda esperar al curado completo antes de montar la estructura."]),
    (2, "2025-03-07", ["La estructura metálica se fabrica en taller y se monta en obra por fases.",
                       "Cada fase se recibe con una inspección visual de las uniones y de los apoyos.",
                       "Los pilares de la nueva zona se anclan a la losa con el par de apriete indicado en el plano."]),
    (3, "2025-04-09", ["Temperatura máxima de diseño: 1.180 °C.",
                       "El revestimiento refractario de la cámara de cocción se diseña para las cargas térmicas de la línea 2.",
                       "Las uniones de dilatación se dimensionan para los ciclos de calentamiento previstos.",
                       "La rampa de calentamiento se programará en el controlador de la línea."]),
    (4, "2025-05-12", ["La acometida se dimensiona para la potencia de la línea 2, con un margen de reserva.",
                       "El cuadro de control se instala en la sala de mandos existente.",
                       "Las protecciones se coordinan con el centro de transformación de la planta."]),
    (5, "2025-06-09", ["El espesor de aislamiento previsto reduce de forma apreciable la temperatura exterior de la cámara.",
                       "Los conductos de humos llevan el mismo sistema de aislamiento en toda su longitud.",
                       "Las sujeciones se revisan después del primer ciclo de calentamiento."]),
    (6, "2025-07-14", ["Las piezas pesadas se transportan en dos viajes, con grúa de descarga propia.",
                       "El acceso al muelle 2 queda libre durante la descarga.",
                       "La ruta de los camiones se revisa con el responsable de seguridad de la planta."]),
    (7, "2025-08-11", ["Se instalan detectores de humo en la nave y una boca de incendio equipada adicional.",
                       "La señalización de evacuación se actualiza con la nueva zona del horno.",
                       "Los extintores de la nave se revisan antes de la prueba en caliente."]),
    (8, "2025-09-29", ["Los ensayos de estanqueidad de los conductos dan resultado favorable.",
                       "Quedan pendientes las pruebas finales del cuadro, que se harán antes del cierre.",
                       "Las medidas de la chapa de los conductos coinciden con el plano revisado."]),
]
CERTIFICADOS = [  # (n, fecha, empresa, representante, objeto, datos)
    (1, "2025-05-19", "Refractarios Sierra del Mar, S.L.", "Tomás Alcalde Varela", "el refractario de la bóveda y de la cámara de cocción",
     ["Lote L-25-0412.", "Ensayo de resistencia a la compresión en frío: conforme.", "Ensayo de dilatación térmica: conforme."]),
    (2, "2025-06-16", "Montajes Garbea, S.L.", "Ana Belén Ruano Soria", "el montaje de la estructura y de los transportadores",
     ["Soldadores con cualificación vigente en la obra.", "Inspección visual de todas las soldaduras: conforme.",
      "Pares de apriete revisados en las uniones atornilladas."]),
    (3, "2025-08-07", "Electroindustrial Prado Alto, S.L.", "Felipe Ortega Requena", "la acometida y el cuadro eléctrico de la línea 2",
     ["Medida de la resistencia de tierra: conforme.", "Comprobación del aislamiento de los cables: conforme.",
      "Documentación del cuadro entregada en la obra."]),
    (4, "2025-10-02", "Aislamientos Térmicos Olmedo, S.A.", "Rafael Olmedo Pina", "el aislamiento térmico de la cámara de cocción y de los conductos",
     ["Espesor medido en cuatro puntos por zona: conforme.", "Ficha técnica de los materiales entregada.",
      "Sin incidencias abiertas en la fecha de este certificado."]),
]
CASO_PAGINAS = [[
    "FICHA DE CASO", "", "DATOS DEL CASO", "Caso 2025-017", "Cliente: Cerámicas Valtierno, S.A.",
    "Tipo de encargo: ingeniería y gestión de obra de la ampliación de la línea del horno 2.",
    "Fecha de apertura: 13 de enero de 2025.", "Responsable técnica: Irene Salcedo Moya.", "Estado: en curso.", "",
    "DESCRIPCIÓN DEL ENCARGO",
    "1. Ampliación de la línea del horno 2 con una nueva zona de cocción, su precalentamiento y su estructura de soporte.",
    "2. Brumeral redacta el proyecto de ejecución, coordina los presupuestos de los suministradores y dirige la obra.",
    "3. El contrato de ingeniería y su adenda regulan los honorarios y los pagos.",
    "4. La prueba en caliente y la puesta en marcha se fijan en las actas de obra.",
], [
    "GESTIONES",
    "1. Visita de replanteo con el jefe de producción del cliente.",
    "2. Solicitud de presupuestos a seis suministradores para las distintas partidas de la obra.",
    "3. Seguimiento de la obra con actas periódicas y con informes técnicos de cada fase.", "",
    "SEGUIMIENTO PREVISTO",
    "Brumeral comunicará al cliente cada hito por escrito y mantendrá al día el expediente en Odín.", "",
    "OBSERVACIONES",
    "El expediente incluye los documentos del contrato, los presupuestos, las facturas, las actas y los certificados de los suministradores.",
]]


def miles(euros):
    return f"{euros:,}".replace(",", ".")


def dmy_iso(texto):
    return dmy(dt.date.fromisoformat(texto))


def forma(n):
    return FORMAS_EXP[n % len(FORMAS_EXP)]


def en(n):
    """Frase de referencia del expediente para la plantilla n: «en el expte. 2025/017», «en su referencia 017»…"""
    return FRASE_EXP[forma(n)]


def exp_doc(nombre, titulo, fecha, cuerpo):
    return doc(EXP_PROY, f"{EXP_PREF}-{nombre}", titulo, fecha, [cuerpo])


def fx(nombre):
    """Ruta de un documento del expediente, por su nombre sin el prefijo ni la extensión."""
    return ruta(EXP_PROY, f"{EXP_PREF}-{nombre}.txt")


def cx(nombre, pagina, ancla):
    return cita(EXP_PROY, f"{EXP_PREF}-{nombre}.txt", pagina, ancla)


def contrato_ingenieria():
    cuerpo = ["CONTRATO DE INGENIERÍA CI-2025-017", "En Valdecerro, a 14 de enero de 2025.", "", *partes(EXP_PROY), "",
              "ESTIPULACIONES", *numeradas([
                  ("Objeto", "Brumeral se obliga a dirigir la ingeniería y la gestión de obra de la ampliación de la línea del horno 2 de Cerámicas Valtierno."),
                  ("Alcance", "Proyecto de ejecución, coordinación de los suministradores, dirección de obra y puesta en marcha del horno."),
                  ("Honorarios", "Honorarios totales: 48.600 euros más IVA, en seis pagos de 8.100 euros."),
                  ("Forma de pago", "Plazo de pago: 60 días desde la fecha de la factura."),
                  ("Dirección facultativa", "Directora facultativa: Irene Salcedo Moya."),
                  ("Expediente", f"Este contrato se archiva en {en(0)}."),
              ]), *numeradas(CLAUSULAS_FINALES, inicio=7), "", *firmas(EXP_PROY)]
    return exp_doc("contrato-ingenieria.txt", "Contrato de ingeniería CI-2025-017", "2025-01-14", cuerpo)


def adenda_1():
    cuerpo = ["ADENDA 1 AL CONTRATO CI-2025-017", "En Valdecerro, a 8 de abril de 2025.", "", *partes(EXP_PROY), "",
              "EXPONEN", "Que las partes suscribieron el contrato CI-2025-017 y acuerdan modificar una de sus cláusulas.", "",
              "ESTIPULACIONES", *numeradas([
                  ("Modificación de la cláusula de precio", "Esta adenda modifica la cláusula de precio del contrato CI-2025-017."),
                  ("Nuevo precio", "Los honorarios totales pasan a ser de 52.200 euros más IVA, en seis pagos de 8.700 euros."),
                  ("Resto del contrato", "Se mantienen sin cambios el objeto, la dirección de obra y las demás cláusulas del contrato CI-2025-017."),
              ]), "", *firmas(EXP_PROY)]
    return exp_doc("adenda-1.txt", "Adenda 1 al contrato CI-2025-017", "2025-04-08", cuerpo)


def presupuesto(n, empresa, cif, rep, objeto, plazo, fecha, euros, nombre, titulo, nota=None):
    cuerpo = [f"Oferta de {empresa}, con CIF {cif}, para {objeto}.",
              f"En relación con {en(n)}, la ampliación de la línea del horno 2 de Cerámicas Valtierno, S.A.", "",
              "Alcance: suministro, montaje y pruebas de lo indicado en esta oferta, según los planos de la ingeniería de Brumeral.",
              f"Plazo de entrega: {plazo} desde la recepción del pedido.",
              f"Total del presupuesto: {miles(euros)} euros.",
              "Forma de pago: 40 % a la aceptación y el resto contra entrega, mediante transferencia.",
              "Validez de la oferta: 30 días desde la fecha de este documento.",
              *([nota] if nota else []), "", "Atentamente,", f"{rep}, de {empresa}"]
    return exp_doc(nombre, titulo, fecha, cuerpo)


def presupuestos():
    docs = []
    for n, empresa, cif, rep, objeto, plazo, fecha, euros in PROVEEDORES:
        docs.append(presupuesto(n, empresa, cif, rep, objeto, plazo, fecha, euros, f"presupuesto-{n}.txt", f"Presupuesto de {empresa}"))
        if n == 2:
            docs.append(presupuesto(n, empresa, cif, rep, objeto, plazo, "2025-03-12", 33900, "presupuesto-2-revisado.txt",
                                    f"Presupuesto revisado de {empresa}",
                                    nota="Esta oferta revisada sustituye al presupuesto de febrero de 2025 para el mismo alcance."))
    return docs


def facturas():
    docs = []
    for n, fecha in enumerate(FECHAS_FACTURA, 1):
        venc = dmy(dt.date.fromisoformat(fecha) + dt.timedelta(days=60))
        lineas = [(f"Pago {n} de 6 de honorarios del contrato CI-2025-017.", 1, FACTURA_BASE)]
        docs.append({**factura(EXP_PROY, f"F-2025-{100 + n}", fecha, lineas, venc), "fichero": f"{EXP_PREF}-factura-{n}.txt"})
    return docs


def correo_de(n, fecha, de, para, asunto, cuerpo):
    return exp_doc(f"correo-{n}.txt", asunto, fecha,
                   correo(dmy_iso(fecha), PERSONAS[de][0], PERSONAS[para][0], asunto, cuerpo, PERSONAS[de][1]))


def correos():
    return [
        correo_de(1, "2025-01-20", "irene", "rebeca", "Visita de replanteo de la línea 2",
                  ["Hola, Rebeca:", "", f"En relación con {en(1)}, te propongo una visita de replanteo a la línea 2 la semana que viene.",
                   "Necesitamos acceso a la nave de hornos y a la zona donde irá la nueva bóveda.", "", "Un saludo, Irene."]),
        correo_de(2, "2025-01-27", "rebeca", "irene", "Confirmación de la visita",
                  ["Hola, Irene:", "", "Confirmo la visita de replanteo. Julián te estará esperando en la portería a las nueve.",
                   "Te pedimos que avises si hace falta parar alguna línea.", "", "Un saludo, Rebeca."]),
        correo_de(3, "2025-02-05", "nuria", "julian", "Accesos a la nave de hornos",
                  ["Hola, Julián:", "", "Te escribo para organizar los accesos a la nave de hornos durante las obras.",
                   "Los camiones de los suministradores entrarán por la puerta del muelle 2.", "", "Gracias, Nuria."]),
        correo_de(4, "2025-02-11", "tomas", "irene", "Planos de la bóveda",
                  ["Hola, Irene:", "", "Te adjunto la propuesta de geometría de la bóveda para revisar antes de la oferta.",
                   "Necesitamos confirmar el espesor del refractario en la zona de quemadores.", "", "Un saludo, Tomás."]),
        correo_de(5, "2025-02-14", "irene", "tomas", "Respuesta sobre los planos",
                  ["Hola, Tomás:", "", "Revisado. La geometría vale, pero el espesor en los quemadores debe ir en el plano de detalle.",
                   "Haz la oferta con ese espesor y con las piezas especiales que indicaste.", "", "Gracias, Irene."]),
        correo_de(6, "2025-02-20", "ana", "nuria", "Visita para medir la estructura",
                  ["Hola, Nuria:", "", "Queremos visitar la línea 2 para medir la estructura antes de presupuestar el montaje.",
                   "¿Podéis indicarnos la fecha y la persona de contacto?", "", "Un saludo, Ana Belén."]),
        correo_de(7, "2025-03-03", "felipe", "irene", "Acometida eléctrica",
                  ["Hola, Irene:", "", "La acometida actual no da para la potencia de la nueva línea sin reforzar el centro de transformación.",
                   "Te propongo que lo incluyamos en el presupuesto como partida separada.", "", "Un saludo, Felipe."]),
        correo_de(8, "2025-03-05", "julian", "nuria", "Parada de la línea 1",
                  ["Hola, Nuria:", "", "La conexión de la nueva línea obliga a parar la línea 1 dos días.",
                   "Preferimos hacerlo en la semana de mantenimiento programado.", "", "Julián."]),
        correo_de(9, "2025-03-12", "ana", "irene", "Oferta revisada de montaje",
                  ["Hola, Irene:", "", f"Te remitimos la oferta revisada para el montaje mecánico, en {en(2)}.",
                   "Sustituye a la que os enviamos en febrero para el mismo alcance.", "", "Un saludo, Ana Belén."]),
        correo_de(10, "2025-03-18", "irene", "rebeca", "Propuesta de adjudicación del montaje",
                  ["Hola, Rebeca:", "", "Te propongo aceptar la oferta revisada de Garbea para el montaje mecánico.",
                   "Si estáis de acuerdo, lo recogemos en la próxima acta de obra.", "", "Un saludo, Irene."]),
        correo_de(11, "2025-04-02", "rafael", "nuria", "Espesor del aislamiento",
                  ["Hola, Nuria:", "", "Para el aislamiento de la cámara proponemos dos espesores según la zona.",
                   "Te envío la tabla con las pérdidas de calor estimadas por zona.", "", "Un saludo, Rafael."]),
        correo_de(12, "2025-04-10", "gerardo", "nuria", "Transporte de la bóveda",
                  ["Hola, Nuria:", "", "Podemos transportar las piezas pesadas la semana del 21 de abril.",
                   "Necesitamos saber antes el acceso del camión por la puerta del muelle 2.", "", "Un saludo, Gerardo."]),
        correo_de(13, "2025-04-16", "elena", "irene", "Cimentación del soporte",
                  ["Hola, Irene:", "", "El soporte del nuevo horno necesita una losa armada con el espesor del estudio geotécnico.",
                   f"Te envío el cálculo preliminar para revisarlo entre todos, en {en(4)}.", "", "Un saludo, Elena."]),
        correo_de(14, "2025-05-06", "rebeca", "irene", "Primera factura",
                  ["Hola, Irene:", "", "Hemos recibido la primera factura de los honorarios y la estamos revisando con administración.",
                   "Te avisaremos en cuanto tengamos el visto bueno.", "", "Un saludo, Rebeca."]),
        correo_de(15, "2025-05-13", "irene", "rebeca", "Recepción del pago",
                  ["Hola, Rebeca:", "", "Confirmo que la documentación de la primera factura está completa.",
                   "Quedo a la espera de la transferencia en el plazo acordado.", "", "Un saludo, Irene."]),
        correo_de(16, "2025-05-22", "felipe", "nuria", "Certificado de la acometida",
                  ["Hola, Nuria:", "", "Adjunto el certificado de la acometida y del cuadro de la línea 2.",
                   "Quedan pendientes las pruebas de los relés, que haremos en julio.", "", "Un saludo, Felipe."]),
        correo_de(17, "2025-06-03", "julian", "irene", "Refractario de la cámara 3",
                  ["Hola, Irene:", "", "Hay una grieta en el refractario de la cámara 3, detectada en la inspección de ayer.",
                   "Hace falta decidir si se repara o se sustituye la pieza antes del montaje.", "", "Julián."]),
        correo_de(18, "2025-06-10", "irene", "julian", "Plan para la reparación",
                  ["Hola, Julián:", "", "Proponemos reparar la grieta con mortero refractario y revisar la cámara después del secado.",
                   "Refractarios Sierra del Mar revisará la pieza en la próxima visita.", "", "Un saludo, Irene."]),
        correo_de(19, "2025-06-24", "nuria", "tomas", "Pedido de ladrillos confirmado",
                  ["Hola, Tomás:", "", "Confirmamos el pedido de ladrillos refractarios para la bóveda.",
                   "Os pedimos que el lote venga con su certificado de calidad.", "", "Un saludo, Nuria."]),
        correo_de(20, "2025-07-01", "ana", "nuria", "Retraso en el montaje",
                  ["Hola, Nuria:", "", "El montaje de la estructura se retrasa una semana por la llegada tardía de un perfil.",
                   "Lo reprogramaremos en cuanto llegue el material.", "", "Un saludo, Ana Belén."]),
        correo_de(21, "2025-07-15", "irene", "ana", "Respuesta sobre el retraso",
                  ["Hola, Ana Belén:", "", "Gracias por avisar. Acordamos reprogramar el montaje sin cambios en el alcance.",
                   "Nos interesa que el montaje quede terminado antes de la prueba de agosto.", "", "Un saludo, Irene."]),
        correo_de(22, "2025-08-04", "rebeca", "irene", "Visita antes de la prueba",
                  ["Hola, Irene:", "", "Queremos una visita de comprobación antes de la prueba en caliente.",
                   "¿Podéis venir la semana que viene con la lista de pendientes?", "", "Un saludo, Rebeca."]),
        correo_de(23, "2025-08-18", "irene", "rebeca", "Preparación de la prueba",
                  ["Hola, Rebeca:", "", "Te envío la lista de pendientes que hay que cerrar antes de la prueba.",
                   "Las incidencias de aislamiento se corregirán esta semana, como se acordó en la reunión.", "", "Un saludo, Irene."]),
        correo_de(24, "2025-09-08", "felipe", "nuria", "Pruebas de los relés",
                  ["Hola, Nuria:", "", "Hemos terminado las pruebas de los relés de protección de la línea 2.",
                   "Os envío el protocolo firmado para incluirlo en el dossier.", "", "Un saludo, Felipe."]),
        correo_de(25, "2025-10-14", "irene", "rebeca", "Documentación final",
                  ["Hola, Rebeca:", "", "Te adelanto el índice del dossier final de la obra.",
                   "Cuando tengamos los certificados de todos los suministradores, lo cerramos.", "", "Un saludo, Irene."]),
    ]


def actas():
    return [exp_doc(f"acta-obra-{n}.txt", f"Acta de obra nº {n}", fecha, [
        f"ACTA DE OBRA Nº {n}", f"Reunión de obra del {dmy_iso(fecha)} en la planta, archivada en {en(n)}.",
        "Asistentes: Irene Salcedo Moya y Nuria Baeza Ortiz (Brumeral); Julián Mesa Beltrán y Sofía Lorenzo Pardo (Cerámicas Valtierno, S.A.).",
        "Orden del día:", *[f"{i}. {t}" for i, t in enumerate(orden, 1)], "", "Acuerdos:",
        *[f"Acuerdo {i}. {t}" for i, t in enumerate(acuerdos, 1)], "",
        "Y no habiendo más asuntos, se levanta la reunión.",
        "Firman el acta: Irene Salcedo Moya, por Brumeral; Rebeca Quintero Aranda, por Cerámicas Valtierno, S.A."])
        for n, fecha, orden, acuerdos in ACTAS]


def informes():
    return [exp_doc(f"informe-tecnico-{n}.txt", f"Informe técnico nº {n}", fecha, [
        f"INFORME TÉCNICO Nº {n}", f"Fecha del informe: {dmy_iso(fecha)}.", f"Realizado por Brumeral en {en(n + 2)}.", "",
        "Conclusiones y datos:", *[f"{i}. {t}" for i, t in enumerate(frases, 1)], "",
        "Firmado: Nuria Baeza Ortiz, de Brumeral."])
        for n, fecha, frases in INFORMES]


def certificados():
    return [exp_doc(f"certificado-{n}.txt", f"Certificado nº {n}", fecha, [
        f"CERTIFICADO Nº {n}",
        f"Se certifica que {empresa} ha suministrado y montado {objeto} para la ampliación de la línea del horno 2 de Cerámicas Valtierno, S.A.",
        f"Fecha de emisión: {dmy_iso(fecha)}.", f"Documento archivado en {en(n + 4)}.", "", "Datos del certificado:",
        *[f"{i}. {t}" for i, t in enumerate(datos, 1)], "", f"Firmado: {rep}, por {empresa}"])
        for n, fecha, empresa, rep, objeto, datos in CERTIFICADOS]


def expedientes_documentos():
    """Los 60 documentos del expediente: 2 contratos, 7 presupuestos, 6 facturas, 25 correos, 8 actas, 8 informes y 4 certificados."""
    return [contrato_ingenieria(), adenda_1(), *presupuestos(), *facturas(), *correos(), *actas(), *informes(), *certificados()]


def correo_ref_017():
    """Señuelo (ajenos): otro asunto de Logística Penedal que cita «su referencia 017». No lleva importes ni fechas que compitan."""
    return doc("logistica-penedal", "correo-ref-017.txt", "Reserva del muelle de carga", "2025-11-04", [correo(
        "04/11/2025", "Ramiro Gallardo Sanz, de Logística Penedal, S.L.", "Gonzalo Robledo Ibáñez, de Logística Penedal, S.L.",
        "Reserva del muelle de carga",
        ["Hola, Gonzalo:", "", "Te escribo sobre su referencia 017: el muelle 4 queda reservado el jueves de la semana que viene para la descarga de palés.",
         "Si necesitáis otro muelle, avisadme antes del martes.", "", "Un saludo, Ramiro."],
        ["Ramiro Gallardo Sanz", "Logística Penedal, S.L."])])


DOCUMENTOS.append(doc(EXP_PROY, CASO_FICHERO, "Caso 2025-017, ampliación de la línea del horno 2", "2025-01-13", CASO_PAGINAS))


def expedientes_json(exp):
    documentos = [ruta(d["proyecto"], d["fichero"]) for d in exp if d["proyecto"] == EXP_PROY]
    return [{"caso": CASO, "proyecto": EXP_PROY, "documentos": documentos, "compartidos": [], "ajenos": [DECOY],
             "formas_numero": FORMAS_EXP, "sustituciones": [[fx("presupuesto-2"), fx("presupuesto-2-revisado")]]}]


def pregunta_gx(n, subtipo, actor, texto, citas=(), claves=(), esperados=(), concreta=False, todas=False, abstener=False,
                prohibidas=(), sustituidos=(), docs_prohibidos=(), ausente=None):
    q = {"id": f"GX{n:02d}", "tipo": "expediente", "origen": "generalidad", "actor": actor, "pregunta": texto,
         "citas_resueltas": list(citas), "fichas_esperadas": [], "todas_las_citas": todas,
         "claves": [list(g) for g in claves], "abstener": abstener, "prohibidas": list(prohibidas),
         "sustituidos": list(sustituidos), "docs_prohibidos": list(docs_prohibidos),
         "caso": CASO, "esperados": list(esperados), "subtipo": subtipo, "concreta": concreta}
    if ausente:
        q["ausente"] = list(ausente)
    if actor in ("t09", "t10"):
        q["rol"] = "neutro"
        if not abstener:
            q["no_afirmar"] = list(NO_AFIRMAR)
    return q


def preguntas_expediente():
    rutas = [ruta(EXP_PROY, d["fichero"]) for d in expedientes_documentos()]
    presupuestos_vigentes = ["presupuesto-1", "presupuesto-2-revisado", "presupuesto-3", "presupuesto-4", "presupuesto-5", "presupuesto-6"]
    return [
        pregunta_gx(1, "concreta", "t09", "¿Cuál es el total de la factura F-2025-104 de Cerámicas Valtierno?",
                    citas=[cx("factura-4", 1, "Total factura: 10.527,00 euros")], claves=[["10.527"]],
                    esperados=[fx("factura-4")], concreta=True),
        pregunta_gx(2, "concreta", "t09", "¿Quién es la directora facultativa de la ampliación de la línea 2 de Cerámicas Valtierno?",
                    citas=[cx("contrato-ingenieria", 1, "Directora facultativa: Irene Salcedo Moya.")], claves=[["Irene Salcedo"]],
                    esperados=[fx("contrato-ingenieria")], concreta=True),
        pregunta_gx(3, "concreta", "t09", "¿Qué día se fijó en el acta de obra nº 5 para la prueba en caliente del horno?",
                    citas=[cx("acta-obra-5", 1, "Se fija la prueba en caliente del horno para el 12 de septiembre de 2025.")],
                    claves=[["12 de septiembre de 2025", "12/09/2025"]], esperados=[fx("acta-obra-5")], concreta=True),
        pregunta_gx(4, "sustituidos", "t09", "¿Cuánto cuesta ahora el presupuesto de Montajes Garbea para la ampliación de la línea 2?",
                    citas=[cx("presupuesto-2-revisado", 1, "Total del presupuesto: 33.900 euros.")], claves=[["33.900"]],
                    prohibidas=["35.400"], sustituidos=[fx("presupuesto-2")], esperados=[fx("presupuesto-2-revisado")], concreta=True),
        pregunta_gx(5, "concreta", "t09", "¿Cuántos honorarios totales tiene el contrato de ingeniería de Cerámicas Valtierno?",
                    citas=[cx("adenda-1", 1, "Los honorarios totales pasan a ser de 52.200 euros más IVA, en seis pagos de 8.700 euros.")],
                    claves=[["52.200"]], prohibidas=["48.600"], esperados=[fx("adenda-1")], concreta=True),
        pregunta_gx(6, "concreta", "t09", "¿Cuál es la temperatura máxima de diseño del horno según el informe técnico nº 3?",
                    citas=[cx("informe-tecnico-3", 1, "Temperatura máxima de diseño: 1.180 °C.")], claves=[["1.180"]],
                    esperados=[fx("informe-tecnico-3")], concreta=True),
        pregunta_gx(7, "analisis", "t09", "¿Cuánto se ha facturado en total en el expte. 2025/017 de Cerámicas Valtierno? Cita cada factura.",
                    citas=[cx(f"factura-{n}", 1, f"Número de factura: F-2025-{100 + n}") for n in range(1, 7)],
                    claves=[["52.200", "63.162"]], todas=True, esperados=[fx(f"factura-{n}") for n in range(1, 7)]),
        pregunta_gx(8, "analisis", "t09", "Resume el expte. 2025/017 de Cerámicas Valtierno: qué se contrató, qué presupuestos hubo, cuál se aceptó y cuánto se ha facturado.",
                    citas=[cx("contrato-ingenieria", 1, "CONTRATO DE INGENIERÍA CI-2025-017"),
                           cx("acta-obra-2", 1, "Se acepta el presupuesto revisado de Montajes Garbea, S.L.")],
                    claves=[["CI-2025-017"], ["Montajes Garbea"], ["52.200", "63.162"]],
                    esperados=[fx("contrato-ingenieria"), fx("adenda-1"), fx("acta-obra-2"), fx("presupuesto-1"), fx("presupuesto-2"),
                               fx("presupuesto-2-revisado"), fx("presupuesto-3"), fx("presupuesto-4"), fx("presupuesto-5"),
                               fx("presupuesto-6"), *[fx(f"factura-{n}") for n in range(1, 7)]]),
        pregunta_gx(9, "analisis", "t09", "¿Qué presupuestos hemos recibido para la ampliación de la línea 2 y cuál es el importe vigente de cada uno?",
                    citas=[cx(p, 1, f"Total del presupuesto: {miles(euros)} euros.") for p, euros in zip(
                        presupuestos_vigentes, [21750, 33900, 27480, 12960, 6240, 41300])],
                    claves=[["Refractarios Sierra del Mar"], ["Montajes Garbea"], ["Electroindustrial Prado Alto"],
                            ["Aislamientos Térmicos Olmedo"], ["Transportes Hidalgo Cruz"], ["Ingeniería Civil Ribazo"]],
                    prohibidas=["35.400"], sustituidos=[fx("presupuesto-2")],
                    esperados=[fx(p) for p in (*presupuestos_vigentes, "presupuesto-2")]),
        pregunta_gx(10, "contiene", "t09", "¿Qué contratos hay en el expte. 2025/017 de Cerámicas Valtierno?",
                    citas=[cx("contrato-ingenieria", 1, "CONTRATO DE INGENIERÍA CI-2025-017"),
                           cx("adenda-1", 1, "ADENDA 1 AL CONTRATO CI-2025-017")],
                    claves=[["Contrato de ingeniería"], ["adenda"]], todas=True,
                    esperados=[fx("contrato-ingenieria"), fx("adenda-1")]),
        pregunta_gx(11, "no_consta", "t09", "¿Qué penalización por retraso tiene el contrato con Cerámicas Valtierno?",
                    abstener=True, ausente=["penalización"], esperados=[fx("contrato-ingenieria"), fx("adenda-1")]),
        pregunta_gx(12, "ajenos", "t09", "Busca todo lo que cite «su referencia 017» y dime qué documentos forman parte del expte. 2025/017.",
                    esperados=rutas, docs_prohibidos=[DECOY]),
        pregunta_gx(13, "aislamiento", "t10", "¿Cuánto se ha facturado en el expte. 2025/017 de Cerámicas Valtierno?",
                    abstener=True, docs_prohibidos=[EXP_DIR]),
        pregunta_gx(14, "aislamiento", "t01", "¿Qué contrato tiene Brumeral con Cerámicas Valtierno?",
                    abstener=True, docs_prohibidos=["brumeral/"]),
    ]


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
    exp = expedientes_documentos() + [correo_ref_017()]
    paginas_exp = {ruta(d["proyecto"], d["fichero"]): paginas_de(d) for d in exp}
    ficheros_exp = {r: "".join(f"=== Página {n} ===\n{t}\n" for n, t in enumerate(ps, 1)) for r, ps in paginas_exp.items()}
    return {"paginas": paginas, "corpus": corpus, "manifiesto": manifiesto,
            "bateria": preguntas(), "claves": claves(),
            "paginas_expediente": paginas_exp, "expedientes_ficheros": ficheros_exp,
            "expedientes": expedientes_json(exp), "bateria_expediente": preguntas_expediente()}


def escribir(modelo, destino):
    shutil.rmtree(destino / "corpus", ignore_errors=True)
    shutil.rmtree(destino / "expedientes", ignore_errors=True)
    for r, texto in modelo["corpus"].items():
        fichero = destino / "corpus" / r
        fichero.parent.mkdir(parents=True, exist_ok=True)
        fichero.write_text(texto, encoding="utf-8")
    for r, texto in modelo["expedientes_ficheros"].items():
        fichero = destino / "expedientes" / r
        fichero.parent.mkdir(parents=True, exist_ok=True)
        fichero.write_text(texto, encoding="utf-8")
    for nombre in ("manifiesto", "claves", "bateria", "expedientes", "bateria_expediente"):
        salida = json.dumps(modelo[nombre], ensure_ascii=False, indent=1) + "\n"
        (destino / f"{nombre.replace('_', '-')}.json").write_text(salida, encoding="utf-8")


# ---------- autocomprobaciones ----------
def comprobar_ids(modelo):
    ids = [q["id"] for q in modelo["bateria"]] + [k["id"] for k in modelo["claves"]]
    ids += [f["id"] for f in FICHAS] + [q["id"] for q in modelo["bateria_expediente"]]
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
        textos += [paginas[d][0] for d in q.get("esperados", [])]
        textos += [FICHAS_POR_ID[f]["texto"] for f in q["fichas_esperadas"]]
        if q.get("tipo") == "expediente":
            textos.append(SUMA_FACTURAS)
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


def comprobar_expediente(modelo, destino):
    e = modelo["expedientes"][0]
    docs, ajenos = e["documentos"], e["ajenos"]
    pag = modelo["paginas_expediente"]
    exigir(len(docs) == 60 and len(set(docs)) == 60, "el expediente no tiene 60 documentos distintos")
    exigir(ajenos == [DECOY] and DECOY not in docs, "el señuelo está en los documentos del expediente")
    exigir(e["compartidos"] == [], "el expediente no debe tener compartidos")
    exigir(e["caso"] in {d["ruta"] for d in modelo["manifiesto"]["documentos"]}, "el caso no está en el manifiesto")
    for r in docs + ajenos:
        exigir((destino / "expedientes" / r).is_file(), f"falta {r}")
        exigir(len(pag[r]) == 1, f"{r}: más de una página")
        lineas = [x for x in pag[r][0].split("\n") if x.strip()]
        exigir(10 <= len(lineas) <= 25, f"{r}: {len(lineas)} líneas")
        exigir(not REDACCION.search(sin_tildes(pag[r][0])) and "penalización" not in pag[r][0].lower(), f"{r}: palabra prohibida")
    for ant, nue in e["sustituciones"]:
        exigir(ant in docs and nue in docs, f"sustitución fuera del expediente: {ant}")
    for cadena, fichero in HECHOS_FIJOS.items():
        con = [r for r in docs + ajenos if cadena in pag[r][0]]
        exigir(con == [EXP_DIR + fichero], f"«{cadena}» debe estar solo en {fichero}, está en {con}")
    for r in docs + ajenos:
        es_factura = "expte-2025-017-factura-" in r
        exigir(("10.527,00" in pag[r][0]) == es_factura and ("1.827,00" in pag[r][0]) == es_factura, f"total de factura fuera de su documento: {r}")
        exigir("63.162" not in pag[r][0], f"63.162 no debe aparecer escrito: {r}")
    for q in modelo["bateria_expediente"]:
        exigir(q["caso"] == e["caso"], f"{q['id']}: caso distinto")
        exigir(all(r in pag for r in q["esperados"]), f"{q['id']}: esperado inexistente")
        exigir(not any(r.startswith(p) for r in q["esperados"] for p in q["docs_prohibidos"]), f"{q['id']}: esperado prohibido")
        exigir(all(s in docs for s in q["sustituidos"]), f"{q['id']}: sustituido fuera del expediente")


def verificar(modelo, destino):
    paginas = {**modelo["paginas"], **modelo["paginas_expediente"]}
    bateria_todas = modelo["bateria"] + modelo["bateria_expediente"]
    comprobar_ids(modelo)
    comprobar_anclas(bateria_todas, paginas)
    comprobar_claves(bateria_todas, paginas)
    comprobar_fuentes_claves(modelo["claves"], modelo["bateria"], paginas)
    comprobar_rutas(modelo["manifiesto"], destino)
    comprobar_redaccion(paginas)
    comprobar_expediente(modelo, destino)


def generar(destino=DATOS):
    destino = Path(destino)
    destino.mkdir(parents=True, exist_ok=True)
    modelo = construir()
    escribir(modelo, destino)
    verificar(modelo, destino)


if __name__ == "__main__":
    generar()
