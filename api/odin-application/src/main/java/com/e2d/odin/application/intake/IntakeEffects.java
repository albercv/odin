package com.e2d.odin.application.intake;

import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.casefile.CaseFileAssignment;
import com.e2d.odin.application.document.MarkCaseDocuments;
import com.e2d.odin.application.document.SupersedeDocument;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Donde las propuestas se vuelven efectos (D2), y solo por la decisión del administrador. Reutiliza los casos de uso
 * que ya existen: no hay otro camino para cambiar un documento más que sacarlo de lo pendiente (proyecto o ninguno) y
 * poner {@code scan_of}. Cada acción es una transacción: lo envuelven los casos de uso que la llaman (todo o nada).
 */
public class IntakeEffects {

    private static final Set<ProposalKind> PLACEMENTS = EnumSet.of(ProposalKind.PROJECT, ProposalKind.COMPANY_WIDE);

    private final IntakeRepository intake;
    private final DocumentRepository documents;
    private final OrganizationRepository organizations;
    private final MarkCaseDocuments markCases;
    private final SupersedeDocument supersede;
    private final AddToCaseFiles caseFiles;
    private final InstallationSettings installation;
    private final Clock clock;

    public IntakeEffects(IntakeRepository intake, DocumentRepository documents, OrganizationRepository organizations,
            MarkCaseDocuments markCases, SupersedeDocument supersede, AddToCaseFiles caseFiles,
            InstallationSettings installation, Clock clock) {
        this.intake = intake;
        this.documents = documents;
        this.organizations = organizations;
        this.markCases = markCases;
        this.supersede = supersede;
        this.caseFiles = caseFiles;
        this.installation = installation;
        this.clock = clock;
    }

    /** El lote del administrador, en revisión: mientras se lee o después de cerrado no se decide nada. */
    IntakeBatch inReview(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        IntakeBatch batch = IntakeBatches.require(intake, actor, companyId, batchId);
        if (batch.state() != IntakeState.IN_REVIEW) {
            throw new ConflictException("El lote no está en revisión: está en " + batch.state());
        }
        return batch;
    }

    static void requireOpen(IntakeProposal p) {
        if (!p.open()) {
            throw new ConflictException("La propuesta ya está decidida");
        }
    }

    /** Confirma las propuestas: primero los proyectos, después las fichas de caso, las parejas y al final los enlaces. */
    void confirm(Actor actor, IntakeBatch batch, List<IntakeProposal> selected, List<IntakeProposal> all) {
        selected.forEach(IntakeEffects::requireOpen);
        requireOnePlacementPerDocument(selected);
        for (IntakeProposal p : ofKind(selected, PLACEMENTS)) {
            Optional<ProjectId> project = p.kind() == ProposalKind.PROJECT
                    ? Optional.of(p.targetProjectId().orElseThrow(() -> new ConflictException("La propuesta no tiene proyecto")))
                    : Optional.empty();
            place(batch, p, project, true, actor, all);
        }
        markCases(actor, batch, ofKind(selected, EnumSet.of(ProposalKind.CASE_MARK)));
        pairs(actor, batch, ofKind(selected, EnumSet.of(ProposalKind.SAME_AS_SCAN, ProposalKind.SUPERSEDES)));
        links(actor, batch, ofKind(selected, EnumSet.of(ProposalKind.CASE_LINK)));
    }

    /** «Corregir el proyecto» y «de toda la empresa»: el documento sale de lo pendiente donde decide la persona. */
    void placeAs(Actor actor, IntakeBatch batch, IntakeProposal p, Optional<ProjectId> project, List<IntakeProposal> all) {
        if (!PLACEMENTS.contains(p.kind())) {
            throw new ValidationException(
                    "Solo se corrige el proyecto de una propuesta de proyecto");
        }
        boolean asProposed = p.kind() == ProposalKind.PROJECT ? project.equals(p.targetProjectId())
                : project.isEmpty();
        place(batch, p, project, asProposed, actor, all);
    }

    void reject(Actor actor, IntakeProposal p) {
        intake.save(p.reject(actor.userId(), clock.instant()));
    }

    void dontKnow(Actor actor, IntakeProposal p) {
        intake.save(p.dontKnow(actor.userId(), clock.instant()));
    }

    /** Las propuestas por decidir sobre un documento descartado ya no se pueden confirmar: se rechazan. */
    void rejectUndecidedAbout(Actor actor, IntakeBatch batch, DocumentId documentId) {
        intake.proposals(batch.companyId(), batch.id()).stream().filter(p -> p.open()
                && (p.documentId().equals(documentId) || p.targetDocumentId().equals(Optional.of(documentId))))
                .forEach(p -> reject(actor, p));
    }

    /** Sin documentos pendientes ni propuestas por decidir, el lote se cierra. */
    void closeIfDone(IntakeBatch batch) {
        IntakeBatches.closeIfDone(intake, documents, batch, clock.instant());
    }

    private void place(IntakeBatch batch, IntakeProposal p, Optional<ProjectId> project, boolean asProposed,
            Actor actor, List<IntakeProposal> all) {
        placeDocument(batch, p.documentId(), project, actor);
        intake.save(asProposed ? p.confirm(actor.userId(), clock.instant()) : p.reject(actor.userId(), clock.instant()));
        all.stream().filter(q -> q.documentId().equals(p.documentId()) && !q.id().equals(p.id())
                && q.open() && PLACEMENTS.contains(q.kind()))
                .forEach(q -> reject(actor, q));
    }

