package com.e2d.odin.domain.audit;

import java.time.Instant;

/** El último eslabón de la cadena: el sello que el administrador guarda fuera de la base. */
public record ChainHead(long id, String hash, Instant at) {
}
