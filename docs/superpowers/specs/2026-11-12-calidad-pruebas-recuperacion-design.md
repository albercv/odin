# Odín · Calidad de las pruebas y de la recuperación (diseño)

- Fecha: 2026-11-12. Autor: CTO 3. Encargo: D-059, del propietario por el CEO.
- Estado: propuesta. La valida el CEO (D-037).
- Rama: `feature/calidad-recuperacion`, que sale de `feature/fase-2-conocimiento` (883e707).
- Solo planifica. No se ha lanzado ninguna medición: las cifras de partida salen de resultados ya guardados en `experimentos/2026-11-12-archivo-ficticio/resultados/`, vueltos a leer con scripts locales y sin cuota de IA.
- Todo con datos ficticios. Los objetivos son objetivos, no promesas.

## 1. Para qué

El propietario quiere garantizar que Odín devuelve datos correctos en un porcentaje alto (D-049). Hoy Haiku acierta el 93,1 % (201 de 216), pero esa cifra no es una garantía:

- es una sola ejecución;
- las instrucciones se ajustaron con las mismas 216 preguntas con las que se mide;
- el archivo y las preguntas son nuestros y están limpios;
- solo se ha probado con Claude.

**Éxito.** Una cifra en la que se pueda confiar, y una recuperación que mejore donde más daña:

- medida con preguntas que nadie usó para ajustar;
- en varias ejecuciones, con su rango y su intervalo;
- con cada fallo grave medido aparte y con su objetivo;
- y sin que ninguna mejora empeore otra métrica.

**No entra:**

- las contradicciones que no explica ningún documento (D-049, van después);
- el piloto con datos reales: aquí solo se prepara el procedimiento, porque necesita al propietario y la validación legal (D-022);
- cambiar el motor de búsqueda sin un disparador medido;
- una IA que puntúe: puntúa un script (D-050).

**Sería un fracaso aunque funcionase:**

- subir la cifra ajustando contra la batería de reserva;
- reintentos que conviertan un «no consta» correcto en una respuesta que la fuente no respalda;
- gastar en mediciones más de lo que la cuota permite;
- cualquier registro que permita juzgar a una persona (D-005).

## 2. Lo que dicen los resultados guardados

Análisis hecho con `puntuacion.json` y `respuestas.jsonl` de las mediciones de Haiku, y con el archivo de `datos/`. No se ha llamado a ninguna IA.

### 2.1 Variación entre ejecuciones

| Comparación | Mejoran | Empeoran |
|---|---|---|
| `haiku-bloque7b` → `haiku-d054` (cambia una frase de la instrucción) | 5 | 10 |
| `haiku-d054` → `haiku-procesado` (cambios pequeños: reglas del OCR y de los temas) | 9 | 6 |

- En las tres últimas mediciones (`bloque7b`, `d054` y `procesado`):
  - 189 preguntas aciertan siempre;
  - 4 fallan siempre: Q147, Q149, Q153 y Q185;
  - 23 cambian de una a otra.
- **La mayoría de los fallos son inestables, no sistemáticos.** Por eso una sola ejecución no permite decir si un cambio mejora.
- **Intervalo.** Con 216 preguntas, cada una vale 0,46 puntos. El intervalo de confianza del 95 % (Wilson) de 201 de 216 va del 88,9 % al 95,8 %. Hoy no se puede afirmar con confianza que se supera el 90 %.
- **Listas completas:** 4 de 8 en `bloque7b` y 8 de 8 en `procesado`. Con 8 preguntas, la cifra por tipo no dice casi nada.

### 2.2 «Limpia del todo»: mezcla calibración y defectos

- En `procesado`, 83 preguntas tienen algún «dato sin respaldo», y 78 de ellas son correctas.
- Se clasificaron los 154 datos marcados según dónde aparecen. Es una clasificación aproximada, por coincidencia de texto:

