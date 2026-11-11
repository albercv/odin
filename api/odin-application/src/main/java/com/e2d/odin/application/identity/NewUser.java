package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Role;
import java.util.Optional;

public record NewUser(String displayName, Optional<String> email, Role role, Optional<String> password) {
}
