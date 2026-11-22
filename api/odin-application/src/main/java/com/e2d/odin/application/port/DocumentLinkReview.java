package com.e2d.odin.application.port;

import com.e2d.odin.domain.citation.LinkRelation;
import com.e2d.odin.domain.citation.LinkStatus;
import com.e2d.odin.domain.context.CorrectionDirection;
import com.e2d.odin.domain.context.PageText;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lo que el administrador ve de los vínculos de un documento y lo que decide sobre cada uno. */
public interface DocumentLinkReview {

    record Link(UUID id, CompanyId companyId, DocumentId source, Optional<DocumentId> target, LinkRelation relation,
            LinkStatus status) {
    }

    /** {@code other} vacío: la cita aún no apunta a ningún documento. */
    record LinkView(UUID id, CorrectionDirection direction, LinkRelation relation, LinkStatus status, String origin,
            Optional<DocumentRef> other, String citedIdentifier, PageText evidence) {
    }

    Optional<Link> find(CompanyScope scope, UUID linkId);

    /** Todos los vínculos del documento, en los dos sentidos y en cualquier estado. */
    List<LinkView> linksOf(CompanyScope scope, DocumentId document);

    void setStatus(CompanyId company, UUID linkId, LinkStatus status, UserId reviewer, Instant at);
}
