# Riesgos de arquitectura — plan de implementación

> Ejecución con subagentes, sin rondas de revisión (instrucción del propietario del 2026-11-15). Cada tarea se verifica con sus pruebas y la feature con `make check` y el ensayo de la V33.

**Goal:** RLS en todas las tablas, atomicidad declarada y vigilada, y ArchUnit sobre bootstrap y adaptadores.

**Spec:** `docs/superpowers/specs/2026-11-16-riesgos-arquitectura-design.md`

## Global Constraints

- Cada tarea va en `task/riesgos-arquitectura/<tarea>`, que sale de `feature/riesgos-arquitectura`, y se fusiona con `--no-ff`. Sin push.
- Commits en español, sin atribución a Claude ni `Co-Authored-By`.
- TDD. Métodos de 30 líneas como máximo. Identificadores en inglés. Comentarios mínimos.
- Migración única: `V33__identity_row_level_security.sql`. No se tocan las migraciones ya escritas: Flyway guarda su suma de comprobación.
- No tocar `deploy/.env`, la pila del propietario ni sus puertos (8080, 8088, 8090). No hacer `mvn install`. Al terminar, ningún contenedor de Testcontainers propio en marcha. El disco del Mac está al 99 %.
- Orden: Tarea 1 y Tarea 3 a la vez; la Tarea 2 cuando la 3 esté fusionada.

## Review Focus

1. Un trabajador sigue encontrando un documento subido por un administrador del grupo, y otro subido por alguien que ya no pertenece a la empresa.
2. Iniciar sesión en el panel, usar una clave de trabajador y usar la clave de chat del administrador siguen funcionando: son búsquedas sin ámbito de empresa.
3. Crear una persona y su pertenencia (alta de trabajador) no choca con la política de `app_user`: la persona se escribe antes de tener pertenencia.
4. Una cola en segundo plano no queda envuelta en una transacción: el estado «en lectura» se ve mientras lee.
5. Dos llamadas `inScope` seguidas dentro de una transacción `@Atomic` no se pasan el ámbito de proyectos.

---

### Task 1: RLS en las tablas de identidad

Rama: `task/riesgos-arquitectura/rls`.

**Files:**
- `api/odin-adapter-postgres/src/main/resources/db/migration/V33__identity_row_level_security.sql` (nuevo): `ENABLE ROW LEVEL SECURITY` y una política por tabla, como en la tabla de la spec, con la misma expresión de ámbito que la V4.
  - En `app_user`, la columna de administrador del grupo es la que tenga hoy el esquema (`group_admin`).
  - Un comentario explica que `admin_chat_key` pasa a tener RLS, aunque la V30 dijera otra cosa.
- `api/odin-bootstrap/.../DatabaseRoleGuard.java`: se niega si el rol es dueño de cualquier tabla del esquema `public`.
- Pruebas: un IT nuevo `IdentityRowLevelSecurityIT` en `odin-adapter-postgres`, con el estilo de `RowLevelSecurityIT`, más `DatabaseRoleGuardIT`.

**Tests:**
- Para cada tabla, con dos empresas A y B, en el ámbito de A:
  - se ven las filas de A y no las de B;
  - en ámbito de grupo se ven todas.
- `app_user`, en el ámbito de A:
  - se ve a un miembro de A, a un administrador del grupo y a un antiguo miembro de A (pertenencia con `deleted_at`);
  - no se ve a quien solo es miembro de B.
- `admin_session` y `odin_group`: en el ámbito de A no se ve nada, y un `INSERT` o un `UPDATE` no afecta a ninguna fila, o lo rechaza la política.
- `admin_chat_key` sin empresa: no se ve en el ámbito de A.
- Búsqueda de conocimiento (`PostgresKnowledgeSearch`) en el ámbito de A: salen un documento subido por un administrador del grupo y otro subido por un antiguo miembro (Review Focus 1).
- `DatabaseRoleGuardIT`: un rol sin privilegios especiales que es dueño de una tabla que no es `document` hace que se niegue a arrancar.
- Siguen en verde todas las pruebas de `odin-adapter-postgres` y `odin-bootstrap`, incluidas las de autenticación y alta de trabajadores (Review Focus 2 y 3).

- [ ] Pruebas que fallan → migración y guarda → verde → commit.

### Task 2: atomicidad declarada

