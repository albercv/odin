# Encargo 26 · Riesgos del análisis de arquitectura

Fecha: 2026-11-16. Rama: `feature/riesgos-arquitectura`, que sale de `develop` 0b7ffa6 (V30).
Parte de los riesgos del informe del encargo 18 (`docs/informes/2026-11-14-analisis-arquitectura-api.md`, commit 6795314 en `feature/analisis-arquitectura`), apartado 4, comprobados de nuevo sobre `develop`.

## 1. Aislamiento: RLS en las tablas de identidad

**Hoy:** 6 tablas sin RLS.
- Las 5 del informe: `odin_group`, `app_user`, `access_key`, `admin_session` y `company_capability`.
- Y `admin_chat_key`, que llegó con la V30.

En todas, el aislamiento depende solo de la consulta y del caso de uso. Además, la comprobación de arranque (`DatabaseRoleGuard`) solo mira que la API no sea dueña de la tabla `document`.

**Decisión: todas llevan RLS, en la V33.** Las políticas siguen la forma de la V4 (`app.group_scope` y `app.company_ids`).

| Tabla | Política | Por qué así |
|---|---|---|
| `company_capability` | ámbito de grupo, o su `company_id` en el ámbito | Es de una empresa. |
| `access_key` | ámbito de grupo, o su `company_id` en el ámbito | Cada clave es de una pertenencia a una empresa. |
| `admin_chat_key` | ámbito de grupo, o su `company_id` en el ámbito; sin empresa (administrador del grupo), solo en ámbito de grupo | Como `access_key`. La V30 dice que no lleva RLS porque se busca antes de conocer el ámbito; esa búsqueda ya usa el ámbito de grupo, así que la política no la cambia. |
| `app_user` | ámbito de grupo, o administrador del grupo, o con alguna pertenencia (aunque esté borrada) visible en el ámbito | La persona es del grupo y puede estar en varias empresas. La subconsulta sobre `membership` ya pasa por la RLS de `membership`, así que solo ve pertenencias de las empresas del ámbito. |
| `admin_session` | solo ámbito de grupo | No tiene empresa. Solo se usa para entrar al panel, antes de conocer el ámbito. |
| `odin_group` | solo ámbito de grupo | Una sola fila (el grupo, `singleton`). Ningún trabajo de una empresa la necesita; `audit_append` la lee como dueño. |

- **Qué no cambia:**
  - Las búsquedas de autenticación (clave por prefijo, sesión por hash, clave de chat por prefijo) y las escrituras de identidad ya van en ámbito de grupo (`CompanyScope.all()`), así que siguen igual.
  - Lo que cambia es todo lo que corre en el ámbito de una empresa: ya no ve claves, capacidades ni personas de otra, ni sesiones ni el grupo.
- **Riesgo que se vigila con pruebas:**
  - La búsqueda de conocimiento une `document` y `knowledge_card` con `app_user` con un `JOIN` interno, en el ámbito de la empresa.
  - Si la política de `app_user` escondiera al autor, el documento desaparecería de la búsqueda.
  - Por eso la política deja ver:
    - a los administradores del grupo;
    - a quien tuvo pertenencia en la empresa, aunque se haya borrado: las pertenencias se borran marcándolas (`deleted_at`), no con `DELETE`.
  - Hay una prueba de que un documento subido por un administrador del grupo, y otro subido por alguien que ya no pertenece a la empresa, siguen saliendo en la búsqueda de un trabajador.
- **Comprobación de arranque:** `DatabaseRoleGuard` se niega a arrancar si el rol de la API es dueño de cualquier tabla del esquema `public`, no solo de `document`. El dueño se salta la RLS, porque no hay `FORCE ROW LEVEL SECURITY`.
- **Orden de migraciones (aviso para el CEO):**
  - Flyway valida el orden y no tiene `out-of-order`.
  - Si la V33 se instala antes que la V31 y la V32, la siguiente actualización que traiga la V31 o la V32 no arrancará.
  - O se instalan en orden, o hay que activar `spring.flyway.out-of-order`.

## 2. Transacciones: «todo o nada»