| Dónde aparece el dato | Datos | Qué es |
|---|---|---|
| En el título o la fecha de entrada del documento citado | 35 | Calibración: lo da Odín como metadato |
| En otro documento del mismo proyecto, no citado | 55 | Defecto real: fuente no citada |
| En una ficha | 8 | Por revisar |
| En ningún documento, con ese formato | 56 | Mezcla: fechas reescritas y cálculos |

- **Fechas reescritas.** Por ejemplo, Q024 da «1/09/2022» y «24/04/2025», y la fuente dice «1 de septiembre de 2022» y «24 de abril de 2025». El puntuador lo cuenta como dato sin respaldo.
- **Conclusión.** La cifra del 56,9 % (57,9 % en `bloque7b`) mide a la vez errores del puntuador y defectos reales. Antes de fijarle un objetivo hay que separarlos (apartado 3.1).
- **Página imprecisa:** 45 preguntas en `procesado`. Le falta la misma revisión.

### 2.3 «No consta» falsos

- En `procesado` hay 9: Q070, Q125, Q138, Q147, Q148, Q149, Q153, Q160 y Q161.
- 4 de los 9 (Q147, Q148, Q149 y Q153) no filtraron nunca por proyecto. Buscaron entre 93 comunidades casi iguales.
- Todas hicieron 3 o 4 búsquedas, con entre 8 y 14 consultas, antes de rendirse. La IA sí reintenta, pero sin el filtro o sin leer lo que encuentra.
- El registro de la batería guarda cuántos resultados dio cada búsqueda, pero no cuáles. Sin eso no se puede saber si la página estaba entre los resultados y la IA no la leyó.

## 3. Pruebas

### 3.1 Puntuador recalibrado y revisado (va primero)

Todo lo demás se apoya en el puntuador. Ya se equivocó dos veces y movió cifras.

**Datos sin respaldo, por clases.** El puntuador clasifica cada dato:

| Clase | Ejemplo | Cuenta como defecto |
|---|---|---|
| `metadata` | el año del título o la fecha de entrada que da Odín | No |
| `reformatted` | «1/09/2022» frente a «1 de septiembre de 2022»; «2.706,40» frente a «2706,40»; «€» frente a «euros» | No |
| `derived` | la suma o la diferencia de dos cifras citadas | No, si la respuesta dice que es un cálculo |
| `uncitedSource` | el dato está en un resultado que la IA recibió o leyó, pero no lo cita | Sí |
| `noSource` | el dato no está en nada que la IA pudiera ver | Sí, grave |

- **Nueva cifra «limpia»:** la respuesta es correcta y no tiene datos `uncitedSource` ni `noSource`. La cifra estricta de hoy se sigue dando, para comparar.
- **Página imprecisa:** se aplica la misma normalización de formatos.
- **Qué no cambia.** La regla de «correcta» no se toca, así que el porcentaje del 90 % no se mueve con la recalibración.
- **El ejecutor guarda más.** Para distinguir `uncitedSource` de `noSource`, `ejecutar.py` guarda en `consultas` el `documentId` y la página de cada resultado, sin el texto.
- **Se vuelven a puntuar** las mediciones que tienen `ids.json`, y se publica la cifra de antes y la de después.

**Conjunto de oro.**

- Son respuestas escritas a mano para cada regla del puntuador: al menos 3 por regla, que la cumplen y que no la cumplen.
  - Por ejemplo: una cifra cambiada, otra página, un sustituido sin avisar, una cita de otra empresa, una fecha reescrita o un cálculo declarado.
- El puntuador tiene que clasificarlas todas bien. Va en sus pruebas, que se ejecutan sin cuota.

**Revisión de las claves.**

1. **Comprobaciones por programa** de cada pregunta:
   - el ancla está literal en su página;
   - la clave está en esa página;
   - la clave no aparece con otro valor en otro documento vigente del mismo proyecto;
   - la marca de sustituido coincide con `pila/sustituciones.py`.
2. **Muestra de 40 preguntas** revisada por un subagente que no ha visto el puntuador ni la instrucción.
   - Recibe la pregunta, la clave y las páginas del proyecto.
   - Contesta dos cosas: si la clave es la respuesta correcta y completa, y si hay otra fuente que diga otra cosa.
   - Los desacuerdos los decide una persona.
