# Piloto del banco de pruebas multiagente (tarea 11)

- Fecha: 2026-11-12, de 17:59 a 18:33. Autor: CTO 3. Con D-060 y D-061.
- Odín medido: `feature/calidad-recuperacion`, 52e4aaa. Resultados en `banco/resultados/2026-11-12-1759-piloto/`.
- Máquina: el M3 Max de 36 GB. Pila `odin-banco` en 48080, con el archivo ficticio completo: 1.236 documentos, 300 fichas y las 55 marcas de sustituido.
- **Límite:** son 5 preguntas de la batería de desarrollo, en una sola ejecución. Sirven para medir el coste y ver cómo funciona el banco, no para dar una cifra de calidad.

## Configuración

`banco/configs/piloto.toml`:
- 5 preguntas, una por tipo, con semilla 7;
- 20 buscadores Haiku: 10 `main`, 5 `topic` y 5 `synonyms`;
- redactor `composer` con Haiku;
- jurado de 3 Haiku y 2 `qwen3.5:9b` (Ollama local), que decide por mayoría;
- tope: 1,5 USD.

## Gasto y tiempo (lo que pide D-061)

| Etapa | Coste notificado por respuesta | Tokens por respuesta | Tiempo por llamada |
|---|---|---|---|
| Buscadores (20 por pregunta, 100 en total) | 0,1072 USD (unos 0,0054 por buscador) | 1,57 millones | mediana de 14,2 s; máximo de 34,8 s |
| Redactor | 0,0060 USD | 80.143 | de 10 a 16 s |
| Jueces Haiku (3) | 0,0039 USD en total | 31.942 | de 6 a 14 s |
| Jueces `qwen3.5:9b` (2) | 0 | — | de 160 a 600 s; uno agotó el tiempo (600 s) |
| **Total** | **0,1132 USD por respuesta** | | |

- **Gasto real del piloto:** 0,5852 USD, según el libro de gasto; no hubo registros con coste desconocido. La estimación era de unos 0,6 USD solo para los buscadores, así que el piloto queda dentro.
- **Tiempo total:** 34 minutos, frente a los unos 7 estimados. La diferencia es de los jueces locales: Ollama atiende las peticiones de una en una, y `qwen3.5:9b` tarda minutos con el texto de las páginas.

## Lo que mide

- **Acierto del script:** 4 de 5. Es una cifra exploratoria.
- **Fallos graves:** 0 fugas, 0 citas inventadas, 0 sustituidos dados por vigentes y 1 «no consta» falso.
- **Por enfoque:**
  - `main` y `synonyms` vieron la página buscada en todas las preguntas y acertaron en todas sus ejecuciones;
  - `topic` acertó en el 92 %.
- **El acuerdo entre buscadores sí parece predecir el acierto:**
  - con 13 de 20 de acuerdo con la mayoría, 2 de 2 aciertos;
  - con 6, 1 de 2.
  - Hay que confirmarlo con el barrido.
- **Jueces frente al script:** kappa 0,545, 0 % de aceptaciones falsas y 25 % de rechazos falsos (uno de cuatro).

## Hallazgos que cambian el barrido

1. **El redactor empeora una respuesta (Q162).**
   - Los 10 buscadores `main` acertaron: dieron las dos fuentes que no cuadran, cada una con su cita, como pide D-054.
   - El redactor leyó «las fuentes no coinciden» como «los buscadores no coinciden», aplicó su regla de «no consta» y falló.
   - Con un solo buscador `main`, las 5 habrían salido bien.
   - **Hay que corregir la regla del redactor:** distinguir que los buscadores discrepen de que las fuentes no cuadren.
2. **Los jueces dan por malos los «no consta» correctos (Q192).**
   - Es la pregunta de aislamiento: lo correcto era «no consta».
   - Los cinco jueces la rechazaron porque no hay páginas citadas que lo respalden.
   - Un juez que no puede buscar no puede valorar una abstención.
   - **Hay que cambiar el diseño:** los «no consta» no se juzgan (cuentan como «sin veredicto»), y el jurado se calibra solo con las respuestas que citan. Para los «no consta» manda el script.
3. **El juez local es demasiado lento para el barrido.**
   - Con 40 preguntas y 2 jueces `qwen3.5:9b` serían horas.
   - Probar el modo sin razonamiento de Ollama (`think: false`) o `qwen3.5:4b-q4_K_M`, que ya está descargado, y medirlo antes del barrido.
4. **Siguen pendientes de la revisión final:**
   - guardar en caché las respuestas finales y los veredictos;
   - el caso del jurado parcial.

## Estimación revisada del barrido de 40 preguntas (tarea 12)

Con el coste medido de unos 0,0054 USD por buscador, unos 0,006 USD por redactor y unos 0,0013 USD por juez Haiku:

| Parte | Llamadas | Coste notificado | Tiempo (concurrencia 4) |
|---|---|---|---|
| Grupo de buscadores (40 × 20) | 800 | unos 4,3 USD | unos 47 min |
| Frontera de un solo buscador (40 × 3 topes de turnos) | 120 | unos 0,65 USD | unos 7 min |
| Redactor (40) | 40 | unos 0,24 USD | unos 8 min |
| Jueces Haiku (40 × 3) | 120 | unos 0,16 USD | unos 4 min |
| Jueces locales | 80 | 0 | depende del arreglo 3 |
| **Total** | | **unos 5,4 USD** | **algo más de 1 h, sin los jueces locales** |

- La estimación del plan era de 4,8 USD para los buscadores más lo que midiera el piloto. Con lo medido, 5,4 USD queda dentro del margen del 25 % de D-061.
- **El resto del plan no cambia:** cada pregunta por buscador cuesta un poco menos de lo supuesto (0,0054 frente a 0,006 USD), así que el total de unos 40 USD sigue siendo una cota razonable.
