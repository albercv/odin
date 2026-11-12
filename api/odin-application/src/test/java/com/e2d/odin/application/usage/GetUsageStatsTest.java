package com.e2d.odin.application.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryUsageStatistics;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetUsageStatsTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryUsageStatistics statistics = new InMemoryUsageStatistics();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneId.of("UTC"));
    private final GetUsageStats usage = new GetUsageStats(orgs, statistics, clock, ZONE);
    private final CompanyId company = Fixtures.company(orgs);

    @Test
    void returnsCompanyAndPeople() {
        var counts = new CompanyUsage(new QuestionCounts(5, 2), 1, new CardCounts(3, 1, 1, 1, 0),
                new DocumentCounts(4, 3, 1, 0), Optional.empty());
        var ana = new PersonUsage(Optional.of(new UserId(UUID.randomUUID())), "Ana Pérez", Optional.of("7F3K-E2D"), 4, 1,
                false);
        var departed = new PersonUsage(Optional.empty(), "Personas dadas de baja", Optional.empty(), 3, 2, true);
        statistics.seed(company, counts, List.of(ana, departed));

        UsageReport report = usage.execute(Actors.companyAdmin(company), company, Optional.empty());

        assertThat(report.month()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(report.company()).isEqualTo(counts);
        assertThat(report.people()).containsExactly(ana, departed);
        assertThat(report.people().get(0).memberCode()).contains("7F3K-E2D");
        assertThat(report.people().get(1).memberCode()).isEmpty();
    }

    @Test
    void usesTheRequestedMonthAndTheInstallationZone() {
        usage.execute(Actors.groupAdmin(), company, Optional.of("2026-09"));

        assertThat(statistics.lastMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(statistics.lastZone()).isEqualTo(ZONE);
    }

    @Test
    void workerIsForbidden() {
        assertThatThrownBy(() -> usage.execute(Actors.worker(company), company, Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        var other = Actors.companyAdmin(Fixtures.company(orgs));
        statistics.seed(company, new CompanyUsage(new QuestionCounts(9, 9), 9, new CardCounts(9, 9, 9, 9, 9),
                new DocumentCounts(9, 9, 9, 9), Optional.empty()), List.of());

        assertThatThrownBy(() -> usage.execute(other, company, Optional.empty())).isInstanceOf(ForbiddenException.class);
        assertThat(statistics.calls()).isZero();
    }

    @Test
    void unknownCompanyIsNotFound() {
        assertThatThrownBy(() -> usage.execute(Actors.groupAdmin(), new CompanyId(UUID.randomUUID()), Optional.empty()))
                .isInstanceOf(NotFoundException.class);
    }
}
