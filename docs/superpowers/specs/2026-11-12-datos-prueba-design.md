# Odín · Datos de prueba y conector de Claude Desktop

- Fecha: 2026-11-12. Autor: CTO. Encargo del propietario por el CEO, el 2026-11-12.
- Rama: `feature/datos-prueba`, que sale de `develop` (94819a5, con la fase 2). No cambia código del producto: solo órdenes de `make`, scripts de `deploy/instalacion/` y el `README`.
- **Objetivo:** que el propietario pruebe Odín desde su Claude Desktop con datos ficticios en su instalación (8088), y pueda volver a empezar.

## 1. Órdenes

| Orden | Qué hace |
|---|---|
| `make datos-prueba-cargar` | Carga datos ficticios en una instalación vacía, todo por la API |
| `make instalacion-reiniciar CONFIRMAR=BORRAR` | Copia la base y los originales en `data/copias/` y deja la instalación vacía, lista para el primer arranque. Sin `CONFIRMAR=BORRAR` solo explica qué haría |
| `make instalacion-restaurar COPIA=<fichero .sql> CONFIRMAR=BORRAR` | Vuelve a una copia: es la vuelta atrás de D-056, la que usan las otras dos órdenes si fallan a medias |
| `make mcp-claude-desktop [TRABAJADOR=marta\|javier\|ignacio]` | Compila `mcp/` y registra la entrada `odin` en Claude Desktop con la clave de ese trabajador de prueba |

- **Cerrojo:** las cuatro van con `data/instalacion.lock` (`mkdir`, atómico). Si está cogido, no hacen nada.
- **Otra instalación:** `INSTALACION_PROYECTO` (por defecto `odin`) y `INSTALACION_ENV` (por defecto `deploy/.env`) eligen la pila. Así se prueban en una pila desechable sin tocar la del propietario.

## 2. Datos

Salen del archivo ficticio de la medición (`experimentos/2026-11-12-archivo-ficticio/datos/`), sin copiarlo:

- **Empresas:** «Administraciones Ribera» (`RIB`) y «Gestión Albar» (`ALB`).
- **Comunidades:** 10 proyectos cerrados, 8 de Ribera (las 3 base del estudio y 5 copias) y 2 de Albar, más el proyecto abierto «Procedimientos generales» y las normas sin proyecto de cada empresa. Unos 160 documentos: casos, actas, pólizas vieja y nueva, anexos, contratos, presupuestos, avisos y normas.
- **Un escaneo** (`deploy/demo/escaneo-ficticio.png`), sin proyecto en Ribera, para ver el lector OCR: sin lector, se queda esperando.
- **Casos:** los `caso-*` se suben marcados como caso.
- **Sustituciones:** las parejas de `pila/sustituciones.py` cuyos dos documentos entran.
- **Temas:** la lista de fincas de la demo (D-031) en cada empresa. Sin etiquetador no hay temas de la IA, así que los casos llevan temas puestos por el administrador de prueba según su tipo (origen `ADMIN`). Con etiquetador, la IA añade los suyos.
- **Fichas:** las del archivo en esos proyectos (37: aprobadas, pendientes y rechazadas). Las escribe el trabajador con su clave (`POST /v1/cards`) y las revisa el administrador.
- **Personas:**
  - el administrador del grupo, que crea la carga con el token de instalación;
  - Marta Olivares Ríos (Ribera, todos los proyectos);
  - Javier Bermúdez Lara (Ribera, solo Calle Pino 26 y Calle Roble 46);
  - Ignacio Salas Ferrer (Albar, todos).
  - Una clave por trabajador.
- **Credenciales:** `data/datos-prueba/credenciales.local.md` y `.json`, con permisos 600. Nunca se escriben en pantalla.

## 3. Decisiones

| Decisión | Motivo | Alternativa descartada |
|---|---|---|
| La carga exige una instalación vacía: hace el primer arranque (`POST /v1/setup`) y para sin tocar nada si ya estaba hecho | No mezclar datos de prueba con datos reales ni pedir la contraseña del propietario | Cargar junto a los datos reales con su usuario |
| Todo por la API, también documentos y fichas | Se aplican las reglas y las dos barreras (D-014, D-020) | El SQL de la medición, que se las salta |
| Los `.txt` se suben tal cual: Odín los guarda como una sola página | Es lo que hace el producto con un texto | Convertirlos a PDF para paginarlos |
| Reiniciar es borrar el esquema `public` y los originales, y arrancar la API para que Flyway lo cree de nuevo | Conserva el rol `odin_app` (V4), que vive fuera de la base | Borrar el volumen de Postgres: obliga a recrear el rol para restaurar |
| Si la carga falla a medias, se vuelve a la instalación vacía con la misma orden de reiniciar | Nunca a medias (D-056) | Dejar lo cargado |
| La copia de `claude_desktop_config.json` va a `data/copias/` | No dejar ficheros en la carpeta de Claude | Copiarlo a su lado |

## 4. Verificación

- Pruebas de Python (biblioteca estándar) en `make check`: la selección, los temas de cada caso, las credenciales con permisos 600 y el registro en Claude Desktop sin tocar otras entradas.
- De principio a fin en una pila desechable (`odin-datos-prueba`, puertos 38080 a 38090): cargar, comprobar el aislamiento por la API y por el MCP de stdio, registrar en un `claude_desktop_config.json` de prueba, reiniciar y restaurar.
