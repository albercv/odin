# Odín · Fase 1.1: correcciones tras la prueba del propietario — Plan de implementación

> **Para quien lo ejecute:** se ejecuta con `superpowers:subagent-driven-development`, tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** corregir los fallos F1 a F9 del diseño para que `feature/api-central` pueda pasar a `develop`.

**Arquitectura:** la misma hexagonal de la fase 1. Casos de uso nuevos en `odin-application`, sobre los puertos que ya existen más `AdminSessions`. El panel añade las acciones que faltan sin cambiar su aspecto.

**Tecnología:** Java 21, Spring Boot 4.1.2, PostgreSQL 17.10 con Flyway, React 19 con Vite, Vitest y Playwright.

**Diseño:** `docs/superpowers/specs/2026-11-10-correcciones-prueba-propietario-design.md` (aquí, «spec»). Amplía `docs/superpowers/specs/2026-11-09-api-central-design.md` («spec base»). Quien ejecute una tarea lee los dos.

## Restricciones globales

Valen todas las del plan `2026-11-09-fase-1-api-central.md`, apartado «Restricciones globales». Las que más pesan aquí:

- **TDD:** primero la prueba, verla fallar, luego el código.
- **Capas:** `odin-domain` solo `java.*`; `odin-application` solo `odin-domain` y `java.*`; Spring solo en los adaptadores y en `odin-bootstrap`. ArchUnit lo comprueba.
- **Métodos de 30 líneas como máximo** (Checkstyle y ESLint). Comentarios mínimos.
- **Identificadores en inglés y camelCase.** Mensajes al usuario, documentación y commits en español.
- **Aislamiento (D-014):** cada caso de uso con actor lleva una prueba con un actor de otra empresa.
- **Maven** siempre desde `api/`, con su repositorio local. **npm** con `node_modules` locales. Nada global.
- **Contrato:** si cambia la API, se regenera en la misma tarea:
  - `cd api && mvn -q verify -Dodin.updateOpenApi=true` reescribe `api/openapi.json`;
  - `cd panel && npm run generate:types` y `cd mcp && npm run generate:types` regeneran los tipos.
- **Cierre de cada tarea:** `make check` termina con código de salida 0.
- **Git:** cada tarea en su rama `task/api-central/<tarea>`, que sale de `feature/api-central`. Commits en español, sin atribución a Claude ni `Co-Authored-By`. Sin push. El merge `--no-ff` lo hace el CTO.
- **Datos:** solo ficticios. **Secretos:** ni en logs ni en el repositorio.

## Foco de revisión

Situaciones que el spec implica y que más pueden fallarle a quien use Odín:

1. **Usuario borrado con una clave o una sesión abiertas:** la siguiente petición da 401. Prueba en la tarea 2 (`ResolveActorTest`).
2. **Correo de un usuario borrado:** se puede volver a usar; el de uno desactivado, no. Pruebas en la tarea 2 (`RegisterUserTest` y `PostgresIdentityRepositoriesIT`).
3. **Administrador que se bloquea solo:** no puede desactivarse ni borrarse, y la recuperación con el token funciona aunque esté desactivado. Pruebas en las tareas 2 y 3.
4. **Contraseña cambiada:** las sesiones anteriores dejan de valer. Prueba en la tarea 3 (`ChangePasswordTest`).
5. **Proyecto borrado con documentos:** el documento sigue saliendo en la búsqueda de su empresa con su cita, y filtrar por ese proyecto da 404. Prueba en la tarea 4 (`ApplicationFlowIT`).

---

## Mapa de ficheros

Rutas bajo `api/<módulo>/src/main/java/com/e2d/odin/`, salvo que se diga otra cosa.

