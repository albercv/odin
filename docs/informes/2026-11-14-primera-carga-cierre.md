# Primera carga: cierre de la construcción (D-107)

Fecha: 2026-11-14. Rama: `feature/primera-carga`, construida sobre `integracion/instalacion` (8fc905e). Spec: `docs/superpowers/specs/2026-11-13-primera-carga-design.md`. Plan: `docs/superpowers/plans/2026-11-13-primera-carga.md`. Medición: `docs/informes/2026-11-14-primera-carga-medicion.md`.

No se ha desplegado en la instalación del propietario. Para eso hace falta su permiso.

## Estado

- Se construyen todas las tareas del plan menos la 10b, que no se hace: V2 no entra (D-107).
- Revisión final de toda la rama con Opus: «con arreglos», con 1 crítico, 7 importantes y 11 menores. Se corrigen en una sola ronda, y la re-revisión sale limpia: todos los puntos de la ronda quedan resueltos, sin críticos ni importantes nuevos.
- `make check` y `make demo` terminan con 0 sobre la rama fusionada (afcf30e):

  | Parte | Pruebas | Fallos |
  |---|---|---|
  | Java | 1.490 | 0 |
  | MCP | 103 | 0 |
  | Panel | 411 | 0 |
  | Python | 101 | 0 |

  En la demo pasan todos los pasos.
- **Ensayo V25 → V26 con la V26 final.** Se hizo sobre la copia del propietario de las 21:22 (1.624 documentos y 313 fichas), en la pila de ensayo `odin-ensayo` y no en la instalación del propietario. La V26 aplicada es la final: su md5 coincide con el del fichero de la rama.
  - Primero se restauraron las imágenes de `integracion/instalacion` (8fc905e) y después se pusieron las de la primera carga.
  - `instalacion-actualizar` termina con 0 en 15 s, y Flyway aplica la 26 con éxito.
  - La huella de los datos queda idéntica: 0 documentos pendientes, 0 lotes y la restricción `project_id_company_unique` una sola vez.
  - El MCP ofrece 15 herramientas a Marta y 14 a Ignacio. Claude Desktop no se ha tocado.
  - La pila se ha bajado.

## Lo que corrigió la revisión final

**C1. Clasificación con una lista parcial.** La clasificación arrancaba con el primer proyecto confirmado. Ahora espera a una acción explícita, «Lista terminada: clasificar», que exige:
- la lectura terminada;
- los candidatos ya buscados en los textos;
- ningún candidato por decidir.

Se acepta con 0 proyectos confirmados. Una vez cerrada la lista, los candidatos ya no cambian.

**I1. Sin lector OCR, la carga ya no se atasca.** Los escaneos que esperan al lector salen en «Sin ubicar» con su estado.

**I2. Lotes sin salida.** Un lote sin nada que decidir se cierra solo. La orden no cierra una subida vacía.

**I3. Ficheros que no se admiten.** La orden salta los formatos no admitidos, los ficheros vacíos y los nombres raros, dice cuáles ha saltado y sigue.

**I4. Grupos de más de 500.** El panel los confirma en partes de 500.

**I6. Original de un pendiente.** El administrador abre desde la bandeja el original de un documento pendiente («Ver el original»).

**I7. Guarda D-099.** La orden pasa por la guarda. Con `--si` hacia un puerto del propietario exige `CONFIRMAR_INSTALACION=PROPIETARIO`.

**Menores corregidos:**
- nunca aparece «Proyecto sin nombre» en el diálogo de confirmación;
- quién y cuándo en cada colocación y en «no lo sé», que pasa a ser un estado propio (`DONT_KNOW`);
- CHECK de quién y cuándo en las propuestas decididas;
- no se decide un documento que se está leyendo;
- cuatro hilos para las tareas programadas;
- guía y README al día;
- sin rutas personales en los resultados.

## Pendiente: decide el CEO o el propietario

1. **«Copia escaneada» (spec 3, D3).** No se ha hecho. Después de confirmar que un escaneo es copia de otro documento, el escaneo deja de salir en las listas y las búsquedas, como pide la spec. Lo que falta es que se vea como «copia escaneada» en la ficha del documento principal y en el expediente, y una forma de deshacer el enlace. Hasta entonces, el trabajador solo llega al escaneo por su id. Ninguna tarea del plan lo tenía, y toca la API, el MCP y el panel. Propuesta: hacerlo antes de un cliente real.
2. **«No lo sé» bloquea la siguiente carga.** Un «no lo sé» deja la carga abierta sin plazo (D-080), y mientras esté abierta no se puede empezar otra. ¿Se acepta, o una carga nueva puede convivir con esos documentos?
3. **`--si` en el servidor de un cliente.** Con los puertos por defecto también pide `CONFIRMAR_INSTALACION=PROPIETARIO`, y el nombre puede confundir al administrador del cliente. El camino normal, que pregunta «¿Seguir? (s/n)», no lo pide.
4. **Antes de un cliente real:**
   - medir con unos 3.000 documentos y 300 proyectos (memoria y tiempo de la clasificación);
   - medir el enlace con expedientes (spec 9);
   - enseñar el tipo de documento en la bandeja;
   - decidir si la cola del lector debe dejar atrás la primera carga frente al trabajo diario.

## Decisiones tomadas durante la construcción

En el orden en que se tomaron. Las que una decisión posterior sustituye llevan la marca «sustituida».

