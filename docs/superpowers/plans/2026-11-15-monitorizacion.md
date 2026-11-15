# Monitorización (encargo 16): plan de construcción

- Fecha: 2026-11-15. Autor: CTO 3. Diseño: `docs/superpowers/specs/2026-11-14-monitorizacion-design.md` (en esta rama).
- **Base:** `feature/monitorizacion` sale de `integracion/instalacion` 7791802 (V25 encargo 17, V26 primera carga). Cada tarea en `task/monitorizacion/<tarea>`, merge `--no-ff` a la feature.
- **Decisiones:** D-113 adopta las 20 recomendaciones del diseño (apartado 8), de forma provisional. Intocable: el interruptor nace apagado; sin política de uso publicada no se registra nada; validación legal antes de activarlo en un cliente (D-022); nada que puntúe o juzgue a personas (D-005).
- **Método (propietario, 15 nov.):** subagentes Sonnet para implementar; sin rondas de revisión; se verifica con pruebas (las de lo tocado durante la tarea, `make check` al final). Las pruebas de aislamiento son obligatorias.
- **Migración:** una sola, `V29__audit_log.sql`. Las tareas posteriores la amplían (no está desplegada en ningún sitio). El CTO tiene la V27 y el CTO 2 la V28: si se instalan en otro orden, se renumera al integrar.

## Restricciones globales (todas las tareas)

- Código e identificadores en inglés; mensajes al usuario, documentación y commits en español. Métodos de 30 líneas como máximo. Sin comentarios salvo el porqué.
- TDD: prueba primero, verla fallar, implementar, verla pasar.
- Commits sin atribución a Claude (sin `Co-Authored-By`). Sin push. No tocar `deploy/.env`, la pila del propietario (`odin-*`) ni los puertos 8080, 8088 y 8090.
- `details` (jsonb) nunca lleva contenido de documentos, texto de fichas, texto de preguntas o respuestas, contraseñas ni claves completas (solo el prefijo). Ningún nombre ni correo en `audit_event`: van en `audit_actor`.
- Nada agrupa, cuenta ni ordena por persona (D-005). El orden es siempre por fecha e id.
- Tras cambiar la API: regenerar `api/openapi.json` con `OpenApiContractIT` (`-Dodin.updateOpenApi=true`) y `npm run generate:types` en `panel` y `mcp`.
- Compilar con `mvn clean …` (un `target/` viejo puede arrastrar migraciones).

## Decisiones de construcción (rulings del CTO 3)

