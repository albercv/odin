# Odín · Fase 2a: identidad con pertenencias y proyectos cerrados por defecto

- Fecha: 2026-11-10. Autor: CTO. Encargo 11.
- Decisión de negocio que lo origina: D-038 (CEO y propietario, 2026-11-10). El propietario la corrigió el 2026-11-11: los proyectos están cerrados por defecto (P1 de `docs/informes/2026-11-11-observaciones-propietario-panel.md`).
- Amplía `2026-11-09-api-central-design.md` («spec base») y `2026-11-10-correcciones-prueba-propietario-design.md` («spec 1.1»). Lo que este documento no cambia sigue como está allí.
- Rama: `feature/identidad-pertenencias`, que sale de `feature/api-central`.

## 1. Por qué

Hoy un usuario tiene un solo papel y una sola empresa. D-038 pide otra cosa:

| D-038 | Hoy | Qué cambia |
|---|---|---|
| Una sola identidad por persona, con una pertenencia por cada empresa | Un usuario por empresa, con correos distintos | La persona se separa de su pertenencia a cada empresa |
| Las claves MCP van por pertenencia | La clave es del usuario | La clave dice quién es y en qué empresa actúa |
| Un solo usuario puede ser administrador y trabajador | Hacen falta dos usuarios (D-037) | Los papeles pasan a la pertenencia y se pueden sumar |
| Proyectos cerrados por defecto: cada persona ve solo los que tiene asignados (corrección del propietario, 2026-11-11) | El trabajador ve todos los proyectos de su empresa | Aparecen los miembros de cada proyecto y la opción de hacerlo visible para toda la empresa |

## 2. Decisiones

**De negocio (D-038, con la corrección del 2026-11-11):** las de la tabla anterior. Además:
- los documentos sin proyecto los ve toda la empresa;
- filtrar por proyecto se hace en la conversación con la IA; el MCP no gana herramientas;
- nunca hay claves por proyecto;
- cada administrador de empresa da y retira los accesos a la suya, sin depender del de otra.

**Resoluciones del CTO sobre la corrección del 2026-11-11**, pendientes de validar por el CEO. Cambiarlas cuesta poco:
- los documentos y las fichas sin proyecto los sigue viendo toda la empresa, como en D-038;
- el administrador puede marcar un proyecto como «Visible para toda la empresa», a propósito y proyecto a proyecto;
- al migrar, cada proyecto que ya existe queda cerrado, con todos los trabajadores activos de su empresa como miembros: nadie pierde el acceso que tiene hoy.

**Del CTO:**
- El correo sigue siendo opcional en quien solo trabaja, y único cuando existe.
- El administrador del grupo deja de ser un papel con empresa vacía y pasa a ser una marca de la persona.
- Quien administra varias empresas entra una sola vez en el panel y elige empresa.
- Un proyecto que el trabajador no puede ver se comporta como si no existiera: 404, nunca 403.
- La restricción por proyecto lleva las mismas dos barreras que la separación entre empresas: el caso de uso con su filtro SQL, y la seguridad por filas de PostgreSQL.

## 3. Fuera de alcance

- Rediseño visual del panel y marca configurable: fase 2b.
- Compartir conocimiento entre empresas del grupo: sigue pendiente de negocio (D-033).
- Equipos o grupos de personas dentro de una empresa. Los miembros de un proyecto se añaden de uno en uno.
- Que el trabajador vea sus fichas pendientes (D-037): fase aparte.

## 4. Modelo

| Concepto | Nombre en código | Qué tiene |
|---|---|---|
| Persona | `User` | Nombre, correo opcional y único, contraseña opcional, si es administrador del grupo, si está activa. Deja de tener papel y empresa |
| Pertenencia | `Membership` | Persona, empresa, papeles (`COMPANY_ADMIN`, `WORKER` o los dos) y si está activa. Una por persona y empresa |
| Clave personal | `AccessKey` | Pasa a ser de una pertenencia: guarda la persona y la empresa |
| Proyecto | `Project` | Gana `restricted`: verdadero por defecto. Falso significa «Visible para toda la empresa» |
| Miembro de proyecto | `ProjectMember` | Proyecto y persona. Solo cuenta si el proyecto no es visible para toda la empresa |

