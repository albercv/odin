package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.IntakeDocument;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.port.ProjectProposer;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.domain.intake.CaseFileRules;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.DuplicateFinder;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeCounts;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.intake.ProjectMatch;
import com.e2d.odin.domain.intake.ProjectRules;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.intake.ProposalSource;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Trabajo periódico de la primera carga: cuando un lote en lectura ya no tiene nada por leer, escribe las propuestas de
 * proyecto, ficha de caso, enlaces y duplicados de sus documentos y lo pasa a IN_REVIEW. Solo escribe propuestas (D2):
 * ningún documento cambia hasta que una persona las confirme. Cada propuesta lleva su cita literal (D-008).
 * <p>
 * Primero las reglas; el modelo local solo para lo que no dé HIGH, y solo con la cola del lector de la empresa vacía,
 * para que el OCR y el modelo no estén cargados a la vez. Si hay modelo y la cola está ocupada, el lote espera.
 * Un lote no se clasifica hasta que el administrador da la lista de proyectos por terminada (D6, spec 4.4); con
 * ninguno confirmado no hay propuestas de proyecto y sí el resto. Varios candidatos sin desempate dan una propuesta LOW
 * por candidato. No hay propuesta COMPANY_WIDE: las reglas del
 * estudio no tienen ninguna señal explícita de «de toda la empresa», así que lo decide la persona en la bandeja.
 */
public class ClassifyIntake {

    private static final int REASON_MAX = IntakeProposal.MAX_REASON;
    private static final System.Logger LOG = System.getLogger(ClassifyIntake.class.getName());

    private final IntakeRepository intake;
    private final DocumentRepository documents;
    private final Optional<ProjectProposer> proposer;
    private final IdGenerator ids;
    private final InstallationSettings installation;
    private final Clock clock;
    private final Consumer<String> timingsLog;

    public ClassifyIntake(IntakeRepository intake, DocumentRepository documents, Optional<ProjectProposer> proposer,
            IdGenerator ids, InstallationSettings installation, Clock clock) {
        this(intake, documents, proposer, ids, installation, clock, line -> LOG.log(System.Logger.Level.INFO, line));
    }

    ClassifyIntake(IntakeRepository intake, DocumentRepository documents, Optional<ProjectProposer> proposer,
            IdGenerator ids, InstallationSettings installation, Clock clock, Consumer<String> timingsLog) {
        this.timingsLog = timingsLog;
        this.intake = intake;
        this.documents = documents;
        this.proposer = proposer;
        this.ids = ids;
        this.installation = installation;
        this.clock = clock;
    }

    /** Clasifica los lotes en lectura y cierra los que ya no tienen nada por decidir (si no, no tendrían salida). */
    public void execute() {
        for (IntakeBatch batch : intake.findReadingOrInReview()) {
            if (batch.state() == IntakeState.READING) {
                classify(batch);
            } else {
                IntakeBatches.closeIfDone(intake, documents, batch, clock.instant());
            }
        }
    }

    /** Lo que se sabe de un lote durante una pasada. */
    private static final class Run {
        final IntakeBatch batch;
        final Map<String, ProjectId> confirmed = new LinkedHashMap<>();
        final Map<DocumentId, String> projectOf = new HashMap<>();
        final List<IntakeProposal> made = new ArrayList<>();
        boolean modelAllowed;
        long rulesMs;
        long modelMs;
        long modelCalls;
        long caseFileMs;

        Run(IntakeBatch batch) {
            this.batch = batch;
        }

        CompanyId company() {
            return batch.companyId();
        }
    }

    private void classify(IntakeBatch batch) {
        Run run = new Run(batch);
        IntakeCounts counts = IntakeBatches.counts(documents, installation, run.company(), batch.id());
        if (counts.pending() == 0) {
            finish(batch); // vacío o todo retirado: no hay nada que clasificar ni que decidir
            return;
        }
        if (!batch.projectListClosed() || counts.reading() > 0) {
            return;
        }
        // sin lector OCR no hay cola del lector que esperar: lo que espera al lector no se está leyendo
        boolean readerBusy = installation.scannedDocumentsSupported() && documents.readerQueueBusy(run.company());
        run.modelAllowed = proposer.isPresent() && !readerBusy;
        if (proposer.isPresent() && !run.modelAllowed) {
            return;
        }
        intake.candidates(run.company(), batch.id()).stream().filter(c -> c.state() == DecisionState.CONFIRMED)
                .forEach(c -> run.confirmed.put(c.name(), c.projectId().orElseThrow()));
        measuredPass(run);
        finish(batch);
    }

