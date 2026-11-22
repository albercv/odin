package com.e2d.odin.domain.citation;

import com.e2d.odin.domain.document.DocumentPage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas cerradas y deterministas: el identificador propio de un documento (etiqueta + código en las primeras líneas de
 * la página 1) y los códigos que cita con un verbo de cambio. Sin parecidos aproximados: el código se lee exacto.
 */
public final class LinkExtractor {

    public static final String VERSION = "links-1";
    static final int HEAD_LINES = 15;
    static final int MAX_QUOTE = 300;

    private static final String NUMBER = "(?:n\\.?\\s?[º°]\\.?|núm\\.?|número)";
    private static final String KIND = "(?:factura(?:\\s+rectificativa)?|póliza|contrato|presupuesto)";
    /** Letras mayúsculas y cifras en bloques unidos por - / . y sin nada pegado delante ni detrás. */
    private static final String CODE = "(?<![\\p{L}\\d_\\-/.])(?<code>[A-Z0-9]+(?:[-/.][A-Z0-9]+)+)(?![\\p{L}\\d_]|[-/.][\\p{L}\\d])";
    private static final Pattern OWN = Pattern.compile("(?iu:(?:" + NUMBER + "\\s+de\\s+" + KIND + "|" + KIND + "\\s+"
            + NUMBER + "))\\s*:?\\s*" + CODE);
    private static final String FILLER = "(?iu:(?:a|al|la|el|lo|los|las|del|de|anterior|" + KIND + "|norma|" + NUMBER
            + ")(?![\\p{L}\\d])\\.?\\s*:?\\s*)";
    private static final Pattern CHANGE = Pattern.compile("(?iu:\\b(?<verb>rectifica|sustituye(?:\\s+y\\s+anula)?|anula|"
            + "modifica|deja\\s+sin\\s+efecto)\\b)\\s*:?\\s*" + FILLER + "{0,6}" + CODE);
    private static final Pattern BOUNDARY = Pattern.compile("(?<=[.!?;])\\s|\\n");

    private LinkExtractor() {
    }

    public static List<OwnIdentifier> ownIdentifiers(List<DocumentPage> pages) {
        return pages.stream().filter(p -> p.number() == 1).findFirst().map(DocumentPage::text).map(LinkExtractor::head)
                .map(LinkExtractor::ownIn).orElse(List.of());
    }

    private static String head(String text) {
        return text.lines().filter(line -> !line.isBlank()).limit(HEAD_LINES).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    /** Una cita con verbo («Rectifica la factura n.º X») no es el identificador propio, aunque lleve la etiqueta. */
    private static List<OwnIdentifier> ownIn(String head) {
        Set<Integer> cited = new java.util.HashSet<>();
        for (Matcher m = CHANGE.matcher(head); m.find(); ) {
            cited.add(m.start("code"));
        }
        Map<String, OwnIdentifier> found = new LinkedHashMap<>();
        for (Matcher m = OWN.matcher(head); m.find(); ) {
            if (!cited.contains(m.start("code")) && hasDigit(m.group("code"))) {
                found.putIfAbsent(m.group("code"), new OwnIdentifier(m.group("code"), 1));
            }
        }
        return new ArrayList<>(found.values());
    }

    public static List<DocumentCitation> citations(List<DocumentPage> pages, List<OwnIdentifier> own) {
        Set<String> ownCodes = own.stream().map(OwnIdentifier::identifier).collect(java.util.stream.Collectors.toSet());
        Map<String, DocumentCitation> found = new LinkedHashMap<>();
        for (DocumentPage page : pages) {
            for (Matcher m = CHANGE.matcher(page.text()); m.find(); ) {
                String code = m.group("code");
                LinkRelation relation = relationOf(m.group("verb"));
                if (hasDigit(code) && !ownCodes.contains(code)) {
                    found.putIfAbsent(relation + "|" + code,
                            new DocumentCitation(relation, code, page.number(), quote(page.text(), m)));
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static boolean hasDigit(String code) {
        return code.chars().anyMatch(Character::isDigit);
    }

    private static LinkRelation relationOf(String verb) {
        String v = verb.toLowerCase(java.util.Locale.ROOT);
        if (v.startsWith("rectifica")) {
            return LinkRelation.RECTIFIES;
        }
        return v.startsWith("modifica") || v.startsWith("deja") ? LinkRelation.MODIFIES : LinkRelation.REPLACES;
    }

    /** La oración entera; si es larguísima (texto de OCR sin puntos), desde el verbo y con tope. */
    private static String quote(String text, Matcher match) {
        int start = 0;
        for (Matcher b = BOUNDARY.matcher(text.substring(0, match.start())); b.find(); ) {
            start = b.end();
        }
        Matcher after = BOUNDARY.matcher(text);
        int end = after.find(match.end()) ? after.start() : text.length();
        if (end - start > MAX_QUOTE) {
            start = match.start();
            end = Math.min(end, start + MAX_QUOTE);
        }
        return text.substring(start, Math.max(end, match.end())).strip();
    }
}
