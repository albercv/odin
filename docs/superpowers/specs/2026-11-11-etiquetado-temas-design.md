# Odín · Fase 2, bloque 3: etiquetado por tema al ingresar documentos

- Fecha: 2026-11-11. Autor: CTO (segunda sesión). Encargo 11.
- Decisiones de negocio que lo originan: D-031 y D-030. Decisión técnica: T-010.
- Amplía `2026-11-09-api-central-design.md` («spec base») y se apoya en `2026-11-10-identidad-pertenencias-design.md` («spec 2a»). Lo que este documento no cambia sigue como está allí.
- Fuente de las medidas: el estudio de búsqueda, apartado 4.9, y el registro técnico, T-010. Los dos están en el repositorio OCR, rama `feature/planificacion-tecnica`.
- Rama: `feature/fase-2-conocimiento`. Se ejecuta cuando cierre la fase 2a.
- El panel de temas va en el bloque 4. Este bloque deja el modelo, la API y el etiquetado.

## 1. Por qué

- **Listas completas:** las preguntas del tipo «todos los casos de agua» fallan si dependen de que el documento use esas palabras (estudio, 4.9).
- **Qué propone el estudio:** guardar el tema de cada documento al ingresarlo, como dato generado por IA y marcado como tal.
- **Qué decidió negocio (D-031):**
  - varios temas por documento y, en caso de duda, todos los que encajen;
  - una lista propia por cliente;
  - etiquetar en local;
  - si no da la talla, vuelve como pregunta de negocio.
- **Lo que está medido** (T-010):
  - El modelo y las instrucciones con una definición de cada tema encuentran los casos de agua de fichas nuevas: `qwen3.5:9b`, 16 de 16; `qwen3.5:4b-q4_K_M`, 14 de 16.
  - Por eso **sigue siendo candidato**: las listas completas siguen a cargo de la herramienta de listado. Este bloque lo construye como pieza opcional y lo mide dentro de Odín.

## 2. Decisiones

**De negocio que se aplican:**
- **D-031:**
  - varios temas por documento, todos los que encajen;
  - lista y definiciones propias de cada empresa;
  - el tema se marca como generado por IA, separado del original;
  - se etiqueta en local, con un equipo modesto.
- **Revisión:** el administrador revisa y corrige los temas que pone la IA, porque se revisa contenido, no personas (encargo 11, «Revisiones»; D-005).

**Del CTO.** Cada decisión dice lo que cuesta si resulta errónea:

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| El etiquetador es un puerto, `TopicTagger`. El adaptador habla con Ollama por HTTP | Es lo que se midió. Otro motor local sería otro adaptador | Un adaptador |
| Mismas instrucciones y misma forma de respuesta que se midieron (`clasificar.py` con definiciones: etiquetas separadas por punto y coma, que se cotejan con la lista) | Pedir JSON o forzar valores cambiaría lo medido, y no se ha medido | Volver a medir con otra forma |
| `temperature 0`, `think false`, contexto de 4.096 tokens y como mucho 60 tokens de respuesta | Las condiciones de T-010 | Configuración |
| Un documento largo se etiqueta **por tramos de páginas** que caben en el contexto, y se suman los temas de todos los tramos | Ollama recorta en silencio lo que no cabe. Sumar los temas de cada tramo sigue la regla de D-031: ante la duda, todos | Una sola llamada con un resumen, que habría que medir |
| Si Ollama dice que el texto ocupó todo el contexto (`prompt_eval_count` igual o mayor que `num_ctx`), el tramo falla y no se etiqueta con lo recortado | No se dan por buenos temas de un texto que el modelo no leyó entero | Reducir el tamaño del tramo |
| Etiquetado **de noche** por defecto (22:00 a 07:00, hora de la instalación), con una ventana configurable. En la demo, siempre | T-010: CPU y de noche, en el equipo de la oficina | Cambiar el valor por defecto |
| Cuando cambia la lista de temas de una empresa, sus documentos se vuelven a etiquetar | Un tema nuevo tiene que llegar a los documentos antiguos. Si no, «todos los casos de X» saldría incompleto | Hacerlo solo bajo petición |
| Lo que decide el administrador sobre un tema de un documento manda sobre la IA para siempre: lo añadido se queda y lo quitado no vuelve | Que la máquina no deshaga una corrección humana | Una acción para volver a lo de la IA |
| Solo se etiquetan documentos, no fichas de conocimiento | D-031 habla de documentos al ingresar | Etiquetar fichas al aprobarlas |
| Un nombre de tema no puede contener a otro (comparando sin tildes ni mayúsculas) | La respuesta se coteja buscando cada nombre dentro del texto, como en lo medido. «Agua» dentro de «agua y humedades» marcaría los dos | Cotejar de otra forma, y medirlo |

