package com.e2d.odin.adapter.web.worker;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.knowledge.SaveKnowledgeCard;
import com.e2d.odin.application.organization.DescribeMe;
import com.e2d.odin.application.search.ListDocuments;
import com.e2d.odin.application.search.ReadPage;
import com.e2d.odin.application.search.SearchKnowledge;
import com.e2d.odin.application.search.SearchRequest;
import com.e2d.odin.application.search.VerifyCitation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerController {

    private static final int MAX_QUERIES = 10;
    private static final int MAX_QUERY_LENGTH = 500;

    private final DescribeMe describeMe;
    private final SearchKnowledge search;
    private final ListDocuments listDocuments;
    private final ReadPage readPage;
    private final VerifyCitation verifyCitation;
    private final SaveKnowledgeCard saveCard;

    public WorkerController(DescribeMe describeMe, SearchKnowledge search, ListDocuments listDocuments,
            ReadPage readPage, VerifyCitation verifyCitation, SaveKnowledgeCard saveCard) {
        this.describeMe = describeMe;
        this.search = search;
        this.listDocuments = listDocuments;
        this.readPage = readPage;
        this.verifyCitation = verifyCitation;
        this.saveCard = saveCard;
    }

    @GetMapping("/v1/me")
    public Responses.Me me(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor) {
        return Responses.Me.from(describeMe.execute(actor));
    }

    @PostMapping("/v1/search")
    public Responses.SearchResponse search(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestBody Requests.SearchRequest request) {
        SearchRequest searchRequest = new SearchRequest(validQueries(request.queries()),
                Optional.ofNullable(request.projectId()).map(ProjectId::new), Optional.ofNullable(request.limit()));
        return Responses.SearchResponse.from(search.execute(actor, searchRequest));
    }

    @GetMapping("/v1/documents")
    public List<Responses.WorkerDocumentView> documents(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestParam(required = false) UUID projectId, @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) Integer limit) {
        return listDocuments.execute(actor, Optional.ofNullable(projectId).map(ProjectId::new), offset,
                Optional.ofNullable(limit)).stream().map(Responses.WorkerDocumentView::from).toList();
    }

    @GetMapping("/v1/documents/{documentId}/pages/{pageNumber}")
    public Responses.Page page(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID documentId,
            @PathVariable int pageNumber) {
        return Responses.Page.from(readPage.execute(actor, new DocumentId(documentId), pageNumber));
    }

    @PostMapping("/v1/citations/verify")
    public Responses.Verification verify(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestBody Requests.VerifyCitation request) {
        if (request.documentId() == null || request.pageNumber() == null || request.quote() == null) {
            throw new ValidationException("Faltan el documento, la página o la cita");
        }
        return new Responses.Verification(verifyCitation.execute(actor, new DocumentId(request.documentId()),
                request.pageNumber(), request.quote()));
    }

    @PostMapping("/v1/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.SavedCard saveCard(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestBody Requests.SaveCard request) {
        return Responses.SavedCard.from(saveCard.execute(actor, request.title(), request.body(),
                Optional.ofNullable(request.projectId()).map(ProjectId::new)));
    }

    private static List<String> validQueries(List<String> queries) {
        boolean sized = queries != null && !queries.isEmpty() && queries.size() <= MAX_QUERIES;
        boolean valid = sized && queries.stream().allMatch(q -> q != null && !q.isBlank()
                && q.trim().length() <= MAX_QUERY_LENGTH);
        if (!valid) {
            throw new ValidationException("Indica de 1 a " + MAX_QUERIES + " consultas, cada una con texto y de "
                    + MAX_QUERY_LENGTH + " caracteres como máximo");
        }
        return queries.stream().map(String::trim).toList();
    }
}
