# Monitorización de acciones y seguridad del panel (encargo 16, D-105 y D-099): diseño y plan

- Fecha: 2026-11-14. Autor: CTO 3. Solo diseño y plan: no se construye nada hasta que el propietario decida los puntos del apartado 8 y el CEO valide.
- Revisado tras una revisión independiente (25 hallazgos, todos tratados aquí).
- **Lo que pide el propietario (D-105):** «Toda acción debe quedar monitorizada y además solo el admin puede ver esas monitorizaciones, estaría bien además que pueda verlas a través del chat».
- **Lo que pasó (D-099):** alguien quitó con un clic, desde el panel, los casos parecidos de Gestión Albar. No se pudo saber quién: la API no registra peticiones y la base no guarda quién cambia las capacidades.
- **Punto de partida** (rama `integracion/instalacion`):
  - no hay ningún registro general de acciones;
  - solo existen `case_file_event` (expedientes) y algunas columnas sueltas de autor (`uploaded_by`, `withdrawn_by`, `reviewer_id`, `decided_by`, `added_by`);
  - faltan quién cambia capacidades, personas, papeles, claves, proyectos y sustituciones;
  - la sesión del panel caduca a las 8 horas sin uso y no tiene límite absoluto;
  - no hay papel de administrador en el chat.

## Construido (15 nov.)

Lo que existe en `feature/monitorizacion`, con las decisiones del CTO 3 que lo fijan (apartado «Decisiones de construcción» del plan):

- **Registro inalterable (migración V30):** `audit_event` con cadena de huellas SHA-256; se escribe solo con `audit_append(...)` (`SECURITY DEFINER`, bloqueo consultivo); `odin_app` no inserta directamente. Nada lo borra hoy (rulings 2 y 5).
- **Interruptor, apagado por defecto:** fila única `audit_setting`; solo el administrador de grupo lo cambia, con contraseña actual (403 y entrada `negado` si falla). Encender exige `usagePolicyReference`; encender escribe la primera entrada y apagar la última. `enabled` y `chatEnabled` son obligatorios en la petición: si falta uno, 400 y no cambia nada (rulings 3 y 4).
- **Acciones de administración:** acceso, instalación, empresas, personas, claves, proyectos, documentos, temas y fichas; en los cambios de configuración, valor de antes y de después. Una etiqueta de proyecto cambiada se anota así; nombre y correo de personas, solo como «cambió» (ruling 8 para los negados que se registran).
- **Acciones del trabajador desde el chat:** con canal, petición, IP y navegador fijados por un filtro web según la credencial; las hechas por enlace llevan `actor_kind = LINK` (rulings 6 y 9).
- **Consulta en el panel:** página «Actividad» para los dos tipos de administrador, con exportación a CSV y sello de integridad; orden siempre por fecha e id, sin agrupar por persona (D-005).
- **Clave de administrador para el chat:** tabla `admin_chat_key` propia; vale solo para `GET /v1/activity` y `GET /v1/me` (`kind = ADMIN_CHAT`); exige registro y chat encendidos; tope de 500 filas al día (ruling 10).
- Sin `make demo` esta ronda (ruling 11).

## Pendiente

- Fase B: caducidad de sesión (30 min / 12 h), sesiones abiertas, confirmaciones y volver a identificarse en general.
- Fase E: recuento de consultas por persona y día.
- Fase F: guiones de instalación.
- Fase G: purga, rol `odin_audit_maint` y `bound_address`.
- Hitos del sistema.
- Entradas al panel bloqueadas por el limitador.
- Subidas no atómicas con su entrada de registro.
- Política de uso y validación legal antes de encender el registro en un cliente.

## 1. Qué se registra

**Cada entrada** dice:
- **quién:** la persona y su papel en ese momento, o «sistema» o «instalador»;
- **qué:** un código de acción de una lista cerrada;
- **cuándo:** la hora del servidor;
- **sobre qué:** el tipo, el identificador y un nombre legible del objeto;
- **por dónde:** panel, API, chat (MCP), guion o sistema;
- **con qué resultado:** hecho, negado o fallido.

En los cambios de configuración guarda también el valor de antes y el de después (por ejemplo, las capacidades).

