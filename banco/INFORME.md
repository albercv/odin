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
| `final.jsonl` | respuesta final por pregunta, `correcta_script` y coste del redactor (`run`) |
| `veredictos.jsonl` | veredictos de cada juez y veredicto del jurado (`run`) |
| `turnos.jsonl`, `curvas.json` | barrido: ejecuciones por tope de turnos y curvas (`sweep`) |
| `resumen.json`, `informe.md` | resumen en claves camelCase e informe en español |
| `PARADA.txt` | solo si hubo parada: tope de gasto, cuota, aislamiento roto |

Si hay parada, la orden escribe lo que haya, marca la parada en el informe y sale con código 2.

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
- **Coste:** «notificado» suma solo las ejecuciones nuevas, el redactor y los jueces. El coste de la estrategia por respuesta suma todos los buscadores (incluidos los de caché) más el redactor, dividido por el número de preguntas.
- **Latencia por respuesta:** el buscador más lento, más el redactor, más el juez más lento. Percentiles 50 y 95 con `statistics.quantiles`.
- **Barrido:** un tramo de `single` por cada tope de turnos (un buscador `main`, réplica 0). La variación es la mitad del intervalo de acierto de `single` con la configuración por defecto. Una mezcla con un solo buscador se resuelve con su única respuesta, sin IA.

## Límites conocidos

- El redactor se ejecuta pregunta a pregunta, sin concurrencia. Con la batería completa, el paso de respuesta final puede ser lento.
- Los jueces sí se paralelizan por pregunta, con `concurrency`. El tope de gasto no los cubre: solo frena a los buscadores.
- Una parada durante el redactor o los jueces deja la respuesta final parcial. El informe lo dice.
- `banco/resultados/` no está en `.gitignore`: contiene respuestas del archivo ficticio, no datos privados, pero conviene revisarlo antes de versionar una carpeta.
