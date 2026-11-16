package com.e2d.odin.domain.intake;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Las reglas de proyecto del estudio de la primera carga (clasificar.py), sin dependencias. Aquí, el descubrimiento de
 * nombres a partir de los textos y la asignación de un documento a un proyecto de la lista.
 */
public final class ProjectRules {

    /** Un nombre descubierto, con la primera grafía vista y en cuántos documentos sale. */
    public record Discovered(String name, int documentCount) {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern PAGE_MARK = Pattern.compile("=== p[aá]gina \\d+ ===", FLAGS);
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final String PLACE = "(?:calle|residencial|avenida)\\s+[^,.\\n;]+";
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("^comunidad:\\s*(?:comunidad de propietarios\\s+)?(" + PLACE + ")", FLAGS | Pattern.MULTILINE),
            Pattern.compile("comunidad de propietarios\\s+(?:de la\\s+)?(" + PLACE + ")", FLAGS),
            Pattern.compile("^(?:domicilio del riesgo):\\s*(" + PLACE + ")", FLAGS | Pattern.MULTILINE));
    private static final Pattern TAIL = Pattern.compile("\\s+(?:de puerto|puerto albar|se|que|en|y|por|con|de|a)\\b.*$", FLAGS);
    private static final Map<String, List<String>> HEAD_VARIANTS = Map.of(
            "calle", List.of("calle", "c", "cl"),
            "residencial", List.of("residencial", "res"),
            "avenida", List.of("avenida", "avda", "av"));
    // Marcas de identidad o contacto: lo que sigue no es parte del nombre del proyecto.
    private static final Pattern IDENTITY_MARK = Pattern.compile("(?<![\\p{L}\\p{N}])(?:c\\.?\\s?i\\.?\\s?f|n\\.?\\s?i\\.?\\s?[fe]"
            + "|d\\.|dª|d[ñn]a|do[ñn]a|don|sra?|tel|tfno|tel[eé]fono|e-?mail|correo|c\\.?\\s?p)(?![\\p{L}\\p{N}])", FLAGS);
    private static final Pattern TITLE = Pattern.compile("d\\.|dª|d[ñn]a|do[ñn]a|don|sra?", FLAGS);
    private static final Set<String> GENERIC = Set.of("calle", "c", "cl", "avenida", "avda", "av", "residencial", "res",
            "plaza", "pza", "paseo", "pso", "comunidad", "urbanizacion", "urb", "camino", "carretera", "travesia",
            "ronda", "barrio", "edificio", "bloque", "portal", "poligono");
    private static final Set<String> EMPTY_WORDS = Set.of("del", "de", "la", "las", "los", "el", "y", "e");
    private static final int HEADER_LINES = 3;
    private static final int HEADER_WEIGHT = 3;
    private static final int QUOTE_MAX = 300;
    private static final Pattern CLOSING_BRACKET = Pattern.compile("\\)\\s*$");

    private ProjectRules() {
    }

