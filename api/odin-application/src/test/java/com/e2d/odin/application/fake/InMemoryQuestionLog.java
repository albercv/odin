package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.QuestionLog;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import java.util.ArrayList;
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
}