| Fichero | Tarea | Qué pasa |
|---|---|---|
| `application/port/AdminSessions.java` | 1 | Nuevo: el puerto del spec 6 |
| `application/identity/StoredAdminSessions.java` | 1 | Nuevo: la sesión de hoy |
| `application/port/AdminSessionRepository.java` | 1 | Añade `deleteByUser(UserId)` |
| `application/identity/{LogInAdmin,LogOutAdmin,ResolveActor}.java` | 1 | Pasan a usar `AdminSessions` |
| `domain/identity/User.java` | 2 | Añade `withDisplayName`, `withEmail` y `withActive`; desaparece `deactivated()` |
| `application/identity/UpdateUser.java`, `UserChanges.java` | 2 | Nuevos. Desaparece `DeactivateUser` |
| `application/identity/DeleteUser.java` | 2 | Nuevo |
| `application/port/UserRepository.java` | 2 | Añade `delete(UserId, Instant)`; las lecturas excluyen los borrados |
| `adapter/postgres/.../db/migration/V5__user_soft_delete.sql` | 2 | Nuevo |
| `application/identity/ChangePassword.java`, `RecoverGroupAdmin.java` | 3 | Nuevos |
| `application/shared/InstallationToken.java` | 3 | Nuevo: la comprobación del token que hoy está en `SetUpInstallation` |
| `application/organization/{RenameProject,DeleteProject}.java` | 4 | Nuevos |
| `adapter/postgres/.../db/migration/V6__project_soft_delete.sql` | 4 | Nuevo |
| `bootstrap/DatabaseCredentialsFailureAnalyzer.java` | 5 | Nuevo |
| `panel/src/components/{Projects,Users,UserForm}.tsx`, `panel/src/pages/Account.tsx` | 6 | Nuevos; `pages/Company.tsx` se queda como contenedor |
| `panel/e2e/admin.spec.ts` | 7 | Nuevo |

## Interfaces compartidas (fuente única de nombres)

```java
// application/port
public interface AdminSessions {
    String open(UserId userId);
    Optional<UserId> resolve(String token);
    void close(String token);
    void closeAllOf(UserId userId);
}

// application/identity
public record UserChanges(Optional<String> displayName, Optional<String> email, Optional<Boolean> active) { }
public class UpdateUser        { public User execute(Actor actor, UserId id, UserChanges changes) }
public class DeleteUser        { public void execute(Actor actor, UserId id) }
public class ChangePassword    { public void execute(Actor actor, UserId target, Optional<String> currentPassword, String newPassword) }
public class RecoverGroupAdmin { public void execute(String setupToken, String newPassword) }

// application/organization
public class RenameProject { public Project execute(Actor actor, CompanyId companyId, ProjectId id, String name) }
public class DeleteProject { public void execute(Actor actor, CompanyId companyId, ProjectId id) }

// puertos que crecen
UserRepository:         void delete(UserId id, Instant at);  Optional<User> findGroupAdmin();
AdminSessionRepository: void deleteByUser(UserId userId);
OrganizationRepository: void deleteProject(CompanyId companyId, ProjectId id, Instant at);
```

DTO nuevos en `adapter/web/dto/Requests.java`: `UpdateUser(String displayName, String email, Boolean active)`, `SetPassword(String currentPassword, String newPassword)` y `RecoverAdmin(String setupToken, String newPassword)`. Desaparece `UserActive`.

---

### Tarea 1: Sesión del panel detrás de un puerto · Sonnet

Rama `task/api-central/puerto-sesion`. Refactorización: no cambia el contrato ni el comportamiento.

**Ficheros:**
- **Crear:** `application/port/AdminSessions.java`, `application/identity/StoredAdminSessions.java`, y su prueba `StoredAdminSessionsTest`.
- **Modificar:**
  - `application/port/AdminSessionRepository.java`, `adapter/postgres/PostgresAdminSessionRepository.java` y el doble `InMemoryAdminSessionRepository`;
  - `LogInAdmin`, `LogOutAdmin` y `ResolveActor`, con sus pruebas;
  - `bootstrap/UseCaseConfiguration.java` y `odin-adapter-web/src/test/.../TestWiring.java`.

**Interfaces que produce:** `AdminSessions` y `AdminSessionRepository.deleteByUser`, como en «Interfaces compartidas».

**Valores:**
- `StoredAdminSessions(AdminSessionRepository, SecretHasher, TokenGenerator, SessionSettings, Clock)`. Token de 32 bytes, como hoy.
- `resolve` con un token nulo devuelve vacío. Con uno caducado, borra la fila y devuelve vacío.
- Constructores nuevos: `LogInAdmin(UserRepository, PasswordHasher, AdminSessions)`, `LogOutAdmin(AdminSessions)` y `ResolveActor(AccessKeyRepository, UserRepository, AdminSessions, SecretHasher)`.
- `ResolveActor.fromSessionToken` sigue exigiendo que el usuario exista y esté activo.

