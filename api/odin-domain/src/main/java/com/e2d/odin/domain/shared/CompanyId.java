package com.e2d.odin.domain.shared;

import java.util.UUID;

public record CompanyId(UUID value) {

    public static CompanyId of(String text) {
        return new CompanyId(UUID.fromString(text));
    }
}
