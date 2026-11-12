package com.e2d.odin.adapter.web.dto;

import com.e2d.odin.application.document.UploadOutcome;
import com.e2d.odin.application.identity.IssuedKey;
import com.e2d.odin.application.organization.MeView;
import com.e2d.odin.application.search.PageView;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.knowledge.CardReview;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.organization.ProjectSummary;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Respuestas de la API. Nunca incluyen huellas de contraseñas, de claves ni de sesiones. Todo campo sin
 * {@code @Nullable} se publica como obligatorio en el contrato OpenAPI ({@code WebConfig}).
 */
public final class Responses {

    private Responses() {
    }

    public record Health(String status) {
    }

    public record UserRef(UUID userId) {
    }

    public record CompanyRef(UUID id, String name, String code, boolean codeReviewed) {
        public static CompanyRef from(Company c) {
            return new CompanyRef(c.id().value(), c.name(), c.code(), c.codeReviewed());
        }
    }

    /** {@code companyId} solo viene si administra una única empresa: el panel entra directamente en ella. */
    public record AdminMe(UUID userId, String displayName, String firstName, @Nullable String lastName,
            String personCode, Role role, @Nullable UUID companyId, List<CompanyRef> companies) {
        public static AdminMe from(Actor a, User person, List<Company> administered) {
            UUID onlyCompany = administered.size() == 1 && !a.isGroupAdmin() ? administered.get(0).id().value() : null;
            return new AdminMe(a.userId().value(), a.displayName(), person.firstName(), person.lastName().orElse(null),
                    person.personCode(), a.role(), onlyCompany,
                    administered.stream().map(CompanyRef::from).toList());
        }
    }

    public record CompanyView(UUID id, String name, String code, Set<Capability> capabilities, boolean codeReviewed) {
        public static CompanyView from(Company c) {
            return new CompanyView(c.id().value(), c.name(), c.code(), c.capabilities(), c.codeReviewed());
        }
    }

    /** La empresa tal como la ve un trabajador: sin código, que es cosa del panel de administración. */
    public record MeCompany(UUID id, String name, Set<Capability> capabilities) {
        public static MeCompany from(Company c) {
            return new MeCompany(c.id().value(), c.name(), c.capabilities());
        }
    }

    /** El proyecto tal como lo ve un trabajador: solo id y nombre; si es cerrado y quién es miembro es cosa del panel. */
    public record MeProject(UUID id, String name) {
        public static MeProject from(Project p) {
            return new MeProject(p.id().value(), p.name());
        }
    }

    public record ProjectView(UUID id, UUID companyId, String name, boolean restricted,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Solo en la lista de proyectos de la empresa.") @Nullable Integer memberCount) {
        /** Sin recuento: solo la lista de proyectos de la empresa lo calcula. */
        public static ProjectView from(Project p) {
            return new ProjectView(p.id().value(), p.companyId().value(), p.name(), p.restricted(), null);
        }

        public static ProjectView from(ProjectSummary s) {
            Project p = s.project();
            return new ProjectView(p.id().value(), p.companyId().value(), p.name(), p.restricted(), s.memberCount());
        }
    }

    public record ProjectMemberView(UUID userId, String displayName) {
        public static ProjectMemberView from(com.e2d.odin.application.organization.ProjectMemberView m) {
            return new ProjectMemberView(m.userId().value(), m.displayName());
        }
    }

    public record Me(UUID userId, String displayName, Role role, MeCompany company, List<MeProject> projects) {
        public static Me from(MeView m) {
            return new Me(m.userId().value(), m.displayName(), m.role(), MeCompany.from(m.company()),
                    m.projects().stream().map(MeProject::from).toList());
        }
    }

    public record MemberView(UUID userId, String displayName, String firstName, @Nullable String lastName,
            String personCode, String memberCode, @Nullable String email, Set<Role> roles, boolean active,
            boolean personActive, boolean hasPassword) {
        public static MemberView from(com.e2d.odin.application.identity.MemberView m) {
            return new MemberView(m.userId().value(), m.displayName(), m.firstName(), m.lastName().orElse(null),
                    m.personCode(), m.memberCode(), m.email().orElse(null), m.roles(), m.active(), m.personActive(),
                    m.hasPassword());
        }
    }

    public record MemberAdded(MemberView member, boolean alreadyExisted, boolean keptPassword) {
        public static MemberAdded from(com.e2d.odin.application.identity.MemberAdded added, Actor viewer) {
            return new MemberAdded(MemberView.from(com.e2d.odin.application.identity.MemberView.of(added.user(),
                    added.membership(), added.company().code(), viewer)), added.alreadyExisted(),
                    added.keptPassword());
        }
    }

