# Odín · Calidad de las pruebas y de la recuperación (diseño)

- Fecha: 2026-11-12. Autor: CTO 3. Encargo: D-059 y peticiones del propietario del mismo día.
- Estado: **validado por el CEO** (D-060, 2026-11-12). Sus respuestas están en el apartado 13.
- Rama: `feature/calidad-recuperacion`, que sale de `feature/fase-2-conocimiento` (883e707).
- **Investigación de apoyo:** `docs/informes/2026-11-12-investigacion-corpus-preguntas-multiagente.md` (corpus públicos, baterías de evaluación, búsqueda multiagente y jueces).
- Solo planifica. No se ha lanzado ninguna medición: las cifras de partida salen de resultados ya guardados, vueltos a leer con scripts locales y sin cuota de IA.

## 1. Para qué

**La preocupación del propietario.** El archivo es sintético y las pruebas están sesgadas. Puede que estemos aprendiendo a aprobar el examen y no a buscar mejor.

**Lo que pide:**

- ampliar el archivo con documentos reales:
  - escaneados con OCR malo;
  - documentación antigua y contradictoria;
  - documentos con errores;
- ampliar las preguntas, en número y en tipos;
- **un banco de pruebas fácil de levantar,** con:
  - varios agentes Haiku que buscan con enfoques distintos;
  - varios jueces que deciden si la respuesta es buena;
  - agentes que se validan entre ellos;
  - para probar combinaciones y ajustar el equilibrio entre calidad y coste.
- **Para qué sirve el banco** (propietario): para encontrar una estrategia que se pueda llevar a Odín. Así que vale para las dos cosas: para medir y como candidata para el producto.

**Lo que ya pedía D-059:** batería de reserva, varias ejecuciones, escaneados, preguntas de administradores, otras IAs, revisión del puntuador y métricas aparte del 90 %. Y en la recuperación: datos sin respaldo, «no consta» con rastro, listas, etiquetas de más, reordenador y vectores.

**Éxito.** Dos cosas:

1. **Una cifra de calidad en la que se pueda confiar,** medida:
   - con documentos y preguntas que no son nuestros o que nadie usó para ajustar;
   - en varias ejecuciones, con su rango;
   - con cada fallo grave aparte.
2. **Una estrategia de búsqueda que gana a la de hoy por encima de la variación,** con su coste y su tiempo por respuesta medidos, lista para decidir si entra en Odín.

**No entra:**

- las contradicciones que no explica ningún documento (D-049);
- datos reales de clientes: aquí se usan solo documentos públicos, y el piloto con un archivo real queda para después de la validación legal (D-022);
- construir en Odín la búsqueda multiagente. Este diseño la mide y prepara la decisión; construirla sería otro diseño, si el CEO lo decide.

**Sería un fracaso aunque funcionase:**

- ganar en el banco gastando más tokens sin comparar con un solo agente que gaste lo mismo;
- jueces que dan por buena cualquier respuesta;
- ajustar contra la batería de reserva;
- gastar más cuota de la que hay;
- cualquier registro que permita juzgar a una persona (D-005).

## 2. Lo que dicen los resultados guardados

Análisis de las mediciones de Haiku guardadas, sin llamar a ninguna IA.

**Variación entre ejecuciones:**

- de `haiku-d054` a `haiku-procesado`, con cambios pequeños entre ellas, mejoran 9 preguntas y empeoran 6;
- en las tres últimas mediciones, 189 aciertan siempre, 4 fallan siempre (Q147, Q149, Q153 y Q185) y 23 cambian;
- **la mayoría de los fallos son inestables.** Una sola ejecución no basta para saber si un cambio mejora.
- **Intervalo:** el intervalo de confianza del 95 % de 201 de 216 va del 88,9 % al 95,8 %. Hoy no se puede afirmar con confianza que se supera el 90 %.

**«Limpia del todo»: mezcla errores del puntuador con defectos reales.** En `procesado`, 83 preguntas tienen algún «dato sin respaldo», y 78 de ellas son correctas. Los 154 datos marcados se reparten así (clasificación aproximada, por coincidencia de texto):

| Dónde aparece el dato | Datos |
|---|---|
| En el título o la fecha de entrada del documento citado | 35 |
| En otro documento del mismo proyecto, sin citarlo | 55 |
| En una ficha | 8 |
| En ningún documento con ese formato | 56 |

En ese último grupo hay fechas reescritas («1/09/2022» por «1 de septiembre de 2022») y cálculos.

**«No consta» falsos (9 en `procesado`):**

- 4 de los 9 nunca filtraron por proyecto;
- todas hicieron de 3 a 4 búsquedas antes de rendirse;
- el registro guarda cuántos resultados dio cada búsqueda, pero no cuáles.

**Listas:** 4 de 8 en una ejecución y 8 de 8 en otra. Con 8 preguntas, la cifra no dice nada.

## 3. Archivo ampliado

Son tres capas que se cargan juntas en la misma pila. Todas usan el formato de hoy (`manifiesto.json`, `claves.json`, `bateria.json`).

### 3.1 Ficticio

- Es el archivo de hoy: 1.236 documentos y 300 fichas, que se queda como está.
- Se le añade lo que faltaba (3.3): correos, hojas de cálculo pasadas a texto, documentos a medias y actas con erratas. Todo generado por programa, con otra semilla.

### 3.2 Público y real

No existe ningún corpus público de documentos de comunidades de propietarios. Se usan documentos públicos reales del mismo mundo. El primer lote:

