# Etiquetas de más del etiquetado por tema (qwen3.5:9b), D-058

Fecha: 2026-11-12. Batería `TaggingBatteryIT` de `odin-adapter-tagger-ollama` contra el Ollama del equipo (GPU, `qwen3.5:9b`, temperatura 0), repetida el 2026-11-12. Nada se ha dejado en el código: las variantes se probaron en copias de `api/` fuera del repositorio.

## 1. Etiquetas de más en control-3 (16 etiquetas en 16 fichas)

Ninguna está entre los `aceptables` de la clave (la clave solo admite `convivencia` en 2028-016, y el modelo puso `instalaciones`). En la repetición de esta sesión la lista fue la de la verificación anterior; el estudio daba 15 con el mismo modelo.

| Ficha | Tema de más | Tema(s) de la clave | Causa probable |
|---|---|---|---|
| 2028-008 antena azotea | estructura y fachada | instalaciones | el mástil está anclado al peto: lee «elemento de la construcción» |
| 2028-013 parada cabina | estructura y fachada | instalaciones | aparece el hueco y el foso del ascensor |
| 2028-038 alumbrado viales | estructura y fachada | instalaciones | postes y viales se leen como obra |
| 2028-010 rama pino vehículo | estructura y fachada | seguros y siniestros | árbol y daño físico al vehículo, sin tema que encaje; elige el más material |
| 2028-029 sellado plato ducha | instalaciones | agua y humedades, convivencia | fontanería aparece en la definición de `instalaciones` y la ficha habla de tuberías |
| 2028-036 peto azotea | instalaciones | estructura y fachada | «azotea» y «solado» se leen como instalación común |
| 2028-021 jardineras forjado garaje | instalaciones | agua y humedades, estructura y fachada | garaje común y jardineras: «común» arrastra a `instalaciones` |
| 2028-040 desinsectación | instalaciones | otros | portal, cuarto de contenedores, ventilación: zona común |
| 2028-016 obras reforma 4B | instalaciones | ruido (acepta convivencia) | la obra toca tuberías o electricidad |
| 2028-039 bicicletas portal | instalaciones | convivencia | portal como equipo común |
| 2028-023 ocupación cubierta | instalaciones | convivencia | cubierta como zona común |
| 2028-011 cornisa acera | otros | estructura y fachada | «otros» como red de seguridad junto al tema correcto |
| 2028-017 perros jardines | otros | convivencia | idem |
| 2028-027 vigas garaje | otros | estructura y fachada | idem |
| 2028-031 barbacoas | ruido | olores y humos, convivencia | el humo molesta; lo lee como molestia |
| 2028-026 cuotas pendientes | convivencia | otros | impago entre vecino y comunidad |

Las causas son una lectura mía del texto de cada ficha y de las definiciones; no hay medida de por qué el modelo eligió cada una.

Reparto: `instalaciones` 7, `estructura y fachada` 4, `otros` 3, `ruido` 1, `convivencia` 1. Dos temas, `instalaciones` y `estructura y fachada`, ponen 11 de 16. Su definición es de dominio amplio («equipos e instalaciones comunes», «elementos constructivos») y se activa por el lugar (azotea, portal, garaje, cubierta) y no por el objeto del problema. `otros` se pone junto a otro tema, aunque su definición dice «lo que no encaje en ninguna otra». Además, en control-1 hay 1 etiqueta de más de agua (2026-013 depuradora eléctrica), que la batería también cuenta.

## 2. Qué midió el estudio (T-010)

Sí lo midió, con el mismo modelo y las mismas definiciones. Control-3, 42 temas obligatorios: `qwen3.5:9b` con definiciones, 41 de 42 (97,6 %) y 15 etiquetas de más; `qwen3.5:4b-q4_K_M` con definiciones, 39 de 42 y 19; sin definiciones, 34 y 27. No fijó un umbral ni propuso ajustar para reducirlas: D-031 prefiere que sobre una etiqueta a que falte un caso. Lo de Odín (97,6 % y 16) reproduce el estudio dentro de una etiqueta.

## 3. Instrucciones y definiciones, literales o no

