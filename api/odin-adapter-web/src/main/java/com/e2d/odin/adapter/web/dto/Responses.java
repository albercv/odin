package com.e2d.odin.adapter.web.dto;

import com.e2d.odin.application.casefile.CaseFileText;
import com.e2d.odin.application.casefile.CaseFileView;
import com.e2d.odin.application.document.ScanLinks;
import com.e2d.odin.application.document.UploadOutcome;
import com.e2d.odin.application.identity.IssuedKey;
import com.e2d.odin.application.organization.MeView;
import com.e2d.odin.application.search.PageView;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.application.topic.DocumentTopicLine;
import com.e2d.odin.application.topic.DocumentTopicsReport;
import com.e2d.odin.application.topic.TaggedDocument;
import com.e2d.odin.application.topic.TaggingStatusReport;
import com.e2d.odin.application.topic.TopicLabel;
import com.e2d.odin.application.usage.CardCounts;
import com.e2d.odin.application.usage.CompanyUsage;
import com.e2d.odin.application.usage.DeliveredVolumeReport;
import com.e2d.odin.application.usage.DeliveryTotal;
import com.e2d.odin.application.usage.Disclosure;
import com.e2d.odin.application.usage.DocumentCounts;
import com.e2d.odin.application.usage.GapGroup;
import com.e2d.odin.application.usage.GapReport;
import com.e2d.odin.application.usage.PersonUsage;
import com.e2d.odin.application.usage.QuestionCounts;
import com.e2d.odin.application.usage.TaggingCounts;
import com.e2d.odin.application.usage.UsageReport;
import com.e2d.odin.application.casefile.ListCaseFileEvents;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentRef;
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
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.search.SimilarCasesResult;
import com.e2d.odin.domain.search.SimilarCase;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.DocumentTagging;
import com.e2d.odin.domain.topic.DocumentTopic;
import com.e2d.odin.domain.topic.Topic;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.e2d.odin.domain.shared.ProjectId;
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
            String personCode, Role role, @Nullable UUID companyId, List<CompanyRef> companies,
            boolean scannedDocumentsSupported) {
        public static AdminMe from(Actor a, User person, List<Company> administered, boolean scannedDocumentsSupported) {
            UUID onlyCompany = administered.size() == 1 && !a.isGroupAdmin() ? administered.get(0).id().value() : null;
            return new AdminMe(a.userId().value(), a.displayName(), person.firstName(), person.lastName().orElse(null),
                    person.personCode(), a.role(), onlyCompany,
                    administered.stream().map(CompanyRef::from).toList(), scannedDocumentsSupported);
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

    public record TopicView(UUID id, String name, String definition) {
        public static TopicView from(Topic t) {
            return new TopicView(t.id().value(), t.name(), t.definition());
        }
    }

    public record ProjectMemberView(UUID userId, String displayName) {
        public static ProjectMemberView from(com.e2d.odin.application.organization.ProjectMemberView m) {
            return new ProjectMemberView(m.userId().value(), m.displayName());
        }
    }

    /** Documentos visibles para quien pregunta que aún no tienen sus temas al día: una lista por tema puede faltar. */
    public record MeTagging(int pendingDocuments) {
    }

    public record Me(UUID userId, String displayName, Role role, MeCompany company, List<MeProject> projects,
            List<TopicView> topics, MeTagging tagging) {
        public static Me from(MeView m) {
            return new Me(m.userId().value(), m.displayName(), m.role(), MeCompany.from(m.company()),
                    m.projects().stream().map(MeProject::from).toList(),
                    m.topics().stream().map(TopicView::from).toList(), new MeTagging(m.taggingPending()));
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

    /** El sustituto directo de un documento sustituido, como lo ve el panel. */
    public record DocumentLink(UUID id, String title) {
        static DocumentLink from(DocumentRef ref) {
            return new DocumentLink(ref.id().value(), ref.title());
        }
    }

    /** Un tema activo de un documento en las listas: quién lo puso, la IA o el administrador. */
    public record TopicLabelView(UUID id, String name, String source) {
        static TopicLabelView from(TopicLabel label) {
            return new TopicLabelView(label.id().value(), label.name(), label.source().name());
        }
    }

    /** Una página de la lista de documentos del administrador y cuántos hay en total con ese filtro. */
    public record DocumentPage(List<DocumentView> documents, int total) {
    }

    /**
     * {@code taggingStatus}, {@code topics} y {@code uploadedByName} solo van completos en la lista de la empresa; en el
     * resto, nulo y vacío.
     */
    public record DocumentView(UUID id, UUID companyId, @Nullable UUID projectId, String title, String filename,
            String format, String status, @Nullable String failureReason, boolean paginated, Instant uploadedAt,
            int attemptCount, @Nullable DocumentLink supersededBy, @Nullable Instant supersededAt,
            List<Integer> pagesWithoutText, @Nullable String taggingStatus, List<TopicLabelView> topics,
            boolean caseDocument, @Nullable Instant withdrawnAt,
            @Schema(description = "Nombre de quien lo subió. Solo en el listado; nulo en el resto de respuestas, o si la persona ya no existe.")
            @Nullable String uploadedByName,
            @Schema(description = "De quién es copia escaneada este documento. Solo en el listado y la ficha.")
            @Nullable ScanLink scanOf,
            @Schema(description = "Escaneos enlazados a este documento. Solo en el listado y la ficha; vacío en el resto.")
            List<ScanLink> scanCopies) {
        public static DocumentView from(DocumentEntry entry) {
            return from(new TaggedDocument(entry, Optional.empty(), List.of()));
        }

        public static DocumentView from(TaggedDocument tagged) {
            return from(tagged, Map.of());
        }

        /** El nombre de quien subió va solo en la lista de la empresa; en el resto, nulo. */
        public static DocumentView from(TaggedDocument tagged, Map<UserId, String> names) {
            DocumentEntry entry = tagged.entry();
            Document d = entry.document();
            return new DocumentView(d.id().value(), d.companyId().value(),
                    d.projectId().map(p -> p.value()).orElse(null), d.title(), d.filename(), d.format().name(),
                    d.status().name(), d.failureReason().orElse(null), d.paginated(), d.uploadedAt(),
                    d.attemptCount(), entry.supersededBy().map(DocumentLink::from).orElse(null),
                    d.supersededAt().orElse(null), d.pagesWithoutText(),
                    tagged.tagging().map(t -> t.status().name()).orElse(null),
                    tagged.topics().stream().map(TopicLabelView::from).toList(), d.caseDocument(),
                    d.withdrawnAt().orElse(null), names.get(d.uploadedBy()),
                    tagged.scanLinks().scanOf().map(ScanLink::from).orElse(null),
                    ScanLink.from(tagged.scanLinks().scanCopies()));
        }
    }

    public record DocumentTopicView(UUID topicId, String name, String source, String state,
            @Nullable String taggerVersion, @Nullable String decidedByName, @Nullable Instant decidedAt) {
        static DocumentTopicView from(DocumentTopicLine line) {
            DocumentTopic t = line.topic();
            return new DocumentTopicView(t.topicId().value(), line.topicName(), t.source().name(), t.state().name(),
                    t.taggerVersion().orElse(null), line.decidedByName().orElse(null), t.decidedAt().orElse(null));
        }
    }

    public record TaggingInfo(String status, int topicListVersion, @Nullable String failureReason) {
        static TaggingInfo from(DocumentTagging t) {
            return new TaggingInfo(t.status().name(), t.topicListVersion(), t.failureReason().orElse(null));
        }
    }

    public record DocumentTopicsResponse(List<DocumentTopicView> topics, @Nullable TaggingInfo tagging) {
        public static DocumentTopicsResponse from(DocumentTopicsReport report) {
            return new DocumentTopicsResponse(report.topics().stream().map(DocumentTopicView::from).toList(),
                    report.tagging().map(TaggingInfo::from).orElse(null));
        }
    }

    /** Con {@code always}, {@code from} y {@code to} van nulos. */
    public record TaggingWindowView(@Nullable String from, @Nullable String to, boolean always) {
        static TaggingWindowView from(TaggingWindow w) {
            return w.always() ? new TaggingWindowView(null, null, true)
                    : new TaggingWindowView(w.from().toString(), w.to().toString(), false);
        }
    }

    public record TaggingStatusView(int pendingDocuments, @Nullable Instant oldestPendingSince,
            TaggingWindowView window, String timezone) {
        public static TaggingStatusView from(TaggingStatusReport report) {
            return new TaggingStatusView(report.backlog().pendingDocuments(),
                    report.backlog().oldestPendingSince().orElse(null), TaggingWindowView.from(report.window()),
                    report.window().zone().getId());
        }
    }

    public record QuestionCountsView(int search, int similarCases) {
        static QuestionCountsView from(QuestionCounts c) {
            return new QuestionCountsView(c.search(), c.similarCases());
        }
    }

    /** Con {@code withheld}, el desglose va nulo: hubo pocos autores para enseñarlo sin deducir nada. */
    public record CardCountsView(int saved, int pending, @Nullable Integer approved, @Nullable Integer corrected,
            @Nullable Integer rejected, boolean withheld, @Nullable String reason) {
        static CardCountsView from(CardCounts c) {
            return new CardCountsView(c.saved(), c.pending(), c.decisions().map(d -> d.approved()).orElse(null),
                    c.decisions().map(d -> d.corrected()).orElse(null),
                    c.decisions().map(d -> d.rejected()).orElse(null), c.withheld(),
                    c.withheld() ? Disclosure.WITHHELD_REASON : null);
        }
    }

    public record DocumentCountsView(int uploaded, int ready, int failed, int waitingForReader) {
        static DocumentCountsView from(DocumentCounts c) {
            return new DocumentCountsView(c.uploaded(), c.ready(), c.failed(), c.waitingForReader());
        }
    }

    public record TaggingCountsView(int tagged, int pending) {
        static TaggingCountsView from(TaggingCounts c) {
            return new TaggingCountsView(c.tagged(), c.pending());
        }
    }

    /** {@code tagging} falta si la empresa no tiene temas. */
    public record CompanyUsageView(QuestionCountsView questions, int questionsWithoutResults, CardCountsView cards,
            DocumentCountsView documents, @Nullable TaggingCountsView tagging) {
        static CompanyUsageView from(CompanyUsage u) {
            return new CompanyUsageView(QuestionCountsView.from(u.questions()), u.questionsWithoutResults(),
                    CardCountsView.from(u.cards()), DocumentCountsView.from(u.documents()),
                    u.tagging().map(TaggingCountsView::from).orElse(null));
        }
    }

    /** Solo dos recuentos por persona. La fila de bajas va sin {@code userId} ni {@code memberCode}. */
    public record PersonUsageView(@Nullable UUID userId, String displayName, @Nullable String memberCode, int questions,
            int cardsSaved) {
        static PersonUsageView from(PersonUsage p) {
            return new PersonUsageView(p.userId().map(id -> id.value()).orElse(null), p.displayName(),
                    p.memberCode().orElse(null), p.questions(), p.cardsSaved());
        }
    }

    public record UsageStatsView(String month, CompanyUsageView company, List<PersonUsageView> people) {
        public static UsageStatsView from(UsageReport report) {
            return new UsageStatsView(report.month().toString(), CompanyUsageView.from(report.company()),
                    report.people().stream().map(PersonUsageView::from).toList());
        }
    }

    /** Un hueco: sin autor y con el día, sin la hora. */
    public record GapView(String text, String kind, int times, LocalDate lastAskedOn) {
        public static GapView from(GapGroup g) {
            return new GapView(g.text(), g.kind().name(), g.times(), g.lastAskedOn());
        }
    }

    /** Con {@code withheld}, sin huecos: los preguntaron muy pocas personas. */
    public record GapsView(boolean withheld, @Nullable String reason, List<GapView> gaps) {
        public static GapsView from(GapReport report) {
            return new GapsView(report.withheld(), report.withheld() ? Disclosure.WITHHELD_REASON : null,
                    report.groups().stream().map(GapView::from).toList());
        }
    }

    public record DeliveryTotalView(String tool, long calls, long bytes) {
        static DeliveryTotalView from(DeliveryTotal t) {
            return new DeliveryTotalView(t.tool().name(), t.calls(), t.bytes());
        }
    }

    /** Volumen entregado a la IA del trabajador: por herramienta, nunca por persona. */
    public record DeliveredVolumeView(String month, List<DeliveryTotalView> totals) {
        public static DeliveredVolumeView from(DeliveredVolumeReport report) {
            return new DeliveredVolumeView(report.month().toString(),
                    report.totals().stream().map(DeliveryTotalView::from).toList());
        }
    }

    /** El vigente del final de la cadena, como lo ve el trabajador. */
    public record Replacement(UUID documentId, String title) {
        static Replacement from(DocumentRef ref) {
            return new Replacement(ref.id().value(), ref.title());
        }
    }

    /** Un documento enlazado como «copia escaneada» (D3): el principal o uno de sus escaneos. */
    public record ScanLink(UUID documentId, String title) {
        static ScanLink from(DocumentRef ref) {
            return new ScanLink(ref.id().value(), ref.title());
        }

        static List<ScanLink> from(List<DocumentRef> refs) {
            return refs.stream().map(ScanLink::from).toList();
        }
    }

    /** Vista del trabajador: solo documentos listos y sin datos internos del proceso. */
    public record WorkerDocumentView(UUID id, @Nullable UUID projectId, String title, String filename, String format,
            boolean paginated, Instant uploadedAt, boolean superseded, @Nullable Replacement replacement,
            List<TopicLabelView> topics, boolean caseDocument, List<ScanLink> scanCopies) {
        public static WorkerDocumentView from(TaggedDocument tagged) {
            Document d = tagged.entry().document();
            return new WorkerDocumentView(d.id().value(), d.projectId().map(p -> p.value()).orElse(null), d.title(),
                    d.filename(), d.format().name(), d.paginated(), d.uploadedAt(), d.superseded(),
                    tagged.entry().replacement().map(Replacement::from).orElse(null),
                    tagged.topics().stream().map(TopicLabelView::from).toList(), d.caseDocument(),
                    ScanLink.from(tagged.scanLinks().scanCopies()));
        }
    }

    /** Un documento del expediente; la ficha del caso va sin quién la añadió ni cuándo. */
    public record CaseFileDocumentView(UUID id, @Nullable UUID projectId, String title, String filename, String format,
            boolean paginated, int pageCount, UUID uploadedBy, Instant uploadedAt, boolean superseded,
            @Nullable Replacement replacement, @Nullable UUID addedBy, @Nullable Instant addedAt, String status,
            @Nullable String uploadedByName, @Nullable String addedByName, @Nullable ScanLink scanOf,
            List<ScanLink> scanCopies) {
        static CaseFileDocumentView from(DocumentEntry entry, int pageCount, @Nullable UserId addedBy,
                @Nullable Instant addedAt, Map<UserId, String> names, ScanLinks links) {
            Document d = entry.document();
            return new CaseFileDocumentView(d.id().value(), d.projectId().map(p -> p.value()).orElse(null), d.title(),
                    d.filename(), d.format().name(), d.paginated(), pageCount, d.uploadedBy().value(), d.uploadedAt(),
                    d.superseded(), entry.replacement().map(Replacement::from).orElse(null),
                    addedBy == null ? null : addedBy.value(), addedAt, d.status().name(), names.get(d.uploadedBy()),
                    addedBy == null ? null : names.get(addedBy), links.scanOf().map(ScanLink::from).orElse(null),
                    ScanLink.from(links.scanCopies()));
        }
    }

    public record CaseFileResponse(CaseFileDocumentView caseDocument, List<CaseFileDocumentView> documents,
            int pendingDocuments, int unreadableDocuments) {
        public static CaseFileResponse from(CaseFileView view) {
            return new CaseFileResponse(CaseFileDocumentView.from(view.caseDocument(), view.casePageCount(), null, null,
                    view.names(), ScanLinks.NONE),
                    view.documents().stream()
                            .map(d -> CaseFileDocumentView.from(d.document(), d.pageCount(), d.addedBy(), d.addedAt(),
                                    view.names(), d.scanLinks())).toList(),
                    view.pendingDocuments(), view.unreadableDocuments());
        }
    }

    public record CaseFileTextPageView(UUID documentId, String title, int pageNumber, String text, boolean unread) {
    }

    public record CaseFileTextResponse(List<CaseFileTextPageView> pages, @Nullable String nextCursor, boolean complete,
            int documentsRead, int documentsTotal, int pagesRead, int pagesTotal, int pendingDocuments,
            int unreadableDocuments) {
        public static CaseFileTextResponse from(CaseFileText t) {
            return new CaseFileTextResponse(t.pages().stream().map(p -> new CaseFileTextPageView(p.documentId().value(),
                    p.title(), p.pageNumber(), p.text(), p.unread())).toList(), t.nextCursor().orElse(null),
                    t.complete(), t.documentsRead(), t.documentsTotal(), t.pagesRead(), t.pagesTotal(),
                    t.pendingDocuments(), t.unreadableDocuments());
        }
    }

    public record CaseFileAdded(int added) {
    }

    public record IssuedLink(String url, Instant expiresAt) {
        public static IssuedLink from(com.e2d.odin.application.link.IssuedLink link) {
            return new IssuedLink(link.url(), link.expiresAt());
        }
    }

    /** {@code caseTitle} solo viaja en un enlace de caso. */
    public record UploadLinkInfo(com.e2d.odin.application.link.UploadTargetKind target, @Nullable String caseTitle, String projectName, Instant expiresAt) {
        public static UploadLinkInfo from(com.e2d.odin.application.link.UploadLinkInfo info) {
            return new UploadLinkInfo(info.kind(), info.caseTitle().orElse(null), info.projectName(),
                    info.expiresAt());
        }
    }

    public record UploadReceipt(String filename, boolean duplicate) {
        public static UploadReceipt from(com.e2d.odin.application.link.UploadReceipt receipt) {
            return new UploadReceipt(receipt.filename(), receipt.duplicate());
        }
    }

    public record CaseFileEventView(UUID id, UUID documentId, @Nullable String documentTitle, String action, UUID actorId,
            Instant at) {
        public static CaseFileEventView from(ListCaseFileEvents.Line line) {
            CaseFileEvent e = line.event();
            return new CaseFileEventView(e.id(), e.documentId().value(), line.documentTitle(), e.action().name(),
                    e.actorId().value(), e.at());
        }
    }

    public record CaseMarksResult(int updated) {
    }

    public record IntakeWarningView(String code, String message) {
        static IntakeWarningView from(com.e2d.odin.application.intake.IntakeWarning warning) {
            return switch (warning) {
                case NO_READER -> new IntakeWarningView(warning.name(),
                        "No hay lector OCR: los escaneos no se leen y esperan en «Sin ubicar»; el resto se clasifica");
                case NO_LOCAL_MODEL -> new IntakeWarningView(warning.name(),
                        "No hay modelo local: solo se propondrá con las reglas");
                case PROJECT_LIST_NOT_CONFIRMED -> new IntakeWarningView(warning.name(),
                        "Falta terminar la lista de proyectos: hasta entonces no se clasifica");
            };
        }
    }

    @Schema(description = "uploaded: documentos subidos sin retirar; reading: aún sin leer; pending: sin confirmar; "
            + "alreadyThere: ficheros que ya estaban al subirlos; discarded: ficheros descartados en este lote; "
            + "proposed: propuestas por decidir por clase (solo las clases que tienen alguna); unplaced: documentos "
            + "pendientes sin propuesta de proyecto por decidir (en revisión). projectListClosedAt: cuándo se dio la lista "
            + "de proyectos por terminada; sin ella no se clasifica.")
    public record IntakeBatchView(UUID id, UUID companyId, String channel, String state, Instant createdAt,
            @Nullable Instant closedAt, int uploaded, int reading, int pending, int alreadyThere, int discarded,
            List<IntakeWarningView> warnings, java.util.Map<String, Integer> proposed, int unplaced, int toDecide,
            @Nullable Instant projectListClosedAt) {
        public static IntakeBatchView from(com.e2d.odin.application.intake.IntakeBatchView view) {
            var b = view.batch();
            return new IntakeBatchView(b.id().value(), b.companyId().value(), b.channel().name(), b.state().name(),
                    b.createdAt(), b.closedAt().orElse(null), view.counts().uploaded(), view.counts().reading(),
                    view.counts().pending(), b.alreadyThere(), b.discarded(),
                    view.warnings().stream().map(IntakeWarningView::from).toList(),
                    view.proposed().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey().name(), java.util.Map.Entry::getValue)), view.unplaced(), view.toDecide(),
                    b.projectListClosedAt().orElse(null));
        }
    }

    @Schema(description = "Un lote sin cerrar o cerrado con documentos sin ubicar. state: OPEN, READING, IN_REVIEW o CLOSED; channel: COMMAND o PANEL.")
    public record IntakeBatchSummary(UUID id, String state, String channel, Instant createdAt) {
        static IntakeBatchSummary from(com.e2d.odin.domain.intake.IntakeBatch b) {
            return new IntakeBatchSummary(b.id().value(), b.state().name(), b.channel().name(), b.createdAt());
        }
    }

    @Schema(description = "Los lotes de la empresa que no están cerrados y los cerrados con documentos sin ubicar, el más reciente primero.")
    public record OpenIntakeBatches(List<IntakeBatchSummary> batches) {
        public static OpenIntakeBatches from(List<com.e2d.odin.domain.intake.IntakeBatch> batches) {
            return new OpenIntakeBatches(batches.stream().map(IntakeBatchSummary::from).toList());
        }
    }

    @Schema(description = "Un documento pendiente del lote. status: el estado de su lectura (FAILED si no se pudo leer). "
            + "path: la ruta de su carpeta al subirlo. documentType: el tipo que le calculó la clasificación "
            + "(acta, contrato, poliza…), o null si no se reconoció.")
    public record IntakeDocumentView(UUID id, String title, @Nullable String path, String status,
            @Nullable String documentType) {
        static IntakeDocumentView from(Document d, java.util.Map<com.e2d.odin.domain.shared.DocumentId, String> types) {
            return new IntakeDocumentView(d.id().value(), d.title(), d.intakePath().orElse(null), d.status().name(),
                    types.get(d.id()));
        }
    }

    public record IntakeDocuments(List<IntakeDocumentView> documents) {
        public static IntakeDocuments from(List<Document> documents,
                java.util.Map<com.e2d.odin.domain.shared.DocumentId, String> types) {
            return new IntakeDocuments(documents.stream().map(d -> IntakeDocumentView.from(d, types)).toList());
        }
    }

    @Schema(description = "outcome: CREATED, ALREADY_THERE (la huella ya estaba) o DISCARDED_IN_BATCH (se descartó en este lote).")
    public record IntakeFile(String outcome, DocumentView document) {
        public static IntakeFile from(com.e2d.odin.application.intake.IntakeFileResult r) {
            return new IntakeFile(r.outcome().name(), DocumentView.from(r.entry()));
        }
    }

    @Schema(description = "origin: IMPORTED (de la lista del cliente) o DISCOVERED (de los textos). state: PROPOSED, "
            + "CONFIRMED o REJECTED. projectId: el proyecto creado o al que se unió, si está confirmado.")
    public record ProjectCandidateView(String name, String origin, int documentCount, String state,
            @Nullable UUID projectId) {
        static ProjectCandidateView from(com.e2d.odin.domain.intake.ProjectCandidate c) {
            return new ProjectCandidateView(c.name(), c.origin().name(), c.documentCount(), c.state().name(),
                    c.projectId().map(ProjectId::value).orElse(null));
        }
    }

    public record ProjectCandidates(List<ProjectCandidateView> candidates) {
        public static ProjectCandidates from(List<com.e2d.odin.domain.intake.ProjectCandidate> candidates) {
            return new ProjectCandidates(candidates.stream().map(ProjectCandidateView::from).toList());
        }
    }

    @Schema(description = "Una propuesta por decidir, con el título de su documento y la cita literal que la motiva. "
            + "targetDocumentId: el otro documento de una pareja o la ficha de un enlace, y targetTitle su título (de la "
            + "misma empresa). caseNumber: el número de caso. source: RULES, FOLDER o MODEL. documentType: el tipo del documento de "
            + "la fila (no el del destino), o null si no se reconoció.")
    public record IntakeProposalItem(UUID id, UUID documentId, String title, @Nullable UUID targetDocumentId,
            @Nullable String targetTitle, @Nullable String caseNumber, String source, String reason,
            @Nullable String documentType) {
        static IntakeProposalItem from(com.e2d.odin.application.intake.IntakeProposalItem i) {
            com.e2d.odin.domain.intake.IntakeProposal p = i.proposal();
            return new IntakeProposalItem(p.id(), p.documentId().value(), i.title(),
                    p.targetDocumentId().map(d -> d.value()).orElse(null), i.targetTitle().orElse(null),
                    p.caseNumber().orElse(null),
                    p.source().name(), p.reason(), i.documentType().orElse(null));
        }
    }

    @Schema(description = "kind: PROJECT, COMPANY_WIDE, CASE_MARK, CASE_LINK, SAME_AS_SCAN o SUPERSEDES. confidence: HIGH "
            + "o LOW. projectId: el proyecto propuesto, si la clase lo tiene.")
    public record IntakeProposalGroup(String kind, String confidence, @Nullable UUID projectId,
            List<IntakeProposalItem> proposals) {
        static IntakeProposalGroup from(com.e2d.odin.application.intake.IntakeProposalGroup g) {
            return new IntakeProposalGroup(g.kind().name(), g.confidence().name(),
                    g.projectId().map(ProjectId::value).orElse(null),
                    g.items().stream().map(IntakeProposalItem::from).toList());
        }
    }

    public record IntakeProposalGroups(List<IntakeProposalGroup> groups) {
        public static IntakeProposalGroups from(List<com.e2d.odin.application.intake.IntakeProposalGroup> groups) {
            return new IntakeProposalGroups(groups.stream().map(IntakeProposalGroup::from).toList());
        }
    }

    public record ConfirmedProposals(int confirmed) {
    }

    @Schema(description = "state: PROPOSED, CONFIRMED, REJECTED o DONT_KNOW («no lo sé»: sigue abierta y se puede decidir "
            + "después). decidedBy y decidedAt: quién y cuándo, si está decidida o con «no lo sé».")
    public record DecidedProposal(UUID id, String state, @Nullable UUID decidedBy, @Nullable Instant decidedAt) {
        public static DecidedProposal from(com.e2d.odin.domain.intake.IntakeProposal p) {
            return new DecidedProposal(p.id(), p.state().name(), p.decidedBy().map(UserId::value).orElse(null),
                    p.decidedAt().orElse(null));
        }
    }

    public record KnownFiles(List<String> sha256) {
    }

    public record Upload(DocumentView document, boolean duplicate) {
        public static Upload from(UploadOutcome o) {
            return new Upload(DocumentView.from(o.entry()), o.duplicate());
        }
    }

    public record ReviewView(UUID reviewerId, String decision, Instant reviewedAt, @Nullable String note) {
        static ReviewView from(CardReview r) {
            return new ReviewView(r.reviewerId().value(), r.decision().name(), r.reviewedAt(), r.note().orElse(null));
        }
    }

    /** Una página de fichas para revisar y cuántas hay en total con ese filtro. */
    public record CardPage(List<CardView> cards, int total) {
    }

    public record CardView(UUID id, UUID companyId, @Nullable UUID projectId, String title, String body,
            @Nullable String originalBody, UUID authorId, Instant createdAt, String status, @Nullable ReviewView review) {
        public static CardView from(KnowledgeCard c) {
            return new CardView(c.id().value(), c.companyId().value(), c.projectId().map(p -> p.value()).orElse(null),
                    c.title(), c.body(), c.originalBody().orElse(null), c.authorId().value(), c.createdAt(),
                    c.status().name(), c.review().map(ReviewView::from).orElse(null));
        }
    }

    /** Lo que el autor ve de la revisión de su ficha: qué se decidió y por qué, sin decir quién revisó. */
    public record MyReviewView(String decision, @Nullable String note, Instant reviewedAt) {
        static MyReviewView from(CardReview r) {
            return new MyReviewView(r.decision().name(), r.note().orElse(null), r.reviewedAt());
        }
    }

    /** Una ficha propia: sin autor ni empresa, que son siempre los del que mira. */
    public record MyCardView(UUID id, String title, String body, @Nullable String originalBody,
            @Nullable UUID projectId, Instant createdAt, String status, @Nullable MyReviewView review) {
        static MyCardView from(KnowledgeCard c) {
            return new MyCardView(c.id().value(), c.title(), c.body(), c.originalBody().orElse(null),
                    c.projectId().map(p -> p.value()).orElse(null), c.createdAt(), c.status().name(),
                    c.review().map(MyReviewView::from).orElse(null));
        }

        public static List<MyCardView> from(List<KnowledgeCard> cards) {
            return cards.stream().map(MyCardView::from).toList();
        }
    }

    /** Una pregunta propia: sin autor (D-048). */
    public record MyQuestionView(String text, String kind, int hitCount, Instant askedAt) {
        static MyQuestionView from(QuestionRecord q) {
            return new MyQuestionView(q.text(), q.kind().name(), q.hitCount(), q.askedAt());
        }

        public static List<MyQuestionView> from(List<QuestionRecord> questions) {
            return questions.stream().map(MyQuestionView::from).toList();
        }
    }

    public record SavedCard(UUID id, String status, String message) {
        public static SavedCard from(KnowledgeCard c) {
            return new SavedCard(c.id().value(), c.status().name(), "Ficha guardada; queda pendiente de revisión.");
        }
    }

    public record Hit(String sourceType, UUID sourceId, String title, @Nullable Integer pageNumber, boolean paginated,
            boolean caseDocument, @Nullable UUID projectId, String authorName, Instant date, String excerpt, double rank,
            boolean superseded, @Nullable Replacement replacement, @Nullable String reviewStatus) {
        static Hit from(SearchHit h) {
            return new Hit(h.sourceType().name(), h.sourceId(), h.title(), h.pageNumber().orElse(null),
                    h.paginated(), h.caseDocument(), h.projectId().map(p -> p.value()).orElse(null), h.authorName(), h.date(),
                    h.excerpt(), h.rank(), h.superseded(),
                    h.replacement().map(Replacement::from).orElse(null),
                    h.reviewStatus().map(Enum::name).orElse(null));
        }
    }

    public record SearchResponse(List<Hit> hits, boolean noSource) {
        public static SearchResponse from(SearchResult r) {
            return new SearchResponse(r.hits().stream().map(Hit::from).toList(), r.hits().isEmpty());
        }
    }

    public record CasePageView(int number, String text) {
    }

    /** Un caso parecido. Sin autor a propósito: ni quien subió el documento ni quien aportó la ficha (D-040). */
    public record SimilarCaseView(String sourceType, UUID sourceId, String title, @Nullable UUID projectId,
            Instant addedAt, int pageCount, List<CasePageView> pages, @Nullable String body, boolean truncated,
            String excerpt, String origin, @Nullable String originLabel, boolean superseded,
            @Nullable Replacement replacement, @Nullable String reviewStatus) {
        static final String CARD_LABEL = "Ficha aportada por un trabajador, redactada con IA";

        static SimilarCaseView from(SimilarCase c) {
            boolean card = c.sourceType() == SourceType.KNOWLEDGE_CARD;
            return new SimilarCaseView(c.sourceType().name(), c.sourceId(), c.title(),
                    c.projectId().map(p -> p.value()).orElse(null), c.addedAt(), c.pageCount(),
                    c.pages().stream().map(p -> new CasePageView(p.number(), p.text())).toList(),
                    c.body().orElse(null), c.truncated(), c.excerpt(),
                    card ? "KNOWLEDGE_CARD" : "ORIGINAL_DOCUMENT", card ? CARD_LABEL : null, c.superseded(),
                    c.replacement().map(Replacement::from).orElse(null), c.reviewStatus().map(Enum::name).orElse(null));
        }
    }

    public record SimilarCasesResponse(List<SimilarCaseView> cases, boolean noSource) {
        public static SimilarCasesResponse from(SimilarCasesResult r) {
            return new SimilarCasesResponse(r.cases().stream().map(SimilarCaseView::from).toList(), r.noSource());
        }
    }

    public record Page(UUID documentId, String title, int pageNumber, boolean paginated, String text,
            boolean superseded, @Nullable Replacement replacement, String readerName) {
        public static Page from(PageView v) {
            return new Page(v.document().id().value(), v.document().title(), v.page().number(),
                    v.document().paginated(), v.page().text(), v.document().superseded(),
                    v.entry().replacement().map(Replacement::from).orElse(null), v.page().readerName());
        }
    }

    public record Verification(boolean verified) {
    }

    public record VerificationResult(UUID documentId, int pageNumber, boolean verified) {
    }

    public record Verifications(List<VerificationResult> results) {
    }
}
