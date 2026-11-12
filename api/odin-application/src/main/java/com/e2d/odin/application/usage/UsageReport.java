package com.e2d.odin.application.usage;

import java.time.YearMonth;
import java.util.List;

public record UsageReport(YearMonth month, CompanyUsage company, List<PersonUsage> people) {
}
