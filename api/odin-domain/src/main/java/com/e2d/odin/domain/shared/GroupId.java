package com.e2d.odin.domain.shared;

import java.util.UUID;

public record GroupId(UUID value) {

    public static GroupId of(String text) {
        return new GroupId(UUID.fromString(text));
    }
}
