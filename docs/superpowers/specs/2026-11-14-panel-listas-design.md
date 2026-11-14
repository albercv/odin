# Diseño técnico: listas del panel con buscador y páginas (encargo 15)

- **Fecha:** 2026-11-14. **Autor:** CTO 2.
- **Origen:** mejoras del panel que pidió el propietario el 2026-11-13. Están en `decisiones.md`, apartado «Pendiente», y las encarga el CEO como encargo 15.
- **Estado:** diseño y plan. **No se construye** hasta que lo diga el propietario.
- **Rama:** `feature/panel-listas`, que sale de `feature/expediente-mejoras` (5708afa) porque es el panel más reciente. Al construir, se rebasa sobre lo que esté integrado en ese momento.

## 1. Lo que pide el propietario

1. Un **selector de empresa en el menú**, siempre con una empresa seleccionada por defecto. Al pulsar una pestaña, por ejemplo Empresa, se cargan los datos de esa empresa.
2. **Pestaña Empresa:**
   - proyectos y personas paginados, con un máximo de 10 por página;
   - un buscador sin botón, que filtra mientras se escribe a partir de 3 letras y devuelve los resultados paginados.
3. **Personas:** igual que la pestaña Empresa.
4. **Documentos:** el mismo buscador, paginado, y se puede elegir cuántos se ven por página, hasta 50.
5. **Revisión** (D-105, 2026-11-14): la lista de lo que hay que revisar no puede ser un listado enorme. Lleva páginas y, si encaja, el mismo buscador desde 3 letras.

**Cómo leo el punto 3:** «Personas» es la sección con ese título dentro de la pestaña Empresa (`components/Members.tsx`). El panel no tiene una pestaña aparte de personas. Si el propietario quiere una pestaña nueva, es un cambio pequeño sobre las mismas piezas: el CEO lo confirma con él.

## 2. Cómo está hoy

- **Selector:** `components/CompanySwitcher.tsx` es un `<select>` nativo.
  - El administrador de grupo empieza sin empresa, con la opción «Elige una empresa» y el mensaje «Elige una empresa en el menú.».
  - La empresa elegida se guarda en el estado de `AppShell`. Cada pestaña monta su página y carga sus datos al montarse (`useLoad`), así que lo de «al pulsar una pestaña se cargan sus datos» ya funciona.
- **Proyectos y personas:** cada lista se pide entera a la API (`GET …/projects` y `GET …/members`). No hay búsqueda ni páginas, y la API no los pagina.
- **Documentos:**
  - el panel pide la lista en tandas de 500 hasta 2.000, con `offset` y `limit`, y avisa si la corta;
  - la API (`GET /v1/admin/companies/{id}/documents`) acepta `offset`, `limit` (de 100 a 500) y `withdrawn`;
  - no tiene búsqueda y no da el total.
- **Revisión:**
  - lista las fichas pendientes de la empresa (`GET /v1/admin/companies/{id}/cards?status=PENDING`), enteras y sin `LIMIT` en el SQL;
  - el panel las tiene todas en memoria, esconde las ya resueltas y saca de ahí el número del menú;
  - crece sin límite si nadie revisa: es la cola de lo que los trabajadores guardan con `saveKnowledgeCard`.
- **Piezas reutilizables:** el panel tiene `Table`, `Section`, `Field`, `Button` y `EmptyState`. No hay componente de páginas, ni de búsqueda, ni de espera al teclear.

## 3. Decisiones

### 3.1 ¿Hace falta búsqueda y paginación en el servidor?

- **Documentos: sí.**
  - La pila de prueba ya tiene 1.354 documentos, y un cliente real tendrá miles.
  - Hoy el panel corta a partir de 2.000. Filtrar en el navegador obligaría a traerlos todos, y no escala.
  - La API gana:
    - el parámetro `q`, que busca en el título sin distinguir mayúsculas ni acentos, con `unaccent`;
    - el total de resultados.
- **Revisión: sí.**
  - Es una cola que crece sin límite.
  - La API gana `q`, que busca en el título y el texto de la ficha sin distinguir acentos ni mayúsculas, junto con `offset`, `limit` y el total.
  - El número del menú sale del total, no de contar en el navegador.
- **Proyectos y personas: no, por ahora.**
  - Una empresa tiene decenas de proyectos (un administrador de fincas, quizá unos cientos de comunidades) y pocas decenas de personas.
  - La lista ya llega entera en una sola petición, sin cortes, así que filtrar y paginar en el navegador es inmediato y no toca la API.
  - **Límite:** si una empresa pasa de 500 proyectos o personas, se pasa al servidor con el mismo patrón que Documentos. Las piezas del panel (§3.3) ya separan la búsqueda de dónde se hace.

### 3.2 API de documentos

- `GET /v1/admin/companies/{companyId}/documents?q=&offset=&limit=&withdrawn=` responde `DocumentPage { documents: DocumentView[], total: int }`.
  - Hoy responde una lista; el cambio de forma afecta al panel y a los guiones de `deploy/instalacion` (adaptados), no al MCP, que usa la API del trabajador.
  - `total` es el número de documentos con los mismos filtros (empresa, retirados y `q`), no solo los de la página.
- **`q`:**
  - opcional;
  - se recortan los espacios, y vacío equivale a no haber búsqueda;
  - más de 100 caracteres dan 400;
  - se busca en el título con `unaccent(lower(...)) LIKE` y los comodines escapados, como en el canal literal de la búsqueda;
  - el mínimo de 3 letras lo pone el panel, no la API.
