# Texto propuesto para CLAUDE.md y el prompt del CTO (encargo 27)

El CEO lo aplica con el visto bueno del propietario; el CTO no edita esos ficheros.

## Odín, `CLAUDE.md`, sección Git

- Cada PR a `develop` o `main`, cada push a esas ramas y cada ejecución manual lanzan `.github/workflows/validacion.yml` en GitHub Actions, con los mismos objetivos que `make check` (`check-api`, `check-mcp`, `check-panel`, `check-instalacion`).
- No se mergea ningún PR con la validación en rojo o sin terminar. GitHub no lo puede imponer en este repositorio privado sin protección de ramas: lo impone quien hace el merge. La puerta de la instalación impide instalarlo, no mergearlo.
- Antes de abrir un PR de feature a `develop`, `make check` en local debe estar en verde.
- La instalación solo se actualiza por la puerta: `make instalacion-actualizar` y `make instalacion-desplegar` instalan el commit validado de la rama fijada en `ODIN_RAMA_INSTALACION` (en `deploy/.env`) o en `RAMA=`. No hay forma de saltarla. `make instalacion-restaurar` sirve para volver a una copia.
- Nada de `make check` ni del despliegue puede depender de `experimentos/`, que está fuera de git. El archivo ficticio se genera con `make archivo-ficticio` desde `datos-prueba/archivo-ficticio/`.

## OCR, `CLAUDE.md`, sección Git

- **Validación (D-123, encargo 27):** `.github/workflows/validacion.yml` ejecuta las pruebas rápidas (`uv run pytest`, sin `-m model`) en GitHub Actions, en Linux:
  - en cada PR a `develop` o `main` y en cada push a esas ramas que cambie código (`uocr/`, `scripts/`, `tests/`, `ocr.py`, `pyproject.toml`, `uv.lock`, `Makefile` o el propio flujo);
  - también se lanza a mano.
- **Qué valida:** la lógica del lector con el backend de CPU de MLX. **No valida** Metal, ni el rendimiento, ni el modelo.
- **Lo que sigue siendo local en el Mac:**
  - las pruebas del modelo (`uv run pytest -m model`);
  - la prueba de Metal (`tests/test_env.py::test_metal_gpu_is_default_device`). Es la única que el flujo deja fuera, de forma explícita.

  Si otra prueba pasa a depender de Metal, fallará en la validación y se verá.
- **El backend de CPU** (`mlx[cpu]`, con la versión de `mlx` que fija `uv.lock`) se instala solo en el runner desechable de Actions. No se toca `pyproject.toml` ni `uv.lock`, así que se cumple la regla de nada global.
- **No se mergea ningún PR que cambie código con la validación en rojo** o sin terminar.

## Prompt del CTO (`.claude/output-styles/cto.md`), sección «Cómo trabajas», viñeta Git

- **Git** (detalle en `CLAUDE.md`): solo se instala lo que ha pasado la validación (D-123). Todo PR pasa por GitHub Actions, no se mergea ninguno en rojo, y `make instalacion-actualizar` se niega a instalar un commit sin validación en verde; no hay vía de emergencia.
