package com.e2d.odin.application.port;

import com.e2d.odin.domain.knowledge.QuestionRecord;

public interface QuestionLog {

    void record(QuestionRecord record);
}
