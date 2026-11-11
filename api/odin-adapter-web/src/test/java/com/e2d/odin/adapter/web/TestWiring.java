package com.e2d.odin.adapter.web;

import com.e2d.odin.application.document.GetDocument;
import com.e2d.odin.application.document.ListCompanyDocuments;
import com.e2d.odin.application.document.RetryDocument;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryKnowledgeCardRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryQuestionLog;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.ScriptedKnowledgeSearch;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequencePersonCodeGenerator;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.identity.AddMember;
import com.e2d.odin.application.identity.ChangePassword;
import com.e2d.odin.application.identity.DeleteUser;
import com.e2d.odin.application.identity.DescribeAdmin;
import com.e2d.odin.application.identity.IssueAccessKey;
import com.e2d.odin.application.identity.ListAccessKeys;
import com.e2d.odin.application.identity.ListMembers;
import com.e2d.odin.application.identity.LogInAdmin;
import com.e2d.odin.application.identity.LogOutAdmin;
import com.e2d.odin.application.identity.RecoverGroupAdmin;
import com.e2d.odin.application.identity.RemoveMember;
import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.application.identity.RevokeAccessKey;
import com.e2d.odin.application.identity.SetUpInstallation;
import com.e2d.odin.application.identity.StoredAdminSessions;
import com.e2d.odin.application.identity.UpdateMember;
import com.e2d.odin.application.identity.UpdateUser;
import com.e2d.odin.application.knowledge.ListCardsForReview;
import com.e2d.odin.application.knowledge.ReviewCard;
import com.e2d.odin.application.knowledge.SaveKnowledgeCard;
import com.e2d.odin.application.organization.AddProjectMember;
import com.e2d.odin.application.organization.CreateCompany;
import com.e2d.odin.application.organization.CreateProject;
import com.e2d.odin.application.organization.DeleteProject;
import com.e2d.odin.application.organization.DescribeMe;
import com.e2d.odin.application.organization.ListCompanies;
import com.e2d.odin.application.organization.ListMemberProjects;
import com.e2d.odin.application.organization.ListProjectMembers;
import com.e2d.odin.application.organization.ListProjects;
import com.e2d.odin.application.organization.RemoveProjectMember;
import com.e2d.odin.application.organization.SetCompanyCapabilities;
import com.e2d.odin.application.organization.UpdateCompany;
import com.e2d.odin.application.organization.UpdateProject;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.search.ListDocuments;
import com.e2d.odin.application.search.ReadPage;
import com.e2d.odin.application.search.SearchKnowledge;
import com.e2d.odin.application.search.VerifyCitation;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.document.DocumentFormat;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestWiring {

    static final long MAX_UPLOAD = 1000;

    final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    final InMemoryProjectMemberRepository projectMembers = new InMemoryProjectMemberRepository(orgs);
    final InMemoryUserRepository users = new InMemoryUserRepository();
    final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    final InMemoryKnowledgeCardRepository cards = new InMemoryKnowledgeCardRepository();
    final ScriptedKnowledgeSearch search = new ScriptedKnowledgeSearch();
    final StubFormatDetector detector = new StubFormatDetector(DocumentFormat.PDF);
    final MutableClock clock = new MutableClock();
    final SequenceIdGenerator ids = new SequenceIdGenerator();
    final SequencePersonCodeGenerator personCodes = new SequencePersonCodeGenerator();
    final SequenceTokenGenerator tokens = new SequenceTokenGenerator();
    final FakeSecretHasher secrets = new FakeSecretHasher();
    final FakePasswordHasher passwords = new FakePasswordHasher();
    final InMemoryOriginalFileStore files = new InMemoryOriginalFileStore();

    @Bean Clock clock() {
        return clock;
    }

    @Bean AdminSessions adminSessions() {
        return new StoredAdminSessions(sessions, secrets, tokens, new SessionSettings(), clock);
    }

    @Bean ResolveActor resolveActor() {
        return new ResolveActor(keys, users, memberships, adminSessions(), secrets);
    }

    @Bean SetUpInstallation setUpInstallation() {
        return new SetUpInstallation(orgs, users, passwords, ids, personCodes, new InstallationSettings("setup-secret"));
    }

    @Bean LogInAdmin logInAdmin() {
        return new LogInAdmin(users, memberships, passwords, adminSessions());
    }

    @Bean LogOutAdmin logOutAdmin() {
        return new LogOutAdmin(adminSessions());
    }

    @Bean CreateCompany createCompany() {
        return new CreateCompany(orgs, ids);
    }

    @Bean ListCompanies listCompanies() {
        return new ListCompanies(orgs);
    }

    @Bean UpdateCompany updateCompany() {
        return new UpdateCompany(orgs);
    }

    @Bean SetCompanyCapabilities setCompanyCapabilities() {
        return new SetCompanyCapabilities(orgs);
    }

    @Bean CreateProject createProject() {
        return new CreateProject(orgs, ids);
    }

    @Bean ListProjects listProjects() {
        return new ListProjects(orgs);
    }

    @Bean UpdateProject updateProject() {
        return new UpdateProject(orgs);
    }

    @Bean AddProjectMember addProjectMember() {
        return new AddProjectMember(orgs, memberships, projectMembers);
    }

    @Bean RemoveProjectMember removeProjectMember() {
        return new RemoveProjectMember(orgs, projectMembers);
    }

    @Bean ListProjectMembers listProjectMembers() {
        return new ListProjectMembers(orgs, users, projectMembers);
    }

    @Bean ListMemberProjects listMemberProjects() {
        return new ListMemberProjects(orgs, memberships, projectMembers);
    }

    @Bean DeleteProject deleteProject() {
        return new DeleteProject(orgs, clock);
    }

    @Bean AddMember addMember() {
        return new AddMember(users, memberships, orgs, projectMembers, passwords, ids, personCodes);
    }

    @Bean ListMembers listMembers() {
        return new ListMembers(users, memberships, orgs);
    }

    @Bean UpdateMember updateMember() {
        return new UpdateMember(users, memberships, orgs, passwords, adminSessions());
    }

    @Bean RemoveMember removeMember() {
        return new RemoveMember(memberships, keys, projectMembers, adminSessions(), clock);
    }

    @Bean DescribeAdmin describeAdmin() {
        return new DescribeAdmin(users);
    }

    @Bean UpdateUser updateUser() {
        return new UpdateUser(users, memberships, adminSessions());
    }

    @Bean ChangePassword changePassword() {
        return new ChangePassword(users, memberships, passwords, adminSessions());
    }

    @Bean RecoverGroupAdmin recoverGroupAdmin() {
        return new RecoverGroupAdmin(users, passwords, adminSessions(), new InstallationSettings("setup-secret"));
    }

    @Bean DeleteUser deleteUser() {
        return new DeleteUser(users, memberships, projectMembers, adminSessions(), clock);
    }

    @Bean IssueAccessKey issueAccessKey() {
        return new IssueAccessKey(keys, users, memberships, secrets, tokens, ids, clock);
    }

    @Bean ListAccessKeys listAccessKeys() {
        return new ListAccessKeys(keys, memberships);
    }

    @Bean RevokeAccessKey revokeAccessKey() {
        return new RevokeAccessKey(keys, users, clock);
    }

    @Bean UploadDocument uploadDocument() {
        return new UploadDocument(docs, orgs, files, detector, new UploadSettings(MAX_UPLOAD), ids, clock);
    }

    @Bean ListCompanyDocuments listCompanyDocuments() {
        return new ListCompanyDocuments(docs);
    }

    @Bean GetDocument getDocument() {
        return new GetDocument(docs);
    }

    @Bean RetryDocument retryDocument() {
        return new RetryDocument(docs);
    }

    @Bean ListCardsForReview listCardsForReview() {
        return new ListCardsForReview(cards);
    }

    @Bean ReviewCard reviewCard() {
        return new ReviewCard(cards, clock);
    }

    @Bean SaveKnowledgeCard saveKnowledgeCard() {
        return new SaveKnowledgeCard(orgs, projectMembers, cards, ids, clock);
    }

    @Bean DescribeMe describeMe() {
        return new DescribeMe(orgs, projectMembers);
    }

    @Bean SearchKnowledge searchKnowledge() {
        return new SearchKnowledge(orgs, projectMembers, search, new InMemoryQuestionLog(), ids, clock);
    }

    @Bean ListDocuments listDocuments() {
        return new ListDocuments(orgs, projectMembers, docs);
    }

    @Bean ReadPage readPage() {
        return new ReadPage(orgs, projectMembers, docs);
    }

    @Bean VerifyCitation verifyCitation() {
        return new VerifyCitation(orgs, projectMembers, docs);
    }
}
