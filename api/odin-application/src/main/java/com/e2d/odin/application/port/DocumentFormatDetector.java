package com.e2d.odin.application.port;

import com.e2d.odin.domain.document.DocumentFormat;
import java.util.Optional;

public interface DocumentFormatDetector {

    Optional<DocumentFormat> detect(byte[] content);
}
