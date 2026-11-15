package com.e2d.odin.application.audit;

import com.e2d.odin.domain.audit.AuditRow;
import java.util.List;
import java.util.Optional;

public record AuditPage(List<AuditRow> items, Optional<String> nextCursor) {
}
