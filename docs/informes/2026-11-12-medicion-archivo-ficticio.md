# Informe: Odín medido con un archivo ficticio grande (encargo 12)

- Fecha: 2026-11-12. Autor: CTO 2. Encargo 12 del propietario, por el CEO. Es la medición de partida de la batería de respuestas (D-050).
- Odín medido: el de hoy, la fase 2a (`feature/identidad-pertenencias`, f6c5019), sin tocar su código. Rama de la medición: `feature/medicion-archivo`. Material, órdenes y datos en `experimentos/2026-11-12-archivo-ficticio/`.
- **Límite:** datos ficticios y una sola ejecución. Las cifras no son una promesa.

## 1. Resumen

- **Odín no llega al 90 % con Haiku:** acierta 146 de 216 preguntas, el **67,6 %**. Si además se exige que no haya citas inventadas ni datos sin respaldo, es el 43,1 %.
- **Sonnet, en una muestra de 30 preguntas** (3 por tipo), acierta 28, el **93,3 %**. Haiku, en esas mismas 30, acierta 21 (70 %).
- **La causa principal no es la búsqueda,** sino que la IA no puede filtrar por proyecto. 48 de los 74 fallos de Haiku vienen de ahí:
  - el MCP no le dice qué proyectos hay;
  - entre 60 comunidades casi iguales, la página correcta no sale entre los 10 primeros resultados sin filtro;
  - con el filtro del proyecto, sale entre los 10 primeros en los 48 casos.
  - Es la herramienta `listProjects` del bloque 6, y los datos la confirman como lo primero.
- **El aislamiento aguanta:**
  - 0 fugas en las 216 respuestas: ni otra empresa ni proyectos cerrados de los que el actor no es miembro;
  - 0 fichas pendientes o rechazadas citadas.
- **Las contradicciones son lo más débil** (37,5 %). La ficha que contradice un documento no nombra la comunidad, así que solo sale filtrando por proyecto, y sin ella la IA no ve que hay dos fuentes.

## 2. Qué se midió

### 2.1 Archivo

- **Documentos:** 1.236, con 2.700 páginas y 4.992.789 bytes.
  - 1.054 salen del generador del estudio (`escala.py 30`, semilla 7, sobre `corpus-limpio` y las 5 fichas de casos límite). Su comprobación de fugas salió limpia.
  - 182 son de ampliación por plantillas: pólizas renovadas, anexos, presupuestos, comunicaciones, normas internas y datos desactualizados.
- **Dos empresas del mismo grupo:**
  - «Administraciones Ribera»: 825 documentos y 61 proyectos, uno de ellos abierto a toda la empresa;
  - «Gestión Albar»: 411 documentos y 33 proyectos.
  - Los proyectos son cerrados por defecto (D-047).
- **Trabajadores:** 8. `t02` solo es miembro de 10 proyectos de Ribera.
- **Fichas:** 300.
  - 220 aprobadas, 50 pendientes y 30 rechazadas;
  - 20 de las aprobadas llevan un error a propósito.
- **Contradicciones apuntadas en `claves.json`:** 242, con su respuesta correcta.
  - anexo: 111;
  - versión: 47;
  - dos casos o documentos que no cuadran: 40;
  - dato desactualizado: 24;
  - ficha contra documento: 20.
- **Generado con scripts y plantillas,** sin IA, y determinista.
- **Dos revisiones** antes de medir, porque podían falsear el resultado:
  - **puntuador:** había 14 falsos avisos de «datos sin respaldo» en las respuestas del estudio, y se corrigieron;
  - **claves:** se revisaron las 216. Había 3 tipos de clave que también casaban con una respuesta errónea, y se corrigieron.

### 2.2 Batería

Son 216 preguntas. En 42 lo correcto es decir «no consta».

| Tipo | Preguntas |
|---|---|
| Versión (pólizas renovadas, anexos, datos desactualizados) | 46 |
| Vocabulario técnico frente a corriente | 34 |
| Dato concreto | 28 |
| Ficha | 25 |
| Sin fuente | 19 |
| Aislamiento | 17 |
| Contradicción | 16 |
| Casos parecidos | 13 |
| Identificador (póliza o caso) | 10 |
| Lista completa | 8 |

**Por origen:**
- 41 del estudio, sobre las comunidades base;
- 57 que repiten plantillas del estudio en las copias;
- 55 de la ampliación;
- 35 de fichas;
- 17 de aislamiento;
- 11 más sin fuente.

### 2.3 Cómo

- **Pila Docker propia y desechable,** `odin-medicion`, en los puertos 28080, 28088 y 28090. No se tocaron las pilas del propietario ni la de `make demo`.
- **Carga:**
  - la organización, por la API;
  - los documentos y las fichas, por SQL, como hace `SearchBatteryIT`, porque Odín guarda un `.txt` como una sola página.
