# Odín · Varios administradores del grupo, desactivar en todo el grupo y códigos migrados · Plan

> Se ejecuta con `superpowers:subagent-driven-development`. Diseño: `docs/superpowers/specs/2026-11-12-administracion-grupo-design.md` («spec»).

## Restricciones globales

Son las del plan de la fase 2a (`2026-11-10-fase-2a-identidad-pertenencias.md`):
- TDD con el rojo guardado;
- capas comprobadas con ArchUnit;
- métodos de 30 líneas como máximo;
- identificadores en inglés y textos al usuario en español;
- aislamiento D-014 con una prueba con actor de otra empresa;
- borrados lógicos;
- contrato y tipos regenerados en la misma tarea;
- `make check` y `make demo` terminan con 0;
- commits en español sin atribución, sin push.

Además:
- no se tocan la búsqueda, los documentos ni el MCP;
- la migración nueva se llama `V11__company_code_reviewed.sql` y se renumera al integrar si choca.

## Tarea 1: Varios administradores del grupo (B-007) · Sonnet

Rama `task/administracion-grupo/varios-administradores`. Spec 1.

**Valores:** los del spec 1, con sus mensajes exactos.

**Ficheros:**
- casos de uso nuevos en `application/identity`: `ListGroupAdmins`, `AddGroupAdmin` y `RevokeGroupAdmin`;
- `UserRepository.findGroupAdmin()` pasa a `listGroupAdmins()` y `findGroupAdminByEmail(...)`;
- en `UserAdministration`: `requireAnotherActiveGroupAdmin(user)`, que llaman `UpdateUser` (al desactivar), `DeleteUser` y `RevokeGroupAdmin`;
- `RecoverGroupAdmin` con `email` opcional;
- controlador, contrato, tipos, panel (`pages/GroupAdmins.tsx` y la entrada en la barra lateral), `admin.spec.ts` y `README`.

**Pasos:**
- [ ] **Paso 1:** pruebas que fallan:
  - `ListGroupAdminsTest`: solo para el administrador del grupo; un administrador de empresa recibe 403;
  - `AddGroupAdminTest`: `createsANewPersonWithItsOwnCode`, `existingPersonGetsTheMarkAndKeepsNameAndCode`, `passwordOnlyWhenMissing`, `emailIsRequired` y `companyAdminIsForbidden`;
  - `RevokeGroupAdminTest`: `revokesTheMarkAndClosesSessionsIfNoLongerAdministering`, `cannotRevokeTheLastActiveGroupAdmin`, `cannotRevokeOneself` y `companyAdminIsForbidden`;
  - `UpdateUserTest.cannotDeactivateTheLastActiveGroupAdmin` y `DeleteUserTest.cannotDeleteTheLastActiveGroupAdmin`, cada una con su caso que sí funciona cuando queda otro;
  - `RecoverGroupAdminTest`: `withSeveralGroupAdminsTheEmailIsRequired`, `recoversTheOneWithThatEmail` y `unknownEmailIs404`;
  - Postgres IT de `listGroupAdmins` y de `findGroupAdminByEmail`, sin las personas borradas.
- [ ] **Paso 2:** implementa el dominio, los casos de uso y el adaptador.
- [ ] **Paso 3:** web y contrato. `AdminApiTest` con una prueba por ruta. `IsolationTest.companyAdminCannotManageGroupAdmins`.
- [ ] **Paso 4:** panel.
  - página «Administración del grupo» según el spec 1;
  - pruebas de componente para el alta, desactivar y activar, quitar la marca y borrar, cada una con su `ConfirmDialog`;
  - comprueba que la fila propia no tiene acciones;
  - pantalla en `visual.spec.ts` con axe.
- [ ] **Paso 5:** `admin.spec.ts`. El administrador del grupo añade un segundo administrador del grupo. Ese segundo entra en el panel, desactiva al primero y el primero ya no puede entrar. Al final se reactiva al primero para no romper los pasos siguientes. `README`: apartado «Cómo cambiar al administrador del grupo» con estas pantallas; el token de instalación queda solo para emergencias, con `email`.
- [ ] **Paso 6:** `make check` y `make demo` terminan con 0. Commit «feat: varios administradores del grupo y relevo sin renombrar la cuenta».

