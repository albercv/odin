package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.IntakeSettings;
import java.util.ArrayList;
import com.e2d.odin.domain.intake.ProposalKind;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** El lote con sus recuentos y, para reanudar la subida, qué huellas ya están. */
public class GetIntakeBatch {

    private final IntakeRepository intake;
    private final DocumentRepository documents;
    private final InstallationSettings installation;
    private final IntakeSettings settings;

    public GetIntakeBatch(IntakeRepository intake, DocumentRepository documents, InstallationSettings installation,
            IntakeSettings settings) {
        this.intake = intake;
        this.documents = documents;
        this.installation = installation;
        this.settings = settings;
    }

    public IntakeBatchView execute(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        IntakeBatch batch = IntakeBatches.require(intake, actor, companyId, batchId);
        List<IntakeWarning> warnings = new ArrayList<>();
        if (!installation.scannedDocumentsSupported()) {
            warnings.add(IntakeWarning.NO_READER);
        }
        if (!settings.localModelConfigured()) {
            warnings.add(IntakeWarning.NO_LOCAL_MODEL);
        }
        if (batch.state() == IntakeState.READING && !batch.projectListClosed()) {
            warnings.add(IntakeWarning.PROJECT_LIST_NOT_CONFIRMED);
        }
        Map<ProposalKind, Integer> proposed = new EnumMap<>(ProposalKind.class);
        List<IntakeProposal> all = intake.proposals(companyId, batchId);
        all.stream().filter(IntakeProposal::open).forEach(p -> proposed.merge(p.kind(), 1, Integer::sum));
        int toDecide = (int) all.stream().filter(p -> p.state() == DecisionState.PROPOSED).count();
        int unplaced = batch.state() == IntakeState.IN_REVIEW || batch.state() == IntakeState.CLOSED
                ? UnplacedDocuments.find(intake, documents, companyId, batchId).size() : 0;
        return new IntakeBatchView(batch, IntakeBatches.counts(documents, installation, companyId, batchId), warnings,
                proposed, unplaced, toDecide);
    }

    /** De esas huellas, las que ya están en la empresa o se descartaron en el lote: el cliente no las vuelve a subir. */
    public Set<String> knownFiles(Actor actor, CompanyId companyId, IntakeBatchId batchId, Collection<String> sha256s) {
        IntakeBatches.require(intake, actor, companyId, batchId);
        return documents.existingSha256ForIntake(companyId, batchId, sha256s);
    }
}
