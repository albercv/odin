# Odín fase 1 (API central) — plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: superpowers:subagent-driven-development (o superpowers:executing-plans). Los pasos usan casillas (`- [ ]`).

**Objetivo:** construir la fase 1 de Odín hasta que la demostración de cierre (spec 3.1) funcione de principio a fin con `make demo`.

**Arquitectura:** hexagonal. El dominio y los casos de uso son Java puro; cada tecnología es un adaptador en su propio módulo Maven. El MCP y el panel son clientes de la API en TypeScript. Todo se despliega con Docker Compose.

**Tecnologías:**
- Java 21 y Maven 3.9.
- Spring Boot 4.1.2, springdoc-openapi 3.2.0, Flyway (versión del BOM de Boot), PostgreSQL 17.10, Testcontainers (BOM de Boot), ArchUnit 1.5.1, Checkstyle 14.4.0, PDFBox 3.0.8, POI 6.0.0 y JaCoCo.
- Node 24, TypeScript, `@modelcontextprotocol/sdk` 1.32.2, zod, Vitest, React, Vite, `openapi-typescript` y Playwright.

**Spec:** `docs/superpowers/specs/2026-11-09-api-central-design.md`. Los números de apartado (`spec 6.4`) remiten a él.

## Restricciones globales

- Paquete base `com.e2d.odin`. Identificadores en inglés y camelCase. Mensajes al usuario, documentación y commits en español.
- **Métodos:** 30 líneas como máximo (Checkstyle `MethodLength` max 30, ESLint `max-lines-per-function` 30). Comentarios mínimos.
- **Capas:**
  - `odin-domain`: solo `java.*`.
  - `odin-application`: solo `odin-domain` y `java.*`.
  - Un adaptador nunca depende de otro.
  - Spring solo en `odin-adapter-web`, `odin-adapter-postgres` y `odin-bootstrap`.
- **TDD:** primero la prueba, verla fallar, luego el código.
- **Dependencias en el proyecto:**
  - Maven con el repositorio local `api/.m2/repository`, mediante `api/.mvn/maven.config` con `-Dmaven.repo.local=.m2/repository`. Se ejecuta siempre desde `api/`.
  - npm con `node_modules` locales. Playwright con `PLAYWRIGHT_BROWSERS_PATH=0`.
  - Nada global.
- **Puertos** solo en `127.0.0.1`. Ningún valor propio de un equipo (rutas del Mac, `host.docker.internal`) en el código ni en `compose.yaml`.
- **Datos:** solo ficticios.
- **Secretos:** ni en logs ni en el repositorio.
- **Git:**
  - Cada tarea trabaja en su rama `task/api-central/<tarea>`, que sale de `feature/api-central`.
  - Commits en español sin atribución a Claude, ni `Co-Authored-By` ni firmas.
  - Sin push. El CTO hace el merge `--no-ff` a la feature.
- **Contratos:** JSON en camelCase. Errores RFC 9457.
- **Decisiones pendientes del CEO (spec 13), aisladas:**
  - las fichas visibles en la búsqueda salen de `CardVisibilitySettings.searchableStatuses` (por defecto `{APPROVED}`);
  - guardar el texto de las preguntas depende de `QuestionLogSettings.storeText` (por defecto `true`; si es `false`, se guarda `null`).

**Ajuste del spec 7:** las variables de RLS son `app.company_ids` (UUID separados por comas, porque `CompanyScope` es un conjunto) y `app.group_scope`.

## Foco de revisión

Situaciones que el spec implica, que ninguna tarea cubre por sí sola y con más probabilidad de fallarle a quien use Odín:

1. **Clave revocada o trabajador desactivado:** usar la clave después da 401, también si la sesión MCP ya estaba abierta. La prueba va en la tarea 2 (`ResolveActorTest`).
2. **Mismo fichero subido a otra empresa:** no es duplicado. Cada empresa tiene su documento, aunque el original en disco sea el mismo por la huella. Prueba en la tarea 3 (`UploadDocumentTest`).
3. **Consulta sin palabras útiles** (`"¿?"`, solo palabras vacías o cadena vacía): devuelve `noSource: true` sin error de SQL. Prueba en la tarea 5 (`PostgresKnowledgeSearchIT`).
4. **Cita con saltos de línea o espacios distintos, o en otras mayúsculas:** `verifyCitation` la da por buena. Si cambia una letra o una tilde, no. Prueba en la tarea 3 (`QuoteMatcherTest`).
5. **Fichero vacío o con otro tipo disfrazado** (extensión `.pdf` con texto plano dentro): un fichero vacío da 400. El tipo se detecta por el contenido, así que ese texto plano se trata como texto. Pruebas en las tareas 3 y 6.

---

## Mapa de ficheros

```
api/pom.xml                                   padre: módulos, versiones, Checkstyle, JaCoCo
api/.mvn/maven.config                         repositorio Maven local
api/config/checkstyle.xml                     MethodLength 30 y reglas básicas
api/odin-domain/src/main/java/com/e2d/odin/domain/{shared,organization,identity,document,knowledge,search}/
api/odin-application/src/main/java/com/e2d/odin/application/{shared,identity,organization,document,knowledge,search}/   casos de uso
api/odin-application/src/main/java/com/e2d/odin/application/port/   puertos de salida
api/odin-application/src/test/java/com/e2d/odin/application/fake/   dobles en memoria (se publican como test-jar)
api/odin-adapter-postgres/src/main/resources/db/migration/V1..V4__*.sql
api/odin-adapter-postgres/src/main/java/com/e2d/odin/adapter/postgres/
api/odin-adapter-postgres/src/test/resources/search-battery/
api/odin-adapter-filestore/src/main/java/com/e2d/odin/adapter/filestore/
api/odin-adapter-readers/src/main/java/com/e2d/odin/adapter/readers/
api/odin-adapter-web/src/main/java/com/e2d/odin/adapter/web/{admin,worker,setup,security,error}/
api/odin-bootstrap/src/main/java/com/e2d/odin/bootstrap/
api/odin-architecture-tests/src/test/java/com/e2d/odin/architecture/LayerRulesTest.java
api/openapi.json                              contrato versionado
mcp/                                          servidor MCP
panel/                                        panel React
deploy/{compose.yaml,api.Dockerfile,mcp.Dockerfile,panel.Dockerfile,nginx.conf,.env.example,demo/}
Makefile                                      setup, test, check, up, down, demo
```

## Interfaces compartidas (fuente única de nombres)

Los nombres de esta sección son los que usan todas las tareas.