Rama: `task/riesgos-arquitectura/transacciones`. Parte del inventario de `…/scratchpad/encargo26/inventario-transacciones.md`, que te dará el controlador.

**Files:**
- `api/odin-application/.../shared/Atomic.java` y `NonAtomic.java` (nuevas): `@Retention(RUNTIME)`, `@Target(TYPE)`. `NonAtomic` lleva `String reason()`.
- `api/odin-bootstrap/.../AtomicUseCases.java` (nuevo): el `BeanPostProcessor` de la spec, con `ProxyFactory` (`proxyTargetClass`) y `TransactionInterceptor` sobre el `PlatformTransactionManager`.
- `UseCaseConfiguration` y las demás configuraciones de bootstrap: se borran las subclases anónimas que solo envuelven, y su clase pasa a llevar `@Atomic`.
- Los casos de uso con varias escrituras sin envolver que señale el inventario: `@Atomic`, o `@NonAtomic("motivo")` en las colas.
- `ScopedTransactions`: su `TransactionTemplate` pasa a `PROPAGATION_NESTED`, y `inTransaction` sin visibilidad fija las variables de proyecto en su valor restrictivo.
- Las anotaciones de los 9 casos y las 4 colas, como dice la spec (apartado 2, «Inventario»).
- `api/odin-architecture-tests/.../TransactionRulesTest.java` (nuevo): la regla de `@Atomic`/`@NonAtomic` y «bootstrap no usa `TransactionTemplate` fuera de `AtomicUseCases`».

**Tests:**
- Las pruebas de atomicidad que ya hay (`MemberAtomicityIT`, `SetUpAtomicityIT`, `ProjectListAtomicityIT`, `LastGroupAdminIT`) siguen en verde sin cambios: prueban que la migración desde las subclases anónimas conserva el comportamiento.
- Un IT en `odin-bootstrap` por cada caso, con un fallo forzado en la segunda escritura (el registro de actividad o el contador):
  - `LogOutAdmin`: la sesión sigue igual y no hay evento;
  - `UploadDocument` y `UploadByLink`: no queda documento y la plaza del enlace no se gasta;
  - `AddFileToIntake`: no queda documento sin contar.
- Las pruebas de subida duplicada que ya hay siguen en verde con los casos envueltos (punto de guardado).
- Un test de bootstrap: toda bean de una clase `@Atomic` del contexto es un proxy de AOP (`AopUtils.isAopProxy`), y ninguna `@NonAtomic` lo es.
- `ScopedTransactions`: en una transacción externa, una llamada con «todos los proyectos» seguida de otra sin visibilidad no deja ver documentos de proyecto a la segunda (Review Focus 5).
- La regla ArchUnit falla con un caso de uso de prueba sin anotar y con dos escrituras. Basta un test que la aplique a una clase de ejemplo del propio módulo de pruebas.

- [ ] Pruebas que fallan → cambio → `mvn verify` de los módulos tocados en verde → commit.

### Task 3: reglas ArchUnit en bootstrap y adaptadores

Rama: `task/riesgos-arquitectura/archunit`.

**Files:**
- `api/odin-architecture-tests/pom.xml`: depende de `odin-bootstrap`.
- `api/odin-architecture-tests/.../LayerRulesTest.java`: las reglas nuevas de la spec (apartado 3), salvo las de transacciones, que son de la Tarea 2.
- El código que una regla nueva señale, si el arreglo es razonable. Si no, la excepción, anotada en la regla con su motivo.

**Tests:** las propias reglas. Cada regla nueva se ve fallar antes de pasar, por ejemplo con una clase temporal que la incumpla y se borra antes del commit, y eso se cuenta en el informe.

- [ ] Reglas → `mvn -q -pl odin-architecture-tests -am verify` en verde → commit.

### Cierre

1. Merge de las tres tareas y de `develop`.
2. `make check`, y comprobar en `odin-architecture-tests/target/surefire-reports` que se han ejecutado las reglas.
3. Ensayo en la pila `odin-ensayo-cto2`: la copia V29 (`odin-v29-antes-despliegue-actualizar-2026-11-16-091115.sql`) con el código de `develop` sube a la V30, y la feature sube a la V33.
   - Comparar las filas de las tablas antes y después.
   - Entrar con una clave de trabajador y con la sesión del panel.
4. Bajar la pila de ensayo con sus volúmenes e imágenes, y quitar la worktree base.