- Definiciones: `temas.json` de la batería es literal respecto a `DEFINITIONS` de `clasificar.py` (los 8 temas). La clave de control-3 es idéntica byte a byte.
- Instrucción: `TaggingInstructions` no es literal. Diferencias:
  - «Eres el sistema de archivo de una administración de fincas» pasa a «de <nombre de la empresa>» (en la batería, «Administraciones Ribera»);
  - «Lee esta ficha de caso» pasa a «Lee este documento»;
  - la ficha se coloca después de la lista y antes de la regla de la duda, igual que en el estudio;
  - el estudio termina la lista con «.» tras «otros: …», igual que Odín.
  El comentario de la clase dice que son las instrucciones medidas y no es exacto. El efecto de estas dos diferencias no está medido por separado; con el modelo y los resultados, no parecen la causa (los resultados coinciden con el estudio).

## 4. Medida de las variantes (copias de prueba, una ejecución cada una, temperatura 0)

Cada celda es control-3 (30 fichas, 42 temas obligatorios). Control-1 y control-2 (solo agua) no cambian en ninguna variante: 100 % y 100 %, con 1 etiqueta de más de agua en control-1 en todas.

| Variante | Exhaustividad | Etiquetas de más (fichas) | Por tema |
|---|---|---|---|
| Actual | 97,6 % (41/42) | 16 | instalaciones 7, estructura 4, otros 3, ruido 1, convivencia 1; falta 1 de convivencia |
| (b) definiciones | 100 % (42/42) | 11 | instalaciones 4, otros 3, estructura 2, ruido 1, convivencia 1 |
| (c) instrucción «solo los claramente presentes» | 95,2 % (40/42) | 15 | instalaciones 8, estructura 4, ruido 2, otros 1; faltan 2 de convivencia |
| (b)+(c) | 100 % | 11 | instalaciones 4, estructura 3, ruido 2, otros 1, convivencia 1 |

Segundos por documento: 2,7 a 3,7 (GPU del equipo, sin diferencia por la variante).

Definiciones de la variante (b), que sustituyen a las de la batería solo en estos tres temas:
- instalaciones: «equipos e instalaciones comunes que funcionan o se averían: ascensor, electricidad, fontanería, climatización, depuradora, bombas y ventilación; no cuenta que el problema ocurra en una azotea, un portal, un garaje o una zona común, ni los daños a la construcción»
- estructura y fachada: «elementos constructivos que se dañan o desprenden: cimentación, forjados, muros, fachadas, cubiertas, fisuras y desprendimientos; no cuenta un equipo que simplemente está sujeto o instalado en ellos»
- otros: «solo si ninguna otra encaja; nunca junto a otra etiqueta»

## 5. Recomendación: (b), con una cautela

- (c) no sirve: con el mismo modelo no baja las etiquetas de más (15) y pierde un tema obligatorio más (95,2 %). Quitar «si dudas, pon todas» además contradice D-031.
- (b) baja las etiquetas de más de 16 a 11 (en 11 fichas) y sube la exhaustividad de 97,6 % a 100 %, sin coste de tiempo. Las que quedan: `instalaciones` 4, `otros` 3.
- Cautela: escribí las exclusiones de (b) viendo las fichas de control-3, así que la mejora está medida sobre las mismas fichas y puede estar inflada. El estudio escribió las definiciones a ciegas precisamente para evitarlo. Para aceptar (b) hace falta medirla en fichas nuevas, no usadas para redactarla. La lista de temas es configuración de cada cliente (D-031): conviene tratar (b) como una definición más de la lista de fincas de la demo, no como cambio de código.
- Si se prefiere no tocar nada: (a) es defendible, porque D-031 admite etiquetas de más y la exhaustividad ya es 97,6 %. El coste de (a) son 16 etiquetas de más para 42 obligatorias en control-3, es decir, fichas que aparecen en una búsqueda por tema sin tratar de ese tema.
- Si se adopta (b), hay que volver a medir con control-1, control-2 y control-3. `TaggingInstructions.VERSION` no cambia, porque (b) no toca el texto de la instrucción.

## 6. Límites

- Una ejecución por variante; temperatura 0, pero no se repitió cada variante para ver el ruido de ejecución a ejecución.
- La batería corre en la GPU del equipo; el estudio midió en CPU con 4 hilos y contexto 4.096. Coinciden los resultados, pero no se ha comprobado que ocurra lo mismo en CPU.
- `otros` no se resolvió con la definición: sigue en 3 fichas.
