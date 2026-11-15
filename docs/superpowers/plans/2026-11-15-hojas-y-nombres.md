# Hojas de cálculo y nombre de los proyectos — plan de implementación

> Ejecución con subagentes, sin rondas de revisión (instrucción del propietario del 2026-11-15): cada tarea se verifica con sus pruebas y la feature con `make check`.

**Goal:** que `.xlsx`, `.xls` y `.ods` entren como documentos citables por hoja, y que cada empresa elija el nombre de sus proyectos.

**Spec:** `docs/superpowers/specs/2026-11-15-hojas-y-nombres-design.md`

**Tech Stack:** Java 21, Spring Boot, Apache POI 6.0.0, PostgreSQL 17 con Flyway, MCP en TypeScript, panel en React.

## Global Constraints

- Ramas: cada tarea en `task/hojas-y-nombres/<tarea>`, que sale de `feature/hojas-y-nombres`. Merge `--no-ff` a la feature.
- Sin push.
- Commits en español, sin atribución a Claude ni `Co-Authored-By`.
- TDD. Métodos de 30 líneas como máximo. Identificadores en inglés y camelCase. Comentarios mínimos.
- Migración: solo **V28**. V27 y V29 son de otras ramas.
- Datos de prueba ficticios. Nada en `private/`. Sin IA.
- No levantar pilas en los puertos 8080, 8088 ni 8090. No tocar la instalación del propietario ni `deploy/.env`.
- Tras cambiar la API: `OpenApiContractIT -Dodin.updateOpenApi=true` y `npm run generate:types` en `panel/` y en `mcp/`.

## Review Focus

1. Un xlsx con miles de filas: no agota la memoria y produce varias páginas de 100 filas o menos, todas con su `Hoja «X» (filas a–b)`.
2. Una celda con fecha o con fórmula: sale el valor mostrado, no el número de serie ni la fórmula.
3. Una cita copiada de una fila, con espacios en lugar de tabuladores: `verifyCitation` devuelve true.
4. Un `.doc` OLE2 sigue dando 415 y no se toma por `.xls`.
5. Una empresa sin etiqueta: el panel y el conector dicen exactamente lo de hoy, «proyecto(s)».

---

### Task 1: lector de xlsx y xls

Rama: `task/hojas-y-nombres/lector-hojas`.

**Files:**
- `odin-domain/.../document/DocumentFormat.java`: añade `XLSX`, `XLS` y `ODS`.
- `odin-adapter-readers`:
  - `MagicBytesFormatDetector`;
  - nuevos `SpreadsheetTextReader`, `SheetRowSink` (interfaz `startSheet(String name)`, `row(int rowNumber, List<String> cells)`), `SpreadsheetPager`, `XlsxSheets` y `XlsSheets`;
  - el pom declara `org.apache.poi:poi` explícitamente.
- `odin-bootstrap/.../AdapterConfiguration.java`: el bean `spreadsheetReader`.
- `odin-adapter-web/.../link/OriginalResponse.java`: los MIME de las hojas.
- `deploy/instalacion/anadir_proyecto.py`: las extensiones en `TIPOS`, con su prueba.
- Si alguna prueba usa `.xlsx` como ejemplo de formato no admitido, cambia el ejemplo a `.pptx`.

**Interfaces:**
- `SpreadsheetPager` implementa `SheetRowSink` y añade `List<String> pages()`. Constantes `MAX_ROWS = 100` y `MAX_CHARS = 10_000`. Si el libro supera `MAX_TOTAL_ROWS = 500_000`, lanza la excepción que el pipeline ya convierte en documento fallido, con el mensaje «Hoja de cálculo demasiado grande».
- `SpreadsheetTextReader.read` devuelve `ReadResult(pages, true, "odin-spreadsheet", "1")`.

**Tests:**
- `SpreadsheetPagerTest`:
  - una hoja de 3 filas da una página `Hoja «Cuotas»\n…` con las celdas separadas por tabulador;
  - las celdas vacías del final se quitan y las intermedias se mantienen;
  - las filas vacías no aparecen;
  - 250 filas dan 3 páginas, la segunda con `Hoja «Cuotas» (filas 101–200)` y el encabezado repetido en su segunda línea;
  - una página nunca pasa de 10.000 caracteres;
  - 2 hojas dan páginas en orden;
  - una hoja sin datos no da página;
  - pasar de 500.000 filas lanza la excepción.
- `SpreadsheetTextReaderTest`, con libros construidos en la prueba (`XSSFWorkbook` y `HSSFWorkbook`):
  - una fecha con formato sale como fecha;
  - una fórmula `=B2*2` sale con su resultado guardado;
  - un número con formato `#,##0.00`;
  - la hoja oculta se incluye.
- `MagicBytesFormatDetectorTest`:
  - xlsx → XLSX;
  - xls → XLS;
  - un `.doc` OLE2 (POIFS sin `Workbook`) → no admitido;
  - docx → DOCX (sin regresión).
- IT de punta a punta, con el estilo de los IT existentes de subida y cita:
  - el administrador de A sube un xlsx de 2 hojas, que se procesa;
  - la búsqueda encuentra el texto de una celda de la hoja 2 en la página 2;
  - `verifyCitation(doc, 2, "<fila con espacios>")` da true; con la página 1 da false;
  - el trabajador de la empresa B no puede leer ni verificar: 404 o false, como con un PDF.

- [ ] Las pruebas fallan → implementación → las pruebas del módulo y el IT pasan → OpenAPI y tipos regenerados → commit.

### Task 2: lector de ods

Rama: `task/hojas-y-nombres/lector-ods`, que sale de la feature con la Task 1 ya fusionada.

