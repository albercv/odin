package com.e2d.odin.application.casefile;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryCaseFileRepository;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Set;

/** Una empresa con un proyecto cerrado del que es miembro el trabajador, y los dobles que comparten los casos de uso. */
final class CaseFileWorld {

    final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    final InMemoryCaseFileRepository files = new InMemoryCaseFileRepository();
    final MutableClock clock = new MutableClock();
    final SequenceIdGenerator ids = new SequenceIdGenerator();
    final CompanyId company = Fixtures.company(orgs);
    final ProjectId project = Fixtures.project(orgs, company);
    final Actor worker = Actors.worker(company);

    CaseFileWorld() {
        members.add(project, worker.userId());
    }

    Document documentIn(ProjectId in) {
        return Fixtures.documentIn(docs, company, in, "texto");
    }

    Document caseIn(ProjectId in) {
        Document d = documentIn(in);
        docs.markCaseDocuments(company, Set.of(d.id()), true);
        return docs.get(d.id()).orElseThrow();
    }

    GetCaseFile getCaseFile() {
        return new GetCaseFile(orgs, members, docs, files);
    }

    AddToCaseFiles addToCaseFiles() {
        return new AddToCaseFiles(orgs, members, docs, files, ids, clock);
    }

    RemoveFromCaseFile removeFromCaseFile() {
        return new RemoveFromCaseFile(orgs, members, docs, files, ids, clock);
    }
}