## 3. Fuera de alcance

- Las pantallas del panel para temas y su revisión: bloque 4.
- Etiquetar fichas de conocimiento.
- Que la IA externa etiquete. D-031 lo deja como pregunta de negocio si el modelo local no da la talla.
- Una lista de temas por defecto en el producto. Una empresa nueva nace sin temas. La lista de fincas es solo de la demo (D-031).
- Elegir modelo según la memoria del equipo de la oficina. Lo decide quien instala, con T-010.

## 4. Modelo

| Concepto | Nombre en código | Qué tiene |
|---|---|---|
| Tema | `Topic` | Empresa, nombre, definición y si está borrado. Nombre único en la empresa |
| Versión de la lista | `Company.topicListVersion` | Sube con cada alta, cambio o borrado de un tema |
| Tema de un documento | `DocumentTopic` | Documento, tema, origen (`AI` o `ADMIN`), estado (`ACTIVE` o `REMOVED`), etiquetador (nombre y versión, si es de la IA), quién lo decidió y cuándo, si es del administrador |
| Estado del etiquetado | `DocumentTagging` | Documento, versión de la lista con la que se etiquetó, estado (`PENDING`, `TAGGING`, `DONE` o `FAILED`), intentos, hora de inicio, motivo del fallo y etiquetador |

- **Migración:**
  - `topic`;
  - `document_topic`;
  - `document_tagging`;
  - `company.topic_list_version integer NOT NULL DEFAULT 0`.
- **Seguridad por filas:**
  - `topic`, por empresa;
  - `document_topic` y `document_tagging`, por empresa y por el proyecto de su documento, igual que `document_page` (spec 2a, 6).
- **Al volver a leer un documento** (reintento, o el OCR del bloque 1), se borra su `DocumentTagging` y se vuelve a etiquetar. Las decisiones del administrador se conservan.

## 5. Reglas

### 5.1 Lista de temas

- **Quién la gestiona:** el administrador de la empresa y el del grupo.
- **Validación:**
  - nombre y definición con texto;
  - nombre único en la empresa;
  - un nombre no puede contener a otro, comparando sin tildes ni mayúsculas.
  - Mensajes:
    - 409, «Ya hay un tema con ese nombre»;
    - 400, «Un nombre de tema no puede contener a otro: <nombre>».
- **Borrado:** lógico. Sus `DocumentTopic` dejan de salir.
- **Versión:** cualquier cambio sube `topicListVersion`.

### 5.2 Etiquetado

- **Caso de uso:** `TagPendingDocuments.processNext()` (`application/topic`).
  - **Ventana:** si estamos fuera de la ventana horaria, o si `isAvailable()` es falso, no toma nada.
  - **Qué toma:** con `FOR UPDATE SKIP LOCKED`, un documento `READY` cuya empresa tiene al menos un tema y que cumple una de estas:
    - no tiene `DocumentTagging`;
    - tiene uno con una versión anterior;
    - lleva en `TAGGING` más de su tiempo de espera.
  - **Intentos:** como en los otros carriles. Suma uno al tomarlo y, si pasa de 3, `FAILED`.
- **Tramos:**
  - las páginas se juntan en orden en tramos de hasta `ODIN_TAGGER_CHUNK_CHARS` caracteres (6.000 por defecto);
  - una página más larga que eso va sola, partida por el límite;
  - cada tramo es una llamada.
- **Resultado:**
  - los temas de la IA son la unión de los de todos los tramos;
  - se sustituyen los `DocumentTopic` de origen `AI` del documento;
  - nunca se toca un tema sobre el que haya decidido el administrador.
