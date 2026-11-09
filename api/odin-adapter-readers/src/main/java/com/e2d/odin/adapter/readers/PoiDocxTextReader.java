package com.e2d.odin.adapter.readers;

import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.shared.UnsupportedFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

public class PoiDocxTextReader implements DocumentTextReader {

    static final String READER_NAME = "poi-ooxml";
    static final String READER_VERSION = "6.0.0";

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.DOCX;
    }

    @Override
    public ReadResult read(byte[] content, DocumentFormat format) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            String text = new XWPFWordExtractor(document).getText();
            return new ReadResult(List.of(text), false, READER_NAME, READER_VERSION);
        } catch (IOException e) {
            throw new UnsupportedFormatException("No se pudo leer el DOCX");
        }
    }
}
