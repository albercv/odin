# Odín · Primera carga de principio a fin: medición

- Fecha: 2026-11-14. Tarea 11 del plan `2026-11-13-primera-carga`. Rama `task/primera-carga/medicion`, que sale de `feature/primera-carga` (a2217d8).
- **Qué se ha medido:** el recorrido completo de la primera carga sobre el archivo ficticio revuelto del estudio, en una pila desechable, con la orden V3 y con el lector OCR y el Ollama locales. Nada ha salido del equipo (D4); no se ha usado `claude -p` ni ninguna IA externa (D-094).
- **Equipo de todas las cifras:** Apple M3 Max, 36 GB, macOS 27.0.1, Docker 29.8.2.
- **Modelos:** lector Unlimited-OCR 2, 8 bits (`mlx-community/Unlimited-OCR-2-8bit`, `uocr 0.1.0`), por el puente del repositorio OCR en `127.0.0.1:8077`; etiquetado y propuesta de proyecto con `qwen3.5:9b` (Q4_K_M) en el Ollama local, con `ODIN_TAGGER_WINDOW=always`.
- **Ejecuciones:** 2 recorridos completos, cada uno sobre una pila nueva (`down -v` y arrancar entre uno y otro). Donde las dos coinciden se dice «2 de 2». La prueba automática pequeña se ha ejecutado 1 vez en su versión final.
- **Datos:** `experimentos/2026-11-13-primera-carga/parte_c/resultados/medicion-1.json` y `medicion-2.json` (con las muestras de memoria sin resumir).

## 0. En pocas líneas

- **El recorrido funciona de principio a fin y sin fugas (D1).**
  - Subida cortada con `SIGKILL` y relanzada: 845 documentos distintos y 845 SHA-256 distintos en la base, ninguno duplicado (2 de 2).
  - Cero fugas con la clave de cuatro trabajadores, antes y después de confirmar, en lista, lectura, búsqueda, enlace al original y cita verificada (2 de 2).
- **Las cifras del estudio se cumplen salvo una:** los escaneos enlazados tienen precisión 1,00 pero cobertura 0,80 (16 de 20), no 1,00. Es por diseño del clasificador, no un fallo de ejecución: ver el apartado 3.
- **El tiempo lo pone el OCR,** como decía el estudio: 231 s de unos 275 s del recorrido de la orden a la última confirmación.
- **Memoria:** el OCR y el modelo, cargados a la vez, suman unos 14,7 GB de huella física. El estudio decía «más de 12 GB».
- **Defectos de producto:** ninguno que impida el recorrido. Hay tres hallazgos menores y una brecha frente al criterio de aceptación (apartado 6).

## 1. Cómo se ha medido

| Pieza | Qué es |
|---|---|
| Pila | Proyecto Compose `odin-primera-carga`, puertos 48080, 48088 y 48090, código de esta worktree. `experimentos/2026-11-13-primera-carga/parte_c/pila.sh up` y `down`. Pasa la guarda D-099 de las órdenes de instalación (`comun.sh`) sin tocarla. No se ha usado ni un puerto de la instalación del propietario ni `odin-ensayo` |
| Recorrido | `parte_c/medir.py`: primer arranque, orden V3, lectura, descubrir y confirmar proyectos, clasificación, propuestas frente a la verdad, D1, confirmar por grupos, colocar lo que queda, D1 |
| Orden V3 | `make primera-carga CARPETA=… ARGS="--si --correo …"`, la de verdad, con la contraseña por la entrada estándar |
| Corte | `SIGKILL` al grupo de procesos de la orden al llegar a 400 ficheros subidos; después, la misma orden otra vez |
| Verdad | `trabajo/verdad.json`. Solo sirve para medir y para simular a la persona que decide; el clasificador no la ve |
| Memoria | `footprint` de macOS (memoria física, GPU incluida) cada 1 s sobre el proceso del puente OCR y sobre los de Ollama |
| Tiempos | Reloj de pared por fase. La lectura y la clasificación se sondean cada 5 s, así que su resolución es de ±5 s |