- **Sin temas:** si la respuesta no trae ningún nombre de la lista, el documento queda `DONE` sin temas. No se inventa ninguno.
- **Errores:**
  - el servicio no responde: se devuelve el intento, como en el bloque 1;
  - fallo o texto recortado en un tramo: falla el documento entero y no se guarda nada a medias.
- **Programador:** `TaggingScheduler`, en `odin-bootstrap`. Solo existe si `odin.tagger.url` tiene valor, se ejecuta cada 60 segundos con `fixedDelay` y usa el hilo de programación del bloque 1. Si el bloque 1 no está, se sube el número de hilos aquí.
- **Seguridad por filas:** sin actor, con `CompanyScope.all()` y `ProjectVisibility.all()`, como los otros carriles internos.

### 5.3 Revisión del administrador

- **Añadir** un tema a un documento: `DocumentTopic(ADMIN, ACTIVE)`, con quién y cuándo.
- **Quitar** un tema: si lo puso la IA, pasa a `REMOVED` con quién y cuándo; si lo puso el administrador, también pasa a `REMOVED`.
- **Volver a añadir** lo quitado lo deja otra vez `ACTIVE`, con origen `ADMIN`.
- **Permisos:** solo administradores de esa empresa o el del grupo. 404 si no existe; 403 si es de otra empresa.

### 5.4 Lo que ve el trabajador

- **`DescribeMe`:** gana `topics` (`id`, `name`, `definition`) y `tagging`, con el número de documentos visibles que esperan etiquetarse con la versión actual.
  - El recuento solo cuenta documentos de sus proyectos visibles, para no revelar cuántos hay en un proyecto restringido.
- **`ListDocuments`:**
  - gana el filtro opcional `topicId`;
  - cada documento lleva `topics`, con `id`, `name` y `source` (`AI` o `ADMIN`), solo los `ACTIVE`.
- **Herramienta MCP `listDocuments`:**
  - gana `topic`, por nombre, que el MCP traduce a `topicId` con la lista de `GET /v1/me`;
  - si llega con tema y hay documentos pendientes de etiquetar, la respuesta lo dice con `taggingPending`.
- **Instrucciones nuevas del MCP:**
  - Los temas de origen `AI` los asignó una IA al ingresar el documento; preséntalos como tales.
  - Una lista por tema puede estar incompleta: si `taggingPending` es mayor que 0, dilo.
  - Para una lista completa, combina el filtro por tema con la búsqueda, porque el etiquetado puede dejarse alguno.

## 6. Contrato

| Método y ruta | Quién | Qué |
|---|---|---|
| `GET, POST /v1/admin/companies/{companyId}/topics` | Administradores | `TopicView { id, name, definition }`; alta con `{ name, definition }` |
| `PATCH, DELETE /v1/admin/companies/{companyId}/topics/{topicId}` | Administradores | `{ name?, definition? }`; borrado lógico, 204 |
| `GET /v1/admin/documents/{documentId}/topics` | Administradores | `DocumentTopicView[]`: tema, origen, estado, etiquetador, quién decidió y cuándo; con el `DocumentTagging` |
| `PUT, DELETE /v1/admin/documents/{documentId}/topics/{topicId}` | Administradores | Añadir o quitar, 204 |
| `GET /v1/admin/companies/{companyId}/documents` | Administradores | `DocumentView` gana `taggingStatus` y `topics` activos |
| `GET /v1/me` | Trabajador | `MeView` gana `topics` y `tagging` |
| `GET /v1/documents?topicId=` | Trabajador | Filtro nuevo; `WorkerDocumentView` gana `topics` |

## 7. Configuración y despliegue

| Variable | Por defecto | Qué es |
|---|---|---|
| `ODIN_TAGGER_URL` | vacía | URL de Ollama. Vacía: no se etiqueta y nada cambia |
| `ODIN_TAGGER_MODEL` | `qwen3.5:9b` | Modelo. Con poca memoria, `qwen3.5:4b-q4_K_M` (T-010) |
| `ODIN_TAGGER_WINDOW` | `22:00-07:00` | Ventana horaria, o `always` |
| `ODIN_TIMEZONE` | `Europe/Madrid` | Zona horaria de la ventana |
| `ODIN_TAGGER_TIMEOUT` | `PT5M` | Tiempo máximo por tramo |
| `ODIN_TAGGER_CHUNK_CHARS` | `6000` | Tamaño máximo de un tramo |
| `ODIN_TAGGER_NUM_GPU` | vacía | Vacía: lo decide Ollama. `0`: solo CPU, como se midió en T-010 |
| `ODIN_TAGGER_NUM_THREAD` | vacía | Hilos de CPU; vacía, lo decide Ollama |

