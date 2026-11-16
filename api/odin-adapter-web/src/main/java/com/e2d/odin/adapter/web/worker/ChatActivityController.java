package com.e2d.odin.adapter.web.worker;

import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.audit.AuditPage;
import com.e2d.odin.application.audit.ListChatActivity;
import com.e2d.odin.application.identity.ChatAccess;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditChannel;
import com.e2d.odin.domain.audit.AuditFilter;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.audit.AuditRow;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** El registro de actividad para la clave de administrador del chat. Sin correo, IP ni navegador en la salida. */
@RestController
public class ChatActivityController {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final java.util.Set<String> PERSONAL = java.util.Set.of("email", "ip", "userAgent");

    private final ListChatActivity list;

    public ChatActivityController(ListChatActivity list) {
        this.list = list;
    }

    @GetMapping("/v1/activity")
    public Responses.ActivityPageView activity(@RequestAttribute(CurrentActor.CHAT_ACCESS) ChatAccess access,
            @RequestParam Optional<Instant> from, @RequestParam Optional<Instant> to,
            @RequestParam Optional<UUID> companyId, @RequestParam Optional<UUID> projectId,
            @RequestParam Optional<AuditAction> action, @RequestParam Optional<UUID> actorUserId,
            @RequestParam Optional<AuditChannel> channel, @RequestParam Optional<AuditOutcome> outcome,
            @RequestParam Optional<String> cursor) {
        AuditPage page = list.execute(access, new AuditFilter(from, to, companyId.map(CompanyId::new),
                projectId.map(ProjectId::new), action, actorUserId.map(UserId::new), channel, outcome), cursor);
        return new Responses.ActivityPageView(page.items().stream().map(ChatActivityController::view).toList(),
                page.nextCursor().orElse(null));
    }

    private static Responses.ActivityRowView view(AuditRow r) {
        return new Responses.ActivityRowView(r.id(), r.occurredAt(), r.company().map(c -> c.value()).orElse(null),
                r.project().map(p -> p.value()).orElse(null), r.actorKind().name(),
                r.actorUserId().map(u -> u.value()).orElse(null), r.actorLabel().orElse(null),
                r.actorRole().orElse(null), r.channel().name(), r.via().orElse(null), r.action(),
                r.targetKind().orElse(null), r.targetId().orElse(null), r.targetLabel().orElse(null),
                r.outcome().name(), withoutPersonalData(JSON.readValue(r.detailsJson(),
                        new TypeReference<Map<String, Object>>() { })));
    }

    /** Quita en cualquier nivel lo que no debe llegar a la IA externa. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withoutPersonalData(Map<String, Object> details) {
        details.keySet().removeAll(PERSONAL);
        for (Iterator<Object> it = details.values().iterator(); it.hasNext();) {
            if (it.next() instanceof Map<?, ?> nested) {
                withoutPersonalData((Map<String, Object>) nested);
            }
        }
        return details;
    }
}