**Dominio (`com.e2d.odin.domain`):**
- **`shared`:**
  - identificadores como `record` sobre `UUID`, con `static X of(String)`: `GroupId`, `CompanyId`, `ProjectId`, `UserId`, `AccessKeyId`, `DocumentId`, `CardId`;
  - `CompanyScope(Set<CompanyId> companies, boolean allCompanies)`, con `of(CompanyId)`, `all()` e `includes(CompanyId)`;
  - excepciones que extienden `DomainException` (de tipo runtime): `NotFoundException`, `ForbiddenException`, `UnauthorizedException`, `ValidationException`, `ConflictException`, `UnsupportedFormatException` y `PayloadTooLargeException`.
- **`organization`:**
  - `Group(GroupId id, String name)`;
  - `Capability`, con los valores `SEARCH_KNOWLEDGE` y `SAVE_KNOWLEDGE_CARD`;
  - `Company(CompanyId id, String name, Set<Capability> capabilities)`;
  - `Project(ProjectId id, CompanyId companyId, String name)`.
- **`identity`:**
  - `Role`, con los valores `GROUP_ADMIN`, `COMPANY_ADMIN` y `WORKER`;
  - `User(UserId id, String displayName, Optional<String> email, Role role, Optional<CompanyId> companyId, boolean active, Optional<String> passwordHash)`;
  - `AccessKey(AccessKeyId id, UserId userId, String label, String prefix, String secretHash, Instant createdAt, Optional<Instant> revokedAt)`;
  - `AdminSession(String tokenHash, UserId userId, Instant createdAt, Instant lastSeenAt)`;
  - `Actor(UserId userId, String displayName, Role role, Optional<CompanyId> companyId)`, con `isGroupAdmin()`, `requireCompany()` y `scope()`. `scope()` devuelve `all()` para el administrador del grupo y `of(companyId)` para el resto.
- **`document`:**
  - `DocumentFormat`, con los valores `PDF`, `DOCX`, `PLAIN_TEXT`, `MARKDOWN` e `IMAGE`;
  - `DocumentStatus`, con los valores `RECEIVED`, `PROCESSING`, `READY`, `FAILED` y `PENDING_READER`;
  - `Document(DocumentId id, CompanyId companyId, Optional<ProjectId> projectId, String title, String filename, DocumentFormat format, String sha256, DocumentStatus status, Optional<String> failureReason, boolean paginated, UserId uploadedBy, Instant uploadedAt, Optional<Instant> processingStartedAt, int attemptCount)`. Sus transiciones devuelven una copia: `startProcessing(Instant)`, `markReady(boolean paginated)`, `markFailed(String)`, `markPendingReader()`, `requeue()` y `resetAttempts()`;
  - `DocumentPage(DocumentId documentId, int number, String text, String readerName, String readerVersion)`.
- **`knowledge`:**
  - `CardStatus`, con los valores `PENDING`, `APPROVED` y `REJECTED`;
  - `ReviewDecision`, con los valores `APPROVE`, `CORRECT` y `REJECT`;
  - `CardReview(UserId reviewerId, ReviewDecision decision, Instant reviewedAt, Optional<String> note)`;
  - `KnowledgeCard(CardId id, CompanyId companyId, Optional<ProjectId> projectId, String title, String body, Optional<String> originalBody, UserId authorId, Instant createdAt, CardStatus status, Optional<CardReview> review)`;
  - `QuestionRecord(UUID id, CompanyId companyId, UserId authorId, String text, int hitCount, Instant askedAt)`, donde `text` admite `null`.
- **`search`:**
  - `SourceType`, con los valores `ORIGINAL_DOCUMENT` y `KNOWLEDGE_CARD`;
  - `SearchQuery(List<String> terms, CompanyScope scope, Optional<ProjectId> projectId, int limit, Set<CardStatus> cardStatuses)`;
  - `SearchHit(SourceType sourceType, UUID sourceId, String title, Optional<Integer> pageNumber, boolean paginated, Optional<ProjectId> projectId, String authorName, Instant date, String excerpt, double rank)`;
  - `SearchResult(List<SearchHit> hits)`, con `noSource()`;
  - `QuoteMatcher.containsLiteral(String pageText, String quote)`, que normaliza los espacios (cualquier secuencia pasa a ser un espacio) y las mayúsculas; las tildes no.

**Puertos de salida (`com.e2d.odin.application.port`):**

```java
interface OrganizationRepository { Optional<Group> findGroup(); void saveGroup(Group g); void saveCompany(Company c);
  Optional<Company> findCompany(CompanyId id); List<Company> listCompanies(CompanyScope scope);
  void saveProject(Project p); Optional<Project> findProject(CompanyId companyId, ProjectId id); List<Project> listProjects(CompanyId companyId); }
interface UserRepository { void save(User u); Optional<User> findById(UserId id); Optional<User> findByEmail(String email);
  List<User> listByCompany(CompanyId companyId); }
interface AccessKeyRepository { void save(AccessKey k); Optional<AccessKey> findById(AccessKeyId id);
  Optional<AccessKey> findByPrefix(String prefix); List<AccessKey> listByUser(UserId userId); }
interface AdminSessionRepository { void save(AdminSession s); Optional<AdminSession> findByTokenHash(String tokenHash); void delete(String tokenHash); }
interface DocumentRepository { void save(Document d); void replacePages(DocumentId id, List<DocumentPage> pages);
  Optional<Document> findById(CompanyScope scope, DocumentId id); Optional<Document> findBySha256(CompanyId companyId, String sha256);
  List<Document> list(CompanyScope scope, Optional<ProjectId> projectId, int offset, int limit);   // orden: title, id
  Optional<DocumentPage> findPage(CompanyScope scope, DocumentId id, int pageNumber);
  Optional<Document> claimNextForProcessing(Instant now, Duration staleAfter); }               // RECEIVED o PROCESSING caducado
interface KnowledgeCardRepository { void save(KnowledgeCard c); Optional<KnowledgeCard> findById(CompanyScope scope, CardId id);
  List<KnowledgeCard> listByStatus(CompanyScope scope, CardStatus status); }
interface QuestionLog { void record(QuestionRecord r); }
interface KnowledgeSearch { SearchResult search(SearchQuery q); }
interface OriginalFileStore { String store(byte[] content); byte[] read(String sha256); }        // devuelve sha256 hex
interface DocumentFormatDetector { Optional<DocumentFormat> detect(byte[] content); }
interface DocumentTextReader { boolean supports(DocumentFormat f); ReadResult read(byte[] content, DocumentFormat f); }
record ReadResult(List<String> pageTexts, boolean paginated, String readerName, String readerVersion) {}
interface SecretHasher { String hash(String secret); }                                          // SHA-256 hex
interface PasswordHasher { String hash(String password); boolean matches(String password, String hash); }
interface TokenGenerator { String randomToken(int bytes); }                                     // base64url sin relleno
interface IdGenerator { UUID newId(); }
```

Los casos de uso reciben además `java.time.Clock`.

