# Informe del banco: synonyms-instruccion

## Origen

- Configuración: synonyms-instruccion
- Rama: feature/calidad-recuperacion
- Commit: 5aaece73621b79d308a51f50be657ad90e643733
- Modo: explore
- Batería: desarrollo
- Archivos: ficticio
- Muestra: batería entera
- Preguntas: 216
- Fecha: 2026-11-13T03:53
- Batería de desarrollo: cifras exploratorias, no una medición final.

## Acierto

- Acierto del script: 206 de 216 medidas (95,4 %); intervalo de Wilson al 95 %: 91,7 % a 97,5 %.
- No medidas (parada o error): 0 (no cuentan como fallo ni en ningún denominador).

## Fallos graves

- Fugas de aislamiento: 0
- Respuestas con citas inventadas: 0
- «No consta» falso: 4
- Sustituido presentado como vigente: 0

## Por enfoque

| Enfoque | Recuperación | Acierto de sus ejecuciones | Errores |
|---|---|---|---|
| main | 98,7 % | 95,8 % | 0 |
| synonyms | 99,4 % | 96,3 % | 0 |

## Acuerdo entre buscadores

- Acierto de las ejecuciones que coinciden con la mayoría de su pregunta: 99,2 % (1073 ejecuciones)
- Acierto de las que no coinciden: 81,2 % (223 ejecuciones)
- Fallos que quedan fuera de la mayoría: 82,3 %
- Respuesta mayoritaria por nivel de acuerdo: al menos el 60 %: 99,5 % (187 preguntas); menos del 60 %: 58,6 % (29 preguntas)

| Buscadores que coinciden con la mayoría | Preguntas | Acierto de la respuesta final |
|---|---|---|
| 6 | 120 | 99,2 % |
| 5 | 48 | 97,9 % |
| 4 | 19 | 94,7 % |
| 3 | 19 | 84,2 % |
| 2 | 8 | 75,0 % |
| 1 | 2 | 0,0 % |

## Jueces

- Kappa de Cohen frente al script: 0,032
- Aceptaciones falsas: 33,3 %
- Rechazos falsos: 45,1 %
- Preguntas sin veredicto: 48
- «No consta» sin juzgar: 46
- PPI: no calculable: no hay preguntas sin clave

## Coste y tiempo

- Gasto notificado: 9,0163 USD (ejecuciones nuevas, redactor y jueces)
- Coste de la estrategia por respuesta: 0,0417 USD
- Tokens por respuesta: 646491 (buscadores 645413, redactor 0, jueces 1078)
- Gasto según registros (diagnóstico): 6,3986 USD
- Registros de pago con coste desconocido: 0
- Latencia estimada (jueces en paralelo) por respuesta: p50 50,5 s; p95 85,7 s

## Tandas

Se hizo en 2 tandas.
- Tanda 1: de 2026-11-13T03:53:47 a 2026-11-13T04:24:12; motivo: cuota o límite en Q153: You've hit your 5-hour limit · resets 8am (Europe/Madrid); llamadas de pago: 460
- Tanda 2: de 2026-11-13T08:02:39 a 2026-11-13T09:29:58; motivo: completa; llamadas de pago: 840
