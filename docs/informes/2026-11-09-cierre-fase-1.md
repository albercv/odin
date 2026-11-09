# Odín · Cierre de la fase 1 de la API central

- Fecha: 2026-11-09. Autor: CTO. Encargo 11 (D-033 a D-037).
- Rama: `feature/api-central`, con el merge 02b0a95 de las correcciones finales y la rama de este informe encima. No está en `develop`: el paso lo aprueba el propietario. Sin push.
- Diseño: `docs/superpowers/specs/2026-11-09-api-central-design.md`. Plan: `docs/superpowers/plans/2026-11-09-fase-1-api-central.md`.

## 1. Qué funciona, verificado ejecutando

Lo ejecutó el CTO el 2026-11-09 sobre 0ae0d62, el contenido que se fusiona con 02b0a95:

| Comprobación | Resultado |
|---|---|
| `make check`: pruebas Java (unitarias, de integración con PostgreSQL 17.10 real, de arquitectura con ArchUnit y de estilo con Checkstyle), MCP y panel | Código de salida 0. API: 251 pruebas según el informe de la corrección final. MCP: 16. Panel: 8 |
| `make demo`: demostración de cierre (spec 3.1) con Docker Compose, un PDF ficticio, un cliente MCP real y Playwright sobre el panel | Código de salida 0. Pasan los 9 pasos |
| Batería de búsqueda del estudio (fichas de 3 comunidades ficticias, 30 preguntas con cita) | 27 de 30 con la página esperada entre las 5 primeras (90,00 %), igual en 3 ejecuciones. Queda como umbral de regresión |

Pasos de `make demo` (spec 3.1):
1. Configuración inicial del grupo.
2. Sesión del administrador del grupo.
3. Empresas A y B, con un trabajador cada una y su clave personal.
4. PDF ficticio subido a A y procesado.
5. Desde el MCP, el trabajador de A recibe un resultado del PDF con su página. Además, `verifyCitation` confirma que el extracto es literal.
6. El trabajador de B, con la misma pregunta, recibe «No consta en los documentos de tu empresa.».
   - **Control positivo:** A lee esa página (200).
   - **Aislamiento:** B recibe un 404 en la misma página.
7. El trabajador de A guarda una ficha, que queda pendiente.
8. El administrador la aprueba desde el panel.
9. La API confirma que está aprobada.

**Aislamiento entre empresas**, regla no negociable (D-014) y exigencia del CEO:
- Todos los casos de uso con actor tienen una prueba con un actor de otra empresa: 24 con prueba y 5 sin actor (configuración inicial, entrada, salida, resolución de credenciales y proceso interno).
- Hay dos barreras:
  - **En el código:** los casos de uso y un filtro de empresa explícito en cada consulta SQL.
  - **En la base de datos:** la seguridad por filas de PostgreSQL.
- **Si la base de datos no aísla:** una prueba lee a través de una conexión que se salta la seguridad por filas y comprueba que no sale nada de la otra empresa.
- **Arranque con un usuario indebido:** la aplicación se niega a arrancar si se conecta con un usuario de base de datos que se salte esa seguridad.

## 2. Qué se ha construido

- **API (Java 21, Spring Boot 4.1.2):**
  - Arquitectura hexagonal en 8 módulos Maven. El dominio y los casos de uso son Java puro; Spring solo está en los adaptadores y en el arranque. Las capas se comprueban con ArchUnit.
  - Adaptadores:
    - PostgreSQL, con migraciones Flyway, seguridad por filas y búsqueda de texto completo en español;
    - originales en disco, guardados por su huella SHA-256;
    - lectores de PDF, DOCX y texto;
    - REST, con errores RFC 9457 y OpenAPI.
  - Contrato `api/openapi.json` versionado y comprobado por una prueba.
- **Servidor MCP (TypeScript):** stdio y HTTP local. Cada trabajador entra con su clave personal y solo ve las herramientas de las capacidades de su empresa.
  - Herramientas: `searchKnowledge`, `readPage`, `listDocuments`, `verifyCitation` y `saveKnowledgeCard`.
  - Las fichas se presentan como fichas («Ficha aportada por…; resumen hecho por IA»), nunca como documentos originales.
  - Los tipos se generan del contrato y `make check` falla si se desvían.
- **Panel del administrador (React):**
  - Pantallas: entrada, empresas y capacidades (solo el administrador del grupo), proyectos, usuarios, claves (visibles una sola vez), documentos y revisión de fichas.
  - No muestra preguntas por persona (D-036).
- **Despliegue:**
  - Docker Compose con PostgreSQL, API, panel con nginx y MCP por HTTP.
  - Todos los puertos solo en `127.0.0.1`. Contenedores sin root en la API y el MCP.
  - Configuración en `deploy/.env`, a partir de `deploy/.env.example`.
  - `README.md` con el arranque y la configuración del MCP.

## 3. Decisiones tomadas por el CTO durante la ejecución

Cada una dice lo que cuesta si resulta errónea.