| Fuente | Documentos | Qué prueba |
|---|---|---|
| BOE consolidado (API de datos abiertos) | Todas las versiones de la Ley 49/1960 de Propiedad Horizontal, de la Ley 29/1994 de Arrendamientos Urbanos y del Real Decreto 88/2013 (ascensores) | Documentos antiguos y sustituidos con respuesta conocida: qué versión estaba en vigor en cada fecha. Las normas que modifican dicen expresamente qué cambian, como pide D-054 |
| Ordenanzas fiscales de 2 o 3 municipios, de 3 a 4 años seguidos (boletines provinciales) | Unas 20 versiones | Versiones anuales y cifras que cambian |
| Gazeta de Madrid y BOE antiguo (escaneos) | Unas 30 páginas | OCR malo de verdad |
| Condiciones generales de seguros de hogar y de comunidades, si su licencia lo permite | De 3 a 5 pólizas con varias ediciones | El tipo de documento de una administración de fincas |
| Contratos públicos de mantenimiento de ascensores y de limpieza, con sus modificados (PLACSP), si su licencia lo permite y no tienen datos de particulares | De 5 a 10 expedientes | Contratos y anexos reales |

- **Dónde van en Odín:**
  - la normativa, sin proyecto: es general y la ve toda la empresa, como pasaría de verdad;
  - las ordenanzas, en un proyecto por municipio;
  - los contratos y las pólizas, en proyectos propios.
- **Cómo se obtienen:** `banco/archivos/publico/descargar.py`, con una lista fija de URL, la huella SHA-256 y la licencia de cada documento.
  - Un documento entra en git solo si su licencia permite redistribuirlo; si no, se guarda en una carpeta local fuera de git. La lista, con la fuente y la licencia de cada documento, se versiona siempre (D-060).
  - **Descargarlos usa la red,** así que se lanza solo con permiso, como con D-057.
- **Antes de usar una fuente,** se lee su licencia en el original (la investigación solo vio fragmentos). Se descarta cualquier documento con datos de particulares.
- **Las preguntas y las claves de este archivo** se hacen como dice 4.2.

### 3.3 Alterado a propósito

Errores metidos con respuesta conocida, sobre documentos de las capas 3.1 y 3.2. Cada alteración queda apuntada en `claves.json` con lo que se espera.

| Alteración | Qué se espera de la respuesta |
|---|---|
| **Cifra cambiada (contrafactual):** una copia de un documento con una cifra distinta, que se carga en lugar del original | La respuesta sigue al texto, no a lo que el modelo «sabe». Demuestra que lee |
| **Duplicado contradictorio sin marcar:** dos documentos con cifras distintas y ninguno dice que cambia al otro | Enseña los dos y no elige (D-054) |
| **Corrección de errores:** un documento que corrige expresamente una cifra de otro | Da la corregida y cita los dos |
| **Documento a medias:** truncado o sin la página que importa | Dice que la fuente es parcial |
| **Fecha equivocada en el texto** | Da la fecha tal cual, con su cita, sin «arreglarla» |
| **Identificador con dígitos mal leídos** («25O» por «250») | Lo encuentra si el canal exacto lo tolera; si no, se mide como pérdida |

### 3.4 Escaneados

- **Sintéticos:**
  - unos 20 documentos (unas 40 páginas) que respondan al menos a 30 preguntas;
  - se pasan a PDF y se degradan con `scan()` del repositorio OCR (`scripts/make_samples.py`), en dos niveles;
  - se leen con el puente del lector, que ya está probado en este Mac (D-058, `make serve` en el repositorio OCR);
  - se cargan como páginas `unlimited-ocr-2` en una variante del archivo;
  - **medición por parejas:** las mismas preguntas con el texto limpio y con el escaneado.
  - Cifras: la pérdida por OCR, en puntos, y el CER del texto leído (`cer_wer`).
- **Dependencia del repositorio OCR** (decisión del CTO):
  - la preparación es un paso aparte, `make banco-escanear`, que se lanza una vez por variante;
  - usa el checkout del repositorio OCR de la ruta `OCR_REPO` (por defecto, `../OCR`) con su propio entorno;
  - arranca el puente solo durante ese paso y lo para al acabar;
  - el puente y el jurado local de Ollama no se cargan a la vez. Juntos caben (unos 14 GB, D-058), pero el equipo ya estaba usando memoria de intercambio.
- **Reales antiguos:** las páginas de la Gazeta (3.2). No tienen transcripción de referencia: un subagente transcribe las 30 páginas y otro revisa la transcripción. Las diferencias las decide una persona.
- **Reales nuevos:** el propietario imprime unos 10 documentos ficticios y los escanea con un escáner y con el móvil.

## 4. Preguntas ampliadas

### 4.1 Tipos

**Los 10 tipos de hoy:**

- dato concreto;
- identificador;
- vocabulario;
- lista completa;
- casos parecidos;
- sin fuente;
- versión;
- contradicción;
- ficha;
- aislamiento.

**Tipos nuevos** (los ejemplos están en la investigación):

| Tipo (`tipo`) | Qué prueba |
|---|---|
| `falso_supuesto` | La pregunta da por hecho algo que no pasó: «no consta» |
| `negacion` | «Qué no cubre», «qué no se aprobó» |
| `vigencia` | Qué valía en una fecha concreta |
| `comparacion` | Dos valores de documentos distintos |
| `calculo` | Que el cálculo se diga como cálculo y se cite cada cifra |
| `varios_saltos` | La respuesta necesita dos documentos |
| `ambiguedad` | Dos ascensores, dos portales: preguntar o dar los dos |
| `respuesta_parcial` | Dar lo que consta y decir lo que falta |
| `casi_negativo` | Lo mismo, pero en otro portal o en otro año: «no consta» |
| `fecha_implicita` | «Qué cuota pagamos ahora», cuando solo consta la de 2022 |
| `errores_en_pregunta` | Faltas, letras por dígitos |
| `tabla_o_escaneo` | El dato está en una tabla o en una página escaneada |

**Tamaño de la batería de desarrollo:**

- al menos 20 preguntas de cada tipo, viejo o nuevo, salvo casos parecidos y ficha, que se quedan como están;
- **en total, unas 550:** las 216 de hoy, más los tipos nuevos y más las de las capas 3.2 y 3.3.

