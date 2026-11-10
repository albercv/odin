# Odín · Dirección visual del panel y de la demo

- Fecha: 2026-11-10. Decisión de negocio: D-039 (y D-038 para la marca por cliente).
- Estado: borrador para aprobación del propietario.
- Maqueta aprobada por el propietario el 2026-11-10: `2026-11-10-direccion-visual/maqueta.html` (ábrela con un servidor local; es desechable y no es código del panel).
- La maqueta fija el aspecto. Cuando enseña algo que la API todavía no da o que es de la tarea 5 (número de claves, «Quién lo ve», selector de empresa, nombre del grupo), manda este documento.

## 1. Por qué

El propietario quiere que Odín «se vea bien desde el principio». El panel de la fase 1 funciona pero tiene el aspecto de un formulario sin diseño: no transmite el producto en la reunión de la demo y no lleva la marca de nadie. D-039 adelanta la dirección visual a la primera tarea de la fase 2a, antes de las pantallas nuevas del panel, para no rehacerlas después.

## 2. Objetivos, no objetivos y antiobjetivos

**Objetivos**
- Un sistema visual propio para el panel: colores, letra, componentes, disposición y movimiento, aplicado a todas sus pantallas.
- Marca configurable por instalación (D-038): E2D es la marca por defecto y ningún valor de marca está fijo en el código.
- Imagen del «cerebro» en 3D en la pantalla de entrada y en la portada de la demo.
- Accesibilidad WCAG 2.2 AA comprobada con pruebas.

**No objetivos**
- La web comercial.
- Editar la marca desde el panel: la marca es configuración de la instalación.
- Cambiar el comportamiento del panel o de la API. Esta tarea cambia el aspecto, no lo que hace cada pantalla.
- Las pantallas nuevas de la fase 2a (personas con varias empresas, selector de empresa, proyectos restringidos): son la tarea 5, que las construirá con este sistema.

**Antiobjetivos** (aunque funcione, sería un fallo)
- 3D o movimiento decorativo en las pantallas de gestión.
- Una entrada más lenta por culpa del 3D.
- Texto por debajo de 4,5:1 de contraste en cualquier tema o marca.
- Peticiones a internet en tiempo de ejecución: fuentes o librerías desde una CDN.
- Un nombre, color o logo de E2D escrito en el código del panel.

## 3. Alcance

| Superficie | Qué entra |
|---|---|
| Panel del administrador | Todas sus pantallas, incluida la entrada |
| Demo | Portada a pantalla completa con el cerebro, para abrir la reunión; el resto de la demo es el panel y la IA del trabajador |
| IA del trabajador (MCP) | Sin trabajo visual. Solo coherencia de términos con el panel (apartado 12) |

## 4. Restricciones

- Todo local: fuentes, iconos y código se sirven desde la instalación.
- Equipos modestos en los clientes: el panel no puede depender de una GPU ni de un procesador potente.
- Calidad (D-029): TDD, métodos de 30 líneas como máximo, nombres en inglés y explicativos, comentarios mínimos, sin duplicar.
- Sin cambios en la API. Lo que el panel necesita y la API no da directamente se monta en el panel con las listas que ya existen (apartado 11), o espera a la tarea 5.
- Navegadores: los de 2022 en adelante (Chrome y Edge 108+, Safari 16+, Firefox 110+). Se usa `<dialog>`. No se usan `popover`, posicionamiento por anclas ni `color-mix()`: los tintes los calcula la derivación de colores. View Transitions solo como mejora donde exista.
- Las pruebas de extremo a extremo que ya existen (`panel/e2e/admin.spec.ts`) y `make demo` siguen pasando. Si cambia el nombre accesible de un control, la prueba se adapta en la misma tarea y la regla del nombre se mantiene: «<acción> <nombre de lo afectado>» (por ejemplo, «Editar Marta Ibáñez»).

## 5. Lo que decidieron el propietario y el CEO

- **Aspecto:** el de la maqueta, aprobada por el propietario el 2026-11-10.
- **3D:** solo en la entrada y en la portada de la demo («Entrada y demo», propietario). Nunca en las pantallas de gestión.
- **Condiciones del 3D** (D-039):
  - ligero;
  - respeta la preferencia de reducir movimiento;
  - tiene una imagen estática de respaldo;
  - nunca retrasa la carga ni la entrada.
