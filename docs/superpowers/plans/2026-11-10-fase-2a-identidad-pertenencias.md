# Odín · Fase 2a: identidad con pertenencias y proyectos cerrados por defecto — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que una persona sea una sola identidad con una pertenencia por empresa, que pueda administrar y trabajar a la vez, y que cada trabajador vea solo los proyectos que tiene asignados (D-038, corregida por el propietario el 2026-11-11: proyectos cerrados por defecto).

**Arquitectura:** la misma hexagonal. La tarea 1 cambia el modelo sin cambiar el comportamiento, con todas las pruebas que ya existen como red. Las tareas 2 a 4 añaden el comportamiento nuevo por capas de negocio. El panel y la prueba de extremo a extremo van al final.

**Tecnología:** Java 21, Spring Boot 4.1.2, PostgreSQL 17.10 con Flyway y seguridad por filas, React 19, Vitest y Playwright.

**Diseño:** `docs/superpowers/specs/2026-11-10-identidad-pertenencias-design.md` (aquí, «spec»). Amplía `2026-11-09-api-central-design.md` («spec base») y `2026-11-10-correcciones-prueba-propietario-design.md` («spec 1.1»). Quien ejecute una tarea lee los tres.

## Restricciones globales

- **TDD con evidencia:** primero la prueba, se ejecuta y se guarda en el informe la orden y la salida en rojo antes de escribir el código. Un fallo de compilación solo vale como rojo para la primera prueba de una clase nueva.
- **Capas:** `odin-domain` solo `java.*`; `odin-application` solo `odin-domain` y `java.*`; Spring solo en los adaptadores y en `odin-bootstrap`. ArchUnit lo comprueba.
- **Clean Code.** Métodos de 30 líneas como máximo (Checkstyle y ESLint). Sin lógica duplicada. Comentarios mínimos.
- **Identificadores en inglés, explicativos y en camelCase.** Mensajes al usuario, documentación y commits en español.
- **Aislamiento (D-014):** cada caso de uso con actor lleva una prueba con un actor de otra empresa. Cada consulta SQL lleva su filtro de empresa explícito; la seguridad por filas es la segunda barrera.
- **Borrados lógicos**, nunca físicos. Un segundo borrado no cambia la fecha del primero.
- **Maven** siempre desde `api/`. **npm** con `node_modules` locales. Nada global.
- **Contrato:** si cambia la API, en la misma tarea se regenera `api/openapi.json` (`cd api && mvn -q verify -Dodin.updateOpenApi=true`) y los tipos (`npm run generate:types` en `panel/` y en `mcp/`).
- **Cierre de cada tarea:** `make check` termina con código de salida 0. Desde la tarea 2, también `make demo`.
- **Dónde se trabaja:** en la copia de trabajo `.worktrees/identidad-pertenencias`, nunca en la carpeta principal del repositorio. No se ejecuta `make up` ni `make down` y no se toca `deploy/.env`.
- **Git:** cada tarea en su rama `task/identidad-pertenencias/<tarea>`, que sale de `feature/identidad-pertenencias`. Commits en español, sin atribución a Claude ni `Co-Authored-By`. Sin push. El merge `--no-ff` lo hace el CTO.
- **Datos:** solo ficticios. **Secretos:** ni en logs ni en el repositorio.

## Foco de revisión

1. **Clave de una pertenencia que ya no vale:** si la pertenencia se desactiva, se borra o pierde el papel de trabajador, o la persona se desactiva o se borra, la clave da 401 en la siguiente petición. Pruebas en la tarea 2 (`ResolveActorTest`).
2. **Persona en dos empresas:** con la clave de A solo ve lo de A, y el administrador de B no ve ni cambia su pertenencia a A. Pruebas en la tarea 2 (`ApplicationFlowIT` e `IsolationTest`).
3. **Proyecto no asignado:** ni la búsqueda, ni la lista, ni la lectura de una página, ni la verificación de una cita devuelven nada suyo, y filtrar por él da 404. Pruebas en la tarea 3, una por caso de uso.
4. **Quitar «Visible para toda la empresa»:** deja de verse para quien no es miembro en la siguiente petición. Prueba en la tarea 3.
5. **Base de datos de la fase 1.1 con datos:** al migrar, cada usuario conserva su acceso y cada clave sigue funcionando. Prueba en la tarea 1 (`MigrationFromPhaseOneIT`).
6. **Proyecto nuevo:** nace cerrado; ningún trabajador lo ve hasta que se le asigna. Prueba en la tarea 3.
7. **Código de persona:** aleatorio con `SecureRandom`, único también entre borradas, nunca se acepta en una petición ni cambia. Pruebas en la tarea 5.

---

## Interfaces compartidas (fuente única de nombres)