    /** Decisión del administrador sobre un documento sin propuesta de proyecto por decidir (D2: sigue siendo suya). */
    void placeUnplaced(Actor actor, IntakeBatch batch, DocumentId documentId, Optional<ProjectId> project) {
        Document d = find(batch.companyId(), documentId);
        if (!d.intakeBatchId().equals(Optional.of(batch.id()))) {
            throw new NotFoundException("Documento no encontrado");
        }
        if (d.status() == DocumentStatus.FAILED) {
            throw new ConflictException("«" + d.title()
                    + "» no se pudo leer: descártalo y, si lo necesitas, súbelo de nuevo desde Documentos");
        }
        if (intake.proposals(batch.companyId(), batch.id()).stream().anyMatch(p -> p.documentId().equals(documentId)
                && p.open() && PLACEMENTS.contains(p.kind()))) {
            throw new ConflictException("«" + d.title() + "» tiene una propuesta de proyecto: decídelo en su propuesta");
        }
        placeDocument(batch, documentId, project, actor);
    }

    /** Saca el documento de lo pendiente con quién y cuándo. Nunca mientras se lee: el lector lo reescribiría (M3). */
    private void placeDocument(IntakeBatch batch, DocumentId documentId, Optional<ProjectId> project, Actor actor) {
        CompanyId company = batch.companyId();
        Document d = find(company, documentId);
        if (!d.intakeBatchId().equals(Optional.of(batch.id()))) {
            throw new NotFoundException("Documento no encontrado");
        }
        if (d.withdrawn() || !d.intakePending()) {
            throw new ConflictException("El documento «" + d.title() + "» ya no está pendiente");
        }
        if (IntakeBatches.beingRead(d, installation)) {
            throw new ConflictException("«" + d.title() + "» se está leyendo; espera a que termine");
        }
        project.ifPresent(id -> organizations.findProject(company, id)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado")));
        documents.confirmIntake(company, d.id(), project, actor.userId(), clock.instant());
    }

    private void markCases(Actor actor, IntakeBatch batch, List<IntakeProposal> marks) {
        if (marks.isEmpty()) {
            return;
        }
        Set<DocumentId> ids = marks.stream().map(p -> resolved(batch, p.documentId()).id())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        markCases.execute(actor, batch.companyId(), ids, true);
        marks.forEach(p -> intake.save(p.confirm(actor.userId(), clock.instant())));
    }

    private void pairs(Actor actor, IntakeBatch batch, List<IntakeProposal> pairs) {
        for (IntakeProposal p : pairs) {
            Document document = resolved(batch, p.documentId());
            Document other = resolved(batch, target(p));
            if (p.kind() == ProposalKind.SUPERSEDES) {
                supersede.mark(actor, other.id(), document.id());
            } else {
                requireSameProjectOrGeneral(other, document, "El original y su escaneo están en proyectos distintos");
                documents.markScanOf(batch.companyId(), document.id(), other.id());
            }
            intake.save(p.confirm(actor.userId(), clock.instant()));
        }
    }

    /** Un solo {@link AddToCaseFiles} con todos los casos del grupo y, en cada uno, sus documentos. */
    private void links(Actor actor, IntakeBatch batch, List<IntakeProposal> links) {
        Map<DocumentId, Set<DocumentId>> byCase = new LinkedHashMap<>();
        for (IntakeProposal p : links) {
            Document card = resolved(batch, target(p));
            Document document = resolved(batch, p.documentId());
            if (!card.caseDocument()) {
                throw new ConflictException("«" + card.title() + "» no está marcada como ficha de caso: confirma antes su marca");
            }
            requireSameProjectOrGeneral(document, card, "El documento y su ficha de caso están en proyectos distintos");
            byCase.computeIfAbsent(card.id(), id -> new LinkedHashSet<>()).add(document.id());
        }
        if (!byCase.isEmpty()) {
            caseFiles.execute(actor, batch.companyId(), byCase.entrySet().stream()
                    .map(e -> new CaseFileAssignment(e.getKey(), e.getValue())).toList());
            links.forEach(p -> intake.save(p.confirm(actor.userId(), clock.instant())));
        }
    }

    /** El documento de una pareja o un enlace tiene que estar ya confirmado: su proyecto es el que decide quién lo ve. */
    private Document resolved(IntakeBatch batch, DocumentId id) {
        Document d = find(batch.companyId(), id);
        if (d.withdrawn() || d.intakePending()) {
            throw new ConflictException("Confirma antes el proyecto de «" + d.title() + "»");
        }
        return d;
    }

    private Document find(CompanyId company, DocumentId id) {
        return documents.findEntryIncludingWithdrawn(CompanyScope.of(company), ProjectVisibility.all(), id)
                .map(DocumentEntry::document).orElseThrow(() -> new NotFoundException("Documento no encontrado"));
    }

    private static DocumentId target(IntakeProposal p) {
        return p.targetDocumentId().orElseThrow(() -> new ConflictException("La propuesta no tiene documento de destino"));
    }

    /** Lo que ve cada uno: el documento en el mismo proyecto que el ancla o sin proyecto (lo ve toda la empresa). */
    private static void requireSameProjectOrGeneral(Document document, Document anchor, String message) {
        if (document.projectId().isPresent() && !document.projectId().equals(anchor.projectId())) {
            throw new ConflictException(message);
        }
    }

    private static void requireOnePlacementPerDocument(List<IntakeProposal> selected) {
        Set<DocumentId> seen = new LinkedHashSet<>();
        if (!ofKind(selected, PLACEMENTS).stream().allMatch(p -> seen.add(p.documentId()))) {
            throw new ConflictException("Un documento no puede tener dos propuestas de proyecto en el mismo grupo");
        }
    }

    private static List<IntakeProposal> ofKind(List<IntakeProposal> proposals, Set<ProposalKind> kinds) {
        return proposals.stream().filter(p -> kinds.contains(p.kind())).toList();
    }
}
