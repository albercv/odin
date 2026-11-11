# Odín · Fase 2, bloque 6: datos correctos medidos (búsqueda a escala y batería de respuestas)

- Fecha: 2026-11-11. Autor: CTO (segunda sesión).
- Decisión de negocio que lo origina: D-049, con su precisión del propietario (merge 1b8f8f5 en `feature/respuestas-cto`). Se aplican también D-008, D-024, D-047 y D-048.
- Amplía `2026-11-09-api-central-design.md` («spec base») y se apoya en `2026-11-10-identidad-pertenencias-design.md` («spec 2a»). Lo que este documento no cambia sigue como está allí.
- Rama: `feature/fase-2-conocimiento`. Es la primera pieza del grupo «Recuperación» (índice de orden) y se ejecuta cuando lo diga el CEO.
- Fuente de los datos del estudio: rama `feature/planificacion-tecnica` del repositorio OCR (c169482), carpeta `docs/business-digital-mind/tecnico/pruebas-busqueda/` («carpeta del estudio»), y `2026-11-06-estudio-busqueda.md` («estudio»).

## 1. Por qué

- **Lo que pide negocio (D-049):** que la IA del trabajador componga una respuesta correcta a partir de datos, documentos y afirmaciones correctos. No se promete «siempre»: se garantiza que Odín no inventa, que cada afirmación se puede rastrear hasta su fuente y que hay un umbral medido que ningún cambio empeora.
- **Lo que se mide hoy:**
  - `SearchBatteryIT` mide si sale la página que responde, sobre el corpus pequeño del estudio: 29 documentos de 3 comunidades. Su umbral es 27 de 30. Las 6 preguntas sin respuesta se saltan.
  - Nada mide la respuesta final que compone una IA con las herramientas de Odín.
- **Lo que midió el estudio (T-007):**
  - Búsqueda a escala, con 899 documentos de 93 comunidades. La configuración que eligió Odín (palabras en español, filtro por comunidad y canal exacto de identificadores) dio el 92 %.
  - Respuestas de Sonnet y de Haiku puntuadas por un script, con 26 de 26 para Sonnet a escala. Pero esas IAs buscaban con sus propias herramientas (Grep y Read sobre los ficheros), no con Odín.
  - La prueba contrafactual quitaba documentos clave. En una pregunta, la IA sacó una conclusión que su fuente parcial no respaldaba (B2, los invitados a la piscina). El script no lo detectó, porque las citas eran literales: se vio leyendo la respuesta.
- **Un hueco que sale al diseñar:** el MCP no le dice a la IA qué proyectos hay. `GET /v1/me` los devuelve, pero `mcp/src/server.ts` solo usa las capacidades. La IA no puede filtrar por comunidad, y el estudio midió que a escala, sin filtro, la búsqueda baja al 23 %.

## 2. Qué se construye

Tres piezas, que se miden antes y después de cada cambio:

1. **Batería de búsqueda a escala:** `SearchScaleBatteryIT`, con los 899 documentos. Va en `make check`, como `SearchBatteryIT`.
2. **Batería de respuestas:** una IA real (Sonnet y Haiku) responde a través del MCP de Odín, y un script puntúa la respuesta. Se ejecuta con `make answer-battery`, fuera de `make check`.
3. **Dos cambios en el MCP que se miden con la batería de respuestas:**
   - la instrucción de fuente parcial que pide el CEO;
   - la herramienta `listProjects`, para que la IA pueda filtrar por proyecto.

**Orden:** primero las dos baterías y la medición de partida con el Odín de ese momento. Después los dos cambios del MCP, y se mide otra vez.

## 3. Decisiones

**De negocio que se aplican:** nunca inventa y cita (D-008, D-024); proyectos cerrados por defecto (D-047); medir antes de afirmar cifras.

**Del CEO (D-049 y su mensaje del 2026-11-11):**
- responden Sonnet y Haiku, como en el estudio, con el corpus pequeño y con el de 899 documentos;
- puntúa un script, no un juez de IA;
- se reutilizan las preguntas del estudio y su forma de puntuar;
- se incluye la prueba contrafactual;
- la primera medición fija el umbral, y cumplirlo es la condición para cerrar la recuperación;
- se usan los modelos de Claude que ya se usan, sin servicios de pago nuevos.

