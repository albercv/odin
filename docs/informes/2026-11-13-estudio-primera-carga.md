# Odín · Estudio de la primera carga

- Fecha: 2026-11-13. Autor: CTO. Encargo del propietario, por el CEO («Primera carga», en el apartado «Pendiente» de `decisiones.md`).
- **Es un estudio:** no construye nada.
- **Medidas:** en `experimentos/2026-11-13-primera-carga/`, sobre el archivo ficticio, solo con modelos locales y sin ninguna API de pago:
  - diseño: `DISENO.md`;
  - duplicados y escaneos: `parte_a/INFORME.md`;
  - clasificación: `parte_b/INFORME.md`.
- **Equipo de todas las medidas:** Apple M3 Max, 36 GB.
- **Rama:** `feature/estudio-primera-carga`, que sale de `develop`.
- **Lo que decide el propietario** va aparte, en el apartado 8.

## 0. En pocas líneas

- **Se puede hacer en local, sin que salga nada del equipo y sin inventar.** Todo lo que Odín asigna es una propuesta que una persona confirma por grupos.
- **En el archivo ficticio, unas reglas sencillas con la lista de proyectos del cliente proponen bien:**
  - el proyecto de 810 de 825 documentos, sin un error;
  - el tipo, la ficha de caso y su número de todos;
  - en 6 segundos.
  - **Es un techo, no una previsión:** el archivo ficticio nombra la comunidad en la cabecera de cada documento, y eso no está garantizado en un archivo real.
- **Para lo que las reglas no resuelven,** un modelo local (`qwen3.5:9b`) acierta 14 de 15 propuestas, en 1,3 s por documento.
  - Como clasificador principal es peor que las reglas: 96 de 100 frente a 99 de 100.
  - Así que solo entra para lo que queda.
- **Duplicados:**
  - las copias exactas y los escaneos de documentos digitales se encuentran todos;
  - de las versiones nuevas se encuentran 38 de 46, y se proponen como sustitución, nunca como duplicado.
  - La clave es separar por comunidad: solo por semejanza, el 99 % de las parejas parecidas son la misma plantilla en otra comunidad.
- **El tiempo lo pone el OCR:** 4,3 s por página escaneada. Lo demás tarda segundos.
- **Recomendación:** la opción B (reglas con la lista del cliente, y el modelo local para lo que queda), con una bandeja de confirmación por grupos en el panel.
- **Seis decisiones del propietario** en el apartado 8.

## 1. Qué se pide y qué hay hoy

**Petición del propietario:**
- al instalar Odín en un cliente nuevo, procesar todo su archivo de una vez y de forma fácil;
- el cliente deja sus documentos en una carpeta, y los escaneados llegan a la misma carpeta a través del OCR;
- Odín propone a qué proyecto y a qué caso pertenece cada documento y cómo se relacionan;
- evita duplicados.

**Lo que hay hoy en Odín:**

| Pieza | Hoy |
|---|---|
| Subida | El administrador sube desde el panel, varios ficheros a la vez, eligiendo él el proyecto y si es un caso |
| Duplicado exacto | Ya se evita: el mismo contenido (SHA-256) en la misma empresa no entra dos veces, y la API responde `duplicate: true` |
| Escaneos | El lector Unlimited-OCR 2, en local por el puente; sin lector, esperan |
| Temas | El etiquetado local (Ollama), de noche (D-031) |
| Casos | Un documento se marca como caso a mano. El expediente (qué documentos forman un caso) lo diseña el CTO 2 en el encargo 13 |
| Versiones | El administrador marca a mano qué documento sustituye a otro (D-050) |
| Proyecto | Lo elige siempre una persona. No hay propuestas |

**De dónde puede venir el archivo** (*sin confirmar hasta las entrevistas*). Dato de mercado del CEO (`estudios/2026-11-13-programas-gestion-fincas.md`):
- ningún programa de gestión de fincas publica una API documentada;
- algunos exportan informes en Excel, PDF o TXT;
- el complemento de Outlook de Gesfincas asocia correos a incidencias.

