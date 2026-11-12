# Odín · Fase 2, bloque 6: datos correctos medidos (búsqueda a escala y batería de respuestas)

- Fecha: 2026-11-11. Autor: CTO (segunda sesión).
- Decisión de negocio que lo origina: D-049, con su precisión del propietario (merge 1b8f8f5 en `feature/respuestas-cto`) y el mínimo que fijó el propietario: más del 90 % de respuestas correctas en los dos corpus. Se aplican también D-008, D-024, D-047 y D-048.
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
- se usan los modelos de Claude que ya se usan, sin servicios de pago nuevos.

**Mínimo del propietario (D-049):**
- se puede empezar a trabajar con más del 90 % de respuestas correctas, contando las que llevan cita y los «no consta» bien dichos;
- se mide en los dos corpus, el pequeño y el de 899 documentos;
- si se supera, la recuperación queda cerrada y lo medido pasa a ser el umbral de regresión, que no puede empeorar;
- si no se supera, la recuperación no se cierra, y el CTO manda al CEO el análisis de los fallos;
- las citas literales no cuentan para el porcentaje.

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
| Cada proyecto se llama con el nombre completo de su comunidad, el que da el generador (`names['full']`): «Calle Olmo 14», «Residencial Los Almendros», «Avenida del Puerto 3» y los de las copias | Es el nombre que usan las preguntas y el que vería un administrador. Un identificador técnico como nombre falsearía el efecto de `listProjects` | — |
| El modelo se fija por su identificador exacto, no por el alias. La medición de partida resuelve `sonnet` y `haiku` y guarda los identificadores en `thresholds.json`. Cambiar de modelo es una decisión que se registra, con una medición nueva | Los alias cambian de modelo con el tiempo, y los umbrales dejarían de ser comparables | — |

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

- **Una empresa con 93 proyectos,** uno por carpeta y con el nombre completo de la comunidad (apartado 3). Los proyectos son cerrados (D-047), y el actor es un trabajador miembro de los 93.
- **El proyecto de cada pregunta** se deduce como hoy en `SearchBatteryIT`, por el nombre corto de la comunidad base que aparece en la pregunta. El cargador compartido no puede cambiar los 27 de 30 de la batería pequeña.
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
  Las preguntas son B2, B3, B5 y F1 (las afectadas) y A1, C2, D1 y D4 (las de control). Cada una tiene en `contrafactual.json` uno de dos modos:
  - **`abstener_o_parcial`** (B2, B3 y F1): es correcta con «no consta» o con fuente parcial, y sin ninguna de sus frases `prohibidas`. En B2, las prohibidas son dar por buena la entrada de los cinco invitados o decir que no hay límite;
  - **`respuesta`** (B5 y las de control): se puntúa como en el juego pequeño, con sus `claves` y su cita esperada. B5 se responde con la ficha de caso, que sigue en el corpus, igual que en el estudio.
  - El constructor redacta las `prohibidas` de B3 y F1 como borrador, y el CTO las revisa antes de la medición de partida. Es un paso del plan.
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
  - una ejecución de `claude -p` con el identificador exacto del modelo (apartado 3);
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
  - **contrafactual:** según el modo de cada pregunta (apartado 6.1). En los dos modos, sin citas inventadas ni de otra comunidad.
- **Validación del puntuador:** con las respuestas que el estudio guardó en `respuestas/` y sus resultados en `resultados/`, el puntuador portado debe dar las mismas cifras en el juego pequeño:
  - Sonnet 30/30 y Haiku 29/30, las dos con 6/6 abstenciones;
  - los mismos estados de cita.
  - El estudio puntuó esas respuestas contra `corpus-con-ocr`. Por eso, esos 29 ficheros (unos 135 KB) se copian solo como datos de validación del puntuador, en `mcp/scripts/answer-battery/data/validation/`. Las baterías de Odín siguen usando `corpus-limpio`.
  - **El juego a escala no se puede revalidar:** sus respuestas citan carpetas anónimas de una ronda cuyo corpus no se guardó, y otra ronda se generó con el generador anterior a su corrección. Se dice en el informe.
  - Es una prueba de `make check`, sin IA. Se portan también los casos de `scripts/autotest.py`.

### 6.4 Lo que el script no ve

