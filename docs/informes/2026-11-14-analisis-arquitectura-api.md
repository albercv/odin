# Análisis de la arquitectura de la API de Odín (encargo 18)

Fecha: 2026-11-14. Código analizado: rama `task/analisis-arquitectura/informe` (= `integracion/instalacion`, cbe4989), carpeta `api/`.
Solo análisis: no se ha cambiado código. Todo lo que sigue sale de ficheros leídos; lo que no se pudo comprobar está en el último apartado.

## Resumen para el propietario

- «Hexagonal» quiere decir: el corazón del programa (las reglas del negocio) no sabe nada de la base de datos, de la web ni de ningún programa externo; todo eso se enchufa desde fuera mediante «enchufes» (interfaces) que el corazón define.
- Veredicto: **sí, es hexagonal de verdad**, y bastante limpio. El README no exagera. Hay una desviación de estilo (las reglas viven en los casos de uso, no en los objetos del dominio), no de estructura.
- Lo más fuerte: la separación está impuesta por Maven (cada módulo solo puede ver a quien declara en su `pom.xml`) y por 4 reglas automáticas (ArchUnit). El núcleo (`odin-domain` y `odin-application`) solo importa clases de Java.
- Segunda fortaleza: el aislamiento entre empresas va en dos capas (reglas RLS en PostgreSQL y filtro en el propio SQL), y la API se niega a arrancar si su usuario de base de datos se saltaría esas reglas.
- Lo más arriesgado: las transacciones. Cada llamada a un repositorio abre su propia transacción; que un caso de uso sea «todo o nada» depende de envolverlo a mano en `odin-bootstrap` (7 casos de uso lo hacen, el resto no). Además hay 5 tablas sin RLS (identidad) y las reglas ArchUnit no vigilan `odin-bootstrap` ni lo que puede importar cada adaptador de la capa de aplicación.
- Las tres funcionalidades en curso no cambian el patrón: añaden casos de uso, un puerto (`IntakeRepository`) y migraciones (V25, V26) con el mismo estilo. `primera-carga` es la que más toca (nueva RLS, nuevo filtro SQL, primer objeto de dominio con una máquina de estados).

---

## 1. ¿Es hexagonal?

### 1.1 Dependencias entre módulos Maven

`api/pom.xml` lista 10 módulos (líneas 11-22). Dependencias declaradas en cada `pom.xml` (solo las de compilación entre módulos y los frameworks vistos):

| Módulo | Depende de (Odín) | Frameworks / librerías en el pom |
|---|---|---|
| `odin-domain` | nada | ninguna (solo el padre) |
| `odin-application` | `odin-domain` | ninguna (+ `maven-jar-plugin` para exportar un test-jar con los fakes) |
| `odin-adapter-postgres` | `odin-application` | spring-jdbc, flyway-core, flyway-database-postgresql, postgresql |
| `odin-adapter-web` | `odin-application` | spring-boot-starter-webmvc, springdoc-openapi |
| `odin-adapter-filestore` | `odin-application` | ninguna |
| `odin-adapter-readers` | `odin-application` | pdfbox, poi-ooxml |
| `odin-adapter-reader-service` | `odin-application` | jackson-databind |
| `odin-adapter-tagger-ollama` | `odin-application` | jackson-databind |
| `odin-bootstrap` | los 6 adaptadores (postgres, filestore, readers, reader-service, tagger-ollama, web) | spring-boot-starter-jdbc, starter-flyway, spring-security-crypto, spring-boot-maven-plugin |
| `odin-architecture-tests` | todos | archunit (test) |

Hechos relevantes:
- Ningún adaptador depende de otro adaptador (ver los `pom.xml`). Solo `odin-bootstrap` los ve todos.
- `odin-domain` no tiene ninguna dependencia, ni siquiera de Spring.
- `odin-adapter-web` no declara JDBC ni `odin-adapter-postgres`: por construcción no puede tocar SQL.

### 1.2 ¿Importan frameworks el dominio y la aplicación?

Comprobado con `grep` de imports y de anotaciones (`@Service`, `@Component`, `@Transactional`, `@Autowired`, `@Repository`, `springframework`, `jackson`, `jakarta`, `java.sql`, `javax`):

- `odin-domain` (64 ficheros main): solo `com.e2d.odin.*`, `java.util`, `java.time`, `java.text`, `java.util.regex`. Cero coincidencias de anotaciones o frameworks.
- `odin-application` (190 ficheros main): solo `com.e2d.odin.*`, `java.util*`, `java.time*`, `java.nio.charset`, `java.util.Base64` y `java.security.MessageDigest`. Cero coincidencias de anotaciones o frameworks. Detalles de JDK: `Base64` en `casefile/ReadCaseFile.java:16` (cursor de paginación), `MessageDigest` en `shared/SecretComparison.java:4`. El registro usa `System.Logger` de Java (`document/ProcessPendingDocuments.java:20`), no SLF4J.
- No hay `@Transactional` en ningún sitio del código principal (grep sobre todo `api/`).

