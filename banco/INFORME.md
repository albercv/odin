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
| `ORIGEN.txt` | configuración, rama, commit, modo, batería, muestra, fecha |
| `ids.json` | documentos y fichas (ids de Odín a rutas), preguntas; sin claves de API |
| `buscadores.jsonl` | una línea por ejecución de buscador (con `cached`) |
| `final.jsonl` | respuesta final por pregunta, `correcta_script`, coste del redactor y `error` si lo hubo (`run`) |
| `veredictos.jsonl` | veredictos de cada juez, veredicto del jurado y `error` si lo hubo (`run`) |
| `turnos.jsonl`, `curvas.json` | barrido: ejecuciones por tope de turnos y curvas (`sweep`) |
| `resumen.json`, `informe.md` | resumen en claves camelCase e informe en español |
| `libro.json` | gasto real del experimento (`spent`), registros de coste desconocido (`unknown`) y tope (`cap`) |
| `PARADA.txt` | solo si hubo parada: tope de gasto, coste desconocido, cuota o límite, aislamiento roto |

`buscadores`, `final`, `veredictos` y `libro.json` se escriben siempre, también si algo falla a mitad (`try/finally`); tras una parada, `buscadores.jsonl` incluye lo que quedó en la caché. La orden sale con código 2 si hubo parada.

## Presupuesto (un solo tope por experimento)

`budgetUsd` limita todo el experimento: buscadores, redactor (`composer`, `pageFusion`) y jueces de Claude. Un libro de gasto compartido (`banco/ledger.py`, con cerrojo) suma lo gastado. Antes de cada llamada de pago se comprueba bajo el cerrojo si queda presupuesto; si no, la llamada no se lanza, se escribe `PARADA.txt` con lo gastado y el tope, y las preguntas restantes quedan sin respuesta o sin veredicto. El exceso solo puede venir de llamadas ya en vuelo (como mucho `concurrency − 1`).

En `sweep` el tope es acumulativo: el grupo principal y cada pasada de turnos comparten el mismo libro.

Los jueces de Ollama son locales y de coste 0: no pasan por la comprobación de presupuesto ni cargan en el libro. Si se agota el presupuesto, siguen corriendo; los de pago de esa pregunta no, y la pregunta queda sin veredicto.

**Fallos tras una llamada de pago:** un buscador, el redactor o un juez de Claude que falla tras llamar a `claude` carga su coste como desconocido (`charge(None)`) y agota el presupuesto. Así no se pierde el gasto ni se sigue gastando sin contarlo.

**Gasto notificado:** `cost.notifiedUsd` es el total real cargado en el libro. `cost.notifiedUsdFromRecords` es la suma de los registros, como diagnóstico.

**Coste desconocido:** un registro de pago nuevo sin `coste_usd` numérico no cuenta como 0. Se suma a `unknownCostRecords` (en `resumen.json` y en el informe) y agota el presupuesto: no se lanza ninguna llamada de pago más, con la razón «coste desconocido». Un juez de Claude sin `total_cost_usd` en su resultado, o una llamada fallida sin coste, cae en este caso.

## Configuración del barrido

```toml
[sweep]
mixes = [{ main = 1 }, { main = 5 }, { main = 5, topic = 5 }]   # enfoque = número de ejecuciones
draws = 20                                                    # por defecto 20
singleMaxTurns = [10, 20, 40]                                 # por defecto 10, 20 y 40
```

## Criterios que fija el código (no están en la configuración)

- **Muestra:** estratos por `sample.strata`, en orden alfabético; cada estrato se baraja con `random.Random(seed)` y se reparte por turnos.
- **Semilla de los jueces:** `sample.seed + índice del juez` (0 sin muestra). Cada juez baraja las fuentes con su propia semilla.
- **Recuperación por enfoque:** de las preguntas con citas resueltas, las que tienen alguna página esperada vista o citada por algún buscador de ese enfoque. Las preguntas sin citas (abstenciones) no cuentan.
- **Acuerdo entre buscadores:** `k` = cuántos buscadores coinciden con la firma mayoritaria. El acierto de cada grupo es el de la respuesta final, no el del buscador.
- **Jueces frente al script:** kappa y tasas solo con preguntas con clave o abstención. Con una sola clase en el script valen «no calculable». El PPI solo se calcula si hay preguntas sin clave; en la batería de desarrollo no hay ninguna.
- **Coste:** «gasto notificado» es el total del libro (ver arriba). El coste de la estrategia por respuesta suma todos los buscadores (incluidos los de caché) más el redactor, dividido por el número de preguntas.
- **Sin medir:** las preguntas sin respuesta final (parada o error) no son fallos. Quedan fuera de todos los denominadores de acierto, de tipo, de acuerdo y de jueces, y se cuentan aparte como «no medidas por la parada» (`notMeasured`).
- **Latencia estimada (jueces en paralelo):** por respuesta, el buscador más lento, más el redactor, más el juez más lento, como si los jueces corrieran en paralelo como en el producto. Las ejecuciones de caché usan su duración guardada, igual que su coste. Percentiles 50 y 95 con `statistics.quantiles`.
- **Barrido:** un tramo de `single` por cada tope de turnos (un buscador `main`, réplica 0). La variación es la mitad del intervalo de acierto de `single` con la configuración por defecto. Una mezcla con un solo buscador se resuelve con su única respuesta, sin IA.

## Límites conocidos

- El redactor se ejecuta pregunta a pregunta, sin concurrencia. Con la batería completa, el paso de respuesta final puede ser lento.
- Los jueces se paralelizan por pregunta, con `concurrency`.
- Un fallo inesperado en una pregunta (redactor o juez) queda registrado en su fila (`error`); el coste de esa llamada queda desconocido y para el experimento por presupuesto. Una cuota o límite detectado en el redactor sí para. Los jueces no exponen el texto del error, así que una cuota en un juez aparece como veredicto inválido, no como parada.
- `banco/resultados/` se versiona como el resto de experimentos (decisión del controlador); solo la reserva queda fuera.
- La variable `ODIN_BANCO_CLAUDE_JUEZ` conserva su nombre en español por ser un ajuste de entorno.
