package com.e2d.odin.application.organization;

import com.e2d.odin.domain.organization.ProjectLabel;
import java.util.Optional;

public record CompanyChanges(Optional<String> name, Optional<String> code, Optional<ProjectLabel> projectLabel) {

    public CompanyChanges(Optional<String> name, Optional<String> code) {
        this(name, code, Optional.empty());
    }
}
