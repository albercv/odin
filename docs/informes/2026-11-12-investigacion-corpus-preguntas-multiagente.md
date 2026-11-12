# Investigación: corpus reales, baterías de preguntas y búsqueda multiagente con jueces

- Fecha: 2026-11-12. Autor: CTO 3, con tres subagentes Haiku.
- Encargo: petición del propietario del 2026-11-12. Pidió buscar en internet documentos y baterías que amplíen la prueba (documentos reales, OCR malo, documentación antigua, contradictoria o con errores), ampliar las preguntas y estudiar un banco con varios agentes de búsqueda y varios jueces.
- **Límite:** solo búsquedas web, sin descargar nada ni ejecutar código.
  - Los datos salen de fragmentos de resultados y resúmenes, no de los documentos completos.
  - Las licencias marcadas «no verificada» hay que leerlas en la fuente antes de usar nada.
  - Las cifras de terceros son de sus artículos: hay que comprobarlas en el original antes de publicarlas.

## 1. Documentos reales y públicos en español

| # | Fuente | Qué aporta | Necesidad que cubre | Licencia o condiciones | Datos personales | Respuesta correcta conocida |
|---|---|---|---|---|---|---|
| 1 | API de legislación consolidada del BOE (`boe.es/datosabiertos/api/api.php`) | Normas con todas sus versiones, fecha de cada versión y norma modificadora (ELI, `estado_consolidacion`) | Versiones y sustituidos; correcciones de errores | Ley 37/2007 y condiciones de la sede. Hay que avisar de que el texto consolidado es meramente informativo | Ninguno | Muy alta: qué versión estaba en vigor en cada fecha |
| 2 | Ordenanzas fiscales y municipales (boletines provinciales y sedes; por ejemplo, Córdoba y La Laguna) | Versiones anuales, aprobación definitiva con fecha y algún texto consolidado | Versiones y contradicciones | Por boletín, no verificada | Bajo (firmas de cargos públicos) | Alta |
| 3 | Gazeta de Madrid y BOE histórico (`boe.es/buscar/gazeta.php`) | Escaneos de 1661 a 1959, con texto buscable de 1836 a 1959 | OCR malo | Reutilización del BOE, no verificada en detalle | Bajo | Media: no hay transcripción de referencia |
| 4 | Biblioteca Digital Hispánica y Hemeroteca Digital (BNE) | Millones de páginas escaneadas, con OCR de calidad variable | OCR malo | CC BY 4.0 según la BNE; las imágenes tienen condiciones propias | Bajo a medio (prensa antigua con nombres) | Baja: hay que transcribir nosotros |
| 5 | Condiciones generales de seguros publicadas (hogar y comunidades) | Pólizas reales con código y fecha de edición | Tipos de documento de una administración de fincas; versiones | Términos de reutilización no verificados | Ninguno | Media |
| 6 | Datos abiertos de contratación pública (PLACSP, datos.gob.es) | Licitaciones, contratos y sus modificaciones | Contratos de mantenimiento, limpieza y ascensores; modificados | No verificada | Medio (empresas y, a veces, autónomos) | Alta en los metadatos; los pliegos en PDF, sin verificar |
| 7 | MultiFinBen-SpanishOCR (Hugging Face) | De 10.000 a 100.000 imágenes de normativa peruana con su texto | OCR | Apache 2.0 según la ficha, por confirmar | Bajo | Media-alta (pares imagen-texto) |
| 8 | XFUND, parte en español | Formularios anotados por campo | Formularios | Ambigua según la copia | Medio | Alta para campos |

- **No recomendado:** sentencias del CENDOJ. Su reutilización se rige por el Reglamento 3/2010 del CGPJ, puede exigir licencia y precio, y tienen datos personales.
- **Conclusión:** no hay ningún corpus público de actas, presupuestos ni contratos de comunidades de propietarios.
  - Lo más cercano son las pólizas de seguro, los contratos públicos de mantenimiento y las ordenanzas con versiones.
  - El BOE consolidado es la mejor fuente real de documentos sustituidos y corregidos con respuesta conocida.

## 2. Baterías públicas de evaluación de RAG

Ninguna trae documentos de fincas en español. Sirven sus métodos, no sus datos.

| Recurso | Qué prueba | Qué tomamos | Licencia |
|---|---|---|---|
| RGB (AAAI 2024, arXiv 2309.01431) | Ruido, rechazo negativo, integración y datos falsos en el contexto | Su rechazo negativo es nuestro «no consta» | No verificada |
| WikiContradict (NeurIPS 2024, arXiv 2406.13805) | Pasajes que se contradicen | «Dos actas dicen cosas distintas» | No verificada |
| ConflictBank (NeurIPS 2024, arXiv 2408.12076) | Conflictos temporales, de desinformación y de significado | Plantillas de conflicto | CC BY 4.0 según arXiv |
| ClashEval (NeurIPS 2024, arXiv 2404.10198) | Si el modelo se cree un dato falso del contexto | Editar un dato del documento y ver si la respuesta lo sigue | No verificada |
| OHRBench (arXiv 2412.02592) | Cómo afectan los errores del OCR a la búsqueda y a la respuesta | El método para medir la pérdida por OCR | No verificada |
| LegalBench-RAG (arXiv 2408.10343) | Recuperar el tramo exacto de texto | Medir la precisión de las citas literales | No verificada |
| RAGTruth (ACL 2024, arXiv 2401.00396) | Afirmaciones que el contexto no respalda | Datos sin respaldo | No verificada |
| FRAMES (Google, arXiv 2409.12941) | Preguntas de varios saltos, numéricas y temporales | Agregación y cálculo | Apache 2.0 |
| CRAG (Meta, arXiv 2406.04744) | Preguntas temporales y de dominio | Solo para uso interno | CC BY-NC 4.0 |
| TimeQA (arXiv 2108.06314) | La fecha cambia la respuesta | Vigencia | No verificada |
| MKQA (Apple) | 10.000 preguntas en 26 idiomas, con español, y algunas sin respuesta | Estilo de preguntas en español | Por verificar |

