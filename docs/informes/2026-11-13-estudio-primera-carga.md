# Odín · Estudio de la primera carga

- Fecha: 2026-11-13. Autor: CTO. Encargo del propietario, por el CEO («Primera carga», en el apartado «Pendiente» de `decisiones.md`).
- **Es un estudio:** no construye nada. Las medidas están en `experimentos/2026-11-13-primera-carga/`, sobre el archivo ficticio y solo con modelos locales.
- **Rama:** `feature/estudio-primera-carga`, que sale de `develop`.
- **Lo que necesita una decisión del propietario** va aparte, en el apartado 8.

> **Estado del trabajo:** borrador. Las cifras marcadas «pendiente» se completan con las partes A y B del experimento. Si este estudio queda a medias, el apartado 10 dice dónde está.

## 1. Qué se pide y qué hay hoy

**Petición del propietario:**
- al instalar Odín en un cliente nuevo, procesar todo su archivo de una vez y de forma fácil;
- el cliente deja sus documentos en una carpeta, y los escaneados llegan a la misma carpeta a través del OCR;
- Odín propone a qué proyecto y a qué caso pertenece cada documento y cómo se relacionan;
- evita duplicados.

**Lo que hay hoy en Odín:**

| Pieza | Hoy |
|---|---|
| Subida | El administrador sube desde el panel, varios ficheros a la vez, eligiendo él el proyecto y si es un caso |
| Duplicado exacto | Ya se evita: el mismo contenido (SHA-256) en la misma empresa no entra dos veces, y la API responde `duplicate: true` |
| Escaneos | El lector Unlimited-OCR 2, en local por el puente; sin lector, esperan |
| Temas | El etiquetado local (Ollama), de noche (D-031) |
| Casos | Un documento se marca como caso a mano. El expediente (qué documentos forman un caso) lo diseña el CTO 2 en el encargo 13 |
| Versiones | El administrador marca a mano qué documento sustituye a otro (D-050) |
| Proyecto | Lo elige siempre una persona. No hay propuestas |

## 2. Recorrido propuesto, común a todas las opciones

1. **Entrega:** el cliente deja su archivo en una carpeta. Cómo llega a Odín está en el apartado 6.
2. **Lectura:** el texto digital se lee directamente y los escaneos pasan por el OCR local. Todo queda **«en revisión»**: el documento existe en Odín, pero sin proyecto confirmado, y ningún trabajador lo ve.
3. **Duplicados y versiones** (apartado 5):
   - la copia exacta no entra;
   - el escaneo de un documento digital se propone como «el mismo documento»;
   - una versión nueva se propone como sustitución (D-050).
4. **Propuestas de clasificación:**
   - el proyecto, o «de toda la empresa»;
   - el tipo de documento;
   - si es una ficha de caso y su número;
   - a qué expedientes podría pertenecer (encargo 13).
   - Cada propuesta lleva su confianza y su motivo, es decir, qué texto la justifica.
5. **Revisión por una persona** (apartado 4). Al confirmarla, el documento pasa a su proyecto y lo ven quienes ven ese proyecto. Los enlaces a expedientes se crean con el mismo caso de uso del encargo 13, con esa persona como autora.
6. **Después:** el etiquetado por tema (de noche) y la búsqueda, como cualquier documento.

Nada se asigna por suposición: hasta que alguien confirma, la propuesta no cambia lo que ve nadie.

## 3. Opciones

| | A · Reglas con la lista del cliente | B · A y un modelo local para lo que queda | C · A y una IA externa con extractos para lo que queda |
|---|---|---|---|
| Cómo propone el proyecto | Busca los nombres de la lista de proyectos en el texto, con sus variantes | Igual que A; lo ambiguo o sin proyecto lo decide `qwen3.5:9b` en el equipo de la oficina | Igual que A; lo que queda lo decide una IA externa con los primeros caracteres del documento |
| Qué sale del equipo | Nada | Nada | Extractos de documentos enteros sin pregunta de un trabajador. Choca con D-016 y D-030: pregunta de negocio |
| Acierto | pendiente (parte B) | pendiente (parte B) | Sin medir: no se usa API de pago. Se mediría con la suscripción si el propietario lo pide |
| Tiempo, 825 documentos | pendiente | pendiente | — |
| Documentos para una persona | pendiente | pendiente | — |
| Coste de construir | El más bajo | A más una llamada al Ollama que ya usa el etiquetado | A más un adaptador externo y su control de lo que sale |

**Recomendación:** pendiente de las cifras. A priori, B: el etiquetado ya usa el Ollama de la oficina y nada sale del equipo.

## 4. Quién confirma y cómo, con poco esfuerzo

