# Odín · Fase 2, bloque 7: documentos sustituidos

- Fecha: 2026-11-11. Autor: CTO (segunda sesión).
- Decisión de negocio que lo origina: D-049. Se aplican también D-005, D-008, D-014, D-024 y D-047.
- Amplía `2026-11-09-api-central-design.md` («spec base») y se apoya en `2026-11-10-identidad-pertenencias-design.md` («spec 2a»). Lo que este documento no cambia sigue como está allí.
- Rama: `feature/fase-2-conocimiento`. Es del grupo «Recuperación» y va después del bloque 6, que lo mide.

## 1. Por qué

- **D-049:** no se presenta como vigente un documento que ya está sustituido. Citar una póliza caducada es devolver un dato incorrecto, aunque la cita sea literal.
- **Hoy:** Odín no sabe que un documento sustituye a otro. Si la póliza de 2022 y la de 2024 hablan de lo mismo, la búsqueda devuelve las dos sin distinguirlas, y la IA tiene que adivinar cuál vale.
- **En el corpus de las baterías** está ese caso: la póliza de Los Almendros de 2022 y la de 2024, con la pregunta E1, «¿qué franquicia tiene ahora mismo?».

## 2. Decisiones

**De negocio** (D-049 y mensaje del CEO del 2026-11-11):
- **Lo decide una persona, nunca la IA:** el administrador marca en el panel que un documento sustituye a otro.
- **El documento viejo se queda guardado,** pero sale detrás y marcado como sustituido, y la instrucción del MCP pide a la IA que lo diga.
- **Si dos fuentes no cuadran,** la IA enseña las dos con su fecha y no elige ninguna.
- **Se respetan las dos barreras de aislamiento:** el filtro SQL y la seguridad por filas (spec 2a, 6).

**Del CTO.** Cada decisión dice lo que cuesta si resulta errónea:

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| La marca va en el documento viejo: `supersededBy`, que apunta al que lo sustituye directamente, y `supersededAt` | Un documento viejo tiene un solo sustituto directo. Una cadena (2022 → 2023 → 2024) sale sola, y un documento nuevo puede sustituir a varios | Una tabla de relaciones, si un documento llega a tener varios sustitutos |
| Lo que ve la IA (`replacement`) es el vigente del final de la cadena, no el sustituto directo. Se resuelve al leer, siguiendo las marcas | La IA necesita el documento que vale hoy. Si se marca después que el de 2023 lo sustituye el de 2024, el de 2022 pasa a apuntar al de 2024 sin tocarlo | Devolver el directo y que la IA siga la cadena |
| Solo sustituciones enteras. Un anexo que cambia una parte de un contrato no lo sustituye | El contrato sigue vigente en lo demás. Marcarlo como sustituido escondería lo que sigue valiendo. Los cambios parciales son contradicciones, que van después (D-049) | — |
| El sustituto tiene que verlo todo el que ve el viejo: o está en el mismo proyecto, o no tiene proyecto. Si no, 409 | Así nadie recibe «sustituido por» un documento que no puede leer, y la marca no revela nada de un proyecto cerrado (D-047) | Permitir cualquier proyecto y decir solo «sustituido» a quien no ve el sustituto |
| «Sale detrás» dentro de los resultados. Los resultados se eligen por relevancia, como hoy, y después los sustituidos pasan detrás de los vigentes, sin cambiar el orden entre ellos | Una pregunta sobre la póliza antigua («¿qué franquicia tenía en 2022?») sigue encontrándola. El conjunto devuelto es el mismo con marcas o sin ellas. Las baterías de búsqueda piden 5 resultados, así que su cuenta no cambia | Una penalización en la puntuación, medida con las baterías |
| El sustituto se resuelve con la visibilidad de contenido del que pregunta (`WorkerAccess.Granted.content()`, la misma de la búsqueda), en la misma consulta y bajo la seguridad por filas | Son las dos barreras de siempre. Si algún día no lo ve, sale `superseded: true` sin sustituto | — |
| El viejo y el sustituto tienen que ser de la misma empresa, también para el administrador del grupo | El administrador del grupo ve todas las empresas, pero el conocimiento de una empresa no se mezcla con el de otra (D-014) | — |
| La marca se guarda con su propia orden de actualización, no con el `save` del documento | El procesado guarda el documento entero con un `upsert` a partir de una copia leída antes. Si las columnas nuevas entraran ahí, una marca puesta mientras se procesa se perdería | — |
| El sustituto tiene que estar `READY` | La IA tiene que poder leerlo. Si aún se está procesando, el panel lo dice y se marca después | — |
| Sin cadenas circulares: si el sustituto, siguiendo sus propias marcas, llega al documento, 409 | Una cadena circular no tiene documento vigente | — |
| Las fichas de conocimiento no se marcan | El encargo habla de documentos. Una ficha se corrige o se rechaza en la revisión | Añadir la marca a las fichas |

