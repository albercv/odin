package com.e2d.odin.adapter.web.worker;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.casefile.GetCaseFile;
import com.e2d.odin.application.casefile.RemoveFromCaseFile;
import com.e2d.odin.application.link.IssueUploadLink;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CaseFileController {

    private final GetCaseFile getCaseFile;
    private final AddToCaseFiles addToCaseFiles;
    private final RemoveFromCaseFile removeFromCaseFile;
    private final IssueUploadLink issueUploadLink;

    public CaseFileController(GetCaseFile getCaseFile, AddToCaseFiles addToCaseFiles,
            RemoveFromCaseFile removeFromCaseFile, IssueUploadLink issueUploadLink) {
        this.getCaseFile = getCaseFile;
        this.addToCaseFiles = addToCaseFiles;
        this.removeFromCaseFile = removeFromCaseFile;
        this.issueUploadLink = issueUploadLink;
    }

    @GetMapping("/v1/cases/{caseId}/file")
    public Responses.CaseFileResponse file(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID caseId) {
        return Responses.CaseFileResponse.from(getCaseFile.execute(actor, new DocumentId(caseId)));
    }

    @PostMapping("/v1/cases/{caseId}/file/documents")
    public Responses.CaseFileAdded add(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID caseId, @RequestBody Requests.CaseFileDocumentsRequest request) {
        return new Responses.CaseFileAdded(
                addToCaseFiles.execute(actor, List.of(request.assignment(caseId))));
    }

    @DeleteMapping("/v1/cases/{caseId}/file/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID caseId,
            @PathVariable UUID documentId) {
        removeFromCaseFile.execute(actor, new DocumentId(caseId), new DocumentId(documentId));
    }

    @PostMapping("/v1/cases/{caseId}/upload-links")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.IssuedLink uploadLink(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID caseId) {
        return Responses.IssuedLink.from(issueUploadLink.execute(actor, new DocumentId(caseId)));
    }
}
