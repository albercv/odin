package com.e2d.odin.application.organization;

import java.util.Optional;

public record ProjectChanges(Optional<String> name, Optional<Boolean> restricted) {
}
