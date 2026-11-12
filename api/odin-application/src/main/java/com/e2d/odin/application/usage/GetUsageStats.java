package com.e2d.odin.application.usage;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.UsageStatistics;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

/** Uso descriptivo de una empresa en un mes. Solo comprueba permisos y mes; los recuentos los da el puerto. */
public class GetUsageStats {

    private final OrganizationRepository organizations;
    private final UsageStatistics statistics;
    private final Clock clock;
    private final ZoneId zone;

    public GetUsageStats(OrganizationRepository organizations, UsageStatistics statistics, Clock clock, ZoneId zone) {
        this.organizations = organizations;
        this.statistics = statistics;
        this.clock = clock;
        this.zone = zone;
    }

    public UsageReport execute(Actor actor, CompanyId companyId, Optional<String> month) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        YearMonth resolved = ReportMonth.resolve(month, clock, zone);
        return new UsageReport(resolved, statistics.companyUsage(companyId, resolved, zone),
                statistics.peopleUsage(companyId, resolved, zone));
    }
}
