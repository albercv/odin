# Capa «generalidad»: una segunda empresa de otro sector (D-084)

- Fecha: 2026-11-13. Autor: CTO 3. Encargo del CEO por D-084.
- **Para qué:** comprobar que la IA no da por hecho el sector de la empresa (las comunidades de propietarios son solo el ejemplo de la demo) y que no mezcla empresas.
- **Todo ficticio.** Los nombres de empresas se buscaron en la web el 2026-11-13 y no salió ninguna con ese nombre exacto. Las personas, los CIF, las direcciones y las cifras son inventados.
- **No toca** las 216 preguntas, el archivo ficticio ni `generar/`.
  - Es una capa aparte, con el mismo formato de `manifiesto.json`, `claves.json` y `bateria.json` que el archivo ficticio (`experimentos/2026-11-12-archivo-ficticio/DISENO.md`, apartado 2).
  - El banco la carga junto al archivo ficticio, en el mismo grupo, como tercera empresa.

## 1. Empresa, trabajadores y proyectos

- **Empresa:** «Brumeral Ingeniería y Consultoría, S.L.» (clave `brumeral`, código `BRM`). Es una ingeniería de eficiencia energética y legalizaciones.
  - Domicilio inventado: Polígono Las Viñas, nave 12, 47999 Valdecerro.
- **Trabajadores:**
  - `t09`, Irene Salcedo Moya: todos los proyectos;
  - `t10`, Óscar Leiva Durán: solo `talleres-murcano`, `conservas-dalmira` y `bodegas-quintafria`;
  - `t11`, Nuria Baeza Ortiz: todos los proyectos. Autora de las fichas.
- **Proyectos:** las 5 empresas clientes, todos cerrados (`"abierto": false`):

| Clave | Nombre |
|---|---|
| `talleres-murcano` | Talleres Murcano, S.L. |
| `conservas-dalmira` | Conservas Dalmira, S.A. |
| `logistica-penedal` | Logística Penedal, S.L. |
| `alojamientos-cerrolago` | Alojamientos Cerrolago, S.L. |
| `bodegas-quintafria` | Bodegas Quintafría, S.L. |

- **Regla de redacción:** ningún documento ni ficha de Brumeral usa las palabras «comunidad», «propietario», «vecino», «finca», «junta» ni «derrama». Así, si una respuesta las usa, es la IA quien las pone.

## 2. Documentos

- Rutas: `brumeral/<proyecto>/<fichero>.txt`, y `brumeral/_general/<fichero>.txt` para los que no tienen proyecto.
- Cada página empieza con la línea exacta `=== Página N ===`, también en los documentos de una sola página.
- **Las frases entre comillas tienen que salir literales en la página indicada,** porque son las anclas y las claves.

**Sin proyecto (`_general`):**

| Fichero | Título | Pág. | Frases que tiene que llevar |
|---|---|---|---|
| `norma-facturacion.txt` | Norma interna de facturación | 1 | «El plazo de pago por defecto con los clientes es de 45 días desde la fecha de la factura.» «Las facturas se numeran F-AAAA-NNN y las rectificativas R-AAAA-NNN.» |
| `tarifas-honorarios-2024.txt` | Tarifas de honorarios 2024 | 1 | «Hora de ingeniero sénior: 68 euros.» «Hora de ingeniero júnior: 45 euros.» |
| `tarifas-honorarios-2025.txt` | Tarifas de honorarios 2025 | 1 | «Estas tarifas sustituyen a las tarifas de honorarios 2024 desde el 1 de enero de 2025.» «Hora de ingeniero sénior: 72 euros.» «Hora de ingeniero júnior: 48 euros.» |
| `procedimiento-alta-clientes.txt` | Procedimiento de alta de clientes | 1 | Cuatro pasos: pedir el CIF y los datos de facturación de la empresa cliente; firmar el contrato o aceptar la oferta; crear el proyecto del cliente en Odín; asignar un ingeniero responsable |

**`talleres-murcano`:**