- **Marca:** el panel lleva la marca de cada cliente; E2D es la marca por defecto. No se crea un logo nuevo: se usa el que existe.
- **IA del trabajador:** no tiene pantalla; su imagen es el formato coherente de las respuestas.

## 6. Kit de marca de E2D

Montado a partir de lo que existe; no hay manual de marca.

**Logo:** la insignia circular «Evolve 2 Digital» de `e2d-website_v2/public/e2d_logo.webp` (1184 × 1168 px).
- Se usa tal cual: ni recolorear, ni recortar, ni deformar.
- Tamaño mínimo: 32 px de diámetro.
- Espacio libre alrededor: un cuarto del diámetro.
- Funciona sobre fondo claro y oscuro porque lleva su propio disco pizarra.
- No hay versión vectorial. De los otros dos ficheros, `e2d-dashboard/public/logo-e2d.svg` es solo el texto «E2D» en la fuente del sistema, y `landing-e2d/src/logo.svg` es el logo de React de la plantilla de Vite: ninguno sirve.

**Colores**, medidos en el logo:

| Nombre | Valor | Uso |
|---|---|---|
| Pizarra | `#283039` | Fondo de la barra lateral y de la portada |
| Turquesa | `#05b4b9` | Acento sobre fondos oscuros |
| Blanco | `#ffffff` | Línea de pulso del logo, texto sobre pizarra |

El turquesa del logo no sirve como texto sobre fondo claro (2,55:1). En tema claro, el acento se deriva automáticamente (apartado 7); para E2D sale en torno a `#03797c`.

La paleta del kit de propuestas de E2D (`crapWebFinder/kit/kit.css`: `#080a0b`, `#22c5b6`, `#f0f0f0`) tiene otro turquesa, `#22c5b6`. Se adopta el del logo, que es la pieza de marca y el que aprobó el propietario en la maqueta.

**Letra:** IBM Plex Sans (400, 500 y 600) para toda la interfaz; IBM Plex Mono (400) solo para las claves `odin_…` y los identificadores. Licencia OFL.

## 7. Marca configurable

### 7.1 Qué es una marca

Un directorio con `brand.json` y un logo. Ejemplo de E2D:

```json
{
  "name": "E2D",
  "logo": "logo.webp",
  "logoAlt": "E2D",
  "colors": { "accent": "#05b4b9", "base": "#283039" },
  "cover": {
    "title": "Odín, el cerebro digital del despacho",
    "subtitle": "Lo que tu despacho ya resolvió, al alcance de cada persona y siempre con su fuente.",
    "note": "Demostración con un despacho y casos ficticios"
  }
}
```

- `name` y `logoAlt`: texto; el nombre aparece como «Odín de <name>» en la entrada.
- `logo`: opcional; fichero del mismo directorio, en PNG, WebP o SVG. El logo siempre se ve sobre el color `base` (barra lateral, entrada y portada), así que la marca debe dar uno que funcione sobre ese fondo; no hay un segundo logo para fondo claro. Sin logo, se ven las iniciales de `name` (hasta dos letras) en un círculo del color de acento, como «FR» en la maqueta.
- `colors.accent`: el color de marca. `colors.base`: el color oscuro de la barra lateral y de la portada.
- `cover`: opcional. Si falta, la portada de la demo no existe para esa instalación (apartado 11).
- El nombre del producto, «Odín», no es configurable (D-038).

### 7.2 Dónde vive

- La marca por defecto, E2D, está en el repositorio como datos, en `panel/brand/e2d/`, y la imagen del panel la incluye en `/brand/`.
- Cada instalación puede montar su propio directorio en `/brand/` del contenedor del panel, por configuración de Compose (`ODIN_BRAND_DIR` en `deploy/.env`). Sin esa variable, se usa la de E2D.
- El panel lee `/brand/brand.json` al arrancar, antes de pintar. Si el fichero falta o no es válido, el panel arranca con un tema neutro sin marca, avisa en la consola y funciona igual.
- Un `brand.json` no válido no rompe nada: se valida (colores en hexadecimal de 6 cifras, logo con extensión admitida, textos con longitud máxima) y, si falla, se usa el tema neutro.
- **Tema neutro:** acento `#4f6d8f` y base `#2b3036`, pasados por la misma derivación de colores. Sin logo ni nombre: la marca muestra solo «Odín» y una «O» en el círculo. El cerebro se ve, porque es del producto y no de la marca. Sin `cover`, no hay portada.

