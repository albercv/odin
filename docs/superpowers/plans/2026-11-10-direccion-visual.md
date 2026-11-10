# Odín · Dirección visual del panel y de la demo — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development` (recomendado) o `superpowers:executing-plans`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que el panel tenga el sistema visual aprobado, con marca configurable por instalación, cerebro 3D en la entrada y en la portada de la demo, y accesibilidad AA probada (D-039, D-038).

**Arquitectura:** el tema es TypeScript puro y probado (contraste, derivación, validación de la marca) que escribe variables CSS en `:root`; React solo las consume. La marca llega en tiempo de ejecución desde `/brand/`, servida por el nginx del panel y sustituible por instalación. El cerebro separa la geometría (pura), el dibujo (canvas) y el ciclo de vida (componente).

**Tecnología:** React 19, TypeScript 5.9, Vite 8 con CSS Modules, Vitest con jsdom, Playwright con `@axe-core/playwright`, `@fontsource/ibm-plex-sans` y `@fontsource/ibm-plex-mono`, nginx.

**Diseño:** `docs/superpowers/specs/2026-11-10-direccion-visual-design.md` («spec»). Referencia visual: `docs/superpowers/specs/2026-11-10-direccion-visual/maqueta.html` («maqueta»), aprobada por el propietario. El código del cerebro de la maqueta (`createBrain`) es la referencia del algoritmo. Quien ejecute una tarea lee el spec entero.

## Restricciones globales

- **Navegadores:** Chrome y Edge 108+, Safari 16+, Firefox 110+. Se usa `<dialog>`. No se usan `popover`, posicionamiento por anclas ni `color-mix()`. View Transitions solo como mejora.
- **Nada desde internet en ejecución:** fuentes desde `@fontsource` dentro del paquete del panel; nada de CDN.
- **Contraste:** todo texto a 4,5:1 o más, en los dos temas y con cualquier marca.
- **Marca:** ningún nombre, color ni logo de E2D en `panel/src`. La marca por defecto vive en `panel/public/brand/`.
- **API:** sin cambios en la API, en `api/openapi.json` ni en `panel/src/api/types.ts`.
- **3D:** solo en la entrada y en la portada. Nunca en las pantallas de gestión.
- **Movimiento** (spec 9): curva `cubic-bezier(.23, 1, .32, 1)`. Duraciones: pulsar 100 ms, pantalla 180 ms, ficha 220–250 ms, aviso 200 ms con cierre a los 2,6 s, interruptor 200 ms. Con `prefers-reduced-motion: reduce`, cambio directo.
- **Nombres accesibles:** «<acción> <nombre de lo afectado>» («Editar Marta Ibáñez»). Si cambia uno, la prueba de extremo a extremo se adapta en la misma tarea.
- **Voz:** los términos de spec 12. El aviso repite el verbo del botón («Aprobar» → «Ficha aprobada»).
- **TDD con evidencia:** primero la prueba; se ejecuta y se guardan en el informe de la tarea la orden y la salida en rojo.
- **Clean Code:** funciones de 30 líneas como máximo (ESLint), sin duplicar, comentarios mínimos, identificadores en inglés en camelCase.
- **Pruebas unitarias:** Vitest solo recoge `src/**/*.test.tsx`. La tarea 1 añade `src/**/*.test.ts` a `include` en `panel/vite.config.ts`.
- **Cierre de cada tarea:** `make check` termina con 0. Desde la tarea 6, también `make demo`.
- **Dónde se trabaja:** en la copia de trabajo `.worktrees/direccion-visual`. No se ejecuta `make up` ni `make down` y no se toca `deploy/.env`.
- **Git:** cada tarea en su rama `task/identidad-pertenencias/visual-<n>`, que sale de `feature/identidad-pertenencias`. Commits en español, sin atribución a Claude ni `Co-Authored-By`. Sin push. El merge `--no-ff` lo hace el CTO.
- **Datos:** solo ficticios.

## Foco de revisión

1. **Nombres muy largos** (empresa, persona o proyecto de 120 caracteres, correos sin espacios): la página no se desborda en horizontal y el nombre completo sigue disponible. Prueba en la tarea 8 (`longNamesDoNotOverflow`).
2. **Pantalla estrecha** (375 px): sin desplazamiento horizontal en empresa y revisión, y todas las acciones alcanzables. Prueba en la tarea 8 (`narrowScreenHasNoHorizontalScroll`).
3. **Marca lenta o caída:** si `/brand/brand.json` no responde en 2 s, el panel arranca con el tema neutro. Prueba en la tarea 1 (`fallsBackToNeutralAfterTwoSeconds`).
4. **Logo SVG con un script:** abrirlo directamente no ejecuta nada en el origen del panel. Prueba en la tarea 8 (`brandFilesCannotRunScripts`).
5. **Cambio de marca después de instalar:** se ve al recargar, sin caché vieja. Prueba en la tarea 8 (`brandIsNotCached`).

---

## Interfaces compartidas (fuente única de nombres)