3. **Antes de cada medición importante,** el conjunto de oro y las comprobaciones tienen que dar todo bien. Un subagente distinto del que lo cambió revisa cada cambio del puntuador.

**Encaje con D-050.** El revisor audita las claves y el puntuador; no puntúa ninguna respuesta. Puntúa siempre el script.

### 3.2 Varias ejecuciones y cómo se decide

**Medición de cierre:**

- 3 ejecuciones de Haiku con la batería de desarrollo y 3 con la de reserva;
- Sonnet, una ejecución de la muestra de 30;
- se da, de cada cifra, la media, el mínimo y el máximo, y el intervalo de Wilson de las tres ejecuciones juntas;
- se da también qué preguntas son estables (3 de 3) y cuáles inestables.

**Mínimo de D-049, regla propuesta:** la peor de las tres ejecuciones por encima del 90 %, en cada batería. Es lo que vería un trabajador en un mal día. La alternativa es exigirlo a la media; decide el CEO (apartado 9).

**Fallos graves:** fugas y citas inventadas, 0 en las tres ejecuciones. Uno solo bloquea el cierre.

**Regresión.** D-049 fija un umbral que ningún cambio puede empeorar. Un cambio empeora si se da cualquiera de estas dos cosas:

- su media queda por debajo del mínimo del rango anterior;
- una pregunta que acertaba en las 3 ejecuciones pasa a fallar en las 3.

**Mediciones intermedias** (D-050: solo Haiku y lo que falla):

- las preguntas que fallan más 30 centinelas, 1 ejecución;
- las centinelas son preguntas estables, repartidas por tipo y elegidas siempre de la batería de desarrollo. Sirven para ver regresiones, que solo con los fallos no se ven;
- una mejora intermedia es una señal, no una cifra: la que vale es la del cierre.

### 3.3 Batería de reserva

**Qué es:**

- unas 150 preguntas nuevas, con al menos 15 por tipo y al menos 20 de lista completa, de versión y de dato concreto;
- y un archivo nuevo, generado por programa con otra semilla:
  - comunidades nuevas dentro de Ribera y de Albar, para que haya plantillas casi iguales;
  - tipos de documento que hoy no hay: correos, hojas de cálculo pasadas a texto, documentos a medias y actas con erratas;
- las preguntas las redacta un subagente que no ha visto la instrucción, los fallos ni la batería de desarrollo:
  - con otro estilo: palabras corrientes, faltas de ortografía y preguntas compuestas;
  - las claves y las anclas las calcula y comprueba el programa, como hoy; si el ancla no está literal en su página, la pregunta no entra.

**El archivo de desarrollo cambia una vez.** Los documentos de la reserva entran en la misma pila, y eso añade distractores a las preguntas de desarrollo. La primera medición con todo cargado es la nueva línea base de las dos baterías.

**Cómo se protege:**

- **Rama aparte:** las preguntas, las claves y los documentos nuevos viven en una rama huérfana, `reserva/bateria`, que no se mezcla nunca con ninguna feature. Una sesión que ajusta trabaja en ramas donde la reserva no existe, así que un `grep` no la encuentra. `medir.sh` la lee con `git show`.
- **Solo resultados agregados:** de una medición con la reserva se versiona solo el resumen (totales, por tipo y fallos graves contados). El detalle de cada pregunta queda fuera de git, en `estado/reserva/`.
- **Registro de usos:** `USOS.md`, en la rama de la reserva, con la fecha, el commit medido, el resultado y quién la lanzó.
- **Regla en el `CLAUDE.md` de Odín:** quien ajusta instrucciones, búsqueda o puntuador no abre la rama de la reserva ni su detalle.
- **Reserva quemada:** si alguien mira el detalle de una pregunta de la reserva, por ejemplo para entender un fallo, la reserva pasa a ser de desarrollo y se genera otra.
- **Límite:** la protección es de procedimiento. Un agente con acceso a git puede leerla; la rama aparte y el registro hacen visible si pasa.

### 3.4 Escaneados con errores de OCR

