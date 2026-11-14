package com.e2d.odin.domain.intake;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fechas, importes, tipo e identificadores de póliza de un texto (textlib.py del estudio). */
public final class DocumentFacts {

    private static final List<String> MONTHS = List.of("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre");
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern DATE = Pattern.compile("\\b(\\d{1,2})\\s+de\\s+(" + String.join("|", MONTHS)
            + ")\\s+de\\s+(\\d{4})\\b|\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b", FLAGS);
    private static final Pattern AMOUNT = Pattern.compile(
            "\\b\\d{1,3}(?:\\.\\d{3})*(?:,\\d+)?\\s*(?:euros|€)|\\b\\d+(?:,\\d+)?\\s*(?:euros|€)", FLAGS);
    private static final String ID = "\\b[A-Z]{2,4}-\\d{3,5}-\\d{4}\\b";
    private static final Pattern OWN_ID = Pattern.compile(ID);
    private static final Pattern REPLACES = Pattern.compile("sustituye a la p[oó]liza n[º°o.]*\\s*(" + ID + ")", FLAGS);
    private static final int TYPE_LINES = 4;
    private static final List<String[]> TYPE_WORDS = List.of(new String[] {"ficha de caso", "caso"},
            new String[] {"acta", "acta"}, new String[] {"estatutos", "estatutos"}, new String[] {"anexo", "anexo"},
            new String[] {"presupuesto", "presupuesto"}, new String[] {"contrato", "contrato"},
            new String[] {"seguro", "poliza"}, new String[] {"poliza", "poliza"},
            new String[] {"comunicacion", "comunicacion"}, new String[] {"aviso", "aviso"},
            new String[] {"norma", "norma"}, new String[] {"procedimiento", "procedimiento"});

    private DocumentFacts() {
    }

    /**
     * Las fechas ("d" + aaaammdd) y los importes ("a" + importe sin espacios ni puntos de millar) del texto, cada uno con
     * su primer fragmento literal.
     */
    public static Map<String, String> literals(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher d = DATE.matcher(text);
        while (d.find()) {
            out.putIfAbsent(dateKey(d), d.group());
        }
        Matcher a = AMOUNT.matcher(text);
        while (a.find()) {
            out.putIfAbsent("a" + a.group().toLowerCase(Locale.ROOT).replaceAll("[\\s.]", "").replace("€", "euros"),
                    a.group());
        }
        return out;
    }

    private static String dateKey(Matcher m) {
        boolean written = m.group(1) != null;
        int day = Integer.parseInt(written ? m.group(1) : m.group(4));
        int month = written ? MONTHS.indexOf(m.group(2).toLowerCase(Locale.ROOT)) + 1 : Integer.parseInt(m.group(5));
        return String.format("d%04d%02d%02d", Integer.parseInt(written ? m.group(3) : m.group(6)), month, day);
    }

    /** Tipo por las palabras de las primeras líneas, o «desconocido». */
    public static String guessType(String text) {
        String head = ProjectRules.normalize(String.join("\n", text.strip().lines().limit(TYPE_LINES).toList()));
        return TYPE_WORDS.stream().filter(w -> head.contains(w[0])).map(w -> w[1]).findFirst().orElse("desconocido");
    }

    /** El primer identificador de póliza del texto (el propio). */
    public static Optional<String> ownId(String text) {
        Matcher m = OWN_ID.matcher(text);
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }

    /** El identificador de la póliza que el texto dice sustituir. */
    public static Optional<String> replacedId(String text) {
        return fragment(text).map(m -> m.group(1).toUpperCase(Locale.ROOT));
    }

    /** El fragmento literal «sustituye a la póliza nº …», si el texto lo dice. */
    public static Optional<String> replacesFragment(String text) {
        return fragment(text).map(Matcher::group);
    }

    private static Optional<Matcher> fragment(String text) {
        Matcher m = REPLACES.matcher(text);
        return m.find() ? Optional.of(m) : Optional.empty();
    }
}
