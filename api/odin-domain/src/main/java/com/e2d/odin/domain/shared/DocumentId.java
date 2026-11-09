package com.e2d.odin.domain.shared;

import java.util.UUID;

public record DocumentId(UUID value) {

    public static DocumentId of(String text) {
        return new DocumentId(UUID.fromString(text));
    }
}
