package com.e2d.odin.application.organization;

import java.util.Optional;

public record CompanyChanges(Optional<String> name, Optional<String> code) {
}