**Ahora, escaneados sintéticos:**

- Se eligen unos 20 documentos del archivo, unas 40 páginas, que respondan al menos a 30 preguntas de dato concreto, identificador o importe.
- Se pasan a PDF y se degradan con `scan()` del repositorio OCR (`scripts/make_samples.py`: 150 DPI, gris, giro y ruido), en dos niveles.
- Se leen con el puente del lector (Unlimited-OCR 2, en local) y se cargan como páginas `unlimited-ocr-2` en una variante de la carga, el «archivo escaneado».
- **Medición por parejas:** las mismas 30 preguntas, con el archivo limpio y con el escaneado, 3 ejecuciones de Haiku cada una.
- **Cifras:**
  - la pérdida por OCR, en puntos;
  - el CER del texto leído, con `cer_wer` del lector.

**Después, escaneados de verdad.** El propietario imprime unos 10 de esos documentos y los escanea con un escáner de oficina y con el móvil. Se sigue el mismo procedimiento. Los datos son ficticios, así que no hay problema legal.

**Arreglo condicional.** Si la pérdida en identificadores e importes supera su objetivo, se diseña un canal exacto tolerante a errores típicos del OCR («25O» por «250»), por ejemplo con similitud de trigramas. Hoy no se construye.

### 3.5 Preguntas de administradores reales

**Antes del piloto, en las entrevistas pendientes:**

- cada administrador recibe 3 comunidades del archivo ficticio, con sus documentos en PDF;
- escribe de 15 a 20 preguntas como se las haría a un compañero;
- nosotros buscamos la página y el ancla de cada una, y el programa comprueba el ancla;
- el administrador confirma la respuesta;
- las que no tienen respuesta en el archivo pasan a ser preguntas sin fuente;
- la mitad va a desarrollo y la otra mitad a la reserva, por sorteo con una semilla fija.

**Con el piloto:** el mismo procedimiento sobre su archivo anonimizado, después de la validación legal (D-022). Queda fuera de este plan.

**Herramienta:** `importar_preguntas.py` pasa un CSV al formato de `bateria.json`, con las comprobaciones de 3.1.

**Privacidad:** no se guarda quién escribió cada pregunta (D-005).

### 3.6 Otras IAs

**Ahora, con Claude: sin las instrucciones del servidor.**

- No todos los clientes pasan al modelo las `instructions` de un servidor MCP.
- Se mide Haiku con un MCP que no las envía. Así se sabe cuánto depende la calidad de ellas.
- Si baja mucho, las reglas clave pasan también a las descripciones de las herramientas, y se vuelve a medir.

**Modelo por API, con el conector en local:**

- Un ejecutor nuevo se conecta en local al MCP de Odín y pasa sus herramientas al modelo por la API del proveedor (OpenAI o Google).
- El MCP no se expone a internet. El proveedor solo recibe los fragmentos que lee el modelo, igual que con Claude (D-016).
- Las `instructions` se pasan como mensaje de sistema, y también se mide sin ellas.
- Mide si el modelo sigue las herramientas y las reglas de Odín.

**Producto con conector (ChatGPT, Gemini):**

- Cuando sus planes en el mercado objetivo admitan servidores MCP remotos.
- Para eso hay que exponer el MCP de prueba hacia fuera. Con datos ficticios es posible, pero el medio para exponerlo lo decide el CEO (D-019).
- Muestra de 30 preguntas, a mano: las respuestas se pegan en JSON y las puntúa el script.

**Decisión:** el modelo mínimo recomendado (D-052) se fija con la misma regla del mínimo que para Claude.

### 3.7 Métricas y objetivos

Una vez cumplido, cada objetivo pasa a ser umbral de regresión (D-049). Todos se miden en la peor de 3 ejecuciones, salvo que se diga otra cosa.