    /** Minúsculas, sin tildes ni signos, sin marcas de página, espacios únicos. */
    public static String normalize(String text) {
        String lower = PAGE_MARK.matcher(text).replaceAll(" ").toLowerCase(Locale.ROOT);
        String plain = Normalizer.normalize(lower, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return NOT_ALPHANUMERIC.matcher(plain).replaceAll(" ").trim();
    }

    /**
     * Propone un proyecto de la lista para el texto. Un solo proyecto es HIGH; varios, LOW (con proyecto si la cabecera
     * desempata). La carpeta del cliente suma su señal, pero si contradice al texto la confianza es LOW.
     */
    public static ProjectMatch match(String text, List<String> projects, Optional<String> folder) {
        return match(text, projects, Map.of(), folder);
    }

    /**
     * Como {@link #match(String, List, Optional)}, y además busca los alias (grafía normalizada → nombre de la lista):
     * un alias cuenta como su nombre, así que nombre y alias juntos no hacen ambiguo al proyecto.
     */
    public static ProjectMatch match(String text, List<String> projects, Map<String, String> aliases,
            Optional<String> folder) {
        ProjectMatch byText = matchText(text, projects, aliases);
        Optional<String> byFolder = folder.flatMap(f -> folderProject(f, projects, aliases));
        return byFolder.map(p -> withFolder(byText, p, folder.get())).orElse(byText);
    }

    private static ProjectMatch matchText(String text, List<String> projects, Map<String, String> aliases) {
        String norm = normalize(text);
        int headerLength = normalize(headerText(text, HEADER_LINES)).length();
        Map<String, Integer> score = new LinkedHashMap<>();
        for (Hit hit : findMatches(norm, projects, aliases)) {
            score.merge(hit.name(), hit.start() < headerLength ? HEADER_WEIGHT : 1, Integer::sum);
        }
        List<String> ranked = score.keySet().stream().sorted((a, b) -> score.get(b) - score.get(a)).toList();
        if (ranked.isEmpty()) {
            return new ProjectMatch(Optional.empty(), List.of(), ProposalConfidence.LOW, ProposalSource.RULES, "");
        }
        boolean single = ranked.size() == 1;
        Optional<String> winner = single || score.get(ranked.get(0)) > score.get(ranked.get(1))
                ? Optional.of(ranked.get(0)) : Optional.empty();
        return new ProjectMatch(winner, ranked, single ? ProposalConfidence.HIGH : ProposalConfidence.LOW,
                ProposalSource.RULES, quote(text, projects, aliases, winner.map(List::of).orElse(ranked)));
    }

    private static ProjectMatch withFolder(ProjectMatch byText, String folderProject, String folder) {
        if (byText.candidates().isEmpty() || (byText.project().isEmpty() && byText.candidates().contains(folderProject))) {
            return new ProjectMatch(Optional.of(folderProject), List.of(folderProject), ProposalConfidence.HIGH,
                    ProposalSource.FOLDER, cut(folder.strip()));
        }
        if (byText.project().equals(Optional.of(folderProject))) {
            return new ProjectMatch(byText.project(), byText.candidates(), ProposalConfidence.HIGH, byText.source(),
                    byText.reason());
        }
        List<String> all = new ArrayList<>(byText.candidates());
        if (!all.contains(folderProject)) {
            all.add(folderProject);
        }
        return new ProjectMatch(byText.project(), all, ProposalConfidence.LOW, byText.source(), byText.reason());
    }

    /** El proyecto que nombra la carpeta, si nombra exactamente uno. */
    private static Optional<String> folderProject(String folder, List<String> projects,
            Map<String, String> aliases) {
        List<String> names = findMatches(normalize(folder), projects, aliases).stream().map(Hit::name).distinct().toList();
        return names.size() == 1 ? Optional.of(names.get(0)) : Optional.empty();
    }

    /** La primera línea del texto en la que sale uno de los proyectos elegidos (la cita literal, de 300 como mucho). */
    private static String quote(String text, List<String> projects, Map<String, String> aliases,
            List<String> chosen) {
        for (String line : text.split("\\R")) {
            if (findMatches(normalize(line), projects, aliases).stream().anyMatch(h -> chosen.contains(h.name()))) {
                return cut(line.strip());
            }
        }
        return cut(headerText(text, 1).strip());
    }

    private static String cut(String quote) {
        return quote.length() > QUOTE_MAX ? quote.substring(0, QUOTE_MAX) : quote;
    }

    private record Hit(int start, int end, String name) {
    }

    /** Variantes normalizadas de un nombre: «C/», «Calle», «Res.», «Avda.»; de la más larga a la más corta. */
    private static List<String> nameVariants(String name) {
        String base = normalize(name);
        int space = base.indexOf(' ');
        String head = space < 0 ? base : base.substring(0, space);
        String rest = space < 0 ? "" : base.substring(space + 1);
        Set<String> variants = new LinkedHashSet<>();
        HEAD_VARIANTS.getOrDefault(head, List.of(head)).forEach(h -> variants.add((h + " " + rest).strip()));
        return variants.stream().sorted((a, b) -> b.length() - a.length()).toList();
    }

    /** Todas las apariciones de nombres, de la más larga a la más corta, sin solaparse. */
    private static List<Hit> findMatches(String norm, List<String> projects, Map<String, String> aliases) {
        List<Hit> found = new ArrayList<>();
        Set<String> searched = new LinkedHashSet<>(projects);
        searched.addAll(aliases.keySet());
        for (String name : searched) {
            for (String variant : nameVariants(name)) {
                Matcher m = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(variant) + "(?![a-z0-9])").matcher(norm);
                while (m.find()) {
                    found.add(new Hit(m.start(), m.end(), aliases.getOrDefault(name, name)));
                }
            }
        }
        found.sort((a, b) -> a.end() - a.start() == b.end() - b.start()
                ? a.start() - b.start() : (b.end() - b.start()) - (a.end() - a.start()));
        List<Hit> kept = new ArrayList<>();
        for (Hit hit : found) {
            if (kept.stream().allMatch(k -> hit.end() <= k.start() || hit.start() >= k.end())) {
                kept.add(hit);
            }
        }
        return kept;
    }

