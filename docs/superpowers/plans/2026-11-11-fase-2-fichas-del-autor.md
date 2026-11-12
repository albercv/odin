# Odín · Fase 2, bloque 5: el autor ve sus fichas y sus preguntas — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que el autor encuentre sus fichas pendientes marcadas en sus búsquedas, vea el estado y la revisión de sus fichas, y vea sus propias consultas. Todo por MCP y sin que nadie más vea nada nuevo.

**Arquitectura:**
- La regla de qué fichas ve cada uno pasa a un solo sitio de PostgreSQL, que usan la búsqueda y los casos parecidos.
- Dos casos de uso de lectura nuevos, con sus rutas bajo `/v1/me` y sus herramientas MCP.

**Tecnología:** Java 21, Spring Boot 4.1.2, PostgreSQL 17.10 con seguridad por filas, TypeScript con el SDK de MCP y Vitest.

**Diseño:** `docs/superpowers/specs/2026-11-11-fichas-del-autor-design.md` (aquí, «spec»), que amplía el spec base y el de la 2a. Quien ejecute una tarea lee los tres.

## Cuándo se ejecuta

- Tras el cierre de la 2a y **después del bloque 2**, cuando lo diga el CEO. En el orden de D-049 van antes los bloques 1, 3, 6, 7 y 2, y después el 4.
- Las respuestas del CEO (D-040 y D-044) ya están aplicadas. La pantalla de revisión, que pide que la nota trate del contenido, va en el bloque 4.

## Restricciones globales

- **TDD con evidencia:** primero la prueba; se ejecuta y se guardan en el informe la orden y la salida en rojo antes del código.
- **Capas:** `odin-domain` solo `java.*`; `odin-application` solo `odin-domain` y `java.*`; Spring solo en los adaptadores y en `odin-bootstrap`. ArchUnit lo comprueba.
- **Clean Code (D-029):** métodos de 30 líneas como máximo, sin lógica duplicada y con comentarios mínimos. El filtro de fichas existe una sola vez.
- **Nombres:** identificadores en inglés, explicativos y en camelCase. Mensajes, documentación y commits en español.
- **Aislamiento (D-014, spec 2a 6):**
  - cada caso de uso lleva una prueba con un actor de otra empresa, otra con la misma persona actuando con la clave de otra empresa, y otra con un proyecto cerrado del que ya no es miembro (D-047);
  - la seguridad por filas es la segunda barrera.
- **D-036:**
  - una ficha pendiente solo la ve su autor;
  - una rechazada no sale en ninguna búsqueda;
  - el panel no gana nada.
- **Contrato:** si cambia la API, en la misma tarea se regeneran `api/openapi.json` (`cd api && mvn -q verify -Dodin.updateOpenApi=true`) y los tipos (`npm run generate:types` en `panel/` y en `mcp/`).
- **Cierre de cada tarea:** `make check` y `make demo` terminan con 0, y `SearchBatteryIT` mantiene 27 de 30 y `SearchScaleBatteryIT` su umbral.
- **Dónde se trabaja:** en `.worktrees/fase-2`. No se ejecutan `make up` ni `make down`.
- **Git:**
  - cada tarea en su rama `task/fase-2-conocimiento/<tarea>`;
  - commits en español, sin atribución a Claude ni `Co-Authored-By`;
  - sin push;
  - el merge `--no-ff` lo hace el CTO.
- **Sin migraciones:** todo lo que hace falta ya está en las tablas.

## Foco de revisión

1. **Un compañero busca justo el título de una ficha pendiente de otro:** no la encuentra, ni en la búsqueda ni en casos parecidos. Prueba en la tarea 1 (`colleagueDoesNotSeePendingCard` y `colleagueDoesNotSeePendingCardInSimilarCases`).
2. **La misma persona en dos empresas:** con la clave de B no ve las fichas ni las preguntas que hizo con la de A. Prueba en la tarea 2 (`sameUserWithKeyOfOtherCompanySeesNothingOfTheFirst`).
3. **Una ficha rechazada que el autor buscaba antes:** deja de salir también para él. Prueba en la tarea 1 (`rejectedCardIsNeverFound`).
4. **Una ficha corregida:** el autor la ve con su texto y con el corregido, y en la búsqueda sale como aprobada. Prueba en la tarea 2 (`correctedCardShowsBothTexts`).
5. **Un administrador que también trabaja:** con su clave ve sus fichas, y con su sesión del panel no cambia nada. Prueba en la tarea 2 (`adminWhoAlsoWorksListsOwnCardsWithKey`).

---

## Interfaces compartidas (fuente única de nombres)

