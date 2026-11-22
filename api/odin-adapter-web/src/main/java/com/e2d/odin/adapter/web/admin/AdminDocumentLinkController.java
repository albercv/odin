package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.context.ReviewDocumentLinks;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminDocumentLinkController {

    private final ReviewDocumentLinks review;

    public AdminDocumentLinkController(ReviewDocumentLinks review) {
        this.review = review;
    }

    @GetMapping("/v1/admin/documents/{documentId}/context")
    public Responses.DocumentContextView context(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID documentId) {
        return Responses.DocumentContextView.from(review.context(actor, new DocumentId(documentId)));
    }

    @PostMapping("/v1/admin/document-links/{linkId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID linkId) {
        review.confirm(actor, linkId);
    }

    @PostMapping("/v1/admin/document-links/{linkId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID linkId) {
        review.dismiss(actor, linkId);
    }
}
