package com.e2d.odin.application.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDeliveryMeter;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetDeliveredVolumeTest {

    private static final Instant IN_NOVEMBER = Instant.parse("2026-11-10T09:00:00Z");
    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDeliveryMeter meter = new InMemoryDeliveryMeter();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneId.of("UTC"));
    private final GetDeliveredVolume volume = new GetDeliveredVolume(orgs, meter, clock, ZoneId.of("Europe/Madrid"));
    private final CompanyId company = Fixtures.company(orgs);

    @Test
    void returnsTotalsByToolWithZeros() {
        meter.record(company, WorkerTool.SEARCH, 100, IN_NOVEMBER);
        meter.record(company, WorkerTool.SEARCH, 50, IN_NOVEMBER);
        meter.record(company, WorkerTool.READ_PAGE, 7, IN_NOVEMBER);
        meter.record(company, WorkerTool.SEARCH, 999, Instant.parse("2026-10-10T09:00:00Z"));

        DeliveredVolumeReport report = volume.execute(Actors.companyAdmin(company), company, Optional.empty());

        assertThat(report.month()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(report.totals()).extracting(DeliveryTotal::tool).containsExactly(WorkerTool.values());
        assertThat(report.totals()).filteredOn(t -> t.tool() == WorkerTool.SEARCH)
                .containsExactly(new DeliveryTotal(WorkerTool.SEARCH, 2, 150));
        assertThat(report.totals()).filteredOn(t -> t.tool() == WorkerTool.MY_QUESTIONS)
                .containsExactly(new DeliveryTotal(WorkerTool.MY_QUESTIONS, 0, 0));
    }

    @Test
    void workerIsForbidden() {
        assertThatThrownBy(() -> volume.execute(Actors.worker(company), company, Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        meter.record(company, WorkerTool.SEARCH, 100, IN_NOVEMBER);
        var other = Actors.companyAdmin(Fixtures.company(orgs));

        assertThatThrownBy(() -> volume.execute(other, company, Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(volume.execute(Actors.groupAdmin(), Fixtures.company(orgs), Optional.empty()).totals())
                .allSatisfy(t -> assertThat(t.bytes()).isZero());
    }
}