| Métrica | Hoy (`procesado`, 1 ejecución) | Objetivo propuesto |
|---|---|---|
| Correctas, mínimo de D-049 | 93,1 % (intervalo del 95 %: 88,9–95,8) | > 90 % en desarrollo y en reserva |
| «Limpia», con el puntuador recalibrado | sin medir; la estricta de hoy da el 56,9 % | ≥ 90 % de las correctas |
| Datos `noSource` sin decir que son un cálculo | sin medir | 0 |
| «No consta» falsos | 9 (6 en `bloque7b`) | ≤ 3 de 216 |
| «No consta» correctos en preguntas sin fuente | 18 de 19 | no baja |
| «No consta» con rastro (3.1 y 4.1) | no existe | todos |
| Página imprecisa, recalibrada | 45 de 216 sin recalibrar | ≤ 10 % de las respuestas |
| Sustituidos dados por vigentes | 0 (3 en `bloque7b`) | 0 en las 3 ejecuciones |
| Fugas | 0 | 0 siempre; una sola bloquea |
| Citas inventadas | 0 | 0 siempre (D-050) |
| Listas completas | 4 de 8 a 8 de 8 | ≥ 90 %, con al menos 20 preguntas y exhaustividad 1,0 |
| Etiquetas de más (batería del etiquetado, control-3) | 16 de 30 fichas | ≤ 5 de 30, sin bajar del 97,6 % de exhaustividad |
| Pérdida por OCR (por parejas) | sin medir | ≤ 5 puntos |
| Preguntas inestables (cambian entre las 3 ejecuciones) | 23 entre 3 mediciones distintas | ≤ 10 de 216 |
| Otras IAs | sin medir | la misma regla del mínimo para recomendarla |

## 4. Recuperación

Todos los cambios de la instrucción interactúan entre sí. Cada uno se mide por separado en una medición intermedia, y todos juntos en el cierre.

### 4.1 «No consta» con rastro y reintento

**Diagnóstico primero: el repetidor de consultas.**

- Es un script que vuelve a lanzar contra la pila las consultas que hizo la IA, guardadas en `consultas`, sin llamar a ninguna IA.
- Clasifica cada «no consta» falso en uno de tres tipos:
  - **lectura:** la página estaba entre los resultados y la IA no la leyó o no la usó;
  - **filtro:** no salía con sus consultas, pero sí con el filtro del proyecto;
  - **búsqueda:** no salía con ninguna de sus consultas.
- Es lo que hizo a mano el CTO 2 en el diagnóstico del bloque 7, automatizado.

**Servidor (MCP).** Cuando `searchKnowledge` no da resultados o los da repartidos, la respuesta añade:

- `searched`: las consultas y el filtro que se usaron;
- `hints`, solo dentro de lo que el trabajador puede ver:
  - sin filtro y con resultados de 3 o más proyectos: «Los resultados son de N proyectos distintos. Si la pregunta es de uno, búscalo con `listProjects` y filtra»;
  - con filtro y sin resultados: «Sin resultados en este proyecto. Prueba con otras palabras, o mira `listDocuments` del proyecto».
- Las pistas nunca cuentan ni nombran nada que el trabajador no vea. La búsqueda ya aplica la visibilidad, y las pistas se calculan sobre sus resultados.

**Instrucción.** Antes de decir «no consta», la IA:

1. si la pregunta nombra un proyecto, filtra por él;
2. busca al menos dos veces más, con palabras técnicas y corrientes;
3. mira la lista de documentos del proyecto, busca el tipo de documento que debería decirlo (póliza, acta, contrato) y lee su página más probable.

Si aun así no lo encuentra, dice «no consta» y cuenta su rastro: «He buscado "…" y "…" en <proyecto> y he revisado <documentos>».

**Riesgos:**

- Más reintentos pueden «encontrar» algo que no responde. Lo vigilan dos cifras: los «no consta» correctos en preguntas sin fuente no pueden bajar, y los falsos positivos se cuentan.
- Más tokens por pregunta. Se mide.

**Con el bloque 4.** Los huecos del panel salen de búsquedas con 0 resultados (`question_record`). Las pistas no cambian ese recuento, pero hay que comprobarlo cuando el bloque 4 exista.

**Se mide con:**

