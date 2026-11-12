package com.e2d.odin.application.topic;

import com.e2d.odin.domain.topic.Topic;
import java.util.List;
import java.util.stream.Collectors;

/**
 * La instrucción del estudio T-010 (clasificar.py) con el preámbulo generalizado: el nombre de la empresa en lugar de
 * «administración de fincas» y «documento» en lugar de «ficha de caso». El resto del texto es el del estudio, con el
 * espacio tras los dos puntos y el punto final incluidos, y la definición de cada tema.
 */
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
