package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PostgresTopicRepository implements TopicRepository {

    private static final String SELECT = "SELECT id, company_id, name, definition FROM topic WHERE deleted_at IS NULL";
    private final ScopedTransactions tx;

    public PostgresTopicRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public List<Topic> list(CompanyId companyId) {
        return tx.inScope(CompanyScope.of(companyId), jdbc -> jdbc
                .sql(SELECT + " AND company_id = :company ORDER BY lower(name), id")
                .param("company", companyId.value()).query((rs, n) -> topic(rs)).list());
    }

    @Override
    public Optional<Topic> find(CompanyId companyId, TopicId topicId) {
        return tx.inScope(CompanyScope.of(companyId), jdbc -> findIn(jdbc, companyId, topicId));
    }

    private static Optional<Topic> findIn(JdbcClient jdbc, CompanyId companyId, TopicId topicId) {
        return jdbc.sql(SELECT + " AND company_id = :company AND id = :id").param("company", companyId.value())
                .param("id", topicId.value()).query((rs, n) -> topic(rs)).optional();
    }

    @Override
    public void save(Topic topic) {
        tx.runInScope(CompanyScope.of(topic.companyId()), jdbc -> {
            if (findIn(jdbc, topic.companyId(), topic.id()).filter(topic::equals).isEmpty()) {
                upsert(jdbc, topic);
                raiseVersion(jdbc, topic.companyId());
            }
        });
    }

    private static void upsert(JdbcClient jdbc, Topic topic) {
        jdbc.sql("""
                INSERT INTO topic (id, company_id, name, definition) VALUES (:id, :company, :name, :definition)
                ON CONFLICT (id) DO UPDATE SET name = :name, definition = :definition
                WHERE topic.company_id = :company""")
                .param("id", topic.id().value()).param("company", topic.companyId().value())
                .param("name", topic.name()).param("definition", topic.definition()).update();
    }

    /** Bloquea la fila de la empresa: dos cambios a la vez suben la versión de uno en uno. */
    private static void raiseVersion(JdbcClient jdbc, CompanyId companyId) {
        jdbc.sql("""
                UPDATE company SET topic_list_version = topic_list_version + 1, topic_list_changed_at = now()
                WHERE id = :company""").param("company", companyId.value()).update();
    }

    @Override
    public void delete(CompanyId companyId, TopicId topicId, Instant at) {
        tx.runInScope(CompanyScope.of(companyId), jdbc -> jdbc.sql("""
                UPDATE topic SET deleted_at = :at WHERE id = :id AND company_id = :company AND deleted_at IS NULL""")
                .param("at", Sql.ts(at)).param("id", topicId.value()).param("company", companyId.value()).update());
    }

    @Override
    public int listVersion(CompanyId companyId) {
        return tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc
                .sql("SELECT topic_list_version FROM company WHERE id = :company")
                .param("company", companyId.value()).query(Integer.class).optional().orElse(0));
    }

    private static Topic topic(ResultSet rs) throws SQLException {
        return new Topic(new TopicId(Sql.uuid(rs, "id")), new CompanyId(Sql.uuid(rs, "company_id")),
                rs.getString("name"), rs.getString("definition"));
    }
}