Por eso una parte del archivo del cliente puede llegar como exportaciones de su programa de gestión, y no solo como documentos sueltos:
- **La lista de comunidades,** exportada. Es la lista de proyectos que necesitan las opciones A y B (decisión D6).
- **La lista de incidencias, con su número.** Daría los números de caso que unen cada documento con su expediente.
- **Informes con datos de muchas comunidades a la vez,** como un listado de recibos. No son de un proyecto. Se tratan como documentos de toda la empresa o se dejan fuera, y no se trocean.
- **Correos guardados con su referencia de incidencia.**

El estudio no mide esto, porque el archivo ficticio no tiene exportaciones. Se diseña cuando las entrevistas digan qué exportan los despachos y en qué formato.

## 2. Recorrido propuesto, común a todas las opciones

1. **Entrega:** el cliente deja su archivo en una carpeta. Cómo llega a Odín está en el apartado 6.
2. **Lectura:** el texto digital se lee directamente y los escaneos pasan por el OCR local. Todo queda **«en revisión»**: el documento existe en Odín, pero sin proyecto confirmado, y ningún trabajador lo ve (D1).
3. **Duplicados y versiones** (apartado 5):
   - la copia exacta no entra;
   - el escaneo de un documento digital se propone como «el mismo documento»;
   - una versión nueva se propone como sustitución (D-050).
4. **Propuestas de clasificación:**
   - el proyecto, o «de toda la empresa»;
   - el tipo de documento;
   - si es una ficha de caso y su número;
   - a qué expedientes podría pertenecer (encargo 13).
   - Cada propuesta lleva su confianza y su motivo: el texto que la justifica.
5. **Revisión por una persona** (apartado 4). Al confirmarla, el documento pasa a su proyecto y lo ven quienes ven ese proyecto. Los enlaces a expedientes se crean con el mismo caso de uso del encargo 13, con esa persona como autora.
6. **Después:** el etiquetado por tema (de noche) y la búsqueda, como cualquier documento.

Nada se asigna por suposición: hasta que alguien confirma, la propuesta no cambia lo que ve nadie.

## 3. Opciones

Cifras sobre los 825 documentos de Administraciones Ribera (61 proyectos), texto digital, en el M3 Max. Son un techo, por lo dicho en el apartado 0.

| | A · Reglas con la lista del cliente | **B · A y un modelo local para lo que queda** | C · A y una IA externa con extractos para lo que queda |
|---|---|---|---|
| Cómo propone el proyecto | Busca en el texto los nombres de la lista de proyectos, con sus variantes. El más largo gana: «Calle Olmo 24 (bloque 2)» frente a «Calle Olmo 24» | Igual que A, y lo que queda sin proyecto lo propone `qwen3.5:9b` (Q4_K_M) en el equipo de la oficina, con la lista y los primeros 2.000 caracteres | Igual que A, y lo que queda lo propone una IA externa con los primeros caracteres del documento |
| Qué sale del equipo | Nada | Nada | Extractos de documentos sin que haya una pregunta de un trabajador. Choca con D-016 y D-030: es pregunta de negocio |
| Proyecto con confianza alta | 810 de 825, todos bien | 810, todos bien | — |
| Lo que queda | 15: 9 normas, que van bien como «de toda la empresa», y 6 procedimientos, sin resolver | 15 propuestas, 14 bien. Falla una norma propuesta como «Procedimientos generales» | Sin medir: no se usa API de pago |
| Tipo de documento, ficha y número de caso | 825 de 825, 825 de 825 y 239 de 239 | igual | — |
| Tiempo | 5,8 s (3 ejecuciones) | 5,8 s + 16,7 s (1,29 s por documento; 1 ejecución) | — |
| Memoria | despreciable | el modelo ocupa 5,5 GB en la GPU según `ollama ps` (RSS de 7,2 a 10,6 GB) | — |
| Para una persona, una a una | 15 | 15, ya con propuesta | — |
| Coste de construir | El más bajo | A más una llamada al Ollama que ya usa el etiquetado | A más un adaptador externo y su control de lo que sale |

