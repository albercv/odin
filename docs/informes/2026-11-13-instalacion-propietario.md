# Tu instalación de Odín: despliegue conjunto del 13 de noviembre de 2026

**Estado:** listo para lanzar, pendiente de que lo lances tú. El sistema de permisos impide al CTO lanzar despliegues en tu instalación.

**Sobre las cifras:** las de este documento se han contado en la base del ensayo. Ese ensayo usó el mismo código (`integracion/instalacion`, cf58b72), los mismos datos y la misma orden. Al desplegar, la orden escribe el recuento de tu base en `odin/data/recuento-<fecha>.md`; con él se confirma este documento.

## Qué entra

- **Expediente del caso (encargo 13).** Cada caso reúne sus documentos: informes, presupuestos, facturas, correos y fotos. Desde Claude se puede:
  - ver el expediente;
  - leerlo con cita y buscar dentro de él;
  - añadir y quitar documentos;
  - pedir un enlace de subida y abrir el original.
- **B-010.** Lo que no tiene proyecto sale como «Sin proyecto (general de la empresa)», no como «Proyecto borrado».
- **Conector con ruta estable.** Claude Desktop arranca su copia del conector en `odin/data/mcp-claude-desktop`. Ya no depende de ninguna worktree.
- **Lector OCR y etiquetado por tema.**
  - El lector OCR (Unlimited-OCR 2) corre en este Mac y lee los PDF escaneados.
  - El etiquetado por tema usa la IA local (Ollama) y trabaja de 22:00 a 07:00.
- **Archivo ficticio completo**, en lugar de la muestra pequeña de antes. Todo son casos inventados.

## Cómo lanzarlo

Desde un terminal:

```bash
make -C /Users/albercv/Documents/personalProjects/odin/.worktrees/ensayo instalacion-desplegar CONFIRMAR=DESPLEGAR TRABAJADOR=marta
```

La orden se lanza desde la worktree de integración porque tu copia principal (3cf5d1b) aún no la tiene. Trabaja sobre tu instalación: la pila `odin`, `odin/deploy/.env` y `odin/data/`. Hace lo siguiente:

1. Copia tu base, los originales, `deploy/.env` y la configuración de Claude Desktop en `odin/data/copias/` (D-056).
2. Pone tu copia principal en `integracion/instalacion` y añade a `deploy/.env` las tres direcciones que faltan:
   - la pública del panel;
   - la del lector OCR;
   - la del etiquetado.
3. Reconstruye, vacía la instalación y carga el archivo ficticio completo con sus expedientes.
4. Cuenta lo cargado y registra el conector de Claude Desktop con Marta. Después comprueba que la API, el panel y el MCP responden y que el MCP rechaza las peticiones sin clave.

**Si falla algo, vuelve sola a la copia,** commit incluido, y te dice dónde está el registro.

**Duración:** entre 15 y 20 minutos (unos 5 de construir y unos 10 de carga). Antes de lanzarla, el puente OCR tiene que estar en marcha. Si has reiniciado el Mac, arráncalo así:

```bash
cd /Users/albercv/Documents/personalProjects/OCR/.worktrees/puente-odin && HF_HOME=/Users/albercv/Documents/personalProjects/OCR/.cache/huggingface HF_HUB_OFFLINE=1 UV_OFFLINE=1 uv run python serve.py --quant 8
```

Sin puente, la orden avisa y los escaneos esperan hasta que llegue el lector.

## Qué tendrás

| Qué | Cuántos |
|---|---|
| Empresas | 2: Administraciones Ribera y Gestión Albar |
| Proyectos | 94: Ribera tiene 60 comunidades más «Procedimientos generales», abierto a todos; Albar, 33 comunidades |
| Personas | 10: administrador del grupo, administrador de empresa de Ribera y 8 trabajadores |
| Documentos | 1.354: 909 de Ribera y 445 de Albar; 18 sin proyecto |
| De ellos, casos | 341 |
| Sustituidos por una versión posterior | 59 |
| Escaneados en PDF leídos por el lector OCR | 30 (55 páginas) |
| Expedientes | 12, con 134 documentos (entre 8 y 17 cada uno; 118 subidos para ellos) |
| Fichas de conocimiento | 300: 220 aprobadas, 50 pendientes y 30 rechazadas |
| Temas | 8 por empresa |
| Contradicciones sembradas a propósito | 242 |

Las contradicciones no están en la base. Se cuentan en el archivo ficticio para probar que Odín enseña las dos versiones y no elige una. Son:

- 111 de anexos;
- 47 de versiones;
- 40 de casos distintos;
- 24 de datos desactualizados;
- 20 de fichas que contradicen a un documento.

## Cómo entrar

- **Panel:** http://127.0.0.1:8088, con el administrador del grupo. Las credenciales y las claves de cada trabajador están en `odin/data/datos-prueba/credenciales.local.md`. Ese fichero solo lo puede leer tu usuario y no se copia en ningún otro sitio.
- **Claude Desktop:** ciérralo del todo y vuelve a abrirlo. El conector «odin» entra como Marta.
- **Cambiar de trabajador:** tras el despliegue, tu copia principal ya tiene la orden. Lanza esto y reinicia Claude Desktop:

```bash
make -C /Users/albercv/Documents/personalProjects/odin mcp-claude-desktop TRABAJADOR=javier
```

| `TRABAJADOR` | Persona | Empresa | Proyectos que ve |
|---|---|---|---|
| `marta` | Marta Olivares Ríos | Ribera | 61 (todos los de Ribera) |
| `lorena`, `raul`, `daniela` | Lorena Cano, Raúl Soler, Daniela Arias | Ribera | 61 |
| `javier` | Javier Bermúdez Lara | Ribera | 11: sus 10 comunidades y «Procedimientos generales» |
| `ignacio`, `paula`, `gonzalo` | Ignacio Salas, Paula Núñez, Gonzalo Pardo | Albar | 33 |

