package com.e2d.odin.adapter.readers;

import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.DocumentFormat;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PlainTextReader implements DocumentTextReader {

    static final String READER_NAME = "odin-plain-text";
    static final String READER_VERSION = "1";

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.PLAIN_TEXT || format == DocumentFormat.MARKDOWN;
    }

    @Override
    public ReadResult read(byte[] content, DocumentFormat format) {
        String text = new String(content, StandardCharsets.UTF_8);
        return new ReadResult(List.of(text), false, READER_NAME, READER_VERSION);
    }
}