### 1.3 Dónde viven los puertos y quién los implementa

- Los puertos están en `odin-application`, paquete `com.e2d.odin.application.port`: 37 ficheros, 27 son `interface` (ver `grep "interface "`). Ejemplos: `DocumentRepository`, `UserRepository`, `OriginalFileStore`, `ReaderService`, `TopicTagger`, `IdGenerator`, `PasswordHasher`, `TokenGenerator`, `SecretHasher`. Los demás son excepciones de puerto y tipos de resultado (`ReadResult`, `TaggerAnswer`).
- Quién los implementa (todo `class X implements Puerto`):
  - `odin-adapter-postgres`: 16 clases `Postgres*` (`PostgresDocumentRepository implements DocumentRepository`, etc.).
  - `odin-adapter-filestore`: `FileSystemOriginalFileStore implements OriginalFileStore`.
  - `odin-adapter-readers`: `PdfBoxTextReader`, `PoiDocxTextReader`, `PlainTextReader` (`DocumentTextReader`) y `MagicBytesFormatDetector`.
  - `odin-adapter-reader-service`: `HttpReaderService implements ReaderService`.
  - `odin-adapter-tagger-ollama`: `OllamaTopicTagger implements TopicTagger`.
  - `odin-bootstrap`: los puertos técnicos pequeños: `BcryptPasswordHasher`, `Sha256SecretHasher`, `RandomUuidGenerator`, `SecureRandomTokenGenerator`, `SecureRandomPersonCodeGenerator`.
- El lado de entrada (driving) no tiene interfaz: los controladores web inyectan directamente las clases de caso de uso (por ejemplo `worker/WorkerController.java` recibe `SearchKnowledge`, `ReadPage`, `VerifyCitation`…). Es habitual en hexagonal: el puerto de entrada es la propia clase del caso de uso.

### 1.4 Qué hacen realmente las reglas ArchUnit

Hay un único fichero: `odin-architecture-tests/src/test/java/com/e2d/odin/architecture/LayerRulesTest.java` (47 líneas, 4 reglas). Importa `com.e2d.odin` completo con `ClassFileImporter`. No hay `ignoreDependency`, ni `FreezingArchRule`, ni `.allowEmptyShould`: no hay excepciones ni violaciones congeladas.

| Regla (método) | Qué exige |
|---|---|
| `domainDependsOnlyOnJava` | Clases de `domain..` solo dependen de `domain..` y `java..` |
| `applicationDependsOnlyOnDomainAndJava` | Clases de `application..` solo dependen de `application..`, `domain..` y `java..` |
| `adaptersDoNotDependOnEachOther` | Rebanadas `adapter.(*)..` sin dependencias entre sí |
| `noSpringOutsideAdaptersAndBootstrap` | Nada fuera de `adapter..` y `bootstrap..` depende de `org.springframework..` |

Lo que cubren bien: las reglas 1 y 2 son «lista blanca», más fuertes que una lista negra (cualquier librería nueva en el núcleo rompería la regla, no solo Spring). La regla 4 queda redundante con las 1 y 2 hoy.

Lo que se les escapa (hechos, sin valoración):
- **No hay regla sobre `bootstrap`**: puede importar cualquier cosa. Es lo esperado (ahí se cablea todo), pero nada impide meter lógica allí; de hecho ya contiene las 7 envolturas de transacción (ver 3.5).
- **Los adaptadores pueden importar cualquier clase de `application..`, no solo `application.port`**. Ninguna regla lo prohíbe. En la práctica: `odin-adapter-web` importa 120 clases de `application` que no son puertos (los casos de uso, es lo esperado); `odin-adapter-postgres` importa tipos de `application.usage` (informes) para devolverlos, y los puertos `DeliveryMeter`, `UsageStatistics`, `TaggingRepository` importan tipos de `application.usage/topic/shared`.
- **Ninguna regla vigila ciclos entre paquetes** de `application` (`casefile`, `document`, `identity`, `link`, `organization`, `search`, `topic`, `usage`, `knowledge`, `shared`, `port`). No hay `slices()` sobre ellos.
- **Ninguna regla de nombres o de ubicación** (por ejemplo, que las interfaces estén en `port`, o que el web no use `Postgres*`). Esto último ya lo impide Maven, no ArchUnit.
- Los adaptadores no tienen límites de librería por ArchUnit (por ejemplo, que `adapter.web` no use `javax.sql`); lo hace el `pom.xml` (1.1), así que el riesgo es bajo, pero dependería de que nadie añada la dependencia.
- La regla 3 solo ve `adapter.<nombre>..`; `bootstrap` y `adapter.web` internos no se separan por subpaquetes.

