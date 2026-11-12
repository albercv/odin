package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.search.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Apoyo de las baterías: comprueba que los resultados de una consulta respetan las marcas de sustitución del archivo. */
final class SupersessionCheck {

    private SupersessionCheck() {
    }

    /** Un texto por cada problema, con la pregunta y el título. {@code supersededBy} va de la ruta del viejo a la del vigente. */
    static List<String> problems(String questionId, List<SearchHit> hits, Map<String, String> supersededBy) {
        List<String> problems = new ArrayList<>();
        SearchHit firstSuperseded = null;
        for (SearchHit hit : hits) {
            if (firstSuperseded != null && !hit.superseded()) {
                problems.add(prefix(questionId, firstSuperseded) + "sustituido sale delante de «" + hit.title() + "», que es vigente");
            }
            if (hit.superseded() && firstSuperseded == null) {
                firstSuperseded = hit;
            }
            markProblem(questionId, hit, supersededBy).ifPresent(problems::add);
        }
        return problems;
    }

    private static Optional<String> markProblem(String questionId, SearchHit hit, Map<String, String> supersededBy) {
        String expected = supersededBy.get(hit.title());
        if (hit.superseded() && expected == null) {
            return Optional.of(prefix(questionId, hit) + "lleva marca y no está en las parejas");
        }
        if (expected != null && !hit.superseded()) {
            return Optional.of(prefix(questionId, hit) + "es viejo y sale sin marca");
        }
        if (expected != null && !hit.replacement().map(r -> r.title().equals(expected)).orElse(false)) {
            return Optional.of(prefix(questionId, hit) + "debería llevar como vigente «" + expected
                    + "» y lleva " + hit.replacement().map(r -> "«" + r.title() + "»").orElse("ninguno"));
        }
        return Optional.empty();
    }

    private static String prefix(String questionId, SearchHit hit) {
        return questionId + " «" + hit.title() + "»: ";
    }
}
