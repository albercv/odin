package com.e2d.odin.adapter.web;

import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.fake.InMemoryAccessLinkRepository;
import com.e2d.odin.application.link.DescribeUploadLink;
import com.e2d.odin.application.link.IssueOriginalLink;
import com.e2d.odin.application.link.IssueUploadLink;
import com.e2d.odin.application.link.LinkIssuer;
import com.e2d.odin.application.link.LinkSettings;
import com.e2d.odin.application.link.OpenOriginal;
import com.e2d.odin.application.link.ResolveLink;
import com.e2d.odin.application.link.UploadByLink;
import com.e2d.odin.application.casefile.GetCaseFile;
import com.e2d.odin.application.casefile.ReadCaseFile;
import com.e2d.odin.application.casefile.ListCaseFileEvents;
import com.e2d.odin.application.casefile.RemoveFromCaseFile;
import com.e2d.odin.application.document.GetDocument;
import com.e2d.odin.application.document.ListCompanyDocuments;
import com.e2d.odin.application.document.RetryDocument;
import com.e2d.odin.application.document.MarkCaseDocuments;
import com.e2d.odin.application.document.SupersedeDocument;
import com.e2d.odin.application.document.RestoreDocument;
import com.e2d.odin.application.document.UpdateDocument;
import com.e2d.odin.application.document.WithdrawDocument;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryDeliveryMeter;
import com.e2d.odin.application.fake.InMemoryCaseFileRepository;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryKnowledgeCardRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryQuestionLog;
import com.e2d.odin.application.fake.InMemoryTaggingRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.application.fake.InMemoryUsageStatistics;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.ScriptedKnowledgeSearch;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequencePersonCodeGenerator;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.identity.AddMember;
import com.e2d.odin.application.identity.ChangePassword;
import com.e2d.odin.application.identity.AddGroupAdmin;
import com.e2d.odin.application.identity.DeleteUser;
import com.e2d.odin.application.identity.ListGroupAdmins;
import com.e2d.odin.application.identity.RevokeGroupAdmin;
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
import com.e2d.odin.application.search.FindSimilarCases;
import com.e2d.odin.application.knowledge.ListMyKnowledgeCards;
import com.e2d.odin.application.search.ListDocuments;
import com.e2d.odin.application.search.ListMyQuestions;
import com.e2d.odin.application.search.ReadPage;
import com.e2d.odin.application.search.SearchKnowledge;
import com.e2d.odin.application.search.VerifyCitation;
import com.e2d.odin.application.topic.CreateTopic;
import com.e2d.odin.application.topic.DeleteTopic;
import com.e2d.odin.application.topic.GetTaggingStatus;
import com.e2d.odin.application.topic.ListDocumentTopics;
import com.e2d.odin.application.topic.ListTopics;
import com.e2d.odin.application.topic.ReviewDocumentTopics;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.application.topic.UpdateTopic;
import com.e2d.odin.application.port.DeliveryMeter;
import com.e2d.odin.application.usage.GetDeliveredVolume;
import com.e2d.odin.application.usage.GetUsageStats;
import com.e2d.odin.application.usage.ListGaps;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.document.DocumentFormat;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
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
    final InMemoryAccessLinkRepository links = new InMemoryAccessLinkRepository();
    final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    final InMemoryCaseFileRepository caseFiles = new InMemoryCaseFileRepository(docs);
    final InMemoryKnowledgeCardRepository cards = new InMemoryKnowledgeCardRepository();
    final InMemoryQuestionLog questions = new InMemoryQuestionLog();
    final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);
    final InMemoryDeliveryMeter meter = new InMemoryDeliveryMeter();
    final InMemoryUsageStatistics usage = new InMemoryUsageStatistics();
    final ScriptedKnowledgeSearch search = new ScriptedKnowledgeSearch();
    final StubFormatDetector detector = new StubFormatDetector(DocumentFormat.PDF);
    final MutableClock clock = new MutableClock();
    final SequenceIdGenerator ids = new SequenceIdGenerator();
    final SequencePersonCodeGenerator personCodes = new SequencePersonCodeGenerator();
    final SequenceTokenGenerator tokens = new SequenceTokenGenerator();
    final FakeSecretHasher secrets = new FakeSecretHasher();
    final FakePasswordHasher passwords = new FakePasswordHasher();
    final InMemoryOriginalFileStore files = new InMemoryOriginalFileStore();

    @Bean InstallationSettings installationSettings() {
        return new InstallationSettings("setup-secret", true);
    }

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
        return new RemoveMember(users, memberships, keys, projectMembers, adminSessions(), clock);
    }

    @Bean DescribeAdmin describeAdmin() {
        return new DescribeAdmin(users);
    }

    @Bean ListGroupAdmins listGroupAdmins() {
        return new ListGroupAdmins(users);
    }

    @Bean AddGroupAdmin addGroupAdmin() {
        return new AddGroupAdmin(users, memberships, passwords, ids, personCodes);
    }

    @Bean RevokeGroupAdmin revokeGroupAdmin() {
        return new RevokeGroupAdmin(users, memberships, adminSessions());
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
        return new RevokeAccessKey(keys, users, links, clock);
    }

    @Bean LinkSettings linkSettings() {
        return new LinkSettings("http://odin.test", java.time.Duration.ofMinutes(15));
    }

    @Bean LinkIssuer linkIssuer() {
        return new LinkIssuer(links, tokens, secrets, ids, clock, linkSettings());
    }

    @Bean IssueUploadLink issueUploadLink() {
        return new IssueUploadLink(orgs, projectMembers, docs, linkIssuer());
    }

    @Bean IssueOriginalLink issueOriginalLink() {
        return new IssueOriginalLink(orgs, projectMembers, docs, linkIssuer());
    }

    @Bean ResolveLink resolveLink() {
        return new ResolveLink(links, resolveActor(), orgs, projectMembers, docs, secrets, clock);
    }

    @Bean DescribeUploadLink describeUploadLink() {
        return new DescribeUploadLink(resolveLink(), orgs);
    }

    @Bean UploadByLink uploadByLink() {
        return new UploadByLink(resolveLink(), links, uploadDocument(), addToCaseFiles(), caseFiles, ids, clock);
    }

    @Bean OpenOriginal openOriginal() {
        return new OpenOriginal(resolveLink(), files, docs);
    }

    @Bean UploadDocument uploadDocument() {
        return new UploadDocument(docs, orgs, files, detector, new UploadSettings(MAX_UPLOAD), ids, clock);
    }

    @Bean ListCompanyDocuments listCompanyDocuments() {
        return new ListCompanyDocuments(docs, topics, tagging);
    }

    @Bean GetDocument getDocument() {
        return new GetDocument(docs);
    }

    @Bean RetryDocument retryDocument() {
        return new RetryDocument(docs);
    }

    @Bean UpdateDocument updateDocument() {
        return new UpdateDocument(docs, caseFiles, clock);
    }

    @Bean WithdrawDocument withdrawDocument() {
        return new WithdrawDocument(docs, clock);
    }

    @Bean RestoreDocument restoreDocument() {
        return new RestoreDocument(docs);
    }

    @Bean MarkCaseDocuments markCaseDocuments() {
        return new MarkCaseDocuments(docs, caseFiles, clock);
    }

    @Bean ReadCaseFile readCaseFile() {
        return new ReadCaseFile(orgs, projectMembers, docs, caseFiles,
                new com.e2d.odin.application.shared.CaseFileSettings());
    }

    @Bean GetCaseFile getCaseFile() {
        return new GetCaseFile(orgs, projectMembers, docs, caseFiles);
    }

    @Bean AddToCaseFiles addToCaseFiles() {
        return new AddToCaseFiles(orgs, projectMembers, docs, caseFiles, ids, clock);
    }

    @Bean RemoveFromCaseFile removeFromCaseFile() {
        return new RemoveFromCaseFile(orgs, projectMembers, docs, caseFiles, ids, clock);
    }

    @Bean ListCaseFileEvents listCaseFileEvents() {
        return new ListCaseFileEvents(docs, caseFiles);
    }

    @Bean SupersedeDocument supersedeDocument() {
        return new SupersedeDocument(docs, clock);
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

    @Bean ListMyKnowledgeCards listMyKnowledgeCards() {
        return new ListMyKnowledgeCards(orgs, projectMembers, cards);
    }

    @Bean ListMyQuestions listMyQuestions() {
        return new ListMyQuestions(orgs, projectMembers, questions);
    }

    @Bean DescribeMe describeMe() {
        return new DescribeMe(orgs, projectMembers, topics, tagging);
    }

    @Bean SearchKnowledge searchKnowledge() {
        return new SearchKnowledge(orgs, projectMembers, search, questions, ids, clock);
    }

    @Bean FindSimilarCases findSimilarCases() {
        return new FindSimilarCases(orgs, projectMembers, search, questions, ids, clock);
    }

    @Bean ListDocuments listDocuments() {
        return new ListDocuments(orgs, projectMembers, docs, topics, tagging);
    }

    @Bean ReadPage readPage() {
        return new ReadPage(orgs, projectMembers, docs);
    }

    @Bean VerifyCitation verifyCitation() {
        return new VerifyCitation(orgs, projectMembers, docs);
    }

    @Bean CreateTopic createTopic() {
        return new CreateTopic(orgs, topics, ids);
    }

    @Bean UpdateTopic updateTopic() {
        return new UpdateTopic(topics);
    }

    @Bean DeleteTopic deleteTopic() {
        return new DeleteTopic(topics, clock);
    }

    @Bean ListTopics listTopics() {
        return new ListTopics(topics);
    }

    @Bean ReviewDocumentTopics reviewDocumentTopics() {
        return new ReviewDocumentTopics(docs, topics, tagging, clock);
    }

    @Bean ListDocumentTopics listDocumentTopics() {
        return new ListDocumentTopics(docs, topics, tagging, users);
    }

    @Bean TaggingWindow taggingWindow() {
        return TaggingWindow.between(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("Europe/Madrid"));
    }

    @Bean GetTaggingStatus getTaggingStatus() {
        return new GetTaggingStatus(tagging, taggingWindow());
    }

    @Bean GetUsageStats getUsageStats() {
        return new GetUsageStats(orgs, usage, clock, ZoneId.of("Europe/Madrid"));
    }

    @Bean ListGaps listGaps() {
        return new ListGaps(orgs, usage, clock, ZoneId.of("Europe/Madrid"));
    }

    @Bean DeliveryMeter deliveryMeter() {
        return meter;
    }

    @Bean GetDeliveredVolume getDeliveredVolume() {
        return new GetDeliveredVolume(orgs, meter, clock, ZoneId.of("Europe/Madrid"));
    }
}
