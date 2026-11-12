package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.UsageStatistics;
import com.e2d.odin.application.usage.CardCounts;
import com.e2d.odin.application.usage.CompanyUsage;
import com.e2d.odin.application.usage.DocumentCounts;
import com.e2d.odin.application.usage.GapGroup;
import com.e2d.odin.application.usage.PersonUsage;
import com.e2d.odin.application.usage.QuestionCounts;
import com.e2d.odin.domain.shared.CompanyId;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Doble del puerto: devuelve lo sembrado para la empresa, sin calcular nada, y apunta con qué se le pidió. */
public class InMemoryUsageStatistics implements UsageStatistics {

    private static final CompanyUsage EMPTY = new CompanyUsage(new QuestionCounts(0, 0), 0,
            new CardCounts(0, 0, 0, 0, 0), new DocumentCounts(0, 0, 0, 0), Optional.empty());

    private final Map<CompanyId, CompanyUsage> companies = new HashMap<>();
    private final Map<CompanyId, List<PersonUsage>> people = new HashMap<>();
    private final Map<CompanyId, List<GapGroup>> gaps = new HashMap<>();
    private int calls;
    private YearMonth lastMonth;
    private ZoneId lastZone;
    private int lastGapLimit;

    public void seed(CompanyId company, CompanyUsage usage, List<PersonUsage> persons) {
        companies.put(company, usage);
        people.put(company, persons);
    }

    public void seedGaps(CompanyId company, List<GapGroup> groups) {
        gaps.put(company, groups);
    }

    public int calls() {
        return calls;
    }

    public YearMonth lastMonth() {
        return lastMonth;
    }

    public ZoneId lastZone() {
        return lastZone;
    }

    public int lastGapLimit() {
        return lastGapLimit;
    }

    @Override
    public CompanyUsage companyUsage(CompanyId companyId, YearMonth month, ZoneId zone) {
        noteCall(month, zone);
        return companies.getOrDefault(companyId, EMPTY);
    }

    @Override
    public List<PersonUsage> peopleUsage(CompanyId companyId, YearMonth month, ZoneId zone) {
        noteCall(month, zone);
        return people.getOrDefault(companyId, List.of());
    }

    @Override
    public List<GapGroup> gaps(CompanyId companyId, YearMonth month, ZoneId zone, int limit) {
        noteCall(month, zone);
        lastGapLimit = limit;
        return gaps.getOrDefault(companyId, List.of());
    }

    private void noteCall(YearMonth month, ZoneId zone) {
        calls++;
        lastMonth = month;
        lastZone = zone;
    }
}
