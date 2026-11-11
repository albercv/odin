package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostgresProjectMemberRepositoryIT extends AbstractPostgresIT {

    @Test
    void addIsIdempotentAndRemoveIsToo() {
        CompanyId a = newCompany();
        ProjectId obra = newProject(a, "Obra");
        UserId wanda = newUser(a);
        UserId pedro = newUser(a);

        projectMembers.add(obra, wanda);
        projectMembers.add(obra, wanda);
        projectMembers.add(obra, pedro);
        assertThat(projectMembers.listMembers(obra)).containsExactlyInAnyOrder(wanda, pedro);

        projectMembers.remove(obra, wanda);
        projectMembers.remove(obra, wanda);
        assertThat(projectMembers.listMembers(obra)).containsExactly(pedro);
    }

    @Test
    void projectsOfListsOnlyNonDeletedProjectsOfThatCompany() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        ProjectId obra = newProject(a, "Obra");
        ProjectId reforma = newProject(a, "Reforma");
        ProjectId deleted = newProject(a, "Vieja");
        ProjectId ofB = newProject(b, "De B");
        UserId wanda = newUser(a);
        for (ProjectId project : new ProjectId[] {obra, reforma, deleted, ofB}) {
            projectMembers.add(project, wanda);
        }
        orgs.deleteProject(a, deleted, Instant.now());

        assertThat(projectMembers.projectsOf(a, wanda)).containsExactlyInAnyOrder(obra, reforma);
        assertThat(projectMembers.projectsOf(b, wanda)).containsExactly(ofB);
        assertThat(projectMembers.projectsOf(a, newUser(a))).isEmpty();
    }

    @Test
    void projectsOfIncludesCompanyWideProjectsTheyBelongTo() {
        CompanyId a = newCompany();
        ProjectId obra = newProject(a, "Obra");
        orgs.saveProject(orgs.findProject(a, obra).orElseThrow().withRestricted(false));
        UserId wanda = newUser(a);
        projectMembers.add(obra, wanda);

        assertThat(projectMembers.projectsOf(a, wanda)).containsExactly(obra);
    }
}
