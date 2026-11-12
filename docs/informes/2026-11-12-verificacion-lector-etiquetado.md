# Verificación real del lector OCR y del etiquetado de Odín (D-043)

Fecha: 2026-11-12. Rama `task/fase-2-conocimiento/temas-bateria-real`. Solo documentos de `samples/` del repositorio OCR; nada de `private/`.

## Máquina

- `hw.model` Mac15,11, chip Apple M3 Max, memoria 38 654 705 664 bytes (36 GB).
- macOS 27.0.1 (compilación 26A434).
- Ollama del equipo, `qwen3.5:9b`, 100 % GPU (`size_vram` igual a `size`). Puente OCR: Unlimited-OCR 2, pesos de 8 bits, MLX y Metal.

## Montaje

- Puente OCR en `127.0.0.1:8077`, sin secreto, arrancado con `make serve` sin red (`UV_OFFLINE`, `HF_HUB_OFFLINE`). Carga del modelo: 1,5 s (caché caliente).
- Pila Compose `odin-verificacion` (API 38080, panel 38088, MCP 38090) con las imágenes ya construidas, sin `--build` (D-054). `ODIN_READER_URL=http://host.docker.internal:8077`, `ODIN_TAGGER_URL=http://host.docker.internal:11434`, `ODIN_TAGGER_WINDOW=always`.
- Empresa de prueba y tres temas ficticios (facturas, identificacion, otros).

## Lector de extremo a extremo

- `factura_scan.pdf` (1 página) y `es_simple_png.png` (1 página) subidos por la API: los dos quedaron `READY`, con una página con texto cada uno (952 y 911 caracteres), sin páginas sin texto. El contenedor de la API alcanzó el puente por `host.docker.internal` sin cambiar `--host`.
- Segundos por página (registro del puente): 4,5 s (555 tokens) y 2,8 s (343 tokens). Solo dos páginas, una ejecución cada una: no es una media fiable.
- Pico MLX del puente: 5,4 GB en las dos lecturas. RSS del proceso Python del puente: unos 125 MB (la memoria de Metal no cuenta en el RSS).
- El etiquetado de la pila terminó los dos documentos (`DONE`) con temas de la IA: `facturas` e `identificacion` para la factura, `otros` para el PNG. Su tiempo por documento no queda registrado en la base ni en el registro de la API, así que no se mide aquí; la cifra por documento es la de la batería.
- No se verificó el hallazgo de un importe con cita y página por el MCP, ni el escaneado de 30 páginas (`long_report`) de la tarea 8: fuera de este encargo.

## Memoria con los dos modelos cargados

| Momento | Dato |
|---|---|
| Antes de arrancar nada de esto | 89 % libre (`memory_pressure`) |
| Puente cargado y parado | 62 % libre |
| Puente cargado y Ollama con `qwen3.5:9b` | 43 % libre; en la batería, mínimo 28 % |
| Ollama (`ollama ps`) | 5,5 GB, 100 % GPU, contexto 4096 |
| Ollama (RSS de `llama-server`) | 7,2 GB, y un máximo de 8,8 GB durante la batería |
| Puente (pico MLX) | 5,4 GB |
| Suma de los dos | unos 14 GB (5,4 + 8,8), por debajo de 24 GB |

- Nivel de presión del sistema (`kern.memorystatus_vm_pressure_level`): 1 (normal) durante la lectura y 2 (aviso) al final de la batería. Nunca llegó a crítica (4).
- Swap en uso al terminar: 5,5 GB de 6 GB, con otros procesos del equipo en marcha (otras pilas Docker, otras sesiones); no se aisló qué parte es de este montaje.
- Como no hubo presión crítica ni se pasó de 24 GB, no se repitió con `qwen3.5:4b-q4_K_M`.

## Batería del etiquetado con el modelo real (`qwen3.5:9b`)

`cd api && mvn -o -q -am -pl odin-adapter-tagger-ollama verify -Dgroups=model -DexcludedGroups=`, con el puente cargado. Salida íntegra en `api/odin-adapter-tagger-ollama/target/tagging-battery.json`. Terminó con éxito.

| Conjunto | Fichas | Exhaustividad global | Fichas con etiqueta de más | s/documento (medio, máx.) |
|---|---|---|---|---|
| control-1 | 8 | 100 % | 1 | 2,7 y 2,9 |
| control-2 | 8 | 100 % | 0 | 2,9 y 3,1 |
| control-3 | 30 | 97,6 % (convivencia 85,7 %: 6 de 7; el resto 100 %) | 16 | 2,8 y 3,3 |

- Documentos largos: 23, todos de un solo tramo salvo alguno de dos (máximo 2), 0 tramos partidos por recorte, máximo 4,7 s.
- Etiquetas de más en control-3: 16 de 30 fichas, que es mucho. Es una cifra a comparar con T-010 con el mismo modelo, como pide el paso 3 de la tarea 6; esa comparación no se ha hecho aquí.

## Pendiente

- Verificación con la copia de la instalación del propietario: pendiente de su permiso. No se ha tocado.
- Comparación con T-010 (exhaustividad y etiquetas de más) y prueba con `qwen3.5:4b-q4_K_M`, con el puente cargado.
- Lectura de 30 páginas escaneadas y hallazgo de un importe con cita y página (tarea 8, punto 3).
- Medida de memoria en un equipo modesto: este es un M3 Max de 36 GB. Los 14 GB de pico caben, pero el swap indica que la máquina ya estaba cargada.
- Limpieza hecha: puente parado, `docker compose -p odin-verificacion down -v --remove-orphans`, sin contenedores, volúmenes ni redes de la pila.
