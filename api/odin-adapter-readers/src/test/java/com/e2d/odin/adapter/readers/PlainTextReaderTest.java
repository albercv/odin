package com.e2d.odin.adapter.readers;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.DocumentFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PlainTextReaderTest {

    @Test
    void plainTextAndMarkdownReturnSinglePage() {
        PlainTextReader reader = new PlainTextReader();
        byte[] content = "# Titulo ficticio\nTexto ficticio".getBytes(StandardCharsets.UTF_8);

        assertThat(reader.supports(DocumentFormat.PLAIN_TEXT)).isTrue();
        assertThat(reader.supports(DocumentFormat.MARKDOWN)).isTrue();
        assertThat(reader.supports(DocumentFormat.PDF)).isFalse();
        ReadResult result = reader.read(content, DocumentFormat.MARKDOWN);

        assertThat(result.paginated()).isFalse();
        assertThat(result.pageTexts()).containsExactly("# Titulo ficticio\nTexto ficticio");
    }
}
