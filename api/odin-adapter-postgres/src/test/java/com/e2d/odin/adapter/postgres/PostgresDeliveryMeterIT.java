package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.usage.DeliveryTotal;
import com.e2d.odin.application.usage.WorkerTool;
import com.e2d.odin.domain.shared.CompanyId;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class PostgresDeliveryMeterIT extends AbstractPostgresIT {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final Instant IN_NOVEMBER = Instant.parse("2026-11-10T09:00:00Z");

    private final PostgresDeliveryMeter meter = new PostgresDeliveryMeter(tx, ZoneId.of("Europe/Madrid"));

    @Test
    void upsertSumsCallsAndBytes() {
        CompanyId company = newCompany();

        meter.record(company, WorkerTool.SEARCH, 100, IN_NOVEMBER);
        meter.record(company, WorkerTool.SEARCH, 50, IN_NOVEMBER.plusSeconds(60));
        meter.record(company, WorkerTool.READ_PAGE, 7, IN_NOVEMBER);

        assertThat(meter.totals(company, NOVEMBER)).containsExactlyInAnyOrder(
                new DeliveryTotal(WorkerTool.SEARCH, 2, 150), new DeliveryTotal(WorkerTool.READ_PAGE, 1, 7));
        assertThat(meter.totals(company, YearMonth.of(2026, 10))).isEmpty();
    }

    @Test
    void monthUsesInstallationZone() {
        CompanyId company = newCompany();

        meter.record(company, WorkerTool.SEARCH, 10, Instant.parse("2026-10-31T23:30:00Z"));
        meter.record(company, WorkerTool.SEARCH, 20, Instant.parse("2026-11-30T23:30:00Z"));

        assertThat(meter.totals(company, NOVEMBER)).containsExactly(new DeliveryTotal(WorkerTool.SEARCH, 1, 10));
        assertThat(meter.totals(company, YearMonth.of(2026, 12))).containsExactly(
                new DeliveryTotal(WorkerTool.SEARCH, 1, 20));
    }

    @Test
    void rowLevelSecurityHidesOtherCompany() {
        CompanyId mine = newCompany();
        CompanyId other = newCompany();
        meter.record(other, WorkerTool.SEARCH, 999, IN_NOVEMBER);

        assertThat(meter.totals(mine, NOVEMBER)).isEmpty();
        long seenWithoutFilter = tx.inScope(com.e2d.odin.domain.shared.CompanyScope.of(mine),
                jdbc -> jdbc.sql("SELECT count(*) FROM delivery_usage").query(Long.class).single());
        assertThat(seenWithoutFilter).isZero();
        assertThat(JdbcClient.create(Db.owner()).sql("SELECT count(*) FROM delivery_usage").query(Long.class).single())
                .isEqualTo(1);
    }
}
