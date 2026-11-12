package com.e2d.odin.application.usage;

import java.time.YearMonth;
import java.util.List;

public record DeliveredVolumeReport(YearMonth month, List<DeliveryTotal> totals) {
}