## Preguntas para probar

Las respuestas esperadas salen de las claves del archivo ficticio. Odín debe citar documento y página. Si no hay fuente, debe decir «no consta». Lo que diga una ficha lo presenta como ficha, con su autor y su fecha.

### Con Marta (Ribera)

| Pregunta | Qué debe responder |
|---|---|
| ¿De cuánto fue la derrama para la fachada de Calle Olmo 14 y en cuántas cuotas se paga? | 48.000 € en 12 cuotas. |
| ¿Qué franquicia por daños por agua tiene ahora mismo la póliza de Los Almendros? | 150 €, de la póliza vigente. La antigua sale marcada como sustituida. |
| ¿Entre qué horas cortan el agua en Calle Roble 46 el 22 de enero de 2026? | Hay dos fuentes que no cuadran: 9:00 y 11:00. Debe enseñar las dos con su cita, o decir cuál cambia a la otra si un documento lo dice. |
| Una vecina de Calle Olmo 14 tiene moho negro en la pared del dormitorio. ¿Consta algún caso parecido y qué concluyó el técnico? | Un caso parecido. El técnico lo atribuyó a condensación por falta de ventilación. |
| La piscina de Los Almendros perdía agua en julio de 2025. ¿Qué se encontró y quién lo arregló? | Una microfisura, arreglada por Aquaservicios. |
| Haz una lista de todos los casos archivados de Calle Abeto 16 con su número. | Debe incluir 2024-461, 2025-344 y 2025-431. |
| ¿De qué color se va a pintar el portal de Calle Abeto 16? | Gris perla, según una ficha aprobada. |
| ¿Qué color le van a dar al portal de Residencial Los Naranjos 2? | No consta. |
| ¿Cubre la póliza de Los Almendros los daños por terremoto? | No consta. Si añade conocimiento general, debe ir aparte. |
| Agrupa por tema los casos de Calle Olmo 14. | 4 casos: 3 de «agua y humedades», 1 de «instalaciones» y 1 de «ruido». Un caso puede tener más de un tema. |
| ¿Qué documentos tiene el expte. 2024/461 de Calle Abeto 16? | Su expediente: informe del perito, presupuestos, factura, correos y parte de trabajo. |
| ¿Cuánto cuesta el presupuesto de Mantenimientos Delta S.L. en el expte. 2024/461 de Calle Abeto 16? | 900 €. |
| ¿Cuál es el importe vigente del presupuesto de Instalaciones Sierra S.L. en el exp. 344/25 de Calle Abeto 16? | 2.120 €, del presupuesto revisado. El anterior está sustituido. |

### Con Javier (Ribera, solo sus comunidades): aislamiento por proyecto

| Pregunta | Qué debe responder |
|---|---|
| Dime la franquicia del seguro de agua de Calle Olmo 47. | No consta. Calle Olmo 47 no es una de sus comunidades y no debe ver nada de ella. |
| ¿Cuántas referencias exigimos para dar de alta a un proveedor? | Dos referencias. Es un procedimiento general de Ribera y sí lo ve. |

### Con Ignacio (Albar): otra empresa

| Pregunta | Qué debe responder |
|---|---|
| ¿Quién preside ahora Avenida del Muelle 33? | Nuria Heredia Lagos, presidenta según la versión vigente. |
| ¿Qué empresa mantiene el ascensor de Avenida del Muelle 33? | Hay dos fuentes que no cuadran: Elevadores Bahía y Ascensores Norte. Debe enseñar las dos, o decir cuál cambia a la otra si un documento lo dice. |
| En Albar, ¿en cuántas horas como máximo hay que contestar un aviso urgente de un vecino? | 3 horas, del procedimiento vigente. |
| ¿Dónde se guardan las llaves de los cuartos técnicos y de las azoteas en la oficina de Ribera? | No consta. Es de Ribera y Albar no debe verlo. |

## Lo que todavía no está bien

- **Las fotos de los expedientes salen como «Fallido».** Son 20 fotos sin texto, y el lector OCR las marca como «no encontró texto» en vez de dejarlas como página sin texto.
  - Dentro del expediente no aparecen y cuentan como pendientes: el del caso 2024/176 de Calle Roble 46 dice «3 pendientes» y nunca se resuelven.
  - Lo revisa el CTO 2. Cuando esté corregido, se vuelve a lanzar la misma orden.
- **Escaneos recién cargados.** Al terminar la carga, algunos de los 30 PDF escaneados pueden seguir en cola. El lector los lee solo en los minutos siguientes, si el puente OCR está en marcha.
- **Etiquetado por tema.** Los casos ya llevan sus temas, puestos por el administrador de prueba. El etiquetado con IA del resto (unos 1.200 documentos) se hace de noche, de 22:00 a 07:00.
- **Solo en este Mac.** Los enlaces de subida y los del original usan http://127.0.0.1:8088, así que solo abren en este equipo. Instalar en un cliente con varios equipos necesitará publicar el panel en la red local. Está anotado para entonces.

## Volver atrás

- **Si la orden falla,** vuelve sola a la copia.
- **Si después quieres volver a lo que tenías antes,** la orden imprime la ruta de la copia. Con ella, este comando devuelve la base y los originales:

```bash
make -C /Users/albercv/Documents/personalProjects/odin instalacion-restaurar COPIA=<ruta de la copia .sql> CONFIRMAR=BORRAR
```

El código de la copia principal no vuelve con esa orden. Si también quieres volver al código anterior, pídeselo al CTO.