1. **Alcance de esta ronda:** lo que pide el CEO (registro, acciones de administración, acciones del trabajador desde el chat, consulta en el panel, clave de administrador para el chat). Quedan para después: fase B (caducidad 30 min / 12 h, sesiones abiertas, confirmaciones, volver a identificarse en general), fase E (recuento de consultas por persona y día), fase F (guiones de instalación), fase G (purga, rol `odin_audit_maint`, `bound_address`) e hitos del sistema. Coste si me equivoco: más tareas en la siguiente ronda; nada de lo construido cambia.
2. **El rol `odin_audit_maint` se crea con la purga (fase G),** no ahora: sin purga no hay nada que ese rol haga. Hoy ningún camino borra.
3. **Volver a identificarse** solo donde lo exige esta ronda: activar o desactivar el interruptor y emitir la clave de administrador para el chat. Se hace en el servidor pidiendo la contraseña actual en el cuerpo de la petición (sin columna `reauthenticated_at`, que es de la fase B). Contraseña mala → 403 y entrada `negado`.
4. **Interruptor:** fila única en `audit_setting` (`enabled` falso por defecto, `usage_policy_reference`, `chat_enabled` falso por defecto, quién y cuándo). Solo el administrador de grupo lo cambia. Para encender hace falta `usagePolicyReference` no vacío (dónde está publicada la política de uso). Encender escribe la primera entrada; apagar escribe la última antes de parar. Apagado: `audit_append` no escribe nada.
5. **Escritura atómica en SQL:** `audit_append(...)` `SECURITY DEFINER` toma `pg_advisory_xact_lock`, lee la cabeza fuera de la RLS, calcula la huella SHA-256 y añade. `odin_app` no tiene INSERT directo sobre `audit_event`, solo EXECUTE de la función y SELECT con RLS. Así nadie añade fuera de la cadena.
6. **Contexto de la petición** (canal, id de petición, IP y navegador): lo fija un filtro web según el tipo de credencial (cookie → `panel`, clave de trabajador → `chat`, clave de administrador para el chat → `chat-admin`, `/v1/setup` → `api`) y lo lee el adaptador por un puerto pequeño. No se cambia la firma de todos los casos de uso solo por el canal.
7. **Entrada fallida al panel con correo desconocido:** sin correo ni HMAC; solo «usuario desconocido» más IP y navegador (decisión 13 dice HMAC; esto guarda menos, y la IP permite ver la repetición). Coste: añadir una columna HMAC si se quiere.
8. **Negados que se registran en esta ronda:** entrada fallida al panel, `/v1/setup*` con token malo, contraseña mala al volver a identificarse, y clave de administrador para el chat usada fuera de su ámbito o de sus límites. Los 403 genéricos de otras rutas no (haría falta un gancho central; fase B). Los fallidos (excepción a mitad) no se registran salvo en esos mismos puntos.
9. **Acciones por enlace:** `actor_kind = LINK`, sin `actor_user_id`; `via` = id del enlace; `details.linkOwnerId` = quien lo emitió.
10. **Clave de administrador para el chat:** tabla propia `admin_chat_key` (no se reutiliza `access_key`, para no aflojar sus invariantes de trabajador), prefijo distinto para que el filtro sepa qué resolver. Vale en `GET /v1/activity` y en `GET /v1/me` (que devuelve `kind = ADMIN_CHAT` sin capacidades, para que el MCP sepa qué herramienta registrar). Exige `audit_setting.chat_enabled` y el registro encendido. El tope de 500 filas al día se cuenta sumando las filas devueltas que ya registran sus propias entradas de consulta.
11. **Sin `make demo`** esta ronda: el CEO pide `make check` en verde. La página «Actividad» lleva pruebas de componentes del panel.

## Tareas

### Tarea 1: registro inalterable, interruptor y contexto

**Rama:** `task/monitorizacion/registro`.

- **Migración `V29__audit_log.sql`:**
  - `audit_event`: `id bigint generated always as identity` (orden de la cadena y cursor), `occurred_at timestamptz`, `group_id`, `company_id` (nulo en acciones de grupo o de instalación), `project_id`, `actor_kind` (`PERSON`, `SYSTEM`, `INSTALLER`, `LINK`), `actor_user_id`, `actor_role`, `channel` (`PANEL`, `API`, `CHAT`, `CHAT_ADMIN`, `SCRIPT`, `SYSTEM`), `via`, `action`, `target_kind`, `target_id`, `target_label`, `outcome` (`DONE`, `DENIED`, `FAILED`), `details jsonb`, `request_id`, `chain_key`, `prev_hash`, `hash`. Únicos `(chain_key, prev_hash)` y `hash`. Índices `(company_id, occurred_at desc, id desc)` y `(action, occurred_at desc, id desc)`.
  - `audit_actor (user_id pk, display_name, email, updated_at)`: fuera de la huella; `audit_append` hace upsert cuando el actor es persona.
  - `audit_setting` (una fila, ver ruling 4).
  - Funciones: `audit_append(...) returns bigint` (`SECURITY DEFINER`, `search_path` fijado; devuelve nulo si el registro está apagado), `audit_chain_head()` y `audit_verify()` (recorre la cadena por `id` y devuelve el primer id roto o nulo).
  - Huella: `sha256` sobre una serialización canónica y documentada de todos los campos de la fila (fecha en UTC con formato fijo, `details::text` de jsonb) más `prev_hash`. El primer eslabón usa un `prev_hash` fijo de ceros.
  - Disparador `BEFORE UPDATE OR DELETE` por fila y `BEFORE TRUNCATE` por sentencia que lanzan error; `REVOKE UPDATE, DELETE, TRUNCATE, INSERT` a `odin_app`; `GRANT SELECT` y `EXECUTE` de las funciones.
  - RLS con el patrón del proyecto: una fila con empresa solo la ven los ámbitos que incluyen esa empresa; las filas sin empresa solo el ámbito de grupo.
