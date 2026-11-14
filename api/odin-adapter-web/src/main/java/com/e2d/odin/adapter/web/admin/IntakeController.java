package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.intake.AddFileToIntake;
import com.e2d.odin.application.intake.CloseIntakeUpload;
import com.e2d.odin.application.intake.ConfirmIntakeProposals;
import com.e2d.odin.application.intake.DecideIntakeProposal;
import com.e2d.odin.application.intake.DiscardIntakeDocument;
import com.e2d.odin.application.intake.IntakeDecision;
import com.e2d.odin.application.intake.ListIntakeProposals;
import com.e2d.odin.application.intake.ListOpenIntakeBatches;
import com.e2d.odin.application.intake.ListUnplacedDocuments;
import com.e2d.odin.application.intake.PlaceIntakeDocument;
import com.e2d.odin.application.intake.ProposalFilter;
import com.e2d.odin.application.intake.ConfirmProjectCandidates;
import com.e2d.odin.application.intake.GetIntakeBatch;
import com.e2d.odin.application.intake.ImportProjectList;
import com.e2d.odin.application.intake.IntakeFileResult;
import com.e2d.odin.application.intake.ListProjectCandidates;
import com.e2d.odin.application.intake.ProjectDecision;
import com.e2d.odin.application.intake.StartIntake;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** La primera carga de una empresa: abrir el lote, subir ficheros de forma reanudable, cerrar la subida y confirmar. */
@RestController
public class IntakeController {

    private static final String BATCHES = "/v1/admin/companies/{companyId}/intake-batches";
    private static final int MAX_DECISIONS = 500;

    private final StartIntake start;
    private final ListOpenIntakeBatches openBatches;
    private final AddFileToIntake addFile;
    private final CloseIntakeUpload closeUpload;
    private final GetIntakeBatch get;
    private final ImportProjectList importList;
    private final ListProjectCandidates candidates;
    private final ConfirmProjectCandidates confirmCandidates;
    private final ListIntakeProposals proposals;
    private final ConfirmIntakeProposals confirmProposals;
    private final DecideIntakeProposal decideProposal;
    private final DiscardIntakeDocument discardDocument;
    private final ListUnplacedDocuments unplaced;
    private final PlaceIntakeDocument placeDocument;

    public IntakeController(StartIntake start, ListOpenIntakeBatches openBatches, AddFileToIntake addFile, CloseIntakeUpload closeUpload,
            GetIntakeBatch get, ImportProjectList importList, ListProjectCandidates candidates,
            ConfirmProjectCandidates confirmCandidates, ListIntakeProposals proposals,
            ConfirmIntakeProposals confirmProposals, DecideIntakeProposal decideProposal,
            DiscardIntakeDocument discardDocument, ListUnplacedDocuments unplaced, PlaceIntakeDocument placeDocument) {
        this.start = start;
        this.openBatches = openBatches;
        this.addFile = addFile;
        this.closeUpload = closeUpload;
        this.get = get;
        this.importList = importList;
        this.candidates = candidates;
        this.confirmCandidates = confirmCandidates;
        this.proposals = proposals;
        this.confirmProposals = confirmProposals;
        this.decideProposal = decideProposal;
        this.discardDocument = discardDocument;
        this.unplaced = unplaced;
        this.placeDocument = placeDocument;
    }

    @PostMapping(BATCHES)
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.IntakeBatchView start(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestBody Requests.StartIntakeRequest request) {
        IntakeBatch batch = start.execute(actor, new CompanyId(companyId), channel(request.channel()));
        return get(actor, companyId, batch.id().value());
    }

    @GetMapping(BATCHES)
    public Responses.OpenIntakeBatches openBatches(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId) {
        return Responses.OpenIntakeBatches.from(openBatches.execute(actor, new CompanyId(companyId)));
    }

