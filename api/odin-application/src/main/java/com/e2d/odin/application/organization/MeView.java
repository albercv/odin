package com.e2d.odin.application.organization;

import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.Topic;
import java.util.List;

/** {@code taggingPending}: documentos visibles para quien pregunta que aún no tienen sus temas al día. */
public record MeView(UserId userId, String displayName, Role role, Company company, List<Project> projects,
        List<Topic> topics, int taggingPending) {
}