    public record GroupAdminView(UUID userId, String firstName, @Nullable String lastName, String displayName,
            @Nullable String email, String personCode, boolean active) {
        public static GroupAdminView from(User u) {
            return new GroupAdminView(u.id().value(), u.firstName(), u.lastName().orElse(null), u.displayName(),
                    u.email().orElse(null), u.personCode(), u.active());
        }
    }

    public record GroupAdminAdded(GroupAdminView admin, boolean alreadyExisted, boolean keptPassword) {
        public static GroupAdminAdded from(com.e2d.odin.application.identity.GroupAdminAdded added) {
            return new GroupAdminAdded(GroupAdminView.from(added.admin()), added.alreadyExisted(),
                    added.keptPassword());
        }
    }

    public record KeyView(UUID id, String label, String prefix, Instant createdAt, @Nullable Instant revokedAt) {
        public static KeyView from(AccessKey k) {
            return new KeyView(k.id().value(), k.label(), k.prefix(), k.createdAt(), k.revokedAt().orElse(null));
        }
    }

    public record IssuedKeyView(UUID id, String prefix, String key) {
        public static IssuedKeyView from(IssuedKey k) {
            return new IssuedKeyView(k.id().value(), k.prefix(), k.plainKey());
        }
    }

    public record DocumentView(UUID id, UUID companyId, @Nullable UUID projectId, String title, String filename,
            String format, String status, @Nullable String failureReason, boolean paginated, Instant uploadedAt,
            int attemptCount) {
        public static DocumentView from(Document d) {
            return new DocumentView(d.id().value(), d.companyId().value(),
                    d.projectId().map(p -> p.value()).orElse(null), d.title(), d.filename(), d.format().name(),
                    d.status().name(), d.failureReason().orElse(null), d.paginated(), d.uploadedAt(),
                    d.attemptCount());
        }
    }

    /** Vista del trabajador: solo documentos listos y sin datos internos del proceso. */
    public record WorkerDocumentView(UUID id, @Nullable UUID projectId, String title, String filename, String format,
            boolean paginated, Instant uploadedAt) {
        public static WorkerDocumentView from(Document d) {
            return new WorkerDocumentView(d.id().value(), d.projectId().map(p -> p.value()).orElse(null), d.title(),
                    d.filename(), d.format().name(), d.paginated(), d.uploadedAt());
        }
    }

    public record Upload(DocumentView document, boolean duplicate) {
        public static Upload from(UploadOutcome o) {
            return new Upload(DocumentView.from(o.document()), o.duplicate());
        }
    }

    public record ReviewView(UUID reviewerId, String decision, Instant reviewedAt, @Nullable String note) {
        static ReviewView from(CardReview r) {
            return new ReviewView(r.reviewerId().value(), r.decision().name(), r.reviewedAt(), r.note().orElse(null));
        }
    }

    public record CardView(UUID id, UUID companyId, @Nullable UUID projectId, String title, String body,
            @Nullable String originalBody, UUID authorId, Instant createdAt, String status, @Nullable ReviewView review) {
        public static CardView from(KnowledgeCard c) {
            return new CardView(c.id().value(), c.companyId().value(), c.projectId().map(p -> p.value()).orElse(null),
                    c.title(), c.body(), c.originalBody().orElse(null), c.authorId().value(), c.createdAt(),
                    c.status().name(), c.review().map(ReviewView::from).orElse(null));
        }
    }

    public record SavedCard(UUID id, String status, String message) {
        public static SavedCard from(KnowledgeCard c) {
            return new SavedCard(c.id().value(), c.status().name(), "Ficha guardada; queda pendiente de revisión.");
        }
    }

    public record Hit(String sourceType, UUID sourceId, String title, @Nullable Integer pageNumber, boolean paginated,
            @Nullable UUID projectId, String authorName, Instant date, String excerpt, double rank) {
        static Hit from(SearchHit h) {
            return new Hit(h.sourceType().name(), h.sourceId(), h.title(), h.pageNumber().orElse(null),
                    h.paginated(), h.projectId().map(p -> p.value()).orElse(null), h.authorName(), h.date(),
                    h.excerpt(), h.rank());
        }
    }

    public record SearchResponse(List<Hit> hits, boolean noSource) {
        public static SearchResponse from(SearchResult r) {
            return new SearchResponse(r.hits().stream().map(Hit::from).toList(), r.hits().isEmpty());
        }
    }

    public record Page(UUID documentId, String title, int pageNumber, boolean paginated, String text) {
        public static Page from(PageView v) {
            return new Page(v.document().id().value(), v.document().title(), v.page().number(),
                    v.document().paginated(), v.page().text());
        }
    }

    public record Verification(boolean verified) {
    }
}
