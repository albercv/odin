package com.e2d.odin.application.link;

import com.e2d.odin.domain.document.DocumentFormat;

public record OriginalFile(String filename, DocumentFormat format, byte[] content) {
}
