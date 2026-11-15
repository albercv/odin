package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.application.audit.ExportAuditActivity.Export;
import com.e2d.odin.domain.audit.AuditRow;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** El CSV de la exportación: cabeza de la cadena en un comentario, cabecera, filas y, si procede, el aviso de corte. */
final class ActivityCsv {

    private static final String EOL = "\r\n";
    private static final List<String> HEADER = List.of("id", "occurredAt", "companyId", "projectId", "actorKind",
            "actorUserId", "actorName", "actorRole", "channel", "via", "action", "targetKind", "targetId",
            "targetLabel", "outcome", "details");

    private ActivityCsv() {
    }

    static String render(Export export, int maxRows) {
        StringBuilder out = new StringBuilder();
        out.append("# cabeza: ").append(export.head().map(h -> h.hash() + " " + h.at()).orElse("(registro vacío)"))
                .append(EOL).append(String.join(",", HEADER)).append(EOL);
        export.rows().forEach(row -> out.append(line(row)).append(EOL));
        if (export.truncated()) {
            out.append("# truncado: solo las ").append(maxRows).append(" filas más recientes").append(EOL);
        }
        return out.toString();
    }

    private static String line(AuditRow r) {
        return java.util.stream.Stream.of(String.valueOf(r.id()), r.occurredAt().toString(), opt(r.company().map(c -> c.value())),
                opt(r.project().map(p -> p.value())), r.actorKind().name(), opt(r.actorUserId().map(u -> u.value())),
                r.actorLabel().orElse(""), r.actorRole().orElse(""), r.channel().name(), r.via().orElse(""),
                r.action(), r.targetKind().orElse(""), r.targetId().orElse(""), r.targetLabel().orElse(""),
                r.outcome().name(), Details.compact(r.detailsJson())).map(ActivityCsv::cell)
                .collect(Collectors.joining(","));
    }

    private static String opt(Optional<?> value) {
        return value.map(Object::toString).orElse("");
    }

    /** Neutraliza lo que una hoja de cálculo tomaría por fórmula y escapa según RFC 4180. */
    static String cell(String value) {
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return safe.matches("(?s).*[\",\r\n].*") ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
    }
}
