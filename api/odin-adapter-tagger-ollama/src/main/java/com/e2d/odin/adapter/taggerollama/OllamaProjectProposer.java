package com.e2d.odin.adapter.taggerollama;

import com.e2d.odin.application.port.ProjectProposer;
import com.e2d.odin.application.port.TaggerFailedException;
import com.e2d.odin.application.port.TaggerUnavailableException;
import com.e2d.odin.application.port.TopicTagger;
import com.e2d.odin.domain.intake.ProjectRules;
import java.util.List;
import java.util.Optional;

/**
 * Propone proyecto con el Ollama local del etiquetado (mismo cliente, modelo y opciones: temperatura 0, think false,
 * contexto 4.096). El prompt es el del estudio de la parte B.
 */
public class OllamaProjectProposer implements ProjectProposer {

    static final int TEXT_CHARACTERS = 2000;
    private static final String NONE = "ninguno";

    private final TopicTagger tagger;

    public OllamaProjectProposer(TopicTagger tagger) {
        this.tagger = tagger;
    }

    @Override
    public Optional<String> propose(List<String> projects, String text) {
        if (projects.isEmpty()) {
            return Optional.empty();
        }
        try {
            return match(tagger.tag(prompt(projects, text)).text(), projects);
        } catch (TaggerFailedException | TaggerUnavailableException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> match(String answer, List<String> projects) {
        String normalized = ProjectRules.normalize(answer);
        if (normalized.equals(NONE)) {
            return Optional.empty();
        }
        return projects.stream().filter(project -> ProjectRules.normalize(project).equals(normalized)).findFirst();
    }

    static String prompt(List<String> projects, String text) {
        String listing = String.join("\n", projects.stream().map(name -> "- " + name).toList());
        String start = text.length() > TEXT_CHARACTERS ? text.substring(0, TEXT_CHARACTERS) : text;
        return "Eres un clasificador. Dado el inicio de un documento de una administración de fincas, "
                + "di a qué proyecto de la lista pertenece. Responde SOLO con el nombre exacto de un proyecto "
                + "de la lista, o con la palabra «ninguno» si el documento no es de un proyecto concreto "
                + "(por ejemplo una norma o un procedimiento de toda la empresa). Sin explicaciones.\n\n"
                + "Lista de proyectos:\n" + listing + "\n\nDocumento (primeros 2.000 caracteres):\n\"\"\"\n" + start
                + "\n\"\"\"\n\nProyecto:";
    }
}
