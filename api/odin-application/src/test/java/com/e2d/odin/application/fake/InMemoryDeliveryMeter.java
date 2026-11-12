package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.DeliveryMeter;
import com.e2d.odin.application.usage.DeliveryTotal;
import com.e2d.odin.application.usage.WorkerTool;
import com.e2d.odin.domain.shared.CompanyId;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Doble del puerto: suma por empresa, mes (en UTC) y herramienta; puede fallar a propósito. */
public class InMemoryDeliveryMeter implements DeliveryMeter {

    public record Entry(CompanyId company, WorkerTool tool, long bytes, Instant at) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private RuntimeException failure;

    public List<Entry> entries() {
        return entries;
    }

    public void failWith(RuntimeException error) {
        failure = error;
    }

    @Override
    public void record(CompanyId companyId, WorkerTool tool, long bytes, Instant at) {
        if (failure != null) {
            throw failure;
        }
        entries.add(new Entry(companyId, tool, bytes, at));
    }

    @Override
    public List<DeliveryTotal> totals(CompanyId companyId, YearMonth month) {
        Map<WorkerTool, long[]> sums = new EnumMap<>(WorkerTool.class);
        entries.stream().filter(e -> e.company().equals(companyId)
                && YearMonth.from(e.at().atZone(ZoneOffset.UTC)).equals(month)).forEach(e -> {
                    long[] sum = sums.computeIfAbsent(e.tool(), t -> new long[2]);
                    sum[0]++;
                    sum[1] += e.bytes();
                });
        return sums.entrySet().stream().map(e -> new DeliveryTotal(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }
}
