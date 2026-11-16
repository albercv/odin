# Seguridad del panel y cierre de la monitorización (encargo 25): plan

- Fecha: 2026-11-16. Autor: CTO 3. Diseño: `docs/superpowers/specs/2026-11-14-monitorizacion-design.md` (fases B, E y F; apartados 1, 4 y 5).
- **Base:** `feature/seguridad-panel` sale de `develop` 0b7ffa6 (encargo 16 instalado, V30). Cada tarea en `task/seguridad-panel/<tarea>`, merge `--no-ff` a la feature.
- **Fuera:** fase G (purga, `odin_audit_maint`, `bound_address`): depende del plazo de conservación y la validación legal (D-036, D-022).
- **Método (D-113):** subagentes Sonnet; sin rondas de revisión; pruebas de lo tocado y `make check` al final; pruebas de aislamiento obligatorias; ensayo sobre `data/copias/odin-v29-antes-despliegue-actualizar-2026-11-16-091115.sql` si hay migración.
- **Migración:** `V32__panel_sessions.sql` (la V31 está reservada para el CTO).

## Restricciones globales (todas las tareas)

- Código e identificadores en inglés; mensajes al usuario, documentación y commits en español. Métodos de 30 líneas como máximo. Sin comentarios salvo el porqué.
- TDD: prueba primero, verla fallar en una aserción, implementar, verla pasar.
- Commits sin atribución a Claude (sin `Co-Authored-By`). Sin push. No tocar `deploy/.env`, la pila del propietario (`odin-*`), sus volúmenes ni los puertos 8080, 8088 y 8090.
- El registro (encargo 16) ya existe: `AuditLog.record`/`recordDenied`, `AuditContext`, `AuditAction` (lista cerrada que se amplía), `AuditAssertions`. Apagado no anota nada. `details` nunca lleva contenido, contraseñas, claves completas, nombres ni correos.
- Nada agrupa, cuenta ni ordena por persona salvo lo que permite D-045 (recuentos de consultas ordenados por nombre, sin hora) (D-005).
- Tras cambiar la API: regenerar `api/openapi.json` (`cd api && mvn -q clean verify -Dodin.updateOpenApi=true`) y `npm run generate:types` en `panel` y `mcp`.
- Compilar con `mvn clean …`. Docker: si falla el arranque de un contenedor por espacio, repetir una vez; nunca borrar imágenes ni volúmenes; si `docker system df` pasa del 90 %, parar y decirlo.

## Decisiones de construcción (rulings del CTO 3)

1. **Volver a identificarse, un solo mecanismo:** la sesión guarda `reauthenticated_at` (entrar cuenta como identificarse). `POST /v1/admin/session/reauthentication` con `{password}` lo renueva; contraseña mala → 403 y entrada `DENIED`. Las operaciones sensibles exigen que sea de hace 10 minutos o menos; si no, 403 con un tipo de problema propio (`reauthentication-required`) y entrada `DENIED`. El interruptor del registro y la clave de administrador para el chat dejan de pedir la contraseña en el cuerpo y pasan a este mecanismo. Coste si me equivoco: volver a poner el campo en dos peticiones.
2. **Operaciones sensibles:** capacidades; administradores del grupo (alta y baja); claves de trabajador (emitir y revocar); clave de administrador para el chat (emitir y revocar); borrar persona (`DeleteUser`), dar de baja de una empresa (`RemoveMember`), borrar proyecto, borrar tema; cambiar la contraseña de otra persona; interruptor del registro y del chat. Lo que tiene deshacer (retirar documento, sustituido) no.
3. **Sesiones:** 30 minutos sin uso y 12 horas como máximo desde `created_at` (configurables en `SessionSettings`). `admin_session` gana `id` (uuid, para no exponer la huella del token), `user_agent` (hasta 300 caracteres), `ip` y `reauthenticated_at`. Cada administrador ve y cierra **solo sus** sesiones. Entradas: `SESSION_EXPIRED` (al presentarse una sesión caducada, con el motivo `IDLE` o `ABSOLUTE`), `SESSION_CLOSED` (cierre desde la lista) y `sessionsClosed` en el cambio de contraseña.
4. **Confirmaciones:** solo en el panel, con el `ConfirmDialog` que ya existe, en capacidades, papeles, administradores del grupo, claves, contraseñas, sustituciones, reetiquetado y revisión de fichas.
5. **Recuentos de consultas (fase E):** solo con el registro encendido (si está apagado, la ruta responde vacía con `logEnabled = false`). Fecha, persona y número; nunca la hora ni el texto. Orden por fecha y nombre, nunca por número. Mismo ámbito que la consulta del registro.
6. **Guiones (fase F):** escriben su entrada llamando a `audit_append` con `psql` dentro del contenedor de la base, como el dueño (que ya ejecuta Flyway y es punto de confianza). Actor `INSTALLER`, canal `SCRIPT`. Sin nombre del operador (dato personal). Si la base no está en marcha, la entrada se omite y la bitácora de fichero lo dice. Con el registro apagado, `audit_append` no anota nada. Una restauración vuelve el registro al estado de la copia; su entrada se escribe después, en la base restaurada, y el sello guardado delata el corte.
7. **Límites baratos:** se arreglan si caben en una tarea corta; si la atomicidad de las subidas no cabe (la carrera de duplicados aborta la transacción en PostgreSQL), se documenta y se deja.

