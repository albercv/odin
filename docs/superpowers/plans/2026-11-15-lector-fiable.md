# Lector fiable — plan de implementación

> Ejecución con subagentes, sin rondas de revisión (instrucción del propietario del 2026-11-15). Cada tarea se verifica con sus pruebas y la feature con `make check`.

**Goal:** que lo subido a mano se lea antes que la primera carga y que el puente del lector arranque solo.

**Spec:** `docs/superpowers/specs/2026-11-15-lector-fiable-design.md`

## Global Constraints

- Cada tarea va en `task/lector-fiable/<tarea>`, que sale de `feature/lector-fiable`, y se fusiona con `--no-ff`. Sin push.
- Commits en español, sin atribución a Claude ni `Co-Authored-By`.
- TDD. Métodos y funciones de 30 líneas como máximo. Identificadores en inglés. Comentarios mínimos.
- Sin migración, salvo que una medición lo pida (sería la V31).
- No instalar el agente de launchd ni llamar a `launchctl` de verdad, ni en las pruebas ni a mano. No tocar `~/Library/LaunchAgents`.
- No tocar el puente que está en marcha (8077), ni la pila del propietario, ni `deploy/.env`. No usar los puertos 8080, 8088 ni 8090.
- No hacer `mvn install`.

## Review Focus

1. Sin documentos subidos a mano pendientes, la primera carga sigue avanzando por orden de llegada.
2. Un documento subido a mano que llega con la primera carga a medias sale en el siguiente reclamo, en las dos colas.
3. `lector-arranque-instalar` sin `CONFIRMAR=ARRANQUE`, o con un `LECTOR_DIR` sin `serve.py`, no escribe nada y explica qué falta.
4. Si `lector-arranque-quitar` se lanza sin el agente instalado, no falla: dice que no había nada.

---

### Task 1: prioridad de lo subido a mano

Rama: `task/lector-fiable/prioridad`.

**Files:**
- `api/odin-adapter-postgres/.../PostgresDocumentRepository.java`: el `ORDER BY` de `claim()`.
- La cola de etiquetado (`TagPendingDocuments` y su consulta de reclamo): el mismo orden, si reclama documentos por `seq`.
- Las pruebas de integración del repositorio que ya prueban `claim`.

**Tests**, en el IT del repositorio:
- Primero un documento de primera carga (`seq` menor) y después uno subido a mano: `claimNextForProcessing` devuelve el subido a mano. Lo mismo con `claimNextForReader` en `PENDING_READER`.
- Dos documentos de primera carga y ninguno a mano: salen por `seq`.
- Si hay cola de etiquetado FIFO, la misma prueba allí.

- [ ] Las pruebas fallan → cambio → pasan, con el resto de pruebas del adaptador de Postgres → commit.

### Task 2: arranque automático del puente del lector

Rama: `task/lector-fiable/arranque`.

**Files:**
- `deploy/instalacion/arranque_lector.py` (nuevo) y `deploy/instalacion/test_arranque_lector.py` (nuevo).
- `Makefile`: `lector-arranque-instalar`, `lector-arranque-quitar` y `lector-arranque-estado`.
- `README.md`: la sección del lector, que ahora dice «arranca el puente en el Mac… `make serve`».
- `deploy/instalacion/desplegar.sh`: el aviso de «el puente OCR no responde» sugiere `make lector-arranque-instalar`.
- La orden manual que hay que replicar está en `docs/informes/2026-11-13-instalacion-propietario.md:227-287`.

**Interfaces:**
- Etiqueta `com.e2d.odin.puente-ocr`.
- `LECTOR_DIR` por defecto `<carpeta de proyectos>/OCR/.worktrees/puente-odin`, la que usa hoy el propietario. Se calcula desde la copia principal de Odín: `../OCR/.worktrees/puente-odin`.
- Log en `<odin>/data/puente-ocr.log`.
- Las pruebas inyectan `ODIN_LAUNCH_AGENTS_DIR` y una función `run`, que sustituye a `launchctl` y a `lsof`.

**Tests** (`unittest`):
- El plist generado tiene:
  - `Label`;
  - `ProgramArguments` = [ruta absoluta de uv, `run`, `python`, `serve.py`, `--quant`, `8`];
  - `WorkingDirectory`;
  - `EnvironmentVariables`, con `HF_HUB_OFFLINE=1`, `UV_OFFLINE=1` y un `PATH` que incluye la carpeta de uv;
  - `RunAtLoad` y `KeepAlive` a true, y `ThrottleInterval` 30;
  - `StandardOutPath` y `StandardErrorPath` en el log.
- En macOS, `plutil -lint` da OK sobre el plist generado.
- Sin `CONFIRMAR=ARRANQUE`, o sin `serve.py`, o sin los pesos: código distinto de 0, ningún fichero escrito y un mensaje que dice qué hacer.
- Instalar, con un `run` falso:
  - escribe el plist en la carpeta inyectada;
  - llama a `bootstrap gui/<uid> <plist>`;
  - con un `serve.py` escuchando en el 8077 (el `lsof` falso lo devuelve), lo para antes.
- Quitar: llama a `bootout gui/<uid>/com.e2d.odin.puente-ocr` y borra el plist. Sin plist, sale con 0 y dice «no estaba instalado».
- Estado: lee la salida de `launchctl print` (falsa) y la de `/health`.

- [ ] Las pruebas fallan → implementación → `python3 -m unittest discover -s deploy/instalacion -p 'test_*.py'` en verde → commit.

### Cierre

Merge de las dos tareas, `make check` en la feature, `develop` mezclado y pilas de prueba bajadas.
