package com.e2d.odin.application.port;

import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;

public interface QuestionLog {

    void record(QuestionRecord record);

    /** Las preguntas de un autor en una empresa, de la más reciente a la más antigua. */
    List<QuestionRecord> listByAuthor(CompanyId companyId, UserId authorId, int limit);
}