- **Archivo:** 885 ficheros (855 de texto y 30 PDF escaneados, 55 páginas), 258 MB. Hay 40 copias exactas con otro nombre: quedan 845 documentos distintos.
- **Empresa:** «Administraciones Ribera (ficticia)», vacía al empezar, con su administrador de empresa, tres trabajadores (t1, t2 y t3) y otra empresa con un trabajador (t4). t1 y t2 entran en los dos proyectos con más documentos justo antes de confirmar; t3 no tiene proyectos.
- **Lista de proyectos:** descubierta por Odín en los textos y confirmada entera (60 de las 61 comunidades de la verdad; falta «Procedimientos generales», que no es una comunidad, igual que en el estudio).

## 2. Tiempo por fase

Segundos de reloj de pared. Las dos ejecuciones coinciden dentro de la resolución de medida.

| Fase | Ejecución 1 | Ejecución 2 | Comentario |
|---|---|---|---|
| Primer arranque y alta de empresas y trabajadores | 0,9 | 0,9 | |
| Subida cortada y relanzada (orden V3) | 6,9 | 6,8 | Corte a los 2,5 s con 409 a 415 documentos; relanzada, 4,0 s |
| Lectura (30 escaneos, 55 páginas, y 815 textos) | 231 | 231 | ±5 s. 4,2 s por página escaneada; el estudio dio 4,3 s por página (238 s), 1 ejecución |
| Descubrir y confirmar proyectos | 0,2 | 0,2 | |
| Clasificación (reglas y modelo para lo que queda) | 35 | 35 | ±5 s. Incluye cargar el modelo. Estudio: 5,8 s las reglas más 1,29 s por documento del modelo |
| Confirmar por grupos (64 clics, 1.132 propuestas) | 1,3 | 1,3 | |
| Colocar los 19 documentos sin ubicar | 0,1 | 0,1 | |
| Comprobación D1 antes de confirmar | 28,5 | 28,5 | 845 documentos por 4 trabajadores en lista, lectura y búsqueda, más una muestra de 106 en enlace y cita |
| Comprobación D1 después de confirmar | 40,1 | 39,4 | |

- **Total de la orden a la última confirmación,** sin las comprobaciones D1: unos 275 s en las dos ejecuciones (subida 7 s, lectura 231 s, clasificación 35 s, confirmar y colocar 1,4 s).
- **Qué pasa en un archivo real** no se deduce de aquí: el OCR de 6.000 páginas sigue siendo del orden de horas (el cálculo del estudio, 4,3 s por página, no es una medida de esta tarea).

## 3. Acierto frente a la verdad y frente al estudio

Sobre los 845 documentos del lote, con la propuesta tal como sale de la API, antes de que nadie confirme. Mismo resultado en las 2 ejecuciones.

| Clase | Medido | Estudio | Criterio del spec (apartado 9) |
|---|---|---|---|
| Proyecto, confianza alta | 826 propuestos, 826 correctos, 0 errores. Con el proyecto en la lista (826 documentos): cobertura 1,00 | 810 de 810 con confianza alta | 100 % de acierto. Cumple |
| Proyecto, sin propuesta | 19 sin propuesta: 12 de toda la empresa (normas) y 7 «Procedimientos generales» (no está en los candidatos). 0 propuestas de confianza baja | 15 sin proyecto con reglas | |
| Ficha de caso y su número | 241 de 241 fichas reales; 241 de 241 números; 0 falsas | 239 de 239 | 100 %. Cumple |
| Tipo de documento | No se mide: el producto no propone tipo (el spec lo deja solo para proponer) | 825 de 825 | Ver «Lo que no salió» |
| Copias exactas | 40 ficheros repetidos que no entran: 845 documentos y 845 SHA-256 distintos en la base | 1,00 y 1,00 | Cumple |
| Escaneo de un documento digital | **Precisión 1,00 (16 de 16), cobertura 0,80 (16 de 20)** | 1,00 y 1,00 (21 de 21) | 1,00 y 1,00. **No cumple en cobertura** |
| Versión nueva (sustituye a) | Precisión 0,837 y cobertura 0,837 (36 de 43 pares) | 0,83 y 0,83 (38 de 46) | 0,83 o más en precisión. Cumple |
| Enlace a ficha de caso | 6 propuestos | Sin medir | Se mide con el archivo ampliado del encargo 13. **Sin medir aquí** (no hay verdad de enlaces) |

