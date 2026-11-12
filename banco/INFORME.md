# Informe de `banco/` (orden de entrada, informe y `make`)

Python 3 de la biblioteca estándar. Pruebas: `make banco-test` (todas las de `banco/` y las de `medir/`). Las pruebas usan falsos: `fake_claude.py`, `banco/fakes/fake_judge.py` y un Ollama falso en 127.0.0.1. Nunca Docker, nunca el `claude` real.

## Órdenes

```
make banco-arriba                       # sube la pila propia (puertos 48080, 48088, 48090; estado en banco/estado)
make banco-cargar ARCHIVOS=ficticio    # carga el archivo ficticio y pone las marcas de sustitución
make banco CONFIG=banco/configs/piloto.toml          # run: buscadores, respuesta final y jueces
make banco-barrido CONFIG=banco/configs/piloto.toml  # sweep: curvas de mezclas y de turnos (sin redactor ni jueces)
make banco-informe RESULTADOS=banco/resultados/<carpeta>   # reescribe resumen.json e informe.md sin IA
make banco-abajo                        # baja la pila sin tocar otros proyectos de Docker
```

Equivalente sin make: `python3 banco/banco.py run|sweep --config X` y `python3 banco/banco.py report --results DIR`.

Variables de entorno (por defecto, las de producción): `ODIN_BANCO_CLAUDE` (`claude`), `ODIN_BANCO_CLAUDE_JUEZ` (igual que el anterior), `ODIN_BANCO_OLLAMA_URL` (`http://127.0.0.1:11434`) y `PILA_ESTADO` (`banco/estado`).

## Salida

Cada orden crea `banco/resultados/<AAAA-MM-DD-HHMM>-<nombre>/` con:

| Fichero | Contenido |
|---|---|
| `ORIGEN.txt` | configuración, rama, huella del código (commit; con «+hash» si había cambios sin commit en banco o medir), modo, batería, muestra, fecha |
| `ids.json` | documentos y fichas (ids de Odín a rutas), preguntas; sin claves de API |
| `buscadores.jsonl` | una línea por ejecución de buscador (con `cached`) |
| `final.jsonl` | respuesta final por pregunta, `correcta_script`, coste del redactor, `cached` y `error` si lo hubo (`run`) |
| `veredictos.jsonl` | veredictos de cada juez (con `cached`), veredicto del jurado, `motivo` si no hay veredicto («no juzgable: no consta» o «parada») y `error` si lo hubo (`run`) |
| `turnos.jsonl`, `curvas.json` | barrido: ejecuciones por tope de turnos y curvas (`sweep`) |
| `resumen.json`, `informe.md` | resumen en claves camelCase e informe en español |
| `libro.json` | gasto real del experimento (`spent`), registros de coste desconocido (`unknown`) y tope (`cap`) |
| `PARADA.txt` | solo si hubo parada: tope de gasto, coste desconocido, cuota o límite, aislamiento roto |

`buscadores`, `final`, `veredictos` y `libro.json` se escriben siempre, también si algo falla a mitad (`try/finally`); tras una parada, `buscadores.jsonl` incluye lo que se hizo antes de parar, también los registros de pago fallidos (la excepción de parada lleva `records`), y lo que quedó en la caché, sin duplicados. La orden sale con código 2 si hubo parada.

## Caché y reanudación

La clave de cada ejecución (`banco/cache.py`) incluye la pregunta, el enfoque, el modelo, la réplica, el tope de turnos, la **huella del código** y un hash de los **mapas de ids** de la pila (`doc_ruta` y `ficha_f` de `carga.json`). La huella del código es el HEAD; si hay cambios sin commit en `banco/` o en `medir/`, se añade un hash de esos cambios (`git diff HEAD`). Así, cambiar el código del banco o recargar la pila con ids nuevos no reutiliza ejecuciones antiguas.

Limitación: el hash cubre los ficheros con cambios seguidos por git; un fichero nuevo sin añadir a git no entra en la huella.

La respuesta del redactor tiene su propia clave (`final_key`): pregunta, tipo de redactor, modelo, las claves de las ejecuciones de entrada que usó (sin orden, solo las sin error), huella del código y pila. Cada veredicto de juez tiene la suya (`verdict_key`): pregunta (id y texto, que el juez lee), hash del JSON de la respuesta final, rúbrica, modelo, número de juez, semilla, `think`, `timeoutS`, huella del código y pila. Cambiar cualquiera de ellos hace que el veredicto se vuelva a juzgar. Los veredictos de Ollama también se guardan, porque cuestan tiempo.