**Acciones por enlace:** hoy quien abre o sube por un enlace corre como el trabajador que lo emitió (`ResolveLink`). La entrada guarda `via` = id del enlace, y el emisor figura solo como dueño del enlace, no como quien actúa.

| Área | Acciones | Detalle que se guarda |
|---|---|---|
| Acceso al panel | entrada, entrada fallida, salida, sesión caducada, sesión cerrada por cambio de contraseña | En las fallidas: `user_id` si la persona existe; si no, un HMAC del correo, conservado 30 días (decisión 13). Navegador e IP (decisión 5) |
| Instalación inicial | `POST /v1/setup` (primer administrador) y `POST /v1/setup/admin-password` (recuperar al administrador del grupo; cierra sus sesiones) | Actor `instalador`, canal `api`; resultado `negado` si el token es malo. Se mantiene el endpoint de recuperación (decisión 14) |
| Organización | crear o cambiar empresa, **capacidades** (antes y después), administradores del grupo (alta y baja) | Campos cambiados |
| Personas y claves | alta, cambio de papel, baja, **cambio de nombre o correo y reactivación** (`UpdateUser`), cambio de contraseña (sin la contraseña), **emisión y revocación de claves** (de trabajador y de administrador para el chat) | Valor anterior y nuevo en `UpdateUser`; solo el prefijo de la clave |
| Proyectos | crear, cambiar (restringido o abierto), borrar; miembros (alta y baja) | Nombre y estado |
| Documentos | subida (también por enlace desde el chat y a un proyecto, encargo 17), reintento, retirada y su deshacer, marca de sustituido y su deshacer, marca de caso, temas (cambio y reetiquetado), **catálogo de temas** (`/v1/admin/companies/{id}/topics`: alta, cambio y borrado), metadatos, **descarga del original** (administrador y trabajador) | Título, proyecto, tamaño y huella; **nunca el contenido**, tampoco en las descargas |
| Fichas | alta (por un trabajador), revisión (aprobada, corregida o rechazada) | Identificador y proyecto; **nunca el texto** |
| Expedientes | añadir, quitar o mover documentos; emitir enlaces de subida y de original | Hoy van en `case_file_event`; se registran también aquí con el mismo formato |
| Consultas de los trabajadores | **sin texto**: solo un recuento por persona y día (decisión 1) | Fecha y número, **nunca la hora** (D-045). Sale de contar filas de `question_record` por persona y día, sin leer el texto |
| Sistema | solo hitos, con autor `sistema`: documento procesado o fallido, y un resumen por pasada de etiquetado | Identificador del documento y resultado. No se registra cada paso de `DocumentProcessingScheduler`, `ReaderProcessingScheduler` ni `TaggingScheduler`: sería mucho volumen y no aporta personas ni datos personales |
| Instalación | despliegue, actualización, restauración, proyecto añadido, reinicio | Versión, quién lo lanzó (el operador), resultado y fichero de bitácora |
| El propio registro | consulta desde el chat, exportación, **activar o desactivar el interruptor** | Filtros usados |

**No se registra:**
- el texto de las preguntas ni de las respuestas (D-036, D-040);
- el contenido de documentos y fichas;
- contraseñas y claves completas;
- lo que cada trabajador lee en las búsquedas (decisión 10); sí las descargas del original, sin contenido;
- cada paso interno del sistema (ver la fila «Sistema»).

## 2. Cómo se guarda

- **Tabla nueva `audit_event`** (migración: el siguiente número libre al integrar, coordinado con los otros CTO (D-055, D-107). Hoy V25 y V26 están ocupados por la primera carga y el encargo 17). Columnas:
  - `id`, `occurred_at`, `group_id`, `company_id`, `project_id`;
  - quién: `actor_kind` (persona, sistema o instalador), `actor_user_id` y `actor_role`. **Ningún nombre ni correo en esta fila** (ver «Datos personales»);
  - qué y dónde: `channel`, `via` (id del enlace, si lo hay), `action`, `target_kind`, `target_id`, `target_label`, `outcome`;
  - `details` (jsonb: antes y después, siempre sin contenido ni datos personales);
  - `request_id`, `chain_key`, `prev_hash` y `hash`.