- **Dominio:** `AuditEntry` (o nombre equivalente al estilo del proyecto) con enums `AuditAction` (lista cerrada; se amplía en cada tarea), `AuditActorKind`, `AuditChannel`, `AuditOutcome`.
- **Aplicación:** puerto `AuditLog.record(entry)` (misma transacción) y `AuditLog.recordDenied(entry)` (transacción propia); puerto de contexto de la petición (ruling 6). `UpdateAuditSettings` y `GetAuditSettings` (solo administrador de grupo, contraseña en el cuerpo, ruling 3 y 4).
- **Web:** filtro de contexto (ruling 6); `GET /v1/admin/audit-settings` y `PUT /v1/admin/audit-settings`.
- **Pruebas (integración con base real salvo las unitarias):**
  - cada `record` deja exactamente una fila con sus campos; con el registro apagado, ninguna;
  - `UPDATE`, `DELETE` y `TRUNCATE` como `odin_app` fallan; `INSERT` directo también;
  - la cadena: `audit_verify()` es nulo en una cadena sana; tras alterar una fila (como dueño, quitando el disparador) devuelve su id;
  - el dueño reescribe toda la cadena recalculando huellas: `audit_verify()` pasa, pero la comprobación contra un sello guardado antes (`id` y `hash`) falla (deja la función de comprobación de sello hecha aquí);
  - concurrencia: 20 escrituras en paralelo dan una cadena válida sin bifurcaciones;
  - una entrada negada en su propia transacción entra en la cadena aunque la del negocio se deshaga;
  - RLS: un ámbito de empresa no ve filas de otra empresa ni las de grupo;
  - interruptor: un administrador de empresa o un trabajador recibe 403; contraseña mala → 403 y entrada `negado`; encender sin `usagePolicyReference` → 400; encender deja la primera entrada y apagar la última;
  - ningún `details` de las pruebas lleva campos prohibidos (comprobación reutilizable para las tareas siguientes).

### Tarea 2: acciones de administración (acceso, instalación, organización, personas, claves, proyectos)

**Rama:** `task/monitorizacion/administracion-1`.

- Entradas en: entrada al panel, entrada fallida (con `user_id` si existe; ruling 7), salida; `POST /v1/setup` y `POST /v1/setup/admin-password` (actor `INSTALLER`, canal `API`, `DENIED` si el token es malo); crear y cambiar empresa; **capacidades con antes y después**; alta y baja de administradores del grupo; personas: alta, cambio de papel, baja, `UpdateUser` (antes y después de nombre, apellidos, correo y activo), cambio de contraseña (sin la contraseña); claves de trabajador: emisión y revocación (solo el prefijo); proyectos: crear, cambiar (restringido o abierto), borrar; miembros: alta y baja.
- IP y navegador solo en las entradas de acceso al panel (decisión 5).
- **Pruebas:** una por acción (prueba de API o de caso de uso con la base real, la que use el proyecto para ese controlador): exactamente una entrada, con actor, papel, canal `PANEL`, objeto, resultado y `details` correctos. Capacidades: antes y después. Un administrador de empresa que cambia algo de su empresa deja `company_id` de esa empresa.

### Tarea 3: acciones de administración (documentos, temas, fichas, primera carga)

**Rama:** `task/monitorizacion/administracion-2`.

- Entradas en: subida, reintento, retirada y su deshacer, marca de sustituido y su deshacer, marca de caso, cambio de temas y reetiquetado, catálogo de temas (alta, cambio, borrado), metadatos, **descarga del original por el administrador**, revisión de fichas (aprobada, corregida, rechazada) y las acciones de administrador de la primera carga (bandeja de entrada).
- Documentos: título, proyecto, tamaño y huella; nunca el contenido. Fichas: id y proyecto; nunca el texto.
- **Pruebas:** como en la tarea 2, más una que comprueba que ninguna entrada de documento o ficha lleva texto.

### Tarea 4: acciones del trabajador desde el chat

**Rama:** `task/monitorizacion/trabajador`.

- Entradas, canal `CHAT`, actor la persona trabajadora: alta de ficha, añadir y quitar del expediente, emitir enlace de subida (caso y proyecto), emitir enlace de original, descarga del original por el trabajador.
- Por enlace (ruling 9): subida por enlace (caso y proyecto, encargo 17), apertura del original por enlace. `via` = id del enlace.
- Un enlace rechazado (404 de siempre) no deja entrada (no se revela nada nuevo y no hay actor).
- **Pruebas:** una por acción; `via` presente en las de enlace; una subida duplicada deja su entrada con `details.duplicate = true`; ninguna lleva texto de ficha ni contenido.