**Del CTO.** Cada decisión dice lo que cuesta si resulta errónea:

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| El corpus de 899 se genera una vez con el generador del estudio (`scripts/escala.py 30`, semilla 7) y se copia a Odín como ficheros de texto versionados | El corpus a escala no está en ningún repositorio: solo están los 29 documentos base y el generador. Copiar lo generado mantiene una sola fuente; portar el generador a Java podría producir otro corpus | Portar el generador |
| La base del corpus es `corpus-limpio`, la misma de `SearchBatteryIT` | Así la batería a escala mide solo el efecto de la escala. El efecto del OCR lo mide el bloque 1. No consta si el 899 del estudio salió de la base limpia o de la que tiene errores de OCR | Regenerar desde `corpus-con-ocr`: una orden |
| La batería a escala usa la misma cuenta que `SearchBatteryIT`: acierta la pregunta si una página con la frase esperada sale entre los 5 primeros resultados | Una sola forma de medir en Odín. El informe da además la cuenta del estudio, con medio acierto, para poder comparar | — |
| La IA responde con Claude Code sin interfaz (`claude -p`), con la cuenta de Claude que ya se usa | El estudio usó subagentes de Claude Code, con los mismos modelos y la misma cuenta. Una orden del sistema hace la medición repetible sin una sesión abierta. No necesita clave de API ni servicio nuevo | Subagentes en una sesión, como en el estudio |
| Cada pregunta se responde en una ejecución nueva, sin memoria de las anteriores | Es la regla del estudio («cada pregunta es independiente»), y así un fallo no arrastra a los siguientes | — |
| La IA solo ve las herramientas de Odín y su instrucción: sin hooks, plugins, memoria, `CLAUDE.md` ni otras herramientas del equipo. El ejecutor lo comprueba en cada ejecución | Se mide Odín, no la configuración de quien ejecuta la batería | — |
| El mensaje a la IA solo dice su papel, la pregunta y el formato de salida. Las reglas de cómo responder vienen solo de la instrucción del MCP | Es lo que recibe la IA de un trabajador de verdad. El estudio ponía las reglas en el mensaje; aquí se mide la instrucción de Odín | Añadir las reglas del estudio al mensaje, para comparar |
| El puntuador se porta de `scripts/puntuar.py` a TypeScript, dentro de `mcp/`. Para validarlo, debe reproducir exactamente las puntuaciones del estudio sobre sus respuestas guardadas | Odín no tiene Python, y `mcp/` ya tiene Node. Reproducir las cifras del estudio demuestra que se puntúa igual | Copiar el script en Python |
| Odín se levanta para la batería con el mismo cargador de corpus que usan las dos baterías de búsqueda, no con la subida del panel | `PlainTextReader` guarda un `.txt` como una sola página sin numerar. El corpus marca las páginas con `=== Página N ===`, y la cita es documento más página | Pasar el corpus a PDF y subirlo por la ingesta real |
| `listProjects` es una herramienta nueva del MCP que devuelve el identificador y el nombre de los proyectos que ve el trabajador. Lee `GET /v1/me`, que ya los trae | Sin ella, la IA no puede filtrar por comunidad. No cambia el backend ni lo que ve el trabajador | Poner los proyectos en la instrucción |

## 4. Fuera de alcance

- Un juez de IA. Lo que el script no detecta se dice en el apartado 6.4.
- Los 9 casos límite del estudio, que son de vocabulario técnico. Ya los usa la batería de casos parecidos del bloque 2.
- Medir con otras IAs (ChatGPT, Gemini). Se mide con los modelos de Claude, como en el estudio.
- Reconciliar contradicciones (D-049, «luego»).
- Medir el rendimiento en el equipo de un cliente.

## 5. Batería de búsqueda a escala

### 5.1 Corpus

