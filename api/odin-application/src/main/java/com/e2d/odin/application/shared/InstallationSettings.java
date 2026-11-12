package com.e2d.odin.application.shared;

public record InstallationSettings(String setupToken, boolean scannedDocumentsSupported) {

    public InstallationSettings(String setupToken) {
        this(setupToken, false);
    }
}