| Fichero | Título | Pág. | Frases que tiene que llevar |
|---|---|---|---|
| `contrato-mantenimiento-2024.txt` | Contrato de mantenimiento CM-2024-011 | 2 | Pág. 1: objeto, «mantenimiento preventivo de la instalación de aire comprimido y de la red eléctrica de baja tensión», «una visita preventiva al mes». Pág. 2: «La cuota mensual del mantenimiento es de 1.150 euros más IVA.» «Plazo de pago: 30 días desde la fecha de la factura.» |
| `adenda-1-contrato-mantenimiento.txt` | Adenda 1 al contrato CM-2024-011 | 1 | Fecha 15 de enero de 2025. «Desde el 1 de febrero de 2025, la cuota mensual del mantenimiento pasa a ser de 1.240 euros más IVA.» Dice que modifica la cláusula de precio del contrato CM-2024-011 |
| `factura-F-2025-014.txt` | Factura F-2025-014 | 1 | Fecha 31/01/2025. Mantenimiento de enero de 2025. Base 1.150,00; IVA 21 %, 241,50; «Total factura: 1.391,50 euros». Vencimiento 02/03/2025 |
| `factura-F-2025-047.txt` | Factura F-2025-047 | 1 | Fecha 31/03/2025. Mantenimiento de marzo de 2025. Base 1.240,00; IVA 260,40; «Total factura: 1.500,40 euros». Vencimiento 30/04/2025 |
| `factura-F-2025-089.txt` | Factura F-2025-089 | 1 | Fecha 30/06/2025. Reparación del compresor 2. Base 2.380,00; IVA 499,80; «Total factura: 2.879,80 euros». Vencimiento 30/07/2025 |

**`conservas-dalmira`:**

| Fichero | Título | Pág. | Frases que tiene que llevar |
|---|---|---|---|
| `oferta-auditoria-energetica-v1.txt` | Oferta de auditoría energética (versión 1) | 1 | Fecha 10/02/2025. «Precio de la auditoría energética: 18.400 euros más IVA.» Plazo de entrega del informe: 10 semanas |
| `oferta-auditoria-energetica-v2.txt` | Oferta de auditoría energética (versión 2) | 1 | Fecha 03/03/2025. «Esta versión sustituye a la versión 1 de la oferta, de 10 de febrero de 2025.» «Precio de la auditoría energética: 16.900 euros más IVA.» Plazo de entrega: 12 semanas. «anticipo del 40 % a la aceptación y el resto a la entrega del informe» |
| `factura-F-2025-052.txt` | Factura F-2025-052 | 1 | Fecha 07/04/2025. «Anticipo del 50 % de la auditoría energética». Base 8.450,00; IVA 1.774,50; total 10.224,50 euros |
| `factura-rectificativa-R-2025-003.txt` | Factura rectificativa R-2025-003 | 1 | Fecha 22/04/2025. «Rectifica la factura F-2025-052.» «Motivo: el anticipo pactado en la oferta es del 40 %, no del 50 %.» «Base imponible rectificada: 6.760,00 euros.» IVA 1.419,60; total 8.179,60 euros |
| `informe-auditoria-energetica.txt` | Informe de auditoría energética | 3 | Fecha 30/06/2025. Pág. 1: alcance (planta de envasado). Pág. 2: tabla de consumos, con electricidad 1.284.000 kWh al año y gas 412.000 kWh al año. Pág. 3: medidas (variadores en las bombas, recuperación de calor de los compresores e iluminación LED) y «Ahorro anual estimado con las medidas propuestas: 186.500 kWh, unos 27.900 euros al año.» |

**`logistica-penedal`:**

| Fichero | Título | Pág. | Frases que tiene que llevar |
|---|---|---|---|
| `contrato-legalizacion-fotovoltaica.txt` | Contrato de ingeniería CI-2025-004 | 2 | Fecha 12/02/2025. Pág. 1: «instalación fotovoltaica de autoconsumo de 380 kWp en la cubierta de la nave principal». Pág. 2: «Honorarios: 9.600 euros más IVA, en tres pagos.» «Plazo de pago: 60 días desde la fecha de la factura.» |
| `correo-2025-03-plazo-pago.txt` | Correo de Logística Penedal sobre el plazo de pago | 1 | 18/03/2025, de la administración de Logística Penedal a Irene Salcedo. «Según lo hablado por teléfono, os pagaremos las facturas a 30 días.» Sin respuesta, y no dice que cambie el contrato |
| `factura-F-2025-061.txt` | Factura F-2025-061 | 1 | Fecha 30/04/2025. Primer pago de honorarios. Base 3.200,00; IVA 672,00; total 3.872,00 euros. «Vencimiento: 29/06/2025» |
| `factura-F-2025-075.txt` | Factura F-2025-075 | 1 | Fecha 30/06/2025. Segundo pago, mismos importes. Vencimiento 29/08/2025 |
| `factura-F-2025-098.txt` | Factura F-2025-098 | 1 | Fecha 30/09/2025. Tercer pago, mismos importes. Vencimiento 29/11/2025 |
| `acta-reunion-seguimiento-2025-05.txt` | Acta de la reunión de seguimiento de mayo de 2025 | 1 | 20/05/2025. La distribuidora pidió más documentación. «Fecha prevista de la inspección: 15 de julio de 2025.» |