```ts
// panel/src/theme/contrast.ts
export function contrastRatio(foreground: string, background: string): number;   // WCAG 2.x, hex #rrggbb
export function mixColors(color: string, over: string, amount: number): string;   // amount 0..1 de color sobre over → hex
export function shiftLightness(hex: string, delta: number): string;               // HSL, conserva tono y saturación

// panel/src/theme/neutrals.ts
export type Neutrals = { background: string; surface: string; surfaceMuted: string; line: string; lineStrong: string;
  text: string; textMuted: string; pendingText: string; pendingBackground: string; danger: string; dangerBackground: string };
export const LIGHT_NEUTRALS: Neutrals;   // valores de spec 8 y 7.4; pendingBackground '#fdf1dc', dangerBackground '#fdecea'
export const DARK_NEUTRALS: Neutrals;    // pendingBackground = mixColors('#f2b85b', '#232c34', 0.12); dangerBackground = mixColors('#f97066', '#232c34', 0.12)
export const SIDEBAR_TEXT = '#c5ced4';
export const DARK_TEXT_ON_ACCENT = '#172026';

// panel/src/theme/deriveTheme.ts
export type ColorMode = 'light' | 'dark';
export type ModeTokens = Neutrals & { accent: string; accentText: string; accentTint: string;
  sidebarBackground: string; sidebarText: string; sidebarAccent: string };
export type ThemeTokens = { light: ModeTokens; dark: ModeTokens;
  brandBase: string; brandBaseDeep: string; brandAccentOnDark: string; brandAccentOnDarkText: string };
export function deriveTheme(colors: BrandColors): ThemeTokens;

// panel/src/theme/brand.ts
export type BrandColors = { accent: string; base: string };
export type BrandCover = { title: string; subtitle: string; note: string };
export type Brand = { name?: string; logoUrl?: string; logoAlt?: string; colors: BrandColors; cover?: BrandCover };
export const NEUTRAL_BRAND: Brand;                                  // { colors: { accent: '#4f6d8f', base: '#2b3036' } }
export function parseBrand(raw: unknown): Brand | undefined;        // logoUrl = '/brand/' + logo
export function loadBrand(fetchBrand?: () => Promise<unknown>, timeoutMs?: number): Promise<Brand>;  // timeout 2000

// panel/src/theme/applyTheme.ts
export function applyTheme(root: HTMLElement, theme: ThemeTokens, mode: ColorMode): void;
// Variables CSS que escribe (las únicas que usan los estilos):
// --bg --surface --surface-muted --line --line-strong --text --text-muted --accent --accent-text --accent-tint
// --pending-text --pending-bg --danger --danger-bg --side-bg --side-text --side-accent
// --brand-base --brand-base-deep --brand-accent-on-dark --brand-accent-on-dark-text ; y root.dataset.theme = mode

// panel/src/theme/useColorMode.ts
export type ColorPreference = ColorMode | 'system';
export const COLOR_PREFERENCE_KEY = 'odin.colorPreference';
export function useColorMode(): { mode: ColorMode; preference: ColorPreference; setPreference(p: ColorPreference): void };

// panel/src/theme/BrandContext.tsx
export function BrandProvider(props: { brand: Brand; theme: ThemeTokens; children: ReactNode }): JSX.Element;
export function useBrand(): Brand;

// panel/src/bootstrap.ts
export function bootstrap(root: HTMLElement, load?: () => Promise<Brand>): Promise<{ brand: Brand; theme: ThemeTokens }>;

// panel/src/ui/
export type IconName = 'building' | 'buildings' | 'file' | 'review' | 'user' | 'logout' | 'lock' | 'people' | 'key'
  | 'more' | 'chevronDown' | 'plus' | 'spark' | 'done' | 'upload' | 'close';
<Icon name={IconName} />                                            // aria-hidden, 18 px, trazo 1,75
<Button variant?="primary"|"secondary"|"ghost"|"danger" icon?={IconName} {...buttonProps} />
<IconButton icon={IconName} label={string} {...buttonProps} />     // label → aria-label
<Field label={string} error?={string}>{input}</Field>               // <label> que envuelve el control, como hoy
<Switch label={string} checked onChange disabled? />               // input checkbox con role="switch"
<StatusPill tone="ok"|"off"|"pending">{texto}</StatusPill>
<Section title description? actions?>{...}</Section>               // h2 + aria-labelledby
<PageHeader title description? actions? />                         // h1
<EmptyState title description? />
<ToastProvider>; useToast(): (message: string) => void              // role="status", 2600 ms
<ConfirmProvider>; useConfirm(): (o: { message: string; confirmLabel: string; danger?: boolean }) => Promise<boolean>
<FormDialog title open onClose>{form}</FormDialog>                  // <dialog> modal; foco al primer campo y vuelta al cerrar
<RowActions label={string}>{botones}</RowActions>                   // botón con aria-expanded que despliega en la fila
export function withViewTransition(update: () => void): void;       // panel/src/ui/viewTransition.ts

// panel/src/brain/
export type BrainPart = 'cortex' | 'cerebellum' | 'stem';
export type BrainPoint = { x: number; y: number; z: number; part: BrainPart };
export type BrainGeometry = { points: BrainPoint[]; edges: [number, number][]; neighbors: number[][] };
export function buildBrainGeometry(seed?: number): BrainGeometry;   // seed 7 por defecto
export type FrameSummary = { averageMs: number; medianMs: number };
export function createFrameGuard(budgetMs?: number, sampleSize?: number): {
  record(frameMs: number): 'continue' | 'freeze'; summary(): FrameSummary | undefined };   // 12 ms y 60 fotogramas
export type BrainPlacement = { centerX: number; centerY: number; widthShare: number; heightShare: number };
export const LOGIN_PLACEMENT: BrainPlacement;   // { centerX: 0.5, centerY: 0.36, widthShare: 0.27, heightShare: 0.3 }
export const COVER_PLACEMENT: BrainPlacement;   // { centerX: 0.5, centerY: 0.31, widthShare: 0.22, heightShare: 0.25 }
export function createBrainRenderer(canvas: HTMLCanvasElement, geometry: BrainGeometry, placement: BrainPlacement): {
  resize(): void; setColor(hex: string): void; drawFrame(deltaMs: number): void; drawStill(): void };
<BrainCanvas placement={BrainPlacement} />   // color de --brand-accent-on-dark; data-average-frame-ms y data-median-frame-ms tras 60 fotogramas
```