    @GetMapping(BATCHES + "/{batchId}")
    public Responses.IntakeBatchView get(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId) {
        return Responses.IntakeBatchView.from(get.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId)));
    }

    @PostMapping(path = BATCHES + "/{batchId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "201", description = "Documento nuevo, pendiente de confirmar")
    @ApiResponse(responseCode = "200", description = "No se creó nada: ya estaba o se descartó en este lote")
    public ResponseEntity<Responses.IntakeFile> addFile(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId, @RequestParam("file") MultipartFile file,
            @RequestParam String path) throws IOException {
        IntakeFileResult result = addFile.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId), path,
                file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(result.outcome() == IntakeFileResult.Outcome.CREATED ? HttpStatus.CREATED
                : HttpStatus.OK).body(Responses.IntakeFile.from(result));
    }

    @GetMapping(BATCHES + "/{batchId}/files")
    public Responses.KnownFiles knownFiles(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId, @RequestParam List<String> sha256) {
        return new Responses.KnownFiles(List.copyOf(
                get.knownFiles(actor, new CompanyId(companyId), new IntakeBatchId(batchId), sha256)));
    }

    @PostMapping(BATCHES + "/{batchId}/close-upload")
    public Responses.IntakeBatchView closeUpload(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId) {
        closeUpload.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId));
        return get(actor, companyId, batchId);
    }

    @GetMapping(BATCHES + "/{batchId}/project-candidates")
    public Responses.ProjectCandidates projectCandidates(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId) {
        return Responses.ProjectCandidates.from(
                candidates.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId)));
    }

    @PostMapping(path = BATCHES + "/{batchId}/project-candidates/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Responses.ProjectCandidates importProjectList(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId, @RequestParam("file") MultipartFile file)
            throws IOException {
        return Responses.ProjectCandidates.from(
                importList.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId), file.getBytes()));
    }

    @PostMapping(BATCHES + "/{batchId}/project-candidates/discover")
    public Responses.ProjectCandidates discoverProjects(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId) {
        return Responses.ProjectCandidates.from(
                candidates.discover(actor, new CompanyId(companyId), new IntakeBatchId(batchId)));
    }

    @PostMapping(BATCHES + "/{batchId}/project-candidates/confirm")
    public Responses.ProjectCandidates confirmProjectCandidates(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId,
            @RequestBody Requests.ConfirmProjectCandidatesRequest request) {
        List<Requests.ProjectDecisionRequest> given = request.decisions();
        if (given == null || given.isEmpty() || given.size() > MAX_DECISIONS || given.contains(null)) {
            throw new ValidationException("Envía de 1 a " + MAX_DECISIONS + " decisiones");
        }
        return Responses.ProjectCandidates.from(confirmCandidates.execute(actor, new CompanyId(companyId),
                new IntakeBatchId(batchId), given.stream().map(IntakeController::decision).toList()));
    }

    @GetMapping(BATCHES + "/{batchId}/proposals")
    public Responses.IntakeProposalGroups proposals(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId, @RequestParam(required = false) String kind,
            @RequestParam(required = false) String confidence, @RequestParam(required = false) UUID project) {
        ProposalFilter filter = new ProposalFilter(parse(kind, ProposalKind::valueOf, "El tipo no es válido"),
                parse(confidence, ProposalConfidence::valueOf, "La confianza tiene que ser HIGH o LOW"),
                Optional.ofNullable(project).map(ProjectId::new));
        return Responses.IntakeProposalGroups.from(
                proposals.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId), filter));
    }

    @PostMapping(BATCHES + "/{batchId}/proposals/confirm")
    public Responses.ConfirmedProposals confirmProposals(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId,
            @RequestBody Requests.ConfirmProposalsRequest request) {
        return new Responses.ConfirmedProposals(confirmProposals.execute(actor, new CompanyId(companyId),
                new IntakeBatchId(batchId), request.proposalIds()));
    }

    @PostMapping(BATCHES + "/{batchId}/proposals/{proposalId}/decide")
    public Responses.DecidedProposal decideProposal(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId, @PathVariable UUID proposalId,
            @RequestBody Requests.DecideProposalRequest request) {
        IntakeDecision decision = parse(request.decision(), IntakeDecision::valueOf,
                "La decisión tiene que ser CONFIRM, CORRECT_PROJECT, COMPANY_WIDE, DONT_KNOW o REJECT")
                .orElseThrow(() -> new ValidationException("Indica la decisión"));
        return Responses.DecidedProposal.from(decideProposal.execute(actor, new CompanyId(companyId),
                new IntakeBatchId(batchId), proposalId, decision,
                Optional.ofNullable(request.projectId()).map(ProjectId::new)));
    }

    @PostMapping(BATCHES + "/{batchId}/documents/{documentId}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID companyId,
            @PathVariable UUID batchId, @PathVariable UUID documentId) {
        discardDocument.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId),
                new DocumentId(documentId));
    }

    @GetMapping(BATCHES + "/{batchId}/documents")
    public Responses.IntakeDocuments unplacedDocuments(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId, @RequestParam boolean unplaced) {
        if (!unplaced) {
            throw new ValidationException("Solo se listan los documentos sin colocar: unplaced=true");
        }
        return Responses.IntakeDocuments.from(
                this.unplaced.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId)));
    }

    @PostMapping(BATCHES + "/{batchId}/documents/{documentId}/place")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void place(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID companyId,
            @PathVariable UUID batchId, @PathVariable UUID documentId,
            @RequestBody Requests.PlaceDocumentRequest request) {
        placeDocument.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId), new DocumentId(documentId),
                Optional.ofNullable(request.projectId()).map(ProjectId::new), Boolean.TRUE.equals(request.companyWide()));
    }

    private static <T> Optional<T> parse(String value, java.util.function.Function<String, T> valueOf, String message) {
        try {
            return Optional.ofNullable(value).map(valueOf);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(message);
        }
    }

    private static ProjectDecision decision(Requests.ProjectDecisionRequest request) {
        try {
            return new ProjectDecision(request.name(), ProjectDecision.Action.valueOf(request.decision()),
                    java.util.Optional.ofNullable(request.projectId()).map(ProjectId::new));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("La decisión tiene que ser CREATE, MERGE o REJECT");
        }
    }

    private static IntakeChannel channel(String value) {
        try {
            return IntakeChannel.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("El canal tiene que ser COMMAND o PANEL");
        }
    }
}
