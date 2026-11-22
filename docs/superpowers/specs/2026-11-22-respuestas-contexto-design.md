# Respuestas generosas, con contexto e historial de cambios (encargo 30, D-126): diseño y plan

- Fecha: 2026-11-22. Autor: CTO 3. Estado: **por validar** por el CEO. No se construye nada hasta su visto bueno.
- **Petición (D-126):**
  - la respuesta directa va primero, y después el contexto;
  - cada dato lleva su documento y su página;
  - si un dato cambió: el valor vigente, desde cuándo y con qué documento, y el historial en orden;
  - si falta un eslabón de la cadena, se dice;
  - los documentos que rectifican a otro quedan vinculados por el identificador que citan, sin IA;
  - el administrador puede marcar a mano una sustitución desde el panel;
  - no se señalan desajustes que no se han comprobado;
  - la prueba pasa con Haiku y con Sonnet.

## 0. Qué hay hoy (comprobado el 22 nov., sin IA)

- **Reproducción sin IA:** pila desechable `odin-e30` con el código de `develop` 1fbe205d y Mirador cargado. Quedaron 241 documentos listos y 29 escaneos sin lector OCR. Las dos preguntas del propietario y dos formulaciones más de cada una se lanzaron contra `POST /v1/search`, con y sin el proyecto. La pila se quitó al terminar.

  | Pregunta | Qué sale entre los 10 primeros |
  |---|---|
  | «¿Cuánto costó el mantenimiento del ascensor de Mirador en marzo de 2025?» y otras dos formas | **La rectificativa AT-2025-0302-R no sale en ninguna de las 6 búsquedas.** La original AT-2025-0302 sale una vez, en el puesto 6. Ganan las actas, el contrato de Torreluz, la subrogación y fichas de caso |
  | «¿Cuál es la franquicia por daños por agua de Residencial Mirador del Puerto?» y otras dos formas | **En 5 de las 6 búsquedas no sale ninguna póliza VLM.** En la otra salen VLM-25 en el puesto 5 y VLM-16, 19 y 22 en los puestos 8 a 10, como sustituidas |

  - Buscada por su número, la rectificativa sale la primera.
  - El resultado de VLM-25 no lleva nada que apunte a sus versiones anteriores. Solo las viejas apuntan a la nueva (`replacement`).
  - Cada respuesta de búsqueda ocupa de 7,4 a 8,5 KB, siempre con 10 resultados.
- **Conclusión:** la hipótesis de D-126 («Haiku no buscó rectificaciones») explica solo una parte. **La búsqueda casi nunca trae las facturas ni las pólizas** para estas preguntas:
  - el orden usa `ts_rank_cd` sin normalizar por longitud, sobre un OR de los términos de la pregunta, así que las páginas largas que repiten palabras ganan a la factura corta, que contiene justo lo preguntado;
  - el texto de la rectificativa no comparte casi palabras con la pregunta: solo el vínculo por identificador puede llevar a ella.
- **Sustituciones** (V11):
  - una columna `superseded_by` en el documento viejo; la cadena se recorre hacia delante, con un tope de 50 saltos y visibilidad comprobada en cada salto (`SupersessionSql`);
  - las sustituidas se devuelven, pero detrás de las vigentes;
  - el documento no tiene una fecha propia: solo `uploadedAt`, y la fecha del texto no se guarda.
- **El botón del panel ya existe:** está en Documentos, «Más acciones», «Marcar como sustituido…» (`SupersedeDialog`), y queda en el registro (`DOCUMENT_SUPERSEDED`). La premisa de D-126 («no se ha visto un botón») se corrige: falta que se vea, no que exista.
- **Instrucciones del conector:** unos 2.081 caracteres con la etiqueta por defecto, y hasta unos 2.155 con una etiqueta propia larga. Quedan unos 45 de margen hasta el tope de 2.200, así que hay que quitar texto para poner texto.
- **Identificadores:** la primera carga reconoce `XXX-NNNN-AAAA` y «sustituye a la póliza», que no encaja con Mirador: `VLM-16-48120` y «sustituye y anula a». No hay extracción de facturas.

