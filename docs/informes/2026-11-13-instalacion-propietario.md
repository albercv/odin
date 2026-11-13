# Tu instalación de Odín: despliegue conjunto del 13 de noviembre de 2026

**Estado:** desplegado el 13 de noviembre a las 09:41, con salida 0. Lo lanzó el CEO con tu permiso, porque el sistema de permisos impide al CTO lanzar despliegues en tu instalación.
- **Código:** `integracion/instalacion`, commit cf58b72.
- **Comprobaciones posteriores, todas correctas:**
  - el panel, la API y el conector responden;
  - Marta tiene 15 herramientas;
  - el aislamiento se cumple: Javier no ve Calle Olmo 47 e Ignacio no ve nada de Ribera;
  - los expedientes se ven y el enlace al original abre.

**Sobre las cifras:** contadas en tu base con `make datos-prueba-recuento` después del despliegue. Coinciden con las del ensayo previo.

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

## Cómo se desplegó

La orden única (`make instalacion-desplegar CONFIRMAR=DESPLEGAR TRABAJADOR=marta`) hizo esto:

1. Copió tu base, los originales, `deploy/.env` y la configuración de Claude Desktop en `odin/data/copias/` (D-056). La base quedó en `odin-v23-antes-despliegue-conjunto-2026-11-13-093931.sql`.
2. Puso tu copia principal en `integracion/instalacion` y añadió a `deploy/.env` la dirección pública del panel, la del lector OCR y la del etiquetado.
3. Vació la instalación y cargó el archivo ficticio completo con sus expedientes. La carga tardó 39 s; la orden entera, unos 1,5 minutos.
4. Contó lo cargado, registró el conector con Marta y comprobó que todo responde.

**Después del despliegue:**
- **09:45.** El lector OCR terminó los 30 PDF escaneados (55 páginas).
- **09:46.** Se abrió la ventana de etiquetado para todo el día (`ODIN_TAGGER_WINDOW=always` en `deploy/.env`, con copia previa), para que puedas probar hoy las listas por tema.

## Próximas actualizaciones: sin vaciar

Desde que lo estás probando, lo que hagas en Odín (fichas, subidas, expedientes, retiradas) no se puede perder. Por eso las próximas actualizaciones **no vacían** la instalación. Usarán esta orden:

```bash
make -C /Users/albercv/Documents/personalProjects/odin/.worktrees/ensayo instalacion-actualizar CONFIRMAR=ACTUALIZAR
```

- **Lo que hace:**
  - copia lo mismo que el despliegue;
  - cambia solo el código y conserva tu base y tus originales;
  - reintenta los documentos fallidos;
  - renueva el conector sin cambiar el trabajador que tengas puesto.
- **Si algo falla,** vuelve a la copia.
- **Vaciar otra vez** exige tu autorización expresa en cada ocasión.

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
| Páginas leídas en total | 1.359: 1.304 de texto y 55 por OCR |
| Fotos de expediente sin leer («Fallido») | 20 (ver «Lo que todavía no está bien») |
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
- **Cambiar de trabajador:** cierra Claude Desktop del todo (Cmd+Q), lanza esto desde Terminal y vuelve a abrirlo. Con Claude Desktop abierto, puede deshacer el cambio al guardar sus preferencias:

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

## Actualización de las 13:05 (sin vaciar)

- **Qué entró:** el arreglo de las fotos y el conector con textos neutros (D-084), en el código e227925, con `make instalacion-actualizar CONFIRMAR=ACTUALIZAR`.
  - La lanzó el CEO con tu permiso; terminó con salida 0 a las 13:06.
  - Copia previa: `odin-v24-antes-despliegue-actualizar-2026-11-13-130521.sql`.
- **Tu base no cambió:** antes y después hay 300 fichas, 134 documentos en expedientes, 3.426 asignaciones de tema, 1.354 documentos, 10 personas y 94 proyectos.
- **Las 20 fotos ya están en «Listo»,** con su página sin texto. Solo se añadieron esas 20 páginas.
  - El expediente 2024/176 de Calle Roble 46 lista sus 3 fotos, sin pendientes ni ilegibles, y el original de cada foto se abre.
- **Lo que tienes que hacer tú:** tu Claude Desktop volvió a su configuración anterior a las 13:01, antes de la actualización.
  - Ahora apunta al conector viejo y usa una clave que ya no existe, así que el conector «odin» no te responde.
  - Lo más probable es que lo causara el propio Claude Desktop, que no se había reiniciado tras el despliegue y guardó lo que tenía en memoria.
  - Para arreglarlo: ciérralo del todo (Cmd+Q), lanza en Terminal `make -C /Users/albercv/Documents/personalProjects/odin mcp-claude-desktop TRABAJADOR=marta` y vuelve a abrirlo.

## Lo que todavía no está bien

- **Corregido a las 13:05: las fotos de los expedientes salían como «Fallido».** Son 20 fotos sin texto, y el lector OCR las marca como «no encontró texto» en vez de dejarlas como página sin texto.
  - Dentro del expediente no aparecen y cuentan como pendientes: el del caso 2024/176 de Calle Roble 46 dice «3 pendientes» y nunca se resuelven.
  - El CTO 2 lo está corrigiendo, sin migración: una foto sin texto pasará a «Listo» con su página marcada como sin texto.
  - Llegará con la orden de actualizar sin vaciar, que reintenta los fallidos sola. El CEO te avisará antes de lanzarla.
- **Etiquetado por tema en curso.**
  - Los casos ya llevan sus temas, puestos por el administrador de prueba.
  - La IA etiqueta el resto (1.327 documentos pendientes a las 09:46), a unos 22 por minuto: debería terminar hacia las 10:50.
  - Mientras tanto, las listas por tema pueden salir incompletas.
  - La ventana queda abierta todo el día. Para volver a la nocturna, se quita la línea `ODIN_TAGGER_WINDOW=always` de `deploy/.env` y se reinicia la API.
- **Solo en este Mac.** Los enlaces de subida y los del original usan http://127.0.0.1:8088, así que solo abren en este equipo. Instalar en un cliente con varios equipos necesitará publicar el panel en la red local. Está anotado para entonces.

## Volver atrás

- **Si la orden falla,** vuelve sola a la copia.
- **Si quieres volver a lo que tenías antes del despliegue,** este comando devuelve la base y los originales. Perderías lo que hayas hecho desde las 09:41, aunque antes de restaurar se guarda una copia de ello:

```bash
make -C /Users/albercv/Documents/personalProjects/odin instalacion-restaurar COPIA=data/copias/odin-v23-antes-despliegue-conjunto-2026-11-13-093931.sql CONFIRMAR=BORRAR
```

El código de la copia principal no vuelve con esa orden. Si también quieres volver al código anterior, pídeselo al CTO.