---

### Tarea 1: Motor del tema · Sonnet

Rama `task/identidad-pertenencias/visual-1`. Spec 7.1, 7.3, 7.4 y 8.

**Ficheros:**
- **Crear:** en `panel/src/theme/`: `contrast.ts`, `neutrals.ts`, `deriveTheme.ts`, `brand.ts` y `applyTheme.ts`, cada uno con su `.test.ts`.
- **Modificar:** `panel/vite.config.ts` (`include` con `src/**/*.test.ts`).

**Valores:**
- `deriveTheme`, tema claro: `shiftLightness` en pasos de −0,005 hasta que `accent` da 4,5:1 contra `surface`, `background`, `surfaceMuted` y su propio `accentTint`. El tinte se recalcula en cada paso: `mixColors(accent, surface, 0.10)`.
- `deriveTheme`, tema oscuro: pasos de +0,005 hasta 4,5:1 contra `surface`, `background`, `surfaceMuted`, `brandBase` y `brandBaseDeep`. El tinte es `mixColors(accent, surface, 0.14)`, y contra él no se exige contraste, porque el acento nunca va como texto sobre su tinte.
- `accentText`: en el tema claro, `#ffffff` si da 4,5:1 y si no `DARK_TEXT_ON_ACCENT`. En el oscuro, al revés: primero `DARK_TEXT_ON_ACCENT`.
- `brandBase`: se oscurece en pasos de −0,005 hasta que `SIDEBAR_TEXT` da 4,5:1. `brandBaseDeep = mixColors('#000000', brandBase, 0.45)`.
- Barra lateral: en el tema claro, `sidebarBackground = brandBase`; en el oscuro, `brandBaseDeep`. En los dos, `sidebarText = SIDEBAR_TEXT` y `sidebarAccent = brandAccentOnDark`.
- `parseBrand`:
  - colores `^#[0-9a-fA-F]{6}$`;
  - `logo` opcional: solo un nombre de fichero (`^[\w.-]+\.(png|webp|svg)$`, sin `/` ni `..`);
  - longitudes máximas: `name` 60, `logoAlt` 80, `cover.title` 80, `cover.subtitle` 160, `cover.note` 100;
  - `cover`, si está, con los tres textos.
- `loadBrand`: lee `/brand/brand.json`. Si falla, si no es válido o si tarda más de 2000 ms, devuelve `NEUTRAL_BRAND` y hace un `console.error` que diga cuál fue el motivo.

