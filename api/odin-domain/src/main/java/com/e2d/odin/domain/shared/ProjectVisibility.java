package com.e2d.odin.domain.shared;

import java.util.Optional;
import java.util.Set;

/** Proyectos que una petición puede ver; lo que no tiene proyecto lo ve cualquiera de la empresa. */
public record ProjectVisibility(boolean allProjects, Set<ProjectId> projects) {

    public ProjectVisibility {
        projects = Set.copyOf(projects);
    }

    public static ProjectVisibility all() {
        return new ProjectVisibility(true, Set.of());
    }

    public static ProjectVisibility of(Set<ProjectId> projects) {
        return new ProjectVisibility(false, projects);
    }

    public boolean allowsProject(ProjectId id) {
        return allProjects || projects.contains(id);
    }

    public boolean allows(Optional<ProjectId> projectOfItem) {
        return projectOfItem.map(this::allowsProject).orElse(true);
    }
}