**Files:** `MagicBytesFormatDetector` (ZIP con `mimetype` de hoja ODS), nuevo `OdsSheets` (StAX sobre `content.xml`, sin dependencias nuevas) y `SpreadsheetTextReader`, que pasa a admitir ODS.

**Tests:**
- `OdsSheetsTest`: un ODS mínimo construido en la prueba (ZIP con `mimetype` y `content.xml`):
  - 2 hojas;
  - una celda con `number-columns-repeated="3"` y texto da 3 celdas;
  - `number-columns-repeated="16384"` vacío no genera celdas;
  - `number-rows-repeated="1000"` vacío no genera filas;
  - una celda con dos `text:p` une los párrafos con un espacio.
- Detector: ODS → ODS, y un ODT (texto) → no admitido.

- [ ] Las pruebas fallan → implementación → pasan → commit.

### Task 3: nombre de los proyectos en la API

Rama: `task/hojas-y-nombres/nombre-proyectos-api`.

**Files:**
- `odin-domain/.../organization/ProjectLabel.java` (nuevo) y `Company.java` (campo `projectLabel`, con un constructor secundario de 5 argumentos que pone `DEFAULT`).
- `CompanyChanges` (`Optional<ProjectLabel>`) y `UpdateCompany`.
- `PostgresOrganizationRepository` (guardar y leer; nulo → `DEFAULT`).
- `V28__company_project_label.sql`.
- `Requests.UpdateCompany`.
- `Responses`: `CompanyView`, `MeCompany` y la vista de información del enlace de subida (el DTO que lee `panel/src/pages/UploadByLink.tsx`).

**Interfaces:**
- JSON `projectLabel: {singular: string, plural: string, feminine: boolean}`. Siempre presente en las respuestas; opcional en el PATCH.

**Tests:**
- `ProjectLabelTest`:
  - `"  Comunidades "` → `"comunidades"`;
  - `"CCPP"` se queda igual;
  - 1 carácter, 31 caracteres o dígitos → `ValidationException`.
- `UpdateCompanyTest`: cambia la etiqueta, y sin `projectLabel` no la toca.
- IT del repositorio:
  - guarda la etiqueta y la lee;
  - una empresa sin etiqueta → `DEFAULT`;
  - la restricción de V28 rechaza 2 de las 3 columnas.
- IT web y de aislamiento:
  - el PATCH del administrador del grupo cambia `/v1/me` del trabajador de A;
  - el trabajador de B sigue con `DEFAULT`;
  - un administrador de empresa que no es del grupo recibe 403 o 404, como hoy en el PATCH;
  - la información del enlace de subida trae la etiqueta.

- [ ] Las pruebas fallan → implementación → pasan → OpenAPI y `types.ts` (panel y mcp) regenerados → commit.

### Task 4: nombre de los proyectos en el panel

Rama: `task/hojas-y-nombres/nombre-proyectos-panel`, que sale de la feature con la Task 3 ya fusionada.

**Files:**
- `panel/src/components/projectTerms.ts` (nuevo, función pura).
- `ProjectTermsContext` y su proveedor en `AppShell`, para la empresa seleccionada. Se refresca cuando la página Empresa guarda.
- El formulario de la empresa en `pages/Company.tsx` o su componente: los campos singular, plural y el/la.
- Todos los ficheros de `panel/src` con «proyecto» visible: `Projects`, `Intake*`, `MemberAccesses`, `MemberForm`, `Members`, `ProjectMembers`, `UploadDialog`, `DocumentDetail`, `CardDetail`, `widelyVisible`, `AddToCaseDialog`, `personConfirmations`, `UploadByLink` y `reviewNames`.

**Interfaces:** `projectTerms(label?: ProjectLabel)` devuelve `{ one, many, One, Many, aOne, newOne, noneOne, agree(masc, fem) }`.

**Tests:**
- `projectTerms.test.ts`:
  - `undefined` → «proyecto», «Nuevo proyecto», «Sin proyecto»;
  - `{comunidad, comunidades, true}` → «Nueva comunidad», «una comunidad» y `agree('asignados', 'asignadas') === 'asignadas'`;
  - `"CCPP"` en mayúsculas se mantiene.
- Las pruebas existentes de los componentes siguen pasando con el valor por defecto (texto idéntico al de hoy).
- Pruebas nuevas:
  - `Projects` con «Comunidades» muestra el título «Comunidades» y el botón «Nueva comunidad»;
  - el formulario de la empresa envía `projectLabel`;
  - `UploadByLink` muestra el término del enlace.
- Las pruebas de accesibilidad existentes siguen en verde.

- [ ] Las pruebas fallan → implementación → `npm test` y `npm run build` del panel → commit.

### Task 5: nombre de los proyectos en el conector

Rama: `task/hojas-y-nombres/nombre-proyectos-mcp`, que sale de la feature con la Task 3 ya fusionada.

**Files:**
- `mcp/src/server.ts`: `instructions(me)` sustituye a la constante `INSTRUCTIONS`.
- `mcp/src/projects.ts`: «Sin…» y «… no disponible» con el término.
- `mcp/src/tools/*.ts`: las descripciones que dicen «proyecto» reciben el término al registrarse.

**Tests:**
- Sin etiqueta: las instrucciones y las descripciones son idénticas a las de hoy, más la frase de las hojas.
- Con `{comunidad, comunidades, true}`: las instrucciones contienen «los proyectos se llaman «comunidades»», `listProjects` describe «comunidades», y `NO_PROJECT` es «Sin comunidad (general de la empresa)».
- Las instrucciones contienen «En una hoja de cálculo, cada página es una hoja».
- `make demo` no se exige. Sí pasa `npm test` del mcp.

- [ ] Las pruebas fallan → implementación → pasan → commit.

### Cierre

Merge de cada tarea a la feature, OpenAPI y tipos regenerados si chocan, y `make check` en la feature.
