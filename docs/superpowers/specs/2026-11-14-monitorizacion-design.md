# Monitorización de acciones y seguridad del panel (encargo 16, D-105 y D-099): diseño y plan

- Fecha: 2026-11-14. Autor: CTO 3. Solo diseño y plan: no se construye nada hasta que el propietario decida los puntos del apartado 8 y el CEO valide.
- **Lo que pide el propietario (D-105):** «Toda acción debe quedar monitorizada y además solo el admin puede ver esas monitorizaciones, estaría bien además que pueda verlas a través del chat».
- **Lo que pasó (D-099):** alguien quitó con un clic, desde el panel, los casos parecidos de Gestión Albar. No se pudo saber quién: la API no registra peticiones y la base no guarda quién cambia las capacidades.
- **Punto de partida** (rama `integracion/instalacion`):
  - no hay ningún registro general de acciones;
  - solo existen `case_file_event` (expedientes) y algunas columnas sueltas de autor (`uploaded_by`, `withdrawn_by`, `reviewer_id`, `decided_by`, `added_by`);
  - faltan quién cambia capacidades, personas, papeles, claves, proyectos y sustituciones;
  - la sesión del panel caduca a las 8 horas sin uso y no tiene límite absoluto;
  - no hay papel de administrador en el chat.

## 1. Qué se registra

**Cada entrada** dice:
- **quién:** la persona y su papel en ese momento, o «sistema» o «instalador»;
- **qué:** un código de acción de una lista cerrada;
- **cuándo:** la hora del servidor;
- **sobre qué:** el tipo, el identificador y un nombre legible del objeto;
- **por dónde:** panel, API, chat (MCP), guion o sistema;
- **con qué resultado:** hecho, negado o fallido.

En los cambios de configuración guarda también el valor de antes y el de después (por ejemplo, las capacidades).

| Área | Acciones | Detalle que se guarda |
|---|---|---|
| Acceso al panel | entrada, entrada fallida, salida, sesión caducada, sesión cerrada por cambio de contraseña | Correo intentado en las fallidas; navegador e IP (decisión 5) |
| Organización | crear o cambiar empresa, **capacidades** (antes y después), administradores del grupo (alta y baja) | Campos cambiados |
| Personas y claves | alta, cambio de papel, baja, cambio de contraseña (sin la contraseña), **emisión y revocación de claves** | Solo el prefijo de la clave |
| Proyectos | crear, cambiar (restringido o abierto), borrar; miembros (alta y baja) | Nombre y estado |
| Documentos | subida (también por enlace desde el chat), reintento, retirada y su deshacer, marca de sustituido y su deshacer, marca de caso, temas (cambio y reetiquetado), metadatos | Título, proyecto, tamaño y huella; **nunca el contenido** |
| Fichas | alta (por un trabajador), revisión (aprobada, corregida o rechazada) | Identificador y proyecto; **nunca el texto** |
| Expedientes | añadir, quitar o mover documentos | Hoy van en `case_file_event`; se registran también aquí con el mismo formato |
| Consultas de los trabajadores | **sin texto**: solo un recuento por persona y día (decisión 1) | Número de consultas y primera y última hora |
| Instalación | despliegue, actualización, restauración, proyecto añadido, reinicio | Versión, quién lo lanzó (el operador), resultado y fichero de bitácora |
| El propio registro | consulta desde el chat, exportación | Filtros usados |

**No se registra:**
- el texto de las preguntas ni de las respuestas (D-036, D-040);
- el contenido de documentos y fichas;
- contraseñas y claves completas;
- lo que cada trabajador lee en las búsquedas (decisión 10).

## 2. Cómo se guarda

- **Tabla nueva `audit_event`** (migración V25). Columnas:
  - `id`, `occurred_at`, `group_id`, `company_id`, `project_id`;
  - quién: `actor_kind` (persona, sistema o instalador), `actor_user_id`, `actor_label` (nombre y correo en ese momento, para que el registro no cambie si la persona cambia de nombre o se da de baja) y `actor_role`;
  - qué y dónde: `channel`, `action`, `target_kind`, `target_id`, `target_label`, `outcome`;
  - `details` (jsonb: antes y después, siempre sin contenido);
  - `request_id`, `prev_hash` y `hash`.
- **Solo se añade, nunca se cambia:**
  - el usuario de la aplicación tiene permiso de insertar y leer, no de cambiar ni de borrar;
  - un disparador en la base niega cualquier cambio o borrado;
  - cada entrada lleva la huella de la anterior (cadena de huellas SHA-256). Una comprobación periódica verifica la cadena, y el panel muestra «Registro íntegro» o avisa del primer eslabón roto.
