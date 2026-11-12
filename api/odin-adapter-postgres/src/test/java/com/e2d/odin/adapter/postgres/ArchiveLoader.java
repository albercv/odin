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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Carga un corpus de ficheros de texto en la base: un proyecto cerrado por carpeta, documentos {@code READY} con sus páginas. */
final class ArchiveLoader {

    private static final Pattern PAGE_MARK = Pattern.compile("^=== Página (\\d+) ===\\s*$", Pattern.MULTILINE);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AbstractPostgresIT it;
    private final Map<String, LoadedDocument> documents = new LinkedHashMap<>();

    private record LoadedDocument(DocumentId id, CompanyId company, Optional<ProjectId> project) {
    }

    ArchiveLoader(AbstractPostgresIT it) {
        this.it = it;
    }

    /** Un nombre de proyecto {@code null} carga la carpeta sin proyecto (documentos de toda la empresa). */
    LoadedArchive load(CompanyId company, UserId uploader, Path corpusDir, Map<String, String> projectNamesByFolder)
            throws IOException {
        return load(company, uploader, corpusDir, projectNamesByFolder, key -> false);
    }

    /** {@code isCaseDocument} recibe la clave {@code carpeta/fichero} y dice si el documento se carga con la marca de caso. */
    LoadedArchive load(CompanyId company, UserId uploader, Path corpusDir, Map<String, String> projectNamesByFolder,
            Predicate<String> isCaseDocument) throws IOException {
        Map<String, ProjectId> projectByFolder = new LinkedHashMap<>();
        Map<String, Map<Integer, String>> pagesByDocument = new LinkedHashMap<>();
        for (Map.Entry<String, String> folder : projectNamesByFolder.entrySet()) {
            Optional<ProjectId> project = Optional.ofNullable(folder.getValue()).map(name -> newProject(company, name));
            project.ifPresent(p -> projectByFolder.put(folder.getKey(), p));
            try (var files = Files.list(corpusDir.resolve(folder.getKey()))) {
                for (Path file : files.sorted().toList()) {
                    String key = folder.getKey() + "/" + file.getFileName();
                    pagesByDocument.put(key, loadDocument(company, uploader, project, key, file,
                            isCaseDocument.test(key)));
                }
            }
        }
        return new LoadedArchive(projectByFolder, pagesByDocument);
    }

    /**
     * Marca como sustituidos los documentos viejos de las contradicciones {@code version} de {@code claves.json} que
     * son de {@code company}, con la hora fija de las pruebas. Devuelve ruta del viejo -> ruta del vigente.
     */
    Map<String, String> applySupersessions(CompanyId company, Path claves) throws IOException {
        Map<String, String> marks = new LinkedHashMap<>();
        for (Map.Entry<String, String> couple : versionCouples(claves).entrySet()) {
            LoadedDocument old = documents.get(couple.getKey());
            LoadedDocument current = documents.get(couple.getValue());
            if (old == null || current == null) {
                throw new IllegalStateException(couple(couple) + " no está en el corpus");
            }
            if (!old.company().equals(company)) {
                continue;
            }
            if (!current.company().equals(company) || !(current.project().isEmpty() || current.project().equals(old.project()))) {
                throw new IllegalStateException(couple(couple) + " no se puede marcar: otra empresa u otro proyecto");
            }
            it.docs.markSuperseded(company, old.id(), current.id(), AbstractPostgresIT.NOW);
            marks.put(couple.getKey(), couple.getValue());
        }
        return marks;
    }

    private static String couple(Map.Entry<String, String> couple) {
        return "La pareja " + couple.getKey() + " → " + couple.getValue();
    }

    /** Las mismas parejas que {@code pila/sustituciones.py}: cada fuente de una entrada {@code version} distinta del vigente. */
    private static Map<String, String> versionCouples(Path claves) throws IOException {
        Map<String, String> couples = new LinkedHashMap<>();
        for (JsonNode entry : JSON.readTree(Files.readString(claves))) {
            if (!"version".equals(entry.get("tipo").asString())) {
                continue;
            }
            String current = entry.get("vigente").get("doc").asString();
            for (JsonNode source : entry.get("fuentes")) {
                String old = source.get("doc").asString();
                if (!old.equals(current) && couples.putIfAbsent(old, current) != null) {
                    throw new IllegalStateException("El documento " + old + " tiene dos sustitutos");
                }
            }
        }
        return couples;
    }

    private ProjectId newProject(CompanyId company, String name) {
        ProjectId project = new ProjectId(UUID.randomUUID());
        it.orgs.saveProject(new Project(project, company, name, true));
        return project;
    }

    private Map<Integer, String> loadDocument(CompanyId company, UserId uploader, Optional<ProjectId> project,
            String key, Path file, boolean caseDocument) throws IOException {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, project, key,
                file.getFileName().toString(), DocumentFormat.PLAIN_TEXT, UUID.randomUUID().toString(),
                DocumentStatus.READY, Optional.empty(), true, uploader, AbstractPostgresIT.NOW, Optional.empty(), 0, Optional.empty(), Optional.empty(), List.of(), caseDocument);
        it.docs.save(d);
        documents.put(key, new LoadedDocument(d.id(), company, project));
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