- **Compose:** servicio opcional `tagger`, con la imagen oficial de Ollama en una versión fijada, perfil `tagging` y volumen propio para el modelo.
  - `make up` no lo arranca salvo que se pida el perfil.
  - `make tagger-model` descarga el modelo una vez; es la única orden de este bloque que usa la red, como `make model` en el lector.
  - En el Mac también vale el Ollama que ya está instalado fuera de Docker, con su URL en `deploy/.env`. Nada del Mac entra en el código común.
- **Memoria de Docker:** el modelo de 9b ocupó 6,1 GB en T-010. El plan comprueba que la máquina virtual de Docker Desktop en este Mac tiene memoria para él antes de usar el servicio de Compose. Si no la tiene, se usa el Ollama del equipo.

## 8. Pruebas y verificación

- **Casos de uso**, con un doble de `TopicTagger`:
  - `TagPendingDocumentsTest`. Cubre:
    - la ventana;
    - el servicio no disponible;
    - los tramos y la unión de sus temas;
    - el texto recortado, que no se etiqueta;
    - las decisiones del administrador, que se conservan al volver a etiquetar;
    - el cambio de versión, que vuelve a poner en cola;
    - la respuesta sin temas de la lista;
    - los intentos.
  - `ManageTopicsTest`: unicidad, la regla de nombres contenidos, el borrado y la versión. Con su caso de otra empresa.
  - `ReviewDocumentTopicsTest`: añadir, quitar y volver a añadir. Con su caso de otra empresa.
  - `ListDocumentsTest` y `DescribeMeTest`: el filtro y el recuento sin contar proyectos restringidos ajenos.
- **Adaptador:** `OllamaTopicTaggerTest` contra un `HttpServer` del JDK. Cubre:
  - el cuerpo enviado (modelo, `think false`, opciones y las instrucciones con las definiciones);
  - el cotejo de la respuesta;
  - `prompt_eval_count` igual o mayor que `num_ctx`;
  - el tiempo agotado.
- **PostgreSQL:** la toma con `SKIP LOCKED` y versiones, la seguridad por filas de las tablas nuevas y el filtro por tema con proyectos visibles.
- **De principio a fin:** `make demo` gana un paso con un etiquetador **de prueba** en la pila de la demo, que responde temas fijos según palabras del texto.
  1. Un documento llega a `DONE` con su tema.
  2. El trabajador lo lista por tema.
  3. El administrador quita el tema y se queda quitado al volver a etiquetar.

  Prueba el camino de Odín, no el modelo.
- **Batería con el modelo real** (`make tagging-battery`, fuera de `make check`):
  - `TaggingBatteryIT` repite con Odín la medida de T-010 sobre las 46 fichas de control, con las claves del estudio copiadas a los recursos de prueba;
  - da la exhaustividad por tema y las etiquetas de más, y los segundos por documento y la memoria que dice Ollama;
  - si las cifras no se parecen a las de T-010 con el mismo modelo, se investiga antes de cerrar.
- **Documentos largos:** la batería añade los documentos de varias páginas del corpus limpio (pólizas, contratos y actas) para medir los tramos. No hay medida previa de eso.
- **Regresión:** la batería de búsqueda mantiene 27 de 30.

## 9. Criterio de cierre

- `make check` y `make demo` terminan con 0.
- `make tagging-battery` ejecutada en este Mac, en CPU, con sus cifras en el informe del bloque.
- Si la exhaustividad de un tema sale peor que en T-010, se dice en el informe y se plantea al CEO.
- Decisiones registradas en el registro técnico (T-017).

## 10. Lo que queda al constructor

- Nombres internos y la consulta exacta de la toma.
- Cómo se parte una página más larga que el tramo: por párrafos o por el límite.
- La versión fijada de la imagen de Ollama, la última estable al ejecutar.
