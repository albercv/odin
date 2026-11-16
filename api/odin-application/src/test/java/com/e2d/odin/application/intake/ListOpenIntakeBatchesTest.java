package com.e2d.odin.application.intake;

import com.e2d.odin.application.fake.InMemoryAuditLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryIntakeRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ListOpenIntakeBatchesTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryIntakeRepository intake = new InMemoryIntakeRepository();
    private final MutableClock clock = new MutableClock();
    private final StartIntake start = new StartIntake(intake, orgs, new SequenceIdGenerator(), clock, new InMemoryAuditLog());
    private final ListOpenIntakeBatches list = new ListOpenIntakeBatches(intake, docs);
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);

    private IntakeBatch started(CompanyId in) {
        clock.advance(Duration.ofMinutes(1));
        return start.execute(Actors.companyAdmin(in), in, IntakeChannel.COMMAND);
    }

    @Test
    void listsTheBatchesThatAreNotClosedMostRecentFirstAndOnlyOfTheCompany() {
        IntakeBatch closed = started(company);
        intake.save(closed.startReading().finishReading().close(clock.instant()));
        IntakeBatch inReview = started(company);
        intake.save(inReview.startReading().finishReading());
        IntakeBatch open = started(company);
        CompanyId other = Fixtures.company(orgs);
        started(other);

        List<IntakeBatch> batches = list.execute(admin, company);

        assertThat(batches).extracting(IntakeBatch::id).containsExactly(open.id(), inReview.id());
        assertThat(batches).extracting(IntakeBatch::state).containsExactly(IntakeState.OPEN, IntakeState.IN_REVIEW);
    }

    @Test
    void aClosedBatchIsListedOnlyWhileItStillHasPendingDocuments() {
        IntakeBatch withPending = started(company);
        intake.save(withPending.startReading().finishReading().close(admin.userId(), clock.instant()));
        pendingIn(withPending);
        IntakeBatch finished = started(company);
        intake.save(finished.startReading().finishReading().close(clock.instant()));
        IntakeBatch open = started(company);

        assertThat(list.execute(admin, company)).extracting(IntakeBatch::id).containsExactly(open.id(), withPending.id());
        assertThat(list.execute(admin, company)).extracting(IntakeBatch::state)
                .containsExactly(IntakeState.OPEN, IntakeState.CLOSED);
    }

    private void pendingIn(IntakeBatch batch) {
        Document d = Fixtures.readyDocument(docs, company, "Acta");
        docs.save(new Document(d.id(), company, Optional.empty(), "Acta", d.filename(), d.format(), d.sha256(),
                DocumentStatus.READY, Optional.empty(), true, d.uploadedBy(), d.uploadedAt(), Optional.empty(), 0,
                Optional.empty(), Optional.empty()).withIntake(batch.id(), "Cliente/Acta"));
    }

    @Test
    void noBatchesIsAnEmptyList() {
        assertThat(list.execute(admin, company)).isEmpty();
    }

    @Test
    void anAdminOfAnotherCompanyAndAWorkerAreRefused() {
        started(company);
        Actor foreignAdmin = Actors.companyAdmin(Fixtures.company(orgs));

        for (Actor intruder : List.of(foreignAdmin, Actors.worker(company))) {
            assertThatThrownBy(() -> list.execute(intruder, company)).isInstanceOf(ForbiddenException.class);
        }
    }
}