### 4.2 Cómo se hacen las preguntas y sus claves

1. **Generadas por programa,** en las capas 3.1 y 3.3, como hoy: la clave se calcula del texto.
2. **Redactadas por agentes que se validan entre ellos,** en la capa 3.2:
   - **el redactor** (Haiku) lee un documento y propone la pregunta, la respuesta y el ancla literal;
   - **el programa** comprueba que el ancla está literal en esa página. Si no está, la pregunta se descarta;
   - **el comprobador** (Haiku, con otra instrucción y sin ver la respuesta del redactor) responde solo con esa página;
   - si los dos coinciden, la pregunta entra;
   - si no coinciden, la decide una persona: la sesión del CEO en modo autónomo, y el propietario solo si es de negocio. Si nadie la decide, se descarta;
   - **canal** (decisión del CTO):
     - los desacuerdos se escriben en `banco/desacuerdos/<fecha>.jsonl`, con la pregunta, las dos respuestas y la página;
     - el CTO le manda la ruta al CEO por mensaje;
     - el CEO escribe sus decisiones en `<fecha>.decisiones.jsonl`;
     - los desacuerdos de preguntas de la reserva van en `estado/reserva/`. Los decide el CEO, que no ajusta nada, y el CTO no los abre;
   - **el redactor no ve nunca la instrucción del MCP ni los fallos de la batería.**
3. **Escritas por administradores reales,** en las entrevistas pendientes:
   - cada uno recibe 3 comunidades del archivo ficticio y escribe de 15 a 20 preguntas;
   - las claves se hacen como en el punto 2;
   - el administrador confirma la respuesta;
   - no se guarda quién escribió cada pregunta (D-005).
   - Herramienta: `importar_preguntas.py`, que pasa un CSV al formato de la batería.

### 4.3 Batería de reserva

- **Qué es** (tamaño: decisión del CTO):
  - unas 200 preguntas;
  - al menos 15 de lista, de versión, de dato concreto y de vigencia;
  - al menos 8 de cada uno de los otros tipos;
  - 4 de casos parecidos y 4 de ficha;
  - sobre documentos que no usa la batería de desarrollo: comunidades nuevas de la capa 3.1 y documentos de la capa 3.2 que se separan para la reserva;
  - con la mitad de las preguntas de los administradores, cuando las haya.
- **Cómo se protege:**
  - **Rama aparte:** vive en una rama huérfana, `reserva/bateria`, que no se mezcla nunca. El banco la lee con `git show`.
  - **Solo resultados agregados:** de una medición con la reserva solo se versiona el resumen. El detalle queda en `estado/reserva/`, fuera de git.
  - **Registro de usos:** `USOS.md`, en esa rama, con la fecha, el commit medido, el resultado y quién la lanzó.
  - **Regla en el `CLAUDE.md` de Odín:** quien ajusta instrucciones, búsqueda, puntuador o banco no la abre.
  - **El banco se niega a usarla** en un barrido o en una muestra. Solo la usa en el modo de cierre (5.5).
  - **Reserva quemada:** la reserva se quema cuando sirve para decidir un cambio, o si alguien mira su detalle. Entonces pasa a desarrollo y se genera otra (D-060).
  - **Límite:** la protección es de procedimiento, no técnica. La rama aparte y el registro hacen visible si se rompe.

## 5. Banco de pruebas multiagente

### 5.1 Qué es y cómo se levanta

- **Dónde:** carpeta `banco/` en la raíz de Odín.
  - Python 3 con la biblioteca estándar y `tomllib` para la configuración; nada que instalar.
  - Reutiliza lo que ya hay en `experimentos/2026-11-12-archivo-ficticio/`:
    - `pila.sh` y `cargar.py` para la pila y la carga;
    - el aislamiento de `ejecutar.py` (`claude -p` con solo el MCP de Odín);
    - `puntuar.py` para puntuar;
    - `fake_claude.py` para las pruebas sin cuota.
- **Órdenes:**

```
make banco-arriba                          # pila desechable odin-banco: API 48080, panel 48088, MCP 48090
make banco-cargar ARCHIVOS=ficticio,publico,alterado[,escaneado]
make banco CONFIG=banco/configs/<nombre>.toml       # un experimento
make banco-barrido CONFIG=banco/configs/<barrido>.toml
make banco-informe RESULTADOS=banco/resultados/<carpeta>
make banco-abajo
make banco-test                            # pruebas del banco con la IA simulada, sin cuota
```

- **Puertos:** nunca los de la instalación del propietario ni los de otras pilas (8080, 8088, 8090, 18080, 28080 y 38080, con sus vecinos).
- **Resultados:** `banco/resultados/<fecha>-<nombre>/`. Se versionan como hoy, salvo los de la reserva.

### 5.2 Buscadores y enfoques

- **Qué es un buscador:** un agente Haiku aislado, como el ejecutor de hoy, con las herramientas de lectura de Odín y la clave de API de Odín del actor de la pregunta. Usa la suscripción, como el ejecutor de hoy, no una clave de pago de Anthropic.
- A su mensaje se añade un **enfoque**, que es un fichero de texto en `banco/enfoques/`:

| Enfoque (`approach`) | Qué hace |
|---|---|
| `main` | Nada más que el papel, como hoy. Sirve de control |
| `topic` | Piensa qué tipo de documento o qué tema lo diría, lista esos documentos y lee |
| `synonyms` | Busca con palabras técnicas, corrientes y con variantes de escritura |
| `filterFirst` | Fija primero el proyecto y el tema, y busca dentro |
| `documentFirst` | Localiza el documento candidato y lo lee entero antes de responder |
| `skeptic` | Busca versiones más nuevas, sustituciones y lo que contradiga lo encontrado |

