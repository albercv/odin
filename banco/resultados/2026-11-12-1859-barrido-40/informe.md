# Informe del banco: barrido-40

## Origen

- Configuración: barrido-40
- Rama: feature/calidad-recuperacion
- Commit: 5528977bf311b9adb415e42cb66b00119daea3f4
- Modo: explore
- Batería: desarrollo
- Archivos: ficticio
- Muestra: 40 (estrato tipo, semilla 7)
- Preguntas: 40
- Fecha: 2026-11-12T18:59
- Batería de desarrollo: cifras exploratorias, no una medición final.

## Acierto

- Sin respuesta final medida: no se compuso ninguna respuesta.

## Fallos graves

- Sin respuestas finales que revisar.

## Por enfoque

| Enfoque | Recuperación | Acierto de sus ejecuciones | Errores |
|---|---|---|---|
| main | 100,0 % | 96,2 % | 243 |
| synonyms | no calculable | no calculable | 200 |
| topic | no calculable | no calculable | 200 |

## Acuerdo entre buscadores

Sin respuestas finales que cruzar.

## Jueces

Sin respuestas que juzgar.

## Coste y tiempo

- Gasto notificado: 0,7774 USD (ejecuciones nuevas, redactor y jueces)
- Coste de la estrategia por respuesta: no calculable
- Tokens por respuesta: no calculable (buscadores 297997, redactor no calculable, jueces no calculable)
- Gasto según registros (diagnóstico): 0,7774 USD
- Registros de pago con coste desconocido: 0

## Curvas

Variación de single por defecto (media de la mitad del intervalo): 2,5 %.

| Mezcla | Ejecuciones | Acierto medio [mín, máx] | Tokens medios | Coste medio | Latencia p50 / p95 | Supera single |
|---|---|---|---|---|---|---|
| main ×3 | 3 | 26,5 % [20,0 %, 30,0 %] | 89121 | 0,0058 USD | 1,4 / 20,2 s | sí |
| main ×5 | 5 | 33,2 % [27,5 %, 40,0 %] | 148478 | 0,0097 USD | 1,6 / 20,9 s | sí |
| main ×10 | 10 | 40,0 % [40,0 %, 40,0 %] | 297997 | 0,0194 USD | 9,5 / 23,0 s | sí |
| main ×1, topic ×1, synonyms ×1 | 3 | 37,8 % [32,5 %, 42,5 %] | 28974 | 0,0019 USD | 1,6 / 18,2 s | sí |
| main ×3, topic ×1, synonyms ×1 | 5 | 27,1 % [20,0 %, 37,5 %] | 89564 | 0,0059 USD | 1,8 / 20,4 s | sí |
| main ×5, topic ×3, synonyms ×2 | 10 | 35,4 % [30,0 %, 45,0 %] | 149311 | 0,0097 USD | 2,1 / 21,9 s | sí |
| main ×10, topic ×5, synonyms ×5 | 20 | 40,0 % [40,0 %, 40,0 %] | 297997 | 0,0194 USD | 9,5 / 23,0 s | sí |
| main ×1 | 1 | 37,6 % [35,0 %, 40,0 %] | 29483 | 0,0019 USD | 1,3 / 18,2 s | no aplica |
| topic ×1 | 1 | 0,0 % [0,0 %, 0,0 %] | 0 | 0,0000 USD | 1,3 / 1,9 s | no aplica |
| synonyms ×1 | 1 | 0,0 % [0,0 %, 0,0 %] | 0 | 0,0000 USD | 1,2 / 1,5 s | no aplica |

| Turnos máximos | Acierto | Tokens medios | Latencia p50 / p95 |
|---|---|---|---|
| 10 | 0,0 % | 0 | 1,2 / 1,7 s |
| 20 | 0,0 % | 0 | 1,3 / 1,4 s |
| 40 | 0,0 % | 0 | 1,2 / 1,4 s |
