package com.e2d.odin.application.port;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Set;

public interface ProjectMemberRepository {

    /** Idempotente: añadir a quien ya es miembro no cambia nada. */
    void add(ProjectId projectId, UserId userId);

    void remove(ProjectId projectId, UserId userId);

    List<UserId> listMembers(ProjectId projectId);

    /** Los proyectos restringidos, no borrados, de la empresa de los que la persona es miembro. */
    Set<ProjectId> restrictedProjectsOf(CompanyId companyId, UserId userId);
}