- **Quién responde:**
  - la IA, con `claude -p` y la cuenta de Claude que ya se usa;
  - una ejecución por pregunta, con la clave de API de su actor;
  - solo con el MCP de Odín por HTTP y sus herramientas, sin otras herramientas, plugins ni memoria;
  - el mensaje solo lleva el papel, la pregunta y el formato de salida. Las reglas son las de la instrucción del MCP de hoy.
- **Puntuación:**
  - la hace el script del estudio, adaptado. Sigue dando las cifras del estudio sobre sus respuestas (30/30 y 6/6);
  - sin juez de IA.
- **Porcentaje del mínimo:** aciertos con cita más «no consta» bien dichos.

## 3. Resultados

### 3.1 Por tipo (Haiku, 216 preguntas)

| Tipo | Correctas | % |
|---|---|---|
| Identificador | 9/10 | 90,0 |
| Aislamiento | 15/17 | 88,2 |
| Vocabulario | 27/34 | 79,4 |
| Sin fuente | 14/19 | 73,7 |
| Dato concreto | 20/28 | 71,4 |
| Casos parecidos | 9/13 | 69,2 |
| Versión | 30/46 | 65,2 |
| Lista completa | 5/8 | 62,5 |
| Ficha | 11/25 | 44,0 |
| Contradicción | 6/16 | 37,5 |
| **Total** | **146/216** | **67,6** |

- **Por origen:**
  - estudio: 80,5 %;
  - copias: 80,7 %;
  - ampliación: 61,8 %;
  - fichas: 31,4 %;
  - aislamiento: 88,2 %;
  - sin fuente: 63,6 %.
- **Frente al estudio:** en las 32 preguntas que el estudio midió a escala, Haiku a través de Odín da 25/32 (78,1 %). En el estudio, con sus propias herramientas (Grep y Read sobre carpetas por comunidad), dio 31/32. De los 8 fallos de las 41 del estudio, 5 son por no poder filtrar por proyecto.

### 3.2 Cuentas aparte (Haiku)

| Cuenta | Número |
|---|---|
| «No consta» falsos: decía «no consta» y sí constaba | 35 |
| Citas no literales («inventada» para el script) | 36 de 374 |
| Respuestas con datos sin respaldo en lo citado | 62 |
| Página imprecisa: el dato está en el documento citado, pero no en la página citada | 27 |
| Sustituido presentado como vigente | 7 |
| Fugas de aislamiento | 0 |
| Falsos positivos (citas de documentos prohibidos) | 0 |

- **Las 36 citas no literales tienen texto real:** ninguna es inventada de verdad.
  - 23 unen trozos literales con «...»;
  - 7 alteran el texto o juntan campos que no van seguidos;
  - 6 de fichas meten el título, el autor y la fecha dentro del fragmento.
  - El script las cuenta como inventadas porque la cita tiene que ser literal. Si se aceptaran, el total subiría al 69,9 %. No se aceptan.
- **Los datos sin respaldo** casi siempre son datos de contexto que la IA vio en la búsqueda y no citó. Por ejemplo, la franquicia de la póliza antigua o la fecha de alta del documento, que el MCP da como `date` y la IA lee como fecha del documento.

### 3.3 Sonnet (muestra de 30 preguntas, 3 por tipo)

- **Resultado:**
  - 28/30 (93,3 %), con 66,7 % estricto;
  - 3 citas no literales de 65, 1 «no consta» falso y 0 fugas.
- **En las mismas 30:**
  - Haiku acierta 21;
  - Sonnet acierta 7 que Haiku falla;
  - ninguna al revés.
- **Por qué lo hace mejor:** insiste más en la búsqueda cuando lo primero que encuentra es de otra comunidad.
- Con 30 preguntas, la diferencia es orientativa.

## 4. Qué falla y por qué

Análisis de los 74 fallos de la primera puntuación. Para cada uno se repitieron las búsquedas contra la API, con el filtro de proyecto y sin él.

| Causa | Fallos | Qué es |
|---|---|---|
| Proyecto | 48 | Sin filtro, entre copias casi iguales, la página no sale entre los 10 primeros; con el filtro, sí. 20 de ellos son fichas, cuyo texto no nombra la comunidad |
| Lectura de la IA | 11 | Odín devuelve la página, pero la IA responde mal, dice «no consta» o no cita la página esperada |
| Ficha | 5 | La ficha no sale o la IA no la cita bien |
| Vigencia | 3 | Da el valor viejo o no dice que está sustituido |
| Búsqueda | 2 | Ni con el filtro sale la página entre los 10 primeros |
| Corpus | 1 | El generador del estudio cambia cada cifra por separado, y un documento se contradice a sí mismo. Además es causa secundaria en otros 6 |
| Clave y puntuador | 4 | Corregidos, con su motivo, en `resultados/2026-11-12-haiku/analisis.md` |

