# Informe del banco: barrido-40

## Origen

- Configuración: barrido-40
- Rama: feature/calidad-recuperacion
- Commit: a3e4b2d8a086075037aa219e111d715d530b462d
- Modo: explore
- Batería: desarrollo
- Archivos: ficticio
- Muestra: 40 (estrato tipo, semilla 7)
- Preguntas: 40
- Fecha: 2026-11-13T03:12
- Batería de desarrollo: cifras exploratorias, no una medición final.

## Acierto

- Sin respuesta final medida: no se compuso ninguna respuesta.

## Fallos graves

- Sin respuestas finales que revisar.

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

Sin respuestas finales que cruzar.

## Jueces

Sin respuestas que juzgar.

## Coste y tiempo

- Gasto notificado: 6,5633 USD (ejecuciones nuevas, redactor y jueces)
- Coste de la estrategia por respuesta: no calculable
- Tokens por respuesta: no calculable (buscadores 2259817, redactor no calculable, jueces no calculable)
- Gasto según registros (diagnóstico): 1,0167 USD
- Registros de pago con coste desconocido: 0

## Tandas

Se hizo en una sola tanda.
- Tanda 1: de 2026-11-13T03:12:15 a 2026-11-13T03:24:09; motivo: completa; llamadas de pago: 167

## Curvas

Variación de single por defecto (media de la mitad del intervalo): 6,2 %.

| Mezcla | Ejecuciones | Acierto medio [mín, máx] | Tokens medios | Coste medio | Latencia p50 / p95 | Supera single |
|---|---|---|---|---|---|---|
| main ×2 | 2 | 79,1 % [67,5 %, 85,0 %] | 167723 | 0,0102 USD | 13,6 / 24,0 s | no |
| main ×3 | 3 | 89,8 % [85,0 %, 95,0 %] | 252750 | 0,0154 USD | 14,8 / 26,2 s | no |
| main ×4 | 4 | 88,9 % [85,0 %, 92,5 %] | 337094 | 0,0205 USD | 15,3 / 26,2 s | no |
| main ×5 | 5 | 91,4 % [85,0 %, 95,0 %] | 421379 | 0,0256 USD | 15,6 / 27,1 s | no |
| main ×1, topic ×1, synonyms ×1 | 3 | 89,4 % [82,5 %, 95,0 %] | 316440 | 0,0205 USD | 18,0 / 32,1 s | no |
| main ×2, topic ×1, synonyms ×1 | 4 | 90,6 % [85,0 %, 95,0 %] | 399097 | 0,0258 USD | 18,0 / 31,8 s | no |
| main ×3, topic ×1, synonyms ×1 | 5 | 92,5 % [87,5 %, 97,5 %] | 486720 | 0,0309 USD | 18,1 / 30,6 s | no |
| main ×10, topic ×5, synonyms ×5 | 20 | 95,0 % [95,0 %, 95,0 %] | 2008101 | 0,1291 USD | 20,9 / 39,9 s | sí |
| main ×1 | 1 | 88,9 % [82,5 %, 95,0 %] | 84618 | 0,0051 USD | 12,7 / 23,1 s | no aplica |

| Turnos máximos | Acierto | Tokens medios | Latencia p50 / p95 | Válida |
|---|---|---|---|---|
| 10 | 92,5 % | 80555 | 12,4 / 18,8 s | sí |
| 20 | 87,2 % | 82761 | 12,6 / 21,7 s | sí |
| 40 | 87,5 % | 84500 | 11,9 / 24,8 s | sí |
