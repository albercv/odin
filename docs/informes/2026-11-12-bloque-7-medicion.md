# Informe: bloque 7, documentos sustituidos y filtro con lo general

- Fecha: 2026-11-12. Autor: CTO 2. Bloque 7 de la fase 2, con D-050 y D-053.
- Odín medido: `feature/fase-2-conocimiento` con el bloque 7. La batería es la misma: el archivo del encargo 12, con 216 preguntas.
- Límite: datos ficticios y una ejecución por medición. Las cifras no son una promesa.

## 1. Qué se construyó

- **La marca de sustituido.** El administrador marca un documento como sustituido por otro y puede quitar la marca.
  - Se guarda con la migración `V11`.
  - Rutas `PUT` y `DELETE /v1/admin/documents/{id}/superseded-by`.
  - Reglas:
    - el sustituto tiene que ser de la misma empresa, también para el administrador del grupo;
    - tiene que estar en el mismo proyecto o no tener proyecto;
    - tiene que estar `READY`;
    - la cadena no puede ser circular.
  - La base de datos lo exige por su cuenta: una clave foránea compuesta con la empresa y la condición en la actualización.
  - Al marcar, los dos documentos se bloquean.
- **Lo que ve el trabajador.**
  - Los sustituidos salen detrás de los vigentes y llevan `superseded` y `replacement`, que es el vigente del final de la cadena.
  - El vigente se resuelve con la visibilidad del que lee, bajo el filtro y la seguridad por filas, y cada salto de la cadena va atado a la empresa del documento.
- **Filtro de D-053.** Al filtrar por un proyecto, la búsqueda devuelve también lo que no tiene proyecto.
- **Panel.** La marca «Sustituido», el diálogo para marcar con su ayuda y su confirmación, el detalle del documento y la demo con una póliza renovada.
- **Revisión de aislamiento con Opus.** No encontró nada grave. Se aplicaron sus refuerzos y las pruebas que faltaban. También salieron de git 171 ficheros de Maven que habían entrado por error.
- **Comprobaciones:**
  - `make check` y `make demo` terminan con 0;
  - `SearchBatteryIT` da 27/30;
  - `ArchiveSearchBatteryIT` da 96/131, ahora con las 55 marcas puestas y sin que ningún sustituido salga delante de un vigente.

## 2. Mediciones (Haiku, archivo completo)

| Medición | Correctas | % | Estricto | «No consta» falsos | Sustituidos como vigentes | Citas inventadas | Fugas |
|---|---|---|---|---|---|---|---|
| Partida (encargo 12) | 146 | 67,6 | 43,1 | 35 | 7 | 36 | 0 |
| Bloque 6 | 196 | 90,7 | 57,4 | 12 | 2 | 0 | 0 |
| Bloque 7, primera instrucción | 183 | 84,7 | 49,1 | 24 | 3 | 0 | 0 |
| **Bloque 7, instrucción corregida** | **200** | **92,6** | **57,9** | **6** | **3** | **0** | **0** |

- **Por tipo, con la instrucción corregida:**

| Tipo | Correctas |
|---|---|
| Ficha | 25/25 |
| Sin fuente | 19/19 |
| Vocabulario | 34/34 |
| Casos parecidos | 13/13 |
| Identificador | 10/10 |
| Aislamiento | 16/17 |
| Contradicción | 14/16 |
| Versión | 41/46 |
| Dato concreto | 24/28 |
| Lista completa | 4/8 |

- **Sonnet, muestra de 30 preguntas:** 28 (93,3 %). En el bloque 6 sacó 29. Haiku, en esa misma muestra: 28.

## 3. Por qué bajó con la primera instrucción y qué se cambió

- **El diagnóstico** repitió contra la pila las búsquedas exactas que hizo Haiku. Está en `experimentos/.../resultados/2026-11-12-haiku-bloque7/diagnostico.md`.
- **De las 21 preguntas que empeoraron, 11 fueron por la instrucción nueva**: «si dos fuentes no cuadran y ninguna está marcada, enseña las dos y no elijas».
  - Haiku la aplicaba también a un anexo que modifica una cláusula o a un acuerdo posterior de la junta.
  - En esos casos no elegía y marcaba «no consta», aunque tenía la fuente entre los 5 primeros resultados.
- **El filtro de D-053 no quitó ninguna respuesta:** con el filtro antiguo se habrían recuperado 0 de las 21.
- **Las otras 10 fueron variación de la IA** entre ejecuciones.
- **Cambio aplicado (pendiente de tu validación, apartado 5).** La instrucción dice ahora: «Si dos fuentes no cuadran y ninguna está marcada como sustituida: si una dice que cambia a la otra (un anexo que modifica una cláusula, un acuerdo posterior de la junta), da lo que vale ahora y cita las dos; si ninguna lo dice, enseña las dos, cada una con su cita y su fecha, y no elijas ninguna.»
- **El formato de salida de la batería aclara** que `no_lo_se` solo vale cuando no consta. Antes no lo explicaba.
- **Con los dos cambios:**
  - de 183 a 200 correctas;
  - «no consta» falsos, de 24 a 6;
  - contradicciones, de 11/16 a 14/16.

## 4. Lo que queda

- **Lista completa (4/8).** Las cuatro que fallan dan la lista entera (exhaustividad 1,0).
  - Q095 y Q096 citan otra página del caso.
  - Q041 no sigue el orden pedido.
  - Q097 marca `no_lo_se` aunque da la lista.
  - Es forma de citar, no búsqueda. En el bloque 6 salieron 8/8.
- **3 sustituidos como vigentes** (Q024, Q047 y Q125).
- **6 «no consta» falsos.** Son de búsqueda: la fuente no sale con las consultas que hace la IA.
- **Variación entre ejecuciones** (lo que pediste). Sin gastar una ejecución más no se puede aislar, porque la IA nunca repite las mismas consultas. El diagnóstico lo estima en unas 5 a 10 preguntas que cambian de resultado en cada sentido entre una ejecución y otra. Con ese ruido, 200 de 216 deja un margen de unas 5 preguntas sobre el mínimo (195).

## 5. Para el CEO

- **Validar la instrucción nueva.** Precisa una regla de negocio de D-049: «si dos fuentes no cuadran, enseña las dos y no elijas».
  - Un documento que dice que cambia a otro (un anexo, un acuerdo de la junta) se aplica.
  - Solo cuando ninguno lo dice se enseñan los dos sin elegir.
  - Así lo esperaban también las claves del archivo.

## 6. Máquina, tiempo y consumo

- **Máquina:** Mac15,11, Apple M3 Max, 36 GB, macOS 27.0.1, Docker 29.8.2.
- **Haiku, archivo completo:** unos 14 min cada medición (13 min 52 s la última), 18,7 a 19,2 millones de tokens y 1,22 a 1,25 USD de coste notificado. Se hicieron dos.
- **Sonnet, muestra:** 1,7 millones de tokens y 2,27 USD de coste notificado.
- **Todo con la suscripción.**