### Tarea 5: consulta en el panel

**Rama:** `task/monitorizacion/consulta`.

- **API:** `GET /v1/admin/activity` con filtros (desde, hasta, empresa, proyecto, acción, persona que actúa, canal, resultado), cursor sobre `(occurred_at, id)`, 50 por página, de la más nueva a la más vieja; cada fila con el nombre del actor desde `audit_actor` («persona suprimida» si no está). Administrador de grupo: todo; de empresa: solo su empresa (filtro SQL más RLS); trabajador: 403.
- `GET /v1/admin/activity.csv` con los mismos filtros y la huella de cabeza con su fecha en la cabecera del fichero; deja su entrada (`EXPORT`, con los filtros en `details`).
- `GET /v1/admin/activity/integrity`: íntegro o primer id roto, más la huella de cabeza y su fecha (es el «sello» que el administrador guarda). `POST /v1/admin/activity/seal-check` con `{id, hash}`: si ese eslabón sigue igual y la cadena hasta él es íntegra.
- **Panel:** página «Actividad» en la navegación de administración: filtros, tabla, «Cargar más», exportar CSV, pie con el estado de la cadena y la huella de cabeza, y aviso «El registro está apagado» cuando lo esté. El administrador de grupo ve el interruptor (encender con la referencia de la política y la contraseña; apagar con la contraseña).
- **Pruebas:** trabajador → 403; administrador de empresa no ve otra empresa ni las filas de grupo (aislamiento); paginación estable con entradas en el mismo instante; prueba de contrato de que ninguna respuesta agrupa, cuenta ni ordena por persona; la exportación CSV deja su entrada; pruebas de componentes de la página (filtros, cargar más, aviso de apagado, interruptor), con Axe si el panel ya lo usa en sus pruebas de componentes.

### Tarea 6: clave de administrador para el chat

**Rama:** `task/monitorizacion/clave-chat`.

- **Migración (amplía V29):** `admin_chat_key` (id, user_id, ámbito: grupo o empresa, prefijo, huella del token, `created_at`, `expires_at` a 30 días, `revoked_at`).
- **Emisión y revocación** desde el panel por el administrador (contraseña en el cuerpo, ruling 3), solo si `audit_setting.chat_enabled`; el administrador de grupo enciende `chat_enabled` en el mismo interruptor. Entradas de emisión y revocación (solo el prefijo).
- **Categoría de ruta propia** para `GET /v1/activity`; el servidor comprueba en cada llamada el tipo de clave, que la persona sigue siendo administradora de ese ámbito, la caducidad y la revocación. Una clave de trabajador en `/v1/activity` → 403; la clave de administrador en cualquier ruta de trabajador o de administración → 401/403.
- **`GET /v1/activity`:** intervalo de fechas obligatorio de 7 días como máximo; 50 filas por llamada; tope de 500 filas al día por clave (ruling 10); sin correo en la salida; cada llamada deja su entrada (`CHAT_QUERY`, filtros y número de filas en `details`, canal `CHAT_ADMIN`). Superar el tope o salirse del ámbito → negado y registrado.
- **MCP:** herramienta `listActivity` registrada solo cuando `/v1/me` devuelve `kind = ADMIN_CHAT` (y entonces solo esa). Su descripción dice que devuelve hechos, que no valore, compare, ordene ni cuente a nadie (D-005), y que el intervalo es de 7 días como máximo.
- **Panel:** en «Actividad», el administrador emite su clave (se muestra una vez), ve si tiene una vigente y la revoca; el de grupo enciende o apaga el chat.
- **Pruebas:** clave de trabajador → 403; clave de otro ámbito (administrador de empresa pidiendo otra empresa) → 403; sin intervalo o de más de 7 días → 400; tope de 500 al día; sin correo en la salida; cada llamada registrada; clave caducada o revocada → 401; `chat_enabled` apagado → no se emite; pruebas del MCP (registro condicionado y validación de entrada) y de componentes del panel.

### Tarea 7: verificación final

- `make check` en verde sobre la punta de la feature. Actualizar el diseño con lo construido y lo que queda (fases B, E, F, G e hitos del sistema).