```java
// domain/identity
public enum Role { GROUP_ADMIN, COMPANY_ADMIN, WORKER }            // el papel con el que actúa una petición; no cambia
public record User(UserId id, String displayName, Optional<String> email, boolean groupAdmin,
        boolean active, Optional<String> passwordHash) { }
        // la tarea 5 cambia displayName por firstName, Optional<String> lastName y personCode; displayName() pasa a ser derivado
public record Membership(UserId userId, CompanyId companyId, Set<Role> roles, boolean active) { }
        // roles: no vacío y solo COMPANY_ADMIN o WORKER
public record Actor(UserId userId, String displayName, Role role, Set<CompanyId> companies) {
    boolean isGroupAdmin();  boolean administers(CompanyId company);
    CompanyId requireCompany();   // la única empresa de un trabajador
    CompanyScope scope();
}
public record AccessKey(AccessKeyId id, UserId userId, CompanyId companyId, String label, String prefix,
        String secretHash, Instant createdAt, Optional<Instant> revokedAt) { }

// domain/organization
public record Project(ProjectId id, CompanyId companyId, String name, boolean restricted) { }
        // restricted: verdadero por defecto; falso = visible para toda la empresa

// domain/shared
public record ProjectVisibility(boolean allProjects, Set<ProjectId> projects) {
    static ProjectVisibility all();  static ProjectVisibility of(Set<ProjectId> projects);
}

// application/port
public interface MembershipRepository {
    void save(Membership membership);
    Optional<Membership> find(UserId userId, CompanyId companyId);
    List<Membership> listByCompany(CompanyId companyId);
    List<Membership> listByUser(UserId userId);
    void delete(UserId userId, CompanyId companyId, Instant at);
}
public interface ProjectMemberRepository {
    void add(ProjectId projectId, UserId userId);
    void remove(ProjectId projectId, UserId userId);
    List<UserId> listMembers(ProjectId projectId);
    Set<ProjectId> projectsOf(CompanyId companyId, UserId userId);   // de los que es miembro
}
```

- `UserRepository` pierde `listByCompany`: la lista de una empresa sale de sus pertenencias.
- Todas las lecturas excluyen lo borrado, como en la fase 1.1.

**Rutas nuevas o cambiadas** (todas bajo `/v1/admin`):

| Ruta | Cuerpo | Respuesta | Tarea |
|---|---|---|---|
| `GET /me` | — | `AdminMe` gana `companies: CompanyRef[]` (`id`, `name`): las que administra | 2 |
| `GET /companies/{companyId}/members` | — | `MemberView[]`: `userId`, `displayName`, `email`, `roles`, `active` | 2 |
| `POST /companies/{companyId}/members` | `AddMember { displayName, email?, roles, password?, projectIds? }` | 201, `MemberAdded { member, alreadyExisted }` | 2; `projectIds`, 3 |
| `GET /companies/{companyId}/members/{userId}/projects` | — | `ProjectView[]`: los proyectos de los que es miembro | 3 |
| `PATCH /companies/{companyId}/members/{userId}` | `UpdateMember { roles?, active?, password? }` | `MemberView` | 2 |
| `DELETE /companies/{companyId}/members/{userId}` | — | 204 | 2 |
| `GET, POST /companies/{companyId}/members/{userId}/keys` | `KeyLabel` | Como hoy | 2 |
| `PATCH /users/{userId}` | `UpdateUser { displayName?, email?, active? }` | `UserRef`. `active` solo lo cambia el administrador del grupo | 2 |
| `PATCH /companies/{companyId}/projects/{projectId}` | `UpdateProject { name?, restricted? }` | `ProjectView` gana `restricted` | 3 |
| `GET /companies/{companyId}/projects/{projectId}/members` | — | `ProjectMemberView[]`: `userId`, `displayName` | 3 |
| `PUT, DELETE /companies/{companyId}/projects/{projectId}/members/{userId}` | — | 204 | 3 |

Desaparecen en la tarea 2: `GET` y `POST /companies/{companyId}/users`, y `GET` y `POST /users/{userId}/keys`. Siguen igual: `DELETE /users/{userId}` (solo el administrador del grupo), `PUT /users/{userId}/password` y `DELETE /keys/{keyId}`.

---

### Tarea 1: Modelo de persona y pertenencia, sin cambio de comportamiento · Sonnet

Rama `task/identidad-pertenencias/modelo`. Spec 4 y 7. Es una refactorización: el contrato no cambia y todas las pruebas que ya existen siguen comprobando lo mismo.

**Ficheros:**
- **Crear:** `domain/identity/Membership.java`, `application/port/MembershipRepository.java`, `adapter/postgres/PostgresMembershipRepository.java`, el doble `InMemoryMembershipRepository`, la migración `V7__memberships.sql` y las pruebas `MembershipTest`, `PostgresMembershipRepositoryIT` y `MigrationFromPhaseOneIT`.
- **Modificar:** `User`, `Actor`, `AccessKey`, `AccessPolicy`, `UserAdministration`, todos los casos de uso de `application/identity`, `DescribeMe`, los repositorios de PostgreSQL y sus dobles, `Responses`, `UseCaseConfiguration`, `AdapterConfiguration`, `TestWiring` y el ayudante de pruebas `Actors`.

