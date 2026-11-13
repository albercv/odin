package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestoreDocumentTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final MutableClock clock = new MutableClock();
    private final WithdrawDocument withdraw = new WithdrawDocument(docs, clock);
    private final RestoreDocument restore = new RestoreDocument(docs);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    @Test
    void restoreClearsTheWithdrawal() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");
        withdraw.execute(Actors.companyAdmin(companyA), d.id());

        var entry = restore.execute(Actors.companyAdmin(companyA), d.id());

        assertThat(entry.document().withdrawn()).isFalse();
        assertThat(entry.document().withdrawnBy()).isEmpty();
        assertThat(docs.get(d.id()).orElseThrow().withdrawn()).isFalse();
    }

    @Test
    void restoringADocumentThatIsNotWithdrawnChangesNothing() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");

        assertThat(restore.execute(Actors.groupAdmin(), d.id()).document()).isEqualTo(d);
    }

    @Test
    void anActiveEqualDocumentBlocksTheRestore() {
        Document old = Fixtures.readyDocument(docs, companyA, "texto");
        withdraw.execute(Actors.companyAdmin(companyA), old.id());
        docs.save(copyOf(old));

        assertThatThrownBy(() -> restore.execute(Actors.companyAdmin(companyA), old.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ya hay un documento igual en ese proyecto; retíralo antes de recuperar este");
        assertThat(docs.get(old.id()).orElseThrow().withdrawn()).isTrue();
    }

    @Test
    void workerIsForbiddenAndOtherCompanyOrUnknownAreNotFound() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");
        withdraw.execute(Actors.companyAdmin(companyA), d.id());

        assertThatThrownBy(() -> restore.execute(Actors.worker(companyA), d.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> restore.execute(Actors.companyAdmin(companyB), d.id()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> restore.execute(Actors.groupAdmin(), new DocumentId(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }

    private static Document copyOf(Document d) {
        return new Document(new DocumentId(UUID.randomUUID()), d.companyId(), d.projectId(), d.title(), d.filename(),
                d.format(), d.sha256(), d.status(), d.failureReason(), d.paginated(), d.uploadedBy(), d.uploadedAt(),
                d.processingStartedAt(), d.attemptCount(), java.util.Optional.empty(), java.util.Optional.empty());
    }
}
