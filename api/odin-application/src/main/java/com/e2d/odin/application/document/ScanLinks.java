package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La «copia escaneada» (D3) vista desde un documento: de quién es copia y qué escaneos tiene, solo los que ve quien
 * mira. Con dos consultas para toda la página.
 */
public record ScanLinks(Optional<DocumentRef> scanOf, List<DocumentRef> scanCopies) {

    public static final ScanLinks NONE = new ScanLinks(Optional.empty(), List.of());

    /** Con {@code status}, solo cuentan como copias los escaneos en ese estado (el trabajador, solo los READY). */
    public static Map<DocumentId, ScanLinks> of(DocumentRepository documents, CompanyScope scope,
            ProjectVisibility visibility, Optional<DocumentStatus> status, Collection<Document> docs) {
        Set<DocumentId> ids = docs.stream().map(Document::id).collect(Collectors.toSet());
        Set<DocumentId> originalIds = docs.stream().flatMap(d -> d.scanOf().stream()).collect(Collectors.toSet());
        Map<DocumentId, List<DocumentRef>> copies = documents.scanCopiesOf(scope, visibility, status, ids);
        Map<DocumentId, DocumentRef> originals = documents.refsOf(scope, visibility, originalIds);
        Map<DocumentId, ScanLinks> links = new HashMap<>();
        for (Document d : docs) {
            links.put(d.id(), new ScanLinks(d.scanOf().map(originals::get), copies.getOrDefault(d.id(), List.of())));
        }
        return links;
    }
}