**Interfaces que produce:** `User`, `Membership`, `Actor`, `AccessKey` y `MembershipRepository`, como en «Interfaces compartidas».

**Valores:**
- `V7`:
  - crea `membership (user_id, company_id, is_company_admin, is_worker, active, deleted_at)`, con clave primaria `(user_id, company_id)` y la misma política de seguridad por filas por empresa que `project`;
  - la rellena desde `app_user`: una fila por usuario con empresa, con el papel que tenía y su `active`;
  - añade `app_user.group_admin` y lo pone a verdadero en quien tenía el papel `GROUP_ADMIN`;
  - añade `access_key.company_id`, lo rellena con la empresa del usuario de la clave y lo deja obligatorio;
  - quita `app_user.role` y `app_user.company_id`;
  - da a `odin_app` los mismos permisos sobre `membership` que tiene sobre `project`.
- En esta tarea cada persona sigue teniendo una sola pertenencia con un solo papel, porque los casos de uso de alta no cambian todavía.
- `UserView` sigue saliendo igual por la API: `role` y `companyId` se calculan desde la pertenencia.
- `Actor.companies`: vacío para el administrador del grupo; las empresas donde tiene `COMPANY_ADMIN` activo para la sesión de un administrador de empresa; la empresa de la clave para un trabajador.
- `ResolveActor.fromAccessKey` exige una pertenencia activa con `WORKER` en la empresa de la clave. `fromSessionToken` exige ser administrador del grupo o tener alguna pertenencia activa con `COMPANY_ADMIN`.
- Desactivar a un usuario desde la ruta actual desactiva su pertenencia, no a la persona, salvo que sea el administrador del grupo.
- `api/openapi.json` no cambia.

- [ ] **Paso 1:** `MembershipTest`: `rolesCannotBeEmpty` y `groupAdminIsNotAMembershipRole`. Comprueba que fallan e implementa `Membership`.
- [ ] **Paso 2:** `MigrationFromPhaseOneIT`: aplica las migraciones hasta V6, inserta un administrador del grupo, un administrador de empresa, un trabajador activo con una clave y un trabajador desactivado; aplica el resto y comprueba:
  - `everyUserWithCompanyGetsOneMembershipWithItsRole`;
  - `groupAdminIsFlaggedAndHasNoMembership`;
  - `accessKeyKeepsItsUserAndGainsItsCompany`;
  - `inactiveWorkerBecomesInactiveMembership`.
  Comprueba que falla y escribe `V7`.
- [ ] **Paso 3:** `PostgresMembershipRepositoryIT`: `savesAndFinds`, `listsByCompanyAndByUser`, `deletedMembershipIsExcludedFromReads` y `secondDeleteKeepsFirstTimestamp`. Implementa el repositorio y el doble.
- [ ] **Paso 4:** cambia `User`, `Actor`, `AccessKey` y los casos de uso para que usen pertenencias. Las pruebas que ya existen se adaptan en cómo construyen los datos, no en lo que comprueban. Añade a `ResolveActorTest`: `keyOfInactiveMembershipIsUnauthorized` y `sessionOfUserWithoutAdminMembershipIsUnauthorized`.
- [ ] **Paso 5:** `make check` y `make demo` terminan con 0. `api/openapi.json` no cambia.
- [ ] **Paso 6:** commit «refactor: persona y pertenencia por empresa, sin cambio de comportamiento».

### Tarea 2: Varias pertenencias y papeles combinados · Sonnet

Rama `task/identidad-pertenencias/pertenencias`. Spec 5.1, 5.2 y 5.3.

**Ficheros:**
- **Crear:** en `application/identity`: `AddMember`, `UpdateMember`, `RemoveMember`, `ListMembers`, con sus pruebas. En `adapter/web/admin`: `MemberController`.
- **Borrar:** `RegisterUser` y `ListUsers`, con sus pruebas; sus casos pasan a las de `AddMember` y `ListMembers`.
- **Modificar:** `UpdateUser`, `DeleteUser`, `ChangePassword`, `IssueAccessKey`, `ListAccessKeys`, `ResolveActor`, `LogInAdmin`, `UserController`, `SessionController`, `Requests`, `Responses`, el cableado, el contrato y los tipos. En `panel/`, solo lo imprescindible para que compile y sus pruebas pasen; el panel nuevo es la tarea 5. En `mcp/scripts/demo-client.ts` y `panel/e2e/`, lo necesario para que `make demo` siga pasando con las rutas nuevas.

**Interfaces que consume:** las de la tarea 1.
**Interfaces que produce:** las rutas de la tabla marcadas con la tarea 2, y los casos de uso:

```java
public record NewMember(String displayName, Optional<String> email, Set<Role> roles, Optional<String> password) { }
public record MemberAdded(User user, Membership membership, boolean alreadyExisted) { }
public record MemberChanges(Optional<Set<Role>> roles, Optional<Boolean> active, Optional<String> password) { }
public class AddMember    { public MemberAdded execute(Actor actor, CompanyId companyId, NewMember newMember) }
public class UpdateMember { public Membership execute(Actor actor, CompanyId companyId, UserId userId, MemberChanges changes) }
public class RemoveMember { public void execute(Actor actor, CompanyId companyId, UserId userId) }
public class ListMembers  { public List<MemberView> execute(Actor actor, CompanyId companyId) }
```

