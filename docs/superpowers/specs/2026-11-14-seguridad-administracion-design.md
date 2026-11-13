# Cambios de administración más seguros (D-099) · diseño

- Fecha: 2026-11-14. Autor: CTO. Encargo del CEO tras el incidente del 13 de noviembre.
- **Estado:** propuesta para que la priorice el propietario. No se ha construido nada.
- **Incidente:**
  - a las 21:26 del 13 de noviembre, un clic en el panel quitó a Gestión Albar la capacidad «casos parecidos» (`SIMILAR_CASES`);
  - fue desde el navegador integrado de Claude Desktop, en una sesión de administrador abierta a las 18:31;
  - no se pudo saber quién fue: ni la API ni la base guardan quién cambia una capacidad.

## 1. Confirmación en el panel

- **Hoy:** el interruptor de capacidades (`panel/src/components/Capabilities.tsx`) manda el cambio en cuanto se pulsa. Borrar un proyecto o quitar a un miembro ya piden confirmación (`confirm(...)` en `Projects.tsx` y `Members.tsx`); las capacidades no.
- **Propuesta:** el mismo diálogo de confirmación antes de:
  - **quitar una capacidad**, con su efecto en palabras: «¿Quitar Casos parecidos a Gestión Albar? Sus trabajadores dejarán de ver la herramienta de casos parecidos.» Añadir una capacidad no pide confirmación;
  - **cambiar el nombre o el código de una empresa;**
  - **quitar a alguien el papel de administrador.**
- **Pruebas:** al cancelar no sale ninguna petición; al confirmar sale una sola.

## 2. Registro de cambios de administración

- **Tabla nueva `admin_event`** (en la siguiente migración libre, coordinada con el CTO 2): `id`, `company_id` (nulo si es de grupo), `actor_id`, `action`, `target_type`, `target_id`, `before` y `after` (JSONB), `at` y `source` (`panel` o `api`).
  - Sigue el patrón de `case_file_event` del expediente.
  - La protege la misma seguridad por filas que el resto (el administrador de una empresa solo ve la suya).
- **Qué se registra:**
  - las capacidades;
  - el alta, la baja y los cambios de miembros y papeles;
  - las claves dadas y retiradas;
  - los proyectos creados, abiertos, cerrados y borrados, y sus miembros;
  - la revisión de fichas;
  - las sustituciones y las retiradas de documentos;
  - el inicio de sesión del panel.
  - No se guardan contraseñas ni claves, solo su etiqueta o prefijo.
- **Cómo:** cada caso de uso de administración que cambia algo escribe su evento en la misma transacción. Si falla el registro, no se hace el cambio.
- **Dónde se ve:**
  - una página «Cambios» en el panel, filtrable por empresa, persona y fecha;
  - el CTO la consulta por SQL de solo lectura cuando haga falta investigar.
- **Registro de peticiones:** además, se añade una línea por petición en el registro de la API (método, ruta sin datos, estado, quién y duración), sin cuerpos. Hoy solo el nginx del panel deja rastro, y no ve lo que llega directo al 8080.
- **Conservación:** se guarda sin plazo de borrado mientras no se decida otra cosa. Es poco volumen. El plazo lo decide el propietario con lo legal (D-022).

## 3. Caducidad de la sesión del panel

- **Hoy:** `SessionSettings` cierra la sesión tras **8 horas sin peticiones**. Cualquier petición la renueva, también las recargas automáticas del panel, así que una pestaña abierta puede mantenerla viva sin nadie delante. La del incidente llevaba casi 5 horas.
- **Propuesta:**
  - **30 minutos sin acción del usuario.** Solo cuentan las peticiones que nacen de un clic o de una navegación, no las recargas automáticas. El panel marca sus recargas, y la API no renueva la sesión con ellas;
  - **8 horas como máximo** desde el inicio de sesión, aunque haya actividad;
  - **volver a pedir la contraseña** antes de los cambios del apartado 1 si han pasado más de 15 minutos desde la última vez que se escribió;
  - las tres cifras, configurables en `deploy/.env`.
- **Pruebas:** sesión caducada por inactividad, por el máximo y por la reautenticación; las recargas automáticas no la renuevan.

## 4. Lo que ya está hecho (no es parte de este encargo)

- **Guiones de prueba:** las órdenes de instalación se niegan a usar los puertos 8080, 8088 y 8090 desde una pila de ensayo, y escribir en la del propietario pide `CONFIRMAR_INSTALACION=PROPIETARIO` (`integracion/instalacion` 9d7e468). El CTO 2 y el CTO 3 lo llevan a sus ejecutores.
- **Regla para las sesiones** (la comunica el CEO): nadie usa el panel del propietario como administrador sin su permiso.

## 5. Orden propuesto y lo que decide el propietario

1. **Confirmación en el panel:** lo más pequeño y lo que habría parado el incidente.
2. **Registro de cambios y de peticiones:** sin él, la próxima vez tampoco se sabrá quién fue.
3. **Caducidad y reautenticación.**

**Decide el propietario:**
- la prioridad frente a lo demás;
- las cifras de la sesión (30 minutos, 8 horas y 15 minutos);
- si quiere la página «Cambios» en el panel o le basta con que se pueda consultar;
- el plazo de conservación del registro (con lo legal).