- **Canal deducido en el servidor** del tipo de credencial: cookie del panel → `panel`; clave de trabajador → `chat`; clave de administrador para el chat → `chat-admin`; guion o sistema → los suyos. No hay cabecera declarada por el cliente (`X-Odin-Channel` se descarta), porque cualquier cliente podría mentir.
- **Solo se añade, nunca se cambia:**
  - el usuario de la aplicación tiene permiso de insertar y leer, no de cambiar ni de borrar;
  - un disparador `BEFORE UPDATE OR DELETE OR TRUNCATE` niega cualquier cambio, y se hace `REVOKE TRUNCATE` (los disparadores por fila no saltan con TRUNCATE);
  - **el dueño de la base** (`ODIN_DB_OWNER_USER`, el que ejecuta Flyway) nunca es la credencial de la aplicación ni la de la purga. **Sigue siendo un punto de confianza residual:** puede quitar el disparador, y se documenta así. Por eso existe el ancla (siguiente punto).
- **Cadena de huellas SHA-256, una global por instalación:**
  - cada entrada lleva la huella de la anterior; escribir toma `pg_advisory_xact_lock` sobre la clave de cadena;
  - el último eslabón se lee **fuera del ámbito de empresa** (que filtra por `app.company_ids`), con una función `SECURITY DEFINER`; si no, la cadena se bifurcaría;
  - único `(chain_key, prev_hash)`: la base rechaza las bifurcaciones;
  - una comprobación periódica verifica la cadena, y el panel muestra «Registro íntegro» o el primer eslabón roto.
- **Ancla contra el dueño de la base** (decisión 12): quien controla la base podría reescribir toda la cadena y recalcular las huellas. Por eso:
  - el panel muestra la **huella de cabeza actual con su fecha**;
  - cada exportación CSV la incluye, y un fichero mensual «sello» que el administrador descarga;
  - la verificación compara contra un sello que el administrador conserva fuera de la base.
- **Datos personales aparte** (decisión 11): una tabla `audit_actor` por `actor_user_id` con nombre y correo en ese momento. **No entra en la huella.** La fila del registro solo guarda el id. Suprimir a una persona borra su fila de `audit_actor`, y sus entradas muestran «persona suprimida» sin romper la cadena.
- **Desde el panel no se puede borrar ni cambiar nada,** porque no hay ni endpoint ni permiso.
- **Purga** (decisión 4):
  - la migración crea el rol `odin_audit_maint`, con INSERT y SELECT sobre `audit_event` y permiso para ejecutar la función de purga, y nada más. La función es de ese rol y es el único camino que borra;
  - sus credenciales viven en el `.env` de la instalación, aparte de las de la aplicación y las del dueño;
  - la purga deja una entrada con `last_purged_hash`, cuántas entradas y el intervalo; el verificador se ancla en ella. Así distingue una purga legítima de una historia manipulada;
  - la purga también pone a nulo `access_link.bound_address` 24 h después de que el enlace caduque (decisión 5), y quita a los 30 días las entradas de intento fallido sin usuario.
- **Misma transacción que el cambio:** la entrada se escribe con el mismo commit que la acción. Si el cambio no se guarda, tampoco su entrada.
- **Negados y fallidos:** se escriben en su propia transacción (la del negocio se deshace) y toman el mismo bloqueo de la cadena.
- **Arquitectura hexagonal,** como el resto de la API:
  - un tipo de evento en el dominio;
  - un puerto `AuditLog` en la aplicación, al que llama cada caso de uso de la lista;
  - un adaptador de PostgreSQL.
- **Guiones de instalación:** desplegar, actualizar, restaurar, añadir proyecto y reiniciar escriben su entrada con una orden pequeña que usa `odin_audit_maint`. La bitácora de fichero que ya escriben se queda como está.
- **Índices:** por empresa y fecha, y por acción y fecha. La paginación usa un cursor sobre (fecha, id).

## 3. Quién lo ve, y cómo

- **Solo administradores:**
  - el de grupo ve todo;
  - el de empresa, solo lo de su empresa (decisión 2);
  - un trabajador recibe un 403 en todas las rutas del registro.
- **En el panel, una página nueva «Actividad»:**
  - filtros por fechas, empresa, proyecto, tipo de acción, persona que actúa, canal y resultado;
  - 50 entradas por página, de la más nueva a la más vieja;
  - orden **solo por fecha**: nunca por persona ni por cantidad;
  - sin gráficos ni recuentos por persona (D-005, D-045);
  - exportación a CSV, que también queda registrada;
  - al pie, el estado de la cadena de huellas.