Los registros con error no se guardan en la caché (`RunCache.put`), ni tampoco los veredictos inválidos (`invalido`, p. ej. una respuesta sin campos válidos): se relanzan. Un veredicto guardado se sirve aunque se haya agotado el presupuesto o la orden esté parándose: no lanza nada, así que no necesita presupuesto. Solo los lanzamientos reales consumen presupuesto. Las órdenes `sweep` no juzgan: no tienen veredictos que cachear.

## Presupuesto (un solo tope por experimento)

`budgetUsd` limita todo el experimento: buscadores, redactor (`composer`, `pageFusion`) y jueces de Claude. Un libro de gasto compartido (`banco/ledger.py`, con cerrojo) suma lo gastado. Antes de cada llamada de pago se comprueba bajo el cerrojo si queda presupuesto; si no, la llamada no se lanza, se escribe `PARADA.txt` con lo gastado y el tope, y las preguntas restantes quedan sin respuesta o sin veredicto. El exceso solo puede venir de llamadas ya en vuelo (como mucho `concurrency − 1`).

En `sweep` el tope es acumulativo: el grupo principal y cada pasada de turnos comparten el mismo libro.

**El tope es del experimento, no de la orden.** El libro se guarda en `banco/estado/libro-<nombre>.json` (con el total y la lista de cargos con fecha, escrita de forma atómica en cada cargo). Al arrancar, `run` y `sweep` leen ese libro: si ya se gastó el tope, la orden se niega con código 2 y un mensaje antes de crear carpeta alguna. Relanzar tras una parada no vuelve a gastar: lo ya hecho sale de la caché (ver «Caché y reanudación») y lo gastado sigue contando. Con `--reiniciar-presupuesto` (en make, `REINICIAR=1`) el libro anterior se archiva como `libro-<nombre>.<fecha>.json` y el tope empieza en cero. Un libro con algún coste desconocido también se niega a arrancar.

Los jueces de Ollama son locales y de coste 0. Su llamada ignora los proxies del entorno (`HTTP_PROXY`), así que solo va al host local configurado: no pasan por la comprobación de presupuesto ni cargan en el libro. Si se agota el presupuesto, siguen corriendo; los de pago de esa pregunta no, y la pregunta queda sin veredicto.

**Relanzar no vuelve a pagar lo ya hecho.** Al relanzar tras una parada, lo que está en la caché (buscadores, redactor y veredictos de los jueces, también los de Ollama) sale de ella con coste 0 y `cached: true`; no se lanza ni se cobra nada de nuevo. Lo gastado en la orden anterior sigue contando en el libro, salvo que se reinicie con `--reiniciar-presupuesto`: en ese caso el libro anterior se archiva y el nuevo empieza en cero, pero la caché sigue sirviendo.

**Fallos tras una llamada de pago:** un buscador, el redactor o un juez de Claude que falla tras llamar a `claude` carga su coste como desconocido (`charge(None)`) y agota el presupuesto. Así no se pierde el gasto ni se sigue gastando sin contarlo.

**Gasto notificado:** `cost.notifiedUsd` es el total real cargado en el libro. `cost.notifiedUsdFromRecords` es la suma de los registros, como diagnóstico.

**Coste desconocido:** un registro de pago nuevo sin `coste_usd` numérico no cuenta como 0. Se suma a `unknownCostRecords` (en `resumen.json` y en el informe) y agota el presupuesto: no se lanza ninguna llamada de pago más, con la razón «coste desconocido». Un juez de Claude sin `total_cost_usd` en su resultado, o una llamada fallida sin coste, cae en este caso. Un error de buscador sin llamada a `claude` (sin duración, p. ej. «sin clave de API») no es coste desconocido: se carga con 0. Los tiempos agotados y los fallos después de lanzar `claude` siguen como desconocidos.

## Configuración del barrido

```toml
[sweep]
mixes = [{ main = 1 }, { main = 5 }, { main = 5, topic = 5 }]   # enfoque = número de ejecuciones
draws = 20                                                    # por defecto 20
singleMaxTurns = [10, 20, 40]                                 # por defecto 10, 20 y 40
```

## Jueces

En `[[judges]]`, `think` (false por defecto) se envía a Ollama como `"think"` en `/api/chat`, y `timeoutS` (600 por defecto) es el tiempo de espera de su petición. Ambas claves solo se admiten en jueces `ollama:`; en un juez de Claude se rechazan con error de configuración. Si la orden ya está parada cuando empiezan los jueces, no se lanza ningún juez (tampoco de Ollama): solo se sirven los veredictos guardados, y cada pregunta respondida tiene su fila de veredicto (sin veredicto si no estaba en caché).