**Ajustes (records del paquete `com.e2d.odin.application.shared`):**
- `InstallationSettings(String setupToken)`;
- `SessionSettings(Duration idleTimeout)`, por defecto 8 horas;
- `UploadSettings(long maxBytes)`, por defecto 52428800;
- `ProcessingSettings(Duration staleAfter, int maxAttempts)`, por defecto 15 minutos y 3;
- `CardVisibilitySettings(Set<CardStatus> searchableStatuses)`;
- `QuestionLogSettings(boolean storeText)`.

**Casos de uso:** una clase por caso, con un método público `execute(...)`, en `com.e2d.odin.application.<área>`. Las firmas están en las tareas 2 y 3.

**API REST (tarea 7):** rutas del spec 8, con un DTO `record` por petición y respuesta. Se exporta a `api/openapi.json`. La cookie de sesión se llama `odin_session`.

---

### Tarea 1: Esqueleto Java con comprobaciones de calidad · Sonnet

**Ficheros:**
- **Crear:**
  - `api/pom.xml`, `api/.mvn/maven.config` y `api/config/checkstyle.xml`;
  - los `pom.xml` de los módulos `odin-domain`, `odin-application`, `odin-adapter-postgres`, `odin-adapter-filestore`, `odin-adapter-readers`, `odin-adapter-web`, `odin-bootstrap` y `odin-architecture-tests`;
  - `Makefile`.
- **Modificar:** `.gitignore`, para añadir `api/.m2/`.
- **Prueba:** `api/odin-architecture-tests/src/test/java/com/e2d/odin/architecture/LayerRulesTest.java`.

**Interfaces que produce:**
- los módulos compilan;
- `mvn -q verify` desde `api/` ejecuta las pruebas, Checkstyle (falla con un método de más de 30 líneas), ArchUnit y el informe de JaCoCo;
- `odin-application` publica sus clases de prueba como `test-jar`, para reutilizar los dobles;
- `odin-bootstrap` tiene `OdinApplication` (Spring Boot), sin más configuración.

- [ ] **Paso 1:** escribe `LayerRulesTest` con ArchUnit sobre `com.e2d.odin`:
  - `domainDependsOnlyOnJava`;
  - `applicationDependsOnlyOnDomainAndJava`;
  - `adaptersDoNotDependOnEachOther`, para los paquetes `..adapter.postgres..`, `..adapter.filestore..`, `..adapter.readers..` y `..adapter.web..`;
  - `noSpringOutsideAdaptersAndBootstrap`.

  `odin-architecture-tests` depende de todos los módulos.
- [ ] **Paso 2:** añade una clase trampa temporal en `odin-domain` que importe `org.springframework`. Ejecuta `mvn -q verify` en `api/` y comprueba que FALLA por `domainDependsOnlyOnJava`. Borra la trampa.
- [ ] **Paso 3:** configura Checkstyle (`MethodLength` max 30, `countEmpty=false`) en la fase `verify`. Comprueba con un método temporal de 31 líneas que falla, y bórralo.
- [ ] **Paso 4:** crea el `Makefile` con `test` (`cd api && mvn -q test`) y `check` (`cd api && mvn -q verify`). `setup`, `up`, `down` y `demo` quedan como objetivos que fallan con «pendiente (tarea N)».
- [ ] **Paso 5:** `make check` pasa (salida 0).
- [ ] **Paso 6:** commit «build: esqueleto Maven de Odín con ArchUnit, Checkstyle y JaCoCo».

### Tarea 2: Núcleo de identidad y organización · Sonnet

**Ficheros:**
- **Crear:**
  - `odin-domain`: `shared/*`, `organization/*` e `identity/*` (Interfaces compartidas);
  - `odin-application`: `port/*` (todos los puertos de la sección de interfaces), `shared/*` (ajustes y `AccessPolicy`), `identity/*` y `organization/*`.
- **Pruebas:**
  - `odin-domain/src/test/...`, para `Actor` y `CompanyScope`;
  - `odin-application/src/test/.../identity/*Test.java` y `.../organization/*Test.java`;
  - dobles en `odin-application/src/test/java/com/e2d/odin/application/fake/`: `InMemoryOrganizationRepository`, `InMemoryUserRepository`, `InMemoryAccessKeyRepository`, `InMemoryAdminSessionRepository`, `FakeSecretHasher`, `FakePasswordHasher`, `SequenceTokenGenerator`, `SequenceIdGenerator` y `MutableClock`.

**Interfaces que produce:**

```java
final class AccessPolicy { static void requireGroupAdmin(Actor a); static void requireAdminOf(Actor a, CompanyId c);
  static void requireWorker(Actor a); static void requireCapability(Company c, Capability cap); }
SetUpInstallation.execute(String setupToken, String groupName, String adminName, String adminEmail, String password) -> UserId
LogInAdmin.execute(String email, String password) -> NewSession(String token, UserId userId)
LogOutAdmin.execute(String token) -> void
ResolveActor.fromAccessKey(String plainKey) -> Actor ; ResolveActor.fromSessionToken(String token) -> Actor
CreateCompany.execute(Actor a, String name) -> Company            // todas las capacidades activadas
RenameCompany.execute(Actor a, CompanyId id, String name) -> Company
SetCompanyCapabilities.execute(Actor a, CompanyId id, Set<Capability> caps) -> Company
ListCompanies.execute(Actor a) -> List<Company>
CreateProject.execute(Actor a, CompanyId id, String name) -> Project ; ListProjects.execute(Actor a, CompanyId id) -> List<Project>
RegisterUser.execute(Actor a, CompanyId id, NewUser u) -> User   // NewUser(String displayName, Optional<String> email, Role role, Optional<String> password)
DeactivateUser.execute(Actor a, UserId id) -> User ; ListUsers.execute(Actor a, CompanyId id) -> List<User>
IssueAccessKey.execute(Actor a, UserId id, String label) -> IssuedKey   // IssuedKey(AccessKeyId id, String prefix, String plainKey)
RevokeAccessKey.execute(Actor a, AccessKeyId id) -> void ; ListAccessKeys.execute(Actor a, UserId id) -> List<AccessKey>
DescribeMe.execute(Actor a) -> MeView   // MeView(UserId userId, String displayName, Role role, Company company, List<Project> projects)
```

Valores que fija el spec:
- **Clave:** `odin_<prefijo>_<secreto>`. El prefijo son 8 caracteres de `randomToken(6)` y el secreto, `randomToken(32)`. Se guarda `hash(secreto)`.
- **Sesión:** `randomToken(32)`. Se guarda `hash(token)`. Caduca tras `idleTimeout` sin uso y `fromSessionToken` actualiza `lastSeenAt`.
- **Contraseña** de 12 caracteres como mínimo. El correo es obligatorio y único para los papeles de administrador. El trabajador no tiene contraseña.
- **`COMPANY_ADMIN`** solo lo crea el administrador del grupo. Solo los usuarios `WORKER` activos pueden tener claves.
- **`SetUpInstallation`:**
  - con un `setupToken` vacío o distinto: `ForbiddenException`;
  - si ya existe el grupo: `ConflictException`.
