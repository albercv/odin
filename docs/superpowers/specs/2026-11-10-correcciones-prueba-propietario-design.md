# Odín · Fase 1.1: correcciones tras la prueba del propietario

- Fecha: 2026-11-10. Autor: CTO. Encargo 11.
- Amplía el diseño `2026-11-09-api-central-design.md` (aquí, «spec base»). Lo que este documento no cambia sigue como está allí.
- Rama: `feature/api-central`. Estas correcciones entran antes de pasar la feature a `develop`.

## 1. Por qué

El propietario probó Odín a mano el 2026-11-10, sobre bacbb41, y encontró fallos que las pruebas automáticas no vieron. La revisión posterior del CTO confirmó cada uno en el código y añadió otros.

| # | Qué pasa | Causa |
|---|---|---|
| F1 | Un trabajador recién creado «no funciona» | El panel pide contraseña a todos. La API la descarta sin avisar si el papel es trabajador, y el trabajador no entra en el panel (D-033) |
| F2 | El botón «Activar» del panel falla siempre | La API solo admite desactivar: responde 400 a `active: true` |
| F3 | Dos usuarios distintos parecen el mismo | La lista del panel no enseña el correo |
| F4 | El panel exige correo al trabajador | La API lo admite sin correo. Con el correo único, un administrador que también es trabajador (spec base 6.2) necesita un segundo correo |
| F5 | Nadie puede cambiar una contraseña, y el administrador del grupo puede desactivarse a sí mismo | No existe el caso de uso. Si se bloquea, la única salida es borrar la instalación |
| F6 | `make up` falla con una traza de Java si el volumen de PostgreSQL es anterior a las claves de `deploy/.env` | PostgreSQL fija las claves en el primer arranque. No está en el `README` y el error no lo explica |
| F7 | Faltan operaciones: editar y borrar usuarios, renombrar y borrar proyectos | No se construyeron en la fase 1 |
| F8 | Cambiar la sesión por JWT obligaría a tocar tres casos de uso | `LogInAdmin`, `LogOutAdmin` y `ResolveActor` saben que la sesión es una fila en la base |
| F9 | Las pruebas del panel no ejercitan casi ninguna acción | Hay 8 pruebas contra un doble escrito a mano, y la prueba de extremo a extremo solo recorre la demo |

## 2. Decisiones

**Del propietario (2026-11-10):**
- Los borrados son lógicos, nunca físicos.
- El trabajador no entra en el panel: usa Odín desde su IA por MCP (D-033, D-037).
- La sesión tiene que poder cambiarse por JWT sin rehacer el núcleo. No se cambia a JWT ahora.

**Del CTO:**
- La sesión sigue siendo la del spec base: token opaco en una cookie `HttpOnly`, guardado como huella en la base. Lo que cambia es que queda detrás de un puerto.
- Borrar un usuario libera su correo; desactivarlo, no.
- Borrar un proyecto no borra ni oculta sus documentos ni sus fichas.
- No se cambia el papel de un usuario: se crea otro.
- Las confirmaciones del panel usan `window.confirm`. El rediseño visual es una fase aparte.

## 3. Fuera de alcance

- Pertenencia de usuarios a proyectos, permisos por proyecto y una persona en varias empresas: fase 2, con decisión de negocio pendiente.
- Rediseño visual del panel con la marca de E2D: fase aparte, después de esta.
- Borrado de empresas y nombre único de empresa: nadie lo ha pedido.
- JWT: solo se deja el puerto.

## 4. Usuarios

### 4.1 Alta según el papel

| Papel | Nombre | Correo | Contraseña |
|---|---|---|---|
| `WORKER` | Obligatorio | Opcional | No tiene. Si llega una no vacía: 400, «Un trabajador no tiene contraseña: entra con su clave personal» |
| `COMPANY_ADMIN` | Obligatorio | Obligatorio | Obligatoria, 12 caracteres o más |

Lo demás del alta no cambia: correo único (409) y quién puede crear cada papel.

### 4.2 Editar, activar y desactivar

`PATCH /v1/admin/users/{userId}` con `UpdateUser { displayName?, email?, active? }`. Solo se aplican los campos que llegan. Sustituye al cuerpo `UserActive`.