## 1. Qué pasa a ser determinista y qué sigue dependiendo del modelo

| Criterio de D-126 | Determinista (herramientas y datos) | Del modelo |
|---|---|---|
| 2. Documento y página | cada resultado, versión y corrección trae su documento, su página y el texto literal | citarlos |
| 3. Vigente e historial | la cadena entera, ordenada, con el fragmento de cada versión para la pregunta y la frase que la enlaza | redactarlo en orden |
| 4. Eslabón que falta | marca `incomplete` cuando la cadena se corta o hay un eslabón que no ves | decirlo |
| 6. Vínculo por identificador | se detecta sin IA, con una lista cerrada de frases | — |
| 7. Avisar de lo que rectifica o sustituye | en todas las salidas (ver el apartado 3) | usarlo |
| 8. Marcar a mano en el panel | ya existe; se hace visible en el detalle del documento | — |
| 1. Respuesta directa primero, 9. no señalar desajustes sin comprobar, 10. Haiku y Sonnet, y la atribución del artículo 4 | — | solo se demuestran con la medición con IA |

## 2. Vínculos por el identificador que se cita (sin IA)

- **Qué se reconoce, con una lista cerrada:**
  - **El identificador propio del documento:** en las primeras líneas de la página 1, tras una etiqueta («Número de factura», «Número de factura rectificativa», «Factura n.º», «Póliza n.º», «N.º de póliza», «Contrato n.º», «Presupuesto n.º»), un código con letras, cifras y guiones. Ejemplos: `AT-2025-0302`, `VLM-25-48120`, `TRL-2025-0388`, `ATL-4471-2023`.
  - **El identificador citado con un verbo de cambio:** «rectifica (la factura)», «sustituye (y anula) a (la póliza / el contrato / la norma)», «anula», «modifica», «deja sin efecto», seguidos de un identificador.
  - Una mención sin verbo no vincula. Por ejemplo, el informe del perito que nombra VLM-22 no la rectifica.
- **Cómo se evitan los falsos positivos:**
  - el código se compara exacto, con límites de palabra: `AT-2025-0302` nunca casa con `AT-2025-0302-R`;
  - solo se vincula si el identificador citado es el propio de **un único** documento, de la misma empresa y del mismo proyecto o de lo general (la regla de las sustituciones). Sin ninguno o con varios, no hay vínculo y la cita queda como pendiente;
  - nada de parecidos aproximados sobre texto leído por OCR;
  - lo que no se ha leído (páginas vacías) no vincula.
- **Prueba sin IA:** sobre los 270 documentos de Mirador y los 1.360 del archivo ficticio salen exactamente los vínculos esperados y ninguno más:
  - VLM-19 a VLM-16, VLM-22 a VLM-19 y VLM-25 a VLM-22;
  - AT-2025-0302-R a AT-2025-0302;
  - las pólizas ATL del archivo ficticio.
- **Dónde se guardan:** tabla nueva `document_link`, en la migración **V34**. Las V31 a V33 ya están escritas en otras ramas.
  - Columnas: `company_id`, `source_document_id`, `target_document_id` (nula mientras la cita esté pendiente), `relation` (`RECTIFIES`, `REPLACES`, `MODIFIES`), `cited_identifier`, `evidence_page`, `evidence_quote`, `origin` (`DETECTED` o `ADMIN`), `created_at`.
  - Claves foráneas compuestas con `company_id` y RLS por empresa con el patrón de siempre: el aislamiento entre empresas no cambia.
  - La tabla `document_identifier (company_id, document_id, identifier)` guarda los identificadores propios.
- **Cuándo se calculan:**
  - al quedar listo cada documento, en un paso nuevo tras la lectura, en las dos direcciones: el nuevo cita a uno que ya estaba, y uno que ya estaba citaba al nuevo;
  - para los documentos ya instalados (1.624 en la instalación del propietario), una pasada única tras la V34, ensayada antes sobre una copia.
