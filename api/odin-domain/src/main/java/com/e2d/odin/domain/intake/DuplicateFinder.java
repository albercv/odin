package com.e2d.odin.domain.intake;

import com.e2d.odin.domain.intake.TextSimilarity.IdPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Escaneos y versiones dentro de cada proyecto propuesto (duplicates.py y textlib.py del estudio): tejas de 5 palabras,
 * MinHash y bandas para hallar candidatas, umbral 0,8 de Jaccard, y la señal «sustituye a la póliza nº …». Nunca
 * compara documentos de proyectos distintos. Los documentos sin proyecto (vacío) se comparan entre sí, como en el estudio.
 * La copia exacta (SHA-256 igual) no entra aquí: la resuelve la subida.
 */
public final class DuplicateFinder {

    /** Un documento del lote: su proyecto propuesto (vacío si es de toda la empresa), si lo leyó el OCR y su texto. */
    public record Candidate(String id, String project, boolean scan, String text) {
    }

    /**
     * SAME_AS_SCAN: {@code documentId} es el escaneo y {@code otherId} el original. SUPERSEDES: {@code documentId}
     * sustituye a {@code otherId}. {@code reason} es un fragmento literal del texto de {@code documentId}.
     */
    public record Duplicate(ProposalKind kind, String documentId, String otherId, String reason) {
    }

    private static final int SHINGLE_WORDS = 5;
    private static final double THRESHOLD = 0.8;

    private record Doc(Candidate source, Set<String> shingles, Map<String, String> literals, String type,
                       Optional<String> ownId, Optional<String> replaced) {

        String id() {
            return source.id();
        }

        boolean scan() {
            return source.scan();
        }

        boolean cites(Doc other) {
            return replaced.isPresent() && replaced.equals(other.ownId);
        }

        Optional<String> latestDate() {
            return literals.keySet().stream().filter(k -> k.startsWith("d")).max(Comparator.naturalOrder());
        }
    }

    private DuplicateFinder() {
    }

    public static List<Duplicate> find(List<Candidate> candidates) {
        Map<String, List<Doc>> byProject = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            byProject.computeIfAbsent(c.project() == null ? "" : c.project(), k -> new ArrayList<>()).add(prepare(c));
        }
        List<Duplicate> out = new ArrayList<>();
        byProject.values().forEach(docs -> out.addAll(findInProject(docs)));
        out.sort(Comparator.comparing(Duplicate::documentId).thenComparing(Duplicate::otherId));
        return out;
    }

    private static Doc prepare(Candidate c) {
        return new Doc(c, TextSimilarity.shingles(ProjectRules.normalize(c.text()), SHINGLE_WORDS),
                DocumentFacts.literals(c.text()), DocumentFacts.guessType(c.text()), DocumentFacts.ownId(c.text()),
                DocumentFacts.replacedId(c.text()));
    }

    private static List<Duplicate> findInProject(List<Doc> docs) {
        Map<String, Doc> byId = new HashMap<>();
        Map<String, long[]> signatures = new HashMap<>();
        docs.forEach(d -> {
            byId.put(d.id(), d);
            signatures.put(d.id(), TextSimilarity.minHash(d.shingles()));
        });
        Set<IdPair> pairs = new TreeSet<>(Comparator.comparing(IdPair::first).thenComparing(IdPair::second));
        pairs.addAll(TextSimilarity.lshCandidates(signatures));
        addCitations(docs, pairs);
        List<Duplicate> out = new ArrayList<>();
        for (IdPair p : pairs) {
            classify(byId.get(p.first()), byId.get(p.second())).ifPresent(out::add);
        }
        return out;
    }

    /** Candidatas por la propia póliza: la que cita a otra del proyecto, aunque los textos se parezcan poco. */
    private static void addCitations(List<Doc> docs, Set<IdPair> pairs) {
        Map<String, Doc> owner = new HashMap<>();
        docs.stream().filter(d -> !d.scan() && d.ownId().isPresent()).forEach(d -> owner.put(d.ownId().get(), d));
        for (Doc d : docs) {
            d.replaced().map(owner::get).filter(o -> !o.id().equals(d.id()))
                    .ifPresent(o -> pairs.add(TextSimilarity.pair(d.id(), o.id())));
        }
    }

    private static Optional<Duplicate> classify(Doc a, Doc b) {
        double sim = TextSimilarity.jaccard(a.shingles(), b.shingles());
        boolean sameType = a.type().equals(b.type());
        boolean cites = a.cites(b) || b.cites(a);
        if (sim < THRESHOLD) {
            return sameType && cites ? supersedes(a, b) : Optional.empty();
        }
        boolean sameFacts = TextSimilarity.jaccard(a.literals().keySet(), b.literals().keySet()) >= THRESHOLD;
        if ((a.scan() || b.scan()) && sameFacts) {
            return Optional.of(sameAsScan(a, b));
        }
        return sameType && !sameFacts && !(a.scan() && b.scan()) ? supersedes(a, b) : Optional.empty();
    }

    private static Duplicate sameAsScan(Doc a, Doc b) {
        boolean aIsScan = a.scan() && (!b.scan() || a.id().compareTo(b.id()) > 0);
        Doc scan = aIsScan ? a : b;
        Doc original = aIsScan ? b : a;
        String reason = scan.literals().entrySet().stream().filter(e -> original.literals().containsKey(e.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(scan.source().text().strip().lines().findFirst().orElse(""));
        return new Duplicate(ProposalKind.SAME_AS_SCAN, scan.id(), original.id(), reason);
    }

    /** El que cita al otro lo sustituye; si no, el de fecha más reciente. Sin orden claro, no se propone nada. */
    private static Optional<Duplicate> supersedes(Doc a, Doc b) {
        if (a.cites(b) || b.cites(a)) {
            Doc newer = a.cites(b) ? a : b;
            Doc older = newer == a ? b : a;
            String reason = DocumentFacts.replacesFragment(newer.source().text()).orElseThrow();
            return Optional.of(new Duplicate(ProposalKind.SUPERSEDES, newer.id(), older.id(), reason));
        }
        int order = a.latestDate().orElse("").compareTo(b.latestDate().orElse(""));
        if (order == 0) {
            return Optional.empty();
        }
        Doc newer = order > 0 ? a : b;
        Doc older = newer == a ? b : a;
        return Optional.of(new Duplicate(ProposalKind.SUPERSEDES, newer.id(), older.id(),
                newer.literals().get(newer.latestDate().orElseThrow())));
    }
}