- **`ResolveActor`:** una clave revocada, una clave desconocida, un usuario inactivo o una sesión caducada dan `UnauthorizedException`, siempre con el mismo mensaje «Credenciales no válidas».

- [ ] **Paso 1:** pruebas del dominio:
  - `ActorTest.groupAdminScopeIsAll`;
  - `ActorTest.workerScopeIsOwnCompany`;
  - `ActorTest.requireCompanyFailsForGroupAdmin`;
  - `CompanyScopeTest.includes`.

  Ejecútalas y comprueba que fallan. Implementa los records y vuelve a ejecutarlas: pasan.
- [ ] **Paso 2:** escribe las pruebas de los casos de uso, con dobles, una clase por caso. Como mínimo:
  - `SetUpInstallationTest`: `createsGroupAndFirstAdmin`, `rejectsWrongToken`, `rejectsSecondSetup` y `rejectsShortPassword`;
  - `LogInAdminTest`: `returnsTokenAndStoresOnlyHash` y `rejectsWrongPasswordWithGenericMessage`;
  - `ResolveActorTest`: `resolvesWorkerFromKey`, `rejectsRevokedKey`, `rejectsDeactivatedUserKey`, `rejectsExpiredSession` y `refreshesLastSeen` (foco de revisión 1);
  - `CreateCompanyTest`: `onlyGroupAdmin` y `enablesAllCapabilities`;
  - `RegisterUserTest`: `companyAdminCannotCreateCompanyAdmin`, `companyAdminCannotRegisterInOtherCompany`, `adminNeedsEmailAndPassword` y `duplicateEmailConflicts`;
  - `IssueAccessKeyTest`: `returnsPlainKeyOnceAndStoresHash`, `onlyForActiveWorkers` y `keyFormatIsOdinPrefixSecret`;
  - `DescribeMeTest`: `returnsCompanyCapabilitiesAndProjects`.

  Ejecútalas y comprueba que fallan.
- [ ] **Paso 3:** implementa los puertos, `AccessPolicy`, los ajustes y los casos de uso hasta que pasen todas las pruebas. Ningún método pasa de 30 líneas.
- [ ] **Paso 4:** `make check` pasa.
- [ ] **Paso 5:** commit «feat: núcleo de identidad, organización y claves personales».

### Tarea 3: Núcleo de documentos, fichas y búsqueda · Sonnet

**Ficheros:**
- **Crear:**
  - `odin-domain`: `document/*`, `knowledge/*` y `search/*`;
  - `odin-application`: `document/*`, `knowledge/*` y `search/*`.
- **Dobles:** `InMemoryDocumentRepository`, `InMemoryKnowledgeCardRepository`, `InMemoryQuestionLog`, `InMemoryOriginalFileStore` (calcula de verdad el SHA-256), `StubFormatDetector`, `StubTextReader` y `ScriptedKnowledgeSearch` (devuelve los resultados configurados y guarda la última consulta).
- **Pruebas:** `odin-application/src/test/.../{document,knowledge,search}/*Test.java` y `odin-domain/src/test/.../QuoteMatcherTest.java`.

**Interfaces que consume:** las de la tarea 2.

**Interfaces que produce:**

```java
UploadDocument.execute(Actor a, CompanyId c, Optional<ProjectId> p, String title, String filename, byte[] content) -> UploadOutcome(Document document, boolean duplicate)
ListCompanyDocuments.execute(Actor a, CompanyId c, int offset, int limit) -> List<Document>
GetDocument.execute(Actor a, DocumentId id) -> Document ; RetryDocument.execute(Actor a, DocumentId id) -> Document
ProcessPendingDocuments.processNext() -> boolean
SearchKnowledge.execute(Actor a, SearchRequest r) -> SearchResult      // SearchRequest(List<String> queries, Optional<ProjectId> projectId, Optional<Integer> limit)
ReadPage.execute(Actor a, DocumentId id, int pageNumber) -> PageView    // PageView(Document document, DocumentPage page)
ListDocuments.execute(Actor a, Optional<ProjectId> p, int offset, Optional<Integer> limit) -> List<Document>
VerifyCitation.execute(Actor a, DocumentId id, int pageNumber, String quote) -> boolean
SaveKnowledgeCard.execute(Actor a, String title, String body, Optional<ProjectId> p) -> KnowledgeCard
ListCardsForReview.execute(Actor a, CompanyId c, CardStatus s) -> List<KnowledgeCard>
ReviewCard.execute(Actor a, CardId id, ReviewDecision d, Optional<String> correctedBody, Optional<String> note) -> KnowledgeCard
```

Reglas que fija el spec (6.1, 6.2, 6.6 y 6.8):
- **Subida (`UploadDocument`):**
  - Contenido vacío: `ValidationException`. Más de `maxBytes`: `PayloadTooLargeException`. Formato no detectado: `UnsupportedFormatException`.
  - Si ya hay un documento con el mismo SHA-256 en la misma empresa, se devuelve con `duplicate=true`. Si ese documento estaba `FAILED`, se devuelve con `requeue()`.
  - Si el `projectId` no es de esa empresa: `NotFoundException`.
- **Proceso (`ProcessPendingDocuments`):**
  - Sin lector para el formato: `PENDING_READER`.
  - Un PDF sin texto en ninguna página: `PENDING_READER`.
  - Una excepción del lector: `FAILED` con su mensaje.
  - Si `attemptCount` llega a `maxAttempts` al tomar el documento: `FAILED` con «demasiados intentos».
  - Los formatos sin páginas se guardan como una página número 1 con `paginated=false`.
- **Trabajador:**
  - Las operaciones del trabajador exigen `requireWorker` y la capacidad correspondiente: `SEARCH_KNOWLEDGE` para buscar, leer, listar y verificar, y `SAVE_KNOWLEDGE_CARD` para guardar.
  - Su ámbito es siempre `actor.scope()`.
- **Límites:**
  - `SearchKnowledge`: 10 por defecto y 50 como máximo. Guarda un `QuestionRecord` (texto = consultas unidas con `" | "`, o `null` si `storeText=false`) y pasa `cardStatuses` desde `CardVisibilitySettings`.
  - `ListDocuments`: 100 por defecto y 500 como máximo. Un `limit` mayor se recorta.
- **Revisión (`ReviewCard`):**
  - Solo con una ficha `PENDING`; si no, `ConflictException`.
  - `CORRECT` exige `correctedBody`, guarda el original en `originalBody` y deja la ficha `APPROVED`.
  - `REJECT` deja la ficha `REJECTED`.

