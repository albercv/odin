# Informe del banco: piloto

## Origen

- Configuración: piloto
- Rama: feature/calidad-recuperacion
- Commit: 52e4aaad22993b7d520b3213ebe8004ca119a387
- Modo: explore
- Batería: desarrollo
- Archivos: ficticio
- Muestra: 5 (estrato tipo, semilla 7)
- Preguntas: 5
- Fecha: 2026-11-12T17:59
- Batería de desarrollo: cifras exploratorias, no una medición final.

## Acierto

- Acierto del script: 4 de 5 medidas (80,0 %); intervalo de Wilson al 95 %: 37,5 % a 96,4 %.
- No medidas (parada o error): 0 (no cuentan como fallo ni en ningún denominador).

## Fallos graves

- Fugas de aislamiento: 0
- Respuestas con citas inventadas: 0
- «No consta» falso: 1
- Sustituido presentado como vigente: 0

## Por enfoque

| Enfoque | Recuperación | Acierto de sus ejecuciones | Errores |
|---|---|---|---|
| main | 100,0 % | 100,0 % | 0 |
| synonyms | 100,0 % | 100,0 % | 0 |
| topic | 100,0 % | 92,0 % | 0 |

## Acuerdo entre buscadores

| Buscadores que coinciden con la mayoría | Preguntas | Acierto de la respuesta final |
|---|---|---|
| 13 | 2 | 100,0 % |
| 7 | 1 | 100,0 % |
| 6 | 2 | 50,0 % |

## Jueces

- Kappa de Cohen frente al script: 0,545
- Aceptaciones falsas: 0,0 %
- Rechazos falsos: 25,0 %
- Preguntas sin veredicto: 0
- PPI: no calculable: no hay preguntas sin clave

## Coste y tiempo

- Gasto notificado: 0,5852 USD (ejecuciones nuevas, redactor y jueces)
- Coste de la estrategia por respuesta: 0,1132 USD
- Tokens por respuesta: 1683047 (buscadores 1570962, redactor 80143, jueces 31942)
- Gasto según registros (diagnóstico): 0,5852 USD
- Registros de pago con coste desconocido: 0
- Latencia estimada (jueces en paralelo) por respuesta: p50 592,1 s; p95 640,3 s