- [ ] **Paso 1:** pruebas, y comprueba que fallan:
  - `contrast.test.ts`:
    - `matchesKnownWcagValues`: `#05b4b9` sobre `#ffffff` da 2,55 ±0,01; `#ffffff` sobre `#000000` da 21; `#03797c` sobre `#ffffff` da 5,21 ±0,01;
    - `mixesColors`: `mixColors('#05b4b9', '#232c34', 0.14)` da `#1f3f47`;
    - `shiftLightnessKeepsTheHue`.
  - `neutrals.test.ts`: `fixedPairsReachFourAndAHalf`, con todos los pares de neutros de spec 7.4.
  - `deriveTheme.test.ts`:
    - `e2dLightAccentPassesOnEveryLightBackground`;
    - `e2dDarkAccentIsTheLogoTeal`: `brandAccentOnDark` es `#05b4b9`;
    - `everyTextPairReachesFourAndAHalf`, para los acentos `#05b4b9`, `#ffd400`, `#c6ff00`, `#f8c8dc`, `#b3e5fc`, `#111111`, `#808080`, `#ff0000`, `#00ff00`, `#0000ff`, `#ffffff`, `#7a5c00` y `#4f6d8f`, con las bases `#283039`, `#2e2427` y `#9aa5b1`;
    - `keepsTheHueOfTheAccent`: ±2°, salvo en los grises;
    - `darkensABaseThatIsTooLight`.
  - `brand.test.ts`:
    - `acceptsTheE2dBrand`, `logoIsOptional`, `resolvesTheLogoInsideTheBrandFolder`;
    - `rejectsLogoPathsOutsideTheFolder`, con `../x.png`, `/etc/x.png` y `https://a/x.png`;
    - `rejectsInvalidColors`, `rejectsUnknownLogoExtensions`, `rejectsOverlongTexts`, `rejectsAnIncompleteCover`;
    - `fallsBackToNeutralWhenMissing`, `fallsBackToNeutralWhenInvalid`;
    - `fallsBackToNeutralAfterTwoSeconds`, con temporizadores falsos.
  - `applyTheme.test.ts`: `writesEveryVariableForTheMode` y `setsTheThemeDataAttribute`.
- [ ] **Paso 2:** implementa las firmas de «Interfaces compartidas».
- [ ] **Paso 3:** `make check` termina con 0.
- [ ] **Paso 4:** commit «feat: motor del tema con marca configurable y contraste garantizado».

### Tarea 2: Marca servida por la instalación · Sonnet

Rama `task/identidad-pertenencias/visual-2`. Spec 6 y 7.2.

**Ficheros:**
- **Crear:**
  - `panel/public/brand/brand.json`, con el JSON de spec 7.1 y el subtítulo corregido «… al alcance de cada persona y con su fuente.»;
  - `panel/public/brand/logo.webp`, copia de `docs/superpowers/specs/2026-11-10-direccion-visual/e2d_logo.webp`;
  - `panel/src/bootstrap.ts` y `panel/src/theme/BrandContext.tsx`, con sus pruebas;
  - `panel/src/theme/noBrandInSource.test.ts`.
- **Modificar:** `panel/src/main.tsx`, `deploy/nginx.conf`, `deploy/compose.yaml`, `deploy/.env.example` y `README.md`.

**Valores:**
- `main.tsx`: `await bootstrap(document.documentElement)` antes de `createRoot(...).render`, con `<BrandProvider>` alrededor de `<App />`. El modo inicial sale de `matchMedia('(prefers-color-scheme: dark)')` y de `localStorage[COLOR_PREFERENCE_KEY]`.
- `nginx.conf`: `location /brand/` con:
  - `add_header Cache-Control "no-cache" always;`
  - `add_header Content-Security-Policy "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox" always;`
  - `add_header X-Content-Type-Options nosniff always;`
- `compose.yaml`, servicio `panel`: el volumen `${ODIN_BRAND_DIR:-../panel/public/brand}:/usr/share/nginx/html/brand:ro`.
- `.env.example`: `# ODIN_BRAND_DIR=/ruta/a/la/marca/del/cliente`, comentado y con una línea que diga qué contiene esa carpeta.
- `README.md`: apartado «Marca»: qué lleva `brand.json`, que el logo debe verse bien sobre el color `base`, que `cover` es opcional y que el cambio se ve al recargar.
- `noBrandInSource.test.ts`: recorre `panel/src` con `node:fs` y falla si encuentra `05b4b9`, `283039`, `E2D` o `Evolve` en cualquier fichero que no sea de prueba.

- [ ] **Paso 1:** pruebas, y comprueba que fallan:
  - `bootstrap.test.ts`: `appliesTheLoadedBrand` y `appliesTheNeutralBrandWhenLoadingFails`;
  - `BrandContext.test.tsx`: `providesTheBrandToChildren`;
  - `noBrandInSource.test.ts`: `sourceHasNoBrandValues`.
- [ ] **Paso 2:** implementa los cambios y los ficheros de marca.
- [ ] **Paso 3:** `make check` termina con 0. `docker compose -f deploy/compose.yaml config` muestra el volumen de marca con y sin `ODIN_BRAND_DIR`.
- [ ] **Paso 4:** commit «feat: marca por instalación servida desde /brand».

### Tarea 3: Base visual y controles · Sonnet

Rama `task/identidad-pertenencias/visual-3`. Spec 6 (letra) y 8.

**Ficheros:**
- **Crear:**
  - `panel/src/styles/global.css`: tipografía, base de los elementos, foco visible con `3px solid var(--accent)` y la regla de reducir movimiento;
  - en `panel/src/ui/`: `Icon.tsx`, `Button.tsx`, `IconButton.tsx`, `Field.tsx`, `Switch.tsx`, `StatusPill.tsx`, `Section.tsx`, `PageHeader.tsx` y `EmptyState.tsx`, cada uno con su `.module.css` y su `.test.tsx`.
