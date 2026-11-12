package com.e2d.odin.application.port;

public interface TopicTagger {

    boolean isAvailable();

    /** Etiqueta un texto con las instrucciones ya montadas. */
    TaggerAnswer tag(String instructions);
}
