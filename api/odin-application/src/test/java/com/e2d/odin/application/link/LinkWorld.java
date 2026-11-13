package com.e2d.odin.application.link;

import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAccessLinkRepository;
import com.e2d.odin.application.fake.InMemoryCaseFileRepository;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Duration;
import java.util.Set;

/** Una empresa con un proyecto cerrado del que es miembro Wanda, y los dobles que comparten los enlaces. */
final class LinkWorld {

    static final String HOME = "192.168.1.20";

    final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    final InMemoryCaseFileRepository caseFiles = new InMemoryCaseFileRepository(docs);
    final InMemoryAccessLinkRepository links = new InMemoryAccessLinkRepository();
    final InMemoryUserRepository users = new InMemoryUserRepository();
    final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    final InMemoryOriginalFileStore store = new InMemoryOriginalFileStore();
    final StubFormatDetector detector = new StubFormatDetector(DocumentFormat.PDF);
    final MutableClock clock = new MutableClock();
    final SequenceIdGenerator ids = new SequenceIdGenerator();
    final SequenceTokenGenerator tokens = new SequenceTokenGenerator();
    final FakeSecretHasher secrets = new FakeSecretHasher();
    final LinkSettings settings = new LinkSettings("http://odin.local/", Duration.ofMinutes(15));
    final CompanyId company = Fixtures.company(orgs);
    final ProjectId project = Fixtures.project(orgs, company);
    final Actor worker;

    LinkWorld() {
        var userId = Fixtures.person(users, memberships, "Marta", Role.WORKER, company, true);
        worker = new Actor(userId, "Marta", Role.WORKER, Set.of(company));
        members.add(project, userId);
    }

    Document caseIn(ProjectId in) {
        Document d = Fixtures.documentIn(docs, company, in, "texto");
        docs.markCaseDocuments(company, Set.of(d.id()), true);
        return docs.get(d.id()).orElseThrow();
    }

    ResolveLink resolve() {
        var actors = new ResolveActor(new InMemoryAccessKeyRepository(), users, memberships, null, secrets);
        return new ResolveLink(links, actors, orgs, members, docs, secrets, clock);
    }

    IssueUploadLink issueUpload() {
        return new IssueUploadLink(orgs, members, docs, new LinkIssuer(links, tokens, secrets, ids, clock, settings));
    }

    IssueOriginalLink issueOriginal() {
        return new IssueOriginalLink(orgs, members, docs, new LinkIssuer(links, tokens, secrets, ids, clock, settings));
    }

    UploadByLink uploadByLink() {
        var upload = new UploadDocument(docs, orgs, store, detector, new UploadSettings(1000), ids, clock);
        var add = new AddToCaseFiles(orgs, members, docs, caseFiles, ids, clock);
        return new UploadByLink(resolve(), links, upload, add);
    }

    OpenOriginal openOriginal() {
        return new OpenOriginal(resolve(), store, docs);
    }

    static String token(IssuedLink link) {
        return link.url().substring(link.url().lastIndexOf('/') + 1).split("#")[0];
    }

    Actor stranger() {
        return Actors.worker(company);
    }
}
