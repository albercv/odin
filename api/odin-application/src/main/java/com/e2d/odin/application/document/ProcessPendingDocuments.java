package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.application.shared.ProcessingSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.shared.UnsupportedFormatException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class ProcessPendingDocuments {

    public static final String TOO_MANY_ATTEMPTS = "demasiados intentos";
    public static final String UNREADABLE = "Documento ilegible: el fichero está dañado o no se puede leer.";
    public static final String READER_UNAVAILABLE = "Lector no disponible: no se pudo leer el documento. Reinténtalo.";
    private static final System.Logger LOG = System.getLogger(ProcessPendingDocuments.class.getName());

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
            documents.save(document.markFailed(TOO_MANY_ATTEMPTS));
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
            // el detalle va solo al log del servidor; el documento guarda un motivo genérico
            LOG.log(System.Logger.Level.WARNING, "No se pudo leer el documento " + document.id().value(), e);
            documents.save(document.markFailed(e instanceof UnsupportedFormatException ? UNREADABLE : READER_UNAVAILABLE));
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