- **Contradicciones (37,5 %):**
  - 8 de los 10 fallos son de proyecto. La ficha que contradice al documento solo sale filtrando por la comunidad, así que la IA ve el documento y no la contradicción.
  - En las que sí ve las dos fuentes, suele enseñarlas, aunque la instrucción de hoy no se lo pide.
- **Versiones (65,2 %):**
  - 11 de los 16 fallos son de proyecto;
  - 2 son de vigencia;
  - el resto, de lectura, búsqueda o del puntuador.
  - Cuando la IA encuentra el documento nuevo, que dice «sustituye a…», solo 2 fallos son de vigencia.
  - Aun así, 7 respuestas de varios tipos presentan un sustituido como vigente.
- **Fichas (44 %):** 12 de los 14 fallos son de proyecto.

## 5. Qué cambia en los diseños de D-049

### 5.1 Bloque 6 (datos correctos medidos)

- **`listProjects` pasa a ser lo primero del bloque,** con la instrucción de filtrar por proyecto. La medición de partida ya está hecha (esta), así que el bloque empieza por ahí.
- **Cada resultado de búsqueda debe traer el nombre del proyecto, no solo su identificador.** Así la IA puede descartar lo que es de otra comunidad. Las fichas no nombran su comunidad en el texto, así que es la única pista. Es un campo más en la respuesta del MCP: `projectName`.
- **El campo `date` del MCP se renombra a `addedAt` y se describe como fecha de alta en Odín.** La IA lo toma por la fecha del documento.
- **Nuevas reglas en la instrucción:**
  - «copia cada fragmento literal por separado; no unas trozos con puntos suspensivos»;
  - «en una ficha, cita solo su texto».
  - La instrucción de fuente parcial sigue, junto con estas.
- **La batería de respuestas** usa este archivo y estas 216 preguntas. Se descarta el diseño anterior con los juegos pequeño y de escala del estudio (D-050: un solo esfuerzo).
- **El ejecutor debe guardar las consultas y las herramientas de cada respuesta.** Hoy guarda solo el nombre de las herramientas, y no se puede saber qué buscó la IA. El análisis de causas tuvo que reconstruirlo.
- **El mínimo se mide con Haiku,** que es el modelo que más falla. Sonnet se mide en muestra, como aquí.
- **Las incoherencias del generador del estudio se apuntan y se excluyen de las preguntas.** Cambia cada cifra por separado, y algún contrato dice «cinco días» y «martes y viernes» a la vez.

### 5.2 Bloque 7 (documentos sustituidos)

- **Sigue siendo necesario:** 7 respuestas presentan un sustituido como vigente.
- **La instrucción de «enseña las dos fuentes con su fecha»** solo sirve si la IA ve las dos fuentes. Va después de `listProjects`, no en su lugar.
- **Los anexos** (111 contradicciones en este archivo) quedan para la reconciliación, como decidió el CEO. Son el tipo más frecuente.

### 5.3 Bloque 2 (casos parecidos)

- **Lo de proyecto le afecta igual** (4 de los 4 fallos). `listProjects` le sirve tal cual.

## 6. Máquina, tiempo y cuota

- **Máquina:**
  - Mac15,11, Apple M3 Max, 36 GB, macOS 27.0.1, Docker 29.8.2;
  - la API, PostgreSQL y el MCP en Docker en la misma máquina.
- **Pila:** se levanta en 52 s (con imágenes en caché) y carga en 2,8 s.
- **Haiku, 216 preguntas:**
  - 14 min 16 s (de 02:19:34 a 02:33:50), 4 a la vez, unos 15 s por pregunta;
  - modelo `claude-haiku-5-5`;
  - 18,9 millones de tokens: 14,5 millones leídos de caché, 3,9 millones escritos en caché y 0,45 millones de salida;
  - coste notificado por Claude Code: 1,16 USD. Es el equivalente de la API: se pagó con la suscripción.
- **Sonnet, 30 preguntas:**
  - 2 min 47 s, 3 a la vez;
  - 2,06 millones de tokens;
  - coste notificado: 2,63 USD.
  - Una medición completa con Sonnet costaría unas 7 veces más que con Haiku. Es una estimación a partir de esta muestra.
- **Generación, revisiones y análisis:** con subagentes Sonnet. No se usó Opus para generar ni para responder.

## 7. Reproducir

Las órdenes están en `experimentos/2026-11-12-archivo-ficticio/`:
1. `generar/generar.py` y después `generar/comprobar.py`.
2. `pila/pila.sh up` y después `pila/cargar.py`.
3. `medir/ejecutar.py --modelo haiku --etiqueta <e>` y después `medir/puntuar.py --respuestas resultados/<e>/respuestas.jsonl`.

El detalle está en los `INFORME.md` de cada carpeta. Las respuestas, las puntuaciones y el análisis de causas están versionados en `resultados/`.