Observación: `odin-architecture-tests` importa `com.e2d.odin` desde las clases compiladas de los 9 módulos, así que sí ve todo el código principal; no ve los tests de cada módulo (no declara sus test-jars).

---

## 2. Qué es realmente y dónde se desvía

Es una **arquitectura hexagonal (puertos y adaptadores) con aire de «clean»**: capas concéntricas (dominio → aplicación → adaptadores → arranque), dependencias solo hacia dentro, un caso de uso por clase. No es solo «capas» (la base de datos no está debajo de la lógica: está detrás de interfaces que la lógica define).

Desviaciones concretas, todas menores:

1. **Dominio «anémico»**. Los objetos del dominio son sobre todo `record` de datos: 39 de 64 ficheros de `odin-domain` son `record` (`grep "^public record"`). `Document` es un `record` de 20 campos (`domain/document/Document.java`), con pocos métodos. Las reglas (permisos, duplicados, estados) están en los casos de uso y en `application.shared` (`AccessPolicy`, `WorkerAccess`, `Validation`). En la rama `primera-carga` aparece el primer objeto con comportamiento real (`IntakeBatch`, ver 5).
2. **Los puertos arrastran tipos de la aplicación**. `port/DeliveryMeter`, `UsageStatistics` y `TaggingRepository` devuelven o reciben tipos de `application.usage`/`topic`. El paquete `port` no es puro respecto al resto de `application`.
3. **Un puerto expone una operación de transacción**: `DocumentRepository.withDocumentsLocked(companyId, first, second, Supplier<T> work)` (`port/DocumentRepository.java:48`; implementación en `PostgresDocumentRepository.java:120`, con `SELECT ... FOR UPDATE`). El concepto «bloqueo de filas» aparece en la interfaz del núcleo. Lo usa solo `document/SupersedeDocument.java:37`.
4. **Los controladores usan tipos del dominio directamente en las respuestas HTTP**. `adapter/web/dto/Responses.java` (574 líneas) importa decenas de clases de `domain.*` y de `application.*` y las convierte en `record` de respuesta con `from(...)`. El contrato HTTP no es independiente del dominio: lo protege el test de contrato OpenAPI (3.8).
5. **Configuración y transacciones por caso de uso en `odin-bootstrap`**, no en el núcleo (ver 3.5). Es coherente con hexagonal, pero la lógica de «qué es atómico» queda fuera del núcleo y sin vigilancia de ArchUnit.
6. **El adaptador web también contiene seguridad de acceso** (filtros, límite de intentos, red local). Es habitual, pero `AccessPolicy`/`WorkerAccess` (permisos de negocio) sí están en `application`: hay dos sitios para «quién puede hacer qué» (ver 3.7).

---

## 3. Patrones de código

### 3.1 Casos de uso

- **Una clase por caso de uso**, con un método público `execute(...)` (hay sobrecargas: `AddToCaseFiles.execute(actor, assignments)` y `execute(actor, companyId, assignments)`, `casefile/AddToCaseFiles.java`). 70 ficheros declaran `public ... execute(`. Algunos casos de uso tienen otros métodos con nombre (`ListCardsForReview.page` en la rama panel-listas, `IssueUploadLink.forProject` en subir-desde-chat).
- Se agrupan por paquete de negocio dentro de `application`: `casefile` (12), `document` (13), `identity` (31), `link` (13), `organization` (20), `topic` (19), `usage` (18), `search` (10), `knowledge` (4), `shared` (13), `port` (37).
- Reciben el `Actor` como primer parámetro y se construyen con inyección por constructor de puertos + `Clock` + `IdGenerator`. Sin anotaciones. El reloj (`java.time.Clock`) y los ids (`IdGenerator`) son inyectados: nada de `Instant.now()` o `UUID.randomUUID()` en el núcleo (se ve en `AddToCaseFiles`).
- Hay clases de ayuda estáticas, no instanciables: `AccessPolicy`, `WorkerAccess`, `Validation`, `SecretComparison` (constructor privado).

### 3.2 Puertos, adaptadores y repositorios

- Repositorios: interfaces `XxxRepository` con métodos de negocio (`DocumentRepository.findById(CompanyScope, ProjectVisibility, DocumentId)`), no CRUD genérico. Casi todos los métodos reciben el alcance de empresa y de proyectos visibles como parámetros.
- Adaptadores Postgres: `JdbcClient` de Spring con SQL escrito a mano (texto en bloques `"""`), sin ORM ni JPA. Cada repositorio recibe `ScopedTransactions tx`.
- Adaptadores de lectura de documentos (`readers`, `reader-service`) y etiquetador (`tagger-ollama`): una clase por puerto; los dos últimos hablan HTTP con el JDK y Jackson para JSON.
- Los puertos definen sus propias excepciones (`ReaderUnavailableException`, `ReaderFailedException`, `UnreadableDocumentException`, `DocumentTooLargeForReaderException`, `TaggerFailedException`, `TaggerUnavailableException`): todas `extends RuntimeException`, no `DomainException`.

