package com.e2d.odin.application.audit;

import com.e2d.odin.application.identity.ChatAccess;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.AuditReader;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditFilter;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.TooManyRequestsException;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * La consulta del registro desde el chat: intervalo obligatorio de 7 días como máximo, 50 filas por llamada y 500 al
 * día por clave (día UTC). Cada llamada deja su entrada; fuera de ámbito o sobre el tope queda negada.
 */
public class ListChatActivity {

    public static final Duration MAX_RANGE = Duration.ofDays(7);
    public static final int DAILY_CAP = 500;

    private final ListAuditActivity list;
    private final AuditReader reader;
    private final AuditLog audit;
    private final Clock clock;

    public ListChatActivity(ListAuditActivity list, AuditReader reader, AuditLog audit, Clock clock) {
        this.list = list;
        this.reader = reader;
        this.audit = audit;
        this.clock = clock;
    }

    public AuditPage execute(ChatAccess access, AuditFilter filter, Optional<String> cursor) {
        requireRange(filter);
        filter.company().filter(c -> !access.actor().administers(c)).ifPresent(c -> {
            audit.recordDenied(entry(access, filter).denied().with("reason", "OUT_OF_SCOPE"));
            throw new ForbiddenException("Esa empresa queda fuera del ámbito de tu clave");
        });
        long used = reader.rowsReturnedToChatKey(access.key().prefix(), clock.instant().truncatedTo(ChronoUnit.DAYS));
        if (used >= DAILY_CAP) {
            audit.recordDenied(entry(access, filter).denied().with("reason", "DAILY_CAP"));
            throw new TooManyRequestsException("Has llegado al tope de " + DAILY_CAP
                    + " filas al día para esta clave; vuelve mañana (día UTC)");
        }
        AuditPage page = list.execute(access.actor(), filter, cursor,
                (int) Math.min(ListAuditActivity.PAGE_SIZE, DAILY_CAP - used));
        audit.record(entry(access, filter).with("rowCount", page.items().size()));
        return page;
    }

    private static void requireRange(AuditFilter filter) {
        Instant from = filter.from().orElseThrow(ListChatActivity::badRange);
        Instant to = filter.to().orElseThrow(ListChatActivity::badRange);
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw badRange();
        }
    }

    private static ValidationException badRange() {
        return new ValidationException("Indica «from» y «to» (instantes ISO-8601) con «to» posterior a «from» y como "
                + "máximo 7 días de intervalo");
    }

    private static AuditEntry entry(ChatAccess access, AuditFilter filter) {
        AuditEntry entry = AuditEntry.by(access.actor(), AuditAction.CHAT_QUERY)
                .with("prefix", access.key().prefix()).with("filters", filter.asDetails());
        return access.key().companyId().map(entry::onCompany).orElse(entry);
    }
}
