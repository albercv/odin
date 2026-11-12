# Búsqueda RRF: experimento y parada (para el CTO 3)

Rama task/fase-2-conocimiento/busqueda-rrf, base 2e4e60b. Decisión del CTO 2: no se cambia la búsqueda, no se baja ningún umbral, no se reformulan las baterías. El parche del experimento (`2026-11-12-busqueda-rrf-experimento.patch`) no se aplica.


Estado: ninguna variante cumple la puerta (27/30, 96/131, 19/19). No hay commits; el árbol está limpio.
El experimento completo (SQL con variantes por variable de entorno ODIN_V/ODIN_W, tests rojos y medición de tiempo) está en `docs/informes/2026-11-12-busqueda-rrf-experimento.patch`.

Base (2e4e60b): SearchBatteryIT 27/30, ArchiveSearchBatteryIT 96/131, SimilarCasesBatteryIT 19/19; búsqueda del archivo ~93-94 ms por consulta (131 consultas, ~8 ms en las baterías pequeñas).

Implementación probada: `rank` = boost del canal exacto + suma RRF (k=60, 100 candidatos por lista); desempate ts_rank_cd de todas las consultas juntas, luego título/página/id; sustituidos detrás (el tsrank entra en el ORDER BY de withSupersession). Una lista por consulta de la llamada.

| Variante | Search 27/30 | Archivo 96/131 | Casos 19/19 |
|---|---|---|---|
| A: RRF por consulta (ts_rank_cd) | 22 | 96 | 17 |
| B: A con cobertura de raíces como primer criterio | 22 | 93 | 17 |
| C: A + lista de la unión (OR) con el mismo peso | 23 | 96 | 17 |
| I: A + lista de la unión ordenada por cobertura, peso 1 / 2 / 3 / 6 / 12 | 24 / 25 / 27 / 26 / 26 | 96 (1 consulta = igual que hoy) | 17 / 17 / 17 / 18 / 18 |
| K: A + lista de la unión por IDF (df dentro de los candidatos), peso 1 / 3 | 24 / 27 | no medido | 17 / 17 |
| D: solo la unión, ordenada por cobertura (sin RRF) | 28 | no medido (con una consulta, B daba 93) | 18 |
| G: solo la unión, por IDF (sin RRF) | 27 | no medido | 18 |

Causa del choque: las reformulaciones de las baterías (A1, B1, C1, C2, N03, N11…) son 3-6 paráfrasis de la misma idea; el acierto depende de que el documento case raíces repartidas entre consultas (lo que premia el OR unido). RRF por consulta exige estar alto en cada lista y los saca de los 5 primeros. Los pesos altos de la unión devuelven el orden de hoy pero anulan el efecto buscado (consulta corta específica junto a genéricas). N03 falla en todas las variantes con RRF real; N11 en las de peso bajo.

Aviso de método: un primer barrido con pesos enteros dio cifras falsas por división entera en SQL (equivalía al orden de hoy); descartado y repetido con 1.0.

Tests rojos escritos (consulta específica corta junto a genérica, búsqueda y casos parecidos): fallaban con el código de hoy; con A pasan; con C/I a peso 1 pasan en búsqueda normal (no re-medido tras corregir la división entera, ver parche).

No hecho (parada antes): umbrales, MCP (descripción de searchKnowledge), make check, make demo, tiempo después. Rendimiento medido con el experimento: 93-102 ms por consulta en el archivo (sin cambio apreciable).
