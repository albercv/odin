# Odín · Cierre de la fase 2a

- Fecha: 2026-11-12. Autor: CTO. Para: propietario y CEO. Encargo 11.
- Rama: `feature/identidad-pertenencias` en f4bcb22. Sale de `feature/api-central`, que ya está en `develop` (297b307).
- Diseño: `docs/superpowers/specs/2026-11-10-identidad-pertenencias-design.md`. Plan: `docs/superpowers/plans/2026-11-10-fase-2a-identidad-pertenencias.md`.
- Decisiones de negocio: D-038, D-047 (proyectos cerrados por defecto), D-048 (código de persona) y D-051 (relevo del administrador del grupo).

## 1. Conclusión

La fase 2a está terminada y verificada:

- `make check` y `make demo` terminan con código de salida 0 sobre f4bcb22, ejecutados por el CTO.
- Cada una de las 7 tareas pasó su propia revisión.
- Después hubo una revisión final de toda la rama, con dos rondas de arreglos revisadas.

Lo que queda abierto está en el apartado 6 y en el backlog: B-005 a B-007.

Siguiente paso: el CEO recorre el panel como administrador. Si el propietario aprueba, la feature pasa a `develop`, y su instalación se actualiza con copia de seguridad previa.

## 2. Qué hay ahora

| Tarea | Qué hace |
|---|---|
| 1 y 2 | Una persona tiene una pertenencia por empresa. Puede administrar y trabajar a la vez. Las claves van por pertenencia |
| 3 | Proyectos cerrados por defecto: cada trabajador ve lo que no tiene proyecto, los proyectos visibles para toda la empresa y aquellos a los que está asignado. Se asigna en el alta, desde la persona («Accesos») y desde el proyecto |
| 4 | Segunda barrera en la base de datos, con seguridad por filas por proyecto, también en los miembros de proyecto. Los filtros SQL se prueban aparte, con una conexión que se salta esa barrera |
| 5 | Nombre y apellidos, y un código de persona aleatorio de 4 caracteres. En cada empresa se muestra con el código de la empresa: `7F3K-E2D` |
| 6 | Panel con el sistema de la dirección visual: personas con código, papeles y accesos; claves con «Revocar clave» y el texto de qué acceso dan; selector de empresa; proyectos con «Quién lo ve» |
| 7 | Prueba de principio a fin contra la API real, con 8 pasos de administración y el paso del MCP con un proyecto cerrado |

Las cuatro observaciones del propietario del 2026-11-11 (B-001 a B-004) están hechas.

## 3. Verificado

- **`make check`:** exit 0. Incluye la API completa (unitarias, integración con PostgreSQL real, ArchUnit), el panel (198 pruebas) y el MCP (16).
- **`make demo`:** exit 0. Son 34 pruebas de extremo a extremo con axe y los pasos 1 a 9 del MCP. En el paso del proyecto cerrado, el trabajador asignado encuentra el documento y su cita se verifica; el no asignado recibe «No consta en los documentos de tu empresa.».
- **Revisiones:**
  - una por tarea, con Opus en las que tocan aislamiento;
  - una final de toda la rama, con Opus;
  - una re-revisión de sus arreglos, también con Opus.

## 4. Fallos que encontraron las revisiones y ya están corregidos

Dejo constancia porque varios no los habría visto una prueba por tarea:

| Fallo | Quién lo vio | Gravedad |
|---|---|---|
| Ascender a un trabajador a administrador fallaba siempre. La prueba simulaba un éxito | Revisión de la tarea 6 | Crítico |
| La migración ocultaba a todos los documentos de proyectos borrados antes de actualizar, y dejaba sin proyectos a quien se reactivara | Revisión final | Crítico |
| Un administrador de empresa podía cambiar el nombre y el correo de entrada del administrador del grupo | Re-revisión final, comprobado ejecutándolo | Importante |
| El dato «tiene contraseña» revelaba a un administrador de empresa que la persona administra otra | Revisión final | Importante |
| El panel había perdido «Borrar» persona en la tarea 2 | Prueba de principio a fin | Importante |
| Recuentos de miembros que contaban personas borradas o se quedaban viejos, y el aviso de cerrar un proyecto basado en ellos | Revisión de la tarea 6 y prueba de principio a fin | Importante |
| Quitar a alguien de un proyecto lo borraba de verdad, contra la regla de borrados lógicos | Revisión final | Importante |

Además, por la revisión final, el administrador del grupo puede trabajar con su IA (D-038). Sobre su persona solo actúa él mismo.

## 5. Decisiones del CTO en esta fase

Con motivo y coste en el registro técnico (T-017) y en el registro de la fase. Las principales:

- Al migrar, nadie pierde acceso: los trabajadores quedan como miembros de todos los proyectos de su empresa, también de los borrados.
- Borrar un proyecto no oculta sus documentos a quien podía verlos.
- «Quién lo ve» sale de un recuento en la API, no de una petición por proyecto.
- Las tablas se ven como bloques por debajo de 720 px.
- Los botones de fila llevan texto corto, y el nombre solo en el nombre accesible.

## 6. Lo que no está verificado o queda abierto

- **La instalación del propietario sigue en la dirección visual (10b7fed, base V7).** Las migraciones V8 a V10 se han probado contra una base de la fase 1.1 con datos (`MigrationFromPhaseOneIT`), pero no sobre su base real. Antes de actualizarla, copia de seguridad.
- **Códigos de empresa generados al migrar:** son las iniciales del nombre y el panel no los marca. Conviene que el administrador del grupo los revise en «Empresas». Pendiente: B-006.
- **Relevo del administrador del grupo:** hoy se hace con el token de instalación. Los pasos están en el `README`, y revocar sus claves es obligatorio. Separar bien la autoría exige varios administradores del grupo (B-007), obligatorio antes del primer cliente real (D-051).
- **Desactivar a una persona en todas sus empresas a la vez:** B-005.
- **Detalles menores anotados en el registro de la fase:**
  - un administrador de empresa ve «Desactivar» en la fila de trabajador del administrador del grupo, y la API lo rechaza;
  - si dos altas a la vez generan el mismo código de persona, la segunda recibe un error en vez de reintentarlo;
  - el administrador del grupo creado al instalar empieza sin apellidos, hasta que los pone en «Mi cuenta».
- **Sin medir:** el coste del recuento de miembros con cientos de proyectos.
- **Sin comprobar:** cómo lee VoiceOver en Safari las tablas en el móvil.

## 7. Para la fase 2

El CTO 2 tiene planificada la fase 2 sobre la 2a (`feature/fase-2-conocimiento`, que sale de 10b7fed). Antes de ejecutarla tiene que traer f4bcb22. Lo que cambia para sus planes:

- `ProjectVisibility` y `WorkerAccess.Granted(visibility, content)`;
- los proyectos cerrados por defecto;
- `project_member` con borrado lógico;
- `MemberView` y `ProjectView` con campos nuevos;
- en la demo de casos parecidos, el trabajador tiene que ser miembro de las comunidades necesarias.