**Valores:**
- Reglas y permisos: la tabla del spec 5.1, literal. Mensajes nuevos:
  - 409, «Esa persona ya pertenece a esta empresa»;
  - 400, «Hace falta una contraseña para administrar», al dar `COMPANY_ADMIN` a quien no tiene contraseña y no llega una;
  - 400, «Una pertenencia necesita al menos un papel»;
  - 403, «Solo el administrador del grupo da o quita la administración de una empresa»;
  - 403, «Solo el administrador del grupo puede editar a esta persona»;
  - 400, «No puedes quitarte el papel con el que estás actuando».
- Al añadir a una persona que ya existe por su correo, su nombre y su contraseña no cambian, y `alreadyExisted` es verdadero.
- Quitar `COMPANY_ADMIN`, desactivar o borrar una pertenencia cierra las sesiones del panel de esa persona si ya no administra nada.
- Claves: se dan sobre la pertenencia `(userId, companyId)`, activa y con `WORKER`.
- Un administrador de empresa nunca recibe datos de las pertenencias de otra empresa: `MemberView` solo lleva lo de la suya.

- [ ] **Paso 1:** pruebas de casos de uso; comprueba que fallan:
  - `AddMemberTest`: `newPersonGetsUserAndMembership`, `existingEmailGetsSecondMembershipAndKeepsNameAndPassword`, `sameCompanyTwiceIsConflict`, `adminRoleNeedsPassword`, `adminAndWorkerRolesTogether`, `workerOnlyHasNoPassword`, `companyAdminCannotGrantCompanyAdmin`, `adminOfAnotherCompanyIsForbidden` y `emptyRolesAreRejected`;
  - `UpdateMemberTest`: `addsWorkerRoleToAnAdmin`, `companyAdminCannotGrantOrRevokeCompanyAdmin`, `grantingAdminToPersonWithoutPasswordNeedsOne`, `removingLastRoleIsRejected`, `cannotRemoveOwnActingRole`, `deactivatesAndReactivatesMembership`, `losingAdminEverywhereClosesSessions` y `adminOfAnotherCompanyIsForbidden`;
  - `RemoveMemberTest`: `removesOnlyThatCompanysMembership`, `personWithNoMembershipLeftStillExists`, `cannotRemoveOneself` y `adminOfAnotherCompanyIsForbidden`;
  - `ListMembersTest`: `listsOnlyThatCompanysMemberships` y `adminOfAnotherCompanyIsForbidden`;
  - `UpdateUserTest`: `companyAdminCannotEditPersonWhoBelongsToAnotherCompany` y `onlyGroupAdminDeactivatesAPerson`;
  - `ResolveActorTest`: `keyStopsWorkingWhenMembershipLosesWorkerRole`, `keyOfCompanyAResolvesToCompanyAOnly` y `adminOfTwoCompaniesGetsBothInActor`;
  - `LogInAdminTest`: `personWhoIsAdminAndWorkerLogsIn` y `workerOnlyCannotLogIn`.
- [ ] **Paso 2:** implementa los casos de uso y adapta los dobles.
- [ ] **Paso 3:** web y contrato. `AdminApiTest`: una prueba por ruta de la tabla. `IsolationTest`: `otherCompanyAdminCannotListAddPatchOrRemoveMembers` y `otherCompanyAdminCannotIssueKeyForForeignMembership`.
- [ ] **Paso 4:** `ApplicationFlowIT`: `samePersonInTwoCompaniesSeesEachCompanyWithItsOwnKey` (una persona con una clave en A y otra en B; con cada una solo encuentra el documento de esa empresa) y `adminWhoAlsoWorksReviewsOwnCard` (la revisión guarda quién revisó).
- [ ] **Paso 5:** regenera el contrato y los tipos; adapta el panel y la demo lo imprescindible. `make check` y `make demo` terminan con 0.
- [ ] **Paso 6:** commit «feat: varias pertenencias por persona y papeles combinados».

### Tarea 3: Proyectos cerrados por defecto y sus miembros · Sonnet

Rama `task/identidad-pertenencias/proyectos-cerrados`. Spec 5.1 (alta con proyectos), 5.4 y la primera barrera del spec 6. Corrección del propietario del 2026-11-11: los proyectos están cerrados por defecto.