## Criterios que fija el código (no están en la configuración)

- **Muestra:** estratos por `sample.strata`, en orden alfabético; cada estrato se baraja con `random.Random(seed)` y se reparte por turnos.
- **Semilla de los jueces:** `sample.seed + índice del juez` (0 sin muestra). Cada juez baraja las fuentes con su propia semilla.
- **Recuperación por enfoque:** de las preguntas que ese enfoque midió (con alguna ejecución sin error) y que tienen citas resueltas, las que tienen alguna página esperada vista o citada. Las preguntas sin citas (abstenciones) no cuentan. El denominador es ese conjunto, no toda la batería.
- **Acierto por enfoque:** sobre las ejecuciones sin error, igual que el acierto global. Las ejecuciones con error se cuentan aparte (`errors`).
- **Firma de mayoría:** `answer_signature` = (`no_lo_se`, conjunto de documentos o fichas citados: `documentId` o `cardId`). Sin cifras ni páginas: la redacción, las cifras y la página citada no cuentan, solo el documento o la ficha. Es la firma que mejor separa en lo medido (D-067): en la mayoría, 132/132 en el barrido y 91/91 en el piloto; fuera de la mayoría, 14/19 y 7/9.
- **Acuerdo entre buscadores:** `k` = cuántos buscadores coinciden con la firma mayoritaria. El acierto de cada grupo es el de la respuesta final, no el del buscador.
- **«No consta» sin juzgar:** una respuesta final con `no_lo_se` o sin citas no llega al jurado: no se lanza ningún juez (ni de pago ni de Ollama), la fila lleva `motivo: "no juzgable: no consta"` y `jury` nulo. En `resumen.json`, `judges.notJudgedNoConsta` cuenta esas preguntas y `judges.manualReview` lista las que no tienen clave (sin veredicto del script ni del jurado; hay que revisarlas a mano). Donde hay clave, decide el script como siempre.
- **Parada durante los jueces:** si una cuota, el presupuesto o un coste desconocido para el experimento ocurre durante los jueces de una pregunta (también en su último juez de pago), esa pregunta queda sin veredicto (`jury` nulo, `motivo: "parada"`), aunque ya haya veredictos parciales. Esos registros de jueces sí se guardan en `veredictos.jsonl`.
- **Jueces frente al script:** kappa y tasas solo con preguntas con clave o abstención que citan (los «no consta» no entran). Con una sola clase en el script valen «no calculable». El PPI solo se calcula si hay preguntas sin clave; en la batería de desarrollo no hay ninguna.
- **Coste:** «gasto notificado» es el total del libro (ver arriba). Por respuesta, los buscadores dividen entre las preguntas ejecutadas (con alguna ejecución); el redactor, los jueces, el coste de la estrategia (buscadores más redactor) y los tokens totales, entre las respuestas medidas. Así, una parada no diluye ni infla las cifras con preguntas que nunca se midieron.
- **Sin medir:** las preguntas sin respuesta final (parada o error) no son fallos. Quedan fuera de todos los denominadores de acierto, de tipo, de acuerdo y de jueces, y se cuentan aparte como «no medidas (parada o error)» (`notMeasured`).
- **Latencia estimada (jueces en paralelo):** por respuesta, el buscador más lento, más el redactor, más el juez más lento, como si los jueces corrieran en paralelo como en el producto. Las ejecuciones de caché usan su duración guardada, igual que su coste. Percentiles 50 y 95 con `statistics.quantiles`.
- **Barrido:** un tramo de `single` por cada tope de turnos (un buscador `main`, réplica 0). La variación es la mitad del intervalo de acierto de `single` con la configuración por defecto. Una mezcla con un solo buscador se resuelve con su única respuesta, sin IA.

## Límites conocidos

- El redactor se ejecuta pregunta a pregunta, sin concurrencia. Con la batería completa, el paso de respuesta final puede ser lento.
- Los jueces se paralelizan por pregunta, con `concurrency`.
- Un fallo inesperado en una pregunta (redactor o juez) queda registrado en su fila (`error`); el coste de esa llamada queda desconocido y para el experimento por presupuesto. Una cuota o límite detectado en el redactor o en un juez de Claude sí para: el registro del juez lleva el texto del error (`error`) y se aplica el mismo patrón que a los buscadores (`PARADA.txt` y código 2). Los jueces de Ollama no tienen cuota.
- `banco/resultados/` se versiona como el resto de experimentos (decisión del controlador); solo la reserva queda fuera.
- La variable `ODIN_BANCO_CLAUDE_JUEZ` conserva su nombre en español por ser un ajuste de entorno.
