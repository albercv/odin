package com.e2d.odin.bootstrap;

import com.e2d.odin.application.document.GetDocument;
import com.e2d.odin.application.identity.UserNames;
import com.e2d.odin.application.document.ListCompanyDocuments;
import com.e2d.odin.application.document.ProcessPendingDocuments;
import com.e2d.odin.application.document.RetryDocument;
import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.link.DescribeUploadLink;
import com.e2d.odin.application.link.IssueOriginalLink;
import com.e2d.odin.application.link.IssueUploadLink;
import com.e2d.odin.application.link.LinkIssuer;
import com.e2d.odin.application.link.LinkSettings;
import com.e2d.odin.application.link.OpenOriginal;
import com.e2d.odin.application.link.ResolveLink;
import com.e2d.odin.application.link.UploadByLink;
import com.e2d.odin.application.casefile.UploadToCaseFile;
import com.e2d.odin.application.document.UploadToProject;
import com.e2d.odin.application.port.AccessLinkRepository;
import com.e2d.odin.application.casefile.GetCaseFile;
import com.e2d.odin.application.casefile.ListCaseFileEvents;
import com.e2d.odin.application.casefile.ReadCaseFile;
import com.e2d.odin.application.casefile.RemoveFromCaseFile;
import com.e2d.odin.application.document.MarkCaseDocuments;
import com.e2d.odin.application.shared.CaseFileSettings;
import com.e2d.odin.application.document.RestoreDocument;
import com.e2d.odin.application.document.SupersedeDocument;
import com.e2d.odin.application.document.UnlinkScanCopy;
import com.e2d.odin.application.document.UpdateDocument;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.intake.AddFileToIntake;
import com.e2d.odin.application.intake.ClassifyIntake;
import com.e2d.odin.application.intake.CloseIntakeBatch;
import com.e2d.odin.application.intake.ListOpenIntakeBatches;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.application.intake.CloseIntakeUpload;
import com.e2d.odin.application.intake.CloseProjectList;
import com.e2d.odin.application.port.ProjectProposer;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import com.e2d.odin.application.intake.ConfirmProjectCandidates;
import com.e2d.odin.application.intake.ConfirmIntakeProposals;
import com.e2d.odin.application.intake.DecideIntakeProposal;
import com.e2d.odin.application.intake.DiscardIntakeDocument;
import com.e2d.odin.application.intake.GetIntakeBatch;
import com.e2d.odin.application.intake.IntakeDecision;
import com.e2d.odin.application.intake.IntakeEffects;
import com.e2d.odin.application.intake.ListIntakeProposals;
import com.e2d.odin.application.intake.ListUnplacedDocuments;
import com.e2d.odin.application.intake.OpenIntakeOriginal;
import com.e2d.odin.application.intake.PlaceIntakeDocument;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.UUID;
import com.e2d.odin.application.intake.ImportProjectList;
import com.e2d.odin.application.intake.ListProjectCandidates;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.application.intake.ProjectDecision;
import com.e2d.odin.application.intake.StartIntake;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.document.WithdrawDocument;
import com.e2d.odin.application.identity.AddGroupAdmin;
import com.e2d.odin.application.identity.AddMember;
import com.e2d.odin.application.identity.ListGroupAdmins;
import com.e2d.odin.application.identity.RevokeGroupAdmin;
import com.e2d.odin.application.identity.ChangePassword;
import com.e2d.odin.application.identity.DeleteUser;
import com.e2d.odin.application.identity.DescribeAdmin;
import com.e2d.odin.application.identity.IssueAccessKey;
import com.e2d.odin.application.identity.ListAccessKeys;
import com.e2d.odin.application.identity.ListMembers;
import com.e2d.odin.application.identity.LogInAdmin;
import com.e2d.odin.application.identity.LogOutAdmin;
import com.e2d.odin.application.identity.MemberAdded;
import com.e2d.odin.application.identity.MemberChanges;
import com.e2d.odin.application.identity.MemberView;
import com.e2d.odin.application.identity.NewMember;
import com.e2d.odin.application.identity.RecoverGroupAdmin;
import com.e2d.odin.application.identity.RemoveMember;
import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.application.identity.RevokeAccessKey;
import com.e2d.odin.application.identity.SetUpInstallation;
import com.e2d.odin.application.identity.StoredAdminSessions;
import com.e2d.odin.application.identity.UpdateMember;
import com.e2d.odin.application.identity.UpdateUser;
import com.e2d.odin.application.identity.UserChanges;
import com.e2d.odin.application.knowledge.ListCardsForReview;
import com.e2d.odin.application.knowledge.ListMyKnowledgeCards;
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
import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.DeliveryMeter;
import com.e2d.odin.application.port.DocumentFormatDetector;
import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.application.port.KnowledgeSearch;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.PersonCodeGenerator;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.port.QuestionLog;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.TokenGenerator;
import com.e2d.odin.application.port.UsageStatistics;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.search.FindSimilarCases;
import com.e2d.odin.application.search.ListDocuments;
import com.e2d.odin.application.search.ListMyQuestions;
import com.e2d.odin.application.search.ReadPage;
import com.e2d.odin.application.search.SearchKnowledge;
import com.e2d.odin.application.search.VerifyCitation;
import com.e2d.odin.application.topic.CreateTopic;
import com.e2d.odin.application.topic.DeleteTopic;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.application.topic.GetTaggingStatus;
import com.e2d.odin.application.topic.ListDocumentTopics;
import com.e2d.odin.application.topic.ListTopics;
import com.e2d.odin.application.topic.ReviewDocumentTopics;
import com.e2d.odin.application.topic.UpdateTopic;
import com.e2d.odin.application.usage.GetDeliveredVolume;
import com.e2d.odin.application.usage.GetUsageStats;
import com.e2d.odin.application.usage.ListGaps;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.IntakeSettings;
import com.e2d.odin.application.shared.ProcessingSettings;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class UseCaseConfiguration {

    @Bean
    AdminSessions adminSessions(
            AdminSessionRepository repository,
            SecretHasher secrets,
            TokenGenerator tokens,
            SessionSettings sessionSettings,
            Clock clock) {
        return new StoredAdminSessions(repository, secrets, tokens, sessionSettings, clock);
    }

    @Bean
    ResolveActor resolveActor(
            AccessKeyRepository keys, UserRepository users, MembershipRepository memberships,
            AdminSessions sessions, SecretHasher secrets) {
        return new ResolveActor(keys, users, memberships, sessions, secrets);
    }

    @Bean
    LogInAdmin logInAdmin(UserRepository users, MembershipRepository memberships, PasswordHasher passwords,
            AdminSessions sessions) {
        return new LogInAdmin(users, memberships, passwords, sessions);
    }

    @Bean
    LogOutAdmin logOutAdmin(AdminSessions sessions) {
        return new LogOutAdmin(sessions);
    }

    @Bean
    CreateCompany createCompany(OrganizationRepository organizations, IdGenerator ids) {
        return new CreateCompany(organizations, ids);
    }

    @Bean
    ListCompanies listCompanies(OrganizationRepository organizations) {
        return new ListCompanies(organizations);
    }

    @Bean
    UpdateCompany updateCompany(OrganizationRepository organizations) {
        return new UpdateCompany(organizations);
    }

    @Bean
    SetCompanyCapabilities setCompanyCapabilities(OrganizationRepository organizations) {
        return new SetCompanyCapabilities(organizations);
    }

    @Bean
    CreateProject createProject(OrganizationRepository organizations, IdGenerator ids) {
        return new CreateProject(organizations, ids);
    }

    @Bean
    CreateTopic createTopic(OrganizationRepository organizations, TopicRepository topics, IdGenerator ids) {
        return new CreateTopic(organizations, topics, ids);
    }

    @Bean
    UpdateTopic updateTopic(TopicRepository topics) {
        return new UpdateTopic(topics);
    }

    @Bean
    DeleteTopic deleteTopic(TopicRepository topics, Clock clock) {
        return new DeleteTopic(topics, clock);
    }

    @Bean
    ListTopics listTopics(TopicRepository topics) {
        return new ListTopics(topics);
    }

    @Bean
    ReviewDocumentTopics reviewDocumentTopics(DocumentRepository documents, TopicRepository topics,
            TaggingRepository tagging, Clock clock) {
        return new ReviewDocumentTopics(documents, topics, tagging, clock);
    }

    @Bean
    ListDocumentTopics listDocumentTopics(DocumentRepository documents, TopicRepository topics,
            TaggingRepository tagging, UserRepository users) {
        return new ListDocumentTopics(documents, topics, tagging, users);
    }

    @Bean
    GetTaggingStatus getTaggingStatus(TaggingRepository tagging, TaggingWindow window) {
        return new GetTaggingStatus(tagging, window);
    }

    @Bean
    GetUsageStats getUsageStats(OrganizationRepository organizations, UsageStatistics statistics, Clock clock,
            @Value("${odin.timezone:Europe/Madrid}") ZoneId zone) {
        return new GetUsageStats(organizations, statistics, clock, zone);
    }

    @Bean
    GetDeliveredVolume getDeliveredVolume(OrganizationRepository organizations, DeliveryMeter meter, Clock clock,
            @Value("${odin.timezone:Europe/Madrid}") ZoneId zone) {
        return new GetDeliveredVolume(organizations, meter, clock, zone);
    }

    @Bean
    ListGaps listGaps(OrganizationRepository organizations, UsageStatistics statistics, Clock clock,
            @Value("${odin.timezone:Europe/Madrid}") ZoneId zone) {
        return new ListGaps(organizations, statistics, clock, zone);
    }

    @Bean
    ListProjects listProjects(OrganizationRepository organizations) {
        return new ListProjects(organizations);
    }

    @Bean
    UpdateProject updateProject(OrganizationRepository organizations) {
        return new UpdateProject(organizations);
    }

    @Bean
    AddProjectMember addProjectMember(OrganizationRepository organizations, MembershipRepository memberships,
            ProjectMemberRepository projectMembers) {
        return new AddProjectMember(organizations, memberships, projectMembers);
    }

    @Bean
    RemoveProjectMember removeProjectMember(OrganizationRepository organizations,
            ProjectMemberRepository projectMembers) {
        return new RemoveProjectMember(organizations, projectMembers);
    }

    @Bean
    ListProjectMembers listProjectMembers(OrganizationRepository organizations, UserRepository users,
            ProjectMemberRepository projectMembers) {
        return new ListProjectMembers(organizations, users, projectMembers);
    }

    @Bean
    ListMemberProjects listMemberProjects(OrganizationRepository organizations, MembershipRepository memberships,
            ProjectMemberRepository projectMembers) {
        return new ListMemberProjects(organizations, memberships, projectMembers);
    }

    @Bean
    DeleteProject deleteProject(OrganizationRepository organizations, Clock clock) {
        return new DeleteProject(organizations, clock);
    }

    @Bean
    DescribeAdmin describeAdmin(UserRepository users) {
        return new DescribeAdmin(users);
    }

    @Bean
    ListMembers listMembers(UserRepository users, MembershipRepository memberships,
            OrganizationRepository organizations) {
        return new ListMembers(users, memberships, organizations);
    }

    @Bean
    RemoveMember removeMember(UserRepository users, MembershipRepository memberships, AccessKeyRepository keys,
            ProjectMemberRepository projectMembers, AdminSessions sessions, Clock clock, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new RemoveMember(users, memberships, keys, projectMembers, sessions, clock) {
            @Override
            public void execute(Actor actor, CompanyId companyId, UserId userId) {
                transaction.executeWithoutResult(status -> super.execute(actor, companyId, userId));
            }
        };
    }

    @Bean
    ListGroupAdmins listGroupAdmins(UserRepository users) {
        return new ListGroupAdmins(users);
    }

    @Bean
    AddGroupAdmin addGroupAdmin(UserRepository users, MembershipRepository memberships, PasswordHasher passwords,
            IdGenerator ids, PersonCodeGenerator personCodes) {
        return new AddGroupAdmin(users, memberships, passwords, ids, personCodes);
    }

    @Bean
    RevokeGroupAdmin revokeGroupAdmin(UserRepository users, MembershipRepository memberships,
            AdminSessions sessions, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new RevokeGroupAdmin(users, memberships, sessions) {
            @Override
            public void execute(Actor actor, UserId id) {
                transaction.executeWithoutResult(status -> super.execute(actor, id));
            }
        };
    }

    @Bean
    UpdateUser updateUser(UserRepository users, MembershipRepository memberships, AdminSessions sessions,
            PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new UpdateUser(users, memberships, sessions) {
            @Override
            public User execute(Actor actor, UserId id, UserChanges changes) {
                return transaction.execute(status -> super.execute(actor, id, changes));
            }
        };
    }

    @Bean
    ChangePassword changePassword(UserRepository users, MembershipRepository memberships, PasswordHasher passwords,
            AdminSessions sessions) {
        return new ChangePassword(users, memberships, passwords, sessions);
    }

    @Bean
    RecoverGroupAdmin recoverGroupAdmin(UserRepository users, PasswordHasher passwords, AdminSessions sessions,
            InstallationSettings installationSettings) {
        return new RecoverGroupAdmin(users, passwords, sessions, installationSettings);
    }

    @Bean
    DeleteUser deleteUser(UserRepository users, MembershipRepository memberships,
            ProjectMemberRepository projectMembers, AdminSessions sessions, Clock clock,
            PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new DeleteUser(users, memberships, projectMembers, sessions, clock) {
            @Override
            public void execute(Actor actor, UserId id) {
                transaction.executeWithoutResult(status -> super.execute(actor, id));
            }
        };
    }

    @Bean
    AddMember addMember(UserRepository users, MembershipRepository memberships,
            OrganizationRepository organizations, ProjectMemberRepository projectMembers, PasswordHasher passwords,
            IdGenerator ids, PersonCodeGenerator personCodes, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new AddMember(users, memberships, organizations, projectMembers, passwords, ids, personCodes) {
            @Override
            public MemberAdded execute(Actor actor, CompanyId companyId, NewMember newMember) {
                return transaction.execute(status -> super.execute(actor, companyId, newMember));
            }
        };
    }

    @Bean
    UpdateMember updateMember(UserRepository users, MembershipRepository memberships,
            OrganizationRepository organizations, PasswordHasher passwords, AdminSessions sessions,
            PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new UpdateMember(users, memberships, organizations, passwords, sessions) {
            @Override
            public MemberView execute(Actor actor, CompanyId companyId, UserId userId, MemberChanges changes) {
                return transaction.execute(status -> super.execute(actor, companyId, userId, changes));
            }
        };
    }

    @Bean
    IssueAccessKey issueAccessKey(
            AccessKeyRepository keys,
            UserRepository users,
            MembershipRepository memberships,
            SecretHasher secrets,
            TokenGenerator tokens,
            IdGenerator ids,
            Clock clock) {
        return new IssueAccessKey(keys, users, memberships, secrets, tokens, ids, clock);
    }

    @Bean
    ListAccessKeys listAccessKeys(AccessKeyRepository keys, MembershipRepository memberships) {
        return new ListAccessKeys(keys, memberships);
    }

    @Bean
    RevokeAccessKey revokeAccessKey(AccessKeyRepository keys, UserRepository users, AccessLinkRepository links,
            Clock clock) {
        return new RevokeAccessKey(keys, users, links, clock);
    }

    @Bean
    LinkIssuer linkIssuer(AccessLinkRepository links, TokenGenerator tokens, SecretHasher secrets, IdGenerator ids,
            Clock clock, LinkSettings settings) {
        return new LinkIssuer(links, tokens, secrets, ids, clock, settings);
    }

    @Bean
    IssueUploadLink issueUploadLink(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, LinkIssuer issuer) {
        return new IssueUploadLink(organizations, projectMembers, documents, issuer);
    }

    @Bean
    IssueOriginalLink issueOriginalLink(OrganizationRepository organizations,
            ProjectMemberRepository projectMembers, DocumentRepository documents, LinkIssuer issuer) {
        return new IssueOriginalLink(organizations, projectMembers, documents, issuer);
    }

    @Bean
    ResolveLink resolveLink(AccessLinkRepository links, ResolveActor actors, OrganizationRepository organizations,
            ProjectMemberRepository projectMembers, DocumentRepository documents, SecretHasher secrets, Clock clock) {
        return new ResolveLink(links, actors, organizations, projectMembers, documents, secrets, clock);
    }

    @Bean
    DescribeUploadLink describeUploadLink(ResolveLink resolve, OrganizationRepository organizations) {
        return new DescribeUploadLink(resolve, organizations);
    }

    @Bean
    UploadByLink uploadByLink(ResolveLink resolve, AccessLinkRepository links, UploadToCaseFile uploadToCaseFile,
            UploadToProject uploadToProject) {
        return new UploadByLink(resolve, links, uploadToCaseFile, uploadToProject);
    }

    @Bean
    UploadToProject uploadToProject(UploadDocument upload, DocumentRepository documents) {
        return new UploadToProject(upload, documents);
    }

    @Bean
    UploadToCaseFile uploadToCaseFile(UploadDocument upload, AddToCaseFiles addToCaseFiles,
            CaseFileRepository caseFiles, IdGenerator ids, Clock clock) {
        return new UploadToCaseFile(upload, addToCaseFiles, caseFiles, ids, clock);
    }

    @Bean
    OpenOriginal openOriginal(ResolveLink resolve, OriginalFileStore files, DocumentRepository documents) {
        return new OpenOriginal(resolve, files, documents);
    }

    @Bean
    UploadDocument uploadDocument(
            DocumentRepository documents,
            OrganizationRepository organizations,
            OriginalFileStore files,
            DocumentFormatDetector detector,
            UploadSettings uploadSettings,
            IdGenerator ids,
            Clock clock) {
        return new UploadDocument(documents, organizations, files, detector, uploadSettings, ids, clock);
    }

    @Bean
    StartIntake startIntake(IntakeRepository intake, OrganizationRepository organizations, IdGenerator ids,
            Clock clock) {
        return new StartIntake(intake, organizations, ids, clock);
    }

    @Bean
    AddFileToIntake addFileToIntake(IntakeRepository intake, UploadDocument upload) {
        return new AddFileToIntake(intake, upload);
    }

    /** El proponente solo existe con odin.tagger.url: sin él, la clasificación usa solo las reglas. */
    @Bean
    ClassifyIntake classifyIntake(IntakeRepository intake, DocumentRepository documents,
            ObjectProvider<ProjectProposer> proposer, IdGenerator ids, InstallationSettings installationSettings,
            Clock clock) {
        return new ClassifyIntake(intake, documents, Optional.ofNullable(proposer.getIfAvailable()), ids,
                installationSettings, clock);
    }

    @Bean
    CloseIntakeUpload closeIntakeUpload(IntakeRepository intake) {
        return new CloseIntakeUpload(intake);
    }

    @Bean
    GetIntakeBatch getIntakeBatch(IntakeRepository intake, DocumentRepository documents,
            InstallationSettings installationSettings, IntakeSettings intakeSettings) {
        return new GetIntakeBatch(intake, documents, installationSettings, intakeSettings);
    }

    @Bean
    ImportProjectList importProjectList(IntakeRepository intake, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new ImportProjectList(intake) {
            @Override
            public List<ProjectCandidate> execute(Actor actor, CompanyId companyId, IntakeBatchId batchId,
                    byte[] content) {
                return transaction.execute(status -> super.execute(actor, companyId, batchId, content));
            }
        };
    }

    @Bean
    ListProjectCandidates listProjectCandidates(IntakeRepository intake, DocumentRepository documents) {
        return new ListProjectCandidates(intake, documents);
    }

    /** Sin transacción: lo que descubre se queda aunque falten candidatos por decidir (lo ve el administrador). */
    @Bean
    CloseProjectList closeProjectList(IntakeRepository intake, DocumentRepository documents,
            ListProjectCandidates candidates, InstallationSettings installationSettings, Clock clock) {
        return new CloseProjectList(intake, documents, candidates, installationSettings, clock);
    }

    @Bean
    ConfirmProjectCandidates confirmProjectCandidates(IntakeRepository intake, OrganizationRepository organizations,
            CreateProject createProject, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new ConfirmProjectCandidates(intake, organizations, createProject) {
            @Override
            public List<ProjectCandidate> execute(Actor actor, CompanyId companyId, IntakeBatchId batchId,
                    List<ProjectDecision> decisions) {
                return transaction.execute(status -> super.execute(actor, companyId, batchId, decisions));
            }
        };
    }

    @Bean
    IntakeEffects intakeEffects(IntakeRepository intake, DocumentRepository documents,
            OrganizationRepository organizations, MarkCaseDocuments markCaseDocuments,
            SupersedeDocument supersedeDocument, AddToCaseFiles addToCaseFiles,
            InstallationSettings installationSettings, Clock clock) {
        return new IntakeEffects(intake, documents, organizations, markCaseDocuments, supersedeDocument,
                addToCaseFiles, installationSettings, clock);
    }

    @Bean
    ListOpenIntakeBatches listOpenIntakeBatches(IntakeRepository intake, DocumentRepository documents) {
        return new ListOpenIntakeBatches(intake, documents);
    }

    @Bean
    CloseIntakeBatch closeIntakeBatch(IntakeRepository intake, Clock clock, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new CloseIntakeBatch(intake, clock) {
            @Override
            public IntakeBatch execute(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
                return transaction.execute(status -> super.execute(actor, companyId, batchId));
            }
        };
    }

    @Bean
    ListIntakeProposals listIntakeProposals(IntakeRepository intake, DocumentRepository documents) {
        return new ListIntakeProposals(intake, documents);
    }

    /** Confirmar, decidir y descartar son todo o nada: una transacción por acción. */
    @Bean
    ConfirmIntakeProposals confirmIntakeProposals(IntakeRepository intake, IntakeEffects effects,
            PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new ConfirmIntakeProposals(intake, effects) {
            @Override
            public int execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, List<UUID> proposalIds) {
                return transaction.execute(status -> super.execute(actor, companyId, batchId, proposalIds));
            }
        };
    }

    @Bean
    DecideIntakeProposal decideIntakeProposal(IntakeRepository intake, IntakeEffects effects,
            PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new DecideIntakeProposal(intake, effects) {
            @Override
            public IntakeProposal execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, UUID proposalId,
                    IntakeDecision decision, Optional<ProjectId> projectId) {
                return transaction.execute(
                        status -> super.execute(actor, companyId, batchId, proposalId, decision, projectId));
            }
        };
    }

    @Bean
    ListUnplacedDocuments listUnplacedDocuments(IntakeRepository intake, DocumentRepository documents) {
        return new ListUnplacedDocuments(intake, documents);
    }

    @Bean
    OpenIntakeOriginal openIntakeOriginal(IntakeRepository intake, DocumentRepository documents,
            OriginalFileStore files) {
        return new OpenIntakeOriginal(intake, documents, files);
    }

    @Bean
    PlaceIntakeDocument placeIntakeDocument(IntakeEffects effects, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new PlaceIntakeDocument(effects) {
            @Override
            public void execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, DocumentId documentId,
                    Optional<ProjectId> projectId, boolean companyWide) {
                transaction.executeWithoutResult(
                        status -> super.execute(actor, companyId, batchId, documentId, projectId, companyWide));
            }
        };
    }

    @Bean
    DiscardIntakeDocument discardIntakeDocument(DocumentRepository documents,
            WithdrawDocument withdrawDocument, IntakeEffects effects, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new DiscardIntakeDocument(documents, withdrawDocument, effects) {
            @Override
            public void execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, DocumentId documentId) {
                transaction.executeWithoutResult(status -> super.execute(actor, companyId, batchId, documentId));
            }
        };
    }

    @Bean
    UserNames userNames(UserRepository users) {
        return new UserNames(users);
    }

    @Bean
    ListCompanyDocuments listCompanyDocuments(DocumentRepository documents, TopicRepository topics,
            TaggingRepository tagging) {
        return new ListCompanyDocuments(documents, topics, tagging);
    }

    @Bean
    UnlinkScanCopy unlinkScanCopy(DocumentRepository documents) {
        return new UnlinkScanCopy(documents);
    }

    @Bean
    GetDocument getDocument(DocumentRepository documents) {
        return new GetDocument(documents);
    }

    @Bean
    RetryDocument retryDocument(DocumentRepository documents) {
        return new RetryDocument(documents);
    }

    @Bean
    UpdateDocument updateDocument(DocumentRepository documents, CaseFileRepository caseFiles, Clock clock) {
        return new UpdateDocument(documents, caseFiles, clock);
    }

    @Bean
    WithdrawDocument withdrawDocument(DocumentRepository documents, Clock clock) {
        return new WithdrawDocument(documents, clock);
    }

    @Bean
    RestoreDocument restoreDocument(DocumentRepository documents) {
        return new RestoreDocument(documents);
    }

    @Bean
    MarkCaseDocuments markCaseDocuments(DocumentRepository documents, CaseFileRepository caseFiles, Clock clock) {
        return new MarkCaseDocuments(documents, caseFiles, clock);
    }

    @Bean
    GetCaseFile getCaseFile(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles, UserRepository users) {
        return new GetCaseFile(organizations, projectMembers, documents, caseFiles, users);
    }

    @Bean
    ReadCaseFile readCaseFile(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles, UserRepository users,
            CaseFileSettings settings) {
        return new ReadCaseFile(organizations, projectMembers, documents, caseFiles, users, settings);
    }

    @Bean
    AddToCaseFiles addToCaseFiles(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles, IdGenerator ids, Clock clock) {
        return new AddToCaseFiles(organizations, projectMembers, documents, caseFiles, ids, clock);
    }

    @Bean
    RemoveFromCaseFile removeFromCaseFile(OrganizationRepository organizations,
            ProjectMemberRepository projectMembers, DocumentRepository documents, CaseFileRepository caseFiles,
            IdGenerator ids, Clock clock) {
        return new RemoveFromCaseFile(organizations, projectMembers, documents, caseFiles, ids, clock);
    }

    @Bean
    ListCaseFileEvents listCaseFileEvents(DocumentRepository documents, CaseFileRepository caseFiles) {
        return new ListCaseFileEvents(documents, caseFiles);
    }

    @Bean
    SupersedeDocument supersedeDocument(DocumentRepository documents, Clock clock) {
        return new SupersedeDocument(documents, clock);
    }

    @Bean
    ListCardsForReview listCardsForReview(KnowledgeCardRepository cards) {
        return new ListCardsForReview(cards);
    }

    @Bean
    ReviewCard reviewCard(KnowledgeCardRepository cards, Clock clock) {
        return new ReviewCard(cards, clock);
    }

    @Bean
    SaveKnowledgeCard saveKnowledgeCard(
            OrganizationRepository organizations,
            ProjectMemberRepository projectMembers,
            KnowledgeCardRepository cards,
            IdGenerator ids,
            Clock clock) {
        return new SaveKnowledgeCard(organizations, projectMembers, cards, ids, clock);
    }

    @Bean
    ListMyKnowledgeCards listMyKnowledgeCards(OrganizationRepository organizations,
            ProjectMemberRepository projectMembers, KnowledgeCardRepository cards) {
        return new ListMyKnowledgeCards(organizations, projectMembers, cards);
    }

    @Bean
    ListMyQuestions listMyQuestions(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            QuestionLog questions) {
        return new ListMyQuestions(organizations, projectMembers, questions);
    }

    @Bean
    DescribeMe describeMe(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            TopicRepository topics, TaggingRepository tagging) {
        return new DescribeMe(organizations, projectMembers, topics, tagging);
    }

    @Bean
    SearchKnowledge searchKnowledge(
            OrganizationRepository organizations,
            ProjectMemberRepository projectMembers,
            KnowledgeSearch search,
            QuestionLog questions,
            IdGenerator ids,
            Clock clock) {
        return new SearchKnowledge(organizations, projectMembers, search, questions, ids, clock);
    }

    @Bean
    FindSimilarCases findSimilarCases(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            KnowledgeSearch search, QuestionLog questions, IdGenerator ids, Clock clock) {
        return new FindSimilarCases(organizations, projectMembers, search, questions, ids, clock);
    }

    @Bean
    ListDocuments listDocuments(
            OrganizationRepository organizations,
            ProjectMemberRepository projectMembers,
            DocumentRepository documents,
            TopicRepository topics,
            TaggingRepository tagging) {
        return new ListDocuments(organizations, projectMembers, documents, topics, tagging);
    }

    @Bean
    ReadPage readPage(
            OrganizationRepository organizations,
            ProjectMemberRepository projectMembers,
            DocumentRepository documents) {
        return new ReadPage(organizations, projectMembers, documents);
    }

    @Bean
    VerifyCitation verifyCitation(
            OrganizationRepository organizations,
            ProjectMemberRepository projectMembers,
            DocumentRepository documents) {
        return new VerifyCitation(organizations, projectMembers, documents);
    }

    @Bean
    ProcessPendingDocuments processPendingDocuments(DocumentRepository documents, OriginalFileStore files,
            List<DocumentTextReader> readers, ProcessingSettings processingSettings, Clock clock) {
        return new ProcessPendingDocuments(documents, files, readers, processingSettings, clock);
    }

    @Bean
    SetUpInstallation setUpInstallation(OrganizationRepository organizations, UserRepository users,
            PasswordHasher passwords, IdGenerator ids, PersonCodeGenerator personCodes,
            InstallationSettings installationSettings, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new SetUpInstallation(organizations, users, passwords, ids, personCodes, installationSettings) {
            @Override
            public UserId execute(String setupToken, String groupName, String adminName, String adminEmail,
                    String password) {
                return transaction.execute(status -> super.execute(setupToken, groupName, adminName, adminEmail,
                        password));
            }
        };
    }
}
