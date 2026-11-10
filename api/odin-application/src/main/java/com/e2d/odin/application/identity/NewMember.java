package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Role;
import java.util.Optional;
import java.util.Set;

public record NewMember(String displayName, Optional<String> email, Set<Role> roles, Optional<String> password) {
}