- los «no consta» falsos;
- el rastro: la respuesta nombra al menos una consulta que la IA hizo de verdad y, si filtró, el proyecto;
- los intentos antes de rendirse, sacados de `consultas`;
- los «no consta» correctos.

### 4.2 Citar cada dato

**Instrucción:**

- cada cifra, fecha, importe o identificador lleva la cita de la página donde está, copiado como está en la fuente, también las fechas;
- un cálculo se dice como cálculo, con la cita de cada cifra que entra en él;
- el título y la fecha de entrada son datos de Odín, y se dicen así: «según el título del documento», «entró en Odín el…».

**Se mide con:** la cifra «limpia», `uncitedSource` y `noSource` (3.1).

**Fuera:** no se cambia el formato de salida de la batería. Sigue pareciéndose a lo que escribe la IA de un trabajador.

### 4.3 Listas completas

**Diagnóstico.**

- Las cuatro que fallaron en `bloque7b` daban la lista entera. Fallaban en la página citada, en el orden o en la marca de «no consta».
- Con 8 preguntas, la variación lo tapa todo: 4 de 8 en una ejecución y 8 de 8 en otra.

**Cambios:**

- **Batería:** al menos 20 preguntas de lista en desarrollo y 15 en la reserva.
- **Puntuador:** en cada elemento de una lista, vale cualquier página de su documento donde el programa encuentre el ancla de ese elemento. Se calcula, no se elige a mano, y entra en el conjunto de oro.
- **Servidor:** `listDocuments` acepta `query`, que es opcional.
  - Devuelve cada documento visible que la cumple dentro del proyecto o del tema, con su mejor página, sin cortar en los primeros resultados.
  - Mantiene el tope de 2.000 documentos y `truncated`.
  - Reutiliza el índice de búsqueda.
  - Es la pieza de «listados completos» del informe del CEO.
- **Instrucción para listas:**
  - un elemento por línea, cada uno con su cita;
  - el orden que se pida;
  - la marca de «no consta» solo si no hay ningún elemento;
  - si la lista puede estar incompleta (`taggingPending` o `truncated`), decirlo.

**Se mide con:** las listas correctas, la exhaustividad y los falsos positivos en listas, es decir, elementos que no tocan.

### 4.4 Etiquetas de más

**Hoy:** 16 de 30 fichas tienen alguna etiqueta de más en control-3. D-031 dice «ante la duda, todos los temas que encajen»: la exhaustividad va primero.

**Pasos:**

1. **Medir la precisión por tema** y compararla con T-010, como pide D-058.
2. **Medir el efecto en las respuestas:** cuántos falsos positivos de las listas de la batería vienen de una etiqueta de más.
3. **Solo si afecta,** probar variantes del etiquetado con su batería, en local y sin cuota de API:
   - un tema principal y solo los secundarios explícitos;
   - un máximo de temas por documento;
   - una segunda comprobación por tema: «¿el documento trata de X? Da la frase».
   - Solo vale una variante que no baje del 97,6 % de exhaustividad. Se mide también su tiempo por documento, hoy unos 3 s.

**Si no afecta a las respuestas,** se deja como está y el administrador corrige en el panel.

### 4.5 Cuándo salen de la reserva el reordenador y los vectores

**Herramienta:** el repetidor de consultas de 4.1.

- Un **fallo de recuperación** es una pregunta fallada en la que la página buscada no estaba en los primeros 5 resultados de ninguna consulta de la IA, en ninguna de las 3 ejecuciones.
- **Repetición sin IA:** se reordenan los 50 primeros resultados de esas mismas consultas, o se mezclan con vectores, y se mira si la página entra en los 5 primeros.

**El reordenador sale de la reserva cuando se cumplen las tres:**

1. los fallos de recuperación son al menos el 3 % de las preguntas con respuesta, en la reserva o en las preguntas de administradores;
2. en la repetición, el reordenador recupera al menos la mitad de esos fallos sin sacar de los 5 primeros ninguna página que antes acertaba;
3. añade como mucho 5 s por búsqueda (percentil 95) en la máquina de referencia de oficina (D-030), con memoria libre junto al lector y el etiquetado.

