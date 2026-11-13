# Cartera de mediciones y pendientes del CTO 3 (D-094)

- Fecha: 2026-11-14. Autor: CTO 3. Petición del propietario, por el CEO: planificar lo pendiente, sin desarrollo y sin IA.
- **Regla D-094:** una hipótesis cada vez, con plan escrito y revisado, comprobación previa sin IA, prueba de humo de 3 a 5 preguntas, primero lo barato, y como mucho **una medición con IA por semana, de hasta 5 USD**. Para más, hace falta el sí del propietario.
- **Los USD son consumo equivalente (precio de API, sin cobro).** Cada coste sale de un consumo medido, que se cita, o, si no lo hay, del precio de la API, y se dice.

## 1. Cartera de hipótesis, ordenada por valor

**Consumos medidos de referencia:**
- Haiku en el banco, 216 preguntas: 0,0054 USD por respuesta (prueba de `synonyms`, 13 nov.).
- Haiku en Brumeral, 34 preguntas: 0,0034 USD por respuesta (14 nov.).
- Sonnet responde a unas 20 veces el precio por token de Haiku (Sonnet 5.5: 2 y 10 USD por millón; Haiku 5.5: 0,10 y 0,50).

| # | Medición | Hipótesis | Umbral para dar por buena la mejora | Coste estimado | Qué aporta al negocio |
|---|---|---|---|---|---|
| 0 | **Listón con todas las herramientas** | Hace falta una base con las cuatro capacidades (`findSimilarCases` y el expediente incluidos) para comparar lo demás de forma justa. Hasta el 13 nov. se midió sin ellas | No es una mejora: fija la cifra de partida (peor de 3) | **0 USD si vale la base nueva del CTO 2** (216 × 3 con todas las capacidades, 13 nov.); si hay que medir en el banco, unos 3,5 USD (216 × 3 × 0,0054) | Que cada cifra que se dé al propietario sea la del producto tal como se instala |
| 1 | **Mejoras del expediente (D-093, plan del CTO 2) junto con Valtierno** | `caseDocument` en `searchKnowledge`, `verifyCitation` por lotes y la aclaración de qué forma parte del expediente suben el acierto en las preguntas de expediente sin empeorar las 216. Con Valtierno (60 documentos), las preguntas concretas no leen el expediente entero (D-078) | Peor de 3 en expedientes mejor que la base, por encima de la variación; 0 lecturas innecesarias de `readCaseFile` en las preguntas concretas; ninguna batería peor | 4,39 USD (plan del CTO 2) más unos 0,4 de Valtierno (14 × 3 × unos 0,01, sin medir: las de expediente usan más turnos) = **unos 4,8 USD** | El expediente es lo que más pide el propietario (encargo 13). Es la función con más uso previsto |
| 2 | **Mirador como batería automática** | Las 32 preguntas del caso límite, puntuadas por programa, dan la cifra de un caso realista: escaneos, contratos largos, trampas | Línea base: no es una comparación. Lo que importa son los fallos graves (citas inventadas, sustituido como vigente, «no consta» falso) y las trampas | **Unos 0,7 USD** (32 × 3 × unos 0,007, sin medir: documentos más largos que los del archivo ficticio) | Lo que el propietario prueba a mano, medido de forma repetible: un aviso automático si una mejora estropea lo que él ya vio |
| 3 | **Q153 con la búsqueda en lo general** (rama del CTO 2) | Con el filtro «solo lo general», la norma interna se encuentra a la primera y Q153 pasa de 0 de 3 a 3 de 3 con la instrucción de hoy | Q153 en 3 de 3 y ninguna pregunta de normas internas peor | **Unos 0,2 USD** con la muestra de las preguntas de normas (unas 12 × 3 × 0,0054). Antes, sin IA: el repetidor con las consultas guardadas (el CTO 2 ya lo usó) | Las normas internas del despacho son la respuesta típica que «está pero no sale» |
| 4 | **Brumeral sin ejemplos de tipo de proyecto** (D-091) | Sin ejemplos en `listProjects`, la IA deja de decir «comunidades» de una empresa de otro sector (2 de 12 con los textos neutros) | 0 de 12 en Brumeral que afirmen el sector; 0 fugas | **Unos 0,35 USD** (34 × 3 × 0,0034, medido); la rama ya está preparada | Cierra D-084: el producto vale para cualquier sector, no solo para fincas |
| 5 | **Sonnet en una muestra** | Con Sonnet, el acierto sube lo bastante como para recomendarlo a los clientes que lo tengan | Peor de 3 mejor que con Haiku, por encima de la variación | **Unos 4,3 USD** con 40 preguntas × 1 (40 × 0,0054 × 20, sin medir en el banco). Las 216 × 3 serían unos 70 USD: hace falta el sí del propietario | Qué IA recomendar a cada cliente y qué esperar de la que ya usa |
| 6 | Otras IAs (OpenAI, Google) | El conector funciona igual con otras IAs | Por definir | Sin estimar: hacen falta cuentas del propietario (T33) | Clientes que no usan Claude |

- **No están en la cartera:**
  - el jurado de IA (aparcado en D-081, falta otra rúbrica u otro modelo);
  - varios buscadores por pregunta (el acuerdo como aviso de poca seguridad: lo decide el propietario, D-016);
  - las reglas de `synonyms` (descartadas en D-081).