- [x] **Paso 1:** escribe `StoredAdminSessionsTest` y comprueba que falla:
  - `openedTokenResolvesToItsUser`;
  - `unknownOrNullTokenResolvesToEmpty`;
  - `idleSessionExpiresAndIsDeleted`;
  - `resolveRenewsIdleTime`;
  - `closedTokenNoLongerResolves`;
  - `closeAllOfClosesEverySessionOfThatUserOnly`.
- [x] **Paso 2:** implementa el puerto, `StoredAdminSessions` y `deleteByUser` en el doble y en PostgreSQL. Añade `deleteByUserRemovesOnlyThatUsersSessions` a `PostgresIdentityRepositoriesIT`.
- [x] **Paso 3:** cambia `LogInAdmin`, `LogOutAdmin` y `ResolveActor` para que usen `AdminSessions`, y adapta sus pruebas y los dos ficheros de cableado. Las pruebas que ya existían siguen pasando sin cambiar lo que comprueban.
- [x] **Paso 4:** `make check` termina con 0. `api/openapi.json` no cambia.
- [x] **Paso 5:** commit «refactor: sesión del panel detrás del puerto AdminSessions».

### Tarea 2: Usuarios: alta por papel, edición, activación y borrado lógico · Sonnet

Rama `task/api-central/usuarios-crud`. Spec 4.1, 4.2 y 4.3.

**Ficheros:**
- **Crear:** `UpdateUser`, `UserChanges`, `DeleteUser`, `V5__user_soft_delete.sql`, y las pruebas `UpdateUserTest` y `DeleteUserTest`.
- **Borrar:** `DeactivateUser` y `DeactivateUserTest`; sus casos pasan a `UpdateUserTest`.
- **Modificar:**
  - `domain/identity/User.java`, `RegisterUser`, `UserRepository`, `PostgresUserRepository` y el doble `InMemoryUserRepository`;
  - `adapter/web/admin/UserController.java` y `dto/Requests.java`;
  - `UseCaseConfiguration`, `TestWiring`;
  - `api/openapi.json` y los tipos del panel y del MCP;
  - `panel/src/api/client.ts` solo si deja de compilar.

**Interfaces que consume:** nada de la tarea 1.
**Interfaces que produce:** `UpdateUser`, `DeleteUser`, `UserChanges`, `UserRepository.delete` y las rutas `PATCH` y `DELETE /v1/admin/users/{userId}`.

**Valores:**
- Mensajes y códigos: los del spec 4.1, 4.2 y 4.3, literales.
- `V5`: añade `app_user.deleted_at timestamptz`; sustituye `app_user_email_key` por el índice único sobre `lower(email)` con `WHERE email IS NOT NULL AND deleted_at IS NULL`.
- `findById`, `findByEmail` y `listByCompany` excluyen los borrados. `PostgresKnowledgeSearch` no se toca: su `JOIN app_user` sigue encontrando al autor borrado.
- `UpdateUser` comprueba el correo repetido con `findByEmail` antes de guardar; el mismo usuario con su mismo correo no es conflicto.

- [x] **Paso 1:** pruebas de casos de uso; comprueba que fallan:
  - `RegisterUserTest`: `workerWithPasswordIsRejected`, `workerWithoutEmailIsCreated` y `deletedUsersEmailCanBeReused`;
  - `UpdateUserTest`: `changesNameAndEmail`, `duplicateEmailIsConflict`, `sameEmailOnSameUserIsNotConflict`, `blankEmailClearsWorkersEmail`, `blankEmailOnAdminIsRejected`, `deactivatesAndReactivates`, `cannotDeactivateOneself`, `adminOfAnotherCompanyIsForbidden` y `deletedUserIsNotFound`;
  - `DeleteUserTest`: `deletedUserDisappearsFromList`, `cannotDeleteOneself`, `adminOfAnotherCompanyIsForbidden` y `deletingTwiceIsNotFound`;
  - `ResolveActorTest`: `deletedWorkersKeyIsUnauthorized` y `deletedAdminsSessionIsUnauthorized`.