- **Qué devuelve cada buscador:**
  - el JSON de hoy (`respuesta`, `no_lo_se` y `citas`);
  - sus `consultas`, que ahora incluyen el `documentId` y la página de cada resultado, sin el texto. Así se sabe qué vio cada buscador.

### 5.3 Combinadores

| Combinador (`combiner`) | Qué hace | ¿Gasta IA? |
|---|---|---|
| `single` | Un solo buscador. Es la referencia | No |
| `majorityVote` | Agrupa las respuestas por la clave que dan y gana la más repetida; si empatan, «no consta» con rastro | No |
| `pageFusion` | Junta las páginas citadas y vistas por todos con RRF, y un redactor escribe la respuesta solo con las 5 primeras | Sí, el redactor |
| `composer` | Un redactor lee las respuestas y las citas de todos y escribe la final, citando solo páginas que comprueba con `verifyCitation` | Sí |
| `judgeSelect` | Los jueces eligen la mejor de las respuestas | Sí, los jueces |

`majorityVote` agrupa por los datos de las respuestas, no por su redacción. Cómo agruparlas queda a criterio de quien lo construya.

### 5.4 Jueces y su calibración

- **Qué recibe un juez:**
  - la pregunta y la respuesta final;
  - el texto literal de las páginas citadas, que el banco lee por la API;
  - **no busca nada, no ve la clave y no sabe qué configuración produjo la respuesta.**
- **Qué devuelve:** `{"correcta": bool, "respaldada": bool, "datosSinRespaldo": [...], "motivo": str}`.
- **Rúbricas:** ficheros en `banco/rubricas/`. Por ejemplo:
  - `strict`: cada dato tiene que estar en una página citada;
  - `answerOnly`: si responde a lo que se pregunta;
  - `sourceRules`: las reglas de sustituidos y de fuentes que no cuadran.
- **Modelos:**
  - Haiku;
  - **el modelo local que ya usa el etiquetado (`qwen3.5:9b`, en Ollama):** es de otra familia, así que reduce la preferencia de Haiku por sus propias respuestas, y no gasta cuota;
  - otros proveedores cuando haya cuentas (5.8).
- **Cómo votan** (`aggregation`):
  - `majority`;
  - `unanimous`;
  - `atLeast:k` (al menos k jueces).
  - El orden del material se baraja en cada juez.
- **Calibración contra el script.** En las preguntas con clave, el script es la referencia (D-050).
  - **Se calibra por capa** (decisión del CTO), porque la inferencia con predicciones supone que las preguntas etiquetadas son de la misma población que las que se puntúan.
  - En la capa pública, las etiquetadas son las preguntas que pasaron la validación cruzada.
  - En un archivo sin ninguna clave, se etiquetan a mano entre 50 y 150 preguntas al azar. Hasta entonces, la cifra del jurado en ese archivo se da como «sin calibrar».
- De cada juez y de cada jurado se mide:
  - el acuerdo con el script (kappa);
  - las **aceptaciones falsas:** el juez da por buena una respuesta que el script da por mala. Es lo más peligroso;
  - los rechazos falsos.
- **Uso donde no hay clave.** Un jurado solo puntúa preguntas sin clave (las de la capa 3.2 que no pasaron la validación cruzada, o las de un piloto) si:
  - sus aceptaciones falsas no pasan del 3 % en las preguntas con clave;
  - y su kappa es de al menos 0,8.
- **Corrección de la cifra.** La cifra del jurado se corrige con inferencia con predicciones (PPI, como ARES), usando las preguntas con clave como etiquetas. Así se da con su intervalo.
- **Validación entre buscadores.** Se mide si el acuerdo entre buscadores predice el acierto. Si lo predice, es una señal de confianza que Odín podría usar: «confianza baja, revisa la fuente».

### 5.5 Configuración, barridos y modo de cierre

**Ejemplo de configuración (`banco/configs/ejemplo.toml`):**

```toml
name = "veinte-buscadores"
archives = ["ficticio", "publico", "alterado"]
battery = "desarrollo"            # "reserva" solo con mode = "closing"
mode = "explore"                  # explore | closing
sample = { size = 40, strata = "tipo", seed = 7 }
repetitions = 1
budgetUsd = 6.0                   # tope de coste notificado; el banco para al llegar
concurrency = 4

[[searchers]]
approach = "main"
count = 10
model = "haiku"

[[searchers]]
approach = "topic"
count = 5
model = "haiku"

[[searchers]]
approach = "synonyms"
count = 5
model = "haiku"

[combiner]
kind = "composer"
model = "haiku"

[[judges]]
rubric = "strict"
count = 3
model = "haiku"

[[judges]]
rubric = "strict"
count = 2
model = "ollama:qwen3.5:9b"

[judging]
aggregation = "majority"
```

**Agrupar y submuestrear: así se prueban muchas combinaciones con poca cuota.**

- Un barrido lanza una sola vez el máximo de buscadores (por ejemplo 20) sobre la muestra. Cada ejecución se guarda en caché, con la pregunta, el enfoque, el modelo, el número de réplica y el commit de Odín.
- Las combinaciones más pequeñas (1, 3, 5 o 10 buscadores, y cualquier mezcla de enfoques) se calculan **por submuestreo de ese grupo,** sin volver a llamar a la IA. Solo se llama si el combinador la usa.
- Igual con los jueces: se juzga una vez con el jurado más grande, y se submuestrea para los jurados pequeños.
- **Las réplicas de `main` dan gratis la variación entre ejecuciones del agente único.**
- **El resultado** son curvas de acierto según el número de buscadores, la mezcla y el número de jueces, cada una con su coste. Ahí se busca el equilibrio.

**Coste** (decisión del CTO):

