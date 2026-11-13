# Barrido de 40 preguntas (tarea 12): resultado y fallo de detección del límite

- Fecha: 2026-11-12, de 18:59 a 19:17. Autor: CTO 3. Con D-061 y D-067.
- Odín y banco: `feature/calidad-recuperacion`, 5528977. Configuraciones `barrido-40` y `barrido-40-jurado`.
- **Resumen:** el barrido **no ha medido lo que tenía que medir.**
  - A mitad de la ejecución, la suscripción alcanzó su límite de 5 horas.
  - El banco no lo reconoció, siguió lanzando llamadas que fallaban al instante y sin coste, y terminó con código 0.
  - Solo son válidas 157 ejecuciones de `main`.
  - Lo que sí sale: el gasto real, el acierto de `main` en lo medido y una primera señal sobre el acuerdo entre buscadores.
  - Los resultados afectados llevan un `NO-VALIDO.md`.

## 1. Gasto real (libro de gasto)

| Ejecución | Gasto notificado | Tope |
|---|---|---|
| `barrido-40` | 0,7774 USD | 5,5 USD |
| `barrido-40-jurado` | 0 USD | 0,6 USD |
| **Total del barrido** | **0,7774 USD** | |

- Las 643 llamadas fallidas notificaron un coste de 0. No se pagó nada por ellas.
- **Gasto acumulado del plan** (piloto y barrido): 1,363 USD.

## 2. Qué falló

- **El mensaje** fue «You've hit your 5-hour limit · resets 9:50pm (Europe/Madrid)».
- **El detector de cuota** `LIMITE` está en `experimentos/.../medir/ejecutar.py` y el banco lo reutiliza. Busca `usage limit`, `rate limit`, `quota`, `limit reached`, `overloaded` y `credit balance`. **No reconoce ese texto.**
- **Efecto:**
  - 643 de 800 buscadores y las 120 ejecuciones de la frontera fallaron en 1,2 s cada una;
  - el banco no escribió `PARADA.txt`;
  - las curvas contaron los errores como fallos;
  - el jurado volvió a intentar los buscadores con el límite activo y no compuso ninguna respuesta.
- **También afecta a `medir/` del CTO 2:** una medición suya que choque con este límite tampoco se pararía.

## 3. Lo que sí se puede leer (157 ejecuciones de `main`, 21 preguntas de los 10 tipos)

- **Acierto de `main` por ejecución:** 151 de 157 (96,2 %). La página buscada salió en todas las preguntas medidas. Es un subconjunto pequeño: no es comparable con el 93,1 % de las 216 preguntas.
- **Acuerdo entre buscadores como señal.** Se probaron, en local y sin cuota, tres formas de decidir que dos respuestas «coinciden», sobre las 15 preguntas con 3 o más ejecuciones válidas y sobre el piloto:

| Firma | Barrido: aciertan en la mayoría | Barrido: aciertan fuera de ella | Piloto: en la mayoría | Piloto: fuera |
|---|---|---|---|---|
| Cifras (la del piloto) | 68/68 | 78/83 | 49/49 | 49/51 |
| Cifras y páginas citadas (la de hoy en el banco) | 65/65 | 81/86 | — | — |
| «No consta» y documentos citados | **132/132** | **14/19** | **91/91** | **7/9** |

- **Lectura:**
  - con «“no consta” y documentos citados», toda ejecución que coincide con la mayoría acierta;
  - las que se apartan tienen 5 de los 6 fallos del barrido. El 26 % de ellas falla, frente al 3,8 % de media;
  - las firmas con cifras o páginas son demasiado finas: separan respuestas correctas que solo difieren en la redacción o en la página.
  - **Es una señal prometedora, no una confirmación:** se apoya en 6 fallos y 2 preguntas del piloto.
- **Votar entre 10 réplicas de `main`:** en las 15 preguntas con 3 o más ejecuciones, la respuesta mayoritaria acierta en todas, frente al 96,2 % por ejecución suelta. No se ha medido si es por encima de la variación, ni con el mismo gasto.

## 4. Lo que no se ha medido

- **El número mínimo de buscadores que mejora frente a uno solo con el mismo gasto:** no hay curvas válidas. Lo que dice D-060 sí acota la búsqueda:
  - con unos 0,005 USD por buscador, el límite provisional de 0,02 USD de más por respuesta permite como mucho unos 4 buscadores más, o 3 más el redactor;
  - el barrido repetido debe centrarse en 2 a 5 buscadores.
- **La calibración del jurado:** no hubo ninguna respuesta que juzgar.

## 5. Propuesta

1. **Antes de repetir** (subagentes Haiku, sin cuota de medición):
   - **Detectar el límite:**
     - ampliar `LIMITE` para que reconozca «hit your … limit» y «resets»; es un cambio en el fichero del CTO 2, que hay que acordar con él;
     - una red de seguridad que no dependa del texto: parar tras 3 errores seguidos de `claude`;
     - una llamada de prueba antes de lanzar el grupo.
   - **Curvas:** no contar los errores como fallos; si una mezcla tiene errores, se marca como «no válida».
   - **Firma de acuerdo:** pasar a «“no consta” y documentos citados», según lo medido arriba.
   - **Si la tasa de error supera el 5 %, la ejecución acaba con código 2 y `PARADA.txt`.**
2. **Repetir el barrido en una ventana con cuota libre:**
   - **Coste:** unos 5 USD. Los arreglos cambian el commit e invalidan la caché, así que las 157 ejecuciones válidas se vuelven a pagar. Con lo ya gastado, el barrido sumaría unos 5,8 USD, un 7 % sobre los 5,4 aprobados y dentro del margen del 25 % de D-061.
   - **Mezclas:** las de 1 a 5 buscadores, más la de 20 como referencia.
   - **Necesita** una hora seguida de llamadas Haiku. El límite de 5 horas es de toda la suscripción del propietario, así que hay que lanzarlo con las otras sesiones paradas o con poco uso.
3. **Después:** la fase 2 del plan (archivo y preguntas ampliados), como estaba.