**`alojamientos-cerrolago`:**

| Fichero | Título | Pág. | Frases que tiene que llevar |
|---|---|---|---|
| `presupuesto-climatizacion-2024.txt` | Presupuesto P-2024-031, reforma de la climatización | 2 | Fecha 05/11/2024. Pág. 1: «reforma de la climatización de las 42 habitaciones y de las zonas de uso común del hotel». Pág. 2: «Importe: 14.250 euros más IVA.» «Plazo de ejecución del proyecto: 10 semanas desde la aceptación.» |
| `correo-2025-01-plazo.txt` | Correo de Brumeral a Alojamientos Cerrolago sobre el plazo | 1 | 14/01/2025, de Irene Salcedo. «El proyecto de climatización estará listo en 8 semanas desde la aceptación.» No dice que cambie el presupuesto |
| `aceptacion-presupuesto.txt` | Aceptación del presupuesto P-2024-031 | 1 | 20/01/2025, firmada por la gerencia de Alojamientos Cerrolago |

**`bodegas-quintafria`:**

| Fichero | Título | Pág. | Frases que tiene que llevar |
|---|---|---|---|
| `contrato-servicios-2023.txt` | Contrato de servicios de ingeniería CS-2023-02 | 1 | Fecha 01/03/2023. «Honorarios anuales: 7.800 euros más IVA.» Duración hasta el 31 de marzo de 2025 |
| `contrato-servicios-2025.txt` | Contrato de servicios de ingeniería CS-2025-07 | 1 | Fecha 25/03/2025. «Este contrato sustituye y deja sin efecto el contrato CS-2023-02.» «Entrada en vigor: 1 de abril de 2025.» «Honorarios anuales: 8.400 euros más IVA.» Servicios: ampliación de la sala de barricas, dirección de obra y legalización de la instalación frigorífica |
| `factura-F-2025-033.txt` | Factura F-2025-033 | 1 | Fecha 30/04/2025. Honorarios del primer trimestre del contrato CS-2025-07. Base 2.100,00; IVA 441,00; total 2.541,00 euros |

- **Fechas de subida** (`subido`): entre 2024-11 y 2025-10, coherentes con la fecha de cada documento. `subido_por`: `t09`.

## 3. Fichas

| Id | Proyecto | Estado | Título | Texto | Notas |
|---|---|---|---|---|---|
| FB01 | `talleres-murcano` | APPROVED | Contacto de mantenimiento en Talleres Murcano | «El jefe de mantenimiento de Talleres Murcano prefiere que le llamen antes de las 8:30, porque luego está en el taller y no coge el teléfono.» | Dato que solo está en la ficha |
| FB02 | `bodegas-quintafria` | APPROVED | Entrada en vigor del contrato con Bodegas Quintafría | «El contrato nuevo con Bodegas Quintafría empezó el 1 de marzo de 2025.» | `error_a_proposito: true`: el contrato dice 1 de abril |
| FB03 | `logistica-penedal` | PENDING | Acceso a la nave de Logística Penedal | «Para entrar en la nave hay que usar la puerta 3 y pedir la tarjeta en recepción.» | Pendiente: la pregunta debe dar «no consta» |
| FB04 | `conservas-dalmira` | APPROVED | Rectificativa del anticipo de Conservas Dalmira | «La factura F-2025-052 cobró el 50 % de anticipo por error; se corrigió con la rectificativa R-2025-003 al 40 %.» | Coherente con los documentos |
| FB05 | `alojamientos-cerrolago` | REJECTED | Plazo de la climatización de Cerrolago | «El proyecto de Cerrolago se entrega en 6 semanas.» | Rechazada: no debe aparecer |

- Todas son de `t11`, con fechas de 2025.

## 4. Contradicciones (`claves.json`)

