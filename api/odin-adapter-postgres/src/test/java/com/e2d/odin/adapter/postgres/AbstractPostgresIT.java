package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;

abstract class AbstractPostgresIT {

    static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    final ScopedTransactions tx = new ScopedTransactions(Db.app());
    final PostgresOrganizationRepository orgs = new PostgresOrganizationRepository(tx);
    final PostgresUserRepository users = new PostgresUserRepository(tx);
    final PostgresMembershipRepository memberships = new PostgresMembershipRepository(tx);
    final PostgresDocumentRepository docs = new PostgresDocumentRepository(tx);
    final PostgresKnowledgeCardRepository cards = new PostgresKnowledgeCardRepository(tx);
    final PostgresProjectMemberRepository projectMembers = new PostgresProjectMemberRepository(tx);

    @BeforeEach
    void cleanDatabase() {
        Db.truncate();
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** Un código de empresa válido y distinto en cada llamada. */
    static String uniqueCode() {
        return "C" + (10000 + SEQUENCE.incrementAndGet());
    }

    /** Una persona con apellidos y un código de persona distinto en cada llamada. */
    static User person(UserId id) {
        return new User(id, "Persona", Optional.of("Pérez"), "P" + (1000 + SEQUENCE.incrementAndGet()),
                Optional.empty(), false, true, Optional.empty());
    }

    CompanyId newCompany() {
        CompanyId id = new CompanyId(UUID.randomUUID());
        orgs.saveCompany(new Company(id, "Empresa " + id.value(), uniqueCode(), Set.of(Capability.SEARCH_KNOWLEDGE)));
        return id;
    }

    UserId newUser(CompanyId company) {
        UserId id = new UserId(UUID.randomUUID());
        users.save(person(id));
        memberships.save(new Membership(id, company, Set.of(Role.WORKER), true));
        return id;
    }

    ProjectId newProject(CompanyId company, String name) {
        ProjectId id = new ProjectId(UUID.randomUUID());
        orgs.saveProject(new Project(id, company, name, true));
        return id;
    }

    Document newDocument(CompanyId company, UserId uploader, String title, DocumentStatus status) {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), title, "f.pdf",
                DocumentFormat.PDF, UUID.randomUUID().toString(), status, Optional.empty(), true, uploader, NOW,
                Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(d);
        return d;
    }

    /** Pone una marca saltándose la regla de proyecto de {@code markSuperseded}: la base solo exige la misma empresa. */
    void forceMark(Document old, Document replacement) {
        JdbcClient.create(Db.owner()).sql("UPDATE document SET superseded_by = :r, superseded_at = now() WHERE id = :id")
                .param("r", replacement.id().value()).param("id", old.id().value()).update();
    }

    /** Dato corrupto: una marca entre empresas, que la FK compuesta impide. Se quita la FK y se vuelve a poner sin validar. */
    void forceMarkAcrossCompanies(Document old, Document foreign) {
        JdbcClient jdbc = JdbcClient.create(Db.owner());
        jdbc.sql("ALTER TABLE document DROP CONSTRAINT document_superseded_by_same_company_fk").update();
        forceMark(old, foreign);
        jdbc.sql("ALTER TABLE document ADD CONSTRAINT document_superseded_by_same_company_fk FOREIGN KEY "
                + "(superseded_by, company_id) REFERENCES document (id, company_id) NOT VALID").update();
    }
}
