package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WithdrawDocumentTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final MutableClock clock = new MutableClock();
    private final WithdrawDocument withdraw = new WithdrawDocument(docs, clock);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    @Test
    void adminWithdrawsKeepingTheOriginalPagesAndRecordsWhoAndWhen() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");
        Actor admin = Actors.companyAdmin(companyA);

        var entry = withdraw.execute(admin, d.id());

        assertThat(entry.document().withdrawn()).isTrue();
        assertThat(entry.document().withdrawnAt()).contains(clock.instant());
        assertThat(entry.document().withdrawnBy()).contains(admin.userId());
        assertThat(docs.pagesOf(d.id())).hasSize(1);
    }

    @Test
    void withdrawingTwiceKeepsTheFirstDate() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");
        Actor admin = Actors.companyAdmin(companyA);
        var first = withdraw.execute(admin, d.id());
        clock.advance(Duration.ofHours(1));

        var second = withdraw.execute(Actors.groupAdmin(), d.id());

        assertThat(second.document().withdrawnAt()).isEqualTo(first.document().withdrawnAt());
        assertThat(second.document().withdrawnBy()).isEqualTo(first.document().withdrawnBy());
    }

    @Test
    void workerIsForbiddenAndNothingChanges() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");

        assertThatThrownBy(() -> withdraw.execute(Actors.worker(companyA), d.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(docs.get(d.id()).orElseThrow().withdrawn()).isFalse();
    }

    @Test
    void adminOfAnotherCompanyAndUnknownDocumentAreNotFound() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");

        assertThatThrownBy(() -> withdraw.execute(Actors.companyAdmin(companyB), d.id()))
                .isInstanceOf(NotFoundException.class).hasMessage("Documento no encontrado");
        assertThatThrownBy(() -> withdraw.execute(Actors.groupAdmin(), new DocumentId(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
        assertThat(docs.get(d.id()).orElseThrow().withdrawnAt()).isEqualTo(Optional.empty());
    }
}
