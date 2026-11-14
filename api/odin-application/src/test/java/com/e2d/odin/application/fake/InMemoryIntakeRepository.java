package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryIntakeRepository implements IntakeRepository {

    private final Map<IntakeBatchId, IntakeBatch> batches = new LinkedHashMap<>();
    private final Map<String, ProjectCandidate> candidates = new LinkedHashMap<>();
    private final Map<UUID, IntakeProposal> proposals = new LinkedHashMap<>();

    @Override
    public synchronized void save(IntakeBatch batch) {
        boolean active = batch.state() == IntakeState.OPEN || batch.state() == IntakeState.READING;
        if (active && batches.values().stream().anyMatch(b -> !b.id().equals(batch.id())
                && b.companyId().equals(batch.companyId()) && (b.state() == IntakeState.OPEN || b.state() == IntakeState.READING))) {
            throw new ConflictException("Ya existe un registro con esos datos");
        }
        batches.put(batch.id(), batch);
    }

    @Override
    public synchronized void countFile(CompanyId companyId, IntakeBatchId id, boolean discarded) {
        IntakeBatch b = batches.get(id);
        batches.put(id, new IntakeBatch(b.id(), b.companyId(), b.startedBy(), b.channel(), b.state(), b.createdAt(),
                b.closedAt(), b.alreadyThere() + (discarded ? 0 : 1), b.discarded() + (discarded ? 1 : 0)));
    }

    @Override
    public synchronized Optional<IntakeBatch> find(CompanyId companyId, IntakeBatchId id) {
        return Optional.ofNullable(batches.get(id)).filter(b -> b.companyId().equals(companyId));
    }

    @Override
    public synchronized Optional<IntakeBatch> findActive(CompanyId companyId) {
        return batches.values().stream().filter(b -> b.companyId().equals(companyId)
                && (b.state() == IntakeState.OPEN || b.state() == IntakeState.READING)).findFirst();
    }

    @Override
    public synchronized void save(ProjectCandidate candidate) {
        candidates.put(candidate.batchId().value() + "/" + candidate.name(), candidate);
    }

    @Override
    public synchronized List<ProjectCandidate> candidates(CompanyId companyId, IntakeBatchId batchId) {
        return candidates.values().stream()
                .filter(c -> c.companyId().equals(companyId) && c.batchId().equals(batchId)).toList();
    }

    @Override
    public synchronized void save(IntakeProposal proposal) {
        proposals.put(proposal.id(), proposal);
    }

    @Override
    public synchronized List<IntakeProposal> proposals(CompanyId companyId, IntakeBatchId batchId) {
        return proposals.values().stream()
                .filter(p -> p.companyId().equals(companyId) && p.batchId().equals(batchId)).toList();
    }
}