- [x] **Paso 2:** implementa los casos de uso y el doble hasta que pasen.
- [x] **Paso 3:** `PostgresIdentityRepositoriesIT`: `deletedUserIsExcludedFromReads`, `deletedUsersEmailIsFreeButInactiveUsersIsNot`. Escribe `V5` y adapta `PostgresUserRepository`.
- [x] **Paso 4:** web. En `AdminApiTest`: `patchUserUpdatesFields`, `patchUserReactivates`, `deleteUserIs204` y `workerWithPasswordIs400`. En `IsolationTest`: `otherCompanyAdminCannotPatchOrDeleteUser`. Implementa el controlador y los DTO.
- [x] **Paso 5:** `ApplicationFlowIT`: `deletedAuthorStillNamedInSearchHit`: un administrador sube un documento, se le borra, y la búsqueda del trabajador sigue devolviendo el resultado con `authorName`.
- [x] **Paso 6:** regenera el contrato y los tipos. `make check` termina con 0.
- [x] **Paso 7:** commit «feat: edición, activación y borrado lógico de usuarios».

### Tarea 3: Contraseñas: cambio, restablecimiento y recuperación · Sonnet

Rama `task/api-central/contrasenas`. Spec 4.4 y 4.5.

**Ficheros:**
- **Crear:** `ChangePassword`, `RecoverGroupAdmin`, `application/shared/InstallationToken.java`, y las pruebas `ChangePasswordTest` y `RecoverGroupAdminTest`.
- **Modificar:**
  - `SetUpInstallation` (usa `InstallationToken`), `UserRepository`, `PostgresUserRepository` y el doble;
  - `adapter/web/admin/UserController.java`, `adapter/web/setup/SetupController.java`, `dto/Requests.java` y `security/ActorResolutionFilter.java` (ruta pública);
  - `UseCaseConfiguration`, `TestWiring`, `api/openapi.json` y los tipos.

**Interfaces que consume:** `AdminSessions.closeAllOf` (tarea 1) y `User.withActive` (tarea 2).
**Interfaces que produce:** `ChangePassword`, `RecoverGroupAdmin`, `UserRepository.findGroupAdmin` y las rutas `PUT /v1/admin/users/{userId}/password` y `POST /v1/setup/admin-password`.

**Valores:**
- Mensajes y códigos: los del spec 4.4 y 4.5, literales.
- `InstallationToken.require(InstallationSettings settings, String token)`: lanza `ForbiddenException("Código de instalación no válido")`. Es el código que hoy tiene `SetUpInstallation.requireValidToken`, movido.
- `User` necesita `withPasswordHash(String)`.
- `SetupController` pasa por `LoginRateLimiter.check("setup", ip)` antes de llamar a `RecoverGroupAdmin`. La IP se obtiene como en `SessionController`.

- [x] **Paso 1:** pruebas; comprueba que fallan:
  - `ChangePasswordTest`: `adminChangesOwnPasswordWithCurrentOne`, `wrongCurrentPasswordIsRejected`, `missingCurrentPasswordIsRejected`, `groupAdminResetsCompanyAdminWithoutCurrentOne`, `companyAdminCannotResetAnotherUser`, `workerHasNoPassword`, `shortPasswordIsRejected`, `changingPasswordClosesAllSessionsOfThatUser`, `oldPasswordNoLongerLogsIn` y `groupAdminCannotResetUserOfUnknownId`;
  - `RecoverGroupAdminTest`: `validTokenSetsPasswordReactivatesAndClosesSessions`, `invalidTokenIsForbidden`, `emptyConfiguredTokenIsForbidden`, `notConfiguredInstallationIsConflict` y `shortPasswordIsRejected`;
  - `SetUpInstallationTest` sigue pasando sin cambios.
