package com.e2d.odin.bootstrap;

import com.e2d.odin.adapter.filestore.FileSystemOriginalFileStore;
import com.e2d.odin.adapter.postgres.PostgresAccessKeyRepository;
import com.e2d.odin.adapter.postgres.PostgresAdminSessionRepository;
import com.e2d.odin.adapter.postgres.PostgresDocumentRepository;
import com.e2d.odin.adapter.postgres.PostgresKnowledgeCardRepository;
import com.e2d.odin.adapter.postgres.PostgresKnowledgeSearch;
import com.e2d.odin.adapter.postgres.PostgresOrganizationRepository;
import com.e2d.odin.adapter.postgres.PostgresQuestionLog;
import com.e2d.odin.adapter.postgres.PostgresUserRepository;
import com.e2d.odin.adapter.postgres.ScopedTransactions;
import com.e2d.odin.adapter.readers.MagicBytesFormatDetector;
import com.e2d.odin.adapter.readers.PdfBoxTextReader;
import com.e2d.odin.adapter.readers.PlainTextReader;
import com.e2d.odin.adapter.readers.PoiDocxTextReader;
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
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdapterConfiguration {

    @Bean
    @DependsOnDatabaseInitialization
    ScopedTransactions scopedTransactions(DataSource dataSource) {
        DatabaseRoleGuard.check(dataSource);
        return new ScopedTransactions(dataSource);
    }

    @Bean
    OrganizationRepository organizationRepository(ScopedTransactions tx) {
        return new PostgresOrganizationRepository(tx);
    }

    @Bean
    UserRepository userRepository(ScopedTransactions tx) {
        return new PostgresUserRepository(tx);
    }

    @Bean
    AccessKeyRepository accessKeyRepository(ScopedTransactions tx) {
        return new PostgresAccessKeyRepository(tx);
    }

    @Bean
    AdminSessionRepository adminSessionRepository(ScopedTransactions tx) {
        return new PostgresAdminSessionRepository(tx);
    }

    @Bean
    DocumentRepository documentRepository(ScopedTransactions tx) {
        return new PostgresDocumentRepository(tx);
    }

    @Bean
    KnowledgeCardRepository knowledgeCardRepository(ScopedTransactions tx) {
        return new PostgresKnowledgeCardRepository(tx);
    }

    @Bean
    KnowledgeSearch knowledgeSearch(ScopedTransactions tx) {
        return new PostgresKnowledgeSearch(tx);
    }

    @Bean
    QuestionLog questionLog(ScopedTransactions tx) {
        return new PostgresQuestionLog(tx);
    }

    @Bean
    OriginalFileStore originalFileStore(@Value("${odin.originals-dir}") Path root) {
        return new FileSystemOriginalFileStore(root);
    }

    @Bean
    DocumentFormatDetector documentFormatDetector() {
        return new MagicBytesFormatDetector();
    }

    @Bean
    DocumentTextReader pdfReader() {
        return new PdfBoxTextReader();
    }

    @Bean
    DocumentTextReader docxReader() {
        return new PoiDocxTextReader();
    }

    @Bean
    DocumentTextReader plainTextReader() {
        return new PlainTextReader();
    }

    @Bean
    SecretHasher secretHasher() {
        return new Sha256SecretHasher();
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new BcryptPasswordHasher();
    }

    @Bean
    TokenGenerator tokenGenerator() {
        return new SecureRandomTokenGenerator();
    }

    @Bean
    IdGenerator idGenerator() {
        return new RandomUuidGenerator();
    }
}
