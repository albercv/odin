# Informe de la dirección visual de Odín: accesibilidad, casos límite y medición

Fecha: 2026-11-11. Rama `task/identidad-pertenencias/visual-8`. Spec: sección 13 y 10 («Umbrales»).

## Máquina y orden

- Apple M3 Max, 36 GiB de memoria (38 654 705 664 bytes), macOS 27.0.1, Node v24.21.0, Playwright 1.65.0 con `channel: 'chromium'`, un solo worker.
- Orden: `make demo` (pila Compose desechable `odin-demo`, 127.0.0.1:18080 y :18088; ejecuta `npx playwright test` sobre `panel/e2e/`). Se ejecutó dos veces seguidas; las dos terminaron con 0 (17 pruebas pasadas cada vez). `make check` también terminó con 0.
- Limitación de CPU: sesión CDP con `Emulation.setCPUThrottlingRate` y `rate: 4`.

## Cifras medidas

Fotograma del cerebro animado de la entrada, con la CPU a un cuarto (60 fotogramas, `data-average-frame-ms` y `data-median-frame-ms`). Umbral: mediana de 4 ms como máximo.

| Ejecución de `make demo` | Media | Mediana |
|---|---|---|
| 1 | 1,43 ms | 1,35 ms |
| 2 | 1,52 ms | 1,30 ms |

Escribir en «Correo» tras cargar la entrada (CPU a un cuarto), 5 cargas con el cerebro animado y 5 con `reducedMotion: 'reduce'`. Se mide desde que el campo tiene el foco hasta el siguiente fotograma tras teclear un correo ficticio de 29 caracteres. Umbral: 50 ms más como máximo en la mediana.

| Ejecución | Con cerebro animado (ms) | Sin animación (ms) | Diferencia de medianas |
|---|---|---|---|
| 1 | 78, 60, 59, 59, 76 | 53, 58, 47, 58, 55 | 5 ms |
| 2 | 96, 74, 60, 73, 53 | 56, 71, 56, 60, 58 | 15 ms |

Los dos umbrales se cumplen con holgura. Las cifras están medidas en esta máquina; en otra con menos potencia pueden cambiar.

## Accesibilidad (axe)

`@axe-core/playwright` 4.14.0, reglas `wcag2a`, `wcag2aa`, `wcag21aa` y `wcag22aa`; la prueba falla con cualquier infracción `serious` o `critical`.

| Pantalla | Tema claro | Tema oscuro |
|---|---|---|
| Entrada | 0 infracciones | 0 infracciones |
| Portada (`#cover`) | 0 infracciones | 0 infracciones |
| Empresa | 0 infracciones | 0 infracciones |
| Revisión | 0 infracciones | 0 infracciones |

Limitación: `demo.spec.ts` aprueba la única ficha pendiente antes de que corra `visual.spec.ts`, así que Revisión se revisó en su estado vacío («No hay fichas pendientes.»), no con una ficha abierta. El tema se fuerza con `emulateMedia({ colorScheme })`.

## Casos límite y marca

Pruebas de `panel/e2e/visual.spec.ts`, todas pasadas en las dos ejecuciones:

- `coverAppearsOnlyWithCover`: con la marca por defecto `#cover` muestra la portada; sirviendo `brand.json` sin `cover`, muestra la entrada.
- `longNamesDoNotOverflow`: empresa, persona y proyecto de 120 caracteres y un correo de 67 caracteres sin espacios; `scrollWidth <= innerWidth` en escritorio y a 375 px, y el nombre completo sigue en el texto.
- `narrowScreenHasNoHorizontalScroll`: 375 px, empresa y revisión, sin desplazamiento horizontal; «Añadir persona», «Revisión», «Mi cuenta» y «Salir» visibles.
- `brandFilesCannotRunScripts`: `/brand/brand.json` lleva `sandbox` en `content-security-policy`.
- `brandIsNotCached`: `cache-control` contiene `no-cache`.

## Tamaños

De la tarea 3 (`npm run build`): `dist/` pasó de 312 KB a 624 KB; CSS 4,24 kB; JS 243,51 kB; 16 ficheros de fuente (8 woff2 y 8 woff, unos 290 KB en total; woff2 unos 148 KB: Sans 400 22,6 y 16,0 kB, 500 24,2 y 16,5, 600 24,3 y 16,4; Mono 14,7 y 13,3 kB).

Medido en esta tarea, con todo el sistema visual construido (`make check`): CSS 18,95 kB (4,49 kB gzip); JS 270,16 kB (84,98 kB gzip); `dist/` 660 KB (`du -sk`), con 8 ficheros woff2 y 8 woff de fuente.