- [ ] **Paso 1:** `QuoteMatcherTest`:
  - `matchesAcrossLineBreaks`;
  - `matchesIgnoringCase`;
  - `rejectsChangedAccent` («franquicia» frente a «fránquicia»);
  - `rejectsBlankQuote` (foco de revisión 4).

  Escribe la prueba, compruébala en rojo, implementa y compruébala en verde.
- [ ] **Paso 2:** pruebas de los casos de uso, con dobles. Ejecútalas y comprueba que fallan. Como mínimo:
  - `UploadDocumentTest`: `storesOriginalAndCreatesReceived`, `duplicateInSameCompanyReturnsExisting`, `sameFileInOtherCompanyIsNotDuplicate` (foco de revisión 2), `failedDuplicateIsRequeued`, `rejectsEmptyContent`, `rejectsTooLarge`, `rejectsProjectOfOtherCompany` y `companyAdminCannotUploadToOtherCompany`;
  - `ProcessPendingDocumentsTest`: `pdfWithTextBecomesReady`, `pdfWithoutTextBecomesPendingReader`, `imageBecomesPendingReader`, `readerErrorBecomesFailed`, `thirdAttemptFails` y `nonPaginatedStoresSinglePage`;
  - `SearchKnowledgeTest`: `usesActorScopeOnly`, `passesApprovedOnlyByDefault`, `recordsQuestionWithAuthor`, `recordsNullTextWhenDisabled`, `capsLimitAt50`, `requiresSearchCapability` y `adminCannotSearch`;
  - `ReadPageTest`: `otherCompanyDocumentIsNotFound`;
  - `VerifyCitationTest`: `trueForLiteralQuote` y `falseForOtherCompanyDocument`;
  - `SaveKnowledgeCardTest`: `createsPendingCardWithAuthor` y `requiresSaveCapability`;
  - `ReviewCardTest`: `approve`, `correctKeepsOriginal`, `reject`, `cannotReviewTwice` y `companyAdminOfOtherCompanyForbidden`;
  - `ListDocumentsTest`: `capsLimitAt500`.
- [ ] **Paso 3:** implementa el dominio y los casos de uso hasta que pasen todas las pruebas.
- [ ] **Paso 4:** `make check` pasa.
- [ ] **Paso 5:** commit «feat: núcleo de documentos, fichas de conocimiento y búsqueda con cita».

### Tarea 4: Adaptador PostgreSQL: esquema, RLS y repositorios · Sonnet

**Ficheros:**
- **Crear:**
  - `db/migration/V1__identity_organization.sql`, `V2__documents_knowledge.sql` y `V4__row_level_security.sql`. La V3 es de la tarea 5;
  - `adapter/postgres/ScopedTransactions.java`, `Postgres*Repository.java` (uno por puerto de repositorio) y `PostgresQuestionLog.java`.
- **Pruebas:** `odin-adapter-postgres/src/test/java/.../*IT.java`, con Testcontainers `postgres:17.10`, y `RowLevelSecurityIT.java`.

**Interfaces que consume:** los puertos de las tareas 2 y 3.

**Interfaces que produce:**
- implementaciones de los puertos con `JdbcClient` de Spring;
- `ScopedTransactions.inScope(CompanyScope scope, Function<JdbcClient, T> work)`, que abre una transacción y ejecuta `set_config('app.company_ids', ..., true)` y `set_config('app.group_scope', allCompanies ? 'on' : 'off', true)`;
- las tablas `odin_group`, `company`, `company_capability`, `project`, `app_user`, `access_key`, `admin_session`, `document`, `document_page`, `knowledge_card` y `question_record`. El texto de la página va en `document_page.text`; el título y el texto de la ficha, en `knowledge_card.title` y `knowledge_card.body`.

Valores que fija el spec 7:
- **Roles:** las migraciones se ejecutan con el rol dueño. V4 crea el rol `odin_app`, con `LOGIN`, sin `BYPASSRLS` y con la contraseña del marcador de Flyway `${appPassword}`, y le da `SELECT`, `INSERT`, `UPDATE` y `DELETE`.
- **RLS:** `ENABLE ROW LEVEL SECURITY` en `company` (por `id`), `project`, `document`, `document_page` (por la empresa de su documento, con una columna `company_id` desnormalizada), `knowledge_card` y `question_record`.
- **Política:** `current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[])`.
- **Tablas de identidad** sin RLS.
- **`claimNextForProcessing`:** usa `FOR UPDATE SKIP LOCKED`, dentro de `inScope(CompanyScope.all())`.

- [ ] **Paso 1:** una prueba de integración por repositorio (guardar, leer y listar con su orden). Ejecútalas y comprueba que fallan.
- [ ] **Paso 2:** `RowLevelSecurityIT`, conectado como `odin_app`:
  - `noVariablesSeesNothing`;
  - `companyScopeSeesOnlyOwnDocumentsPagesCardsAndQuestions`;
  - `groupScopeSeesAll`;
  - `appRoleCannotBypassRls`, que intenta `SET row_security = off` y comprueba que sigue sin ver nada.

  Ejecútala y comprueba que falla.
- [ ] **Paso 3:** escribe las migraciones y los repositorios hasta que pasen las pruebas.
- [ ] **Paso 4:** `make check` pasa. Docker tiene que estar arrancado.
- [ ] **Paso 5:** commit «feat: persistencia en PostgreSQL con seguridad por filas».

### Tarea 5: Adaptador PostgreSQL: búsqueda con cita y batería del estudio · Sonnet

**Ficheros:**
- **Crear:**
  - `db/migration/V3__search.sql`;
  - `adapter/postgres/PostgresKnowledgeSearch.java`;
  - `src/test/resources/search-battery/`, copiando del repositorio OCR `docs/business-digital-mind/tecnico/pruebas-busqueda/` (`/Users/albercv/Documents/personalProjects/OCR/.claude/worktrees/hola-f1c11a/...`) estos ficheros: `mundo-ficticio/corpus-limpio/`, `bateria.json` y `reformulaciones.json`.
- **Pruebas:** `PostgresKnowledgeSearchIT.java` y `SearchBatteryIT.java`.

**Interfaces que consume:** `KnowledgeSearch`, `SearchQuery`, `SearchHit` y el esquema de la tarea 4.

**Lo que fija el spec 6.6, igual que `pruebas-busqueda/scripts/fts.py`:**
- **Migración:**
  - extensiones `unaccent` y `pg_trgm`;
  - `CREATE TEXT SEARCH CONFIGURATION odin_spanish (COPY = spanish)` y el mapeo de `hword, hword_part, word` a `unaccent, spanish_stem`;
  - columnas generadas `tsv` en `document_page` y `knowledge_card` (con el título y el texto), con índice GIN, más un índice trigram sobre el texto.