**Hoy:**
- Cada llamada a un repositorio es su propia transacción.
- Un caso de uso que escribe varias veces es atómico solo si `odin-bootstrap` lo envuelve a mano, en una subclase anónima con un `TransactionTemplate`. Hoy son unas 20, la mayoría añadidas con el registro de actividad de la V30.
- Un caso de uso nuevo con varias escrituras no es atómico por defecto, y nada avisa.
- Dentro de una transacción externa, `set_config(..., true)` dura hasta el final. `inScope` solo fija las variables de proyecto si recibe una visibilidad, así que una llamada sin visibilidad hereda la de la anterior, que puede ser «todos los proyectos».

**Decisión:**
- **Una sola forma de declarar la atomicidad:**
  - Dos anotaciones Java puras en `odin-application` (`application.shared`):
    - `@Atomic`: el caso de uso corre entero en una transacción.
    - `@NonAtomic(reason)`: escribe por pasos a propósito, con el motivo.
  - En `odin-bootstrap`, un `BeanPostProcessor` envuelve en una transacción (proxy de clase con el `PlatformTransactionManager`) los métodos públicos de toda bean de una clase con `@Atomic`.
  - Las subclases anónimas que solo envuelven pasan a ser `@Atomic` en la clase del caso de uso, y se borran de `UseCaseConfiguration`. Si una hace algo más, se queda como está y se anota.
- **Regla que avisa (ArchUnit):** toda clase de `application` que, en sus propios métodos, llame a dos o más métodos de escritura de los puertos de persistencia (repositorios y `AuditLog`) tiene que llevar `@Atomic` o `@NonAtomic`.
  - Un método es de lectura si empieza por `find`, `list`, `count`, `get`, `exists`, `search`, `read`, `load`, `is` o `has`.
  - Todo lo demás cuenta como escritura. Es una regla conservadora: puede pedir una anotación de más, pero no se le escapa ninguna escritura.
- **Las colas en segundo plano son `@NonAtomic`**: lector OCR, carril local, etiquetado, primera carga.
  - Su reclamo tiene que confirmarse antes de una lectura de hasta 30 minutos.
  - Una transacción larga retendría la conexión y los bloqueos, y escondería el estado «en lectura».
- **`inScope` fija siempre todas las variables:** sin visibilidad, deja las de proyecto en su valor restrictivo (`app.project_ids` vacío y `app.all_projects` a `off`), que es lo que ya ve una transacción nueva. Así una llamada no hereda el ámbito de la anterior.
- **Orden de trabajo:**
  - Primero los casos con varias escrituras sin envolver que usa la instalación del propietario, según el inventario de la tarea.
  - Cada uno lleva una prueba de integración: un fallo forzado en la segunda escritura deja la base como estaba.
  - `withDocumentsLocked` (sustituir documento) se queda como está: bloquea filas a propósito.

## 3. ArchUnit

**Hoy:**
- 4 reglas, todas sobre el núcleo.
- `odin-architecture-tests` no carga `odin-bootstrap`.
- Ninguna regla dice qué parte de la aplicación pueden usar los adaptadores, ni prohíbe ciclos dentro de la aplicación.
- El módulo ya está en el reactor de Maven, así que `make check` (`mvn verify`) lo ejecuta.

**Decisión: reglas nuevas.**
- Nadie depende de `bootstrap`, salvo `bootstrap`.
- Los adaptadores de salida (`postgres`, `filestore`, `readers`, `readerservice` y `tagger`) solo usan de la aplicación sus puertos (`application.port..`) y lo compartido (`application.shared..`), nunca los casos de uso. El web (de entrada) sí usa casos de uso.
- Sin ciclos entre los paquetes de `application`, ni entre los de `domain`.
- `bootstrap` no hace transacciones a mano: no usa `TransactionTemplate`, salvo el `BeanPostProcessor` de `@Atomic`.
- La regla de `@Atomic`/`@NonAtomic` del apartado 2.
- Al cerrar se comprueba que `make check` las ejecuta de verdad, mirando el informe de surefire del módulo.
- Si una regla nueva falla con el código de hoy, se arregla el código. Si el arreglo no es razonable, se anota la excepción en la regla, con el motivo.

## Fuera de alcance

- `FORCE ROW LEVEL SECURITY`: el dueño solo lo usan Flyway y las órdenes de instalación, y la comprobación de arranque impide que la API lo sea.
- Revisar una por una todas las consultas que usan `inScope`, `inVisibleProjects` o `notWithdrawn`.
