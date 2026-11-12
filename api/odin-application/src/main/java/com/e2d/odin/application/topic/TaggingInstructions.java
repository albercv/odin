package com.e2d.odin.application.topic;

import com.e2d.odin.domain.topic.Topic;
import java.util.List;
import java.util.stream.Collectors;

/** Las instrucciones que se midieron en T-010 (clasificar.py, espacio tras los dos puntos y punto final incluidos), con el nombre de la empresa y la definición de cada tema. */
public final class TaggingInstructions {

    /** Cambiar el texto de {@link #build} obliga a cambiar esta versión y a volver a medir. */
    public static final String VERSION = "instrucciones v1";

    private TaggingInstructions() {
    }

    public static String build(String companyName, List<Topic> topics, String text) {
        String list = topics.stream().map(t -> "\n- " + t.name() + ": " + t.definition())
                .collect(Collectors.joining());
        return "Eres el sistema de archivo de " + companyName + ". Lee este documento y asígnale una o varias "
                + "etiquetas de esta lista: " + list + ".\n\n" + text + "\n\n"
                + "Si dudas, pon todas las que encajen: es peor que falte una etiqueta que poner una de más. "
                + "Responde solo con las etiquetas que correspondan, separadas por punto y coma.";
    }
}
