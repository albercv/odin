package com.e2d.odin.domain.shared;

import java.util.UUID;

public record UserId(UUID value) {

    public static UserId of(String text) {
        return new UserId(UUID.fromString(text));
    }
}