### 3.3 Objetos de valor y registros

- Identificadores tipados como `record` con un `UUID`: `CompanyId`, `DocumentId`, `ProjectId`, `UserId`, `GroupId`, `AccessKeyId`, `CardId`, `TopicId` (en `domain/shared`, ej. `CompanyId(UUID value)` con `of(String)`).
- Otros valores: `CompanyScope` (qué empresas ve alguien) y `ProjectVisibility` (qué proyectos ve) en `domain/shared`; ambos viajan por todos los repositorios.
- Casi todo el dominio y los resultados de aplicación son `record` (también `Optional<...>` en campos de `record`, ej. `Document.projectId`).

### 3.4 DTO y mapeos

- Entrada y salida HTTP en dos ficheros: `adapter/web/dto/Requests.java` (128 líneas) y `Responses.java` (574 líneas), con `record` anidados. El mapeo es manual con métodos estáticos `from(...)` (ej. `Responses.IssuedLink.from(...)`), sin MapStruct ni similares.
- `WebConfig.requiredResponseFields` (`WebConfig.java`) recorre por reflexión los `record` de `Responses` y marca como obligatorios en OpenAPI los campos sin `@Nullable`.
- Los casos de uso también tienen sus propios «views» (`CaseFileView`, `PageView`, `MeView`, `UsageReport`…) que los controladores convierten a `Responses`.

### 3.5 Inyección de dependencias, configuración y transacciones

- Los casos de uso **no son beans por anotación**. Se declaran uno a uno con `@Bean` en `odin-bootstrap/.../UseCaseConfiguration.java` (641 líneas, 76 `@Bean`); `AdapterConfiguration` tiene 27 `@Bean` (repositorios y adaptadores); además `SettingsConfiguration`, `ReaderLaneConfiguration`, `TaggingConfiguration`. Ejemplo: `new RemoveMember(users, memberships, keys, projectMembers, sessions, clock)`.
- `OdinApplication` usa `@SpringBootApplication(scanBasePackages = {"com.e2d.odin.bootstrap", "com.e2d.odin.adapter.web"})`: solo se escanean bootstrap y web. El adaptador web sí usa anotaciones de Spring (`@RestController`, `@Component`, `@RestControllerAdvice`).
- `ScopedTransactions` (`odin-adapter-postgres/.../ScopedTransactions.java`): cada llamada `inScope(scope, visibility, jdbc -> ...)` abre una transacción (`TransactionTemplate` sobre `DataSourceTransactionManager`), ejecuta `set_config('app.company_ids', ..., true)`, `app.group_scope`, `app.include_withdrawn` y, si procede, `app.project_ids`/`app.all_projects`, y luego el trabajo. También traduce `DuplicateKeyException` a `ConflictException("Ya existe un registro con esos datos")`.
- **Frontera de transacción = una llamada a repositorio**, por defecto. Un caso de uso que llama a varios repositorios no es atómico salvo que se envuelva. Se envuelven a mano, en `UseCaseConfiguration`, con una subclase anónima que añade un `TransactionTemplate` (líneas 293 `RemoveMember`, 316 `RevokeGroupAdmin`, 328 `UpdateUser`, 353 (un `execute(actor, id)` de usuario, sin identificar), 366 `AddMember`, 379 `UpdateMember`, 631 `SetUpInstallation`). Hay tests de integración de atomicidad para algunos (`MemberAtomicityIT`, `SetUpAtomicityIT`, `LastGroupAdminIT`).
- Esto exige que esas clases de caso de uso no sean `final` y que el `PlatformTransactionManager` de bootstrap y el `DataSource` de `ScopedTransactions` sean el mismo recurso; no lo he verificado ejecutando (ver «No comprobado»).
- Un caso de uso con bloqueo explícito: `SupersedeDocument` usa `documents.withDocumentsLocked(...)`, que abre su propia transacción con `FOR UPDATE` (ordenando los ids para evitar interbloqueos, `PostgresDocumentRepository.java:120-130`).

### 3.6 Gestión de errores y camino hasta HTTP

