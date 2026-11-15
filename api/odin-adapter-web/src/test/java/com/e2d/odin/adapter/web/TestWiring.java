package com.e2d.odin.adapter.web;

import com.e2d.odin.application.audit.CheckAuditSeal;
import com.e2d.odin.application.audit.ExportAuditActivity;
import com.e2d.odin.application.audit.GetAuditIntegrity;
import com.e2d.odin.application.audit.GetAuditSettings;
import com.e2d.odin.application.audit.ListAuditActivity;
import com.e2d.odin.application.audit.UpdateAuditSettings;
import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.casefile.GetCaseFile;
import com.e2d.odin.application.casefile.ListCaseFileEvents;
import com.e2d.odin.application.casefile.ReadCaseFile;
import com.e2d.odin.application.casefile.RemoveFromCaseFile;
import com.e2d.odin.application.casefile.UploadToCaseFile;
import com.e2d.odin.application.document.GetDocument;
import com.e2d.odin.application.document.ListCompanyDocuments;
import com.e2d.odin.application.document.MarkCaseDocuments;
import com.e2d.odin.application.document.RestoreDocument;
import com.e2d.odin.application.document.RetryDocument;
import com.e2d.odin.application.document.SupersedeDocument;
import com.e2d.odin.application.document.UpdateDocument;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.document.UploadToProject;
import com.e2d.odin.application.document.WithdrawDocument;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryAccessLinkRepository;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryAuditReader;
import com.e2d.odin.application.fake.InMemoryAuditSettings;
import com.e2d.odin.application.fake.InMemoryCaseFileRepository;
import com.e2d.odin.application.fake.InMemoryDeliveryMeter;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryIntakeRepository;
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
import com.e2d.odin.application.identity.AddGroupAdmin;
import com.e2d.odin.application.identity.AddMember;
import com.e2d.odin.application.identity.ChangePassword;
import com.e2d.odin.application.identity.DeleteUser;
import com.e2d.odin.application.identity.DescribeAdmin;
import com.e2d.odin.application.identity.IssueAccessKey;
import com.e2d.odin.application.identity.ListAccessKeys;
import com.e2d.odin.application.identity.ListGroupAdmins;
import com.e2d.odin.application.identity.ListMembers;
import com.e2d.odin.application.identity.LogInAdmin;
import com.e2d.odin.application.identity.LogOutAdmin;
import com.e2d.odin.application.identity.RecoverGroupAdmin;
import com.e2d.odin.application.identity.RemoveMember;
import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.application.identity.RevokeAccessKey;
import com.e2d.odin.application.identity.RevokeGroupAdmin;
import com.e2d.odin.application.identity.SetUpInstallation;
import com.e2d.odin.application.identity.StoredAdminSessions;
import com.e2d.odin.application.identity.UpdateMember;
import com.e2d.odin.application.identity.UpdateUser;
import com.e2d.odin.application.identity.UserNames;
import com.e2d.odin.application.intake.AddFileToIntake;
import com.e2d.odin.application.intake.CloseIntakeUpload;
import com.e2d.odin.application.intake.CloseProjectList;
import com.e2d.odin.application.intake.ConfirmIntakeProposals;
import com.e2d.odin.application.intake.ConfirmProjectCandidates;
import com.e2d.odin.application.intake.DecideIntakeProposal;
import com.e2d.odin.application.intake.DiscardIntakeDocument;
import com.e2d.odin.application.intake.GetIntakeBatch;
import com.e2d.odin.application.intake.ImportProjectList;
import com.e2d.odin.application.intake.IntakeEffects;
import com.e2d.odin.application.intake.ListIntakeProposals;
import com.e2d.odin.application.intake.ListOpenIntakeBatches;
import com.e2d.odin.application.intake.ListProjectCandidates;
import com.e2d.odin.application.intake.ListUnplacedDocuments;
import com.e2d.odin.application.intake.OpenIntakeOriginal;
import com.e2d.odin.application.intake.PlaceIntakeDocument;
import com.e2d.odin.application.intake.StartIntake;
import com.e2d.odin.application.knowledge.ListCardsForReview;
import com.e2d.odin.application.knowledge.ListMyKnowledgeCards;
import com.e2d.odin.application.knowledge.ReviewCard;
import com.e2d.odin.application.knowledge.SaveKnowledgeCard;
import com.e2d.odin.application.link.DescribeUploadLink;
import com.e2d.odin.application.link.IssueOriginalLink;
import com.e2d.odin.application.link.IssueUploadLink;
import com.e2d.odin.application.link.LinkIssuer;
import com.e2d.odin.application.link.LinkSettings;
import com.e2d.odin.application.link.OpenOriginal;
import com.e2d.odin.application.link.ResolveLink;
import com.e2d.odin.application.link.UploadByLink;
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
import com.e2d.odin.application.port.AuditContext;
import com.e2d.odin.application.port.DeliveryMeter;
import com.e2d.odin.application.search.FindSimilarCases;
import com.e2d.odin.application.search.ListDocuments;
import com.e2d.odin.application.search.ListMyQuestions;
import com.e2d.odin.application.search.ReadPage;
import com.e2d.odin.application.search.SearchKnowledge;
import com.e2d.odin.application.search.VerifyCitation;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.application.topic.CreateTopic;
import com.e2d.odin.application.topic.DeleteTopic;
import com.e2d.odin.application.topic.GetTaggingStatus;
import com.e2d.odin.application.topic.ListDocumentTopics;
import com.e2d.odin.application.topic.ListTopics;
import com.e2d.odin.application.topic.ReviewDocumentTopics;
import com.e2d.odin.application.topic.UpdateTopic;
import com.e2d.odin.application.usage.GetDeliveredVolume;
import com.e2d.odin.application.usage.GetUsageStats;
import com.e2d.odin.application.usage.ListGaps;
import com.e2d.odin.domain.document.DocumentFormat;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestWiring {

    static final long MAX_UPLOAD = 1000;

    final InMemoryAuditLog auditLog = new InMemoryAuditLog();
    final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    final InMemoryProjectMemberRepository projectMembers = new InMemoryProjectMemberRepository(orgs);
    final InMemoryUserRepository users = new InMemoryUserRepository();
    final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    final InMemoryAccessLinkRepository links = new InMemoryAccessLinkRepository();
    final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    final com.e2d.odin.application.shared.IntakeSettings intakeSettings = new com.e2d.odin.application.shared.IntakeSettings(false);
    final InMemoryIntakeRepository intake = new InMemoryIntakeRepository();
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
        return new SetUpInstallation(orgs, users, passwords, ids, personCodes, new InstallationSettings("setup-secret"), auditLog());
    }

    @Bean LogInAdmin logInAdmin() {
        return new LogInAdmin(users, memberships, passwords, adminSessions(), auditLog(), () -> AuditContext.SYSTEM);
    }

    @Bean LogOutAdmin logOutAdmin() {
        return new LogOutAdmin(adminSessions(), auditLog(), () -> AuditContext.SYSTEM);
    }

    @Bean CreateCompany createCompany() {
        return new CreateCompany(orgs, ids, auditLog());
    }

    @Bean ListCompanies listCompanies() {
        return new ListCompanies(orgs);
    }

    @Bean UpdateCompany updateCompany() {
        return new UpdateCompany(orgs, auditLog());
    }

    @Bean SetCompanyCapabilities setCompanyCapabilities() {
        return new SetCompanyCapabilities(orgs, auditLog());
    }

    @Bean CreateProject createProject() {
        return new CreateProject(orgs, ids, auditLog());
    }

    @Bean ListProjects listProjects() {
        return new ListProjects(orgs);
    }

    @Bean UpdateProject updateProject() {
        return new UpdateProject(orgs, auditLog());
    }

    @Bean AddProjectMember addProjectMember() {
        return new AddProjectMember(orgs, memberships, projectMembers, auditLog());
    }

    @Bean RemoveProjectMember removeProjectMember() {
        return new RemoveProjectMember(orgs, projectMembers, auditLog());
    }

    @Bean ListProjectMembers listProjectMembers() {
        return new ListProjectMembers(orgs, users, projectMembers);
    }

    @Bean ListMemberProjects listMemberProjects() {
        return new ListMemberProjects(orgs, memberships, projectMembers);
    }

    @Bean DeleteProject deleteProject() {
        return new DeleteProject(orgs, clock, auditLog());
    }

    @Bean AddMember addMember() {
        return new AddMember(users, memberships, orgs, projectMembers, passwords, ids, personCodes, auditLog());
    }

    @Bean ListMembers listMembers() {
        return new ListMembers(users, memberships, orgs);
    }

    @Bean UpdateMember updateMember() {
        return new UpdateMember(users, memberships, orgs, passwords, adminSessions(), auditLog());
    }

    @Bean RemoveMember removeMember() {
        return new RemoveMember(users, memberships, keys, projectMembers, adminSessions(), clock, auditLog());
    }

    @Bean DescribeAdmin describeAdmin() {
        return new DescribeAdmin(users);
    }

    @Bean InMemoryAuditLog auditLog() {
        return auditLog;
    }

    @Bean InMemoryAuditSettings auditSettings() {
        return new InMemoryAuditSettings();
    }

    @Bean InMemoryAuditReader auditReader() {
        return new InMemoryAuditReader();
    }

    @Bean ListAuditActivity listAuditActivity(InMemoryAuditReader reader) {
        return new ListAuditActivity(reader);
    }

    @Bean ExportAuditActivity exportAuditActivity(InMemoryAuditReader reader) {
        return new ExportAuditActivity(reader, auditLog());
    }

    @Bean GetAuditIntegrity getAuditIntegrity(InMemoryAuditReader reader, InMemoryAuditSettings settings) {
        return new GetAuditIntegrity(reader, settings);
    }

    @Bean CheckAuditSeal checkAuditSeal(InMemoryAuditReader reader) {
        return new CheckAuditSeal(reader);
    }

    @Bean GetAuditSettings getAuditSettings(InMemoryAuditSettings settings) {
        return new GetAuditSettings(settings);
    }

    @Bean UpdateAuditSettings updateAuditSettings(InMemoryAuditSettings settings) {
        return new UpdateAuditSettings(settings, auditLog(), users, passwords);
    }

    @Bean ListGroupAdmins listGroupAdmins() {
        return new ListGroupAdmins(users);
    }

    @Bean AddGroupAdmin addGroupAdmin() {
        return new AddGroupAdmin(users, memberships, passwords, ids, personCodes, auditLog());
    }

    @Bean RevokeGroupAdmin revokeGroupAdmin() {
        return new RevokeGroupAdmin(users, memberships, adminSessions(), auditLog());
    }

    @Bean UpdateUser updateUser() {
        return new UpdateUser(users, memberships, adminSessions(), auditLog());
    }

    @Bean ChangePassword changePassword() {
        return new ChangePassword(users, memberships, passwords, adminSessions(), auditLog());
    }

    @Bean RecoverGroupAdmin recoverGroupAdmin() {
        return new RecoverGroupAdmin(users, passwords, adminSessions(), new InstallationSettings("setup-secret"), auditLog());
    }

    @Bean DeleteUser deleteUser() {
        return new DeleteUser(users, memberships, projectMembers, adminSessions(), clock, auditLog());
    }

    @Bean IssueAccessKey issueAccessKey() {
        return new IssueAccessKey(keys, users, memberships, secrets, tokens, ids, clock, auditLog());
    }

    @Bean ListAccessKeys listAccessKeys() {
        return new ListAccessKeys(keys, memberships);
    }

    @Bean RevokeAccessKey revokeAccessKey() {
        return new RevokeAccessKey(keys, users, links, clock, auditLog());
    }

    @Bean LinkSettings linkSettings() {
        return new LinkSettings("http://odin.test", java.time.Duration.ofMinutes(15));
    }

    @Bean LinkIssuer linkIssuer() {
        return new LinkIssuer(links, tokens, secrets, ids, clock, linkSettings(), auditLog());
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
        return new UploadByLink(resolveLink(), links, uploadToCaseFile(), uploadToProject(), auditLog());
    }

    @Bean UploadToProject uploadToProject() {
        return new UploadToProject(uploadDocument(), docs);
    }

    @Bean UploadToCaseFile uploadToCaseFile() {
        return new UploadToCaseFile(uploadDocument(), addToCaseFiles(), caseFiles, ids, clock, auditLog());
    }

    @Bean OpenOriginal openOriginal() {
        return new OpenOriginal(resolveLink(), files, docs, auditLog());
    }

    @Bean UploadDocument uploadDocument() {
        return new UploadDocument(docs, orgs, files, detector, new UploadSettings(MAX_UPLOAD), ids, clock, auditLog());
    }

    @Bean StartIntake startIntake() {
        return new StartIntake(intake, orgs, ids, clock, auditLog());
    }

    @Bean AddFileToIntake addFileToIntake() {
        return new AddFileToIntake(intake, uploadDocument(), auditLog());
    }

    @Bean CloseIntakeUpload closeIntakeUpload() {
        return new CloseIntakeUpload(intake, auditLog());
    }

    @Bean GetIntakeBatch getIntakeBatch() {
        return new GetIntakeBatch(intake, docs, installationSettings(), intakeSettings);
    }

    @Bean ImportProjectList importProjectList() {
        return new ImportProjectList(intake, auditLog());
    }

    @Bean ListProjectCandidates listProjectCandidates() {
        return new ListProjectCandidates(intake, docs);
    }

    @Bean OpenIntakeOriginal openIntakeOriginal() {
        return new OpenIntakeOriginal(intake, docs, files, auditLog());
    }

    @Bean CloseProjectList closeProjectList() {
        return new CloseProjectList(intake, docs, listProjectCandidates(), installationSettings(), clock, auditLog());
    }

    @Bean ConfirmProjectCandidates confirmProjectCandidates() {
        return new ConfirmProjectCandidates(intake, orgs, createProject(), auditLog());
    }

    @Bean IntakeEffects intakeEffects() {
        return new IntakeEffects(intake, docs, orgs, markCaseDocuments(), supersedeDocument(), addToCaseFiles(),
                installationSettings(), clock);
    }

    @Bean ListUnplacedDocuments listUnplacedDocuments() {
        return new ListUnplacedDocuments(intake, docs);
    }

    @Bean PlaceIntakeDocument placeIntakeDocument() {
        return new PlaceIntakeDocument(intakeEffects(), auditLog());
    }

    @Bean ListOpenIntakeBatches listOpenIntakeBatches() {
        return new ListOpenIntakeBatches(intake);
    }
    @Bean ListIntakeProposals listIntakeProposals() {
        return new ListIntakeProposals(intake, docs);
    }

    @Bean ConfirmIntakeProposals confirmIntakeProposals() {
        return new ConfirmIntakeProposals(intake, intakeEffects(), auditLog());
    }

    @Bean DecideIntakeProposal decideIntakeProposal() {
        return new DecideIntakeProposal(intake, intakeEffects(), auditLog());
    }

    @Bean DiscardIntakeDocument discardIntakeDocument() {
        return new DiscardIntakeDocument(docs, withdrawDocument(), intakeEffects(), auditLog());
    }

    @Bean UserNames userNames() {
        return new UserNames(users);
    }

    @Bean ListCompanyDocuments listCompanyDocuments() {
        return new ListCompanyDocuments(docs, topics, tagging);
    }

    @Bean GetDocument getDocument() {
        return new GetDocument(docs);
    }

    @Bean RetryDocument retryDocument() {
        return new RetryDocument(docs, auditLog());
    }

    @Bean UpdateDocument updateDocument() {
        return new UpdateDocument(docs, caseFiles, clock, auditLog());
    }

    @Bean WithdrawDocument withdrawDocument() {
        return new WithdrawDocument(docs, clock, auditLog());
    }

    @Bean RestoreDocument restoreDocument() {
        return new RestoreDocument(docs, auditLog());
    }

    @Bean MarkCaseDocuments markCaseDocuments() {
        return new MarkCaseDocuments(docs, caseFiles, clock, auditLog());
    }

    @Bean ReadCaseFile readCaseFile() {
        return new ReadCaseFile(orgs, projectMembers, docs, caseFiles, users,
                new com.e2d.odin.application.shared.CaseFileSettings());
    }

    @Bean GetCaseFile getCaseFile() {
        return new GetCaseFile(orgs, projectMembers, docs, caseFiles, users);
    }

    @Bean AddToCaseFiles addToCaseFiles() {
        return new AddToCaseFiles(orgs, projectMembers, docs, caseFiles, ids, clock, auditLog());
    }

    @Bean RemoveFromCaseFile removeFromCaseFile() {
        return new RemoveFromCaseFile(orgs, projectMembers, docs, caseFiles, ids, clock, auditLog());
    }

    @Bean ListCaseFileEvents listCaseFileEvents() {
        return new ListCaseFileEvents(docs, caseFiles);
    }

    @Bean SupersedeDocument supersedeDocument() {
        return new SupersedeDocument(docs, clock, auditLog());
    }

    @Bean ListCardsForReview listCardsForReview() {
        return new ListCardsForReview(cards);
    }

    @Bean ReviewCard reviewCard() {
        return new ReviewCard(cards, clock, auditLog());
    }

    @Bean SaveKnowledgeCard saveKnowledgeCard() {
        return new SaveKnowledgeCard(orgs, projectMembers, cards, ids, clock, auditLog());
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
        return new CreateTopic(orgs, topics, ids, auditLog());
    }

    @Bean UpdateTopic updateTopic() {
        return new UpdateTopic(topics, auditLog());
    }

    @Bean DeleteTopic deleteTopic() {
        return new DeleteTopic(topics, clock, auditLog());
    }

    @Bean ListTopics listTopics() {
        return new ListTopics(topics);
    }

    @Bean ReviewDocumentTopics reviewDocumentTopics() {
        return new ReviewDocumentTopics(docs, topics, tagging, clock, auditLog());
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
