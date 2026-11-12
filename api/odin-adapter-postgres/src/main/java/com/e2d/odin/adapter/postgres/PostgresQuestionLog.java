package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.QuestionLog;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class PostgresQuestionLog implements QuestionLog {

    private final ScopedTransactions tx;

    public PostgresQuestionLog(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void record(QuestionRecord r) {
        tx.runInScope(CompanyScope.of(r.companyId()), jdbc -> jdbc.sql("""
                INSERT INTO question_record (id, company_id, author_id, text, hit_count, asked_at, kind)
                VALUES (:id, :company, :author, :text, :hits, :asked, :kind)""")
                .param("id", r.id()).param("company", r.companyId().value()).param("author", r.authorId().value())
                .param("text", r.text()).param("hits", r.hitCount()).param("asked", Sql.ts(r.askedAt())).param("kind", r.kind().name()).update());
    }

    @Override
    public List<QuestionRecord> listByAuthor(CompanyId companyId, UserId authorId, int limit) {
        CompanyScope scope = CompanyScope.of(companyId);
        String sql = "SELECT id, company_id, author_id, text, hit_count, asked_at, kind FROM question_record"
                + " WHERE author_id = :author AND " + Sql.inScope("company_id") + " ORDER BY asked_at DESC, id LIMIT :limit";
        return tx.inScope(scope, jdbc -> Sql.scoped(jdbc.sql(sql), scope).param("author", authorId.value())
                .param("limit", limit).query((rs, n) -> question(rs)).list());
    }

    private static QuestionRecord question(ResultSet rs) throws SQLException {
        return new QuestionRecord(Sql.uuid(rs, "id"), new CompanyId(Sql.uuid(rs, "company_id")),
                new UserId(Sql.uuid(rs, "author_id")), rs.getString("text"), rs.getInt("hit_count"),
                Sql.instant(rs, "asked_at"), QuestionKind.valueOf(rs.getString("kind")));
    }
}
