package com.e2d.odin.adapter.readers;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.DocumentFormat;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfBoxTextReaderTest {

    @Test
    void pdfReaderReturnsTextPerPage() throws Exception {
        PdfBoxTextReader reader = new PdfBoxTextReader();

        ReadResult result = reader.read(twoPagePdf(), DocumentFormat.PDF);

        assertThat(reader.supports(DocumentFormat.PDF)).isTrue();
        assertThat(result.paginated()).isTrue();
        assertThat(result.readerName()).isEqualTo("pdfbox");
        assertThat(result.pageTexts()).hasSize(2);
        assertThat(result.pageTexts().get(0)).contains("Pagina ficticia uno");
        assertThat(result.pageTexts().get(1)).contains("Pagina ficticia dos");
    }

    private static byte[] twoPagePdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addPage(doc, "Pagina ficticia uno");
            addPage(doc, "Pagina ficticia dos");
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void addPage(PDDocument doc, String text) throws Exception {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(72, 700);
            stream.showText(text);
            stream.endText();
        }
    }
}