    /** Hace la pasada y deja una línea con lo que tardó cada paso (el modelo aparte de las reglas). */
    private void measuredPass(Run run) {
        long start = clock.millis();
        List<IntakeProposal> existing = intake.proposals(run.company(), run.batch.id());
        rememberUnambiguous(run, existing.stream().filter(p -> p.kind() == ProposalKind.PROJECT).toList());
        Set<DocumentId> done = existing.stream().map(IntakeProposal::documentId).collect(Collectors.toSet());
        List<IntakeDocument> all = documents.intakeReadyDocuments(run.company(), run.batch.id());
        List<IntakeDocument> todo = all.stream().filter(d -> !done.contains(d.id())).toList();
        todo.forEach(d -> proposeFor(run, d));
        long t = clock.millis();
        links(run, all);
        long linksMs = clock.millis() - t;
        t = clock.millis();
        duplicates(run, all, existing);
        long duplicatesMs = clock.millis() - t;
        t = clock.millis();
        saveNew(run, existing, all);
        long saveMs = clock.millis() - t;
        if (!todo.isEmpty()) {
            timingsLog.accept(String.format("intake-classify batch=%s docs=%d rules_ms=%d model_ms=%d model_calls=%d "
                    + "casefile_ms=%d links_ms=%d duplicates_ms=%d save_ms=%d total_ms=%d", run.batch.id().value(),
                    todo.size(), run.rulesMs, run.modelMs, run.modelCalls, run.caseFileMs, linksMs, duplicatesMs, saveMs,
                    clock.millis() - start));
        }
    }

    private void finish(IntakeBatch batch) {
        IntakeBatch inReview = batch.finishReading();
        intake.save(inReview);
        IntakeBatches.closeIfDone(intake, documents, inReview, clock.instant());
    }

    /** El proyecto propuesto de un documento solo cuenta para enlaces y duplicados si es uno solo. */
    private void rememberUnambiguous(Run run, List<IntakeProposal> projects) {
        projects.stream().collect(Collectors.groupingBy(IntakeProposal::documentId)).values().stream()
                .filter(group -> group.size() == 1).map(group -> group.get(0))
                .forEach(p -> p.targetProjectId().ifPresent(id -> run.projectOf.put(p.documentId(), id.value().toString())));
    }

    private void saveNew(Run run, List<IntakeProposal> existing, List<IntakeDocument> all) {
        Set<String> known = existing.stream().map(ClassifyIntake::key).collect(Collectors.toSet());
        List<List<IntakeProposal>> groups = run.made.stream().filter(p -> known.add(key(p)))
                .collect(Collectors.groupingBy(IntakeProposal::documentId, LinkedHashMap::new, Collectors.toList()))
                .values().stream().toList();
        Map<DocumentId, String> types = new HashMap<>();
        all.forEach(d -> CaseFileRules.documentType(d.text()).ifPresent(t -> types.put(d.id(), t)));
        intake.saveClassification(run.company(), run.batch.id(), types, groups);
    }

    private static String key(IntakeProposal p) {
        return p.documentId().value() + "/" + p.kind() + "/" + p.targetDocumentId().map(d -> d.value().toString()).orElse("")
                + "/" + p.targetProjectId().map(x -> x.value().toString()).orElse("");
    }

    private void proposeFor(Run run, IntakeDocument d) {
        long start = clock.millis();
        long modelBefore = run.modelMs;
        List<IntakeProposal> projects = projectProposals(run, d);
        run.rulesMs += clock.millis() - start - (run.modelMs - modelBefore);
        run.made.addAll(projects);
        rememberUnambiguous(run, projects);
        start = clock.millis();
        CaseFileRules.CaseMark mark = CaseFileRules.detect(d.text());
        run.caseFileMs += clock.millis() - start;
        if (mark.caseFile() && !mark.reason().isBlank()) {
            run.made.add(proposal(run, d.id(), ProposalKind.CASE_MARK, Optional.empty(), Optional.empty(),
                    mark.number(), ProposalConfidence.HIGH, ProposalSource.RULES, mark.reason()));
        }
    }

    /**
     * Un HIGH de las reglas; si no, varios candidatos sin desempate (uno LOW por candidato, con su cita); si las reglas
     * no encuentran ningún proyecto, el modelo (LOW); y, con un ganador LOW, ese.
     */
    private List<IntakeProposal> projectProposals(Run run, IntakeDocument d) {
        List<String> names = List.copyOf(run.confirmed.keySet());
        if (names.isEmpty()) {
            return List.of();
        }
        ProjectMatch match = ProjectRules.match(d.text(), names, folderOf(d));
        boolean usable = match.project().isPresent() && !match.reason().isBlank();
        if (usable && match.confidence() == ProposalConfidence.HIGH) {
            return List.of(project(run, d, match.project().get(), ProposalConfidence.HIGH, match.source(),
                    match.reason()));
        }
        if (match.project().isEmpty() && match.candidates().size() > 1) {
            return match.candidates().stream().map(c -> candidateProposal(run, d, c)).flatMap(Optional::stream)
                    .toList();
        }
        Optional<String> byModel = run.modelAllowed && match.candidates().isEmpty() ? ask(run, names, d.text())
                : Optional.empty();
        if (byModel.isPresent() && !beginning(d.text()).isBlank()) {
            return List.of(project(run, d, byModel.get(), ProposalConfidence.LOW, ProposalSource.MODEL,
                    beginning(d.text())));
        }
        return usable ? List.of(project(run, d, match.project().get(), ProposalConfidence.LOW, match.source(),
                match.reason())) : List.of();
    }