- **Generación** (una vez, en la tarea de la batería):
  - `scripts/escala.py 30 <carpeta>` de la carpeta del estudio, en c169482;
  - semilla 7, con `BDM_CORPUS` apuntando a `corpus-limpio`;
  - Python del entorno aislado del repositorio OCR, en una carpeta temporal;
  - la comprobación de fugas del propio generador (`leaks()`) no debe encontrar nombres de las comunidades base en las copias.
- **Copia a Odín:** `api/odin-adapter-postgres/src/test/resources/search-battery-scale/`, con:
  - `corpus/`: 93 carpetas, una por comunidad, con 899 ficheros `.txt`;
  - `bateria_escala.json` y `reformulaciones.json` de la carpeta del estudio;
  - `threshold.txt`;
  - `ORIGEN.md`: commit de origen, orden exacta, versión de Python, número de ficheros, bytes totales y SHA-256 de un manifiesto con la suma de cada fichero.
- **Tamaño:** se estimaba en unos 4,2 MB sin haberlo medido. Se mide al generar y se pone en `ORIGEN.md`.
- **Todo ficticio:** el mundo del estudio es inventado («Todo es ficticio», `spec-corpus.md`).

### 5.2 Carga y búsqueda

- **Una empresa con 93 proyectos,** uno por carpeta y con el nombre de la comunidad. Los proyectos son cerrados (D-047), y el actor es un trabajador miembro de los 93.
- **Cargador compartido:** el de `SearchBatteryIT` se saca a una clase de apoyo de pruebas y lo usan las dos baterías de búsqueda y la de respuestas. No se copia.
- **Preguntas:**
  - las 26 de `bateria_escala.json` que llevan cita;
  - las 6 que no tienen respuesta se miden en la batería de respuestas, porque la búsqueda siempre devuelve algo.
- **Consulta:** igual que `SearchBatteryIT`. Se usan las tres reformulaciones de cada pregunta y el proyecto de la comunidad que nombra, con límite 5.
- **Acierto:** una página que contiene literalmente la frase esperada sale entre los 5 primeros resultados (`QuoteMatcher.containsLiteral`).

### 5.3 Umbral

- **Primera medición:** fija `threshold.txt` como porcentaje, igual que en la batería pequeña.
- **Antes de fijarlo, se compara con el estudio,** con su cuenta de medio acierto. El estudio da el 92 % en esa configuración. Si Odín queda por debajo, se busca la causa y se explica en el informe antes de fijar el umbral.
- **Trinquete:** si una medición posterior supera el umbral, el umbral sube en ese mismo cambio. La búsqueda es determinista, así que subirlo no da falsos fallos.
- **Duración:** se mide en la primera ejecución. Si añade más de 2 minutos a `make check`, sale a `make battery`, y la regla de cierre de cada tarea pasa a incluir `make battery`.

## 6. Batería de respuestas

### 6.1 Qué preguntas y qué corpus

| Juego | Corpus | Preguntas | Origen |
|---|---|---|---|
| Pequeño | `corpus-limpio`, 29 documentos en 3 proyectos | 36: 30 con respuesta y 6 sin ella (5 sin respuesta y 1 con premisa falsa) | `bateria_resuelta.json` |
| Escala | los 899 del apartado 5, en 93 proyectos | 32: 26 con respuesta y 6 sin ella | `bateria_escala.json` |
| Contrafactual pequeño | `corpus-limpio` sin 3 documentos | 8: 4 afectadas y 4 de control | estudio 4.6 y `respuestas/contrafactual-sonnet.json` |
| Contrafactual a escala | los 899 sin esos 3 documentos de las comunidades base | las mismas 8 | nuevo |

