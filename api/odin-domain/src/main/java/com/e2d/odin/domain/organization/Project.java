package com.e2d.odin.domain.organization;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;

public record Project(ProjectId id, CompanyId companyId, String name) {

    public Project withName(String newName) {
        return new Project(id, companyId, newName);
    }
}
