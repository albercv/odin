package com.e2d.odin.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntakeBatchTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private static IntakeBatch open() {
        return IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()), IntakeChannel.COMMAND, NOW);
    }

    @Test
    void walksOpenReadingInReviewClosed() {
        IntakeBatch batch = open();
        assertThat(batch.state()).isEqualTo(IntakeState.OPEN);

        batch = batch.startReading();
        assertThat(batch.state()).isEqualTo(IntakeState.READING);

        batch = batch.finishReading();
        assertThat(batch.state()).isEqualTo(IntakeState.IN_REVIEW);
        assertThat(batch.closedAt()).isEmpty();

        batch = batch.close(NOW.plusSeconds(60));
        assertThat(batch.state()).isEqualTo(IntakeState.CLOSED);
        assertThat(batch.closedAt()).isEqualTo(Optional.of(NOW.plusSeconds(60)));
    }

    @Test
    void forbiddenTransitionsAreConflicts() {
        IntakeBatch fresh = open();
        assertThatThrownBy(fresh::finishReading).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> fresh.close(NOW)).isInstanceOf(ConflictException.class);

        IntakeBatch reading = fresh.startReading();
        assertThatThrownBy(reading::startReading).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> reading.close(NOW)).isInstanceOf(ConflictException.class);

        IntakeBatch closed = reading.finishReading().close(NOW);
        assertThatThrownBy(closed::startReading).isInstanceOf(ConflictException.class);
        assertThatThrownBy(closed::finishReading).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> closed.close(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void theProjectListIsClosedOnceWhileReadingWithWhoAndWhen() {
        UserId admin = new UserId(UUID.randomUUID());
        IntakeBatch reading = open().startReading();
        assertThat(reading.projectListClosed()).isFalse();

        IntakeBatch closed = reading.closeProjectList(admin, NOW);

        assertThat(closed.projectListClosed()).isTrue();
        assertThat(closed.projectListClosedBy()).contains(admin);
        assertThat(closed.projectListClosedAt()).contains(NOW);
        assertThat(closed.finishReading().projectListClosedBy()).contains(admin);
        assertThatThrownBy(() -> closed.closeProjectList(admin, NOW)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> open().closeProjectList(admin, NOW)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> closed.finishReading().closeProjectList(admin, NOW))
                .isInstanceOf(ConflictException.class);
    }
}
