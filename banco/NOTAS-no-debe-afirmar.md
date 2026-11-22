# Nota para CTO 2: clave `no_debe_afirmar`

- Qué cambia: clave opcional de pregunta con afirmaciones prohibidas (criterio 9 de D-126, no dar por verificados desajustes que no lo están).
- Dónde está el gancho: `scoring.is_correct` (`banco/scoring.py`). Aplica `scoring.forbidden_hit` tras el resultado de `puntuar.score_item`.
  Todo lo que usa `is_correct` lo hereda: `correcta_script`, acierto por enfoque, acuerdo y curvas.
- Uso: en la pregunta de la batería, `"no_debe_afirmar": ["desajuste", "no\\s+cuadra", "discrepancia", "falta una segunda factura"]`.
  Cada elemento es texto o regex; se compara sin mayúsculas ni tildes, frase a frase. Ausente o vacío: sin efecto.
- Excepción opcional: `"no_debe_afirmar_salvo": ["no consta"]`. Si la frase que casa también casa con uno de estos patrones, no cuenta.
  Frases cortadas por `. ! ? ;` y salto de línea; no entiende negaciones más allá de eso.
- Informe: `resumen.json` trae `noDebeAfirmar` (id y patrón) e `informe.md` una sección «Afirmaciones prohibidas».
- `puntuar.py` no se toca: vive fuera de git en `experimentos/` y no debe modificarse; el gancho va en el banco, versionado.
- Una regex mal escrita lanza `re.error` (falla fuerte, no se ignora).
- Pruebas: `banco/test_no_debe_afirmar.py`. El PR va a `feature/calidad-recuperacion`, nunca a `develop`.