- [x] **Paso 2:** implementa `InstallationToken`, los dos casos de uso, `findGroupAdmin` y `withPasswordHash`.
- [x] **Paso 3:** web. `AdminApiTest`: `putPasswordIs204` y `putPasswordWithWrongCurrentIs400`. `AuthenticationTest`: `recoverAdminIsPublicAndNeedsToken` y `sixthRecoverAttemptIs429`. `IsolationTest`: `companyAdminCannotResetUserOfAnotherCompany`.
- [x] **Paso 4:** `PostgresIdentityRepositoriesIT`: `findGroupAdminReturnsTheOnlyGroupAdmin`.
- [x] **Paso 5:** regenera el contrato y los tipos. `make check` termina con 0.
- [x] **Paso 6:** commit «feat: cambio de contraseña y recuperación del administrador del grupo».

### Tarea 4: Proyectos: renombrar, borrado lógico y nombre único · Sonnet

Rama `task/api-central/proyectos-crud`. Spec 5.

**Ficheros:**
- **Crear:** `RenameProject`, `DeleteProject`, `V6__project_soft_delete.sql`, y las pruebas `RenameProjectTest` y `DeleteProjectTest`.
- **Modificar:**
  - `domain/organization/Project.java` (`withName`), `CreateProject`, `OrganizationRepository`, `PostgresOrganizationRepository` y el doble `InMemoryOrganizationRepository`;
  - `adapter/web/admin/CompanyController.java`;
  - `UseCaseConfiguration`, `TestWiring`, `api/openapi.json` y los tipos.

**Interfaces que produce:** `RenameProject`, `DeleteProject`, `OrganizationRepository.deleteProject` y las rutas `PATCH` y `DELETE /v1/admin/companies/{companyId}/projects/{projectId}`.

**Valores:**
- Mensajes y códigos: los del spec 5, literales.
- `V6`: añade `project.deleted_at timestamptz` y el índice único `(company_id, lower(name)) WHERE deleted_at IS NULL`.
- `findProject` y `listProjects` excluyen los borrados. Los documentos y las fichas no se tocan.
- El nombre repetido lo comprueban `CreateProject` y `RenameProject` con `listProjects`, sin distinguir mayúsculas y tras recortar espacios. El índice es la última barrera.
- Renombrar un proyecto a su mismo nombre no es conflicto.

- [x] **Paso 1:** pruebas; comprueba que fallan:
  - `CreateProjectTest`: `duplicateNameInSameCompanyIsConflict`, `sameNameInAnotherCompanyIsAllowed` y `nameOfDeletedProjectCanBeReused`;
  - `RenameProjectTest`: `renames`, `duplicateNameIsConflict`, `sameNameIsNotConflict`, `blankNameIsRejected`, `projectOfAnotherCompanyIsNotFound` y `adminOfAnotherCompanyIsForbidden`;
  - `DeleteProjectTest`: `deletedProjectDisappearsFromListAndMe`, `deletingTwiceIsNotFound` y `adminOfAnotherCompanyIsForbidden`.
- [x] **Paso 2:** implementa los casos de uso y el doble.
- [x] **Paso 3:** `PostgresOrganizationRepositoryIT`: `deletedProjectIsExcludedFromReads` y `uniqueNameIndexIgnoresDeletedProjects`. Escribe `V6`.
- [x] **Paso 4:** web. `AdminApiTest`: `patchProjectRenames`, `deleteProjectIs204` y `duplicateProjectNameIs409`. `IsolationTest`: `otherCompanyAdminCannotRenameOrDeleteProject`.
- [x] **Paso 5:** `ApplicationFlowIT`: `documentOfDeletedProjectStaysSearchableAndFilterIs404`.
- [x] **Paso 6:** regenera el contrato y los tipos. `make check` termina con 0.
- [x] **Paso 7:** commit «feat: renombrar y borrar proyectos, con nombre único por empresa».

### Tarea 5: Error de arranque claro y `README` de operación · Haiku

Rama `task/api-central/operacion`. Spec 7.

**Ficheros:**
- **Crear:**
  - `api/odin-bootstrap/src/main/java/com/e2d/odin/bootstrap/DatabaseCredentialsFailureAnalyzer.java`;
  - `api/odin-bootstrap/src/main/resources/META-INF/spring.factories`;
  - `api/odin-bootstrap/src/test/java/com/e2d/odin/bootstrap/DatabaseCredentialsFailureAnalyzerTest.java`.
