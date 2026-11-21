# Odín — API central de Business Digital Mind

Odín es el cerebro digital de una empresa: centraliza documentos, casos y fichas de conocimiento para que cada trabajador encuentre cómo se resolvió algo parecido, con respuestas que citan su fuente, desde la IA que ya usa (por MCP) y con un panel para el administrador.

## Dónde está cada cosa

- Diseño aprobado de la API: `docs/superpowers/specs/`.
- Planes de implementación y estado de las tareas: `docs/superpowers/plans/`.
- Producto y negocio (definición, decisiones `D-xxx`, lenguaje ubicuo, encargos del CTO): de momento en el repositorio OCR, `../OCR/docs/business-digital-mind/`. Registro técnico (`T-xxx`): `../OCR/docs/business-digital-mind/registro-tecnico.md`.

Este fichero es configuración del proyecto. No guardar aquí requisitos, planes ni estado: van en los documentos anteriores.

## Reglas no negociables

- Nunca inventa: todo lo que se devuelve sobre el trabajo del cliente sale con cita verificable (documento, página, texto literal); sin fuente, «no lo sé» (D-008, D-024). Se distingue siempre el documento original de lo generado por IA.
- Aislamiento: dos clientes nunca comparten instalación (D-014). Dentro de un grupo, cada empresa está separada por defecto; el cerebro garantiza la separación, no la IA del trabajador (D-020, D-033).
- E2D no recibe datos de clientes ni hace de intermediario (D-019). A la IA externa solo van los fragmentos necesarios para cada respuesta (D-016).
- Conocimiento, no evaluación: nada que juzgue, puntúe o compare a personas (D-005).
- Desacoplado: el núcleo no depende de ninguna tecnología, proveedor ni hardware. Nada específico de un equipo (rutas del Mac, Metal, MLX) entra en el código común; va en la configuración de cada instalación.
- El lector OCR es Unlimited-OCR 2 y vive en su propio repositorio; nunca la v1.
- Datos ficticios en la demo. Nunca documentos reales de clientes ni del propietario en este repositorio.
- No declarar precisión ni rendimiento sin medición que lo respalde.
- Nada global: dependencias solo en el proyecto o en sus contenedores. Pedir permiso antes de instalar o registrar algo fuera (Claude Desktop, atajos del sistema).
- Verificar ejecutando antes de dar algo por terminado.

## Calidad (D-029)

- TDD: ninguna línea de producción sin una prueba que falle antes.
- Arquitectura hexagonal: dominio y casos de uso sin dependencias de frameworks; las tecnologías son adaptadores. Las capas se comprueban con pruebas (ArchUnit).
- SOLID y Clean Code; métodos de 30 líneas como máximo (Checkstyle, ESLint).
- Nombres en inglés y explicativos, camelCase en variables y métodos. Comentarios mínimos.
- APIs documentadas en OpenAPI.
- Reutilizar antes de escribir: buscar en el código (codegraph) antes de crear algo nuevo.

## Convenciones

- Documentación, informes y mensajes de commit en español; código e identificadores en inglés.

## Flujo de trabajo

superpowers: diseño aprobado (spec) → plan (`superpowers:writing-plans`) → ejecución con subagentes (Sonnet para lo complejo, Haiku para lo mecánico) → `superpowers:verification-before-completion`.

## Git (gitflow por PR, D-122)

- Ramas: `main` (cierres de sprint), `develop` (integración), `feature/<nombre>` (sale de `develop`), `task/<feature>/<tarea>` (sale de su feature).
- GitHub (`albercv/odin`, privado) es el remoto `origin` y la referencia. No se mergea en local: toda unión se hace por PR, con merge commit (`gh pr merge --merge`).
- Cada tarea en su propia rama `task/…`, subida a `origin`. Al terminar, PR a su feature, que mergea quien trabaja la tarea.
- Una feature se pone al día con `develop` (y ensaya su migración sobre una copia, si la trae) y abre su PR a `develop`. Ese PR se mergea solo con el visto bueno del propietario; después se trae `develop` (`git pull`) y la instalación del propietario se actualiza desde `develop` (`make instalacion-actualizar`).
- **Validación en el repo (D-123, encargo 27):** cada PR a `develop` o `main`, cada push a esas ramas y cada ejecución manual lanzan `.github/workflows/validacion.yml` en GitHub Actions, con los mismos objetivos que `make check` (`check-api`, `check-mcp`, `check-panel`, `check-instalacion`). Es un solo trabajo que ejecuta siempre las cuatro partes, una tras otra, y muestra todos los fallos a la vez; acaba en rojo si falla cualquiera.
- **No se mergea ningún PR con la validación en rojo o sin terminar.** GitHub no lo puede imponer en este repositorio privado sin protección de ramas: lo impone quien hace el merge. La puerta de la instalación impide instalarlo, no mergearlo.
- Antes de abrir un PR de feature a `develop`, `make check` en local debe estar en verde.
- **La instalación solo se actualiza por la puerta:** `make instalacion-actualizar` y `make instalacion-desplegar` instalan el commit validado de la rama fijada en `ODIN_RAMA_INSTALACION` (en `deploy/.env`) o en `RAMA=`. No hay forma de saltarla. `make instalacion-restaurar` sirve para volver a una copia.
- Nada de `make check` ni del despliegue puede depender de `experimentos/`, que está fuera de git. El archivo ficticio se genera con `make archivo-ficticio` desde `datos-prueba/archivo-ficticio/`.
- **Consumo de Actions:** pasada la primera semana, el propietario comprueba el consumo real en GitHub (cuenta personal, Settings, Billing and licensing, Usage, filtrado por Actions). Las cifras del CTO (unos 9 minutos facturables por ejecución con caché y 13 en frío; unas 222 ejecuciones al mes dentro de los 2.000 minutos gratis; cada cambio mergeado cuesta al menos una ejecución de PR y otra de push) son una estimación, la suma del tiempo del trabajo redondeada hacia arriba, no la factura.
- `develop` pasa a `main` al cierre de sprint por PR desde `release/<versión>`, con etiqueta en `main`. Las instalaciones de clientes salen solo de `main`. Arreglos urgentes: `hotfix/<nombre>` desde `main`, por PR a `main` y a `develop`.
- Sube tu rama a `origin` al menos al acabar cada sesión de trabajo. Antes de cada push, revisa que no haya claves.
- `experimentos/` está fuera de git (`.gitignore`). Su historial anterior está en `data/copias/odin-git-antes-github-2026-11-17.git`.
- Sin ramas de integración aparte.
- Ningún commit ni PR lleva atribución a Claude: sin `Co-Authored-By` ni firmas de Claude Code.
