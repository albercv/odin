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
