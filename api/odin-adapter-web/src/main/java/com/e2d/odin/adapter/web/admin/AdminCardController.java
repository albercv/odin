package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.knowledge.ListCardsForReview;
import com.e2d.odin.application.knowledge.ReviewCard;
import io.swagger.v3.oas.annotations.Parameter;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminCardController {

    private static final int MAX_QUERY = 100;

    private final ListCardsForReview list;
    private final ReviewCard review;

    public AdminCardController(ListCardsForReview list, ReviewCard review) {
        this.list = list;
        this.review = review;
    }

    @GetMapping("/v1/admin/companies/{companyId}/cards")
    public Responses.CardPage list(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestParam(defaultValue = "PENDING") CardStatus status,
            @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Texto del título o del cuerpo, sin mayúsculas ni acentos (hasta 100 caracteres)")
            @RequestParam(required = false) String q) {
        if (q != null && q.strip().length() > MAX_QUERY) {
            throw new ValidationException("La búsqueda admite hasta " + MAX_QUERY + " caracteres");
        }
        var page = list.page(actor, new CompanyId(companyId), status, Optional.ofNullable(q), offset, limit);
        return new Responses.CardPage(page.cards().stream().map(Responses.CardView::from).toList(), page.total());
    }

    @PostMapping("/v1/admin/cards/{cardId}/review")
    public Responses.CardView review(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID cardId, @RequestBody Requests.Review request) {
        if (request.decision() == null) {
            throw new ValidationException("Falta la decisión");
        }
        return Responses.CardView.from(review.execute(actor, new CardId(cardId), request.decision(),
                Optional.ofNullable(request.correctedBody()), Optional.ofNullable(request.note())));
    }
}