**Ficheros:**
- **Crear:** `domain/shared/ProjectVisibility.java`, `application/port/ProjectMemberRepository.java`, `adapter/postgres/PostgresProjectMemberRepository.java`, el doble, `V8__closed_projects.sql`; en `application/organization`: `UpdateProject`, `AddProjectMember`, `RemoveProjectMember`, `ListProjectMembers` y `ListMemberProjects`, con sus pruebas.
- **Borrar:** `RenameProject`, que pasa a `UpdateProject`.
- **Modificar:** `Project`, `CreateProject`, `AddMember`, `WorkerAccess`, `DescribeMe`, los casos de uso de `application/search`, `SaveKnowledgeCard`, `SearchQuery`, `PostgresKnowledgeSearch`, `PostgresDocumentRepository`, `PostgresKnowledgeCardRepository`, `CompanyController`, el contrato y los tipos.

**Interfaces que produce:** `ProjectVisibility`, `ProjectMemberRepository` y las rutas de la tabla marcadas con la tarea 3.

**Valores:**
- `V8`:
  - añade `project.restricted boolean NOT NULL DEFAULT true`;
  - crea `project_member (project_id, user_id, added_at)` con clave primaria `(project_id, user_id)`;
  - da de alta como miembros de cada proyecto no borrado a las pertenencias de su empresa con `is_worker`, `active` y sin `deleted_at`. Así nadie pierde el acceso que tenía.
- Un proyecto nuevo (`CreateProject`) nace con `restricted = true` y sin miembros.
- Proyectos visibles para un trabajador: los de su empresa con `restricted = false` más aquellos de los que es miembro. `WorkerAccess` lo calcula una vez por petición y devuelve un `ProjectVisibility`.
- Cada consulta del trabajador filtra con `project_id IS NULL OR project_id = ANY(:visibleProjects)`. Los documentos y las fichas sin proyecto los sigue viendo toda la empresa.
- Un `projectId` que el trabajador no ve da 404, «Proyecto no encontrado», igual que uno de otra empresa.
- Las rutas del administrador usan `ProjectVisibility.all()`.
- Miembros: solo personas con pertenencia activa y papel `WORKER` en esa empresa; si no, 400, «Solo un trabajador de la empresa puede ser miembro».
- Pasar `restricted` a falso («Visible para toda la empresa») conserva los miembros; pasarlo a verdadero no añade ni quita ninguno.
- `AddMember` acepta `projectIds` opcional. Cada id tiene que ser un proyecto no borrado de esa empresa; si no, 404, «Proyecto no encontrado». Solo se admite con papel `WORKER`; si no, 400 con el mensaje de los miembros. La pertenencia y sus proyectos se guardan en la misma transacción: si algo falla, no queda nada.
- `ListMemberProjects`: los proyectos de los que la persona es miembro en esa empresa (`ProjectView`, con `restricted`). Un administrador de otra empresa recibe 404.

- [ ] **Paso 1:** pruebas de casos de uso; comprueba que fallan:
  - `UpdateProjectTest`: los casos que tenía `RenameProjectTest`, más `marksCompanyWideAndClosesAgain` y `companyWideKeepsMembers`;
  - `CreateProjectTest.newProjectIsClosedWithoutMembers`;
  - `AddProjectMemberTest`: `addsWorkerOfTheCompany`, `nonWorkerOrForeignPersonIsRejected`, `addingTwiceIsIdempotent` y `adminOfAnotherCompanyIsForbidden`;
  - `RemoveProjectMemberTest`, `ListProjectMembersTest` y `ListMemberProjectsTest`, con su caso de otra empresa;
  - `AddMemberTest`: `addsProjectsOnCreation`, `projectOfAnotherCompanyIsRejectedAndNothingIsSaved` y `projectsOnlyForWorkers`;
  - `DescribeMeTest`: `workerSeesCompanyWideProjectsAndAssignedOnes` y `newProjectIsInvisibleUntilAssigned`;
  - una prueba `unassignedProjectIsInvisible` en cada una de `SearchKnowledgeTest`, `ListDocumentsTest`, `ReadPageTest`, `VerifyCitationTest` y `SaveKnowledgeCardTest`;
  - `SearchKnowledgeTest.documentWithoutProjectIsVisibleToEveryWorker` y `closingACompanyWideProjectHidesItAtOnce`.
- [ ] **Paso 2:** implementa los casos de uso y los dobles.
- [ ] **Paso 3:** PostgreSQL. `PostgresProjectMemberRepositoryIT` y, en `PostgresKnowledgeSearchIT`, `PostgresDocumentRepositoryIT` y `PostgresKnowledgeRepositoriesIT`, una prueba `filtersByVisibleProjects` cada una. Escribe `V8`. En la prueba de migración (`MigrationFromPhaseOneIT` o una hermana), `existingProjectsBecomeClosedWithTheirWorkersAsMembers`.
- [ ] **Paso 4:** web y contrato. `AdminApiTest`: una prueba por ruta. `IsolationTest`: `otherCompanyAdminCannotManageProjectMembers` y `otherCompanyAdminCannotListMemberProjects`. `WorkerApiTest`: `filterByUnassignedProjectIs404`.
- [ ] **Paso 5:** `ApplicationFlowIT`: `projectIsFoundOnlyByAssignedWorkers`: dos trabajadores de la misma empresa, uno dado de alta con el proyecto y otro sin él, y un documento en ese proyecto. El asignado lo encuentra con su cita; el otro recibe «no consta» y un 404 al leer la página.
- [ ] **Paso 6:** regenera el contrato y los tipos. Si la demo o el extremo a extremo crean proyectos y buscan en ellos, asignan a sus trabajadores; no se marca nada como visible para toda la empresa para que pasen. `make check` y `make demo` terminan con 0.
- [ ] **Paso 7:** commit «feat: proyectos cerrados por defecto, visibles solo para sus miembros».

