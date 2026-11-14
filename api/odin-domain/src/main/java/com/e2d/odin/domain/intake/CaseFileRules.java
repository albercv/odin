package com.e2d.odin.domain.intake;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Las reglas de ficha de caso y de tipo de documento del estudio (clasificar.py), sin dependencias. */
public final class CaseFileRules {

    /** Si el documento es una ficha de caso, su número y la cita literal que lo justifica (hasta 300 caracteres). */
    public record CaseMark(boolean caseFile, Optional<String> number, String reason) {
    }

    private record TypeRule(String type, Pattern pattern, boolean firstLineOnly) {
        TypeRule(String type, String regex) {
            this(type, Pattern.compile(regex), regex.contains("^"));
        }
    }

    private static final int CASE_FILE_LINES = 3;
    private static final int NUMBER_LINES = 12;
    private static final int QUOTE_MAX = 300;
    private static final Pattern CASE_NUMBER = Pattern.compile("(?i)\\bcaso\\s+(\\d{4})\\s*[-–]\\s*(\\d{3})\\b");
    // El orden importa: «anexo al contrato» antes que «contrato».
    private static final List<TypeRule> TYPE_RULES = List.of(
            new TypeRule("caso", "\\bficha de caso\\b"),
            new TypeRule("anexo", "\\banexo\\b"),
            new TypeRule("acta", "\\bacta de la junta\\b|^acta\\b"),
            new TypeRule("contrato", "\\bcontrato\\b"),
            new TypeRule("poliza", "\\bseguro multirriesgo\\b|\\bcondiciones particulares\\b"),
            new TypeRule("presupuesto", "\\bpresupuesto\\b"),
            new TypeRule("comunicacion", "\\bcomunicacion a los propietarios\\b"),
            new TypeRule("aviso", "^aviso\\b"),
            new TypeRule("norma", "\\bnorma interna\\b"),
            new TypeRule("procedimiento", "^procedimiento\\b"),
            new TypeRule("estatutos", "^estatutos\\b"));

    private CaseFileRules() {
    }

    /** «FICHA DE CASO» en la cabecera y el número en sus dos formatos («Caso 2024-342» y «Número de caso: …»). */
    public static CaseMark detect(String text) {
        Optional<String> fichaLine = ProjectRules.headerText(text, CASE_FILE_LINES).lines()
                .filter(l -> ProjectRules.normalize(l).contains("ficha de caso")).findFirst();
        Matcher m = CASE_NUMBER.matcher(ProjectRules.headerText(text, NUMBER_LINES));
        Optional<String> number = m.find() ? Optional.of(m.group(1) + "-" + m.group(2)) : Optional.empty();
        String reason = fichaLine.orElse(number.isPresent() ? m.group() : "");
        return new CaseMark(fichaLine.isPresent(), number,
                reason.length() > QUOTE_MAX ? reason.substring(0, QUOTE_MAX) : reason);
    }

    /** El tipo de documento por las palabras de la cabecera; vacío si no se reconoce. */
    public static Optional<String> documentType(String text) {
        String head = ProjectRules.normalize(ProjectRules.headerText(text, CASE_FILE_LINES));
        String first = ProjectRules.normalize(ProjectRules.headerText(text, 1));
        return TYPE_RULES.stream().filter(r -> r.pattern().matcher(r.firstLineOnly() ? first : head).find())
                .map(TypeRule::type).findFirst();
    }
}