- **Modificar:** `panel/package.json` y `panel/package-lock.json` (dependencias de las fuentes), y `panel/src/main.tsx` (importa `global.css` en lugar de `styles.css`).
- **Borrar:** `panel/src/styles.css` cuando ya no lo use nadie. Si algún estilo suyo sigue haciendo falta hasta la tarea 7, pasa a `global.css`.

**Valores:**
- Fuentes: `@fontsource/ibm-plex-sans` (400, 500 y 600) y `@fontsource/ibm-plex-mono` (400), solo los subconjuntos `latin` y `latin-ext`, importados por fichero de peso. Versión exacta en `package.json`.
- Escala tipográfica: 13, 14, 15 (cuerpo), 18, 22 y 26 px. Pesos: 400 para el texto, 500 para etiquetas y botones, 600 para títulos.
- Radios: 6, 10 y 16 px. Botones de 38 px de alto. Aspecto de cada control: el de la maqueta.
- Iconos: los 16 de `IconName`, en SVG de 24 × 24 con trazo `currentColor`. Los de la maqueta se reutilizan; `upload` y `close` son nuevos.
- Todo color sale de las variables CSS de la tarea 1, nunca de valores escritos a mano.

- [ ] **Paso 1:** pruebas, y comprueba que fallan:
  - `iconIsHiddenFromAssistiveTech`;
  - `buttonVariantsKeepTheNativeButton`, `iconButtonUsesTheLabelAsAccessibleName`;
  - `fieldLabelWrapsTheControl`, `fieldShowsTheErrorAsAlert`;
  - `switchHasSwitchRoleAndToggles`;
  - `statusPillShowsItsText`;
  - `sectionHeadingLabelsTheSection`, `pageHeaderIsTheOnlyH1`;
  - `emptyStateShowsTitleAndDescription`.
- [ ] **Paso 2:** implementa los componentes y los estilos.
- [ ] **Paso 3:** `make check` termina con 0. Anota en el informe el tamaño de `dist/` y el de las fuentes, antes y después.
- [ ] **Paso 4:** commit «feat: base visual y controles del panel».

### Tarea 4: Ventanas, avisos, acciones de fila, tema y transiciones · Sonnet

Rama `task/identidad-pertenencias/visual-4`. Spec 7.5, 8 («Dónde van los formularios») y 9.

**Ficheros:**
- **Crear:** en `panel/src/ui/`: `Toast.tsx`, `ConfirmDialog.tsx`, `FormDialog.tsx`, `RowActions.tsx` y `viewTransition.ts`, y `panel/src/theme/useColorMode.ts`, cada uno con su prueba y su `.module.css` si tiene estilo.

**Valores:**
- `ConfirmDialog`:
  - un `<dialog>` con `showModal()`;
  - botones «Cancelar» y `confirmLabel`;
  - con `danger`, el botón de confirmar es de la variante de peligro;
  - `Escape` y «Cancelar» resuelven `false`;
  - el foco empieza en «Cancelar» y vuelve al control que lo abrió.
- `FormDialog`: un `<dialog>` con `showModal()`, el título en `h2`, el foco al primer campo y de vuelta al cerrar. Lleva un botón de cerrar con `aria-label="Cerrar"`.
- `Toast`: una sola región `role="status"` y `aria-live="polite"`. El aviso se cierra a los 2600 ms y uno nuevo sustituye al anterior.
- `RowActions`: botón `aria-expanded` y `aria-controls`; despliega sus hijos en la fila; `Escape` lo pliega y devuelve el foco al botón.
- `useColorMode`:
  - la preferencia se guarda en `localStorage[COLOR_PREFERENCE_KEY]`, con `try/catch` en cada lectura y escritura;
  - con «system», sigue los cambios de `matchMedia` en vivo;
  - cada cambio llama a `applyTheme`.
- `withViewTransition`: usa `document.startViewTransition` con `flushSync` si existe y no se pide reducir movimiento; si no, llama a `update()` directamente.

- [ ] **Paso 1:** pruebas, y comprueba que fallan:
  - `confirmResolvesTrueOnConfirm`, `confirmResolvesFalseOnEscapeAndCancel`, `confirmReturnsFocus`;
  - `formDialogFocusesTheFirstFieldAndReturnsFocus`;
  - `toastAnnouncesAndClosesAfter2600ms`, `newToastReplacesThePrevious`;
  - `rowActionsTogglesWithAriaExpanded`, `rowActionsClosesOnEscape`;
  - `colorModeFollowsTheSystem`, `colorModeKeepsTheChoice`, `colorModeSurvivesBlockedStorage`, `followsSystemChangesWhenPreferenceIsSystem`;
  - `viewTransitionFallsBackWithoutSupport`, `viewTransitionSkippedWithReducedMotion`.
  
  jsdom no implementa `showModal`: la prueba lo simula en `HTMLDialogElement.prototype`.
- [ ] **Paso 2:** implementa los componentes.
- [ ] **Paso 3:** `make check` termina con 0.
- [ ] **Paso 4:** commit «feat: ventanas, avisos, acciones de fila, tema y transiciones».

