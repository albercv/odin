package com.e2d.odin.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectVisibilityTest {

    private final ProjectId visible = new ProjectId(UUID.randomUUID());
    private final ProjectId hidden = new ProjectId(UUID.randomUUID());

    @Test
    void allSeesEveryProject() {
        assertThat(ProjectVisibility.all().allowsProject(hidden)).isTrue();
    }

    @Test
    void ofSeesOnlyItsProjectsAndWhatHasNoProject() {
        ProjectVisibility visibility = ProjectVisibility.of(Set.of(visible));

        assertThat(visibility.allowsProject(visible)).isTrue();
        assertThat(visibility.allowsProject(hidden)).isFalse();
        assertThat(visibility.allows(Optional.of(hidden))).isFalse();
        assertThat(visibility.allows(Optional.empty())).isTrue();
    }
}