    /** La cita propia de un candidato: la de las reglas con ese nombre como única lista. */
    private Optional<IntakeProposal> candidateProposal(Run run, IntakeDocument d, String name) {
        String reason = ProjectRules.match(d.text(), List.of(name), Optional.empty()).reason();
        return reason.isBlank() ? Optional.empty()
                : Optional.of(project(run, d, name, ProposalConfidence.LOW, ProposalSource.RULES, reason));
    }

    /** El modelo no debería lanzar, pero un fallo suyo no tumba el lote: no propone nada. */
    private Optional<String> ask(Run run, List<String> names, String text) {
        long start = clock.millis();
        run.modelCalls++;
        try {
            return proposer.orElseThrow().propose(names, text).filter(names::contains);
        } catch (RuntimeException e) {
            return Optional.empty();
        } finally {
            run.modelMs += clock.millis() - start;
        }
    }

    private IntakeProposal project(Run run, IntakeDocument d, String name, ProposalConfidence confidence,
            ProposalSource source, String reason) {
        return proposal(run, d.id(), ProposalKind.PROJECT, Optional.of(run.confirmed.get(name)), Optional.empty(),
                Optional.empty(), confidence, source, reason);
    }

    /** El principio literal del texto que se le dio al modelo: nunca su respuesta. */
    private static String beginning(String text) {
        return cut(text.strip());
    }

    private static Optional<String> folderOf(IntakeDocument d) {
        return d.path().filter(p -> p.contains("/")).map(p -> p.substring(0, p.lastIndexOf('/')));
    }

    /** Un documento que cita el número de una ficha de caso del mismo proyecto propuesto. */
    private void links(Run run, List<IntakeDocument> all) {
        for (IntakeDocument card : all) {
            CaseFileRules.CaseMark mark = CaseFileRules.detect(card.text());
            Optional<String> number = mark.caseFile() ? mark.number() : Optional.empty();
            String project = run.projectOf.get(card.id());
            if (number.isEmpty() || project == null) {
                continue;
            }
            Pattern citation = citation(number.get());
            for (IntakeDocument d : all) {
                Matcher m = citation.matcher(d.text());
                if (d != card && project.equals(run.projectOf.get(d.id())) && m.find()) {
                    run.made.add(proposal(run, d.id(), ProposalKind.CASE_LINK, Optional.empty(), Optional.of(card.id()),
                            number, ProposalConfidence.HIGH, ProposalSource.RULES, cut(m.group())));
                }
            }
        }
    }

    /** «Caso 2024-342» y «Número de caso: 2024-342», con guion o raya. */
    private static Pattern citation(String number) {
        String[] parts = number.split("-");
        return Pattern.compile("(?i)\\bcaso\\s*:?\\s*" + parts[0] + "\\s*[-–]\\s*" + parts[1] + "\\b");
    }

    private void duplicates(Run run, List<IntakeDocument> all, List<IntakeProposal> existing) {
        // un documento con un único proyecto propuesto se compara en ese proyecto; sin ninguna propuesta de proyecto, en el
        // grupo "" (como en el estudio); los ambiguos (varias propuestas) no se comparan con nadie
        Set<DocumentId> proposed = Stream.concat(existing.stream(), run.made.stream())
                .filter(p -> p.kind() == ProposalKind.PROJECT).map(IntakeProposal::documentId).collect(Collectors.toSet());
        List<DuplicateFinder.Candidate> candidates = all.stream()
                .filter(d -> run.projectOf.containsKey(d.id()) || !proposed.contains(d.id()))
                .map(d -> new DuplicateFinder.Candidate(d.id().value().toString(), run.projectOf.getOrDefault(d.id(), ""),
                        d.scan(), d.text())).toList();
        for (DuplicateFinder.Duplicate dup : DuplicateFinder.find(candidates)) {
            if (!dup.reason().isBlank()) {
                run.made.add(proposal(run, new DocumentId(java.util.UUID.fromString(dup.documentId())), dup.kind(),
                        Optional.empty(), Optional.of(new DocumentId(java.util.UUID.fromString(dup.otherId()))),
                        Optional.empty(), ProposalConfidence.HIGH, ProposalSource.RULES, cut(dup.reason())));
            }
        }
    }

    private IntakeProposal proposal(Run run, DocumentId document, ProposalKind kind, Optional<ProjectId> project,
            Optional<DocumentId> target, Optional<String> caseNumber, ProposalConfidence confidence,
            ProposalSource source, String reason) {
        return new IntakeProposal(ids.newId(), run.company(), run.batch.id(), document, kind, project, target,
                caseNumber, confidence, source, reason, DecisionState.PROPOSED, Optional.empty(), Optional.empty());
    }

    private static String cut(String reason) {
        return reason.length() > REASON_MAX ? reason.substring(0, REASON_MAX) : reason;
    }
}
