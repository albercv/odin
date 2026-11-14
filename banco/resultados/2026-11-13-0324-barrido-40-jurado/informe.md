# Informe del banco: barrido-40-jurado

## Origen

- Configuración: barrido-40-jurado
- Rama: feature/calidad-recuperacion
- Commit: a3e4b2d8a086075037aa219e111d715d530b462d
- Modo: explore
- Batería: desarrollo
- Archivos: ficticio
- Muestra: 40 (estrato tipo, semilla 7)
- Preguntas: 40
- Fecha: 2026-11-13T03:24
- Batería de desarrollo: cifras exploratorias, no una medición final.

## Acierto

- Acierto del script: 38 de 40 medidas (95,0 %); intervalo de Wilson al 95 %: 83,5 % a 98,6 %.
- No medidas (parada o error): 0 (no cuentan como fallo ni en ningún denominador).

## Fallos graves

- Fugas de aislamiento: 0
- Respuestas con citas inventadas: 0
- «No consta» falso: 0
- Sustituido presentado como vigente: 0

## Por enfoque

| Enfoque | Recuperación | Acierto de sus ejecuciones | Errores |
|---|---|---|---|
| main | 96,8 % | 89,5 % | 0 |
| synonyms | 96,8 % | 94,0 % | 0 |
| topic | 100,0 % | 91,5 % | 0 |

## Acuerdo entre buscadores

- Acierto de las ejecuciones que coinciden con la mayoría de su pregunta: 97,0 % (663 ejecuciones)
- Acierto de las que no coinciden: 62,8 % (137 ejecuciones)
- Fallos que quedan fuera de la mayoría: 71,8 %
- Respuesta mayoritaria por nivel de acuerdo: al menos el 60 %: 100,0 % (35 preguntas); menos del 60 %: 60,0 % (5 preguntas)

| Buscadores que coinciden con la mayoría | Preguntas | Acierto de la respuesta final |
|---|---|---|
| 20 | 17 | 100,0 % |
| 19 | 4 | 100,0 % |
| 18 | 1 | 0,0 % |
| 17 | 2 | 100,0 % |
| 16 | 4 | 100,0 % |
| 15 | 1 | 100,0 % |
| 14 | 3 | 66,7 % |
| 13 | 3 | 100,0 % |
| 11 | 1 | 100,0 % |
| 9 | 1 | 100,0 % |
| 8 | 1 | 100,0 % |
| 7 | 1 | 100,0 % |
| 4 | 1 | 100,0 % |

## Jueces

- Kappa de Cohen frente al script: -0,104
- Aceptaciones falsas: 100,0 %
- Rechazos falsos: 22,6 %
- Preguntas sin veredicto: 7
- «No consta» sin juzgar: 7
- PPI: no calculable: no hay preguntas sin clave

## Coste y tiempo

- Gasto notificado: 0,4325 USD (ejecuciones nuevas, redactor y jueces)
- Coste de la estrategia por respuesta: 0,1357 USD
- Tokens por respuesta: 2122676 (buscadores 2008101, redactor 90368, jueces 24206)
- Gasto según registros (diagnóstico): 0,4324 USD
- Registros de pago con coste desconocido: 0
- Latencia estimada (jueces en paralelo) por respuesta: p50 61,9 s; p95 144,9 s

## Tandas

Se hizo en una sola tanda.
- Tanda 1: de 2026-11-13T03:24:16 a 2026-11-13T03:48:18; motivo: completa; llamadas de pago: 140