- **El contrafactual se reconstruye.** El estudio no guardó su corpus recortado ni su fichero de preguntas. Se sabe qué se quitó:
  - `c1-olmo14/estatutos.txt`;
  - `c2-almendros/estatutos.txt`;
  - `c3-puerto3/acta-ordinaria-2025.txt`.
  Las preguntas son B2, B3, B5 y F1 (las afectadas) y A1, C2, D1 y D4 (las de control). Lo que se espera de cada una va en `contrafactual.json`, y lo revisa el CTO antes de medir:
  - **B3 y F1:** «no consta» o fuente parcial;
  - **B2:** fuente parcial o «no consta», sin dar por buena la entrada de los invitados (apartado 6.4);
  - **B5:** se puede responder con la ficha de caso, que sigue en el corpus;
  - **las de control:** igual que en el juego pequeño.
- **El contrafactual a escala es nuevo y mide un riesgo propio de la escala.** Los estatutos de las copias siguen en el corpus. Si la IA cita los de otra comunidad, el puntuador lo marca como fuente de otra comunidad (apartado 6.3).
- **Ficheros en Odín:** `mcp/scripts/answer-battery/data/`, con:
  - las baterías;
  - `contrafactual.json`;
  - las respuestas y los resultados del estudio que sirven para validar el puntuador (apartado 6.3).
  El corpus no se duplica: se lee de los recursos de prueba de `odin-adapter-postgres`.

### 6.2 Quién responde y cómo

- **Odín para la batería:** `AnswerBatteryIT`, en las pruebas de `odin-bootstrap`. Va excluida de `make check`.
  1. Levanta PostgreSQL con Testcontainers y carga el corpus del juego con el cargador compartido.
  2. Arranca la API en un puerto local.
  3. Crea el trabajador, miembro de todos los proyectos, y su clave de API.
  4. Lanza el ejecutor de TypeScript con la URL, la clave y el juego, y espera a que termine.
  5. Falla si el ejecutor termina con un código distinto de 0.
  - El cargador escribe un mapa de identificador de documento a fichero del corpus. El puntuador cita por identificador, nunca por título.
- **El ejecutor** (`mcp/scripts/answer-battery/run.ts`) hace, para cada pregunta:
  - una ejecución de `claude -p` con el modelo pedido (`sonnet` o `haiku`);
  - como único servidor MCP, el de Odín compilado de `mcp/`, con la URL y la clave;
  - solo con las herramientas de Odín, sin sesión guardada y en una carpeta de trabajo vacía;
  - salida JSON validada con `--json-schema`.
- **Aislamiento de la IA:**
  - `--bare` no sirve, porque exige una clave de API y no lee la sesión de la cuenta. Las opciones exactas las fija el plan, con `--restricted`, `--strict-mcp-config` y la lista de herramientas.
  - En cada ejecución, el ejecutor comprueba en el evento de inicio que solo hay herramientas de Odín y ningún plugin. Si encuentra otra cosa, para.
  - La primera tarea comprueba además con una pregunta de sonda que no llegan instrucciones del equipo.
- **El mensaje a la IA:**
  - su papel: «Eres la IA de un trabajador de un despacho de administración de fincas. Responde con las herramientas de Odín.»;
  - la pregunta tal cual;
  - el formato de salida: `{ respuesta, no_lo_se, fuente_parcial, citas: [{ documentId, pagina, fragmento }] }`. Aquí `no_lo_se` es verdadero si la respuesta es que no consta, y `fuente_parcial` si dice que la fuente es parcial.
  - Nada más: las reglas son las del MCP.
- **Concurrencia:** varias preguntas a la vez, 4 por defecto. El plan la ajusta con lo que mida.
- **Lo que se guarda de cada ejecución:**
  - la respuesta;
  - el identificador exacto del modelo que dio Claude Code;
  - los tokens y la duración.
  Va en `mcp/scripts/answer-battery/results/<fecha>-<juego>-<modelo>.json`, que se versiona: es texto sobre datos ficticios y sirve de prueba de la medición.

### 6.3 Puntuación