- **Papel con el que se actúa:** `Actor` sigue llevando un solo papel por petición.
  - Con la sesión del panel: `GROUP_ADMIN`, o `COMPANY_ADMIN` con el conjunto de empresas que administra.
  - Con una clave personal: `WORKER` en la empresa de la clave.
- **Contraseña:** la necesita quien es administrador del grupo o tiene `COMPANY_ADMIN` en alguna pertenencia. Quien solo trabaja no tiene.
- **Borrados:** lógicos. Borrar una pertenencia no borra a la persona. Borrar a la persona borra sus pertenencias.

## 5. Reglas

### 5.1 Personas y pertenencias

- **Dar de alta en una empresa:** nombre, correo, papeles y, si hay `COMPANY_ADMIN` y la persona no tiene contraseña, contraseña. Con papel `WORKER`, además, los proyectos de esa empresa a los que accede (opcional; sin ninguno no ve ningún proyecto).
  - Si ya existe una persona con ese correo, se le añade la pertenencia y no se crea otra persona. Su nombre no cambia.
  - Si la persona ya pertenece a esa empresa: 409, «Esa persona ya pertenece a esta empresa».
- **Quién gestiona qué:**

| Acción | Administrador del grupo | Administrador de la empresa |
|---|---|---|
| Añadir una pertenencia a una empresa | Sí | Solo en la suya |
| Quitar una pertenencia | Sí | Solo en la suya, y solo si esa pertenencia no administra la empresa |
| Cambiar los papeles de una pertenencia | Sí | Solo `WORKER`, en la suya |
| Dar o quitar `COMPANY_ADMIN` | Sí | No |
| Activar o desactivar una pertenencia | Sí | Solo en la suya, y solo si esa pertenencia no administra la empresa |
| Editar nombre y correo de la persona | Sí | Solo si la persona no pertenece a ninguna otra empresa |
| Desactivar o borrar a la persona entera | Sí | No |

- **Quitar el último papel** de una pertenencia no vale: se borra la pertenencia.
- **Quitar a una persona de una empresa revoca sus claves de esa empresa.** Si se la vuelve a añadir, necesita claves nuevas. Desactivar y reactivar la pertenencia conserva las claves.
- **Una pertenencia que administra la empresa** solo la activa, la desactiva o la quita el administrador del grupo (ajuste de la revisión de la tarea 2): reactivarla equivale a dar la administración.
- **Nadie se quita a sí mismo** el papel con el que está actuando, ni se desactiva ni se borra.
- **Un administrador de empresa** solo ve, de cada persona, lo que toca a su empresa: no ve en qué otras empresas está.

### 5.2 Claves personales

- Se dan sobre una pertenencia activa con papel `WORKER`.
- La clave resuelve a `Actor(persona, WORKER, empresa de la clave)`.
- Deja de valer (401) si se revoca, si la pertenencia se desactiva, se borra o pierde `WORKER`, o si la persona se desactiva o se borra.
- Las claves que ya existen pasan a la pertenencia del usuario que las tenía.

### 5.3 Panel y sesión

- Entra quien tiene contraseña y es administrador del grupo o `COMPANY_ADMIN` en alguna pertenencia activa.
- `GET /v1/admin/me` devuelve además las empresas que administra.
- Quien administra una sola empresa entra directamente en ella. Quien administra varias elige; el administrador del grupo ve todas, como hoy.
- Si la misma persona es administradora y trabajadora, revisa también sus propias fichas. Queda guardado quién revisó (D-038).

### 5.4 Proyectos cerrados por defecto

