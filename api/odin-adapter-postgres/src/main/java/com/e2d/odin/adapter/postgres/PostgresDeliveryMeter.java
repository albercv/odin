package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.DeliveryMeter;
import com.e2d.odin.application.usage.DeliveryTotal;
import com.e2d.odin.application.usage.WorkerTool;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/** Un UPSERT por respuesta entregada, siempre dentro de la empresa de la clave. */
public class PostgresDeliveryMeter implements DeliveryMeter {

    private final ScopedTransactions tx;
    private final ZoneId zone;

    public PostgresDeliveryMeter(ScopedTransactions tx, ZoneId zone) {
        this.tx = tx;
        this.zone = zone;
    }

    @Override
    public void record(CompanyId companyId, WorkerTool tool, long bytes, Instant at) {
        LocalDate month = YearMonth.from(at.atZone(zone)).atDay(1);
        tx.runInScope(CompanyScope.of(companyId), jdbc -> jdbc.sql("""
                INSERT INTO delivery_usage (company_id, month, tool, calls, bytes)
                VALUES (:company, :month, :tool, 1, :bytes)
                ON CONFLICT (company_id, month, tool) DO UPDATE SET calls = delivery_usage.calls + 1,
                    bytes = delivery_usage.bytes + :bytes""")
                .param("company", companyId.value()).param("month", month).param("tool", tool.name())
                .param("bytes", bytes).update());
    }

    @Override
    public List<DeliveryTotal> totals(CompanyId companyId, YearMonth month) {
        CompanyScope scope = CompanyScope.of(companyId);
        String sql = "SELECT tool, calls, bytes FROM delivery_usage WHERE month = :month AND "
                + Sql.inScope("company_id") + " ORDER BY tool";
        return tx.inScope(scope, jdbc -> Sql.scoped(jdbc.sql(sql), scope).param("month", month.atDay(1))
                .query((rs, n) -> new DeliveryTotal(WorkerTool.valueOf(rs.getString("tool")), rs.getLong("calls"),
                        rs.getLong("bytes"))).list());
    }
}