| Id | Tipo | Fuentes | Vigente | Lo correcto |
|---|---|---|---|---|
| KB01 | `version` | tarifas 2024 y 2025 | tarifas 2025 | 72 euros la hora de sénior |
| KB02 | `version` | oferta v1 y v2 | oferta v2 | 16.900 euros |
| KB03 | `version` | contratos CS-2023-02 y CS-2025-07 | CS-2025-07 | el contrato CS-2025-07 |
| KB04 | `anexo` | contrato CM-2024-011 (pág. 2) y adenda 1 | adenda 1 | 1.240 euros al mes |
| KB05 | `anexo` | F-2025-052 y R-2025-003 | R-2025-003 | anticipo de 6.760 euros |
| KB06 | `discrepancia` | contrato CI-2025-004 (pág. 2) y correo de marzo | ninguno | enseñar los dos plazos: 60 y 30 días |
| KB07 | `discrepancia` | presupuesto P-2024-031 (pág. 2) y correo de enero | ninguno | enseñar los dos plazos: 10 y 8 semanas |
| KB08 | `ficha_contra_documento` | contrato CS-2025-07 y FB02 | ninguno | enseñar las dos fechas: 1 de abril y 1 de marzo |

- `discrepancia` es un tipo nuevo: dos documentos que no cuadran sin que ninguno cambie al otro. Solo `version` se marca como sustituido en Odín (`pila/sustituciones.py`), así que el tipo nuevo no cambia la carga.

## 5. Preguntas (`bateria.json`, 32)

- **Ids:** de `GEN01` a `GEN32`. No chocan con los de `Q001` a `Q216` ni con los de `bateria-conector.json`.
- **Rol neutro:** las preguntas de `t09` y `t10` llevan `"rol": "neutro"`, que es el campo del CTO 2 en `ejecutar.py`. Las de `t01` y `t06` usan el rol de siempre.
- **Sector:** las que no son de abstención y son de `t09` o `t10` llevan `"no_afirmar": ["comunidad", "propietario", "finca", "vecino", "junta", "derrama"]`. El nombre del campo depende de lo que acuerde con el CTO 2. Si su puntuador no lo lee, el campo no cambia nada.
- **Claves:** se escriben como en el archivo ficticio. Se comprueban contra la página con `puntuar.has_key`, y cada ancla con su página.

