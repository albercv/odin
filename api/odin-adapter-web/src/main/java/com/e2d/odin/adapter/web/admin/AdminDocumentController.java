package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.document.GetDocument;
import com.e2d.odin.application.document.ListCompanyDocuments;
import com.e2d.odin.application.document.RestoreDocument;
import com.e2d.odin.application.document.RetryDocument;
import com.e2d.odin.application.document.MarkCaseDocuments;
import com.e2d.odin.application.document.SupersedeDocument;
import com.e2d.odin.application.document.UpdateDocument;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.document.UploadOutcome;
import com.e2d.odin.application.identity.UserNames;
import com.e2d.odin.application.link.OpenOriginal;
import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.casefile.UploadToCaseFile;
import com.e2d.odin.application.document.WithdrawDocument;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import com.e2d.odin.adapter.web.link.OriginalResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AdminDocumentController {

    private static final int MAX_QUERY = 100;

    private final UploadDocument upload;
    private final ListCompanyDocuments list;
    private final GetDocument get;
    private final RetryDocument retry;
    private final SupersedeDocument supersede;
    private final UpdateDocument update;
    private final MarkCaseDocuments markCases;
    private final AddToCaseFiles addToCaseFiles;
    private final UploadToCaseFile uploadToCaseFile;
    private final OpenOriginal openOriginal;
    private final WithdrawDocument withdraw;
    private final RestoreDocument restore;
    private final UserNames userNames;

    public AdminDocumentController(UploadDocument upload, ListCompanyDocuments list, GetDocument get,
            RetryDocument retry, SupersedeDocument supersede, UpdateDocument update, MarkCaseDocuments markCases,
            AddToCaseFiles addToCaseFiles, UploadToCaseFile uploadToCaseFile, OpenOriginal openOriginal,
            WithdrawDocument withdraw, RestoreDocument restore, UserNames userNames) {
        this.upload = upload;
        this.list = list;
        this.get = get;
        this.retry = retry;
        this.supersede = supersede;
        this.update = update;
        this.markCases = markCases;
        this.addToCaseFiles = addToCaseFiles;
        this.uploadToCaseFile = uploadToCaseFile;
        this.openOriginal = openOriginal;
        this.withdraw = withdraw;
        this.restore = restore;
        this.userNames = userNames;
    }

    @GetMapping("/v1/admin/companies/{companyId}/documents")
    public Responses.DocumentPage list(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "Si es true, devuelve solo los documentos retirados (con su paginación)")
            @RequestParam(defaultValue = "false") boolean withdrawn,
            @Parameter(description = "Texto del título, sin mayúsculas ni acentos (hasta 100 caracteres)")
            @RequestParam(required = false) String q) {
        if (q != null && q.strip().length() > MAX_QUERY) {
            throw new ValidationException("La búsqueda admite hasta " + MAX_QUERY + " caracteres");
        }
        var page = list.page(actor, new CompanyId(companyId), offset, limit, withdrawn, Optional.ofNullable(q));
        Map<UserId, String> names = userNames.of(page.documents().stream().map(t -> t.entry().document().uploadedBy())
                .collect(Collectors.toSet()));
        return new Responses.DocumentPage(
                page.documents().stream().map(t -> Responses.DocumentView.from(t, names)).toList(), page.total());
    }

    @PostMapping(path = "/v1/admin/companies/{companyId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "201", description = "Documento nuevo")
    @ApiResponse(responseCode = "200", description = "El mismo fichero ya estaba en la empresa (duplicate: true)")
    public ResponseEntity<Responses.Upload> upload(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title, @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "false") boolean caseDocument,
            @RequestParam(required = false) @Schema(description = "Caso a cuyo expediente se añade el documento.")
            UUID caseId) throws IOException {
        String name = title == null || title.isBlank() ? file.getOriginalFilename() : title;
        Optional<ProjectId> project = Optional.ofNullable(projectId).map(ProjectId::new);
        UploadOutcome outcome = caseId == null
                ? upload.execute(actor, new CompanyId(companyId), project, name, file.getOriginalFilename(),
                        file.getBytes(), caseDocument)
                : uploadToCaseFile.executeAsAdmin(actor,
                        addToCaseFiles.requireTarget(actor, new CompanyId(companyId), new DocumentId(caseId), project),
                        name, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(outcome.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(Responses.Upload.from(outcome));
    }

    @GetMapping("/v1/admin/documents/{documentId}")
    public Responses.DocumentView get(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID documentId) {
        return Responses.DocumentView.from(get.execute(actor, new DocumentId(documentId)));
    }

    @GetMapping("/v1/admin/documents/{documentId}/original")
    public ResponseEntity<Resource> downloadOriginal(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID documentId) {
        return OriginalResponse.of(openOriginal.execute(actor, new DocumentId(documentId)));
    }

    @PatchMapping("/v1/admin/documents/{documentId}")
    public Responses.DocumentView update(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID documentId, @RequestBody Requests.UpdateDocumentRequest request) {
        if (request.caseDocument() == null) {
            throw new ValidationException("Indica si el documento es la ficha de un caso");
        }
        return Responses.DocumentView.from(update.execute(actor, new DocumentId(documentId), request.caseDocument()));
    }

    @PostMapping("/v1/admin/companies/{companyId}/documents/case-marks")
    public Responses.CaseMarksResult markCases(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestBody Requests.CaseMarksRequest request) {
        if (request.caseDocument() == null) {
            throw new ValidationException("Indica si se marcan o se quitan como casos");
        }
        if (request.documentIds() == null || request.documentIds().contains(null)) {
            throw new ValidationException("Elige entre 1 y 500 documentos");
        }
        Set<DocumentId> ids = request.documentIds().stream().map(DocumentId::new).collect(Collectors.toSet());
        return new Responses.CaseMarksResult(
                markCases.execute(actor, new CompanyId(companyId), ids, request.caseDocument()));
    }

    @PostMapping("/v1/admin/documents/{documentId}/retry")
    public Responses.DocumentView retry(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID documentId) {
        return Responses.DocumentView.from(retry.execute(actor, new DocumentId(documentId)));
    }

    @PutMapping("/v1/admin/documents/{documentId}/superseded-by")
    public Responses.DocumentView supersede(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID documentId, @RequestBody Requests.SupersedeRequest request) {
        if (request.replacementId() == null) {
            throw new ValidationException("Indica el documento que lo sustituye");
        }
        return Responses.DocumentView.from(supersede.mark(actor, new DocumentId(documentId),
                new DocumentId(request.replacementId())));
    }

    @DeleteMapping("/v1/admin/documents/{documentId}/superseded-by")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsupersede(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID documentId) {
        supersede.unmark(actor, new DocumentId(documentId));
    }

    @PutMapping("/v1/admin/documents/{documentId}/withdrawal")
    public Responses.DocumentView withdraw(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID documentId) {
        return Responses.DocumentView.from(withdraw.execute(actor, new DocumentId(documentId)));
    }

    @DeleteMapping("/v1/admin/documents/{documentId}/withdrawal")
    public Responses.DocumentView restore(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID documentId) {
        return Responses.DocumentView.from(restore.execute(actor, new DocumentId(documentId)));
    }
}
