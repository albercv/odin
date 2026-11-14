# Guía de la primera carga

Para el administrador de la empresa. Con esta guía subes el archivo de tu empresa a Odín tú mismo, desde el servidor de la oficina. Los documentos no salen de ese equipo y nadie de fuera los toca.

## Qué necesitas

- Tu correo y tu contraseña de administrador de Odín.
- Una terminal abierta en la carpeta de Odín, en el servidor de la oficina.
- La carpeta del archivo, con la ruta completa (por ejemplo `/srv/archivo-ruiz`).
- Odín en marcha. Si la orden dice que no responde, pide a quien lo instaló que lo arranque.
- Sitio en el disco del servidor: calcula unas tres veces lo que ocupa la carpeta.

## La orden

```
make primera-carga CARPETA=/srv/archivo-ruiz
```

Cambia la ruta por la de tu carpeta. Si tu usuario administra varias empresas, añade `ARGS="--empresa CODIGO"` con el código de la empresa que cargas.

Qué hace con la carpeta:

- Sube también las subcarpetas y guarda la ruta de cada fichero, que luego sirve de pista para ordenarlo.
- Se salta lo oculto (lo que empieza por punto), los accesos directos, y `.DS_Store`, `Thumbs.db` y `desktop.ini`.
- Se salta, avisándote, lo que pese más que el límite de subida (50 MB si nadie lo cambió).
- No toca ni borra nada de tu carpeta.

## Qué te pregunta

1. **Tu correo** y **tu contraseña**. La contraseña no se ve al escribirla y no se guarda en ningún sitio.
2. **El resumen:** la empresa, cuántos ficheros y cuánto ocupan, lo que se salta y los avisos.
3. **«¿Seguir? (s/n)»**. Escribe `s` para subir. Con `n` no se sube nada. Para no recibir esta pregunta, añade `ARGS="--si"`.

## Los avisos

- **Sin lector OCR:** los documentos escaneados esperan a que el lector esté instalado. Los de texto se leen igual.
- **Sin modelo local:** Odín no podrá proponer proyectos para los documentos que no se reconocen por su carpeta o su contenido. Los decides tú a mano.
- **Poco espacio en el disco:** haz sitio antes de seguir.
- **Hay una primera carga abierta sin cerrar:** la orden sigue con ella y no abre otra.
- **Ya hay una primera carga en curso** (leyéndose o esperando tu revisión): la orden no sube nada. Termínala en el panel, en «Primera carga».

## Si se corta

Corte de luz, red o terminal cerrada: vuelve a lanzar la misma orden. Odín pregunta qué ficheros ya tiene y sube solo los que faltan. Nada se duplica. Un fichero que descartaste en el panel no vuelve a entrar.

## Al terminar

La orden cuenta cuántos ficheros se han subido, cuántos ya estaban y cuántos se descartaron en esta carga, y cierra la subida. Odín empieza a leer los documentos. Entonces abre el panel de Odín y entra en **«Primera carga»**.

## Los cuatro pasos del panel

Nadie ve un documento hasta que tú lo confirmas.

1. **Lista de proyectos:** importa la lista que exporta tu programa de gestión (un nombre por línea) o confirma los que Odín encuentra en las carpetas. Sin una lista confirmada no se proponen proyectos.
2. **Proyectos:** Odín agrupa los documentos por proyecto y te enseña el motivo de cada uno. Confirma el grupo entero o decide documento a documento.
3. **Parejas:** escaneos y su copia en texto, y versiones que sustituyen a otras.
4. **Fichas y expedientes:** documentos de casos y enlaces a sus fichas.

En cada propuesta puedes confirmarla, corregir el proyecto, marcarla «de toda la empresa» o rechazarla.

- **«No lo sé»** deja el documento oculto y pendiente, sin plazo. Puedes volver a él cuando lo sepas.
- **Descartar** retira el documento. No vuelve a entrar si repites la orden.

## Cuánto tarda

Subir es rápido: va del disco a Odín dentro del mismo equipo. Lo que tarda es el lector OCR en los escaneos. Medido en un Mac M3, 89 páginas escaneadas tardaron 19 minutos (unos 13 segundos por página), el 14 de noviembre de 2026. Con otro equipo, el tiempo cambia. Los documentos de texto se leen mucho más deprisa. Puedes dejar la lectura en marcha y volver al panel más tarde.
