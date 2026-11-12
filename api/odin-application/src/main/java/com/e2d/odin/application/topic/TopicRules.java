package com.e2d.odin.application.topic;

import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ValidationException;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicNames;
import java.util.List;
import java.util.Optional;

/** Reglas de la lista de temas que comparten el alta y el cambio. */
final class TopicRules {

    private TopicRules() {
    }

    static String requireName(String name) {
        String text = Validation.requireText(name, "el nombre");
        if (text.length() > Topic.MAX_NAME) {
            throw new ValidationException("El nombre del tema no puede pasar de " + Topic.MAX_NAME + " caracteres");
        }
        return text;
    }

    static String requireDefinition(String definition) {
        String text = Validation.requireText(definition, "la definición");
        if (text.length() > Topic.MAX_DEFINITION) {
            throw new ValidationException("La definición no puede pasar de " + Topic.MAX_DEFINITION + " caracteres");
        }
        return text;
    }

    /** Ni repetido (409) ni contenido en otro nombre, ni conteniéndolo (400): la respuesta se coteja por contenido. */
    static void requireCompatibleName(List<Topic> others, Optional<TopicId> ignored, String name) {
        List<Topic> rest = others.stream().filter(t -> ignored.isEmpty() || !t.id().equals(ignored.get())).toList();
        String normalized = TopicNames.normalize(name);
        if (rest.stream().anyMatch(t -> TopicNames.normalize(t.name()).equals(normalized))) {
            throw new ConflictException("Ya hay un tema con ese nombre");
        }
        rest.stream().filter(t -> TopicNames.contains(name, t.name()) || TopicNames.contains(t.name(), name))
                .findFirst().ifPresent(t -> {
                    throw new ValidationException("Un nombre de tema no puede contener a otro: " + t.name());
                });
    }
}
