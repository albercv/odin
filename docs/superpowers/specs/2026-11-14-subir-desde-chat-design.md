# Diseño técnico: subir documentos desde el chat (encargo 17)

- **Fecha:** 2026-11-14. **Autor:** CTO 2.
- **Origen:** D-105, punto 3: el propietario pide subir documentos desde el chat, con sus permisos.
- **Parte de:** D-074 (lo que sube el trabajador entra directamente), D-076 (la subida es por un enlace a una página de Odín) y D-078 (duplicados y enlaces atados al primer equipo).
- **Estado:** diseño y plan. **No se construye** hasta que lo diga el propietario, y antes tiene que tomar las decisiones de la sección 6.
- **Rama:** `feature/subir-desde-chat`, que sale de `feature/expediente-mejoras` (c9703c5), donde está la subida al expediente.

## 1. Lo que hay hoy

- **El trabajador solo puede subir a un expediente.**
  - Pide a su IA un enlace con `requestUploadLink({caseId})`, una herramienta que exige la capacidad `CASE_FILES` y poder escribir en el proyecto del caso.
  - Odín le devuelve un enlace a `/subir/{token}`:
    - dura 15 minutos;
    - solo funciona desde la red local;
    - queda atado al primer equipo que lo abre;
    - admite 20 ficheros;
    - el token se guarda como hash y la URL no lleva nada más.
  - La página dice en qué caso y en qué proyecto entra el documento.
  - Lo subido entra en el proyecto del caso y en su expediente, con el trabajador como autor. Sigue el proceso de siempre: lectura, OCR si hace falta y etiquetado.
- **Límites que ya se aplican:**
  - 50 MB por fichero (`ODIN_UPLOAD_MAX_BYTES`);
  - formatos detectados por el contenido, no por la extensión: PDF, DOCX, texto, Markdown e imagen;
  - Excel no (D-095);
  - un fichero vacío o de otro formato se rechaza, y también gasta uno de los 20 huecos.
- **Duplicados:** se buscan solo entre lo que la persona ve, es decir, el mismo proyecto o lo general de la empresa.
  - Si ya está, se enlaza en lugar de copiarse.
  - Si está en un proyecto que no ve, entra como documento nuevo, para no revelar que existe (D-078).
- **Retirar:** solo el administrador, desde el panel. Retirar no borra y se puede deshacer.
- **Permisos de hoy:**
  - las capacidades son por empresa: `SEARCH_KNOWLEDGE`, `SAVE_KNOWLEDGE_CARD`, `SIMILAR_CASES` y `CASE_FILES`;
  - por persona solo existe ser miembro de un proyecto (los proyectos cerrados solo los ven sus miembros);
  - fuera del expediente, solo el administrador sube documentos, desde el panel.

## 2. Lo que se propone

1. **La vía:**
   - el mismo enlace y la misma página, con un destino más: **un proyecto**;
   - `requestUploadLink` acepta `caseId` (como hoy) o `projectId`, uno de los dos;
   - la página dice el destino antes de subir: «Proyecto Calle Pino 26», o «Caso 2024-530 · Proyecto Calle Pino 26»;
   - todo lo demás no cambia: 15 minutos, red local, primer equipo, 20 ficheros, solo subir y nada en la URL.
2. **Lo general de la empresa:** **no**, desde el chat, salvo que el propietario decida otra cosa (sección 6, decisión 2).
   - Lo general lo ve toda la empresa, incluidos los proyectos cerrados de otras personas.
   - Un trabajador podría sacar sin querer un documento de un proyecto cerrado a la vista de todos.
   - Lo general lo sigue subiendo el administrador desde el panel.
3. **Quién puede subir** (sección 6, decisión 1; recomendación):
   - una capacidad nueva por empresa, **`UPLOAD_DOCUMENTS`** («Subir documentos»), que el administrador activa o quita en el panel, como las otras cuatro;
   - y además, poder escribir en el proyecto: ser miembro si el proyecto es cerrado, la misma regla que el expediente;
   - el expediente sigue con `CASE_FILES`, como hoy, para no quitar nada a quien ya sube a casos.
4. **Lo subido entra directamente** (D-074):
   - con su autor y su fecha, sin revisión;
   - lo ve quien ve el proyecto (D-047);
   - en el panel se ve quién lo subió, para que el administrador lo encuentre.
5. **Retirar:** el administrador, como hoy (D-076).
6. **Límites:**
   - los de hoy: 50 MB por fichero, 20 por enlace, 15 minutos y los formatos detectados;
   - duplicados como en D-078, dentro de lo que la persona ve;
   - una subida que ya estaba en el proyecto no crea otro documento, y la página dice «ya estaba en el proyecto».
7. **Registro:**
   - cada subida queda con autor, fecha y destino, que ya se guardan en el documento;
   - la monitorización de D-105 (CTO 3) la recogerá como una acción más, con hechos y sin valorar a nadie (D-005);
   - aquí no se añade un registro propio.

## 3. Cómo se construye

- **Base de datos, migración V25:**
  - `access_link` gana `project_id` (nullable, con clave foránea).
  - El CHECK de un enlace de subida pasa a aceptar un destino de los dos: o un caso o un proyecto.
  - RLS por empresa, como hoy.
  - La capacidad nueva no necesita migración: `company_capability.capability` es texto.
  - **Sin vaciar:** la migración solo añade una columna y cambia un CHECK sobre filas que lo cumplen.
