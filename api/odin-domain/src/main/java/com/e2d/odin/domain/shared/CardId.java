package com.e2d.odin.domain.shared;

import java.util.UUID;

public record CardId(UUID value) {

    public static CardId of(String text) {
        return new CardId(UUID.fromString(text));
    }
}
