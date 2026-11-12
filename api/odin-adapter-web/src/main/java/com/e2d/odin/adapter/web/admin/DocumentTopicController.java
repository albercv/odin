package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.topic.GetTaggingStatus;
import com.e2d.odin.application.topic.ListDocumentTopics;
import com.e2d.odin.application.topic.ReviewDocumentTopics;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.topic.TopicId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Temas de un documento, su revisión y el estado del etiquetado de la empresa. */
@RestController
public class DocumentTopicController {

    private final ListDocumentTopics list;
    private final ReviewDocumentTopics review;
    private final GetTaggingStatus status;

    public DocumentTopicController(ListDocumentTopics list, ReviewDocumentTopics review, GetTaggingStatus status) {
        this.list = list;
        this.review = review;
        this.status = status;
    }

    @GetMapping("/v1/admin/documents/{documentId}/topics")
    public Responses.DocumentTopicsResponse documentTopics(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID documentId) {
        return Responses.DocumentTopicsResponse.from(list.execute(actor, new DocumentId(documentId)));
    }

    @PutMapping("/v1/admin/documents/{documentId}/topics/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addTopic(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID documentId,
            @PathVariable UUID topicId) {
        review.add(actor, new DocumentId(documentId), new TopicId(topicId));
    }

    @DeleteMapping("/v1/admin/documents/{documentId}/topics/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTopic(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID documentId,
            @PathVariable UUID topicId) {
        review.remove(actor, new DocumentId(documentId), new TopicId(topicId));
    }

    @PostMapping("/v1/admin/documents/{documentId}/topics/retag")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retag(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID documentId) {
        review.retag(actor, new DocumentId(documentId));
    }

    @GetMapping("/v1/admin/companies/{companyId}/tagging-status")
    public Responses.TaggingStatusView taggingStatus(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId) {
        return Responses.TaggingStatusView.from(status.execute(actor, new CompanyId(companyId)));
    }
}
