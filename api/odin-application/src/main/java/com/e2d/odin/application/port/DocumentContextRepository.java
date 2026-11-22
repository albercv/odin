package com.e2d.odin.application.port;

import com.e2d.odin.domain.context.DocumentContext;
import com.e2d.odin.domain.context.DocumentCorrection;
import com.e2d.odin.domain.context.PageText;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Versiones y correcciones de documentos ya visibles para quien lee; cada salto se vuelve a filtrar por visibilidad. */
public interface DocumentContextRepository {

    Map<DocumentId, DocumentContext> contexts(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> ids);

    Map<DocumentId, List<DocumentCorrection>> corrections(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> ids);

    /** La mejor página de cada documento para la misma consulta de texto de la búsqueda, sin tocar su orden. */
    Map<DocumentId, PageText> bestMatches(CompanyScope scope, ProjectVisibility visibility, List<String> terms,
            Collection<DocumentId> ids);
}
