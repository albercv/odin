package com.e2d.odin.application.port;

import com.e2d.odin.domain.document.DocumentFormat;

public interface DocumentTextReader {

    boolean supports(DocumentFormat format);

    ReadResult read(byte[] content, DocumentFormat format);
}
