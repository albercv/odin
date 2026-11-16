package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditEntry;
import java.util.ArrayList;
import java.util.List;

/** Guarda las entradas y, en {@code events}, el orden respecto a otros dobles que comparten la lista. */
public class InMemoryAuditLog implements AuditLog {

    public final List<AuditEntry> entries = new ArrayList<>();
    public final List<AuditEntry> denied = new ArrayList<>();
    private final List<String> events;

    public InMemoryAuditLog(List<String> events) {
        this.events = events;
    }

    public InMemoryAuditLog() {
        this(new ArrayList<>());
    }

    @Override
    public void record(AuditEntry entry) {
        entries.add(entry);
        events.add("record:" + entry.action());
    }

    @Override
    public void recordDenied(AuditEntry entry) {
        denied.add(entry);
        events.add("denied:" + entry.action());
    }
}
