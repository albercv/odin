# Encargo 20 · Hojas de cálculo y nombre de los proyectos de cada empresa

Fecha: 2026-11-15. Rama: `feature/hojas-y-nombres` (sale de `integracion/instalacion` 7791802, V26).

## 1. Hojas de cálculo como documentos

**Qué:** los `.xlsx`, `.xls` y `.ods` entran como documentos: texto buscable, legible con `readPage` y citable con `verifyCitation`. Sin IA, en local.

**Decisiones:**

- **Unidad de cita: la hoja es la página.** Página N = hoja N en el orden del libro, ocultas incluidas (son datos). Una hoja sin celdas con texto no genera página.
- **Hojas largas se parten.** La búsqueda guarda un `tsvector` por página (límite de 1 MB en PostgreSQL) y `readPage` devuelve la página entera a la IA. Por eso una página tiene como máximo **100 filas o 10.000 caracteres**. Una hoja más larga ocupa páginas consecutivas.
- **Texto de cada página:**
  - Primera línea: `Hoja «Nombre»`. En una hoja partida, `Hoja «Nombre» (filas 101–200)`, con los números de fila de la hoja de cálculo.
  - En las páginas de continuación, la segunda línea repite la primera fila con datos de la hoja (el encabezado), para que se sepa qué es cada columna.
  - Después, una línea por fila no vacía, con las celdas separadas por tabulador. Se quitan las celdas vacías del final de la fila y se mantienen las intermedias.
  - `QuoteMatcher` ya junta los espacios en blanco, así que una cita copiada con espacios en vez de tabuladores se comprueba bien.
- **Valor de cada celda:**
  - Se toma el que se ve en la hoja: el formato numérico y de fecha de la celda (`DataFormatter` con locale `es-ES`) y el resultado guardado de las fórmulas, nunca la fórmula.
  - En ODS se toma el texto mostrado (`text:p`).
- **Lectura en streaming donde importa:**
  - `.xlsx` con la API de eventos de POI (`XSSFReader` + `XSSFSheetXMLHandler`): un xlsx de 50 MB con el modelo DOM agota la memoria de la API.
  - `.xls` con HSSF: el formato limita a 65.536 filas por hoja y los ficheros son pequeños.
  - `.ods` con StAX sobre `content.xml`. Los atributos `number-columns-repeated` y `number-rows-repeated` se expanden solo si la celda o la fila tiene texto.
- **Tope de seguridad:** más de 500.000 filas con datos en todo el libro hace fallar el documento con el motivo «Hoja de cálculo demasiado grande». Nunca se trunca en silencio.
- **Formato:**
  - Nuevas constantes `DocumentFormat.XLSX`, `XLS` y `ODS`. Las detecta `MagicBytesFormatDetector` por contenido:
    - ZIP con `xl/workbook.xml` → XLSX;
    - OLE2 con flujo `Workbook` o `Book` → XLS (un `.doc` OLE2 sigue sin admitirse);
    - ZIP cuyo `mimetype` es `application/vnd.oasis.opendocument.spreadsheet` → ODS.
  - `paginated = true`.
  - `readerName = "odin-spreadsheet"`, versión `1`.
  - `OriginalResponse` sirve cada uno con su MIME, como adjunto.
- **Lector:** un solo `SpreadsheetTextReader` (admite los tres formatos). Arquitectura:
  - Tres fuentes, una por formato, que emiten filas a una interfaz común (hoja → filas numeradas con sus celdas).
  - Un paginador puro que convierte esas filas en textos de página. Así el paginador se prueba sin ficheros, y las fuentes con ficheros construidos en la prueba (como `PoiDocxTextReaderTest`).
- **Guiones de despliegue:** `anadir_proyecto.py` añade las tres extensiones a `TIPOS`.

## 2. Nombre de los proyectos de cada empresa

**Qué:** el administrador elige cómo llama su empresa a los proyectos («comunidades», «clientes», «obras»). El panel y el conector usan ese nombre. Sin elegir, «proyectos».

**Decisiones:**

- **Dominio:**
  - `ProjectLabel(String singular, String plural, boolean feminine)`, con `ProjectLabel.DEFAULT = ("proyecto", "proyectos", false)`.
  - El género hace falta para concordar el texto: «Nueva comunidad», «Sin comunidades asignadas».
  - Validación: cada palabra de 2 a 30 caracteres, solo letras, espacios y guion.
  - Normalización: se quitan los espacios sobrantes y la primera letra pasa a minúscula salvo que la palabra vaya entera en mayúsculas (siglas). El panel y el conector ponen la mayúscula donde toca.
- **Datos:**
  - V28 añade a `company` las columnas `project_label_singular`, `project_label_plural` y `project_label_feminine`, que admiten nulo. Una restricción exige las tres o ninguna; nulo significa «proyectos».
  - `Company` gana el campo, con un constructor secundario que pone el valor por defecto para no tocar las llamadas existentes.
- **API:**
  - `PATCH /v1/admin/companies/{id}` acepta `projectLabel` opcional (si no se envía, no cambia). Lo pueden cambiar quienes ya cambian el nombre y el código (administrador del grupo).
  - Devuelven `projectLabel` siempre relleno: `CompanyView`, `MeCompany` (`/v1/me`, lo usa el conector), `AdminMe` si lista empresas, y la información del enlace de subida (la página «Subir documentos» del trabajador).
- **Panel:**
  - Una función pura `projectTerms(label)` da las formas: «comunidad», «comunidades», «Comunidad», «Comunidades», «una comunidad», «Nueva comunidad», «Sin comunidad», y una concordancia `agree('asignados', 'asignadas')`.
  - Un contexto de React da los términos de la empresa seleccionada.
  - Todas las cadenas visibles con «proyecto» pasan a usarlo: Empresa, Primera carga, miembros, subida, detalle de documento y de ficha, y la subida por enlace.
  - En la página Empresa, el administrador elige el nombre (singular, plural y «el/la») en el formulario de la empresa.
- **Conector (MCP):**
  - Las instrucciones y las descripciones de las herramientas se construyen al abrir la sesión con la etiqueta de `/v1/me`.
  - Si no es la de por defecto, las instrucciones dicen: «En esta empresa, los proyectos se llaman «comunidades»: usa esa palabra con el trabajador.»
  - Los nombres de herramienta y de parámetro (`listProjects`, `projectId`) no cambian.
  - «Sin proyecto (general de la empresa)» y «Proyecto no disponible» usan el término.
  - Se añade a las instrucciones: «En una hoja de cálculo, cada página es una hoja; su primera línea dice cuál.»
- **Aislamiento (pruebas obligatorias):**
  - el trabajador de B no ve la etiqueta de A en `/v1/me`;
  - un administrador que no lo es del grupo de A no puede cambiarla;
  - una hoja de cálculo de A no se lee ni se verifica desde B.

## Fuera de alcance

- CSV como hoja de cálculo: hoy entra como texto.
- Los mensajes de error de la API que dicen «proyecto».
- Cambiar los identificadores del código.
