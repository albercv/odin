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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