**Métodos para preguntas que no contaminen la medición:**

- congelar el conjunto antes de ajustar, y partirlo por documento o por fecha;
- **edición contrafactual:** cambiar una cifra o una fecha del documento y comprobar que la respuesta cambia con el texto;
- paráfrasis con sinónimos, palabras corrientes y faltas;
- **pares casi negativos:** la misma pregunta sobre otro edificio, otro año u otro portal, que debe dar «no consta»;
- ruido de OCR simulado (0 y O, 1 y l, tablas rotas).

**Tipos de pregunta que faltan o hay que reforzar:**

| Tipo | Ejemplo |
|---|---|
| Falso supuesto | «¿Cuánto pagó la comunidad por la derrama de 2019 del ascensor?» (no hubo derrama) |
| Negación | «¿Qué parte de la finca no cubre el seguro?» |
| Vigencia | «¿Quién es el administrador según el acta más reciente?» |
| Conflicto | Dos actas con cifras distintas para el mismo presupuesto |
| Agregación | «Enumera las juntas en las que se aprobaron obras de fachada» |
| Comparación | «¿Fue mayor el presupuesto de 2021 o el de 2023?» |
| Cálculo | «¿Cuánto paga el 3.º B si su cuota es el 12 % de 45.000 €?» |
| Varios saltos | «¿Quién firmó el contrato de la última reparación de la cubierta?» |
| Ambigüedad | «¿Cuándo se revisó el ascensor?» (hay dos) |
| Palabras corrientes | «¿Cuántos meses llevamos de cuotas atrasadas?» |
| Errores en la pregunta | «Presupuesto de rehablitación 2O22» |
| Respuesta parcial | «¿Qué garantía tiene el ascensor y hasta cuándo?» (solo consta la garantía) |
| Dato en una tabla o en un escaneo | La página citada tiene que ser la de la tabla |
| Casi negativo | La misma pregunta sobre otro portal |
| Fecha implícita | «¿Qué cuota pagamos ahora?» (solo consta hasta 2022) |

## 3. Varios agentes de búsqueda y varios jueces

**Agentes de búsqueda:**

- **Anthropic, sistema de investigación multiagente** (anthropic.com/engineering/multi-agent-research-system):
  - un líder Opus 4 con subagentes Sonnet 4 superó en un 90,2 % a un solo Opus 4 en su evaluación interna;
  - **el gasto de tokens explica el 80 % de la variación del rendimiento.** Más agentes rinden más en buena parte porque gastan más.
- **Fusión de consultas.** RRF (Cormack y otros, SIGIR 2009) es el método estándar para combinar listas de resultados.
  - Un estudio de Dell de 2026 (arXiv 2603.02153) encuentra que la fusión mejora qué candidatos aparecen, pero no la respuesta final, y añade latencia.
  - Hay que compararla siempre con un solo agente que gaste lo mismo.
- **Muchas muestras y votación** (autoconsistencia):
  - las ganancias son pequeñas en preguntas sobre documentos, por ejemplo +0,4 % en HotpotQA con 20 muestras (arXiv 2511.00751);
  - el coste crece casi en línea recta con el número de muestras.

**Jueces:**

- Los jueces de IA tienen sesgos de posición, de longitud y de **preferencia por lo suyo**: un modelo favorece lo que reconoce como escrito por él (arXiv 2306.05685 y 2404.13076). Haiku juzgando a Haiku cae de lleno ahí.
- **PoLL** (arXiv 2404.18796): un jurado de 3 modelos pequeños de familias distintas supera a un juez grande y cuesta unas 7 veces menos. Lo que ayuda es la diversidad, no el número. Diez jueces iguales comparten los mismos sesgos.
- **Calibrar los jueces con lo que ya sabemos que es correcto.**
  - ARES (arXiv 2311.09476) y la inferencia con predicciones (PPI, arXiv 2301.09633) corrigen la cifra de los jueces con un conjunto pequeño bien etiquetado. ARES habla de unas 150 etiquetas.
  - En nuestro caso, ese conjunto son las preguntas con clave que puntúa el script.
- **Acuerdo entre jueces:** kappa de Cohen o alfa de Krippendorff. El alfa es preferible con varios jueces, pero engaña cuando casi todo es «correcto».

**Recomendación de la investigación, que el diseño recoge:**

- empezar con pocos agentes (3 a 5) y subir solo si la ganancia supera la variación medida;
- comparar siempre con un solo agente con el mismo gasto de tokens;
- un jurado pequeño y diverso: distintas instrucciones y, cuando se pueda, distintos modelos;
- calibrar los jueces contra el script;
- caché de búsquedas y de juicios, muestras al iterar y la batería completa solo para la configuración final.

## Fuentes

anthropic.com/engineering/multi-agent-research-system · plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf · arXiv 2603.02153, 2212.10496, 2203.11171, 2511.00751, 2306.05685, 2404.13076, 2404.18796, 2509.12382, 2311.09476, 2301.09633, 2309.15217, 2309.01431, 2406.13805, 2408.12076, 2404.10198, 2412.02592, 2408.10343, 2401.00396, 2409.12941, 2406.04744, 2108.06314, 2007.15207, 2407.01449 · boe.es/datosabiertos · boe.es/buscar/gazeta.php · bdh.bne.es · datos.gob.es · huggingface.co/datasets/TheFinAI/MultiFinBen-SpanishOCR · github.com/doc-analysis/XFUND
