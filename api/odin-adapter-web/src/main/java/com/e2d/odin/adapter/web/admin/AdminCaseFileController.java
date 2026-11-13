package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.casefile.GetCaseFile;
import com.e2d.odin.application.casefile.ListCaseFileEvents;
import com.e2d.odin.application.casefile.RemoveFromCaseFile;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
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
public class AdminCaseFileController {

    private static final String BASE = "/v1/admin/companies/{companyId}/cases/{caseId}";

    private final GetCaseFile getCaseFile;
    private final AddToCaseFiles addToCaseFiles;
    private final RemoveFromCaseFile removeFromCaseFile;
    private final ListCaseFileEvents listEvents;

    public AdminCaseFileController(GetCaseFile getCaseFile, AddToCaseFiles addToCaseFiles,
            RemoveFromCaseFile removeFromCaseFile, ListCaseFileEvents listEvents) {
        this.getCaseFile = getCaseFile;
        this.addToCaseFiles = addToCaseFiles;
        this.removeFromCaseFile = removeFromCaseFile;
        this.listEvents = listEvents;
    }

    @GetMapping(BASE + "/file")
    public Responses.CaseFileResponse file(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID caseId) {
        return Responses.CaseFileResponse.from(
                getCaseFile.execute(actor, new CompanyId(companyId), new DocumentId(caseId)));
    }

    @PostMapping(BASE + "/file/documents")
    public Responses.CaseFileAdded add(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID caseId,
            @RequestBody Requests.CaseFileDocumentsRequest request) {
        return new Responses.CaseFileAdded(
                addToCaseFiles.execute(actor, new CompanyId(companyId), List.of(request.assignment(caseId))));
    }

    @DeleteMapping(BASE + "/file/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID companyId,
            @PathVariable UUID caseId, @PathVariable UUID documentId) {
        removeFromCaseFile.execute(actor, new CompanyId(companyId), new DocumentId(caseId),
                new DocumentId(documentId));
    }

    @GetMapping(BASE + "/events")
    public List<Responses.CaseFileEventView> events(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID caseId) {
        return listEvents.execute(actor, new CompanyId(companyId), new DocumentId(caseId)).stream()
                .map(Responses.CaseFileEventView::from).toList();
    }
}
