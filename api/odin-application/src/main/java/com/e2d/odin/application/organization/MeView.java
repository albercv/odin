package com.e2d.odin.application.organization;

import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;

public record MeView(UserId userId, String displayName, Role role, Company company, List<Project> projects) {
}