- **Una conclusión en palabras que no sea un dato** (por ejemplo, «podrían entrar») solo la detecta el script si está en la lista `prohibidas` de su pregunta. Así es como se vio el fallo de B2.
- **Por eso:** en cada medición, el CTO lee las 32 respuestas contrafactuales (8 preguntas por 2 modelos por 2 corpus) y anota en el informe cualquier conclusión sin respaldo que el script no haya marcado. Lo que encuentre se añade a `prohibidas`, y así el script la marca desde entonces.
- No es un juez de IA, y es la única parte que no hace un script. El límite se dice en el informe.

### 6.5 Mínimo, umbrales y ruido

- **Porcentaje de correctas (el mínimo de D-049):**
  - aciertos con cita más «no consta» bien dichos, sobre el total de preguntas del juego;
  - una pregunta con respuesta es un acierto si cumple las reglas del estudio (apartado 6.3): dato esperado, cita esperada, sin abstenerse, orden y sin falsos positivos;
  - una pregunta sin respuesta es un acierto si dice «no consta» sin citas inventadas;
  - las citas literales no entran en el porcentaje (D-049). Se cuentan aparte, abajo.
- **El mínimo, en preguntas:** más del 90 % es al menos 33 de 36 en el juego pequeño y al menos 29 de 32 en el de escala.
- **Cuentas aparte,** por juego y modelo:
  - citas inventadas;
  - citas en otra página;
  - datos sin respaldo;
  - fuentes de otra comunidad;
  - contrafactuales correctos;
  - sustituidos presentados como vigentes (bloque 7).
- **Ruido:** la IA no responde siempre igual.
  - Una pregunta falla si no es un acierto o si suma algo a una de las cuentas aparte.
  - Una pregunta que falla se repite dos veces más, y cuenta como fallo si falla en 2 de las 3. Se repite solo esa pregunta, no la batería.
- **Antes de cerrar la recuperación,** las mediciones sirven para diagnosticar: dan la partida y el efecto de cada cambio, y no bloquean.
- **Al cerrar la recuperación** (apartado 6.6), lo medido se guarda en `mcp/scripts/answer-battery/thresholds.json` y pasa a ser el umbral de regresión (D-049):
  - el porcentaje no puede bajar;
  - ninguna cuenta aparte puede empeorar.
- **Sin trinquete automático.** El umbral solo sube si dos mediciones seguidas lo superan, y el informe del bloque lo dice. Una sola medición buena puede ser suerte.

### 6.6 Cuándo se ejecuta

- **Medición de partida:** al empezar el bloque, con el Odín de ese momento, antes de cambiar el MCP.
- **Después de cada cambio del MCP de este bloque,** para saber qué efecto tiene cada uno.
- **Al cerrar cada bloque de la recuperación:** este, el 7 (documentos sustituidos) y el 2 (casos parecidos).
- **Después de la recuperación:** al cerrar cualquier bloque que cambie la instrucción del MCP, la búsqueda o lo que devuelven sus herramientas.
- **Condición para cerrar la recuperación (D-049), medida al cerrar el bloque 2:**
  - la batería de respuestas pasa del 90 % en el juego pequeño y en el de escala;
  - la batería a escala de búsqueda cumple su umbral, y la pequeña mantiene 27 de 30.
  - Si se cumple, la recuperación queda cerrada y se fijan los umbrales (apartado 6.5).
  - Si no se cumple, la recuperación no se cierra. El CTO manda al CEO el análisis de los fallos: qué preguntas fallan, con qué modelo y en qué corpus; si el fallo es de búsqueda, de instrucción o de la IA; y qué propone.

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
- El puntuador reproduce las cifras del estudio en el juego pequeño.
- La batería de respuestas está medida antes y después de los dos cambios del MCP, con el porcentaje de cada juego y modelo, las cuentas aparte y el efecto de cada cambio.
- Las respuestas contrafactuales están leídas por el CTO.
- El informe en `docs/informes/` da las cifras con la máquina, los modelos exactos, los tokens y la duración. Lleva su entrada en el registro técnico, con el siguiente número libre.
- Este bloque no cierra la recuperación: la cierran el 7 y el 2, con la condición del apartado 6.6. Si el bloque 6 ya pasa del 90 %, el informe lo dice, pero el umbral se fija al final.

## 11. Lo que queda al constructor

- El módulo o el `test-jar` del cargador compartido.
- Las opciones exactas de `claude -p` para el aislamiento, comprobadas con la sonda.
- El formato interno de `contrafactual.json`, `thresholds.json` y los resultados.
- La concurrencia del ejecutor.

## 12. Revisión del constructor (2026-11-11)

