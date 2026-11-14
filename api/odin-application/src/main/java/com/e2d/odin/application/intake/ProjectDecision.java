package com.e2d.odin.application.intake;

import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;

/** Lo que el administrador decide de un candidato: crear su proyecto, unirlo a uno que ya existe o rechazarlo. */
public record ProjectDecision(String name, Action action, Optional<ProjectId> projectId) {

    public enum Action { CREATE, MERGE, REJECT }
}