- Jerarquía en `domain/shared`: `DomainException` (abstracta, `extends RuntimeException`) con `NotFoundException`, `ForbiddenException`, `UnauthorizedException`, `ValidationException`, `ConflictException`, `UnsupportedFormatException`, `PayloadTooLargeException`. Los mensajes son en español y se muestran al cliente.
- `adapter/web/error/ProblemDetailsHandler` (`@RestControllerAdvice`, RFC 9457): `DomainException` → estado con un `switch` (404, 403, 401, 400, 409, 415, 413; cualquier otra subclase → 500); `TooManyAttemptsException` → 429; `Exception` → 500 con «Error interno» y registro del error. Extiende `ResponseEntityExceptionHandler` para los errores de Spring MVC.
- Los filtros usan la misma salida: `security/GuardFilter` captura `DomainException` y la pasa a `HandlerExceptionResolver`, así el rechazo de un filtro tiene el mismo formato que el de un controlador.
- Las excepciones de puerto (lector, etiquetador) no son `DomainException`: el único sitio que las trata que he visto es `document/ProcessDocumentsWithReader.java`/`ProcessPendingDocuments` (procesos en segundo plano, no HTTP). Si una escapara a un controlador, el manejador general la convertiría en 500 (no he buscado ningún caso así).

### 3.7 Aislamiento entre empresas y seguridad

**Base de datos (RLS)**
- `V4__row_level_security.sql`: crea el rol `odin_app` con `NOBYPASSRLS`, concede permisos y activa RLS con una política por tabla basada en `current_setting('app.group_scope')` y `current_setting('app.company_ids')`. `V9` añade RLS por proyecto; `V7`, `V13`, `V23`, `V24` más políticas para tablas nuevas.
- Tablas creadas en migraciones: 20. Con RLS activado: 15. **Sin RLS: `access_key`, `admin_session`, `app_user`, `company_capability`, `odin_group`** (comprobado comparando `CREATE TABLE` con `ENABLE ROW LEVEL SECURITY` en `db/migration/*.sql`). No hay `FORCE ROW LEVEL SECURITY` en ninguna migración: el dueño de la tabla se salta la RLS, por eso existe el guarda siguiente.
- `odin-bootstrap/.../DatabaseRoleGuard`: al arrancar, `AdapterConfiguration.scopedTransactions` lo llama y lanza `IllegalStateException` si el rol es superusuario, tiene `BYPASSRLS` o es dueño de la tabla `document`. Solo comprueba `document`, no el resto.
- Dos conexiones distintas: `application.yaml` usa `ODIN_DB_APP_USER` para la API y `ODIN_DB_OWNER_USER` para Flyway (migraciones); `V4` crea `odin_app` con la contraseña por placeholder Flyway (`${appPassword}`).
- Tests de RLS en `odin-adapter-postgres/src/test`: `RowLevelSecurityIT`, `CaseFileRowLevelSecurityIT`, `OwnerConnectionIsolationIT`; en bootstrap: `DatabaseRoleGuardIT`, `CaseFileIsolationIT`.
- Migraciones: V1-V13 y V20-V24 (no existen V14-V19; 18 ficheros en total). No he investigado la causa.

**SQL (segunda barrera)** en `odin-adapter-postgres/.../Sql.java`:
- `inScope(columna)`: `(:allCompanies OR columna = ANY(...:companyIds))` — «segunda barrera si la conexión llegara a saltarse la RLS» (comentario en el código).
- `inVisibleProjects(columna)`: proyectos visibles; lo que no tiene proyecto lo ve toda la empresa.
- `notWithdrawn(columna)`: oculta lo retirado salvo que la sesión active `app.include_withdrawn` (solo la vista del administrador, vía `ScopedTransactions.includeWithdrawn(true)`).
- Los parámetros se añaden con `Sql.scoped(spec, scope)` y `Sql.visible(spec, visibility)`. Los usan los 16 repositorios.

**Sesiones, claves y rutas**
- `security/ActorResolutionFilter` (`@Order(2)`): `Bearer` solo en rutas de trabajador (`WorkerRoutes.isWorkerPath`), cookie `odin_session` solo en `/v1/admin`; el resto, público. Resuelve el `Actor` con `ResolveActor` (casos de uso `fromAccessKey` / `fromSessionToken`) y lo deja en un atributo de la petición (`CurrentActor.ATTRIBUTE`), que los controladores leen con `@RequestAttribute`.
- `OriginCheckFilter` (`@Order(1)`), `DeliveryMeteringFilter` (`@Order(3)`, cuenta bytes por empresa y herramienta, nunca por persona), `LoginRateLimiter` (5 fallos/minuto por clave y por IP, en memoria; comentario `shortcut: se pierde al reiniciar`).
- `security/WorkerRoutes`: una lista única de rutas del trabajador (método + patrón → `WorkerTool`); los prefijos se derivan de esa lista para que no diverjan. Hay 16 rutas.
- Permisos de negocio en `application.shared`: `AccessPolicy` (`requireGroupAdmin`, `requireAdminOf`, `requireAdmin`, `requireWorker`, `requireCapability`) y `WorkerAccess.require(actor, organizations, projectMembers, capability, ...)`, que comprueba rol, empresa, capacidad y calcula los proyectos visibles (`Granted`). Las capacidades (`Capability`) están en `domain/organization`.
- Secretos: puertos `SecretHasher`/`PasswordHasher`; implementados en bootstrap (`Sha256SecretHasher`, `BcryptPasswordHasher`, con `spring-security-crypto`). Comparación de secretos en `shared/SecretComparison` (`MessageDigest`).
- `link/LocalNetworkGuard` (adaptador web): deja pasar solo loopback, enlace local, IPv4 privadas e IPv6 local única. `security/ClientAddress` decide la IP del cliente: `X-Forwarded-For` solo cuenta si la conexión viene de un proxy de `ODIN_TRUSTED_PROXIES` (comentario de la clase). Pruebas: `LocalNetworkGuardTest`, `ClientAddressTest`, `ForwardedForRateLimitIT`.

