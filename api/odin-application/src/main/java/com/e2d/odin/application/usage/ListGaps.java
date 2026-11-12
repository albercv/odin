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
import java.util.List;
import java.util.Optional;

/** Las consultas que no encontraron nada, sin autor: huecos de la documentación. */
public class ListGaps {

    static final int LIMIT = 200;

    private final OrganizationRepository organizations;
    private final UsageStatistics statistics;
    private final Clock clock;
    private final ZoneId zone;

    public ListGaps(OrganizationRepository organizations, UsageStatistics statistics, Clock clock, ZoneId zone) {
        this.organizations = organizations;
        this.statistics = statistics;
        this.clock = clock;
        this.zone = zone;
    }

    public List<GapGroup> execute(Actor actor, CompanyId companyId, Optional<String> month) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        YearMonth resolved = ReportMonth.resolve(month, clock, zone);
        return statistics.gaps(companyId, resolved, zone, LIMIT);
    }
}
