package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.CaseFileSettings;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El texto de todas las páginas del expediente por tandas: la ficha primero y después por fecha de alta. */
public class ReadCaseFile {

    private static final String INVALID_CURSOR = "El cursor no es válido";

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final GetCaseFile getCaseFile;
    private final CaseFileSettings settings;

    public ReadCaseFile(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles, CaseFileSettings settings) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.getCaseFile = new GetCaseFile(organizations, projectMembers, documents, caseFiles);
        this.settings = settings;
    }

    private record Source(DocumentEntry entry, int pageCount) {
    }

    public CaseFileText execute(Actor actor, DocumentId caseId, Optional<String> cursor) {
        CaseFileView view = getCaseFile.execute(actor, caseId);
        var access = CaseFileAccess.of(actor, Optional.empty(), organizations, projectMembers);
        List<Source> sources = new ArrayList<>(List.of(new Source(view.caseDocument(), view.casePageCount())));
        view.documents().forEach(d -> sources.add(new Source(d.document(), d.pageCount())));
        int total = sources.stream().mapToInt(Source::pageCount).sum();
        int start = cursor.map(c -> position(c, sources)).orElse(0);
        List<CaseFileTextPage> pages = new ArrayList<>();
        int position = start;
        int chars = 0;
        while (position < total) {
            CaseFileTextPage page = pageAt(access, sources, position);
            if (!pages.isEmpty() && chars + page.text().length() > settings.chunkChars()) {
                break;
            }
            pages.add(page);
            chars += page.text().length();
            position++;
        }
        boolean complete = position >= total;
        return new CaseFileText(pages, complete ? Optional.empty() : Optional.of(cursorAt(sources, position)),
                complete, documentsRead(sources, position), sources.size(), position, total,
                view.pendingDocuments());
    }

    private CaseFileTextPage pageAt(CaseFileAccess access, List<Source> sources, int position) {
        int offset = position;
        for (Source source : sources) {
            if (offset < source.pageCount()) {
                var document = source.entry().document();
                String text = documents.findPage(access.scope(), access.visibility(), document.id(), offset + 1)
                        .map(DocumentPage::text).orElse("");
                return new CaseFileTextPage(document.id(), document.title(), offset + 1, text, text.isBlank());
            }
            offset -= source.pageCount();
        }
        throw new IllegalStateException("Posición fuera del expediente: " + position);
    }

    private static int documentsRead(List<Source> sources, int position) {
        int read = 0;
        int end = 0;
        for (Source source : sources) {
            end += source.pageCount();
            if (end <= position) {
                read++;
            }
        }
        return read;
    }

    private static String cursorAt(List<Source> sources, int position) {
        int offset = position;
        for (Source source : sources) {
            if (offset < source.pageCount()) {
                String raw = source.entry().document().id().value() + ":" + (offset + 1);
                return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            }
            offset -= source.pageCount();
        }
        throw new IllegalStateException("Posición fuera del expediente: " + position);
    }

    private static int position(String cursor, List<Source> sources) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":");
            UUID document = UUID.fromString(parts[0]);
            int page = Integer.parseInt(parts[1]);
            int before = 0;
            for (Source source : sources) {
                if (source.entry().document().id().value().equals(document)) {
                    if (page < 1 || page > source.pageCount()) {
                        break;
                    }
                    return before + page - 1;
                }
                before += source.pageCount();
            }
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new ValidationException(INVALID_CURSOR);
        }
        throw new ValidationException(INVALID_CURSOR);
    }
}
