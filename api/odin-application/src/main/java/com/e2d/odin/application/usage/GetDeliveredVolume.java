package com.e2d.odin.application.usage;

import com.e2d.odin.application.port.DeliveryMeter;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Volumen entregado a la IA del trabajador en un mes: todas las herramientas, con 0 en las que no tuvieron uso. */
public class GetDeliveredVolume {

    private final OrganizationRepository organizations;
    private final DeliveryMeter meter;
    private final Clock clock;
    private final ZoneId zone;

    public GetDeliveredVolume(OrganizationRepository organizations, DeliveryMeter meter, Clock clock, ZoneId zone) {
        this.organizations = organizations;
        this.meter = meter;
        this.clock = clock;
        this.zone = zone;
    }

    public DeliveredVolumeReport execute(Actor actor, CompanyId companyId, Optional<String> month) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        YearMonth resolved = ReportMonth.resolve(month, clock, zone);
        var used = meter.totals(companyId, resolved).stream()
                .collect(Collectors.toMap(DeliveryTotal::tool, Function.identity()));
        return new DeliveredVolumeReport(resolved, Arrays.stream(WorkerTool.values())
                .map(tool -> used.getOrDefault(tool, new DeliveryTotal(tool, 0, 0))).toList());
    }
}
