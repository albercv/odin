package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryTaggingRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.topic.TaggingStatus;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DescribeMeTest {

    @Test
    void returnsCompanyCapabilitiesAndProjects() {
        InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
        CompanyId id = new CompanyId(UUID.randomUUID());
        Company company = new Company(id, "Talleres Demo", "TD", EnumSet.of(Capability.SEARCH_KNOWLEDGE));
        Project project = new Project(new ProjectId(UUID.randomUUID()), id, "Obra 1", false);
        organizations.saveCompany(company);
        organizations.saveProject(project);
        Actor worker = Actors.worker(id);

        MeView me = Fixtures.describeMe(organizations, new InMemoryProjectMemberRepository(organizations)).execute(worker);

        assertThat(me.userId()).isEqualTo(worker.userId());
        assertThat(me.company().capabilities()).containsExactly(Capability.SEARCH_KNOWLEDGE);
        assertThat(me.projects()).containsExactly(project);
    }

    @Test
    void workerOfBSeesOnlyCompanyBAndItsProjects() {
        InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
        CompanyId a = Fixtures.company(organizations);
        CompanyId b = Fixtures.companyWith(organizations, EnumSet.of(Capability.SAVE_KNOWLEDGE_CARD));
        ProjectId projectOfA = Fixtures.companyWideProject(organizations, a);
        ProjectId projectOfB = Fixtures.companyWideProject(organizations, b);

        MeView me = Fixtures.describeMe(organizations, new InMemoryProjectMemberRepository(organizations))
                .execute(Actors.worker(b));

        assertThat(me.company().id()).isEqualTo(b);
        assertThat(me.company().name()).isNotEqualTo(organizations.findCompany(a).orElseThrow().name());
        assertThat(me.company().capabilities()).containsExactly(Capability.SAVE_KNOWLEDGE_CARD);
        assertThat(me.projects()).extracting(Project::id).containsExactly(projectOfB).doesNotContain(projectOfA);
    }

    @Test
    void workerSeesCompanyWideProjectsAndAssignedOnes() {
        InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
        InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(organizations);
        CompanyId company = Fixtures.company(organizations);
        ProjectId companyWide = Fixtures.companyWideProject(organizations, company);
        ProjectId assigned = Fixtures.project(organizations, company);
        ProjectId notAssigned = Fixtures.project(organizations, company);
        ProjectId assignedToOther = Fixtures.project(organizations, company);
        Actor worker = Actors.worker(company);
        members.add(assigned, worker.userId());
        members.add(assignedToOther, new UserId(UUID.randomUUID()));

        MeView me = Fixtures.describeMe(organizations, members).execute(worker);

        assertThat(me.projects()).extracting(Project::id).containsExactlyInAnyOrder(companyWide, assigned)
                .doesNotContain(notAssigned, assignedToOther);
    }

    @Test
    void newProjectIsInvisibleUntilAssigned() {
        InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
        InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(organizations);
        CompanyId company = Fixtures.company(organizations);
        Project created = new CreateProject(organizations, new SequenceIdGenerator())
                .execute(Actors.companyAdmin(company), company, "Obra");
        Actor worker = Actors.worker(company);
        DescribeMe describe = Fixtures.describeMe(organizations, members);

        assertThat(describe.execute(worker).projects()).isEmpty();

        members.add(created.id(), worker.userId());
        assertThat(describe.execute(worker).projects()).containsExactly(created);
    }

    @Test
    void deletedProjectIsNotListed() {
        InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
        InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(organizations);
        CompanyId company = Fixtures.company(organizations);
        ProjectId open = Fixtures.companyWideProject(organizations, company);
        Actor worker = Actors.worker(company);
        organizations.deleteProject(company, open, Fixtures.NOW);

        assertThat(Fixtures.describeMe(organizations, members).execute(worker).projects()).isEmpty();
    }

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryProjectMemberRepository projectMembers = new InMemoryProjectMemberRepository(orgs);
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);
    private final DescribeMe describe = new DescribeMe(orgs, projectMembers, topics, tagging);

    private Topic topic(CompanyId company, String name) {
        Topic topic = new Topic(new TopicId(UUID.randomUUID()), company, name, "Definición de " + name);
        topics.save(topic);
        return topic;
    }

    @Test
    void listsTopicsAndPendingCount() {
        CompanyId company = Fixtures.company(orgs);
        CompanyId other = Fixtures.company(orgs);
        Topic water = topic(company, "Agua");
        topic(other, "Ajeno");
        Document pending = Fixtures.readyDocument(docs, company, "uno");
        Document done = Fixtures.readyDocument(docs, company, "dos");
        Document failed = Fixtures.readyDocument(docs, company, "tres");
        tagging.seedRow(done, TaggingStatus.DONE);
        tagging.seedRow(failed, TaggingStatus.FAILED);

        MeView me = describe.execute(Actors.worker(company));

        assertThat(me.topics()).containsExactly(water);
        assertThat(me.taggingPending()).isEqualTo(2);
        assertThat(describe.execute(Actors.worker(other)).taggingPending()).isZero();
        assertThat(pending).isNotNull();
    }

    @Test
    void pendingCountIgnoresInvisibleProjects() {
        CompanyId company = Fixtures.company(orgs);
        topic(company, "Agua");
        ProjectId closed = Fixtures.project(orgs, company);
        ProjectId open = Fixtures.companyWideProject(orgs, company);
        Fixtures.documentIn(docs, company, closed, "secreto");
        Fixtures.documentIn(docs, company, open, "abierto");
        Fixtures.readyDocument(docs, company, "sin proyecto");
        Actor outsider = Actors.worker(company);
        Actor member = Actors.worker(company);
        projectMembers.add(closed, member.userId());

        assertThat(describe.execute(outsider).taggingPending()).isEqualTo(2);
        assertThat(describe.execute(member).taggingPending()).isEqualTo(3);
    }

    @Test
    void pendingCountIncludesDocumentsOfADeletedProjectTheWorkerSaw() {
        CompanyId company = Fixtures.company(orgs);
        topic(company, "Agua");
        ProjectId open = Fixtures.companyWideProject(orgs, company);
        Fixtures.documentIn(docs, company, open, "abierto");
        Fixtures.readyDocument(docs, company, "sin proyecto");
        orgs.deleteProject(company, open, Fixtures.NOW);

        MeView me = describe.execute(Actors.worker(company));

        assertThat(me.projects()).isEmpty();
        assertThat(me.taggingPending()).isEqualTo(2);
    }
}