- **Qué significa un vínculo detectado (decisión 1 del CEO):** no cambia nada por sí solo. D-050 dice que una sustitución la marca una persona. El resultado avisa («consta que lo rectifica AT-2025-0302-R») y el panel ofrece confirmarlo como sustitución con un clic, que queda registrado.

## 3. Qué devuelven las herramientas (todas las salidas)

- **`searchKnowledge`:** el orden y el SQL de búsqueda no se tocan. Después de buscar, se añade la información de vínculos usando los ids de los resultados. Cada resultado de documento puede traer:
  - `versions`: la cadena de sustituciones entera, hacia atrás y hacia delante, de la más antigua a la más reciente. Lleva un tope de 10 y, por cada versión:
    - `documentId` y `title`;
    - `current`;
    - el mejor fragmento de esa versión para la misma pregunta, con su página;
    - la frase que la enlaza, con su página (por ejemplo, «Esta póliza sustituye y anula a la póliza n.º VLM-22-48120 desde el 1 de octubre de 2025.»).
  - `corrections`: los documentos que lo rectifican o modifican, y los que él rectifica o modifica, con la relación, el documento, la página y la frase literal.
  - `incomplete`: verdadero si la cadena se corta, si hay un eslabón que no ves o si una cita quedó pendiente.
- **Agrupar versiones:** si varias versiones de la misma cadena salen entre los resultados, se agrupan en el resultado vigente, y así quedan huecos para otros documentos.
- **Fechas:** no hay un campo de fecha calculado. Las fechas salen solo como texto literal con su página: la frase del vínculo y las primeras líneas de la página 1 de cada versión («Efecto: 1 de octubre de 2025», «Fecha: 02/04/2025»). `addedAt` y `supersededAt` no son «desde cuándo».
- **`readPage`:** los mismos `versions`, `corrections` e `incomplete` del documento leído.
- **`readCaseFile` y `getCaseFile`:** por cada documento del expediente, su marca de sustituido o corregido y el vigente. Hoy no la lleva por página.
- **`verifyCitation`:** cada resultado dice si la cita es de un documento sustituido o corregido, y cuál es el vigente. Es la última puerta que ya exigen las instrucciones.
- **`findSimilarCases`:** solo `corrections`, añadido con el mismo paso común. No se toca su orden (encargo 29).
- **`listDocuments`:** la marca de corregido, junto a la de sustituido.
- **Visibilidad:** cada salto se filtra como en `SupersessionSql`, por empresa, proyectos visibles y documentos que se pueden listar. Un documento que el trabajador no ve nunca aparece con su id ni su título. Qué se le dice entonces es la decisión 2 del CEO.

## 4. La forma de la respuesta (instrucciones del conector, con el tope de 2.200)

- **La frase larga de sustituidos de las instrucciones** se cambia por una más corta y general: responde primero lo preguntado y después el contexto; si un resultado trae versiones o correcciones, da el valor vigente con su documento, su página y su fecha tal como consta, y el historial en orden, cada valor con su cita; si falta un eslabón, dilo; compara dos documentos solo después de leer los dos.
- **El detalle** (qué es `versions`, `corrections` e `incomplete`) va en la descripción de `searchKnowledge`, como ya se hizo con otras reglas (D-115).
- **La prueba del tope** pasa a medir con la etiqueta de proyecto más larga, no solo con la de por defecto. Se actualizan las pruebas que fijan las frases de ahora.

## 5. El orden de los resultados (decisión 3 del CEO)

- **Sin cambiar el orden,** la pregunta del ascensor no trae ni la original: los vínculos solo ayudan cuando sale alguna versión. Mejorarlo está en mi área (recuperación de datos), pero cambia el orden de todas las búsquedas.
- **Propuesta:**
  - una batería de búsqueda sin IA con las preguntas de Mirador y sus documentos esperados entre los 10 primeros;
  - probar dos variantes: normalizar por longitud, y premiar las páginas que contienen más términos distintos de la pregunta;
  - aceptarlas solo si no empeoran ninguna de las baterías sin IA que ya hay: `ArchiveSearchBatteryIT` (216 preguntas, recall@10), `CaseNumberBatteryIT` y `SimilarCasesBatteryIT`.
