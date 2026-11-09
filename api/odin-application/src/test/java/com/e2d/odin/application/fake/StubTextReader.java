package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.DocumentFormat;
import java.util.Set;

public class StubTextReader implements DocumentTextReader {

    private final Set<DocumentFormat> formats;
    private final ReadResult result;
    private final RuntimeException failure;

    public StubTextReader(Set<DocumentFormat> formats, ReadResult result) {
        this(formats, result, null);
    }

    public StubTextReader(Set<DocumentFormat> formats, RuntimeException failure) {
        this(formats, null, failure);
    }

    private StubTextReader(Set<DocumentFormat> formats, ReadResult result, RuntimeException failure) {
        this.formats = formats;
        this.result = result;
        this.failure = failure;
    }

    @Override
    public boolean supports(DocumentFormat format) {
        return formats.contains(format);
    }

    @Override
    public ReadResult read(byte[] content, DocumentFormat format) {
        if (failure != null) {
            throw failure;
        }
        return result;
    }
}