- **Qué se guarda:** de cada llamada, los tokens por clase (entrada, salida, caché leída y caché escrita) y el coste que notifica `claude -p`.
- **El tope `budgetUsd`** se aplica sobre ese coste notificado.
- **El coste en el producto** se calcula multiplicando los tokens por los precios de `banco/precios.toml`.
  - Ese fichero se rellena con los precios oficiales de cada proveedor cuando el CEO lo pida.
  - Hasta entonces, el coste se da en tokens y en coste notificado, sin inventar precios.

**Comparación justa** (decisión del CTO):

- `single` con `main` se ejecuta con varios topes de turnos (`--max-turns` 10, 20 y 40). Así sale una curva de acierto según los tokens medios por respuesta.
- Una combinación gana con el mismo gasto si queda por encima de esa curva, con sus mismos tokens medios por respuesta, por más que la variación medida.
- La investigación avisa de que buena parte de la ganancia de varios agentes viene de gastar más tokens.

**Modo de cierre (`closing`):**

- la batería completa, 3 repeticiones, la reserva incluida;
- sin submuestreo;
- solo con las configuraciones finalistas;
- lo lanza el CTO que cierra una fase, y queda apuntado en `USOS.md`.

### 5.6 Qué mide cada experimento

En el informe (`informe.md` y `resumen.json`):

**Acierto:**

- el del script en las preguntas con clave, en total y por tipo;
- el porcentaje del mínimo y la cifra «limpia», según 6.1;
- con la media, el mínimo y el máximo entre repeticiones, y el intervalo.

**Fallos graves:**

- fugas;
- citas inventadas;
- «no consta» falsos;
- sustituidos dados por vigentes;
- datos `noSource`.

**Por enfoque:**

- **recuperación:** en cuántas preguntas vio la página correcta algún buscador con ese enfoque, aunque luego no la usara;
- su acierto propio.

**Jueces:** el acuerdo con el script, las aceptaciones y los rechazos falsos, el acuerdo entre jueces y la cifra corregida con PPI.

**Coste y tiempo,** tal como serían en el producto:

- tokens por respuesta;
- coste notificado por respuesta;
- **latencia por respuesta:** con los buscadores en paralelo, el más lento de ellos, más el combinador y los jueces;
- percentiles 50 y 95.

### 5.7 De estrategia a Odín

Cada estrategia del banco declara cómo llegaría a Odín. Su coste en el producto depende de eso:

| Cómo llega | Ejemplo | Quién paga la IA | Decisión |
|---|---|---|---|
| **Instrucciones y herramientas** para la IA del trabajador | Un enfoque que gana, convertido en reglas de la instrucción del MCP | El trabajador, como hoy | CTO, medida en el banco |
| **Odín sin IA** | Que `searchKnowledge` fusione varias consultas con RRF; un diccionario de sinónimos del sector; las pistas de 7.1 | Nadie | CTO, medida en el banco |
| **Odín con su propia IA** | Una herramienta `deepSearch` que lanza N buscadores en el servidor; una herramienta `checkAnswer` que pasa la respuesta del trabajador por los jueces antes de darla | El motor externo de Odín por API (D-016), o un modelo local | **CEO:** coste por respuesta, quién lo paga (pendiente), latencia y qué fragmentos salen al proveedor de Odín |

**Regla para que una estrategia pase a Odín** (propuesta):

- gana a `single` con el mismo gasto, por encima de la variación medida, en desarrollo y en reserva;
- no empeora ningún fallo grave;
- su coste y su latencia por respuesta caben en lo que fije el CEO.

**Límite.** En el producto, Odín no controla cuántos agentes lanza la IA del trabajador. Una estrategia de varios agentes solo llega tal cual si la ejecuta el propio Odín (`deepSearch`).

### 5.8 Otras IAs en el banco

- **Ahora, con Claude:** un experimento con el MCP sin `instructions`. No todos los clientes se las pasan al modelo, y así se mide cuánto depende la calidad de ellas.
- **Con cuentas de OpenAI o Google:**
  - buscadores y jueces de esas familias, con un adaptador que conecta en local el MCP de Odín con la API del proveedor;
  - el MCP no se expone a internet;
  - el proveedor solo recibe los fragmentos leídos, como con Claude (D-016).
- **Producto con conector (ChatGPT, Gemini):** una muestra de 30 preguntas a mano, cuando lo admitan. Exponer el MCP de prueba hacia fuera lo decide el CEO (D-019).
- **El modelo mínimo recomendado** (D-052) se fija con la regla de 6.2.

## 6. Medición fiable

### 6.1 Puntuador recalibrado y revisado

**Datos sin respaldo, por clases:**

| Clase | Ejemplo | Defecto |
|---|---|---|
| `metadata` | El año del título o la fecha de entrada que da Odín | No |
| `reformatted` | «1/09/2022» frente a «1 de septiembre de 2022»; «2.706,40» frente a «2706,40»; «€» frente a «euros» | No |
| `derived` | La suma o la diferencia de cifras citadas | No, si la respuesta dice que es un cálculo |
| `uncitedSource` | El dato está en un resultado que la IA vio o en una página que leyó, y no lo cita | Sí |
| `noSource` | El dato no está en nada que la IA pudiera ver | Sí, grave |

- **Nueva cifra «limpia»:** correcta y sin datos `uncitedSource` ni `noSource`. La cifra estricta de hoy se sigue dando.
- **La regla de «correcta» no cambia,** así que el porcentaje del 90 % no se mueve.
- **Mediciones antiguas:** solo saben qué páginas abrió la IA con `readPage`, no qué resultados vio. Ahí `uncitedSource` y `noSource` solo se separan para lo leído; el resto va a una clase propia, `unknownExposure`. Desde el cambio del ejecutor (5.2) se separan siempre.
- **Página imprecisa:** se aplica la misma normalización.
- **Conjunto de oro:** al menos 3 respuestas escritas a mano por regla, que la cumplen y que no la cumplen. El puntuador tiene que clasificarlas todas bien, y va en sus pruebas.
- **Revisión de las claves:**
  1. **por programa:**
     - el ancla está en su página;
     - la clave está en esa página;
     - la clave no aparece con otro valor en otro documento vigente del proyecto;
     - la marca de sustituido es coherente;
  2. **una muestra de 40 preguntas** revisada por un subagente que no ha visto el puntuador ni la instrucción. Los desacuerdos los decide la sesión del CEO;
  3. **cada cambio del puntuador** lo revisa un subagente distinto del que lo hizo.

