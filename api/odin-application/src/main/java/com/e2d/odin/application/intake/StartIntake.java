package com.e2d.odin.application.intake;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;

/** Abre el lote de la primera carga de una empresa. Solo puede haber uno abierto o en lectura a la vez. */
public class StartIntake {

    private final IntakeRepository intake;
    private final OrganizationRepository organizations;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog audit;

    public StartIntake(IntakeRepository intake, OrganizationRepository organizations, IdGenerator ids, Clock clock,
            AuditLog audit) {
        this.audit = audit;
        this.intake = intake;
        this.organizations = organizations;
        this.ids = ids;
        this.clock = clock;
    }

    public IntakeBatch execute(Actor actor, CompanyId companyId, IntakeChannel channel) {
        AccessPolicy.requireAdminOf(actor, companyId);
        if (channel == null) {
            throw new ValidationException("Indica el canal de la carga");
        }
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        IntakeBatch batch = IntakeBatch.open(new IntakeBatchId(ids.newId()), companyId, actor.userId(), channel,
                clock.instant());
        intake.save(batch);
        audit.record(AuditSupport.onIntake(AuditEntry.by(actor, AuditAction.INTAKE_STARTED), companyId, batch.id())
                .with("channel", channel.name()));
        return batch;
    }
}