```java
// domain/knowledge
public enum ReviewStatus { APPROVED, PENDING }          // solo lo que puede salir en una búsqueda
// SearchHit y SimilarCase ganan el componente final `Optional<ReviewStatus> reviewStatus` (vacío en documentos)
// SearchQuery y SimilarCasesQuery ganan el componente final `UserId searcher`

// application/port — KnowledgeCardRepository gana:
List<KnowledgeCard> listByAuthor(CompanyScope scope, ProjectVisibility visibility, UserId authorId,
        Optional<CardStatus> status, int limit);
// application/port — QuestionLog gana:
List<QuestionRecord> listByAuthor(CompanyId companyId, UserId authorId, int limit);

// application/knowledge
public class ListMyKnowledgeCards { public List<KnowledgeCard> execute(Actor actor, Optional<CardStatus> status); }
// application/search
public class ListMyQuestions { public List<QuestionRecord> execute(Actor actor, Optional<Integer> limit); }

// adapter/postgres — en SearchSql (bloque 2):
static String visibleCards(String alias);   // "(c.status = 'APPROVED' OR (c.status = 'PENDING' AND c.author_id = :searcher))"
```

**Rutas:**

| Ruta | Respuesta | Tarea |
|---|---|---|
| `POST /v1/search` · `POST /v1/similar-cases` | `Hit` y `SimilarCaseView` ganan `reviewStatus` (`null` en documentos) | 1 |
| `GET /v1/me/cards?status=` | `MyCardView[]`: `id`, `title`, `body`, `originalBody`, `projectId`, `createdAt`, `status`, `review { decision, note, reviewedAt }` o `null` | 2 |
| `GET /v1/me/questions?limit=` | `MyQuestionView[]`: `text`, `kind`, `hitCount`, `askedAt` | 2 |

---

### Tarea 1: Fichas pendientes del autor en sus búsquedas · Sonnet

Rama `task/fase-2-conocimiento/pendientes-del-autor`. Spec 4.1.

**Ficheros:**
- **Crear:** `domain/knowledge/ReviewStatus.java`.
- **Modificar:**
  - dominio: `SearchHit`, `SimilarCase`, `SearchQuery` y `SimilarCasesQuery`;
  - aplicación: `SearchKnowledge` y `FindSimilarCases`, que pasan `actor.userId()`;
  - PostgreSQL: `SearchSql` y `PostgresKnowledgeSearch`;
  - web: `Responses`;
  - MCP: `mcp/src/server.ts`;
  - pruebas: `ScriptedKnowledgeSearch`;
  - contrato y tipos.

**Valores:**
- Las dos consultas usan `SearchSql.visibleCards("c")` en lugar de `c.status = 'APPROVED'`. `:searcher` es `searcher.value()`.
- `reviewStatus` sale de `c.status` en las fichas y es nulo en los documentos.
- Instrucción nueva: «Una ficha con reviewStatus PENDING es tuya y está pendiente de revisión: dilo al citarla y recuerda que tus compañeros todavía no la ven.».
- `hitCount` del registro cuenta lo que recibió quien preguntó.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `PostgresKnowledgeSearchIT`:
    - `authorSeesOwnPendingCardMarked`;
    - `colleagueDoesNotSeePendingCard`;
    - `rejectedCardIsNeverFound`, para el autor también;
    - `ownPendingCardInRestrictedProjectNoLongerVisibleIsHidden`: una ficha pendiente de un proyecto cerrado del que el autor ya no es miembro no sale;
    - `authorSeesOwnPendingCardInSimilarCases`;
    - `colleagueDoesNotSeePendingCardInSimilarCases`.
  - `SearchKnowledgeTest.passesSearcher` y `FindSimilarCasesTest.passesSearcher`;
  - `WorkerApiTest.searchHitCarriesReviewStatus`;
  - la prueba de `INSTRUCTIONS` del MCP.
- [ ] **Paso 2:** implementa. Comprueba con `grep -rn "status = 'APPROVED'" api/odin-adapter-postgres/src/main` que no queda ninguna regla de fichas fuera de `SearchSql`.
- [ ] **Paso 3:** regenera el contrato y los tipos. `make check` y `make demo` terminan con 0.
- [ ] **Paso 4:** commit «feat: el autor ve sus fichas pendientes en sus búsquedas».

### Tarea 2: Mis fichas y mis preguntas · Sonnet

Rama `task/fase-2-conocimiento/mis-fichas-y-preguntas`. Spec 4.2, 4.3, 5 y 6.

**Ficheros:**
- **Crear:**
  - `application/knowledge/ListMyKnowledgeCards.java` y `application/search/ListMyQuestions.java`, con sus pruebas;
  - `mcp/src/tools/listMyKnowledgeCards.ts` y `listMyQuestions.ts`, con sus pruebas.
- **Modificar:**
  - puertos y dobles: `KnowledgeCardRepository`, `QuestionLog`, `InMemoryKnowledgeCardRepository` e `InMemoryQuestionLog`;
  - PostgreSQL: `PostgresKnowledgeCardRepository` y `PostgresQuestionLog`, con sus IT;
  - web: `WorkerController`, `Responses`, `UseCaseConfiguration` y `TestWiring`;
  - MCP: `mcp/src/server.ts`, `mcp/src/api/client.ts` y `mcp/src/tools/saveKnowledgeCard.ts`;
  - demostración: `mcp/scripts/demo-client.ts`;
  - contrato y tipos.