- El reordenador del estudio, de 9.000 millones de parámetros, tarda unos 25 s por pregunta y ocupa 5,5 GB: no cumple el punto 3.
- El primer candidato es un reordenador pequeño y dedicado. Descargarlo necesita permiso.

**Los vectores salen de la reserva cuando se cumplen las tres:**

1. los fallos de recuperación por vocabulario (la página no comparte ninguna raíz con las consultas de la IA) son al menos el 3 % en las preguntas de administradores reales.
   - Las nuestras no sirven para esto: la reformulación se diseñó con nuestro vocabulario y da 34 de 34.
2. en la repetición, la búsqueda híbrida recupera al menos la mitad sin perder ninguna pregunta de identificador ni de negación, que siguen yendo por el canal exacto;
3. el CEO acepta el coste de mantener el índice.

**Escala.** La prueba a 10.000 documentos se hace cuando haya un cliente de ese tamaño, como dice el informe del CEO.

## 5. Orden

1. **Medir bien,** sin cuota de IA:
   - puntuador recalibrado;
   - conjunto de oro;
   - revisión de claves;
   - repeticiones y centinelas;
   - repetidor de consultas.
2. **Nueva línea base** sobre los bloques 2, 5 y 4 del CTO 2 ya cerrados: 3 ejecuciones de Haiku con la batería de desarrollo.
3. **Reserva:** generar, cargar y hacer su primera medición (3 ejecuciones).
4. **Sin instrucciones del servidor,** con Claude: es barato, y su resultado decide dónde van las reglas de 4.1 a 4.3.
5. **Recuperación:** 4.1, después 4.2 y después 4.3, cada uno con su medición intermedia.
6. **Escaneados sintéticos,** con su medición por parejas.
7. **Etiquetas de más** y **reordenador y vectores** (4.4 y 4.5): son trabajo local y van en paralelo.
8. **Otras IAs por API,** cuando estén las cuentas.
9. **Cierre:**
   - 3 ejecuciones con la batería de desarrollo y 3 con la de reserva;
   - la muestra de Sonnet;
   - el informe.

## 6. Choques con el trabajo del CTO 2

El CTO 2 construye los bloques 2, 5 y 4 en `feature/fase-2-conocimiento` y es el dueño de `medir/`. Este plan se ejecuta después, encima de su trabajo.

| Fichero o pieza | Bloques del CTO 2 | Parte de este plan | Cómo se evita el choque |
|---|---|---|---|
| `mcp/src/server.ts` (`INSTRUCTIONS`) | 2 y 5 (y antes 1, 3, 6 y 7) | 3.6, 4.1, 4.2 y 4.3 | Empezar cuando 2 y 5 estén mezclados. La línea base se toma con su instrucción final |
| `medir/puntuar.py`, `ejecutar.py` y `medir.sh` | dueño de la batería | 3.1 y 3.2 | Hacerlo después de su última medición de bloque. Lo nuevo va en ficheros nuevos (`resumir.py`, `repetir_consultas.py`, `importar_preguntas.py`) |
| `datos/bateria.json` y `generar/` | 2 (preguntas de casos parecidos, si las añade) | 3.3 y 4.3 | La reserva va en su rama aparte. Las preguntas de lista nuevas se añaden después de las suyas |
| `searchKnowledge` y `listDocuments` (MCP y API) | 2 (casos parecidos) | 4.1 y 4.3 | Después del bloque 2 |
| `question_record` y huecos | 4 | 4.1 | Comprobar que las pistas no cambian el recuento de huecos |
| Etiquetado (instrucción del modelo local) | 3, ya cerrado (D-058) | 4.4 | No choca. El bloque 4 (panel de temas) solo lee |

## 7. Consumo estimado

Calculado con lo ya medido: Haiku, 216 preguntas, unos 14 minutos, entre 18,7 y 19,2 millones de tokens (casi todo caché) y unos 1,2 USD de coste notificado, es decir, unos 0,006 USD por pregunta. La muestra de Sonnet cuesta entre 2,1 y 2,6 USD. Es el coste notificado dentro de la suscripción, no un precio.

