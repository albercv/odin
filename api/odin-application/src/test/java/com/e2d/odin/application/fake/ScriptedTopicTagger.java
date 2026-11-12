package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.TaggerAnswer;
import com.e2d.odin.application.port.TopicTagger;
import java.util.ArrayList;
import java.util.List;

/** Etiquetador con guion: cada llamada devuelve o lanza lo siguiente del guion, y la última entrada se repite. */
public class ScriptedTopicTagger implements TopicTagger {

    public static final String VERSION = "modelo-de-prueba; instrucciones v1";
    private static final int CONTEXT = 4096;

    private final List<Object> script = new ArrayList<>();
    private final List<String> received = new ArrayList<>();
    private boolean available = true;

    public ScriptedTopicTagger available(boolean value) {
        available = value;
        return this;
    }

    public ScriptedTopicTagger answers(String text) {
        script.add(new TaggerAnswer(text, 100, CONTEXT, VERSION));
        return this;
    }

    /** Una respuesta que ocupó todo el contexto: el texto no se leyó entero. */
    public ScriptedTopicTagger truncated() {
        script.add(new TaggerAnswer("", CONTEXT, CONTEXT, VERSION));
        return this;
    }

    public ScriptedTopicTagger fails(RuntimeException error) {
        script.add(error);
        return this;
    }

    public List<String> received() {
        return received;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public TaggerAnswer tag(String instructions) {
        Object next = script.get(Math.min(received.size(), script.size() - 1));
        received.add(instructions);
        if (next instanceof RuntimeException error) {
            throw error;
        }
        return (TaggerAnswer) next;
    }
}
