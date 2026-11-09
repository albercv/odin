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
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.InMemoryQuestionLog;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.ScriptedKnowledgeSearch;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.identity.DeactivateUser;
import com.e2d.odin.application.identity.IssueAccessKey;
import com.e2d.odin.application.identity.ListAccessKeys;
import com.e2d.odin.application.identity.ListUsers;
import com.e2d.odin.application.identity.LogInAdmin;
import com.e2d.odin.application.identity.LogOutAdmin;
import com.e2d.odin.application.identity.RegisterUser;
import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.application.identity.RevokeAccessKey;
import com.e2d.odin.application.identity.SetUpInstallation;
import com.e2d.odin.application.knowledge.ListCardsForReview;
import com.e2d.odin.application.knowledge.ReviewCard;
import com.e2d.odin.application.knowledge.SaveKnowledgeCard;
import com.e2d.odin.application.organization.CreateCompany;
import com.e2d.odin.application.organization.CreateProject;
import com.e2d.odin.application.organization.DescribeMe;
import com.e2d.odin.application.organization.ListCompanies;
import com.e2d.odin.application.organization.ListProjects;
import com.e2d.odin.application.organization.RenameCompany;
import com.e2d.odin.application.organization.SetCompanyCapabilities;
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
    final InMemoryUserRepository users = new InMemoryUserRepository();
    final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    final InMemoryKnowledgeCardRepository cards = new InMemoryKnowledgeCardRepository();
    final ScriptedKnowledgeSearch search = new ScriptedKnowledgeSearch();
    final StubFormatDetector detector = new StubFormatDetector(DocumentFormat.PDF);
    final MutableClock clock = new MutableClock();
    final SequenceIdGenerator ids = new SequenceIdGenerator();
    final SequenceTokenGenerator tokens = new SequenceTokenGenerator();
    final FakeSecretHasher secrets = new FakeSecretHasher();
    final FakePasswordHasher passwords = new FakePasswordHasher();
    final InMemoryOriginalFileStore files = new InMemoryOriginalFileStore();

    @Bean Clock clock() {
        return clock;
    }

    @Bean ResolveActor resolveActor() {
        return new ResolveActor(keys, users, sessions, secrets, new SessionSettings(), clock);
    }

    @Bean SetUpInstallation setUpInstallation() {
        return new SetUpInstallation(orgs, users, passwords, ids, new InstallationSettings("setup-secret"));
    }

    @Bean LogInAdmin logInAdmin() {
        return new LogInAdmin(users, sessions, passwords, secrets, tokens, clock);
    }

    @Bean LogOutAdmin logOutAdmin() {
        return new LogOutAdmin(sessions, secrets);
    }

    @Bean CreateCompany createCompany() {
        return new CreateCompany(orgs, ids);
    }

    @Bean ListCompanies listCompanies() {
        return new ListCompanies(orgs);
    }

    @Bean RenameCompany renameCompany() {
        return new RenameCompany(orgs);
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

    @Bean RegisterUser registerUser() {
        return new RegisterUser(users, orgs, passwords, ids);
    }

    @Bean ListUsers listUsers() {
        return new ListUsers(users);
    }

    @Bean DeactivateUser deactivateUser() {
        return new DeactivateUser(users);
    }

    @Bean IssueAccessKey issueAccessKey() {
        return new IssueAccessKey(keys, users, secrets, tokens, ids, clock);
    }

    @Bean ListAccessKeys listAccessKeys() {
        return new ListAccessKeys(keys, users);
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
        return new SaveKnowledgeCard(orgs, cards, ids, clock);
    }

    @Bean DescribeMe describeMe() {
        return new DescribeMe(orgs);
    }

    @Bean SearchKnowledge searchKnowledge() {
        return new SearchKnowledge(orgs, search, new InMemoryQuestionLog(), ids, clock);
    }

    @Bean ListDocuments listDocuments() {
        return new ListDocuments(orgs, docs);
    }

    @Bean ReadPage readPage() {
        return new ReadPage(orgs, docs);
    }

    @Bean VerifyCitation verifyCitation() {
        return new VerifyCitation(orgs, docs);
    }
}