### 7.3 Derivación de colores

De `accent` y `base` salen, con una función pura y probada, los colores de cada tema:

- **Acento para tema claro:** se oscurece el acento (bajando la luminosidad en HSL, conservando el tono) hasta que alcanza 4,5:1 contra todos los fondos claros donde se usa como texto: blanco, fondo de página, gris de las cabeceras de tabla y el tinte del acento.
- **Texto sobre el botón del tema claro:** blanco si da 4,5:1; si no, el texto oscuro.
- **Acento para tema oscuro:** se aclara hasta alcanzar 4,5:1 contra todos los fondos oscuros: superficie, fondo, gris y la barra lateral (la base y la base profunda).
- **Texto sobre el botón del tema oscuro:** el texto oscuro (`#172026`) si da 4,5:1; si no, blanco.
- **Base:** se oscurece si hace falta hasta que el texto de la barra lateral (`#c5ced4`) alcance 4,5:1. La base profunda del tema oscuro es la base mezclada con negro.
- **Regla de uso:** el acento nunca va como texto sobre el tinte del propio acento. El elemento seleccionado se marca con el tinte de fondo, el texto normal y una marca de acento (borde o barra). En tema oscuro, el turquesa sobre su tinte da 4,43:1.

- **Tintes:** el tinte del acento (10 % sobre la superficie clara, 14 % sobre la oscura) también lo calcula la derivación, no CSS, porque `color-mix()` queda fuera de los navegadores admitidos.

Los neutros (fondos, texto, líneas, avisos de pendiente y error) son fijos y no dependen de la marca. Así el contraste del resto queda garantizado.

### 7.4 Contraste medido con la marca E2D

| Par | Colores | Contraste |
|---|---|---|
| Claro: texto | `#1d252c` sobre `#ffffff` | 15,52 |
| Claro: texto secundario | `#56636d` sobre `#eef2f4` | 5,48 |
| Claro: acento (derivado) | `#03797c` sobre el tinte `#e6f2f2` | 4,55 |
| Claro: texto en el botón | `#ffffff` sobre `#03797c` | 5,21 |
| Claro: pendiente | `#8a4b00` sobre `#fdf1dc` | 6,09 |
| Claro: error | `#b42318` sobre `#ffffff` | 6,57 |
| Oscuro: texto | `#e8eef1` sobre `#232c34` | 12,10 |
| Oscuro: texto secundario | `#9fb0bb` sobre `#232c34` | 6,35 |
| Oscuro: acento | `#05b4b9` sobre `#232c34` | 5,56 |
| Oscuro: texto en el botón | `#172026` sobre `#05b4b9` | 6,48 |
| Oscuro: error | `#f97066` sobre `#232c34` | 5,09 |
| Barra lateral: texto | `#c5ced4` sobre `#283039` | 8,37 |
| Barra lateral: activo | `#05b4b9` sobre `#283039` | 5,24 |

### 7.5 Claro y oscuro

- El panel sigue la preferencia del sistema.
- En «Mi cuenta» se puede fijar claro, oscuro o «como el sistema». La elección se guarda en el navegador, no en la API.

## 8. Sistema visual

**Neutros** (valores de la maqueta):

| Token | Claro | Oscuro |
|---|---|---|
| Fondo | `#f3f6f7` | `#1b2229` |
| Superficie | `#ffffff` | `#232c34` |
| Gris (cabeceras, hover) | `#eef2f4` | `#2a343d` |
| Línea | `#dbe2e6` | `#34404a` |
| Texto | `#1d252c` | `#e8eef1` |
| Texto secundario | `#56636d` | `#9fb0bb` |

**Escala tipográfica:** 13, 14, 15 (texto), 18, 22, 26 px. Pesos: 400 para el texto, 500 para etiquetas y botones, 600 para títulos.

**Radios:** 6 px en controles, 10 px en elementos de lista, 16 px en secciones.

**Disposición:**
- Barra lateral fija de 248 px, siempre en el color base de la marca: logo; el nombre de la empresa abierta, sin interacción (el selector de empresa es de la tarea 5, que lo pondrá en ese hueco; el nombre del grupo no se muestra porque la API no lo da); navegación con su contador de fichas pendientes; y abajo la persona, «Mi cuenta» y «Salir».
- El administrador de grupo sigue cambiando de empresa como hoy, desde «Empresas» → «Abrir <empresa>».
- Contenido a la derecha, alineado a la izquierda: cabecera de página con título y acción principal, y secciones con tablas.
- Por debajo de 900 px, la barra lateral pasa a barra superior.