    /** Las primeras líneas con contenido, sin las marcas de página. */
    static String headerText(String text, int lines) {
        return text.lines().filter(l -> !l.isBlank() && !PAGE_MARK.matcher(l.strip()).matches())
                .limit(lines).collect(java.util.stream.Collectors.joining("\n"));
    }

    /** Nombres de proyecto que salen en el texto, sin lista previa, normalizados y en orden de aparición. */
    public static List<String> discover(String text) {
        return spellings(text).stream().map(ProjectRules::normalize).toList();
    }

    /** Los nombres de todos los textos, con en cuántos documentos sale cada uno (contado una vez por documento). */
    public static List<Discovered> discover(Collection<String> texts) {
        Map<String, String> spelling = new LinkedHashMap<>();
        Map<String, Integer> documents = new LinkedHashMap<>();
        for (String text : texts) {
            Map<String, String> inThisOne = new LinkedHashMap<>();
            for (String raw : spellings(text)) {
                inThisOne.putIfAbsent(normalize(raw), raw);
            }
            inThisOne.forEach((key, raw) -> {
                spelling.putIfAbsent(key, raw);
                documents.merge(key, 1, Integer::sum);
            });
        }
        return spelling.entrySet().stream()
                .map(e -> new Discovered(e.getValue(), documents.get(e.getKey()))).toList();
    }

    private static List<String> spellings(String text) {
        List<String> found = new ArrayList<>();
        for (Pattern pattern : PATTERNS) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String cut = TAIL.matcher(m.group(1).strip()).replaceFirst("");
                cut = CLOSING_BRACKET.matcher(cut).replaceFirst(")");
                clean(closeBracket(cut)).ifPresent(found::add);
            }
        }
        return found;
    }

    /**
     * El nombre sin lo que sigue a la primera marca de identidad o de contacto (CIF, Doña, Tel.…; la primera palabra, la
     * de cabeza, nunca se corta). Vacío si queda solo una palabra genérica o acaba en una palabra vacía (nombre cortado).
     */
    public static Optional<String> clean(String name) {
        String stripped = name.strip();
        int head = stripped.indexOf(' ');
        String cut = head < 0 ? stripped : stripped.substring(0, markStart(stripped, head + 1));
        cut = cut.replaceAll("[\\s\\-:/]+$", "");
        String key = normalize(cut);
        String last = key.substring(key.lastIndexOf(' ') + 1);
        boolean useless = key.isEmpty() || GENERIC.contains(key) || EMPTY_WORDS.contains(last);
        return useless ? Optional.empty() : Optional.of(cut);
    }

    /** Dónde empieza la primera marca de identidad a partir de {@code from}; un tratamiento (Don, Doña…) pegado a la palabra de cabeza no vale. */
    private static int markStart(String name, int from) {
        Matcher mark = IDENTITY_MARK.matcher(name);
        while (mark.find(from)) {
            if (mark.start() > from || !TITLE.matcher(mark.group()).matches()) {
                return mark.start();
            }
            from = mark.end(); // «Calle Don Ramón»: «Don» es parte del nombre, se sigue buscando después
        }
        return name.length();
    }

    private static String closeBracket(String name) {
        return name.chars().filter(c -> c == '(').count() > name.chars().filter(c -> c == ')').count()
                ? name + ")" : name;
    }
}
