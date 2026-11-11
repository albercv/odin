package com.e2d.odin.adapter.readers;

import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.shared.UnsupportedFormatException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfBoxTextReader implements DocumentTextReader {

    static final String READER_NAME = "pdfbox";
    static final String READER_VERSION = "3.0.8";

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.PDF;
    }

    @Override
    public ReadResult read(byte[] content, DocumentFormat format) {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new ReadResult(pageTexts(document), true, READER_NAME, READER_VERSION);
        } catch (IOException e) {
            throw new UnsupportedFormatException("No se pudo leer el PDF");
        }
    }

    private static List<String> pageTexts(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        List<String> texts = new ArrayList<>();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            texts.add(stripper.getText(document));
        }
        return texts;
    }
}