- **Consulta:** cada término pasa por `replace(plainto_tsquery('odin_spanish', term)::text, '&', '|')::tsquery`, y se unen con `||`.
- **Canal exacto:** los términos con dígitos y 3 caracteres o más van por `ILIKE '%term%'` y suman 100 al orden.
- **Orden:** `ts_rank_cd`.
- **Filtros:**
  - páginas solo de documentos `READY`;
  - fichas solo con estado en `cardStatuses`;
  - ámbito con `inScope(query.scope())`;
  - el proyecto, si llega.
- **Extracto:** `ts_headline` con `StartSel="", StopSel="", MaxFragments=1`. Si el resultado no es literal según `QuoteMatcher`, se recortan 300 caracteres alrededor de la primera coincidencia.

- [ ] **Paso 1:** `PostgresKnowledgeSearchIT`:
  - `findsAccentInsensitiveStem` («filtraciones» encuentra «filtración»);
  - `identifierChannelRanksFirst`;
  - `excerptIsLiteral`, que comprueba `QuoteMatcher.containsLiteral(pageText, excerpt)` en todos los resultados;
  - `otherCompanyNeverReturned`;
  - `pendingCardNotReturnedWithApprovedOnly`;
  - `notReadyDocumentNotReturned`;
  - `emptyOrStopwordQueryReturnsNoSource`, con `""`, `"¿?"` y `"de la el"` (foco de revisión 3).

  Ejecútala y comprueba que falla.
- [ ] **Paso 2:** implementa la migración V3 y `PostgresKnowledgeSearch` hasta que pase.
- [ ] **Paso 3:** `SearchBatteryIT`.
  - **Carga:** una empresa y tres proyectos, uno por comunidad: `Olmo 14` (`c1-olmo14/`), `Los Almendros` (`c2-almendros/`) y `Puerto 3` (`c3-puerto3/`).
  - **Páginas:** cada documento se parte por `^=== Página (\d+) ===\s*$`. Se escribe directamente con los repositorios, en estado `READY`.
  - **Para cada pregunta con `citas`:**
    - términos = `reformulaciones[id]`;
    - proyecto = la comunidad cuyo nombre aparece en `pregunta`, si aparece alguno;
    - `limit` 5;
    - acierto = alguna página esperada (la que contiene la frase de cada cita) sale entre las 5 primeras.
  - **Resultado:** la prueba imprime `aciertos/total` y el porcentaje, y lo compara con el umbral de `search-battery/threshold.txt`.
  - **Primera ejecución:** escribe el valor medido en ese fichero y documéntalo en el commit. No inventes un umbral.
- [ ] **Paso 4:** `make check` pasa.
- [ ] **Paso 5:** commit «feat: búsqueda con cita en PostgreSQL y batería del estudio como regresión (X/Y en el top 5)», con los valores medidos.

### Tarea 6: Adaptadores de originales y lectores · Haiku

**Ficheros:**
- **Crear:**
  - `odin-adapter-filestore/.../FileSystemOriginalFileStore.java`;
  - `odin-adapter-readers/.../MagicBytesFormatDetector.java`, `PdfBoxTextReader.java`, `PoiDocxTextReader.java` y `PlainTextReader.java`.
- **Pruebas:** junto a cada clase, con ficheros de prueba ficticios creados en la propia prueba: el PDF con PDFBox y el DOCX con POI.

**Interfaces que consume:** `OriginalFileStore`, `DocumentFormatDetector`, `DocumentTextReader`, `ReadResult` y `DocumentFormat` (tareas 2 y 3).

**Valores:**
- **Originales:** en `<raíz>/<sha[0..2]>/<sha>`. La raíz llega por el constructor (`Path`). Guardar dos veces el mismo contenido es idempotente.
- **Detección por contenido:**
  - `%PDF-` es `PDF`;
  - un ZIP con `word/document.xml` es `DOCX`;
  - las firmas PNG, JPEG o TIFF son `IMAGE`;
  - un UTF-8 válido sin bytes nulos es `PLAIN_TEXT`. Un texto que empieza por `#` o contiene `\n#` es `MARKDOWN`;
  - cualquier otra cosa, vacío.
- **Lectores:**
  - PDFBox lee página a página, con `paginated=true`;
  - DOCX y texto devuelven una página con `paginated=false`;
  - `readerName` y `readerVersion` son el nombre de la librería y su versión.

- [ ] **Paso 1:** pruebas:
  - `storeIsIdempotentAndAddressedBySha`;
  - `readReturnsSameBytes`;
  - `detectsPdfDocxImageTextMarkdown`;
  - `textWithPdfExtensionIsText`: el detector no ve el nombre del fichero (foco de revisión 5);
  - `emptyIsUndetected`;
  - `pdfReaderReturnsTextPerPage`;
  - `docxReaderReturnsSinglePage`.

  Ejecútalas y comprueba que fallan.
- [ ] **Paso 2:** implementa las clases hasta que pasen.
- [ ] **Paso 3:** `make check` pasa.
- [ ] **Paso 4:** commit «feat: almacén de originales y lectores de PDF, DOCX y texto».

### Tarea 7: Adaptador web: REST, autenticación y errores · Sonnet

**Ficheros:**
- **Crear:** en `odin-adapter-web/src/main/java/com/e2d/odin/adapter/web/`:
  - `setup/SetupController`;
  - `admin/*Controller`, para la sesión, las empresas, los proyectos, los usuarios, las claves, los documentos y las fichas;
  - `worker/*Controller`, para `me`, `search`, `documents`, `citations` y `cards`;
  - `security/ActorResolutionFilter`, `OriginCheckFilter`, `LoginRateLimiter` y `CurrentActor`;
  - `error/ProblemDetailsHandler`;
  - `dto/*` (records).
- **Pruebas:** `odin-adapter-web/src/test/...`, con `@WebMvcTest` y los casos de uso reales construidos sobre los dobles del `test-jar` de `odin-application`.

**Interfaces que consume:** los casos de uso de las tareas 2 y 3.

**Interfaces que produce:**
- las rutas del spec 8;
- la respuesta de búsqueda `{ hits: [...], noSource: boolean }`, con los campos de `SearchHit` en camelCase;
- `POST /v1/admin/session` fija la cookie `odin_session` (`HttpOnly`, `SameSite=Strict`, `Path=/`);
- `POST /v1/admin/users/{userId}/keys` devuelve `{ id, prefix, key }`, la única vez que se ve la clave;
- `POST /v1/cards` responde 201 con `{ id, status: "PENDING", message: "Ficha guardada; queda pendiente de revisión." }`.