- **Por el chat:**
  - una herramienta del conector, `listActivity`. Hoy los administradores no tienen clave de chat (`IssueAccessKey` y `ResolveActor.worker` exigen ser trabajador, y `ActorResolutionFilter` solo resuelve claves en las rutas de trabajador). Se crea un **tipo de credencial nuevo, la clave de administrador para el chat**:
    - la emite el administrador desde el panel, con volver a identificarse; ligada a esa persona y a su ámbito (grupo o empresa); vale 30 días;
    - solo vale en `GET /v1/activity`;
    - esa ruta tiene su **propia categoría**, y el servidor comprueba en cada llamada el tipo de clave, el papel y el ámbito. Esconder la herramienta en el MCP no es seguridad;
  - devuelve entradas (hechos) con los mismos filtros, con **intervalo de fechas obligatorio de 7 días como máximo por llamada**, como mucho 50 filas por llamada, **tope diario de 500 filas** y **sin correo** en lo que devuelve;
  - no ofrece agrupar ni contar por persona, y su descripción le dice a la IA que no valore, compare ni ordene a nadie (D-005);
  - cada consulta desde el chat queda registrada con sus filtros;
  - **aviso legal:** lo que se consulta por el chat llega a la IA del administrador, que es un tercero (D-016, D-019). Va a la validación legal (D-022) y está apagado por defecto (decisión 3).

## 4. Seguridad del panel (D-099), que comparte el registro

| Medida | Propuesta |
|---|---|
| Caducidad de la sesión | **30 minutos sin uso** (hoy son 8 horas) y **12 horas como máximo** aunque se use (hoy no hay máximo; `StoredAdminSessions` solo mira la inactividad). El límite absoluto cuenta desde `created_at`. Aviso en el panel 5 minutos antes |
| Sesiones abiertas | El administrador ve sus sesiones (desde cuándo, desde qué navegador e IP) y puede cerrarlas. Hacen falta columnas de user-agent e IP en `admin_session`, con su migración. Habría delatado la sesión de D-099, abierta a las 18:31 y viva a las 23:25 |
| Confirmación | Un diálogo de confirmación en todos los cambios sensibles: hoy solo lo tienen algunos (miembros, proyectos, retirar documentos, expedientes). Se añade a capacidades, papeles, administradores del grupo, claves, contraseñas, sustituciones, reetiquetado y revisión de fichas |
| Volver a identificarse | Para lo más delicado (capacidades, administradores del grupo, emisión y revocación de claves, borrados), la contraseña si la última vez fue hace más de 10 minutos (decisión 6). **Se exige en el servidor**, no solo en el diálogo: la fila de la sesión guarda `reauthenticated_at`; si es antigua, la API responde 403 y registra la negativa como negado |
| Origen | Se mantiene `OriginCheckFilter` y `SameSite=Strict`. No cambia |

## 5. Límites (D-005, D-036, D-040, D-045, D-022)

- **Hechos, no evaluación:**
  - el registro responde a «quién hizo qué y cuándo»;
  - no hay puntuaciones, comparaciones, rankings ni recuentos por persona salvo los de consultas que ya permite D-045 (ordenados por nombre);
  - la IA del chat recibe hechos y la instrucción de no juzgar.
  - Se comprueba con pruebas de contrato: ninguna ruta agrupa ni ordena por persona.
- **Preguntas:** nunca su texto ni la hora. Como mucho, fecha y cuántas por persona y día (decisión 1; D-045: la hora identificaría a quien preguntó). Los huecos siguen como hoy: sin autor y con un mínimo de 3 personas (D-036).
- **RGPD y LOPDGDD:**
  - minimización: solo lo de la tabla del apartado 1;
  - plazo de conservación (decisión 4), que incluye la IP de entrada al panel y `bound_address`;
  - la política de uso dice a los trabajadores qué se registra, quién lo ve, cuánto tiempo y cómo pedir sus propias entradas (decisión 9);
  - **no se activa hasta que esa política esté publicada.** Un interruptor por instalación, que **se construye en la fase A** y está apagado por defecto (D-105, D-022):
    - mientras está apagado **no se registra nada** y el panel lo dice;
    - activarlo o desactivarlo es la primera entrada una vez encendido;
  - supresión (art. 17): ver «Datos personales aparte» en el apartado 2.
