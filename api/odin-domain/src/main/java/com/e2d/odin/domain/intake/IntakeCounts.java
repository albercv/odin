package com.e2d.odin.domain.intake;

/** Lo que se sabe del lote por sus documentos: subidos, esperando a la lectura y aún pendientes de confirmar. */
public record IntakeCounts(int uploaded, int reading, int pending) {
}