**Componentes:** botón (principal, normal, discreto y de peligro), botón de icono, campo con etiqueta y error, área de texto, interruptor, etiqueta de estado, sección con cabecera, tabla, aviso emergente, diálogo de confirmación, estado vacío, cabecera de página, marca (logo y nombre) y barra lateral.
- El diálogo de confirmación sustituye a `window.confirm`, que no lleva la marca ni el tema.
- El aviso emergente sustituye a los textos sueltos de «Contraseña restablecida» y similares, con el mismo `role="status"`.

**Dónde van los formularios:**
- **Crear** (añadir persona, nuevo proyecto, nueva empresa, subir documento): en un `<dialog>` modal que se abre con la acción principal. El foco va al primer campo y vuelve al botón al cerrar.
- **Clave recién dada** («No se volverá a mostrar»): en un `<dialog>` modal que solo se cierra con «Entendido», para que nadie la pierda por un clic fuera.
- **Editar algo de una fila** (renombrar, editar persona, ver claves, restablecer contraseña): se despliega dentro de la propia fila, como hoy.
- **Acciones secundarias de una fila:** un botón «Más acciones de <nombre>» (`aria-expanded`) despliega en la fila los botones de siempre, con su nombre accesible de hoy («Editar Marta Ibáñez»). Sin `popover` ni menús flotantes.

**Iconos:** un conjunto propio y pequeño de SVG en línea (unos 15), con trazo de 1,75 px. Siempre acompañan a un texto o llevan nombre accesible.

## 9. Movimiento

Solo como respuesta a lo que hace la persona, para enseñar qué ha cambiado:

| Momento | Movimiento | Duración |
|---|---|---|
| Pulsar un botón | Escala a 0,97 | 100 ms |
| Cambiar de pantalla | Fundido con View Transitions donde el navegador lo admita; donde no, cambio directo | 180 ms |
| Aprobar, corregir o rechazar una ficha | La ficha se pliega y sale de la cola; aparece la siguiente | 220–250 ms |
| Aviso emergente | Entra subiendo 10 px y sale solo a los 2,6 s | 200 ms |
| Interruptor | Desliza el punto | 200 ms |

- La curva es `cubic-bezier(.23, 1, .32, 1)`, una salida suave.
- Con la preferencia de reducir movimiento, todo pasa a cambio directo.
- Sin librerías de animación: CSS y la API de View Transitions.

## 10. El cerebro

**Qué se ve:** un cerebro de perfil hecho de unos 570 puntos unidos en red:
- dos hemisferios con circunvoluciones y la cisura entre ellos, unidos solo por un puente central;
- el cerebelo y el tronco.

El cerebro gira unos 35° a cada lado, nunca entero, para que siempre se reconozca. Por la red viajan señales: cada llegada enciende la neurona con un destello del color de la marca y a veces la señal sigue en cadena.

**Cómo** (decisión del CTO, apartado 16): en canvas 2D, con la proyección 3D calculada a mano, sin Three.js ni otra librería.
- Geometría: una función pura y determinista (semilla fija) que devuelve los puntos y las conexiones.
- Dibujo: un módulo que recibe el contexto del canvas.
- Componente React: monta el canvas y gestiona el ciclo de vida.

**Condiciones de D-039, y cómo se cumplen:**
- **Ligero:** sin dependencias. La geometría se calcula una vez. Las líneas se dibujan agrupadas por profundidad, en cuatro trazos por fotograma. En la maqueta, sobre el Mac del propietario: 7 ms para montar el cerebro y 0,5 ms por fotograma. La cifra en un equipo modesto se mide en la tarea (apartado 13).
- **Reducir movimiento:** si el sistema lo pide, se dibuja un solo fotograma quieto. Lo mismo en equipos de 2 núcleos o menos, o con ahorro de datos activado.
- **Imagen estática de respaldo:** ese fotograma quieto, sobre un degradado CSS con el color de la marca. Si el canvas no está disponible, queda el degradado.
- **No retrasa la carga:** el formulario de entrada se pinta primero. El cerebro arranca en el fotograma siguiente, no bloquea el foco ni el envío del formulario, y se para cuando la pestaña no se ve o se sale de la pantalla.
- **Umbrales**, con la CPU limitada a un cuarto (criterio de aceptación):
  - mediana del dibujo por fotograma de 4 ms como máximo;
  - el formulario de entrada responde (foco y escritura en el correo) como mucho 50 ms más tarde que sin cerebro.
