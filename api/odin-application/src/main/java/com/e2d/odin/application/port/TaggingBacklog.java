package com.e2d.odin.application.port;

import java.time.Instant;
import java.util.Optional;

/** Documentos de una empresa que esperan sus temas, y desde cuándo espera el más antiguo. */
public record TaggingBacklog(int pendingDocuments, Optional<Instant> oldestPendingSince) {
}
