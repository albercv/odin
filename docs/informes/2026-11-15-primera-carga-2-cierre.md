# Primera carga, segunda ronda: cierre (encargo 19, D-113)

Fecha: 2026-11-15. Rama `feature/primera-carga-2`, que sale de lo instalado (7791802) y ya tiene `develop` (7687358) mezclada. No está desplegada ni fusionada en `develop`: las dos cosas necesitan el permiso del propietario.

## Lo que entra

1. **Copia escaneada (D3).**
   - La ficha del documento principal enseña sus «Copias escaneadas». La ficha del escaneo dice «Copia escaneada de …» y tiene el botón «No es una copia escaneada», que deshace el enlace. Al deshacerlo, el escaneo vuelve a salir en las listas y las búsquedas.
   - El listado del trabajador, que el MCP usa en `listDocuments`, lleva `scanCopies`, así que el trabajador puede abrir o leer el escaneo por su id.
   - En el expediente, un escaneo cuyo documento principal también está en él sale dentro de ese documento, y su texto no se repite. Si el principal no está en el expediente, el escaneo sale suelto con «Copia escaneada de …».
   - La ruta para deshacer el enlace es `DELETE /v1/admin/documents/{id}/scan-of`.
2. **Cerrar la carga.**
   - El panel tiene el botón «Cerrar la carga» (`POST …/intake-batches/{id}/close`). Solo funciona con la carga en revisión y sin propuestas por decidir.
   - Lo que estaba en «No lo sé» pasa a «Sin ubicar». Queda registrado quién lo decidió y cuándo, y la carga queda cerrada con quién la cerró (`closed_by`).
   - Una carga cerrada conserva su «Sin ubicar»: sus documentos se pueden colocar o descartar, y siguen ocultos al trabajador hasta que se colocan.
   - Una carga en revisión ya no bloquea la siguiente. La orden solo se niega si hay otra carga leyéndose, y avisa de que la anterior está pendiente de revisar. Si hay varias cargas, el panel deja elegir cuál revisar.
3. **Confirmación neutra.** Con `--si` hacia los puertos de siempre, la orden ahora pide `CONFIRMAR_INSTALACION=ESCRIBIR`; `PROPIETARIO` deja de valer. El mensaje habla de «la instalación en uso».
4. **Tipo de documento en la bandeja.**
   - La clasificación guarda el tipo en `document.intake_type`. Lo calcula con las reglas del estudio (`CaseFileRules`).
   - Cada fila de propuesta y de «Sin ubicar» lo enseña: «Acta», «Póliza», «Ficha de caso»… Si no se reconoce, sale «Tipo sin identificar».
   - No se enseña al trabajador ni en el MCP, porque no es un campo de Odín (spec, apartado 1).

## Migración

`V27__intake_round_two.sql` añade dos columnas que admiten nulos: `document.intake_type`, y `intake_batch.closed_by` con su CHECK. No toca datos.

## Verificación

- `make check` termina con 0 sobre c396973:

  | Parte | Pruebas | Fallos |
  |---|---|---|
  | Java | 1.521 | 0 |
  | MCP | 105 | 0 |
  | Panel | 421 | 0 |
  | Python | 103 | 0 |

  Incluye las pruebas de aislamiento de cada ruta y campo nuevos.
- **Ensayo V26 → V27** en la pila `odin-ensayo`, nunca en la instalación del propietario:
  - se restauró la copia de las 12:30 con las imágenes de `develop`, y su huella coincide con la del propietario;
  - `instalacion-actualizar` con la feature termina con 0 en 20 s, y Flyway aplica la 27;
  - la huella de los datos queda idéntica: 0 lotes, 0 documentos con tipo y las dos columnas nuevas;
  - el MCP da 15 herramientas a Marta y 14 a Ignacio, y Claude Desktop no se ha tocado;
  - la pila se ha bajado con sus volúmenes.

## Decisiones tomadas

1. **`ESCRIBIR` como valor neutro.** Es el verbo de la acción, igual que `CONFIRMAR=BORRAR` o `ACTUALIZAR`. El mensaje de `comun.sh` sigue diciendo «del propietario», porque solo sale en pilas de ensayo; `experimentos/` no se ha tocado.
2. **El tipo, solo en la bandeja.** Las cargas clasificadas antes de la V27 lo enseñan como «Tipo sin identificar».
3. **La clasificación guarda en una sola transacción** los tipos y las propuestas de toda una pasada. Antes era una transacción por documento.
4. **Cerrar con propuestas por decidir da 409** («Quedan N propuestas por decidir…»). Así, el cierre solo convierte los «No lo sé».
5. **El lote lleva un campo nuevo, `toDecide`.** `proposed` cuenta también los «No lo sé», así que no servía para ese 409.
6. **Deshacer una copia escaneada:**
   - lo puede hacer el administrador de la empresa del documento;
   - con un documento de otra empresa la respuesta es 404, como el resto de rutas por id de documento, para no revelar que existe;
   - no guarda quién ni cuándo, igual que deshacer una sustitución.
7. **En el listado del trabajador solo salen las copias escaneadas ya leídas**, igual que el resto de su listado.

## Pendiente o fuera de alcance

- No se detecta que un escaneo y su documento digital son el mismo si llegaron en cargas distintas: los duplicados se buscan dentro de cada carga.
- En una carga cerrada que aún tiene documentos sin ubicar, el aviso del resumen sigue mencionando «No lo sé».
- La spec aún no describe `DONT_KNOW`, el cierre manual ni la copia escaneada tal como se han construido.
- No se ha probado el panel en un navegador: solo con sus pruebas.
- No se ha vuelto a medir la clasificación con unos 3.000 documentos.
