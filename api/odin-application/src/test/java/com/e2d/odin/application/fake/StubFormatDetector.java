package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.DocumentFormatDetector;
import com.e2d.odin.domain.document.DocumentFormat;
import java.util.Optional;

public class StubFormatDetector implements DocumentFormatDetector {

    private Optional<DocumentFormat> format;

    public StubFormatDetector(DocumentFormat format) {
        this.format = Optional.ofNullable(format);
    }

    public void detects(DocumentFormat newFormat) {
        format = Optional.ofNullable(newFormat);
    }

    @Override
    public Optional<DocumentFormat> detect(byte[] content) {
        return format;
    }
}
