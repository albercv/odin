package com.e2d.odin.adapter.readers;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.DocumentFormat;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class MagicBytesFormatDetectorTest {

    private final MagicBytesFormatDetector detector = new MagicBytesFormatDetector();

    @Test
    void detectsPdfDocxImageTextMarkdown() throws Exception {
        assertThat(detector.detect("%PDF-1.4 ficticio".getBytes(StandardCharsets.US_ASCII)))
                .contains(DocumentFormat.PDF);
        assertThat(detector.detect(docxBytes())).contains(DocumentFormat.DOCX);
        assertThat(detector.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}))
                .contains(DocumentFormat.IMAGE);
        assertThat(detector.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0}))
                .contains(DocumentFormat.IMAGE);
        assertThat(detector.detect("II*\0ficticio".getBytes(StandardCharsets.US_ASCII)))
                .contains(DocumentFormat.IMAGE);
        assertThat(detector.detect("Texto plano ficticio".getBytes(StandardCharsets.UTF_8)))
                .contains(DocumentFormat.PLAIN_TEXT);
        assertThat(detector.detect("# Titulo ficticio".getBytes(StandardCharsets.UTF_8)))
                .contains(DocumentFormat.MARKDOWN);
        assertThat(detector.detect("Parrafo\n# Seccion".getBytes(StandardCharsets.UTF_8)))
                .contains(DocumentFormat.MARKDOWN);
    }

    @Test
    void detectsXlsxXlsAndRejectsOle2Doc() throws Exception {
        assertThat(detector.detect(write(new XSSFWorkbook()))).contains(DocumentFormat.XLSX);
        HSSFWorkbook xls = new HSSFWorkbook();
        xls.createSheet("A");
        assertThat(detector.detect(write(xls))).contains(DocumentFormat.XLS);
        ByteArrayOutputStream doc = new ByteArrayOutputStream();
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            fs.createDocument(new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}), "WordDocument");
            fs.writeFilesystem(doc);
        }
        assertThat(detector.detect(doc.toByteArray())).isEmpty();
    }

    @Test
    void detectsOdsButNotOdt() throws Exception {
        assertThat(detector.detect(OdsSheetsTest.zip("application/vnd.oasis.opendocument.spreadsheet", "<a/>")))
                .contains(DocumentFormat.ODS);
        assertThat(detector.detect(OdsSheetsTest.zip("application/vnd.oasis.opendocument.text", "<a/>"))).isEmpty();
    }

    private static byte[] write(org.apache.poi.ss.usermodel.Workbook workbook) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }

    @Test
    void textWithPdfExtensionIsText() {
        assertThat(detector.detect("Esto no es un PDF".getBytes(StandardCharsets.UTF_8)))
                .contains(DocumentFormat.PLAIN_TEXT);
    }

    @Test
    void emptyIsUndetected() {
        assertThat(detector.detect(new byte[0])).isEmpty();
    }

    @Test
    void binaryWithoutSignatureIsUndetected() {
        assertThat(detector.detect(new byte[] {1, 0, 2, 0, 3})).isEmpty();
    }

    private static byte[] docxBytes() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Parrafo ficticio");
            doc.write(out);
            return out.toByteArray();
        }
    }
}
