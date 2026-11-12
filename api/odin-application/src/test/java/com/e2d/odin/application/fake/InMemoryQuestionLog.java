package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.QuestionLog;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InMemoryQuestionLog implements QuestionLog {

    private final List<QuestionRecord> records = new ArrayList<>();

    public List<QuestionRecord> records() {
        return records;
    }

    @Override
    public void record(QuestionRecord record) {
        records.add(record);
    }

    @Override
    public List<QuestionRecord> listByAuthor(CompanyId companyId, UserId authorId, int limit) {
        return records.reversed().stream().filter(r -> r.companyId().equals(companyId) && r.authorId().equals(authorId))
                .sorted(Comparator.comparing(QuestionRecord::askedAt).reversed()).limit(limit).toList();
    }
}
