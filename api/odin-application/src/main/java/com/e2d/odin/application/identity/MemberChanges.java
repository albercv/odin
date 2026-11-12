package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Role;
import java.util.Optional;
import java.util.Set;

public record MemberChanges(Optional<Set<Role>> roles, Optional<Boolean> active, Optional<String> password) {
}
