# Resultados no válidos

Durante esta ejecución, la suscripción alcanzó su límite de 5 horas («You've hit your 5-hour limit»). El detector de cuota (`ejecutar.LIMITE`) no reconoce ese mensaje, así que el banco siguió lanzando llamadas que fallaban al instante, sin coste, y acabó con código 0.

- En el barrido solo son válidas 157 ejecuciones de `main`, de 21 de las 40 preguntas. `topic` y `synonyms` no tienen ninguna. Las curvas de `curvas.json` y del informe cuentan los errores como fallos: **no sirven**.
- El jurado no midió ninguna pregunta.

Análisis de lo que sí vale: `docs/informes/2026-11-12-barrido-banco.md`.
