package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.QuestionLog;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.shared.CompanyScope;

public class PostgresQuestionLog implements QuestionLog {

    private final ScopedTransactions tx;

    public PostgresQuestionLog(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void record(QuestionRecord r) {
        tx.runInScope(CompanyScope.of(r.companyId()), jdbc -> jdbc.sql("""
                INSERT INTO question_record (id, company_id, author_id, text, hit_count, asked_at)
                VALUES (:id, :company, :author, :text, :hits, :asked)""")
                .param("id", r.id()).param("company", r.companyId().value()).param("author", r.authorId().value())
                .param("text", r.text()).param("hits", r.hitCount()).param("asked", Sql.ts(r.askedAt())).update());
    }
}