### 3.8 Pruebas

Ficheros de test por módulo: application 109, postgres 30, bootstrap 21, web 13, domain 7, readers 4, tagger 3, filestore 1, reader-service 1, architecture 1.
- **Casos de uso con dobles en memoria**: `odin-application/src/test/.../fake/` (`InMemoryAccessKeyRepository`, `InMemoryTopicRepository`, `InMemoryOriginalFileStore`, `SequenceIdGenerator`, `MutableClock`, `ScriptedTopicTagger`, `StubTextReader`, `Fixtures`, `Actors`…). Se exportan como test-jar (`maven-jar-plugin` en `odin-application/pom.xml`) y el adaptador web los reutiliza (`odin-adapter-web/src/test/.../TestWiring.java`).
- **Web**: pruebas con MVC sobre esos dobles (`WorkerApiTest`, `AdminApiTest`, `AuthenticationTest`, `IsolationTest`, `CaseFileControllerTest`…).
- **Postgres**: `*IT` con Testcontainers (`AbstractPostgresIT`): un IT por repositorio, más baterías de búsqueda (`SearchBatteryIT`, `SimilarCasesBatteryIT`, `CaseNumberBatteryIT`) y migración (`MigrationFromPhaseOneIT`).
- **Bootstrap**: `AbstractIT` + Testcontainers, flujo completo (`ApplicationFlowIT`) y los de seguridad/atomicidad/lectura.
- **Contrato OpenAPI**: `odin-bootstrap/.../OpenApiContractIT.servedContractMatchesTheVersionedFile` compara lo que sirve `/v1/openapi.json` con `api/openapi.json` versionado; con `-Dodin.updateOpenApi=true` lo regenera.
- **Arquitectura**: las 4 reglas de 1.4.

### 3.9 Convenciones de nombres

- Identificadores de código en inglés (`ResolveActor`, `PostgresDocumentRepository`, `inVisibleProjects`).
- Mensajes de error, comentarios Javadoc y mensajes al usuario en español (ej. «No tienes permiso sobre esta empresa» en `AccessPolicy`; «Ya existe un registro con esos datos» en `ScopedTransactions`). Los nombres de capacidades y tablas están en inglés.
- Casos de uso con verbo + objeto en infinitivo/imperativo (`AddToCaseFiles`, `WithdrawDocument`, `ListCompanyDocuments`); adaptadores con prefijo de tecnología (`Postgres*`, `Ollama*`, `PdfBox*`, `Http*`).

---

## 4. Fortalezas, incoherencias y riesgos (solo hechos)

### Fortalezas
- Aislamiento por Maven: ningún adaptador puede importar a otro ni a `odin-bootstrap` (los `pom.xml` no lo declaran).
- Núcleo sin frameworks, verificado por `grep` y por dos reglas de «lista blanca» (1.2, 1.4).
- Tiempo, aleatoriedad e identificadores inyectados (`Clock`, `IdGenerator`, `TokenGenerator`): los casos de uso son deterministas con los dobles de `fake/`.
- Doble barrera de aislamiento de empresa (RLS + `Sql.inScope`) más guarda de arranque (`DatabaseRoleGuard`) y tests específicos (`OwnerConnectionIsolationIT`, `RowLevelSecurityIT`).
- Contrato HTTP versionado y comprobado en una prueba (`OpenApiContractIT`).
- Errores homogéneos RFC 9457 también desde los filtros (`GuardFilter`).
- Lista de rutas de trabajador única (`WorkerRoutes`), de la que salen prefijos y herramientas medidas.

### Incoherencias
- Transacciones: unos casos de uso están envueltos en `UseCaseConfiguration` (7, líneas 293, 316, 328, 353, 366, 379, 631) y el resto no. No hay anotación ni convención que marque cuáles deben serlo. El puerto `DocumentRepository` expone además `withDocumentsLocked(... Supplier ...)` (`port/DocumentRepository.java:48`), otra forma distinta de agrupar operaciones.
- Excepciones: las de dominio extienden `DomainException`; las de puertos extienden `RuntimeException` y no tienen traducción en `ProblemDetailsHandler` (irían a 500 si llegaran a un controlador).
- Dónde viven los permisos: parte en `application.shared` (`AccessPolicy`, `WorkerAccess`), parte en el filtro web (`ActorResolutionFilter`, `WorkerRoutes`).
- Los puertos importan tipos de otros paquetes de `application` (`DeliveryMeter`, `UsageStatistics`, `TaggingRepository`).
- Nombres: `AddToCaseFiles` (plural) frente al resto en singular; `UseCaseConfiguration` de 641 líneas con 76 beans escritos uno a uno.
- Las migraciones saltan de V13 a V20.

