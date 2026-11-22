# Encargo 30: plan de prueba con IA (D-094)

- Fecha: 2026-11-22. Autor: CTO 3. Estado: **pendiente del permiso del propietario**. No se lanza nada sin él.
- Diseño: `docs/superpowers/specs/2026-11-22-respuestas-contexto-design.md`.

## Hipótesis (una sola)

Con lo que trae el encargo 30, Haiku y Sonnet responden a las dos preguntas del propietario con:
- el valor vigente, su documento, su página y su fecha;
- el historial en orden, cada valor con su documento y su página;
- ninguna cita inventada;
- ningún desajuste que no exista.

Con `develop`, no lo hacen. Lo que trae el encargo 30: los vínculos por identificador, `versions`, `corrections` e `incomplete` en todas las salidas, el orden nuevo de la búsqueda y las instrucciones nuevas.

## Qué se compara

- **Antes:** `develop` 1fbe205d.
- **Después:** la punta de `feature/respuestas-contexto` en el momento de lanzar. Se anota el SHA.
- **Mismos datos:** Mirador (ficticio, `.worktrees/mirador/experimentos/2026-11-14-caso-mirador/datos/`) en una pila desechable. Nunca la instalación del propietario.
- **Mismas preguntas,** el mismo trabajador (con acceso al proyecto) y el mismo ejecutor del banco.

## Preguntas y claves

| # | Pregunta | Acierto (lo comprueba un programa) |
|---|---|---|
| M1 | «¿Cuál es la franquicia por daños por agua de Residencial Mirador del Puerto?» | Aparecen:<br>- 450 € con VLM-25 (página 1) y la fecha de efecto 1 oct 2025;<br>- 300 € con VLM-22 y VLM-19, y 150 € con VLM-16, en orden;<br>- la regla de una franquicia por siniestro, citada a la página 3 (art. 4 de las generales).<br><br>Ninguna cita que `verifyCitation` no confirme |
| M2 | «¿Cuánto costó el mantenimiento del ascensor de Mirador en marzo de 2025?» | Aparecen 470,69 € con AT-2025-0302-R, y que rectifica 481,58 € de AT-2025-0302.<br><br>**No debe afirmar:** «desajuste», «no cuadra», «discrepancia» ni «falta una segunda factura» referido a uno o dos ascensores. Sí puede hablar de la diferencia de 398 € frente a 389 € que explica la rectificativa |
| M3 a M8 | las del apartado 8 del diseño: el anexo de la limpieza, la prima de 2022, la derrama, el artículo 12, un dato sin versiones y un trabajador sin acceso | sus claves en el diseño. Sirven para vigilar que nada empeora |

## Umbral que decide

- **Se adopta** si, con la feature:
  - M1 y M2 aciertan en las 3 de 3 repeticiones con Haiku, es decir, el peor de 3;
  - M1 y M2 aciertan con Sonnet (una repetición);
  - ninguna de M3 a M8 queda peor que con `develop`, comparando el peor de 3 con Haiku.
- **Si no,** no se adopta: la feature no pasa a `develop` y se diagnostica con las transcripciones.

## Cómo se puntúa

- **Lo que ya hay:** el ejecutor y el puntuador del banco.
  - `puntuar.py` está en `experimentos/2026-11-12-archivo-ficticio/medir/`, fuera de git. El banco lo importa desde allí (`banco/medir_path.py`, en `feature/calidad-recuperacion`).
- **El campo `no_debe_afirmar`:** por eso no puede ir dentro de `puntuar.py`, como estaba previsto. Va como una comprobación del banco, versionada en `feature/calidad-recuperacion` y con su prueba: una respuesta que contenga una afirmación prohibida de su clave no acierta.
  - Se hace en una tarea de esa rama, por el flujo de D-122.
  - Cuando se reanude, el CTO 2 la integra desde un PR a esa feature.

## Orden y coste (estimado, sin medir)

- **Haiku:** unos 0,01 USD por respuesta. Lo medido en el banco son de 0,0034 a 0,0054, y las respuestas de Mirador son más largas.
- **Sonnet:** unos 0,2 USD por respuesta, a unas 20 veces el precio de Haiku.

| Paso | Qué | Respuestas | Coste estimado |
|---|---|---|---|
| 0 | Comprobación previa sin IA: la pila carga, el banco pasa su `preflight` y las claves aparecen en sus páginas | — | 0 |
| 1 | Prueba de humo: M1 y M2 con Haiku (3 veces, antes y después) y con Sonnet (una vez, después) | 12 + 2 | unos 0,5 USD |
| 2 | Completa: M1 a M8 con Haiku (3 veces, antes y después) y con Sonnet (una vez, después) | 48 + 8 | unos 2,1 USD |

- **Tope:** se para si el gasto acumulado llega a 3 USD, dentro del máximo de 5 USD a la semana de D-094.
- **Si la prueba de humo ya no pasa,** no se lanza la completa.
- **Al terminar:** un resumen de 5 líneas con lo medido, separando lo medido de lo estimado.

## Riesgos conocidos

- **Tamaño de las respuestas:** sin filtro de proyecto, la respuesta de la franquicia pasa de unos 12 a unos 21 KB, porque las cadenas de pólizas del archivo de base llegan con sus versiones. Si el consumo sube de forma apreciable, el siguiente paso es recortar `heading` y `match` a las versiones más cercanas a la vigente.
- **Las reglas que pasaron de las instrucciones a las descripciones** (D-115 y la tarea 4) dependen de que la IA lea las descripciones.