- **El puntuador** es el del estudio portado a TypeScript (`mcp/scripts/answer-battery/score.ts`), con sus mismas reglas:
  - **cita:** cada cita (documento, página y fragmento) se clasifica:
    - `exacta` si el fragmento está en la página citada, tras normalizar minúsculas, tildes, espacios y separadores de miles;
    - `aproximada` si la similitud es de 0,85 o más (tolerancia de OCR);
    - `otra_pagina` si está en otra página del documento;
    - `inventada` si no está, si mide menos de 8 caracteres o si el documento no existe.
  - **dato esperado:** la respuesta contiene las `claves` (cada sublista son alternativas, y hacen falta todas las sublistas);
  - **cita esperada:** está el documento y la página de `citas_resueltas` (todas si `todas_las_citas`);
  - **abstención:** en una pregunta sin respuesta, es correcta si `no_lo_se` es verdadero y no hay ninguna cita inventada. En una pregunta con respuesta, decir «no consta» es una falsa abstención;
  - **orden y falsos positivos,** como en el estudio.
  - La cita se compara con el fichero original del corpus, no con lo que devuelve Odín.
- **Comprobaciones nuevas:**
  - **datos sin respaldo:** cada dato comprobable de la respuesta debe estar en el texto de alguna página citada o en la pregunta. Cuenta como dato comprobable una cifra de dos o más dígitos, un importe, un porcentaje, una fecha, una hora o un identificador del tipo de `ATL-4417-2024`. Los que no están se cuentan y se listan;
  - **fuente de otra comunidad:** una cita a un documento de otro proyecto distinto del de la pregunta, en el juego a escala;
  - **contrafactual:**
    - es correcta si la respuesta tiene `no_lo_se` o `fuente_parcial`;
    - no contiene ninguna frase de su lista `prohibidas`, que son las conclusiones que la fuente no respalda. Para B2: dar por buena la entrada de los cinco invitados o decir que no hay límite;
    - y no tiene citas inventadas ni de otra comunidad.
- **Validación del puntuador:** con las respuestas que el estudio guardó en `respuestas/` y sus resultados en `resultados/`, el puntuador portado debe dar las mismas cifras:
  - pequeño: Sonnet 30/30 y Haiku 29/30;
  - escala, en las dos rondas: Sonnet 26/26 y Haiku 25/26;
  - en todas, 6/6 abstenciones y los mismos estados de cita.
  Es una prueba de `make check`, sin IA. Se portan también los casos de `scripts/autotest.py`.

### 6.4 Lo que el script no ve

- **Una conclusión en palabras que no sea un dato** (por ejemplo, «podrían entrar») solo la detecta el script si está en la lista `prohibidas` de su pregunta. Así es como se vio el fallo de B2.
- **Por eso:** en cada medición, el CTO lee las 32 respuestas contrafactuales (8 preguntas por 2 modelos por 2 corpus) y anota en el informe cualquier conclusión sin respaldo que el script no haya marcado. Lo que encuentre se añade a `prohibidas`, y así el script la marca desde entonces.
- No es un juez de IA, y es la única parte que no hace un script. El límite se dice en el informe.

### 6.5 Umbrales y ruido

- **Por cada juego y modelo** se guardan en `mcp/scripts/answer-battery/thresholds.json`:
  - respuestas correctas;
  - abstenciones correctas;
  - falsas abstenciones;
  - citas inventadas;
  - datos sin respaldo;
  - fuentes de otra comunidad;
  - contrafactuales correctos.
- **La primera medición fija los umbrales.** Una medición no puede empeorar ninguna cuenta: ni menos aciertos, ni más citas inventadas, ni más datos sin respaldo.
- **Ruido:** la IA no responde siempre igual. Una pregunta que falla se repite dos veces más, y cuenta como fallo si falla en 2 de las 3. Se repite solo la pregunta que falla, no la batería.
- **Trinquete:** si una medición mejora una cuenta, el umbral se actualiza en ese cambio, y el informe lo dice.

### 6.6 Cuándo se ejecuta

- **Medición de partida:** al empezar el bloque, con el Odín de ese momento, antes de cambiar el MCP.
- **Al cerrar cada bloque de la recuperación:** este, el 7 (documentos sustituidos) y el 2 (casos parecidos).
- **Cuando cambie la instrucción del MCP, la búsqueda o lo que devuelven sus herramientas,** aunque sea en otro bloque.
- **Condición para cerrar la recuperación (D-049):**
  - la medición final de la batería de respuestas cumple todos los umbrales;
  - la batería a escala cumple el suyo;
  - la batería pequeña mantiene 27 de 30.