### Riesgos
- **Transacciones**: un caso de uso nuevo que llame a varios repositorios es no atómico por defecto, y nada (ni ArchUnit ni el compilador) avisa. Los repositorios abren una transacción por llamada; si se anida dentro de una externa (las envolturas de bootstrap), `set_config(..., true)` dura hasta el final de la transacción externa, así que un `inScope` interior con otro alcance cambiaría las variables de empresa para lo que quede de ella. Es lo que sale del código de `ScopedTransactions.inTransaction`; no lo he ejecutado.
- **RLS**: 5 tablas sin RLS (`access_key`, `admin_session`, `app_user`, `company_capability`, `odin_group`); en ellas el aislamiento entre empresas depende solo del filtro SQL de cada consulta y de los casos de uso. Sin `FORCE ROW LEVEL SECURITY`; la comprobación de arranque solo mira la tabla `document` (`DatabaseRoleGuard.ROLE`).
- **ArchUnit**: no cubre `bootstrap`, ciclos entre paquetes de `application`, ni qué clases de `application` importan los adaptadores; el núcleo está bien vigilado pero el borde lo está menos.
- **Contrato HTTP acoplado al dominio**: `Responses.java` (574 líneas) mezcla tipos de `domain` y `application`; un cambio en un `record` del dominio puede cambiar el contrato (lo detecta `OpenApiContractIT`, pero después).
- **Dominio anémico**: las reglas están dispersas en 190 clases de `application`; `Document` tiene 20 campos y un constructor posicional usado en varios sitios (en la rama `primera-carga`, `UploadDocument` ya lo construye con una función `shape` extra para no tocarlo).
- **Límite de intentos y proxies**: `LoginRateLimiter` es en memoria (se pierde al reiniciar, comentado como `shortcut`) y se apoya en `ClientAddress`, cuya fiabilidad depende de configurar bien `ODIN_TRUSTED_PROXIES`.
- **Seguridad por red local**: `LocalNetworkGuard` acepta cualquier IP privada, no solo la de la empresa; depende de que `ClientAddress` devuelva la IP real.

---

## 5. Funcionalidades en curso: ¿cambian algo?

Se compararon con `git diff integracion/instalacion..<rama> --stat -- api/`. Conclusión general: **ninguna cambia el patrón arquitectónico**; todas siguen puertos + casos de uso + `@Bean` en bootstrap + RLS + dobles en memoria. Cambios por rama:

### `feature/primera-carga` (50 ficheros, +2246/-52)
- **Migración nueva `V25__intake.sql`**: tablas `intake_batch`, `intake_project_candidate`, `intake_proposal`; columnas nuevas en `document` (`intake_batch_id`, `intake_pending`, `intake_path`, `scan_of`) con claves foráneas compuestas y `CHECK`; reescribe la política `document_scope`; `GRANT ... TO odin_app`; RLS activado y una política por cada tabla nueva (`intake_batch_scope`, etc.). Mantiene el patrón RLS.
- **Puerto nuevo `port/IntakeRepository`** (lotes, candidatos, propuestas), con `PostgresIntakeRepository` (152 líneas) y su `@Bean` en `AdapterConfiguration`. Dobles: `InMemoryIntakeRepository`.
- **Casos de uso nuevos** en un paquete nuevo `application/intake` (`StartIntake`, `AddFileToIntake`, `CloseIntakeUpload`, `GetIntakeBatch`) con 4 `@Bean` nuevos; `UploadDocument` gana `executeForIntake` y un parámetro `shape` (`UnaryOperator<Document>`).
- **Dominio con comportamiento**: paquete nuevo `domain/intake`; `IntakeBatch` es una máquina de estados OPEN → READING → IN_REVIEW → CLOSED con `moveTo`, que lanza `ConflictException` en transiciones no permitidas. Es el primer objeto con reglas propias (ver 2.1). `Document` añade `withIntake(...)`.
- **Mecanismo nuevo de visibilidad**: `ScopedTransactions.includeIntake(boolean)` + variable de sesión `app.include_intake`, y en `Sql.java` tres filtros nuevos (`notPending`, `visibleDocument`, `listable`). Es una tercera variable del mismo tipo que `include_withdrawn`. `PostgresDocumentRepository`, `PostgresKnowledgeSearch`, `SearchSql`, `SupersessionSql`, `PostgresCaseFileRepository` se tocan para aplicarlos: toda consulta de documentos debe llevar el filtro, y es fácil olvidarlo en una consulta nueva (riesgo, no hecho comprobado).
- **Web**: `admin/IntakeController`, usando `MultipartFile`; `Requests`/`Responses` +41 líneas; `openapi.json` +321.
- **Pruebas**: `IntakeSecurityIT`, `IntakeUploadIT`, `IntakeUploadTest`, `IntakeControllerTest`, `IntakeBatchTest`.
- Desviación: no se ha añadido ninguna regla ArchUnit.

