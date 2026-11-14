package com.e2d.odin.application.link;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.organization.Project;

/** Adónde lleva un enlace ya comprobado: a un caso, a un proyecto o al original de un documento. */
public sealed interface LinkTarget {

    record CaseTarget(Document caseDocument) implements LinkTarget {
    }

    record ProjectTarget(Project project) implements LinkTarget {
    }

    record OriginalTarget(Document document) implements LinkTarget {
    }
}