## 3. Fuera de alcance

- Que la IA proponga o detecte sustituciones.
- Cambios parciales (anexos) y contradicciones entre documentos: «reconciliar contradicciones», después (D-049).
- Borrar documentos: hoy no existe en la API ni en el panel.
- Extraer la fecha de un documento de su texto. Sería adivinarla (D-008).

## 4. Modelo

| Concepto | Nombre en código | Qué cambia |
|---|---|---|
| Documento | `Document` | Gana `supersededBy` (`Optional<DocumentId>`) y `supersededAt` (`Optional<Instant>`) |
| Resultado de búsqueda | `SearchHit` | Gana `superseded` y `replacement` (`documentId` y `title`, o nulo) |

- **Migración** (la siguiente libre):
  - `document.superseded_by uuid NULL REFERENCES document(id)`;
  - `document.superseded_at timestamptz NULL`;
  - una comprobación de que `superseded_by` no es el propio `id`;
  - una comprobación de que las dos columnas son nulas a la vez o no lo son;
  - un índice por `superseded_by`.
- Las políticas de seguridad por filas de `document` (V9) ya cubren las columnas nuevas.
- **En las instalaciones que ya existen** no cambia nada: ningún documento nace sustituido.

## 5. Reglas

### 5.1 Marcar y desmarcar

- **Quién:** el administrador de esa empresa o el del grupo.
- **Marcar:** `PUT /v1/admin/documents/{documentId}/superseded-by` con `{ replacementId }`. Devuelve el `DocumentView` del viejo.
  - Si ya estaba marcado, la marca se cambia.
- **Desmarcar:** `DELETE /v1/admin/documents/{documentId}/superseded-by`. Devuelve 204, también si no estaba marcado.
- **Comprobaciones, en este orden:**
  1. 404 si el documento no existe o es de otra empresa, como en las otras rutas `/v1/admin/documents/{documentId}` (`GetDocument`, `RetryDocument`).
  2. 400 si `replacementId` es el propio documento.
  3. 404 si el sustituto no existe o es de otra empresa distinta de la del documento, con el mismo texto en los dos casos, para no revelar nada de otra empresa (D-014). Vale también para el administrador del grupo.
  4. 409 si el sustituto no está `READY`: «El documento que lo sustituye aún no está listo. Márcalo cuando termine de procesarse.»
  5. 409 si el sustituto está en otro proyecto: «El documento que lo sustituye tiene que estar en el mismo proyecto o sin proyecto, para que lo vea todo el que ve este.»
  6. 409 si se forma una cadena circular: «Ese documento ya está sustituido, directa o indirectamente, por este.»
- **Subir un duplicado** (misma SHA-256) no cambia la marca, igual que el título y el proyecto (spec base 6.1).

### 5.2 Buscar y leer