### 6.2 Varias ejecuciones y cómo se decide

- **Cierre:** 3 repeticiones con la batería de desarrollo y 3 con la de reserva. La muestra de Sonnet (30 preguntas, una ejecución) sale de desarrollo, nunca de la reserva.
- **Mínimo de D-049, propuesta:** la peor de las tres por encima del 90 %, en cada batería. La alternativa es exigirlo a la media; decide el CEO.
- **Fugas y citas inventadas:** 0 en las tres. Una sola bloquea.
- **Regresión.** Un cambio empeora si se da cualquiera de estas dos cosas:
  - su media queda por debajo del mínimo del rango anterior;
  - una pregunta que acertaba en las 3 repeticiones falla en las 3.
- **Intermedias:** las preguntas que fallan más 30 centinelas estables, de desarrollo, en 1 ejecución (D-050).
- **El repetidor de consultas** (7.1) se ejecuta contra una pila cargada con el mismo commit y el mismo archivo que la medición que analiza. El archivo es determinista (semilla fija), y los identificadores se traducen con `ids.json`.

## 7. Recuperación: lo que se prueba en el banco y llega a Odín

Cada mejora es una estrategia del banco (5.7). Entra en Odín si cumple la regla de 5.7.

### 7.1 «No consta» con rastro y reintento

**Diagnóstico: el repetidor de consultas.** Vuelve a lanzar contra la pila las consultas guardadas, sin IA, y clasifica cada «no consta» falso:

- **lectura:** la página estaba entre los resultados y la IA no la usó;
- **filtro:** la página salía con el filtro del proyecto;
- **búsqueda:** no salía con ninguna de sus consultas.

**Servidor (Odín sin IA).** Cuando `searchKnowledge` no da resultados o los da repartidos, la respuesta añade `searched`, con las consultas y el filtro, y `hints`:

- sin filtro y con resultados de 3 o más proyectos: «Los resultados son de N proyectos distintos. Si la pregunta es de uno, búscalo con `listProjects` y filtra»;
- con filtro y sin resultados: «Prueba con otras palabras o mira `listDocuments` del proyecto».

Las pistas se calculan sobre los resultados visibles para el trabajador. Nunca cuentan ni nombran nada que él no vea.

**Instrucción.** Antes de decir «no consta», la IA:

1. filtra por el proyecto, si la pregunta lo nombra;
2. busca dos veces más, con palabras técnicas y corrientes;
3. mira la lista de documentos del tipo que debería decirlo y lee su página más probable.

Si aun así no lo encuentra, responde: «No consta. He buscado "…" y "…" en <proyecto> y he revisado <documentos>».

**Lo vigilan:**

- los «no consta» correctos y los falsos positivos, que no pueden empeorar;
- los tokens por pregunta.

**Con el bloque 4:** comprobar que las pistas no cambian el recuento de huecos (`question_record`).

### 7.2 Citar cada dato

- Cada cifra, fecha, importe o identificador lleva la cita de su página, copiado como está.
- Un cálculo se dice como cálculo, con la cita de cada cifra que entra en él.
- El título y la fecha de entrada se presentan como datos de Odín.
- **Se mide con:** la cifra «limpia», `uncitedSource` y `noSource`.
- **En el banco se prueba también** `checkAnswer` con jueces (5.7). Es el candidato de «Odín con su propia IA» para este problema.

### 7.3 Listas completas

- **Batería:** al menos 20 preguntas de lista (4.1).
- **Puntuador:** en cada elemento vale cualquier página de su documento donde el programa encuentre el ancla de ese elemento.
- **Servidor:** `listDocuments` acepta un `query` opcional.
  - Devuelve cada documento visible que lo cumple, con su mejor página y sin cortar en los primeros resultados.
  - Mantiene el tope de 2.000 y `truncated`.
- **Instrucción:**
  - un elemento por línea, cada uno con su cita;
  - el orden que se pida;
  - la marca de «no consta» solo si no hay ningún elemento;
  - avisar si la lista puede estar incompleta.

### 7.4 Etiquetas de más

- **Hoy:** 16 de 30 fichas tienen alguna en la batería del etiquetado. D-031 pone primero la exhaustividad.
- **Pasos:**
  1. medir la precisión por tema y compararla con T-010;
  2. contar cuántos falsos positivos de las listas vienen de una etiqueta de más;
  3. **solo si afectan,** probar variantes del etiquetado en su batería, en local y sin cuota:
     - un tema principal más los secundarios explícitos;
     - un máximo de temas;
     - una segunda comprobación por tema.
  - Solo vale una variante que no baje del 97,6 % de exhaustividad.

### 7.5 Cuándo salen de la reserva el reordenador y los vectores

- **Un fallo de recuperación** es una pregunta en la que la página buscada no salió en los 5 primeros resultados de ninguna consulta de ningún buscador, en ninguna repetición.
- **En el repetidor, sin IA,** se reordenan los 50 primeros resultados, o se mezclan con vectores, y se mira si la página entra en los 5 primeros.

**El reordenador sale de la reserva cuando se cumplen las tres:**

1. los fallos de recuperación son al menos el 3 % de las preguntas con respuesta, en la reserva, en la capa pública o en las preguntas de administradores;
2. en el repetidor, el reordenador recupera al menos la mitad de esos fallos sin perder ningún acierto;
3. añade como mucho 5 s por búsqueda (percentil 95) en la máquina de referencia de oficina (D-030).

