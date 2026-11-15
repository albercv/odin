# Odín · Primera carga a escala: medición (encargo 22)

Fecha: 2026-11-15. Código medido: `feature/primera-carga-2` (491cfb1), que es lo que se va a instalar. Herramientas en `feature/primera-carga-escala`, carpeta `experimentos/2026-11-15-primera-carga-escala/`. Se ha medido en una pila desechable (`odin-primera-carga`, puertos 48080/48088/48090), nunca en la del propietario. Todo en local: el lector OCR (Unlimited-OCR 2) y Ollama (`qwen3.5:9b`). No se ha usado ninguna IA de pago.

## Qué se ha medido

- **Archivo ficticio generado sin IA:**
  - el mundo ficticio de las mediciones anteriores, ampliado con más copias;
  - Mirador, el proyecto «Residencial Mirador del Puerto», con 248 documentos; se quitan 22 facturas de las que solo había imagen;
  - Valtierno: el archivo «generalidad», con 6 empresas cliente y 88 documentos.
- **Volumen:** 2.940 ficheros (749 MB): 2.805 documentos distintos y 135 copias exactas con otro nombre. Incluye 105 escaneos, 70 de ellos con su original en la carpeta. Una empresa con 300 proyectos.
- **Recorrido:** el mismo que en la medición anterior.
  - la orden real (`make primera-carga --si`);
  - la lectura;
  - Odín descubre la lista de proyectos en los textos y se confirma entera;
  - la clasificación;
  - un administrador simulado decide con la verdad: confirma por grupos, decide las propuestas de confianza baja y coloca lo que queda.
- **Lo que no se ha medido a esta escala:** el corte con `SIGKILL` ni D1 (las comprobaciones de aislamiento), porque los dos se midieron en la ronda anterior.
- **Repeticiones:** dos ejecuciones completas. Cada una tarda unos 35 minutos de reloj, contando la generación.

## Tiempos (s, reloj de pared; lectura y clasificación con ±5 s)

| Fase | Ejecución 1 | Ejecución 2 | Ronda anterior (845 documentos, 61 proyectos) |
|---|---|---|---|
| Subida (orden V3) | 18,5 | 19,3 | 6,9 (con corte) |
| Lectura (2.700 textos y 105 escaneos) | 808 | 763 | 231 (30 escaneos) |
| Clasificación | 401 | 376 | 35 |
| Confirmar y colocar (simulado) | 15 | 15 | 1,4 |

De la orden a la última confirmación van unos 20 minutos. El administrador hizo 295 clics de «Confirmar grupo» en los proyectos seguros, uno por proyecto, y 121 decisiones una a una.

## Memoria (pico)

| Qué | Ejecución 1 | Ejecución 2 |
|---|---|---|
| Puente OCR, leyendo | 7,9 GB | 7,9 GB |
| Ollama, clasificando | 10,3 GB | 10,3 GB |
| OCR en reposo y Ollama a la vez, clasificando | 4,6 + 10,3 GB | 4,6 + 10,3 GB |
| Contenedor `api` | 1.029 MiB | 1.125 MiB |
| Contenedor `postgres` | 104 MiB | 114 MiB |

- **Cómo se mide:** con `footprint` de macOS en el OCR y en Ollama, y con `docker stats` en los contenedores.
- **El modelo pesa más que en la ronda anterior:** su pico es de 10,3 GB, frente a 8,0 GB entonces.
- **El puente OCR lo comparte la instalación del propietario,** así que su cifra puede incluir otro trabajo.

## Acierto frente a la verdad (las dos ejecuciones dan lo mismo)

