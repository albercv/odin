package com.e2d.odin.application.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryIntakeRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.intake.ProjectDecision.Action;
import com.e2d.odin.application.organization.CreateProject;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.CandidateOrigin;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectListTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryIntakeRepository intake = new InMemoryIntakeRepository();
    private final SequenceIdGenerator ids = new SequenceIdGenerator();
    private final StartIntake start = new StartIntake(intake, orgs, ids, new MutableClock());
    private final ImportProjectList importList = new ImportProjectList(intake);
    private final ListProjectCandidates list = new ListProjectCandidates(intake, docs);
    private final ConfirmProjectCandidates confirm = new ConfirmProjectCandidates(intake, orgs,
            new CreateProject(orgs, ids, new InMemoryAuditLog()));
    private final MutableClock clock = new MutableClock();
    private final CloseProjectList closeList = new CloseProjectList(intake, docs, list,
            new InstallationSettings("t", true), clock);
    private final CompanyId company = Fixtures.company(orgs);
    private final CompanyId other = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);
    private IntakeBatch batch;

    @BeforeEach
    void openBatch() {
        batch = start.execute(admin, company, IntakeChannel.PANEL);
    }

    private List<ProjectCandidate> importText(String text) {
        return importList.execute(admin, company, batch.id(), text.getBytes(StandardCharsets.UTF_8));
    }

    private void pendingDocument(String text) {
        Document d = Fixtures.readyDocument(docs, company, text);
        docs.save(d.withIntake(batch.id(), "a/" + d.id().value() + ".pdf"));
    }

    private List<String> names(List<ProjectCandidate> candidates) {
        return candidates.stream().map(ProjectCandidate::name).toList();
    }

    @Test
    void importingIgnoresEmptyAndRepeatedLinesAndTheByteOrderMark() {
        List<ProjectCandidate> candidates = importText("﻿Calle Olmo 24\n\n  \nCALLE OLMO 24 \nc/ Olmo 24\r\nResidencial Las Acacias\n");

        assertThat(names(candidates)).containsExactlyInAnyOrder("Calle Olmo 24", "c/ Olmo 24", "Residencial Las Acacias");
        assertThat(candidates).allSatisfy(c -> {
            assertThat(c.origin()).isEqualTo(CandidateOrigin.IMPORTED);
            assertThat(c.state()).isEqualTo(DecisionState.PROPOSED);
        });
        assertThat(intake.candidates(company, batch.id())).hasSize(3);
    }

    @Test
    void importingTwiceDoesNotDuplicateWhatIsAlreadyThere() {
        importText("Calle Olmo 24\nCalle Haya 2");

        assertThat(names(importText("calle olmo 24\nCalle Pino 7"))).containsExactlyInAnyOrder("Calle Haya 2", "Calle Olmo 24",
                "Calle Pino 7");
    }

    @Test
    void anEmptyListOrOneThatIsNotUtf8IsRefused() {
        assertThatThrownBy(() -> importText(" \n\n")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> importList.execute(admin, company, batch.id(), new byte[] {(byte) 0xC3, 0x28}))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void importingRejectsTheDiscoveredCandidatesStillProposed() {
        pendingDocument("Comunidad: Calle Haya 2");
        list.discover(admin, company, batch.id());

        importText("Calle Olmo 24");

        assertThat(intake.candidates(company, batch.id())).extracting(ProjectCandidate::name, ProjectCandidate::state)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Calle Haya 2", DecisionState.REJECTED),
                        org.assertj.core.groups.Tuple.tuple("Calle Olmo 24", DecisionState.PROPOSED));
    }

    @Test
    void aDiscoveredNameRepeatedInTheImportedListEndsAsOneImportedProposedCandidate() {
        pendingDocument("Comunidad: Calle Haya 2");
        pendingDocument("Comunidad: Calle Olmo 24");
        list.discover(admin, company, batch.id());

        importText("Calle Haya 2\nCalle Pino 7");

        assertThat(intake.candidates(company, batch.id())).extracting(ProjectCandidate::name,
                ProjectCandidate::origin, ProjectCandidate::state).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("Calle Haya 2", CandidateOrigin.IMPORTED, DecisionState.PROPOSED),
                org.assertj.core.groups.Tuple.tuple("Calle Pino 7", CandidateOrigin.IMPORTED, DecisionState.PROPOSED),
                org.assertj.core.groups.Tuple.tuple("Calle Olmo 24", CandidateOrigin.DISCOVERED, DecisionState.REJECTED));
    }

    @Test
    void discoveryProposesTheNamesOfTheReadPendingDocumentsWithTheirCount() {
        pendingDocument("Comunidad: Calle Haya 2\nnada");
        pendingDocument("COMUNIDAD DE PROPIETARIOS CALLE HAYA 2, Puerto Albar");
        pendingDocument("Comunidad: Residencial Las Acacias");
        Fixtures.document(docs, company, com.e2d.odin.domain.document.DocumentStatus.RECEIVED);

        List<ProjectCandidate> found = list.discover(admin, company, batch.id());

        assertThat(found).extracting(ProjectCandidate::name, ProjectCandidate::documentCount, ProjectCandidate::origin)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Calle Haya 2", 2, CandidateOrigin.DISCOVERED),
                        org.assertj.core.groups.Tuple.tuple("Residencial Las Acacias", 1, CandidateOrigin.DISCOVERED));
    }

    @Test
    void discoveringAgainUpdatesCountsAndKeepsWhatTheAdminAlreadyDecided() {
        pendingDocument("Comunidad: Calle Haya 2");
        list.discover(admin, company, batch.id());
        confirm.execute(admin, company, batch.id(), List.of(new ProjectDecision("Calle Haya 2", Action.REJECT,
                Optional.empty())));
        pendingDocument("Comunidad: Calle Haya 2");
        pendingDocument("Comunidad: Calle Olmo 24");

        List<ProjectCandidate> found = list.discover(admin, company, batch.id());

        assertThat(found).extracting(ProjectCandidate::name, ProjectCandidate::state, ProjectCandidate::documentCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Calle Haya 2", DecisionState.REJECTED, 1),
                        org.assertj.core.groups.Tuple.tuple("Calle Olmo 24", DecisionState.PROPOSED, 1));
    }

    @Test
    void anImportedListWinsOverDiscovery() {
        importText("Calle Olmo 24");
        pendingDocument("Comunidad: Calle Haya 2");

        assertThat(names(list.discover(admin, company, batch.id()))).containsExactly("Calle Olmo 24");
    }

    @Test
    void confirmingCreatesTheProjectsAndLinksTheCandidates() {
        importText("Calle Olmo 24\nCalle Haya 2");

        List<ProjectCandidate> done = confirm.execute(admin, company, batch.id(), List.of(
                new ProjectDecision("Calle Olmo 24", Action.CREATE, Optional.empty())));

        assertThat(orgs.listProjects(company)).extracting(Project::name).containsExactly("Calle Olmo 24");
        ProjectCandidate olmo = done.stream().filter(c -> c.name().equals("Calle Olmo 24")).findFirst().orElseThrow();
        assertThat(olmo.state()).isEqualTo(DecisionState.CONFIRMED);
        assertThat(olmo.projectId()).contains(orgs.listProjects(company).get(0).id());
        assertThat(done.stream().filter(c -> c.name().equals("Calle Haya 2")).findFirst().orElseThrow().state())
                .isEqualTo(DecisionState.PROPOSED);
    }

    @Test
    void mergingLinksAnExistingProjectAndCreatesNone() {
        importText("C/ Olmo 24");
        ProjectId existing = new CreateProject(orgs, ids, new InMemoryAuditLog()).execute(admin, company, "Calle Olmo 24").id();

        List<ProjectCandidate> done = confirm.execute(admin, company, batch.id(), List.of(
                new ProjectDecision("C/ Olmo 24", Action.MERGE, Optional.of(existing))));

        assertThat(orgs.listProjects(company)).hasSize(1);
        assertThat(done.get(0).state()).isEqualTo(DecisionState.CONFIRMED);
        assertThat(done.get(0).projectId()).contains(existing);
    }

    @Test
    void rejectingCreatesNothing() {
        importText("Calle Olmo 24");

        List<ProjectCandidate> done = confirm.execute(admin, company, batch.id(), List.of(
                new ProjectDecision("Calle Olmo 24", Action.REJECT, Optional.empty())));

        assertThat(orgs.listProjects(company)).isEmpty();
        assertThat(done.get(0).state()).isEqualTo(DecisionState.REJECTED);
    }

    @Test
    void aBadDecisionChangesNothingNotEvenTheValidOnes() {
        importText("Calle Olmo 24\nCalle Haya 2");
        new CreateProject(orgs, ids, new InMemoryAuditLog()).execute(admin, company, "Calle Haya 2");
        ProjectDecision good = new ProjectDecision("Calle Olmo 24", Action.CREATE, Optional.empty());

        // el nombre ya es un proyecto: hay que unirlo, no crearlo
        assertThatThrownBy(() -> confirm.execute(admin, company, batch.id(),
                List.of(good, new ProjectDecision("Calle Haya 2", Action.CREATE, Optional.empty()))))
                .isInstanceOf(ConflictException.class);
        // un candidato que no existe
        assertThatThrownBy(() -> confirm.execute(admin, company, batch.id(),
                List.of(good, new ProjectDecision("Calle Pino 7", Action.REJECT, Optional.empty()))))
                .isInstanceOf(NotFoundException.class);
        // unir sin proyecto, o con uno de otra empresa
        assertThatThrownBy(() -> confirm.execute(admin, company, batch.id(),
                List.of(good, new ProjectDecision("Calle Haya 2", Action.MERGE, Optional.empty()))))
                .isInstanceOf(ValidationException.class);
        ProjectId foreign = Fixtures.project(orgs, other);
        assertThatThrownBy(() -> confirm.execute(admin, company, batch.id(),
                List.of(good, new ProjectDecision("Calle Haya 2", Action.MERGE, Optional.of(foreign)))))
                .isInstanceOf(NotFoundException.class);
        // el mismo candidato dos veces
        assertThatThrownBy(() -> confirm.execute(admin, company, batch.id(), List.of(good, good)))
                .isInstanceOf(ValidationException.class);

        assertThat(orgs.listProjects(company)).extracting(Project::name).containsExactly("Calle Haya 2");
        assertThat(intake.candidates(company, batch.id())).extracting(ProjectCandidate::state)
                .containsOnly(DecisionState.PROPOSED);
    }

    @Test
    void aDecidedCandidateCannotBeDecidedAgain() {
        importText("Calle Olmo 24");
        ProjectDecision reject = new ProjectDecision("Calle Olmo 24", Action.REJECT, Optional.empty());
        confirm.execute(admin, company, batch.id(), List.of(reject));

        assertThatThrownBy(() -> confirm.execute(admin, company, batch.id(), List.of(reject)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void anAdminOfAnotherCompanyAndAWorkerAreRefusedAndNothingChanges() {
        importText("Calle Olmo 24");
        Actor foreignAdmin = Actors.companyAdmin(other);
        Actor worker = Actors.worker(company);
        byte[] bytes = "Calle Haya 2".getBytes(StandardCharsets.UTF_8);
        List<ProjectDecision> decisions = List.of(new ProjectDecision("Calle Olmo 24", Action.CREATE, Optional.empty()));

        for (Actor intruder : List.of(foreignAdmin, worker)) {
            assertThatThrownBy(() -> importList.execute(intruder, company, batch.id(), bytes))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> list.execute(intruder, company, batch.id())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> list.discover(intruder, company, batch.id())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> confirm.execute(intruder, company, batch.id(), decisions))
                    .isInstanceOf(ForbiddenException.class);
        }
        // el lote de otra empresa, pedido con la propia, no existe
        assertThatThrownBy(() -> list.execute(foreignAdmin, other, batch.id())).isInstanceOf(NotFoundException.class);
        assertThat(orgs.listProjects(company)).isEmpty();
        assertThat(names(intake.candidates(company, batch.id()))).containsExactly("Calle Olmo 24");
    }

    @Test
    void discoveryNeverReadsThePendingTextsOfAnotherCompany() {
        Document foreign = Fixtures.readyDocument(docs, other, "Comunidad: Calle Haya 2");
        docs.save(foreign.withIntake(batch.id(), "x.pdf"));

        assertThat(list.discover(admin, company, batch.id())).isEmpty();
    }

    private IntakeBatch reading() {
        batch = batch.startReading();
        intake.save(batch);
        return batch;
    }

    private IntakeBatch stored() {
        return intake.find(company, batch.id()).orElseThrow();
    }

    @Test
    void theListIsClosedOnlyWhenNothingIsLeftToDecideAndKeepsWhoAndWhen() {
        importText("Calle Olmo 24\nCalle Haya 2");
        reading();
        confirm.execute(admin, company, batch.id(), List.of(new ProjectDecision("Calle Olmo 24", Action.CREATE,
                Optional.empty())));

        assertThatThrownBy(() -> closeList.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class)
                .hasMessage("Falta 1 candidato por decidir");
        assertThat(stored().projectListClosed()).isFalse();

        confirm.execute(admin, company, batch.id(), List.of(new ProjectDecision("Calle Haya 2", Action.REJECT,
                Optional.empty())));
        closeList.execute(admin, company, batch.id());

        assertThat(stored().projectListClosedBy()).contains(admin.userId());
        assertThat(stored().projectListClosedAt()).contains(clock.instant());
    }

    @Test
    void theListIsNotClosedWhileDocumentsAreStillBeingRead() {
        reading();
        for (int i = 0; i < 2; i++) {
            Document d = Fixtures.document(docs, company, com.e2d.odin.domain.document.DocumentStatus.PROCESSING);
            docs.save(d.withIntake(batch.id(), "a/" + i + ".pdf"));
        }

        assertThatThrownBy(() -> closeList.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class)
                .hasMessage("Aún se están leyendo 2 documentos");
    }

    @Test
    void withoutAReaderTheScansWaitingForItDoNotHoldTheList() {
        reading();
        Document scan = Fixtures.document(docs, company, com.e2d.odin.domain.document.DocumentStatus.PENDING_READER);
        docs.save(scan.withIntake(batch.id(), "a/escaneo.pdf"));
        CloseProjectList withoutReader = new CloseProjectList(intake, docs, list, new InstallationSettings("t", false),
                clock);

        assertThatThrownBy(() -> closeList.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class)
                .hasMessage("Aún se está leyendo 1 documento");
        withoutReader.execute(admin, company, batch.id());

        assertThat(stored().projectListClosed()).isTrue();
    }

    @Test
    void closingRunsTheDiscoveryFirstSoNewCandidatesMustBeDecided() {
        reading();
        pendingDocument("Comunidad: Calle Haya 2");
        pendingDocument("Comunidad: Calle Olmo 24");

        assertThatThrownBy(() -> closeList.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class)
                .hasMessage("Faltan 2 candidatos por decidir");
        assertThat(names(intake.candidates(company, batch.id()))).containsExactlyInAnyOrder("Calle Haya 2",
                "Calle Olmo 24");
    }

    @Test
    void aListWithNoConfirmedProjectCanBeClosed() {
        importText("Calle Olmo 24");
        reading();
        confirm.execute(admin, company, batch.id(), List.of(new ProjectDecision("Calle Olmo 24", Action.REJECT,
                Optional.empty())));

        closeList.execute(admin, company, batch.id());

        assertThat(stored().projectListClosed()).isTrue();
    }

    @Test
    void theListIsClosedOnlyInReadingAndOnlyOnce() {
        assertThatThrownBy(() -> closeList.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class);
        reading();
        closeList.execute(admin, company, batch.id());
        assertThatThrownBy(() -> closeList.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void withTheListClosedOrTheBatchInReviewTheCandidatesCannotChange() {
        importText("Calle Olmo 24");
        reading();
        confirm.execute(admin, company, batch.id(), List.of(new ProjectDecision("Calle Olmo 24", Action.REJECT,
                Optional.empty())));
        closeList.execute(admin, company, batch.id());
        assertCandidatesFrozen();

        intake.save(stored().finishReading());
        assertCandidatesFrozen();
        intake.save(stored().close(clock.instant()));
        assertCandidatesFrozen();
        assertThat(list.execute(admin, company, batch.id())).hasSize(1);
    }

    private void assertCandidatesFrozen() {
        byte[] bytes = "Calle Pino 7".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> importList.execute(admin, company, batch.id(), bytes))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> list.discover(admin, company, batch.id())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> confirm.execute(admin, company, batch.id(), List.of(new ProjectDecision(
                "Calle Olmo 24", Action.CREATE, Optional.empty())))).isInstanceOf(ConflictException.class);
    }

    @Test
    void closingTheListIsRefusedToAnAdminOfAnotherCompanyAndToAWorker() {
        reading();

        for (Actor intruder : List.of(Actors.companyAdmin(other), Actors.worker(company))) {
            assertThatThrownBy(() -> closeList.execute(intruder, company, batch.id()))
                    .isInstanceOf(ForbiddenException.class);
        }
        assertThatThrownBy(() -> closeList.execute(Actors.companyAdmin(other), other, batch.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(stored().projectListClosed()).isFalse();
    }
}
