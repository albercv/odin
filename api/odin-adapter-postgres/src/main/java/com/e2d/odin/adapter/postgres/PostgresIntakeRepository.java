package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.intake.CandidateOrigin;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.intake.ProposalSource;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class PostgresIntakeRepository implements IntakeRepository {

    private static final String BATCH_COLUMNS = "id, company_id, started_by, channel, state, created_at, closed_at, "
            + "already_there, discarded, project_list_closed_by, project_list_closed_at";
    private static final String PROPOSAL_COLUMNS = "id, company_id, batch_id, document_id, kind, target_project_id, "
            + "target_document_id, case_number, confidence, source, reason, state, decided_by, decided_at";

    /** Sin visibilidad de proyectos, la RLS de las propuestas solo vería documentos sin proyecto. */
    private static final ProjectVisibility ALL = ProjectVisibility.all();

    private final ScopedTransactions tx;

    public PostgresIntakeRepository(ScopedTransactions tx) {
        this.tx = tx.includeIntake(true);
    }

    @Override
    public void save(IntakeBatch b) {
        tx.runInScope(CompanyScope.of(b.companyId()), ALL, jdbc -> jdbc.sql("""
                INSERT INTO intake_batch (id, company_id, started_by, channel, state, created_at, closed_at,
                    project_list_closed_by, project_list_closed_at)
                VALUES (:id, :company, :by, :channel, :state, :created, :closed, :listBy, :listAt)
                ON CONFLICT (id) DO UPDATE SET state = EXCLUDED.state, closed_at = EXCLUDED.closed_at,
                    project_list_closed_by = EXCLUDED.project_list_closed_by,
                    project_list_closed_at = EXCLUDED.project_list_closed_at""")
                .param("id", b.id().value()).param("company", b.companyId().value()).param("by", b.startedBy().value())
                .param("channel", b.channel().name()).param("state", b.state().name())
                .param("created", Sql.ts(b.createdAt())).param("closed", Sql.ts(b.closedAt()))
                .param("listBy", b.projectListClosedBy().map(UserId::value).orElse(null))
                .param("listAt", Sql.ts(b.projectListClosedAt())).update());
    }

    @Override
    public void countFile(CompanyId companyId, IntakeBatchId id, boolean discarded) {
        String column = discarded ? "discarded" : "already_there";
        tx.runInScope(CompanyScope.of(companyId), ALL, jdbc -> jdbc.sql("UPDATE intake_batch SET " + column + " = "
                + column + " + 1 WHERE id = :id AND company_id = :company").param("id", id.value())
                .param("company", companyId.value()).update());
    }

    @Override
    public Optional<IntakeBatch> find(CompanyId companyId, IntakeBatchId id) {
        return tx.inScope(CompanyScope.of(companyId), ALL, jdbc -> jdbc.sql("SELECT " + BATCH_COLUMNS
                + " FROM intake_batch WHERE id = :id AND company_id = :company").param("id", id.value())
                .param("company", companyId.value()).query((rs, n) -> batch(rs)).optional());
    }

    @Override
    public Optional<IntakeBatch> findActive(CompanyId companyId) {
        return tx.inScope(CompanyScope.of(companyId), ALL, jdbc -> jdbc.sql("SELECT " + BATCH_COLUMNS
                + " FROM intake_batch WHERE company_id = :company AND state IN ('OPEN', 'READING')")
                .param("company", companyId.value()).query((rs, n) -> batch(rs)).optional());
    }

    @Override
    public List<IntakeBatch> findOpen(CompanyId companyId) {
        return tx.inScope(CompanyScope.of(companyId), ALL, jdbc -> jdbc.sql("SELECT " + BATCH_COLUMNS
                + " FROM intake_batch WHERE company_id = :company AND state <> 'CLOSED' ORDER BY created_at DESC")
                .param("company", companyId.value()).query((rs, n) -> batch(rs)).list());
    }

    @Override
    public List<IntakeBatch> findReadingOrInReview() {
        return tx.inScope(CompanyScope.all(), ALL, jdbc -> jdbc.sql("SELECT " + BATCH_COLUMNS
                + " FROM intake_batch WHERE state IN ('READING', 'IN_REVIEW') ORDER BY created_at")
                .query((rs, n) -> batch(rs)).list());
    }

    @Override
    public void save(ProjectCandidate c) {
        tx.runInScope(CompanyScope.of(c.companyId()), ALL, jdbc -> jdbc.sql("""
                INSERT INTO intake_project_candidate (company_id, batch_id, name, origin, document_count, state, project_id)
                VALUES (:company, :batch, :name, :origin, :count, :state, :project)
                ON CONFLICT (batch_id, name) DO UPDATE SET origin = EXCLUDED.origin, document_count = EXCLUDED.document_count,
                    state = EXCLUDED.state, project_id = EXCLUDED.project_id""")
                .param("company", c.companyId().value()).param("batch", c.batchId().value()).param("name", c.name())
                .param("origin", c.origin().name()).param("count", c.documentCount()).param("state", c.state().name())
                .param("project", c.projectId().map(ProjectId::value).orElse(null)).update());
    }

    @Override
    public List<ProjectCandidate> candidates(CompanyId companyId, IntakeBatchId batchId) {
        return tx.inScope(CompanyScope.of(companyId), ALL, jdbc -> jdbc.sql("""
                SELECT company_id, batch_id, name, origin, document_count, state, project_id
                FROM intake_project_candidate WHERE company_id = :company AND batch_id = :batch ORDER BY name""")
                .param("company", companyId.value()).param("batch", batchId.value())
                .query((rs, n) -> candidate(rs)).list());
    }

    @Override
    public void save(IntakeProposal p) {
        tx.runInScope(CompanyScope.of(p.companyId()), ALL, jdbc -> jdbc.sql(INSERT_PROPOSAL + """
                ON CONFLICT (id) DO UPDATE SET state = EXCLUDED.state, decided_by = EXCLUDED.decided_by,
                    decided_at = EXCLUDED.decided_at""").params(proposalParams(p)).update());
    }

    @Override
    public void addProposals(List<IntakeProposal> proposals) {
        if (proposals.isEmpty()) {
            return;
        }
        tx.runInScope(CompanyScope.of(proposals.get(0).companyId()), ALL, jdbc -> proposals.forEach(
                p -> jdbc.sql(INSERT_PROPOSAL + " ON CONFLICT DO NOTHING").params(proposalParams(p)).update()));
    }

    @Override
    public void saveClassification(CompanyId companyId, IntakeBatchId batchId, Map<DocumentId, String> types,
            List<List<IntakeProposal>> proposalGroups) {
        tx.runInScope(CompanyScope.of(companyId), ALL, jdbc -> {
            types.forEach((document, type) -> jdbc.sql(UPDATE_TYPE).param("type", type).param("id", document.value())
                    .param("company", companyId.value()).param("batch", batchId.value()).update());
            proposalGroups.forEach(group -> group.forEach(
                    p -> jdbc.sql(INSERT_PROPOSAL + " ON CONFLICT DO NOTHING").params(proposalParams(p)).update()));
        });
    }

    private static final String UPDATE_TYPE = """
            UPDATE document SET intake_type = :type
            WHERE id = :id AND company_id = :company AND intake_batch_id = :batch""";

    @Override
    public Map<DocumentId, String> intakeTypes(CompanyId companyId, IntakeBatchId batchId) {
        return tx.inScope(CompanyScope.of(companyId), ALL, jdbc -> jdbc.sql("""
                SELECT id, intake_type FROM document
                WHERE company_id = :company AND intake_batch_id = :batch AND intake_type IS NOT NULL""")
                .param("company", companyId.value()).param("batch", batchId.value())
                .query((rs, n) -> Map.entry(new DocumentId(Sql.uuid(rs, "id")), rs.getString("intake_type"))).list()
                .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static final String INSERT_PROPOSAL = """
            INSERT INTO intake_proposal (id, company_id, batch_id, document_id, kind, target_project_id,
                target_document_id, case_number, confidence, source, reason, state, decided_by, decided_at)
            VALUES (:id, :company, :batch, :document, :kind, :targetProject, :targetDocument, :caseNumber,
                :confidence, :source, :reason, :state, :decidedBy, :decidedAt)""";

    private static Map<String, Object> proposalParams(IntakeProposal p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.id());
        m.put("company", p.companyId().value());
        m.put("batch", p.batchId().value());
        m.put("document", p.documentId().value());
        m.put("kind", p.kind().name());
        m.put("targetProject", p.targetProjectId().map(ProjectId::value).orElse(null));
        m.put("targetDocument", p.targetDocumentId().map(DocumentId::value).orElse(null));
        m.put("caseNumber", p.caseNumber().orElse(null));
        m.put("confidence", p.confidence().name());
        m.put("source", p.source().name());
        m.put("reason", p.reason());
        m.put("state", p.state().name());
        m.put("decidedBy", p.decidedBy().map(UserId::value).orElse(null));
        m.put("decidedAt", Sql.ts(p.decidedAt()));
        return m;
    }

    @Override
    public List<IntakeProposal> proposals(CompanyId companyId, IntakeBatchId batchId) {
        return tx.inScope(CompanyScope.of(companyId), ALL, jdbc -> jdbc.sql("SELECT " + PROPOSAL_COLUMNS
                + " FROM intake_proposal WHERE company_id = :company AND batch_id = :batch ORDER BY id")
                .param("company", companyId.value()).param("batch", batchId.value())
                .query((rs, n) -> proposal(rs)).list());
    }

    private static IntakeBatch batch(ResultSet rs) throws SQLException {
        return new IntakeBatch(new IntakeBatchId(Sql.uuid(rs, "id")), new CompanyId(Sql.uuid(rs, "company_id")),
                new UserId(Sql.uuid(rs, "started_by")), IntakeChannel.valueOf(rs.getString("channel")),
                IntakeState.valueOf(rs.getString("state")), Sql.instant(rs, "created_at"),
                Sql.optionalInstant(rs, "closed_at"), rs.getInt("already_there"), rs.getInt("discarded"),
                optional(rs, "project_list_closed_by", UserId::new), Sql.optionalInstant(rs, "project_list_closed_at"));
    }

    private static ProjectCandidate candidate(ResultSet rs) throws SQLException {
        return new ProjectCandidate(new CompanyId(Sql.uuid(rs, "company_id")),
                new IntakeBatchId(Sql.uuid(rs, "batch_id")), rs.getString("name"),
                CandidateOrigin.valueOf(rs.getString("origin")), rs.getInt("document_count"),
                DecisionState.valueOf(rs.getString("state")), optional(rs, "project_id", ProjectId::new));
    }

    private static IntakeProposal proposal(ResultSet rs) throws SQLException {
        return new IntakeProposal(Sql.uuid(rs, "id"), new CompanyId(Sql.uuid(rs, "company_id")),
                new IntakeBatchId(Sql.uuid(rs, "batch_id")), new DocumentId(Sql.uuid(rs, "document_id")),
                ProposalKind.valueOf(rs.getString("kind")), optional(rs, "target_project_id", ProjectId::new),
                optional(rs, "target_document_id", DocumentId::new), Optional.ofNullable(rs.getString("case_number")),
                ProposalConfidence.valueOf(rs.getString("confidence")), ProposalSource.valueOf(rs.getString("source")),
                rs.getString("reason"), DecisionState.valueOf(rs.getString("state")),
                optional(rs, "decided_by", UserId::new), Sql.optionalInstant(rs, "decided_at"));
    }

    private static <T> Optional<T> optional(ResultSet rs, String column, Function<UUID, T> wrap) throws SQLException {
        return Optional.ofNullable(Sql.uuid(rs, column)).map(wrap);
    }
}
