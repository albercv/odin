package com.e2d.odin.application.usage;

/** Llamadas y bytes de respuesta de una herramienta en un mes. Nunca por persona. */
public record DeliveryTotal(WorkerTool tool, long calls, long bytes) {
}
