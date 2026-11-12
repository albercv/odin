package com.e2d.odin.application.port;

import com.e2d.odin.application.usage.DeliveryTotal;
import com.e2d.odin.application.usage.WorkerTool;
import com.e2d.odin.domain.shared.CompanyId;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/** Volumen que Odín entrega a la IA del trabajador, por empresa, mes y herramienta. */
public interface DeliveryMeter {

    void record(CompanyId companyId, WorkerTool tool, long bytes, Instant at);

    /** Solo las herramientas con llamadas en el mes. */
    List<DeliveryTotal> totals(CompanyId companyId, YearMonth month);
}
