package com.e2d.odin.application.fake;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    private Fixtures() {
    }

    /** El caso de uso con repositorios de temas vacíos, para quien no los mira. */
    public static com.e2d.odin.application.organization.DescribeMe describeMe(InMemoryOrganizationRepository orgs,
            InMemoryProjectMemberRepository members) {
        InMemoryTopicRepository topics = new InMemoryTopicRepository();
        return new com.e2d.odin.application.organization.DescribeMe(orgs, members, topics,
                new InMemoryTaggingRepository(orgs, new InMemoryDocumentRepository(), topics));
    }

    public static CompanyId company(InMemoryOrganizationRepository orgs) {
        return companyWith(orgs, EnumSet.allOf(Capability.class));
    }

    public static CompanyId companyWith(InMemoryOrganizationRepository orgs, Set<Capability> capabilities) {
        CompanyId id = new CompanyId(UUID.randomUUID());
        orgs.saveCompany(new Company(id, "Empresa " + id.value(), codeOf(id), capabilities));
        return id;
    }

    /** Un código de empresa válido y distinto para cada identificador aleatorio. */
    public static String codeOf(CompanyId id) {
        return id.value().toString().substring(0, 6).toUpperCase();
    }

    /** Un proyecto cerrado, como nace: solo lo ven sus miembros. */
    public static ProjectId project(InMemoryOrganizationRepository orgs, CompanyId company) {
        return project(orgs, company, true);
    }

    public static ProjectId companyWideProject(InMemoryOrganizationRepository orgs, CompanyId company) {
        return project(orgs, company, false);
    }

    private static ProjectId project(InMemoryOrganizationRepository orgs, CompanyId company, boolean restricted) {
        ProjectId id = new ProjectId(UUID.randomUUID());
        orgs.saveProject(new Project(id, company, "Proyecto", restricted));
        return id;
    }

    public static Document documentIn(InMemoryDocumentRepository docs, CompanyId company, ProjectId project,
            String pageText) {
        Document d = readyDocument(docs, company, pageText);
        Document inProject = new Document(d.id(), company, Optional.of(project), d.title(), d.filename(), d.format(),
                d.sha256(), d.status(), d.failureReason(), d.paginated(), d.uploadedBy(), d.uploadedAt(),
                d.processingStartedAt(), d.attemptCount(), Optional.empty(), Optional.empty());
        docs.save(inProject);
        return inProject;
    }

    /** Una persona con su pertenencia (activa o no) a una empresa, guardada en los dobles en memoria. */
    public static UserId person(InMemoryUserRepository users, InMemoryMembershipRepository memberships, String name,
            Role role, CompanyId company, boolean active) {
        UserId id = new UserId(UUID.randomUUID());
        People.member(users, memberships, id, name, Optional.empty(), role, company, active, Optional.empty());
        return id;
    }

    public static Document document(InMemoryDocumentRepository docs, CompanyId company, DocumentStatus status) {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), "Manual", "manual.pdf",
                DocumentFormat.PDF, UUID.randomUUID().toString(), status, Optional.empty(), true,
                new UserId(UUID.randomUUID()), NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(d);
        return d;
    }

    public static Document titledDocument(InMemoryDocumentRepository docs, CompanyId company, String title) {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), title, "t.pdf",
                DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(), true,
                new UserId(UUID.randomUUID()), NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(d);
        return d;
    }

    public static Document readyDocument(InMemoryDocumentRepository docs, CompanyId company, String pageText) {
        Document d = document(docs, company, DocumentStatus.READY);
        docs.replacePages(d.id(), java.util.List.of(new DocumentPage(d.id(), 1, pageText, "stub", "1")));
        return d;
    }

    public static KnowledgeCard card(InMemoryKnowledgeCardRepository cards, CompanyId company, CardStatus status) {
        return card(cards, company, status, "Titulo", "Cuerpo original");
    }

    public static KnowledgeCard card(InMemoryKnowledgeCardRepository cards, CompanyId company, CardStatus status,
            String title, String body) {
        KnowledgeCard c = new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.empty(), title,
                body, Optional.empty(), new UserId(UUID.randomUUID()), NOW, status, Optional.empty());
        cards.save(c);
        return c;
    }
}
