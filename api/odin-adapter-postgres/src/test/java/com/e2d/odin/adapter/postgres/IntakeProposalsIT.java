package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.intake.ProposalSource;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Las propuestas nuevas son idempotentes en la base y se guardan todas o ninguna. */
class IntakeProposalsIT extends AbstractPostgresIT {

    private final PostgresIntakeRepository intake = new PostgresIntakeRepository(tx);
    private CompanyId company;
    private IntakeBatch batch;
    private Document document;
    private ProjectId project;

    @BeforeEach
    void seed() {
        company = newCompany();
        UserId user = newUser(company);
        batch = IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), company, user, IntakeChannel.PANEL, NOW);
        intake.save(batch);
        document = newDocument(company, user, "Uno", DocumentStatus.READY);
        project = newProject(company, "Calle Olmo 24");
    }

    private IntakeProposal proposal(DocumentId on, ProposalKind kind, Optional<ProjectId> target) {
        return new IntakeProposal(UUID.randomUUID(), company, batch.id(), on, kind, target, Optional.empty(),
                Optional.empty(), ProposalConfidence.HIGH, ProposalSource.RULES, "cita", DecisionState.PROPOSED,
                Optional.empty(), Optional.empty());
    }

    @Test
    void theSameProposalAddedTwiceLeavesOneRow() {
        intake.addProposals(List.of(proposal(document.id(), ProposalKind.PROJECT, Optional.of(project))));
        intake.addProposals(List.of(proposal(document.id(), ProposalKind.PROJECT, Optional.of(project))));

        assertThat(intake.proposals(company, batch.id())).hasSize(1);
    }

    @Test
    void classificationSavesTheTypesAndProposalsTogetherAndOtherCompaniesSeeNone() {
        JdbcClient.create(Db.owner()).sql("UPDATE document SET intake_pending = true, intake_batch_id = '"
                + batch.id().value() + "' WHERE id = '" + document.id().value() + "'").update();
        intake.saveClassification(company, batch.id(), Map.of(document.id(), "acta"),
                List.of(List.of(proposal(document.id(), ProposalKind.PROJECT, Optional.of(project)))));

        assertThat(intake.intakeTypes(company, batch.id())).containsExactlyEntriesOf(Map.of(document.id(), "acta"));
        assertThat(intake.proposals(company, batch.id())).hasSize(1);
        assertThat(intake.intakeTypes(newCompany(), batch.id())).isEmpty();
    }

    @Test
    void sameDocumentAndKindWithAnotherTargetIsAnotherProposal() {
        ProjectId other = newProject(company, "Calle Haya 2");

        intake.addProposals(List.of(proposal(document.id(), ProposalKind.PROJECT, Optional.of(project)),
                proposal(document.id(), ProposalKind.PROJECT, Optional.of(other))));

        assertThat(intake.proposals(company, batch.id())).hasSize(2);
    }

    @Test
    void aFailureWhileAddingLeavesNoneOfTheProposals() {
        IntakeProposal good = proposal(document.id(), ProposalKind.PROJECT, Optional.of(project));
        IntakeProposal broken = proposal(new DocumentId(UUID.randomUUID()), ProposalKind.CASE_MARK, Optional.empty());

        assertThatThrownBy(() -> intake.addProposals(List.of(good, broken))).isInstanceOf(RuntimeException.class);

        assertThat(intake.proposals(company, batch.id())).isEmpty();
    }

    @Test
    void decidingAnExistingProposalStillUpdatesIt() {
        IntakeProposal p = proposal(document.id(), ProposalKind.PROJECT, Optional.of(project));
        intake.addProposals(List.of(p));

        intake.save(p.confirm(newUser(company), NOW));

        assertThat(intake.proposals(company, batch.id())).singleElement()
                .satisfies(x -> assertThat(x.state()).isEqualTo(DecisionState.CONFIRMED));
    }

    @Test
    void aDontKnowIsStoredWithWhoAndWhenAndADecidedProposalWithoutThemIsRefused() {
        IntakeProposal p = proposal(document.id(), ProposalKind.PROJECT, Optional.of(project));
        intake.addProposals(List.of(p));
        UserId admin = newUser(company);

        intake.save(p.dontKnow(admin, NOW));

        assertThat(intake.proposals(company, batch.id())).singleElement().satisfies(x -> {
            assertThat(x.state()).isEqualTo(DecisionState.DONT_KNOW);
            assertThat(x.decidedBy()).contains(admin);
            assertThat(x.decidedAt()).contains(NOW);
        });
        for (String state : List.of("CONFIRMED", "REJECTED", "DONT_KNOW")) {
            assertThatThrownBy(() -> JdbcClient.create(Db.owner()).sql("UPDATE intake_proposal SET state = '" + state
                    + "', decided_by = NULL, decided_at = now() WHERE id = '" + p.id() + "'").update())
                    .as(state + " sin quién").isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

    @Test
    void placingADocumentStoresWhoAndWhen() {
        UserId admin = newUser(company);
        JdbcClient.create(Db.owner()).sql("UPDATE document SET intake_pending = true, intake_batch_id = '"
                + batch.id().value() + "' WHERE id = '" + document.id().value() + "'").update();

        docs.confirmIntake(company, document.id(), Optional.of(project), admin, NOW);

        Map<String, Object> row = JdbcClient.create(Db.owner()).sql("SELECT intake_pending, intake_placed_by, "
                + "intake_placed_at FROM document WHERE id = '" + document.id().value() + "'").query().singleRow();
        assertThat(row.get("intake_pending")).isEqualTo(false);
        assertThat(row.get("intake_placed_by")).isEqualTo(admin.value());
        assertThat(((java.sql.Timestamp) row.get("intake_placed_at")).toInstant()).isEqualTo(NOW);
    }
}