1. **Base.** `integracion/instalacion`, que ya tiene V24, `AddToCaseFiles` y `WithdrawDocument` (lo manda el CEO). Si fuera incorrecto: rebase sobre `develop` cuando llegue el encargo 13.
2. **Tarea 10b.** No se hace (D-107).
3. **Filtro de escaneos.** El filtro del trabajador `scan_of IS NULL` va en la T1, con `intake_pending`; la T8 rellena la columna.
4. **Número de migración.** V25 en esta rama, coordinado con CTO 2 y CTO 3. *Sustituida por la 19.*
5. **Cierre del lote.** Lo hace la T8, al confirmar o retirar el último pendiente.
6. **Batería con el modelo.** Con el Ollama local, fuera de `make check`.
7. **Sin IA externa en la medición.** Sin `claude -p` (D-094).
8. **Batería de pruebas.** `intake-battery` se crea con un guion determinista versionado y un subconjunto copiado a los recursos de prueba.
9. **Lista del administrador.** No enseña lo pendiente; solo la bandeja ve lo pendiente. Si el propietario lo quiere ver allí, se añade un aviso con enlace a la bandeja.
10. **Quién se queda la V25.** El primero que integre se queda la V25 y el segundo pasa a V26 (Flyway sin outOfOrder).
11. **Quién ve lo pendiente en el servidor.** Las cadenas del sistema (lectores, clasificación) y el descarte desde la bandeja sí ven lo pendiente. La lista de retirados del administrador no lo enseña.
12. **Otra empresa.** Su administrador recibe 403, por la convención de `AccessPolicy`, aunque el plan decía 404.
13. **Trabajador en rutas de administración.** Recibe 401; el 403 se prueba en el caso de uso.
14. **Avisos del lote.** «Sin lector OCR» y «sin modelo local» salen de la configuración de la instalación. «Espacio libre» lo comprueba la orden.
15. **Descubrir candidatos.** Ruta propia (`POST project-candidates/discover`), para que el GET no escriba.
16. **Nombre descubierto.** Se queda con la primera grafía que aparece en los textos; el administrador lo confirma.
17. **«Media» del guion.** Se queda en LOW con todos los candidatos; D2 pide confirmación igual.
18. **Número de migración, tras el aviso del CEO.** La primera carga pasa a `V26__intake.sql`, porque el encargo 17 lleva V25 y está desplegado.
19. **Duplicados de toda la empresa.** Se comparan entre sí. *Sustituida por la 26 y después por la 37.*
20. **Propuestas de «toda la empresa».** Solo con señal citable; si nada sitúa el documento, no se propone proyecto (D-008).
21. **Motivo de las propuestas del modelo.** Es una cita literal del texto, nunca la respuesta del modelo (D-008).
22. **Cuándo clasificar.** Con al menos un candidato confirmado. *Sustituida por la 38.*
23. **Varios candidatos sin desempate.** Una propuesta LOW por candidato; confirmar una rechaza las demás. El modelo solo cuando las reglas no encuentran nada.
24. **Confianza de escaneos y versiones.** SAME_AS_SCAN y SUPERSEDES en HIGH. Se espera con la cola del lector ocupada.
25. **Guardado atómico.** Las propuestas de un documento se guardan en una transacción, con un índice único contra duplicados.
26. **Documentos sin proyecto propuesto.** No entran en duplicados. *Sustituida por la 37.*
27. **Cuándo se cierra el lote.** Sin documentos pendientes y sin propuestas por decidir.
28. **Decisiones del implementador de la T8, aceptadas:**
    - corregir y «de toda la empresa» rechazan la propuesta con quién y cuándo;
    - descartar rechaza también las propuestas que apuntan al documento;
    - un enlace a una ficha sin marca o de otro proyecto da 409;
    - SAME_AS_SCAN exige el original en el mismo proyecto.
29. **Documentos sin ubicar.** El administrador los coloca uno a uno: es una decisión humana (D2).
30. **«No lo sé».** Sin estado nuevo. *Sustituida en la ronda final: ahora es `DONT_KNOW`, con quién y cuándo.*
31. **Lotes en curso.** Hay una ruta que lista los lotes no cerrados. El panel nunca crea lotes.
32. **Título del destino.** `targetTitle` en parejas y enlaces.
33. **Barra lateral.** La entrada de la primera carga sale sin condición: en el panel solo entran administradores.
34. **Canal PANEL.** `POST intake-batches` con canal PANEL se queda en la API.
35. **La orden.** Es de producto: confirmación interactiva y sin `CONFIRMAR_INSTALACION`. *Matizada en la ronda final (I7): `--si` hacia un puerto del propietario lo exige.*
36. **Lote abierto antes de «¿Seguir?».** Un lote OPEN vacío se reutiliza.
37. **Comparación de duplicados.** Los documentos sin ninguna propuesta de proyecto se comparan entre sí en el grupo «». Los ambiguos siguen fuera, y nunca se compara entre proyectos propuestos distintos. Así la cobertura de escaneos llega a 1,00 (spec 9).
38. **Clasificar tras «lista terminada».** Acción explícita, válida con 0 confirmados. Coste si fuera incorrecto: un clic más.
39. **Copia escaneada (I5).** Fuera de la ronda final; se lleva al CEO.
40. **Otros fuera de la ronda.** El tipo en la bandeja, la medición a 3.000 documentos, el orden de la cola del lector y los menores que pueden esperar.
41. **V26 modificada en el sitio.** No está aplicada en ninguna base persistente. Coste si fuera incorrecto: recrear una base de desarrollo.

## Menores que quedan

No bloquean el uso con los datos ficticios:
- el panel no marca las propuestas «no lo sé» ni refresca solo la vista del lote mientras se lee;
- cerrar la lista y la tarea programada pueden pisarse durante un instante;
- la spec no describe aún `DONT_KNOW` ni las dos rutas nuevas;
- `medir.py` toma por defecto «1 de 1» ejecuciones;
- se deja de pruebas: un cerrojo huérfano si se mata la orden con SIGKILL, y pruebas de aislamiento más estrictas.