- **Alcance:** solo en la consulta de `searchKnowledge`, no en `findSimilarCases`, para no chocar con el encargo 29.

## 6. Panel

- En el detalle del documento:
  - la cadena de versiones y los vínculos detectados, cada uno con su frase y su página;
  - «Confirmar como sustitución» en un vínculo `REPLACES` o `RECTIFIES` detectado, y «Descartar el vínculo»;
  - «Marcar como sustituido…», que hoy solo está en «Más acciones» de la lista.
- Todo lo hace solo el administrador, con confirmación y con registro: entradas nuevas `DOCUMENT_LINK_CONFIRMED` y `DOCUMENT_LINK_DISMISSED`, además de `DOCUMENT_SUPERSEDED`, que ya existe.

## 7. Consumo (sin IA; lo de la IA, con permiso)

- **Medido hoy:** una respuesta de búsqueda ocupa de 7,4 a 8,5 KB.
- **Previsto, sin medir:**
  - unos 1,5 a 2 KB más en los resultados que tienen versiones o correcciones, como 4 versiones de unos 350 B y una corrección de unos 400 B;
  - la agrupación de versiones lo compensa en parte, porque siguen siendo 10 resultados;
  - en los demás resultados, nada.
- **Tokens:** no hay una equivalencia medida entre caracteres y tokens, porque los registros del banco no guardan el texto de las herramientas. Con unos 4 caracteres por token, sin medir, serían unos 400 a 500 tokens más por búsqueda afectada.
- **Lo que se ahorra, sin medir:** llamadas: hoy la IA tiene que leer cada póliza por su cuenta.
- **Después de construir,** se mide sin IA el tamaño real con la batería de búsqueda, y con IA, el consumo de cada respuesta (apartado 8).

## 8. Baterías

- **Sin IA** (en `make check` y en Actions):
  - **Datos:** se crea un conjunto ficticio pequeño en los recursos de prueba, que imita a Mirador sin depender de él. Lleva:
    - cuatro pólizas encadenadas con «sustituye y anula a»;
    - la factura y su rectificativa;
    - un anexo que cambia un precio sin citar el identificador;
    - un documento que cita un identificador que no existe;
    - el mismo identificador en otra empresa y en un proyecto cerrado.
  - **Pruebas:**
    - los vínculos que salen son exactamente los esperados;
    - cada salida trae `versions`, `corrections` e `incomplete` como corresponde;
    - aislamiento con dos empresas y un proyecto cerrado;
    - la batería de búsqueda del apartado 5.
- **Con IA** (permiso del propietario, D-094). Preguntas sobre Mirador:

  | # | Pregunta | Clave |
  |---|---|---|
  | M1 | franquicia por agua | 450 € vigente desde el 1 oct 2025 (VLM-25, p1); historial 150 € (2016, VLM-16), 300 € (2019, VLM-19 y 2022, VLM-22); la regla de una franquicia por siniestro es del art. 4 de las condiciones generales (p3) |
  | M2 | mantenimiento del ascensor en marzo de 2025 | 470,69 € (AT-2025-0302-R, 2/4/2025), que rectifica 481,58 € (AT-2025-0302, 10/3/2025); base correcta 389 €; **no debe afirmar** que falta una segunda factura ni un desajuste entre uno y dos ascensores (el contrato de Torreluz y el acta del 27/3/2025: 389 € por los dos) |
  | M3 | precio de la limpieza | 1.290 € desde el anexo de Faronda del 20 feb 2023, antes 1.180 €; el anexo no cita identificador, así que debe encontrarlo leyendo, y si no, decir que falta el eslabón |
  | M4 | prima del seguro de 2022 | 2.710 € (VLM-22), y que ya no está vigente (VLM-25, 2.980 €) |
  | M5 | derrama cambiada por un acuerdo | el valor del acuerdo posterior, con las dos citas |
  | M6 | artículo 12 de los estatutos cambiado por la junta de 2023 sin citarlos | da las dos y no juzga si es válido (regla ya vigente) |
  | M7 | dato sin versiones (control) | no inventa historial |
  | M8 | preguntada por un trabajador sin acceso | no revela lo que no ve |

  - **Sobre el criterio 9:** la clave lleva una lista de afirmaciones prohibidas que se comprueban por programa («desajuste», «no cuadra», «falta una segunda factura» y similares), salvo el desajuste real de 398 € frente a 389 €, explicado por la rectificativa. El programa que puntúa es `puntuar.py` del banco del CTO 2, que está parado. Añadirle el campo `no_debe_afirmar` es la decisión 5.
  - **Coste estimado, sin medir:**
    - **Haiku:** unos 0,01 USD por respuesta. Lo medido en el banco son de 0,0034 a 0,0054, y las preguntas de Mirador son más largas.
    - **Sonnet:** unos 0,2 USD por respuesta, a unas 20 veces el precio de Haiku.
    - **Propuesta:**
      - prueba de humo con M1 y M2, 3 veces con Haiku y una con Sonnet: unos 0,5 USD;
      - medición completa de las 8 preguntas, 3 veces con Haiku antes y después y una con Sonnet después: unos 0,5 + 1,6 = **2,1 USD**.

      Cabe en el tope de D-094 (5 USD a la semana).