El reordenador del estudio tarda unos 25 s por pregunta, así que no cumple el punto 3. El primer candidato es uno pequeño y dedicado, y descargarlo necesita permiso.

**Los vectores salen de la reserva cuando se cumplen las tres:**

1. los fallos de recuperación por vocabulario son al menos el 3 % en las preguntas de administradores o en la capa pública. Las nuestras no sirven para esto: la reformulación se diseñó con nuestro vocabulario;
2. en el repetidor, la búsqueda híbrida recupera al menos la mitad sin perder identificadores ni negaciones;
3. el CEO acepta mantener el índice.

## 8. Métricas y objetivos

Los objetivos son propuestas para el CEO. Una vez cumplido, cada objetivo pasa a ser umbral de regresión (D-049). Se miden en la peor de 3 repeticiones del cierre, en desarrollo y en reserva, salvo que se diga otra cosa.

| Métrica | Hoy (`procesado`, 1 ejecución) | Objetivo |
|---|---|---|
| Correctas, mínimo de D-049 | 93,1 % (intervalo: 88,9–95,8) | > 90 % |
| «Limpia», recalibrada | sin medir (la estricta da el 56,9 %) | ≥ 90 % de las correctas |
| Datos `noSource` sin decir que son un cálculo | sin medir | 0 |
| «No consta» falsos | 9 de 216 | ≤ 1,5 % de las preguntas con respuesta |
| «No consta» correctos (sin fuente, falso supuesto y casi negativo) | 18 de 19 | ≥ 95 % |
| «No consta» con rastro | no existe | todos |
| Página imprecisa, recalibrada | 45 de 216 sin recalibrar | ≤ 10 % |
| Sustituidos dados por vigentes | 0 | 0 |
| Fugas | 0 | 0 siempre; una sola bloquea |
| Citas inventadas | 0 | 0 siempre (D-050) |
| Listas completas | de 4 a 8 de 8 | ≥ 90 %, con exhaustividad 1,0 |
| Contrafactual (cifra cambiada, 3.3) | sin medir | 100 %: la respuesta sigue al texto |
| Capa pública (3.2) | sin medir | > 90 % |
| Pérdida por OCR (por parejas) | sin medir | ≤ 5 puntos |
| Etiquetas de más (control-3) | 16 de 30 | ≤ 5 de 30, solo si afectan a las listas |
| Preguntas inestables | 23 entre 3 mediciones | ≤ 5 % de la batería |
| Jurado: aceptaciones falsas frente al script | sin medir | ≤ 3 % |
| Jurado: kappa frente al script | sin medir | ≥ 0,8 |
| Otras IAs | sin medir | la regla de 6.2 para recomendarlas |

## 9. Orden y punto de partida

- **Base:** `feature/calidad-recuperacion`, en 883e707.
- **Lo que toca piezas del CTO 2** (`medir/`, la instrucción del MCP, las herramientas y la búsqueda) espera a que sus bloques 2, 5 y 4 estén mezclados en `feature/fase-2-conocimiento`, y entonces se trae esa feature a esta rama.
- **El banco y el archivo ampliado son ficheros nuevos,** así que pueden empezar ya.

**Fases:**

1. **Fase 0, ahora y sin choque con el CTO 2:**
   - esqueleto del banco con la IA simulada;
   - generador de las capas 3.1 y 3.3;
   - lista de documentos públicos y lectura de sus licencias. Leer páginas web y consultar los listados de la API del BOE vale sin permiso, porque el propietario pidió buscar en internet; **descargar documentos sí lo necesita;**
   - generador de los tipos de pregunta nuevos.
2. **Fase 1, primera prueba real del banco:**
   - **piloto de 5 preguntas** con 20 buscadores y el jurado más grande. Mide de verdad el coste de buscadores, redactores y jueces;
   - si el coste cuadra, **barrido de 40 preguntas** sobre el archivo ficticio: curvas de número de buscadores, de mezcla y de jueces;
   - calibración de los jueces.
3. **Fase 2, archivo y preguntas ampliados:**
   - descarga de la capa pública, con permiso;
   - capa alterada y preguntas validadas entre agentes;
   - batería de reserva;
   - puntuador recalibrado, cuando el CTO 2 haya terminado con `medir/`;
   - nueva línea base con 3 repeticiones.
4. **Fase 3, recuperación:**
   - 7.1, 7.2 y 7.3 como estrategias del banco;
   - las ganadoras se llevan a Odín sobre los bloques del CTO 2;
   - 7.4 y 7.5 en local.
5. **Fase 4, escaneados y otras IAs:**
   - escaneados sintéticos y de la Gazeta;
   - otras IAs, cuando haya cuentas.
6. **Fase 5, cierre:** modo de cierre con las finalistas, la muestra de Sonnet y el informe.

## 10. Choques con el trabajo del CTO 2

| Fichero o pieza | Bloques del CTO 2 | Parte de este diseño | Cómo se evita el choque |
|---|---|---|---|
| `mcp/src/server.ts` (`INSTRUCTIONS`) | 2 y 5 | 5.8, 7.1, 7.2 y 7.3 | El banco prueba las instrucciones como texto de enfoque, sin tocar el fichero. Llevarlas a Odín, después de sus bloques |
| `medir/puntuar.py`, `ejecutar.py` y `medir.sh` | dueño de la batería | 5.2 (registrar los resultados vistos) y 6.1 | El banco usa `medir/` como biblioteca. Los cambios en `medir/`, después de su última medición de bloque |
| `datos/bateria.json` y `generar/` | 2, si añade preguntas | 3, 4.1 y 4.3 | Las capas nuevas van en `banco/archivos/`, y la reserva en su rama aparte |
| `searchKnowledge` y `listDocuments` | 2 | 7.1 y 7.3 | Después del bloque 2 |
| `question_record` y huecos | 4 | 7.1 | Comprobarlo cuando exista el bloque 4 |
| Etiquetado | 3, cerrado (D-058) | 7.4 | No choca |

