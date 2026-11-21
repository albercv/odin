# Especificación del mundo ficticio de prueba

Todo es ficticio. Despacho: «Administraciones Ribera». Ciudad: Puerto Albar (inventada). No uses nombres de personas famosas, empresas reales ni aseguradoras reales.

## Reglas generales (obligatorias)

1. Un fichero `.txt` por documento, en UTF-8, en la carpeta indicada. Nombre de fichero exactamente el indicado.
2. Cada página empieza con una línea exacta `=== Página N ===` (N = 1, 2, 3…). La primera línea del fichero es `=== Página 1 ===`. Nada antes.
3. Cada página tiene entre 200 y 350 palabras de texto realista en español de España, con el estilo de los documentos de una administración de fincas (actas con orden del día, asistentes y coeficientes, ruegos y preguntas; pólizas con condiciones; contratos con cláusulas).
4. El número de páginas de cada documento es el indicado.
5. Las **frases obligatorias** se copian **literalmente**, carácter por carácter, en la página indicada. No las parafrasees ni las repitas en otros documentos.
6. **Prohibido** (para no contaminar la prueba): mencionar terremotos, seísmos o el Consorcio de Compensación de Seguros; teléfonos de Seguros Atalaya; juntas del año 2023 en Los Almendros; ascensor en Los Almendros; cualquier franquicia, número de póliza, importe de derrama o nombre de presidente distinto de los indicados para cada comunidad; derramas no indicadas.
7. Las actas de una misma comunidad siguen la misma plantilla (mismo orden del día y redacción parecida); los documentos del mismo tipo entre comunidades también se parecen, porque los redacta el mismo despacho. Esto es intencionado.
8. Personas: nombres inventados corrientes. En los casos, no valores ni compares a las personas que los gestionaron.

## C1 · carpeta `c1-olmo14` · Comunidad de Propietarios Calle Olmo 14 (24 viviendas, ascensor, azotea transitable, fachada principal a la calle)

| Fichero | Págs | Frases obligatorias (página) |
|---|---|---|
| `estatutos.txt` | 2 | p2: «Artículo 9. Queda prohibida la instalación de aparatos de aire acondicionado, antenas o toldos en la fachada principal sin acuerdo previo de la Junta de Propietarios.» |
| `poliza-seguro-2023.txt` | 3 | p1: «Póliza nº ATL-4471-2023» y «Seguros Atalaya» y «Vigencia: del 1 de enero de 2023 al 31 de diciembre de 2026.»; p2: «Daños por agua: cubiertos los daños causados por escapes de agua de conducciones comunitarias y privativas, con una franquicia de 300 euros por siniestro.»; p3, en la lista de exclusiones: «humedades producidas por condensación» |
| `contrato-ascensor.txt` | 2 | p1: «Ascensores Norte S.L.» y fecha 10 de enero de 2022; p1: «cuota mensual de 185 euros (IVA no incluido)»; p2: «plazo máximo de respuesta ante averías de 24 horas, incluidas las averías con personas atrapadas» |
| `anexo-1-contrato-ascensor.txt` | 1 | p1: «A partir del 1 de marzo de 2025 la cuota mensual pasa a ser de 210 euros (IVA no incluido).» y «En caso de personas atrapadas en la cabina, el plazo máximo de respuesta será de 4 horas.» |
| `contrato-limpieza.txt` | 2 | p1: «Limpiezas Brillo S.L.»; p1: «limpieza de zonas comunes tres días por semana»; p2: «importe mensual de 420 euros» |
| `acta-ordinaria-2024.txt` | 3 | p1: fecha «14 de marzo de 2024»; p2: «Se elige por unanimidad presidenta de la Comunidad a Dña. Marta Quiroga Ferrán, propietaria del 3º A.» |
| `acta-ordinaria-2025.txt` | 3 | p1: fecha «13 de marzo de 2025»; p2: «Se elige presidente de la Comunidad a D. Luis Ferrer Montoya, propietario del 1º B, con 15 votos a favor y 3 abstenciones.» |
| `acta-extraordinaria-2025-fachada.txt` | 2 | p1: fecha «12 de febrero de 2025»; p2: «Se aprueba una derrama extraordinaria de 48.000 euros para la rehabilitación de la fachada, pagadera en 12 cuotas mensuales a partir de abril de 2025.» |
| `caso-2024-017-filtracion-azotea.txt` | 2 | Ficha de caso. p1: «Caso 2024-017», apertura «3 de octubre de 2024», «filtración de agua desde la azotea al techo de la vivienda 4º B tras las lluvias»; p2, apartado Resolución: «Se dio parte a Seguros Atalaya con cargo a la póliza ATL-4471-2023; el perito valoró los daños y la aseguradora reparó la impermeabilización de la azotea.»; p2, apartado Resultado: «Cerrado el 20 de diciembre de 2024. Indemnización de 1.150 euros al propietario del 4º B. Sin nuevas filtraciones ni reclamaciones posteriores.» |
| `caso-2025-006-ruido-ascensor.txt` | 2 | Ficha de caso: ruido nocturno de la maquinaria del ascensor; resolución: aviso a Ascensores Norte S.L., que ajustó el motor; resultado: cerrado sin incidencias. |

