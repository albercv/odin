package com.e2d.odin.application.shared;

/** Lo que la primera carga necesita saber de la instalación, además del lector OCR ({@link InstallationSettings}). */
public record IntakeSettings(boolean localModelConfigured) {
}
