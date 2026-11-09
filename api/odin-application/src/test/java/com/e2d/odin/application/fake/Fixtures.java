package com.e2d.odin.application.fake;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
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

    public static CompanyId company(InMemoryOrganizationRepository orgs) {
        return companyWith(orgs, EnumSet.allOf(Capability.class));
    }

    public static CompanyId companyWith(InMemoryOrganizationRepository orgs, Set<Capability> capabilities) {
        CompanyId id = new CompanyId(UUID.randomUUID());
        orgs.saveCompany(new Company(id, "Empresa " + id.value(), capabilities));
        return id;
    }

    public static ProjectId project(InMemoryOrganizationRepository orgs, CompanyId company) {
        ProjectId id = new ProjectId(UUID.randomUUID());
        orgs.saveProject(new Project(id, company, "Proyecto"));
        return id;
    }

    public static Document document(InMemoryDocumentRepository docs, CompanyId company, DocumentStatus status) {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), "Manual", "manual.pdf",
                DocumentFormat.PDF, UUID.randomUUID().toString(), status, Optional.empty(), true,
                new UserId(UUID.randomUUID()), NOW, Optional.empty(), 0);
        docs.save(d);
        return d;
    }

    public static Document readyDocument(InMemoryDocumentRepository docs, CompanyId company, String pageText) {
        Document d = document(docs, company, DocumentStatus.READY);
        docs.replacePages(d.id(), java.util.List.of(new DocumentPage(d.id(), 1, pageText, "stub", "1")));
        return d;
    }

    public static KnowledgeCard card(InMemoryKnowledgeCardRepository cards, CompanyId company, CardStatus status) {
        KnowledgeCard c = new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.empty(), "Titulo",
                "Cuerpo original", Optional.empty(), new UserId(UUID.randomUUID()), NOW, status, Optional.empty());
        cards.save(c);
        return c;
    }
}