- **Por qué la cobertura de escaneos es 0,80.**
  - Los 4 escaneos no enlazados son los de documentos sin proyecto propuesto: `scan-0001`, `scan-0002` y `scan-0003` (normas, sin proyecto) y `scan-0030` (procedimiento de «Procedimientos generales», que no salió como candidato). Comprobado en la base de la segunda ejecución: los 14 escaneos sin enlace son esos 4 y los 10 que no tienen original en la carpeta.
  - `ClassifyIntake` solo compara entre documentos con un único proyecto propuesto (spec, `SAME_AS_SCAN` y `SUPERSEDES` «solo entre documentos del mismo proyecto propuesto»). No he comprobado cómo trataba el estudio estos casos. Es una decisión de diseño con una consecuencia medida: hay que decidir si se acepta, o si los documentos sin proyecto se comparan en su propio grupo.
- **Versiones:** 7 de las 43 propuestas no están en la verdad. El estudio tuvo 8 falsos positivos y los atribuyó a cadenas de pólizas de 2022, 2024 y 2025 de una misma comunidad que la verdad no anota; aquí no se han revisado una a una, así que esa explicación no está comprobada para estos 7.
- **El modelo local** (`qwen3.5:9b`) no ha dado ninguna propuesta de confianza baja. La memoria del Ollama pasó de 45 MB a unos 7,7 a 8,0 GB durante la clasificación en las 2 ejecuciones, así que se cargó, pero no hay registro de lo que contestó a cada documento. Con «Procedimientos generales» fuera de la lista no podía acertar los 7 procedimientos, y para las 12 normas la respuesta correcta era ninguna. No se puede comparar con el «14 de 15» del estudio, que sí tenía ese proyecto en la lista.
- **Trabajo de la persona,** en el archivo ficticio: 64 clics de grupo (60 de proyecto y uno de ficha de caso, versiones, escaneos y enlaces) y 19 documentos uno a uno. El estudio estimaba 60 clics, 15 documentos y 67 parejas por revisar.
  - En esta simulación se confirmaron los grupos de versiones y de escaneos enteros, sin revisar pareja a pareja, y entraron así 7 sustituciones que la verdad no anota. Es lo que el estudio ya advertía: las parejas se revisan, no se confirman a ciegas.

## 4. Memoria

Huella física (`footprint`) de macOS, que incluye la memoria de la GPU. Máximos y mínimos de las muestras de cada ejecución.

| Qué | Ejecución 1 | Ejecución 2 | Estudio |
|---|---|---|---|
| Puente OCR en reposo, con el modelo cargado | 4,5 GB | 4,0 GB | |
| Puente OCR, pico leyendo escaneos | 7,8 GB | 7,8 GB | 7,2 GB de pico en MLX (otra forma de medir) |
| Puente OCR tras la lectura | 4,5 GB | 4,6 GB | |
| Ollama con `qwen3.5:9b`, pico | 7,7 GB | 8,0 GB | 5,5 GB en la GPU (`ollama ps`); RSS de 7,2 a 10,6 GB |
| OCR y Ollama a la vez, pico de la suma | 14,7 GB | 14,8 GB | «Juntos pasan de 12 GB» |

- **Se solapan:** al empezar la lectura, Ollama aún tenía el modelo cargado (su permanencia por defecto) mientras el OCR subía. `ClassifyIntake` impide *usar* los dos a la vez, no que estén *residentes* a la vez.
- **El puente OCR no es de esta pila.** Es el mismo proceso que usa la instalación del propietario: su memoria en reposo y su pico pueden incluir lo que otro cliente le pida. En estas dos ejecuciones no se ha podido comprobar que estuviera libre.

## 5. D1: aislamiento con claves de trabajadores

Por la API, en las dos ejecuciones, con 845 documentos del lote. Cada trabajador, sobre todos los documentos: lista (`GET /v1/documents`), lectura de la página 1, y búsqueda con el texto del propio documento. Sobre una muestra de 106 (uno de cada 8): enlace al original y cita verificada.