- **Orden recomendado:**
  - primero, el listón (0), si la base del CTO 2 sirve: no cuesta nada;
  - después, una medición por semana en el orden de la tabla.
  - La 3 y la 4 son baratas, pero D-094 pide una hipótesis cada vez: no se juntan aunque quepan en 5 USD.
- **Antes de cada una:**
  - la comprobación previa del banco tiene que pasar. Hoy falla a propósito: falta traer el arreglo del ejecutor del CTO 2 (1cfd36f);
  - prueba de humo con 3 a 5 preguntas;
  - resumen de 5 líneas al terminar.

## 2. Mirador como batería automática del banco (plan; no se lanza)

- **Objetivo:** las 32 preguntas de `experimentos/2026-11-14-caso-mirador/entrega/preguntas.md`, puntuadas por el programa del banco, con su clave y 3 repeticiones, sobre una pila del banco (puertos 48080 a 48090, D-099), nunca sobre la instalación del propietario.

| Paso | Qué | Notas |
|---|---|---|
| 1 | **Capa `mirador`** en el registro de `banco/capas.py`, con los datos de `experimentos/2026-11-14-caso-mirador/datos/` | Una sola empresa (Ribera) y un proyecto |
| 2 | **Carga real en la pila del banco**, reutilizando la orden de subida del CTO (`instalacion-anadir-proyecto`) contra los puertos del banco | Los PDF, escaneos, JPG y CSV se suben por la API, así que pasan por los lectores reales y el OCR. El cargador SQL del banco no sirve: guarda todo como texto y se perderían las trampas de escaneo y de PDF mixto. El OCR tarda unos 19 minutos (medido en la carga del propietario) |
| 3 | **Mapa de identificadores:** después de la carga, listar los documentos por la API y escribir `carga.json` (ruta ↔ documentId, ficha ↔ cardId) | Así lo esperan el banco y la comprobación previa |
| 4 | **`bateria-mirador.json`,** generado desde las preguntas de `generar/preguntas.py`, que ya localizan documento y página | Ids M01–M32. Tipos: `dato_concreto`, `version`, `contradiccion` (las dos claves), `sin_fuente` y `aislamiento` (abstención), `ficha`. Claves numéricas exactas; `citas_resueltas` con la página; `sustituidos` en las pólizas; `docs_prohibidos` en las de Javier |
| 5 | **Lo que el puntuador no sabe hacer hoy** | Hacen falta campos nuevos, acordados con el CTO 2 porque `puntuar.py` es suyo: `debe_mencionar` (la nota manuscrita: tiene que advertir de que es un escaneo y puede tener errores) y la abstención en el PDF mixto (la página de firmas no se ha leído). Sin ellos, esas dos preguntas solo se puntúan por las claves |
| 6 | **Actores:** Marta (`t01`) y Javier (`t02`), con sus claves de la carga | Javier no es miembro del proyecto: sus preguntas son de aislamiento |
| 7 | **Configuración** `banco/configs/mirador.toml`: `archives = ["mirador"]`, `battery = ["mirador"]`, un buscador `main` × 3, todas las capacidades, tope de 1 USD | Prueba de humo antes: M01, M15, M24, M27 y M29 |
| 8 | **Pruebas sin IA:** la batería se genera igual dos veces; todas las claves aparecen en sus páginas; la comprobación previa pasa con una pila cargada | Como las de la capa generalidad |

- **Esfuerzo:** unas 2 tareas de desarrollo:
  - una para la capa, la carga por la API y el mapa;
  - otra para la batería y los dos campos del puntuador, junto con el CTO 2.
- **Riesgo:** la carga por la API tarda (OCR y etiquetado). Para repetir, conviene guardar la base de la pila del banco ya cargada y restaurarla, en lugar de cargar de nuevo.

## 3. Refuerzos pendientes de D-099 en el banco (para cuando haya permiso)

- **Estado:** el guardia está en `feature/calidad-recuperacion` 40411c2. La revisión independiente encontró cuatro huecos menores.
- **Por qué no se hicieron:** el control de permisos denegó al subagente la escritura de las pruebas («Modify Shared Resources»). No se hizo por otra vía. Hace falta el permiso del propietario.

| # | Hueco | Arreglo | Prueba |
|---|---|---|---|
| 1 | Los puertos se comparan como texto: `ODIN_MCP_PORT=08090` pasa en `banco/propietario.py` y en `pila.sh` | `int()` en Python y `$((10#…))` en shell, comprobando antes que sea un número; si no lo es, se niega con «No se ha cambiado nada.» | `08090`, `abc` y un puerto no numérico en una URL |
| 2 | No se mira la URL del juez local (`ODIN_BANCO_OLLAMA_URL`): se podría mandar contenido al puerto del propietario | Añadirla a las URL que revisa la sección «propietario» y la orden de `propietario.py` | `http://127.0.0.1:8080` |
| 3 | La orden `python3 banco/propietario.py` (`banco-arriba`, `banco-cargar` y `banco-abajo`) no lee las URL de `carga.json` | Un único ayudante en `comprobacion.py` (`urls_del_banco(layout)`: URL de la pila más la del juez), que usan la comprobación previa y la orden | La orden con un `PILA_ESTADO` temporal cuyo `carga.json` apunta a 8090 |
| 4 | Un `carga.json` cuya raíz no es un objeto (`[]`, `"x"`) da un error sin el mensaje de la regla | Comprobar el tipo y convertirlo en el mensaje de «No se ha cambiado nada.» | `[]` y `"x"` |

- **Esfuerzo:** una tarea corta. Las pruebas primero; pasa `make banco-test`; revisión independiente.