- **`SearchKnowledge`:**
  - elige los resultados por relevancia, como hoy;
  - después ordena los sustituidos detrás de los vigentes, sin cambiar el orden entre ellos;
  - cada resultado de documento lleva `superseded` y `replacement`, que es el vigente del final de la cadena.
- **`ReadPage`:** la página lleva `superseded` y `replacement` de su documento.
- **`ListDocuments`:** cada documento lleva `superseded` y `replacement`. No hay filtro nuevo.
- **El sustituto** se resuelve en la misma consulta, con el filtro explícito de empresa y de proyectos (`Sql.inScope` y `Sql.inVisibleProjects`) y bajo la seguridad por filas. Si no se ve, `replacement` es nulo y `superseded` sigue siendo verdadero.
- **Casos parecidos (bloque 2, que va después):** `SimilarCaseView` lleva los mismos dos campos y el mismo orden.

### 5.3 Panel

- **Lista de documentos:**
  - la marca «Sustituido», con «por <título del sustituto>»;
  - la acción «Marcar como sustituido…» en cada documento, que abre un diálogo:
    - un selector de documentos `READY` de la empresa, con búsqueda por título, que enseña el proyecto y la fecha de alta;
    - una ayuda: «Usa esta marca cuando un documento sustituye entero a otro, como una póliza renovada. Un anexo que cambia una parte no sustituye al contrato.»;
    - la confirmación: «“<viejo>” quedará guardado, pero Odín lo dará como sustituido por “<nuevo>” y lo pondrá detrás en las búsquedas.»;
  - la acción «Quitar marca de sustituido».
- **Detalle del documento:** la marca, el sustituto y la fecha de la marca.
- Los errores 409 se enseñan con su texto.
- Con los componentes y las reglas de la dirección visual (D-039).

## 6. Contrato

| Método y ruta | Quién | Qué |
|---|---|---|
| `PUT /v1/admin/documents/{documentId}/superseded-by` | Administradores | `{ replacementId }` → `DocumentView` |
| `DELETE /v1/admin/documents/{documentId}/superseded-by` | Administradores | → 204 |
| `POST /v1/search` | Trabajador | Cada resultado de documento gana `superseded` y `replacement` |
| `GET /v1/documents/{documentId}/pages/{pageNumber}` | Trabajador | Gana `superseded` y `replacement` |
| `GET /v1/documents` | Trabajador | `WorkerDocumentView` gana `superseded` y `replacement` |
| `GET /v1/admin/companies/{companyId}/documents` y `GET /v1/admin/documents/{documentId}` | Administradores | `DocumentView` gana `supersededBy` (`id` y `title`) y `supersededAt` |

## 7. MCP

- **`searchKnowledge`, `readPage` y `listDocuments`** pasan `superseded` y `replacement`.
- **Instrucciones nuevas:**
  - «Si un resultado viene marcado como sustituido, no lo presentes como vigente: di que está sustituido y por cuál, y busca o lee el que lo sustituye antes de responder. Si no puedes ver el que lo sustituye, di solo que está sustituido.»
  - «Si dos fuentes no cuadran y ninguna está marcada como sustituida, enseña las dos, cada una con su cita y su fecha, y no elijas ninguna. La fecha es la que conste en el texto del documento; si no consta, da la fecha en que entró en Odín y di que es esa.»
- **Casos resueltos de forma distinta (bloque 2):** no es una contradicción entre fuentes. Sigue su regla: se enseñan los dos casos y se sugiere solo el que acabó bien, con su resultado escrito.

## 8. Medición (con el bloque 6)

- **Marcas en las baterías:** `sustituciones.json` en los recursos de las baterías.
  - Corpus pequeño: `c2-almendros/poliza-seguro-2022.txt` sustituida por `c2-almendros/poliza-seguro-2024.txt`.
  - Corpus a escala: la misma pareja en cada comunidad generada desde Los Almendros.
  - El cargador compartido las aplica.
