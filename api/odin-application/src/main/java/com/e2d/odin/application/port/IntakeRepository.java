package com.e2d.odin.application.port;

import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lotes de primera carga con sus candidatos y propuestas. Todo es de la bandeja del administrador. */
public interface IntakeRepository {

    /** Crea o actualiza el lote. Un segundo lote OPEN o READING en la empresa lanza ConflictException. */
    void save(IntakeBatch batch);

    Optional<IntakeBatch> find(CompanyId companyId, IntakeBatchId id);

    /** El lote abierto o en lectura de la empresa, si lo hay. */
    Optional<IntakeBatch> findActive(CompanyId companyId);

    /** Los lotes de la empresa que no están cerrados, el más reciente primero. */
    List<IntakeBatch> findOpen(CompanyId companyId);

    /** Los lotes en lectura o en revisión de todas las empresas: es lo que recorre la tarea programada. */
    List<IntakeBatch> findReadingOrInReview();

    /** Suma uno al recuento de «ya estaban» o de «descartados en este lote», sin pisar a otra subida en marcha. */
    void countFile(CompanyId companyId, IntakeBatchId id, boolean discarded);

    void save(ProjectCandidate candidate);

    List<ProjectCandidate> candidates(CompanyId companyId, IntakeBatchId batchId);

    void save(IntakeProposal proposal);

    /**
     * Añade propuestas nuevas, todas o ninguna. Una propuesta igual a otra ya guardada (mismo lote, documento, tipo y
     * destinos) se ignora, así que dos pasadas a la vez no duplican. Decidir una propuesta existente sigue siendo save.
     */
    void addProposals(List<IntakeProposal> proposals);

    List<IntakeProposal> proposals(CompanyId companyId, IntakeBatchId batchId);

    /**
     * Guarda de una vez, en una sola transacción, el tipo calculado de los documentos que lo tienen (el resto queda sin tipo) y las
     * propuestas nuevas de la pasada (como addProposals, una por tanda).
     */
    void saveClassification(CompanyId companyId, IntakeBatchId batchId, Map<DocumentId, String> types,
            List<List<IntakeProposal>> proposalGroups);

    /** El tipo de documento de cada documento del lote que lo tiene; solo para la bandeja del administrador. */
    Map<DocumentId, String> intakeTypes(CompanyId companyId, IntakeBatchId batchId);
}