### `feature/subir-desde-chat` (38 ficheros, +975/-66)
- **Migración nueva `V26__upload_links_to_projects.sql`**: `access_link.project_id` con clave foránea compuesta `(project_id, company_id)` y una restricción `CHECK` nueva. Añade la clave única `project_id_company_unique` solo si no existe (comentario: «Otra migración crea la misma clave», que es la de V25 de `primera-carga`: se resolvió el posible choque de orden entre ramas).
- **Sin puertos nuevos**. Casos de uso nuevos: `document/UploadToProject`, `link/ProjectUploadAccess`, `link/LinkTarget`, `link/UploadTargetKind`; cambios en `IssueUploadLink`, `ResolveLink`, `UploadByLink`, `OpenOriginal`, `LinkIssuer`.
- **Dominio**: `Capability` gana `UPLOAD_DOCUMENTS` (desactivada por defecto hasta que el administrador la active, según el comentario de V26).
- **Web**: `worker/ProjectUploadController` (`POST /v1/projects/{projectId}/upload-links`) y una ruta nueva en `WorkerRoutes` (+1 línea).
- No introduce patrones nuevos ni RLS nueva (usa las políticas de `access_link` existentes).

### `feature/panel-listas` (21 ficheros, +460/-64)
- **Sin migraciones** (no aparece ningún `.sql` en el diff).
- **Puertos modificados**, no añadidos: `DocumentRepository.listWithdrawn(...)` pasa a `search(scope, visibility, withdrawn, titleQuery, offset, limit)` + `count(...)`; `KnowledgeCardRepository` gana `search` + `count`. Implementados en `PostgresDocumentRepository` y `PostgresKnowledgeCardRepository`. Comentario del puerto: la búsqueda no distingue mayúsculas ni acentos y trata `%` y `_` como texto.
- Casos de uso: `ListCompanyDocuments`, `ListCardsForReview` (nuevo `page(...)` con `CardPage`; tope de límite entre 10 y 50, usa `WorkerAccess.cap`). `ListCardsForReview.execute` se sustituye por `page`.
- **Web**: `AdminCardController` y `AdminDocumentController` ganan paginación y búsqueda; `Responses` +8; `openapi.json` +76/-.
- Cambio de contrato: `listWithdrawn` desaparece del puerto; las pruebas de integración y fakes se actualizan (`InMemoryDocumentRepository`, `InMemoryKnowledgeCardRepository`).

### Interacción entre ramas (hechos)
- `primera-carga` y `subir-desde-chat` añaden las migraciones V25 y V26 con la misma clave única protegida contra repetición, para que apliquen en cualquier orden.
- `primera-carga` y `panel-listas` tocan los mismos ficheros (`PostgresDocumentRepository`, `DocumentRepository`, `ListCompanyDocuments`, `Responses`, `openapi.json`, `InMemoryDocumentRepository`). Son zonas de posible conflicto al integrar; no he intentado el merge.

---

## No comprobado

- **No pude ejecutar las pruebas ArchUnit**: `mvn -o -pl odin-architecture-tests -am test` falla sin red (falta `spring-boot-dependencies:4.1.2` en el repositorio local de Maven). Las 4 reglas están leídas del código fuente, no ejecutadas; no sé si pasan hoy ni en las ramas en curso.
- No ejecuté ninguna prueba ni levanté Docker/Testcontainers.
- No verifiqué en ejecución el comportamiento de las transacciones anidadas (`ScopedTransactions` dentro de las envolturas de `UseCaseConfiguration`), ni que compartan el mismo `DataSource`/gestor.
- No revisé el contenido de todas las migraciones (solo las políticas RLS, la lista de tablas y V25/V26) ni cuáles consultas SQL aplican `inScope`/`inVisibleProjects`/`notWithdrawn` una por una.
- No leí los 70 casos de uso ni las 16 clases `Postgres*` completas: los ejemplos son muestras, no un inventario.
- No localicé dónde se capturan `TaggerFailedException`/`TaggerUnavailableException`; solo vi el tratamiento de las del lector.
- No investigué por qué faltan las migraciones V14-V19.
- No analicé los ficheros fuera de `api/` (panel web, MCP, scripts) ni el contenido de `api/config`.
- No ejecuté el merge de las tres ramas para ver conflictos.
