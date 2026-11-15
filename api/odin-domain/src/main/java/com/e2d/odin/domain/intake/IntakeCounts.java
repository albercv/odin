package com.e2d.odin.domain.intake;

/**
 * Lo que se sabe del lote por sus documentos: subidos, en lectura (incluidos los que esperan al lector OCR), aún
 * pendientes de confirmar, y de ellos los que esperan al lector OCR.
 */
public record IntakeCounts(int uploaded, int reading, int pending, int awaitingReader) {

    /** Sin lector OCR, lo que espera al lector no se está leyendo: espera sin plazo y no detiene el lote. */
    public IntakeCounts withoutReader() {
        return new IntakeCounts(uploaded, reading - awaitingReader, pending, awaitingReader);
    }
}
