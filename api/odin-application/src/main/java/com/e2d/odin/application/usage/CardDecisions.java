package com.e2d.odin.application.usage;

/** Cómo se decidieron las fichas aportadas en el mes. Solo por empresa y solo con suficientes autores. */
public record CardDecisions(int approved, int corrected, int rejected) {
}
