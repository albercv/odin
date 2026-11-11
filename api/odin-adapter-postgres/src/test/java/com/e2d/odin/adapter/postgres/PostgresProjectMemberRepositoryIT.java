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

        projectMembers.add(a, obra, wanda);
        projectMembers.add(a, obra, wanda);
        projectMembers.add(a, obra, pedro);
        assertThat(projectMembers.listMembers(a, obra)).containsExactlyInAnyOrder(wanda, pedro);

        projectMembers.remove(a, obra, wanda);
        projectMembers.remove(a, obra, wanda);
        assertThat(projectMembers.listMembers(a, obra)).containsExactly(pedro);
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
        projectMembers.add(a, obra, wanda);
        projectMembers.add(a, reforma, wanda);
        projectMembers.add(a, deleted, wanda);
        projectMembers.add(b, ofB, wanda);
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
        projectMembers.add(a, obra, wanda);

        assertThat(projectMembers.projectsOf(a, wanda)).containsExactly(obra);
    }

    @Test
    void callsWithAnotherCompanyIdNeitherAddRemoveNorList() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        ProjectId obra = newProject(a, "Obra");
        UserId wanda = newUser(a);
        UserId pedro = newUser(a);
        projectMembers.add(a, obra, wanda);

        projectMembers.add(b, obra, pedro);
        projectMembers.remove(b, obra, wanda);
        projectMembers.removeAllOf(b, wanda);

        assertThat(projectMembers.listMembers(b, obra)).isEmpty();
        assertThat(projectMembers.listMembers(a, obra)).containsExactly(wanda);
    }

    @Test
    void removeAllOfDropsOnlyThatCompanysProjectsOfThePerson() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        ProjectId inA = newProject(a, "Obra");
        ProjectId inB = newProject(b, "Otra");
        UserId wanda = newUser(a);
        projectMembers.add(a, inA, wanda);
        projectMembers.add(b, inB, wanda);

        projectMembers.removeAllOf(a, wanda);

        assertThat(projectMembers.projectsOf(a, wanda)).isEmpty();
        assertThat(projectMembers.projectsOf(b, wanda)).containsExactly(inB);
    }

    @Test
    void deletedProjectsOfListsOnlyDeletedOnesOfThatCompany() {
        CompanyId a = newCompany();
        ProjectId live = newProject(a, "Viva");
        ProjectId gone = newProject(a, "Vieja");
        UserId wanda = newUser(a);
        projectMembers.add(a, live, wanda);
        projectMembers.add(a, gone, wanda);
        orgs.deleteProject(a, gone, Instant.now());

        assertThat(projectMembers.deletedProjectsOf(a, wanda)).containsExactly(gone);
        assertThat(projectMembers.deletedProjectsOf(newCompany(), wanda)).isEmpty();
        assertThat(orgs.listDeletedProjects(a)).extracting(p -> p.id()).containsExactly(gone);
    }
}