| Decisión | Motivo | Coste si es errónea |
|---|---|---|
| Tareas en serie, no en paralelo | Evitar choques entre subagentes en el mismo repositorio | Solo tiempo |
| Todas las versiones en el `pom` padre: Spring Boot 4.1.2, springdoc 3.2.0, ArchUnit 1.5.1, PDFBox 3.0.8, POI 6.0.0 | Las tareas no tocan el padre | Bajo |
| ArchUnit con `@Test` y `.check()`, nunca `@ArchTest` | Con JUnit 6, `@ArchTest` no ejecuta nada y todo sale en verde sin comprobar | Solo estilo de pruebas |
| Vale cualquier JDK 21 o superior, sin rutas del Mac | Nada del Mac en el código común; verificado con JDK 21 y 26 | Una prueba que falle con un JDK concreto |
| Fichas en la búsqueda: solo las aprobadas | D-036 y D-037. Que el autor vea sus fichas pendientes, en la fase 2 | — |
| `ReviewCard` responde 404, y no 403, ante una ficha de otra empresa | Igual que con los documentos; nunca se consulta con el ámbito de todas las empresas para un administrador de empresa | Solo el código HTTP |
| «Al tercer intento, FAILED» = tres intentos reales | Lectura natural del spec 6.1 | Un intento de más |
| Las citas aceptan espacios de no separación | Los PDF los traen; si no, la verificación de la demo falla | Una línea |
| Canal exacto de la búsqueda: solo identificadores, con un único impulso | Reproducir la variante medida en el estudio, como dice el spec 6.6. La batería subió de 26/30 a 27/30 | Volver al anterior: 26/30 |
| Errores de duplicado traducidos a errores del dominio | Ninguna excepción de Spring cruza el puerto | Bajo |
| Contrato OpenAPI corregido antes del MCP (tarea 9a) | Choque de nombres de esquemas: la respuesta de la búsqueda tenía el esquema de la petición | Una tarea pequeña |
| `MCP_HOST` configurable | En un contenedor no puede escuchar en `127.0.0.1` | Ninguno |
| `/v1/docs` y `/v1/openapi.json` activos | La API solo escucha en `127.0.0.1` y la documentación no lleva datos | Se desactivan con una propiedad |
| Usuario de la aplicación fijo (`odin_app`) y carpeta de originales fija en Compose | Cambiarlos rompía el arranque | Ninguno |
| La demo en su propio proyecto de Compose, con puertos 18080, 18088 y 18090 y datos desechables | No toca una instalación arrancada ni el puerto 8090, que está ocupado en este Mac | Solo rutas de ficheros |
| Revisión final repartida en dos revisores (API, y clientes con el despliegue), con una sola corrección conjunta | El diff de la fase ocupa unos 576 KB | Algún hallazgo entre las dos partes; los dos tenían el contrato y el registro |

**Comprobaciones nuevas que pidió el CEO o la revisión, y quedaron dentro:**
- la tarea 2b: pruebas de aislamiento de identidad y organización. Destapó un fallo real: un trabajador podía listar empresas;
- el límite de intentos de entrada, con memoria acotada.

## 4. Qué falta

**Reservado al propietario:**
- Pasar `feature/api-central` a `develop`.
- Registrar el conector MCP en Claude Desktop de este equipo. Es configuración fuera del repositorio; el `README` explica cómo hacerlo.
- Remoto y push, cuando exista el repositorio en GitHub.

**Fase 2 (spec 3.2):**
- Lector OCR: contrato del servicio lector y puente al Unlimited-OCR 2 en el repositorio OCR. Hoy los escaneados quedan en `PENDING_READER`, sin perderse.
- Etiquetado por tema.
- Casos parecidos.
- Permisos por proyecto.
- En el panel: temas, consumo de la IA, estadísticas descriptivas y configuración de la IA.
- Compartir conocimiento entre empresas, con la decisión de negocio pendiente (D-033).
- Que el autor vea sus fichas pendientes (D-037).
- Motor de IA propio con verificación de citas en el servidor.

**No probado:**
- Con Claude Desktop ni con otra IA real: la demo usa el cliente MCP del SDK.
- En Linux o Windows, ni con imágenes amd64: solo se construyó en arm64, sobre bases multiarquitectura.
- Que la IP real del cliente llegue al límite de intentos a través de nginx: las cabeceras están puestas, pero no hay prueba.
- Rendimiento y consumo de memoria: no se han medido, así que no se dan cifras.

**Avisos menores diferidos.** La revisión final los clasificó; ninguno bloquea:
- **Contrato:** los campos que pueden ser nulos salen como opcionales.
- **Lectores:**
  - un DOCX dañado muestra «Lector no disponible» en lugar de «Documento ilegible»;
  - el log de errores del proceso puede incluir fragmentos de mensajes del parser.
- **Identidad:**
  - el administrador del grupo puede desactivarse a sí mismo;
  - con un correo desconocido, la entrada no ejecuta el hasher (diferencia de tiempos);
  - la comprobación de la guarda del rol (`USAGE` frente a `MEMBER`).
- **Panel:** algunos formularios se vacían aunque falle la acción, y no avisa de un documento duplicado.
- **Despliegue:**
  - etiquetas de imagen sin fijar (nginx, node, maven);
  - sin caché de Maven en el Dockerfile;
  - sin `healthcheck` en el panel ni en el MCP.
- **Pruebas:** faltan algunas de ficheros corruptos y de concurrencia de `SKIP LOCKED`.
- **Contraseña del usuario de la aplicación:** se fija en el primer arranque y solo admite letras, dígitos, `-` y `_`. Está documentado en `.env.example`.

## 5. Para el registro técnico

T-012 en `../OCR/docs/business-digital-mind/registro-tecnico.md` recoge las decisiones de implementación de la fase 1.