| Trabajador | Antes de confirmar: ve | Después: debe ver | Después: ve | Fugas |
|---|---|---|---|---|
| t1, de un proyecto de la empresa | 0 | 29 en lista y búsqueda, 30 legibles | 29, 29 y 30 | 0 |
| t2, de otro proyecto | 0 | 28 en lista y búsqueda, 30 legibles | 28, 28 y 30 | 0 |
| t3, de la empresa y sin proyectos | 0 | 12 (lo de toda la empresa) | 12 | 0 |
| t4, de otra empresa | 0 | 0 | 0 | 0 |

- **Lo esperado se calcula con la base, no con la verdad:** lo que quedó en el proyecto del trabajador y lo de toda la empresa, sin lo retirado ni lo pendiente. Los escaneos enlazados a su digital (D3) no salen en lista ni búsqueda, pero se leen por su identificador: por eso 29 y 30. Faltas: 0 en lista, lectura y búsqueda.
- **Antes de confirmar,** con 845 documentos pendientes y t1 y t2 ya en sus proyectos: 0 en lista, 0 legibles, 0 en búsqueda, 0 enlaces al original, 0 citas verificadas.
- **Prueba automática repetible** (`IntakeFirstLoadIT`, en `make check`): el mismo recorrido con un subconjunto de 12 ficheros del archivo (semilla 13), con una base real y la clave de tres trabajadores y otra empresa. Comprueba D1 antes de confirmar y después de cada grupo, y que un proyecto cuyo grupo no se ha confirmado sigue oculto para su trabajador.
- **La RLS sola, sin el filtro SQL,** y los temas y el expediente no se han vuelto a medir aquí: los cubren las pruebas de las tareas anteriores (`IntakeSecurityIT`, `IntakeConfirmationIT`). `findSimilarCases` y el expediente no se han recorrido con el archivo completo en esta tarea.

## 6. Lo que no salió como esperaba

1. **Cobertura de escaneos de 0,80, no 1,00** (apartado 3). Por diseño, pero incumple el criterio literal del spec. Hace falta una decisión de quien lo definió.
2. **Un `SIGKILL` deja el cerrojo de la instalación.** `data/pilas/<proyecto>/instalacion.lock` queda sin borrar porque `cerrojo.sh` limpia con un `trap` que un `SIGKILL` no ejecuta. La propia orden dice cómo quitarlo (`rmdir`). Con Ctrl-C no ocurre. Se ha borrado a mano en las dos ejecuciones antes de relanzar. No lo he tocado.
3. **Nombres descubiertos con la mayúscula del texto.** La prueba pequeña lo enseñó: se descubre «calle Olmo 47» si el texto lo trae así. El administrador confirma el nombre tal cual. Es cosmético, pero crea un proyecto con ese nombre.
4. **La orden no se puede relanzar después de cerrar la subida.** Con el lote ya en lectura, la orden contesta «Ya hay una primera carga en curso» y no sube nada. Es lo que dice su texto y lo esperado, pero significa que reanudar solo vale hasta cerrar la subida. Lo vi en una prueba previa con una carpeta pequeña, que terminó antes de poder cortarla.
5. **La orden pide la contraseña con `getpass`** y, sin terminal, avisa de que puede mostrarla («Password input may be echoed»). Solo ocurre sin terminal, que es mi montaje de medida.
6. **Sin medir:** enlace con expedientes (6 propuestas, sin verdad), tipo de documento (el producto no lo propone), un archivo real, escaneos malos y la lectura del modelo documento a documento.
7. **Un solo equipo y un puente compartido:** las cifras son de este Mac con el puente del propietario; no valen para el equipo de referencia del cliente (T-010).

## 7. Cómo repetirlo

1. Con el puente OCR y Ollama en marcha: `experimentos/2026-11-13-primera-carga/parte_c/pila.sh up`.
2. `python3 experimentos/2026-11-13-primera-carga/parte_c/medir.py --salida medicion-N.json` (escribe en `parte_c/resultados/`).
3. `experimentos/2026-11-13-primera-carga/parte_c/pila.sh down` (`down -v`).
4. La prueba pequeña va en `make check` (`IntakeFirstLoadIT`; los recursos salen de `parte_c/subconjunto_extremo_a_extremo.py`) y las pruebas de las funciones que miden, en `parte_c/test_medir.py`.