- **El modelo no sustituye a las reglas.** En una muestra de 100 documentos, el modelo acierta 96 y las reglas 99. Sus fallos son confundir «bloque 2» con la comunidad gemela.
- **Es estable:** la misma respuesta en 30 de 30 documentos en dos pasadas, con temperatura 0.
- **Recomendación: B.**
  - Las reglas hacen casi todo y explican su motivo, porque citan el nombre que encontraron.
  - El modelo local da una propuesta para lo poco que queda, sin que nada salga del equipo.
  - C solo se plantearía si en un archivo real lo local se queda corto (D4).

**Sin lista de proyectos** (D6), las reglas descubren los candidatos en el texto («Comunidad:», «COMUNIDAD DE PROPIETARIOS…»):
- salen 60 de las 61 comunidades, sin ninguna de más;
- falta «Procedimientos generales», que no es una comunidad;
- el administrador confirmaría la lista antes de clasificar.

## 4. Quién confirma y cómo, con poco esfuerzo

- **Quién:** el administrador de la empresa, desde el panel. El trabajador no entra en el panel (D-033).
- **Cómo:** una bandeja «Primera carga» con las propuestas agrupadas por proyecto y por confianza.
  - **Confianza alta,** cuando el nombre del proyecto aparece y no hay otro candidato: se confirma un grupo entero con un clic. Antes se ve la lista, con el título y la frase que lo justifica.
  - **Confianza baja o sin proyecto:** una a una, con la propuesta del modelo y el texto en que se apoya. La persona elige el proyecto, «de toda la empresa» o «no lo sé», y lo que no sabe se queda en revisión.
  - **Duplicados y versiones:** en la misma bandeja, en pareja, con las diferencias señaladas (fechas, importes y números).
  - **Expedientes:** se proponen los enlaces con el número de caso o el identificador que los une, y se confirman por caso, con el caso de uso del encargo 13.
- **Esfuerzo en el archivo ficticio** (825 documentos):
  - 60 confirmaciones de grupo, una por comunidad, que cubren 810 documentos;
  - 15 documentos uno a uno;
  - 67 parejas que revisar: 46 propuestas de versión y 21 de escaneo. Las 40 copias exactas no se revisan (apartado 5).
  - En un archivo real, el grupo de «una a una» será mayor: cuánto, lo dirá la primera carga real o las entrevistas.

## 5. Duplicados y versiones

Medido en una carpeta plana de 885 ficheros:
- 815 documentos digitales de Ribera, dos tercios con nombre neutro;
- 40 copias exactas con otro nombre;
- 30 escaneos en PDF (55 páginas) leídos por el OCR. De ellos, 20 tienen su original digital en la carpeta y 10 no.

Con tejas de 5 palabras, un umbral de semejanza de 0,8 y una ejecución:

| Caso | Precisión | Cobertura | Qué hace Odín | Quién decide |
|---|---|---|---|---|
| Copia exacta (mismo SHA-256) | 1,00 | 1,00 (40 de 40) | No entra. Ya es así hoy | Nadie: es seguro |
| Escaneo de un documento digital | 1,00 | 1,00 (21 de 21) | Propone «el mismo documento» | El administrador (D3) |
| Versión nueva (póliza renovada) | 0,83 | 0,83 (38 de 46) | Propone «sustituye a» (D-050), nunca duplicado | El administrador |
| Plantilla parecida de otra comunidad | — | — | Nada: no es un duplicado | — |

- **La regla que lo hace posible es separar por comunidad.**
  - Solo por semejanza, con Jaccard de 0,8 o más hay 10.750 parejas, y solo 83 son relaciones verdaderas (precisión del 0,8 %): es la misma plantilla en otra comunidad.
  - Los importes no sirven para separarlas, porque también cambian entre versiones.
  - Con la lista de proyectos, la comunidad sale en 878 de 885 ficheros. Los 7 que no salen van a revisión.