- **Protección en marcha:** si la media de los primeros 60 fotogramas pasa de 12 ms, el cerebro se queda en el fotograma quieto.
- **Color:** el del acento del tema oscuro de la marca.

## 11. Pantallas

| Pantalla | Qué cambia |
|---|---|
| Entrada | Dos mitades: a la izquierda, la marca, el cerebro y el lema «Cómo se resolvió, y dónde consta.»; a la derecha, el formulario. Ayuda para la contraseña olvidada: «Pide a quien administra el grupo que te la restablezca». |
| Portada de la demo | A pantalla completa, sin sesión ni llamadas a la API: el cerebro, los textos de `cover` y el botón «Empezar la demostración», que lleva a la entrada. Se abre con la dirección `#portada`. Sin `cover` en la marca, esa dirección lleva a la entrada. |
| Empresas | Lista de empresas del grupo, con su nombre y sus capacidades como interruptores. |
| Empresa | Cabecera con el nombre y las capacidades: interruptores para el administrador de grupo; etiquetas de solo lectura para el administrador de empresa, que hoy no puede cambiarlas. Sección de proyectos (nombre y acciones; la columna «Quién lo ve» llega con la tarea 5). Sección de personas (nombre y correo, papel, acceso y acciones; el número de claves no se muestra, porque pediría una llamada por persona, y las claves se ven al desplegarlas). La acción principal es «Añadir persona». |
| Documentos | Mismo patrón: cabecera, subida como acción principal y tabla. |
| Revisión de fichas | Dos columnas: la cola de fichas pendientes y el detalle de la seleccionada. Ver los detalles abajo. |
| Mi cuenta | Formulario de contraseña y la elección de tema claro, oscuro o del sistema. |

**Detalle de la revisión de fichas:**
- Etiqueta «Pendiente de revisión».
- Origen: «Ficha redactada por la IA de <persona>», con un borde discontinuo del color de acento junto al texto, para que nunca se confunda con un documento original.
- Proyecto, quién la guardó y la fecha.
- La ficha trae `authorId` y `projectId`. Los nombres salen de las listas de personas y de proyectos de la empresa, que la pantalla carga una vez, no una por ficha. Si el autor ya no está en la empresa: «una persona que ya no está en la empresa». Sin proyecto: «Sin proyecto».
- Texto editable. Si se cambia, el panel lo dice y deja ver el original.
- «Aprobar»; «Corregir y aprobar», que solo se activa si el texto cambió; y «Rechazar», que pide el motivo.

Las acciones de las filas siguen el apartado 8 («Dónde van los formularios»).

## 12. Voz y términos (panel y MCP)

Los mismos términos en el panel y en lo que el MCP dice a la IA del trabajador:

| Término | Uso |
|---|---|
| Odín | Nombre del producto, siempre igual. |
| Documento | Original subido a Odín; se cita por documento, página y texto literal. |
| Ficha de conocimiento (o «ficha») | La redacta la IA de una persona cuando esta lo pide; se cita por título, autor y fecha, y nunca se presenta como documento. |
| Pendiente de revisión | Ficha guardada que el administrador aún no ha aprobado. |
| No consta | Respuesta cuando no hay fuente. |

Los textos del panel siguen la misma voz: verbos claros, frases cortas y sin disculpas. Cada botón dice lo que hace, y el aviso de después repite su verbo («Aprobar» → «Ficha aprobada»). El MCP ya usa estos términos (`mcp/src/server.ts`, `saveKnowledgeCard`); solo se revisa que el panel coincida.

## 13. Pruebas y medición

**Pruebas unitarias:**
- Derivación de colores: para la marca E2D y para un conjunto de al menos 12 acentos difíciles, todos los pares de texto que usa el panel dan 4,5:1 o más, en los dos temas. Los acentos difíciles incluyen amarillo, verde lima, pasteles claros, casi negro, gris y rojo puro.
- Neutros: la tabla de pares fijos (apartado 7.4) da 4,5:1 o más.
- Validación de `brand.json`: ficheros válidos y no válidos.
- Geometría del cerebro:
  - es determinista;
  - el número de puntos está dentro de lo previsto;
  - toda neurona tiene al menos una conexión;
  - ninguna conexión cruza la cisura salvo las del puente central.

