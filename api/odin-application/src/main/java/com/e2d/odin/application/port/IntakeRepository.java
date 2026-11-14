package com.e2d.odin.application.port;

import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.List;
import java.util.Optional;

/** Lotes de primera carga con sus candidatos y propuestas. Todo es de la bandeja del administrador. */
public interface IntakeRepository {

    /** Crea o actualiza el lote. Un segundo lote OPEN o READING en la empresa lanza ConflictException. */
    void save(IntakeBatch batch);

    Optional<IntakeBatch> find(CompanyId companyId, IntakeBatchId id);

    /** El lote abierto o en lectura de la empresa, si lo hay. */
    Optional<IntakeBatch> findActive(CompanyId companyId);

    /** Suma uno al recuento de «ya estaban» o de «descartados en este lote», sin pisar a otra subida en marcha. */
    void countFile(CompanyId companyId, IntakeBatchId id, boolean discarded);

    void save(ProjectCandidate candidate);

    List<ProjectCandidate> candidates(CompanyId companyId, IntakeBatchId batchId);

    void save(IntakeProposal proposal);

    List<IntakeProposal> proposals(CompanyId companyId, IntakeBatchId batchId);
}
