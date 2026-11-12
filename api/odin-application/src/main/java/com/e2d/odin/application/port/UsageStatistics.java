package com.e2d.odin.application.port;

import com.e2d.odin.application.usage.CompanyUsage;
import com.e2d.odin.application.usage.GapGroup;
import com.e2d.odin.application.usage.PersonUsage;
import com.e2d.odin.domain.shared.CompanyId;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/** Recuentos descriptivos de una empresa y mes, calculados al pedirlos sobre los datos que ya existen. */
public interface UsageStatistics {

    CompanyUsage companyUsage(CompanyId companyId, YearMonth month, ZoneId zone);

    /** Por nombre, nunca por cantidad; las personas dadas de baja, sumadas en una fila al final. */
    List<PersonUsage> peopleUsage(CompanyId companyId, YearMonth month, ZoneId zone);

    /** Consultas sin resultados, de la más reciente a la más antigua. */
    List<GapGroup> gaps(CompanyId companyId, YearMonth month, ZoneId zone, int limit);
}
