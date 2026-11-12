package com.e2d.odin.application.document;

import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import java.util.List;
import java.util.stream.IntStream;

/** Convierte lo que devuelve un lector en las páginas del documento; lo usan el carril local y el del lector. */
final class DocumentPages {

    private DocumentPages() {
    }

    static List<DocumentPage> from(Document document, ReadResult result) {
        List<String> texts = result.paginated() ? result.pageTexts() : List.of(String.join("\n", result.pageTexts()));
        return IntStream.range(0, texts.size())
                .mapToObj(i -> new DocumentPage(document.id(), i + 1, texts.get(i), result.readerName(),
                        result.readerVersion()))
                .toList();
    }

    /** Números de las páginas que quedan en blanco tras quitar los espacios. */
    static List<Integer> withoutText(List<DocumentPage> pages) {
        return pages.stream().filter(p -> p.text().strip().isEmpty()).map(DocumentPage::number).toList();
    }
}
