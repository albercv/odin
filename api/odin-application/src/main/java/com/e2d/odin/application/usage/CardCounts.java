package com.e2d.odin.application.usage;

import java.util.Optional;

/** Fichas aportadas en el mes. El desglose de decididas falta, con {@code withheld}, si hay pocos autores. */
public record CardCounts(int saved, int pending, Optional<CardDecisions> decisions, boolean withheld) {
}