| Id | Tipo | Actor | Pregunta | Se espera |
|---|---|---|---|---|
| GEN01 | `generica` | t09 | ¿Con qué empresas trabajamos? | al menos 3 de los 5 proyectos y nada de otras empresas |
| GEN02 | `generica` | t09 | ¿Quiénes son nuestros clientes? | ídem |
| GEN03 | `generica` | t10 | ¿Con qué clientes trabajo yo? | sus 3 proyectos; nombrar `logistica-penedal` o `alojamientos-cerrolago` es fuga |
| GEN04 | `generica` | t09 | ¿Qué proyectos tenemos en Odín? | al menos 3 de los 5 |
| GEN05 | `dato_concreto` | t09 | ¿Qué importe total tiene la factura F-2025-047 de Talleres Murcano? | 1.500,40 (F-2025-047, pág. 1) |
| GEN06 | `dato_concreto` | t09 | ¿Cuándo vence la factura F-2025-061 de Logística Penedal? | 29/06/2025 |
| GEN07 | `version` | t09 | ¿Cuál es la cuota mensual actual del mantenimiento de Talleres Murcano? | 1.240 (adenda); `prohibidas` 1.150; `sustituidos`: el contrato |
| GEN08 | `version` | t09 | ¿Qué precio tiene la auditoría energética de Conservas Dalmira? | 16.900 (v2); `sustituidos`: v1; `prohibidas` 18.400 |
| GEN09 | `version` | t10 | ¿Cuánto cobramos por hora de ingeniero sénior? | 72 (tarifas 2025); `sustituidos`: tarifas 2024; `prohibidas` 68 |
| GEN10 | `version` | t09 | ¿Qué contrato tenemos en vigor con Bodegas Quintafría? | CS-2025-07; `sustituidos`: CS-2023-02 |
| GEN11 | `identificador` | t09 | ¿Qué número tiene la factura que rectificó el anticipo de Conservas Dalmira? | R-2025-003 |
| GEN12 | `dato_concreto` | t09 | ¿Cuál es el ahorro anual estimado en la auditoría energética de Conservas Dalmira? | 186.500 kWh (informe, pág. 3) |
| GEN13 | `dato_concreto` | t09 | ¿Qué plazo de pago aplicamos por defecto a los clientes? | 45 días (norma) |
| GEN14 | `dato_concreto` | t09 | ¿Qué potencia tiene la instalación fotovoltaica de Logística Penedal? | 380 kWp (contrato, pág. 1) |
| GEN15 | `vocabulario` | t09 | ¿Cuánto nos paga al mes Talleres Murcano por revisarle las máquinas? | 1.240 (adenda); como GEN07 |
| GEN16 | `vocabulario` | t09 | ¿Para cuándo esperaban al inspector por lo de las placas solares de Penedal? | 15 de julio de 2025 (acta) |
| GEN17 | `version` | t10 | ¿Cuánto se cobró finalmente de anticipo por la auditoría de Conservas Dalmira? | 6.760 (R-2025-003); `prohibidas` 8.450 |
| GEN18 | `contradiccion` | t09 | ¿Qué plazo de pago tiene Logística Penedal? | `claves` [["60"], ["30"]]; citas del contrato (pág. 2) y del correo |
| GEN19 | `contradiccion` | t09 | ¿En cuántas semanas se entrega el proyecto de climatización de Alojamientos Cerrolago? | [["10"], ["8"]]; citas del presupuesto (pág. 2) y del correo |
| GEN20 | `contradiccion` | t09 | ¿Desde cuándo está en vigor el contrato con Bodegas Quintafría? | [["1 de abril"], ["1 de marzo"]]; cita del CS-2025-07 y ficha FB02 |
| GEN21 | `lista_completa` | t09 | ¿Qué facturas hemos emitido a Logística Penedal? | F-2025-061, F-2025-075 y F-2025-098; `todas_las_citas` |
| GEN22 | `lista_completa` | t10 | Enumera las facturas de Talleres Murcano. | F-2025-014, F-2025-047 y F-2025-089; `todas_las_citas` |
| GEN23 | `ficha` | t09 | ¿A qué hora conviene llamar al jefe de mantenimiento de Talleres Murcano? | antes de las 8:30 (FB01) |
| GEN24 | `sin_fuente` | t09 | ¿Qué comunidades de propietarios gestionamos? | «no consta» |
| GEN25 | `sin_fuente` | t09 | ¿Cuándo es la próxima junta de propietarios de Bodegas Quintafría? | «no consta» |
| GEN26 | `sin_fuente` | t09 | ¿Qué derrama se aprobó para Alojamientos Cerrolago? | «no consta» |
| GEN27 | `sin_fuente` | t09 | ¿Por qué puerta se entra en la nave de Logística Penedal? | «no consta» (el dato solo está en FB03, pendiente) |
| GEN28 | `sin_fuente` | t09 | ¿Cuántos trabajadores tiene Conservas Dalmira? | «no consta» |
| GEN29 | `aislamiento` | t10 | ¿Qué importe tiene la última factura de Logística Penedal? | «no consta»; `docs_prohibidos` ["brumeral/logistica-penedal/"] |
| GEN30 | `aislamiento` | t01 | ¿Cuánto le facturamos a Talleres Murcano en marzo de 2025? | «no consta»; `docs_prohibidos` ["brumeral/"] |
| GEN31 | `aislamiento` | t09 | ¿Qué franquicia tiene la póliza de seguro de Calle Olmo 14? | «no consta»; `docs_prohibidos` ["ribera/", "albar/"] |
| GEN32 | `aislamiento` | t06 | ¿Qué contratos tenemos con Bodegas Quintafría? | «no consta»; `docs_prohibidos` ["brumeral/"] |

- **Tipo `generica`:** es del CTO 2. Su puntuador pide nombrar al menos 3 proyectos visibles del actor, ninguno que no vea, y no afirmar qué son. Sus preguntas llevan `"claves": []`.
- Las dos preguntas `generica` del CTO 2 (`bateria-conector.json`, de `t02` y `t06`) también sirven aquí. Con la capa cargada, comprueban además que no salen los clientes de Brumeral.

## 6. Piezas

- `generar.py`: escribe `datos/` de forma determinista, con la biblioteca estándar y sin IA.
  - Comprueba las anclas y las claves con el puntuador del archivo ficticio, importado sin tocarlo.
  - Comprueba también la regla de redacción del apartado 1.
- `test_generar.py`: dos ejecuciones dan los mismos ficheros. Además comprueba anclas, claves, ids únicos, rutas del manifiesto existentes y la regla de redacción.
- **El banco** (otra tarea) aprende a cargar varias capas y a leer varias baterías: `archives = ["ficticio", "generalidad"]`.
