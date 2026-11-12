package com.e2d.odin.application.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import com.e2d.odin.domain.topic.Topic;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManageTopicsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final CreateTopic create = new CreateTopic(orgs, topics, new SequenceIdGenerator());
    private final UpdateTopic update = new UpdateTopic(topics);
    private final DeleteTopic delete = new DeleteTopic(topics, new MutableClock());
    private final ListTopics list = new ListTopics(topics);
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);

    @Test
    void createsTopic() {
        Topic topic = create.execute(admin, company, "  Agua ", "  Fugas y humedades ");

        assertThat(topic.name()).isEqualTo("Agua");
        assertThat(topic.definition()).isEqualTo("Fugas y humedades");
        assertThat(list.execute(admin, company)).containsExactly(topic);
        assertThat(list.execute(Actors.groupAdmin(), company)).containsExactly(topic);
    }

    @Test
    void duplicateNameIsConflict() {
        create.execute(admin, company, "Agua", "Fugas");

        assertThatThrownBy(() -> create.execute(admin, company, " AGUA ", "Otra"))
                .isInstanceOf(ConflictException.class).hasMessage("Ya hay un tema con ese nombre");
    }

    @Test
    void nameContainingAnotherIsRejected() {
        create.execute(admin, company, "Agua", "Fugas");

        assertThatThrownBy(() -> create.execute(admin, company, "Agua y humedades", "Otra"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Un nombre de tema no puede contener a otro: Agua");
    }

    @Test
    void nameContainedInAnotherIsRejectedToo() {
        create.execute(admin, company, "Agua y humedades", "Fugas");

        assertThatThrownBy(() -> create.execute(admin, company, "agua", "Otra"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Un nombre de tema no puede contener a otro: Agua y humedades");
    }

    @Test
    void renameIntoContainingNameIsRejected() {
        create.execute(admin, company, "Agua", "Fugas");
        Topic other = create.execute(admin, company, "Seguros", "Pólizas");

        assertThatThrownBy(() -> update.execute(admin, company, other.id(),
                new TopicChanges(Optional.of("Seguros del agua"), Optional.empty())))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Un nombre de tema no puede contener a otro: Agua");
        assertThat(topics.find(company, other.id()).orElseThrow().name()).isEqualTo("Seguros");
    }

    @Test
    void renameKeepsIdAndAllowsItsOwnName() {
        Topic topic = create.execute(admin, company, "Agua", "Fugas");

        Topic renamed = update.execute(admin, company, topic.id(),
                new TopicChanges(Optional.of("AGUA"), Optional.of("Fugas y goteras")));

        assertThat(renamed.id()).isEqualTo(topic.id());
        assertThat(renamed.name()).isEqualTo("AGUA");
        assertThat(topics.listVersion(company)).isEqualTo(2);
    }

    @Test
    void thirtyFirstTopicIsRejected() {
        for (int i = 1; i <= Topic.MAX_PER_COMPANY; i++) {
            create.execute(admin, company, String.format("Tema %02d", i), "Definición");
        }

        assertThatThrownBy(() -> create.execute(admin, company, "Tema 31", "Definición"))
                .isInstanceOf(ValidationException.class).hasMessage("Una empresa puede tener como mucho 30 temas");
    }

    @Test
    void limitsOnNameAndDefinition() {
        assertThatThrownBy(() -> create.execute(admin, company, " ", "Fugas"))
                .isInstanceOf(ValidationException.class).hasMessage("Falta el nombre");
        assertThatThrownBy(() -> create.execute(admin, company, "Agua", null))
                .isInstanceOf(ValidationException.class).hasMessage("Falta la definición");
        assertThatThrownBy(() -> create.execute(admin, company, "a".repeat(61), "Fugas"))
                .isInstanceOf(ValidationException.class).hasMessage("El nombre del tema no puede pasar de 60 caracteres");
        assertThatThrownBy(() -> create.execute(admin, company, "Agua", "d".repeat(501)))
                .isInstanceOf(ValidationException.class).hasMessage("La definición no puede pasar de 500 caracteres");
        assertThat(create.execute(admin, company, "a".repeat(60), "d".repeat(500))).isNotNull();
    }

    @Test
    void deleteIsLogical() {
        Topic topic = create.execute(admin, company, "Agua", "Fugas");

        delete.execute(admin, company, topic.id());

        assertThat(list.execute(admin, company)).isEmpty();
        assertThat(topics.listVersion(company)).isEqualTo(1);
        assertThatThrownBy(() -> delete.execute(admin, company, topic.id())).isInstanceOf(NotFoundException.class);
        assertThat(create.execute(admin, company, "Agua", "Otra vez")).isNotNull();
    }

    @Test
    void workerIsForbidden() {
        Actor worker = Actors.worker(company);
        Topic topic = create.execute(admin, company, "Agua", "Fugas");

        assertThatThrownBy(() -> create.execute(worker, company, "Seguros", "Pólizas")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> list.execute(worker, company)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> update.execute(worker, company, topic.id(), new TopicChanges(Optional.of("X"), Optional.empty())))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> delete.execute(worker, company, topic.id())).isInstanceOf(ForbiddenException.class);
        assertThat(list.execute(admin, company)).containsExactly(topic);
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        Actor other = Actors.companyAdmin(Fixtures.company(orgs));
        Topic topic = create.execute(admin, company, "Agua", "Fugas");

        assertThatThrownBy(() -> create.execute(other, company, "Seguros", "Pólizas")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> list.execute(other, company)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> update.execute(other, company, topic.id(), new TopicChanges(Optional.of("X"), Optional.empty())))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> delete.execute(other, company, topic.id())).isInstanceOf(ForbiddenException.class);
        assertThat(list.execute(admin, company)).containsExactly(topic);
    }
}