Un subagente leyó el diseño como si tuviera que planificarlo. Hizo 5 preguntas, todas técnicas, y el CTO las decidió. Ya están en el texto:
- el puntuador se valida solo con el juego pequeño, con `corpus-con-ocr` como dato de validación; el de escala no se puede revalidar (6.3);
- los proyectos se llaman con el nombre completo de la comunidad, y el proyecto de cada pregunta se deduce como en `SearchBatteryIT` (3 y 5.2);
- el ruido, qué es una pregunta que falla y el umbral sin trinquete automático (6.5), ajustados al mínimo del propietario;
- el contrafactual con dos modos, y B5 se puntúa como respuesta (6.1);
- el modelo, fijado por su identificador exacto (3 y 6.2).

## 13. Pendiente del CEO

- **El mínimo, ¿por modelo?** Recomendación del CTO: que pase del 90 % cada modelo (Sonnet y Haiku) en cada corpus, porque el trabajador puede usar cualquiera. En el estudio, Haiku dio 35 de 36 y 31 de 32 con sus propias herramientas.


## 14. Revisión tras la prueba grande (D-052, 2026-11-12)

La medición de partida ya está hecha: es la prueba grande del encargo 12 (informe `docs/informes/2026-11-12-medicion-archivo-ficticio.md`, rama `feature/medicion-archivo`, ca94774). Haiku dio 146 de 216 (67,6 %), y 48 de los 74 fallos vinieron de que la IA no podía filtrar por proyecto. D-052 valida estos cambios, que **mandan sobre los apartados 2 a 10** donde no cuadren con ellos:

- **La batería de respuestas es el archivo del encargo 12:** 1.236 documentos, 300 fichas, 242 contradicciones y 216 preguntas de 10 tipos, con su generador, su pila, su ejecutor y su puntuador en `experimentos/2026-11-12-archivo-ficticio/`.
  - Sustituye a los juegos pequeño, de escala y contrafactual del apartado 6.1, y al puntuador en TypeScript del 6.3.
  - El puntuador es el de Python del experimento, validado con las cifras del estudio. Se queda en Python: es una herramienta de medición, no código del producto.
  - Se trae a la rama con un merge de `feature/medicion-archivo`.
- **El mínimo del 90 % (D-049, D-050)** se mide en ese archivo con Haiku, el modelo que más falla. La medición completa con Haiku se puede repetir (unos 14 minutos). Sonnet, solo en muestra hasta el cierre; al cierre, completa.
- **Batería de búsqueda determinista en `make check`:** sobre el mismo archivo, en lugar de `SearchScaleBatteryIT` con el corpus de 899.
  - Usa las páginas esperadas de las preguntas que llevan cita, con el filtro del proyecto de la pregunta.
  - La primera medición fija el umbral, como en el apartado 5.3.
  - El cargador de Java es el mismo que el de `SearchBatteryIT`, compartido.
- **Cambios en el MCP, en este orden (D-052):**
  1. **`listProjects`** (apartado 7), con la instrucción de filtrar por proyecto.
  2. **El nombre del proyecto en cada resultado** (`projectName`) de `searchKnowledge`, `readPage` y `listDocuments`.
     - El MCP lo saca de los proyectos de `GET /v1/me`, sin cambiar la API.
     - Un proyecto borrado que el trabajador aún ve en su contenido sale como «Proyecto borrado».
  3. **`addedAt` en lugar de `date`** en lo que el MCP da a la IA, descrito como «fecha en que entró en Odín, no la del documento».
  4. **Reglas de cita literal** en la instrucción:
     - «Copia cada fragmento literal por separado; no unas trozos con puntos suspensivos.»
     - «De una ficha, cita solo su texto, sin el título, el autor ni la fecha.»
  5. **La instrucción de fuente parcial** (apartado 7).
- **El ejecutor guarda las consultas:** cada llamada a una herramienta, con sus argumentos y el número de resultados. Hace falta para separar los fallos de búsqueda de los de lectura.
- **Fallos graves, aparte en cada medición** (D-052): los «no consta» que sí constaban y los documentos sustituidos presentados como vigentes. Son los que más daño hacen al cliente.
- **Mediciones del bloque:**
  - una completa con Haiku después de los cambios 1 a 3, otra después del 4 y el 5;
  - la muestra de Sonnet, al cerrar el bloque.
- **Sin cambios:** el aislamiento de la IA, la regla de 2 de 3, el criterio de cierre de la recuperación (apartado 6.6) y que el bloque 6 no la cierra.
