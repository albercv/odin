package com.e2d.odin.application.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryUsageStatistics;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListGapsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryUsageStatistics statistics = new InMemoryUsageStatistics();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneId.of("UTC"));
    private final ListGaps gaps = new ListGaps(orgs, statistics, clock, ZoneId.of("Europe/Madrid"));
    private final CompanyId company = Fixtures.company(orgs);

    @Test
    void returnsGapsWithoutAuthor() {
        var gap = new GapGroup("goteras en el techo", QuestionKind.SEARCH, 2, LocalDate.of(2026, 11, 10));
        statistics.seedGaps(company, List.of(gap));

        GapReport result = gaps.execute(Actors.companyAdmin(company), company, Optional.empty());

        assertThat(result.groups()).containsExactly(gap);
        assertThat(result.withheld()).isFalse();
        assertThat(Arrays.stream(GapGroup.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("text", "kind", "times", "lastAskedOn");
        assertThat(statistics.lastGapLimit()).isEqualTo(200);
    }

    @Test
    void passesOnTheWithheldFlagWithoutGroups() {
        statistics.seedGapReport(company, new GapReport(List.of(), true));

        GapReport result = gaps.execute(Actors.groupAdmin(), company, Optional.empty());

        assertThat(result.withheld()).isTrue();
        assertThat(result.groups()).isEmpty();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        var other = Actors.companyAdmin(Fixtures.company(orgs));

        assertThatThrownBy(() -> gaps.execute(other, company, Optional.empty())).isInstanceOf(ForbiddenException.class);
        assertThat(statistics.calls()).isZero();
    }

    @Test
    void workerIsForbiddenAndUnknownCompanyIsNotFound() {
        assertThatThrownBy(() -> gaps.execute(Actors.worker(company), company, Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> gaps.execute(Actors.groupAdmin(), new CompanyId(UUID.randomUUID()), Optional.empty()))
                .isInstanceOf(NotFoundException.class);
    }
}
