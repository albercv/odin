package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentPagesTest {

    private final Document document = Fixtures.document(new InMemoryDocumentRepository(),
            Fixtures.company(new InMemoryOrganizationRepository()), DocumentStatus.PROCESSING);

    @Test
    void paginatedResultKeepsPages() {
        List<DocumentPage> pages = DocumentPages.from(document,
                new ReadResult(List.of("uno", "dos"), true, "pdfbox", "3"));

        assertThat(pages).extracting(DocumentPage::number, DocumentPage::text, DocumentPage::readerName,
                DocumentPage::readerVersion).containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "uno", "pdfbox", "3"),
                        org.assertj.core.groups.Tuple.tuple(2, "dos", "pdfbox", "3"));
        assertThat(pages).allMatch(p -> p.documentId().equals(document.id()));
    }

    @Test
    void nonPaginatedResultIsOnePage() {
        List<DocumentPage> pages = DocumentPages.from(document,
                new ReadResult(List.of("uno", "dos"), false, "text", "1"));

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).text()).isEqualTo("uno\ndos");
    }

    @Test
    void withoutTextListsBlankPages() {
        List<DocumentPage> pages = DocumentPages.from(document,
                new ReadResult(List.of("uno", " \n", "tres", ""), true, "pdfbox", "3"));

        assertThat(DocumentPages.withoutText(pages)).containsExactly(2, 4);
    }
}