## 9. Decisiones del CEO

1. **Vínculo detectado:** ¿solo avisa y propone confirmar en el panel, o marca por sí solo la sustitución? Recomiendo que solo avise y proponga confirmar, como manda D-050.
2. **Corrección que el trabajador no ve:** cuando el documento que corrige está en un proyecto que el trabajador no ve, ¿qué se le dice? Hoy, con una sustitución, se le dice «sustituido» sin decir por cuál. Recomiendo lo mismo: «corregido, por un documento al que no tienes acceso», sin id ni título.
3. **Tocar el orden de la búsqueda** (apartado 5): sin eso, M2 no se arregla. Recomiendo hacerlo con la batería sin IA y el requisito de que ninguna batería existente empeore.
4. **Mirador en git:** el código que genera Mirador y su narrativa, escrita con IA y que no se puede regenerar, viven en `experimentos/`, fuera de git (D-122). Opciones:
   - versionar el generador y la narrativa en `datos-prueba/caso-mirador/`, con las dos comprobaciones de siempre (de dónde viene y que todo es ficticio);
   - medir con Mirador solo en local.

   Las pruebas sin IA no dependen de esto. Recomiendo versionarlo, porque la batería con IA tiene que poder repetirse.
5. **`no_debe_afirmar` en `puntuar.py`,** que es del CTO 2 y está parado. Recomiendo añadirlo yo, con su prueba.
6. **Permiso para la medición con IA** del apartado 8 (unos 2,1 USD, más 0,5 de la prueba de humo). La herramienta del banco está en `feature/calidad-recuperacion`, sin unir a `develop`, y la batería de respuestas, en `experimentos/`: se lanzaría en local.

## 10. Plan (tras el visto bueno; cada tarea en `task/respuestas-contexto/<tarea>`, con PR a la feature)

| Tarea | Qué | Cómo se verifica |
|---|---|---|
| 1 | V34 (`document_identifier`, `document_link`) y extractor determinista | conjunto ficticio: exactamente los vínculos esperados; aislamiento; migración sobre filas existentes |
| 2 | cálculo al quedar listo cada documento y pasada única para los ya instalados | pruebas de las dos direcciones; ensayo sobre una copia de la base del propietario |
| 3 | `versions`, `corrections` e `incomplete` en todas las salidas, agrupación de versiones y visibilidad por salto | API y MCP; aislamiento con dos empresas y un proyecto cerrado |
| 4 | instrucciones y descripciones (tope de 2.200 con la etiqueta más larga) | pruebas del MCP |
| 5 | orden de la búsqueda (si lo decides) con la batería sin IA de Mirador | ninguna batería existente empeora |
| 6 | panel: detalle con la cadena, los vínculos y las acciones, con registro | pruebas de componentes |
| 7 | baterías con IA (si das permiso), `make check`, ensayo de la V34 y PR de la feature en borrador | medición con Haiku y Sonnet; validación en verde |
