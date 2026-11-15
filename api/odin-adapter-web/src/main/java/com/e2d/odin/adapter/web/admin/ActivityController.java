package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.audit.AuditPage;
import com.e2d.odin.application.audit.CheckAuditSeal;
import com.e2d.odin.application.audit.ExportAuditActivity;
import com.e2d.odin.application.audit.GetAuditIntegrity;
import com.e2d.odin.application.audit.ListAuditActivity;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditChannel;
import com.e2d.odin.domain.audit.AuditFilter;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.audit.AuditRow;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta del registro de actividad: por fecha e id descendentes, sin ordenar, agrupar ni contar por persona. */
@RestController
@RequestMapping("/v1/admin")
public class ActivityController {

    private final ListAuditActivity list;
    private final ExportAuditActivity export;
    private final GetAuditIntegrity integrity;
    private final CheckAuditSeal seal;

    public ActivityController(ListAuditActivity list, ExportAuditActivity export, GetAuditIntegrity integrity,
            CheckAuditSeal seal) {
        this.list = list;
        this.export = export;
        this.integrity = integrity;
        this.seal = seal;
    }

    @GetMapping("/activity")
    public Responses.ActivityPageView list(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestParam Optional<Instant> from, @RequestParam Optional<Instant> to,
            @RequestParam Optional<UUID> companyId, @RequestParam Optional<UUID> projectId,
            @RequestParam Optional<AuditAction> action, @RequestParam Optional<UUID> actorUserId,
            @RequestParam Optional<AuditChannel> channel, @RequestParam Optional<AuditOutcome> outcome,
            @RequestParam Optional<String> cursor) {
        AuditPage page = list.execute(actor,
                filter(from, to, companyId, projectId, action, actorUserId, channel, outcome), cursor);
        return new Responses.ActivityPageView(page.items().stream().map(ActivityController::view).toList(),
                page.nextCursor().orElse(null));
    }

    @GetMapping("/activity.csv")
    public ResponseEntity<String> exportCsv(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestParam Optional<Instant> from, @RequestParam Optional<Instant> to,
            @RequestParam Optional<UUID> companyId, @RequestParam Optional<UUID> projectId,
            @RequestParam Optional<AuditAction> action, @RequestParam Optional<UUID> actorUserId,
            @RequestParam Optional<AuditChannel> channel, @RequestParam Optional<AuditOutcome> outcome) {
        var result = export.execute(actor,
                filter(from, to, companyId, projectId, action, actorUserId, channel, outcome));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"actividad.csv\"")
                .body(ActivityCsv.render(result, ExportAuditActivity.MAX_ROWS));
    }

    @GetMapping("/activity/integrity")
    public Responses.ActivityIntegrityView integrity(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor) {
        var result = integrity.execute(actor);
        return new Responses.ActivityIntegrityView(result.logEnabled(), result.intact(),
                result.firstBrokenId().isPresent() ? result.firstBrokenId().getAsLong() : null,
                result.head().map(h -> h.id()).orElse(null), result.head().map(h -> h.hash()).orElse(null),
                result.head().map(h -> h.at()).orElse(null));
    }

    @PostMapping("/activity/seal-check")
    public Responses.SealCheckView sealCheck(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestBody Requests.SealCheck request) {
        return new Responses.SealCheckView(seal.execute(actor, request.id(), request.hash()));
    }

    private static AuditFilter filter(Optional<Instant> from, Optional<Instant> to, Optional<UUID> company,
            Optional<UUID> project, Optional<AuditAction> action, Optional<UUID> actor,
            Optional<AuditChannel> channel, Optional<AuditOutcome> outcome) {
        return new AuditFilter(from, to, company.map(CompanyId::new), project.map(ProjectId::new), action,
                actor.map(UserId::new), channel, outcome);
    }

    private static Responses.ActivityRowView view(AuditRow r) {
        return new Responses.ActivityRowView(r.id(), r.occurredAt(), r.company().map(c -> c.value()).orElse(null),
                r.project().map(p -> p.value()).orElse(null), r.actorKind().name(),
                r.actorUserId().map(u -> u.value()).orElse(null), r.actorLabel().orElse(null),
                r.actorRole().orElse(null), r.channel().name(), r.via().orElse(null), r.action(),
                r.targetKind().orElse(null), r.targetId().orElse(null), r.targetLabel().orElse(null),
                r.outcome().name(), Details.parse(r.detailsJson()));
    }
}