## C2 · carpeta `c2-almendros` · Comunidad de Propietarios Residencial Los Almendros (60 viviendas adosadas, piscina, jardines, garaje común; NO tiene ascensor)

| Fichero | Págs | Frases obligatorias (página) |
|---|---|---|
| `estatutos.txt` | 3 | p2: «La piscina comunitaria permanecerá abierta del 15 de junio al 15 de septiembre, en horario de 11:00 a 21:00.»; p2: «Cada vivienda podrá invitar a un máximo de 4 personas por día a la piscina, que deberán ir acompañadas por un propietario.» |
| `poliza-seguro-2022.txt` | 3 | p1: «Póliza nº ATL-3982-2022», «Seguros Atalaya», «Vigencia: del 1 de mayo de 2022 al 30 de abril de 2024.»; p2: «Daños por agua: cubiertos los daños por escapes de agua de conducciones comunitarias, con una franquicia de 200 euros por siniestro.»; p3: «responsabilidad civil de la comunidad hasta un límite de 300.000 euros por siniestro» |
| `poliza-seguro-2024.txt` | 3 | p1: «Póliza nº ATL-4417-2024», «Seguros Atalaya», «Vigencia: del 1 de mayo de 2024 al 30 de abril de 2027.»; p2: «Daños por agua: cubiertos los daños por escapes de agua de conducciones comunitarias, incluida la localización y reparación de fugas, con una franquicia de 150 euros por siniestro.»; p3: «responsabilidad civil de la comunidad, incluida la derivada del uso de la piscina, hasta un límite de 300.000 euros por siniestro» |
| `contrato-mantenimiento-piscina.txt` | 2 | p1: «Aquaservicios del Sur S.L.», «importe de 2.900 euros por temporada»; p2: «La Comunidad declara disponer de un seguro de responsabilidad civil con un límite de 600.000 euros que cubre el uso de la piscina.» |
| `contrato-limpieza.txt` | 2 | p1: «Limpiezas Brillo S.L.»; p1: «limpieza de zonas comunes tres días por semana»; p2: «importe mensual de 980 euros» |
| `acta-ordinaria-2024.txt` | 3 | p1: fecha «18 de abril de 2024»; presidente reelegido: «D. Ramón Ibarra Soler» (p2). |
| `acta-ordinaria-2025.txt` | 3 | p1: fecha «24 de abril de 2025». **Frontera de página obligatoria:** el punto sobre videovigilancia empieza al final de la página 1 con «Se somete a votación la propuesta de instalar cámaras de videovigilancia en el garaje comunitario, con grabación durante 30 días,» y la página 2 empieza exactamente con «quedando aprobada la instalación de cuatro cámaras de videovigilancia en el garaje con 38 votos a favor, 9 en contra y 5 abstenciones.» p3: «Se acuerda ampliar la limpieza de zonas comunes a cinco días por semana a partir de junio de 2025.» |
| `acta-extraordinaria-2024-piscina.txt` | 2 | p1: fecha «20 de mayo de 2024»; p2: «Se aprueba una derrama extraordinaria de 22.500 euros para la reparación del vaso de la piscina, repartida en proporción a la cuota de participación de cada vivienda.» |
| `caso-2025-011-fuga-riego.txt` | 2 | Ficha de caso. p1: «Caso 2025-011», «fuga de agua en la red de riego de los jardines comunitarios»; p2 Resolución: «Se dio parte a Seguros Atalaya con cargo a la póliza ATL-4417-2024, que cubrió la localización de la fuga.»; p2 Resultado: «Cerrado el 2 de agosto de 2025; la comunidad asumió la franquicia de 150 euros.» |
| `caso-2024-022-ruido-piscina.txt` | 2 | Ficha de caso: quejas de vecinos por ruido en la piscina fuera de horario; resolución: carteles con el horario y aviso por escrito; resultado: cerrado. |