### Tarea 5: El cerebro · Sonnet

Rama `task/identidad-pertenencias/visual-5`. Spec 10. Puede ir a la vez que las tareas 3 y 4.

**Ficheros:**
- **Crear:** en `panel/src/brain/`: `brainGeometry.ts`, `frameGuard.ts`, `brainRenderer.ts` y `BrainCanvas.tsx`, cada uno con su prueba.

**Valores:**
- Geometría: el algoritmo de `createBrain` de la maqueta, con el mismo generador de números (Park-Miller, semilla 7).
  - Puntos: 230 por hemisferio, 80 de cerebelo y 28 de tronco, en total 568.
  - Conexiones: las 3 vecinas más cercanas sin cruzar la cisura.
  - Puente central: puntos de corteza con |x| < 0,12, y > −0,1 y |z| < 0,6.
- Dibujo:
  - rotación `−π/2 + 0,6·sen(t·0,00022)` e inclinación 0,22;
  - cámara a distancia 6;
  - líneas en 4 trazos por profundidad, con alfa 0,05 + 0,075·nivel;
  - señales nuevas cada 180 ms, a 380 ms por conexión;
  - en cada llegada, sigue con probabilidad 0,62 y se ramifica con 0,12;
  - destello que cae a `0,0025^(dt/1000)`;
  - como mucho 60 señales a la vez.
- `BrainCanvas`:
  - `aria-hidden`;
  - quieto con `prefers-reduced-motion: reduce`, con `navigator.hardwareConcurrency <= 2` o con `navigator.connection?.saveData`;
  - arranca en el `requestAnimationFrame` siguiente al montaje;
  - se para con la pestaña oculta (`visibilitychange`) y fuera de la pantalla (`IntersectionObserver`), y al desmontarse;
  - si `frameGuard` dice `freeze`, dibuja el fotograma quieto y para el bucle;
  - escribe `data-average-frame-ms` y `data-median-frame-ms` una sola vez, tras 60 fotogramas;
  - densidad de píxeles como mucho 2;
  - debajo del canvas, un degradado CSS con `--brand-base`.

- [ ] **Paso 1:** pruebas, y comprueba que fallan:
  - `brainGeometry.test.ts`: `isDeterministicForTheSameSeed`, `hasTheExpectedPointCount` (568), `everyPointHasAConnection` y `onlyTheCentralBridgeCrossesTheFissure`;
  - `frameGuard.test.ts`: `freezesWhenTheFirstSixtyFramesAverageOverTwelveMs`, `keepsRunningUnderBudget`, `decidesOnlyOnce` y `summarizesAverageAndMedian`;
  - `BrainCanvas.test.tsx`:
    - `drawsOneStillFrameWhenReducedMotion`: ningún `requestAnimationFrame` después del primero;
    - `drawsStillOnLowPowerDevices`;
    - `stopsTheLoopOnUnmount`;
    - `pausesWhileTheTabIsHidden`;
    - `freezesWhenTheGuardSaysSo`.
  
  jsdom no tiene canvas: la prueba simula `HTMLCanvasElement.prototype.getContext` con un contexto falso que cuenta las llamadas.
- [ ] **Paso 2:** implementa los módulos.
- [ ] **Paso 3:** `make check` termina con 0.
- [ ] **Paso 4:** commit «feat: cerebro con sinapsis para la entrada y la portada».

### Tarea 6: Estructura, entrada y portada · Sonnet

Rama `task/identidad-pertenencias/visual-6`. Spec 8 (disposición) y 11 (entrada y portada). Necesita las tareas 2 a 5.

**Ficheros:**
- **Crear:** `panel/src/layout/AppShell.tsx`, `Sidebar.tsx`, `BrandMark.tsx` y `panel/src/pages/Cover.tsx`, con sus `.module.css` y sus pruebas.
- **Modificar:** `panel/src/App.tsx`, `panel/src/App.test.tsx`, `panel/src/pages/Login.tsx` y, si cambia algún nombre accesible, `panel/e2e/admin.spec.ts` y `panel/e2e/demo.spec.ts`.

**Valores:**
- `BrandMark`:
  - logo, o las iniciales de `name` (hasta dos letras), o «O» con la marca neutra;
  - texto «Odín» y, en la entrada y la portada, «de <name>» debajo, si hay `name`.
- `Sidebar`:
  - de arriba abajo: `BrandMark`, el nombre de la empresa abierta (sin interacción) y la navegación;
  - navegación: «Empresas» solo para el administrador de grupo, luego «Empresa», «Documentos» y «Revisión»;
  - «Revisión» lleva el contador de fichas pendientes, que se oculta en 0 y se carga con `api.pendingCards(companyId)` al abrir la empresa;
  - abajo: la persona, «Mi cuenta» y «Salir»;
  - `aria-current="page"` en la página activa;
  - por debajo de 900 px pasa a barra superior.