- **Nada sale de la oficina del cliente** salvo lo que el administrador pida por el chat (apartado 3).

## 6. Plan (sin estimación de tiempo hasta las decisiones)

| Fase | Qué | Cómo se verifica |
|---|---|---|
| A | Migración de `audit_event`, `audit_actor` y rol `odin_audit_maint`; solo añadir (disparador y REVOKE TRUNCATE); cadena global con bloqueo; puerto y adaptador; **interruptor (apagado por defecto)**; entradas en todos los casos de uso del apartado 1, incluidas las **escrituras del trabajador desde el chat** (fichas, añadir y quitar del expediente, emitir enlaces, subida por enlace y la del encargo 17), los endpoints de instalación y los hitos del sistema; negados y fallidos | Integración: cada acción deja exactamente una entrada con sus campos; cambiar, borrar y TRUNCATE fallan; la cadena detecta una entrada alterada; **concurrencia** (N escrituras en paralelo dan una cadena válida); **manipulación por el dueño** detectada contra un sello; una entrada negada o fallida también entra en la cadena y toma el bloqueo; interruptor apagado: nada se registra; `via` = id del enlace; instalador con token malo: `negado`; ningún `details` lleva texto de preguntas, documentos ni fichas |
| B | Seguridad del panel: caducidad de 30 minutos y 12 horas, columnas user-agent e IP en `admin_session`, sesiones abiertas, confirmaciones, volver a identificarse con `reauthenticated_at` | API: sesión caducada; **límite absoluto de 12 h sobre `created_at`**; sin reidentificar → 403 y negativa registrada; migración y listado de sesiones con navegador e IP; panel (diálogos) |
| C | Lectura: `GET /v1/admin/activity` con filtros y cursor, página «Actividad», exportación CSV (con la huella de cabeza), sello mensual y estado de la cadena | Un trabajador → 403; un administrador de empresa solo ve la suya; paginación estable; contrato sin agrupaciones por persona; **la exportación CSV deja su entrada** |
| D | Chat: clave de administrador, categoría de ruta propia, `GET /v1/activity`, herramienta `listActivity` | Una clave de trabajador o de otro ámbito → 403; sin intervalo o de más de 7 días → rechazo; el tope de 500 filas al día; sin correo en la salida; cada llamada queda registrada |
| E | Actividad de los trabajadores: recuento por persona y día | Cuenta `question_record` sin leer el texto; **ninguna fila de consultas expone horas** |
| F | Guiones de instalación | Un despliegue de ensayo deja sus entradas; **fallo de un despliegue y deshacer (restauración)** también |
| G | Purga por plazo y texto de la política (CEO y legal) | La purga deja su entrada con `last_purged_hash`; **verificar después de purgar**; el ancla cruza la purga; `bound_address` queda a nulo 24 h tras caducar el enlace |

- **Coordinación:**
  - con el CTO 2: la revisión paginada (encargo 15) escribe en este registro;
  - con el encargo 17: cada subida a un proyecto queda en el registro (D-106), desde la fase A;
  - con el CTO: los guiones de instalación (fase F) y el rol y las credenciales de `odin_audit_maint` en el `.env`;
  - con el CTO de la primera carga y el encargo 17: el número de la migración (D-055, D-107).
- **Orden recomendado:** A y B primero, porque cubren D-099; después C. D, E y F más tarde, y G antes de activarlo en un cliente.
- **D-099, lo que era «ya»:** el bloqueo de puertos de los guiones de prueba y la regla para las sesiones de Claude son normas de trabajo, no del producto. No entran en este plan. Lo que sí entra son sus propuestas de confirmación, registro y caducidad (fases A y B).

## 7. Riesgos

- **Volumen:** las acciones de administración son pocas. Lo que más crece son los recuentos de consultas (una fila por persona y día); los hitos del sistema añaden una fila por documento.
  - Con 50 personas son unas 18.000 filas al año: sin problema. No es una cifra medida: es la cuenta con ese supuesto.
- **Cadena de huellas:** una cadena global con bloqueo corto serializa todas las escrituras de la instalación. Con este volumen es aceptable; no está medido.
- **El dueño de la base** puede deshacer el solo-añadir. Se mitiga con el ancla (decisión 12) y se documenta como punto de confianza.
- **El chat como canal de salida de datos personales:** véase el apartado 3 y la decisión 3.

