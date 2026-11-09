package com.e2d.odin.domain.shared;

import java.util.UUID;

public record AccessKeyId(UUID value) {

    public static AccessKeyId of(String text) {
        return new AccessKeyId(UUID.fromString(text));
    }
}