- **Modificar:** `README.md`.

**Valores:**
- `DatabaseCredentialsFailureAnalyzer extends AbstractFailureAnalyzer<SQLException>`. Devuelve un `FailureAnalysis` solo si `getSQLState()` es `"28P01"`; si no, `null`.
- Descripción y acción: los dos textos del spec 7, literales.
- `spring.factories`: `org.springframework.boot.diagnostics.FailureAnalyzer=com.e2d.odin.bootstrap.DatabaseCredentialsFailureAnalyzer`.
- `README`: los cuatro apartados del spec 7. El de «Claves de la base de datos» incluye:
  - la orden `docker compose -f deploy/compose.yaml --env-file deploy/.env exec postgres psql -U <usuario> -d <base> -c "ALTER ROLE <rol> PASSWORD '<clave nueva>'"`, para `odin_app` y para el usuario propietario;
  - el orden: primero `ALTER ROLE`, después `deploy/.env`, después `make down` y `make up`.
- El apartado «Contraseña olvidada» lleva la orden `curl` de `POST /v1/setup/admin-password`, con la forma de la que ya hay en «Primer arranque».

- [x] **Paso 1:** escribe `DatabaseCredentialsFailureAnalyzerTest` y comprueba que falla:
  - `wrongPasswordStateGivesActionableAnalysis`: con `new IllegalStateException(new SQLException("x", "28P01"))`, el análisis no es nulo y su acción contiene `docker volume rm odin_pgdata odin_originals`;
  - `otherSqlStatesAreIgnored`: con el estado `"08001"`, devuelve `null`.
- [x] **Paso 2:** implementa el analizador y regístralo.
- [x] **Paso 3:** escribe los cuatro apartados del `README`.
- [x] **Paso 4:** `make check` termina con 0.
- [x] **Paso 5:** commit «feat: error de arranque claro con claves rechazadas y README de operación».

### Tarea 6: Panel: gestión de usuarios y proyectos, y mi cuenta · Sonnet

Rama `task/api-central/panel-gestion`. Spec 8. Sin cambios de aspecto: mismas clases CSS y mismos elementos.

**Ficheros:**
- **Crear:** `panel/src/components/Projects.tsx`, `Users.tsx` y `UserForm.tsx`; `panel/src/pages/Account.tsx`; sus pruebas junto a cada uno (`*.test.tsx`).
- **Modificar:** `panel/src/pages/Company.tsx` (queda como contenedor), `panel/src/App.tsx` (página «Mi cuenta» y nombre en la navegación), `panel/src/api/client.ts` y `panel/src/mockApi.ts`.

**Interfaces que consume:** las rutas de las tareas 2, 3 y 4, con los tipos generados.

**Valores:**
- Funciones nuevas en `api` (`client.ts`): `updateUser(userId, changes)`, `deleteUser(userId)`, `setPassword(userId, body)`, `renameProject(companyId, projectId, name)` y `deleteProject(companyId, projectId)`. `setActive` pasa a llamar a `updateUser`.
- Textos de los botones: «Editar», «Guardar», «Cancelar», «Borrar», «Activar», «Desactivar», «Restablecer contraseña», «Renombrar» y «Cambiar contraseña». Cada botón de fila lleva el nombre de su usuario o proyecto, como hoy («Borrar Ana»).
- Confirmación antes de borrar: `window.confirm('¿Borrar a <nombre>? No se puede deshacer.')` y `window.confirm('¿Borrar el proyecto <nombre>? Sus documentos se conservan.')`.
- La fila de usuario muestra: nombre · correo o «sin correo» · «Trabajador» o «Administrador» · «activo» o «desactivado».
- «Claves» solo en trabajadores. «Restablecer contraseña» solo en administradores de empresa y solo si `me.role === 'GROUP_ADMIN'`.
- Tras cambiar la contraseña propia: la aplicación vuelve a la entrada y muestra «Contraseña cambiada. Entra de nuevo.».
- Ningún formulario se vacía si la acción falla (hoy `AddForm` y `Keys` lo hacen).