## 7. Cambios en el MCP

- **Instrucción de fuente parcial** (D-049), que se añade a `INSTRUCTIONS`: «Afirma solo lo que dicen las citas. Si la fuente es parcial, porque falta el documento que lo regula o el que tienes no lo dice todo, dilo y no completes la conclusión.»
- **`listProjects`:** devuelve `[{ projectId, name }]` de `GET /v1/me`. La instrucción gana: «Si la pregunta nombra un proyecto (una comunidad, un cliente, una obra), búscalo con listProjects y filtra la búsqueda con su projectId.»
  - Sale solo con `SEARCH_KNOWLEDGE`, como las otras herramientas de lectura.
  - Respeta lo que ya devuelve `GET /v1/me`: los proyectos que ve el trabajador (D-047).
- **Se mide igual con los dos cambios juntos o por separado:** el plan aplica cada uno con su medición, para saber qué efecto tiene cada uno.

## 8. Pruebas

Aquí se dice qué se prueba. Los nombres exactos y el módulo de cada prueba los fija el plan.

- **`SearchScaleBatteryIT`:**
  - carga los 899 documentos en 93 proyectos cerrados con el actor miembro;
  - mide;
  - escribe el umbral si no existe y falla si queda por debajo.
- **Cargador compartido:** `SearchBatteryIT` sigue dando 27 de 30 con él.
- **Puntuador** (TypeScript, en `make check`):
  - los casos portados de `autotest.py`;
  - la reproducción de las cifras del estudio (apartado 6.3);
  - pruebas de las comprobaciones nuevas, cada una con un caso que falla y otro que pasa: datos sin respaldo, fuente de otra comunidad y contrafactual con frase prohibida.
- **Ejecutor:**
  - la construcción de la orden de `claude -p` y el parar si el evento de inicio trae herramientas ajenas, con un `claude` falso;
  - la regla de 2 de 3.
- **`listProjects`:** la herramienta y la instrucción, en las pruebas de `mcp/`. Un trabajador que no es miembro de un proyecto cerrado no lo ve en la lista.
- **`AnswerBatteryIT`:** una ejecución corta con un `claude` falso que devuelve respuestas fijas. Prueba la cadena completa (carga, API, MCP y puntuación) sin gastar la cuenta.

## 9. Coste y tiempo

- **Ejecuciones por medición completa:** son 84 preguntas por 2 modelos, es decir, 168 ejecuciones (36 + 32 + 8 + 8).
- **Tokens:** el estudio midió entre 11.800 y 18.600 tokens por pregunta, según modelo y corpus. Con esas cifras, una medición completa estaría entre 2 y 3 millones de tokens del plan de Claude que ya se usa. Es una estimación: la primera medición da la cifra real y va al informe.
- **Duración:** sin medir. La primera medición la da.
- **Sin servicios nuevos:** ni clave de API ni cuenta nueva. Si algo de la batería llegara a necesitarlas, se pregunta antes al CEO.

## 10. Criterio de cierre del bloque

- `make check` y `make demo` terminan con 0.
- La batería a escala está medida, con su umbral fijado y explicada su diferencia con el estudio.
- El puntuador reproduce las cifras del estudio.
- La batería de respuestas está medida antes y después de los dos cambios del MCP, con sus umbrales y el efecto de cada cambio.
- Las respuestas contrafactuales están leídas por el CTO.
- El informe en `docs/informes/` da las cifras con la máquina, los modelos exactos, los tokens y la duración. Lleva su entrada en el registro técnico, con el siguiente número libre.

## 11. Lo que queda al constructor

- El módulo o el `test-jar` del cargador compartido.
- Las opciones exactas de `claude -p` para el aislamiento, comprobadas con la sonda.
- El formato interno de `contrafactual.json`, `thresholds.json` y los resultados.
- La concurrencia del ejecutor.
