# Odín · Revisión tras la prueba del propietario

- Fecha: 2026-11-10. Autor: CTO. Para: CEO y propietario. Encargo 11.
- Revisado: `feature/api-central` en bacbb41, la fase 1 cerrada el 2026-11-09.
- Diseño de las correcciones: `docs/superpowers/specs/2026-11-10-correcciones-prueba-propietario-design.md`. Plan: `docs/superpowers/plans/2026-11-10-fase-1-1-correcciones.md`.

## 1. Conclusión

La fase 1 no estaba lista para pasar a `develop`. El núcleo (búsqueda con cita, aislamiento entre empresas, MCP) funciona y sus pruebas siguen en verde: `make check` terminó con código de salida 0 el 2026-11-10 sobre bacbb41. Lo que falla es la administración desde el panel: el propietario no pudo dar de alta un trabajador que funcionara ni recuperar uno desactivado.

Se abre una fase 1.1 de correcciones, con 7 tareas, antes de pedir otra vez el paso a `develop`.

## 2. Fallos confirmados

Cada uno está comprobado en el código o en la base de datos de la instalación de prueba.

| # | Fallo | Gravedad | Quién lo vio |
|---|---|---|---|
| F1 | El panel pide contraseña al crear un trabajador y la API la descarta sin avisar. El trabajador no entra en el panel (D-033), pero nada lo dice | Alta | Propietario |
| F2 | El botón «Activar» falla siempre: la API solo admite desactivar. Un usuario desactivado no se recupera desde el panel | Alta | CTO, al revisar |
| F3 | La lista de usuarios no enseña el correo: dos usuarios con el mismo nombre parecen el mismo | Media | Propietario |
| F4 | El panel exige correo al trabajador, aunque la API no lo necesita. Como el correo es único, un administrador que también trabaja necesita dos correos | Media | CTO |
| F5 | No se puede cambiar ninguna contraseña, y el administrador del grupo puede desactivarse a sí mismo. Si se bloquea, hay que borrar la instalación | Alta | CTO |
| F6 | Si se cambian las claves de la base después del primer arranque, `make up` falla con una traza de Java. El `README` no lo explica | Media | Propietario |
| F7 | Faltan operaciones: editar y borrar usuarios, renombrar y borrar proyectos | Media | Propietario |
| F8 | La sesión del panel está acoplada a su almacenamiento en tres casos de uso. Cambiarla por JWT no sería un cambio pequeño | Baja | Propietario |
| F9 | Las pruebas del panel no ejercitan casi ninguna acción | Alta, es la causa de fondo | CTO |

**Lo que el propietario creyó ver y no era:** dos usuarios con el mismo correo. Tenían correos distintos y el mismo nombre (F3). El correo ya es único en toda la instalación, con un índice en la base de datos.

## 3. Por qué no se vio antes

- **El panel se probó contra un doble escrito a mano.** Tiene 8 pruebas y ninguna crea, activa ni desactiva un usuario. El doble no se comprueba contra la API real, así que el panel y la API podían no encajar (F2) con todo en verde.
- **La prueba de extremo a extremo solo recorre la demo:** entrar, abrir una empresa y aprobar una ficha.
- **Las dos revisiones finales fueron de código, no de uso.** Nadie recorrió el panel como lo haría un administrador.
- **El diseño no decía qué campos lleva el alta según el papel.** El formulario salió igual para todos (F1, F4).

**Cambio de método, desde ahora:** ninguna acción del panel se da por terminada sin un paso en una prueba de extremo a extremo contra la API real. Entra en la tarea 7 y queda como criterio de cierre.

## 4. Qué se corrige en la fase 1.1

