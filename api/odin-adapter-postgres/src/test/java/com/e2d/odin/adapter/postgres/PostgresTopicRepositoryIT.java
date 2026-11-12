package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class PostgresTopicRepositoryIT extends AbstractPostgresIT {

    private final PostgresTopicRepository topics = new PostgresTopicRepository(tx);

    private Topic topic(CompanyId company, String name, String definition) {
        return new Topic(new TopicId(UUID.randomUUID()), company, name, definition);
    }

    @Test
    void createAndEditRaiseListVersion() {
        CompanyId company = newCompany();
        assertThat(topics.listVersion(company)).isZero();
        Topic agua = topic(company, "Agua", "Fugas");

        topics.save(agua);
        assertThat(topics.listVersion(company)).isEqualTo(1);
        topics.save(new Topic(agua.id(), company, "Agua y humedades", "Fugas"));
        assertThat(topics.listVersion(company)).isEqualTo(2);
        topics.save(new Topic(agua.id(), company, "Agua y humedades", "Fugas y goteras"));
        assertThat(topics.listVersion(company)).isEqualTo(3);

        assertThat(topics.list(company)).containsExactly(new Topic(agua.id(), company, "Agua y humedades", "Fugas y goteras"));
        assertThat(JdbcClient.create(Db.owner()).sql("SELECT topic_list_changed_at IS NOT NULL FROM company WHERE id = :id")
                .param("id", company.value()).query(Boolean.class).single()).isTrue();
    }

    @Test
    void deleteKeepsListVersion() {
        CompanyId company = newCompany();
        Topic agua = topic(company, "Agua", "Fugas");
        topics.save(agua);

        topics.delete(company, agua.id(), NOW);

        assertThat(topics.listVersion(company)).isEqualTo(1);
        assertThat(topics.list(company)).isEmpty();
        assertThat(topics.find(company, agua.id())).isEmpty();
        topics.save(topic(company, "AGUA", "Con el nombre libre otra vez"));
        assertThat(topics.list(company)).hasSize(1);
    }

    @Test
    void sameEditTwiceRaisesOnce() {
        CompanyId company = newCompany();
        Topic agua = topic(company, "Agua", "Fugas");
        topics.save(agua);

        topics.save(agua);
        topics.save(new Topic(agua.id(), company, "Agua", "Fugas"));

        assertThat(topics.listVersion(company)).isEqualTo(1);
    }

    @Test
    void sameNameWithOtherAccentsOrCaseIsConflict() {
        CompanyId company = newCompany();
        topics.save(topic(company, "Instalación", "Cables"));

        assertThatThrownBy(() -> topics.save(topic(company, "INSTALACION", "Otra")))
                .isInstanceOf(ConflictException.class);
        assertThat(topics.listVersion(company)).isEqualTo(1);
    }

    @Test
    void rowLevelSecurityHidesOtherCompanyTopics() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        topics.save(topic(a, "Agua", "Fugas"));
        topics.save(topic(b, "Seguros", "Pólizas"));

        assertThat(rawTopicCount(CompanyScope.of(a))).isEqualTo(1);
        assertThat(rawTopicCount(CompanyScope.of(b))).isEqualTo(1);
        assertThat(rawTopicCount(CompanyScope.all())).isEqualTo(2);
        assertThat(topics.list(b)).extracting(Topic::name).containsExactly("Seguros");
        assertThat(topics.find(b, topics.list(a).isEmpty() ? null : topics.list(a).get(0).id())).isEmpty();
    }

    /** Sin filtro de empresa en el SQL: solo la seguridad por filas decide. */
    private long rawTopicCount(CompanyScope scope) {
        return tx.inScope(scope, jdbc -> jdbc.sql("SELECT count(*) FROM topic").query(Long.class).single());
    }
}