## 11. Consumo estimado

**Base:** lo ya medido. Haiku, 216 preguntas, unos 14 minutos con concurrencia 4 y unos 1,2 USD de coste notificado, es decir, unos 0,006 USD por pregunta y buscador. Es el coste notificado dentro de la suscripción, no un precio. Los jueces y los redactores no se han medido: el piloto de la fase 1 los mide antes de cualquier barrido.

| Experimento | Ejecuciones de buscador | Tiempo | Coste notificado de los buscadores |
|---|---|---|---|
| Piloto de la fase 1 | 5 × 20 = 100 | unos 7 min | unos 0,6 USD |
| Barrido de la fase 1 | 40 × 20 = 800 | unos 50 min | unos 4,8 USD |
| Línea base de la fase 2 | 3 × unas 550 | unas 1 h 45 min | unos 10 USD |
| Primera medición de la reserva | 3 × 200 | unos 40 min | unos 3,6 USD |
| Intermedias de la fase 3 (fallos más centinelas, 3 estrategias, 2 intentos) | unas 6 × 60 | unos 25 min | unos 2,2 USD |
| Escaneados por parejas | 2 × 3 × 30 | unos 12 min | unos 1,1 USD |
| Cierre con un buscador (desarrollo, reserva y la muestra de Sonnet) | 3 × unas 550, 3 × 200 y 30 de Sonnet | unas 2 h 30 min | unos 16 USD |

- **Si gana una estrategia de N buscadores,** el cierre cuesta unas N veces más. El tope `budgetUsd` de cada experimento impide pasarse.
- **Sin cuota:**
  - el jurado local;
  - el repetidor;
  - el etiquetado;
  - el submuestreo;
  - las pruebas del banco con la IA simulada.
- **Construcción:** los subagentes que construyen gastan aparte. Haiku por defecto; Sonnet solo si Haiku falla dos veces en la misma tarea.

## 12. Lo que necesita del propietario

| Qué | Para qué | Cuándo |
|---|---|---|
| Permiso para descargar los documentos públicos de la lista (3.2) | Capa pública | Fase 2 |
| Imprimir y escanear unos 10 documentos ficticios | Escaneados reales nuevos (3.4) | Fase 4 |
| Entrevistas con 3 a 5 administradores, de 15 a 20 preguntas cada uno | Preguntas reales (4.2) | Cuando se hagan las entrevistas pendientes |
| Cuentas de API de OpenAI y Google, con un presupuesto | Buscadores y jueces de otras familias (5.8) | Fase 4 |
| Archivo real anonimizado y validación legal (D-022) | Piloto | Antes del primer cliente |
| Permiso para descargar un reordenador pequeño | Solo si se dispara 7.5 | Si hace falta |
| Cuánta cuota semanal se puede dedicar a esto | Ordenar las fases dentro del límite semanal | Antes de la fase 1 |

## 13. Validación del CEO (D-060)

1. **Mínimo:** la peor de 3 repeticiones tiene que superar el 90 %. Si no llega, el análisis vuelve al CEO.
2. **Objetivos del apartado 8:** validados tal cual. Una vez cumplidos, pasan a umbral de regresión.
3. **Jueces:**
   - donde hay clave, manda el script;
   - donde no la hay, puntúa un jurado de modelos distintos, calibrado (aceptaciones falsas ≤ 3 % y kappa ≥ 0,8), y siempre con la cifra corregida.
4. **Documentos públicos:**
   - basta con la licencia de cada fuente; no hace falta la validación legal de D-022;
   - solo textos institucionales y normativos, sin datos de particulares: ni nombramientos ni nombres de personas físicas;
   - se anotan la fuente y la licencia de cada documento;
   - entran en git solo si la licencia permite redistribuirlos; si no, van a una carpeta local fuera de git;
   - la descarga de la lista de 3.2 queda autorizada. Cualquier fuente nueva se consulta antes con el CEO.
5. **«No consta» con rastro y citar cada dato:** sí a los dos. El rastro, breve y en palabras llanas.
6. **`deepSearch` y `checkAnswer`:**
   - umbral provisional: como mucho 10 s más en la mediana y 0,02 USD más de coste notificado por respuesta;
   - además, tienen que ganar a un solo agente con el mismo gasto, por encima de la variación;
   - antes de llevarlas al producto vuelven al propietario, porque cambian quién paga la IA y qué sale al proveedor de Odín (D-016).
7. **Disparadores del reordenador y de los vectores:** validados.
8. **Reserva:**
   - se quema cuando sirve para decidir un cambio, y se renueva;
   - solo la lanza el CTO que cierra una fase.
9. **Cuota:** la decide el propietario. Hasta entonces no se lanza ni el piloto ni el barrido; si lo autoriza, se empieza por el piloto de 5 preguntas.
10. **Cuentas de OpenAI y Google:** son de pago y las decide el propietario. Quedan para la fase 4.
11. **Coordinación:** `medir/` no se toca sin hablarlo antes con el CTO 2.

## 14. Lo que queda a criterio de quien lo construya

- Los nombres exactos de los ficheros, de las clases del puntuador y de los enfoques, dentro de lo que dice este diseño.
- La redacción de cada enfoque y de cada rúbrica: se ajusta en el banco, nunca contra la reserva.
- Cómo agrupa `majorityVote` respuestas con los mismos datos.
- Las plantillas de los documentos nuevos y las alteraciones de 3.3.
- Qué páginas concretas de la Gazeta y qué ordenanzas, dentro de lo que dice 3.2.
- Las 30 centinelas, por tipo y con una semilla fija.
- La forma de `hints`, siempre que no revele nada fuera de la visibilidad del trabajador.
