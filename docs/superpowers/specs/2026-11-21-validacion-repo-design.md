# Validación en el repositorio antes de instalar (encargo 27, D-123): diseño y plan

- Fecha: 2026-11-21. Autor: CTO 3. Estado: **aprobado por el propietario el 21 nov. y construido** (albercv/odin#6 a #12, albercv/ocr#9).
- **Decisiones del propietario (21 nov.):**
  - el clon limpio con la opción (a): el generador en `datos-prueba/archivo-ficticio/`, y `sustituciones.py` y `cargar_expedientes.py` en `deploy/instalacion/`;
  - la rama de la instalación es un parámetro, `ODIN_RAMA_INSTALACION` o `RAMA=`, sin valor fijo;
  - sin vía de emergencia;
  - el lector en Linux con el backend de CPU solo en el runner, con la versión de `uv.lock` y solo la prueba de Metal fuera;
  - la forma A: un trabajo cuyos cuatro pasos se ejecutan siempre.
- **Medido** (suma de cada trabajo redondeada al minuto; no es la factura):
  - forma A: 13 minutos en frío y 9 con caché;
  - forma B: 13 en frío y 11 con caché;
  - el lector: 1 minuto por ejecución.
- **La primera actualización tras el merge** solo pasa por la puerta si antes se trae `develop` a la copia principal: el `desplegar.sh` instalado hoy no la tiene.
- Regla (D-123): se instala solo lo que ha pasado la validación en el repositorio. GitHub no deja proteger ramas en el repositorio privado sin plan de pago, así que la regla la impone la instalación.

## 0. Lo comprobado antes de diseñar (21 nov.)

- **`make check` en un clon limpio de `origin/develop` bc6384a2** (sin `experimentos/`), parte por parte:

  | Parte | Resultado | Tiempo en este Mac |
  |---|---|---|
  | API (`mvn verify`) | falla: `ArchiveSearchBatteryIT` y `CaseNumberBatteryIT` no encuentran el archivo ficticio. Fallan, no se saltan. El resto, unas 1.700 pruebas, pasa | 2 min 55 s (sin cortar en el primer fallo) |
  | MCP | pasa (123) | 33 s |
  | Panel | pasa (454) | 13 s |
  | Guiones (`deploy/instalacion`) | fallan 3 de 35: `datos_prueba.py` importa `sustituciones` de `experimentos/…/pila` | 6 s |
  | `parte_c` | no existe en un clon limpio | — |

- **El problema va más allá de las pruebas:** `datos_prueba.py`, que carga el archivo ficticio al desplegar, y `recuento.sh` leen `experimentos/`. Hoy un clon limpio no puede ni validar ni desplegar desde cero.
- **El archivo ficticio se puede regenerar** sin guardarlo en git:
  - lo hace `generar/generar.py`, con semilla 7, solo biblioteca estándar, sin red y sin IA;
  - ocupa 156 KB de código más 308 KB de `vendor/` y genera 8,6 MB en 1 segundo;
  - comprobado: reproduce `datos/` byte a byte. La única diferencia es `bateria-conector.json`, que no usa nada de `make check` ni del despliegue.
- **Linux:** las pruebas solo necesitan Docker con `postgres:17.10`, Java 21, Maven, Node 24 y Python 3.12. No usan órdenes de macOS ni rutas del Mac. `plutil` se salta solo en Linux; `AccessLinkIT` se salta si no hay IPv6 local.
- **Lector OCR:** `mlx` 0.32.4 tiene ruedas para Linux (x86_64 y aarch64) en `uv.lock`. Las pruebas rápidas (`-m 'not model'`) importan `mlx` en `test_env` y `test_guards`. Falta una ejecución de prueba en Linux.
- **Bloqueo:** GitHub rechaza el push de cualquier fichero de `.github/workflows/`: el token de `gh` de este equipo no tiene el permiso `workflow`. Sin él no se puede construir la validación en ningún repositorio.
- **Coste (documentación de GitHub, leída el 21 nov.):**
  - el plan gratuito incluye 12 USD al mes en repositorios privados, unos 2.000 minutos de Linux a 0,006 USD por minuto;
  - macOS cuesta 0,062 USD por minuto, unas 10 veces más;
  - sin forma de pago, Actions se bloquea al agotarse lo incluido;
  - el consumo real de la cuenta no se puede leer: el token tampoco tiene el permiso `user`.

## 1. Validación en GitHub Actions para `albercv/odin`

- **Fichero:** `.github/workflows/validacion.yml`.
- **Disparadores:** cada PR a `develop` o `main`, cada push a `develop` o `main`, y a mano (`workflow_dispatch`).
  - **Sin filtros de rutas en los push:** la puerta exige un resultado para cada commit instalable. Si un merge que solo toca documentación no tuviera ejecución, la puerta se negaría a instalarlo siempre.
- **Una sola verdad:** `make check` se parte en `check-api`, `check-mcp`, `check-panel` y `check-instalacion`. `make check` los llama todos, y Actions llama a los mismos objetivos. Así no hay dos listas que mantener ni que se separen con el tiempo.
- **Seguridad:**
  - permisos mínimos (`contents: read`), sin secretos y solo datos ficticios;
  - acciones de terceros fijadas por SHA, no por etiqueta;
  - un PR nuevo cancela la ejecución anterior del mismo PR; los push no se cancelan.
- **Cachés:** el repositorio de Maven del proyecto (`api/.m2`) y el de npm.
- **Forma, a decidir con lo medido:**

  | Forma | Ventaja | Inconveniente |
  |---|---|---|
  | A: un trabajo | menos minutos (un solo arranque) | más lento |
  | B: cuatro trabajos en paralelo | resultado antes | cada trabajo paga su arranque y se redondea por separado |

  Mido las dos, en frío y con caché, y el CEO elige.

## 2. Lo que falta en un clon limpio (decisión del CEO: toca D-122)

| Opción | Qué entra en git | Qué se pierde |
|---|---|---|
| **a (recomendada)** | el generador del archivo ficticio (`generar/` y `vendor/`, unos 460 KB, solo código y semillas ficticias) en `datos-prueba/archivo-ficticio/`, y `sustituciones.py` (70 líneas) en `deploy/instalacion/`. `datos/` se genera al vuelo y sigue ignorado | nada |
| b | `datos/` tal cual, 8,6 MB | nada, pero se versionan datos generados |
| c | nada | `ArchiveSearchBatteryIT` y `CaseNumberBatteryIT` en Actions, y el despliegue desde un clon limpio sigue roto |

- **Con a o b,** las pruebas, `datos_prueba.py` y `recuento.sh` apuntan a la ruta nueva.
- **`parte_c` sale de `make check`** en cualquier opción: prueba un guion de medición del experimento, no el producto. `make check` deja de hacer esas 3 pruebas.

## 3. Puerta en la instalación

- **Dónde:** un guion nuevo, `deploy/instalacion/puerta.py` (biblioteca estándar, con `gh` como cliente de GitHub), que llaman `instalacion-actualizar` e `instalacion-desplegar` cuando la pila es la del propietario (`odin`).
- **La rama es un parámetro:** `RAMA=develop` o `RAMA=main`; no admite otra. Propuesta: el valor sale de `ODIN_RAMA_INSTALACION` en `deploy/.env`. Si no está y no se pasa `RAMA`, la orden se niega y dice cómo fijarla. Así no queda fijada en el código.
- **Comprobación:**
  1. `git fetch origin <rama>`.
  2. El commit que se va a instalar tiene que estar en `origin/<rama>`.
  3. Tiene que existir una ejecución de `validacion.yml`, del evento push, de ese SHA exacto y terminada en verde. No vale cualquier comprobación verde: GitGuardian solo busca claves.
  4. Se instala ese SHA exacto, no lo que haya en la rama en ese momento.
- **Cerrada ante la duda:** se niega con «No se ha cambiado nada.» si falta `gh`, no tiene sesión, no hay red, la API falla, la validación no ha terminado o ha fallado.
- **Imágenes prehechas:** `CONSTRUIR=no` se niega en la pila del propietario, porque serían imágenes hechas desde otra worktree, sin validar.
- **Sin cambios:** las pilas de ensayo y `instalacion-restaurar` no pasan por la puerta (restaurar es la vuelta atrás).
- **Modo explicación:** sin `CONFIRMAR`, la orden dice qué commit instalaría y el veredicto de la puerta.
- **Pruebas:** con un `gh` falso que devuelve respuestas reales, capturadas de una ejecución de verdad. No hay ningún interruptor para saltar la puerta, tampoco en pruebas.
- **Vía de emergencia: no la construyo.** Opciones para el CEO:
  - **ninguna (recomendada):** si la validación falla por algo ajeno al código, se repite la ejecución en GitHub; si falla por el código, se arregla con un PR; restaurar ya sirve para volver atrás;
  - **una orden aparte solo para el propietario,** con una palabra de confirmación y su motivo escrito en la bitácora. Debilita la regla: el día que se usa, se instala algo sin validar.
- **Lo que la puerta no impide:** que se mergee un PR en rojo. Impide instalarlo hasta que un PR lo arregle.

## 4. Lector OCR (`albercv/ocr`)

- **Primero, una ejecución de prueba en Linux** de `uv sync` y las pruebas rápidas. Cuesta unos céntimos y depende del permiso `workflow`.
- **Si pasa:** validación en Linux en cada PR y push a `develop` y `main`, solo cuando cambia el código (`uocr/`, `scripts/`, `tests/`, `ocr.py`, `pyproject.toml`, `uv.lock`, `Makefile`). La mayoría de los PR de este repositorio son documentos de negocio, y aquí no hay puerta que exija una ejecución por commit.
- **Si no pasa, opciones:**
  - macOS de GitHub: unas 10 veces más caro, y sus máquinas no dan Metal;
  - un runner propio en el Mac del propietario: gratis y con Metal, pero instala un servicio en su equipo, así que hace falta su permiso;
  - en Linux, solo las pruebas que no importan `mlx`.
- Las pruebas con el modelo (`-m model`) siguen siendo locales en todos los casos.

## 5. Medición

- **Cuánto tarda:** duración de cada ejecución, en frío y con caché, para las formas A y B, leída de la API de GitHub.
- **Cuánto gasta:** minutos facturables, de la API de ejecuciones. Hago la cuenta con 12 USD al mes para dar cuántas validaciones caben al mes.
- **El consumo real de la cuenta** solo se puede leer con el permiso `user`.

## 6. Documentación

- **README de Odín:** lo actualizo yo.
- **`CLAUDE.md` de los dos repositorios y el prompt del CTO:** las normas de esta sesión no me dejan cambiarlos por petición de otra sesión. Dejo el texto propuesto en el PR, y los cambia el propietario o me lo pide él directamente en esta sesión.

## 7. Plan (tras el visto bueno)

| Tarea | Rama | Qué | Cómo se verifica |
|---|---|---|---|
| 1 | `task/validacion-repo/clon-limpio` | la opción del apartado 2; `make check` partido en objetivos; `parte_c` fuera | `make check` en verde en un clon limpio |
| 2 | `task/validacion-repo/actions` | `validacion.yml`; un disparador temporal en la rama de la feature para capturar una ejecución de push real (se quita antes del PR final); PR en borrador de la feature a `develop` | ejecuciones en verde de PR y de push; medidas de las formas A y B |
| 3 | `task/validacion-repo/puerta` | `puerta.py` y su llamada desde `desplegar.sh` | pruebas con el `gh` falso: en verde, en rojo, pendiente, sin red, sin sesión, SHA fuera de la rama, `CONSTRUIR=no`; ensayo en una pila desechable sin tocar la del propietario |
| 4 | en `albercv/ocr` | ejecución de prueba en Linux y la validación que salga | ejecución en verde y su medida |
| 5 | `task/validacion-repo/documentos` | README y el texto propuesto para `CLAUDE.md` y el prompt del CTO | revisión de texto |

- **Lo que no se puede comprobar antes del merge:** la primera ejecución de push sobre un SHA real de `develop` solo existe después de mergear el PR. La puerta se prueba con la ejecución capturada en la rama de la feature.