| Medición | Ejecuciones | Tiempo | Coste notificado |
|---|---|---|---|
| Línea base (desarrollo) | 3 × 216 | unos 42 min | unos 3,7 USD |
| Primera de la reserva | 3 × 150 | unos 30 min | unos 2,7 USD |
| Sin instrucciones del servidor | 1 × 216 | unos 14 min | unos 1,2 USD |
| Intermedias de 4.1, 4.2 y 4.3 (fallos más 30 centinelas, hasta 2 intentos cada una) | unas 6 × 45 | unos 20 min | unos 1,6 USD |
| Escaneados por parejas | 2 × 3 × 30 | unos 12 min | unos 1,1 USD |
| Cierre (desarrollo, reserva y Sonnet) | 3 × 216, 3 × 150 y 30 | unos 80 min | unos 8,7 USD |
| **Total** | | **unas 3 h 20 min** | **unos 19 USD** |

- **Otras IAs:** depende de los precios de cada proveedor. Se empieza con una muestra de 30 preguntas.
- **Sin cuota de IA:** el repetidor, el etiquetado y el reordenador en repetición usan la pila y los modelos locales.
- **Construcción:** los subagentes que construyen gastan aparte. Haiku por defecto; Sonnet solo si Haiku falla dos veces en la misma tarea.

## 8. Lo que necesita del propietario

| Qué | Para qué | Cuándo |
|---|---|---|
| Imprimir y escanear unos 10 documentos ficticios (unas 30 páginas), con el escáner y con el móvil | Escaneados de verdad (3.4) | Antes de la demo |
| Entrevistas con 3 a 5 administradores, de 15 a 20 preguntas cada uno | Preguntas reales (3.5); ya están pendientes en `decisiones.md` | Antes de la demo |
| Archivo real anonimizado y validación legal (D-022) | Piloto (3.5) | Antes del primer cliente |
| Cuentas de API de OpenAI y Google, con un presupuesto | Otras IAs (3.6) | Cuando el CEO lo priorice |
| Plan de ChatGPT o de Gemini con conectores MCP, y un medio para exponer el MCP de prueba | Producto con conector (3.6) | Cuando lo admitan |
| Permiso para descargar un reordenador pequeño | Solo si se dispara 4.5 | Si hace falta |
| Cuota: unos 19 USD de coste notificado y unas 3 h 20 min de mediciones en todo el plan | Mediciones (7) | Repartido entre las fases |

## 9. Preguntas para el CEO

1. **Regla del mínimo con 3 ejecuciones.** Propongo que la peor de las tres supere el 90 %. La alternativa es exigirlo a la media.
2. **Objetivos de 3.7:** validarlos o cambiarlos.
3. **«No consta» con rastro (4.1).** Cambia lo que ve el trabajador: la respuesta dice qué se buscó. Recomiendo que sí.
4. **Citar cada dato (4.2).** Las respuestas llevan más citas y dicen cuándo algo es un cálculo o un metadato. Recomiendo que sí.
5. **Etiquetas de más frente a D-031.** Recomiendo tocarlas solo si ensucian las listas y sin bajar la exhaustividad.
6. **Disparadores del reordenador y de los vectores (4.5):** validar los umbrales (3 %, la mitad recuperada, 5 s).
7. **Reserva (3.3):** validar la regla de quemado y quién puede lanzarla. Propongo que solo el CTO que cierra una fase, con el registro de usos.

## 10. Lo que queda a criterio de quien lo construya

- Los nombres exactos de los ficheros y de las clases del puntuador, dentro de lo que dice 3.1.
- Las plantillas de los documentos nuevos de la reserva, siempre ficticias y generadas por programa.
- Cuáles son los 20 documentos de los escaneados sintéticos, con el criterio de 3.4.
- Las 30 centinelas, repartidas por tipo y con una semilla fija.
- La forma de `hints` en la respuesta de `searchKnowledge`, siempre que no revele nada fuera de la visibilidad del trabajador.
