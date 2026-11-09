package com.e2d.odin.application.port;

import java.util.List;

public record ReadResult(List<String> pageTexts, boolean paginated, String readerName, String readerVersion) {
}
