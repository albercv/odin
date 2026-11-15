package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** El administrador confirma un grupo de propuestas (D2): todo o nada, en una sola transacción. */
public class ConfirmIntakeProposals {

    public static final int MAX_PROPOSALS = 500;

    private final IntakeRepository intake;
    private final IntakeEffects effects;

    public ConfirmIntakeProposals(IntakeRepository intake, IntakeEffects effects) {
        this.intake = intake;
        this.effects = effects;
    }

    /** Devuelve cuántas propuestas se han confirmado. */
    public int execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, List<UUID> proposalIds) {
        IntakeBatch batch = effects.inReview(actor, companyId, batchId);
        if (proposalIds == null || proposalIds.isEmpty() || proposalIds.size() > MAX_PROPOSALS
                || !proposalIds.stream().allMatch(new HashSet<>()::add)) {
            throw new ValidationException("Elige entre 1 y " + MAX_PROPOSALS + " propuestas distintas");
        }
        List<IntakeProposal> all = intake.proposals(companyId, batchId);
        Map<UUID, IntakeProposal> byId = all.stream().collect(Collectors.toMap(IntakeProposal::id, Function.identity()));
        List<IntakeProposal> selected = proposalIds.stream().map(id -> {
            IntakeProposal p = byId.get(id);
            if (p == null) {
                throw new NotFoundException("Propuesta no encontrada");
            }
            return p;
        }).toList();
        effects.confirm(actor, batch, selected, all);
        effects.closeIfDone(batch);
        return selected.size();
    }
}
