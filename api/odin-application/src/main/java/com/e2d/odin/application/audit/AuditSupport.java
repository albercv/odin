package com.e2d.odin.application.audit;

import com.e2d.odin.application.port.AuditContext.AuditRequest;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.InstallationToken;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.Collection;

/** Piezas comunes de las entradas de administración. */
public final class AuditSupport {

    public static final int MAX_IDS = 100;

    private AuditSupport() {
    }

    public static java.util.List<String> sorted(Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().toList();
    }

    /** IP y navegador van solo en las entradas de acceso al panel. */
    public static AuditEntry withClient(AuditEntry entry, AuditRequest request) {
        AuditEntry result = entry;
        if (request.ipAddress().isPresent()) {
            result = result.with("ip", request.ipAddress().get());
        }
        if (request.userAgent().isPresent()) {
            result = result.with("userAgent", request.userAgent().get());
        }
        return result;
    }

    /** Comprueba el código de instalación; si es malo deja la entrada negada (fuera de la transacción) y lo propaga. */
    public static void requireInstallationToken(InstallationSettings settings, String token, AuditLog audit,
            AuditAction action) {
        try {
            InstallationToken.require(settings, token);
        } catch (ForbiddenException e) {
            audit.recordDenied(AuditEntry.byInstaller(action).denied());
            throw e;
        }
    }

    /** Un documento como objetivo: su título es la etiqueta; nunca su texto. */
    public static AuditEntry onDocument(AuditEntry entry, Document document) {
        AuditEntry result = entry.onCompany(document.companyId())
                .on("DOCUMENT", document.id().value().toString(), document.title());
        return document.projectId().map(result::onProject).orElse(result);
    }

    /** Un expediente como objetivo: es el de su ficha de caso. */
    public static AuditEntry onCaseFile(AuditEntry entry, Document caseDocument) {
        AuditEntry result = entry.onCompany(caseDocument.companyId())
                .on("CASE_FILE", caseDocument.id().value().toString(), caseDocument.title());
        return caseDocument.projectId().map(result::onProject).orElse(result);
    }

    /** El lote de la primera carga como objetivo. */
    public static AuditEntry onIntake(AuditEntry entry, CompanyId company, IntakeBatchId batch) {
        return entry.onCompany(company).on("INTAKE_BATCH", batch.value().toString(), null);
    }

    /** Una acción en lote: el recuento y hasta {@value #MAX_IDS} ids; más allá, {@code truncated}. */
    public static AuditEntry withIds(AuditEntry entry, Collection<String> ids) {
        AuditEntry result = entry.with("count", ids.size()).with("ids", ids.stream().limit(MAX_IDS).toList());
        return ids.size() > MAX_IDS ? result.with("truncated", true) : result;
    }
}