- **Los 8 «falsos positivos» de versión** son pólizas de 2022 y de 2024 de la misma comunidad.
  - La verdad del archivo solo anota la sustitución por la de 2025.
  - Comprobado en Las Encinas 2: la de 2022 vence el 30 de abril de 2024 y la de 2024 empieza el 1 de mayo, así que es una versión real que la verdad no anota.
- **Las 8 versiones no halladas** son renovaciones con el texto reescrito (Jaccard de 0,02 a 0,10). Solo se enlazan si el texto nuevo cita el número de la póliza anterior («sustituye a la póliza nº…»), y esa señal está en las reglas propuestas.
- **Coste:** 8,0 s para los 885 ficheros, con un índice MinHash hecho solo con la biblioteca estándar.
  - El índice encontró todas las parejas por encima de 0,5 que da la comparación de todas con todas, que tardó 11,5 s.
  - Con archivos mucho mayores, el índice es lo que evita comparar todo con todo.
- **Límite:** los escaneos se fabricaron limpios. Un escaneo real peor baja la semejanza, y el umbral para escaneos habrá que volver a medirlo con escaneos reales.

## 6. Cómo llega la carpeta a Odín (D-076)

- **La página de subida del encargo 13** (D-076): enlace de 15 minutos y solo desde la red local.
  - **Sirve para:** lotes medianos y los documentos que llegan después.
  - **Para el archivo entero:**
    - subir miles de ficheros desde el navegador de una vez depende de que la página aguante cortes y reanude;
    - y si la subida dura más de 15 minutos, el enlace tiene que valer para empezar la sesión de subida, no para cada fichero.
  - Sin medir: no se ha probado subir una carpeta entera por el navegador.
- **Alternativa: una orden de importación en el equipo de la oficina,** que lanza quien instala, como las órdenes de `make` de la instalación.
  - Lee la carpeta directamente del disco del servidor, sin pasar por la red.
  - Se puede reanudar: lo ya subido se reconoce por su SHA-256.
  - Usa los mismos casos de uso que la subida del panel.
- **Recomendación:** la orden de importación para la primera carga y la página para lo de después. Si el propietario quiere que el propio cliente haga la primera carga sin el instalador, la página necesita subida reanudable de carpetas (D5).

## 7. Medidas

Todas en el Apple M3 Max de 36 GB, sobre el archivo ficticio. Detalle en los informes de cada parte.

| Medida | Valor | Modelo | Ejecuciones |
|---|---|---|---|
| OCR de escaneos | 4,3 s por página de media (de 2,1 a 6,4); 55 páginas en 238 s; carga del modelo 2,4 s | Unlimited-OCR 2, 8 bits (MLX) | 1 |
| Memoria del OCR | 7,2 GB de pico en MLX | igual | 1 |
| Calidad del OCR en escaneos limpios | semejanza de palabras con el original de 0,999 de media (mínimo 0,994) | igual | 1 |
| Clasificación por reglas (825 documentos) | 5,8 s; proyecto 810 de 810 con confianza alta; tipo 825 de 825; ficha y número de caso 825 de 825 y 239 de 239 | sin modelo | 3 de tiempo, 1 de acierto |
| Reglas sobre los 30 escaneos | proyecto 28 de 30 (los mismos 2 procedimientos que en digital); tipo, ficha y número 30 de 30 | sin modelo | 1 |
| Modelo local, lo que queda | 14 de 15; 1,29 s por documento | `qwen3.5:9b` Q4_K_M, temperatura 0, contexto 4.096 | 1 |
| Modelo local, muestra | 96 de 100 (las reglas, 99 de 100) | igual | 1 |
| Estabilidad del modelo | 30 de 30 iguales en dos pasadas | igual | 2 |
| Memoria del modelo | 5,5 GB en la GPU (`ollama ps`); RSS de 7,2 a 10,6 GB | igual | 1 |
| Duplicados (885 ficheros) | 8,0 s; copias 1,00 y 1,00; escaneos 1,00 y 1,00; versiones 0,83 y 0,83 (precisión y cobertura) | sin modelo | 1 |

