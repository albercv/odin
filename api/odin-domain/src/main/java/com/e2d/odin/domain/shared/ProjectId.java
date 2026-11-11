package com.e2d.odin.domain.shared;

import java.util.UUID;

public record ProjectId(UUID value) {

    public static ProjectId of(String text) {
        return new ProjectId(UUID.fromString(text));
    }
}
