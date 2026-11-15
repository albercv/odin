package com.e2d.odin.domain.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.ValidationException;
import org.junit.jupiter.api.Test;

class ProjectLabelTest {

    @Test
    void normalizesSpacesAndFirstLetter() {
        ProjectLabel label = ProjectLabel.of("  Comunidad ", "  Comunidades ", true);

        assertThat(label).isEqualTo(new ProjectLabel("comunidad", "comunidades", true));
    }

    @Test
    void keepsAcronymsAndHyphensAndAccents() {
        assertThat(ProjectLabel.of("CCPP", "CCPP", true).singular()).isEqualTo("CCPP");
        assertThat(ProjectLabel.of("Árbol-guía", "Árboles-guía", false).singular()).isEqualTo("árbol-guía");
    }

    @Test
    void rejectsBadWords() {
        assertThatThrownBy(() -> ProjectLabel.of("a", "obras", true)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ProjectLabel.of("a".repeat(31), "obras", true))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ProjectLabel.of("obra", "obras 2", true)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ProjectLabel.of(null, "obras", true)).isInstanceOf(ValidationException.class);
    }

    @Test
    void defaultIsProyectos() {
        assertThat(ProjectLabel.DEFAULT).isEqualTo(new ProjectLabel("proyecto", "proyectos", false));
    }
}
