package com.e2d.odin.application.identity;

import java.util.Optional;

public record UserChanges(Optional<String> displayName, Optional<String> email, Optional<Boolean> active) {
}