## Tarea 2: Desactivar a una persona en todo el grupo (B-005) · Haiku

Rama `task/administracion-grupo/desactivar-en-grupo`, que sale de la tarea 1. Spec 2.

- [ ] **Paso 1:** pruebas que fallan:
  - `ListMembersTest`: `personActive`;
  - `Members.test.tsx`: `groupAdminDeactivatesInTheWholeGroupAfterConfirming`, `hiddenForCompanyAdmins` y `inactivePersonIsFlagged`.
- [ ] **Paso 2:** implementa `MemberView.personActive`, el contrato, los tipos y el panel con los textos del spec 2.
- [ ] **Paso 3:** `make check` termina con 0. Commit «feat: desactivar a una persona en todo el grupo».

## Tarea 3: Códigos de empresa migrados (B-006) · Haiku

Rama `task/administracion-grupo/codigos-migrados`, que sale de la tarea 2. Spec 3.

- [ ] **Paso 1:** pruebas que fallan:
  - migración: `existingCompaniesNeedTheirCodeReviewed`;
  - `CreateCompanyTest.newCompanyCodeIsReviewed`;
  - `UpdateCompanyTest.savingMarksTheCodeReviewed`;
  - `Companies.test.tsx`: `unreviewedCodeIsFlaggedUntilSaved`.
- [ ] **Paso 2:** escribe `V11` e implementa `Company.codeReviewed`, el contrato, los tipos y el panel.
- [ ] **Paso 3:** `make check` y `make demo` terminan con 0. Commit «feat: códigos de empresa migrados por revisar».

## Tarea 4: Selector de empresa en el menú y «Grupo» (B-008, B-009) · Haiku

Rama `task/administracion-grupo/menu-grupo`. Petición del propietario, transmitida por el CEO el 2026-11-12. Solo panel.

**Valores:**
- **Administrador del grupo:** en el hueco de la empresa de la barra lateral, siempre un selector con la etiqueta «Empresa» y todas las empresas del grupo.
  - Si no ha elegido ninguna, la primera opción es «Elige una empresa».
  - Al elegir una, se abre «Empresa».
  - La empresa actual queda a la vista.
  - Si se crea o se renombra una empresa, la lista se actualiza.
- **Administrador de empresa:** como hoy. Con una sola empresa, el nombre; con varias, el selector.
- **Sin empresa elegida:** el aviso pasa a ser «Elige una empresa en el menú.».
- **«Empresas» pasa a «Grupo»:** en el enlace del menú y en el título de la página. Los identificadores en inglés no cambian.
- El selector sigue el estilo de `CompanySwitcher` sobre la barra oscura.

- [ ] **Paso 1:** pruebas que fallan:
  - `Sidebar.test.tsx`: `groupAdminAlwaysSeesTheSwitcherWithAllCompanies`, `noCompanyChosenShowsThePrompt` y `menuSaysGrupo`;
  - `AppShell` o `CompanySwitcher`: `choosingACompanyOpensIt`;
  - `Companies.test.tsx`: `pageTitleIsGrupo`.
- [ ] **Paso 2:** implementa el selector y el cambio de nombre.
- [ ] **Paso 3:**
  - en `admin.spec.ts`, el administrador del grupo elige una empresa en el selector del menú, se abre «Empresa» con su nombre, y el menú dice «Grupo»;
  - actualiza los selectores que buscaban «Empresas»;
  - axe del selector del administrador del grupo en `visual.spec.ts`.
- [ ] **Paso 4:** `make check` y `make demo` terminan con 0. Commit «feat: selector de empresa en el menú y «Grupo» (B-008, B-009)».

## Orden

Las tareas van en serie. Revisión de la feature con Opus, centrada en los permisos de la tarea 1. Después, `make check` y `make demo` ejecutados por el CTO.