### Tarea 4: Segunda barrera: seguridad por filas por proyecto · Sonnet

Rama `task/identidad-pertenencias/rls-proyectos`. Spec 6.

**Ficheros:**
- **Crear:** `V9__project_row_level_security.sql`.
- **Modificar:** `adapter/postgres/ScopedTransactions.java` y quien lo llama, `RowLevelSecurityIT` y `OwnerConnectionIsolationIT`.

**Valores:**
- Las políticas de `document`, `document_page` y `knowledge_card` exigen, además de la empresa: `project_id IS NULL`, o `project_id = ANY` de `app.project_ids`, o `app.all_projects = 'on'`. `document_page` toma el proyecto de su documento.
- `project_member` también lleva seguridad por filas: la política toma la empresa de su proyecto (hallazgo de la revisión de la tarea 3).
- `ScopedTransactions` fija las dos variables en la misma transacción en la que ya fija las de empresa, a partir del `ProjectVisibility` de la consulta. Sin `ProjectVisibility`, no fija ninguna: solo se ve lo que no tiene proyecto.
- Ninguna consulta pierde su filtro explícito de la tarea 3.

- [ ] **Paso 1:** en `RowLevelSecurityIT`, con una conexión que ejecuta SQL sin el filtro del código; comprueba que fallan:
  - `restrictedProjectRowsAreHiddenWithoutItsIdInScope`, para las tres tablas;
  - `rowsWithoutProjectAreVisibleWithEmptyProjectScope`;
  - `allProjectsFlagShowsEverythingOfTheCompany`;
  - `noProjectVariablesMeansOnlyProjectlessRows`;
  - `projectMembersOfAnotherCompanyAreHidden`.
- [ ] **Paso 2:** escribe `V9` y adapta `ScopedTransactions`.
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0. La batería de búsqueda (`SearchBatteryIT`) mantiene 27 de 30.
- [ ] **Paso 4:** commit «feat: seguridad por filas para los proyectos cerrados».

### Tarea 5: Nombre, apellidos y código de persona · Sonnet

Rama `task/identidad-pertenencias/codigo-persona`. Spec 5.5 y 7. Petición del propietario del 2026-11-11 (O4, P2 a P4 y formato confirmado).

**Ficheros:**
- **Crear:** `application/port/PersonCodeGenerator.java` (`String next()`), su adaptador con `SecureRandom` junto al generador de ids que ya existe, un doble determinista para las pruebas, `UpdateCompany` (sustituye a `RenameCompany`) y `V10__person_names_and_codes.sql`.
- **Borrar:** `RenameCompany`, que pasa a `UpdateCompany`.
- **Modificar:** `User`, `Company`, `CreateCompany`, `AddMember`, `UpdateUser`, `ListMembers` y `MemberView`, `AdminMe`, `PostgresUserRepository`, `PostgresOrganizationRepository`, los controladores, el contrato, los tipos y las pruebas, la demo y el extremo a extremo que crean personas o empresas.

**Valores:**
- `User` sustituye `displayName` por `firstName`, `Optional<String> lastName` (vacío solo en personas migradas) y `personCode`. `displayName()` devuelve «firstName lastName», o solo `firstName` si faltan apellidos. `Actor` y las vistas siguen llevando `displayName`.
- Código de persona: 4 caracteres de `23456789ABCDEFGHJKMNPQRSTUVWXYZ` con `SecureRandom`. Índice único sobre todas las filas, borradas incluidas: un código nunca se reutiliza. Si el generado existe, se genera otro, hasta 5 intentos; después, error interno con log. No se acepta en ninguna petición y no cambia nunca.
- `Company.code`: `^[A-Z0-9]{2,6}$`; si no, 400, «El código de la empresa tiene de 2 a 6 letras mayúsculas o cifras». Único en el grupo: 409, «Ya existe una empresa con ese código». Obligatorio al crear; cambiarlo, solo el administrador del grupo.
- `AddMember { firstName, lastName, email?, roles, password?, projectIds? }`: nombre y apellidos obligatorios; si falta alguno, 400, «Faltan el nombre o los apellidos». Si la persona ya existía en el grupo, conserva su nombre y su código (spec 5.1).
- `UpdateUser { firstName?, lastName?, email?, active? }`: ninguno de los dos nombres puede quedar vacío.
- `MemberView` gana `firstName`, `lastName` (null si faltan), `personCode` y `memberCode` (`<personCode>-<code de la empresa>`, por ejemplo `7F3K-E2D`), y conserva `displayName`. `CompanyView` y `CompanyRef` ganan `code`. `AdminMe` gana `personCode`.
- `V10`:
  - `app_user.first_name` toma `display_name`; `last_name` queda NULL; `display_name` se borra;
  - cada persona recibe un `person_code` aleatorio y único (bloque con reintento);
  - cada empresa recibe como `code` las iniciales de las palabras de su nombre, en mayúsculas y sin tildes, hasta 4. Si salen menos de 2, las primeras letras del nombre. Si se repite, una cifra al final;
  - después, `NOT NULL` e índices únicos.
