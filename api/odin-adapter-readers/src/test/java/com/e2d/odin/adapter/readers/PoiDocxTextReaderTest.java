package com.e2d.odin.adapter.readers;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.DocumentFormat;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class PoiDocxTextReaderTest {

    @Test
    void docxReaderReturnsSinglePage() throws Exception {
        PoiDocxTextReader reader = new PoiDocxTextReader();

        ReadResult result = reader.read(docx("Parrafo ficticio uno", "Parrafo ficticio dos"), DocumentFormat.DOCX);

        assertThat(reader.supports(DocumentFormat.DOCX)).isTrue();
        assertThat(result.paginated()).isFalse();
        assertThat(result.readerName()).isEqualTo("poi-ooxml");
        assertThat(result.pageTexts()).hasSize(1);
        assertThat(result.pageTexts().get(0)).contains("Parrafo ficticio uno").contains("Parrafo ficticio dos");
    }

    private static byte[] docx(String... paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                doc.createParagraph().createRun().setText(paragraph);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }
}