- [x] **Paso 1:** pruebas con el doble; comprueba que fallan:
  - `UserForm.test.tsx`: `workerFormHasNoPasswordAndEmailIsOptional`, `adminFormRequiresPassword` y `failedCreateKeepsTheTypedValues`;
  - `Users.test.tsx`: `rowShowsEmailRoleAndState`, `activateSendsActiveTrue`, `editSavesNameAndEmail`, `conflictShowsTheApiMessage`, `deleteAsksForConfirmationAndRemovesTheRow`, `cancelledConfirmationDeletesNothing`, `keysOnlyForWorkers` y `resetPasswordOnlyForGroupAdminOnCompanyAdmins`;
  - `Projects.test.tsx`: `renamesProject`, `deleteAsksForConfirmation` y `duplicateNameShowsTheApiMessage`;
  - `Account.test.tsx`: `changesOwnPasswordAndReturnsToLoginWithNotice` y `wrongCurrentPasswordShowsTheApiMessage`.
- [x] **Paso 2:** implementa los componentes hasta que pasen. Las 8 pruebas que ya existían siguen pasando.
- [x] **Paso 3:** `make check` termina con 0.
- [x] **Paso 4:** commit «feat: panel con gestión completa de usuarios y proyectos y cambio de contraseña».

### Tarea 7: Prueba de extremo a extremo de la administración · Sonnet

Rama `task/api-central/e2e-administracion`. Spec 9.

**Ficheros:**
- **Crear:** `panel/e2e/admin.spec.ts`.
- **Modificar:** `deploy/demo/run-demo.sh` solo si hace falta para que se ejecute; `README.md` si cambia lo que hace `make demo`.

**Valores:**
- Corre en la pila de `make demo`, contra la API real, con el administrador del grupo que deja `DEMO_STATE`.
- Trabaja en una empresa propia, «Empresa E2E», para no tocar las de `demo.spec.ts`.
- **Límite de intentos:** el login admite 5 por minuto y por IP. Entre los dos ficheros no puede haber más de 4 entradas por minuto: reutiliza la sesión con `storageState` y deja para el final los pasos que entran de nuevo.
- La contraseña del administrador del grupo queda al terminar como estaba, para no romper otros pasos de la demo.

- [x] **Paso 1:** escribe `admin.spec.ts` con estos pasos, cada uno con su `console.log('✓ …')` como en `demo.spec.ts`:
  1. crea «Empresa E2E» y la abre;
  2. crea un trabajador sin correo ni contraseña y lo ve en la lista como «sin correo»;
  3. crea un administrador de empresa con correo y contraseña;
  4. desactiva al trabajador y lo vuelve a activar;
  5. edita su nombre y le pone correo; con el correo del administrador de empresa aparece «Ya existe un usuario con ese correo»;
  6. le da una clave personal y la ve una sola vez;
  7. lo borra: desaparece de la lista, y se puede crear otro usuario con su mismo correo;
  8. crea un proyecto, lo renombra, comprueba que otro con el mismo nombre da «Ya existe un proyecto con ese nombre», y lo borra;
  9. restablece la contraseña del administrador de empresa, sale, y entra con él y la contraseña nueva: ve su empresa y no ve «Empresas»;
  10. en «Mi cuenta», ese administrador cambia su contraseña: vuelve a la entrada con «Contraseña cambiada. Entra de nuevo.» y entra con la nueva.
- [x] **Paso 2:** ejecuta `make demo`. Si un paso falla por un fallo real del panel o de la API, corrígelo con su prueba unitaria en el módulo que toque; no adaptes la prueba al fallo.
- [x] **Paso 3:** `make demo` y `make check` terminan con 0.
- [x] **Paso 4:** commit «test: prueba de extremo a extremo de la administración del panel».

## Orden y paralelismo

En serie y en este orden: 1, 2, 3, 4, 5, 6, 7. Las tareas 2, 3 y 4 cambian el contrato y los mismos ficheros de cableado; la 6 necesita el contrato final y la 7 necesita el panel.

Al terminar la 7, el CTO ejecuta `make check` y `make demo` sobre `feature/api-central` y repite a mano el recorrido del spec 10.