## Tareas

### Task 1: sesiones (caducidad, lista y cierre)

**Rama:** `task/seguridad-panel/sesiones`.

- `V32__panel_sessions.sql`: columnas del ruling 3 en `admin_session`, con valores por defecto válidos para las filas que ya existen (`id` generado, `reauthenticated_at = created_at`).
- Caducidad del ruling 3 en `StoredAdminSessions`; `SESSION_EXPIRED` con el motivo.
- Entrar guarda `user_agent` e `ip` (del `AuditContext` o del filtro); cambio de contraseña: `details.sessionsClosed`.
- API: `GET /v1/admin/sessions` (las del propio administrador: id, desde, último uso, navegador, IP, si es la actual) y `DELETE /v1/admin/sessions/{id}` (solo propias; ajena o inexistente → 404). La respuesta de quién soy (`/v1/admin/session` o la que use el panel) incluye `expiresAt` (el menor de inactividad y máximo).
- Panel: página «Mis sesiones» (lista y cerrar, con `ConfirmDialog`); aviso 5 minutos antes de caducar, con «Seguir conectado» (una petición que renueva la inactividad) y la indicación del máximo de 12 horas.
- **Pruebas:** caducidad por inactividad y por máximo (reloj inyectable); la fila caducada no se renueva; entrada `SESSION_EXPIRED`; migración sobre filas existentes; listar solo las propias; cerrar una ajena → 404 (aislamiento entre administradores y entre empresas); cerrar la propia deja `SESSION_CLOSED`; componentes del panel (lista, cerrar, aviso).

### Task 2: volver a identificarse y confirmaciones

**Rama:** `task/seguridad-panel/reidentificar`.

- Rulings 1 y 2 en la API: comprobación común en la aplicación (una sola clase) usada por todas las operaciones sensibles; ruta de reidentificación; tipo de problema propio.
- El interruptor del registro y la clave de chat pasan al mecanismo común (quitar `password` de sus cuerpos; actualizar panel, pruebas y `openapi.json`).
- Panel: ante el 403 `reauthentication-required`, un diálogo pide la contraseña, llama a la ruta y repite la petición una vez. Confirmaciones del ruling 4.
- **Pruebas:** cada operación sensible sin reidentificación reciente → 403 y `DENIED`; con ella → hecha; contraseña mala → 403 y `DENIED`; a los 10 minutos caduca; una operación no sensible no la pide; panel: diálogo, reintento y confirmaciones.

### Task 3: recuentos de consultas

**Rama:** `task/seguridad-panel/consultas`.

- API `GET /v1/admin/activity/questions` con `from`, `to` y `companyId`: filas `{date, userId, name, count}` contadas sobre `question_record` por persona y día (zona `Europe/Madrid`), sin leer el texto, ruling 5.
- Panel: sección «Consultas por persona y día» en «Actividad».
- **Pruebas:** recuentos correctos en el límite del día; ninguna respuesta lleva hora; orden por fecha y nombre; administrador de empresa solo su empresa (aislamiento); trabajador rechazado; registro apagado → vacío; contrato sin parámetros de orden ni agrupación.

### Task 4: guiones de instalación en el registro

**Rama:** `task/seguridad-panel/guiones`.

- Ruling 6 en `deploy/instalacion/`: desplegar, actualizar (si es una orden aparte), restaurar, añadir proyecto y reiniciar escriben su entrada (`INSTALL_DEPLOYED`, `INSTALL_UPDATED`, `INSTALL_RESTORED`, `INSTALL_PROJECT_ADDED`, `INSTALL_RESTARTED`; `details`: versión (commit), resultado, fichero de bitácora). Un ayudante común en `comun.sh`.
- **Pruebas:** con el estilo de las pruebas de los guiones; el ayudante construye la llamada correcta, se salta sin base en marcha y nunca pone datos personales; una prueba de integración con PostgreSQL desechable que comprueba que la llamada real deja una entrada válida en la cadena cuando el registro está encendido y ninguna cuando está apagado.

### Task 5: límites baratos

**Rama:** `task/seguridad-panel/limites`.

- «Hasta» del filtro: fin del día en la hora local del navegador (la del propietario), no en UTC.
- Filtro por persona: lista de personas que el panel ya sabe cargar (miembros de la empresa elegida y administradores), en vez de un id.
- Atomicidad de la entrada de subida (ruling 7): punto de guardado alrededor de la inserción para que la carrera de duplicados no aborte la transacción, y la entrada en la misma transacción. Si no cabe, documentarlo y dejarlo.
- **Pruebas:** las de cada arreglo.

### Task 6: cierre

- Mezclar `develop` en la feature; `make check` en verde; ensayo de V29 → V30 → V32 sobre la copia; actualizar el diseño («Construido» y «Pendiente»); bajar las pilas de prueba.
