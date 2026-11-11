# Odín · Observaciones del propietario en el panel

- Fecha: 2026-11-11. Autor: CTO. Para: propietario y CEO. Encargo 11, fase 2a.
- Origen: el propietario probó el panel de su instalación, en `feature/identidad-pertenencias` (10b7fed). Esa versión incluye las tareas 1 y 2 de la fase 2a y la dirección visual (D-039).
- Comprobado en el código de 10b7fed, en el diseño de la fase 2a (`specs/2026-11-10-identidad-pertenencias-design.md`) y en su plan. No lo he reproducido en el navegador.

## 1. Resumen

| # | Observación | Tipo | ¿Cubierto por la fase 2a? | Decide |
|---|---|---|---|---|
| O1 | Al añadir una persona no se le puede asignar ningún proyecto, y no se sabe a qué tiene acceso | Hueco del diseño | En parte | Propietario y CEO (P1). El resto, CTO |
| O2 | Una clave no se puede conectar a ningún proyecto. ¿Qué accesos da? | Hueco del panel | En parte | CTO |
| O3 | El botón dice «Revocar &lt;nombre de la clave&gt;» y debería decir «Revocar clave» | Fallo visual | No | CTO |
| O4 | Cada trabajador debe tener nombre, apellidos y un código de trabajador, que genera el sistema si no se escribe | Funcionalidad nueva | No | Propietario y CEO (P2 a P4). El resto, CTO |

## 2. Detalle

### O1. Proyectos de una persona

**Hoy (10b7fed):**
- No existen los proyectos restringidos: están en la tarea 3 de la fase 2a, en curso.
- Por eso un trabajador ve **todos** los proyectos de su empresa, no ninguno.

**Lo que dice el diseño (D-038, spec 5.4):**
- Los proyectos están abiertos por defecto, y el administrador puede restringir uno a sus miembros.
- Los miembros se gestionan desde el proyecto (tarea 5).
- Desde la persona no se ve ni se cambia a qué proyectos accede. Ese es el hueco.

**Propuesta, para la tarea 5:**
- En cada persona, un apartado «Accesos» con dos partes:
  - «Proyectos abiertos: todos (N)»;
  - la lista de proyectos restringidos de los que es miembro, con «Añadir» y «Quitar».
- En el alta, elegir de forma opcional los proyectos restringidos.
- Lo que se cambia en la persona y lo que se cambia en el proyecto son los mismos miembros, vistos desde los dos lados.

**Pregunta de negocio (P1):** el propietario entendía que una persona nueva no accede a ningún proyecto mientras no se le asigne. D-038 dice lo contrario.

| Opción | A favor | En contra |
|---|---|---|
| **Abiertos por defecto (D-038, hoy)** | Menos trabajo para el administrador: solo marca lo excepcional | Una persona nueva ve todos los proyectos abiertos desde el primer momento |
| **Cerrados por defecto** | Mínimo acceso: nadie ve un proyecto sin que se le asigne | Cada proyecto nuevo y cada persona nueva exigen asignar accesos. Los documentos sin proyecto los seguiría viendo toda la empresa, salvo que también se cambie eso |

Recomendación: mantener D-038 y hacer visible el acceso con el apartado «Accesos». Si se decide cambiar, conviene hacerlo ahora, mientras la tarea 3 está abierta. Después habría que invertir el cálculo de proyectos visibles y la seguridad por filas de la tarea 4, y migrar datos.

### O2. Accesos de una clave

**Diseño (D-038, spec 5.2):** nunca hay claves por proyecto. La clave actúa como la persona en la empresa de la clave, así que da acceso exactamente a lo que esa persona ve en esa empresa.

**Hueco:** el panel no lo dice, y parece que la clave no tiene permisos.

**Propuesta, para la tarea 5:**
- Junto a las claves de cada persona, un texto: «Cada clave da acceso a lo mismo que ve &lt;persona&gt; en esta empresa», con enlace a sus «Accesos» (O1).
- Los permisos se dan, se cambian y se quitan en la persona, no en la clave.
- Revocar una clave ya existe y no cambia.

### O3. «Revocar &lt;nombre&gt;» en el botón de la clave

**Causa:** `panel/src/components/Keys.tsx:28` mete el nombre de la clave en el texto visible para que cada botón tenga un nombre accesible distinto.

**Arreglo:**
- Texto visible: «Revocar clave».
- Nombre accesible: «Revocar clave &lt;nombre&gt;», que empieza por el texto visible, como pide WCAG 2.5.3.
- Ajustar `panel/src/App.test.tsx:120`.

**Mismo patrón:** el plan de la tarea 5 prevé «Quitar &lt;nombre&gt;» en los miembros de un proyecto. Allí se aplica la misma regla: texto corto («Quitar») y el nombre solo en el nombre accesible. La regla vale para todos los botones de fila.

### O4. Nombre, apellidos y código de trabajador

**Hoy:** la persona (`User`) solo tiene `displayName`, un único campo de nombre. No hay código de trabajador.

**Propuesta:**
- Nombre (`firstName`) y apellidos (`lastName`), los dos obligatorios en los formularios.
- Un código de trabajador (`employeeCode`, término que se añadirá al lenguaje ubicuo). Se puede escribir a mano. Si se deja vacío, lo genera el sistema.
- El código identifica a la persona en la lista, el alta y la edición. No sale en el MCP ni en las citas.

**Preguntas de negocio:**
- **P2. ¿De quién es el código?** Puede ser de la persona en todo el grupo, o de su pertenencia a cada empresa. Recomendación: de la pertenencia, único dentro de la empresa, porque cada empresa suele numerar a su plantilla.
- **P3. ¿Qué formato tiene el código generado?** Recomendación: un número correlativo por empresa con un prefijo fijo, por ejemplo `T-0001`. Se puede editar después y nunca se reutiliza.
- **P4. ¿Lo llevan también quienes solo administran?** Recomendación: sí. Lo lleva toda pertenencia, tenga el papel que tenga.

**Datos que ya existen:** el nombre actual pasa a «nombre» y los apellidos quedan vacíos. Partir el nombre por el primer espacio fallaría con nombres compuestos. El panel marca a esas personas como «faltan apellidos» hasta que se completen. A cada pertenencia que ya exista se le genera un código.

## 3. Encaje en la fase 2a

| Tarea | Cambio |
|---|---|
| 3 (en curso) | Ninguno, salvo que P1 cambie el valor por defecto |
| Nueva, entre la 4 y la 5 | O4: modelo, migración, contrato y casos de uso de nombre, apellidos y código. Necesita P2 a P4 |
| 5 (panel) | O1 «Accesos» por persona y proyectos en el alta; O2 texto en las claves; O3 regla de los botones de fila. Además, ya estaba anotado usar `ConfirmDialog` y los componentes de D-039 en lugar de `window.confirm` y `<select>` |
| 6 (extremo a extremo) | Un paso que asigna un proyecto restringido desde la persona y lo comprueba desde el proyecto, y al revés |

El spec y el plan de la fase 2a se actualizan con estos cambios cuando haya respuesta a P1 a P4.
