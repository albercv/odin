package com.e2d.odin.application.search;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.QuestionLog;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.organization.Capability;
import java.util.List;
import java.util.Optional;

/** Las consultas que la IA del actor hizo con su clave: solo las suyas y solo las de la empresa de la clave (D-036). */
public class ListMyQuestions {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final QuestionLog questions;

    public ListMyQuestions(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            QuestionLog questions) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.questions = questions;
    }

    public List<QuestionRecord> execute(Actor actor, Optional<Integer> limit) {
        var access = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE);
        return questions.listByAuthor(access.companyId(), actor.userId(),
                WorkerAccess.cap(limit, DEFAULT_LIMIT, MAX_LIMIT));
    }
}
