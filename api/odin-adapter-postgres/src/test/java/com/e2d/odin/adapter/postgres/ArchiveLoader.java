package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Carga un corpus de ficheros de texto en la base: un proyecto cerrado por carpeta, documentos {@code READY} con sus páginas. */
final class ArchiveLoader {

    private static final Pattern PAGE_MARK = Pattern.compile("^=== Página (\\d+) ===\\s*$", Pattern.MULTILINE);
    private final AbstractPostgresIT it;

    ArchiveLoader(AbstractPostgresIT it) {
        this.it = it;
    }

    /** Un nombre de proyecto {@code null} carga la carpeta sin proyecto (documentos de toda la empresa). */
    LoadedArchive load(CompanyId company, UserId uploader, Path corpusDir, Map<String, String> projectNamesByFolder)
            throws IOException {
        Map<String, ProjectId> projectByFolder = new LinkedHashMap<>();
        Map<String, Map<Integer, String>> pagesByDocument = new LinkedHashMap<>();
        for (Map.Entry<String, String> folder : projectNamesByFolder.entrySet()) {
            Optional<ProjectId> project = Optional.ofNullable(folder.getValue()).map(name -> newProject(company, name));
            project.ifPresent(p -> projectByFolder.put(folder.getKey(), p));
            try (var files = Files.list(corpusDir.resolve(folder.getKey()))) {
                for (Path file : files.sorted().toList()) {
                    String key = folder.getKey() + "/" + file.getFileName();
                    pagesByDocument.put(key, loadDocument(company, uploader, project, key, file));
                }
            }
        }
        return new LoadedArchive(projectByFolder, pagesByDocument);
    }

    private ProjectId newProject(CompanyId company, String name) {
        ProjectId project = new ProjectId(UUID.randomUUID());
        it.orgs.saveProject(new Project(project, company, name, true));
        return project;
    }

    private Map<Integer, String> loadDocument(CompanyId company, UserId uploader, Optional<ProjectId> project,
            String key, Path file) throws IOException {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, project, key,
                file.getFileName().toString(), DocumentFormat.PLAIN_TEXT, UUID.randomUUID().toString(),
                DocumentStatus.READY, Optional.empty(), true, uploader, AbstractPostgresIT.NOW, Optional.empty(), 0);
        it.docs.save(d);
        Map<Integer, String> pages = splitPages(Files.readString(file));
        it.docs.replacePages(d.id(), pages.entrySet().stream()
                .map(p -> new DocumentPage(d.id(), p.getKey(), p.getValue(), "corpus", "1")).toList());
        return pages;
    }

    static Map<Integer, String> splitPages(String content) {
        Map<Integer, String> pages = new LinkedHashMap<>();
        Matcher marks = PAGE_MARK.matcher(content);
        int number = 0;
        int start = 0;
        while (marks.find()) {
            if (number > 0) {
                pages.put(number, content.substring(start, marks.start()).strip());
            }
            number = Integer.parseInt(marks.group(1));
            start = marks.end();
        }
        pages.put(number, content.substring(start).strip());
        return pages;
    }
}
