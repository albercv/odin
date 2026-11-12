package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;
import java.util.Set;

public record NewMember(String firstName, String lastName, Optional<String> email, Set<Role> roles,
        Optional<String> password, Set<ProjectId> projectIds) {
}
