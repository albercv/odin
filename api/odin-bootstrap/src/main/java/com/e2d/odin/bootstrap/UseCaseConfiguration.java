package com.e2d.odin.bootstrap;

import com.e2d.odin.application.document.GetDocument;
import com.e2d.odin.application.document.ListCompanyDocuments;
import com.e2d.odin.application.document.ProcessPendingDocuments;
import com.e2d.odin.application.document.RetryDocument;
import com.e2d.odin.application.document.UploadDocument;
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
import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.application.port.DocumentFormatDetector;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.application.port.KnowledgeSearch;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.application.port.PasswordHasher;
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
    ResolveActor resolveActor(
            AccessKeyRepository keys,
            UserRepository users,
            AdminSessionRepository sessions,
            SecretHasher secrets,
            SessionSettings sessionSettings,
            Clock clock) {
        return new ResolveActor(keys, users, sessions, secrets, sessionSettings, clock);
    }

    @Bean
    LogInAdmin logInAdmin(
            UserRepository users,
            AdminSessionRepository sessions,
            PasswordHasher passwords,
            SecretHasher secrets,
            TokenGenerator tokens,
            Clock clock) {
        return new LogInAdmin(users, sessions, passwords, secrets, tokens, clock);
    }

    @Bean
    LogOutAdmin logOutAdmin(AdminSessionRepository sessions, SecretHasher secrets) {
        return new LogOutAdmin(sessions, secrets);
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
    RegisterUser registerUser(
            UserRepository users,
            OrganizationRepository organizations,
            PasswordHasher passwords,
            IdGenerator ids) {
        return new RegisterUser(users, organizations, passwords, ids);
    }

    @Bean
    ListUsers listUsers(UserRepository users) {
        return new ListUsers(users);
    }

    @Bean
    DeactivateUser deactivateUser(UserRepository users) {
        return new DeactivateUser(users);
    }

    @Bean
    IssueAccessKey issueAccessKey(
            AccessKeyRepository keys,
            UserRepository users,
            SecretHasher secrets,
            TokenGenerator tokens,
            IdGenerator ids,
            Clock clock) {
        return new IssueAccessKey(keys, users, secrets, tokens, ids, clock);
    }

    @Bean
    ListAccessKeys listAccessKeys(AccessKeyRepository keys, UserRepository users) {
        return new ListAccessKeys(keys, users);
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
            KnowledgeCardRepository cards,
            IdGenerator ids,
            Clock clock) {
        return new SaveKnowledgeCard(organizations, cards, ids, clock);
    }

    @Bean
    DescribeMe describeMe(OrganizationRepository organizations) {
        return new DescribeMe(organizations);
    }

    @Bean
    SearchKnowledge searchKnowledge(
            OrganizationRepository organizations,
            KnowledgeSearch search,
            QuestionLog questions,
            IdGenerator ids,
            Clock clock) {
        return new SearchKnowledge(organizations, search, questions, ids, clock);
    }

    @Bean
    ListDocuments listDocuments(
            OrganizationRepository organizations,
            DocumentRepository documents) {
        return new ListDocuments(organizations, documents);
    }

    @Bean
    ReadPage readPage(
            OrganizationRepository organizations,
            DocumentRepository documents) {
        return new ReadPage(organizations, documents);
    }

    @Bean
    VerifyCitation verifyCitation(
            OrganizationRepository organizations,
            DocumentRepository documents) {
        return new VerifyCitation(organizations, documents);
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
