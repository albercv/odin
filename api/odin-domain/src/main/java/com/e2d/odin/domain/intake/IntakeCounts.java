package com.e2d.odin.domain.intake;

/** Lo que se sabe del lote por sus documentos: cuántos hay subidos y cuántos esperan a la lectura. */
public record IntakeCounts(int uploaded, int reading) {
}