- **Quién:** el administrador de la empresa, desde el panel. El trabajador no entra en el panel (D-033).
- **Cómo:** una bandeja «Primera carga» con las propuestas agrupadas por proyecto propuesto y por confianza.
  - **Confianza alta**, cuando el nombre del proyecto aparece y no hay otro candidato: se confirma un grupo entero con un clic. Antes se ve la lista, con el título y la frase que lo justifica.
  - **Confianza baja o ambigua:** una a una, con los candidatos y el texto que los apoya. La persona elige el proyecto, «de toda la empresa» o «no lo sé», y lo que no sabe se queda en revisión.
  - **Duplicados y versiones:** se confirman en la misma bandeja, en pareja.
  - **Expedientes:** el sistema propone los enlaces con el número de caso o el identificador que los une, y se confirman por caso.
- **Esfuerzo esperado:** pendiente de las cifras: cuántos documentos van a confirmación por grupo y cuántos una a uno.

## 5. Duplicados y versiones

| Caso | Qué hace Odín | Quién decide |
|---|---|---|
| Copia exacta (mismo contenido) | No entra. Ya es así hoy | Nadie: es seguro |
| Escaneo de un documento digital | Propone «el mismo documento» | El administrador, en la bandeja |
| Versión nueva (póliza renovada, acta posterior que cambia otra) | Propone «sustituye a» (D-050). Nunca lo trata como duplicado | El administrador |
| Plantilla parecida de otra comunidad | Nada: no es un duplicado | — |

Acierto y falsos positivos: pendiente (parte A).

## 6. Cómo llega la carpeta a Odín (D-076)

- **La página de subida del encargo 13** (D-076): enlace de 15 minutos y solo desde la red local.
  - **Sirve para:** lotes medianos y los documentos que llegan después.
  - **Para el archivo entero:**
    - subir miles de ficheros desde el navegador de una vez depende de que la página aguante cortes y reanude;
    - y si la subida dura más de 15 minutos, el enlace tiene que valer para empezar la sesión de subida, no para cada fichero.
- **Alternativa: una orden de importación en el equipo de la oficina,** que lanza quien instala.
  - Lee la carpeta directamente del disco del servidor, sin pasar por la red.
  - Se puede reanudar: lo ya subido se reconoce por su SHA-256.
  - Es lo que se haría en la instalación.
- **Recomendación:** la orden de importación para la primera carga y la página para lo de después. Si el propietario quiere que el propio cliente haga la primera carga sin el instalador, la página necesita subida reanudable de carpetas: es la decisión D5.

## 7. Medidas

Pendiente: partes A y B del experimento, con el equipo (Apple M3 Max, 36 GB), los modelos y el número de ejecuciones.

## 8. Lo que decide el propietario

| # | Decisión | Recomendación del CTO |
|---|---|---|
| D1 | ¿Los documentos sin confirmar se ocultan a los trabajadores? | Sí. Un documento mal asignado a otro proyecto sería una fuga (D-047) |
| D2 | ¿Confirmación automática por encima de una confianza, o siempre una persona? | Siempre una persona, por grupos. Lo automático solo si las medidas dan cero errores en confianza alta y el propietario lo acepta |
| D3 | El escaneo de un documento digital: ¿se guardan los dos o solo uno? | Guardar el digital como principal y el escaneo enlazado a él, porque puede llevar firma o sello. Lo decide el administrador en la bandeja |
| D4 | Si lo local no basta: ¿extractos a la IA externa para clasificar? | No mientras lo local dé la talla (D-016, D-030). Depende de las cifras |
| D5 | ¿Quién hace la primera carga: el instalador con una orden, o el cliente desde la página? | El instalador, con la orden. La página, para lo de después |
| D6 | ¿De dónde sale la lista de proyectos: del programa de gestión del cliente o la descubre Odín y la confirma el administrador? | La del programa de gestión, si se puede exportar (entrevistas). Si no, la descubre Odín y la confirma el administrador |

## 9. Dependencias

- **El enlace entre documento y expediente es el del encargo 13** (CTO 2).
  - La primera carga no crea otro: guarda sus propuestas aparte, y al confirmarlas llama a su caso de uso de añadir un documento al expediente, con la persona que confirma como actora.
  - Pedido al CTO 2 el 2026-11-13.
  - Medir el enlace de expedientes necesita el archivo ampliado con expedientes de ese encargo, porque el archivo de hoy no cita números de caso fuera de la ficha.
- **Las entrevistas:**
  - cómo guardan hoy los despachos su archivo (carpetas o no);
  - si su programa de gestión exporta la lista de comunidades;
  - cuántos documentos tiene un expediente.

## 10. Dónde está el trabajo

- **Hecho:**
  - diseño del experimento (`experimentos/2026-11-13-primera-carga/DISENO.md`);
  - este borrador;
  - aviso al CTO 2.
- **En marcha:** parte A (carpeta revuelta, escaneos, OCR y duplicados) y parte B (clasificación con reglas y modelo local), cada una en su subcarpeta.
- **Falta:**
  - completar los apartados 3, 4, 5 y 7 con las cifras;
  - la revisión;
  - el aviso al CEO.