- **Qué ve un trabajador:** los documentos y las fichas sin proyecto, los de proyectos visibles para toda la empresa y los de proyectos de los que es miembro.
- **Un proyecto nuevo** nace cerrado y sin miembros: ningún trabajador lo ve hasta que se le asigna.
- **Dónde se aplica:** `searchKnowledge`, `listDocuments`, `readPage`, `verifyCitation`, `saveKnowledgeCard` y la lista de proyectos de `DescribeMe`.
- **Lo que no ve no existe para él:** un proyecto que no tiene asignado da 404 al filtrar, y sus documentos y fichas no salen en ninguna búsqueda ni lectura.
- **El administrador de la empresa** ve y gestiona todos sus proyectos: marca o desmarca «Visible para toda la empresa» y añade o quita miembros, desde el proyecto, desde la persona («Accesos») y en el alta.
- **Miembros:** solo personas con pertenencia activa y papel `WORKER` en esa empresa.
- **Al quitar «Visible para toda la empresa»** solo lo ven sus miembros. Si no tiene ninguno, el panel lo avisa antes de confirmar.
- **Al marcar «Visible para toda la empresa»**, sus miembros se conservan por si se vuelve a cerrar.

## 6. Aislamiento: dos barreras

- **Primera:** cada caso de uso del trabajador calcula sus proyectos visibles y cada consulta SQL lleva el filtro explícito.
- **Segunda:** seguridad por filas en `document`, `document_page` y `knowledge_card`. Además de la empresa, la política exige `project_id IS NULL`, o `project_id` dentro de `app.project_ids`, o `app.all_projects = 'on'`.
  - Las rutas del trabajador fijan `app.project_ids` con sus proyectos visibles.
  - Las rutas del administrador fijan `app.all_projects`.
  - Si no se fija ninguna de las dos, solo se ve lo que no tiene proyecto.
- **Pruebas obligatorias:**
  - cada caso de uso del trabajador, con un proyecto que no tiene asignado;
  - la lectura con una conexión que se salta el filtro del código, como la que ya existe para empresas;
  - cada caso de uso con actor, con un actor de otra empresa (D-014), incluidas las pertenencias.

## 7. Datos que ya existen

La migración conserva todo:
- cada usuario con empresa pasa a ser una persona con una pertenencia, con el papel que tenía;
- el administrador del grupo pasa a ser una persona con la marca de administrador del grupo;
- cada clave pasa a la pertenencia de su usuario;
- cada proyecto queda cerrado, con todas las pertenencias activas con papel `WORKER` de su empresa como miembros: nadie pierde el acceso que tenía.

## 8. Contrato

Cambian las rutas de usuarios y se añaden las de pertenencias y miembros. Los nombres exactos los fija el plan. Lo que no cambia:
- las rutas del trabajador y las herramientas del MCP;
- la cookie de sesión y el formato de la clave personal.

## 9. Panel

Cambios funcionales, sin rediseño (fase 2b):
- **Personas de la empresa:** la lista muestra nombre, correo, papeles y estado de la pertenencia. Tras un alta con un correo que ya era de otra persona del grupo, el panel avisa: «Esta persona ya existía en el grupo: se ha añadido a esta empresa».
- **Papeles:** casillas «Administra la empresa» y «Trabaja con su IA».
- **Claves:** en quien tiene el papel de trabajador.
- **Selector de empresa** para quien administra varias.
- **Proyectos:** casilla «Visible para toda la empresa», sin marcar por defecto, y lista de miembros.
- **Accesos de cada persona** (observación O1 del propietario): sus proyectos asignados, con «Añadir» y «Quitar», y cuántos proyectos ve además por ser visibles para toda la empresa. En el alta, los proyectos se eligen con casillas.
- **Claves** (O2): junto a ellas, «Cada clave da acceso a lo mismo que ve <persona> en esta empresa».
- **Botones de fila** (O3): texto corto («Revocar clave», «Quitar»), y el nombre de lo afectado solo en el nombre accesible, que empieza por el texto visible.

## 10. Criterio de cierre

- `make check` y `make demo` terminan con código de salida 0.
- La prueba de extremo a extremo de la administración cubre: la misma persona en dos empresas con una clave en cada una, una persona administradora y trabajadora a la vez, un proyecto que un trabajador sin asignar no encuentra, y una persona dada de alta con ese proyecto que sí lo encuentra.
- Una instalación de la fase 1.1 con datos arranca con el código nuevo y conserva usuarios, claves y proyectos.