- **Permiso:** el mismo que hoy para desactivar (`UserAdministration.requireAdminOfUser`).
- **`displayName`:** no puede quedar vacío (400).
- **`email`:** se normaliza. Repetido en otro usuario no borrado: 409, «Ya existe un usuario con ese correo». Cadena vacía: quita el correo de un trabajador; en un administrador es 400, «El administrador necesita correo».
- **`active`:** admite `true` y `false`. Nadie se desactiva a sí mismo: 400, «No puedes desactivarte a ti mismo».
- **Usuario de otra empresa:** 403, como hoy. **Usuario que no existe o está borrado:** 404.

### 4.3 Borrado lógico

`DELETE /v1/admin/users/{userId}` responde 204.

- **Permiso:** el mismo que para editar. Nadie se borra a sí mismo: 400, «No puedes borrarte a ti mismo».
- **En la base:** `app_user.deleted_at` (migración V5). El índice único del correo pasa a `WHERE email IS NOT NULL AND deleted_at IS NULL`.
- **Efectos:**
  - no aparece en ninguna lista ni se puede editar, activar ni darle claves (404);
  - sus claves personales y sus sesiones dejan de valer en la siguiente petición (401), porque el usuario ya no se resuelve;
  - su correo queda libre para un usuario nuevo;
  - su autoría se conserva: los documentos, las fichas y las citas siguen mostrando su nombre.
- **No hay «deshacer»** en esta fase.

### 4.4 Contraseñas

`PUT /v1/admin/users/{userId}/password` con `SetPassword { currentPassword?, newPassword }` responde 204.

| Quién | Sobre quién | Regla |
|---|---|---|
| Cualquier administrador | Sí mismo | `currentPassword` obligatoria y correcta. Si no: 400, «La contraseña actual no es correcta» |
| Administrador del grupo | Un administrador de empresa | Sin `currentPassword` |
| Administrador de empresa | Otro usuario | 403, «Solo el administrador del grupo restablece la contraseña de otro usuario» |
| Cualquiera | Un trabajador | 400, «Un trabajador no tiene contraseña: entra con su clave personal» |

- `newPassword`: 12 caracteres o más (400).
- Al cambiarla se cierran todas las sesiones de ese usuario, también la actual.

### 4.5 Recuperar al administrador del grupo

`POST /v1/setup/admin-password` con `RecoverAdmin { setupToken, newPassword }` responde 204. Es pública, como `/v1/setup`.

- **Token:** el de instalación de `deploy/.env`. Si no vale: 403, «Código de instalación no válido». Quien tiene ese fichero ya controla la base de datos, así que no abre nada nuevo.
- **Sin configurar:** si no hay administrador del grupo, 409, «La instalación no está configurada».
- **Efecto:** cambia su contraseña, lo deja activo y cierra sus sesiones.
- **Límite de intentos:** el mismo `LoginRateLimiter` del login, con la clave fija `setup` y la IP.

## 5. Proyectos

- **Renombrar:** `PATCH /v1/admin/companies/{companyId}/projects/{projectId}` con `Name { name }`.
- **Borrado lógico:** `DELETE` sobre la misma ruta, 204. Columna `project.deleted_at` (migración V6).
- **Nombre único** por empresa entre los proyectos no borrados, sin distinguir mayúsculas: 409, «Ya existe un proyecto con ese nombre». Índice único `(company_id, lower(name)) WHERE deleted_at IS NULL`.
- **Permiso:** `AccessPolicy.requireAdminOf`, como al crear. Proyecto de otra empresa o borrado: 404.
- **Efectos del borrado:**
  - no aparece en las listas, ni en `DescribeMe`, ni se puede elegir para documentos o fichas nuevos;
  - sus documentos y sus fichas conservan el proyecto como dato histórico y siguen saliendo en las búsquedas de su empresa, con su cita;
  - filtrar una búsqueda o una lista por un proyecto borrado da 404, igual que con uno ajeno.

## 6. Sesión del panel detrás de un puerto

Puerto nuevo en `odin-application`:

```java
public interface AdminSessions {
    String open(UserId userId);
    Optional<UserId> resolve(String token);
    void close(String token);
    void closeAllOf(UserId userId);
}
```

- **`open`:** devuelve el token que viaja en la cookie.
- **`resolve`:** vacío si el token no existe o caducó por inactividad; si vale, renueva la inactividad.
- **Implementación de hoy:** `StoredAdminSessions`, en `odin-application`, con el comportamiento actual (token aleatorio, huella, fila en `admin_session`, caducidad por inactividad). Usa los puertos que ya existen, más `AdminSessionRepository.deleteByUser`.
- **Quién lo usa:** `LogInAdmin`, `LogOutAdmin` y `ResolveActor` dejan de conocer `AdminSessionRepository`, `TokenGenerator` y `SessionSettings`.
- **Sin cambio de contrato:** la cookie, las rutas y las respuestas son las mismas.
- **Cambiar a JWT** será escribir otra clase que implemente `AdminSessions` y cambiar una línea de `UseCaseConfiguration`. `closeAllOf` exigirá entonces una versión de sesión por usuario; se anota en el `README`.

## 7. Arranque y operación

- **Error claro:** un `FailureAnalyzer` de Spring Boot en `odin-bootstrap` reconoce el estado SQL `28P01` y sustituye la traza por este texto:
  - descripción: «PostgreSQL rechaza la clave del usuario de la base de datos.»;
  - acción: «Las claves de la base se fijan en el primer arranque y no cambian al editar deploy/.env. En una instalación nueva, borra los volúmenes (make down; docker volume rm odin_pgdata odin_originals) y arranca otra vez. Si ya tiene datos, vuelve a poner la clave anterior o cámbiala en PostgreSQL (README, «Claves de la base de datos»).».
- **`README`**, apartados nuevos:
  - «Quién entra dónde»: administradores al panel, trabajadores por MCP con su clave personal;
  - «Claves de la base de datos»: se fijan en el primer arranque; cómo cambiarlas con `ALTER ROLE` y `deploy/.env`; cuándo borrar los volúmenes;
  - «Contraseña olvidada»: la orden `curl` del apartado 4.5;
  - «Sesión del panel»: qué es hoy y cómo se cambiaría a JWT.

## 8. Panel

Cambios funcionales, sin rediseño visual.

- **Alta de usuario:** la contraseña solo se pide para el administrador de empresa; el correo es opcional para el trabajador.
- **Lista de usuarios:** nombre, correo, papel y estado. Por usuario: activar o desactivar, editar nombre y correo, borrar (con confirmación), claves (solo trabajadores) y restablecer contraseña (solo administradores de empresa, y solo si quien mira es el administrador del grupo).
- **Proyectos:** renombrar y borrar (con confirmación).
- **Mi cuenta:** página nueva con el nombre de quien ha entrado y el cambio de su contraseña. Al cambiarla vuelve a la entrada con el aviso «Contraseña cambiada. Entra de nuevo.».
- **Navegación:** muestra el nombre de quien ha entrado.
- **Formularios:** ninguno se vacía si la acción falla.

## 9. Pruebas

- **Casos de uso:** cada regla de los apartados 4 a 6, con los dobles en memoria. Cada caso de uso con actor lleva su prueba con un actor de otra empresa (D-014).
- **PostgreSQL real:** las migraciones V5 y V6, el índice del correo con usuarios borrados y el del nombre del proyecto.
- **Web y contrato:** rutas, códigos y `api/openapi.json` regenerado; los tipos del panel y del MCP se regeneran con él.
- **Panel:** una prueba por acción con el doble.
- **De extremo a extremo:** `panel/e2e/admin.spec.ts`, con Playwright contra la pila real de `make demo`. Recorre las acciones del apartado 8. Desde ahora, ninguna acción del panel se da por terminada sin su paso en una prueba contra la API real.

## 10. Criterio de cierre

- `make check` y `make demo` terminan con código de salida 0.
- El recorrido del propietario funciona en una instalación limpia: crear un trabajador sin contraseña, darle su clave, desactivarlo y activarlo, borrarlo y volver a crear otro con el mismo correo.