- **Desde el panel no se puede borrar ni cambiar nada,** porque no hay ni endpoint ni permiso.
  - Las entradas viejas solo las quita la purga automática al pasar el plazo de conservación (decisión 4), con un usuario de mantenimiento aparte.
  - La purga deja su propia entrada («purgado hasta …») y la cadena sigue a partir de ella.
- **En la misma transacción que el cambio:** la entrada se escribe con el mismo commit que la acción. Si el cambio no se guarda, tampoco su entrada. Los intentos negados o fallidos se registran aparte.
- **Arquitectura hexagonal,** como el resto de la API:
  - un tipo de evento en el dominio;
  - un puerto `AuditLog` en la aplicación, al que llama cada caso de uso de la lista;
  - un adaptador de PostgreSQL.
  - El canal sale de la propia petición: el MCP manda una cabecera `X-Odin-Channel: mcp` y los guiones la suya.
- **Guiones de instalación:** desplegar, actualizar, restaurar, añadir proyecto y reiniciar escriben su entrada con una orden pequeña que usa el usuario de mantenimiento. La bitácora de fichero que ya escriben se queda como está.
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
  - una herramienta del conector, `listActivity`, que solo aparece con una **clave de administrador para el chat**. Hoy los administradores no tienen clave de chat: es una clave nueva, emitida desde el panel y ligada a esa persona y a su ámbito (grupo o empresa);
  - el MCP la registra solo si `/v1/me` dice que la clave es de administrador;
  - devuelve entradas (hechos) con los mismos filtros y como mucho 50 por llamada;
  - no ofrece agrupar ni contar por persona, y su descripción le dice a la IA que no valore, compare ni ordene a nadie (D-005);
  - cada consulta desde el chat queda registrada con sus filtros;
  - **aviso legal:** lo que se consulta por el chat llega a la IA del administrador, que es un tercero (D-016, D-019). Va a la validación legal (D-022) y está apagado por defecto (decisión 3).

## 4. Seguridad del panel (D-099), que comparte el registro

| Medida | Propuesta |
|---|---|
| Caducidad de la sesión | **30 minutos sin uso** (hoy son 8 horas) y **12 horas como máximo** aunque se use (hoy no hay máximo). Aviso en el panel 5 minutos antes |
| Sesiones abiertas | El administrador ve sus sesiones (desde cuándo, desde qué navegador) y puede cerrarlas. Habría delatado la sesión de D-099, abierta a las 18:31 y viva a las 23:25 |
| Confirmación | Un diálogo de confirmación en todos los cambios sensibles: hoy solo lo tienen algunos (miembros, proyectos, retirar documentos, expedientes). Se añade a capacidades, papeles, administradores del grupo, claves, contraseñas, sustituciones, reetiquetado y revisión de fichas |
| Volver a identificarse | Para lo más delicado (capacidades, administradores del grupo, emisión y revocación de claves, borrados), la contraseña si la última vez fue hace más de 10 minutos (decisión 6). Si no se confirma, se registra como negado |
| Origen | Se mantiene `OriginCheckFilter` y `SameSite=Strict`. No cambia |

## 5. Límites (D-005, D-036, D-040, D-045, D-022)

- **Hechos, no evaluación:**
  - el registro responde a «quién hizo qué y cuándo»;
  - no hay puntuaciones, comparaciones, rankings ni recuentos por persona salvo los de consultas que ya permite D-045 (ordenados por nombre);
  - la IA del chat recibe hechos y la instrucción de no juzgar.
  - Se comprueba con pruebas de contrato: ninguna ruta agrupa ni ordena por persona.
- **Preguntas:** nunca su texto. Como mucho, cuántas por persona y día (decisión 1). Los huecos siguen como hoy: sin autor y con un mínimo de 3 personas (D-036).
- **RGPD y LOPDGDD:**
  - minimización: solo lo de la tabla del apartado 1;
  - plazo de conservación (decisión 4);
  - la política de uso dice a los trabajadores qué se registra, quién lo ve, cuánto tiempo y cómo pedir sus propias entradas (decisión 9);
  - **no se activa hasta que esa política esté publicada.** Un interruptor por instalación, apagado por defecto (D-105, D-022).
- **Nada sale de la oficina del cliente** salvo lo que el administrador pida por el chat (apartado 3).