**Interfaces que consume:** `QuestionKind` (bloque 2), y de la 2a `WorkerAccess` (con `Granted(companyId, visibility, content)`), `ProjectMemberRepository` y `ProjectVisibility`.

**Valores:**
- `ListMyKnowledgeCards`:
  - recibe `ProjectMemberRepository projectMembers` en el constructor, como `SearchKnowledge` y `VerifyCitation`;
  - `var access = WorkerAccess.require(actor, organizations, projectMembers, Capability.SAVE_KNOWLEDGE_CARD)`;
  - lee con `access.content()` como visibilidad de proyectos, igual que `SearchKnowledge`;
  - límite 100, de la más reciente a la más antigua.
- `ListMyQuestions`:
  - recibe `ProjectMemberRepository projectMembers` y llama a `WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE)`;
  - `WorkerAccess.cap(limit, 50, 200)`;
  - solo la empresa de la clave, de la más reciente a la más antigua.
- `MyCardView.review` es nulo si la ficha está `PENDING`. `originalBody` solo tiene valor si se corrigió.
- **Herramientas MCP:**
  - `listMyKnowledgeCards`, con `SAVE_KNOWLEDGE_CARD` y entrada `status` (`PENDING`, `APPROVED` o `REJECTED`). Descripción: «Lista las fichas que has guardado y en qué estado está su revisión. Si una está rechazada, enseña la nota del revisor tal cual.».
  - `listMyQuestions`, con `SEARCH_KNOWLEDGE` y entrada `limit`. Descripción: «Lista las consultas que tu IA ha hecho al cerebro con tu clave.».
  - `saveKnowledgeCard`: su descripción añade «Puedes ver su estado con listMyKnowledgeCards.».
- **`INSTRUCTIONS` gana** (D-044):
  - «Si una ficha tuya tiene la decisión CORRECT, preséntala como corregida por el administrador y enseña los dos textos: el que enviaste y el corregido.»;
  - «Si una ficha tuya está rechazada, preséntala como rechazada y enseña la nota del revisor tal cual.».
- `demo-client.ts`, paso nuevo:
  1. el trabajador A guarda una ficha;
  2. la encuentra con `reviewStatus: PENDING` y el trabajador de la misma empresa no la encuentra;
  3. el administrador la rechaza con la nota «Falta la fecha de la junta» por la API;
  4. A la ve como `REJECTED` con esa nota y ya no la encuentra;
  5. `listMyQuestions` devuelve sus consultas, la primera la más reciente.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - `ListMyKnowledgeCardsTest`:
    - `listsOnlyOwnCardsNewestFirst`;
    - `filtersByStatus`;
    - `correctedCardShowsBothTexts`;
    - `rejectedCardShowsReviewNote`;
    - `capabilityDisabledIsForbidden`;
    - `sameUserWithKeyOfOtherCompanySeesNothingOfTheFirst`;
    - `ownCardInRestrictedProjectNoLongerVisibleIsHidden`: una ficha de un proyecto cerrado del que ya no es miembro no sale;
    - `adminWhoAlsoWorksListsOwnCardsWithKey`.
  - `ListMyQuestionsTest`:
    - `listsOnlyOwnQuestionsOfKeyCompanyNewestFirst`;
    - `limitDefaultsToFiftyAndCapsAtTwoHundred`;
    - `actorOfAnotherCompanySeesNothing`.
  - IT de PostgreSQL: `listByAuthorFiltersCompanyProjectsAndStatus` y `listByAuthorOfQuestionsFiltersCompany`.
  - `WorkerApiTest`: una prueba por ruta. `IsolationTest.keyOfOtherPersonNeverListsSomeoneElsesCardsOrQuestions`.
  - MCP: `registeredOnlyWithCapability`, para las dos, `sendsStatusFilter`, y la prueba de `INSTRUCTIONS` con las dos frases nuevas.
- [ ] **Paso 2:** implementa. Regenera el contrato y los tipos.
- [ ] **Paso 3:** `make demo` dos veces seguidas y `make check` terminan con 0.
- [ ] **Paso 4:** commit «feat: el autor ve sus fichas y sus preguntas por MCP».

### Cierre del bloque · CTO

- Revisión final de la rama con Opus. El foco es D-036: ninguna ficha pendiente o rechazada llega a quien no debe.
- `make check` y `make demo` ejecutados por el CTO.
- Regresión: `SearchBatteryIT` en 27 de 30 y `SearchScaleBatteryIT` con su umbral.
- Al cerrar el bloque se mide la batería de respuestas (diseño del bloque 6, 6.6), porque las dos herramientas MCP nuevas y sus instrucciones cambian lo que ve la IA.
- Informe del bloque, su entrada en el registro técnico (el siguiente número libre al escribirla) y copia del informe en la carpeta de notas para el CEO.

## Orden y modelos

- En serie: 1 y luego 2. La tarea 2 toca el mismo `server.ts` y el mismo cableado.
- **Revisores:** Opus en las dos, porque tocan quién ve qué.