- `App`: `#portada` con `brand.cover` muestra `Cover`; sin `cover`, `#portada` muestra la entrada. Las páginas cambian con `withViewTransition`. Los proveedores `ToastProvider` y `ConfirmProvider` envuelven la aplicación.
- `Login`:
  - a la izquierda, el bloque de marca con `BrainCanvas` (`LOGIN_PLACEMENT`), el lema «Cómo se resolvió, y dónde consta.» y el texto «Odín guarda los documentos y las fichas de la empresa y responde a la IA de cada persona citando la fuente.»;
  - a la derecha, «Entra en el panel», los campos «Correo» y «Contraseña», el botón «Entrar» y la ayuda «¿Has olvidado la contraseña? Pide a quien administra el grupo que te la restablezca.»;
  - el aviso de `notice` sigue en un `role="status"`.
- `Cover`:
  - `BrainCanvas` (`COVER_PLACEMENT`) y los tres textos de `brand.cover`;
  - el botón «Empezar la demostración», que quita `#portada` y lleva a la entrada;
  - ninguna llamada a la API.

- [ ] **Paso 1:** pruebas, y comprueba que fallan:
  - `brandMarkShowsLogoOrInitials`;
  - `sidebarHidesCompaniesForCompanyAdmins`, `sidebarMarksTheCurrentPage`, `pendingCountHiddenAtZero`;
  - `coverShowsTheBrandTexts`, `coverMakesNoApiCalls`;
  - `portadaWithoutCoverShowsLogin`;
  - `loginStillLogsIn`.
- [ ] **Paso 2:** implementa la estructura y las dos pantallas.
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0.
- [ ] **Paso 4:** commit «feat: estructura del panel, entrada y portada de la demo».

### Tarea 7: Pantallas de gestión · Sonnet

Rama `task/identidad-pertenencias/visual-7`. Spec 8 («Dónde van los formularios»), 11 y 12. Necesita la tarea 6.

**Ficheros:**
- **Modificar:**
  - `panel/src/pages/`: `Companies.tsx`, `Company.tsx`, `Documents.tsx`, `Review.tsx` y `Account.tsx`;
  - `panel/src/components/`: `Projects.tsx`, `Users.tsx`, `UserForm.tsx`, `Keys.tsx` y `Notice.tsx`;
  - sus pruebas, y `panel/e2e/admin.spec.ts` y `panel/e2e/demo.spec.ts`.
- **Crear:** `panel/src/pages/reviewNames.ts` y `reviewNames.test.ts`.

**Valores:**
- **Empresas:** `PageHeader` con «Nueva empresa» en `FormDialog`. Por empresa: nombre, capacidades como `Switch` y acciones en la fila («Abrir <empresa>» y «Renombrar <empresa>»).
- **Empresa:**
  - cabecera con el nombre; las capacidades son `Switch` para el administrador de grupo y `StatusPill` de solo lectura para el administrador de empresa;
  - «Añadir persona» es la acción principal y abre `FormDialog`;
  - sección «Proyectos», con «Nuevo proyecto» en `FormDialog`, y cada fila con «Renombrar <proyecto>» desplegado en la fila y «Borrar <proyecto>» con `useConfirm`;
  - sección «Personas»: nombre y correo (o «Sin correo»), papel y acceso con `StatusPill`;
  - acciones de cada persona en `RowActions` («Más acciones de <persona>»);
  - quitar a una persona pasa por `useConfirm`;
  - la clave recién dada se enseña en un `FormDialog` que solo se cierra con «Entendido»;
  - «Contraseña restablecida» pasa a `useToast`.
- **Documentos:** «Subir documento» en `FormDialog`. La tabla y los estados de hoy, con `StatusPill`. El aviso de lista cortada se queda en `role="status"`.
- **Revisión:**
  - cola y detalle en dos columnas;
  - el detalle lleva `StatusPill tone="pending"` «Pendiente de revisión»;
  - origen: «Ficha redactada por la IA de <persona>», con borde discontinuo de acento;
  - campos: proyecto, quién la guardó y fecha;
  - texto editable; si cambia, el aviso «Has cambiado el texto.» y «Ver el original»;
  - botones «Aprobar», «Corregir y aprobar» (solo con el texto cambiado) y «Rechazar», que despliega el motivo obligatorio y «Rechazar la ficha»;
  - avisos: «Ficha aprobada», «Ficha corregida y aprobada» y «Ficha rechazada»;
  - la ficha resuelta sale de la cola con el movimiento de 220–250 ms y el contador de la barra lateral se actualiza;
  - la ficha seleccionada se marca con `--accent-tint` y una barra de acento, con el texto normal.
- `reviewNames.ts`:
  - `authorName(authorId: string, members: S['MemberView'][]): string`: si el autor no está, «una persona que ya no está en la empresa»;
  - `projectName(projectId: string | undefined, projects: S['ProjectView'][]): string`: «Sin proyecto» si no tiene proyecto.
