package com.e2d.odin.application.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryIntakeRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.port.ProjectProposer;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.intake.CandidateOrigin;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.intake.ProposalSource;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassifyIntakeTest {

    private static final String OLMO = "Calle Olmo 24";
    private static final String HAYA = "Calle Haya 2";
    private static final String OLMO_TEXT = "Comunidad: Comunidad de Propietarios Calle Olmo 24\nActa de la junta de marzo";

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryIntakeRepository intake = new InMemoryIntakeRepository();
    private final SequenceIdGenerator ids = new SequenceIdGenerator();
    private final List<String> asked = new ArrayList<>();
    private Optional<String> modelAnswer = Optional.empty();
    private boolean modelFails;
    private final ProjectProposer model = (projects, text) -> {
        asked.add(text);
        if (modelFails) {
            throw new IllegalStateException("el modelo se cae");
        }
        return modelAnswer;
    };
    private final CompanyId company = Fixtures.company(orgs);
    private final CompanyId other = Fixtures.company(orgs);
    private IntakeBatch batch;
    private ProjectId olmoId;
    private ProjectId hayaId;

    @BeforeEach
    void openReadingBatch() {
        batch = new StartIntake(intake, orgs, ids, new MutableClock())
                .execute(Actors.companyAdmin(company), company, IntakeChannel.PANEL).startReading();
        intake.save(batch);
        olmoId = Fixtures.project(orgs, company);
        hayaId = Fixtures.project(orgs, company);
    }

    private ClassifyIntake classify(boolean withModel) {
        return new ClassifyIntake(intake, docs, withModel ? Optional.of(model) : Optional.empty(), ids);
    }

    private void confirmed(String name, ProjectId project) {
        intake.save(new ProjectCandidate(company, batch.id(), name, CandidateOrigin.IMPORTED, 0,
                DecisionState.CONFIRMED, Optional.of(project)));
    }

    private Document pending(String path, String text) {
        return pending(batch, path, text, "stub", DocumentStatus.READY);
    }

    private Document pending(IntakeBatch in, String path, String text, String reader, DocumentStatus status) {
        Document d = Fixtures.document(docs, in.companyId(), status).withIntake(in.id(), path);
        docs.save(d);
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, text, reader, "1")));
        return d;
    }

    private List<IntakeProposal> proposals(ProposalKind kind) {
        return intake.proposals(company, batch.id()).stream().filter(p -> p.kind() == kind).toList();
    }

    /** Falla al guardar las propuestas de un documento: el repositorio no guarda ninguna de esa tanda. */
    private final class FailingOnce extends InMemoryIntakeRepository {
        boolean armed;
        int writes;

        @Override
        public synchronized void save(IntakeProposal proposal) {
            if (armed && ++writes == 2) {
                armed = false;
                throw new IllegalStateException("se cae a mitad");
            }
            super.save(proposal);
        }

        @Override
        public synchronized void addProposals(List<IntakeProposal> added) {
            if (armed && added.size() >= 2) {
                armed = false;
                throw new IllegalStateException("se cae a mitad");
            }
            super.addProposals(added);
        }
    }

    private IntakeState state() {
        return intake.find(company, batch.id()).orElseThrow().state();
    }

    @Test
    void onlyReadyPendingDocumentsWithoutProposalsAreClassified() {
        confirmed(OLMO, olmoId);
        Document ready = pending("a/uno.pdf", OLMO_TEXT);
        pending(batch, "a/dos.pdf", OLMO_TEXT, "stub", DocumentStatus.FAILED);
        Document done = pending("a/tres.pdf", OLMO_TEXT);
        intake.save(new IntakeProposal(ids.newId(), company, batch.id(), done.id(), ProposalKind.PROJECT,
                Optional.of(olmoId), Optional.empty(), Optional.empty(), ProposalConfidence.HIGH, ProposalSource.RULES,
                "ya", DecisionState.PROPOSED, Optional.empty(), Optional.empty()));
        Document sinceDecided = Fixtures.readyDocument(docs, company, OLMO_TEXT);

        classify(false).execute();
        classify(false).execute();

        assertThat(proposals(ProposalKind.PROJECT)).extracting(IntakeProposal::documentId)
                .containsExactlyInAnyOrder(ready.id(), done.id()).doesNotContain(sinceDecided.id());
    }

    @Test
    void rulesGiveAHighProjectWithTheLiteralReasonAndTheModelIsNotAsked() {
        confirmed(OLMO, olmoId);
        confirmed(HAYA, hayaId);
        Document d = pending("a/uno.pdf", OLMO_TEXT);

        classify(true).execute();

        IntakeProposal p = proposals(ProposalKind.PROJECT).get(0);
        assertThat(p.documentId()).isEqualTo(d.id());
        assertThat(p.targetProjectId()).contains(olmoId);
        assertThat(p.confidence()).isEqualTo(ProposalConfidence.HIGH);
        assertThat(p.source()).isEqualTo(ProposalSource.RULES);
        assertThat(OLMO_TEXT).contains(p.reason());
        assertThat(p.reason()).isNotBlank();
        assertThat(p.state()).isEqualTo(DecisionState.PROPOSED);
        assertThat(asked).isEmpty();
    }

    @Test
    void theFolderHintsTheProjectAndItsReasonIsTheFolder() {
        confirmed(OLMO, olmoId);
        pending("Calle Olmo 24/uno.pdf", "Un papel sin nombre de proyecto");

        classify(false).execute();

        IntakeProposal p = proposals(ProposalKind.PROJECT).get(0);
        assertThat(p.source()).isEqualTo(ProposalSource.FOLDER);
        assertThat(p.reason()).isEqualTo("Calle Olmo 24");
    }

    @Test
    void theModelIsAskedOnlyWhenRulesDoNotGiveHighAndAnswersWithLowAndTheLiteralStartOfTheText() {
        confirmed(OLMO, olmoId);
        pending("a/uno.pdf", "Carta sin proyecto en el texto");
        modelAnswer = Optional.of(OLMO);

        classify(true).execute();

        IntakeProposal p = proposals(ProposalKind.PROJECT).get(0);
        assertThat(p.targetProjectId()).contains(olmoId);
        assertThat(p.confidence()).isEqualTo(ProposalConfidence.LOW);
        assertThat(p.source()).isEqualTo(ProposalSource.MODEL);
        assertThat(p.reason()).isEqualTo("Carta sin proyecto en el texto");
        assertThat(asked).hasSize(1);
    }

    @Test
    void theModelIsNotAskedWhileTheReaderQueueHasDocumentsAndTheBatchWaits() {
        confirmed(OLMO, olmoId);
        pending("a/uno.pdf", "Carta sin proyecto en el texto");
        Fixtures.document(docs, company, DocumentStatus.PENDING_READER);
        modelAnswer = Optional.of(OLMO);

        classify(true).execute();

        assertThat(asked).isEmpty();
        assertThat(proposals(ProposalKind.PROJECT)).isEmpty();
        assertThat(state()).isEqualTo(IntakeState.READING);
    }

    @Test
    void theReaderQueueOfAnotherCompanyDoesNotHoldTheModel() {
        confirmed(OLMO, olmoId);
        pending("a/uno.pdf", "Carta sin proyecto en el texto");
        Fixtures.document(docs, other, DocumentStatus.READER_PROCESSING);
        modelAnswer = Optional.of(OLMO);

        classify(true).execute();

        assertThat(asked).hasSize(1);
        assertThat(state()).isEqualTo(IntakeState.IN_REVIEW);
    }

    @Test
    void aFailingModelProposesNothingAndDoesNotStopTheBatch() {
        confirmed(OLMO, olmoId);
        pending("a/uno.pdf", "Carta sin proyecto en el texto");
        modelFails = true;

        classify(true).execute();

        assertThat(proposals(ProposalKind.PROJECT)).isEmpty();
        assertThat(state()).isEqualTo(IntakeState.IN_REVIEW);
    }

    @Test
    void aModelAnswerOutsideTheConfirmedListIsDiscarded() {
        confirmed(OLMO, olmoId);
        pending("a/uno.pdf", "Carta sin proyecto en el texto");
        modelAnswer = Optional.of("Calle Inventada 1");

        classify(true).execute();

        assertThat(proposals(ProposalKind.PROJECT)).isEmpty();
    }

    @Test
    void withoutAConfirmedListNothingIsProposedAndTheBatchStaysInReadingUntilItIsConfirmed() {
        intake.save(new ProjectCandidate(company, batch.id(), OLMO, CandidateOrigin.DISCOVERED, 1,
                DecisionState.PROPOSED, Optional.empty()));
        pending("a/uno.pdf", OLMO_TEXT);
        pending("a/caso.pdf", "FICHA DE CASO\nCaso 2024-342\nGoteras en " + OLMO);

        classify(true).execute();

        assertThat(intake.proposals(company, batch.id())).isEmpty();
        assertThat(asked).isEmpty();
        assertThat(state()).isEqualTo(IntakeState.READING);

        confirmed(OLMO, olmoId);
        classify(true).execute();

        assertThat(proposals(ProposalKind.PROJECT)).hasSize(2);
        assertThat(proposals(ProposalKind.CASE_MARK)).hasSize(1);
        assertThat(state()).isEqualTo(IntakeState.IN_REVIEW);
    }

    @Test
    void anAmbiguousDocumentGetsOneLowProposalPerCandidateEachWithItsOwnReasonAndTheModelIsNotAsked() {
        confirmed(OLMO, olmoId);
        confirmed(HAYA, hayaId);
        String text = "Informe general\nUna linea\nOtra linea\nObras en " + OLMO + " del bloque\nY tambien en " + HAYA + " del patio";
        Document d = pending("a/uno.pdf", text);
        modelAnswer = Optional.of(OLMO);

        classify(true).execute();

        List<IntakeProposal> ps = proposals(ProposalKind.PROJECT);
        assertThat(ps).extracting(IntakeProposal::documentId).containsOnly(d.id());
        assertThat(ps).extracting(p -> p.targetProjectId().orElseThrow()).containsExactlyInAnyOrder(olmoId, hayaId);
        assertThat(ps).allSatisfy(p -> {
            assertThat(p.confidence()).isEqualTo(ProposalConfidence.LOW);
            assertThat(p.source()).isEqualTo(ProposalSource.RULES);
        });
        assertThat(ps).filteredOn(p -> p.targetProjectId().get().equals(olmoId)).singleElement()
                .satisfies(p -> assertThat(p.reason()).contains(OLMO).doesNotContain(HAYA));
        assertThat(ps).filteredOn(p -> p.targetProjectId().get().equals(hayaId)).singleElement()
                .satisfies(p -> assertThat(p.reason()).contains(HAYA).doesNotContain(OLMO));
        assertThat(asked).isEmpty();
    }

    @Test
    void aCaseFileGetsACaseMarkWithItsNumberAndLiteralReason() {
        confirmed(OLMO, olmoId);
        Document caso = pending("a/caso.pdf", "FICHA DE CASO\nCaso 2024-342\nGoteras en " + OLMO);

        classify(false).execute();

        IntakeProposal p = proposals(ProposalKind.CASE_MARK).get(0);
        assertThat(p.documentId()).isEqualTo(caso.id());
        assertThat(p.caseNumber()).contains("2024-342");
        assertThat(p.reason()).isEqualTo("FICHA DE CASO");
        assertThat(p.confidence()).isEqualTo(ProposalConfidence.HIGH);
    }

    @Test
    void aDocumentThatQuotesTheCaseNumberOfTheSameProjectGetsACaseLinkWithTheCitation() {
        confirmed(OLMO, olmoId);
        confirmed(HAYA, hayaId);
        Document caso = pending("a/caso.pdf", "FICHA DE CASO\nCaso 2024-342\nGoteras en " + OLMO);
        Document carta = pending("a/carta.pdf", OLMO_TEXT + "\nVer el Caso 2024-342 abierto.");
        pending("b/otra.pdf", "Comunidad: Comunidad de Propietarios " + HAYA + "\nVer el Caso 2024-342 abierto.");

        classify(false).execute();

        List<IntakeProposal> links = proposals(ProposalKind.CASE_LINK);
        assertThat(links).hasSize(1);
        assertThat(links.get(0).documentId()).isEqualTo(carta.id());
        assertThat(links.get(0).targetDocumentId()).contains(caso.id());
        assertThat(links.get(0).caseNumber()).contains("2024-342");
        assertThat(links.get(0).reason()).isEqualTo("Caso 2024-342");
    }

    @Test
    void aScanOfTheSameProjectIsProposedAsSameAsScanWithTheOriginalAsTarget() {
        confirmed(OLMO, olmoId);
        String body = OLMO_TEXT + "\nIncluye una derrama de 1.200 euros acordada el 12 de marzo de 2024 para el "
                + "arreglo del tejado del bloque segundo con la empresa constructora local";
        Document original = pending(batch, "a/acta.pdf", body, "pdfbox", DocumentStatus.READY);
        Document scan = pending(batch, "a/acta-escaneada.pdf", body, "unlimited-ocr-2", DocumentStatus.READY);

        classify(false).execute();

        IntakeProposal p = proposals(ProposalKind.SAME_AS_SCAN).get(0);
        assertThat(p.documentId()).isEqualTo(scan.id());
        assertThat(p.targetDocumentId()).contains(original.id());
        assertThat(p.reason()).isNotBlank();
        assertThat(body).contains(p.reason());
    }

    @Test
    void similarDocumentsOfDifferentProjectsAreNeverProposedAsDuplicates() {
        confirmed(OLMO, olmoId);
        confirmed(HAYA, hayaId);
        String tail = "\nIncluye una derrama de 1.200 euros acordada el 12 de marzo de 2024 para el "
                + "arreglo del tejado del bloque segundo con la empresa constructora local";
        pending(batch, "a/acta.pdf", OLMO_TEXT + tail, "pdfbox", DocumentStatus.READY);
        pending(batch, "b/acta.pdf", OLMO_TEXT.replace(OLMO, HAYA) + tail, "unlimited-ocr-2", DocumentStatus.READY);

        classify(false).execute();

        assertThat(proposals(ProposalKind.SAME_AS_SCAN)).isEmpty();
        assertThat(proposals(ProposalKind.SUPERSEDES)).isEmpty();
    }

    @Test
    void thereIsNoCompanyWideProposalWithoutAnExplicitSignal() {
        confirmed(OLMO, olmoId);
        pending("a/norma.pdf", "NORMA INTERNA\nAprobación de gastos de la empresa");

        classify(true).execute();

        assertThat(proposals(ProposalKind.COMPANY_WIDE)).isEmpty();
        assertThat(proposals(ProposalKind.PROJECT)).isEmpty();
    }

    @Test
    void everyProposalCarriesALiteralReasonOfAtMost300Characters() {
        confirmed(OLMO, olmoId);
        pending("a/uno.pdf", "Comunidad: Comunidad de Propietarios " + OLMO + " " + "x".repeat(400));
        pending("a/dos.pdf", "FICHA DE CASO\nCaso 2024-342");
        pending("a/tres.pdf", "z".repeat(500));
        modelAnswer = Optional.of(OLMO);

        classify(true).execute();

        assertThat(intake.proposals(company, batch.id())).isNotEmpty().allSatisfy(p -> {
            assertThat(p.reason()).isNotBlank();
            assertThat(p.reason().length()).isLessThanOrEqualTo(300);
        });
    }

    @Test
    void whenNothingIsLeftToReadOrProposeTheBatchGoesToInReviewOnceAndDocumentsAreUntouched() {
        confirmed(OLMO, olmoId);
        Document d = pending("a/uno.pdf", OLMO_TEXT);

        classify(false).execute();
        classify(false).execute();

        assertThat(state()).isEqualTo(IntakeState.IN_REVIEW);
        assertThat(docs.all()).filteredOn(x -> x.id().equals(d.id())).singleElement().satisfies(x -> {
            assertThat(x.intakePending()).isTrue();
            assertThat(x.projectId()).isEmpty();
        });
        assertThat(proposals(ProposalKind.PROJECT)).hasSize(1);
    }

    @Test
    void theBatchStaysInReadingWhileAnyDocumentIsStillBeingRead() {
        confirmed(OLMO, olmoId);
        pending("a/uno.pdf", OLMO_TEXT);
        pending(batch, "a/escaneo.pdf", "", "stub", DocumentStatus.PENDING_READER);

        classify(false).execute();

        assertThat(state()).isEqualTo(IntakeState.READING);
        assertThat(intake.proposals(company, batch.id())).isEmpty();
    }

    @Test
    void withoutAModelTheBatchStillFinishes() {
        confirmed(OLMO, olmoId);
        pending("a/uno.pdf", "Carta sin proyecto en el texto");

        classify(false).execute();

        assertThat(state()).isEqualTo(IntakeState.IN_REVIEW);
        assertThat(intake.proposals(company, batch.id())).isEmpty();
    }

    @Test
    void aBatchOfAnotherCompanyIsClassifiedOnlyWithItsOwnDocumentsAndList() {
        confirmed(OLMO, olmoId);
        IntakeBatch otherBatch = new StartIntake(intake, orgs, ids, new MutableClock())
                .execute(Actors.companyAdmin(other), other, IntakeChannel.PANEL).startReading();
        intake.save(otherBatch);
        Document mine = pending("a/uno.pdf", OLMO_TEXT);
        Document theirs = pending(otherBatch, "a/uno.pdf", OLMO_TEXT, "stub", DocumentStatus.READY);

        classify(false).execute();

        assertThat(intake.proposals(company, batch.id())).extracting(IntakeProposal::documentId)
                .containsOnly(mine.id());
        assertThat(intake.proposals(other, otherBatch.id())).isEmpty();
        assertThat(theirs.companyId()).isEqualTo(other);
    }

    @Test
    void aFailureWhileSavingADocumentLeavesItWithNoProposalsAndTheNextPassProposesItFully() {
        FailingOnce failing = new FailingOnce();
        batch = new StartIntake(failing, orgs, ids, new MutableClock())
                .execute(Actors.companyAdmin(company), company, IntakeChannel.PANEL).startReading();
        failing.save(batch);
        failing.save(new ProjectCandidate(company, batch.id(), OLMO, CandidateOrigin.IMPORTED, 0,
                DecisionState.CONFIRMED, Optional.of(olmoId)));
        Document caso = pending("a/caso.pdf", "FICHA DE CASO\nCaso 2024-342\nGoteras en " + OLMO);
        failing.armed = true;
        ClassifyIntake job = new ClassifyIntake(failing, docs, Optional.empty(), ids);

        assertThatThrownBy(job::execute).isInstanceOf(IllegalStateException.class);

        assertThat(failing.proposals(company, batch.id())).isEmpty();
        assertThat(failing.find(company, batch.id()).orElseThrow().state()).isEqualTo(IntakeState.READING);

        job.execute();

        assertThat(failing.proposals(company, batch.id())).extracting(IntakeProposal::kind)
                .containsExactlyInAnyOrder(ProposalKind.PROJECT, ProposalKind.CASE_MARK);
        assertThat(failing.proposals(company, batch.id())).extracting(IntakeProposal::documentId).containsOnly(caso.id());
    }

    @Test
    void documentsWithoutAProposedProjectAreNeverComparedAmongThemselves() {
        confirmed(OLMO, olmoId);
        String body = "Acta de la junta\nIncluye una derrama de 1.200 euros acordada el 12 de marzo de 2024 para el "
                + "arreglo del tejado del bloque segundo con la empresa constructora local";
        pending(batch, "a/acta.pdf", body, "pdfbox", DocumentStatus.READY);
        pending(batch, "a/acta-escaneada.pdf", body, "unlimited-ocr-2", DocumentStatus.READY);

        classify(false).execute();

        assertThat(proposals(ProposalKind.SAME_AS_SCAN)).isEmpty();
        assertThat(proposals(ProposalKind.SUPERSEDES)).isEmpty();
        assertThat(proposals(ProposalKind.PROJECT)).isEmpty();
    }
}
