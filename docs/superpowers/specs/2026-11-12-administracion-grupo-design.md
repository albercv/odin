# Odín · Varios administradores del grupo, desactivar en todo el grupo y códigos migrados

- Fecha: 2026-11-12. Autor: CTO. Encargo 11.
- Origen: D-051 y D-055. Backlog: B-007, B-005 y B-006.
- Rama: `feature/administracion-grupo`, que sale de `feature/identidad-pertenencias` (4224659). Amplía `2026-11-10-identidad-pertenencias-design.md`.
- No toca la búsqueda, los documentos ni el MCP, porque los está cambiando el CTO 2 en la fase 2.

## 1. Varios administradores del grupo (B-007)

**Hoy** el administrador del grupo es una marca de la persona (`groupAdmin`), pero el código supone que solo hay uno. Su relevo se hace renombrando la cuenta, y eso mezcla la autoría (D-005, D-051).

**Reglas:**
- Puede haber varios administradores del grupo. Todos tienen la misma autoridad.
- **Solo un administrador del grupo** puede:
  - **añadir a otro:**
    - se identifica por su correo, que es obligatorio;
    - si la persona ya existe en el grupo, recibe la marca, conserva su nombre y su código, y solo se le pide contraseña si no tiene;
    - si no existe, se crea con nombre, apellidos, correo y contraseña, y recibe su propio código de persona;
  - **desactivar o activar a otro.** Afecta a la persona entera: deja de entrar en el panel y sus claves dejan de valer;
  - **quitarle la marca.** La persona sigue existiendo, con sus pertenencias;
  - **borrarlo** con la acción «Borrar» que ya existe.
- **Siempre queda al menos uno activo.** Desactivar, quitar la marca o borrar al último administrador del grupo activo da 409, «Tiene que quedar al menos un administrador del grupo activo».
- **Nadie se lo hace a sí mismo:** es la regla que ya existe. Tampoco puede quitarse su propia marca.
- **Un administrador de empresa no puede nunca:** 403, como hoy.
- **Sesiones:** al desactivar, quitar la marca o borrar, se cierran sus sesiones del panel si ya no administra ninguna empresa.
- **Relevo:** primero se añade a la persona nueva como administradora del grupo y después se desactiva a la anterior. Cada una conserva su código y su autoría.
- **Recuperación de emergencia con el token de instalación:**
  - `POST /v1/setup/admin-password` acepta `email`, que es obligatorio si hay más de un administrador del grupo;
  - restablece la contraseña de ese administrador del grupo, lo reactiva y cierra sus sesiones;
  - con un solo administrador del grupo funciona como hoy, sin `email`;
  - un `email` que no es de un administrador del grupo da 404, «No hay ningún administrador del grupo con ese correo».

**API** (todo bajo `/v1/admin`; las tres rutas nuevas, solo para el administrador del grupo):

| Ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /group-admins` | — | `GroupAdminView[]`: `userId`, `firstName`, `lastName`, `displayName`, `email`, `personCode`, `active` |
| `POST /group-admins` | `AddGroupAdmin { firstName, lastName, email, password? }` | 201, `GroupAdminAdded { admin, alreadyExisted, keptPassword }` |
| `DELETE /group-admins/{userId}` | — | 204: quita la marca |

Desactivar y activar usan `PATCH /users/{userId}` con `{ active }`, que ya existe. Borrar usa `DELETE /users/{userId}`. Las dos rutas suman la regla de que siempre quede uno activo.

**Panel:**
- Página nueva, «Administración del grupo», en la barra lateral, solo para el administrador del grupo.
- Tabla: código, persona (nombre y correo) y estado.
- Acción principal: «Añadir administrador del grupo».
- Acciones de fila: «Desactivar» o «Activar», «Quitar administración» y «Borrar», todas con `ConfirmDialog`.
- En la fila propia no hay acciones.

## 2. Desactivar a una persona en todo el grupo (B-005)

- **API:** ya existe (`PATCH /users/{userId}` con `{ active }`, solo el administrador del grupo). `MemberView` gana `personActive`.
- **Panel:**
  - en la fila de una persona, solo para el administrador del grupo: «Desactivar en el grupo» o «Activar en el grupo»;
  - antes de desactivar, `ConfirmDialog`: «¿Desactivar a <nombre> en todo el grupo? No podrá usar Odín en ninguna empresa hasta que se reactive. Sus claves y sus proyectos se conservan.»;
  - con `personActive` falso, la fila muestra «Desactivada en el grupo».
- Si la persona es administradora del grupo, aplica la regla del último activo del apartado 1.

## 3. Códigos de empresa generados al migrar (B-006)

- **Datos:**
  - `Company` gana `codeReviewed`;
  - migración nueva, con el número que toque al integrar: la columna nace a verdadero por defecto, y las empresas que ya existen quedan a falso, porque su código salió de las iniciales;
  - una empresa nueva nace con el código revisado.
- **Revisar:** cuando el administrador del grupo guarda la empresa, el código queda revisado, cambie o no.
- **API:** `CompanyView` y `CompanyRef` ganan `codeReviewed`, solo en las rutas de administración.
- **Panel:** en «Empresas», junto al código, «Código por revisar» hasta que se guarde.

## 4. Criterio de cierre

- `make check` y `make demo` terminan con 0.
- La prueba de principio a fin cubre que un administrador del grupo añade a otro, que el nuevo entra en el panel y que el nuevo desactiva al primero, que ya no entra.
- El `README` describe el relevo con estas pantallas y deja el token de instalación como recuperación de emergencia.