- Las rutas del trabajador y las herramientas del MCP no cambian.

- [ ] **Paso 1:** pruebas; comprueba que fallan:
  - generador: `codesHaveFourCharactersOfTheAlphabet` y, con el doble, `collisionGeneratesAnotherCode` y `givesUpAfterFiveCollisions`;
  - `AddMemberTest`: `newPersonGetsAGeneratedCode`, `existingPersonKeepsNameAndCode`, `missingFirstOrLastNameIsRejected` y `memberCodeJoinsPersonAndCompanyCodes`;
  - `UpdateUserTest`: `editsFirstAndLastName`, `namesCannotBeCleared` y `codeNeverChanges`;
  - `CreateCompanyTest`: `codeIsRequiredAndValidated` y `duplicateCodeIsRejected`; `UpdateCompanyTest`: los casos de `RenameCompanyTest` más `onlyGroupAdminChangesTheCode`.
- [ ] **Paso 2:** implementa el dominio, los casos de uso y el generador.
- [ ] **Paso 3:** PostgreSQL. `PostgresUserRepositoryIT.personCodeIsUniqueAmongDeletedToo`; en la prueba de migración, `existingPeopleKeepTheirNameAsFirstNameAndGetACode` y `existingCompaniesGetTheirInitialsAsCode`. Escribe `V10`.
- [ ] **Paso 4:** web y contrato. `AdminApiTest`: una prueba por ruta cambiada. `IsolationTest.companyAdminCannotChangeACompanyCode`.
- [ ] **Paso 5:** regenera el contrato y los tipos. La demo y el extremo a extremo dan apellidos a sus personas y código a sus empresas. `make check` y `make demo` terminan con 0.
- [ ] **Paso 6:** commit «feat: nombre, apellidos y código de persona».

### Tarea 6: Panel: personas, papeles, accesos, selector de empresa y proyectos · Sonnet

Rama `task/identidad-pertenencias/panel`. Spec 9. Con el sistema de la dirección visual (D-039), sin rediseñarlo: `docs/superpowers/specs/2026-11-10-direccion-visual-design.md` (apartados 11 y 15) y su maqueta. El selector de empresa va en el hueco del nombre de la empresa en la barra lateral, y la columna «Quién lo ve» de proyectos dice «Toda la empresa», «N miembros» («1 miembro») o, sin miembros, «Nadie todavía».

**Ficheros:**
- **Crear:** `panel/src/components/Members.tsx`, `MemberForm.tsx`, `ProjectMembers.tsx` y `CompanySwitcher.tsx`, con sus pruebas.
- **Borrar:** `Users.tsx` y `UserForm.tsx`, con sus pruebas; sus casos pasan a las nuevas.
- **Modificar:** `Projects.tsx`, `Keys.tsx`, `pages/Company.tsx`, `App.tsx` y `api/client.ts`.

**Valores:**
- Lista de personas: código completo (`7F3K-E2D`) · nombre y apellidos, o «Faltan apellidos» · correo o «sin correo» · «Administra la empresa», «Trabaja con su IA» o los dos · «activo» o «desactivado».
- Alta: nombre, apellidos, correo, las casillas «Administra la empresa» y «Trabaja con su IA» (al menos una) y, solo con la primera marcada, contraseña. «Administra la empresa» solo aparece si quien mira es el administrador del grupo.
- Tras un alta con `alreadyExisted`: «Esta persona ya existía en el grupo: se ha añadido a esta empresa», en un `role="status"`.
- Por persona: cambiar papeles, activar o desactivar, quitar de la empresa con `ConfirmDialog`: «¿Quitar a <nombre> de esta empresa? Sus claves y sus proyectos de esta empresa dejarán de valer.», y claves si trabaja.
- `CompanySwitcher`: selector con la etiqueta «Empresa» y el estilo de la dirección visual, en el hueco del nombre de la empresa de la barra lateral. Con una sola empresa se ve su nombre, como hoy.
- Proyectos: casilla «Visible para toda la empresa», sin marcar en un proyecto nuevo. Al desmarcarla en un proyecto sin miembros, `ConfirmDialog`: «¿Cerrar <nombre>? Solo lo verán sus miembros, y ahora no tiene ninguno.». Lista de miembros con «Añadir» y «Quitar».
- Empresas: campo «Código» al crear, y al editar solo para el administrador del grupo.
- Alta (O1): con «Trabaja con su IA» marcada, casillas con los proyectos de la empresa, todas sin marcar; se envían en `projectIds`.
- Accesos de cada persona (O1): sus proyectos (`ListMemberProjects`), con «Añadir» y «Quitar», y «Además ve los N proyectos visibles para toda la empresa». Lo que se cambia aquí y en el proyecto son los mismos miembros.
- Claves (O2): junto a la lista, «Cada clave da acceso a lo mismo que ve <nombre> en esta empresa.».
- Botones de fila (O3): texto visible corto («Revocar clave», «Quitar») y nombre accesible con el nombre de lo afectado, que empieza por el texto visible (`Revocar clave <etiqueta>`, `Quitar <nombre>`). Se corrige también `Keys.tsx`.
- Confirmaciones con `ConfirmDialog` y selector de empresa con los componentes de la dirección visual (D-039), nunca `window.confirm` ni `<select>` sin estilo.
- Se mantienen las reglas de accesibilidad y de formularios de la fase 1.1.