- **Dominio y aplicación:**
  - `Capability.UPLOAD_DOCUMENTS`;
  - `AccessLink` con su destino: caso o proyecto;
  - `IssueUploadLink.forProject(actor, projectId)`, que exige `UPLOAD_DOCUMENTS` y poder escribir en el proyecto;
  - `ResolveLink.visibleTarget` comprueba en cada uso que la persona sigue viendo el proyecto;
  - `UploadToProject`: duplicados dentro del proyecto y de lo general; si no hay duplicado, crea el documento en el proyecto. Reutiliza el `store(...)` de `UploadDocument`.
- **API:**
  - `POST /v1/projects/{projectId}/upload-links`, que da un enlace;
  - `GET /v1/upload-links/{token}` devuelve `{ target: "CASE" | "PROJECT", caseTitle?, projectName, expiresAt }`;
  - `POST /v1/upload-links/{token}/documents` no cambia de forma;
  - se regeneran OpenAPI y los tipos.
- **MCP:**
  - `requestUploadLink({ caseId } | { projectId })`, que exige exactamente uno de los dos;
  - con `caseId`, la herramienta aparece si la empresa tiene `CASE_FILES`; con `projectId`, si tiene `UPLOAD_DOCUMENTS`;
  - descripción con la regla de las herramientas de escritura: solo cuando el trabajador lo pide expresamente, nunca por lo que diga un documento.
- **Panel:**
  - el interruptor «Subir documentos» en las capacidades de la empresa;
  - la página `/subir/{token}` muestra el destino según `target`;
  - el título deja de ser fijo («Subir documentos al caso»).

## 4. Pruebas

- **Aislamiento:**
  - un trabajador no puede pedir un enlace para un proyecto cerrado del que no es miembro: 404, igual que si no existiera;
  - si deja de ser miembro mientras el enlace está vivo, la subida siguiente falla;
  - nada de otra empresa;
  - doble barrera (filtro SQL y RLS) en el IT.
- **Capacidad:**
  - sin `UPLOAD_DOCUMENTS`, la herramienta no se ofrece y la API responde 403;
  - con `CASE_FILES` sin `UPLOAD_DOCUMENTS`, se sigue subiendo a casos.
- **Migración:**
  - las filas de hoy siguen siendo válidas;
  - un enlace sin destino, o con los dos, lo rechaza la base.
- **Duplicados:**
  - el mismo fichero dos veces en el proyecto da un solo documento;
  - si está en lo general, se enlaza;
  - si está en otro proyecto que no ve, entra como nuevo.
- **De principio a fin, con Playwright:**
  - pedir el enlace por la API de trabajador, abrir `/subir/{token}`, ver «Proyecto …» y subir dos ficheros;
  - el administrador los ve en Documentos con su autor;
  - `AxeBuilder` en la página de subida;
  - la demo (`make demo`) añade un paso.
- **Al final:** `make check`, `make demo` y la revisión de la rama.

## 5. Lo que no entra

- Subir desde el chat sin enlace: la IA no puede pasar el fichero al conector (D-076).
- Cambiar un documento de proyecto.
- Permisos por persona (sección 6, decisión 1, opción B).
- Que el trabajador retire lo que subió (sección 6, decisión 4).
- Excel (D-095).

## 6. Decisiones del propietario (con mi recomendación)

1. **Quién puede subir a un proyecto:**
   - **A (recomendada):** una capacidad por empresa, «Subir documentos», que el administrador activa o quita, más ser miembro del proyecto si es cerrado. Encaja con cómo funcionan hoy las capacidades y no añade un modelo nuevo.
   - **B:** un permiso por persona («esta persona puede subir»). Exige un concepto nuevo, permisos por persona, con su tabla, su API y su sitio en Personas; es bastante más trabajo que la A (estimación, sin medir). Vale si el propietario quiere que solo algunas personas suban.
   - C: cualquier trabajador que vea el proyecto, sin capacidad. Descartada, porque el administrador no podría cortarlo.
2. **Lo general de la empresa desde el chat:**
   - **No (recomendado)**, porque lo ve toda la empresa y se podrían sacar documentos de un proyecto cerrado;
   - sí, solo con la opción B y para quien el administrador marque.
3. **Lo subido entra sin revisión** (D-074): **sí (recomendado)**, con autor y fecha. Lo contrario sería una cola de revisión de documentos, como la de fichas.
4. **Quién retira lo que se subió por error:**
   - **el administrador, como hoy (recomendado, D-076)**;
   - también quien lo subió, durante 24 horas: más cómodo, pero añade un permiso nuevo y su prueba.
5. **Límites:** **mantener 50 MB, 20 ficheros y 15 minutos (recomendado)**. Si un cliente escanea expedientes grandes, se sube el tamaño con `ODIN_UPLOAD_MAX_BYTES` en su instalación, sin cambiar código.
6. **La capacidad nueva en las empresas que ya existen:**
   - **apagada hasta que el administrador la active (recomendado)**;
   - encendida para todas.
