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

## Git (gitflow, solo local)

- Ramas: `main` (cierres de sprint), `develop` (integración), `feature/<nombre>` (sale de `develop`), `task/<feature>/<tarea>` (sale de su feature).
- Cada tarea en su propia rama `task/…`; al terminar, merge `--no-ff` a su feature.
- Una feature pasa a `develop` solo cuando el propietario la aprueba; `develop` pasa a `main` al cierre de sprint.
- Sin remoto de momento: no hacer push.
- Ningún commit ni PR lleva atribución a Claude: sin `Co-Authored-By` ni firmas de Claude Code.