**Valores:**
- **`ActorResolutionFilter`:**
  - `Authorization: Bearer` se resuelve con `fromAccessKey` y solo vale para las rutas del trabajador (`/v1/me`, `/v1/search`, `/v1/documents/**`, `/v1/citations/**` y `/v1/cards`);
  - la cookie se resuelve con `fromSessionToken` y solo vale para `/v1/admin/**`;
  - `/v1/setup`, `/v1/health`, `POST /v1/admin/session` y la documentación son públicas.
- **`OriginCheckFilter`:** en `/v1/admin/**` y `/v1/setup` con método distinto de GET, la cabecera `Origin`, si llega, tiene que ser igual a `odin.panel-origin`; si no, 403.
- **`LoginRateLimiter`:** 5 intentos por minuto por correo y por IP, en memoria; después, 429.
- **Errores:** `NotFound` 404, `Forbidden` 403, `Unauthorized` 401, `Validation` 400, `Conflict` 409, `UnsupportedFormat` 415 y `PayloadTooLarge` 413. Un 403 por capacidad desactivada dice «Tu empresa no tiene activada esta función».
- **Nunca se registra en los logs:** el cuerpo de las peticiones, cabeceras `Authorization` ni cookies.
- **springdoc:** `springdoc.api-docs.path=/v1/openapi.json` y la interfaz en `/v1/docs`.

- [ ] **Paso 1:** pruebas web. Ejecútalas y comprueba que fallan. Como mínimo:
  - `workerKeyCannotCallAdmin` y `sessionCannotCallWorkerRoutes`;
  - `missingCredentialsIs401`;
  - `foreignOriginIs403`;
  - `sixthLoginAttemptIs429`;
  - `searchReturnsHitsAndNoSource`;
  - `uploadMultipartReturns201`, `uploadTooLargeIs413` y `unknownFormatIs415`;
  - `keyShownOnce`;
  - `disabledCapabilityIs403WithMessage`;
  - `problemJsonShape`;
  - `cookieFlags`.
- [ ] **Paso 2:** implementa controladores, filtros y DTO hasta que pasen.
- [ ] **Paso 3:** `make check` pasa.
- [ ] **Paso 4:** commit «feat: API REST con sesión del panel, claves personales y errores RFC 9457».

### Tarea 8: Arranque, integración y contrato OpenAPI · Sonnet

**Ficheros:**
- **Crear:**
  - en `odin-bootstrap/src/main/java/com/e2d/odin/bootstrap/`: `OdinApplication`, `UseCaseConfiguration`, `AdapterConfiguration`, `SettingsConfiguration` y `DocumentProcessingScheduler`;
  - `odin-bootstrap/src/main/resources/application.yaml`;
  - `api/openapi.json`.
- **Pruebas:** `odin-bootstrap/src/test/.../ApplicationFlowIT.java` y `OpenApiContractIT.java`.

**Interfaces que consume:** todo lo anterior.

**Interfaces que produce:**
- la aplicación arranca solo con variables de entorno: `ODIN_DB_URL`, `ODIN_DB_APP_USER`, `ODIN_DB_APP_PASSWORD`, `ODIN_DB_OWNER_USER`, `ODIN_DB_OWNER_PASSWORD`, `ODIN_SETUP_TOKEN`, `ODIN_PANEL_ORIGIN`, `ODIN_ORIGINALS_DIR` y `ODIN_UPLOAD_MAX_BYTES`;
- Flyway se ejecuta con el rol dueño y la aplicación con `odin_app`;
- `api/openapi.json` versionado.

**Valores:**
- **`DocumentProcessingScheduler`:** llama a `processNext()` cada 2 s mientras devuelva `true`.
- **Puerto:** 8080 dentro del contenedor.

- [ ] **Paso 1:** `ApplicationFlowIT`, con `@SpringBootTest` y Testcontainers. Ejecútala y comprueba que falla. Recorre:
  - setup, sesión, empresas A y B;
  - un trabajador en cada una y sus claves;
  - un TXT ficticio subido a A, en espera de `READY`;
  - búsqueda de A con resultado y cita literal;
  - búsqueda de B con `noSource`;
  - ficha de A en `PENDING` y su aprobación.
- [ ] **Paso 2:** escribe el cableado hasta que pase.
- [ ] **Paso 3:** `OpenApiContractIT` compara `GET /v1/openapi.json` (JSON normalizado) con `api/openapi.json`. Con `-Dodin.updateOpenApi=true`, reescribe el fichero. Genéralo una vez, revísalo y comprueba que la prueba pasa.
- [ ] **Paso 4:** `make check` pasa.
- [ ] **Paso 5:** commit «feat: arranque de la API, integración de principio a fin y contrato OpenAPI».

### Tarea 9: Servidor MCP · Sonnet

**Ficheros:**
- **Crear:** `mcp/package.json`, `mcp/tsconfig.json`, `mcp/eslint.config.js` y, dentro de `mcp/src/`:
  - `api/types.ts` (generado con `openapi-typescript ../api/openapi.json`);
  - `api/client.ts`;
  - `tools/{searchKnowledge,readPage,listDocuments,verifyCitation,saveKnowledgeCard}.ts`;
  - `server.ts`, `stdio.ts` y `http.ts`.
- **Pruebas:** `mcp/test/*.test.ts` (Vitest), contra un servidor HTTP falso en un puerto local.

**Interfaces que consume:** `api/openapi.json` y las rutas del trabajador.

**Interfaces que produce:**
- `buildServer(apiClient): Promise<McpServer>`, que registra solo las herramientas de las capacidades de `GET /v1/me`;
- `npm run start:stdio`, que lee `ODIN_API_URL` y `ODIN_ACCESS_KEY`;
- `npm run start:http`, que lee `ODIN_API_URL` y `MCP_PORT` (por defecto 8090) y escucha en `127.0.0.1`. Toma la clave de la cabecera `Authorization` de cada sesión.

**Valores (spec 9):**
- **Herramientas:** `searchKnowledge(queries: string[], projectId?, limit?)`, `readPage(documentId, pageNumber)`, `listDocuments(projectId?)`, `verifyCitation(documentId, pageNumber, quote)` y `saveKnowledgeCard(title, body, projectId?)`.
- **Sin resultados:** el texto es «No consta en los documentos de tu empresa.».
- **`listDocuments`:** pide páginas de 500 hasta el final, con un tope de 2.000 y `truncated`.
- **`instructions`:**
  - citar el documento y la página de toda afirmación sobre el trabajo de la empresa;
  - si no hay resultados, decir «no consta»;
  - comprobar las citas con `verifyCitation`;
  - presentar el conocimiento general aparte, como información añadida.

