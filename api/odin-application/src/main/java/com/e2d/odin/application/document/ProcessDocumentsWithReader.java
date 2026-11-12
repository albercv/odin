package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.DocumentTooLargeForReaderException;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.application.port.ReaderService;
import com.e2d.odin.application.port.ReaderUnavailableException;
import com.e2d.odin.application.port.UnreadableDocumentException;
import com.e2d.odin.application.shared.ReaderSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** Carril del lector OCR: lee los documentos en espera con el servicio lector, aparte del carril local. */
public class ProcessDocumentsWithReader {

    public static final String READER_FAILED = "El lector OCR no pudo leer el documento. Reinténtalo.";
    public static final String NO_TEXT_FOUND = "El lector OCR no encontró texto en el documento.";
    public static final String TOO_LARGE_FOR_READER =
            "El documento es demasiado grande para el lector OCR: divídelo en partes más pequeñas.";
    private static final System.Logger LOG = System.getLogger(ProcessDocumentsWithReader.class.getName());

    private final DocumentRepository documents;
    private final OriginalFileStore files;
    private final ReaderService reader;
    private final ReaderSettings settings;
    private final Clock clock;

    public ProcessDocumentsWithReader(DocumentRepository documents, OriginalFileStore files, ReaderService reader,
            ReaderSettings settings, Clock clock) {
        this.documents = documents;
        this.files = files;
        this.reader = reader;
        this.settings = settings;
        this.clock = clock;
    }

    public boolean processNext() {
        if (!reader.isAvailable()) {
            return false;
        }
        Optional<Document> claimed = documents.claimNextForReader(clock.instant(), settings.staleAfter());
        claimed.ifPresent(this::process);
        return claimed.isPresent();
    }

    private void process(Document document) {
        if (document.attemptCount() > settings.maxAttempts()) {
            documents.save(document.markFailed(ProcessPendingDocuments.TOO_MANY_ATTEMPTS));
            return;
        }
        try {
            store(document, reader.read(files.read(document.sha256()), document.format()));
        } catch (ReaderUnavailableException e) {
            documents.save(document.returnToReaderQueue(false));
        } catch (UnreadableDocumentException e) {
            documents.save(document.markFailed(ProcessPendingDocuments.UNREADABLE));
        } catch (DocumentTooLargeForReaderException e) {
            documents.save(document.markFailed(TOO_LARGE_FOR_READER));
        } catch (RuntimeException e) {
            // el detalle va solo al log del servidor; el documento guarda un motivo genérico
            LOG.log(System.Logger.Level.WARNING, "El lector no pudo leer el documento " + document.id().value(), e);
            documents.save(document.attemptCount() >= settings.maxAttempts() ? document.markFailed(READER_FAILED)
                    : document.returnToReaderQueue(true));
        }
    }

    private void store(Document document, ReadResult result) {
        List<DocumentPage> pages = DocumentPages.from(document, result);
        List<Integer> blank = DocumentPages.withoutText(pages);
        if (blank.size() == pages.size()) {
            documents.save(document.markFailed(NO_TEXT_FOUND));
            return;
        }
        documents.replacePages(document.id(), pages);
        documents.save(document.markReady(result.paginated(), blank));
    }
}