## C3 · carpeta `c3-puerto3` · Comunidad de Propietarios Avenida del Puerto 3 (18 viviendas y 2 locales comerciales en planta baja, ascensor, cubierta plana)

| Fichero | Págs | Frases obligatorias (página) |
|---|---|---|
| `estatutos.txt` | 2 | p2: «Artículo 14. Los locales comerciales de la planta baja no participarán en los gastos de conservación, reparación ni sustitución del ascensor.» |
| `poliza-seguro-2024.txt` | 3 | p1: «Póliza nº ATL-5120-2024», «Seguros Atalaya», «Vigencia: del 1 de febrero de 2024 al 31 de enero de 2027.»; p2: «Daños por agua: cubiertos los daños por escapes de agua de conducciones comunitarias, con una franquicia de 250 euros por siniestro.»; p3, exclusiones: «Quedan excluidos los daños por agua procedentes de tuberías y aparatos privativos de cada vivienda o local.» |
| `contrato-ascensor.txt` | 2 | p1: «Elevadores Bahía S.L.», «cuota mensual de 160 euros (IVA no incluido)»; p2: «plazo máximo de respuesta de 48 horas, que se reduce a 6 horas cuando haya personas atrapadas» |
| `contrato-limpieza.txt` | 2 | p1: «Limpiezas Brillo S.L.»; p1: «limpieza de zonas comunes dos días por semana»; p2: «importe mensual de 600 euros» |
| `acta-ordinaria-2024.txt` | 3 | p1: fecha «7 de marzo de 2024»; p2: presidenta reelegida «Dña. Celia Nuño Vidal». |
| `acta-ordinaria-2025.txt` | 3 | p1: fecha «6 de marzo de 2025». p2: **tabla obligatoria** del presupuesto 2025, en texto con barras verticales, exactamente con estas filas: `| Limpieza | 7.200 euros |`, `| Mantenimiento del ascensor | 1.920 euros |`, `| Seguro comunitario | 1.980 euros |`, `| Electricidad de zonas comunes | 2.400 euros |`, `| Fondo de reserva | 1.350 euros |`, `| Total | 14.850 euros |` (con una fila de cabecera `| Partida | Importe |`). |
| `acta-extraordinaria-2025-ascensor.txt` | 2 | p1: fecha «10 de junio de 2025»; p2: «Se aprueba una derrama extraordinaria de 36.000 euros para la sustitución del ascensor.» y «Conforme al artículo 14 de los estatutos, los dos locales comerciales quedan exentos de esta derrama, que se reparte entre las 18 viviendas.» |
| `caso-2025-004-filtracion-cubierta.txt` | 2 | Ficha de caso. p1: «Caso 2025-004», «filtración de agua desde la cubierta al techo de la vivienda 3º A»; p2 Resolución: «La comunidad encargó directamente la reparación a un albañil por 640 euros, sin dar parte al seguro.»; p2 Resultado: «Reabierto el 18 de julio de 2025 por una nueva filtración en el mismo punto. El propietario del 3º A presentó una reclamación por escrito a la comunidad.» |
| `caso-2025-009-humos-local.txt` | 2 | Ficha de caso: olores y humos del bar de uno de los locales; resolución: requerimiento por escrito al titular para revisar la salida de humos; resultado: en curso. |
