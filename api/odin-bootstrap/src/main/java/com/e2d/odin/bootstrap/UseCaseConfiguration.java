package com.e2d.odin.bootstrap;

import com.e2d.odin.application.document.GetDocument;
import com.e2d.odin.application.document.ListCompanyDocuments;
import com.e2d.odin.application.document.ProcessPendingDocuments;
import com.e2d.odin.application.document.RetryDocument;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.identity.AddMember;
import com.e2d.odin.application.identity.ChangePassword;
import com.e2d.odin.application.identity.DeleteUser;
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
import com.e2d.odin.application.organization.RenameCompany;
import com.e2d.odin.application.organization.SetCompanyCapabilities;
import com.e2d.odin.application.organization.UpdateProject;
import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.DocumentFormatDetector;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.application.port.KnowledgeSearch;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.QuestionLog;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.TokenGenerator;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.search.ListDocuments;
import com.e2d.odin.application.search.ReadPage;
import com.e2d.odin.application.search.SearchKnowledge;
import com.e2d.odin.application.search.VerifyCitation;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.ProcessingSettings;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.util.List;
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
    RenameCompany renameCompany(OrganizationRepository organizations) {
        return new RenameCompany(organizations);
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
    ListMembers listMembers(UserRepository users, MembershipRepository memberships) {
        return new ListMembers(users, memberships);
    }

    @Bean
    RemoveMember removeMember(MembershipRepository memberships, AccessKeyRepository keys,
            ProjectMemberRepository projectMembers, AdminSessions sessions, Clock clock, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new RemoveMember(memberships, keys, projectMembers, sessions, clock) {
            @Override
            public void execute(Actor actor, CompanyId companyId, UserId userId) {
                transaction.executeWithoutResult(status -> super.execute(actor, companyId, userId));
            }
        };
    }

    @Bean
    UpdateUser updateUser(UserRepository users, MembershipRepository memberships, AdminSessions sessions) {
        return new UpdateUser(users, memberships, sessions);
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
    DeleteUser deleteUser(UserRepository users, MembershipRepository memberships, AdminSessions sessions,
            Clock clock, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new DeleteUser(users, memberships, sessions, clock) {
            @Override
            public void execute(Actor actor, UserId id) {
                transaction.executeWithoutResult(status -> super.execute(actor, id));
            }
        };
    }

    @Bean
    AddMember addMember(UserRepository users, MembershipRepository memberships,
            OrganizationRepository organizations, ProjectMemberRepository projectMembers, PasswordHasher passwords,
            IdGenerator ids, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new AddMember(users, memberships, organizations, projectMembers, passwords, ids) {
            @Override
            public MemberAdded execute(Actor actor, CompanyId companyId, NewMember newMember) {
                return transaction.execute(status -> super.execute(actor, companyId, newMember));
            }
        };
    }

    @Bean
    UpdateMember updateMember(UserRepository users, MembershipRepository memberships, PasswordHasher passwords,
            AdminSessions sessions, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new UpdateMember(users, memberships, passwords, sessions) {
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
    RevokeAccessKey revokeAccessKey(AccessKeyRepository keys, UserRepository users, Clock clock) {
        return new RevokeAccessKey(keys, users, clock);
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
    ListCompanyDocuments listCompanyDocuments(DocumentRepository documents) {
        return new ListCompanyDocuments(documents);
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
    DescribeMe describeMe(OrganizationRepository organizations, ProjectMemberRepository projectMembers) {
        return new DescribeMe(organizations, projectMembers);
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
    ListDocuments listDocuments(
            OrganizationRepository organizations,
            ProjectMemberRepository projectMembers,
            DocumentRepository documents) {
        return new ListDocuments(organizations, projectMembers, documents);
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
            PasswordHasher passwords, IdGenerator ids, InstallationSettings installationSettings,
            PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return new SetUpInstallation(organizations, users, passwords, ids, installationSettings) {
            @Override
            public UserId execute(String setupToken, String groupName, String adminName, String adminEmail,
                    String password) {
                return transaction.execute(status -> super.execute(setupToken, groupName, adminName, adminEmail,
                        password));
            }
        };
    }
}
