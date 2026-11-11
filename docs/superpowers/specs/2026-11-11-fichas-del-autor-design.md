# Odín · Fase 2, bloque 5: el autor ve sus fichas y sus preguntas

- Fecha: 2026-11-11. Autor: CTO (segunda sesión). Encargo 11.
- Decisiones de negocio que lo originan: D-036 y D-037.
- Amplía `2026-11-09-api-central-design.md` («spec base»), apartados 6.8 y 13, y se apoya en `2026-11-10-identidad-pertenencias-design.md` («spec 2a»). Lo que este documento no cambia sigue como está allí.
- Rama: `feature/fase-2-conocimiento`. Se ejecuta cuando cierre la fase 2a.

## 1. Por qué

- **D-036 lo pide:**
  - el autor ve sus fichas pendientes en sus búsquedas, marcadas como «pendiente de revisión», mientras sus compañeros no las ven;
  - el trabajador ve sus propias preguntas.
- **D-037 lo dejó para la fase 2:** el trabajador no tiene panel, así que tiene que ser por MCP.
- **Hoy:**
  - quien guarda una ficha recibe el aviso de que queda pendiente y después no vuelve a saber de ella;
  - si se la rechazan, no se entera;
  - no puede ver qué ha preguntado.

## 2. Decisiones

**De negocio que se aplican:**
- **D-036:**
  - una ficha pendiente la ven solo su autor y los administradores;
  - una rechazada no sale en ninguna búsqueda;
  - el trabajador ve sus preguntas, y el panel nunca enseña las de una persona.
- **D-005:** la revisión es del contenido, no de la persona. Nada cuenta las fichas rechazadas de nadie.

**Preguntas al CEO** (enviadas el 2026-11-11). El diseño sigue la recomendación del CTO:

| Pregunta | Recomendación, que es lo que sigue el diseño | Si la respuesta es otra |
|---|---|---|
| ¿Qué se dice al autor de una ficha rechazada? (D-036 lo dejó abierto) | La ve en su lista como «rechazada», con la nota del revisor, que trata del contenido. Nunca sale en búsquedas | Se quita la nota o el estado de la respuesta |
| ¿Entra aquí que el trabajador vea sus preguntas? | Sí, por MCP | Se quita la tarea de las preguntas, que es independiente |

**Del CTO.** Cada decisión dice lo que cuesta si resulta errónea:

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| Las fichas pendientes del autor salen en sus búsquedas y en sus casos parecidos (bloque 2), marcadas | D-036 dice «en sus búsquedas», y los casos parecidos son una búsqueda. Así no hay dos reglas distintas | Quitar el filtro de una consulta |
| La regla de qué fichas ve cada uno vive en un solo sitio, el filtro común de fichas de la búsqueda, y la usan la búsqueda y los casos parecidos | Que no se pueda cumplir en una consulta y olvidar en otra | — |
| La lista del autor no dice quién revisó | Al autor le sirve qué se decidió y por qué, no quién | Un campo más |
| Las preguntas propias salen de la empresa de la clave, no de todas las del autor | La clave actúa en una sola empresa (spec 2a, 5.2). Cada empresa tiene su registro | Ninguno: es la regla de aislamiento |
| Las fichas y preguntas propias respetan los proyectos restringidos: una ficha de un proyecto que el autor ya no ve no sale | Las mismas dos barreras que todo lo demás (spec 2a, 6) | — |

## 3. Fuera de alcance

- Que el autor edite, retire o vuelva a enviar una ficha. Para corregirla guarda otra.
- Avisos activos al autor cuando se revisa su ficha: el MCP no envía mensajes, responde cuando se le pregunta.
- Borrar preguntas propias. El plazo de conservación espera la validación legal (D-022, D-036).
- Que un administrador vea las preguntas de una persona: nunca (D-036).

## 4. Reglas

### 4.1 Fichas pendientes en las búsquedas del autor

- **Qué fichas entran** en `searchKnowledge` y en `findSimilarCases`:
  - las `APPROVED`, como hoy;
  - más las `PENDING` cuyo autor es el actor.
  - Siempre con el filtro de empresa y de proyectos visibles.
- **Resultado:** cada ficha lleva `reviewStatus`, `APPROVED` o `PENDING`.
- **Las rechazadas** no entran nunca.
- **Instrucción del MCP:** una ficha con `reviewStatus` `PENDING` es tuya y está pendiente de revisión. Dilo al citarla y recuerda que tus compañeros todavía no la ven.

