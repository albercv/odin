package com.e2d.odin.application.intake;

import com.e2d.odin.application.fake.InMemoryAuditLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryIntakeRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListOpenIntakeBatchesTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryIntakeRepository intake = new InMemoryIntakeRepository();
    private final MutableClock clock = new MutableClock();
    private final StartIntake start = new StartIntake(intake, orgs, new SequenceIdGenerator(), clock, new InMemoryAuditLog());
    private final ListOpenIntakeBatches list = new ListOpenIntakeBatches(intake);
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
