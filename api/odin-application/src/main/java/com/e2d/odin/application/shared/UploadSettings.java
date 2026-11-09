package com.e2d.odin.application.shared;

public record UploadSettings(long maxBytes) {

    public UploadSettings() {
        this(52_428_800L);
    }
}
