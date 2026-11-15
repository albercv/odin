package com.e2d.odin.application.port;

import java.util.List;
import java.util.Optional;

/** Propone el proyecto de un texto con el modelo local, solo para lo que las reglas no resuelven (D2: es una propuesta). */
public interface ProjectProposer {

    /**
     * Un nombre exacto de la lista confirmada, o vacío si el modelo no propone ninguno, responde otra cosa o falla.
     * Nunca lanza: un fallo no debe tumbar el lote.
     */
    Optional<String> propose(List<String> projects, String text);
}