## 6. Plan (sin estimación de tiempo hasta las decisiones)

| Fase | Qué | Cómo se verifica |
|---|---|---|
| A | Tabla `audit_event` (V25), permisos de solo añadir, disparador, cadena de huellas, puerto y adaptador; entradas en todos los casos de uso del apartado 1; negados y fallidos | Pruebas de integración: cada acción deja exactamente una entrada con sus campos; cambiar o borrar falla; la cadena se verifica y detecta una entrada alterada; ningún `details` lleva texto de preguntas, documentos ni fichas |
| B | Seguridad del panel: caducidad de 30 minutos y 12 horas, sesiones abiertas, confirmaciones, volver a identificarse | Pruebas de la API (sesión caducada, sin reidentificar → 403 registrado) y del panel (diálogos) |
| C | Lectura: `GET /v1/admin/activity` con filtros y cursor, página «Actividad», exportación CSV y estado de la cadena | Un trabajador → 403; un administrador de empresa solo ve la suya; paginación estable; prueba de contrato sin agrupaciones por persona |
| D | Chat: clave de administrador, `GET /v1/activity` y la herramienta `listActivity` del MCP | Pruebas del MCP: la herramienta solo aparece con esa clave y cada llamada queda registrada |
| E | Actividad de los trabajadores: recuento por persona y día, y escrituras por el chat (fichas, expedientes y la subida por enlace del CTO 2, D-105) | El recuento sin texto; las escrituras con canal `mcp` |
| F | Guiones de instalación | Un despliegue de ensayo deja sus entradas |
| G | Purga por plazo, interruptor de activación y texto de la política (CEO y legal) | La purga deja su entrada y la cadena sigue; apagado no se registra nada nuevo |

- **Coordinación:**
  - con el CTO 2: la subida desde el chat (D-105) y la revisión paginada (encargo 15) escriben en este registro;
  - con el CTO: los guiones de instalación (fase F) y el interruptor por instalación.
- **Orden recomendado:** A y B primero, porque cubren D-099; después C. D, E y F más tarde, y G antes de activarlo en un cliente.

## 7. Riesgos

- **Volumen:** las acciones de administración son pocas. Lo que más crece son los recuentos de consultas (una fila por persona y día).
  - Con 50 personas son unas 18.000 filas al año: sin problema. No es una cifra medida: es la cuenta con ese supuesto.
- **Cadena de huellas:** dos escrituras a la vez necesitan un orden. Se serializan con un bloqueo corto, o con una cadena por empresa.
- **El chat como canal de salida de datos personales:** véase el apartado 3 y la decisión 3.

## 8. Decisiones del propietario (con la recomendación del CTO 3)

| # | Decisión | Opciones | Recomendación |
|---|---|---|---|
| 1 | Qué se registra de las consultas de los trabajadores | a) nada nuevo (el panel ya tiene recuentos); b) **recuento por persona y día, sin texto**; c) una entrada por consulta, sin texto | **b**: «toda acción monitorizada» sin leer lo que se pregunta (D-005, D-040) |
| 2 | Quién lo ve | solo los administradores de grupo; o también los de empresa, cada uno lo suyo | **También los de empresa, solo su empresa** |
| 3 | Consultarlo por el chat | sí, con una clave de administrador para el chat; o solo en el panel | **Sí, pero apagado por defecto** y tras la validación legal: los datos llegan a la IA del administrador |
| 4 | Plazo de conservación | 6, 12 o 24 meses | **12 meses las acciones y 6 los recuentos de consultas**, pendiente de la validación legal (D-022) |
| 5 | IP y navegador | en ninguna; solo en las entradas al panel; en todo | **Solo en las entradas al panel** (seguridad) |
| 6 | Volver a identificarse | nunca; en lo más delicado | **En lo más delicado**: capacidades, administradores del grupo, claves y borrados |
| 7 | Caducidad de la sesión | 8 horas sin uso (hoy); 30 minutos sin uso y 12 horas de máximo | **30 minutos y 12 horas** |
| 8 | Filtrar por persona en el panel | sí; no | **Sí, para auditar hechos**, nunca para ordenar ni contar |
| 9 | Que cada trabajador vea sus propias entradas | sí (transparencia, derecho de acceso); no | **Sí**, más adelante («mi actividad» en el chat) |
| 10 | Registrar qué documentos lee cada trabajador | no; solo las descargas del original (B-011) | **Solo las descargas del original**: lo que lee cada uno sería vigilancia |
