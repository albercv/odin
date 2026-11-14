package com.e2d.odin.domain.intake;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Las reglas de proyecto del estudio de la primera carga (clasificar.py), sin dependencias. Aquí, el descubrimiento de
 * nombres a partir de los textos; la asignación de un documento a un proyecto de la lista se añade en otra tarea.
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
    private static final Pattern CLOSING_BRACKET = Pattern.compile("\\)\\s*$");

    private ProjectRules() {
    }

    /** Minúsculas, sin tildes ni signos, sin marcas de página, espacios únicos. */
    public static String normalize(String text) {
        String lower = PAGE_MARK.matcher(text).replaceAll(" ").toLowerCase(Locale.ROOT);
        String plain = Normalizer.normalize(lower, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return NOT_ALPHANUMERIC.matcher(plain).replaceAll(" ").trim();
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
                found.add(closeBracket(cut));
            }
        }
        return found;
    }

    private static String closeBracket(String name) {
        return name.chars().filter(c -> c == '(').count() > name.chars().filter(c -> c == ')').count()
                ? name + ")" : name;
    }
}