- **Fichas pendientes:** `GET /v1/admin/companies/{companyId}/cards?status=&q=&offset=&limit=` responde `CardPage { cards, total }`.
  - Mismas reglas de `q` que en los documentos, con búsqueda en `title` y `body`.
  - `limit` va de 10 a 50.
  - Orden: la más antigua primero, como hoy (`seq`), porque es una cola.
- **Doble barrera:** el filtro de empresa, de proyectos y de retirados sigue igual, con el filtro SQL y RLS. La búsqueda y el recuento van dentro del mismo filtro.
- **`limit`:** se mantiene de 100 a 500 por defecto, para otros usos. El panel pide 10, 25 o 50.

### 3.3 Piezas del panel

- **`ui/SearchBox`:**
  - `<input type="search">` dentro de `role="search"`, con `aria-label`, por ejemplo «Buscar proyectos»;
  - avisa del cambio 300 ms después de dejar de escribir;
  - con menos de 3 letras, sin contar los espacios, entrega la búsqueda vacía, es decir, la lista entera;
  - sin botón; Escape borra el texto;
  - debajo, un texto en `aria-live="polite"` con el número de resultados («12 resultados», «Ningún resultado para «abc»»).
- **`ui/Pagination`:**
  - `<nav aria-label="Páginas de …">` con «Anterior», «Página 2 de 7» y «Siguiente»;
  - los botones se desactivan en los extremos y no se muestra si hay una sola página.
- **`ui/PageSizeSelect`:** un `<select>` con 10, 25 y 50 y su etiqueta, «Por página». Solo en Documentos; empieza en 25.
- **`components/usePagedList(items, filter, pageSize)`:** filtra y pagina en el navegador (proyectos y personas). Vuelve a la página 1 cuando cambia la búsqueda. Si se borra el último elemento de la última página, se queda en la última página que exista.
- **`components/useServerPage(fetchPage, { q, pageSize, withdrawn })`:** pide una página a la API (Documentos). Vuelve a la página 1 cuando cambia `q`, `pageSize` o `withdrawn`, y descarta respuestas viejas si se teclea rápido.

### 3.4 Pantallas

- **Revisión:**
  - buscador «Buscar fichas» y 10 por página;
  - al aprobar o rechazar una ficha, se vuelve a pedir la página; si queda vacía y no es la primera, se pide la anterior;
  - el número del menú sale del `total`;
  - las fichas resueltas dejan de esconderse en el navegador, porque ya no vuelven del servidor.
- **Selector de empresa:**
  - el administrador de grupo empieza en la primera empresa de la lista, en el orden de `GET /v1/admin/companies`;
  - desaparece la opción vacía y el mensaje «Elige una empresa en el menú.»;
  - si el enlace de un caso trae `?empresa=`, manda esa;
  - la empresa elegida se mantiene al cambiar de pestaña;
  - la página Grupo sigue igual.
- **Empresa:**
  - Proyectos y Personas, cada una con su buscador y 10 por página;
  - los proyectos se buscan por nombre;
  - las personas, por nombre y por correo;
  - crear, editar o borrar sigue funcionando y recarga la lista sin perder la búsqueda.
- **Documentos:**
  - buscador por título, páginas y «Por página» (10, 25 o 50);
  - el interruptor «Retirados» se mantiene y vuelve a la página 1;
  - la selección de documentos vale para la página que se ve y se vacía al cambiar de página, de búsqueda o de tamaño. Así ninguna acción cae sobre documentos que no están a la vista;
  - el diálogo «Sustituir» deja de recibir la lista entera y busca los candidatos con la misma API (`q`), porque con páginas el panel ya no tiene todos los documentos;
  - el aviso de lista cortada desaparece, porque ya no se corta.

## 4. Pruebas

- **API:**
  - pruebas de unidad del caso de uso y del controlador (`q` vacío, recortado, de 101 caracteres, y el total);
  - IT del repositorio con datos de dos empresas: `q` nunca trae nada de la otra, el total cuenta solo lo que se ve, el acento y las mayúsculas dan igual, y `%` y `_` se buscan como texto;
  - se regeneran `openapi.json` y los dos `types.ts`.
- **Panel, con vitest y testing-library:**
  - las tres piezas, con la espera simulada: menos de 3 letras, Escape, páginas extremas y el anuncio de resultados;
  - las páginas Empresa y Documentos con `mockFetch`, incluidas las URL con `q`;
  - las pruebas del selector que esperaban la opción vacía se cambian.
- **De principio a fin, con Playwright: un fichero nuevo `panel/e2e/listas.spec.ts`.**
  - Prepara por la API 23 proyectos, 12 personas y 60 documentos en una empresa.
  - Comprueba:
    - el administrador de grupo entra con una empresa ya elegida;
    - escribir 2 letras no filtra y 3 sí;
    - las páginas;
    - «Por página» con 10, 25 y 50;
    - buscar en «Retirados»;
    - Revisión: 15 fichas pendientes en dos páginas; aprobar una actualiza el número del menú;
    - el diálogo «Sustituir» encuentra candidatos que no están en la página.
- **Accesibilidad:** `AxeBuilder`, sin violaciones graves ni críticas, en cada estado: con resultados, sin resultados y con la segunda página. Se manejan solo con el teclado el buscador, las páginas y el tamaño. Si cambian las capturas de `visual.spec.ts`, se revisan y se actualizan a propósito.
- **Al final:** `make check` y `make demo` terminando con 0, y la revisión de toda la rama.

## 5. Lo que no entra

- Buscar dentro del texto de los documentos: para eso está la búsqueda del trabajador.
- Ordenar por columnas.
- Guardar la búsqueda o el tamaño de página entre sesiones.
- Paginar en el servidor los proyectos y las personas (§3.1, con su límite).
- Una pestaña nueva de Personas (§1, a confirmar).
