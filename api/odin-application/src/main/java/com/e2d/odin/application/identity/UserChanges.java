package com.e2d.odin.application.identity;

import java.util.Optional;

public record UserChanges(Optional<String> firstName, Optional<String> lastName, Optional<String> email,
        Optional<Boolean> active) {
}