- **Mi cuenta:** el formulario de contraseña de hoy y un grupo de opciones «Tema» («Como el sistema», «Claro», «Oscuro») con `useColorMode`.
- `Notice` queda solo para errores (`role="alert"`) con el estilo nuevo.
- En toda la tarea, `window.confirm` desaparece del panel.

- [ ] **Paso 1:** pruebas adaptadas y nuevas, y comprueba que las nuevas fallan:
  - `reviewNames.test.ts`: `authorNameFallsBackWhenTheAuthorLeft` y `projectNameWithoutProjectIsSinProyecto`;
  - `Review.test.tsx`: `correctAndApproveOnlyAfterEditing`, `rejectRequiresAReason`, `resolvedCardLeavesTheQueueAndTheCountDrops` y `showsTheOriginalAfterEditing`;
  - `Company.test.tsx`: `capabilitiesReadOnlyForCompanyAdmin` y `addPersonOpensADialog`;
  - `Users.test.tsx`: `removeAsksWithTheConfirmDialog` y `issuedKeyOnlyClosesWithEntendido`;
  - `Projects.test.tsx`: `deleteAsksWithTheConfirmDialog`;
  - `Account.test.tsx`: `themeChoiceIsSaved`;
  - `noWindowConfirm`: busca `window.confirm` en `panel/src` y no lo encuentra.
- [ ] **Paso 2:** reescribe las pantallas.
- [ ] **Paso 3:** adapta `admin.spec.ts` y `demo.spec.ts`: el diálogo nativo de confirmación pasa a ser el `ConfirmDialog`, y las acciones de fila se despliegan antes. `make check` y `make demo` terminan con 0.
- [ ] **Paso 4:** commit «feat: pantallas de gestión con el sistema visual».

### Tarea 8: Accesibilidad, casos límite y medición · Sonnet

Rama `task/identidad-pertenencias/visual-8`. Spec 13. Necesita la tarea 7.

**Ficheros:**
- **Crear:**
  - `panel/e2e/visual.spec.ts`: accesibilidad, casos límite y marca;
  - `panel/e2e/performance.spec.ts`;
  - `docs/informes/<fecha>-direccion-visual.md`.
- **Modificar:** `panel/package.json` y `panel/package-lock.json` (`@axe-core/playwright` como dependencia de desarrollo, con la versión exacta).

**Valores:**
- La revisión de accesibilidad pasa axe sin infracciones `serious` ni `critical` (reglas `wcag2a`, `wcag2aa`, `wcag21aa` y `wcag22aa`) en la entrada, la portada, empresa y revisión, en tema claro y en oscuro.
- Medición, con la CPU limitada a un cuarto (`Emulation.setCPUThrottlingRate` con `rate: 4` en una sesión CDP):
  - la mediana de `data-median-frame-ms` es de 4 ms como máximo;
  - escribir en «Correo» tras cargar la entrada tarda como mucho 50 ms más con el cerebro animado que con `reducedMotion: 'reduce'` (mediana de 5 cargas de cada tipo).
- El informe recoge las cifras medidas, la máquina y la orden, y el tamaño del panel y de las fuentes de la tarea 3. Sin cifras que no se hayan medido.

- [ ] **Paso 1:** pruebas, y comprueba cuáles fallan:
  - `visual.spec.ts`:
    - `axeFindsNoSeriousIssues`, para cada pantalla y tema;
    - `coverAppearsOnlyWithCover`;
    - `longNamesDoNotOverflow`: empresa, persona y proyecto de 120 caracteres y un correo sin espacios; `document.documentElement.scrollWidth <= innerWidth`;
    - `narrowScreenHasNoHorizontalScroll`: 375 px, en empresa y revisión;
    - `brandFilesCannotRunScripts`: la respuesta de `/brand/brand.json` lleva `sandbox` en `content-security-policy`;
    - `brandIsNotCached`: `cache-control` contiene `no-cache`;
  - `performance.spec.ts`: `brainFrameUnderFourMsAtQuarterCpu` y `loginNotSlowedByTheBrain`.
- [ ] **Paso 2:** corrige cada fallo en su componente, con su prueba unitaria; la prueba no se adapta al fallo.
- [ ] **Paso 3:** `make demo` dos veces seguidas y `make check` terminan con 0. Escribe el informe.
- [ ] **Paso 4:** commit «test: accesibilidad, casos límite y medición de la dirección visual».

## Orden y paralelismo

- 1 → 2. La 3, la 4 y la 5 necesitan la 1 y pueden ir a la vez entre ellas. La 6 necesita de la 2 a la 5. Después, 7 → 8.
- Con la fase 2a: este plan va antes de su tarea 5 y puede avanzar a la vez que sus tareas 3 y 4, que no tocan el panel salvo `panel/src/api/types.ts`, que este plan no cambia. La tarea 5 de la 2a se construye con estos componentes: `ConfirmDialog` en lugar de `window.confirm`, y `CompanySwitcher` en el hueco de la empresa abierta de la barra lateral.

Al terminar: revisión final de toda la rama, `make check` y `make demo` ejecutados por el CTO, y el propietario ve el panel real antes de cerrar.
