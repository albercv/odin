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

Escribir en «Correo» tras cargar la entrada (CPU a un cuarto), 5 cargas con el cerebro animado y 5 con `reducedMotion: 'reduce'`. Se mide desde que el campo tiene el foco hasta el siguiente fotograma tras teclear un correo ficticio de 29 caracteres; incluye la ida y vuelta de Playwright por cada tecla, igual en las dos modalidades, de modo que lo que importa es la diferencia y no el valor absoluto. Umbral: 50 ms más como máximo en la mediana.

| Ejecución | Con cerebro animado (ms) | Sin animación (ms) | Diferencia de medianas |
|---|---|---|---|
| 1 | 78, 60, 59, 59, 76 | 53, 58, 47, 58, 55 | 5 ms |
| 2 | 96, 74, 60, 73, 53 | 56, 71, 56, 60, 58 | 15 ms |

Los dos umbrales se cumplen con holgura. Las cifras están medidas en esta máquina; en otra con menos potencia pueden cambiar.

## Accesibilidad (axe)

`@axe-core/playwright` 4.14.0, reglas `wcag2a`, `wcag2aa`, `wcag21aa` y `wcag22aa`; la prueba falla con cualquier infracción `serious` o `critical`. `visual.spec.ts` crea sus propios datos (una empresa con una ficha pendiente y otra sin fichas) y no depende de lo que haya hecho `demo.spec.ts`. El tema se fuerza con `emulateMedia({ colorScheme })`.

Cifras: número de infracciones `serious` o `critical` (las únicas que hacen fallar la prueba). El total de infracciones de cualquier gravedad también fue 0 en todas las combinaciones.

| Pantalla | Tema claro | Tema oscuro |
|---|---|---|
| Entrada | 0 | 0 |
| Portada (`#cover`) | 0 | 0 |
| Empresa | 0 | 0 |
| Revisión, sin fichas pendientes | 0 | 0 |
| Revisión, con una ficha abierta | 0 | 0 |
| Revisión, con el panel «Rechazar» abierto (campo del motivo visible) | 0 | 0 |

Resultados `incomplete` de `color-contrast` (axe no pudo decidir), registrados por pantalla y tema en cada ejecución:

| Pantalla | Claro | Oscuro | Elementos |
|---|---|---|---|
| Entrada | 4 | 4 | `span` (propietario de la marca), `small`, `h1`, `p` de la declaración |
| Portada | 5 | 5 | `span` y `.owner`, `h1`, `p`, `small` de la declaración |
| Empresa y las tres de Revisión | 0 | 0 | |

Son los textos que están sobre el lienzo del cerebro y un degradado; axe no puede calcular el color de fondo de un `canvas`. Cómo se comprobaron: el texto de esas zonas es `#fff` y `--side-text` (`#c5ced4`) y queda sobre `--brand-base-deep` gracias al degradado de la capa `shade`. Con la marca por defecto (calculado con `deriveTheme` y `contrastRatio`): `#fff` sobre `--brand-base-deep` 17,48:1 y sobre `--brand-base` 13,36:1; `#c5ced4` sobre `--brand-base-deep` 10,95:1 y sobre `--brand-base` 8,37:1; todos por encima de 4,5:1. No se midió el contraste con los puntos del cerebro que pasen por detrás del texto (el color del punto, `#05b4b9`, daría 2,55:1 con blanco si un punto cubriera una letra); esa comprobación sigue pendiente y es visual.

## Casos límite y marca

Pruebas de `panel/e2e/visual.spec.ts`, todas pasadas en las dos ejecuciones (21 pruebas en total en `make demo`):

- `coverAppearsOnlyWithCover`: con la marca por defecto `#cover` muestra la portada; sirviendo `brand.json` sin `cover`, muestra la entrada.
- `longNamesDoNotOverflow`: empresa, persona y proyecto de 120 caracteres y un correo de 67 caracteres sin espacios; `scrollWidth <= innerWidth` en escritorio y a 375 px, y el nombre completo sigue en el texto.
- `narrowScreenHasNoHorizontalScroll`: 375 px, empresa y revisión, sin desplazamiento horizontal; «Añadir persona», «Revisión», «Mi cuenta» y «Salir» visibles.
- `brandFilesCannotRunScripts`: `/brand/brand.json` y el logotipo `/brand/<logo>` leído de ese fichero llevan `sandbox` en `content-security-policy`.
- `brandIsNotCached`: `cache-control` contiene `no-cache`.

## Tamaños

De la tarea 3 (`npm run build`): `dist/` pasó de 312 KB a 624 KB; CSS 4,24 kB; JS 243,51 kB; 16 ficheros de fuente (8 woff2 y 8 woff, unos 290 KB en total; woff2 unos 148 KB: Sans 400 22,6 y 16,0 kB, 500 24,2 y 16,5, 600 24,3 y 16,4; Mono 14,7 y 13,3 kB).

Medido en esta tarea, con todo el sistema visual construido (`make check`): CSS 18,95 kB (4,49 kB gzip); JS 270,16 kB (84,98 kB gzip); `dist/` 660 KB (`du -sk`), con 8 ficheros woff2 y 8 woff de fuente.