### 4.2 Mis fichas

- **Caso de uso:** `ListMyKnowledgeCards(actor, status?)`.
  - **Acceso:** `WorkerAccess` con `SAVE_KNOWLEDGE_CARD`.
  - **Qué devuelve:** las fichas del actor en la empresa de su clave y en sus proyectos visibles, de la más reciente a la más antigua, hasta 100.
  - **Cada ficha:** `id`, `title`, `body`, `projectId`, `createdAt`, `status` y `review`.
    - `review` lleva `decision`, `note` y `reviewedAt`.
    - Si se corrigió, `body` es el texto corregido y `originalBody` el que envió el autor.
- **Filtro `status`:** `PENDING`, `APPROVED` o `REJECTED`.

### 4.3 Mis preguntas

- **Caso de uso:** `ListMyQuestions(actor, limit?)`.
  - **Acceso:** `WorkerAccess` con `SEARCH_KNOWLEDGE`.
  - **Qué devuelve:** los `QuestionRecord` del actor en la empresa de su clave, de la más reciente a la más antigua.
    - Cada uno: `text`, `kind` (del bloque 2; `SEARCH` si no está), `hitCount` y `askedAt`.
    - 50 por defecto y 200 como máximo.
- El texto es lo que envió su IA como consulta, no lo que escribió el trabajador. La descripción de la herramienta lo dice.

## 5. Contrato

| Método y ruta | Quién | Qué |
|---|---|---|
| `POST /v1/search` y `POST /v1/similar-cases` | Trabajador | Cada ficha gana `reviewStatus` |
| `GET /v1/me/cards?status=` | Trabajador | `MyCardView[]` |
| `GET /v1/me/questions?limit=` | Trabajador | `MyQuestionView[]` |

## 6. MCP

- **`listMyKnowledgeCards`,** con la capacidad `SAVE_KNOWLEDGE_CARD`. Entrada: `status`, opcional.
  - Descripción: «Lista las fichas que has guardado y en qué estado está su revisión».
  - Si una está rechazada, enseña la nota del revisor tal cual.
- **`listMyQuestions`,** con la capacidad `SEARCH_KNOWLEDGE`. Entrada: `limit`, opcional.
  - Descripción: «Lista las consultas que tu IA ha hecho al cerebro con tu clave».
- **`saveKnowledgeCard`:** su descripción añade que el autor puede ver el estado con `listMyKnowledgeCards`.
- **Instrucción nueva:** la del apartado 4.1.

## 7. Pruebas

- **Casos de uso:**
  - `SearchKnowledgeTest`. Cubre:
    - `authorSeesOwnPendingCardMarked`;
    - `colleagueDoesNotSeePendingCard`;
    - `rejectedCardIsNeverFound`, para el autor también;
    - `ownPendingCardInRestrictedProjectNoLongerVisibleIsHidden`.
  - `FindSimilarCasesTest`: `authorSeesOwnPendingCard`.
  - `ListMyKnowledgeCardsTest`. Cubre:
    - solo las del actor;
    - el filtro por estado;
    - la corregida con los dos textos;
    - la rechazada con su nota;
    - la capacidad desactivada;
    - el actor de la misma persona con la clave de otra empresa, que no ve las de la primera (D-014 y spec 2a).
  - `ListMyQuestionsTest`. Cubre:
    - solo las del actor y de la empresa de la clave;
    - el orden;
    - el límite;
    - el actor de otra empresa.
- **PostgreSQL:** el filtro común de fichas, con autor y estado, y la seguridad por filas.
- **Web y contrato:** una prueba por ruta, e `IsolationTest`: con la clave de otra persona no se ven las fichas ni las preguntas de nadie más.
- **De principio a fin:** `make demo` gana un paso.
  1. El trabajador guarda una ficha.
  2. La encuentra marcada como pendiente; otro trabajador no la encuentra.
  3. El administrador la rechaza con una nota.
  4. El autor ve «rechazada» con la nota, y ya no la encuentra al buscar.
  5. El autor ve sus preguntas.
- **Regresión:** la batería de búsqueda mantiene 27 de 30.

## 8. Criterio de cierre

- `make check` y `make demo` terminan con 0.
- Respuestas del CEO aplicadas y decisiones registradas en el registro técnico (T-019).

## 9. Lo que queda al constructor

- Nombres internos y cómo se pasa el autor al filtro común de fichas.
- Los textos exactos de las descripciones de las herramientas, con el sentido de los apartados 4 y 6.