| Qué | Resultado |
|---|---|
| Proyecto, confianza alta | 2.480 propuestas: 2.370 bien y 110 mal |
| Proyecto, confianza baja | 121 documentos: en 100 la verdad estaba entre los candidatos; 21 (25 en la ejecución 2) hubo que corregirlos |
| Sin propuesta de proyecto («Sin ubicar») | 204 (7,3 %) |
| Fichas de caso | 736 de 737; número de caso correcto en 729 |
| Escaneo y su original | precisión 1,00, cobertura 0,90 (63 de 70) |
| Versiones | precisión 0,88, cobertura 0,84 |

- **Los 110 errores de confianza alta son todos de Mirador.** Fuera de Mirador, la confianza alta acierta en todos los casos: 2.370 de 2.370.
  - Al descubrir la lista, Odín tomó como nombres de proyecto trozos de los textos de Mirador. Normalizados, son «residencial mirador del puerto cif h 00000048», «residencial mirador del puerto dona rocio villaverde», «residencial mirador del» y «avenida»; el nombre verdadero, «Residencial Mirador del Puerto», no salió.
  - Con la lista confirmada sin corregir, 108 documentos de Mirador fueron al proyecto «…CIF H-00000048» y 2 a «Avenida». Los 108 están juntos, pero en un proyecto con mal nombre; los 2 de «Avenida» están en un proyecto que no corresponde a nada.
- **Los 204 sin ubicar:**
  - 98 son de proyectos que no salieron en la lista: las 6 empresas de Valtierno (93) y «Procedimientos generales» (5). Sus textos no nombran el proyecto como esperan las reglas;
  - 87 son de proyectos que sí estaban en la lista;
  - 19 eran de toda la empresa.
- **Mal propuestos:** 131 en total (4,7 %): los 110 de Mirador y los 21 de confianza baja.

## Qué cambiaría

1. **Limpiar los nombres que se descubren y dejar corregirlos antes de confirmar la lista.**
   - Hay que cortar el nombre en «CIF», «NIF», «D.», «Dña.» y similares, y descartar candidatos de una sola palabra genérica, como «Avenida».
   - En el panel, el administrador debería poder renombrar o unir candidatos antes de «Lista terminada».
   - Es el único error de confianza alta que ha salido, y manda documentos a un proyecto equivocado: los vería quien entre en ese proyecto.
2. **Ver de dónde sale el tiempo de la clasificación antes de ir más arriba.** Con 3,3 veces más documentos ha tardado 11 veces más (35 s → unos 390 s).
   - No se ha medido el reparto entre las reglas, los duplicados y el modelo.
   - Si se toma la cifra del estudio (1,29 s por documento que va al modelo), unos 325 documentos que no resuelven las reglas darían el mismo orden de tiempo. Es una hipótesis, no una medida.
   - Hay que medir por pasos antes de una carga de 10.000 documentos.
3. **Memoria del servidor del cliente.** Durante la clasificación están cargados a la vez Ollama (10,3 GB) y el OCR en reposo (4,6 GB): unos 15 GB, sin contar Odín.
   - Con 16 GB de memoria no queda margen.
   - Propuesta: descargar el modelo del OCR al terminar la lectura, o fijar como mínimo 32 GB. Hay que medirlo en el equipo del cliente.
4. **Proyectos que los textos no nombran** (las empresas de Valtierno): con la lista importada (`project-candidates/import`) estarían en la lista desde el principio. No se ha medido con importación.
5. **Confirmar 300 proyectos son 295 clics.** Un «Confirmar todos los grupos seguros», con su resumen delante, sigue siendo una decisión humana (D2) y ahorraría ese tiempo. Es una decisión de producto para el CEO.

## Cómo repetirlo

`experimentos/2026-11-15-primera-carga-escala/escala.sh todo`: genera la carpeta, sube la pila, mide, y después baja la pila con sus volúmenes y borra la carpeta. `escala.sh pequena` hace lo mismo con unos 150 ficheros. Las pruebas son `test_generar.py` y `parte_c/test_medir.py`. Mientras mide, la orden mira el disco de la VM de Docker y para si llega al 90 %; en estas ejecuciones estuvo en el 84 %.