**Pruebas de componentes:**
- El cerebro con reducir movimiento: un solo dibujo y ningún bucle de animación.
- El cerebro se para al desmontarse y con la pestaña oculta.
- Diálogo de confirmación, aviso emergente, interruptor y tema guardado.

**De extremo a extremo:**
- `admin.spec.ts` adaptado.
- Revisión de accesibilidad automática con axe en la entrada, empresa, revisión y portada, en los dos temas.
- La portada aparece con `cover` y no aparece sin él.

**Medición, para registrar y no para declarar sin ella:**
- Tamaño del panel compilado antes y después, con el peso de las fuentes aparte.
- Tiempo de dibujo por fotograma del cerebro, con la CPU limitada a un cuarto (emulación de Chrome en Playwright).
- Tiempo hasta que el formulario de entrada responde, con y sin cerebro.

Al final, el propietario ve el panel real antes de dar la tarea por cerrada.

## 14. Lo que queda al constructor

- La organización interna de carpetas del panel, siguiendo la que ya tiene.
- Los nombres de componentes y módulos, en inglés y explicativos.
- Los textos de ayuda y vacíos que no estén en este documento, con la voz del apartado 12.
- El favicon (el logo de la marca o una «O» con el acento).
- Los detalles de espaciado que la maqueta no fija.

## 15. Encaje con la fase 2a

- Esta tarea va antes de la tarea 5 (pantallas nuevas del panel), que pasa a construirse con este sistema.
- Las tareas 3 y 4 (API y seguridad por filas) no se tocan y pueden avanzar a la vez.
- El spec de la fase 2a dice hoy que el rediseño visual y la marca configurable son de la fase 2b. Al aprobar este documento se actualizan ese spec y el plan de la 2a. Lo hace quien lleva la 2a, para no pisar su trabajo en curso.

## 16. Decisiones del CTO (para el registro técnico, T-014)

| Decisión | Motivo | Descartado |
|---|---|---|
| Cerebro en canvas 2D con proyección propia | Sin dependencias, cabe en equipos modestos y sale bien en la maqueta | Three.js: añade una librería y más carga en la entrada para una imagen que no lo necesita. Se reconsidera si el propietario quiere más 3D en la demo. |
| Marca en `brand.json` servido por la instalación | Una instalación cambia de marca sin recompilar; es configuración, no código (D-013, D-038) | En tiempo de compilación: una imagen por cliente. En la base de datos y editable desde el panel: no se ha pedido. |
| Colores de acento derivados por contraste | Cualquier color de cliente queda legible sin revisarlo a mano | Pedir al cliente cuatro colores ya contrastados: frágil y sin garantía. |
| Neutros fijos | El contraste del resto no depende del cliente | Neutros por marca. |
| CSS Modules con variables CSS | Vite los trae y el panel no gana dependencias | Tailwind: una dependencia y otra forma de escribir estilos en un panel pequeño. |
| IBM Plex Sans y Mono desde paquetes `@fontsource`, servidos por el panel | Legible en interfaces, con tildes y eñes completas, licencia OFL y sin CDN | Fuentes del sistema: cambian en cada equipo. Inter: la más vista. |
| Iconos SVG propios | Son unos 15; no merece una dependencia | lucide-react y otros paquetes de iconos. |
| `<dialog>` nativo para crear y confirmar; acciones de fila desplegables en la propia fila | Accesible, sin librerías y compatible con navegadores de 2022 | `popover` y anclas (más recientes); Radix u otros paquetes de componentes. |
| Navegadores de 2022 en adelante | Equipos de oficina con Chrome o Edge actualizados; nada del panel depende de funciones más nuevas | Solo lo más reciente: dejaría fuera equipos sin actualizar. |
| Umbrales del cerebro y protección en marcha | Convierte «ligero» y «no retrasa la carga» en criterios que se pueden medir | Medir solo para registrar, sin criterio de aceptación. |
| Formularios de crear en `<dialog>`, edición en la fila | La clave entregada no se puede perder; editar en la fila mantiene el contexto y las pruebas de hoy | Todo en modales; todo en la página. |
| Revisión de accesibilidad con `@axe-core/playwright` | Comprueba AA en cada `make demo` (donde corren las pruebas de extremo a extremo) y no depende de una revisión a ojo | Solo revisión manual. |
