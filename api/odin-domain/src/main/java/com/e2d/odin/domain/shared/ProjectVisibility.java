package com.e2d.odin.domain.shared;

import java.util.Optional;
import java.util.Set;

/** Los proyectos que una petición puede ver. Lo que no tiene proyecto lo ve toda la empresa. */
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

    public boolean includes(ProjectId project) {
        return allProjects || projects.contains(project);
    }

    public boolean allows(Optional<ProjectId> project) {
        return project.map(this::includes).orElse(true);
    }
}
