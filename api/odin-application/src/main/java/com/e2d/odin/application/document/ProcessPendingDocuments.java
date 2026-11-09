package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.application.shared.ProcessingSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class ProcessPendingDocuments {

    private final DocumentRepository documents;
    private final OriginalFileStore files;
    private final List<DocumentTextReader> readers;
    private final ProcessingSettings settings;
    private final Clock clock;

    public ProcessPendingDocuments(DocumentRepository documents, OriginalFileStore files,
            List<DocumentTextReader> readers, ProcessingSettings settings, Clock clock) {
        this.documents = documents;
        this.files = files;
        this.readers = List.copyOf(readers);
        this.settings = settings;
        this.clock = clock;
    }

    public boolean processNext() {
        Optional<Document> claimed = documents.claimNextForProcessing(clock.instant(), settings.staleAfter());
        claimed.ifPresent(this::process);
        return claimed.isPresent();
    }

    private void process(Document document) {
        if (document.attemptCount() > settings.maxAttempts()) {
            documents.save(document.markFailed("demasiados intentos"));
            return;
        }
        Optional<DocumentTextReader> reader = readers.stream().filter(r -> r.supports(document.format())).findFirst();
        if (reader.isEmpty()) {
            documents.save(document.markPendingReader());
            return;
        }
        try {
            ReadResult result = reader.get().read(files.read(document.sha256()), document.format());
            store(document, result);
        } catch (RuntimeException e) {
            documents.save(document.markFailed(String.valueOf(e.getMessage())));
        }
    }

    private void store(Document document, ReadResult result) {
        List<String> texts = result.paginated() ? result.pageTexts() : List.of(String.join("\n", result.pageTexts()));
        if (texts.stream().allMatch(String::isBlank)) {
            documents.save(document.markPendingReader());
            return;
        }
        List<DocumentPage> pages = IntStream.range(0, texts.size())
                .mapToObj(i -> new DocumentPage(document.id(), i + 1, texts.get(i), result.readerName(),
                        result.readerVersion()))
                .toList();
        documents.replacePages(document.id(), pages);
        documents.save(document.markReady(result.paginated()));
    }
}