| Tarea | Qué hace | Fallos | Modelo |
|---|---|---|---|
| 1 | Sesión del panel detrás de un puerto. Sigue siendo la de hoy; cambiar a JWT será una clase nueva | F8 | Sonnet |
| 2 | Usuarios: alta según el papel, editar, activar y desactivar, borrado lógico | F1, F2, F4, F7 | Sonnet |
| 3 | Contraseñas: cambiar la propia, restablecer la de un administrador de empresa y recuperar al administrador del grupo con el token de instalación | F5 | Sonnet |
| 4 | Proyectos: renombrar, borrado lógico y nombre único por empresa | F7 | Sonnet |
| 5 | Error de arranque claro y `README` de operación | F6 | Haiku |
| 6 | Panel: gestión completa de usuarios y proyectos, y «Mi cuenta» | F1 a F4, F7 | Sonnet |
| 7 | Prueba de extremo a extremo de la administración, dentro de `make demo` | F9 | Sonnet |

**Decisiones del propietario que recoge** (2026-11-10):
- los borrados son lógicos;
- el trabajador no entra en el panel;
- la sesión tiene que poder cambiarse por JWT sin rehacer el núcleo.

**Decisiones del CTO.** Cada una dice lo que cuesta si resulta errónea:

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| No se cambia a JWT ahora; solo se deja el puerto | En un panel local, la sesión en servidor se cierra al instante y no hay claves de firma que custodiar | Una clase nueva |
| Borrar un usuario libera su correo; desactivarlo, no | Borrar es «ya no está»; desactivar es «volverá» | Un índice |
| Borrar un proyecto no oculta sus documentos | El conocimiento es de la empresa (D-001) | Un filtro en la búsqueda |
| El borrado no se puede deshacer desde el panel | Nadie lo ha pedido; el dato sigue en la base | Una ruta más |
| No se cambia el papel de un usuario | Trabajador y administrador se identifican de forma distinta | Un caso de uso |
| Recuperación del administrador del grupo con el token de instalación | Quien tiene `deploy/.env` ya controla la base de datos | Quitar una ruta |

**No entra:** borrado de empresas y nombre único de empresa. Nadie lo ha pedido.

## 5. Preguntas para el CEO

Son de negocio y condicionan la fase 2. Ninguna bloquea la fase 1.1.

1. **Qué ve un trabajador dentro de su empresa.** Hoy ve todos los proyectos de su empresa. El propietario espera que los usuarios pertenezcan a proyectos.
   - Recomendación del CTO: pertenencia de usuario a proyecto dentro de la empresa. El trabajador ve los documentos de sus proyectos y los que no tienen proyecto. «Cambiar de proyecto» es un filtro en la conversación con su IA, sin pantalla.
   - Falta saber: un ejemplo real de algo que un trabajador del proyecto A no deba encontrar del proyecto B. Se lo pregunté al propietario y quedó sin respuesta.
2. **Una persona en varias empresas del grupo.** Hoy un usuario es de una sola empresa y el correo es único, así que esa persona necesita un usuario y un correo por empresa.
   - Recomendación del CTO: una sola identidad por persona, con una pertenencia por empresa. Cada petición actúa en una sola empresa, así que la separación por defecto de D-033 no cambia.
   - Toca D-014 y D-033: por eso no lo decido yo.
3. **Administrador que también trabaja.** El diseño le da dos usuarios (D-037). Con la respuesta a la pregunta 2 podría ser uno solo con dos papeles. ¿Se mantienen los dos usuarios?
4. **Marca del panel.** El propietario quiere el panel «con estilo E2D». En su equipo hay dos marcas:
   - E2D Projects: colores `#080a0b`, `#22c5b6` y `#f0f0f0`, con logo;
   - Evolve2Digital: la web `e2d-website_v2`.

   ¿Cuál vale? ¿El panel lleva la marca de E2D o una marca propia de Odín «por E2D»? El panel se instala en la oficina de cada cliente.

## 6. Qué viene después

1. **Fase 1.1:** las 7 tareas, en serie. Al terminar, `make check`, `make demo` y el recorrido del propietario a mano.
2. **Rediseño visual del panel**, cuando se responda la pregunta 4. Una aclaración: el panel es React, no Spring Boot. Spring Boot solo sirve la API.
3. **Fase 2**, según el informe de cierre de la fase 1. Los permisos por proyecto esperan a las preguntas 1 a 3. El lector OCR no espera a nada: su contrato ya está en el diseño.

## 7. Resultado de la fase 1.1

Pendiente: se rellena al terminar las tareas.