- **Cálculo para un archivo real,** hecho con lo medido; no es una medida.
  - Las reglas, el modelo sobre lo que queda y los duplicados tardan segundos o minutos.
  - El OCR manda: por ejemplo, 6.000 páginas escaneadas a 4,3 s son unas 7 horas, una noche.
  - El OCR y el modelo no conviene tenerlos cargados a la vez en un equipo pequeño: juntos pasan de 12 GB, y la máquina de referencia del cliente es la de T-010.
- **Con qué no se ha medido:**
  - un archivo real;
  - escaneos malos (fotocopias, arrugas, manuscritos);
  - exportaciones del programa de gestión;
  - documentos sin nombre de comunidad;
  - el modelo sobre los 825 y sobre los escaneos;
  - el enlace con expedientes (apartado 9).
  - Además, durante la medición del modelo el OCR pudo estar usando la GPU, así que sus segundos pueden ser algo peores que con el equipo libre.

## 8. Lo que decide el propietario

| # | Decisión | Recomendación del CTO |
|---|---|---|
| D1 | ¿Los documentos sin confirmar se ocultan a los trabajadores? | Sí. Un documento mal asignado a otro proyecto sería una fuga entre proyectos (D-047) |
| D2 | ¿Confirmación automática por encima de una confianza, o siempre una persona? | Siempre una persona, por grupos: en el archivo ficticio son 60 clics para 810 documentos. El 100 % de acierto es un techo de un archivo regular, así que lo automático no se plantea hasta medirlo en una primera carga real |
| D3 | El escaneo de un documento digital: ¿se guardan los dos o solo uno? | Guardar el digital como principal y el escaneo enlazado a él, porque puede llevar firma o sello. Lo confirma el administrador en la bandeja |
| D4 | Si lo local no basta: ¿extractos a la IA externa para clasificar? | No. Lo local da la talla en lo medido (D-016, D-030). Se vuelve a preguntar solo si una carga real deja demasiados documentos para revisar uno a uno |
| D5 | ¿Quién hace la primera carga: el instalador con una orden, o el cliente desde la página (D-076)? | El instalador, con la orden. La página, para lo de después. Que el cliente lo haga solo pide subida reanudable de carpetas |
| D6 | ¿De dónde sale la lista de proyectos: del programa de gestión del cliente o la descubre Odín? | La del programa de gestión, si se puede exportar; algunos exportan en Excel, PDF o TXT, *sin confirmar hasta las entrevistas*. Si no, la descubre Odín (60 de 61 en el archivo ficticio) y la confirma el administrador |

## 9. Dependencias

- **El enlace entre documento y expediente es el del encargo 13** (CTO 2).
  - La primera carga no crea otro: guarda sus propuestas aparte y, al confirmarlas, llama a su caso de uso de añadir un documento al expediente, con la persona que confirma como actora.
  - Pedido al CTO 2 el 2026-11-13, sin respuesta todavía.
  - **Sin medir:** el archivo de hoy no cita números de caso fuera de la ficha del caso. Se medirá con el archivo ampliado con expedientes del encargo 13.
  - Las reglas ya extraen el número de cada ficha (239 de 239), que es la mitad del enlace.
- **Las entrevistas:**
  - cómo guardan hoy los despachos su archivo (carpetas o no);
  - qué exporta su programa de gestión;
  - cuántos documentos tiene un expediente;
  - cómo son sus escaneos.

## 10. Estado

- **Hecho:** el experimento (partes A y B, revisadas por el CTO) y este estudio.
- **Revisión del CTO sobre lo que entregaron los subagentes:**
  - las reglas solo leen el texto, nunca la ruta ni el nombre de fichero, que delatan el proyecto;
  - las 16 pruebas de la parte B y las 15 de la parte A pasan;
  - la explicación de los falsos positivos de versión está comprobada en una comunidad.
- **Pendiente** (no bloquea las decisiones):
  - medir el enlace con expedientes, cuando exista el archivo ampliado del encargo 13;
  - repetir con un archivo real y con escaneos reales, con permiso del propietario y en su equipo.