- **Baterías de búsqueda:**
  - la cuenta no cambia, porque los 5 primeros son los mismos (apartado 2);
  - se añade una comprobación: en ninguna lista de resultados sale un sustituido delante de un vigente, y todos los sustituidos llevan su marca.
- **Batería de respuestas:**
  - el puntuador gana la comprobación «sustituido presentado como vigente»: una respuesta que cita un documento sustituido tiene que decir que lo está, con «sustituido» o «sustituida» en cualquier parte de la respuesta. La instrucción lo pide así. Es una aproximación: no comprueba a qué frase acompaña la palabra;
  - ninguna pregunta del estudio cita la póliza de 2022, así que se añade un juego propio, `bateria_sustituidos.json`, en los dos corpus. Lleva E1 («¿qué franquicia tiene ahora?», que espera la de 2024) y una pregunta nueva sobre la franquicia que tenía la de 2022, que espera esa póliza y la palabra «sustituida»;
  - ese juego no entra en el porcentaje del mínimo, que se mide con las preguntas del estudio (bloque 6, 6.5). Tiene su propia cuenta.
- Se mide antes y después de este bloque, y E1 debe dar la franquicia de la póliza de 2024.

## 9. Pruebas

Aquí se dice qué se prueba. Los nombres exactos los fija el plan.

- **Casos de uso:**
  - marcar y desmarcar;
  - cambiar la marca;
  - cada 400, 404 y 409 del apartado 5.1;
  - actor de otra empresa (D-014);
  - trabajador sin permiso de administración, 403.
- **Búsqueda:**
  - el orden: un sustituido con mejor puntuación sale detrás de un vigente, y los 5 primeros son los mismos que sin marcas;
  - `replacement` nulo cuando el sustituto no se ve;
  - un trabajador que no es miembro de un proyecto cerrado no ve sus documentos, marcados o no.
- **PostgreSQL:** la migración con una base que ya tiene documentos, las comprobaciones de la tabla y la seguridad por filas al resolver el sustituto.
- **Web y contrato:** una prueba por ruta, e `IsolationTest` para las dos rutas nuevas.
- **MCP:** los campos en las tres herramientas y las instrucciones nuevas.
- **De principio a fin:** `make demo` gana un paso. El administrador sube la póliza ficticia renovada, marca la antigua como sustituida, y `searchKnowledge` devuelve la antigua detrás, marcada y con su sustituto.

## 10. Criterio de cierre

- `make check` y `make demo` terminan con 0.
- Las baterías de búsqueda, con sus umbrales y la comprobación nueva del orden.
- La batería de respuestas, medida al cerrar el bloque, sin empeorar ningún umbral.
- Entrada en el registro técnico, con el siguiente número libre, e informe del bloque.

## 11. Lo que queda al constructor

- Cómo se ordena en SQL (una subconsulta con el `LIMIT` y un orden exterior, u otra forma con el mismo efecto).
- Cómo se detecta la cadena circular (una consulta recursiva o un recorrido en el caso de uso).
- Los textos exactos de la marca en el panel, dentro de la dirección visual.

## 12. Revisión del constructor (2026-11-11)

Un subagente leyó el diseño como si tuviera que planificarlo. Hizo 3 preguntas y 4 correcciones, todas técnicas, y el CTO las decidió. Ya están en el texto:
- `replacement` es el vigente del final de la cadena (2 y 5.2);
- el juego `bateria_sustituidos.json` con una pregunta nueva sobre la póliza de 2022, y cómo se comprueba la palabra «sustituido» (8);
- misma empresa también para el administrador del grupo (2 y 5.1);
- un documento de otra empresa da 404, no 403, como en las otras rutas de documento (5.1);
- «los 5 primeros son los mismos» solo vale con límite 5; con límite 10 cambia el orden dentro de los 10, que es lo que se busca (2);
- la marca no entra en el `upsert` del procesado (2);
- el sustituto se resuelve con `content()`, la visibilidad de la búsqueda (2).

