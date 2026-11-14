package com.e2d.odin.application.intake;

/** Avisos de la bandeja: lo que falta en la instalación y limita la primera carga. */
public enum IntakeWarning {
    /** No hay lector OCR: los escaneos esperan. */
    NO_READER,
    /** No hay modelo local (ODIN_TAGGER_URL): solo propone con reglas. */
    NO_LOCAL_MODEL,
    /** El lote está en lectura y la lista de proyectos no está terminada: no se clasifica. */
    PROJECT_LIST_NOT_CONFIRMED
}