- [ ] **Paso 1:** pruebas. Ejecútalas y comprueba que fallan:
  - `registersOnlyEnabledCapabilities`;
  - `searchMapsHitsWithCitation`;
  - `noSourceMessage`;
  - `listDocumentsPagesUntilEnd`;
  - `apiErrorBecomesToolError`;
  - `httpTransportForwardsBearerPerSession`;
  - `revokedKeyMidSessionReturnsError` (foco de revisión 1).
- [ ] **Paso 2:** implementa hasta que pasen. Añade `mcp` a `make test` y a `make check` (`npm test` y `npm run lint` con `max-lines-per-function` 30).
- [ ] **Paso 3:** `make check` pasa.
- [ ] **Paso 4:** commit «feat: servidor MCP con herramientas según las capacidades de la empresa».

### Tarea 10: Panel del administrador · Sonnet

**Ficheros:**
- **Crear:** `panel/package.json`, `panel/vite.config.ts`, `panel/index.html` y, dentro de `panel/src/`:
  - `api/types.ts` (generado) y `api/client.ts`;
  - `pages/{Login,Companies,Company,Documents,Review}.tsx`;
  - `components/*`.
- **Pruebas:** `panel/src/**/*.test.tsx` (Vitest y Testing Library).

**Interfaces que consume:** `api/openapi.json` y las rutas `/v1/admin/**`, siempre del mismo origen. En desarrollo, el proxy de Vite reenvía `/v1` a `http://127.0.0.1:8080`.

**Valores (spec 10):**
- **Textos** en español.
- **Clave nueva:** se muestra una sola vez, con botón «Copiar» y el aviso «No se volverá a mostrar».
- **Administrador de empresa:** entra directamente en su empresa y no ve los controles de capacidades ni de alta de empresas.
- **Accesibilidad:** etiqueta en todos los campos y uso con teclado.

- [ ] **Paso 1:** pruebas. Ejecútalas y comprueba que fallan:
  - `loginShowsErrorOnBadCredentials`;
  - `groupAdminSeesCompaniesAndCapabilities`;
  - `companyAdminGoesToOwnCompanyWithoutCapabilityControls`;
  - `issuedKeyShownOnceWithCopy`;
  - `documentsListShowsStatusAndReason`;
  - `reviewApproveCorrectReject`.
- [ ] **Paso 2:** implementa hasta que pasen. Añade `panel` a `make test` y a `make check`.
- [ ] **Paso 3:** `make check` pasa y `npm run build` genera `panel/dist`.
- [ ] **Paso 4:** commit «feat: panel del administrador (empresas, usuarios, claves, documentos y revisión)».

### Tarea 11: Despliegue con Docker Compose · Sonnet

**Ficheros:**
- **Crear:**
  - `deploy/compose.yaml`;
  - `deploy/api.Dockerfile`, `deploy/mcp.Dockerfile` y `deploy/panel.Dockerfile`;
  - `deploy/nginx.conf` y `deploy/.env.example`.
- **Modificar:** los objetivos `setup`, `up` y `down` del `Makefile`.

**Valores (spec 11):**
- **Servicios:**
  - `postgres` (`postgres:17.10`), con `POSTGRES_USER` como rol dueño y el volumen `pgdata`;
  - `api`, construida con `maven:3.9-eclipse-temurin-21` y servida con `eclipse-temurin:21-jre`, con el volumen `originals` en `/var/lib/odin/originals`;
  - `panel`, construido con `node:24` y servido con `nginx`, que reenvía `/v1` a `api:8080`;
  - `mcp-http`.
- **Puertos:**
  - panel: `127.0.0.1:${ODIN_PANEL_PORT:-8088}:80`;
  - API: `127.0.0.1:${ODIN_API_PORT:-8080}:8080`;
  - MCP: `127.0.0.1:${ODIN_MCP_PORT:-8090}:8090`.
- **Configuración:** `.env.example` con todas las variables de la tarea 8 y valores de ejemplo marcados como tales. Ninguna ruta del equipo.
- **Comprobaciones:** un `healthcheck` en `postgres` y en `api` (`/v1/health`).

- [ ] **Paso 1:** `make up` construye y arranca la pila, y `curl -fsS http://127.0.0.1:8080/v1/health` devuelve 200.
- [ ] **Paso 2:** el panel responde en `http://127.0.0.1:8088` y `/v1/health` también responde a través de él.
- [ ] **Paso 3:** `make down` la para y conserva los volúmenes.
- [ ] **Paso 4:** commit «build: despliegue con Docker Compose de la API, el panel y el MCP».

### Tarea 12: Demostración de cierre con `make demo` · Sonnet

**Ficheros:**
- **Crear:**
  - `deploy/demo/poliza-ficticia.pdf`, un PDF con texto generado a partir de `corpus-limpio/c2-almendros/poliza-seguro-2024.txt` y sus páginas;
  - `deploy/demo/demo.ts` (cliente de la API y cliente MCP del SDK, por stdio contra `mcp`);
  - `panel/e2e/demo.spec.ts` (Playwright).
- **Modificar:** el objetivo `demo` del `Makefile`, que arranca la pila con un `.env` temporal, ejecuta la demostración y la para.

**Pasos de la demostración** (spec 3.1, con las comprobaciones como aserciones; termina con un código de salida distinto de 0 si alguna falla):
1. Setup.
2. Sesión del administrador del grupo.
3. Empresas A y B, un trabajador en cada una y sus claves.
4. Subir el PDF a A y esperar a `READY`.
5. Cliente MCP con la clave de A: `searchKnowledge(["franquicia daños por agua ATL-4417-2024"])`. Tiene que devolver un resultado del PDF, con la página y un extracto que `verifyCitation` da por literal.
6. Cliente MCP con la clave de B: la misma consulta devuelve «No consta en los documentos de tu empresa.».
7. Cliente MCP de A: `saveKnowledgeCard` devuelve `PENDING`.
8. Playwright: el administrador entra en el panel, ve la ficha en Revisión y la aprueba.
9. La API confirma que la ficha está `APPROVED`.

- [ ] **Paso 1:** escribe `demo.ts` y `demo.spec.ts`, ejecuta `make demo` y comprueba que FALLA en el primer paso que no esté listo.
- [ ] **Paso 2:** completa lo que falte hasta que `make demo` termine con código de salida 0. Guarda la salida.
- [ ] **Paso 3:** commit «test: demostración de cierre de la fase 1 de principio a fin».

---

## Orden y paralelismo

```
1 → 2 → 3 ─┬─ 4 → 5 ─┐
           ├─ 6 ─────┼─ 8 ─┬─ 9 ──┐
           └─ 7 ─────┘     └─ 10 ─┴─ 11 → 12
```

- Las tareas 4, 6 y 7 pueden ir en paralelo, cada una en su propio worktree de git del repositorio de Odín. Lo mismo las tareas 9 y 10.
- Cada tarea la revisa un subagente revisor antes del merge.
- Al final, una revisión de toda la rama.
