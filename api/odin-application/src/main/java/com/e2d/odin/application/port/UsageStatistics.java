package com.e2d.odin.application.port;

import com.e2d.odin.application.usage.CompanyUsage;
import com.e2d.odin.application.usage.GapReport;
import com.e2d.odin.application.usage.PersonUsage;
import com.e2d.odin.domain.shared.CompanyId;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/** Recuentos descriptivos de una empresa y mes, calculados al pedirlos sobre los datos que ya existen. */
public interface UsageStatistics {

    CompanyUsage companyUsage(CompanyId companyId, YearMonth month, ZoneId zone);

    /** Por nombre, nunca por cantidad; las bajas, sumadas en una fila al final solo si son suficientes. */
    List<PersonUsage> peopleUsage(CompanyId companyId, YearMonth month, ZoneId zone);

    /** Consultas sin resultados, de la más reciente a la más antigua; ninguna si las preguntaron muy pocas personas. */
    GapReport gaps(CompanyId companyId, YearMonth month, ZoneId zone, int limit);
}
