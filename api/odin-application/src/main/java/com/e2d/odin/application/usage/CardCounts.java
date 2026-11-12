package com.e2d.odin.application.usage;

/** Fichas aportadas en el mes y cómo van. Solo por empresa: por persona nunca se cuentan las decididas. */
public record CardCounts(int saved, int approved, int corrected, int rejected, int pending) {
}