- [ ] **Paso 1:** una prueba por acción, que compruebe la petición enviada y lo que se ve; comprueba que fallan:
  - `MemberForm.test.tsx`: `workerOnlySendsNoPassword`, `adminRoleRequiresPassword`, `adminCheckboxOnlyForGroupAdmin`, `atLeastOneRoleIsRequired`, `firstAndLastNameAreRequired`, `projectsOnlyForWorkersAndSentAsProjectIds`, `alreadyExistedShowsTheNotice` y `failedAddKeepsTheTypedValues`;
  - `Members.test.tsx`: `rowShowsMemberCodeRolesAndState`, `missingLastNameIsFlagged`, `changesRoles`, `deactivatesAndActivates`, `removeAsksForConfirmation`, `keysOnlyForWorkers` y `accessesListAddAndRemoveProjects`;
  - `Keys.test.tsx`: `explainsWhatAKeyGivesAccessTo` y `revokeButtonSaysRevokeKeyAndNamesItForScreenReaders`;
  - `Companies.test.tsx`: `codeIsRequiredOnCreate` y `onlyGroupAdminEditsTheCode`;
  - `CompanySwitcher.test.tsx`: `hiddenWithOneCompany` y `switchingLoadsTheOtherCompany`;
  - `ProjectMembers.test.tsx`: `closingAsksForConfirmationWhenNoMembers`, `addsAndRemovesMembers` y `whoSeesItShowsCompanyWideOrMemberCount`.
- [ ] **Paso 2:** implementa los componentes.
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0, con axe sin infracciones graves en las pantallas nuevas.
- [ ] **Paso 4:** commit «feat: panel con personas, papeles, accesos y proyectos cerrados».

### Tarea 7: Prueba de extremo a extremo y demostración · Sonnet

Rama `task/identidad-pertenencias/e2e`. Spec 10.

**Ficheros:** `panel/e2e/admin.spec.ts`, `mcp/scripts/demo-client.ts` y `README.md`.

**Valores:**
- `admin.spec.ts` pasa a recorrer, contra la API real y sin gastar más entradas que hoy:
  1. una persona añadida a «Empresa E2E» y después a una segunda empresa con el mismo correo: aparece el aviso de que ya existía;
  2. una persona con los dos papeles: entra en el panel y tiene clave;
  3. el selector de empresa de quien administra dos;
  4. cambiar papeles, desactivar y activar, y quitar de la empresa;
  5. el alta de una persona muestra su código completo, con el patrón `^[2-9A-HJKMNP-Z]{4}-<código de la empresa>$`;
  6. una persona dada de alta con un proyecto lo ve en sus «Accesos» y en los miembros del proyecto; quitarla desde el proyecto la quita de sus «Accesos».
  7. ascender a una trabajadora sin contraseña a «Administra la empresa»: el panel pide la contraseña, la envía y ella entra en el panel con su correo y esa contraseña (petición del CEO, 2026-11-11);
  8. borrar a una persona que es miembro de un proyecto: «Quién lo ve» baja en uno y deja de figurar entre sus miembros (petición del CEO, 2026-11-11).
- `demo-client.ts` añade un paso con el MCP: el trabajador asignado encuentra el documento del proyecto y el que no está asignado recibe «No consta en los documentos de tu empresa.».
- `README`: «Quién entra dónde» y el resto de apartados dicen personas, pertenencias y proyectos cerrados por defecto.

- [ ] **Paso 1:** escribe los pasos nuevos y ejecuta `make demo`. Un fallo real del panel o de la API se corrige en su módulo con su prueba; la prueba no se adapta al fallo.
- [ ] **Paso 2:** `make demo` dos veces seguidas y `make check` terminan con 0.
- [ ] **Paso 3:** commit «test: extremo a extremo de pertenencias y proyectos cerrados».

## Orden y paralelismo

En serie: 1 a 7. Cada tarea cambia el contrato o el modelo que usa la siguiente.

Al terminar, revisión final de toda la rama y, después, `make check` y `make demo` ejecutados por el CTO.
