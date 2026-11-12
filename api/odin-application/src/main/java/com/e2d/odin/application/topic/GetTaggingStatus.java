package com.e2d.odin.application.topic;

import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;

/** Cuántos documentos esperan sus temas y cuándo se etiqueta: lo que el panel enseña a quien administra. */
public class GetTaggingStatus {

    private final TaggingRepository tagging;
    private final TaggingWindow window;

    public GetTaggingStatus(TaggingRepository tagging, TaggingWindow window) {
        this.tagging = tagging;
        this.window = window;
    }

    public TaggingStatusReport execute(Actor actor, CompanyId companyId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        return new TaggingStatusReport(tagging.backlog(companyId), window);
    }
}