## 8. Decisiones del propietario (con la recomendación del CTO 3)

| # | Decisión | Opciones | Recomendación |
|---|---|---|---|
| 1 | Qué se registra de las consultas de los trabajadores | a) nada nuevo (el panel ya tiene recuentos); b) **recuento por persona y día, sin texto**; c) una entrada por consulta, sin texto | **b**: «toda acción monitorizada» sin leer lo que se pregunta (D-005, D-040) |
| 2 | Quién lo ve | solo los administradores de grupo; o también los de empresa, cada uno lo suyo | **También los de empresa, solo su empresa** |
| 3 | Consultarlo por el chat | sí, con una clave de administrador para el chat; o solo en el panel | **Sí, pero apagado por defecto** y tras la validación legal: los datos llegan a la IA del administrador. Con intervalo de 7 días, tope de 500 filas al día y sin correo |
| 4 | Plazo de conservación | 6, 12 o 24 meses | **12 meses las acciones y 6 los recuentos de consultas**, pendiente de la validación legal (D-022) |
| 5 | IP y navegador; y `bound_address` de los enlaces | en ninguna; solo en las entradas al panel; en todo | **Solo en las entradas al panel**, con el plazo de la decisión 4. `bound_address` se pone a nulo 24 h tras caducar el enlace |
| 6 | Volver a identificarse | nunca; en lo más delicado | **En lo más delicado**: capacidades, administradores del grupo, claves y borrados |
| 7 | Caducidad de la sesión | 8 horas sin uso (hoy); 30 minutos sin uso y 12 horas de máximo | **30 minutos y 12 horas** |
| 8 | Filtrar por persona en el panel | sí; no | **Sí, para auditar hechos**, nunca para ordenar ni contar |
| 9 | Que cada trabajador vea sus propias entradas | sí (transparencia, derecho de acceso); no | **Sí**, más adelante («mi actividad» en el chat) |
| 10 | Registrar qué documentos lee cada trabajador | no; solo las descargas del original (B-011) | **Solo las descargas del original** (administrador y trabajador, sin contenido), ya en el catálogo: lo que lee cada uno sería vigilancia. **Cerrada** |
| 11 | Supresión frente a inmutabilidad (art. 17) | nombre y correo dentro de la fila; o en una tabla aparte fuera de la huella | **Tabla aparte `audit_actor`**; suprimir borra esa fila y las entradas muestran «persona suprimida». Requiere validación legal |
| 12 | Dónde se ancla la última huella | solo en la base; huella y fichero «sello» mensual que descarga el administrador; almacén externo | **Huella en el panel, en cada CSV y en un sello mensual**; se verifica contra un sello que el administrador conserva |
| 13 | Correo de un intento de entrada fallido | guardarlo; solo `user_id`; `user_id` o HMAC del correo | **`user_id` si existe; si no, HMAC del correo, 30 días** |
| 14 | Endpoint de recuperación de la instalación (`/v1/setup/admin-password`) | quitarlo; mantenerlo | **Mantenerlo y registrarlo** (actor `instalador`, `negado` si el token es malo) |
| 15 | Una cadena global o una por empresa | global; por empresa | **Global por instalación**, con bloqueo (la lectura del último eslabón sale del ámbito de empresa) |
| 16 | Roles y credenciales de purga y mantenimiento | la aplicación con más permisos; rol aparte | **Rol `odin_audit_maint`** con sus credenciales en el `.env`, aparte de la aplicación y del dueño de la base |
| 17 | Clave de administrador para el chat | no hay; clave de 30 días, solo `GET /v1/activity` | **Clave de 30 días**, emitida desde el panel con volver a identificarse, categoría de ruta propia y comprobación en el servidor |
| 18 | Hora o solo día en el número de consultas | con hora; solo fecha y número | **Solo fecha y número** (D-045) |
| 19 | Interruptor desde la fase A | en la fase G; en la A | **En la A, apagado por defecto**; apagado no se registra nada y el panel lo dice |
| 20 | Registrar las escrituras del sistema | todo; solo hitos; nada | **Solo hitos** con autor `sistema`: volumen y sin datos personales |
